/*
 * Adapted from premiumcentraal-boop/Cyclone-Cloak @ 62a05e4a966119908125016211096983cb258529
 * android/app/src/main/java/dev/cyclone/cloak/forge/ProfileBackup.kt
 * MIT, Copyright (c) 2026 Cyclone Cloak contributors. See docs/licenses/Cyclone-Cloak-MIT.txt.
 * Projects only draft settings and rebinds a verified image reference before transactional restore.
 */
package com.cyclone.open.core

import org.json.JSONObject
import java.util.UUID

internal data class ProfileBackup(
    val name: String,
    val backend: RuntimeKind,
    val memoryMiB: Int,
    val reference: ReferenceKey?,
    val imageDigest: String,
    val imageBytes: Long,
    val androidVersion: Int
) {
    init {
        require(Rules.label(name) == name && backend != RuntimeKind.HOST && memoryMiB in draftMemoryOptions)
        require(Rules.digest(imageDigest) == imageDigest && imageBytes in 1..MAX_IMAGE_BYTES)
        Rules.version(androidVersion)
    }

    fun request(registry: Registry, reference: ReferenceKey): DraftRequest {
        val matching = registry.images.filter { image ->
            image.sha256 == imageDigest && image.bytes == imageBytes && image.androidVersion == androidVersion
        }
        val image = matching.singleOrNull() ?: throw DraftReviewException(if (matching.isEmpty()) {
            "Import the matching Android image reference before restoring this backup."
        } else {
            "More than one image reference matches. Keep one matching reference before restoring."
        })
        return DraftRequest(name, reference, image.id, backend, memoryMiB)
    }

    fun encode(): JSONObject = JSONObject().put("schema", 1).put("name", name).put("backend", backend.name)
        .put("memoryMiB", memoryMiB).put("imageDigest", imageDigest).put("imageBytes", imageBytes)
        .put("androidVersion", androidVersion).put("reference", reference?.let {
            JSONObject().put("id", it.id).put("revision", it.revision)
        } ?: JSONObject.NULL)

    companion object {
        fun capture(registry: Registry, id: String): ProfileBackup {
            val phone = requireNotNull(registry.phones.singleOrNull { it.id == id })
            val image = registry.images.single { it.id == phone.imageId }
            return ProfileBackup(phone.name, phone.runtime, phone.memoryMiB, phone.reference, image.sha256, image.bytes, image.androidVersion)
        }

        fun decode(json: JSONObject): ProfileBackup {
            require(json.keys().asSequence().toSet() == setOf(
                "schema", "name", "backend", "memoryMiB", "reference", "imageDigest", "imageBytes", "androidVersion"
            ))
            require(json.get("schema") == 1)
            val reference = if (json.isNull("reference")) null else json.getJSONObject("reference").let {
                require(it.keys().asSequence().toSet() == setOf("id", "revision"))
                ReferenceKey(it.get("id") as String, it.get("revision") as Int)
            }
            val bytes = when (val value = json.get("imageBytes")) {
                is Int -> value.toLong()
                is Long -> value
                else -> error("Invalid backup size.")
            }
            return ProfileBackup(
                json.get("name") as String, RuntimeKind.valueOf(json.get("backend") as String),
                json.get("memoryMiB") as Int, reference, json.get("imageDigest") as String, bytes,
                json.get("androidVersion") as Int
            )
        }
    }
}

internal class ProfileBackups(private val directory: JsonDirectory) {
    fun list(): List<Pair<String, ProfileBackup>> = directory.all().map { (id, json) -> id to ProfileBackup.decode(json) }

    fun save(backup: ProfileBackup) {
        directory.writeBatch(listOf(UUID.randomUUID().toString() to backup.encode()))
    }

    fun delete(id: String) = directory.delete(id)
}
