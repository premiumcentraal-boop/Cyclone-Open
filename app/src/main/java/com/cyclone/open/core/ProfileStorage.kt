/*
 * Adapted from premiumcentraal-boop/cyclone-cloak-forge @ 5113af4a84a6607b067b08566431c304c74f1776
 * app/src/main/java/com/cyclonecloak/app/data/ProfileStorage.kt
 * Owner-authorized reuse. The source revision does not include a license file.
 * Adapted to Cyclone Open's private profile directories, UUIDs and error handling.
 */
package com.cyclone.open.core

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

internal class InsufficientProfileStorageException(cause: Throwable) :
    IOException("Not enough storage to save the change. Free up space and try again.", cause)

internal fun storageFull(error: Throwable): Boolean = generateSequence(error) { it.cause }.any {
    it is InsufficientProfileStorageException ||
        (it is ErrnoException && (it.errno == OsConstants.ENOSPC || it.errno == OsConstants.EDQUOT))
}

private val storageKeyPattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,119}")

internal class ProfileStorage(
    private val dataRoot: File,
    private val cacheRoot: File,
    private val createProfileDirectory: (File) -> Unit = { Os.mkdir(it.path, PRIVATE_DIRECTORY_MODE) }
) {
    constructor(context: Context) : this(
        File(context.noBackupFilesDir, "phone-storage"),
        File(context.cacheDir, "phone-storage")
    )

    enum class Area { FILES, PREFERENCES, CACHE }

    fun prepare(id: UUID) = storageIo {
        ensureDirectory(dataRoot)
        createProfileDirectory(File(dataRoot, id.toString()))
    }

    fun isPrepared(id: UUID): Boolean = storageIo {
        directoryExists(dataRoot) && directoryExists(File(dataRoot, id.toString()))
    }

    fun discardIncomplete(id: UUID) = storageIo {
        if (isPrepared(id)) {
            // An interrupted prepare owns only an empty directory. Preserve unexpected contents.
            Os.remove(File(dataRoot, id.toString()).path)
        }
    }

    fun read(id: UUID, area: Area, key: String): ByteArray? = storageIo {
        val target = File(areaDirectory(id, area), checkedKey(key))
        val mode = modeIfPresent(target) ?: return@storageIo null
        if (!OsConstants.S_ISREG(mode)) throw IOException("Invalid profile storage entry.")
        target.readBytes()
    }

    fun write(id: UUID, area: Area, key: String, value: ByteArray) = storageIo {
        val directory = areaDirectory(id, area)
        val target = File(directory, checkedKey(key))
        if (area == Area.CACHE) ensureDirectory(cacheRoot)
        ensureDirectory(directory)
        val temp = File.createTempFile(".write-", ".tmp", directory)
        try {
            FileOutputStream(temp).use { output ->
                output.write(value)
                if (area != Area.CACHE) output.fd.sync()
            }
            Os.rename(temp.path, target.path)
        } finally {
            temp.delete()
        }
    }

    private fun areaDirectory(id: UUID, area: Area): File {
        if (!isPrepared(id)) throw IOException("Profile storage has not been prepared.")
        val profile = File(dataRoot, id.toString())
        val directory = when (area) {
            Area.FILES -> File(profile, "files")
            Area.PREFERENCES -> File(profile, "preferences")
            Area.CACHE -> File(cacheRoot, id.toString())
        }
        if (area == Area.CACHE) directoryExists(cacheRoot)
        directoryExists(directory)
        return directory
    }

    private fun checkedKey(value: String): String {
        require(storageKeyPattern.matches(value)) { "Invalid profile storage key." }
        return value
    }

    private fun ensureDirectory(directory: File) {
        if (directoryExists(directory)) return
        try {
            Os.mkdir(directory.path, PRIVATE_DIRECTORY_MODE)
        } catch (error: ErrnoException) {
            if (error.errno != OsConstants.EEXIST || !directoryExists(directory)) throw error
        }
    }

    private fun directoryExists(entry: File): Boolean {
        val mode = modeIfPresent(entry) ?: return false
        if (!OsConstants.S_ISDIR(mode)) throw IOException("Invalid profile storage directory.")
        return true
    }

    private fun modeIfPresent(entry: File): Int? = try {
        Os.lstat(entry.path).st_mode
    } catch (error: ErrnoException) {
        if (error.errno != OsConstants.ENOENT) throw error
        null
    }

    private inline fun <T> storageIo(operation: () -> T): T = try {
        operation()
    } catch (error: ErrnoException) {
        throw storageError(error)
    } catch (error: IOException) {
        throw storageError(error)
    }

    private fun storageError(error: Exception): IOException = when {
        error is InsufficientProfileStorageException -> error
        storageFull(error) -> InsufficientProfileStorageException(error)
        else -> IOException("Could not access profile storage.", error)
    }

    private companion object {
        const val PRIVATE_DIRECTORY_MODE = 0x1C0 // 0700
    }
}
