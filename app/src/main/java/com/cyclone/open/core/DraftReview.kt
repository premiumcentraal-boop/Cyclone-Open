/*
 * Review and revalidation patterns adapted from premiumcentraal-boop/Cyclone-Cloak
 * at 62a05e4a966119908125016211096983cb258529:
 * android/app/src/main/java/dev/cyclone/cloak/ui/PhoneEditorScreen.kt
 * android/app/src/main/java/dev/cyclone/cloak/ui/ProfileOperations.kt
 * MIT, Copyright (c) 2026 Cyclone Cloak contributors. See docs/licenses/Cyclone-Cloak-MIT.txt.
 * Approval binds a draft to the exact catalog and image reference before volume provisioning.
 */
package com.cyclone.open.core

import java.io.IOException
import java.util.UUID

internal data class DraftRequest(
    val name: String,
    val reference: ReferenceKey,
    val imageId: String,
    val backend: RuntimeKind,
    val memoryMiB: Int
)

internal data class DraftReview(
    val instanceId: String,
    val request: DraftRequest,
    val catalogDigest: String,
    val reference: ReferenceDeviceSpec,
    val image: ImageRecord
)

internal class DraftReviewException(message: String) : IOException(message)

internal object DraftReviews {
    fun prepare(registry: Registry, catalog: ReferenceCatalog, request: DraftRequest): DraftReview =
        resolve(registry, catalog, request, UUID.randomUUID().toString())

    private fun resolve(registry: Registry, catalog: ReferenceCatalog, request: DraftRequest, instanceId: String): DraftReview {
        val reference = catalog.resolve(request.reference)
            ?: throw DraftReviewException("The selected virtual reference is missing or outdated. Choose it again.")
        val image = registry.images.firstOrNull { it.id == request.imageId }
            ?: throw DraftReviewException("The selected image reference is missing. Choose another image.")
        if (request.backend == RuntimeKind.HOST) throw DraftReviewException("Choose a VM backend for this draft.")
        if (request.memoryMiB !in draftMemoryOptions) throw DraftReviewException("Choose a supported memory target.")
        if (registry.phones.size >= MAX_PHONES) throw DraftReviewException("The phone draft limit has been reached.")
        val normalized = request.copy(name = Rules.label(request.name))
        return DraftReview(instanceId, normalized, catalog.digest, reference, image)
    }

    fun confirm(registry: Registry, catalog: ReferenceCatalog, approved: DraftReview): DraftRequest {
        val current = try {
            resolve(registry, catalog, approved.request, approved.instanceId)
        } catch (_: DraftReviewException) {
            throw staleReview()
        }
        if (current != approved) throw staleReview()
        return current.request
    }

    private fun staleReview() = DraftReviewException("The reference catalog or image changed. Review the draft again.")
}
