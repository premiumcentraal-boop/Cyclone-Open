/*
 * Adapted from premiumcentraal-boop/Cyclone-Cloak @ 62a05e4a966119908125016211096983cb258529
 * android/app/src/main/java/dev/cyclone/cloak/diagnostics/DiagnosticsRegistry.kt
 * android/app/src/main/java/dev/cyclone/cloak/diagnostics/BindingRunJournal.kt
 * MIT, Copyright (c) 2026 Cyclone Cloak contributors. See docs/licenses/Cyclone-Cloak-MIT.txt.
 * Stores bounded typed controller runs. It records no profile identity, logs or exception payloads.
 */
package com.cyclone.open.core

import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.time.Instant
import java.util.UUID

internal enum class ControllerAction { CREATE_DRAFT, RESTORE_DRAFT, SAVE_BACKUP, REMOVE_BACKUP, RETURN_HOME }
internal enum class RunStatus { STARTED, SUCCEEDED, FAILED, INTERRUPTED }
internal enum class RunReason { NONE, STORAGE_FULL, REVIEW_REQUIRED, IO_FAILURE, INTERRUPTED, OPERATION_FAILED }

internal data class LifecycleRun(
    val id: UUID,
    val action: ControllerAction,
    val observedAt: Instant,
    val status: RunStatus,
    val reason: RunReason = RunReason.NONE
)

internal class DiagnosticsRegistry(base: File, private val now: () -> Instant = Instant::now) {
    private val file = AtomicFile(base)
    private val observed = MutableStateFlow<List<LifecycleRun>?>(null)
    val history = observed.asStateFlow()

    @Synchronized
    fun recoverInterrupted() {
        val existing = read()
        val recovered = existing.map { run ->
            if (run.status == RunStatus.STARTED) {
                run.copy(observedAt = now(), status = RunStatus.INTERRUPTED, reason = RunReason.INTERRUPTED)
            } else run
        }
        if (recovered != existing) write(recovered) else observed.value = recovered
    }

    @Synchronized
    fun start(id: UUID, action: ControllerAction) {
        val existing = read()
        val next = if (existing.size < MAX_RECORDS) existing else {
            val retired = existing.indexOfFirst { it.status != RunStatus.STARTED }
            if (retired < 0) return
            existing.filterIndexed { index, _ -> index != retired }
        }
        write(next + LifecycleRun(id, action, now(), RunStatus.STARTED))
    }

    @Synchronized
    fun finish(id: UUID, status: RunStatus, reason: RunReason) {
        require(status != RunStatus.STARTED)
        val runs = read()
        val index = runs.indexOfFirst { it.id == id && it.status == RunStatus.STARTED }
        if (index < 0) return
        val updated = runs.toMutableList()
        updated[index] = updated[index].copy(observedAt = now(), status = status, reason = reason)
        write(updated)
    }

    fun markUnavailable() { observed.value = null }

    private fun read(): List<LifecycleRun> {
        val bytes = try {
            file.openRead().use { StrictJson.readBytes(it, 32 * 1024) }
        } catch (error: FileNotFoundException) {
            if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) throw error
            return emptyList()
        }
        val text = StrictJson.text(bytes)
        StrictJson.check(text)
        val root = JSONObject(text)
        require(root.get("schema") == 1 && root.keys().asSequence().toSet() == setOf("schema", "runs"))
        val array = root.getJSONArray("runs")
        require(array.length() <= MAX_RECORDS)
        return (0 until array.length()).map { index ->
            val value = array.getJSONObject(index)
            require(value.keys().asSequence().toSet() == setOf("id", "action", "at", "status", "reason"))
            val id = value.get("id") as String
            require(Rules.validId(id))
            LifecycleRun(
                UUID.fromString(id), ControllerAction.valueOf(value.get("action") as String),
                Instant.parse(value.get("at") as String), RunStatus.valueOf(value.get("status") as String),
                RunReason.valueOf(value.get("reason") as String)
            )
        }.also { records -> require(records.map { it.id }.toSet().size == records.size) }
    }

    private fun write(runs: List<LifecycleRun>) {
        val array = JSONArray(runs.map { run ->
            JSONObject().put("id", run.id.toString()).put("action", run.action.name)
                .put("at", run.observedAt.toString()).put("status", run.status.name).put("reason", run.reason.name)
        })
        val bytes = JSONObject().put("schema", 1).put("runs", array).toString().toByteArray(Charsets.UTF_8)
        val output = file.startWrite()
        try {
            output.write(bytes)
            output.fd.sync()
        } catch (error: IOException) {
            file.failWrite(output)
            throw error
        }
        file.finishWrite(output)
        if (!file.readFully().contentEquals(bytes)) throw IOException("Operation history publication could not be verified.")
        observed.value = runs
    }

    private companion object { const val MAX_RECORDS = 48 }
}
