package com.cyclone.open.core

import java.util.UUID

const val HOST_ID = "host"
const val MAX_PHONES = 200
const val MAX_IMAGES = 100
const val MAX_IMAGE_BYTES = 16L * 1024 * 1024 * 1024

enum class RuntimeKind { HOST, LOCAL_VM, REMOTE_VM }
enum class RuntimeState { HOST_AVAILABLE, NOT_IMPLEMENTED }
data class ImageRecord(val id: String, val name: String, val androidVersion: Int, val uri: String, val sha256: String, val bytes: Long)
data class PhoneRecord(val id: String, val name: String, val runtime: RuntimeKind, val imageId: String, val memoryMiB: Int)
data class Registry(val images: List<ImageRecord> = emptyList(), val phones: List<PhoneRecord> = emptyList(), val selected: String = HOST_ID)

object Rules {
    fun label(raw: String): String = raw.trim().also { require(it.length in 1..64 && it.none { c -> c.isISOControl() }) { "Enter a name of 1–64 characters." } }
    fun validId(id: String) = runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)
    fun digest(raw: String) = raw.trim().lowercase().also { require(it.matches(Regex("[0-9a-f]{64}"))) { "Enter a 64-character SHA-256." } }
    fun version(v: Int) = v.also { require(it in 13..16) { "Select Android 13–16." } }
    fun validate(r: Registry): Registry {
        require(r.phones.size <= MAX_PHONES && r.images.size <= MAX_IMAGES) { "Registry limit exceeded." }
        require(r.images.map { it.id }.distinct().size == r.images.size && r.phones.map { it.id }.distinct().size == r.phones.size)
        r.images.forEach {
            require(validId(it.id) && label(it.name) == it.name && version(it.androidVersion) == it.androidVersion)
            require(it.uri.startsWith("content://") && digest(it.sha256) == it.sha256 && it.bytes in 1..MAX_IMAGE_BYTES)
        }
        r.phones.forEach {
            require(validId(it.id) && label(it.name) == it.name && it.runtime != RuntimeKind.HOST)
            require(it.memoryMiB in listOf(2048, 3072, 4096) && r.images.any { image -> image.id == it.imageId })
        }
        require(r.selected == HOST_ID || r.phones.any { it.id == r.selected })
        return r
    }
    fun addImage(r: Registry, image: ImageRecord): Registry {
        require(r.images.none { it.uri == image.uri }) { "This document is already in the library." }
        return validate(r.copy(images = r.images + image))
    }
    fun addPhone(r: Registry, name: String, imageId: String, runtime: RuntimeKind, memory: Int): Registry {
        val phone = PhoneRecord(UUID.randomUUID().toString(), label(name), runtime, imageId, memory)
        return validate(r.copy(phones = r.phones + phone, selected = phone.id))
    }
    fun select(r: Registry, id: String): Registry = validate(r.copy(selected = id))
    fun rename(r: Registry, id: String, name: String): Registry {
        require(r.phones.any { it.id == id })
        return validate(r.copy(phones = r.phones.map { if (it.id == id) it.copy(name = label(name)) else it }))
    }
    fun remove(r: Registry, id: String): Registry {
        require(r.phones.any { it.id == id })
        return validate(r.copy(phones = r.phones.filterNot { it.id == id }, selected = if (r.selected == id) HOST_ID else r.selected))
    }
    fun removeImage(r: Registry, id: String): Registry {
        require(r.images.any { it.id == id })
        require(r.phones.none { it.imageId == id }) { "Remove the phone drafts using this image first." }
        return validate(r.copy(images = r.images.filterNot { it.id == id }))
    }
}

data class RuntimeStatus(val state: RuntimeState, val detail: String)
interface PhoneRuntime { fun status(): RuntimeStatus }
object HostRuntime : PhoneRuntime {
    override fun status() = RuntimeStatus(RuntimeState.HOST_AVAILABLE, "Current Android user")
}
class UnavailableRuntime(private val kind: RuntimeKind) : PhoneRuntime {
    override fun status() = RuntimeStatus(RuntimeState.NOT_IMPLEMENTED, when (kind) {
        RuntimeKind.LOCAL_VM -> "Local Android guest backend not implemented. Pixel AVF and graphics require verification."
        RuntimeKind.REMOTE_VM -> "Remote runtime not connected. No server configured."
        RuntimeKind.HOST -> error("Use HostRuntime")
    })
}
