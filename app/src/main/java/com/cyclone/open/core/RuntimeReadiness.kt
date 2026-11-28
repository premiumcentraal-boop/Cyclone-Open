/*
 * Evidence patterns adapted from premiumcentraal-boop/Cyclone-Cloak
 * at 62a05e4a966119908125016211096983cb258529:
 * docs/PLAY-SURFACE-CONTRACT-V1.md and docs/SERVICE-SURFACE-ISOLATION-V2.md.
 * MIT, Copyright (c) 2026 Cyclone Cloak contributors. See docs/licenses/Cyclone-Cloak-MIT.txt.
 * Binds functional observations to an exact guest scope and keeps ecosystem gates independent.
 */
package com.cyclone.open.core

import java.time.Instant

internal enum class BackendAvailability { AVAILABLE, BLOCKED, UNKNOWN }

internal enum class QualificationGate {
    GUEST_BOOT, DISPLAY_AND_INPUT, PERSISTENCE, ISOLATION, APPLICATION_COMPATIBILITY,
    PLAY_LICENSING, PLAY_STORE_FUNCTION, CERTIFICATION
}

internal enum class QualificationOutcome { PASSED, FAILED, DEGRADED, UNSUPPORTED }

internal enum class GateStatus { NOT_RUN, UNKNOWN, SUPPORTED, DEGRADED, BLOCKED, UNSUPPORTED, STALE }

internal data class GuestScope(
    val instanceId: String,
    val imageDigest: String,
    val backend: RuntimeKind,
    val backendBuildDigest: String,
    val generation: Long,
    val suiteVersion: String
) {
    init {
        require(Rules.validId(instanceId) && backend != RuntimeKind.HOST && generation >= 0)
        require(Rules.digest(imageDigest) == imageDigest && Rules.digest(backendBuildDigest) == backendBuildDigest)
        require(suiteVersion.matches(Regex("[A-Za-z0-9._-]{1,40}")))
    }
}

/** Created only from independently qualified runtime evidence. No configuration fields enter this record. */
internal data class GuestQualification(
    val scope: GuestScope,
    val evidenceDigest: String,
    val observedAt: Instant,
    val validUntil: Instant,
    val outcomes: Map<QualificationGate, QualificationOutcome>
) {
    init {
        require(Rules.digest(evidenceDigest) == evidenceDigest && validUntil.isAfter(observedAt))
        require(outcomes.isNotEmpty())
    }
}

internal data class RuntimeReadiness(
    val availability: BackendAvailability,
    val gates: Map<QualificationGate, GateStatus>
) {
    private fun passed(gate: QualificationGate) = gates[gate] == GateStatus.SUPPORTED

    val usable: Boolean get() = availability == BackendAvailability.AVAILABLE && listOf(
        QualificationGate.GUEST_BOOT, QualificationGate.DISPLAY_AND_INPUT, QualificationGate.PERSISTENCE,
        QualificationGate.ISOLATION, QualificationGate.APPLICATION_COMPATIBILITY
    ).all(::passed)

    val playCapable: Boolean get() = usable && listOf(
        QualificationGate.PLAY_LICENSING, QualificationGate.PLAY_STORE_FUNCTION, QualificationGate.CERTIFICATION
    ).all(::passed)
}

internal fun runtimeReadiness(
    availability: BackendAvailability,
    scope: GuestScope?,
    evidence: GuestQualification?,
    now: Instant
): RuntimeReadiness = RuntimeReadiness(availability, QualificationGate.entries.associateWith { gate ->
    val outcome = evidence?.outcomes?.get(gate) ?: return@associateWith GateStatus.NOT_RUN
    when {
        scope == null || now.isBefore(evidence.observedAt) -> GateStatus.UNKNOWN
        evidence.scope != scope || !now.isBefore(evidence.validUntil) -> GateStatus.STALE
        else -> when (outcome) {
            QualificationOutcome.PASSED -> GateStatus.SUPPORTED
            QualificationOutcome.FAILED -> GateStatus.BLOCKED
            QualificationOutcome.DEGRADED -> GateStatus.DEGRADED
            QualificationOutcome.UNSUPPORTED -> GateStatus.UNSUPPORTED
        }
    }
})
