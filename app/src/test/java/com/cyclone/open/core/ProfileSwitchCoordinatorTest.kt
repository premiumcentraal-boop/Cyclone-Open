package com.cyclone.open.core

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ProfileSwitchCoordinatorTest {
    private val host = GenerationLease(PhoneTarget.Host, 0)
    private val local = PhoneTarget(UUID.randomUUID().toString(), RuntimeKind.LOCAL_VM)

    @Test
    fun confirmationPublishesNewLeaseAndInvalidatesOldOne() = runBlocking {
        val journal = MemoryJournal()
        val observed = mutableListOf<SwitchStage>()
        val controller = ProfileSwitchCoordinator(journal, runtimeFor = { testRuntime { stage, lease ->
            assertEquals(SwitchOperation(lease, stage), journal.read().pending)
            assertEquals(host, journal.read().active)
            observed += stage
            RuntimeStepResult.Verified(lease)
        } })
        val expected = GenerationLease(local, 1)
        assertEquals(RuntimeStepResult.Verified(expected), controller.switchTo(local))
        assertEquals(SwitchStage.entries, observed)
        assertTrue(controller.accepts(expected))
        assertFalse(controller.accepts(host))
    }

    @Test
    fun staleOrWrongConfirmationKeepsPreviousLease() = runBlocking {
        for ((reason, response) in listOf(
            SwitchFailure.WRONG_TARGET to GenerationLease(PhoneTarget.Host, 1),
            SwitchFailure.STALE_GENERATION to GenerationLease(local, 0)
        )) {
            val journal = MemoryJournal()
            val controller = ProfileSwitchCoordinator(journal, runtimeFor = { testRuntime { stage, lease ->
                RuntimeStepResult.Verified(if (stage == SwitchStage.CONFIRM) response else lease)
            } })
            assertEquals(RuntimeStepResult.Rejected(reason), controller.switchTo(local))
            assertEquals(reason, journal.read().lastFailure)
            assertTrue(controller.accepts(host))
            assertFalse(controller.accepts(GenerationLease(local, 1)))
        }
    }

    @Test
    fun timeoutClearsPendingAndNextOperationUsesANewGeneration() = runBlocking {
        val journal = MemoryJournal()
        val controller = ProfileSwitchCoordinator(journal, runtimeFor = { kind ->
            if (kind == RuntimeKind.HOST) HostRuntime else testRuntime { _, _ -> awaitCancellation() }
        }, deadlineMillis = 50)
        assertEquals(RuntimeStepResult.Rejected(SwitchFailure.TIMEOUT), controller.switchTo(local))
        assertNull(journal.read().pending)
        assertTrue(controller.accepts(host))
        assertEquals(RuntimeStepResult.Verified(GenerationLease(PhoneTarget.Host, 2)), controller.switchTo(PhoneTarget.Host))
    }

    @Test
    fun productionGuestRuntimesRejectSwitches() = runBlocking {
        val journal = MemoryJournal()
        val controller = ProfileSwitchCoordinator(journal)
        for (kind in listOf(RuntimeKind.LOCAL_VM, RuntimeKind.REMOTE_VM)) {
            val target = PhoneTarget(local.id, kind)
            assertEquals(RuntimeStepResult.Rejected(SwitchFailure.NOT_IMPLEMENTED), controller.switchTo(target))
            assertEquals(RuntimeState.NOT_IMPLEMENTED, UnavailableRuntime(kind).status().state)
            assertTrue(controller.accepts(host))
        }
    }

    private class MemoryJournal : SwitchJournal {
        private var state = SwitchState()
        override fun read() = state
        override fun write(state: SwitchState) { this.state = state }
    }

    private fun testRuntime(operation: suspend (SwitchStage, GenerationLease) -> RuntimeStepResult) = object : PhoneRuntime {
        override fun status() = RuntimeStatus(RuntimeState.NOT_IMPLEMENTED, "Test runtime fixture")
        override suspend fun step(stage: SwitchStage, lease: GenerationLease) = operation(stage, lease)
    }
}
