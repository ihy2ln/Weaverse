package com.ihy2ln.weaverse.feature.chatting.media

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.network.httpHeaders
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.core.media.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** How many pictures one post or message carries, like Twitter's four-up grid. */
const val MAX_ATTACHMENTS = 4

/** GIFs, and the silent looping MP4s ("gifv") that Mastodon, Imgur and others serve GIFs as. */
fun isGifPath(path: String): Boolean = path.endsWith(".gif", ignoreCase = true) || isLoopVideoPath(path)

fun isLoopVideoPath(path: String): Boolean = path.endsWith(".mp4", ignoreCase = true) || path.endsWith(".webm", ignoreCase = true)

/**
 * A picture from app storage. GIFs play through Android's own animated decoder
 * (API 28+), so no extra image library is needed; older devices show the first frame.
 */
@Composable
fun ChatImage(
    path: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    showGifBadge: Boolean = true,
) {
    Box(modifier) {
        if (isLoopVideoPath(path)) {
            com.ihy2ln.weaverse.core.ui.components.LoopingVideoBackground(
                path, Modifier.fillMaxSize(), fitInside = contentScale == ContentScale.Fit || contentScale == ContentScale.Inside,
            )
        } else if (isGifPath(path) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            AnimatedGif(File(path), contentScale, Modifier.fillMaxSize())
        } else {
            coil3.compose.AsyncImage(
                model = File(path),
                contentDescription = "Picture",
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (showGifBadge && isGifPath(path)) {
            Text(
                "GIF",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xB3000000))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun AnimatedGif(file: File, contentScale: ContentScale, modifier: Modifier) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
    val drawable by produceState<Drawable?>(null, file) {
        value = withContext(Dispatchers.IO) {
            runCatching { ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) }.getOrNull()
        }
    }
    DisposableEffect(drawable) {
        (drawable as? AnimatedImageDrawable)?.apply {
            repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
            start()
        }
        onDispose { (drawable as? AnimatedImageDrawable)?.stop() }
    }
    AndroidView(
        factory = { context -> ImageView(context) },
        update = { view ->
            view.scaleType = when (contentScale) {
                ContentScale.Fit, ContentScale.Inside -> ImageView.ScaleType.FIT_CENTER
                else -> ImageView.ScaleType.CENTER_CROP
            }
            if (view.drawable !== drawable) view.setImageDrawable(drawable)
        },
        modifier = modifier,
    )
}

/**
 * Twitter's attachment layout: one picture wide, two side by side, three as one tall
 * and two stacked, four in a square. Tap opens the full-screen viewer at that picture.
 */
@Composable
fun MediaGrid(
    paths: List<String>,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 230.dp,
    corner: Dp = 16.dp,
    border: Color = Color.Transparent,
) {
    if (paths.isEmpty()) return
    val shown = paths.take(MAX_ATTACHMENTS)
    val gap = 2.dp
    Box(
        modifier
            .fillMaxWidth()
            .height(if (shown.size == 1 && isGifPath(shown[0])) height * 0.85f else height)
            .clip(RoundedCornerShape(corner))
            .border(1.dp, border, RoundedCornerShape(corner)),
    ) {
        @Composable
        fun Cell(i: Int, m: Modifier) {
            ChatImage(shown[i], m.clickable { onOpen(i) })
        }
        when (shown.size) {
            1 -> Cell(0, Modifier.fillMaxSize())
            2 -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Cell(0, Modifier.weight(1f).fillMaxHeight())
                Cell(1, Modifier.weight(1f).fillMaxHeight())
            }
            3 -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Cell(0, Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    Cell(1, Modifier.weight(1f).fillMaxWidth())
                    Cell(2, Modifier.weight(1f).fillMaxWidth())
                }
            }
            else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Cell(0, Modifier.weight(1f).fillMaxHeight())
                    Cell(1, Modifier.weight(1f).fillMaxHeight())
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Cell(2, Modifier.weight(1f).fillMaxHeight())
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        Cell(3, Modifier.fillMaxSize())
                        if (paths.size > MAX_ATTACHMENTS) {
                            Box(
                                Modifier.fillMaxSize().background(Color(0x99000000)).clickable { onOpen(3) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("+${paths.size - MAX_ATTACHMENTS}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Pictures picked for the next post or message, each removable before sending. */
@Composable
fun AttachmentStrip(paths: List<String>, onRemove: (Int) -> Unit, modifier: Modifier = Modifier) {
    if (paths.isEmpty()) return
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        paths.forEachIndexed { i, path ->
            Box(Modifier.size(76.dp).clip(RoundedCornerShape(10.dp))) {
                ChatImage(path, Modifier.fillMaxSize())
                Icon(
                    Icons.Filled.Close,
                    "Remove picture",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(0xBF000000))
                        .clickable { onRemove(i) }
                        .padding(3.dp),
                )
            }
        }
    }
}

/** Full-screen viewer: swipe between pictures, pinch or double-tap to zoom. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaViewer(paths: List<String>, start: Int, onClose: () -> Unit, caption: String = "") {
    if (paths.isEmpty()) return
    val context = LocalContext.current
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler(onBack = onClose)
        val pager = rememberPagerState(initialPage = start.coerceIn(0, paths.lastIndex)) { paths.size }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                ZoomablePicture(paths[page])
            }
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close", tint = Color.White) }
                Spacer(Modifier.weight(1f))
                if (paths.size > 1) {
                    Text("${pager.currentPage + 1} / ${paths.size}", color = Color.White, fontSize = 14.sp)
                    Spacer(Modifier.weight(1f))
                }
                IconButton(onClick = { sharePicture(context, paths[pager.currentPage]) }) {
                    Icon(Icons.Outlined.Share, "Share picture", tint = Color.White)
                }
            }
            if (caption.isNotBlank()) {
                Text(
                    caption,
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color(0x99000000))
                        .navigationBarsPadding()
                        .padding(16.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ZoomablePicture(path: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale > 1f) offset + pan else Offset.Zero
    }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(path) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                })
            }
            // Only claim drags while zoomed, so an unzoomed swipe still turns the page.
            .then(if (scale > 1f) Modifier.transformable(state) else Modifier.transformable(state, lockRotationOnZoomPan = true, canPan = { false }))
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
        contentAlignment = Alignment.Center,
    ) {
        ChatImage(path, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, showGifBadge = false)
    }
}

private fun sharePicture(context: android.content.Context, path: String) {
    runCatching {
        // The app's FileProvider only exposes the cache, so the picture is copied there first.
        val source = File(path)
        val file = File(File(context.cacheDir, "shared").apply { mkdirs() }, source.name)
        source.copyTo(file, overwrite = true)
        val authority = context.packageName + ".extension-files"
        val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = if (isLoopVideoPath(path)) "video/mp4" else if (isGifPath(path)) "image/gif" else "image/*"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Share picture"))
    }
}


// --------------------------------------------------------------- picker

/** One picture in the app's Pictures library, as the picker lists it. */
data class LibraryPicture(
    val id: String,
    val path: String,
    val name: String,
    val searchText: String,
    val isGif: Boolean,
)

/** Web search state for the picker's Web tab. */
data class WebSearchState(
    val query: String = "",
    val kind: WebSearchKind = WebSearchKind.All,
    val results: List<WebPicture> = emptyList(),
    val loading: Boolean = false,
    val searched: Boolean = false,
    val message: String = "",
    val downloading: Boolean = false,
)

@HiltViewModel
class PictureLibraryViewModel @Inject constructor(
    mediaRepository: MediaRepository,
    private val web: WebPictureSearch,
) : ViewModel() {
    private val adultAllowedOverride = MutableStateFlow<Boolean?>(null)
    val pictures: StateFlow<List<LibraryPicture>> = combine(mediaRepository.observeAll(), adultAllowedOverride) { all, allowed ->
            all.filter { (it.type == "image" || (it.type == "video" && "gifv" in it.tags)) && (allowed != false || "source_adult" !in it.tags) }.map { entity ->
                val path = mediaRepository.resolveFile(entity).absolutePath
                LibraryPicture(
                    id = entity.id,
                    path = path,
                    name = entity.displayName.ifBlank { entity.category.ifBlank { "Picture" } },
                    searchText = listOf(entity.displayName, entity.category, entity.tags).joinToString(" ").lowercase(),
                    isGif = entity.mimeType.contains("gif") || isGifPath(path),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _web = kotlinx.coroutines.flow.MutableStateFlow(WebSearchState())
    val webState: StateFlow<WebSearchState> = _web
    private var searchJob: kotlinx.coroutines.Job? = null

    fun setAdultAllowed(allowed: Boolean?) {
        adultAllowedOverride.value = allowed
    }

    fun setQuery(query: String) {
        _web.value = _web.value.copy(query = query)
        // Search as the writer types, once they pause.
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(SEARCH_DEBOUNCE_MS)
            runSearch()
        }
    }

    fun setKind(kind: WebSearchKind) {
        _web.value = _web.value.copy(kind = kind)
        search()
    }

    fun search() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch { runSearch() }
    }

    private suspend fun runSearch() {
        val state = _web.value
        _web.value = state.copy(loading = true, message = "")
        val outcome = runCatching {
            web.search(state.query, state.kind, adultAllowedOverride.value ?: SocialContentPolicy.explicit)
        }
        _web.value = _web.value.copy(
            loading = false,
            searched = true,
            results = outcome.getOrNull()?.results.orEmpty(),
            message = outcome.fold(
                onSuccess = { found ->
                    when {
                        found.results.isEmpty() && found.failedSources.isNotEmpty() ->
                            "Couldn't reach ${found.failedSources.joinToString()}. Check your connection."
                        found.results.isEmpty() -> "Nothing found. Try other words, or another tab above."
                        found.failedSources.isNotEmpty() -> "Skipped ${found.failedSources.joinToString()} (not reachable)."
                        else -> ""
                    }
                },
                onFailure = { "Search failed: ${it.message.orEmpty()}" },
            ),
        )
    }

    /** Downloads the picked results into the library, then hands their ids to [done]. */
    fun addWebPictures(picked: List<WebPicture>, done: (List<String>) -> Unit) {
        if (picked.isEmpty()) return
        viewModelScope.launch {
            _web.value = _web.value.copy(downloading = true, message = "")
            val saved = picked.mapNotNull { pic -> runCatching { web.download(pic, _web.value.query) }.getOrNull() }
            val failed = picked.size - saved.size
            _web.value = _web.value.copy(
                downloading = false,
                message = if (failed > 0) "$failed couldn't be downloaded." else "",
            )
            if (saved.isNotEmpty()) done(saved.map { it.id })
        }
    }

    fun sourceEnabled(source: WebSource): Boolean = web.enabled(source)
    fun key(id: String): String = web.key(id)
    fun setKey(id: String, value: String) {
        web.setKey(id, value)
        search()
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 550L
    }
}

/** Where the picked pictures come from. */
sealed interface PickedMedia {
    data class FromDevice(val uris: List<android.net.Uri>) : PickedMedia
    data class FromLibrary(val mediaIds: List<String>) : PickedMedia
}

/** Which tab the picker opens on. */
enum class PickerStart { Library, Gifs, Web }

private enum class PickerTab(val label: String) { Web("Search web"), Library("Library"), Gifs("My GIFs") }

/**
 * The attach sheet: search the web (memes, GIFs, pictures), your Pictures library, or the
 * device. Up to [limit] at once; web picks are downloaded into the library first.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PicturePickerSheet(
    limit: Int,
    start: PickerStart = PickerStart.Library,
    adultAllowed: Boolean? = null,
    onDismiss: () -> Unit,
    onPicked: (PickedMedia) -> Unit,
    surface: Color,
    text: Color,
    muted: Color,
    accent: Color,
) {
    val viewModel: PictureLibraryViewModel = hiltViewModel()
    val pictures by viewModel.pictures.collectAsState()
    val web by viewModel.webState.collectAsState()
    var tab by rememberSaveable {
        mutableStateOf(
            when (start) {
                PickerStart.Library -> PickerTab.Library
                PickerStart.Gifs, PickerStart.Web -> PickerTab.Web
            },
        )
    }
    androidx.compose.runtime.LaunchedEffect(adultAllowed) {
        viewModel.setAdultAllowed(adultAllowed)
        if (start == PickerStart.Gifs) viewModel.setKind(WebSearchKind.Gifs) else viewModel.search()
    }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    var webSelected by remember { mutableStateOf(listOf<WebPicture>()) }
    var keysOpen by rememberSaveable { mutableStateOf(false) }
    val max = limit.coerceAtLeast(1)
    val devicePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(max.coerceAtLeast(2)),
    ) { uris ->
        if (uris.isNotEmpty()) onPicked(PickedMedia.FromDevice(uris.take(max)))
    }
    val shown = pictures
        .filter { tab == PickerTab.Library || it.isGif }
        .filter { query.isBlank() || it.searchText.contains(query.lowercase()) }
    val count = if (tab == PickerTab.Web) webSelected.size else selected.size
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Add pictures & GIFs", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = text, modifier = Modifier.weight(1f))
                if (web.downloading) {
                    androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), color = accent, strokeWidth = 2.dp)
                } else if (count > 0) {
                    Text(
                        "Add $count",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(accent)
                            .clickable {
                                if (tab == PickerTab.Web) {
                                    viewModel.addWebPictures(webSelected) { ids -> onPicked(PickedMedia.FromLibrary(ids)) }
                                } else {
                                    onPicked(PickedMedia.FromLibrary(selected))
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 7.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PickerTab.entries.forEach { t ->
                    val on = t == tab
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (on) accent else muted.copy(alpha = 0.15f))
                            .clickable { tab = t }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            when (t) {
                                PickerTab.Web -> Icons.Filled.Search
                                PickerTab.Gifs -> Icons.Filled.Gif
                                PickerTab.Library -> Icons.Filled.PhotoLibrary
                            },
                            null,
                            tint = if (on) Color.White else text,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(t.label, color = if (on) Color.White else text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Outlined.AddPhotoAlternate,
                    "From device",
                    tint = accent,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .border(1.dp, accent.copy(alpha = 0.5f), CircleShape)
                        .clickable { devicePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                        .padding(8.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            // One search box: the web tab searches online, the others filter the library.
            SearchField(
                value = if (tab == PickerTab.Web) web.query else query,
                onChange = { if (tab == PickerTab.Web) viewModel.setQuery(it) else query = it },
                placeholder = if (tab == PickerTab.Web) "Search memes, GIFs and pictures" else "Search your pictures",
                onSearch = { if (tab == PickerTab.Web) viewModel.search() },
                text = text,
                muted = muted,
                accent = accent,
            )
            Spacer(Modifier.height(8.dp))
            if (tab == PickerTab.Web) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    WebSearchKind.entries.forEach { kind ->
                        val on = web.kind == kind
                        Text(
                            kind.label,
                            color = if (on) Color.White else text,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (on) accent.copy(alpha = 0.85f) else Color.Transparent)
                                .border(1.dp, if (on) accent else muted.copy(alpha = 0.4f), RoundedCornerShape(50))
                                .clickable { viewModel.setKind(kind) }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Sources",
                        color = accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { keysOpen = true }.padding(4.dp),
                    )
                }
                if (web.message.isNotBlank()) {
                    Text(web.message, color = muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
                Spacer(Modifier.height(6.dp))
                if (web.loading && web.results.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator(color = accent)
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(104.dp),
                    modifier = Modifier.fillMaxWidth().height(400.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(web.results, key = { it.id }) { picture ->
                        val index = webSelected.indexOfFirst { it.id == picture.id }
                        WebTile(
                            picture = picture,
                            index = index,
                            single = max == 1,
                            accent = accent,
                            onClick = {
                                webSelected = when {
                                    index >= 0 -> webSelected.filterNot { it.id == picture.id }
                                    max == 1 -> listOf(picture)
                                    webSelected.size < max -> webSelected + picture
                                    else -> webSelected
                                }
                            },
                        )
                    }
                }
            } else {
                if (shown.isEmpty()) {
                    Text(
                        when {
                            query.isNotBlank() -> "Nothing matches \"$query\"."
                            tab == PickerTab.Gifs -> "No GIFs saved yet. Search the web and the ones you add land here."
                            else -> "Your Pictures library is empty. Search the web, or add from the device."
                        },
                        color = muted,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(96.dp),
                    modifier = Modifier.fillMaxWidth().height(400.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(shown, key = { it.id }) { picture ->
                        val index = selected.indexOf(picture.id)
                        Box(
                            Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .border(3.dp, if (index >= 0) accent else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable {
                                    selected = when {
                                        index >= 0 -> selected - picture.id
                                        max == 1 -> listOf(picture.id)
                                        selected.size < max -> selected + picture.id
                                        else -> selected
                                    }
                                },
                        ) {
                            ChatImage(picture.path, Modifier.fillMaxSize())
                            SelectionBadge(index, max == 1, accent, Modifier.align(Alignment.TopEnd))
                        }
                    }
                }
            }
        }
    }
    if (keysOpen) {
        SourcesDialog(viewModel = viewModel, onDismiss = { keysOpen = false })
    }
}

@Composable
private fun SearchField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    onSearch: () -> Unit,
    text: Color,
    muted: Color,
    accent: Color,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(muted.copy(alpha = 0.15f)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = muted, modifier = Modifier.size(18.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = text, fontSize = 15.sp),
            cursorBrush = SolidColor(accent),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 10.dp),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(placeholder, color = muted, fontSize = 15.sp)
                inner()
            },
        )
        if (value.isNotEmpty()) {
            Icon(Icons.Filled.Close, "Clear", tint = muted, modifier = Modifier.size(18.dp).clickable { onChange("") })
        }
    }
}

@Composable
private fun WebTile(picture: WebPicture, index: Int, single: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x22888888))
            .border(3.dp, if (index >= 0) accent else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        val context = LocalContext.current
        // Some previews (Openverse's thumbnail proxy, notably) fail; fall back to the file itself.
        var useFull by remember(picture.id) { mutableStateOf(false) }
        val url = if (useFull) picture.fullUrl else picture.thumbUrl
        coil3.compose.AsyncImage(
            onError = { if (!useFull && picture.fullUrl != picture.thumbUrl) useFull = true },
            model = remember(url) {
                coil3.request.ImageRequest.Builder(context)
                    .data(url)
                    .httpHeaders(
                        coil3.network.NetworkHeaders.Builder()
                            .set("User-Agent", WebPictureSearch.USER_AGENT)
                            .build(),
                    )
                    .build()
            },
            contentDescription = picture.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color(0x99000000)).padding(horizontal = 5.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (picture.isGif) {
                Text("GIF", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(4.dp))
            }
            Text(picture.source, color = Color.White, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        SelectionBadge(index, single, accent, Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun SelectionBadge(index: Int, single: Boolean, accent: Color, modifier: Modifier) {
    if (index < 0) return
    Box(
        modifier.padding(5.dp).size(22.dp).clip(CircleShape).background(accent),
        contentAlignment = Alignment.Center,
    ) {
        if (single) {
            Icon(Icons.Filled.CheckCircle, null, tint = Color.White, modifier = Modifier.size(18.dp))
        } else {
            Text("${index + 1}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Which sites the web tab searches. Four work with no account; GIPHY, Tenor and Google
 * Images need a free key pasted here (stored encrypted, like the AI keys).
 */
@Composable
private fun SourcesDialog(viewModel: PictureLibraryViewModel, onDismiss: () -> Unit) {
    var giphy by remember { mutableStateOf(viewModel.key(KEY_GIPHY)) }
    var tenor by remember { mutableStateOf(viewModel.key(KEY_TENOR)) }
    var google by remember { mutableStateOf(viewModel.key(KEY_GOOGLE)) }
    var googleCx by remember { mutableStateOf(viewModel.key(KEY_GOOGLE_CX)) }
    var brave by remember { mutableStateOf(viewModel.key(KEY_BRAVE)) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Picture search sources") },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Always on, no account needed: Openverse (openly licensed pictures and GIFs), " +
                        "Wikimedia Commons, and Imgflip's popular meme templates. For the big " +
                        "reaction-GIF and meme libraries, add a GIPHY or Tenor key.",
                    fontSize = 13.sp,
                )
                Text("Optional — paste a free key to add:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                KeyField("GIPHY API key (developers.giphy.com)", giphy) { giphy = it }
                KeyField("Tenor API key (Google Cloud → Tenor API)", tenor) { tenor = it }
                KeyField("Google API key (Custom Search JSON API)", google) { google = it }
                KeyField("Google search engine ID (cx, with image search on)", googleCx) { googleCx = it }
                KeyField("Brave Image Search API key", brave) { brave = it }
                Text(
                    "Keys stay on this phone, encrypted. Pictures you add are saved to your " +
                        "Pictures library under Web, tagged with where they came from.",
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                viewModel.setKey(KEY_GIPHY, giphy)
                viewModel.setKey(KEY_TENOR, tenor)
                viewModel.setKey(KEY_GOOGLE, google)
                viewModel.setKey(KEY_GOOGLE_CX, googleCx)
                viewModel.setKey(KEY_BRAVE, brave)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun KeyField(label: String, value: String, onChange: (String) -> Unit) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        label = { Text(label, fontSize = 12.sp) },
        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
}
