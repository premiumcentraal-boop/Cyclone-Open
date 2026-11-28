package com.cyclone.open.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.cyclone.open.core.ImageRecord

@Composable
internal fun DraftImagePicker(
    images: List<ImageRecord>,
    query: String,
    selectedId: String,
    enabled: Boolean,
    onQuery: (String) -> Unit,
    onSelect: (String) -> Unit
) {
    OutlinedTextField(query, onQuery, label = { Text("Search images") }, singleLine = true, enabled = enabled)
    val matches = images.filter { it.name.contains(query.trim(), ignoreCase = true) }
    if (matches.isEmpty()) Text("No matching images.", color = OpenGlass.Muted)
    LazyColumn(Modifier.heightIn(max = 160.dp).selectableGroup()) {
        items(matches, key = { it.id }) { image ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                    selected = selectedId == image.id, enabled = enabled, role = Role.RadioButton,
                    onClick = { onSelect(image.id) }
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = selectedId == image.id, onClick = null, enabled = enabled)
                Text(image.name, Modifier.padding(start = 12.dp))
            }
        }
    }
}
