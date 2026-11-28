package com.cyclone.open.core

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import java.time.Instant

internal data class ControllerObservation(
    val androidApi: Int,
    val abis: List<String>,
    val virtualizationFeature: Boolean?,
    val availableMemoryBytes: Long?,
    val availableStorageBytes: Long?,
    val thermalStatus: Int?,
    val observedAt: Instant
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

    fun guest(kind: RuntimeKind, now: Instant): RuntimeReadiness {
        val runtime = UnavailableRuntime(kind).status()
        // The app has no connected runtime supplying authenticated guest observations.
        check(runtime.state == RuntimeState.NOT_IMPLEMENTED)
        return runtimeReadiness(BackendAvailability.BLOCKED, null, null, now)
    }
}
