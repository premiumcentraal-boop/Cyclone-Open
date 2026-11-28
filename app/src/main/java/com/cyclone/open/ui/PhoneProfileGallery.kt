/*
 * Adapted from premiumcentraal-boop/Cyclone @ 1ad783823c79960d9cc09935e8f46253c3823dcc
 * apps/mobile/app/src/main/java/com/cyclone/mobile/ui/v32/CycloneProfilesPage.kt
 * apps/mobile/app/src/main/java/com/cyclone/mobile/ui/v32/ProfileGlassKit.kt
 * Proprietary source, owner-authorized reuse. Uses state-labelled cards and visible profile actions.
 * Also adapted from premiumcentraal-boop/Cyclone-Cloak @ 62a05e4a966119908125016211096983cb258529
 * android/app/src/main/java/dev/cyclone/cloak/ui/CloakProfileGallery.kt
 * MIT, Copyright (c) 2026 Cyclone Cloak contributors. See docs/licenses/Cyclone-Cloak-MIT.txt.
 * Keeps deterministic presentation-only avatars and adds explicit draft/runtime semantics.
 */
package com.cyclone.open.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cyclone.open.core.PhoneRecord
import com.cyclone.open.core.RuntimeKind
import com.cyclone.open.core.UnavailableRuntime
import java.util.Locale

private val avatarPalette = listOf(
    Color(0xFF3E7BFA), Color(0xFF7B61FF), Color(0xFF168756), Color(0xFFAD590D),
    Color(0xFFAF4248), Color(0xFF1B7B9E), Color(0xFF9636A7), Color(0xFF687183)
)

internal fun profileInitial(label: String): String {
    val words = label.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    val word = if (words.size > 1 && words.first().equals("Profile", true)) words[1] else words.firstOrNull()
    return word?.let { String(Character.toChars(it.codePointAt(0))).uppercase(Locale.ROOT) } ?: "?"
}

@Composable
private fun ProfileAvatar(id: String, title: String, selected: Boolean) {
    val color = avatarPalette[Math.floorMod(id.hashCode(), avatarPalette.size)]
    Box(
        Modifier.size(48.dp).clearAndSetSemantics { }
            .background(Brush.linearGradient(listOf(lerp(color, Color.Black, .35f), color)), CircleShape)
            .then(if (selected) Modifier.border(2.dp, OpenGlass.Mint, CircleShape) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(profileInitial(title), fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PhoneProfileCard(
    phone: PhoneRecord,
    androidVersion: Int,
    selected: Boolean,
    busy: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit
) {
    val runtimeDetail = UnavailableRuntime(phone.runtime).status().detail
    GlassPanel(selected = selected) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .clickable(enabled = !busy, role = Role.Button, onClickLabel = "Select draft", onClick = onSelect)
                .semantics(mergeDescendants = true) {
                    this.selected = selected
                    stateDescription = if (selected) "Selected draft" else "Draft"
                },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileAvatar(phone.id, phone.name, selected)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(phone.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    if (selected) "Selected draft" else "Phone draft",
                    Modifier.clearAndSetSemantics { },
                    color = OpenGlass.Mint,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        val location = if (phone.runtime == RuntimeKind.LOCAL_VM) "Local VM" else "Remote VM"
        Text("Android $androidVersion · ${phone.memoryMiB} MiB · $location", color = OpenGlass.Muted)
        Text(runtimeDetail, color = OpenGlass.Muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            GlassTool("Select", phone.name, !busy, onSelect)
            GlassTool("Rename", phone.name, !busy, onRename)
            GlassTool("Remove", phone.name, !busy, onRemove)
        }
    }
}

@Composable
internal fun AuroraDraftRow(phone: PhoneRecord, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Select draft", onClick = onSelect)
            .semantics(mergeDescendants = true) {
                this.selected = selected
                stateDescription = "Draft. Guest runtime unavailable."
            }.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProfileAvatar(phone.id, phone.name, selected)
        Column(Modifier.weight(1f)) {
            Text(phone.name, style = MaterialTheme.typography.titleMedium)
            Text("Draft · guest unavailable", Modifier.clearAndSetSemantics { }, color = OpenGlass.Muted)
        }
    }
}
