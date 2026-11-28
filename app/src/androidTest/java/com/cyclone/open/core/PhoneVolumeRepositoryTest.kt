/*
 * Adapted from premiumcentraal-boop/cyclone-cloak-forge @ 5113af4a84a6607b067b08566431c304c74f1776
 * app/src/androidTest/java/com/cyclonecloak/app/ProfileStorageTest.kt
 * app/src/androidTest/java/com/cyclonecloak/app/StartupRecoveryTest.kt
 * Owner-authorized reuse. The source revision does not include a license file.
 * Exercises registry publication, legacy drafts and recovery with a private volume journal.
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
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PhoneVolumeRepositoryTest {
    private lateinit var root: File
    private lateinit var registryFile: File
    private lateinit var store: RegistryStore
    private lateinit var storage: ProfileStorage
    private lateinit var journal: PhoneVolumeJournal
    private lateinit var imageId: String

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(context.noBackupFilesDir, "volume-test-${UUID.randomUUID()}")
        check(root.mkdir())
        registryFile = File(root, "registry-v1.json")
        store = RegistryStore(registryFile)
        storage = ProfileStorage(File(root, "data"), File(root, "cache"))
        journal = PhoneVolumeJournal(File(root, "pending"))
        imageId = UUID.randomUUID().toString()
        store.change {
            Rules.addImage(it, ImageRecord(imageId, "Image", 15, "content://test/$imageId", "a".repeat(64), 1))
        }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun legacyDraftsAreProvisionedOnceWithoutChangingSelection() {
        val before = store.change { Rules.addPhone(it, "Legacy", imageId, RuntimeKind.LOCAL_VM, 3072) }
        val repository = PhoneVolumeRepository(store, storage, journal)
        val migrated = repository.open()
        assertEquals(before.selected, migrated.selected)
        assertTrue(migrated.phones.single().volumePrepared)
        val id = UUID.fromString(migrated.selected)
        storage.write(id, ProfileStorage.Area.FILES, "keep", byteArrayOf(2))
        assertEquals(migrated, repository.open())
        assertEquals(migrated, RegistryStore(registryFile).read())
        assertArrayEquals(byteArrayOf(2), storage.read(id, ProfileStorage.Area.FILES, "keep"))
    }

    @Test
    fun preparationFailurePreservesExistingSelectionAndRetryPublishesOneVolume() {
        val repository = PhoneVolumeRepository(store, storage, journal)
        val saved = repository.create("Saved", imageId, RuntimeKind.LOCAL_VM, 3072)
        val bytes = registryFile.readBytes()
        val fault = ProfileStorage(File(root, "data"), File(root, "cache")) {
            throw ErrnoException("mkdir", OsConstants.ENOSPC)
        }
        assertThrows(InsufficientProfileStorageException::class.java) {
            PhoneVolumeRepository(store, fault, journal).create("Retry", imageId, RuntimeKind.LOCAL_VM, 3072)
        }
        assertEquals(saved, repository.open())
        assertArrayEquals(bytes, registryFile.readBytes())
        assertTrue(journal.pending().isEmpty())
        val retried = repository.create("Retry", imageId, RuntimeKind.LOCAL_VM, 3072)
        assertEquals(2, retried.phones.size)
        assertTrue(retried.phones.all { storage.isPrepared(UUID.fromString(it.id)) })
    }

    @Test
    fun startupPreservesPublishedDataAndDiscardsOnlyEmptyPendingVolumes() {
        val repository = PhoneVolumeRepository(store, storage, journal)
        val saved = repository.create("Saved", imageId, RuntimeKind.LOCAL_VM, 3072)
        val committed = UUID.fromString(saved.selected)
        val pending = UUID.randomUUID()
        journal.begin(committed)
        journal.begin(pending)
        storage.prepare(pending)
        storage.write(committed, ProfileStorage.Area.FILES, "keep", byteArrayOf(5))

        val reopened = PhoneVolumeRepository(RegistryStore(registryFile), storage, journal)
        assertEquals(saved, reopened.open())
        assertFalse(storage.isPrepared(pending))
        assertArrayEquals(byteArrayOf(5), storage.read(committed, ProfileStorage.Area.FILES, "keep"))
        assertTrue(journal.pending().isEmpty())
    }
}
