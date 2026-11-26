package com.cyclone.open.core

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One process-wide store; Android users keep separate private state. Do not overwrite an unreadable registry. */
class RegistryStore private constructor(context: Context) {
    companion object {
        @Volatile private var instance: RegistryStore? = null
        fun get(context: Context): RegistryStore = instance ?: synchronized(this) {
            instance ?: RegistryStore(context.applicationContext).also { instance = it }
        }
    }
    private val file = AtomicFile(File(context.noBackupFilesDir, "registry-v1.json"))
    @Synchronized fun read(): Registry {
        val json = try { file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() } }
        catch (e: java.io.FileNotFoundException) {
            if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) throw e
            return Registry()
        }
        val o = JSONObject(json)
        require(o.getInt("schema") == 1) { "Unknown registry schema." }
        val images = o.getJSONArray("images")
        val phones = o.getJSONArray("phones")
        require(images.length() <= MAX_IMAGES && phones.length() <= MAX_PHONES)
        return Rules.validate(Registry(
            images = (0 until images.length()).map { i -> images.getJSONObject(i).let {
                ImageRecord(it.getString("id"), it.getString("name"), it.getInt("version"), it.getString("uri"), it.getString("sha256"), it.getLong("bytes"))
            } },
            phones = (0 until phones.length()).map { i -> phones.getJSONObject(i).let {
                PhoneRecord(it.getString("id"), it.getString("name"), RuntimeKind.valueOf(it.getString("runtime")), it.getString("image"), it.getInt("memory"))
            } },
            selected = o.getString("selected")
        ))
    }
    @Synchronized fun change(transform: (Registry) -> Registry): Registry {
        val next = Rules.validate(transform(read()))
        val data = JSONObject().put("schema", 1).put("selected", next.selected)
            .put("images", JSONArray(next.images.map {
                JSONObject().put("id", it.id).put("name", it.name).put("version", it.androidVersion).put("uri", it.uri).put("sha256", it.sha256).put("bytes", it.bytes)
            }))
            .put("phones", JSONArray(next.phones.map {
                JSONObject().put("id", it.id).put("name", it.name).put("runtime", it.runtime.name).put("image", it.imageId).put("memory", it.memoryMiB)
            })).toString().toByteArray(Charsets.UTF_8)
        val out = file.startWrite()
        try { out.write(data); file.finishWrite(out) }
        catch (e: Exception) { file.failWrite(out); throw e }
        return next
    }
}
