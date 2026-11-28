/*
 * Validation patterns adapted from premiumcentraal-boop/Cyclone-Cloak
 * at 62a05e4a966119908125016211096983cb258529, docs/CATALOG-IMPORT-BRIDGE-V1.md.
 * MIT, Copyright (c) 2026 Cyclone Cloak contributors. See docs/licenses/Cyclone-Cloak-MIT.txt.
 * Closed schema for public reference dimensions and Open's suggested settings.
 */
package com.cyclone.open.core

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.security.MessageDigest
import java.time.LocalDate

private val referenceId = Regex("[a-z][a-z0-9-]{2,47}")
internal val draftMemoryOptions = listOf(2048, 3072, 4096)

data class ReferenceKey(val id: String, val revision: Int) {
    init {
        require(referenceId.matches(id) && revision in 1..1000)
    }
}

internal data class DisplayReference(
    val widthPx: Int,
    val heightPx: Int,
    val maxRefreshHz: Int,
    val dimensionsSource: String,
    val refreshSource: String,
    val reviewedOn: LocalDate
) {
    init {
        require(widthPx in 320..2160 && heightPx in widthPx..4320 && maxRefreshHz in 24..240)
        for (source in listOf(dimensionsSource, refreshSource)) {
            val uri = URI(source)
            require(uri.scheme == "https" && uri.host != null && uri.userInfo == null)
        }
    }
}

internal data class SuggestedDraftSettings(val densityDpi: Int, val memoryMiB: Int, val revision: Int) {
    init {
        require(densityDpi in 120..640 && memoryMiB in draftMemoryOptions && revision in 1..1000)
    }
}

internal data class ReferenceDeviceSpec(
    val key: ReferenceKey,
    val label: String,
    val display: DisplayReference,
    val suggested: SuggestedDraftSettings
) {
    init {
        require(Rules.label(label) == label && label.endsWith("-class"))
    }
}

internal data class ReferenceCatalog(val digest: String, val references: List<ReferenceDeviceSpec>) {
    init {
        require(Rules.digest(digest) == digest)
        require(references.isNotEmpty() && references.size <= 16)
        require(references.map { it.key.id }.toSet().size == references.size)
    }

    fun resolve(key: ReferenceKey): ReferenceDeviceSpec? = references.firstOrNull { it.key == key }
}

internal class CatalogUnavailableException(cause: Exception) :
    IOException("The reference catalog could not be verified. Saved drafts are still available.", cause)

internal object ReferenceCatalogReader {
    private const val MAX_BYTES = 32 * 1024

    fun load(context: Context): ReferenceCatalog = try {
        context.assets.open("reference-devices-v1.json").use(::read)
    } catch (error: CatalogUnavailableException) {
        throw error
    } catch (error: IOException) {
        throw CatalogUnavailableException(error)
    }

    fun read(input: InputStream): ReferenceCatalog = try {
        val bytes = StrictJson.readBytes(input, MAX_BYTES)
        val text = StrictJson.text(bytes)
        StrictJson.check(text)
        val root = JsonReader(text.reader()).use { reader ->
            reader.isLenient = false
            val value = reader.value(0)
            require(reader.peek() == JsonToken.END_DOCUMENT)
            value.fields("schema", "references")
        }
        require(root["schema"] == 1)
        val specs = (root["references"] as List<*>).map { entry ->
            val fields = entry.fields("id", "revision", "label", "display", "suggested")
            val display = fields["display"].fields(
                "widthPx", "heightPx", "maxRefreshHz", "dimensionsSource", "refreshSource", "reviewedOn"
            )
            val suggested = fields["suggested"].fields("densityDpi", "memoryMiB", "revision")
            ReferenceDeviceSpec(
                ReferenceKey(fields.string("id"), fields.integer("revision")), fields.string("label"),
                DisplayReference(
                    display.integer("widthPx"), display.integer("heightPx"), display.integer("maxRefreshHz"),
                    display.string("dimensionsSource"), display.string("refreshSource"), LocalDate.parse(display.string("reviewedOn"))
                ),
                SuggestedDraftSettings(suggested.integer("densityDpi"), suggested.integer("memoryMiB"), suggested.integer("revision"))
            )
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        ReferenceCatalog(digest, specs)
    } catch (error: Exception) {
        throw CatalogUnavailableException(error)
    }

    private fun JsonReader.value(depth: Int): Any {
        require(depth <= 6)
        return when (peek()) {
            JsonToken.BEGIN_OBJECT -> {
                val result = linkedMapOf<String, Any>()
                beginObject()
                while (hasNext()) {
                    val name = nextName()
                    require(name.length <= 48 && result.size < 16 && name !in result)
                    result[name] = value(depth + 1)
                }
                endObject()
                result
            }
            JsonToken.BEGIN_ARRAY -> {
                val result = mutableListOf<Any>()
                beginArray()
                while (hasNext()) {
                    require(result.size < 16)
                    result.add(value(depth + 1))
                }
                endArray()
                result
            }
            JsonToken.STRING -> nextString().also { require(it.length <= 512 && it.none(Char::isISOControl)) }
            JsonToken.NUMBER -> nextString().toInt()
            else -> throw IllegalArgumentException("Unsupported catalog value.")
        }
    }

    private fun Any?.fields(vararg expected: String): Map<*, *> {
        val fields = this as Map<*, *>
        require(fields.keys == expected.toSet())
        return fields
    }

    private fun Map<*, *>.string(name: String): String = this[name] as String
    private fun Map<*, *>.integer(name: String): Int = this[name] as Int
}
