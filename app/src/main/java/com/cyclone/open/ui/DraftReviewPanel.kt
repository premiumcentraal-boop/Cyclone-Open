/*
 * Adapted from premiumcentraal-boop/Cyclone-Cloak at 62a05e4a966119908125016211096983cb258529:
 * android/app/src/main/java/dev/cyclone/cloak/ui/PhoneEditorScreen.kt
 * android/app/src/main/java/dev/cyclone/cloak/ui/ProfileOperations.kt
 * MIT, Copyright (c) 2026 Cyclone Cloak contributors. See docs/licenses/Cyclone-Cloak-MIT.txt.
 * Reviews only the draft request and shows which checks still need real runtime evidence.
 */
package com.cyclone.open.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.cyclone.open.core.DraftReview
import com.cyclone.open.core.ReferenceDeviceSpec
import com.cyclone.open.core.ReferenceKey
import com.cyclone.open.core.RuntimeKind

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReferenceChoices(
    references: List<ReferenceDeviceSpec>,
    selected: ReferenceKey?,
    enabled: Boolean,
    onSelect: (ReferenceDeviceSpec) -> Unit
) {
    Text("Virtual reference", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        references.forEach { reference ->
            FilterChip(
                selected = selected == reference.key,
                onClick = { onSelect(reference) },
                label = { Text(reference.label) },
                enabled = enabled
            )
        }
    }
}

@Composable
internal fun DraftReviewPanel(review: DraftReview) {
    val display = review.reference.display
    Text(review.request.name, style = MaterialTheme.typography.titleLarge)
    Text("${review.reference.label} · Virtual reference")
    Text("Display target ${display.widthPx} × ${display.heightPx} px · up to ${display.maxRefreshHz} Hz", color = OpenGlass.Muted)
    Text("Suggested display density ${review.reference.suggested.densityDpi} dpi", color = OpenGlass.Muted)
    Text("Memory target ${review.request.memoryMiB} MiB", color = OpenGlass.Muted)
    val backend = if (review.request.backend == RuntimeKind.LOCAL_VM) "Local VM" else "Remote VM"
    Text("$backend · Guest backend unavailable")
    Text("${review.image.name} · Android ${review.image.androidVersion}")
    Text("Image checksum recorded at import. Publisher and boot checks remain pending.", color = OpenGlass.Muted)
    Text("Saving prepares private profile storage. Guest start remains unavailable.", color = OpenGlass.Muted)
}
