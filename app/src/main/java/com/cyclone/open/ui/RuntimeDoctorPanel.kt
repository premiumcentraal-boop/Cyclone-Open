/*
 * Readiness presentation adapted from premiumcentraal-boop/Cyclone-Cloak
 * at 62a05e4a966119908125016211096983cb258529:
 * android/app/src/main/java/dev/cyclone/cloak/ui/ProfileReadinessCard.kt.
 * MIT, Copyright (c) 2026 Cyclone Cloak contributors. See docs/licenses/Cyclone-Cloak-MIT.txt.
 * Displays read-only host facts alongside separate guest, licensing and certification gates.
 */
package com.cyclone.open.ui

import android.os.PowerManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.cyclone.open.core.BackendAvailability
import com.cyclone.open.core.ControllerObservation
import com.cyclone.open.core.GateStatus
import com.cyclone.open.core.QualificationGate
import com.cyclone.open.core.RuntimeDoctor
import com.cyclone.open.core.RuntimeKind
import java.time.Instant
import java.util.Locale

@Composable
internal fun RuntimeDoctorPanel(observation: ControllerObservation?, busy: Boolean, refresh: () -> Unit) {
    GlassPanel {
        Text("Runtime Doctor", style = MaterialTheme.typography.titleLarge)
        if (observation == null) {
            Text("Host observations are pending.", color = OpenGlass.Muted)
        } else {
            Text("Host API ${observation.androidApi} · ${observation.abis.joinToString().ifEmpty { "ABI unknown" }}")
            Text("Available host memory · ${formatBytes(observation.availableMemoryBytes)}")
            Text("Available profile storage · ${formatBytes(observation.availableStorageBytes)}")
            Text("Host thermal status · ${thermalLabel(observation.thermalStatus)}")
            val feature = when (observation.virtualizationFeature) {
                true -> "Advertised by Android"
                false -> "Not advertised by Android"
                null -> "Unknown"
            }
            Text("Virtualization feature · $feature")
            Text("Observed ${observation.observedAt}", style = MaterialTheme.typography.labelMedium, color = OpenGlass.Muted)
        }
        TextButton(onClick = refresh, enabled = !busy) { Text("Refresh checks") }
    }
    val now = observation?.observedAt ?: Instant.now()
    val local = RuntimeDoctor.guest(RuntimeKind.LOCAL_VM, now)
    val remote = RuntimeDoctor.guest(RuntimeKind.REMOTE_VM, now)
    GlassPanel {
        Text("Guest readiness", style = MaterialTheme.typography.titleLarge)
        Text("Local VM (Pixel AVF) · ${local.availability.label()}")
        Text("Remote VM (Cuttlefish) · ${remote.availability.label()}")
        local.gates.forEach { (gate, status) ->
            val remoteStatus = remote.gates.getValue(gate)
            val result = if (status == remoteStatus) status.label() else "Local ${status.label()} · Remote ${remoteStatus.label()}"
            Text("${gate.label()} · $result", color = OpenGlass.Muted)
        }
        Text("Guest checks require a qualified Pixel backend or an authorized Cuttlefish host.", color = OpenGlass.Muted)
    }
}

private fun formatBytes(bytes: Long?): String = bytes?.let {
    String.format(Locale.getDefault(), "%.1f GiB", it / 1_073_741_824.0)
} ?: "Unknown"

private fun thermalLabel(value: Int?): String = when (value) {
    PowerManager.THERMAL_STATUS_NONE -> "Normal"
    PowerManager.THERMAL_STATUS_LIGHT -> "Light"
    PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
    PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
    PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
    PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
    PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown"
    else -> "Unknown"
}

private fun BackendAvailability.label() = when (this) {
    BackendAvailability.AVAILABLE -> "Available"
    BackendAvailability.BLOCKED -> "Blocked"
    BackendAvailability.UNKNOWN -> "Unknown"
}

private fun QualificationGate.label() = when (this) {
    QualificationGate.GUEST_BOOT -> "Guest boot"
    QualificationGate.DISPLAY_AND_INPUT -> "Guest display and input"
    QualificationGate.PERSISTENCE -> "Guest persistence"
    QualificationGate.ISOLATION -> "Guest isolation"
    QualificationGate.APPLICATION_COMPATIBILITY -> "Application compatibility"
    QualificationGate.PLAY_LICENSING -> "Google Play licensing"
    QualificationGate.PLAY_STORE_FUNCTION -> "Play Store functionality"
    QualificationGate.CERTIFICATION -> "Certification"
}

private fun GateStatus.label(): String = when (this) {
    GateStatus.NOT_RUN -> "Not run"
    else -> name.lowercase().replaceFirstChar(Char::titlecase)
}
