/*
 * Adapted from premiumcentraal-boop/Cyclone-Cloak @ 62a05e4a966119908125016211096983cb258529
 * android/app/src/main/java/dev/cyclone/cloak/diagnostics/LogBus.kt
 * MIT, Copyright (c) 2026 Cyclone Cloak contributors. See docs/licenses/Cyclone-Cloak-MIT.txt.
 * Wraps typed operations and recovers prior-run history without replacing the functional journal.
 */
package com.cyclone.open.core

import android.content.Context
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.IOException
import java.util.UUID

internal class LogBus(private val registry: DiagnosticsRegistry) {
    private var started = false
    val history = registry.history

    @Synchronized
    fun startProcess() {
        if (started) return
        bestEffort(registry::recoverInterrupted)
        started = true
    }

    suspend fun <T> track(action: ControllerAction, work: suspend () -> T): T {
        startProcess()
        val id = UUID.randomUUID()
        bestEffort { registry.start(id, action) }
        try {
            val result = work()
            bestEffort { registry.finish(id, RunStatus.SUCCEEDED, RunReason.NONE) }
            return result
        } catch (error: CancellationException) {
            bestEffort { registry.finish(id, RunStatus.INTERRUPTED, RunReason.INTERRUPTED) }
            throw error
        } catch (error: Exception) {
            bestEffort { registry.finish(id, RunStatus.FAILED, when (error) {
                is InsufficientProfileStorageException -> RunReason.STORAGE_FULL
                is DraftReviewException -> RunReason.REVIEW_REQUIRED
                is IOException -> RunReason.IO_FAILURE
                else -> RunReason.OPERATION_FAILED
            }) }
            throw error
        }
    }

    private fun bestEffort(action: () -> Unit) {
        try { action() } catch (_: Exception) { registry.markUnavailable() }
    }

    companion object {
        @Volatile private var instance: LogBus? = null
        fun get(context: Context): LogBus = instance ?: synchronized(this) {
            instance ?: LogBus(DiagnosticsRegistry(File(context.noBackupFilesDir, "operation-runs-v1.json")))
                .also { instance = it }
        }
    }
}
