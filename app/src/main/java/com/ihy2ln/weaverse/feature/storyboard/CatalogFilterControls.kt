package com.ihy2ln.weaverse.feature.storyboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.core.manga.CatalogTags

@Composable
internal fun CatalogFilterSection(title: String, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(if (expanded) "⌃" else "⌄")
        }
        if (expanded) content()
        HorizontalDivider()
    }
}

@Composable
internal fun CatalogFilterChoice(label: String, value: String, options: List<Pair<String, String>>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        OutlinedCard(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(options.firstOrNull { it.second == value }?.first ?: value, Modifier.weight(1f))
                    Text("⌄")
                }
            }
        }
        DropdownMenu(expanded, { expanded = false }, modifier = Modifier.heightIn(max = 360.dp)) {
            options.forEach { (name, key) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onChange(key); expanded = false })
            }
        }
    }
}

@Composable
internal fun CatalogTagSection(title: String, options: List<String>, value: String, onChange: (String) -> Unit) {
    val selected = value.split(',').map(String::trim).filter(String::isNotEmpty)
    val count = options.count { option -> selected.any { it.removePrefix("!").equals(option, true) } }
    CatalogFilterSection(if (count == 0) title else "$title ($count)") {
        options.forEach { option ->
            val checked = selected.any { it.equals(option, true) }
            val excluded = selected.any { it.equals("!$option", true) }
            Row(Modifier.fillMaxWidth().clickable {
                onChange(CatalogTags.cycle(value, option))
            }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                if (excluded) Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Text("✕", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleLarge)
                } else Checkbox(checked, onCheckedChange = null)
                Text(option, Modifier.padding(start = 12.dp))
            }
        }
    }
}
