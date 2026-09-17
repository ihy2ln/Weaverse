@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.ihy2ln.weaverse.feature.library

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.ihy2ln.weaverse.core.ui.theme.*
import com.ihy2ln.weaverse.feature.shell.*

val BrowseBackground = StreamingTokens.background
val BrowseAccent = StreamingTokens.activePill
private val BrowseMuted = StreamingTokens.secondaryText

/** Browsing keeps the shared cinematic identity even when an alternate profile is chosen. */
@Composable
fun BookBrowsingTheme(content: @Composable () -> Unit) {
    WeaverseTheme(profile = AppearanceProfile.Streaming, content = content)
}

/** Route strings are saveable; every route owns independent vertical, horizontal and filter state. */
object BookRoutes {
    fun details(id: String) = "details:${android.net.Uri.encode(id)}"
    fun shelf(id: String) = "shelf:${android.net.Uri.encode(id)}"
    fun value(route: String) = android.net.Uri.decode(route.substringAfter(':'))
}

@Composable
fun BookBrowserScreen(
    routes: List<String>, onRoutes: (List<String>) -> Unit,
    modes: List<AppMode>, onMode: (AppMode) -> Unit, onRecent: (HomeItem) -> Unit,
    onRead: (String) -> Unit, onWrite: (String) -> Unit,
    onCreate: () -> Unit, onImport: () -> Unit, onExport: (String) -> Unit,
    modifier: Modifier = Modifier, viewModel: BookBrowserViewModel = hiltViewModel(),
    homeModel: HomeViewModel = hiltViewModel(),
) {
    val books by viewModel.books.collectAsState()
    val history by homeModel.shelves.collectAsState()
    val art by homeModel.art.collectAsState()
    val status by viewModel.status.collectAsState()
    val route = routes.lastOrNull() ?: "home"
    val state = rememberSaveableStateHolder()
    val open: (BrowseBook) -> Unit = { book -> viewModel.opened(book.id); onRoutes(routes + BookRoutes.details(book.id)) }
    val shelf: (String) -> Unit = { onRoutes(routes + BookRoutes.shelf(it)) }
    BackHandler(routes.size > 1) { onRoutes(routes.dropLast(1)) }
    BookBrowsingTheme {
        Column(modifier.fillMaxSize().background(BrowseBackground).testTag("book-browser")) {
            Box(Modifier.weight(1f)) {
                state.SaveableStateProvider(route) {
                    when {
                        route.startsWith("details:") -> {
                            val book = books.firstOrNull { it.id == BookRoutes.value(route) }
                            if (book == null) EmptyBrowse("Book unavailable", "Return to your library to choose another title.")
                            else BookDetails(book, viewModel, { onRead(book.id) }, { onWrite(book.id) }, { onExport(book.id) },
                                onDeleted = { onRoutes(routes.dropLast(1).ifEmpty { listOf("books") }) })
                        }
                        route == "home" -> StreamingHomeContent(books, modes, history, art, onMode, onRecent, open,
                            onRead, { onRoutes(listOf("books")) }, homeModel::remove, homeModel::clear)
                        route == "books" -> NovelBooksShelf(books, open, onRead, shelf, onCreate, onImport)
                        else -> {
                            val filter = when (route) { "list" -> "list"; "search" -> "all"; else -> BookRoutes.value(route) }
                            BookShelfResults(books, filter, open, if (route == "search") "Search your library" else shelfTitle(filter))
                        }
                    }
                }
            }
            if (status.isNotBlank()) Text(status, Modifier.fillMaxWidth().clickable { viewModel.status.value = "" }.padding(12.dp), color = BrowseAccent)
            NavigationBar(containerColor = BrowseBackground, tonalElevation = 0.dp, windowInsets = WindowInsets(0, 0, 0, 0), modifier = Modifier.testTag("browse-navigation")) {
                // Books has no nav slot of its own: Home already opens the library shelf.
                listOf("home" to "Home", "search" to "Search", "list" to "My List").forEach { (id, label) ->
                    NavigationBarItem(selected = routes.firstOrNull() == id, onClick = { onRoutes(listOf(id)) },
                        icon = { Icon(when (id) { "home" -> Icons.Default.Home; "search" -> Icons.Default.Search; else -> Icons.Default.BookmarkBorder }, label) },
                        label = { Text(label, fontSize = 11.sp) }, colors = NavigationBarItemDefaults.colors(indicatorColor = Color(0xFF29223D), selectedIconColor = BrowseAccent, selectedTextColor = Color.White, unselectedIconColor = BrowseMuted, unselectedTextColor = BrowseMuted))
                }
            }
        }
    }
}

fun shelfTitle(id: String): String = when (id) {
    "reading" -> "Continue Reading"; "writing" -> "Continue Writing"; "list" -> "My List"
    "recent" -> "Recently opened"; "added" -> "Recently Added"; "all" -> "All books"
    else -> id.removePrefix("genre:")
}

@Composable
fun FeaturedBook(book: BrowseBook, onRead: () -> Unit, onDetails: (() -> Unit)? = null) {
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("featured-book")) {
        val wide = maxWidth >= 600.dp
        Box(Modifier.fillMaxWidth()) {
            val background = book.backdrop ?: book.cover
            if (background != null) BrowseImage(background, Modifier.matchParentSize().then(if (book.backdrop == null) Modifier.blur(28.dp) else Modifier), ContentScale.Crop)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(BrowseBackground.copy(alpha = .18f), BrowseBackground.copy(alpha = .8f), BrowseBackground))))
            @Composable fun artwork(modifier: Modifier) {
                Box(modifier.height(if (wide) 200.dp else if (onDetails == null) 200.dp else 160.dp).padding(top = 12.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
                    CoverArtwork(book.book.title, book.cover, Modifier.width(if (wide) 144.dp else if (onDetails == null) 120.dp else 96.dp).fillMaxHeight(), rounded = true)
                }
            }
            @Composable fun caption(modifier: Modifier) {
                Column(modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("FROM YOUR LIBRARY", fontSize = 10.sp, letterSpacing = 2.sp, color = BrowseAccent, fontWeight = FontWeight.Bold)
                    Text(book.book.title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp)
                    val subtitle = listOf(book.book.genre, book.series).filter { it.isNotBlank() }.joinToString("  ·  ")
                    if (subtitle.isNotBlank()) Text(subtitle, color = BrowseMuted, style = MaterialTheme.typography.labelLarge)
                    if (onDetails != null && book.browsing.synopsis.isNotBlank()) Text(book.browsing.synopsis, color = Color(0xFFD4D2DF), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(onClick = onRead, shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = BrowseBackground)) {
                            Icon(Icons.Default.MenuBook, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (book.hasRead) "Continue reading" else "Read", fontWeight = FontWeight.Bold)
                        }
                        if (onDetails != null) FilledTonalButton(onClick = onDetails, shape = RoundedCornerShape(8.dp)) { Icon(Icons.Default.Info, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Details") }
                    }
                }
            }
            if (wide) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) { caption(Modifier.weight(1.3f)); artwork(Modifier.weight(1f)) }
            else Column { artwork(Modifier.fillMaxWidth()); caption(Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
fun BrowseImage(path: String, modifier: Modifier, scale: ContentScale = ContentScale.Fit,
    onLoaded: () -> Unit = {}, onFailed: () -> Unit = {}) {
    val context = LocalContext.current
    // Coil bounds decoding and caches the resulting bitmap; never decode a full-resolution cover for a small card.
    AsyncImage(model = remember(path) { ImageRequest.Builder(context).data(path).size(900, 900).build() }, contentDescription = null,
        contentScale = scale, modifier = modifier, onSuccess = { onLoaded() }, onError = { onFailed() })
}

@Composable
fun CoverArtwork(title: String, path: String?, modifier: Modifier = Modifier, rounded: Boolean = true) {
    var loaded by remember(path) { mutableStateOf(false) }
    val colors = listOf(Color(0xFF39335F), Color(0xFF214453), Color(0xFF543348), Color(0xFF54482B))
    val color = colors[(title.hashCode() and Int.MAX_VALUE) % colors.size]
    Box(modifier.then(if (rounded) Modifier.clip(RoundedCornerShape(8.dp)) else Modifier)
        .background(Brush.linearGradient(listOf(color, Color(0xFF16151F)))), contentAlignment = Alignment.Center) {
        if (!loaded) Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("W", fontSize = 28.sp, fontWeight = FontWeight.Light, color = Color.White.copy(alpha = .35f))
            Spacer(Modifier.height(16.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White, maxLines = 5, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(16.dp))
            Box(Modifier.width(22.dp).height(2.dp).background(BrowseAccent))
        }
        if (path != null) BrowseImage(path, Modifier.fillMaxSize(), onLoaded = { loaded = true }, onFailed = { loaded = false })
    }
}

@Composable
fun BookCoverCard(book: BrowseBook, onOpen: () -> Unit, modifier: Modifier = Modifier, onRemove: (() -> Unit)? = null) {
    var menu by remember { mutableStateOf(false) }
    Column(modifier.clip(RoundedCornerShape(8.dp)).combinedClickable(onClick = onOpen, onLongClickLabel = "History options", onLongClick = if (onRemove == null) null else { { menu = true } }).testTag("book-cover:${book.id}")) {
        CoverArtwork(book.book.title, book.cover, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
        DropdownMenu(menu, { menu = false }) { DropdownMenuItem(text = { Text("Remove from recents") }, onClick = { menu = false; onRemove?.invoke() }) }
        Text(book.book.title, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, minLines = 2, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 9.dp))
        if (book.book.genre.isNotBlank()) Text(book.book.genre, fontSize = 11.sp, color = BrowseMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun BookCoverShelf(title: String, books: List<BrowseBook>, onOpen: (BrowseBook) -> Unit, onAll: () -> Unit,
    emptyText: String = "Open your books library.", onRemove: ((BrowseBook) -> Unit)? = null, onClear: (() -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ShelfHeading(title, onAll, onClear)
        if (books.isEmpty()) Text(emptyText, Modifier.fillMaxWidth().clickable(onClick = onAll).padding(horizontal = 20.dp, vertical = 8.dp), color = BrowseMuted, fontSize = 13.sp)
        else BoxWithConstraints(Modifier.fillMaxWidth()) {
            val width = ((maxWidth - 40.dp) / 2.5f).coerceIn(110.dp, 180.dp)
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.testTag("shelf:$title")) {
                items(books, key = { it.id }) { book -> BookCoverCard(book, { onOpen(book) }, Modifier.width(width), onRemove?.let { remove -> { remove(book) } }) }
            }
        }
    }
}

@Composable
fun ShelfHeading(title: String, onAll: () -> Unit, onClear: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("shelf-heading:$title"), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 19.sp, modifier = Modifier.weight(1f).clickable(onClick = onAll))
        if (onClear != null) IconButton(onClick = onClear) { Icon(Icons.Default.MoreHoriz, "Books history options", tint = BrowseMuted) }
        TextButton(onClick = onAll) { Text("See all", fontSize = 12.sp, color = BrowseAccent) }
    }
}

@Composable
fun EmptyBrowse(title: String, description: String) {
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(description, color = BrowseMuted)
    }
}

@Composable
fun BookShelfResults(books: List<BrowseBook>, shelf: String, onOpen: (BrowseBook) -> Unit, title: String) {
    var query by rememberSaveable { mutableStateOf("") }
    var genre by rememberSaveable { mutableStateOf("") }
    var filters by remember { mutableStateOf(false) }
    val result = remember(books, shelf, query, genre) { booksForShelf(books, shelf, query, genre) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp))
        OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth().testTag("book-search"), singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) }, label = { Text("Search title, genre or synopsis") })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${result.size} books", color = BrowseMuted, modifier = Modifier.weight(1f))
            Box {
                TextButton(onClick = { filters = true }) { Text(genre.ifBlank { "All genres" }); Icon(Icons.Default.ExpandMore, null) }
                DropdownMenu(filters, { filters = false }) {
                    (listOf("", "Uncategorized") + books.map { it.book.genre }.filter { it.isNotBlank() }.distinct().sorted()).forEach { label ->
                        DropdownMenuItem(text = { Text(label.ifBlank { "All genres" }) }, onClick = { genre = label; filters = false })
                    }
                }
            }
        }
        if (result.isEmpty()) EmptyBrowse("No books here yet", if (query.isNotBlank() || genre.isNotBlank()) "Try a different search or genre." else "Your matching library titles will appear here.")
        LazyVerticalGrid(columns = GridCells.Adaptive(125.dp), modifier = Modifier.weight(1f).testTag("book-results"), contentPadding = PaddingValues(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            items(result, key = { it.id }) { BookCoverCard(it, { onOpen(it) }) }
        }
    }
}

@Composable
private fun BookDetails(book: BrowseBook, viewModel: BookBrowserViewModel, onRead: () -> Unit, onWrite: () -> Unit,
    onExport: () -> Unit, onDeleted: () -> Unit) {
    val info by remember(book.id) { viewModel.details(book.id) }.collectAsState(initial = BookDetailsInfo())
    var more by remember { mutableStateOf(false) }
    var edit by rememberSaveable { mutableStateOf<String?>(null) }
    var delete by remember { mutableStateOf(false) }
    var audio by remember { mutableStateOf(false) }
    var backdrop by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { viewModel.artwork(book.id, it, backdrop) } }
    LazyColumn(Modifier.fillMaxSize().testTag("book-details"), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { FeaturedBook(book, onRead) }
        item {
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(info.position, color = BrowseMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("reading-position"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(onClick = onWrite, shape = RoundedCornerShape(8.dp)) { Icon(Icons.Default.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (book.hasWritten) "Continue writing" else "Write") }
                    OutlinedButton(onClick = { viewModel.toggleList(book.id) }, shape = RoundedCornerShape(8.dp)) { Icon(if (book.browsing.listedAt > 0) Icons.Default.Check else Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (book.browsing.listedAt > 0) "Remove from My List" else "Add to My List") }
                    Box {
                        TextButton(onClick = { more = true }) { Icon(Icons.Default.MoreHoriz, null); Text("More") }
                        DropdownMenu(more, { more = false }) {
                            DropdownMenuItem(text = { Text("Edit cover") }, onClick = { more = false; backdrop = false; picker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                            DropdownMenuItem(text = { Text("Edit backdrop") }, onClick = { more = false; backdrop = true; picker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                            DropdownMenuItem(text = { Text("Book metadata") }, onClick = { more = false; edit = "metadata" })
                            DropdownMenuItem(text = { Text("Export book") }, onClick = { more = false; onExport() })
                            DropdownMenuItem(text = { Text("Duplicate book") }, onClick = { more = false; viewModel.duplicate(book.id) })
                            DropdownMenuItem(text = { Text("Delete book") }, onClick = { more = false; delete = true })
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Synopsis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { edit = "synopsis" }) { Text("Edit synopsis") }
                }
                Text(book.browsing.synopsis.ifBlank { "Add a synopsis to introduce this book." }, color = BrowseMuted)
                if (info.audio.isNotEmpty()) OutlinedButton(onClick = { audio = true }) { Icon(Icons.Default.Headphones, null); Spacer(Modifier.width(8.dp)); Text("Linked audio · ${info.audio.size} file${if (info.audio.size == 1) "" else "s"}") }
            }
        }
    }
    if (edit != null) {
        var synopsis by rememberSaveable(book.id, edit) { mutableStateOf(book.browsing.synopsis) }
        var title by rememberSaveable(book.id, edit) { mutableStateOf(book.book.title) }
        var genre by rememberSaveable(book.id, edit) { mutableStateOf(book.book.genre) }
        AlertDialog(onDismissRequest = { edit = null }, title = { Text(if (edit == "synopsis") "Edit synopsis" else "Book metadata") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (edit == "synopsis") OutlinedTextField(synopsis, { synopsis = it }, label = { Text("Synopsis") }, minLines = 5)
                else { OutlinedTextField(title, { title = it }, label = { Text("Title") }); OutlinedTextField(genre, { genre = it }, label = { Text("Genre") }); if (book.series.isNotBlank()) Text("Series: ${book.series}") }
            }
        }, confirmButton = { TextButton(onClick = { if (edit == "synopsis") viewModel.synopsis(book.id, synopsis) else viewModel.metadata(book.id, title, genre); edit = null }) { Text("Save") } }, dismissButton = { TextButton(onClick = { edit = null }) { Text("Cancel") } })
    }
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("Delete ${book.book.title}?") }, text = { Text("This permanently removes this book. Export a backup first if you want to keep a copy.") }, confirmButton = { TextButton(onClick = { viewModel.delete(book.id); delete = false; onDeleted() }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { delete = false }) { Text("Cancel") } })
    if (audio) LinkedAudioDialog(info.audio) { audio = false }
}

@Composable
private fun LinkedAudioDialog(files: List<BookAudio>, onClose: () -> Unit) {
    var playing by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf("") }
    val player = remember { android.media.MediaPlayer() }
    DisposableEffect(player) { onDispose { player.release() } }
    AlertDialog(onDismissRequest = onClose, title = { Text("Book-linked audio") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Individual audio files attached to this book or its scenes.", color = BrowseMuted)
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            files.forEach { file ->
                TextButton(onClick = {
                    if (playing == file.path) { player.stop(); playing = null }
                    else runCatching { player.reset(); player.setDataSource(file.path); error = ""; playing = file.path
                        player.setOnCompletionListener { playing = null }
                        player.setOnErrorListener { _, _, _ -> playing = null; error = "This audio file could not be played."; true }
                        player.setOnPreparedListener { it.start() }; player.prepareAsync() }.onFailure { playing = null; error = "This audio file could not be played." }
                }) { Text((if (playing == file.path) "Stop · " else "Play · ") + file.title + if (file.scene.isBlank()) "" else " · ${file.scene}") }
            }
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text("Close") } })
}
