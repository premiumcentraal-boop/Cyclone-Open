/*
 * Adapted from premiumcentraal-boop/cyclone-cloak-forge @ 5113af4a84a6607b067b08566431c304c74f1776
 * app/src/main/java/com/cyclonecloak/app/data/ProfileRepository.kt
 * app/src/main/java/com/cyclonecloak/app/CycloneCloakApplication.kt
 * Owner-authorized reuse. The source revision does not include a license file.
 * Replaces Room reservations with durable markers around the existing AtomicFile registry.
 */
package com.cyclone.open.core

import android.content.Context
import android.system.Os
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.UUID

internal class PhoneVolumeRepository(
    private val store: RegistryStore,
    private val storage: ProfileStorage,
    private val journal: PhoneVolumeJournal
) {
    constructor(context: Context, store: RegistryStore) : this(
        store,
        ProfileStorage(context),
        PhoneVolumeJournal(File(context.noBackupFilesDir, "phone-volume-pending"))
    )

    fun open(): Registry = synchronized(store) {
        var registry = store.read()
        recoverPending(registry)
        for (phone in registry.phones.filterNot { it.volumePrepared }) {
            registry = provision(UUID.fromString(phone.id), registry)
        }
        registry
    }

    fun create(name: String, imageId: String, runtime: RuntimeKind, memory: Int): Registry = synchronized(store) {
        val current = store.read()
        recoverPending(current)
        val proposed = Rules.addPhone(current, name, imageId, runtime, memory)
        provision(UUID.fromString(proposed.selected), proposed)
    }

    private fun provision(id: UUID, proposed: Registry): Registry {
        journal.begin(id)
        val committed = try {
            if (!storage.isPrepared(id)) storage.prepare(id)
            val updated = proposed.copy(phones = proposed.phones.map { phone ->
                if (phone.id == id.toString()) phone.copy(volumePrepared = true) else phone
            })
            store.change { updated }
        } catch (error: IOException) {
            try {
                reconcile(id, store.read())
            } catch (cleanup: IOException) {
                error.addSuppressed(cleanup)
            }
            throw error
        }
        // Publication is complete. A leftover marker can be reconciled on the next startup.
        finishRecovery(id, committed)
        return committed
    }

    private fun recoverPending(registry: Registry) {
        journal.pending().forEach { finishRecovery(it, registry) }
    }

    private fun finishRecovery(id: UUID, registry: Registry) {
        try {
            reconcile(id, registry)
        } catch (error: IOException) {
            Log.w("PhoneVolumes", "Profile volume recovery deferred: ${error.javaClass.simpleName}")
        }
    }

    private fun reconcile(id: UUID, registry: Registry) {
        val published = registry.phones.any { it.id == id.toString() && it.volumePrepared }
        if (published) {
            if (!storage.isPrepared(id)) throw IOException("Published phone storage is missing.")
        } else {
            storage.discardIncomplete(id)
        }
        journal.finish(id)
    }
}

internal class PhoneVolumeJournal(private val root: File) {
    fun begin(id: UUID) = profileStorageIo {
        ensureProfileDirectory(root)
        ensureProfileDirectory(File(root, id.toString()))
        syncProfileDirectory(root)
        syncProfileDirectory(requireNotNull(root.parentFile))
    }

    fun pending(): List<UUID> = profileStorageIo {
        if (!profileDirectoryExists(root)) return@profileStorageIo emptyList()
        val entries = root.listFiles() ?: throw IOException("Could not list pending phone storage.")
        entries.map { entry ->
            if (!Rules.validId(entry.name) || !profileDirectoryExists(entry)) throw IOException("Invalid pending phone storage.")
            UUID.fromString(entry.name)
        }
    }

    fun finish(id: UUID) = profileStorageIo {
        val marker = File(root, id.toString())
        if (profileDirectoryExists(marker)) {
            Os.remove(marker.path)
            syncProfileDirectory(root)
        }
    }
}
