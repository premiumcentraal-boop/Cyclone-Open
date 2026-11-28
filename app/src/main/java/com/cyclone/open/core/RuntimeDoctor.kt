package com.cyclone.open.core

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import java.time.Instant
import java.util.UUID

internal data class ControllerObservation(
    val androidApi: Int,
    val abis: List<String>,
    val virtualizationFeature: Boolean?,
    val availableMemoryBytes: Long?,
    val availableStorageBytes: Long?,
    val thermalStatus: Int?,
    val observedAt: Instant
)

internal enum class DraftVolumeStatus { PRESENT, MISSING, UNKNOWN }

internal enum class DraftGrantStatus { HELD, MISSING, UNKNOWN }

internal data class DraftHealth(
    val phoneId: String,
    val volume: DraftVolumeStatus,
    val imageGrant: DraftGrantStatus
)

internal object RuntimeDoctor {
    fun observe(context: Context): ControllerObservation = ControllerObservation(
        androidApi = Build.VERSION.SDK_INT,
        abis = Build.SUPPORTED_ABIS.toList(),
        virtualizationFeature = runCatching {
            context.packageManager.hasSystemFeature("android.software.virtualization_framework")
        }.getOrNull(),
        availableMemoryBytes = runCatching {
            context.getSystemService(ActivityManager::class.java)?.let { manager ->
                ActivityManager.MemoryInfo().also(manager::getMemoryInfo).availMem
            }
        }.getOrNull(),
        availableStorageBytes = runCatching { StatFs(context.noBackupFilesDir.path).availableBytes }.getOrNull(),
        thermalStatus = runCatching { context.getSystemService(PowerManager::class.java)?.currentThermalStatus }.getOrNull(),
        observedAt = Instant.now()
    )

    fun inspectDraft(registry: Registry, storage: ProfileStorage, images: ImageReferences): DraftHealth? {
        val selected = registry.phones.firstOrNull { it.id == registry.selected } ?: return null
        val volume = runCatching { storage.isPrepared(UUID.fromString(selected.id)) }.getOrNull()
        val image = registry.images.firstOrNull { it.id == selected.imageId }
        val grant = image?.let { runCatching { images.hasPersistedReadGrant(it) }.getOrNull() }
        return DraftHealth(
            selected.id,
            when (volume) {
                true -> DraftVolumeStatus.PRESENT
                false -> DraftVolumeStatus.MISSING
                null -> DraftVolumeStatus.UNKNOWN
            },
            when (grant) {
                true -> DraftGrantStatus.HELD
                false -> DraftGrantStatus.MISSING
                null -> DraftGrantStatus.UNKNOWN
            }
        )
    }

    fun guest(kind: RuntimeKind, now: Instant): RuntimeReadiness {
        val runtime = UnavailableRuntime(kind).status()
        // The app has no connected runtime supplying authenticated guest observations.
        check(runtime.state == RuntimeState.NOT_IMPLEMENTED)
        return runtimeReadiness(BackendAvailability.BLOCKED, null, null, now)
    }
}
