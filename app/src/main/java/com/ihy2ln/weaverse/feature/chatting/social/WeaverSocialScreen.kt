package com.ihy2ln.weaverse.feature.chatting.social

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import com.ihy2ln.weaverse.core.roleplay.avatarColorHexFor
import com.ihy2ln.weaverse.core.ui.theme.LocalGlassClarity
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.resolveSectionColor
import com.ihy2ln.weaverse.core.ui.util.parseHexColor
import com.ihy2ln.weaverse.data.settings.AppearanceOverrides
import com.ihy2ln.weaverse.feature.chatting.DiscordChatScreen
import com.ihy2ln.weaverse.feature.chatting.DiscordChatViewModel
import com.ihy2ln.weaverse.feature.chatting.DiscordRoomUi
import com.ihy2ln.weaverse.feature.chatting.DiscordStatus
import com.ihy2ln.weaverse.feature.chatting.ROOM_KIND_CHANNEL
import com.ihy2ln.weaverse.feature.chatting.presenceFor
import com.ihy2ln.weaverse.feature.chatting.media.AttachmentStrip
import com.ihy2ln.weaverse.feature.chatting.media.ChatImage
import com.ihy2ln.weaverse.feature.chatting.media.MAX_ATTACHMENTS
import com.ihy2ln.weaverse.feature.chatting.media.MediaGrid
import com.ihy2ln.weaverse.feature.chatting.media.MediaViewer
import com.ihy2ln.weaverse.feature.chatting.media.PickedMedia
import com.ihy2ln.weaverse.feature.chatting.media.PickerStart
import com.ihy2ln.weaverse.feature.chatting.media.PicturePickerSheet
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.ImageSearch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** WeaverSocial's palette: Discord's layered greys, Twitter's clean type, one Weaverse violet. */
private data class WsColors(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val raised: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val bubble: Color,
    val accent: Color,
    val blue: Color,
) {
    val pink = Color(0xFFF91880)
    val green = Color(0xFF00BA7C)
    val red = Color(0xFFF23F43)
    val idle = Color(0xFFF0B232)
    val brand = Brush.linearGradient(listOf(accent, blue))
}

@Composable
private fun rememberWsColors(appearance: AppearanceOverrides, wallpaperVisible: Boolean): WsColors {
    val tokens = inkTokens()
    val clarity = LocalGlassClarity.current
    val wallpaperFactor = if (wallpaperVisible) 1f - clarity * 0.8f else 1f
    fun section(section: com.ihy2ln.weaverse.data.settings.SectionAppearance, fallback: Color) =
        resolveSectionColor(section, fallback).let { it.copy(alpha = it.alpha * wallpaperFactor) }
    return WsColors(
        dark = tokens.background.luminance() < 0.5f,
        bg = section(appearance.chrome, tokens.background),
        surface = section(appearance.content, tokens.panel),
        raised = section(appearance.page, tokens.page),
        border = section(appearance.rail, tokens.hairline),
        text = tokens.primaryText,
        muted = tokens.secondaryText,
        bubble = section(appearance.chatBubble, tokens.hover),
        accent = tokens.activePill,
        blue = androidx.compose.material3.MaterialTheme.colorScheme.tertiary,
    )
}

private enum class WsTab(val label: String, val icon: ImageVector, val selected: ImageVector) {
    Home("Home", Icons.Outlined.Home, Icons.Filled.Home),
    Social("Social", Icons.Outlined.Share, Icons.Filled.Public),
    Servers("Servers", Icons.Outlined.Forum, Icons.Filled.Forum),
    Explore("Explore", Icons.Outlined.Explore, Icons.Filled.Explore),
    Alerts("Alerts", Icons.Outlined.Notifications, Icons.Filled.Notifications),
    You("You", Icons.Outlined.PersonOutline, Icons.Filled.Person),
}

private val Feelings = listOf(
    "😊" to "happy", "🥰" to "loved", "😇" to "blessed", "😎" to "cool", "🤩" to "excited",
    "😌" to "relaxed", "🤔" to "thoughtful", "😴" to "tired", "😢" to "sad", "😤" to "determined",
    "🥳" to "festive", "😋" to "hungry", "💪" to "strong", "🙏" to "thankful", "😬" to "nervous",
)

private fun feelingEmoji(feeling: String): String =
    Feelings.firstOrNull { it.second.equals(feeling, true) }?.first ?: "🙂"

private fun discordStatusFor(name: String): DiscordStatus = presenceFor(name)

/**
 * WeaverSocial: the Chatting mode as one social network. The home feed borrows Twitter's
 * fast timeline, reshares, quotes and view counts and Facebook's stories, feelings,
 * reactions and comment threads; Servers is the full Discord workspace; profiles carry
 * all three — a cover photo, follower counts and a presence status with roles.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeaverSocialScreen(
    selectedServerId: String?,
    selectedRoomId: String?,
    onServerSelect: (String?) -> Unit,
    onRoomSelect: (String?) -> Unit,
    onOpenFriends: () -> Unit,
    appearance: AppearanceOverrides = AppearanceOverrides(),
    wallpaperVisible: Boolean = true,
    viewModel: SocialFeedViewModel = hiltViewModel(key = PLATFORM_WEAVERSOCIAL),
    discordViewModel: DiscordChatViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.bind(PLATFORM_WEAVERSOCIAL) }
    val state by viewModel.uiState.collectAsState()
    val servers by discordViewModel.uiState.collectAsState()
    val c = rememberWsColors(appearance, wallpaperVisible)
    // Opening a conversation from Recents lands on the Servers tab.
    var tab by rememberSaveable { mutableStateOf(if (selectedRoomId != null) WsTab.Servers else WsTab.Home) }
    LaunchedEffect(selectedRoomId) { if (selectedRoomId != null) tab = WsTab.Servers }
    var stack by rememberSaveable { mutableStateOf(listOf<String>()) }
    var composer by rememberSaveable { mutableStateOf(false) }
    var quoteId by rememberSaveable { mutableStateOf<String?>(null) }
    var storyIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val push: (String) -> Unit = { stack = stack + it }
    val closeComposer = { composer = false; quoteId = null; viewModel.clearImage() }
    BackHandler(stack.isNotEmpty() && !composer && storyIndex == null && tab != WsTab.Servers) { stack = stack.dropLast(1) }
    BackHandler(composer) { closeComposer() }
    BackHandler(storyIndex != null) { storyIndex = null }

    // null = closed; otherwise the tab the picker opens on.
    var pickerGifs by remember { mutableStateOf<PickerStart?>(null) }
    LaunchedEffect(state.mediaPickRequestId) {
        if (state.mediaPickRequestId > 0) pickerGifs = PickerStart.Library
    }
    var viewing by remember { mutableStateOf<Triple<List<String>, Int, String>?>(null) }
    LaunchedEffect(state.castLoaded) {
        if (state.castLoaded && state.posts.isEmpty()) viewModel.refreshFeed(5)
    }

    val openRoom: (DiscordRoomUi) -> Unit = { room ->
        onServerSelect(room.bookId)
        onRoomSelect(room.chatId)
        stack = emptyList()
        tab = WsTab.Servers
    }
    val message: (String?) -> Unit = { characterId ->
        onRoomSelect(null)
        onServerSelect(null)
        if (characterId != null) discordViewModel.openDirectMessage(characterId) else discordViewModel.openDmContacts()
        stack = emptyList()
        tab = WsTab.Servers
    }
    val actions = WsActions(
        onOpenPost = { push("post:$it") },
        onOpenProfile = { push("profile:" + (it ?: "you")) },
        onReact = viewModel::react,
        onReshare = viewModel::toggleRepost,
        onQuote = { quoteId = it; composer = true },
        onSave = viewModel::toggleBookmark,
        onDelete = viewModel::delete,
        onMessage = message,
        onFollow = viewModel::toggleFollow,
        onBlock = viewModel::block,
        onMute = viewModel::mute,
        onNotInterested = viewModel::hidePost,
        onOpenSafety = { push("safety") },
    )
    val stories = state.posts.filter { !it.isYou && it.repostOf == null }.distinctBy { it.authorCharacterId }.take(12)
    val unreadRooms = servers.recentConversations.filter { it.unread > 0 }
    val serverUnread = servers.serverUnread.values.sum() + servers.dmUnread

    CompositionLocalProvider(
        LocalOpenMedia provides { paths, index, caption -> viewing = Triple(paths, index, caption) },
        LocalPickMedia provides { start -> pickerGifs = start },
    ) {
    Box(Modifier.fillMaxSize().background(c.bg)) {
        val imeOpen = WindowInsets.isImeVisible
        Column(Modifier.fillMaxSize().then(if (tab == WsTab.Servers) Modifier else Modifier.imePadding())) {
            Box(
                Modifier
                    .weight(1f)
                    // The Discord workspace pads for the navigation bar itself; the bar below
                    // already sits above it, so it must not pad twice.
                    .consumeWindowInsets(WindowInsets.navigationBars),
            ) {
                val top = stack.lastOrNull()
                when {
                    tab == WsTab.Servers -> DiscordChatScreen(
                        selectedServerId = selectedServerId,
                        selectedRoomId = selectedRoomId,
                        onServerSelect = onServerSelect,
                        onRoomSelect = onRoomSelect,
                        onOpenFriends = onOpenFriends,
                        viewModel = discordViewModel,
                        appearance = appearance,
                        wallpaperVisible = wallpaperVisible,
                    )
                    top?.startsWith("post:") == true -> PostDetail(
                        postId = top.removePrefix("post:"),
                        state = state,
                        c = c,
                        actions = actions,
                        onBack = { stack = stack.dropLast(1) },
                        onReply = { parent, text, to -> viewModel.reply(parent, text, to) },
                        onRemoveAttachment = viewModel::removeAttachment,
                    )
                    top == "safety" -> SafetyScreen(
                        state = state,
                        c = c,
                        viewModel = viewModel,
                        onBack = { stack = stack.dropLast(1) },
                        onOpenProfile = { push("profile:$it") },
                    )
                    top?.startsWith("profile:") == true -> Profile(
                        who = top.removePrefix("profile:"),
                        state = state,
                        c = c,
                        actions = actions,
                        onBack = { stack = stack.dropLast(1) },
                        onCompose = { composer = true },
                    )
                    tab == WsTab.Home || tab == WsTab.Social -> HomeFeed(
                        state = state,
                        c = c,
                        stories = stories,
                        activeRooms = servers.recentConversations.take(10),
                        overview = tab == WsTab.Home,
                        actions = actions,
                        onCompose = { composer = true },
                        onPhoto = { composer = true; pickerGifs = PickerStart.Library },
                        onGif = { composer = true; pickerGifs = PickerStart.Gifs },
                        onStory = { storyIndex = it },
                        onRefresh = { viewModel.refreshFeed() },
                        onLoadMore = { viewModel.refreshFeed(6) },
                        onRoom = openRoom,
                        onSearch = { tab = WsTab.Explore },
                        onOpenAlerts = { tab = WsTab.Alerts },
                        onMessages = { message(null) },
                        onSafety = { push("safety") },
                        messagesUnread = servers.dmUnread,
                    )
                    tab == WsTab.Explore -> Explore(
                        state = state,
                        c = c,
                        actions = actions,
                        servers = servers.servers.map { Triple(it.bookId, it.title, it.colorHex) },
                        serverUnread = servers.serverUnread,
                        onServer = { id -> onServerSelect(id); stack = emptyList(); tab = WsTab.Servers },
                    )
                    tab == WsTab.Alerts -> Alerts(
                        state = state,
                        c = c,
                        unreadRooms = unreadRooms,
                        onRoom = openRoom,
                        onOpen = { id -> id?.let { push("post:$it") } },
                    )
                    else -> Profile(
                        who = "you",
                        state = state,
                        c = c,
                        actions = actions,
                        onBack = null,
                        onCompose = { composer = true },
                    )
                }
                if (tab != WsTab.Servers) {
                    if (state.notice.isNotBlank()) {
                        LaunchedEffect(state.notice) {
                            kotlinx.coroutines.delay(3_500)
                            viewModel.dismissNotice()
                        }
                        Text(
                            state.notice,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(start = 16.dp, end = 16.dp, bottom = 84.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(c.red.copy(alpha = 0.92f))
                                .clickable { viewModel.dismissNotice() }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                    if (state.mediaNotice.isNotBlank()) {
                        Text(state.mediaNotice, color = c.text, fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp, start = 12.dp, end = 12.dp)
                                .clip(RoundedCornerShape(8.dp)).background(c.raised)
                                .clickable { viewModel.dismissMediaNotice() }
                                .padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                    if (state.generating) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter), color = c.accent, trackColor = Color.Transparent)
                    }
                    if (state.error.isNotBlank()) {
                        Text(
                            state.error,
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF2B2D36))
                                .clickable { viewModel.dismissError() }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                    if (stack.isEmpty() && (tab == WsTab.Home || tab == WsTab.Social || tab == WsTab.You)) {
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                                .size(56.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(c.brand)
                                .clickable { quoteId = null; composer = true },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Add, "Post", tint = Color.White, modifier = Modifier.size(28.dp)) }
                    }
                }
            }
            // The keyboard needs the Servers composer's full height, so the bar steps aside.
            if (!(imeOpen && tab == WsTab.Servers)) {
                BottomBar(
                    tab = tab,
                    c = c,
                    badges = mapOf(WsTab.Servers to serverUnread, WsTab.Alerts to state.notifications.size.coerceAtMost(9) + unreadRooms.size),
                    onSelect = { t -> stack = emptyList(); tab = t },
                )
            }
        }
        if (composer) {
            Composer(
                state = state,
                c = c,
                quote = quoteId?.let { state.allById[it] },
                onRemoveAttachment = viewModel::removeAttachment,
                onCancel = closeComposer,
                onPost = { text, feeling ->
                    viewModel.post(text, feeling, quoteOf = quoteId)
                    composer = false
                    quoteId = null
                },
            )
        }
        storyIndex?.let { index ->
            StoryViewer(
                stories = stories,
                start = index,
                onClose = { storyIndex = null },
                onReply = { post -> storyIndex = null; push("post:${post.id}") },
            )
        }
    }
    }
    pickerGifs?.let { start ->
        PicturePickerSheet(
            limit = (MAX_ATTACHMENTS - state.pendingImagePaths.size).coerceAtLeast(1),
            start = start,
            adultAllowed = state.safety.adultEnabled,
            onDismiss = { pickerGifs = null },
            onPicked = { picked ->
                pickerGifs = null
                when (picked) {
                    is PickedMedia.FromDevice -> viewModel.attachFromDevice(picked.uris)
                    is PickedMedia.FromLibrary -> viewModel.attachFromLibrary(picked.mediaIds)
                }
            },
            surface = c.surface,
            text = c.text,
            muted = c.muted,
            accent = c.accent,
        )
    }
    viewing?.let { (paths, index, caption) ->
        MediaViewer(paths = paths, start = index, caption = caption, onClose = { viewing = null })
    }
}

/** Opens the full-screen viewer; provided once by [WeaverSocialScreen] for every card. */
private val LocalOpenMedia = staticCompositionLocalOf<(List<String>, Int, String) -> Unit> { { _, _, _ -> } }

/** Opens the picture picker on the given tab. */
private val LocalPickMedia = staticCompositionLocalOf<(PickerStart) -> Unit> { { } }

private class WsActions(
    val onOpenPost: (String) -> Unit,
    val onOpenProfile: (String?) -> Unit,
    val onReact: (String, String) -> Unit,
    val onReshare: (String) -> Unit,
    val onQuote: (String) -> Unit,
    val onSave: (String) -> Unit,
    val onDelete: (String) -> Unit,
    val onMessage: (String?) -> Unit,
    val onFollow: (String) -> Unit,
    val onBlock: (String, Boolean) -> Unit,
    val onMute: (String, Boolean) -> Unit,
    val onNotInterested: (String) -> Unit,
    val onOpenSafety: () -> Unit,
)

// ------------------------------------------------------------------ chrome

@Composable
private fun BottomBar(tab: WsTab, c: WsColors, badges: Map<WsTab, Int>, onSelect: (WsTab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(c.surface).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        Row(Modifier.fillMaxWidth().height(58.dp)) {
            WsTab.entries.forEach { t ->
                val selected = t == tab
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable { onSelect(t) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) c.accent.copy(alpha = 0.16f) else Color.Transparent)
                                .padding(horizontal = 14.dp, vertical = 3.dp),
                        ) {
                            Icon(
                                if (selected) t.selected else t.icon,
                                t.label,
                                tint = if (selected) c.accent else c.muted,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        val badge = badges[t] ?: 0
                        if (badge > 0) {
                            Text(
                                if (badge > 99) "99+" else badge.toString(),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 4.dp, y = (-4).dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(c.red)
                                    .padding(horizontal = 5.dp),
                            )
                        }
                    }
                    Text(
                        t.label,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) c.text else c.muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun Wordmark(c: WsColors, size: Int = 24) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size((size + 6).dp).clip(RoundedCornerShape(9.dp)).background(c.brand),
            contentAlignment = Alignment.Center,
        ) { Text("W", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = (size - 4).sp) }
        Spacer(Modifier.width(8.dp))
        Text(
            buildAnnotatedString {
                pushStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = c.text))
                append("Weaver")
                pop()
                pushStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, brush = c.brand))
                append("Social")
                pop()
            },
            fontSize = size.sp,
            letterSpacing = (-0.5).sp,
        )
    }
}

@Composable
private fun RoundButton(icon: ImageVector, description: String, c: WsColors, badge: Int = 0, onClick: () -> Unit) {
    Box {
        Box(
            Modifier.padding(horizontal = 3.dp).size(38.dp).clip(CircleShape).background(c.raised).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, description, tint = c.text, modifier = Modifier.size(20.dp)) }
        if (badge > 0) {
            Box(Modifier.align(Alignment.TopEnd).size(10.dp).clip(CircleShape).background(c.red))
        }
    }
}

@Composable
private fun Hairline(c: WsColors) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
}

/** Avatar with a Discord presence dot. */
@Composable
private fun PresenceAvatar(name: String, colorHex: String, size: Dp, c: WsColors, status: DiscordStatus? = discordStatusFor(name), ring: Color = c.surface) {
    Box {
        SocialAvatar(name, colorHex, size)
        if (status != null) {
            val dot = (size.value * 0.3f).coerceAtLeast(10f).dp
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(dot)
                    .clip(CircleShape)
                    .background(ring)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(
                        when (status) {
                            DiscordStatus.Online -> c.green
                            DiscordStatus.Idle -> c.idle
                            DiscordStatus.DoNotDisturb -> c.red
                            DiscordStatus.Invisible -> c.muted
                        },
                    ),
            )
        }
    }
}

// ------------------------------------------------------------------- home

@Composable
private fun HomeFeed(
    state: SocialUiState,
    c: WsColors,
    stories: List<SocialPostUi>,
    activeRooms: List<DiscordRoomUi>,
    overview: Boolean,
    actions: WsActions,
    onCompose: () -> Unit,
    onPhoto: () -> Unit,
    onGif: () -> Unit,
    onStory: (Int) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRoom: (DiscordRoomUi) -> Unit,
    onSearch: () -> Unit,
    onOpenAlerts: () -> Unit,
    onMessages: () -> Unit,
    onSafety: () -> Unit,
    messagesUnread: Int,
) {
    var following by rememberSaveable { mutableStateOf(false) }
    val posts = if (following) state.posts.filter { it.isYou || it.authorCharacterId in state.followingIds } else state.posts
    val listState = rememberLazyListState()
    var requestedAtPostCount by remember { mutableIntStateOf(-1) }
    LaunchedEffect(listState, posts.size, state.generating) {
        snapshotFlow {
            val layout = listState.layoutInfo
            layout.totalItemsCount > 0 &&
                (layout.visibleItemsInfo.lastOrNull()?.index ?: 0) >= layout.totalItemsCount - 3
        }.distinctUntilChanged().collect { nearEnd ->
            if (nearEnd && posts.isNotEmpty() && !state.generating && requestedAtPostCount != posts.size) {
                requestedAtPostCount = posts.size
                onLoadMore()
            }
        }
    }
    @OptIn(ExperimentalMaterial3Api::class)
    PullToRefreshBox(
        isRefreshing = state.generating,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 96.dp)) {
        item(key = "header") {
            Column(Modifier.fillMaxWidth().background(c.surface)) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        if (overview) Wordmark(c, size = 21) else Text("Social", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = c.text)
                        Spacer(Modifier.width(7.dp))
                        Text("18+", color = if (state.safety.adultEnabled) Color.White else c.muted,
                            fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                .background(if (state.safety.adultEnabled) c.red else c.raised)
                                .clickable(onClick = onSafety).padding(horizontal = 5.dp, vertical = 3.dp))
                    }
                    RoundButton(Icons.Filled.Search, "Search", c, onClick = onSearch)
                    RoundButton(Icons.Outlined.MailOutline, "Messages", c, badge = messagesUnread, onClick = onMessages)
                }
                Row(Modifier.fillMaxWidth().height(44.dp)) {
                    listOf("For you" to false, "Following" to true).forEach { (label, value) ->
                        val selected = following == value
                        Box(Modifier.weight(1f).fillMaxHeight().clickable { following = value }, contentAlignment = Alignment.Center) {
                            Text(label, fontSize = 15.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) c.text else c.muted)
                            if (selected) {
                                Box(Modifier.align(Alignment.BottomCenter).width(56.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(c.brand))
                            }
                        }
                    }
                }
                Hairline(c)
            }
        }
        item(key = "composer") {
            Column(Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clip(CircleShape).clickable { actions.onOpenProfile(null) }) {
                        PresenceAvatar(state.personaName, avatarColorHexFor(state.personaName, null), 40.dp, c, DiscordStatus.Online)
                    }
                    Text(
                        "What's happening in your worlds?",
                        color = c.muted,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(c.raised)
                            .clickable(onClick = onCompose)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
                Row(Modifier.padding(start = 50.dp, top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ComposerChip(Icons.Outlined.Image, "Photo", c.green, c, onPhoto)
                    ComposerChip(Icons.Filled.Gif, "GIF", c.accent, c, onGif)
                    ComposerChip(Icons.Outlined.EmojiEmotions, "Feeling", c.idle, c, onCompose)
                    ComposerChip(Icons.Outlined.Refresh, "Refresh", c.blue, c, onRefresh)
                }
            }
            Hairline(c)
        }
        if (overview) item(key = "stories") {
            LazyRow(
                Modifier.fillMaxWidth().background(c.surface).padding(vertical = 10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "you") {
                    Column(
                        Modifier.width(96.dp).height(160.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, c.border, RoundedCornerShape(14.dp)).clickable(onClick = onCompose),
                    ) {
                        Box(
                            Modifier.fillMaxWidth().height(112.dp).background(
                                Brush.verticalGradient(listOf(parseHexColor(avatarColorHexFor(state.personaName, null), c.accent), c.surface)),
                            ),
                            contentAlignment = Alignment.Center,
                        ) { SocialAvatar(state.personaName, avatarColorHexFor(state.personaName, null), 48.dp) }
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                            Box(
                                Modifier.offset(y = (-14).dp).size(28.dp).clip(CircleShape).background(c.surface).padding(3.dp).clip(CircleShape).background(c.brand),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                            Text("Your story", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.text, modifier = Modifier.padding(top = 16.dp))
                        }
                    }
                }
                items(stories.size, key = { "story-" + stories[it].id }) { index ->
                    val story = stories[index]
                    val tint = parseHexColor(story.colorHex, c.accent)
                    Box(
                        Modifier
                            .width(96.dp)
                            .height(160.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Brush.verticalGradient(listOf(tint, tint.copy(alpha = 0.5f), Color(0xFF0F1015))))
                            .clickable { onStory(index) },
                    ) {
                        if (story.imagePath != null) {
                            ChatImage(story.imagePath, Modifier.fillMaxSize(), showGifBadge = false)
                        } else {
                            Text(story.text, color = Color.White, fontSize = 10.sp, maxLines = 5, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(8.dp))
                        }
                        Box(Modifier.padding(6.dp).clip(CircleShape).border(2.dp, c.accent, CircleShape).padding(2.dp)) {
                            SocialAvatar(story.authorName, story.colorHex, 26.dp)
                        }
                        Text(story.authorName, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
                    }
                }
            }
            Hairline(c)
        }
        if (overview) postItems(posts.take(2), state, c, actions)
        if (overview && activeRooms.isNotEmpty()) {
            item(key = "rooms") {
                Column(Modifier.fillMaxWidth().background(c.surface).padding(vertical = 10.dp)) {
                    Text("Your servers & chats", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.muted, modifier = Modifier.padding(horizontal = 16.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        activeRooms.forEach { room -> RoomChip(room, c) { onRoom(room) } }
                    }
                }
                Hairline(c)
            }
        }
        if (overview && state.notifications.isNotEmpty()) {
            item(key = "activity") {
                Column(Modifier.fillMaxWidth().background(c.surface).clickable(onClick = onOpenAlerts).padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Latest activity", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.text, modifier = Modifier.weight(1f))
                        Text("See all", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.accent)
                    }
                    state.notifications.take(2).forEach { notification ->
                        Text("${notification.actorName} ${notification.text}", fontSize = 13.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                Hairline(c)
            }
        }
        if (overview && state.trends.isNotEmpty()) {
            item(key = "home-trends") {
                Column(Modifier.fillMaxWidth().background(c.surface).padding(vertical = 10.dp)) {
                    Row(Modifier.fillMaxWidth().clickable(onClick = onSearch).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Trending in your worlds", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.text, modifier = Modifier.weight(1f))
                        Text("Explore", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.accent)
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.trends.take(6).forEach { (tag, count) ->
                            Column(Modifier.clip(RoundedCornerShape(12.dp)).background(c.raised).clickable(onClick = onSearch).padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(tag, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.text)
                                Text("${compactCount(count)} posts", fontSize = 11.sp, color = c.muted)
                            }
                        }
                    }
                }
                Hairline(c)
            }
        }
        if (posts.isEmpty() && !state.generating) {
            item(key = "empty") {
                Column(Modifier.fillMaxWidth().padding(32.dp)) {
                    Text(if (following) "Nobody you follow has posted" else "Your worlds are quiet", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = c.text)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (following) "Follow people from Explore to fill this tab." else "Tap Refresh and your characters will start posting.",
                        color = c.muted,
                        fontSize = 15.sp,
                    )
                }
            }
        }
        postItems(if (overview) posts.drop(2) else posts, state, c, actions)
        if (state.generating) {
            item(key = "loading") {
                Row(Modifier.fillMaxWidth().background(c.surface).padding(14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = c.accent, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(state.status, color = c.muted, fontSize = 14.sp)
                }
                Hairline(c)
            }
        }
    }
    }
}

@Composable
private fun ComposerChip(icon: ImageVector, label: String, tint: Color, c: WsColors, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(c.raised).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun RoomChip(room: DiscordRoomUi, c: WsColors, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(c.raised)
            .border(1.dp, if (room.unread > 0) c.accent.copy(alpha = 0.6f) else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (room.kind == ROOM_KIND_CHANNEL) {
            Icon(Icons.Filled.Tag, null, tint = c.muted, modifier = Modifier.size(18.dp))
        } else {
            PresenceAvatar(room.name, room.avatarColorHex, 22.dp, c, ring = c.raised)
        }
        Spacer(Modifier.width(6.dp))
        Column {
            Text(room.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp))
            Text(room.serverTitle.ifBlank { "Direct message" }, fontSize = 11.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp))
        }
        if (room.unread > 0) {
            Spacer(Modifier.width(6.dp))
            Text(
                room.unread.toString(),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.red).padding(horizontal = 6.dp),
            )
        }
    }
}

// --------------------------------------------------------------- post card

private fun LazyListScope.postItems(posts: List<SocialPostUi>, state: SocialUiState, c: WsColors, actions: WsActions) {
    items(posts, key = { it.id }) { post ->
        PostCard(post, state, c, actions)
        Hairline(c)
    }
}

@Composable
private fun PostCard(post: SocialPostUi, state: SocialUiState, c: WsColors, actions: WsActions, inDetail: Boolean = false) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    if (confirmBlock && post.authorCharacterId != null) {
        BlockDialog(post.authorName, post.handle, onConfirm = { confirmBlock = false; actions.onBlock(post.authorCharacterId, true) }, onDismiss = { confirmBlock = false })
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.surface)
            .clickable(enabled = !inDetail) { actions.onOpenPost(post.parentId ?: post.id) }
            .padding(start = 14.dp, end = 8.dp, top = 12.dp),
    ) {
        if (post.userReposted) {
            Row(Modifier.padding(start = 26.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Repeat, null, tint = c.green, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("You reshared", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.muted)
            }
        }
        Row {
            Box(Modifier.clip(CircleShape).clickable { actions.onOpenProfile(post.authorCharacterId) }) {
                PresenceAvatar(post.authorName, post.colorHex, 42.dp, c, if (post.isYou) DiscordStatus.Online else discordStatusFor(post.authorName))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(post.authorName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (post.verified) {
                        Spacer(Modifier.width(3.dp))
                        Icon(Icons.Filled.Verified, "Verified", tint = c.accent, modifier = Modifier.size(15.dp))
                    }
                    Text(" @${post.handle} · ${compactAge(post.createdAt)}", fontSize = 14.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.weight(0.01f))
                    Box {
                        Icon(Icons.Filled.MoreHoriz, "More", tint = c.muted, modifier = Modifier.size(30.dp).clip(CircleShape).clickable { menu = true }.padding(6.dp))
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (post.bookmarked) "Remove from Saved" else "Save") },
                                leadingIcon = { Icon(if (post.bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, null) },
                                onClick = { menu = false; actions.onSave(post.id) },
                            )
                            if (post.isYou) {
                                DropdownMenuItem(text = { Text("Delete", color = c.red) }, onClick = { menu = false; actions.onDelete(post.id) })
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Not interested in this post") },
                                    leadingIcon = { Icon(Icons.Outlined.VisibilityOff, null) },
                                    onClick = { menu = false; actions.onNotInterested(post.id) },
                                )
                                DropdownMenuItem(text = { Text("View profile") }, onClick = { menu = false; actions.onOpenProfile(post.authorCharacterId) })
                                DropdownMenuItem(
                                    text = { Text("Message ${post.authorName}") },
                                    leadingIcon = { Icon(Icons.Outlined.MailOutline, null) },
                                    onClick = { menu = false; actions.onMessage(post.authorCharacterId) },
                                )
                                post.authorCharacterId?.let { id ->
                                    DropdownMenuItem(
                                        text = { Text("Mute @${post.handle}") },
                                        leadingIcon = { Icon(Icons.Outlined.VolumeOff, null) },
                                        onClick = { menu = false; actions.onMute(id, true) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Block @${post.handle}", color = c.red) },
                                        leadingIcon = { Icon(Icons.Outlined.Block, null, tint = c.red) },
                                        onClick = { menu = false; confirmBlock = true },
                                    )
                                }
                            }
                        }
                    }
                }
                if (post.feeling.isNotBlank()) {
                    Text("is ${feelingEmoji(post.feeling)} feeling ${post.feeling}", fontSize = 13.sp, color = c.muted)
                }
                if (post.parentId != null) {
                    state.allById[post.parentId]?.let { parent ->
                        Text(linkified("Replying to @${parent.handle}", c.blue), fontSize = 13.sp, color = c.muted)
                    }
                }
                if (post.text.isNotBlank()) {
                    val big = post.text.length < 70 && post.imagePaths.isEmpty() && post.repostOf == null && post.parentId == null
                    Text(
                        linkified(post.text, c.blue),
                        fontSize = if (big) 19.sp else 15.sp,
                        lineHeight = if (big) 25.sp else 21.sp,
                        color = c.text,
                        maxLines = if (inDetail) Int.MAX_VALUE else 10,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                SensitiveGate(post, state.safety.warnSensitive, c) { PostMedia(post, c) }
                if (post.imagePaths.isEmpty() && post.id in state.loadingMediaIds) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp))
                        .background(c.raised).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = c.accent, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Finding a picture or GIF…", color = c.muted, fontSize = 12.sp)
                    }
                }
                if (post.sourceUrl.startsWith("https://")) {
                    Column(Modifier.fillMaxWidth().padding(top = 6.dp)
                        .clip(RoundedCornerShape(10.dp)).background(c.raised)
                        .clickable {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(post.sourceUrl)))
                        }.padding(10.dp)) {
                        Text("${if (post.originKind == "public_video") "▶ Public video preview" else "Public source"} · ${post.sourceSite.ifBlank { "Web" }}", color = c.accent,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(post.sourceTitle.ifBlank { post.sourceUrl }, color = c.text,
                            fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    post.mediaCredits.withIndex().filter { it.value.isNotBlank() }.take(1).forEach { (index, credit) ->
                        val link = post.mediaLinks.getOrNull(index).orEmpty()
                        Text("Media: $credit", color = c.muted, fontSize = 11.sp,
                            modifier = Modifier.padding(top = 3.dp).clickable(enabled = link.startsWith("https://")) {
                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(link)))
                            })
                    }
                }
                post.repostOf?.let { QuotedPost(it, c) { actions.onOpenPost(it.id) } }
                if (post.likeCount > 0 || post.replyCount > 0 || post.repostCount > 0) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (post.likeCount > 0) {
                            ReactionStack(post.topReactions.ifEmpty { listOf(FbReaction.Like) }, c)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (post.userReaction.isNotBlank() && post.likeCount > 1) "You and ${compactCount(post.likeCount - 1)}" else compactCount(post.likeCount),
                                fontSize = 13.sp,
                                color = c.muted,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        val tail = buildList {
                            if (post.replyCount > 0) add("${post.replyCount} ${if (post.replyCount == 1) "reply" else "replies"}")
                            if (post.repostCount > 0) add("${compactCount(post.repostCount)} reshares")
                        }.joinToString(" · ")
                        Text(tail, fontSize = 13.sp, color = c.muted, modifier = Modifier.padding(end = 6.dp))
                    }
                }
                ActionBar(post, c, actions)
            }
        }
        if (!inDetail) {
            val replies = state.repliesByParent[post.id].orEmpty()
            if (replies.isNotEmpty()) {
                Column(Modifier.padding(start = 52.dp, end = 6.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    replies.takeLast(1).forEach { reply -> ReplyBubble(reply, c, actions, onReply = { actions.onOpenPost(post.id) }) }
                    if (replies.size > 1) {
                        Text(
                            "View all ${replies.size} replies",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = c.muted,
                            modifier = Modifier.clickable { actions.onOpenPost(post.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReactionStack(reactions: List<FbReaction>, c: WsColors) {
    Box(Modifier.width((18 + (reactions.size - 1).coerceAtLeast(0) * 12).dp)) {
        reactions.forEachIndexed { i, r ->
            Box(
                Modifier.offset(x = (i * 12).dp).size(18.dp).clip(CircleShape).background(c.surface).padding(1.dp),
                contentAlignment = Alignment.Center,
            ) { Text(r.emoji, fontSize = 12.sp) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActionBar(post: SocialPostUi, c: WsColors, actions: WsActions) {
    val context = LocalContext.current
    var picker by remember { mutableStateOf(false) }
    var reshareMenu by remember { mutableStateOf(false) }
    val reacted = FbReaction.of(post.userReaction)
    Box {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            // React: tap to Like, hold for the seven reactions.
            Row(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .combinedClickable(
                        onClick = { actions.onReact(post.id, reacted?.id ?: FbReaction.Like.id) },
                        onLongClick = { picker = true },
                    )
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (reacted == null) {
                    Icon(Icons.Outlined.ThumbUp, "React", tint = c.muted, modifier = Modifier.size(19.dp))
                } else {
                    Text(reacted.emoji, fontSize = 17.sp)
                }
                // Only a chosen reaction is named; the bare button stays an icon so the bar fits.
                if (reacted != null) {
                    Spacer(Modifier.width(4.dp))
                    Text(reacted.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(reacted.colorHex), maxLines = 1)
                }
            }
            BarAction(Icons.Outlined.ChatBubbleOutline, post.replyCount, c.muted, c.blue) { actions.onOpenPost(post.parentId ?: post.id) }
            Box {
                BarAction(Icons.Filled.Repeat, post.repostCount, if (post.userReposted) c.green else c.muted, c.green) { reshareMenu = true }
                DropdownMenu(expanded = reshareMenu, onDismissRequest = { reshareMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (post.userReposted) "Undo reshare" else "Reshare") },
                        leadingIcon = { Icon(Icons.Filled.Repeat, null) },
                        onClick = { reshareMenu = false; actions.onReshare(post.id) },
                    )
                    DropdownMenuItem(
                        text = { Text("Quote") },
                        leadingIcon = { Icon(Icons.Outlined.FormatQuote, null) },
                        onClick = { reshareMenu = false; actions.onQuote(post.id) },
                    )
                }
            }
            BarAction(Icons.Outlined.BarChart, post.viewCount, c.muted, c.muted) {}
            Row {
                Icon(
                    if (post.bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    "Save",
                    tint = if (post.bookmarked) c.accent else c.muted,
                    modifier = Modifier.size(30.dp).clip(CircleShape).clickable { actions.onSave(post.id) }.padding(6.dp),
                )
                Icon(
                    Icons.Outlined.Share,
                    "Share",
                    tint = c.muted,
                    modifier = Modifier.size(32.dp).clip(CircleShape).clickable {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, "${post.authorName} (@${post.handle}): ${post.text}")
                        }
                        runCatching { context.startActivity(android.content.Intent.createChooser(intent, "Share post")) }
                    }.padding(6.dp),
                )
            }
        }
        if (picker) {
            DropdownMenu(expanded = true, onDismissRequest = { picker = false }) {
                Row(Modifier.padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    FbReaction.entries.forEach { r ->
                        Text(
                            r.emoji,
                            fontSize = 30.sp,
                            modifier = Modifier.clip(CircleShape).clickable { picker = false; actions.onReact(post.id, r.id) }.padding(4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BarAction(icon: ImageVector, count: Int, tint: Color, hot: Color, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(30.dp).padding(6.dp))
        Text(if (count > 0) compactCount(count) else "", fontSize = 12.sp, color = if (tint == hot) hot else tint, modifier = Modifier.widthIn(min = 8.dp), maxLines = 1, softWrap = false)
    }
}

/**
 * X's sensitive-media warning: when the writer turned it on, sexual or violent posts show
 * their pictures behind a cover until tapped.
 */
@Composable
private fun SensitiveGate(post: SocialPostUi, warn: Boolean, c: WsColors, content: @Composable () -> Unit) {
    if (post.imagePaths.isEmpty()) return
    val sensitive = warn && post.labels.any { it in ContentLabel.SENSITIVE }
    var revealed by remember(post.id) { mutableStateOf(false) }
    if (!sensitive || revealed) {
        content()
        return
    }
    Column(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.raised)
            .clickable { revealed = true },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.VisibilityOff, null, tint = c.muted, modifier = Modifier.size(28.dp))
        Text("Sensitive content", color = c.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(
            post.labels.filter { it in ContentLabel.SENSITIVE }.joinToString(" · ") { it.label },
            color = c.muted,
            fontSize = 12.sp,
        )
        Text("Show", color = c.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun BlockDialog(name: String, handle: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Block @$handle?") },
        text = {
            Text(
                "$name won't show in your feed, can't reply to your posts or message you, and you'll " +
                    "stop following them. You can unblock any time under Privacy & filters.",
            )
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onConfirm) { Text("Block") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The post's pictures and GIFs in a Twitter-style grid; tap opens the viewer. */
@Composable
private fun PostMedia(post: SocialPostUi, c: WsColors, height: androidx.compose.ui.unit.Dp = 230.dp) {
    val open = LocalOpenMedia.current
    MediaGrid(
        paths = post.imagePaths,
        onOpen = { i -> open(post.imagePaths, i, post.text) },
        modifier = Modifier.padding(top = 8.dp),
        height = height,
        border = c.border,
    )
}

@Composable
private fun QuotedPost(post: SocialPostUi, c: WsColors, onClick: () -> Unit) {
    Column(
        Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, c.border, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SocialAvatar(post.authorName, post.colorHex, 20.dp)
            Spacer(Modifier.width(6.dp))
            Text(post.authorName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.text, maxLines = 1)
            if (post.verified) Icon(Icons.Filled.Verified, null, tint = c.accent, modifier = Modifier.size(14.dp))
            Text(" @${post.handle} · ${compactAge(post.createdAt)}", fontSize = 13.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        PostMedia(post, c, height = 160.dp)
        Text(linkified(post.text, c.blue), fontSize = 14.sp, color = c.text, maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

/** A reply in Facebook's comment-bubble style, with Like and Reply underneath. */
@Composable
private fun ReplyBubble(reply: SocialPostUi, c: WsColors, actions: WsActions, onReply: () -> Unit) {
    Row {
        Box(Modifier.clip(CircleShape).clickable { actions.onOpenProfile(reply.authorCharacterId) }) {
            SocialAvatar(reply.authorName, reply.colorHex, 30.dp)
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Box {
                Column(Modifier.clip(RoundedCornerShape(16.dp)).background(c.bubble).padding(horizontal = 12.dp, vertical = 7.dp)) {
                    Text(reply.authorName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.text)
                    if (reply.text.isNotBlank()) Text(linkified(reply.text, c.blue, bold = true), fontSize = 14.sp, color = c.text)
                }
                if (reply.likeCount > 0) {
                    Row(
                        Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 9.dp).clip(RoundedCornerShape(10.dp)).background(c.surface).border(1.dp, c.border, RoundedCornerShape(10.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(FbReaction.of(reply.userReaction)?.emoji ?: "👍", fontSize = 11.sp)
                        Text(" ${reply.likeCount}", fontSize = 11.sp, color = c.muted)
                    }
                }
            }
            if (reply.imagePaths.isNotEmpty()) PostMedia(reply, c, height = 150.dp)
            Row(Modifier.padding(start = 12.dp, top = 3.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(compactAge(reply.createdAt), fontSize = 12.sp, color = c.muted)
                Text(
                    "Like",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (reply.userReaction.isNotBlank()) c.accent else c.muted,
                    modifier = Modifier.clickable { actions.onReact(reply.id, FbReaction.Like.id) },
                )
                Text("Reply", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.muted, modifier = Modifier.clickable(onClick = onReply))
            }
        }
    }
}

// ------------------------------------------------------------- post detail

@Composable
private fun TopBar(title: String, c: WsColors, onBack: (() -> Unit)?, subtitle: String? = null) {
    Row(Modifier.fillMaxWidth().background(c.surface).height(54.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = c.text) }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Column {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = c.text, maxLines = 1)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = c.muted)
        }
    }
    Hairline(c)
}

@Composable
private fun PostDetail(
    postId: String,
    state: SocialUiState,
    c: WsColors,
    actions: WsActions,
    onBack: () -> Unit,
    onReply: (String, String, SocialPostUi?) -> Unit,
    onRemoveAttachment: (Int) -> Unit,
) {
    val post = state.allById[postId]
    var draft by rememberSaveable(postId) { mutableStateOf("") }
    var replyTo by remember(postId) { mutableStateOf<SocialPostUi?>(null) }
    Column(Modifier.fillMaxSize().background(c.surface)) {
        TopBar("Post", c, onBack)
        if (post == null) {
            Text("This post was deleted.", color = c.muted, modifier = Modifier.padding(24.dp))
            return@Column
        }
        val replies = state.repliesByParent[post.id].orEmpty()
        LazyColumn(Modifier.weight(1f)) {
            item(key = "post") {
                PostCard(post, state, c, actions, inDetail = true)
                Text(
                    SimpleDateFormat("h:mm a · MMM d, yyyy", Locale.getDefault()).format(Date(post.createdAt)) + " · " + compactCount(post.viewCount) + " views",
                    fontSize = 13.sp,
                    color = c.muted,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
                Hairline(c)
                Text("Replies", fontWeight = FontWeight.Bold, color = c.text, fontSize = 15.sp, modifier = Modifier.padding(14.dp))
            }
            if (replies.isEmpty() && !state.generating) {
                item(key = "none") { Text("No replies yet. Start the conversation.", color = c.muted, modifier = Modifier.padding(horizontal = 14.dp)) }
            }
            items(replies, key = { it.id }) { reply ->
                Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    ReplyBubble(reply, c, actions, onReply = { replyTo = reply })
                }
            }
            if (state.generating) {
                item(key = "typing") {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("•••", color = c.muted, fontSize = 16.sp, modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(c.bubble).padding(horizontal = 12.dp, vertical = 2.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Someone is replying…", color = c.muted, fontSize = 13.sp)
                    }
                }
            }
        }
        Hairline(c)
        replyTo?.let { target ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(linkified("Replying to @${target.handle}", c.blue), color = c.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.Close, "Cancel", tint = c.muted, modifier = Modifier.size(16.dp).clickable { replyTo = null })
            }
        }
        AttachmentStrip(
            paths = state.pendingImagePaths,
            onRemove = onRemoveAttachment,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
        )
        val pick = LocalPickMedia.current
        val blockedTargetId = replyTo?.authorCharacterId ?: post.authorCharacterId
        if (state.safety.cantInteract(blockedTargetId)) {
            Text(
                if (blockedTargetId in state.safety.blockedBy) {
                    "@${replyTo?.handle ?: post.handle} blocked you. You can't reply to them."
                } else {
                    "You blocked @${replyTo?.handle ?: post.handle}. Unblock them to reply."
                },
                color = c.muted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            return@Column
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SocialAvatar(state.personaName, avatarColorHexFor(state.personaName, null), 32.dp)
            Icon(
                Icons.Outlined.Image,
                "Add picture",
                tint = c.green,
                modifier = Modifier.size(36.dp).clip(CircleShape).clickable { pick(PickerStart.Library) }.padding(7.dp),
            )
            Icon(
                Icons.Filled.Gif,
                "Add GIF",
                tint = c.accent,
                modifier = Modifier.size(36.dp).clip(CircleShape).clickable { pick(PickerStart.Gifs) }.padding(5.dp),
            )
            Icon(
                Icons.Filled.ImageSearch,
                "Search pictures and GIFs",
                tint = c.blue,
                modifier = Modifier.size(36.dp).clip(CircleShape).clickable { pick(PickerStart.Web) }.padding(7.dp),
            )
            BasicTextField(
                value = draft,
                onValueChange = { if (it.length <= SocialFeedViewModel.POST_CHARS) draft = it },
                textStyle = TextStyle(color = c.text, fontSize = 15.sp),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(c.raised).padding(horizontal = 14.dp, vertical = 10.dp),
                decorationBox = { inner ->
                    if (draft.isEmpty()) Text(replyTo?.let { "Reply to ${it.authorName}…" } ?: "Post your reply", color = c.muted, fontSize = 15.sp)
                    inner()
                },
            )
            Spacer(Modifier.width(6.dp))
            Pill("Reply", c, enabled = draft.isNotBlank() || state.pendingImagePaths.isNotEmpty()) {
                onReply(post.id, draft, replyTo)
                draft = ""
                replyTo = null
            }
        }
    }
}

@Composable
private fun Pill(label: String, c: WsColors, enabled: Boolean = true, filled: Boolean = true, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = if (filled) Color.White else c.text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .then(if (filled) Modifier.background(if (enabled) c.brand else SolidColor(c.accent.copy(alpha = 0.4f))) else Modifier.border(1.dp, c.border, RoundedCornerShape(50)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
    )
}

// ---------------------------------------------------------------- composer

@Composable
private fun Composer(
    state: SocialUiState,
    c: WsColors,
    quote: SocialPostUi?,
    onRemoveAttachment: (Int) -> Unit,
    onCancel: () -> Unit,
    onPost: (String, String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var feeling by rememberSaveable { mutableStateOf("") }
    var feelingsOpen by rememberSaveable { mutableStateOf(false) }
    val limit = SocialFeedViewModel.POST_CHARS
    Column(Modifier.fillMaxSize().background(c.surface).imePadding().clickable(enabled = false) {}) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Cancel", fontSize = 16.sp, color = c.text, modifier = Modifier.clickable(onClick = onCancel).padding(4.dp))
            Spacer(Modifier.weight(1f))
            Pill("Post", c, enabled = text.isNotBlank() || state.pendingImagePaths.isNotEmpty() || quote != null) { onPost(text, feeling) }
        }
        Row(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp)) {
            PresenceAvatar(state.personaName, avatarColorHexFor(state.personaName, null), 42.dp, c, DiscordStatus.Online)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    buildAnnotatedString {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(state.personaName)
                        pop()
                        if (feeling.isNotBlank()) append(" is ${feelingEmoji(feeling)} feeling $feeling")
                    },
                    color = c.text,
                    fontSize = 15.sp,
                )
                Row(
                    Modifier.padding(top = 4.dp).clip(RoundedCornerShape(50)).border(1.dp, c.accent.copy(alpha = 0.5f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Public, null, tint = c.accent, modifier = Modifier.size(12.dp))
                    Text(" Everyone", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.accent)
                }
                BasicTextField(
                    value = text,
                    onValueChange = { if (it.length <= limit) text = it },
                    textStyle = TextStyle(color = c.text, fontSize = if (text.length < 70 && state.pendingImagePaths.isEmpty()) 21.sp else 16.sp),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 90.dp),
                    decorationBox = { inner ->
                        if (text.isEmpty()) Text(if (quote != null) "Add your take" else "What's happening in your worlds?", color = c.muted, fontSize = 21.sp)
                        inner()
                    },
                )
                if (state.pendingImagePaths.isNotEmpty()) {
                    val open = LocalOpenMedia.current
                    MediaGrid(
                        paths = state.pendingImagePaths,
                        onOpen = { i -> open(state.pendingImagePaths, i, "") },
                        modifier = Modifier.padding(top = 8.dp),
                        border = c.border,
                    )
                    AttachmentStrip(state.pendingImagePaths, onRemoveAttachment, Modifier.padding(top = 6.dp))
                }
                quote?.let { QuotedPost(it, c) {} }
            }
        }
        if (feelingsOpen) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Feelings.forEach { (emoji, label) ->
                    Text(
                        "$emoji $label",
                        color = c.text,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (feeling == label) c.accent.copy(alpha = 0.25f) else c.raised)
                            .clickable { feeling = if (feeling == label) "" else label; feelingsOpen = false }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
        Hairline(c)
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            val pick = LocalPickMedia.current
            val full = state.pendingImagePaths.size >= MAX_ATTACHMENTS
            IconButton(onClick = { pick(PickerStart.Library) }, enabled = !full) { Icon(Icons.Outlined.Image, "Add photo", tint = if (full) c.muted else c.green) }
            IconButton(onClick = { pick(PickerStart.Gifs) }, enabled = !full) { Icon(Icons.Filled.Gif, "Add GIF", tint = if (full) c.muted else c.accent, modifier = Modifier.size(30.dp)) }
            IconButton(onClick = { pick(PickerStart.Web) }, enabled = !full) { Icon(Icons.Filled.ImageSearch, "Search pictures and GIFs", tint = if (full) c.muted else c.blue) }
            if (state.pendingImagePaths.isNotEmpty()) {
                Text("${state.pendingImagePaths.size}/$MAX_ATTACHMENTS", fontSize = 12.sp, color = c.muted)
            }
            IconButton(onClick = { feelingsOpen = !feelingsOpen }) { Icon(Icons.Outlined.EmojiEmotions, "Feeling", tint = c.idle) }
            Spacer(Modifier.weight(1f))
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(end = 8.dp)) {
                CircularProgressIndicator(
                    progress = { (text.length / limit.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.size(24.dp),
                    color = if (text.length > limit - 20) c.idle else c.accent,
                    trackColor = c.border,
                    strokeWidth = 2.5.dp,
                )
                if (text.length > limit - 20) Text((limit - text.length).toString(), fontSize = 9.sp, color = c.muted)
            }
        }
    }
}

// ------------------------------------------------------------------ stories

@Composable
private fun StoryViewer(stories: List<SocialPostUi>, start: Int, onClose: () -> Unit, onReply: (SocialPostUi) -> Unit) {
    var index by remember { mutableStateOf(start.coerceIn(0, (stories.size - 1).coerceAtLeast(0))) }
    val story = stories.getOrNull(index) ?: run { onClose(); return }
    val progress = remember(index) { Animatable(0f) }
    LaunchedEffect(index) {
        progress.animateTo(1f, tween(5_000, easing = LinearEasing))
        if (index < stories.lastIndex) index++ else onClose()
    }
    val tint = parseHexColor(story.colorHex, Color(0xFF8B6CFF))
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(tint, Color(0xFF0F1015))))) {
        story.imagePath?.let {
            coil3.compose.AsyncImage(model = java.io.File(it), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
        Text(story.text, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(32.dp))
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight().clickable { if (index > 0) index-- })
            Box(Modifier.weight(1f).fillMaxHeight().clickable { if (index < stories.lastIndex) index++ else onClose() })
        }
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                stories.indices.forEach { i ->
                    Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.35f))) {
                        val fill = when {
                            i < index -> 1f
                            i == index -> progress.value
                            else -> 0f
                        }
                        Box(Modifier.fillMaxWidth(fill).fillMaxHeight().background(Color.White))
                    }
                }
            }
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                SocialAvatar(story.authorName, story.colorHex, 36.dp)
                Spacer(Modifier.width(8.dp))
                Text(story.authorName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("  ${compactAge(story.createdAt)}", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close", tint = Color.White) }
            }
        }
        Text(
            "Reply to ${story.authorName}…",
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(50))
                .clickable { onReply(story) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

// ------------------------------------------------------------------ explore

@Composable
private fun Explore(
    state: SocialUiState,
    c: WsColors,
    actions: WsActions,
    servers: List<Triple<String, String, String>>,
    serverUnread: Map<String, Int>,
    onServer: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(c.surface)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp).clip(RoundedCornerShape(50)).background(c.raised).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, null, tint = c.muted, modifier = Modifier.size(18.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 15.sp),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 11.dp),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Search people, posts and #tags", color = c.muted, fontSize = 15.sp)
                    inner()
                },
            )
            if (query.isNotEmpty()) Icon(Icons.Filled.Close, "Clear", tint = c.muted, modifier = Modifier.size(18.dp).clickable { query = "" })
        }
        Hairline(c)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (query.isNotBlank()) {
                val people = state.people.filter { it.name.contains(query, true) || it.handle.contains(query.removePrefix("@"), true) }
                items(people, key = { "p-" + it.characterId }) { PersonRow(it, c, actions) }
                val posts = (state.posts + state.repliesByParent.values.flatten()).filter { it.text.contains(query, true) }
                postItems(posts, state, c, actions)
                if (people.isEmpty() && posts.isEmpty()) {
                    item(key = "none") { Text("No results for \"$query\".", color = c.muted, modifier = Modifier.padding(16.dp)) }
                }
            } else {
                if (servers.isNotEmpty()) {
                    item(key = "servers-title") { SectionTitle("Your servers", c) }
                    item(key = "servers") {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            servers.forEach { (id, title, color) ->
                                Column(Modifier.width(76.dp).clickable { onServer(id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box {
                                        Box(
                                            Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(parseHexColor(color, c.accent)),
                                            contentAlignment = Alignment.Center,
                                        ) { Text(title.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp) }
                                        val unread = serverUnread[id] ?: 0
                                        if (unread > 0) {
                                            Text(
                                                unread.toString(),
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.align(Alignment.BottomEnd).clip(RoundedCornerShape(8.dp)).background(c.red).padding(horizontal = 5.dp),
                                            )
                                        }
                                    }
                                    Text(title, fontSize = 12.sp, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }
                }
                item(key = "trends-title") { SectionTitle("Trending in your worlds", c) }
                if (state.trends.isEmpty()) {
                    item(key = "no-trends") { Text("Nothing's trending yet.", color = c.muted, modifier = Modifier.padding(horizontal = 16.dp)) }
                }
                items(state.trends.size, key = { "t-" + state.trends[it].first }) { i ->
                    val (tag, count) = state.trends[i]
                    Row(Modifier.fillMaxWidth().clickable { query = tag }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", color = c.muted, fontSize = 13.sp, modifier = Modifier.width(22.dp))
                        Column {
                            Text(tag, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.text)
                            Text("${compactCount(count)} posts", fontSize = 12.sp, color = c.muted)
                        }
                    }
                }
                item(key = "people-title") { SectionTitle("People you may know", c) }
                items(state.people.sortedBy { it.isFollowing }.take(15), key = { "w-" + it.characterId }) { PersonRow(it, c, actions) }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, c: WsColors) {
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = c.text, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp))
}

@Composable
private fun PersonRow(person: SocialPersonUi, c: WsColors, actions: WsActions) {
    Row(
        Modifier.fillMaxWidth().clickable { actions.onOpenProfile(person.characterId) }.padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PresenceAvatar(person.name, person.colorHex, 44.dp, c)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(person.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (person.verified) Icon(Icons.Filled.Verified, null, tint = c.accent, modifier = Modifier.size(15.dp))
            }
            Text("@${person.handle} · ${compactCount(person.followers)} followers", fontSize = 13.sp, color = c.muted, maxLines = 1)
            if (person.bio.isNotBlank()) Text(person.bio, fontSize = 13.sp, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        FollowButton(person.isFollowing, c) { actions.onFollow(person.characterId) }
    }
}

@Composable
private fun FollowButton(following: Boolean, c: WsColors, onClick: () -> Unit) {
    Text(
        if (following) "Following" else "Follow",
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = if (following) c.text else Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .then(if (following) Modifier.border(1.dp, c.border, RoundedCornerShape(50)) else Modifier.background(c.brand))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

// ------------------------------------------------------------------- alerts

@Composable
private fun Alerts(
    state: SocialUiState,
    c: WsColors,
    unreadRooms: List<DiscordRoomUi>,
    onRoom: (DiscordRoomUi) -> Unit,
    onOpen: (String?) -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf(0) }
    val shown = state.notifications.filter {
        when (filter) {
            1 -> it.kind == "reply" || it.kind == "mention"
            2 -> false
            else -> true
        }
    }
    Column(Modifier.fillMaxSize().background(c.surface)) {
        TopBar("Alerts", c, onBack = null)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Mentions", "Servers").forEachIndexed { i, label ->
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (filter == i) Color.White else c.text,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .then(if (filter == i) Modifier.background(c.brand) else Modifier.background(c.raised))
                        .clickable { filter = i }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (filter != 1 && unreadRooms.isNotEmpty()) {
                item(key = "rooms-title") { SectionTitle("Unread in your servers", c) }
                items(unreadRooms, key = { "r-" + it.chatId }) { room ->
                    Row(
                        Modifier.fillMaxWidth().background(c.accent.copy(alpha = 0.07f)).clickable { onRoom(room) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (room.kind == ROOM_KIND_CHANNEL) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.raised), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Tag, null, tint = c.muted)
                            }
                        } else {
                            PresenceAvatar(room.name, room.avatarColorHex, 44.dp, c)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                (if (room.kind == ROOM_KIND_CHANNEL) "#" else "") + room.name,
                                fontWeight = FontWeight.Bold,
                                color = c.text,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(room.preview.ifBlank { room.serverTitle }, color = c.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(
                            room.unread.toString(),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(c.red).padding(horizontal = 7.dp, vertical = 1.dp),
                        )
                    }
                }
            }
            if (filter != 2) {
                item(key = "social-title") { SectionTitle("Activity", c) }
                if (shown.isEmpty()) {
                    item(key = "empty") { Text("Reactions, replies, reshares and follows show up here.", color = c.muted, modifier = Modifier.padding(horizontal = 16.dp)) }
                }
                items(shown, key = { it.id }) { n ->
                    Row(Modifier.fillMaxWidth().clickable { onOpen(n.postId) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            SocialAvatar(n.actorName, n.actorColorHex, 44.dp)
                            val (icon, tint) = when (n.kind) {
                                "like" -> Icons.Outlined.ThumbUp to c.blue
                                "repost" -> Icons.Filled.Repeat to c.green
                                "follow" -> Icons.Filled.Person to c.accent
                                "mention" -> Icons.Outlined.AlternateEmail to c.accent
                                else -> Icons.Outlined.ChatBubbleOutline to c.green
                            }
                            Box(
                                Modifier.align(Alignment.BottomEnd).size(20.dp).clip(CircleShape).background(tint),
                                contentAlignment = Alignment.Center,
                            ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(12.dp)) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                buildAnnotatedString {
                                    if (n.text.startsWith(n.actorName)) {
                                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                                        append(n.actorName)
                                        pop()
                                        append(n.text.removePrefix(n.actorName))
                                    } else {
                                        append(n.text)
                                    }
                                },
                                color = c.text,
                                fontSize = 14.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(compactAge(n.createdAt), color = c.muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ profile

@Composable
private fun Profile(
    who: String,
    state: SocialUiState,
    c: WsColors,
    actions: WsActions,
    onBack: (() -> Unit)?,
    onCompose: () -> Unit,
) {
    val isYou = who == "you"
    val person = state.people.firstOrNull { it.characterId == who }
    val name = if (isYou) state.personaName else person?.name ?: "Account"
    val handle = if (isYou) state.youHandle else person?.handle ?: "unknown"
    val color = if (isYou) avatarColorHexFor(name, null) else person?.colorHex ?: "#8B6CFF"
    val tint = parseHexColor(color, c.accent)
    val status = if (isYou) DiscordStatus.Online else discordStatusFor(name)
    val all = state.allById.values
    var viewAnyway by rememberSaveable(who) { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    // Someone who blocked you can't be seen; someone you blocked can, if you choose to.
    val hidden = !isYou && person != null && (person.blockedYou || (person.blockedByYou && !viewAnyway))
    val theirs = if (hidden) emptyList() else all.filter { if (isYou) it.isYou else it.authorCharacterId == who }.sortedByDescending { it.createdAt }
    var tab by rememberSaveable(who) { mutableStateOf(0) }
    val shown = when (tab) {
        0 -> theirs.filter { it.parentId == null }
        1 -> theirs.filter { it.parentId != null }
        2 -> theirs.filter { it.imagePaths.isNotEmpty() }
        else -> if (isYou) all.filter { it.bookmarked }.sortedByDescending { it.createdAt } else all.filter { it.userReaction.isNotBlank() && it.authorCharacterId == who }
    }
    val friends = state.people.filter { it.isFollowing }
    LazyColumn(Modifier.fillMaxSize().background(c.surface), contentPadding = PaddingValues(bottom = 96.dp)) {
        item(key = "header") {
            Box(Modifier.fillMaxWidth().height(200.dp)) {
                Box(Modifier.fillMaxWidth().height(140.dp).background(Brush.linearGradient(listOf(tint, tint.copy(alpha = 0.45f), c.accent.copy(alpha = 0.6f)))))
                if (onBack != null) {
                    IconButton(onClick = onBack, modifier = Modifier.padding(8.dp).size(36.dp).clip(CircleShape).background(Color(0x66000000))) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                }
                // Background shape rather than clip, so the presence dot is not cut off.
                Box(Modifier.align(Alignment.BottomStart).padding(start = 16.dp).background(c.surface, CircleShape).padding(5.dp)) {
                    PresenceAvatar(name, color, 96.dp, c, status)
                }
                Row(Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isYou) {
                        Icon(
                            Icons.Outlined.Shield,
                            "Privacy & filters",
                            tint = c.text,
                            modifier = Modifier.size(36.dp).clip(CircleShape).border(1.dp, c.border, CircleShape).clickable(onClick = actions.onOpenSafety).padding(7.dp),
                        )
                        Pill("New post", c, onClick = onCompose)
                    } else if (person != null) {
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            Icon(
                                Icons.Filled.MoreHoriz,
                                "More",
                                tint = c.text,
                                modifier = Modifier.size(36.dp).clip(CircleShape).border(1.dp, c.border, CircleShape).clickable { menu = true }.padding(7.dp),
                            )
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (person.mutedByYou) "Unmute @${person.handle}" else "Mute @${person.handle}") },
                                    leadingIcon = { Icon(Icons.Outlined.VolumeOff, null) },
                                    onClick = { menu = false; actions.onMute(person.characterId, !person.mutedByYou) },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (person.blockedByYou) "Unblock @${person.handle}" else "Block @${person.handle}", color = c.red) },
                                    leadingIcon = { Icon(Icons.Outlined.Block, null, tint = c.red) },
                                    onClick = {
                                        menu = false
                                        if (person.blockedByYou) actions.onBlock(person.characterId, false) else confirmBlock = true
                                    },
                                )
                            }
                        }
                        if (!person.blockedYou && !person.blockedByYou) {
                            Icon(
                                Icons.Outlined.MailOutline,
                                "Message",
                                tint = c.text,
                                modifier = Modifier.size(36.dp).clip(CircleShape).border(1.dp, c.border, CircleShape).clickable { actions.onMessage(person.characterId) }.padding(7.dp),
                            )
                            FollowButton(person.isFollowing, c) { actions.onFollow(person.characterId) }
                        } else if (person.blockedByYou) {
                            Text(
                                "Blocked",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(c.red).clickable { actions.onBlock(person.characterId, false) }
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = c.text)
                        if (person?.verified == true) Icon(Icons.Filled.Verified, null, tint = c.accent, modifier = Modifier.size(20.dp))
                    }
                    Text("@$handle · ${status.label}", fontSize = 14.sp, color = c.muted)
                }
                val bio = if (isYou) state.personaBio else person?.bio.orEmpty()
                if (bio.isNotBlank()) Text(linkified(bio, c.blue), fontSize = 15.sp, color = c.text)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CalendarMonth, null, tint = c.muted, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Joined " + SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(person?.joinedAt ?: System.currentTimeMillis())),
                        fontSize = 14.sp,
                        color = c.muted,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Stat(if (isYou) friends.size else person?.following ?: 0, "Following", c)
                    Stat(if (isYou) friends.size else (person?.followers ?: 0) + (if (person?.isFollowing == true) 1 else 0), "Followers", c)
                }
                // Discord-style roles.
                val roles = buildList {
                    if (isYou) {
                        add("Writer"); add("Server Owner")
                    } else {
                        add("Character")
                        if (person?.verified == true) add("Verified")
                        if (person?.isFollowing == true) add("Friend")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    roles.forEach { role ->
                        Row(
                            Modifier.clip(RoundedCornerShape(6.dp)).background(c.raised).padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(9.dp).clip(CircleShape).background(tint))
                            Spacer(Modifier.width(5.dp))
                            Text(role, fontSize = 12.sp, color = c.text)
                        }
                    }
                }
            }
            if (isYou && friends.isNotEmpty()) {
                Text("Following", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.text, modifier = Modifier.padding(start = 16.dp, top = 10.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    friends.take(12).forEach { f ->
                        Column(Modifier.width(64.dp).clickable { actions.onOpenProfile(f.characterId) }, horizontalAlignment = Alignment.CenterHorizontally) {
                            PresenceAvatar(f.name, f.colorHex, 52.dp, c)
                            Text(f.name, fontSize = 11.sp, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (confirmBlock && person != null) {
                BlockDialog(person.name, person.handle, onConfirm = { confirmBlock = false; actions.onBlock(person.characterId, true) }, onDismiss = { confirmBlock = false })
            }
            if (person?.blockedYou == true) {
                BlockBanner(
                    title = "@${person.handle} blocked you",
                    body = "You can't follow or message @${person.handle}, see their posts, or reply to them.",
                    c = c,
                )
            } else if (person?.blockedByYou == true) {
                BlockBanner(
                    title = "@${person.handle} is blocked",
                    body = "They can't reply to you or message you.",
                    c = c,
                    action = if (viewAnyway) null else ("View posts" to { viewAnyway = true }),
                )
            }
            Row(Modifier.fillMaxWidth().height(46.dp)) {
                listOf("Posts", "Replies", "Media", if (isYou) "Saved" else "Liked").forEachIndexed { index, label ->
                    val selected = tab == index
                    Box(Modifier.weight(1f).fillMaxHeight().clickable { tab = index }, contentAlignment = Alignment.Center) {
                        Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) c.text else c.muted, fontSize = 14.sp)
                        if (selected) Box(Modifier.align(Alignment.BottomCenter).width(44.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(c.brand))
                    }
                }
            }
            Hairline(c)
        }
        if (shown.isEmpty()) {
            item(key = "empty") {
                Text(
                    when {
                        isYou -> "Nothing here yet — tap New post."
                        hidden -> "Posts aren't available."
                        else -> "@$handle hasn't posted here yet."
                    },
                    color = c.muted,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
        postItems(shown, state, c, actions)
    }
}

@Composable
private fun Stat(value: Int, label: String, c: WsColors) {
    Text(
        buildAnnotatedString {
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = c.text))
            append(compactCount(value))
            pop()
            append(" $label")
        },
        fontSize = 14.sp,
        color = c.muted,
    )
}

// ------------------------------------------------------------ privacy & filters

@Composable
private fun BlockBanner(title: String, body: String, c: WsColors, action: Pair<String, () -> Unit>? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.red.copy(alpha = 0.12f))
            .border(1.dp, c.red.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Block, null, tint = c.red, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, color = c.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Text(body, color = c.muted, fontSize = 13.sp)
        action?.let { (label, onClick) ->
            Text(label, color = c.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onClick).padding(top = 4.dp))
        }
    }
}

/**
 * X's "Privacy and safety" for WeaverSocial: what shows in the feed (topic filters, muted
 * words, sensitive media), and who is muted, blocked, or has blocked the writer.
 * Nothing is filtered until the writer turns it on.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SafetyScreen(
    state: SocialUiState,
    c: WsColors,
    viewModel: SocialFeedViewModel,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val safety = state.safety
    var word by rememberSaveable { mutableStateOf("") }
    var mediaSettings by remember { mutableStateOf(false) }
    var creatorSettings by remember { mutableStateOf(false) }
    val byId = state.people.associateBy { it.characterId }
    LazyColumn(Modifier.fillMaxSize().background(c.surface), contentPadding = PaddingValues(bottom = 40.dp)) {
        item(key = "top") {
            TopBar("Privacy & filters", c, onBack, subtitle = "Everything shows until you filter it")
        }
        item(key = "content-title") { SectionTitle("Content you see", c) }
        item(key = "adult") {
            SwitchRow(
                title = "WeaverSocial 18+",
                body = "On by default. Include adult posts and search adult media. Turning it off hides saved sexual posts until you turn it back on.",
                checked = safety.adultEnabled,
                c = c,
            ) { viewModel.setAdultEnabled(it) }
        }
        item(key = "media-settings") {
            Column(Modifier.fillMaxWidth().clickable { mediaSettings = true }
                .padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("Media sources & AI images", color = c.accent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Free Civitai, Gelbooru and Danbooru galleries; optional Brave search, OpenRouter image model and ComfyUI workflow.",
                    color = c.muted, fontSize = 12.sp)
                if (state.mediaNotice.isNotBlank()) Text(state.mediaNotice, color = c.muted, fontSize = 12.sp)
            }
        }
        item(key = "fictional-creators") {
            Column(Modifier.fillMaxWidth().clickable { creatorSettings = true }
                .padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("Fictional social accounts", color = c.accent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Choose adult creators and everyday voices to add to the shared Characters Codex and social feed.",
                    color = c.muted, fontSize = 12.sp)
            }
        }
        item(key = "sensitive") {
            SwitchRow(
                title = "Warn before sensitive media",
                body = "Cover pictures on sexual or violent posts until you tap them.",
                checked = safety.warnSensitive,
                c = c,
            ) { viewModel.setWarnSensitive(it) }
        }
        item(key = "topics-title") {
            Text(
                "Hide topics",
                color = c.text,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp),
            )
            Text(
                "Posts labelled with a hidden topic leave your feed, replies and alerts.",
                color = c.muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        items(ContentLabel.entries.size, key = { "label-" + ContentLabel.entries[it].id }) { i ->
            val label = ContentLabel.entries[i]
            SwitchRow(
                title = "Hide ${label.label.lowercase()}",
                body = null,
                checked = label in safety.hiddenLabels,
                c = c,
            ) { viewModel.setLabelHidden(label, it) }
        }
        item(key = "words-title") { SectionTitle("Muted words", c) }
        item(key = "words-add") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = word,
                    onValueChange = { word = it },
                    singleLine = true,
                    textStyle = TextStyle(color = c.text, fontSize = 15.sp),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(c.raised).padding(horizontal = 14.dp, vertical = 10.dp),
                    decorationBox = { inner ->
                        if (word.isEmpty()) Text("Word, phrase or #hashtag", color = c.muted, fontSize = 15.sp)
                        inner()
                    },
                )
                Spacer(Modifier.width(8.dp))
                Pill("Mute", c, enabled = word.isNotBlank()) {
                    viewModel.addMutedWord(word)
                    word = ""
                }
            }
        }
        item(key = "words-list") {
            if (safety.mutedWords.isEmpty()) {
                Text("No muted words.", color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            } else {
                androidx.compose.foundation.layout.FlowRow(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    safety.mutedWords.sorted().forEach { w ->
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).background(c.raised).padding(start = 12.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(w, color = c.text, fontSize = 13.sp)
                            Icon(
                                Icons.Filled.Close,
                                "Unmute $w",
                                tint = c.muted,
                                modifier = Modifier.padding(start = 4.dp).size(18.dp).clip(CircleShape).clickable { viewModel.removeMutedWord(w) },
                            )
                        }
                    }
                }
            }
        }
        if (safety.hiddenPosts.isNotEmpty()) {
            item(key = "hidden-posts") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${safety.hiddenPosts.size} posts marked Not interested", color = c.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("Show them again", color = c.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.clickable { viewModel.restoreHiddenPosts() })
                }
            }
        }
        accountSection(
            key = "muted",
            title = "Muted accounts",
            empty = "You haven't muted anyone.",
            ids = safety.muted,
            byId = byId,
            c = c,
            actionLabel = "Unmute",
            onAction = { viewModel.mute(it, false) },
            onOpen = onOpenProfile,
        )
        accountSection(
            key = "blocked",
            title = "Blocked accounts",
            empty = "You haven't blocked anyone.",
            ids = safety.blocked,
            byId = byId,
            c = c,
            actionLabel = "Unblock",
            onAction = { viewModel.block(it, false) },
            onOpen = onOpenProfile,
        )
        accountSection(
            key = "blocked-by",
            title = "Accounts that blocked you",
            empty = "Nobody has blocked you.",
            ids = safety.blockedBy,
            byId = byId,
            c = c,
            actionLabel = "Lift (author)",
            onAction = { viewModel.liftBlockOnYou(it) },
            onOpen = onOpenProfile,
            note = "Characters block you in character. As the author you can lift a block here.",
        )
    }
    if (mediaSettings) SocialMediaSettingsDialog(state, viewModel, c) { mediaSettings = false }
    if (creatorSettings) SocialCreatorDialog(state, viewModel, c) { creatorSettings = false }
}

@Composable
private fun SocialCreatorDialog(state: SocialUiState, viewModel: SocialFeedViewModel, c: WsColors, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fictional social accounts") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Add any of these adults to your Codex. They can post, reply, and appear in chats. You can edit their character entries later.",
                    fontSize = 12.sp, color = c.muted)
                SocialCreatorTemplates.all.forEach { creator ->
                    val added = SocialCreatorTemplates.id(creator.slug) in state.addedCreatorIds
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.raised)
                        .clickable(enabled = !added) { viewModel.addFictionalCreator(creator) }.padding(10.dp)) {
                        Text("${creator.name} · @${handleFor(creator.name)}", color = c.text, fontWeight = FontWeight.Bold)
                        Text(creator.focus, color = c.accent, fontSize = 12.sp)
                        Text(if (added) "Added to Codex" else "Tap to add to Codex", color = c.muted, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun SocialMediaSettingsDialog(state: SocialUiState, viewModel: SocialFeedViewModel, c: WsColors, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var brave by remember { mutableStateOf(viewModel.braveKey()) }
    var civitai by remember { mutableStateOf(viewModel.civitaiKey()) }
    var gelbooruUser by remember { mutableStateOf(viewModel.gelbooruUserId()) }
    var gelbooruKey by remember { mutableStateOf(viewModel.gelbooruApiKey()) }
    var giphy by remember { mutableStateOf(viewModel.giphyKey()) }
    var tenor by remember { mutableStateOf(viewModel.tenorKey()) }
    var endpoint by remember { mutableStateOf(state.comfyEndpoint) }
    var workflow by remember { mutableStateOf(state.comfyWorkflow) }
    var imageModel by remember { mutableStateOf(state.imageModelRef) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("WeaverSocial media") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Public search is read-only. Civitai, Gelbooru and Danbooru galleries are tried without a paid search key when their servers allow it. Brave is optional for indexed creator sites, adult hubs and forums. Private or paid posts cannot be imported.",
                    fontSize = 12.sp, color = c.muted)
                OutlinedTextField(civitai, { civitai = it }, label = { Text("Civitai API token (optional)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                Text("Check Civitai · ${state.civitaiStatus.ifBlank { "Not checked" }}", color = c.accent,
                    fontSize = 13.sp, modifier = Modifier.clickable { viewModel.checkCivitai() })
                Text("Browse Civitai images", color = c.accent, fontSize = 13.sp,
                    modifier = Modifier.clickable {
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(if (state.safety.adultEnabled) "https://civitai.red/images" else "https://civitai.com/images")))
                    })
                Text("Browse Civitai videos", color = c.accent, fontSize = 13.sp,
                    modifier = Modifier.clickable {
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(if (state.safety.adultEnabled) "https://civitai.red/videos" else "https://civitai.com/videos")))
                    })
                OutlinedTextField(gelbooruUser, { gelbooruUser = it }, label = { Text("Gelbooru user ID (optional)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(gelbooruKey, { gelbooruKey = it }, label = { Text("Gelbooru API key (optional)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                Text("Check Gelbooru · ${state.gelbooruStatus.ifBlank { "Not checked" }}", color = c.accent,
                    fontSize = 13.sp, modifier = Modifier.clickable { viewModel.checkGelbooru() })
                OutlinedTextField(brave, { brave = it }, label = { Text("Brave Image & Video Search API key") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                Text(if (brave.isBlank()) "Brave: key needed" else "Save the key, then check the connection.",
                    fontSize = 12.sp, color = c.muted)
                Text("Check Brave · ${state.braveStatus.ifBlank { "Not checked" }}", color = c.accent,
                    fontSize = 13.sp, modifier = Modifier.clickable { viewModel.checkBrave() })
                Text("Adult GIF searches include public Gelbooru and Danbooru GIFs. Openverse and Wikimedia are also keyless; Brave, GIPHY and Tenor are optional. Only actual GIF files attach to GIF requests; video pages use credited preview cards.",
                    fontSize = 12.sp, color = c.muted)
                OutlinedTextField(giphy, { giphy = it }, label = { Text("GIPHY API key (optional)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                OutlinedTextField(tenor, { tenor = it }, label = { Text("Tenor API key (optional)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                OutlinedTextField(imageModel, { imageModel = it },
                    label = { Text("OpenRouter image model ref") }, singleLine = true,
                    placeholder = { Text("openrouter/provider/model") }, modifier = Modifier.fillMaxWidth())
                Text("Choose an image-output model for original character photos. A model with reference-image editing can also use that character's Codex portrait for likeness; otherwise their written appearance guides the result.",
                    fontSize = 12.sp, color = c.muted)
                OutlinedTextField(endpoint, { endpoint = it }, label = { Text("ComfyUI URL (optional)") },
                    singleLine = true, placeholder = { Text("http://192.168.1.10:8188") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(workflow, { workflow = it },
                    label = { Text("ComfyUI API workflow JSON (optional)") },
                    minLines = 4, modifier = Modifier.fillMaxWidth())
                Text("Export an API workflow from ComfyUI. Put __PROMPT__ in its positive prompt text and optionally __SEED__ in its seed field. The phone must be able to reach the URL.",
                    fontSize = 12.sp, color = c.muted)
                Text("At most one original AI image is attempted per refresh. Search and saved pictures fill the other posts.",
                    fontSize = 12.sp, color = c.muted)
                Text("Check saved ComfyUI URL · ${state.comfyStatus.ifBlank { "Not checked" }}", color = c.accent,
                    fontSize = 13.sp, modifier = Modifier.clickable { viewModel.checkComfy() })
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = {
            viewModel.setBraveKey(brave)
            viewModel.setCivitaiKey(civitai)
            viewModel.setGelbooruUserId(gelbooruUser)
            viewModel.setGelbooruApiKey(gelbooruKey)
            viewModel.setGiphyKey(giphy)
            viewModel.setTenorKey(tenor)
            viewModel.saveMediaSettings(endpoint, workflow, imageModel)
            onDismiss()
        }) { Text("Save") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun LazyListScope.accountSection(
    key: String,
    title: String,
    empty: String,
    ids: Set<String>,
    byId: Map<String, SocialPersonUi>,
    c: WsColors,
    actionLabel: String,
    onAction: (String) -> Unit,
    onOpen: (String) -> Unit,
    note: String? = null,
) {
    item(key = "$key-title") {
        SectionTitle(title, c)
        if (note != null) Text(note, color = c.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
        if (ids.isEmpty()) Text(empty, color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
    }
    items(ids.toList(), key = { "$key-$it" }) { id ->
        val person = byId[id]
        Row(
            Modifier.fillMaxWidth().clickable { onOpen(id) }.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SocialAvatar(person?.name ?: "?", person?.colorHex ?: "#888888", 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(person?.name ?: "Removed character", color = c.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (person != null) Text("@${person.handle}", color = c.muted, fontSize = 13.sp)
            }
            Text(
                actionLabel,
                color = c.text,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, c.border, RoundedCornerShape(50)).clickable { onAction(id) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, body: String?, checked: Boolean, c: WsColors, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 15.sp)
            if (body != null) Text(body, color = c.muted, fontSize = 13.sp)
        }
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = c.accent),
        )
    }
}
