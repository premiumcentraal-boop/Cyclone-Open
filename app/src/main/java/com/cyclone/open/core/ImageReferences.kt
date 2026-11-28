package com.cyclone.open.core

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.io.IOException
import java.util.UUID

internal interface ImageReadPermissions {
    fun held(): Set<Uri>
    fun take(uri: Uri)
    fun release(uri: Uri)
}

internal class ImageReferenceException(message: String, cause: Throwable? = null) : IOException(message, cause)

internal class ImageReferences(
    private val store: RegistryStore,
    private val permissions: ImageReadPermissions,
    private val inspect: suspend (Uri, String) -> ImageDigest
) {
    constructor(store: RegistryStore, resolver: ContentResolver) : this(
        store, PersistedImageReadPermissions(resolver), { uri, sha -> ImageInspector.inspect(resolver, uri, sha) }
    )

    suspend fun add(uri: Uri, name: String, version: Int, expectedSha: String) {
        val label = Rules.label(name)
        Rules.version(version)
        val uriText = uri.toString()
        require(store.read().images.none { it.uri == uriText })
        val verified = inspect(uri, expectedSha)
        val record = ImageRecord(UUID.randomUUID().toString(), label, version, uriText, verified.hash, verified.bytes)
        synchronized(store) {
            val proposed = Rules.addImage(store.read(), record)
            val acquired = uri !in permissions.held()
            if (acquired) {
                try {
                    permissions.take(uri)
                } catch (error: SecurityException) {
                    throw accessError(error)
                }
            }
            try {
                store.change { proposed }
            } catch (error: Exception) {
                // Preserve a grant if publication was unconfirmed and the persisted record may need it.
                val unused = runCatching { store.read().images.none { it.uri == uriText } }.getOrDefault(false)
                if (acquired && unused) release(uri)
                throw error
            }
        }
    }

    fun remove(id: String) = synchronized(store) {
        val previous = store.read()
        val removed = previous.images.firstOrNull { it.id == id }
        requireNotNull(removed)
        val committed = store.change { Rules.removeImage(it, id) }
        val uri = Uri.parse(removed.uri)
        if (committed.images.none { it.uri == removed.uri } && uri in permissions.held()) release(uri)
    }

    fun reconcile() = synchronized(store) {
        // Cyclone Open only persists read grants for imported image references.
        val needed = store.read().images.map { Uri.parse(it.uri) }.toSet()
        (permissions.held() - needed).forEach(::release)
    }

    fun hasPersistedReadGrant(record: ImageRecord): Boolean = Uri.parse(record.uri) in permissions.held()

    suspend fun recheck(id: String) {
        val record = requireNotNull(store.read().images.firstOrNull { it.id == id })
        val uri = Uri.parse(record.uri)
        if (!hasPersistedReadGrant(record)) throw accessError()
        val current = inspect(uri, record.sha256)
        checkOriginal(record, current)
    }

    suspend fun restoreAccess(id: String, uri: Uri) {
        val record = requireNotNull(store.read().images.firstOrNull { it.id == id })
        if (uri.toString() != record.uri) throw ImageReferenceException("Select the original document to restore access.")
        checkOriginal(record, inspect(uri, record.sha256))
        synchronized(store) {
            require(record in store.read().images)
            try {
                permissions.take(uri)
            } catch (error: SecurityException) {
                throw accessError(error)
            }
        }
    }

    private fun checkOriginal(record: ImageRecord, current: ImageDigest) {
        if (current.bytes != record.bytes || current.hash != record.sha256) {
            throw ImageReferenceException("The image contents changed. The saved checksum is unchanged.")
        }
    }

    private fun release(uri: Uri) {
        try {
            permissions.release(uri)
        } catch (_: SecurityException) {
            Log.w("ImageReferences", "A persisted image grant could not be released. Reconciliation will retry.")
        }
    }

    private fun accessError(cause: Throwable? = null) =
        ImageReferenceException("Read access to the image has expired. Use Restore access to select the original file.", cause)
}

private class PersistedImageReadPermissions(private val resolver: ContentResolver) : ImageReadPermissions {
    override fun held() = resolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri }.toSet()
    override fun take(uri: Uri) = resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    override fun release(uri: Uri) = resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
