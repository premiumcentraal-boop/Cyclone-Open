package com.cyclone.open.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReferenceCatalogReaderTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun packagedReferencesHaveSeparatePublicDisplayAndSuggestedSettings() {
        val catalog = ReferenceCatalogReader.load(context)
        assertEquals(listOf("pixel-7-class", "pixel-7a-class", "pixel-8-class", "galaxy-s22-class"), catalog.references.map { it.key.id })
        assertEquals(listOf(2400, 2400, 2400, 2340), catalog.references.map { it.display.heightPx })
        assertEquals(listOf(90, 90, 120, 120), catalog.references.map { it.display.maxRefreshHz })
        assertEquals(listOf(3072, 3072, 3072, 3072), catalog.references.map { it.suggested.memoryMiB })
        assertEquals(64, catalog.digest.length)
    }

    @Test
    fun malformedUnknownAndOutOfBoundsInputsAreRejected() {
        val valid = context.assets.open("reference-devices-v1.json").bufferedReader().use { it.readText() }
        val fixtures = listOf(
            "",
            "{\"schema\":1,\"references\":[]}",
            valid.replace("\"schema\": 1", "\"schema\": 2"),
            valid.replace("\"schema\": 1", "\"schema\": 1, \"schema\": 1"),
            valid.replace("\"widthPx\": 1080", "\"widthPx\": \"1080\""),
            valid.replace("\"widthPx\": 1080", "\"widthPx\": 200000"),
            valid.replace("\"widthPx\": 1080", "\"widthPx\": 1080, \"unexpected\": \"value\""),
            valid.replace("\"dimensionsSource\": \"https:", "\"dimensionsSource\": \"file:"),
            valid.replace("\"galaxy-s22-class\"", "\"pixel-7-class\""),
            valid.trimEnd().dropLast(1),
            "${valid}{}",
            valid + " ".repeat(32 * 1024)
        )
        fixtures.forEach { json ->
            assertThrows(CatalogUnavailableException::class.java) { ReferenceCatalogReader.read(json.byteInputStream()) }
        }
    }
}
