package com.ihy2ln.weaverse.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.data.settings.ModeBackdrops
import com.ihy2ln.weaverse.feature.shell.AmbientVideos
import com.ihy2ln.weaverse.feature.shell.BackdropFile
import com.ihy2ln.weaverse.feature.shell.ModeArt
import com.ihy2ln.weaverse.feature.shell.ModeArtImage
import com.ihy2ln.weaverse.feature.shell.ModeBrand
import java.io.File

/** Home and every mode, in the order the tabs show them. */
private val backdropModes: List<ModeBrand>
    get() = listOf(ModeArt.home) + com.ihy2ln.weaverse.feature.shell.AppMode.entries.map(ModeArt::of)

/**
 * Appearance → Mode backgrounds: several backgrounds per mode, taken in turn on each visit
 * and as a slow slideshow on the mode's first page.
 */
@Composable
fun ModeBackgroundsSection(
    backdrops: Map<String, List<String>>,
    slideshow: Boolean,
    files: Map<String, BackdropFile>,
    onSlideshow: (Boolean) -> Unit,
    onAdd: (modeKey: String) -> Unit,
    onRemove: (modeKey: String, entry: String) -> Unit,
) {
    val tokens = inkTokens()
    Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
        backdropModes.forEach { brand ->
            val chosen = backdrops[brand.key].orEmpty()
            Text(
                brand.title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = tokens.secondaryText,
                modifier = Modifier.padding(top = InkSpacing.md),
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm),
            ) {
                if (chosen.isEmpty()) {
                    BackdropTile(if (brand.key == ModeArt.home.key) "Splash setting" else "Its key art", selected = false) {
                        ModeArtImage(brand, it, fade = 1f)
                    }
                }
                chosen.forEach { entry ->
                    BackdropTile(entryLabel(entry), selected = false, onRemove = { onRemove(brand.key, entry) }) {
                        EntryPreview(entry, files, it)
                    }
                }
                AddTile { onAdd(brand.key) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = InkSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Slideshow", style = MaterialTheme.typography.bodyMedium, color = tokens.primaryText)
                Text(
                    "With more than one background, a mode's first page moves to the next every 30 seconds. Each visit starts on the next one either way.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.secondaryText,
                )
            }
            Switch(checked = slideshow, onCheckedChange = onSlideshow)
        }
    }
}

/** The picker for one mode: key art, focus videos, the wallpaper, or the user's own files. */
@Composable
fun ModeBackdropPicker(
    modeKey: String,
    chosen: List<String>,
    onToggle: (String) -> Unit,
    onFromPhone: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = inkTokens()
    val title = backdropModes.firstOrNull { it.key == modeKey }?.title ?: "Mode"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$title backgrounds") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
                Text("Pick as many as you like. Videos play on the mode's first page and pause deeper in.", color = tokens.secondaryText, fontSize = 13.sp)
                Group("Focus videos") {
                    AmbientVideos.all.forEach { video ->
                        val entry = ModeBackdrops.VIDEO + video.key
                        BackdropTile(video.title, selected = entry in chosen, onClick = { onToggle(entry) }) {
                            Box(it) {
                                AsyncImage(AmbientVideos.poster(video.key), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                Icon(Icons.Filled.PlayCircle, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.align(Alignment.Center).size(26.dp))
                            }
                        }
                    }
                }
                Group("Weaverse art") {
                    ModeArt.splashChoices.forEach { brand ->
                        val entry = ModeBackdrops.ART + brand.key
                        BackdropTile(brand.title, selected = entry in chosen, onClick = { onToggle(entry) }) { ModeArtImage(brand, it, fade = 1f) }
                    }
                }
                Group("More") {
                    BackdropTile("Wallpaper", selected = ModeBackdrops.WALLPAPER in chosen, onClick = { onToggle(ModeBackdrops.WALLPAPER) }) {
                        Box(it.background(tokens.hover), contentAlignment = Alignment.Center) { Text("Appearance\nwallpaper", color = tokens.secondaryText, fontSize = 11.sp) }
                    }
                    BackdropTile("From your phone", selected = false, onClick = onFromPhone) {
                        Box(it.background(tokens.hover), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.PhotoLibrary, null, tint = tokens.activePill, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun Group(title: String, tiles: @Composable () -> Unit) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = inkTokens().secondaryText,
        modifier = Modifier.padding(top = InkSpacing.sm),
    )
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm)) { tiles() }
}

private fun entryLabel(entry: String): String = when {
    entry == ModeBackdrops.WALLPAPER -> "Wallpaper"
    entry.startsWith(ModeBackdrops.VIDEO) -> AmbientVideos.title(entry.removePrefix(ModeBackdrops.VIDEO))
    entry.startsWith(ModeBackdrops.ART) -> ModeArt.splash(entry.removePrefix(ModeBackdrops.ART)).title
    else -> "Your file"
}

@Composable
private fun EntryPreview(entry: String, files: Map<String, BackdropFile>, modifier: Modifier) {
    val tokens = inkTokens()
    when {
        entry.startsWith(ModeBackdrops.VIDEO) -> Box(modifier) {
            AsyncImage(AmbientVideos.poster(entry.removePrefix(ModeBackdrops.VIDEO)), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Icon(Icons.Filled.PlayCircle, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.align(Alignment.Center).size(24.dp))
        }
        entry.startsWith(ModeBackdrops.ART) -> ModeArtImage(ModeArt.splash(entry.removePrefix(ModeBackdrops.ART)), modifier, fade = 1f)
        entry.startsWith(ModeBackdrops.MEDIA) -> {
            val file = files[entry.removePrefix(ModeBackdrops.MEDIA)]
            when {
                file == null -> Box(modifier.background(tokens.hover), contentAlignment = Alignment.Center) { Text("Missing", color = tokens.secondaryText, fontSize = 11.sp) }
                file.video -> Box(modifier.background(tokens.hover), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayCircle, null, tint = tokens.activePill, modifier = Modifier.size(28.dp))
                }
                else -> AsyncImage(File(file.path), null, contentScale = ContentScale.Crop, modifier = modifier)
            }
        }
        else -> Box(modifier.background(tokens.hover), contentAlignment = Alignment.Center) { Text("Wallpaper", color = tokens.secondaryText, fontSize = 11.sp) }
    }
}

@Composable
private fun BackdropTile(
    label: String,
    selected: Boolean,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    art: @Composable (Modifier) -> Unit,
) {
    val tokens = inkTokens()
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.width(100.dp).clip(shape).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        Box(
            Modifier.fillMaxWidth().height(64.dp).clip(shape)
                .border(if (selected) 2.dp else 1.dp, if (selected) tokens.activePill else tokens.hairline, shape),
        ) {
            art(Modifier.matchParentSize())
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(5.dp).size(20.dp).background(tokens.activePill, RoundedCornerShape(999.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Check, null, tint = tokens.activePillLabel, modifier = Modifier.size(14.dp)) }
            }
            if (onRemove != null) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
                        .clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Close, "Remove $label", tint = Color.White, modifier = Modifier.size(14.dp)) }
            }
        }
        Text(label, color = tokens.primaryText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp, start = 2.dp))
    }
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    val tokens = inkTokens()
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.width(100.dp).clip(shape).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(64.dp).clip(shape).border(1.dp, tokens.activePill, shape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Add, null, tint = tokens.activePill)
        }
        Text("Add", color = tokens.activePill, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, start = 2.dp))
    }
}
