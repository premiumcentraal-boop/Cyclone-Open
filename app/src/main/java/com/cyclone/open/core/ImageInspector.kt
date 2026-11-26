package com.cyclone.open.core

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.ensureActive
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

data class ImageDigest(val hash: String, val bytes: Long)
object ImageInspector {
    /** Read-only; no unpacking or execution. Read access and revalidation required before any future boot. */
    suspend fun inspect(resolver: ContentResolver, uri: Uri, expected: String): ImageDigest {
        require(uri.scheme == "content")
        val target = Rules.digest(expected)
        val hash = MessageDigest.getInstance("SHA-256")
        var size = 0L
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                coroutineContext.ensureActive()
                val n = input.read(buffer)
                if (n < 0) break
                if (n == 0) continue
                size += n
                require(size <= MAX_IMAGE_BYTES) { "Image exceeds 16 GiB." }
                hash.update(buffer, 0, n)
            }
        } ?: error("Cannot open selected image.")
        require(size > 0) { "Empty file." }
        val actual = hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        require(actual == target) { "SHA-256 mismatch. Image was not added." }
        return ImageDigest(actual, size)
    }
}
