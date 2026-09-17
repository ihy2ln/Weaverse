package com.ihy2ln.weaverse.feature.shell

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ihy2ln.weaverse.core.ui.theme.inkTokens

val HomeAccent: Color
    @Composable get() = if (inkTokens().background.luminance() > .5f) Color(0xFF6840B8) else Color(0xFFB39AFF)

@Composable
fun HomeScreen(modes: List<AppMode>, onMode: (AppMode) -> Unit, onOpen: (HomeItem) -> Unit,
    modifier: Modifier = Modifier, viewModel: HomeViewModel = hiltViewModel()) {
    val shelves by viewModel.shelves.collectAsState()
    val art by viewModel.art.collectAsState()
    HomeShelves(modes, shelves, art, onMode, onOpen, viewModel::remove, viewModel::clear, modifier)
}

@Composable
fun HomeShelves(modes: List<AppMode>, shelves: Map<String, List<HomeItem>>, art: Map<String, String>,
    onMode: (AppMode) -> Unit, onOpen: (HomeItem) -> Unit, onRemove: (HomeItem) -> Unit,
    onClear: (String) -> Unit, modifier: Modifier = Modifier) {
    val tokens = inkTokens()
    var clearMode by remember { mutableStateOf<AppMode?>(null) }
    BoxWithConstraints(modifier.background(tokens.background.copy(alpha = .96f))) {
        val coverWidth = ((maxWidth - 60.dp) / 2.2f).coerceIn(128.dp, 188.dp)
        LazyColumn(modifier = Modifier.testTag("home-feed"), contentPadding = PaddingValues(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                    Text("YOUR WORLDS, WITHIN REACH", color = HomeAccent, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif)
                    Text("Welcome home", color = tokens.primaryText, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, fontSize = 28.sp, fontFamily = FontFamily.SansSerif)
                    Text("Pick up a story. Find your next adventure.", color = tokens.secondaryText, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.SansSerif)
                }
            }
            items(modes, key = { it.name }) { mode ->
                val entries = shelves[mode.name].orEmpty()
                var menu by remember { mutableStateOf(false) }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onMode(mode) }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(mode.label, color = tokens.primaryText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.SansSerif)
                            Icon(Icons.Default.ChevronRight, "Open ${mode.label}", tint = HomeAccent, modifier = Modifier.padding(start = 8.dp))
                        }
                        Box {
                            IconButton(onClick = { menu = true }, enabled = entries.isNotEmpty()) { Icon(Icons.Default.MoreHoriz, "${mode.label} history options", tint = tokens.secondaryText) }
                            DropdownMenu(menu, { menu = false }) { DropdownMenuItem(text = { Text("Clear recent history", fontFamily = FontFamily.SansSerif) }, onClick = { menu = false; clearMode = mode }) }
                        }
                    }
                    if (entries.isEmpty()) {
                        Surface(onClick = { onMode(mode) }, modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = tokens.panel, border = BorderStroke(1.dp, HomeAccent.copy(alpha = .15f))) {
                            Column(Modifier.padding(20.dp)) {
                                Text("Explore ${mode.label}", color = tokens.primaryText, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.SansSerif)
                                Text("Your recently opened content will appear here.", color = tokens.secondaryText, modifier = Modifier.padding(top = 6.dp), fontFamily = FontFamily.SansSerif)
                                Text("Enter mode  →", color = HomeAccent, modifier = Modifier.padding(top = 12.dp), fontWeight = FontWeight.Medium, fontFamily = FontFamily.SansSerif)
                            }
                        }
                    } else LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(entries, key = { it.key }) { item -> HomePoster(item, art[item.mediaId] ?: item.remoteCover, coverWidth, { onOpen(item) }, { onRemove(item) }) }
                    }
                }
            }
        }
    }
    clearMode?.let { mode -> AlertDialog(onDismissRequest = { clearMode = null }, title = { Text("Clear ${mode.label} history?", fontFamily = FontFamily.SansSerif) }, text = { Text("Your content stays in its library. Opening it again adds it back to Home.", fontFamily = FontFamily.SansSerif) }, confirmButton = { TextButton(onClick = { onClear(mode.name); clearMode = null }) { Text("Clear history", fontFamily = FontFamily.SansSerif) } }, dismissButton = { TextButton(onClick = { clearMode = null }) { Text("Cancel", fontFamily = FontFamily.SansSerif) } }) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomePoster(item: HomeItem, art: String?, width: androidx.compose.ui.unit.Dp, onOpen: () -> Unit, onRemove: () -> Unit) {
    val tokens = inkTokens()
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.width(width)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF38314F), Color(0xFF181722))))
            .border(1.dp, HomeAccent.copy(alpha = .2f), RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onOpen, onLongClick = { menu = true }), contentAlignment = Alignment.Center) {
            Text(item.title.split(' ').take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString(""), color = HomeAccent.copy(alpha = .6f), fontSize = 44.sp, fontWeight = FontWeight.Light, fontFamily = FontFamily.SansSerif)
            if (!art.isNullOrBlank()) AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(80.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .65f)))))
            Text(item.badge, color = Color.White, fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomStart).padding(12.dp), fontFamily = FontFamily.SansSerif)
            Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                IconButton(onClick = { menu = true }, colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = .45f), contentColor = Color.White)) {
                    Icon(Icons.Default.MoreHoriz, "Options for ${item.title}")
                }
                DropdownMenu(menu, { menu = false }) { DropdownMenuItem(text = { Text("Remove from recents", fontFamily = FontFamily.SansSerif) }, onClick = { menu = false; onRemove() }) }
            }
        }
        Text(item.title, color = tokens.primaryText, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp,
            minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(top = 10.dp), fontFamily = FontFamily.SansSerif)
    }
}

/** Streaming Home shares the book artwork components while retaining each mode's history and actions. */
@Composable
fun StreamingHomeContent(books: List<com.ihy2ln.weaverse.feature.library.BrowseBook>, modes: List<AppMode>,
    shelves: Map<String, List<HomeItem>>, art: Map<String, String>, onMode: (AppMode) -> Unit,
    onRecent: (HomeItem) -> Unit, onBook: (com.ihy2ln.weaverse.feature.library.BrowseBook) -> Unit,
    onRead: (String) -> Unit, onBooks: () -> Unit, onRemove: (HomeItem) -> Unit, onClear: (String) -> Unit) {
    var clear by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = ((maxWidth - 40.dp) / 2.5f).coerceIn(110.dp, 180.dp)
        LazyColumn(Modifier.fillMaxSize().testTag("home-feed"), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item {
                val featured = com.ihy2ln.weaverse.feature.library.featuredBook(books)
                if (featured != null) com.ihy2ln.weaverse.feature.library.FeaturedBook(featured, { onRead(featured.id) }, { onBook(featured) })
                else com.ihy2ln.weaverse.feature.library.EmptyBrowse("A world of stories. Yours.", "Build your library in Books, or explore a mode below.")
            }
            item {
                com.ihy2ln.weaverse.feature.library.BookCoverShelf("Books", com.ihy2ln.weaverse.feature.library.booksForShelf(books, "recent").take(10), onBook, onBooks,
                    onRemove = { book -> shelves["Novel"].orEmpty().firstOrNull { it.contentId == book.id }?.let(onRemove) },
                    onClear = { clear = "Novel" })
            }
            items(modes.filter { it != AppMode.Novel }, key = { it.name }) { mode ->
                val entries = shelves[mode.name].orEmpty()
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(mode.label, Modifier.weight(1f).clickable { onMode(mode) }.padding(vertical = 12.dp), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        if (entries.isNotEmpty()) {
                            var menu by remember { mutableStateOf(false) }
                            Box { IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreHoriz, "${mode.label} history options") }
                                DropdownMenu(menu, { menu = false }) { DropdownMenuItem(text = { Text("Clear recent history") }, onClick = { menu = false; clear = mode.name }) }
                            }
                        }
                        IconButton(onClick = { onMode(mode) }) { Icon(Icons.Default.ChevronRight, "Open ${mode.label}", tint = com.ihy2ln.weaverse.feature.library.BrowseAccent) }
                    }
                    if (entries.isEmpty()) Surface(onClick = { onMode(mode) }, color = Color(0xFF17171F), shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Explore ${mode.label}", Modifier.weight(1f), fontSize = 14.sp)
                            Text("Enter →", color = com.ihy2ln.weaverse.feature.library.BrowseAccent, fontSize = 13.sp)
                        }
                    } else LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(entries, key = { it.key }) { item -> HomePoster(item, art[item.mediaId] ?: item.remoteCover, width, { onRecent(item) }, { onRemove(item) }) }
                    }
                }
            }
        }
    }
    clear?.let { mode -> AlertDialog(onDismissRequest = { clear = null }, title = { Text("Clear recent history?") }, text = { Text("Your content stays in its library.") }, confirmButton = { TextButton(onClick = { onClear(mode); clear = null }) { Text("Clear history") } }, dismissButton = { TextButton(onClick = { clear = null }) { Text("Cancel") } }) }
}
