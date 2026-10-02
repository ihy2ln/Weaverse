package com.ihy2ln.weaverse.feature.chatting

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MarkChatUnread
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ihy2ln.weaverse.core.ui.util.parseHexColor
import com.ihy2ln.weaverse.feature.roleplay.friends.CharacterAvatar
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Flattened rows of the message list, so a message id maps straight to a list index. */
private sealed interface ChatRow {
    val key: String

    data class Welcome(override val key: String = "welcome") : ChatRow
    data class Day(val label: String, override val key: String) : ChatRow
    data class NewDivider(override val key: String = "new-divider") : ChatRow
    data class System(val message: DiscordMessageUi, override val key: String) : ChatRow
    data class Message(val message: DiscordMessageUi, val grouped: Boolean, override val key: String) : ChatRow
}

private fun buildRows(state: DiscordChatUiState, viewModel: DiscordChatViewModel): List<ChatRow> {
    val rows = mutableListOf<ChatRow>(ChatRow.Welcome())
    var lastDay = ""
    var newPlaced = false
    state.messages.forEachIndexed { index, message ->
        val day = viewModel.dayLabel(message.createdAt)
        if (day != lastDay) {
            lastDay = day
            rows += ChatRow.Day(day, "day-$day-$index")
        }
        val isNew = !newPlaced && state.unreadSince > 0L && message.createdAt > state.unreadSince && !message.isUser
        if (isNew) {
            newPlaced = true
            rows += ChatRow.NewDivider()
        }
        val previous = state.messages.getOrNull(index - 1)
        val grouped = !isNew && previous != null && !previous.isSystem &&
            previous.authorName == message.authorName &&
            message.replyTo == null &&
            message.createdAt - previous.createdAt < GROUP_WINDOW_MS &&
            viewModel.dayLabel(previous.createdAt) == day
        rows += if (message.isSystem) {
            ChatRow.System(message, message.id)
        } else {
            ChatRow.Message(message, grouped, message.id)
        }
    }
    return rows
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscordMessageList(
    state: DiscordChatUiState,
    viewModel: DiscordChatViewModel,
    onOpenProfile: (characterId: String?, name: String, colorHex: String, isYou: Boolean) -> Unit,
    jumpToMessageId: String?,
    onJumpHandled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = discordColors()
    val roomId = state.selectedRoomId
    val rows = remember(state.messages, state.unreadSince) { buildRows(state, viewModel) }
    val listState = rememberSaveable(roomId, saver = LazyListState.Saver) { LazyListState() }
    val scope = rememberCoroutineScope()
    var highlightId by remember { mutableStateOf<String?>(null) }
    var actionTarget by remember { mutableStateOf<DiscordMessageUi?>(null) }
    var editTarget by remember { mutableStateOf<DiscordMessageUi?>(null) }
    var deleteTarget by remember { mutableStateOf<DiscordMessageUi?>(null) }
    var reactionPickerFor by remember { mutableStateOf<String?>(null) }
    var revealedSpoilers by remember(roomId) { mutableStateOf(setOf<String>()) }
    var viewing by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    val lastIndex = rows.size + (if (state.isStreaming) 1 else 0)

    fun jumpTo(messageId: String) {
        val index = rows.indexOfFirst { it.key == messageId }
        if (index >= 0) {
            scope.launch {
                listState.animateScrollToItem(index)
                highlightId = messageId
                kotlinx.coroutines.delay(1_600)
                if (highlightId == messageId) highlightId = null
            }
        }
    }

    LaunchedEffect(jumpToMessageId) {
        jumpToMessageId?.let { jumpTo(it) }
        if (jumpToMessageId != null) onJumpHandled()
    }
    LaunchedEffect(roomId) {
        if (roomId == null) return@LaunchedEffect
        val saved = viewModel.scrollFor(roomId)
        if (saved != null) {
            listState.scrollToItem(saved.first, saved.second)
        } else {
            // Opening a room with unread messages lands on the NEW line, like Discord.
            val newIndex = rows.indexOfFirst { it is ChatRow.NewDivider }
            listState.scrollToItem(if (newIndex > 0) newIndex else lastIndex)
        }
    }
    var lastSeenId by remember(roomId) { mutableStateOf(state.messages.lastOrNull()?.id) }
    LaunchedEffect(roomId, state.messages, state.isStreaming) {
        if (roomId == null) return@LaunchedEffect
        val totalItems = listState.layoutInfo.totalItemsCount
        val atBottom = (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= totalItems - 3
        // Something the writer just sent always comes into view, wherever they were scrolled.
        val newest = state.messages.lastOrNull()
        val justSent = newest != null && newest.isUser && newest.id != lastSeenId
        lastSeenId = newest?.id
        if (viewModel.scrollFor(roomId) == null || atBottom || justSent) {
            listState.scrollToItem((totalItems - 1).coerceAtLeast(0))
        }
    }
    LaunchedEffect(roomId) {
        if (roomId == null) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> viewModel.rememberScroll(roomId, index, offset) }
    }
    val scrolledUp by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last < info.totalItemsCount - 4
        }
    }

    Box(modifier) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
            items(rows, key = { it.key }) { row ->
                when (row) {
                    is ChatRow.Welcome -> ChannelWelcome(state.selectedRoom)
                    is ChatRow.Day -> DayDivider(row.label)
                    is ChatRow.NewDivider -> NewMessagesDivider()
                    is ChatRow.System -> SystemMessageRow(row.message, viewModel.timestampFull(row.message.createdAt))
                    is ChatRow.Message -> MessageRow(
                        message = row.message,
                        grouped = row.grouped,
                        highlighted = highlightId == row.message.id,
                        timeFull = viewModel.timestampFull(row.message.createdAt),
                        timeShort = viewModel.timestampShort(row.message.createdAt),
                        spoilersRevealed = row.message.id in revealedSpoilers,
                        onRevealSpoilers = { revealedSpoilers = revealedSpoilers + row.message.id },
                        onLongPress = { actionTarget = row.message },
                        onToggleReaction = { emoji -> viewModel.toggleReaction(row.message.id, emoji) },
                        onAddReaction = { reactionPickerFor = row.message.id },
                        onJumpToReply = { id -> jumpTo(id) },
                        onOpenMedia = { i -> viewing = row.message.mediaPaths to i },
                        onOpenProfile = {
                            onOpenProfile(
                                row.message.authorCharacterId,
                                row.message.authorName,
                                row.message.authorColorHex,
                                row.message.isUser,
                            )
                        },
                    )
                }
            }
            if (state.isStreaming) {
                item(key = "streaming") {
                    StreamingRow(
                        authorName = state.members.firstOrNull()?.name ?: state.selectedRoom?.name ?: "…",
                        text = state.streamingText,
                    )
                }
            }
        }
        if (scrolledUp) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.blurple)
                    .clickable { scope.launch { listState.animateScrollToItem(lastIndex) } }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "You're viewing older messages",
                    fontSize = 13.sp,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                Text("Jump To Present", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }

    actionTarget?.let { target ->
        MessageActionSheet(
            message = target,
            canRegenerate = !target.isUser && state.messages.lastOrNull()?.id == target.id,
            onDismiss = { actionTarget = null },
            onReact = { emoji -> viewModel.toggleReaction(target.id, emoji); actionTarget = null },
            onMoreReactions = { reactionPickerFor = target.id; actionTarget = null },
            onReply = { viewModel.startReply(target); actionTarget = null },
            onEdit = { editTarget = target; actionTarget = null },
            onPin = { viewModel.togglePin(target.id); actionTarget = null },
            onMarkUnread = { viewModel.markUnreadFrom(target); actionTarget = null },
            onRegenerate = { viewModel.retry(); actionTarget = null },
            onDelete = { deleteTarget = target; actionTarget = null },
            onThread = if (state.selectedRoom?.kind == ROOM_KIND_CHANNEL && !target.isSystem) {
                { viewModel.beginThread(target); actionTarget = null }
            } else null,
            onSaveToCodex = if (!target.isSystem && target.text.isNotBlank()) {
                { viewModel.beginCodexSave(target); actionTarget = null }
            } else null,
        )
    }
    reactionPickerFor?.let { messageId ->
        ModalBottomSheet(onDismissRequest = { reactionPickerFor = null }, containerColor = colors.sidebar) {
            Text(
                "Add Reaction",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colors.header,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            EmojiGrid { emoji ->
                viewModel.toggleReaction(messageId, emoji)
                reactionPickerFor = null
            }
        }
    }
    viewing?.let { (paths, index) ->
        com.ihy2ln.weaverse.feature.chatting.media.MediaViewer(paths = paths, start = index, onClose = { viewing = null })
    }
    editTarget?.let { target ->
        var text by remember(target.id) { mutableStateOf(target.text) }
        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = { Text("Edit Message") },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.editMessage(target.id, text); editTarget = null },
                    enabled = text.isNotBlank(),
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editTarget = null }) { Text("Cancel") } },
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Message") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Are you sure you want to delete this message?")
                    Text(
                        "${target.authorName}: ${target.text.take(160)}",
                        fontSize = 13.sp,
                        color = colors.muted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.elevated)
                            .padding(8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteMessage(target.id); deleteTarget = null }) {
                    Text("Delete", color = colors.red)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ChannelWelcome(room: DiscordRoomUi?) {
    val colors = discordColors()
    room ?: return
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)) {
        if (room.kind == ROOM_KIND_CHANNEL || room.kind == ROOM_KIND_VOICE) {
            Box(
                Modifier.size(68.dp).clip(CircleShape).background(colors.elevated),
                contentAlignment = Alignment.Center,
            ) {
                Icon(roomKindIcon(room.kind) ?: Icons.Filled.Tag, contentDescription = null, tint = colors.header, modifier = Modifier.size(42.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text("Welcome to #${room.name}!", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = colors.header)
            Text("This is the start of the #${room.name} channel.", fontSize = 15.sp, color = colors.muted)
        } else if (room.kind == ROOM_KIND_THREAD) {
            Box(
                Modifier.size(68.dp).clip(CircleShape).background(colors.elevated),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Forum, contentDescription = null, tint = colors.header, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(room.name, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = colors.header)
            Text("This is the start of the thread.", fontSize = 15.sp, color = colors.muted)
        } else {
            CharacterAvatar(name = room.name, colorHex = room.avatarColorHex, size = 80.dp)
            Spacer(Modifier.height(8.dp))
            Text(room.name, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = colors.header)
            Text(
                room.name.lowercase().replace(' ', '_'),
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = colors.text,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (room.kind == ROOM_KIND_DM) {
                    "This is the beginning of your direct message history with ${room.name}."
                } else {
                    "This is ${room.name}'s room. Anything said here stays between you and them."
                },
                fontSize = 15.sp,
                color = colors.muted,
            )
        }
        if (room.topic.isNotBlank()) {
            Text(room.topic, fontSize = 13.sp, color = colors.muted, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun DayDivider(label: String) {
    val colors = discordColors()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(colors.divider))
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.muted,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Box(Modifier.weight(1f).height(1.dp).background(colors.divider))
    }
}

@Composable
private fun NewMessagesDivider() {
    val colors = discordColors()
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(colors.red))
        Text(
            "NEW",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                .background(colors.red)
                .padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

/** A join/system line: Discord's green arrow and a muted sentence. */
@Composable
private fun SystemMessageRow(message: DiscordMessageUi, time: String) {
    val colors = discordColors()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("→", fontSize = 18.sp, color = colors.green, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
        Spacer(Modifier.width(16.dp))
        Text(
            message.text,
            fontSize = 14.sp,
            color = colors.muted,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text("  $time", fontSize = 11.sp, color = colors.muted)
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun MessageRow(
    message: DiscordMessageUi,
    grouped: Boolean,
    highlighted: Boolean,
    timeFull: String,
    timeShort: String,
    spoilersRevealed: Boolean,
    onRevealSpoilers: () -> Unit,
    onLongPress: () -> Unit,
    onToggleReaction: (String) -> Unit,
    onAddReaction: () -> Unit,
    onJumpToReply: (String) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenMedia: (Int) -> Unit,
) {
    val colors = discordColors()
    val background = when {
        highlighted -> colors.blurple.copy(alpha = 0.18f)
        message.mentionsYou -> colors.mentionBg
        else -> Color.Transparent
    }
    val accent = when {
        message.mentionsYou -> colors.gold
        message.pinned -> colors.blurple
        else -> null
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = if (grouped) 0.dp else 12.dp)
            .background(background)
            .drawBehind {
                if (accent != null) drawRect(accent, size = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height))
            }
            .combinedClickable(onClick = { if (!spoilersRevealed) onRevealSpoilers() }, onLongClick = onLongPress),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 2.dp)) {
            message.replyTo?.let { reply -> ReplyPreview(reply, onClick = { onJumpToReply(reply.messageId) }) }
            Row(verticalAlignment = Alignment.Top) {
                if (grouped) {
                    Box(Modifier.width(40.dp), contentAlignment = Alignment.TopCenter) {
                        Text(
                            timeShort,
                            fontSize = 10.sp,
                            color = colors.muted.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 4.dp),
                            maxLines = 1,
                        )
                    }
                } else {
                    Box(Modifier.clip(CircleShape).clickable(onClick = onOpenProfile)) {
                        CharacterAvatar(name = message.authorName, colorHex = message.authorColorHex, size = 40.dp)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    if (!grouped) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                message.authorName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (message.isUser) colors.header else parseHexColor(message.authorColorHex, colors.header),
                                modifier = Modifier.clickable(onClick = onOpenProfile),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (message.isBot) {
                                Text(
                                    "APP",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(colors.blurple)
                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                            Text(timeFull, fontSize = 11.sp, color = colors.muted, maxLines = 1)
                            if (message.pinned) {
                                Icon(Icons.Filled.PushPin, "Pinned", tint = colors.muted, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                    if (message.text.isNotBlank()) {
                        MessageBody(message.text, message.edited, spoilersRevealed)
                    }
                    if (message.mediaPaths.isNotEmpty()) {
                        com.ihy2ln.weaverse.feature.chatting.media.MediaGrid(
                            paths = message.mediaPaths,
                            onOpen = { i -> onOpenMedia(i) },
                            modifier = Modifier.padding(top = 6.dp).widthIn(max = 320.dp),
                            height = if (message.mediaPaths.size == 1) 200.dp else 220.dp,
                            corner = 8.dp,
                        )
                    }
                    if (message.hasMedia && message.mediaPaths.isEmpty()) {
                        Text("media attachment", fontSize = 12.sp, color = colors.muted)
                    }
                    if (message.reactions.isNotEmpty()) {
                        FlowRow(
                            Modifier.padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            message.reactions.forEach { reaction ->
                                ReactionChip(reaction, onClick = { onToggleReaction(reaction.emoji) })
                            }
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.elevated)
                                    .clickable(onClick = onAddReaction)
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Icon(Icons.Filled.AddReaction, "Add reaction", tint = colors.muted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplyPreview(reply: DiscordReplyPreviewUi, onClick: () -> Unit) {
    val colors = discordColors()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, bottom = 2.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Discord's curved "spine" from the reply to the quoted message.
        Box(
            Modifier
                .padding(top = 8.dp)
                .width(34.dp)
                .height(10.dp)
                .border(
                    width = 2.dp,
                    color = colors.divider,
                    shape = RoundedCornerShape(topStart = 6.dp),
                ),
        )
        Spacer(Modifier.width(4.dp))
        CharacterAvatar(name = reply.authorName, colorHex = reply.authorColorHex, size = 16.dp)
        Spacer(Modifier.width(4.dp))
        Text(
            "@" + reply.authorName,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = parseHexColor(reply.authorColorHex, colors.header),
            maxLines = 1,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            reply.snippet,
            fontSize = 13.sp,
            color = colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MessageBody(text: String, edited: Boolean, spoilersRevealed: Boolean) {
    val colors = discordColors()
    if (isJumboEmoji(text)) {
        Text(text.trim(), fontSize = 44.sp, lineHeight = 52.sp)
        return
    }
    // No SelectionContainer: it swallows the long-press that opens the message actions,
    // and Copy Text in that sheet covers copying.
    run {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val blocks = parseDiscordBlocks(text)
            blocks.forEachIndexed { index, block ->
                val last = index == blocks.lastIndex
                when (block) {
                    is DiscordBlock.Prose -> Text(
                        androidx.compose.ui.text.buildAnnotatedString {
                            append(discordInline(block.text, colors, spoilersRevealed))
                            if (last && edited) {
                                pushStyle(androidx.compose.ui.text.SpanStyle(color = colors.muted, fontSize = 10.sp))
                                append(" (edited)")
                                pop()
                            }
                        },
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        color = colors.text,
                    )
                    is DiscordBlock.Quote -> Row(Modifier.heightIn(min = 20.dp)) {
                        Box(
                            Modifier
                                .width(4.dp)
                                .height(20.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(colors.divider),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(discordInline(block.text, colors, spoilersRevealed), fontSize = 15.sp, color = colors.text)
                    }
                    is DiscordBlock.Code -> Text(
                        block.code,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = colors.text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.codeBg)
                            .border(1.dp, colors.rail, RoundedCornerShape(4.dp))
                            .horizontalScroll(rememberScrollState())
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReactionChip(reaction: DiscordReactionUi, onClick: () -> Unit) {
    val colors = discordColors()
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (reaction.mine) colors.blurple.copy(alpha = 0.18f) else colors.elevated)
            .border(1.dp, if (reaction.mine) colors.blurple else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(reaction.emoji, fontSize = 15.sp)
        Text(
            reaction.count.toString(),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (reaction.mine) colors.mentionPillText else colors.muted,
        )
    }
}

@Composable
private fun StreamingRow(authorName: String, text: String) {
    val colors = discordColors()
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CharacterAvatar(
            name = authorName,
            colorHex = com.ihy2ln.weaverse.core.roleplay.avatarColorHexFor(authorName, null),
            size = 40.dp,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(authorName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.header)
            if (text.isNotBlank()) {
                Text(discordInline(text, colors, false), fontSize = 15.sp, color = colors.text.copy(alpha = 0.75f))
            } else {
                TypingDots()
            }
        }
    }
}

/** Discord's "Name is typing…" line under the list, with the bouncing dots. */
@Composable
fun TypingIndicator(state: DiscordChatUiState) {
    val colors = discordColors()
    Row(
        Modifier.fillMaxWidth().height(22.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.isStreaming) {
            TypingDots()
            Spacer(Modifier.width(6.dp))
            val who = state.members.firstOrNull()?.name ?: state.selectedRoom?.name ?: "Someone"
            Text(
                AnnotatedString.Builder().apply {
                    pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = colors.header))
                    append(who)
                    pop()
                    append(" is typing…")
                }.toAnnotatedString(),
                fontSize = 12.sp,
                color = colors.text,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TypingDots() {
    val colors = discordColors()
    val transition = rememberInfiniteTransition(label = "typing")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500, delayMillis = i * 160),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$i",
            )
            Box(
                Modifier
                    .size(6.dp)
                    .offset(y = ((1f - alpha) * 2).dp)
                    .clip(CircleShape)
                    .background(colors.header.copy(alpha = alpha)),
            )
        }
    }
}

// ------------------------------------------------------------ action sheet

private val QuickReactions = listOf("👍", "❤️", "😂", "😮", "😢", "🔥")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageActionSheet(
    message: DiscordMessageUi,
    canRegenerate: Boolean,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onMoreReactions: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onPin: () -> Unit,
    onMarkUnread: () -> Unit,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit,
    onThread: (() -> Unit)? = null,
    onSaveToCodex: (() -> Unit)? = null,
) {
    val colors = discordColors()
    val clipboard = LocalClipboardManager.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.sidebar) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            QuickReactions.forEach { emoji ->
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(colors.elevated)
                        .clickable { onReact(emoji) },
                    contentAlignment = Alignment.Center,
                ) { Text(emoji, fontSize = 22.sp) }
            }
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(colors.elevated).clickable(onClick = onMoreReactions),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.AddReaction, "More reactions", tint = colors.muted) }
        }
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.elevated),
        ) {
            if (message.isUser) SheetAction(Icons.Filled.Edit, "Edit Message", onClick = onEdit)
            SheetAction(Icons.AutoMirrored.Filled.Reply, "Reply", onClick = onReply)
            if (onThread != null) SheetAction(Icons.Filled.Forum, "Create Thread", onClick = onThread)
            if (onSaveToCodex != null) SheetAction(Icons.AutoMirrored.Filled.MenuBook, "Save to Codex", onClick = onSaveToCodex)
            SheetAction(Icons.Filled.PushPin, if (message.pinned) "Unpin Message" else "Pin Message", onClick = onPin)
            SheetAction(Icons.Filled.ContentCopy, "Copy Text") {
                clipboard.setText(AnnotatedString(message.text))
                onDismiss()
            }
            SheetAction(Icons.Filled.MarkChatUnread, "Mark Unread", onClick = onMarkUnread)
            if (canRegenerate) SheetAction(Icons.Filled.Refresh, "Regenerate Reply", onClick = onRegenerate)
            SheetAction(Icons.Filled.Delete, "Delete Message", tint = colors.red, onClick = onDelete)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, tint: Color = discordColors().text, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = tint)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmojiGrid(onPick: (String) -> Unit) {
    FlowRow(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ChatEmoji.forEach { emoji ->
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).clickable { onPick(emoji) },
                contentAlignment = Alignment.Center,
            ) { Text(emoji, fontSize = 26.sp) }
        }
    }
}

// ---------------------------------------------------------------- side panels

@Composable
private fun PanelHeader(title: String, icon: ImageVector?, onClose: (() -> Unit)?) {
    val colors = discordColors()
    Row(
        Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = colors.muted, modifier = Modifier.size(20.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.header, modifier = Modifier.weight(1f))
        if (onClose != null) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close", tint = colors.muted) }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.rail.copy(alpha = 0.5f)))
}

/** Discord's right-hand member list, grouped by presence. */
@Composable
fun MemberListPanel(
    state: DiscordChatUiState,
    status: DiscordStatus,
    onOpenProfile: (DiscordMemberUi) -> Unit,
    onOpenYou: () -> Unit,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = discordColors()
    // Hoisted roles get their own group, highest role first; everyone else groups by status.
    val roleGroups = state.roles.filter { it.hoist }.map { role ->
        role to state.members.filter { m -> state.roles.firstOrNull { it.hoist && it.id in state.memberRoles[m.characterId].orEmpty() }?.id == role.id }
    }.filter { it.second.isNotEmpty() }
    val grouped = roleGroups.flatMap { it.second }.map { it.characterId }.toSet()
    val byStatus = state.members.filterNot { it.characterId in grouped }.groupBy { presenceFor(it.name) }
    Column(modifier.background(colors.sidebar)) {
        PanelHeader("Members", icon = null, onClose = onClose)
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp)) {
            item(key = "owner-header") { MemberGroupHeader("Server Owner — 1") }
            item(key = "you") {
                MemberRow(
                    name = state.personaName,
                    colorHex = com.ihy2ln.weaverse.core.roleplay.avatarColorHexFor(state.personaName, null),
                    status = status,
                    activity = "Writing this world",
                    crown = true,
                    onClick = onOpenYou,
                )
            }
            roleGroups.forEach { (role, members) ->
                item(key = "role-${role.id}") { MemberGroupHeader("${role.name} — ${members.size}") }
                items(members, key = { "rm-" + role.id + it.characterId }) { member ->
                    MemberRow(
                        name = member.name,
                        colorHex = role.colorHex,
                        status = presenceFor(member.name),
                        activity = state.selectedServer?.title.orEmpty(),
                        crown = false,
                        onClick = { onOpenProfile(member) },
                    )
                }
            }
            listOf(DiscordStatus.Online, DiscordStatus.Idle, DiscordStatus.DoNotDisturb).forEach { group ->
                val members = byStatus[group].orEmpty()
                if (members.isNotEmpty()) {
                    item(key = "hdr-${group.name}") { MemberGroupHeader("${group.label} — ${members.size}") }
                    items(members, key = { "m-" + it.characterId }) { member ->
                        MemberRow(
                            name = member.name,
                            colorHex = state.roleColors[member.characterId] ?: member.colorHex,
                            status = group,
                            activity = if (member.joinedViaMention) "Invited by mention" else state.selectedServer?.title.orEmpty(),
                            crown = false,
                            onClick = { onOpenProfile(member) },
                        )
                    }
                }
            }
            if (state.members.isEmpty()) {
                item(key = "none") {
                    Text(
                        "Nobody's seated here yet — @mention someone to pull them in.",
                        fontSize = 13.sp,
                        color = colors.muted,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MemberGroupHeader(label: String) {
    Text(
        label.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = discordColors().muted,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun MemberRow(
    name: String,
    colorHex: String,
    status: DiscordStatus,
    activity: String,
    crown: Boolean,
    onClick: () -> Unit,
) {
    val colors = discordColors()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusAvatar(name, colorHex, 32.dp, status)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = parseHexColor(colorHex, colors.header),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (crown) Text("👑", fontSize = 12.sp)
            }
            if (activity.isNotBlank()) {
                Text(activity, fontSize = 12.sp, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun PinnedMessagesPanel(
    state: DiscordChatUiState,
    onJump: (String) -> Unit,
    onUnpin: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = discordColors()
    Column(modifier.background(colors.sidebar)) {
        PanelHeader("Pinned Messages", Icons.Filled.PushPin, onClose)
        val pinned = state.pinnedMessages.reversed()
        if (pinned.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.PushPin, null, tint = colors.muted, modifier = Modifier.size(48.dp))
                Text(
                    "This channel doesn't have any pinned messages... yet.",
                    fontSize = 14.sp,
                    color = colors.muted,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Long-press a message and choose Pin Message to keep it here.",
                    fontSize = 12.sp,
                    color = colors.muted,
                    textAlign = TextAlign.Center,
                )
            }
        }
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(pinned, key = { it.id }) { message ->
                MessageCard(
                    message = message,
                    actionLabel = "Jump",
                    onAction = { onJump(message.id) },
                    onRemove = { onUnpin(message.id) },
                )
            }
        }
    }
}

@Composable
fun SearchMessagesPanel(
    state: DiscordChatUiState,
    viewModel: DiscordChatViewModel,
    onJump: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = discordColors()
    var query by rememberSaveable { mutableStateOf("") }
    // Discord-style filters: "from: name" narrows by author, "has: image" by attachments.
    val terms = query.trim()
    val from = Regex("from:\\s*(\\S+)", RegexOption.IGNORE_CASE).find(terms)?.groupValues?.get(1)
    val hasImage = Regex("has:\\s*(image|file|attachment)", RegexOption.IGNORE_CASE).containsMatchIn(terms)
    val free = terms.replace(Regex("(from|has):\\s*\\S+", RegexOption.IGNORE_CASE), "").trim()
    val results = if (terms.isBlank()) {
        emptyList()
    } else {
        state.messages.filter { m ->
            !m.isSystem &&
                (from == null || m.authorName.contains(from, ignoreCase = true)) &&
                (!hasImage || m.hasMedia) &&
                (free.isBlank() || m.text.contains(free, ignoreCase = true))
        }.reversed()
    }
    Column(modifier.background(colors.sidebar)) {
        PanelHeader("Search", Icons.Filled.Search, onClose)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.rail)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = colors.text, fontSize = 14.sp),
                cursorBrush = SolidColor(colors.blurple),
                modifier = Modifier.weight(1f).padding(vertical = 9.dp),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Search  ·  from: name  ·  has: image", fontSize = 14.sp, color = colors.muted)
                    inner()
                },
            )
            Icon(Icons.Filled.Search, null, tint = colors.muted, modifier = Modifier.size(18.dp))
        }
        if (terms.isNotBlank()) {
            Text(
                "${results.size} RESULTS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = colors.muted,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results, key = { it.id }) { message ->
                MessageCard(
                    message = message,
                    actionLabel = "Jump",
                    onAction = { onJump(message.id) },
                    onRemove = null,
                    subtitle = viewModel.timestampFull(message.createdAt),
                )
            }
        }
    }
}

@Composable
private fun MessageCard(
    message: DiscordMessageUi,
    actionLabel: String,
    onAction: () -> Unit,
    onRemove: (() -> Unit)?,
    subtitle: String = "",
) {
    val colors = discordColors()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.chat)
            .border(1.dp, colors.rail.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable(onClick = onAction)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CharacterAvatar(name = message.authorName, colorHex = message.authorColorHex, size = 24.dp)
            Text(
                message.authorName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = parseHexColor(message.authorColorHex, colors.header),
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            if (subtitle.isNotBlank()) Text(subtitle, fontSize = 11.sp, color = colors.muted, maxLines = 1)
            if (onRemove != null) {
                Icon(
                    Icons.Filled.Close,
                    "Unpin",
                    tint = colors.muted,
                    modifier = Modifier.size(18.dp).clip(CircleShape).clickable(onClick = onRemove),
                )
            }
        }
        Text(
            discordInline(message.text.take(400), colors, false),
            fontSize = 14.sp,
            color = colors.text,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            actionLabel,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.link,
            modifier = Modifier.align(Alignment.End),
        )
    }
}

/** Discord's profile popout: banner, avatar with status, about me, roles, Message button. */
@Composable
fun ProfilePanel(
    profile: DiscordProfileUi?,
    status: DiscordStatus,
    onMessage: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** The server's roles, to give or take from this member. */
    roles: List<ServerRole> = emptyList(),
    memberRoleIds: List<String> = emptyList(),
    onToggleRole: (String) -> Unit = {},
) {
    val colors = discordColors()
    Column(modifier.background(colors.sidebar).verticalScroll(rememberScrollState())) {
        if (profile == null) {
            PanelHeader("Profile", null, onClose)
            return@Column
        }
        val tint = parseHexColor(profile.colorHex, colors.blurple)
        Box(Modifier.fillMaxWidth().height(150.dp)) {
            Box(Modifier.fillMaxWidth().height(106.dp).background(tint))
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Filled.Close, "Close profile", tint = Color.White)
            }
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp)
                    .clip(CircleShape)
                    .background(colors.sidebar)
                    .padding(6.dp),
            ) {
                StatusAvatar(
                    profile.name,
                    profile.colorHex,
                    84.dp,
                    if (profile.isYou) status else presenceFor(profile.name),
                    ring = colors.sidebar,
                )
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(profile.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.header)
                Text(profile.name.lowercase().replace(' ', '_'), fontSize = 14.sp, color = colors.text)
            }
            if (!profile.isYou && profile.characterId != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.blurple)
                            .clickable { onMessage(profile.characterId) }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.ChatBubbleOutline, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Message", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                    if (profile.isMember) {
                        Text(
                            "Remove",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.red,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.elevated)
                                .clickable { onRemove(profile.characterId) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.panel)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (roles.isNotEmpty() && !profile.isYou && profile.characterId != null) {
                    ProfileSection("Server Roles") {
                        RoleToggles(roles, memberRoleIds, onToggleRole)
                    }
                }
                if (profile.about.isNotBlank()) {
                    ProfileSection("About Me") {
                        Text(profile.about, fontSize = 14.sp, color = colors.text)
                    }
                }
                ProfileSection("Member Since") {
                    Text(
                        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(profile.memberSince)),
                        fontSize = 14.sp,
                        color = colors.text,
                    )
                }
                if (profile.roles.isNotEmpty()) {
                    ProfileSection("Roles") {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            profile.roles.forEach { role ->
                                Row(
                                    Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(colors.elevated)
                                        .padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Box(Modifier.size(10.dp).clip(CircleShape).background(tint))
                                    Text(role, fontSize = 12.sp, color = colors.text)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = discordColors().header)
        content()
    }
}

private const val GROUP_WINDOW_MS = 7L * 60L * 1000L
