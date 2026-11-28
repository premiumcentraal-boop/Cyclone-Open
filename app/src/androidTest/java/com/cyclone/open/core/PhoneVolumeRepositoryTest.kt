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
    private lateinit var catalog: ReferenceCatalog
    private lateinit var imageId: String

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        catalog = ReferenceCatalogReader.load(context)
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
        val saved = create(repository, "Saved")
        val bytes = registryFile.readBytes()
        val fault = ProfileStorage(File(root, "data"), File(root, "cache")) {
            throw ErrnoException("mkdir", OsConstants.ENOSPC)
        }
        assertThrows(InsufficientProfileStorageException::class.java) {
            create(PhoneVolumeRepository(store, fault, journal), "Retry")
        }
        assertEquals(saved, repository.open())
        assertArrayEquals(bytes, registryFile.readBytes())
        assertTrue(journal.pending().isEmpty())
        val retried = create(repository, "Retry")
        assertEquals(2, retried.phones.size)
        assertTrue(retried.phones.all { storage.isPrepared(UUID.fromString(it.id)) })
    }

    @Test
    fun startupPreservesPublishedDataAndDiscardsOnlyEmptyPendingVolumes() {
        val repository = PhoneVolumeRepository(store, storage, journal)
        val saved = create(repository, "Saved")
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

    @Test
    fun referenceAndDraftPublishTogetherAndStaleReviewCreatesNothing() {
        val repository = PhoneVolumeRepository(store, storage, journal)
        val request = request("Reviewed draft")
        val review = repository.reviewDraft(catalog, request)
        store.change { registry -> registry.copy(images = registry.images.map { it.copy(sha256 = "b".repeat(64)) }) }
        val before = registryFile.readBytes()
        assertThrows(DraftReviewException::class.java) { repository.publishDraft(catalog, review) }
        assertArrayEquals(before, registryFile.readBytes())
        assertFalse(File(root, "data").exists())
        assertTrue(journal.pending().isEmpty())
        val freshApproval = repository.reviewDraft(catalog, request)
        val updated = repository.publishDraft(catalog, freshApproval)
        assertEquals(request.reference, updated.phones.single().reference)
        assertTrue(updated.phones.single().volumePrepared)
        assertEquals(freshApproval.instanceId, updated.phones.single().id)
        assertEquals(updated, repository.publishDraft(catalog, freshApproval))
        assertEquals(1, File(root, "data").listFiles()?.size)
        assertEquals(updated, RegistryStore(registryFile).read())
        store.change { Rules.remove(it, freshApproval.instanceId) }
        assertThrows(DraftReviewException::class.java) { repository.publishDraft(catalog, freshApproval) }
        assertTrue(store.read().phones.isEmpty())
        assertEquals(1, File(root, "data").listFiles()?.size)
    }

    private fun request(name: String) = DraftRequest(name, catalog.references.first().key, imageId, RuntimeKind.LOCAL_VM, 3072)

    private fun create(repository: PhoneVolumeRepository, name: String) =
        repository.publishDraft(catalog, repository.reviewDraft(catalog, request(name)))
}
