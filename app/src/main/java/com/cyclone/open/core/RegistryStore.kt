package com.cyclone.open.core

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

class RegistryReadException(cause: Exception) : IOException("Cannot read the saved registry.", cause)
class RegistryWriteException(cause: IOException) : IOException("Cannot save the registry.", cause)

/** One process-wide store. Android users keep separate private state. */
class RegistryStore internal constructor(
    baseFile: File,
    private val writeBytes: (FileOutputStream, ByteArray) -> Unit = FileOutputStream::write
) {
    companion object {
        @Volatile private var instance: RegistryStore? = null
        fun get(context: Context): RegistryStore = instance ?: synchronized(this) {
            instance ?: RegistryStore(File(context.noBackupFilesDir, "registry-v1.json")).also { instance = it }
        }
    }
    private val file = AtomicFile(baseFile)
    private val legacyBackup = File(baseFile.path + ".bak")
    private val snapshots = MutableStateFlow<Registry?>(null)
    internal val committed = snapshots.asStateFlow()

    @Synchronized
    fun read(): Registry {
        try {
            val bytes = try {
                file.readFully()
            } catch (e: FileNotFoundException) {
                if (file.baseFile.exists() || legacyBackup.exists()) throw e
                return Registry().also { snapshots.value = it }
            }
            return decode(bytes.toString(Charsets.UTF_8)).also { snapshots.value = it }
        } catch (e: Exception) {
            snapshots.value = null
            throw RegistryReadException(e)
        }
    }

    private fun decode(json: String): Registry {
        val o = JSONObject(json)
        require(o.get("schema") == 1) { "Unknown registry schema." }
        val images = o.getJSONArray("images")
        val phones = o.getJSONArray("phones")
        require(images.length() <= MAX_IMAGES && phones.length() <= MAX_PHONES)
        return Rules.validate(Registry(
            images = (0 until images.length()).map { i -> images.getJSONObject(i).let {
                ImageRecord(it.getString("id"), it.getString("name"), it.getInt("version"), it.getString("uri"), it.getString("sha256"), it.getLong("bytes"))
            } },
            phones = (0 until phones.length()).map { i -> phones.getJSONObject(i).let {
                PhoneRecord(
                    it.getString("id"), it.getString("name"), RuntimeKind.valueOf(it.getString("runtime")),
                    it.getString("image"), it.getInt("memory"),
                    if (it.has("volumePrepared")) it.get("volumePrepared") as Boolean else false
                )
            } },
            selected = o.getString("selected")
        ))
    }
    @Synchronized
    fun change(transform: (Registry) -> Registry): Registry {
        val current = read()
        val next = Rules.validate(transform(current))
        if (next == current) return current

        val data = JSONObject().put("schema", 1).put("selected", next.selected)
            .put("images", JSONArray(next.images.map {
                JSONObject().put("id", it.id).put("name", it.name).put("version", it.androidVersion).put("uri", it.uri).put("sha256", it.sha256).put("bytes", it.bytes)
            }))
            .put("phones", JSONArray(next.phones.map {
                JSONObject().put("id", it.id).put("name", it.name).put("runtime", it.runtime.name)
                    .put("image", it.imageId).put("memory", it.memoryMiB).put("volumePrepared", it.volumePrepared)
            })).toString().toByteArray(Charsets.UTF_8)
        try {
            val out = file.startWrite()
            try {
                writeBytes(out, data)
                // AtomicFile logs sync and rename failures without returning them to callers.
                out.fd.sync()
            } catch (e: IOException) {
                file.failWrite(out)
                throw e
            }
            file.finishWrite(out)
            if (!file.readFully().contentEquals(data)) {
                throw IOException("Registry commit could not be verified.")
            }
        } catch (e: IOException) {
            if (storageFull(e)) throw InsufficientProfileStorageException(e)
            throw RegistryWriteException(e)
        }
        return next.also { snapshots.value = it }
    }
}
