/*
 * Adapted from premiumcentraal-boop/Cyclone @ 1ad783823c79960d9cc09935e8f46253c3823dcc
 * apps/mobile/app/src/main/java/com/cyclone/mobile/ui/ProfileRescueBar.kt
 * Proprietary source, owner-authorized reuse. Keeps the quick return action on a host-owned surface.
 * Uses an in-app sheet with explicit draft selection and Android Home actions.
 */
package com.cyclone.open.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cyclone.open.core.Registry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AuroraSheet(
    registry: Registry?,
    busy: Boolean,
    dismiss: () -> Unit,
    openAndroidHome: () -> Unit,
    selectDraft: (String) -> Unit
) {
    ModalBottomSheet(onDismissRequest = dismiss) {
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 460.dp).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Aurora", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = dismiss) { Text("Close") }
                }
                Button(onClick = openAndroidHome) { Text("Return to Android Home") }
            }
            if (registry != null) {
                items(registry.phones, key = { it.id }) { phone ->
                    AuroraDraftRow(phone, phone.id == registry.selected, !busy) { selectDraft(phone.id) }
                }
            }
        }
    }
}
