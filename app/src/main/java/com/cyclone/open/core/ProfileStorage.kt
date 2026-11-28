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
private const val PRIVATE_DIRECTORY_MODE = 0x1C0 // 0700

internal inline fun <T> profileStorageIo(operation: () -> T): T = try {
    operation()
} catch (error: ErrnoException) {
    throw profileStorageError(error)
} catch (error: IOException) {
    throw profileStorageError(error)
}

internal fun profileStorageError(error: Exception): IOException = when {
    error is InsufficientProfileStorageException -> error
    storageFull(error) -> InsufficientProfileStorageException(error)
    else -> IOException("Could not access profile storage.", error)
}

internal fun ensureProfileDirectory(directory: File) {
    if (profileDirectoryExists(directory)) return
    try {
        Os.mkdir(directory.path, PRIVATE_DIRECTORY_MODE)
    } catch (error: ErrnoException) {
        if (error.errno != OsConstants.EEXIST || !profileDirectoryExists(directory)) throw error
    }
}

internal fun profileDirectoryExists(entry: File): Boolean {
    val mode = modeIfPresent(entry) ?: return false
    if (!OsConstants.S_ISDIR(mode)) throw IOException("Invalid profile storage directory.")
    return true
}

internal fun syncProfileDirectory(directory: File) {
    val fd = Os.open(directory.path, OsConstants.O_RDONLY, 0)
    try {
        if (!OsConstants.S_ISDIR(Os.fstat(fd).st_mode)) throw IOException("Invalid profile storage directory.")
        Os.fsync(fd)
    } finally {
        Os.close(fd)
    }
}

private fun modeIfPresent(entry: File): Int? = try {
    Os.lstat(entry.path).st_mode
} catch (error: ErrnoException) {
    if (error.errno != OsConstants.ENOENT) throw error
    null
}

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

    fun prepare(id: UUID) = profileStorageIo {
        ensureProfileDirectory(dataRoot)
        createProfileDirectory(File(dataRoot, id.toString()))
        syncProfileDirectory(dataRoot)
        syncProfileDirectory(requireNotNull(dataRoot.parentFile))
    }

    fun isPrepared(id: UUID): Boolean = profileStorageIo {
        profileDirectoryExists(dataRoot) && profileDirectoryExists(File(dataRoot, id.toString()))
    }

    fun discardIncomplete(id: UUID) = profileStorageIo {
        if (isPrepared(id)) {
            // An interrupted prepare owns only an empty directory. Preserve unexpected contents.
            Os.remove(File(dataRoot, id.toString()).path)
            syncProfileDirectory(dataRoot)
        }
    }

    fun read(id: UUID, area: Area, key: String): ByteArray? = profileStorageIo {
        val target = File(areaDirectory(id, area), checkedKey(key))
        val mode = modeIfPresent(target) ?: return@profileStorageIo null
        if (!OsConstants.S_ISREG(mode)) throw IOException("Invalid profile storage entry.")
        target.readBytes()
    }

    fun write(id: UUID, area: Area, key: String, value: ByteArray) = profileStorageIo {
        val directory = areaDirectory(id, area)
        val target = File(directory, checkedKey(key))
        if (area == Area.CACHE) ensureProfileDirectory(cacheRoot)
        ensureProfileDirectory(directory)
        val temp = File.createTempFile(".write-", ".tmp", directory)
        try {
            FileOutputStream(temp).use { output ->
                output.write(value)
                if (area != Area.CACHE) output.fd.sync()
            }
            Os.rename(temp.path, target.path)
            if (area != Area.CACHE) {
                syncProfileDirectory(directory)
                syncProfileDirectory(requireNotNull(directory.parentFile))
            }
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
        if (area == Area.CACHE) profileDirectoryExists(cacheRoot)
        profileDirectoryExists(directory)
        return directory
    }

    private fun checkedKey(value: String): String {
        require(storageKeyPattern.matches(value)) { "Invalid profile storage key." }
        return value
    }
}
