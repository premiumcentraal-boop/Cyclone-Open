/*
 * Adapted from premiumcentraal-boop/Cyclone-Cloak @ 62a05e4a966119908125016211096983cb258529
 * android/app/src/main/java/dev/cyclone/cloak/data/JsonDirectory.kt
 * MIT, Copyright (c) 2026 Cyclone Cloak contributors. See docs/licenses/Cyclone-Cloak-MIT.txt.
 * Uses canonical UUID filenames, bounded strict reads, synced staging and restart rollback.
 */
package com.cyclone.open.core

import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE

internal class JsonDirectory(
    private val root: File,
    private val move: (File, File) -> Unit = { source, target -> Files.move(source.toPath(), target.toPath(), ATOMIC_MOVE) }
) {
    private val parent = requireNotNull(root.absoluteFile.parentFile)
    private val previous = File(parent, ".${root.name}.previous")
    private val stage = File(parent, ".${root.name}.pending")

    fun all(): List<Pair<String, JSONObject>> = synchronized(lock) {
        recover()
        entries(root).map { it.nameWithoutExtension to read(it) }
    }

    fun writeBatch(values: List<Pair<String, JSONObject>>) = synchronized(lock) {
        recover()
        val existing = entries(root)
        val names = values.map { (id, _) -> fileName(id) }
        require(names.size == names.toSet().size && existing.size + names.size <= MAX_ENTRIES)
        require(existing.none { it.name in names }) { "A backup already exists." }
        if (values.isEmpty()) return@synchronized
        if (!stage.mkdir()) throw IOException("Could not stage backup records.")

        try {
            existing.forEach { file -> write(File(stage, file.name), read(file)) }
            values.zip(names).forEach { (record, name) -> write(File(stage, name), record.second) }
            syncProfileDirectory(stage)
            if (root.exists()) move(root, previous)
            try {
                move(stage, root)
                syncProfileDirectory(parent)
            } catch (error: Exception) {
                if (!root.exists() && previous.exists()) {
                    try { move(previous, root) } catch (rollback: Exception) { error.addSuppressed(rollback) }
                }
                throw error
            }
            previous.deleteRecursively()
        } finally {
            stage.deleteRecursively()
        }
    }

    fun delete(id: String): Boolean = synchronized(lock) {
        recover()
        val file = entries(root).firstOrNull { it.name == fileName(id) } ?: return@synchronized false
        Files.delete(file.toPath())
        syncProfileDirectory(root)
        true
    }

    private fun recover() {
        if (profileDirectoryExists(previous) && !profileDirectoryExists(root)) {
            entries(previous)
            move(previous, root)
            syncProfileDirectory(parent)
        }
        // Refuse unexpected entries before removing a recoverable copy.
        entries(root)
        if (profileDirectoryExists(previous)) {
            entries(previous)
            entries(root).forEach(::read)
            if (!previous.deleteRecursively()) throw IOException("Could not retire the previous backup directory.")
        }
        if (profileDirectoryExists(stage)) {
            entries(stage)
            if (!stage.deleteRecursively()) throw IOException("Could not clear interrupted backup staging.")
        }
    }

    private fun entries(directory: File): List<File> {
        if (!profileDirectoryExists(directory)) return emptyList()
        val files = directory.listFiles() ?: throw IOException("Could not read the backup directory.")
        require(files.size <= MAX_ENTRIES)
        return files.sortedBy { it.name }.onEach { file ->
            require(Files.isRegularFile(file.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS))
            require(file.name == fileName(file.nameWithoutExtension))
        }
    }

    private fun fileName(id: String): String {
        require(Rules.validId(id))
        return "$id.json"
    }

    private fun read(file: File): JSONObject = file.inputStream().use {
        val text = StrictJson.text(StrictJson.readBytes(it, MAX_RECORD_BYTES))
        StrictJson.check(text)
        JSONObject(text)
    }

    private fun write(file: File, json: JSONObject) {
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_RECORD_BYTES)
        FileOutputStream(file).use { out ->
            out.write(bytes)
            out.fd.sync()
        }
    }

    private companion object {
        val lock = Any()
        const val MAX_ENTRIES = 200
        const val MAX_RECORD_BYTES = 16 * 1024
    }
}
