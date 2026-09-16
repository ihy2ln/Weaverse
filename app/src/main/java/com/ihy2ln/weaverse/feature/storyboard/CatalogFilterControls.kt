package com.ihy2ln.weaverse.feature.storyboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.ihy2ln.weaverse.core.manga.CatalogTags
import com.ihy2ln.weaverse.core.manga.MediaTagCatalog

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
internal fun CatalogTagSection(title: String, options: List<String>, value: String, onChange: (String) -> Unit,
    groups: List<MediaTagCatalog.Group> = emptyList()) {
    val selected = value.split(',').map(String::trim).filter(String::isNotEmpty)
    val count = options.count { option -> selected.any { CatalogTags.normalize(it.removePrefix("!")) == CatalogTags.normalize(option) } }
    CatalogFilterSection(if (count == 0) title else "$title ($count)") {
        var query by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("") }
        var onlySelected by remember { mutableStateOf(false) }
        if (groups.isNotEmpty()) CatalogFilterChoice("Tag category", category,
            listOf("All categories" to "") + groups.map { it.name to it.name }) { category = it }
        OutlinedTextField(query, { query = it }, label = { Text("Search ${title.lowercase()}") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("catalog-${title.lowercase()}-search"))
        val categoryKeys = groups.firstOrNull { it.name == category }?.tags?.map(CatalogTags::normalize)?.toSet()
        val selectedKeys = selected.map { CatalogTags.normalize(it.removePrefix("!")) }.toSet()
        val visible = MediaTagCatalog.search(options, query).filter {
            (categoryKeys == null || CatalogTags.normalize(it) in categoryKeys) && (!onlySelected || CatalogTags.normalize(it) in selectedKeys)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${visible.size} of ${options.size}", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
            FilterChip(onlySelected, { onlySelected = !onlySelected }, label = { Text("Selected ($count)") })
        }
        if (visible.isEmpty()) Text("No matching tags. Clear search or choose another category.", style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) {
        items(visible, key = { it }) { option ->
            val checked = selected.any { !it.startsWith("!") && CatalogTags.normalize(it) == CatalogTags.normalize(option) }
            val excluded = selected.any { it.startsWith("!") && CatalogTags.normalize(it.drop(1)) == CatalogTags.normalize(option) }
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
}
