/*
 * Adapted from premiumcentraal-boop/cyclone-cloak-forge @ 5113af4a84a6607b067b08566431c304c74f1776
 * app/src/androidTest/java/com/cyclonecloak/app/ProfileActivationRestartTest.kt
 * app/src/androidTest/java/com/cyclonecloak/app/ProfileCreationRestartTest.kt
 * app/src/androidTest/java/com/cyclonecloak/app/ProfileDeletionRestartTest.kt
 * Owner-authorized reuse. The source revision does not include a license file.
 * Adapted to two isolated instrumentation invocations and an explicit ADB force-stop.
 */
package com.cyclone.open.core

import android.os.Process
import android.util.AtomicFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@Retention(AnnotationRetention.RUNTIME)
annotation class ProcessRestartCase

@RunWith(AndroidJUnit4::class)
@ProcessRestartCase
class ProfileRestartTest {
    private val fixture: File
        get() {
            val run = InstrumentationRegistry.getArguments().getString("restartRun")
            require(run != null && run.matches(Regex("[A-Za-z0-9-]{1,80}")))
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            return File(context.noBackupFilesDir, "restart-test-$run")
        }

    @Test
    fun beforeForceStop() {
        val directory = fixture
        check(directory.mkdir())
        val file = File(directory, "registry-v1.json")
        val store = RegistryStore(file)
        val imageId = UUID.randomUUID().toString()
        store.change {
            Rules.addImage(it, ImageRecord(imageId, "Image", 15, "content://test/$imageId", "a".repeat(64), 1))
        }
        val keep = store.change { Rules.addPhone(it, "Keep", imageId, RuntimeKind.LOCAL_VM, 3072) }.selected
        val remove = store.change { Rules.addPhone(it, "Remove", imageId, RuntimeKind.REMOTE_VM, 2048) }.selected
        store.change { Rules.rename(Rules.select(Rules.remove(it, remove), keep), keep, "Renamed") }
        val storage = ProfileStorage(File(directory, "data"), File(directory, "cache"))
        val journal = PhoneVolumeJournal(File(directory, "pending"))
        PhoneVolumeRepository(store, storage, journal).open()
        storage.write(UUID.fromString(keep), ProfileStorage.Area.PREFERENCES, "owner-data", byteArrayOf(4))
        // Leave one published and one unregistered volume pending when the process ends.
        val incomplete = UUID.randomUUID()
        journal.begin(UUID.fromString(keep))
        journal.begin(incomplete)
        storage.prepare(incomplete)
        AtomicFile(file).startWrite().use { staged ->
            staged.write("{\"schema\":1,".toByteArray())
            staged.fd.sync()
        }
        File(directory, "phase").writeText("${Process.myPid()}\n$keep\n$incomplete")
    }

    @Test
    fun afterForceStop() {
        val directory = fixture
        try {
            val (previousPid, keep, incomplete) = File(directory, "phase").readLines()
            assertNotEquals(previousPid.toInt(), Process.myPid())
            val store = RegistryStore(File(directory, "registry-v1.json"))
            val storage = ProfileStorage(File(directory, "data"), File(directory, "cache"))
            val journal = PhoneVolumeJournal(File(directory, "pending"))
            val registry = PhoneVolumeRepository(store, storage, journal).open()
            assertEquals(keep, registry.selected)
            assertEquals(keep, registry.phones.single().id)
            assertEquals("Renamed", registry.phones.single().name)
            assertArrayEquals(byteArrayOf(4), storage.read(UUID.fromString(keep), ProfileStorage.Area.PREFERENCES, "owner-data"))
            assertEquals(false, storage.isPrepared(UUID.fromString(incomplete)))
            assertEquals(emptyList<UUID>(), journal.pending())
            println("Registry reopen verified across processes $previousPid and ${Process.myPid()}.")
        } finally {
            directory.deleteRecursively()
        }
    }
}
