/*
 * Adapted from premiumcentraal-boop/Cyclone @ 1ad783823c79960d9cc09935e8f46253c3823dcc
 * apps/mobile/app/src/main/java/com/cyclone/mobile/runtime/workspaces/ProfileStepJournal.kt
 * Proprietary source, owner-authorized reuse. Records a typed phase before executing it.
 * Uses a dedicated private AtomicFile with target, generation and recovery outcome.
 */
package com.cyclone.open.core

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

internal class AtomicSwitchJournal(base: File) : SwitchJournal {
    private val file = AtomicFile(base)

    override fun read(): SwitchState {
        try {
            val bytes = try {
                file.readFully()
            } catch (error: FileNotFoundException) {
                val base = file.baseFile
                if (base.exists() || File(base.path + ".bak").exists()) throw error
                return SwitchState()
            }
            val json = JSONObject(bytes.toString(Charsets.UTF_8))
            require(json.get("schema") == 1)
            val pending = if (json.has("pending")) json.getJSONObject("pending").let {
                SwitchOperation(lease(it.getJSONObject("lease")), SwitchStage.valueOf(it.getString("stage")))
            } else null
            val failure = if (json.has("failure")) SwitchFailure.valueOf(json.getString("failure")) else null
            return SwitchState(json.getLong("generation"), lease(json.getJSONObject("active")), pending, failure)
        } catch (error: Exception) {
            throw IOException("Saved phone switch state is unreadable.", error)
        }
    }

    override fun write(state: SwitchState) {
        val data = JSONObject().put("schema", 1).put("generation", state.generation)
            .put("active", encode(state.active)).apply {
                state.pending?.let { put("pending", JSONObject().put("lease", encode(it.lease)).put("stage", it.stage.name)) }
                state.lastFailure?.let { put("failure", it.name) }
            }.toString().toByteArray(Charsets.UTF_8)
        val out = file.startWrite()
        try {
            out.write(data)
            out.fd.sync()
        } catch (error: IOException) {
            file.failWrite(out)
            throw error
        }
        file.finishWrite(out)
        if (!file.readFully().contentEquals(data)) throw IOException("Phone switch state could not be saved.")
    }

    private fun lease(json: JSONObject): GenerationLease = GenerationLease(
        PhoneTarget(json.getString("id"), RuntimeKind.valueOf(json.getString("runtime"))),
        json.getLong("generation")
    )

    private fun encode(lease: GenerationLease): JSONObject = JSONObject()
        .put("id", lease.target.id).put("runtime", lease.target.kind.name).put("generation", lease.generation)

    companion object {
        @Volatile private var instance: ProfileSwitchCoordinator? = null

        fun coordinator(context: Context): ProfileSwitchCoordinator = instance ?: synchronized(this) {
            instance ?: ProfileSwitchCoordinator(AtomicSwitchJournal(File(context.noBackupFilesDir, "phone-switch-v1.json")))
                .also { instance = it }
        }
    }
}
