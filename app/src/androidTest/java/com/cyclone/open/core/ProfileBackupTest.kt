package com.cyclone.open.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ProfileBackupTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val image = ImageRecord(id(), "Image", 15, "content://fixture/private", "a".repeat(64), 4)

    @Test
    fun parserRejectsDuplicatesLenientGrammarAndOversizedInput() {
        for (value in listOf("{\"id\":1,\"\\u0069d\":2}", "{'id':1}", "[01]", "[1,]", "{}{}", "[/* comment */1]")) {
            assertThrows(IllegalArgumentException::class.java) { StrictJson.check(value) }
        }
        StrictJson.check("{\"value\":[true,false,null,-2.4e+4]}")
        assertThrows(IllegalArgumentException::class.java) { StrictJson.readBytes("abcd".byteInputStream(), 3) }
    }

    @Test
    fun backupRestoresOnlySettingsIntoANewReviewedDraft() = fixture { root ->
        val store = RegistryStore(File(root, "registry.json"))
        val catalog = ReferenceCatalogReader.load(context)
        val key = catalog.references.first().key
        val first = store.change {
            val seeded = Rules.addImage(it, image)
            Rules.addPhone(seeded, "Original", image.id, RuntimeKind.LOCAL_VM, 3072, key)
        }
        val backups = ProfileBackups(JsonDirectory(File(root, "backups")))
        val snapshot = ProfileBackup.capture(first, first.selected)
        val encoded = snapshot.encode().toString()
        assertFalse(encoded.contains(image.uri))
        assertFalse(encoded.contains(first.selected))
        assertThrows(IllegalArgumentException::class.java) { ProfileBackup.decode(JSONObject(encoded).put("extra", "data")) }
        backups.save(snapshot)
        val (backupId, saved) = ProfileBackups(JsonDirectory(File(root, "backups"))).list().single()
        assertEquals(snapshot, saved)
        assertThrows(DraftReviewException::class.java) { saved.request(Registry(), key) }
        assertThrows(DraftReviewException::class.java) {
            saved.request(Registry(images = listOf(image, image.copy(id = id()))), key)
        }
        val volumes = PhoneVolumeRepository(store, ProfileStorage(File(root, "data"), File(root, "cache")), PhoneVolumeJournal(File(root, "pending")))
        val approved = volumes.reviewDraft(catalog, saved.request(store.read(), key))
        val restored = volumes.publishDraft(catalog, approved)
        assertEquals(2, restored.phones.size)
        assertTrue(restored.selected != first.selected && restored.selected == approved.instanceId)
        assertTrue(File(File(root, "data"), restored.selected).listFiles()?.isEmpty() == true)
        assertTrue(backups.delete(backupId))
        assertEquals(restored, store.read())
    }

    @Test
    fun directorySwapRollsBackAndRecoversAnInterruptedBatch() = fixture { root ->
        val directory = File(root, "backups")
        val before = id()
        JsonDirectory(directory).writeBatch(listOf(before to JSONObject().put("v", 1)))
        val fault = JsonDirectory(directory) { source, target ->
            if (source.name.endsWith(".pending")) throw IOException("Injected publication failure")
            Files.move(source.toPath(), target.toPath(), ATOMIC_MOVE)
        }
        assertThrows(IOException::class.java) { fault.writeBatch(listOf(id() to JSONObject().put("v", 2))) }
        assertEquals(listOf(before), JsonDirectory(directory).all().map { it.first })
        check(directory.renameTo(File(root, ".backups.previous")))
        check(File(root, ".backups.pending").mkdir())
        assertEquals(listOf(before), JsonDirectory(directory).all().map { it.first })
        assertFalse(File(root, ".backups.pending").exists())
    }

    private fun fixture(test: (File) -> Unit) {
        val dir = File(context.noBackupFilesDir, "backup-test-${id()}")
        check(dir.mkdir())
        try { test(dir) } finally { dir.deleteRecursively() }
    }

    private fun id() = UUID.randomUUID().toString()
}
