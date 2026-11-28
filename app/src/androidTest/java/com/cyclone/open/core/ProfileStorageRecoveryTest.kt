/*
 * Adapted from premiumcentraal-boop/cyclone-cloak-forge @ 5113af4a84a6607b067b08566431c304c74f1776
 * app/src/androidTest/java/com/cyclonecloak/app/StartupRecoveryTest.kt and ProfileStorageTest.kt
 * Owner-authorized reuse. The source revision does not include a license file.
 * Adapted to the Android AtomicFile registry, errno injection and private profile roots.
 */
package com.cyclone.open.core

import android.system.ErrnoException
import android.system.OsConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ProfileStorageRecoveryTest {
    private lateinit var directory: File
    private lateinit var data: File
    private lateinit var cache: File

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(context.noBackupFilesDir, "profile-test-${UUID.randomUUID()}")
        check(directory.mkdir())
        data = File(directory, "data")
        cache = File(directory, "cache")
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun recoversEmptyIncompleteAreaAndPreservesUnexpectedContents() {
        val pending = UUID.randomUUID()
        val interrupted = ProfileStorage(data, cache) { path ->
            check(path.mkdir())
            throw ErrnoException("mkdir", OsConstants.EIO)
        }
        assertThrows(IOException::class.java) { interrupted.prepare(pending) }
        val reopened = ProfileStorage(data, cache)
        reopened.discardIncomplete(pending)
        reopened.discardIncomplete(pending)
        assertFalse(reopened.isPrepared(pending))

        val retained = UUID.randomUUID()
        reopened.prepare(retained)
        reopened.write(retained, ProfileStorage.Area.FILES, "owner-data", byteArrayOf(3))
        assertThrows(IOException::class.java) { reopened.discardIncomplete(retained) }
        assertArrayEquals(byteArrayOf(3), reopened.read(retained, ProfileStorage.Area.FILES, "owner-data"))
    }

    @Test
    fun areasAndProfilesKeepIndependentBytes() {
        val store = ProfileStorage(data, cache)
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        store.prepare(first)
        store.prepare(second)
        for (area in ProfileStorage.Area.entries) {
            store.write(first, area, "settings", byteArrayOf(area.ordinal.toByte()))
        }
        val reopened = ProfileStorage(data, cache)
        for (area in ProfileStorage.Area.entries) {
            assertArrayEquals(byteArrayOf(area.ordinal.toByte()), reopened.read(first, area, "settings"))
            assertNull(reopened.read(second, area, "settings"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            reopened.write(second, ProfileStorage.Area.FILES, "../settings", byteArrayOf(1))
        }
    }

    @Test
    fun partialRegistryWriteOnDiskFullPreservesCommittedBytesAndDisplaysActionableError() {
        val file = File(directory, "registry-v1.json")
        val original = RegistryStore(file)
        val imageId = UUID.randomUUID().toString()
        original.change {
            Rules.addImage(it, ImageRecord(imageId, "Image", 15, "content://test/$imageId", "a".repeat(64), 1))
        }
        val before = original.change { Rules.addPhone(it, "Saved", imageId, RuntimeKind.LOCAL_VM, 3072) }
        val bytes = file.readBytes()
        for (errno in listOf(OsConstants.ENOSPC, OsConstants.EDQUOT)) {
            val fault = RegistryStore(file) { output, staged ->
                output.write(staged, 0, staged.size / 2)
                throw IOException(ErrnoException("write", errno))
            }
            val error = assertThrows(InsufficientProfileStorageException::class.java) {
                fault.change { Rules.rename(it, before.selected, "Retry") }
            }
            assertEquals("Not enough storage to save the change. Free up space and try again.", error.message)
            assertArrayEquals(bytes, file.readBytes())
            assertFalse(File(file.path + ".new").exists())
            assertEquals(before, RegistryStore(file).read())
        }
        val retried = original.change { Rules.rename(it, before.selected, "Retry") }
        assertEquals(retried, RegistryStore(file).read())
    }
}
