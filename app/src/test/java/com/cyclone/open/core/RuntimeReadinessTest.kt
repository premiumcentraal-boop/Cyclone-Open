package com.cyclone.open.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class RuntimeReadinessTest {
    private val observed = Instant.parse("2026-11-20T00:00:00Z")
    private val now = observed.plusSeconds(10)
    private val scope = GuestScope(UUID.randomUUID().toString(), "a".repeat(64), RuntimeKind.REMOTE_VM, "b".repeat(64), 1, "fixture-v1")
    private val functional = QualificationGate.entries.take(5).associateWith { QualificationOutcome.PASSED }
    private fun evidence(outcomes: Map<QualificationGate, QualificationOutcome>) =
        GuestQualification(scope, "f".repeat(64), observed, observed.plusSeconds(100), outcomes)

    @Test
    fun anAvailableBackendWithoutEvidenceCannotQualify() {
        val report = runtimeReadiness(BackendAvailability.AVAILABLE, scope, null, now)
        assertTrue(report.gates.values.all { it == GateStatus.NOT_RUN })
        assertFalse(report.usable)
        assertFalse(report.playCapable)
        assertFalse(RuntimeDoctor.guest(RuntimeKind.LOCAL_VM, now).usable)
        assertEquals(BackendAvailability.BLOCKED, RuntimeDoctor.guest(RuntimeKind.REMOTE_VM, now).availability)
    }

    @Test
    fun playFunctionLicensingAndCertificationAreIndependent() {
        val withoutLicenseOrCertification = evidence(functional + (QualificationGate.PLAY_STORE_FUNCTION to QualificationOutcome.PASSED))
        val reference = runtimeReadiness(BackendAvailability.AVAILABLE, scope, withoutLicenseOrCertification, now)
        assertTrue(reference.usable)
        assertFalse(reference.playCapable)
        assertEquals(GateStatus.NOT_RUN, reference.gates[QualificationGate.PLAY_LICENSING])
        assertEquals(GateStatus.NOT_RUN, reference.gates[QualificationGate.CERTIFICATION])
        val complete = evidence(QualificationGate.entries.associateWith { QualificationOutcome.PASSED })
        assertTrue(runtimeReadiness(BackendAvailability.AVAILABLE, scope, complete, now).playCapable)
        assertFalse(runtimeReadiness(BackendAvailability.BLOCKED, scope, complete, now).playCapable)
    }

    @Test
    fun evidenceStalesOnEveryScopeChangeAndExpiry() {
        val existing = evidence(functional)
        val staleScopes = listOf(
            scope.copy(instanceId = UUID.randomUUID().toString()),
            scope.copy(imageDigest = "c".repeat(64)),
            scope.copy(backend = RuntimeKind.LOCAL_VM),
            scope.copy(backendBuildDigest = "d".repeat(64)),
            scope.copy(generation = 2),
            scope.copy(suiteVersion = "fixture-v2")
        )
        for (changed in staleScopes) {
            val report = runtimeReadiness(BackendAvailability.AVAILABLE, changed, existing, now)
            assertEquals(GateStatus.STALE, report.gates[QualificationGate.GUEST_BOOT])
            assertFalse(report.usable)
        }
        val expired = runtimeReadiness(BackendAvailability.AVAILABLE, scope, existing, existing.validUntil)
        assertEquals(GateStatus.STALE, expired.gates[QualificationGate.GUEST_BOOT])
        assertFalse(expired.usable)
        val future = runtimeReadiness(BackendAvailability.AVAILABLE, scope, existing, observed.minusSeconds(1))
        assertEquals(GateStatus.UNKNOWN, future.gates[QualificationGate.GUEST_BOOT])
        assertFalse(future.usable)
    }
}
