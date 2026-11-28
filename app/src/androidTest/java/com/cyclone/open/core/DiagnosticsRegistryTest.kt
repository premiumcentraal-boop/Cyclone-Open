package com.cyclone.open.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DiagnosticsRegistryTest {
    @Test
    fun restartClosesOnlyUnfinishedRunsAndKeepsTypedFailureWithoutInput() = fixture { file ->
        val before = DiagnosticsRegistry(file)
        val pending = UUID.randomUUID()
        before.start(pending, ControllerAction.CREATE_DRAFT)
        val current = DiagnosticsRegistry(file) { Instant.parse("2026-11-28T12:00:00Z") }
        val bus = LogBus(current)
        bus.startProcess()
        runBlocking {
            runCatching { bus.track(ControllerAction.SAVE_BACKUP) { throw IOException("private filename and content") } }
        }
        val history = current.history.value.orEmpty()
        assertEquals(listOf(RunStatus.INTERRUPTED, RunStatus.FAILED), history.map { it.status })
        assertEquals(listOf(RunReason.INTERRUPTED, RunReason.IO_FAILURE), history.map { it.reason })
        assertEquals(pending, history.first().id)
        assertFalse(file.readText().contains("private filename"))
    }

    @Test
    fun historyIsBoundedAndCorruptStateDoesNotBlockTheOperation() = fixture { file ->
        val registry = DiagnosticsRegistry(file)
        for (index in 1..53) {
            val id = UUID.randomUUID()
            registry.start(id, ControllerAction.SAVE_BACKUP)
            registry.finish(id, RunStatus.SUCCEEDED, RunReason.NONE)
        }
        assertEquals(48, registry.history.value?.size)
        file.writeText("{\"schema\":1,\"runs\":")
        val reopened = LogBus(DiagnosticsRegistry(file))
        val result = runBlocking { reopened.track(ControllerAction.CREATE_DRAFT) { 4 } }
        assertEquals(4, result)
        assertNull(reopened.history.value)
        assertEquals("{\"schema\":1,\"runs\":", file.readText())
    }

    private fun fixture(body: (File) -> Unit) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.noBackupFilesDir, "runs-${UUID.randomUUID()}")
        check(dir.mkdir())
        try { body(File(dir, "runs.json")) } finally { dir.deleteRecursively() }
    }
}
