package com.ihy2ln.weaverse.feature.chatting.social

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.util.parseHexColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class TwColors(
    val bg: Color,
    val text: Color,
    val muted: Color,
    val divider: Color,
    val hover: Color,
    val search: Color,
) {
    val blue = Color(0xFF1D9BF0)
    val pink = Color(0xFFF91880)
    val green = Color(0xFF00BA7C)
}

private val TwDark = TwColors(
    bg = Color(0xFF000000),
    text = Color(0xFFE7E9EA),
    muted = Color(0xFF71767B),
    divider = Color(0xFF2F3336),
    hover = Color(0xFF16181C),
    search = Color(0xFF202327),
)
private val TwLight = TwColors(
    bg = Color(0xFFFFFFFF),
    text = Color(0xFF0F1419),
    muted = Color(0xFF536471),
    divider = Color(0xFFEFF3F4),
    hover = Color(0xFFF7F9F9),
    search = Color(0xFFEFF3F4),
)

private enum class TwTab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Home("Home", Icons.Outlined.Home, Icons.Filled.Home),
    Explore("Explore", Icons.Filled.Search, Icons.Filled.Search),
    Notifications("Notifications", Icons.Outlined.Notifications, Icons.Filled.Notifications),
    Bookmarks("Bookmarks", Icons.Outlined.BookmarkBorder, Icons.Filled.Bookmark),
    Profile("Profile", Icons.Outlined.PersonOutline, Icons.Filled.Person),
}

/**
 * Chatting mode's Twitter: the cast tweets in character, replies, likes and reposts,
 * with the home timeline, explore, notifications, bookmarks, profiles and threads.
 */
@Composable
fun TwitterScreen(
    onOpenMessages: () -> Unit,
    viewModel: SocialFeedViewModel = hiltViewModel(key = PLATFORM_TWITTER),
) {
    LaunchedEffect(Unit) { viewModel.bind(PLATFORM_TWITTER) }
    val state by viewModel.uiState.collectAsState()
    val c = if (socialDark()) TwDark else TwLight
    var tab by rememberSaveable { mutableStateOf(TwTab.Home) }
    // Pushed screens: "post:<id>" or "profile:<characterId|you>".
    var stack by rememberSaveable { mutableStateOf(listOf<String>()) }
    var composeOpen by rememberSaveable { mutableStateOf(false) }
    var quoteId by rememberSaveable { mutableStateOf<String?>(null) }
    val push: (String) -> Unit = { stack = stack + it }
    BackHandler(stack.isNotEmpty() && !composeOpen) { stack = stack.dropLast(1) }
    BackHandler(composeOpen) { composeOpen = false; quoteId = null; viewModel.clearImage() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.attachImage(uri)
    }
    LaunchedEffect(state.mediaPickRequestId) {
        if (state.mediaPickRequestId > 0) picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    LaunchedEffect(state.castLoaded) {
        // First visit: fill an empty timeline so there is something to scroll.
        if (state.castLoaded && state.posts.isEmpty()) viewModel.refreshFeed(5)
    }

    val actions = TweetActions(
        onOpenPost = { push("post:$it") },
        onOpenProfile = { push("profile:" + (it ?: "you")) },
        onLike = { viewModel.react(it, "like") },
        onRepost = viewModel::toggleRepost,
        onQuote = { quoteId = it; composeOpen = true },
        onBookmark = viewModel::toggleBookmark,
        onDelete = viewModel::delete,
        onReply = { push("post:$it") },
    )

    Box(Modifier.fillMaxSize().background(c.bg).navigationBarsPadding().imePadding()) {
        Column(Modifier.fillMaxSize()) {
            val top = stack.lastOrNull()
            Box(Modifier.weight(1f)) {
                when {
                    top?.startsWith("post:") == true -> TweetDetail(
                        postId = top.removePrefix("post:"),
                        state = state,
                        c = c,
                        actions = actions,
                        onBack = { stack = stack.dropLast(1) },
                        onReply = { parent, text, to -> viewModel.reply(parent, text, to) },
                    )
                    top?.startsWith("profile:") == true -> TwitterProfile(
                        who = top.removePrefix("profile:"),
                        state = state,
                        c = c,
                        actions = actions,
                        onBack = { stack = stack.dropLast(1) },
                        onFollow = viewModel::toggleFollow,
                        onMessage = onOpenMessages,
                    )
                    else -> when (tab) {
                        TwTab.Home -> TwitterHome(state, c, actions, onRefresh = { viewModel.refreshFeed() }, onAvatar = { push("profile:you") })
                        TwTab.Explore -> TwitterExplore(state, c, actions, onFollow = viewModel::toggleFollow)
                        TwTab.Notifications -> TwitterNotifications(state, c, onOpen = { id -> id?.let { push("post:$it") } })
                        TwTab.Bookmarks -> TwitterBookmarks(state, c, actions)
                        TwTab.Profile -> TwitterProfile(
                            who = "you",
                            state = state,
                            c = c,
                            actions = actions,
                            onBack = null,
                            onFollow = viewModel::toggleFollow,
                            onMessage = onOpenMessages,
                        )
                    }
                }
                if (state.generating) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                        color = c.blue,
                        trackColor = Color.Transparent,
                    )
                }
                if (state.error.isNotBlank()) {
                    Row(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.blue)
                            .clickable { viewModel.dismissError() }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) { Text(state.error, color = Color.White, fontSize = 14.sp) }
                }
                if (stack.isEmpty() && tab != TwTab.Notifications) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(c.blue)
                            .clickable { quoteId = null; composeOpen = true },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Add, "Post", tint = Color.White, modifier = Modifier.size(28.dp)) }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                TwTab.entries.forEach { t ->
                    val selected = t == tab && stack.isEmpty()
                    Box(
                        Modifier.weight(1f).fillMaxSize().clickable { tab = t; stack = emptyList() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(if (selected) t.selectedIcon else t.icon, t.label, tint = c.text, modifier = Modifier.size(26.dp))
                        if (t == TwTab.Notifications && state.notifications.isNotEmpty() && tab != TwTab.Notifications) {
                            Box(
                                Modifier
                                    .align(Alignment.Center)
                                    .offset(x = 10.dp, y = (-10).dp)
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(c.blue),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    state.notifications.size.coerceAtMost(9).toString(),
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (composeOpen) {
            TwitterCompose(
                state = state,
                c = c,
                quote = quoteId?.let { state.allById[it] },
                onPickImage = viewModel::requestImagePick,
                onClearImage = viewModel::clearImage,
                onCancel = { composeOpen = false; quoteId = null; viewModel.clearImage() },
                onPost = { text ->
                    viewModel.post(text, quoteOf = quoteId)
                    composeOpen = false
                    quoteId = null
                },
            )
        }
    }
}

private class TweetActions(
    val onOpenPost: (String) -> Unit,
    val onOpenProfile: (String?) -> Unit,
    val onLike: (String) -> Unit,
    val onRepost: (String) -> Unit,
    val onQuote: (String) -> Unit,
    val onBookmark: (String) -> Unit,
    val onDelete: (String) -> Unit,
    val onReply: (String) -> Unit,
)

// ------------------------------------------------------------------ home

@Composable
private fun TwitterHome(
    state: SocialUiState,
    c: TwColors,
    actions: TweetActions,
    onRefresh: () -> Unit,
    onAvatar: () -> Unit,
) {
    var following by rememberSaveable { mutableStateOf(false) }
    val posts = if (following) state.posts.filter { it.authorCharacterId in state.followingIds || it.isYou } else state.posts
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp)) {
            Box(Modifier.align(Alignment.CenterStart).clip(CircleShape).clickable(onClick = onAvatar)) {
                SocialAvatar(state.personaName, com.ihy2ln.weaverse.core.roleplay.avatarColorHexFor(state.personaName, null), 32.dp)
            }
            Text("🐦", fontSize = 26.sp, modifier = Modifier.align(Alignment.Center))
            IconButton(onClick = onRefresh, modifier = Modifier.align(Alignment.CenterEnd), enabled = !state.generating) {
                Icon(Icons.Outlined.Refresh, "Load new posts", tint = c.text)
            }
        }
        Row(Modifier.fillMaxWidth().height(48.dp)) {
            listOf("For you" to false, "Following" to true).forEach { (label, value) ->
                val selected = following == value
                Box(Modifier.weight(1f).fillMaxSize().clickable { following = value }, contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        fontSize = 15.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) c.text else c.muted,
                    )
                    if (selected) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .width(56.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(c.blue),
                        )
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
            if (state.generating) {
                item(key = "loading") {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = c.blue, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(state.status, color = c.muted, fontSize = 14.sp)
                    }
                }
            } else if (state.posts.isNotEmpty()) {
                item(key = "show-more") {
                    Text(
                        "Show new posts",
                        color = c.blue,
                        fontSize = 15.sp,
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onRefresh).padding(14.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                }
            }
            if (posts.isEmpty() && !state.generating) {
                item(key = "empty") {
                    EmptyTimeline(
                        c,
                        if (following) "You aren't following anyone yet" else "Welcome to your timeline",
                        if (following) "Follow people from Explore to see their posts here." else "Tap refresh and the cast will start posting.",
                    )
                }
            }
            tweetItems(posts, state, c, actions)
        }
    }
}

private fun LazyListScope.tweetItems(posts: List<SocialPostUi>, state: SocialUiState, c: TwColors, actions: TweetActions) {
    items(posts, key = { it.id }) { post ->
        TweetRow(post, state, c, actions)
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
    }
}

@Composable
private fun EmptyTimeline(c: TwColors, title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(32.dp)) {
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = c.text, lineHeight = 34.sp)
        Spacer(Modifier.height(8.dp))
        Text(body, fontSize = 15.sp, color = c.muted)
    }
}

@Composable
private fun TweetRow(post: SocialPostUi, state: SocialUiState, c: TwColors, actions: TweetActions, showReplyingTo: Boolean = true) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { actions.onOpenPost(post.parentId ?: post.id) }
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
    ) {
        if (post.userReposted) {
            Row(Modifier.padding(start = 28.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Repeat, null, tint = c.muted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text("You reposted", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.muted)
            }
        }
        Row {
            Box(Modifier.clip(CircleShape).clickable { actions.onOpenProfile(post.authorCharacterId) }) {
                SocialAvatar(post.authorName, post.colorHex, 40.dp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        post.authorName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (post.verified) {
                        Spacer(Modifier.width(2.dp))
                        Icon(Icons.Filled.Verified, "Verified", tint = c.blue, modifier = Modifier.size(16.dp))
                    }
                    Text(
                        " @${post.handle} · ${compactAge(post.createdAt)}",
                        fontSize = 15.sp,
                        color = c.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.weight(0.01f))
                    Box {
                        Icon(
                            Icons.Filled.MoreHoriz,
                            "More",
                            tint = c.muted,
                            modifier = Modifier.size(18.dp).clip(CircleShape).clickable { menu = true },
                        )
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (post.isYou) {
                                DropdownMenuItem(text = { Text("Delete", color = Color(0xFFF4212E)) }, onClick = { menu = false; actions.onDelete(post.id) })
                            } else {
                                DropdownMenuItem(text = { Text("View @${post.handle}") }, onClick = { menu = false; actions.onOpenProfile(post.authorCharacterId) })
                            }
                            DropdownMenuItem(
                                text = { Text(if (post.bookmarked) "Remove Bookmark" else "Bookmark") },
                                onClick = { menu = false; actions.onBookmark(post.id) },
                            )
                        }
                    }
                }
                if (showReplyingTo && post.parentId != null) {
                    val parent = state.allById[post.parentId]
                    if (parent != null) {
                        Text(
                            linkified("Replying to @${parent.handle}", c.blue),
                            fontSize = 14.sp,
                            color = c.muted,
                        )
                    }
                }
                if (post.text.isNotBlank()) {
                    Text(linkified(post.text, c.blue), fontSize = 15.sp, lineHeight = 20.sp, color = c.text)
                }
                post.imagePath?.let { TweetImage(it, c) }
                post.repostOf?.let { QuotedTweet(it, c) { actions.onOpenPost(it.id) } }
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TweetAction(Icons.Outlined.ChatBubbleOutline, post.replyCount, c.muted, c.blue) { actions.onReply(post.parentId ?: post.id) }
                    RepostAction(post, c, actions)
                    TweetAction(
                        if (post.userReaction.isNotBlank()) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        post.likeCount,
                        if (post.userReaction.isNotBlank()) c.pink else c.muted,
                        c.pink,
                    ) { actions.onLike(post.id) }
                    TweetAction(Icons.Outlined.BarChart, post.viewCount, c.muted, c.blue) {}
                    Row {
                        Icon(
                            if (post.bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            "Bookmark",
                            tint = if (post.bookmarked) c.blue else c.muted,
                            modifier = Modifier.size(34.dp).clip(CircleShape).clickable { actions.onBookmark(post.id) }.padding(8.dp),
                        )
                        Icon(
                            Icons.Outlined.Share,
                            "Share",
                            tint = c.muted,
                            modifier = Modifier.size(34.dp).clip(CircleShape).clickable { sharePost(context, post) }.padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun sharePost(context: android.content.Context, post: SocialPostUi) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, "${post.authorName} (@${post.handle}): ${post.text}")
    }
    runCatching { context.startActivity(android.content.Intent.createChooser(intent, "Share post")) }
}

@Composable
private fun RepostAction(post: SocialPostUi, c: TwColors, actions: TweetActions) {
    var menu by remember { mutableStateOf(false) }
    Box {
        TweetAction(Icons.Filled.Repeat, post.repostCount, if (post.userReposted) c.green else c.muted, c.green) { menu = true }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(if (post.userReposted) "Undo repost" else "Repost") },
                leadingIcon = { Icon(Icons.Filled.Repeat, null) },
                onClick = { menu = false; actions.onRepost(post.id) },
            )
            DropdownMenuItem(
                text = { Text("Quote") },
                leadingIcon = { Icon(Icons.Outlined.FormatQuote, null) },
                onClick = { menu = false; actions.onQuote(post.id) },
            )
        }
    }
}

@Composable
private fun TweetAction(icon: ImageVector, count: Int, tint: Color, hot: Color, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(30.dp).padding(6.dp))
        Text(
            if (count > 0) compactCount(count) else "",
            fontSize = 13.sp,
            color = if (tint == hot) hot else tint,
            modifier = Modifier.width(40.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun TweetImage(path: String, c: TwColors) {
    coil3.compose.AsyncImage(
        model = java.io.File(path),
        contentDescription = "Image",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .heightIn(max = 280.dp)
            .height(220.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, c.divider, RoundedCornerShape(16.dp)),
    )
}

@Composable
private fun QuotedTweet(post: SocialPostUi, c: TwColors, onClick: () -> Unit) {
    Column(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, c.divider, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SocialAvatar(post.authorName, post.colorHex, 20.dp)
            Spacer(Modifier.width(6.dp))
            Text(post.authorName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = c.text, maxLines = 1)
            if (post.verified) Icon(Icons.Filled.Verified, null, tint = c.blue, modifier = Modifier.size(14.dp))
            Text(" @${post.handle} · ${compactAge(post.createdAt)}", fontSize = 14.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(linkified(post.text, c.blue), fontSize = 14.sp, color = c.text, maxLines = 6, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------- detail

@Composable
private fun TopBar(title: String, subtitle: String? = null, c: TwColors, onBack: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = c.text) }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Column {
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = c.text, maxLines = 1)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = c.muted)
        }
    }
}

@Composable
private fun TweetDetail(
    postId: String,
    state: SocialUiState,
    c: TwColors,
    actions: TweetActions,
    onBack: () -> Unit,
    onReply: (String, String, SocialPostUi?) -> Unit,
) {
    val post = state.allById[postId]
    var draft by rememberSaveable(postId) { mutableStateOf("") }
    var replyTo by remember(postId) { mutableStateOf<SocialPostUi?>(null) }
    Column(Modifier.fillMaxSize()) {
        TopBar("Post", c = c, onBack = onBack)
        if (post == null) {
            EmptyTimeline(c, "Hmm…this page doesn't exist.", "This post was deleted.")
            return@Column
        }
        val replies = state.repliesByParent[post.id].orEmpty()
        LazyColumn(Modifier.weight(1f)) {
            item(key = "main") {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.clip(CircleShape).clickable { actions.onOpenProfile(post.authorCharacterId) }) {
                            SocialAvatar(post.authorName, post.colorHex, 44.dp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(post.authorName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = c.text)
                                if (post.verified) Icon(Icons.Filled.Verified, null, tint = c.blue, modifier = Modifier.size(17.dp))
                            }
                            Text("@${post.handle}", fontSize = 15.sp, color = c.muted)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(linkified(post.text, c.blue), fontSize = 17.sp, lineHeight = 24.sp, color = c.text)
                    post.imagePath?.let { TweetImage(it, c) }
                    post.repostOf?.let { QuotedTweet(it, c) { actions.onOpenPost(it.id) } }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        SimpleDateFormat("h:mm a · MMM d, yyyy", Locale.getDefault()).format(Date(post.createdAt)) +
                            " · " + compactCount(post.viewCount) + " Views",
                        fontSize = 15.sp,
                        color = c.muted,
                    )
                    Box(Modifier.padding(vertical = 12.dp).fillMaxWidth().height(1.dp).background(c.divider))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Stat(post.repostCount, "Reposts", c)
                        Stat(post.replyCount, "Replies", c)
                        Stat(post.likeCount, "Likes", c)
                        Stat(if (post.bookmarked) 1 else 0, "Bookmarks", c)
                    }
                    Box(Modifier.padding(vertical = 12.dp).fillMaxWidth().height(1.dp).background(c.divider))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        Icon(Icons.Outlined.ChatBubbleOutline, "Reply", tint = c.muted, modifier = Modifier.size(22.dp).clickable { replyTo = null })
                        Box { RepostAction(post, c, actions) }
                        Icon(
                            if (post.userReaction.isNotBlank()) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            "Like",
                            tint = if (post.userReaction.isNotBlank()) c.pink else c.muted,
                            modifier = Modifier.size(22.dp).clickable { actions.onLike(post.id) },
                        )
                        Icon(
                            if (post.bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            "Bookmark",
                            tint = if (post.bookmarked) c.blue else c.muted,
                            modifier = Modifier.size(22.dp).clickable { actions.onBookmark(post.id) },
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
            }
            items(replies, key = { it.id }) { reply ->
                Box(Modifier.clickable { replyTo = reply }) {
                    TweetRow(reply, state, c, actions, showReplyingTo = true)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
            }
            if (state.generating) {
                item(key = "typing") {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = c.blue, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Someone is replying…", color = c.muted, fontSize = 14.sp)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
        if (replyTo != null) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(linkified("Replying to @${replyTo!!.handle}", c.blue), fontSize = 13.sp, color = c.muted, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.Close, "Cancel", tint = c.muted, modifier = Modifier.padding(end = 12.dp).size(16.dp).clickable { replyTo = null })
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SocialAvatar(state.personaName, com.ihy2ln.weaverse.core.roleplay.avatarColorHexFor(state.personaName, null), 32.dp)
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = draft,
                onValueChange = { if (it.length <= 280) draft = it },
                textStyle = TextStyle(color = c.text, fontSize = 16.sp),
                cursorBrush = SolidColor(c.blue),
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                decorationBox = { inner ->
                    if (draft.isEmpty()) Text("Post your reply", color = c.muted, fontSize = 16.sp)
                    inner()
                },
            )
            PillButton("Reply", enabled = draft.isNotBlank(), c = c) {
                onReply(post.id, draft, replyTo)
                draft = ""
                replyTo = null
            }
        }
    }
}

@Composable
private fun Stat(value: Int, label: String, c: TwColors) {
    Text(
        androidx.compose.ui.text.buildAnnotatedString {
            pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = c.text))
            append(compactCount(value))
            pop()
            append(" $label")
        },
        fontSize = 14.sp,
        color = c.muted,
    )
}

@Composable
private fun PillButton(label: String, enabled: Boolean = true, c: TwColors, filled: Boolean = true, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = if (filled) Color.White else c.text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (!filled) Color.Transparent else if (enabled) c.blue else c.blue.copy(alpha = 0.5f))
            .border(if (filled) 0.dp else 1.dp, c.divider, RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
    )
}

// ---------------------------------------------------------------- compose

@Composable
private fun TwitterCompose(
    state: SocialUiState,
    c: TwColors,
    quote: SocialPostUi?,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
    onCancel: () -> Unit,
    onPost: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(c.bg).clickable(enabled = false) {}) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Cancel", fontSize = 16.sp, color = c.text, modifier = Modifier.clickable(onClick = onCancel).padding(4.dp))
            Spacer(Modifier.weight(1f))
            PillButton("Post", enabled = text.isNotBlank() || state.pendingImagePath != null || quote != null, c = c) {
                onPost(text)
                text = ""
            }
        }
        Row(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp)) {
            SocialAvatar(state.personaName, com.ihy2ln.weaverse.core.roleplay.avatarColorHexFor(state.personaName, null), 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Everyone ▾",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = c.blue,
                    modifier = Modifier.border(1.dp, c.blue.copy(alpha = 0.5f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 2.dp),
                )
                BasicTextField(
                    value = text,
                    onValueChange = { if (it.length <= 280) text = it },
                    textStyle = TextStyle(color = c.text, fontSize = 19.sp),
                    cursorBrush = SolidColor(c.blue),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 80.dp),
                    decorationBox = { inner ->
                        if (text.isEmpty()) Text(if (quote != null) "Add a comment" else "What's happening?", color = c.muted, fontSize = 19.sp)
                        inner()
                    },
                )
                state.pendingImagePath?.let { path ->
                    Box {
                        TweetImage(path, c)
                        Icon(
                            Icons.Filled.Close,
                            "Remove image",
                            tint = Color.White,
                            modifier = Modifier.padding(14.dp).size(28.dp).clip(CircleShape).background(Color(0xBF000000)).clickable(onClick = onClearImage).padding(4.dp),
                        )
                    }
                }
                quote?.let { QuotedTweet(it, c) {} }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Public, null, tint = c.blue, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Everyone can reply", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.blue)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPickImage) { Icon(Icons.Outlined.Image, "Add photo", tint = c.blue) }
            Spacer(Modifier.weight(1f))
            val progress = text.length / 280f
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.size(24.dp),
                    color = if (text.length > 260) Color(0xFFFFD400) else c.blue,
                    trackColor = c.divider,
                    strokeWidth = 2.5.dp,
                )
                if (text.length > 260) Text((280 - text.length).toString(), fontSize = 10.sp, color = c.muted)
            }
        }
    }
}

// --------------------------------------------------------------- explore

@Composable
private fun TwitterExplore(state: SocialUiState, c: TwColors, actions: TweetActions, onFollow: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(50))
                .background(c.search)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, null, tint = c.muted, modifier = Modifier.size(18.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 15.sp),
                cursorBrush = SolidColor(c.blue),
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 10.dp),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Search", color = c.muted, fontSize = 15.sp)
                    inner()
                },
            )
            if (query.isNotEmpty()) Icon(Icons.Filled.Close, "Clear", tint = c.muted, modifier = Modifier.size(18.dp).clickable { query = "" })
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
            if (query.isNotBlank()) {
                val people = state.people.filter { it.name.contains(query, true) || it.handle.contains(query.removePrefix("@"), true) }
                items(people, key = { "p-" + it.characterId }) { person -> WhoToFollowRow(person, c, actions, onFollow) }
                val posts = (state.posts + state.repliesByParent.values.flatten()).filter { it.text.contains(query, true) }
                tweetItems(posts, state, c, actions)
                if (people.isEmpty() && posts.isEmpty()) {
                    item(key = "none") { EmptyTimeline(c, "No results for \"$query\"", "Try searching for something else.") }
                }
            } else {
                item(key = "trends-title") { SectionTitle("Trends for you", c) }
                if (state.trends.isEmpty()) {
                    item(key = "no-trends") { Text("Nothing's trending yet.", color = c.muted, modifier = Modifier.padding(16.dp)) }
                }
                items(state.trends, key = { "t-" + it.first }) { (tag, count) ->
                    Column(Modifier.fillMaxWidth().clickable { query = tag }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text("Trending", fontSize = 13.sp, color = c.muted)
                        Text(tag, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.text)
                        Text("${compactCount(count)} posts", fontSize = 13.sp, color = c.muted)
                    }
                }
                item(key = "wtf-div") { Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(1.dp).background(c.divider)) }
                item(key = "wtf-title") { SectionTitle("Who to follow", c) }
                items(state.people.sortedBy { it.isFollowing }.take(12), key = { "w-" + it.characterId }) { person ->
                    WhoToFollowRow(person, c, actions, onFollow)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, c: TwColors) {
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = c.text, modifier = Modifier.padding(16.dp))
}

@Composable
private fun WhoToFollowRow(person: SocialPersonUi, c: TwColors, actions: TweetActions, onFollow: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { actions.onOpenProfile(person.characterId) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SocialAvatar(person.name, person.colorHex, 40.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(person.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (person.verified) Icon(Icons.Filled.Verified, null, tint = c.blue, modifier = Modifier.size(16.dp))
            }
            Text("@${person.handle}", fontSize = 14.sp, color = c.muted)
            if (person.bio.isNotBlank()) Text(person.bio, fontSize = 14.sp, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        FollowButton(person.isFollowing, c) { onFollow(person.characterId) }
    }
}

@Composable
private fun FollowButton(following: Boolean, c: TwColors, onClick: () -> Unit) {
    Text(
        if (following) "Following" else "Follow",
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = if (following) c.text else c.bg,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (following) Color.Transparent else c.text)
            .border(1.dp, if (following) c.divider else c.text, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

// ---------------------------------------------------------- notifications

@Composable
private fun TwitterNotifications(state: SocialUiState, c: TwColors, onOpen: (String?) -> Unit) {
    var mentionsOnly by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TopBar("Notifications", c = c, onBack = null)
        Row(Modifier.fillMaxWidth().height(48.dp)) {
            listOf("All" to false, "Mentions" to true).forEach { (label, value) ->
                val selected = mentionsOnly == value
                Box(Modifier.weight(1f).fillMaxSize().clickable { mentionsOnly = value }, contentAlignment = Alignment.Center) {
                    Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) c.text else c.muted, fontSize = 15.sp)
                    if (selected) Box(Modifier.align(Alignment.BottomCenter).width(56.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.blue))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
        val shown = state.notifications.filter { !mentionsOnly || it.kind == "reply" || it.kind == "mention" }
        LazyColumn(Modifier.fillMaxSize()) {
            if (shown.isEmpty()) {
                item(key = "empty") { EmptyTimeline(c, "Nothing to see here — yet", "Likes, mentions, reposts and replies show up here.") }
            }
            items(shown, key = { it.id }) { n ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(n.postId) }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    val (icon, tint) = when (n.kind) {
                        "like" -> Icons.Filled.Favorite to c.pink
                        "repost" -> Icons.Filled.Repeat to c.green
                        "follow" -> Icons.Filled.Person to c.blue
                        "mention" -> Icons.Outlined.AlternateEmail to c.blue
                        else -> Icons.Outlined.ChatBubbleOutline to c.blue
                    }
                    Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        SocialAvatar(n.actorName, n.actorColorHex, 32.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(n.text, fontSize = 15.sp, color = c.text)
                        Text(compactAge(n.createdAt), fontSize = 13.sp, color = c.muted)
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
            }
        }
    }
}

@Composable
private fun TwitterBookmarks(state: SocialUiState, c: TwColors, actions: TweetActions) {
    val saved = state.allById.values.filter { it.bookmarked }.sortedByDescending { it.createdAt }
    Column(Modifier.fillMaxSize()) {
        TopBar("Bookmarks", "@${state.youHandle}", c, onBack = null)
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
            if (saved.isEmpty()) {
                item(key = "empty") { EmptyTimeline(c, "Save posts for later", "Bookmark posts to easily find them again in the future.") }
            }
            tweetItems(saved, state, c, actions)
        }
    }
}

// ---------------------------------------------------------------- profile

@Composable
private fun TwitterProfile(
    who: String,
    state: SocialUiState,
    c: TwColors,
    actions: TweetActions,
    onBack: (() -> Unit)?,
    onFollow: (String) -> Unit,
    onMessage: () -> Unit,
) {
    val isYou = who == "you"
    val person = state.people.firstOrNull { it.characterId == who }
    val name = if (isYou) state.personaName else person?.name ?: "Account"
    val handle = if (isYou) state.youHandle else person?.handle ?: "unknown"
    val color = if (isYou) com.ihy2ln.weaverse.core.roleplay.avatarColorHexFor(name, null) else person?.colorHex ?: "#1D9BF0"
    val all = state.allById.values
    val theirs = all.filter { if (isYou) it.isYou else it.authorCharacterId == who }.sortedByDescending { it.createdAt }
    var tab by rememberSaveable(who) { mutableStateOf(0) }
    val shown = when (tab) {
        0 -> theirs.filter { it.parentId == null }
        1 -> theirs.filter { it.parentId != null }
        2 -> theirs.filter { it.imagePath != null }
        else -> if (isYou) all.filter { it.userReaction.isNotBlank() }.sortedByDescending { it.createdAt } else emptyList()
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        item(key = "header") {
            Box(Modifier.fillMaxWidth().height(170.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(120.dp).background(
                        Brush.linearGradient(listOf(parseHexColor(color, c.blue), parseHexColor(color, c.blue).copy(alpha = 0.45f))),
                    ),
                )
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(8.dp).size(34.dp).clip(CircleShape).background(Color(0x99000000)),
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                }
                Box(Modifier.align(Alignment.BottomStart).padding(start = 16.dp).clip(CircleShape).background(c.bg).padding(4.dp)) {
                    SocialAvatar(name, color, 72.dp)
                }
                Row(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isYou) {
                        PillButton("Edit profile", c = c, filled = false) {}
                    } else if (person != null) {
                        Icon(
                            Icons.Outlined.MailOutline,
                            "Message",
                            tint = c.text,
                            modifier = Modifier.size(36.dp).clip(CircleShape).border(1.dp, c.divider, CircleShape).clickable(onClick = onMessage).padding(7.dp),
                        )
                        FollowButton(person.isFollowing, c) { onFollow(person.characterId) }
                    }
                }
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = c.text)
                        if (person?.verified == true) Icon(Icons.Filled.Verified, null, tint = c.blue, modifier = Modifier.size(20.dp))
                    }
                    Text("@$handle", fontSize = 15.sp, color = c.muted)
                }
                val bio = if (isYou) state.personaBio else person?.bio.orEmpty()
                if (bio.isNotBlank()) Text(linkified(bio, c.blue), fontSize = 15.sp, color = c.text)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CalendarMonth, null, tint = c.muted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Joined " + SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(person?.joinedAt ?: System.currentTimeMillis())),
                        fontSize = 15.sp,
                        color = c.muted,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Stat(if (isYou) state.followingIds.size else person?.following ?: 0, "Following", c)
                    Stat(if (isYou) state.followingIds.size else (person?.followers ?: 0) + (if (person?.isFollowing == true) 1 else 0), "Followers", c)
                }
            }
            Row(Modifier.fillMaxWidth().height(48.dp)) {
                listOf("Posts", "Replies", "Media", "Likes").forEachIndexed { index, label ->
                    val selected = tab == index
                    Box(Modifier.weight(1f).fillMaxSize().clickable { tab = index }, contentAlignment = Alignment.Center) {
                        Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) c.text else c.muted, fontSize = 15.sp)
                        if (selected) Box(Modifier.align(Alignment.BottomCenter).width(48.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.blue))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
        }
        if (shown.isEmpty()) {
            item(key = "empty") {
                EmptyTimeline(
                    c,
                    if (isYou) "You haven't posted yet" else "@$handle hasn't posted",
                    if (isYou) "When you post, it'll show up here." else "When they do, their posts will show up here.",
                )
            }
        }
        tweetItems(shown, state, c, actions)
    }
}
