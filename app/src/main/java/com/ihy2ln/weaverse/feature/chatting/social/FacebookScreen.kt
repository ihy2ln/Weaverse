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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.EmojiEmotions
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.roleplay.avatarColorHexFor
import com.ihy2ln.weaverse.core.ui.util.parseHexColor

private data class FbColors(
    val bg: Color,
    val card: Color,
    val text: Color,
    val muted: Color,
    val divider: Color,
    val input: Color,
    val bubble: Color,
    val button: Color,
    val unread: Color,
) {
    val blue = Color(0xFF1877F2)
    val green = Color(0xFF45BD62)
}

private val FbLight = FbColors(
    bg = Color(0xFFF0F2F5),
    card = Color(0xFFFFFFFF),
    text = Color(0xFF050505),
    muted = Color(0xFF65676B),
    divider = Color(0xFFCED0D4),
    input = Color(0xFFF0F2F5),
    bubble = Color(0xFFF0F2F5),
    button = Color(0xFFE4E6EB),
    unread = Color(0xFFE7F3FF),
)
private val FbDark = FbColors(
    bg = Color(0xFF18191A),
    card = Color(0xFF242526),
    text = Color(0xFFE4E6EB),
    muted = Color(0xFFB0B3B8),
    divider = Color(0xFF3E4042),
    input = Color(0xFF3A3B3C),
    bubble = Color(0xFF3A3B3C),
    button = Color(0xFF3A3B3C),
    unread = Color(0xFF263951),
)

private enum class FbTab(val icon: ImageVector, val selectedIcon: ImageVector, val label: String) {
    Home(Icons.Outlined.Home, Icons.Filled.Home, "Home"),
    Friends(Icons.Outlined.People, Icons.Filled.People, "Friends"),
    Notifications(Icons.Outlined.Notifications, Icons.Filled.Notifications, "Notifications"),
    Menu(Icons.Outlined.Menu, Icons.Filled.Menu, "Menu"),
}

private val Feelings = listOf(
    "😊" to "happy", "🥰" to "loved", "😇" to "blessed", "😎" to "cool", "🤩" to "excited",
    "😌" to "relaxed", "🤔" to "thoughtful", "😴" to "tired", "😢" to "sad", "😤" to "determined",
    "🥳" to "festive", "😋" to "hungry", "💪" to "strong", "🙏" to "thankful", "😬" to "nervous",
)

private fun feelingEmoji(feeling: String): String =
    Feelings.firstOrNull { it.second.equals(feeling, true) }?.first ?: "🙂"

/**
 * Chatting mode's Facebook: news feed with stories, reactions, comments and shares,
 * friends and friend requests, notifications, and profiles — the cast posts in character.
 */
@Composable
fun FacebookScreen(
    onOpenMessenger: () -> Unit,
    viewModel: SocialFeedViewModel = hiltViewModel(key = PLATFORM_FACEBOOK),
) {
    LaunchedEffect(Unit) { viewModel.bind(PLATFORM_FACEBOOK) }
    val state by viewModel.uiState.collectAsState()
    val c = if (socialDark()) FbDark else FbLight
    var tab by rememberSaveable { mutableStateOf(FbTab.Home) }
    var stack by rememberSaveable { mutableStateOf(listOf<String>()) }
    var composer by rememberSaveable { mutableStateOf(false) }
    var sharing by rememberSaveable { mutableStateOf<String?>(null) }
    var storyIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    val push: (String) -> Unit = { stack = stack + it }
    BackHandler(stack.isNotEmpty() && !composer && storyIndex == null) { stack = stack.dropLast(1) }
    BackHandler(composer) { composer = false; viewModel.clearImage() }
    BackHandler(storyIndex != null) { storyIndex = null }
    BackHandler(searching && stack.isEmpty()) { searching = false }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.attachImage(uri)
    }
    LaunchedEffect(state.mediaPickRequestId) {
        if (state.mediaPickRequestId > 0) picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    LaunchedEffect(state.castLoaded) {
        if (state.castLoaded && state.posts.isEmpty()) viewModel.refreshFeed(5)
    }

    val actions = FbActions(
        onOpenPost = { push("post:$it") },
        onOpenProfile = { push("profile:" + (it ?: "you")) },
        onReact = viewModel::react,
        onShare = { sharing = it },
        onDelete = viewModel::delete,
        onSave = viewModel::toggleBookmark,
    )
    // Stories: the latest post from each person who has posted.
    val stories = state.posts.filter { !it.isYou && it.repostOf == null }.distinctBy { it.authorCharacterId }.take(12)

    Box(Modifier.fillMaxSize().background(c.bg).navigationBarsPadding().imePadding()) {
        Column(Modifier.fillMaxSize()) {
            val top = stack.lastOrNull()
            if (top == null) {
                if (searching) {
                    FbSearchBar(c, onClose = { searching = false })
                } else {
                    FbTopBar(
                        c,
                        onCreate = { composer = true },
                        onSearch = { searching = true },
                        onMessenger = onOpenMessenger,
                    )
                }
                FbTabRow(tab, c, unread = state.notifications.size) { tab = it }
            }
            Box(Modifier.weight(1f)) {
                when {
                    top?.startsWith("post:") == true -> FbPostDetail(
                        postId = top.removePrefix("post:"),
                        state = state,
                        c = c,
                        actions = actions,
                        onBack = { stack = stack.dropLast(1) },
                        onComment = { parent, text, to -> viewModel.reply(parent, text, to) },
                    )
                    top?.startsWith("profile:") == true -> FbProfile(
                        who = top.removePrefix("profile:"),
                        state = state,
                        c = c,
                        actions = actions,
                        onBack = { stack = stack.dropLast(1) },
                        onFriend = viewModel::toggleFollow,
                        onMessage = onOpenMessenger,
                        onCompose = { composer = true },
                    )
                    searching -> FbSearchResults(state, c, actions)
                    else -> when (tab) {
                        FbTab.Home -> FbFeed(
                            state = state,
                            c = c,
                            stories = stories,
                            actions = actions,
                            onCompose = { composer = true },
                            onPhoto = { composer = true; viewModel.requestImagePick() },
                            onStory = { storyIndex = it },
                            onRefresh = { viewModel.refreshFeed() },
                        )
                        FbTab.Friends -> FbFriends(state, c, actions, onFriend = viewModel::toggleFollow)
                        FbTab.Notifications -> FbNotifications(state, c, onOpen = { id -> id?.let { push("post:$it") } })
                        FbTab.Menu -> FbProfile(
                            who = "you",
                            state = state,
                            c = c,
                            actions = actions,
                            onBack = null,
                            onFriend = viewModel::toggleFollow,
                            onMessage = onOpenMessenger,
                            onCompose = { composer = true },
                        )
                    }
                }
                if (state.generating) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter), color = c.blue, trackColor = Color.Transparent)
                }
                if (state.error.isNotBlank()) {
                    Text(
                        state.error,
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF323436))
                            .clickable { viewModel.dismissError() }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
        if (composer) {
            FbComposer(
                state = state,
                c = c,
                onPickImage = viewModel::requestImagePick,
                onClearImage = viewModel::clearImage,
                onCancel = { composer = false; viewModel.clearImage() },
                onPost = { text, feeling ->
                    viewModel.post(text, feeling)
                    composer = false
                },
            )
        }
        sharing?.let { id ->
            FbShareSheet(
                post = state.allById[id],
                c = c,
                onDismiss = { sharing = null },
                onShare = { caption -> viewModel.shareNow(id, caption); sharing = null },
            )
        }
        storyIndex?.let { index ->
            FbStoryViewer(
                stories = stories,
                start = index,
                onClose = { storyIndex = null },
                onReply = { post -> storyIndex = null; push("post:${post.id}") },
            )
        }
    }
}

private class FbActions(
    val onOpenPost: (String) -> Unit,
    val onOpenProfile: (String?) -> Unit,
    val onReact: (String, String) -> Unit,
    val onShare: (String) -> Unit,
    val onDelete: (String) -> Unit,
    val onSave: (String) -> Unit,
)

// ------------------------------------------------------------------ chrome

@Composable
private fun FbTopBar(c: FbColors, onCreate: () -> Unit, onSearch: () -> Unit, onMessenger: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(c.card).padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "facebook",
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (c == FbDark) c.text else c.blue,
            letterSpacing = (-1).sp,
            modifier = Modifier.weight(1f),
        )
        RoundIcon(Icons.Filled.Add, "Create", c, onCreate)
        RoundIcon(Icons.Filled.Search, "Search", c, onSearch)
        RoundIcon(Icons.Filled.Send, "Messenger", c, onMessenger)
    }
}

@Composable
private fun FbSearchBar(c: FbColors, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(c.card).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = c.text) }
        Text("Search Facebook", color = c.muted, fontSize = 16.sp)
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, description: String, c: FbColors, onClick: () -> Unit) {
    Box(
        Modifier.padding(horizontal = 4.dp).size(38.dp).clip(CircleShape).background(c.button).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = c.text, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun FbTabRow(tab: FbTab, c: FbColors, unread: Int, onSelect: (FbTab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(c.card)) {
        Row(Modifier.fillMaxWidth().height(48.dp)) {
            FbTab.entries.forEach { t ->
                val selected = t == tab
                Box(Modifier.weight(1f).fillMaxHeight().clickable { onSelect(t) }, contentAlignment = Alignment.Center) {
                    Icon(
                        if (selected) t.selectedIcon else t.icon,
                        t.label,
                        tint = if (selected) c.blue else c.muted,
                        modifier = Modifier.size(26.dp),
                    )
                    if (t == FbTab.Notifications && unread > 0 && !selected) {
                        Text(
                            if (unread > 9) "9+" else unread.toString(),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .offset(x = 12.dp, y = (-10).dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFE41E3F))
                                .padding(horizontal = 5.dp),
                        )
                    }
                    if (selected) {
                        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp).background(c.blue))
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
    }
}

// ------------------------------------------------------------------- feed

@Composable
private fun FbFeed(
    state: SocialUiState,
    c: FbColors,
    stories: List<SocialPostUi>,
    actions: FbActions,
    onCompose: () -> Unit,
    onPhoto: () -> Unit,
    onStory: (Int) -> Unit,
    onRefresh: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "composer") {
            Row(
                Modifier.fillMaxWidth().background(c.card).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.clip(CircleShape).clickable { actions.onOpenProfile(null) }) {
                    SocialAvatar(state.personaName, avatarColorHexFor(state.personaName, null), 40.dp)
                }
                Text(
                    "What's on your mind?",
                    color = c.text,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, c.divider, RoundedCornerShape(50))
                        .clickable(onClick = onCompose)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                )
                Icon(Icons.Outlined.Image, "Photo", tint = c.green, modifier = Modifier.size(28.dp).clickable(onClick = onPhoto))
            }
        }
        item(key = "stories") {
            LazyRow(
                Modifier.fillMaxWidth().background(c.card).padding(vertical = 10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "create-story") {
                    Column(
                        Modifier
                            .width(108.dp)
                            .height(186.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, c.divider, RoundedCornerShape(12.dp))
                            .background(c.card)
                            .clickable(onClick = onCompose),
                    ) {
                        Box(
                            Modifier.fillMaxWidth().height(130.dp).background(
                                Brush.verticalGradient(listOf(parseHexColor(avatarColorHexFor(state.personaName, null), c.blue), c.card)),
                            ),
                            contentAlignment = Alignment.Center,
                        ) { SocialAvatar(state.personaName, avatarColorHexFor(state.personaName, null), 56.dp) }
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                            Box(
                                Modifier.offset(y = (-16).dp).size(32.dp).clip(CircleShape).background(c.card).padding(3.dp).clip(CircleShape).background(c.blue),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                            Text("Create story", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.text, modifier = Modifier.padding(top = 20.dp))
                        }
                    }
                }
                items(stories.size, key = { "story-" + stories[it].id }) { index ->
                    val story = stories[index]
                    val tint = parseHexColor(story.colorHex, c.blue)
                    Box(
                        Modifier
                            .width(108.dp)
                            .height(186.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.verticalGradient(listOf(tint, tint.copy(alpha = 0.55f), Color(0xFF111111))))
                            .clickable { onStory(index) },
                    ) {
                        if (story.imagePath != null) {
                            coil3.compose.AsyncImage(
                                model = java.io.File(story.imagePath),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text(
                                story.text,
                                color = Color.White,
                                fontSize = 11.sp,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.align(Alignment.Center).padding(10.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                        SocialAvatar(story.authorName, story.colorHex, 32.dp, ring = c.blue, modifier = Modifier.padding(8.dp))
                        Text(
                            story.authorName,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                        )
                    }
                }
            }
        }
        if (state.generating) {
            item(key = "loading") {
                Row(Modifier.fillMaxWidth().background(c.card).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = c.blue, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(state.status, color = c.muted)
                }
            }
        } else {
            item(key = "refresh") {
                Row(
                    Modifier.fillMaxWidth().background(c.card).clickable(onClick = onRefresh).padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Refresh, null, tint = c.blue, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("See new posts", color = c.blue, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (state.posts.isEmpty() && !state.generating) {
            item(key = "empty") {
                Column(Modifier.fillMaxWidth().background(c.card).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No posts yet", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.text)
                    Text("Tap See new posts and your characters will start sharing.", color = c.muted, textAlign = TextAlign.Center)
                }
            }
        }
        items(state.posts, key = { it.id }) { post ->
            FbPostCard(post, state, c, actions)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FbPostCard(post: SocialPostUi, state: SocialUiState, c: FbColors, actions: FbActions, inDetail: Boolean = false) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(c.card)) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(CircleShape).clickable { actions.onOpenProfile(post.authorCharacterId) }) {
                SocialAvatar(post.authorName, post.colorHex, 40.dp)
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    androidx.compose.ui.text.buildAnnotatedString {
                        pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold))
                        append(post.authorName)
                        pop()
                        when {
                            post.repostOf != null -> append(" shared a post.")
                            post.feeling.isNotBlank() -> append(" is ${feelingEmoji(post.feeling)} feeling ${post.feeling}.")
                        }
                    },
                    color = c.text,
                    fontSize = 15.sp,
                    modifier = Modifier.clickable { actions.onOpenProfile(post.authorCharacterId) },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(facebookAge(post.createdAt) + " · ", color = c.muted, fontSize = 13.sp)
                    Icon(Icons.Filled.Public, "Public", tint = c.muted, modifier = Modifier.size(12.dp))
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreHoriz, "Post options", tint = c.muted) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (post.bookmarked) "Unsave post" else "Save post") },
                        leadingIcon = { Icon(if (post.bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, null) },
                        onClick = { menu = false; actions.onSave(post.id) },
                    )
                    if (post.isYou) {
                        DropdownMenuItem(text = { Text("Move to trash") }, onClick = { menu = false; actions.onDelete(post.id) })
                    }
                }
            }
        }
        if (post.text.isNotBlank()) {
            val big = post.text.length < 85 && post.imagePath == null && post.repostOf == null
            Text(
                linkified(post.text, c.blue),
                color = c.text,
                fontSize = if (big) 22.sp else 15.sp,
                lineHeight = if (big) 28.sp else 20.sp,
                maxLines = if (inDetail) Int.MAX_VALUE else 8,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).clickable { actions.onOpenPost(post.id) },
            )
        } else {
            Spacer(Modifier.height(8.dp))
        }
        post.imagePath?.let { path ->
            coil3.compose.AsyncImage(
                model = java.io.File(path),
                contentDescription = "Photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(300.dp).clickable { actions.onOpenPost(post.id) },
            )
        }
        post.repostOf?.let { shared ->
            Column(
                Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, c.divider, RoundedCornerShape(8.dp))
                    .clickable { actions.onOpenPost(shared.id) },
            ) {
                shared.imagePath?.let {
                    coil3.compose.AsyncImage(
                        model = java.io.File(it),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                    )
                }
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    SocialAvatar(shared.authorName, shared.colorHex, 32.dp)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(shared.authorName, fontWeight = FontWeight.Bold, color = c.text, fontSize = 14.sp)
                        Text(facebookAge(shared.createdAt), color = c.muted, fontSize = 12.sp)
                    }
                }
                Text(shared.text, color = c.text, fontSize = 14.sp, maxLines = 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp))
            }
        }
        // Reaction summary: stacked reaction icons and the count, comments and shares on the right.
        if (post.likeCount > 0 || post.replyCount > 0 || post.repostCount > 0) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (post.likeCount > 0) {
                    Box(Modifier.width((18 + (post.topReactions.size - 1).coerceAtLeast(0) * 12).dp)) {
                        post.topReactions.ifEmpty { listOf(FbReaction.Like) }.forEachIndexed { i, r ->
                            Box(
                                Modifier
                                    .offset(x = (i * 12).dp)
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(c.card)
                                    .padding(1.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(r.emoji, fontSize = 12.sp) }
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (post.userReaction.isNotBlank()) {
                            if (post.likeCount > 1) "You and ${compactCount(post.likeCount - 1)} others" else state.personaName
                        } else {
                            compactCount(post.likeCount)
                        },
                        color = c.muted,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                val tail = buildList {
                    if (post.replyCount > 0) add("${post.replyCount} comment${if (post.replyCount == 1) "" else "s"}")
                    if (post.repostCount > 0) add("${post.repostCount} share${if (post.repostCount == 1) "" else "s"}")
                }.joinToString(" · ")
                Text(tail, color = c.muted, fontSize = 14.sp, modifier = Modifier.clickable { actions.onOpenPost(post.id) })
            }
        }
        Box(Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(1.dp).background(c.divider))
        FbActionRow(post, c, actions)
        if (!inDetail) {
            val comments = state.repliesByParent[post.id].orEmpty()
            if (comments.size > 2) {
                Text(
                    "View more comments",
                    color = c.muted,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 12.dp).clickable { actions.onOpenPost(post.id) },
                )
            }
            comments.takeLast(2).forEach { comment ->
                FbComment(comment, c, actions, onReply = { actions.onOpenPost(post.id) }, onLike = { actions.onReact(comment.id, "like") })
            }
            Spacer(Modifier.height(if (comments.isEmpty()) 0.dp else 8.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FbActionRow(post: SocialPostUi, c: FbColors, actions: FbActions) {
    var picker by remember { mutableStateOf(false) }
    val reacted = FbReaction.of(post.userReaction)
    Box {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .combinedClickable(
                        onClick = { actions.onReact(post.id, if (reacted != null) reacted.id else "like") },
                        onLongClick = { picker = true },
                    ),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (reacted == null) {
                    Icon(Icons.Outlined.ThumbUp, null, tint = c.muted, modifier = Modifier.size(20.dp))
                } else {
                    Text(reacted.emoji, fontSize = 18.sp)
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    reacted?.label ?: "Like",
                    color = reacted?.let { Color(it.colorHex) } ?: c.muted,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            }
            FbActionButton(Icons.Outlined.ChatBubbleOutline, "Comment", c) { actions.onOpenPost(post.id) }
            FbActionButton(Icons.Outlined.Share, "Share", c) { actions.onShare(post.id) }
        }
        if (picker) {
            DropdownMenu(expanded = true, onDismissRequest = { picker = false }) {
                Row(
                    Modifier.padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    FbReaction.entries.forEach { r ->
                        Column(
                            Modifier.clip(CircleShape).clickable { picker = false; actions.onReact(post.id, r.id) }.padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(r.emoji, fontSize = 30.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.FbActionButton(icon: ImageVector, label: String, c: FbColors, onClick: () -> Unit) {
    Row(
        Modifier.weight(1f).fillMaxHeight().clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = c.muted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun FbComment(comment: SocialPostUi, c: FbColors, actions: FbActions, onReply: () -> Unit, onLike: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Box(Modifier.clip(CircleShape).clickable { actions.onOpenProfile(comment.authorCharacterId) }) {
            SocialAvatar(comment.authorName, comment.colorHex, 32.dp)
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Box {
                Column(
                    Modifier.clip(RoundedCornerShape(18.dp)).background(c.bubble).padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(comment.authorName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.text)
                    Text(linkified(comment.text, c.blue, bold = true), fontSize = 15.sp, color = c.text)
                }
                if (comment.likeCount > 0) {
                    Row(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 8.dp, y = 10.dp)
                            .shadow(1.dp, RoundedCornerShape(10.dp))
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.card)
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(FbReaction.of(comment.userReaction)?.emoji ?: "👍", fontSize = 11.sp)
                        Text(" ${comment.likeCount}", fontSize = 11.sp, color = c.muted)
                    }
                }
            }
            Row(Modifier.padding(start = 12.dp, top = 3.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(compactAge(comment.createdAt), fontSize = 12.sp, color = c.muted)
                Text(
                    "Like",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (comment.userReaction.isNotBlank()) c.blue else c.muted,
                    modifier = Modifier.clickable(onClick = onLike),
                )
                Text("Reply", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.muted, modifier = Modifier.clickable(onClick = onReply))
            }
        }
    }
}

// ----------------------------------------------------------- post detail

@Composable
private fun FbPostDetail(
    postId: String,
    state: SocialUiState,
    c: FbColors,
    actions: FbActions,
    onBack: () -> Unit,
    onComment: (String, String, SocialPostUi?) -> Unit,
) {
    val post = state.allById[postId]
    var draft by rememberSaveable(postId) { mutableStateOf("") }
    var replyTo by remember(postId) { mutableStateOf<SocialPostUi?>(null) }
    Column(Modifier.fillMaxSize().background(c.card)) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = c.text) }
            Text(post?.let { "${it.authorName}'s post" } ?: "Post", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.text)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
        if (post == null) {
            Text("This content isn't available right now.", color = c.muted, modifier = Modifier.padding(24.dp))
            return@Column
        }
        val comments = state.repliesByParent[post.id].orEmpty()
        LazyColumn(Modifier.weight(1f)) {
            item(key = "post") { FbPostCard(post, state, c, actions, inDetail = true) }
            item(key = "sort") {
                Text("Most relevant ▾", fontWeight = FontWeight.SemiBold, color = c.text, fontSize = 14.sp, modifier = Modifier.padding(12.dp))
            }
            items(comments, key = { it.id }) { comment ->
                FbComment(comment, c, actions, onReply = { replyTo = comment }, onLike = { actions.onReact(comment.id, "like") })
                Spacer(Modifier.height(6.dp))
            }
            if (state.generating) {
                item(key = "typing") {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("•••", color = c.muted, fontSize = 18.sp, modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(c.bubble).padding(horizontal = 14.dp, vertical = 4.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Someone is writing a comment…", color = c.muted, fontSize = 13.sp)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
        replyTo?.let { target ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Replying to ${target.authorName}", color = c.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.Close, "Cancel", tint = c.muted, modifier = Modifier.size(16.dp).clickable { replyTo = null })
            }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SocialAvatar(state.personaName, avatarColorHexFor(state.personaName, null), 32.dp)
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                textStyle = TextStyle(color = c.text, fontSize = 15.sp),
                cursorBrush = SolidColor(c.blue),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.input)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                decorationBox = { inner ->
                    if (draft.isEmpty()) {
                        Text(
                            replyTo?.let { "Reply to ${it.authorName}…" } ?: "Write a comment…",
                            color = c.muted,
                            fontSize = 15.sp,
                        )
                    }
                    inner()
                },
            )
            IconButton(
                onClick = {
                    onComment(post.id, draft, replyTo)
                    draft = ""
                    replyTo = null
                },
                enabled = draft.isNotBlank(),
            ) { Icon(Icons.Filled.Send, "Post comment", tint = if (draft.isNotBlank()) c.blue else c.muted) }
        }
    }
}

// -------------------------------------------------------------- composer

@Composable
private fun FbComposer(
    state: SocialUiState,
    c: FbColors,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
    onCancel: () -> Unit,
    onPost: (String, String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var feeling by rememberSaveable { mutableStateOf("") }
    var feelingsOpen by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(c.card).clickable(enabled = false) {}) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel", tint = c.text) }
            Text("Create post", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.text, modifier = Modifier.weight(1f))
            val enabled = text.isNotBlank() || state.pendingImagePath != null
            Text(
                "POST",
                fontWeight = FontWeight.Bold,
                color = if (enabled) Color.White else c.muted,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (enabled) c.blue else c.button)
                    .clickable(enabled = enabled) { onPost(text, feeling) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            SocialAvatar(state.personaName, avatarColorHexFor(state.personaName, null), 44.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    androidx.compose.ui.text.buildAnnotatedString {
                        pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold))
                        append(state.personaName)
                        pop()
                        if (feeling.isNotBlank()) append(" is ${feelingEmoji(feeling)} feeling $feeling.")
                    },
                    color = c.text,
                    fontSize = 15.sp,
                )
                Row(
                    Modifier.padding(top = 4.dp).clip(RoundedCornerShape(6.dp)).background(c.button).padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Public, null, tint = c.text, modifier = Modifier.size(12.dp))
                    Text(" Public ▾", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                }
            }
        }
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = TextStyle(color = c.text, fontSize = if (text.length < 85 && state.pendingImagePath == null) 24.sp else 16.sp),
            cursorBrush = SolidColor(c.blue),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 120.dp),
            decorationBox = { inner ->
                if (text.isEmpty()) Text("What's on your mind?", color = c.muted, fontSize = 24.sp)
                inner()
            },
        )
        state.pendingImagePath?.let { path ->
            Box(Modifier.padding(12.dp)) {
                coil3.compose.AsyncImage(
                    model = java.io.File(path),
                    contentDescription = "Photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(8.dp)),
                )
                Icon(
                    Icons.Filled.Close,
                    "Remove photo",
                    tint = c.text,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp).clip(CircleShape).background(c.card).clickable(onClick = onClearImage).padding(4.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (feelingsOpen) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Feelings.forEach { (emoji, label) ->
                    Text(
                        "$emoji $label",
                        color = c.text,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (feeling == label) c.unread else c.button)
                            .clickable { feeling = if (feeling == label) "" else label; feelingsOpen = false }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
        ComposerOption(Icons.Outlined.Image, "Photo/video", c.green, c, onPickImage)
        ComposerOption(Icons.Outlined.EmojiEmotions, "Feeling/activity", Color(0xFFF7B928), c) { feelingsOpen = !feelingsOpen }
    }
}

@Composable
private fun ComposerOption(icon: ImageVector, label: String, tint: Color, c: FbColors, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = c.text, fontSize = 16.sp)
    }
}

@Composable
private fun FbShareSheet(post: SocialPostUi?, c: FbColors, onDismiss: () -> Unit, onShare: (String) -> Unit) {
    var caption by rememberSaveable { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onDismiss)) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(c.card)
                .clickable(enabled = false) {}
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.divider))
            Text("Share", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.text)
            BasicTextField(
                value = caption,
                onValueChange = { caption = it },
                textStyle = TextStyle(color = c.text, fontSize = 16.sp),
                cursorBrush = SolidColor(c.blue),
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                decorationBox = { inner ->
                    if (caption.isEmpty()) Text("Say something about this…", color = c.muted, fontSize = 16.sp)
                    inner()
                },
            )
            post?.let { Text("${it.authorName}: ${it.text}", color = c.muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            Text(
                "Share now",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(c.blue).clickable { onShare(caption) }.padding(10.dp),
            )
        }
    }
}

// --------------------------------------------------------------- stories

@Composable
private fun FbStoryViewer(stories: List<SocialPostUi>, start: Int, onClose: () -> Unit, onReply: (SocialPostUi) -> Unit) {
    var index by remember { mutableStateOf(start.coerceIn(0, (stories.size - 1).coerceAtLeast(0))) }
    val story = stories.getOrNull(index) ?: run { onClose(); return }
    val progress = remember(index) { Animatable(0f) }
    LaunchedEffect(index) {
        progress.animateTo(1f, tween(5_000, easing = LinearEasing))
        if (index < stories.lastIndex) index++ else onClose()
    }
    val tint = parseHexColor(story.colorHex, Color(0xFF1877F2))
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(tint, Color(0xFF111111))))) {
        if (story.imagePath != null) {
            coil3.compose.AsyncImage(
                model = java.io.File(story.imagePath),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            story.text,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).padding(32.dp),
        )
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
                SocialAvatar(story.authorName, story.colorHex, 36.dp, ring = Color(0xFF1877F2))
                Spacer(Modifier.width(8.dp))
                Text(story.authorName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("  ${compactAge(story.createdAt)}", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close", tint = Color.White) }
            }
        }
        Text(
            "Send message…",
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(50))
                .clickable { onReply(story) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

// --------------------------------------------------------------- friends

@Composable
private fun FbFriends(state: SocialUiState, c: FbColors, actions: FbActions, onFriend: (String) -> Unit) {
    val friends = state.people.filter { it.isFollowing }
    val others = state.people.filterNot { it.isFollowing }
    // Some of the cast send requests; the rest are suggestions.
    val requests = others.filter { it.name.hashCode() % 2 == 0 }
    val suggestions = others - requests.toSet()
    LazyColumn(Modifier.fillMaxSize().background(c.card), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "title") {
            Text("Friends", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = c.text, modifier = Modifier.padding(16.dp))
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Suggestions", c)
                Chip("Your friends", c)
            }
        }
        if (requests.isNotEmpty()) {
            item(key = "req-title") {
                Row(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Friend requests ", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.text)
                    Text("${requests.size}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE41E3F))
                }
            }
            items(requests, key = { "r-" + it.characterId }) { person ->
                FriendRow(person, c, "${(person.followers % 40) + 1} mutual friends", "Confirm", "Delete", actions, onPrimary = { onFriend(person.characterId) }, onSecondary = null)
            }
        }
        item(key = "sugg-title") {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(1.dp).background(c.divider))
            Text("People you may know", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.text, modifier = Modifier.padding(16.dp))
        }
        items(suggestions, key = { "s-" + it.characterId }) { person ->
            FriendRow(person, c, person.bio.take(40), "Add friend", "Remove", actions, onPrimary = { onFriend(person.characterId) }, onSecondary = null)
        }
        item(key = "friends-title") {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(1.dp).background(c.divider))
            Text("Your friends (${friends.size})", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.text, modifier = Modifier.padding(16.dp))
        }
        items(friends, key = { "f-" + it.characterId }) { person ->
            Row(
                Modifier.fillMaxWidth().clickable { actions.onOpenProfile(person.characterId) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SocialAvatar(person.name, person.colorHex, 56.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(person.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = c.text)
                    Text("${(person.followers % 40) + 1} mutual friends", fontSize = 13.sp, color = c.muted)
                }
                Icon(Icons.Filled.MoreHoriz, null, tint = c.muted, modifier = Modifier.clickable { onFriend(person.characterId) })
            }
        }
    }
}

@Composable
private fun Chip(label: String, c: FbColors) {
    Text(label, color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(50)).background(c.button).padding(horizontal = 12.dp, vertical = 7.dp))
}

@Composable
private fun FriendRow(
    person: SocialPersonUi,
    c: FbColors,
    subtitle: String,
    primary: String,
    secondary: String,
    actions: FbActions,
    onPrimary: () -> Unit,
    onSecondary: (() -> Unit)?,
) {
    var hidden by remember { mutableStateOf(false) }
    if (hidden) return
    Row(
        Modifier.fillMaxWidth().clickable { actions.onOpenProfile(person.characterId) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SocialAvatar(person.name, person.colorHex, 72.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(person.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, fontSize = 13.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    primary,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(c.blue).clickable(onClick = onPrimary).padding(vertical = 7.dp),
                )
                Text(
                    secondary,
                    color = c.text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(c.button)
                        .clickable { onSecondary?.invoke() ?: run { hidden = true } }.padding(vertical = 7.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------- notifications

@Composable
private fun FbNotifications(state: SocialUiState, c: FbColors, onOpen: (String?) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().background(c.card)) {
        item(key = "title") {
            Text("Notifications", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = c.text, modifier = Modifier.padding(16.dp))
        }
        if (state.notifications.isEmpty()) {
            item(key = "empty") { Text("You have no notifications.", color = c.muted, modifier = Modifier.padding(16.dp)) }
        }
        items(state.notifications, key = { it.id }) { n ->
            val fresh = System.currentTimeMillis() - n.createdAt < 86_400_000L
            Row(
                Modifier.fillMaxWidth().background(if (fresh) c.unread else Color.Transparent).clickable { onOpen(n.postId) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    SocialAvatar(n.actorName, n.actorColorHex, 56.dp)
                    val (badge, color) = when (n.kind) {
                        "like" -> "👍" to c.blue
                        "reply", "mention" -> "💬" to c.green
                        "repost" -> "↗" to c.blue
                        else -> "👤" to c.blue
                    }
                    Box(
                        Modifier.align(Alignment.BottomEnd).size(24.dp).clip(CircleShape).background(color),
                        contentAlignment = Alignment.Center,
                    ) { Text(badge, fontSize = 12.sp, color = Color.White) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        androidx.compose.ui.text.buildAnnotatedString {
                            val name = n.actorName
                            if (n.text.startsWith(name)) {
                                pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold))
                                append(name)
                                pop()
                                append(n.text.removePrefix(name))
                            } else {
                                append(n.text)
                            }
                        },
                        color = c.text,
                        fontSize = 15.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(facebookAge(n.createdAt), color = if (fresh) c.blue else c.muted, fontSize = 13.sp)
                }
                Icon(Icons.Filled.MoreHoriz, null, tint = c.muted)
            }
        }
    }
}

// ---------------------------------------------------------------- search

@Composable
private fun FbSearchResults(state: SocialUiState, c: FbColors, actions: FbActions) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(c.card)) {
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = c.text, fontSize = 16.sp),
            cursorBrush = SolidColor(c.blue),
            modifier = Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(50)).background(c.input).padding(horizontal = 16.dp, vertical = 10.dp),
            decorationBox = { inner ->
                if (query.isEmpty()) Text("Search people and posts", color = c.muted, fontSize = 16.sp)
                inner()
            },
        )
        val people = if (query.isBlank()) state.people.take(8) else state.people.filter { it.name.contains(query, true) }
        val posts = if (query.isBlank()) emptyList() else state.posts.filter { it.text.contains(query, true) }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item(key = "people-title") { Text(if (query.isBlank()) "Recent" else "People", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = c.text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            items(people, key = { "p-" + it.characterId }) { person ->
                Row(
                    Modifier.fillMaxWidth().clickable { actions.onOpenProfile(person.characterId) }.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SocialAvatar(person.name, person.colorHex, 44.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(person.name, color = c.text, fontWeight = FontWeight.SemiBold)
                        Text(if (person.isFollowing) "Friend" else "${(person.followers % 40) + 1} mutual friends", color = c.muted, fontSize = 13.sp)
                    }
                }
            }
            if (posts.isNotEmpty()) {
                item(key = "posts-title") { Text("Posts", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = c.text, modifier = Modifier.padding(16.dp)) }
                items(posts, key = { "sp-" + it.id }) { post -> FbPostCard(post, state, c, actions) }
            }
        }
    }
}

// --------------------------------------------------------------- profile

@Composable
private fun FbProfile(
    who: String,
    state: SocialUiState,
    c: FbColors,
    actions: FbActions,
    onBack: (() -> Unit)?,
    onFriend: (String) -> Unit,
    onMessage: () -> Unit,
    onCompose: () -> Unit,
) {
    val isYou = who == "you"
    val person = state.people.firstOrNull { it.characterId == who }
    val name = if (isYou) state.personaName else person?.name ?: "Facebook user"
    val color = if (isYou) avatarColorHexFor(name, null) else person?.colorHex ?: "#1877F2"
    val tint = parseHexColor(color, c.blue)
    val posts = state.posts.filter { if (isYou) it.isYou else it.authorCharacterId == who }
    val friends = state.people.filter { it.isFollowing }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "header") {
            Column(Modifier.fillMaxWidth().background(c.card)) {
                Box(Modifier.fillMaxWidth().height(260.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(Brush.linearGradient(listOf(tint, tint.copy(alpha = 0.4f), Color(0xFF1C1E21)))),
                    )
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = Modifier.padding(8.dp).size(36.dp).clip(CircleShape).background(Color(0x66000000))) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                        }
                    }
                    Box(Modifier.align(Alignment.BottomStart).padding(start = 16.dp).clip(CircleShape).background(c.card).padding(4.dp)) {
                        SocialAvatar(name, color, 150.dp)
                    }
                }
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(name, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = c.text)
                    Text(
                        if (isYou) "${friends.size} friends" else "${compactCount(person?.followers ?: 0)} followers · ${(person?.followers ?: 0) % 40 + 1} mutual friends",
                        color = c.muted,
                        fontSize = 15.sp,
                    )
                    val bio = if (isYou) state.personaBio else person?.bio.orEmpty()
                    if (bio.isNotBlank()) Text(bio, color = c.text, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
                }
                Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isYou) {
                        ProfileButton("+ Add to story", c.blue, Color.White, Modifier.weight(1f), onCompose)
                        ProfileButton("✎ Edit profile", c.button, c.text, Modifier.weight(1f)) {}
                    } else if (person != null) {
                        ProfileButton(
                            if (person.isFollowing) "✓ Friends" else "+ Add friend",
                            if (person.isFollowing) c.button else c.blue,
                            if (person.isFollowing) c.text else Color.White,
                            Modifier.weight(1f),
                        ) { onFriend(person.characterId) }
                        ProfileButton("Message", if (person.isFollowing) c.blue else c.button, if (person.isFollowing) Color.White else c.text, Modifier.weight(1f), onMessage)
                    }
                }
            }
        }
        if (isYou && friends.isNotEmpty()) {
            item(key = "friends") {
                Column(Modifier.fillMaxWidth().background(c.card).padding(16.dp)) {
                    Text("Friends", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.text)
                    Text("${friends.size} friends", color = c.muted)
                    Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        friends.take(9).forEach { f ->
                            Column(Modifier.width(96.dp).clickable { actions.onOpenProfile(f.characterId) }) {
                                Box(
                                    Modifier.size(96.dp).clip(RoundedCornerShape(8.dp)).background(parseHexColor(f.colorHex, c.blue)),
                                    contentAlignment = Alignment.Center,
                                ) { SocialAvatar(f.name, f.colorHex, 64.dp) }
                                Text(f.name, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                            }
                        }
                    }
                }
            }
        }
        item(key = "posts-title") {
            Column(Modifier.fillMaxWidth().background(c.card)) {
                Text("Posts", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.text, modifier = Modifier.padding(16.dp))
                if (isYou) {
                    Row(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        SocialAvatar(name, color, 40.dp)
                        Text(
                            "What's on your mind?",
                            color = c.text,
                            modifier = Modifier.weight(1f).padding(start = 8.dp).clip(RoundedCornerShape(50)).border(1.dp, c.divider, RoundedCornerShape(50)).clickable(onClick = onCompose).padding(horizontal = 14.dp, vertical = 9.dp),
                        )
                    }
                }
            }
        }
        if (posts.isEmpty()) {
            item(key = "none") {
                Text("No posts available", color = c.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().background(c.card).padding(24.dp))
            }
        }
        items(posts, key = { it.id }) { post -> FbPostCard(post, state, c, actions) }
    }
}

@Composable
private fun ProfileButton(label: String, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    Text(
        label,
        color = fg,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        textAlign = TextAlign.Center,
        modifier = modifier.clip(RoundedCornerShape(6.dp)).background(bg).clickable(onClick = onClick).padding(vertical = 9.dp),
    )
}
