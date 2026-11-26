package com.cyclone.open.core

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class RegistryTest {
    private fun image() = ImageRecord(UUID.randomUUID().toString(), "AOSP", 15, "content://test/image", "a".repeat(64), 5)
    private fun library() = Rules.addImage(Registry(), image())
    private fun phone(r: Registry = library()) = Rules.addPhone(r, "Work", r.images.first().id, RuntimeKind.LOCAL_VM, 3072)
    @Test fun selectionSurvivesRename() {
        val r = phone()
        val next = Rules.rename(r, r.selected, "Personal")
        assertEquals(r.selected, next.selected)
        assertEquals("Personal", next.phones.single().name)
    }
    @Test fun selectedRemovalReturnsHost() { val r = phone(); assertEquals(HOST_ID, Rules.remove(r, r.selected).selected) }
    @Test fun separateRecords() { val r = phone(); val n = phone(r); assertEquals(2,n.phones.map { it.id }.distinct().size) }
    @Test(expected = IllegalArgumentException::class) fun orphanImageRejected() { val r = phone(); Rules.removeImage(r, r.images.single().id) }
    @Test(expected = IllegalArgumentException::class) fun missingTargetRejected() { Rules.select(Registry(), "missing") }
    @Test(expected = IllegalArgumentException::class) fun controlCharactersRejected() { Rules.label("one\ntwo") }
    @Test(expected = IllegalArgumentException::class) fun badDigestRejected() { Rules.digest("123") }
    @Test(expected = IllegalArgumentException::class) fun oldVersionRejected() { Rules.version(12) }
    @Test(expected = IllegalArgumentException::class) fun unsupportedMemoryRejected() { val r=library(); Rules.addPhone(r,"Work",r.images.single().id,RuntimeKind.LOCAL_VM,8192) }
    @Test(expected = IllegalArgumentException::class) fun fakeHostRejected() { val r=library(); Rules.addPhone(r,"Work",r.images.single().id,RuntimeKind.HOST,3072) }
    @Test(expected = IllegalArgumentException::class) fun duplicateDocumentRejected() { val r=library(); Rules.addImage(r,r.images.first().copy(id=UUID.randomUUID().toString())) }
    @Test fun guestStatusNeverClaimsRunning() {
        assertEquals(RuntimeState.NOT_IMPLEMENTED,UnavailableRuntime(RuntimeKind.LOCAL_VM).status().state)
        assertEquals(RuntimeState.NOT_IMPLEMENTED,UnavailableRuntime(RuntimeKind.REMOTE_VM).status().state)
    }
}
