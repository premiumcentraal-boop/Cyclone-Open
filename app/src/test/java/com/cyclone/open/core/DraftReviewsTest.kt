package com.cyclone.open.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class DraftReviewsTest {
    private val reference = ReferenceDeviceSpec(
        ReferenceKey("example-class", 1), "Example-class",
        DisplayReference(1080, 2400, 90, "https://example.com/dimensions", "https://example.com/refresh", LocalDate.of(2026, 11, 28)),
        SuggestedDraftSettings(420, 3072, 1)
    )
    private val catalog = ReferenceCatalog("a".repeat(64), listOf(reference))
    private val image = ImageRecord(UUID.randomUUID().toString(), "AOSP", 15, "content://test/image", "b".repeat(64), 1024)
    private val registry = Registry(images = listOf(image))
    private val request = DraftRequest("  Lab draft ", reference.key, image.id, RuntimeKind.REMOTE_VM, 3072)

    @Test
    fun reviewNormalizesNameAndKeepsExplicitDraftTargets() {
        val review = DraftReviews.prepare(registry, catalog, request)
        assertEquals("Lab draft", review.request.name)
        assertEquals(RuntimeKind.REMOTE_VM, review.request.backend)
        assertEquals(review.request, DraftReviews.confirm(registry, catalog, review))
    }

    @Test
    fun missingStaleUnsupportedAndOutOfBoundsChoicesFailReview() {
        for (invalid in listOf(
            request.copy(reference = reference.key.copy(revision = 2)),
            request.copy(imageId = UUID.randomUUID().toString()),
            request.copy(backend = RuntimeKind.HOST),
            request.copy(memoryMiB = 16384)
        )) {
            assertThrows(DraftReviewException::class.java) { DraftReviews.prepare(registry, catalog, invalid) }
        }
    }

    @Test
    fun revalidationRejectsChangedCatalogAndMissingImage() {
        val approved = DraftReviews.prepare(registry, catalog, request)
        assertThrows(DraftReviewException::class.java) {
            DraftReviews.confirm(registry, catalog.copy(digest = "c".repeat(64)), approved)
        }
        assertThrows(DraftReviewException::class.java) { DraftReviews.confirm(Registry(), catalog, approved) }
    }
}
