package com.cyclone.open.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AtomicSwitchJournalTest {
    @Test
    fun startupInvalidatesInterruptedLeaseAndRetainsPreviousTarget() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = File(context.noBackupFilesDir, "switch-test-${UUID.randomUUID()}")
        check(fixture.mkdir())
        try {
            val path = File(fixture, "switch.json")
            val guest = PhoneTarget(UUID.randomUUID().toString(), RuntimeKind.LOCAL_VM)
            val before = GenerationLease(PhoneTarget.Host, 5)
            val pending = GenerationLease(guest, 6)
            AtomicSwitchJournal(path).write(SwitchState(6, before, SwitchOperation(pending, SwitchStage.PREPARE)))
            val controller = ProfileSwitchCoordinator(AtomicSwitchJournal(path))
            assertFalse(controller.accepts(before))
            assertFalse(controller.accepts(pending))
            val recovered = controller.recover()
            assertEquals(6L, recovered.generation)
            assertEquals(before, recovered.active)
            assertNull(recovered.pending)
            assertEquals(SwitchFailure.INTERRUPTED, recovered.lastFailure)
            assertEquals(recovered, AtomicSwitchJournal(path).read())
            assertTrue(controller.accepts(before))
            assertFalse(controller.accepts(pending))

            path.writeText("{\"schema\":1,")
            assertThrows(IOException::class.java) { AtomicSwitchJournal(path).read() }
            assertEquals("{\"schema\":1,", path.readText())
        } finally {
            fixture.deleteRecursively()
        }
    }
}
