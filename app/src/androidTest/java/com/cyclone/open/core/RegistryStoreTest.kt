package com.cyclone.open.core

import android.util.AtomicFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class RegistryStoreTest {
    private lateinit var directory: File
    private lateinit var base: File
    private lateinit var store: RegistryStore

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(context.noBackupFilesDir, "registry-test-${UUID.randomUUID()}")
        check(directory.mkdir())
        base = File(directory, "registry-v1.json")
        store = RegistryStore(base)
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun phoneDraftLifecyclePersistsAcrossStoreReopen() {
        val image = image()
        store.change { Rules.addImage(it, image) }
        val created = store.change { Rules.addPhone(it, "Phone", image.id, RuntimeKind.LOCAL_VM, 3072) }
        val phoneId = created.phones.single().id
        assertEquals(phoneId, created.selected)
        val reopened = RegistryStore(base)
        assertEquals(created, reopened.read())

        val renamed = reopened.change { Rules.rename(it, phoneId, "Renamed") }
        assertEquals("Renamed", renamed.phones.single().name)
        assertEquals(phoneId, renamed.phones.single().id)
        val selected = reopened.change { Rules.select(it, HOST_ID) }
        assertEquals(HOST_ID, selected.selected)
        assertEquals(selected, RegistryStore(base).read())

        reopened.change { Rules.select(it, phoneId) }
        val removed = reopened.change { Rules.remove(it, phoneId) }
        assertEquals(Registry(images = listOf(image)), removed)
        assertEquals(removed, RegistryStore(base).read())
    }

    @Test
    fun corruptAndUnsupportedRegistriesAreNeverOverwritten() {
        val badRegistries = listOf(
            "{\"schema\":1,",
            """{"schema":2,"selected":"host","images":[],"phones":[]}""",
            """{"schema":1.5,"selected":"host","images":[],"phones":[]}""",
            """{"schema":1,"selected":"missing","images":[],"phones":[]}"""
        )
        for (registry in badRegistries) {
            val bytes = registry.toByteArray()
            base.writeBytes(bytes)
            assertThrows(RegistryReadException::class.java) { store.read() }
            assertThrows(RegistryReadException::class.java) {
                store.change { throw AssertionError("Mutation ran against an unreadable registry.") }
            }
            assertArrayEquals(bytes, base.readBytes())
        }
    }

    @Test
    fun interruptedWriteRetainsLastCommit() {
        val before = store.change { Rules.addImage(it, image()) }
        interruptWrite()
        val reopened = RegistryStore(base)
        assertEquals(before, reopened.read())
        val after = reopened.change { Rules.addImage(it, image()) }
        assertEquals(after, RegistryStore(base).read())
        assertEquals(2, after.images.size)
    }

    @Test
    fun interruptedFirstWriteDoesNotPublishPartialData() {
        interruptWrite()
        val reopened = RegistryStore(base)
        assertEquals(Registry(), reopened.read())
        val after = reopened.change { Rules.addImage(it, image()) }
        assertEquals(after, RegistryStore(base).read())
    }

    @Test
    fun legacyAtomicFileBackupIsRecovered() {
        val before = store.change { Rules.addImage(it, image()) }
        check(base.renameTo(File(base.path + ".bak")))
        base.writeText("partial legacy write")
        assertEquals(before, RegistryStore(base).read())
    }

    @Test
    fun failedWriteReturnsErrorAndPreservesExistingRegistry() {
        val before = store.change { Rules.addImage(it, image()) }
        val bytes = base.readBytes()
        val pending = File(base.path + ".new")
        check(pending.mkdir())
        File(pending, "blocked").writeText("test fixture")

        assertThrows(RegistryWriteException::class.java) {
            store.change { Rules.addImage(it, image()) }
        }
        assertArrayEquals(bytes, base.readBytes())
        check(pending.deleteRecursively())
        assertEquals(before, RegistryStore(base).read())
    }

    @Test
    fun concurrentChangesSurviveReopen() {
        val images = List(20) { image() }
        val start = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(4)
        try {
            val results = images.map { image ->
                workers.submit {
                    check(start.await(5, TimeUnit.SECONDS))
                    store.change { Rules.addImage(it, image) }
                }
            }
            start.countDown()
            results.forEach { it.get(15, TimeUnit.SECONDS) }
            assertEquals(images.toSet(), RegistryStore(base).read().images.toSet())
        } finally {
            workers.shutdownNow()
        }
    }

    private fun interruptWrite() {
        AtomicFile(base).startWrite().use { out ->
            out.write("{\"schema\":1,".toByteArray())
            out.fd.sync()
        }
    }

    private fun image(): ImageRecord {
        val id = UUID.randomUUID().toString()
        return ImageRecord(id, "Image", 15, "content://registry.test/$id", "a".repeat(64), 1L)
    }
}
