/*
 * Adapted from premiumcentraal-boop/Cyclone @ 1ad783823c79960d9cc09935e8f46253c3823dcc
 * apps/mobile/app/src/main/java/com/cyclone/mobile/runtime/workspaces/ProfileSwitch.kt
 * apps/mobile/app/src/main/java/com/cyclone/mobile/runtime/workspaces/WorkspaceRuntime.kt
 * Proprietary source, owner-authorized reuse. Preserves stage and generation checks.
 * Runtime adapters accept typed requests, with confirmation supplied by the adapter.
 */
package com.cyclone.open.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

internal data class PhoneTarget(val id: String, val kind: RuntimeKind) {
    init {
        require(if (kind == RuntimeKind.HOST) id == HOST_ID else Rules.validId(id))
    }

    companion object {
        val Host = PhoneTarget(HOST_ID, RuntimeKind.HOST)
    }
}

internal data class GenerationLease(val target: PhoneTarget, val generation: Long) {
    init {
        require(generation >= 0)
    }
}

internal enum class SwitchStage { PREFLIGHT, PREPARE, CONFIRM }

internal enum class SwitchFailure { NOT_IMPLEMENTED, WRONG_TARGET, STALE_GENERATION, TIMEOUT, INTERRUPTED }

internal sealed interface RuntimeStepResult {
    data class Verified(val lease: GenerationLease) : RuntimeStepResult
    data class Rejected(val reason: SwitchFailure) : RuntimeStepResult
}

internal data class SwitchOperation(val lease: GenerationLease, val stage: SwitchStage)

internal data class SwitchState(
    val generation: Long = 0,
    val active: GenerationLease = GenerationLease(PhoneTarget.Host, 0),
    val pending: SwitchOperation? = null,
    val lastFailure: SwitchFailure? = null
) {
    init {
        require(active.generation <= generation)
        if (pending != null) {
            require(pending.lease.generation == generation && generation > active.generation)
            require(lastFailure == null)
        }
    }
}

internal interface SwitchJournal {
    fun read(): SwitchState
    fun write(state: SwitchState)
}

internal class ProfileSwitchCoordinator(
    private val journal: SwitchJournal,
    private val runtimeFor: (RuntimeKind) -> PhoneRuntime = { kind ->
        if (kind == RuntimeKind.HOST) HostRuntime else UnavailableRuntime(kind)
    },
    private val deadlineMillis: Long = 5_000
) {
    private val mutex = Mutex()

    suspend fun recover(): SwitchState = mutex.withLock { recoverLocked() }

    suspend fun switchTo(target: PhoneTarget): RuntimeStepResult = mutex.withLock {
        val previous = recoverLocked()
        val generation = Math.addExact(previous.generation, 1)
        val lease = GenerationLease(target, generation)
        val runtime = runtimeFor(target.kind)
        val reserved = previous.copy(generation = generation, lastFailure = null)
        val result = withTimeoutOrNull(deadlineMillis) { execute(reserved, lease, runtime) }
            ?: RuntimeStepResult.Rejected(SwitchFailure.TIMEOUT)
        val completed = when (result) {
            is RuntimeStepResult.Verified -> reserved.copy(active = result.lease)
            is RuntimeStepResult.Rejected -> reserved.copy(lastFailure = result.reason)
        }
        journal.write(completed)
        result
    }

    suspend fun accepts(lease: GenerationLease): Boolean = mutex.withLock {
        val state = journal.read()
        state.pending == null && state.active == lease
    }

    private fun recoverLocked(): SwitchState {
        val state = journal.read()
        if (state.pending == null) return state
        return state.copy(pending = null, lastFailure = SwitchFailure.INTERRUPTED).also(journal::write)
    }

    private suspend fun execute(
        reserved: SwitchState,
        lease: GenerationLease,
        runtime: PhoneRuntime
    ): RuntimeStepResult {
        for (stage in SwitchStage.entries) {
            journal.write(reserved.copy(pending = SwitchOperation(lease, stage)))
            when (val result = runtime.step(stage, lease)) {
                is RuntimeStepResult.Rejected -> return result
                is RuntimeStepResult.Verified -> {
                    if (result.lease.target != lease.target) return RuntimeStepResult.Rejected(SwitchFailure.WRONG_TARGET)
                    if (result.lease.generation != lease.generation) return RuntimeStepResult.Rejected(SwitchFailure.STALE_GENERATION)
                }
            }
        }
        return RuntimeStepResult.Verified(lease)
    }
}
