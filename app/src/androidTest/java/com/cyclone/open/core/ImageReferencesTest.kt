package com.cyclone.open.core

import android.net.Uri
import android.system.ErrnoException
import android.system.OsConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ImageReferencesTest {
    private lateinit var directory: File
    private lateinit var store: RegistryStore
    private val originalUri = Uri.parse("content://test/original")
    private val original = ImageDigest("a".repeat(64), 4)

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(context.noBackupFilesDir, "image-reference-test-${UUID.randomUUID()}")
        check(directory.mkdir())
        store = RegistryStore(File(directory, "registry-v1.json"))
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun recoveryAndRemovalReleaseOnlyUnusedGrants() {
        val orphaned = Uri.parse("content://test/orphaned")
        val recordA = record("A")
        val recordB = record("B")
        // Earlier saved data can contain multiple references to the same document.
        store.change { it.copy(images = listOf(recordA, recordB)) }
        val permissions = FakePermissions(originalUri, orphaned)
        val references = references(permissions)
        references.reconcile()
        assertEquals(setOf(originalUri), permissions.held())
        val draft = store.change { Rules.addPhone(it, "Draft", recordA.id, RuntimeKind.LOCAL_VM, 3072) }
        assertTrue(runCatching { references.remove(recordA.id) }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(setOf(originalUri), permissions.held())
        store.change { Rules.remove(it, draft.selected) }
        references.remove(recordA.id)
        assertEquals(setOf(originalUri), permissions.held())
        references.remove(recordB.id)
        assertTrue(permissions.held().isEmpty())
        assertTrue(store.committed.value!!.images.isEmpty())
    }

    @Test
    fun failedImportPublicationRollsBackAcquiredGrantAndKeepsRegistry() = runBlocking {
        val permissions = FakePermissions()
        val fault = RegistryStore(File(directory, "registry-v1.json")) { output, bytes ->
            output.write(bytes, 0, bytes.size / 2)
            throw IOException(ErrnoException("write", OsConstants.ENOSPC))
        }
        val references = ImageReferences(fault, permissions) { _, _ -> original }
        val error = runCatching { references.add(originalUri, "Original", 15, original.hash) }.exceptionOrNull()
        assertTrue(error is InsufficientProfileStorageException)
        assertTrue(permissions.held().isEmpty())
        assertEquals(Registry(), store.read())
    }

    @Test
    fun changedAndRevokedDocumentsStayUnverifiedUntilOriginalAccessIsRestored() = runBlocking {
        val saved = record("Original")
        store.change { Rules.addImage(it, saved) }
        val permissions = FakePermissions()
        var source = original
        val references = ImageReferences(store, permissions) { _, _ -> source }
        assertTrue(runCatching { references.recheck(saved.id) }.exceptionOrNull() is ImageReferenceException)
        assertTrue(runCatching {
            references.restoreAccess(saved.id, Uri.parse("content://test/other"))
        }.exceptionOrNull() is ImageReferenceException)
        assertTrue(permissions.held().isEmpty())
        references.restoreAccess(saved.id, originalUri)
        references.recheck(saved.id)
        source = ImageDigest("b".repeat(64), 4)
        assertTrue(runCatching { references.recheck(saved.id) }.exceptionOrNull() is ImageReferenceException)
        assertEquals(saved, store.read().images.single())
        assertFalse(permissions.held().isEmpty())
    }

    @Test
    fun draftDoctorReadsOnlyTheSelectedPrivateDirectoryAndGrant() {
        val saved = record("Original")
        val first = Rules.addPhone(Registry(images = listOf(saved)), "First", saved.id, RuntimeKind.LOCAL_VM, 3072)
        val second = Rules.addPhone(first, "Second", saved.id, RuntimeKind.REMOTE_VM, 3072)
        val dataRoot = File(directory, "profiles")
        val storage = ProfileStorage(dataRoot, File(directory, "cache"))
        val permissions = FakePermissions(originalUri)
        val images = ImageReferences(store, permissions) { _, _ -> error("Draft checks must not read image bytes.") }

        assertNull(RuntimeDoctor.inspectDraft(Rules.select(second, HOST_ID), storage, images))
        assertEquals(DraftHealth(second.selected, DraftVolumeStatus.MISSING, DraftGrantStatus.HELD),
            RuntimeDoctor.inspectDraft(second, storage, images))
        assertFalse(dataRoot.exists())

        storage.prepare(UUID.fromString(first.selected))
        permissions.release(originalUri)
        assertEquals(DraftHealth(first.selected, DraftVolumeStatus.PRESENT, DraftGrantStatus.MISSING),
            RuntimeDoctor.inspectDraft(Rules.select(second, first.selected), storage, images))
        assertEquals(DraftHealth(second.selected, DraftVolumeStatus.MISSING, DraftGrantStatus.MISSING),
            RuntimeDoctor.inspectDraft(second, storage, images))
    }

    private fun record(label: String) = ImageRecord(UUID.randomUUID().toString(), label, 15, originalUri.toString(), original.hash, original.bytes)
    private fun references(permissions: ImageReadPermissions) = ImageReferences(store, permissions) { _, _ -> original }

    private class FakePermissions(vararg held: Uri) : ImageReadPermissions {
        private val grants = held.toMutableSet()
        override fun held() = grants.toSet()
        override fun take(uri: Uri) { grants.add(uri) }
        override fun release(uri: Uri) { grants.remove(uri) }
    }
}
