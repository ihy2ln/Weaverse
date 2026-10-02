package com.ihy2ln.weaverse.feature.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ihy2ln.weaverse.core.ui.components.glassPanel
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusMd
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import kotlinx.coroutines.delay
import java.util.Calendar

/**
 * The splash behind Home: the chosen key art, full bleed and drifting slowly, washed in the
 * profile so the tabs at the top and the rows below stay legible. [model] overrides the
 * art (the user's own background picture); a null [brand] draws the scrim alone over the
 * shell wallpaper.
 */
@Composable
fun HomeSplashBackdrop(brand: ModeBrand?, modifier: Modifier = Modifier, model: Any? = null) {
    val background = inkTokens().background
    val light = background.luminance() > 0.5f
    Box(modifier) {
        if (brand != null) {
            val drift = rememberInfiniteTransition(label = "splashDrift")
            val zoom by drift.animateFloat(
                initialValue = 1.02f,
                targetValue = 1.1f,
                animationSpec = infiniteRepeatable(tween(32_000, easing = LinearEasing), RepeatMode.Reverse),
                label = "splashZoom",
            )
            ModeArtImage(
                brand = brand,
                model = model,
                fade = 1f,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.2f)
                },
            )
        }
        // Readable top for the status bar and tabs, open middle for the art, solid bottom
        // under the rows, the way a streaming app fades its billboard into the page.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to background.copy(alpha = if (light) 0.82f else 0.78f),
                    0.09f to background.copy(alpha = if (light) 0.5f else 0.42f),
                    0.2f to background.copy(alpha = if (light) 0.18f else 0.06f),
                    0.3f to background.copy(alpha = if (light) 0.3f else 0.22f),
                    0.5f to background.copy(alpha = if (light) 0.8f else 0.72f),
                    0.72f to background.copy(alpha = 0.95f),
                    1f to background,
                ),
            ),
        )
        // A left-side shade under the title block, like a billboard's text side.
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to background.copy(alpha = if (light) 0.45f else 0.5f),
                    0.65f to background.copy(alpha = 0f),
                ),
            ),
        )
    }
}

/**
 * Home: a title screen over the splash, then the modes as a streaming app's poster row, then
 * a "Continue in …" row per mode from the recent history. Everything is drawn from the
 * active profile's tokens, type and corners.
 */
@Composable
fun WeaverHomeScreen(
    modes: List<AppMode>,
    onMode: (AppMode) -> Unit,
    onRecent: (HomeItem) -> Unit,
    onCreate: (AppMode) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val shelves by viewModel.shelves.collectAsState()
    val art by viewModel.art.collectAsState()
    WeaverHomeContent(modes, shelves, art, onMode, onRecent, onCreate, viewModel::remove, viewModel::clear, modifier)
}

@Composable
fun WeaverHomeContent(
    modes: List<AppMode>,
    shelves: Map<String, List<HomeItem>>,
    art: Map<String, String>,
    onMode: (AppMode) -> Unit,
    onRecent: (HomeItem) -> Unit,
    onCreate: (AppMode) -> Unit,
    onRemove: (HomeItem) -> Unit,
    onClear: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latest = shelves.values.flatten().maxByOrNull { it.accessedAt }
    val lastMode = latest?.let { item -> AppMode.entries.firstOrNull { it.name == item.mode } }
    var clearMode by rememberSaveable { mutableStateOf<String?>(null) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= 700.dp
        // Short screens (a phone on its side) shrink the billboard and posters so the modes
        // still show without scrolling.
        val short = maxHeight < 520.dp
        val byWidth = if (wide) 210.dp else ((maxWidth - 48.dp) / 2.15f).coerceIn(150.dp, 220.dp)
        val posterWidth = minOf(byWidth, maxHeight * 0.6f * (2f / 3f)).coerceAtLeast(110.dp)
        val heroHeight = if (short) {
            (maxHeight * 0.6f).coerceAtLeast(190.dp)
        } else {
            (maxHeight * if (wide) 0.5f else 0.46f).coerceIn(300.dp, 520.dp)
        }
        LazyColumn(
            Modifier.fillMaxSize().testTag("weaver-home"),
            contentPadding = PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            item(key = "hero") {
                HomeHero(
                    latest = latest,
                    lastMode = lastMode,
                    modes = modes,
                    onResume = { latest?.let(onRecent) ?: onMode(modes.firstOrNull() ?: AppMode.Novel) },
                    onCreate = onCreate,
                    modifier = Modifier.fillMaxWidth().height(heroHeight),
                )
            }
            item(key = "modes") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RowHeading("Choose a mode", "${modes.size} modes")
                    val listState = rememberLazyListState()
                    LazyRow(
                        state = listState,
                        flingBehavior = rememberSnapFlingBehavior(listState),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.testTag("home-modes"),
                    ) {
                        items(modes, key = { it.name }) { mode ->
                            val recents = shelves[mode.name].orEmpty()
                            ModePoster(
                                mode = mode,
                                recentCount = recents.size,
                                lastTitle = recents.firstOrNull()?.title,
                                isLast = mode == lastMode,
                                width = posterWidth,
                                onClick = { onMode(mode) },
                            )
                        }
                    }
                }
            }
            modes.forEach { mode ->
                val entries = shelves[mode.name].orEmpty()
                if (entries.isNotEmpty()) item(key = "recent-${mode.name}") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        RowHeading(
                            title = "Continue in ${ModeArt.of(mode).title}",
                            action = "See all",
                            onAction = { onMode(mode) },
                            onClear = { clearMode = mode.name },
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(entries, key = { it.key }) { item ->
                                RecentPoster(
                                    item = item,
                                    art = art[item.mediaId] ?: item.remoteCover,
                                    width = (posterWidth * 0.72f).coerceAtLeast(112.dp),
                                    onOpen = { onRecent(item) },
                                    onRemove = { onRemove(item) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    clearMode?.let { mode ->
        val label = AppMode.entries.firstOrNull { it.name == mode }?.let { ModeArt.of(it).title } ?: mode
        AlertDialog(
            onDismissRequest = { clearMode = null },
            title = { Text("Clear $label history?") },
            text = { Text("Your content stays in its library. Opening it again adds it back to Home.") },
            confirmButton = { TextButton(onClick = { onClear(mode); clearMode = null }) { Text("Clear history") } },
            dismissButton = { TextButton(onClick = { clearMode = null }) { Text("Cancel") } },
        )
    }
}

/** Title-screen block at the foot of the splash: brand, greeting, Resume and Create. */
@Composable
private fun HomeHero(
    latest: HomeItem?,
    lastMode: AppMode?,
    modes: List<AppMode>,
    onResume: () -> Unit,
    onCreate: (AppMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = inkTokens()
    val dark = tokens.background.luminance() < 0.5f
    val shadow = if (dark) Shadow(Color.Black.copy(alpha = 0.55f), blurRadius = 18f) else null
    Box(modifier, contentAlignment = Alignment.BottomStart) {
        Column(
            Modifier.padding(horizontal = 24.dp).widthIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeaverseMark(Modifier.size(30.dp))
                Text(
                    "WEAVERSE",
                    color = tokens.primaryText,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 6.sp,
                    fontSize = 14.sp,
                    style = TextStyle(shadow = shadow),
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            Text(
                greeting().uppercase(),
                color = tokens.activePill,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                fontSize = 11.sp,
                style = TextStyle(shadow = shadow),
            )
            Text(
                ModeArt.home.tagline,
                style = MaterialTheme.typography.displaySmall.copy(shadow = shadow),
                fontWeight = FontWeight.Bold,
                color = tokens.primaryText,
            )
            Text(
                if (latest != null) "Last time: ${latest.title} · ${lastMode?.let { ModeArt.of(it).title } ?: latest.badge}"
                else "Choose a mode below to begin your first world.",
                style = MaterialTheme.typography.bodyMedium.copy(shadow = shadow),
                color = tokens.primaryText.copy(alpha = 0.82f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                val shape = RoundedCornerShape(inkRadiusMd().coerceAtMost(12.dp))
                Row(
                    Modifier
                        .clip(shape)
                        .background(tokens.primaryText)
                        .clickable(onClick = onResume)
                        .heightIn(min = 46.dp)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = tokens.background, modifier = Modifier.size(24.dp))
                    Text(
                        if (latest != null) "Resume" else "Start",
                        color = tokens.background,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                val creatable = modes.filter { it in CreatableModes }
                if (creatable.isNotEmpty()) {
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        Row(
                            Modifier
                                .glassPanel(shape, tokens.hover)
                                .clickable { menu = true }
                                .heightIn(min = 46.dp)
                                .padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Add, null, tint = tokens.primaryText, modifier = Modifier.size(22.dp))
                            Text("Create", color = tokens.primaryText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(start = 6.dp))
                        }
                        DropdownMenu(menu, { menu = false }) {
                            creatable.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(CreateLabels[mode].orEmpty()) },
                                    leadingIcon = { Icon(ModeArt.of(mode).icon, null) },
                                    onClick = { menu = false; onCreate(mode) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val CreatableModes = setOf(AppMode.Novel, AppMode.Roleplay, AppMode.Storyboard)
private val CreateLabels = mapOf(
    AppMode.Novel to "New novel",
    AppMode.Roleplay to "New campaign",
    AppMode.Storyboard to "New manga project",
)

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Burning the midnight oil"
}

@Composable
private fun RowHeading(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
) {
    val tokens = inkTokens()
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = tokens.primaryText,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (action != null) {
            Text(
                action,
                color = if (onAction != null) tokens.activePill else tokens.secondaryText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .then(if (onAction != null) Modifier.clickable(onClick = onAction) else Modifier)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
        if (onClear != null) {
            var menu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreHoriz, "$title options", tint = tokens.secondaryText) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Clear recent history") }, onClick = { menu = false; onClear() })
                }
            }
        }
    }
}

/** A mode as a streaming original's poster: key art, glyph, eyebrow, title, progress. */
@Composable
private fun ModePoster(
    mode: AppMode,
    recentCount: Int,
    lastTitle: String?,
    isLast: Boolean,
    width: Dp,
    onClick: () -> Unit,
) {
    val brand = ModeArt.of(mode)
    val tokens = inkTokens()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        when {
            pressed -> 0.96f
            focused -> 1.04f
            else -> 1f
        },
        tween(160),
        label = "modePosterScale",
    )
    val shape = RoundedCornerShape(inkRadiusMd() * 1.5f)
    val lit = isLast || focused
    ModeArtImage(
        brand = brand,
        fade = 0.38f,
        modifier = Modifier
            .width(width)
            .aspectRatio(2f / 3f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (lit) 20.dp else 10.dp, shape, ambientColor = tokens.activePill, spotColor = tokens.activePill)
            .clip(shape)
            .border(
                if (lit) 2.dp else 1.dp,
                if (lit) tokens.activePill else Color.White.copy(alpha = 0.12f),
                shape,
            )
            .clickable(interactionSource = interaction, indication = ripple(), onClick = onClick)
            .semantics { contentDescription = "Open ${brand.title}" }
            .testTag("mode-${mode.name}"),
    ) {
        Box(
            Modifier
                .padding(12.dp)
                .size(34.dp)
                .clip(CircleShape)
                .background(tokens.background.copy(alpha = 0.55f))
                .border(1.dp, tokens.activePill.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(brand.icon, null, tint = tokens.activePill, modifier = Modifier.size(18.dp))
        }
        if (isLast) {
            Text(
                "CONTINUE",
                color = tokens.activePillLabel,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                letterSpacing = 1.5.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(tokens.activePill)
                    .padding(horizontal = 7.dp, vertical = 4.dp),
            )
        }
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(brand.eyebrow, color = tokens.activePill, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.6.sp, maxLines = 1)
            Text(
                brand.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = tokens.primaryText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when {
                    lastTitle != null && recentCount > 1 -> "$lastTitle · +${recentCount - 1} more"
                    lastTitle != null -> lastTitle
                    else -> brand.tagline
                },
                color = tokens.primaryText.copy(alpha = 0.78f),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One recently opened book, chat, manga, note or thread. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentPoster(item: HomeItem, art: String?, width: Dp, onOpen: () -> Unit, onRemove: () -> Unit) {
    val tokens = inkTokens()
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(inkRadiusMd())
    val brand = AppMode.entries.firstOrNull { it.name == item.mode }?.let(ModeArt::of) ?: ModeArt.home
    Column(Modifier.width(width)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape)
                .background(Brush.linearGradient(listOf(tokens.hover, tokens.panel)))
                .border(1.dp, tokens.hairline.copy(alpha = 0.6f), shape)
                .combinedClickable(onClick = onOpen, onLongClick = { menu = true }),
        ) {
            if (!art.isNullOrBlank()) {
                AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                // No cover yet: the mode's own art with the title's initials, so the row
                // still reads as a shelf of posters rather than blank tiles.
                ModeArtImage(brand, Modifier.fillMaxSize(), fade = 0.2f) {
                    Text(
                        item.title.split(' ').take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString(""),
                        color = tokens.primaryText.copy(alpha = 0.85f),
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Light,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))),
            )
            Text(
                item.badge.uppercase(),
                color = Color.White,
                fontSize = 9.sp,
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
            )
            Box(Modifier.align(Alignment.TopEnd)) {
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Remove from recents") }, onClick = { menu = false; onRemove() })
                }
            }
        }
        Text(
            item.title,
            color = tokens.primaryText,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

/**
 * The launch splash: the Weaverse mark and wordmark over the same art Home sits on, then a
 * fade into Home. Tap to skip. [onDone] fires once.
 */
@Composable
fun HomeIntro(brand: ModeBrand?, onDone: () -> Unit, modifier: Modifier = Modifier, model: Any? = null) {
    val tokens = inkTokens()
    val reveal = remember { Animatable(0f) }
    val exit = remember { Animatable(1f) }
    var skipped by remember { mutableStateOf(false) }
    LaunchedEffect(skipped) {
        if (!skipped) {
            reveal.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
            delay(650)
        }
        exit.animateTo(0f, tween(if (skipped) 180 else 480))
        onDone()
    }
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = exit.value }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { skipped = true }
            .testTag("home-intro"),
        contentAlignment = Alignment.Center,
    ) {
        if (brand != null) ModeArtImage(brand, Modifier.fillMaxSize(), model = model, fade = 1f)
        Box(Modifier.fillMaxSize().background(tokens.background.copy(alpha = 0.72f)))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            WeaverseMark(
                Modifier
                    .size(108.dp)
                    .graphicsLayer {
                        val s = 0.82f + 0.18f * reveal.value
                        scaleX = s
                        scaleY = s
                        alpha = reveal.value
                    },
            )
            Text(
                "WEAVERSE",
                color = tokens.primaryText,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                letterSpacing = (16f - 7f * reveal.value).sp,
                modifier = Modifier.padding(top = 8.dp).graphicsLayer { alpha = reveal.value },
            )
            Spacer(Modifier.height(18.dp))
            // A thread drawn across, the "weave" loading in.
            Box(Modifier.width(160.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(tokens.hairline.copy(alpha = 0.5f))) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = reveal.value
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                        }
                        .background(Brush.horizontalGradient(listOf(tokens.activePill.copy(alpha = 0.2f), tokens.activePill))),
                )
            }
        }
    }
}
