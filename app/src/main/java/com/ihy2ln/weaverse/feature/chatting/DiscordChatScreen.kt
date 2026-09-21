package com.ihy2ln.weaverse.feature.chatting

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.components.PromptActionMenuButton
import com.ihy2ln.weaverse.feature.prompt.UnifiedPromptBar
import com.ihy2ln.weaverse.core.ui.components.VoiceToTextField
import com.ihy2ln.weaverse.core.ui.components.mergeSpokenText
import com.ihy2ln.weaverse.core.ui.components.rememberSpeechToText
import com.ihy2ln.weaverse.core.ui.theme.InkAccentBlue
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusSm
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.alwaysScrollEndSpacer
import com.ihy2ln.weaverse.core.ui.util.parseHexColor
import com.ihy2ln.weaverse.feature.prompt.PromptModelPickerDialog
import com.ihy2ln.weaverse.feature.prompt.PromptModelSelection
import com.ihy2ln.weaverse.feature.prompt.PromptWordLimit

/**
 * Discord-style Chatting workspace: a server rail of works (novels and campaign
 * adventures), a channel sidebar per work, and a message pane with AI narration
 * and @-mentionable characters. Home shows direct messages instead of channels.
 */
@Composable
fun DiscordChatScreen(
    selectedServerId: String?,
    selectedRoomId: String?,
    onServerSelect: (String?) -> Unit,
    onRoomSelect: (String?) -> Unit,
    onOpenFriends: () -> Unit,
    viewModel: DiscordChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val tokens = inkTokens()

    // Keep the VM's selection in step with the shell-owned state. When both props change
    // together (e.g. opening a recent conversation in a different server from Home),
    // selectedRoomId is already the new target here, so the server switch skips its own
    // "return to the last room" lookup instead of racing to override this room choice.
    androidx.compose.runtime.LaunchedEffect(selectedServerId) {
        viewModel.selectServer(selectedServerId, autoRestoreLastRoom = selectedRoomId == null)
    }
    androidx.compose.runtime.LaunchedEffect(selectedRoomId) {
        viewModel.selectRoom(selectedRoomId)
    }

    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var channelDialogOpen by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteRoomId by rememberSaveable { mutableStateOf<String?>(null) }
    var promptCollapsed by rememberSaveable { mutableStateOf(false) }
    var modelsOpen by rememberSaveable { mutableStateOf(false) }
    var modelSearch by rememberSaveable { mutableStateOf("") }
    val startDictate = rememberSpeechToText { spoken ->
        viewModel.onInputChange(mergeSpokenText(viewModel.currentInput(), spoken))
    }
    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.attachMedia(uris)
    }
    androidx.compose.runtime.LaunchedEffect(state.mediaPickRequestId) {
        if (state.mediaPickRequestId > 0L) {
            mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        }
    }

    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        // Landscape on a phone is wide enough to show the room list beside the
        // conversation, so it uses the two-pane layout even under the 700dp bar.
        val scaledWidth = maxWidth / androidx.compose.ui.platform.LocalDensity.current.fontScale
        val landscape = maxWidth > maxHeight
        val compact = if (landscape) scaledWidth < 560.dp else scaledWidth < 700.dp
        var channelsOpen by rememberSaveable { mutableStateOf(selectedRoomId == null) }
        // Back-to-list must also clear the actual room selection, not just toggle this
        // local flag — otherwise the room list still shows the old room as selected, and
        // tapping it again is a no-op that just reopens the same conversation.
        val backToRoomList = { channelsOpen = true; onRoomSelect(null) }
        val openDirectMessages = { channelsOpen = true; onRoomSelect(null); onServerSelect(null) }
        androidx.activity.compose.BackHandler(compact && !channelsOpen) { backToRoomList() }
        androidx.compose.runtime.LaunchedEffect(state.selectedRoomId) {
            channelsOpen = state.selectedRoomId == null
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(tokens.background)
                // Without these the pane runs under the navigation bar and keyboard, which
                // in landscape pushed the prompt window off the bottom of the screen.
                .navigationBarsPadding()
                .imePadding(),
        ) {
            if (!compact || channelsOpen) {
                ServerRail(
                    servers = state.servers,
                    selectedServerId = selectedServerId,
                    dmSelected = state.selectedRoom?.kind == ROOM_KIND_DM || selectedServerId == null,
                    onOpenDirectMessages = openDirectMessages,
                    onSelect = onServerSelect,
                )
                ChannelSidebar(
                    state = state,
                    onRoomSelect = { onRoomSelect(it); channelsOpen = false },
                    onOpenRecent = { room ->
                        onServerSelect(room.bookId)
                        onRoomSelect(room.chatId)
                        channelsOpen = false
                    },
                    onAddChannel = { channelDialogOpen = true },
                    onAddCharacter = { pickerOpen = true },
                    onDeleteRoom = { pendingDeleteRoomId = it },
                    modifier = if (compact) Modifier.weight(1f) else Modifier.width(224.dp),
                )
            }
            if (!compact || !channelsOpen) Column(Modifier.weight(1f).fillMaxHeight()) {
                if (compact) {
                    IconButton(
                        onClick = backToRoomList,
                        modifier = Modifier.semantics { contentDescription = "Back to conversations" },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = tokens.primaryText,
                        )
                    }
                }
                MessagePane(
                    state = state, viewModel = viewModel, onOpenFriends = onOpenFriends,
                    onOpenDirectMessages = openDirectMessages,
                    promptCollapsed = promptCollapsed, onPromptCollapsedChange = { promptCollapsed = it },
                    onModelClick = { modelsOpen = true },
                    onMicTap = { if (!state.isStreaming) startDictate() },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }

    if (pickerOpen) {
        CharacterPickerDialog(
            onDismiss = { pickerOpen = false },
            onPick = { characterId ->
                pickerOpen = false
                viewModel.createCharacterRoom(characterId)
            },
        )
    }
    if (modelsOpen) {
        PromptModelPickerDialog(
            models = state.writingModels,
            search = modelSearch,
            onSearchChange = { modelSearch = it },
            selectedRef = state.selectedModelRef,
            defaultRef = state.defaultModelRef,
            onSelect = { id ->
                viewModel.selectModel(id)
                modelsOpen = false
            },
            onUseDefault = {
                viewModel.useDefaultModel()
                modelsOpen = false
            },
            onDismiss = { modelsOpen = false },
        )
    }
    if (channelDialogOpen) {
        var channelName by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { channelDialogOpen = false },
            title = { Text("New channel") },
            text = {
                OutlinedTextField(
                    value = channelName,
                    onValueChange = { channelName = it },
                    singleLine = true,
                    placeholder = { Text("new-channel") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createChannel(channelName)
                        channelDialogOpen = false
                    },
                    enabled = channelName.isNotBlank(),
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { channelDialogOpen = false }) { Text("Cancel") }
            },
        )
    }
    pendingDeleteRoomId?.let { roomId ->
        val roomName = (state.rooms + state.directMessages)
            .find { it.chatId == roomId }?.name.orEmpty()
        AlertDialog(
            onDismissRequest = { pendingDeleteRoomId = null },
            title = { Text("Delete #${roomName}?") },
            text = { Text("This removes the room and every message inside it. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRoom(roomId)
                    pendingDeleteRoomId = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteRoomId = null }) { Text("Cancel") }
            },
        )
    }
}

// ------------------------------------------------------------------ server rail

@Composable
private fun ServerRail(
    servers: List<DiscordServerUi>,
    selectedServerId: String?,
    dmSelected: Boolean,
    onOpenDirectMessages: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    val tokens = inkTokens()
    Column(
        modifier = Modifier
            .width(64.dp)
            .fillMaxHeight()
            .background(tokens.panel)
            .padding(vertical = InkSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(InkSpacing.sm),
    ) {
        ServerIcon(
            label = "⌂",
            colorHex = null,
            selected = selectedServerId == null,
            onClick = { onSelect(null) },
        )
        DmRailButton(
            selected = selectedServerId == null && dmSelected,
            onClick = onOpenDirectMessages,
        )
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(tokens.hairline),
        )
        if (servers.isEmpty()) {
            Text(
                "—",
                style = MaterialTheme.typography.labelLarge,
                color = tokens.secondaryText,
            )
        }
        servers.forEach { server ->
            ServerIcon(
                label = server.monogram,
                colorHex = server.colorHex,
                selected = selectedServerId == server.bookId,
                onClick = { onSelect(server.bookId) },
            )
        }
    }
}

@Composable
private fun ServerIcon(
    label: String,
    colorHex: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = inkTokens()
    val shape = if (selected) RoundedCornerShape(14.dp) else CircleShape
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(shape)
            .background(
                when {
                    selected -> parseHexColor(colorHex, MaterialTheme.colorScheme.primary)
                    colorHex != null ->
                        parseHexColor(colorHex, MaterialTheme.colorScheme.primary).copy(alpha = 0.55f)
                    else -> tokens.hover
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (selected && colorHex == null) tokens.activePillLabel else tokens.primaryText,
        )
    }
}

// --------------------------------------------------------------- channel sidebar

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelSidebar(
    state: DiscordChatUiState,
    onRoomSelect: (String?) -> Unit,
    onOpenRecent: (DiscordRoomUi) -> Unit,
    onAddChannel: () -> Unit,
    onAddCharacter: () -> Unit,
    onDeleteRoom: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = inkTokens()
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(tokens.background)
            .padding(vertical = InkSpacing.md),
    ) {
        Text(
            state.selectedServer?.title ?: "Direct Messages",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = tokens.primaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = InkSpacing.lg),
        )
        Text(
            when {
                state.selectedServer == null -> "Your private conversations"
                state.selectedServer.workType == "campaign" -> "Campaign server"
                else -> "Novel server"
            },
            style = MaterialTheme.typography.labelSmall,
            color = tokens.secondaryText,
            modifier = Modifier.padding(horizontal = InkSpacing.lg, vertical = InkSpacing.xxs),
        )
        Spacer(Modifier.height(InkSpacing.sm))

        if (state.selectedServerId == null) {
            if (state.recentConversations.isNotEmpty()) {
                CategoryHeader("Recent Conversations")
                RecentConversationList(
                    rooms = state.recentConversations,
                    selectedRoomId = state.selectedRoomId,
                    onOpenRecent = onOpenRecent,
                )
                Spacer(Modifier.height(InkSpacing.sm))
            }
            CategoryHeader("Direct Messages")
            if (state.directMessages.isEmpty()) {
                SidebarHint("No DMs yet — open Contacts and tap a friend.")
            }
            RoomList(
                rooms = state.directMessages,
                selectedRoomId = state.selectedRoomId,
                onRoomSelect = onRoomSelect,
                onDeleteRoom = onDeleteRoom,
            )
            // Recent is capped and sorted by activity, so every server's channels and
            // character sub-rooms are also listed in full underneath it.
            state.serverSections.forEach { section ->
                Spacer(Modifier.height(InkSpacing.sm))
                CategoryHeader(section.title)
                RecentConversationList(
                    rooms = section.channels + section.characterRooms,
                    selectedRoomId = state.selectedRoomId,
                    onOpenRecent = onOpenRecent,
                )
            }
        } else {
            CategoryHeaderRow(label = "Text Channels", trailing = "+", onTrailing = onAddChannel)
            RoomList(
                rooms = state.rooms.filter { it.kind == ROOM_KIND_CHANNEL },
                selectedRoomId = state.selectedRoomId,
                onRoomSelect = onRoomSelect,
                onDeleteRoom = onDeleteRoom,
            )
            CategoryHeaderRow(label = "Characters", trailing = "+", onTrailing = onAddCharacter)
            val characterRooms = state.rooms.filter { it.kind == ROOM_KIND_CHARACTER }
            if (characterRooms.isEmpty()) {
                SidebarHint("Tap + to give a character a room here.")
            }
            RoomList(
                rooms = characterRooms,
                selectedRoomId = state.selectedRoomId,
                onRoomSelect = onRoomSelect,
                onDeleteRoom = onDeleteRoom,
            )
        }
    }
}

@Composable
private fun CategoryHeader(label: String) {
    Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = inkTokens().secondaryText,
        modifier = Modifier.padding(horizontal = InkSpacing.lg, vertical = InkSpacing.xs),
    )
}

@Composable
private fun CategoryHeaderRow(label: String, trailing: String, onTrailing: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = InkSpacing.lg, vertical = InkSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = inkTokens().secondaryText,
            modifier = Modifier.weight(1f),
        )
        Text(
            trailing,
            style = MaterialTheme.typography.titleMedium,
            color = inkTokens().secondaryText,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onTrailing)
                .padding(horizontal = InkSpacing.xs),
        )
    }
}

@Composable
private fun SidebarHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = inkTokens().secondaryText,
        modifier = Modifier.padding(horizontal = InkSpacing.lg, vertical = InkSpacing.xxs),
    )
}

/** Most recently active rooms across every server — each row shows which one it's from. */
@Composable
private fun RecentConversationList(
    rooms: List<DiscordRoomUi>,
    selectedRoomId: String?,
    onOpenRecent: (DiscordRoomUi) -> Unit,
) {
    rooms.forEach { room ->
        RecentConversationRow(
            room = room,
            selected = room.chatId == selectedRoomId,
            onClick = { onOpenRecent(room) },
        )
    }
}

@Composable
private fun RecentConversationRow(
    room: DiscordRoomUi,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = inkTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = InkSpacing.sm, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) tokens.activePill else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
    ) {
        if (room.kind == ROOM_KIND_CHANNEL) {
            Text(
                "#",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = tokens.secondaryText,
            )
        } else {
            com.ihy2ln.weaverse.feature.roleplay.friends.CharacterAvatar(
                name = room.name,
                colorHex = room.avatarColorHex,
                size = 22.dp,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                room.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) tokens.activePillLabel else tokens.primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (room.serverTitle.isNotBlank()) {
                Text(
                    room.serverTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) tokens.activePillLabel else tokens.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (room.unread > 0) {
            Text(
                room.unread.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun RoomList(
    rooms: List<DiscordRoomUi>,
    selectedRoomId: String?,
    onRoomSelect: (String?) -> Unit,
    onDeleteRoom: (String) -> Unit,
) {
    rooms.forEach { room ->
        RoomRow(
            room = room,
            selected = room.chatId == selectedRoomId,
            onClick = { onRoomSelect(room.chatId) },
            onLongClick = { onDeleteRoom(room.chatId) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoomRow(
    room: DiscordRoomUi,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val tokens = inkTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = InkSpacing.sm, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) tokens.activePill else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
    ) {
        if (room.kind == ROOM_KIND_CHANNEL) {
            Text(
                "#",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = tokens.secondaryText,
            )
        } else {
            com.ihy2ln.weaverse.feature.roleplay.friends.CharacterAvatar(
                name = room.name,
                colorHex = room.avatarColorHex,
                size = 22.dp,
            )
        }
        Text(
            room.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) tokens.activePillLabel else tokens.primaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (room.unread > 0) {
            Text(
                room.unread.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

// ----------------------------------------------------------------- message pane

@Composable
private fun MessagePane(
    state: DiscordChatUiState,
    viewModel: DiscordChatViewModel,
    onOpenFriends: () -> Unit,
    onOpenDirectMessages: () -> Unit,
    promptCollapsed: Boolean,
    onPromptCollapsedChange: (Boolean) -> Unit,
    onModelClick: () -> Unit,
    onMicTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = inkTokens()
    Column(
        modifier = modifier.background(tokens.page),
    ) {
        val room = state.selectedRoom
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(tokens.background)
                .padding(horizontal = InkSpacing.lg, vertical = InkSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
        ) {
            if (room?.kind == ROOM_KIND_CHANNEL) {
                Text(
                    "#",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = tokens.secondaryText,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    room?.name ?: "Welcome",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = tokens.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (room != null && room.topic.isNotBlank()) {
                    Text(
                        room.topic,
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (room != null) {
                // Lives in the header, not the prompt bar: the compact composer row has no
                // spare width, and crowding it squeezes the other controls out.
                IconButton(
                    onClick = { viewModel.requestMediaPick() },
                    modifier = Modifier
                        .size(34.dp)
                        .semantics { contentDescription = "Send a picture" },
                ) {
                    Icon(
                        Icons.Outlined.Image,
                        contentDescription = null,
                        tint = if (state.hasPendingMedia) tokens.activePill else tokens.primaryText,
                    )
                }
            }
            if (state.selectedServerId != null) {
                DmRailButton(selected = false, onClick = onOpenDirectMessages)
            }
            InkTextButton(
                label = "Friends",
                onClick = onOpenFriends,
            )
        }

        if (room == null) {
            EmptyPaneHint(state)
        } else {
            if (room.kind != ROOM_KIND_DM && state.members.isNotEmpty()) {
                MemberStrip(members = state.members, onRemove = viewModel::removeMember)
            }
            MessageList(state, viewModel, Modifier.weight(1f))
            if (state.lastUsage.isNotBlank()) {
                Text(
                    state.lastUsage,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.secondaryText,
                    modifier = Modifier.padding(horizontal = InkSpacing.lg, vertical = InkSpacing.xxs),
                )
            }
            if (state.errorMessage.isNotBlank()) {
                Text(
                    state.errorMessage,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.onInputChange(state.input) }
                        .padding(horizontal = InkSpacing.lg, vertical = InkSpacing.xxs),
                )
            }
            ChatPromptWindow(
                state = state,
                viewModel = viewModel,
                roomName = room.name,
                onModelClick = onModelClick,
                onMicTap = onMicTap,
                modifier = Modifier
                    .fillMaxWidth()
                    // Floor so the message list's weight cannot squeeze the window down to
                    // its drag handle, which is what happened in landscape.
                    .heightIn(min = 148.dp)
                    .padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xs),
            )
        }
    }
}

/** Rail shortcut to Home's Direct Messages: an envelope carrying a "DM" label. */
@Composable
private fun DmRailButton(selected: Boolean, onClick: () -> Unit) {
    val tokens = inkTokens()
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(if (selected) RoundedCornerShape(14.dp) else CircleShape)
            .background(if (selected) tokens.activePill else tokens.hover)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Direct Messages" },
        contentAlignment = Alignment.Center,
    ) {
        val ink = if (selected) tokens.activePillLabel else tokens.primaryText
        Icon(
            Icons.Outlined.MailOutline,
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(34.dp),
        )
        Text(
            "DM",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = ink,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Compact avatar strip for who's seated in the room; long-press to remove. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemberStrip(members: List<DiscordMemberUi>, onRemove: (String) -> Unit) {
    val tokens = inkTokens()
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(tokens.background)
            .padding(horizontal = InkSpacing.lg, vertical = InkSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
    ) {
        items(members, key = { it.characterId }) { member ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .combinedClickable(onClick = {}, onLongClick = { onRemove(member.characterId) })
                    .padding(InkSpacing.xxs),
            ) {
                com.ihy2ln.weaverse.feature.roleplay.friends.CharacterAvatar(
                    name = member.name,
                    colorHex = member.colorHex,
                    size = 32.dp,
                )
                Text(
                    member.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(48.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * The Chatting composer: the same prompt window the RPG adventure and Novel
 * editor use (word range, AI/manual, model, retry/continue/cancel, context
 * meter, backspace clear with hold-to-undo).
 */
@Composable
private fun MentionAutocompleteRow(
    candidates: List<DiscordMemberUi>,
    memberIds: Set<String>,
    onPick: (String) -> Unit,
) {
    val tokens = inkTokens()
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = InkSpacing.lg, vertical = InkSpacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
    ) {
        items(candidates, key = { it.characterId }) { candidate ->
            val isMember = candidate.characterId in memberIds
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(InkSpacing.xxs),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(tokens.hover)
                    .clickable { onPick(candidate.name) }
                    .padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xs),
            ) {
                com.ihy2ln.weaverse.feature.roleplay.friends.CharacterAvatar(
                    name = candidate.name,
                    colorHex = candidate.colorHex,
                    size = 18.dp,
                )
                Text(candidate.name, style = MaterialTheme.typography.labelMedium, color = tokens.primaryText)
                if (!isMember) {
                    Text("+ add", style = MaterialTheme.typography.labelSmall, color = tokens.secondaryText)
                }
            }
        }
    }
}

@Composable
private fun EmptyPaneHint(state: DiscordChatUiState) {
    val tokens = inkTokens()
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(InkSpacing.xs),
            modifier = Modifier.padding(InkSpacing.xl),
        ) {
            Text(
                if (state.selectedServerId == null) "Pick a conversation" else "Pick a channel",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = tokens.primaryText,
            )
            Text(
                if (state.selectedServerId == null) {
                    "Choose a DM under Home, or pick a work's server from the rail to chat about it."
                } else {
                    "Choose a text channel, or open a character's room and @mention them anywhere."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.secondaryText,
            )
        }
    }
}

@Composable
private fun MessageList(
    state: DiscordChatUiState,
    viewModel: DiscordChatViewModel,
    modifier: Modifier = Modifier,
) {
    val tokens = inkTokens()
    val roomId = state.selectedRoomId
    val listState = rememberSaveable(roomId, saver = LazyListState.Saver) { LazyListState() }
    LaunchedEffect(roomId) {
        if (roomId == null) return@LaunchedEffect
        val saved = viewModel.scrollFor(roomId)
        if (saved != null) {
            listState.scrollToItem(saved.first, saved.second)
        } else if (state.messages.isNotEmpty()) {
            listState.scrollToItem((listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
        }
    }
    LaunchedEffect(roomId, state.messages.size) {
        if (roomId == null) return@LaunchedEffect
        val totalItems = listState.layoutInfo.totalItemsCount
        val atBottom = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index == totalItems - 1
        if (viewModel.scrollFor(roomId) == null || atBottom) {
            listState.scrollToItem((totalItems - 1).coerceAtLeast(0))
        }
    }
    LaunchedEffect(roomId) {
        if (roomId == null) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> viewModel.rememberScroll(roomId, index, offset) }
    }
    LazyColumn(state = listState, modifier = modifier) {
        if (state.messages.isEmpty() && !state.isStreaming) {
            item(key = "empty") {
                Text(
                    "This is the start of #${state.selectedRoom?.name.orEmpty()}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.secondaryText,
                    modifier = Modifier.padding(InkSpacing.lg),
                )
            }
        }
        DayGroupedMessages(state, viewModel)
        if (state.isStreaming) {
            item(key = "streaming") {
                StreamingRow(
                    authorName = state.members.firstOrNull()?.name
                        ?: state.selectedRoom?.name
                        ?: "…",
                    text = state.streamingText,
                )
            }
        }
        alwaysScrollEndSpacer()
    }
}

/** Groups messages by day, then renders Discord-style compact groups per author. */
private fun LazyListScope.DayGroupedMessages(
    state: DiscordChatUiState,
    viewModel: DiscordChatViewModel,
) {
    var lastDay = ""
    state.messages.forEachIndexed { index, message ->
        val day = viewModel.dayLabel(message.createdAt)
        if (day != lastDay) {
            lastDay = day
            item(key = "day-$day-$index") {
                DayDivider(label = day)
            }
        }
        val previous = state.messages.getOrNull(index - 1)
        val grouped = previous != null &&
            previous.authorName == message.authorName &&
            message.createdAt - previous.createdAt < GROUP_WINDOW_MS &&
            viewModel.dayLabel(previous.createdAt) == day
        item(key = message.id) {
            if (message.isSystem) {
                SystemMessageRow(message.text)
            } else {
                MessageRow(
                    message = message,
                    grouped = grouped,
                    timeFull = viewModel.timestampFull(message.createdAt),
                    timeShort = viewModel.timestampShort(message.createdAt),
                )
            }
        }
    }
}

@Composable
private fun DayDivider(label: String) {
    val tokens = inkTokens()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = InkSpacing.lg, vertical = InkSpacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = tokens.secondaryText,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(tokens.background)
                .padding(horizontal = InkSpacing.sm, vertical = 2.dp),
        )
    }
}

/** A join/system line — centered and dimmed, not a chat bubble. */
@Composable
private fun SystemMessageRow(text: String) {
    val tokens = inkTokens()
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = InkSpacing.xxs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = tokens.secondaryText,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MessageRow(
    message: DiscordMessageUi,
    grouped: Boolean,
    timeFull: String,
    timeShort: String,
) {
    val tokens = inkTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = InkSpacing.lg, vertical = if (grouped) 1.dp else InkSpacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        if (grouped) {
            Spacer(Modifier.width(36.dp))
            Text(
                timeShort,
                style = MaterialTheme.typography.labelSmall,
                color = tokens.secondaryText,
                modifier = Modifier.padding(top = 3.dp),
            )
            Spacer(Modifier.width(InkSpacing.sm))
        } else {
            com.ihy2ln.weaverse.feature.roleplay.friends.CharacterAvatar(
                name = message.authorName,
                colorHex = message.authorColorHex,
                size = 36.dp,
            )
            Spacer(Modifier.width(InkSpacing.sm))
        }
        Column(modifier = Modifier.weight(1f)) {
            if (!grouped) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
                ) {
                    Text(
                        message.authorName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = parseHexColor(message.authorColorHex, tokens.primaryText),
                    )
                    if (message.isBot) {
                        Text(
                            "APP",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                    Text(
                        timeFull,
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.secondaryText,
                    )
                }
            }
            if (message.text.isNotBlank()) {
                // Per row, not around the whole list: a SelectionContainer wrapping the
                // LazyColumn makes it report its full content height, which squeezes the
                // prompt window down to its drag handle.
                SelectionContainer {
                    Text(
                        message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.primaryText,
                    )
                }
            }
            message.mediaPaths.take(4).forEach { path ->
                coil3.compose.AsyncImage(
                    model = java.io.File(path),
                    contentDescription = "Attached image",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .padding(top = InkSpacing.xs)
                        .size(width = 200.dp, height = 130.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
            }
            if (message.hasMedia && message.mediaPaths.isEmpty()) {
                Text(
                    "media attachment",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.secondaryText,
                )
            }
        }
    }
}

@Composable
private fun StreamingRow(authorName: String, text: String) {
    val tokens = inkTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = InkSpacing.lg, vertical = InkSpacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        com.ihy2ln.weaverse.feature.roleplay.friends.CharacterAvatar(
            name = authorName,
            colorHex = com.ihy2ln.weaverse.core.roleplay.avatarColorHexFor(authorName, null),
            size = 36.dp,
        )
        Spacer(Modifier.width(InkSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "$authorName is typing…",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = tokens.secondaryText,
            )
            if (text.isNotBlank()) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.primaryText,
                )
            }
        }
    }
}

@Composable
private fun CharacterPickerDialog(
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val viewModel: CharacterPickerViewModel = hiltViewModel()
    val characters by viewModel.characters.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a character room") },
        text = {
            if (characters.isEmpty()) {
                Text("No characters yet — add one under RPG → Roster or Contacts → Meet someone.")
            } else {
                LazyColumn {
                    items(characters, key = { it.id }) { character ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(character.id) }
                                .padding(vertical = InkSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm),
                        ) {
                            com.ihy2ln.weaverse.feature.roleplay.friends.CharacterAvatar(
                                name = character.name,
                                colorHex = com.ihy2ln.weaverse.core.roleplay.avatarColorHexFor(
                                    character.name,
                                    character.colorHex,
                                ),
                                size = 28.dp,
                            )
                            Text(
                                character.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private const val GROUP_WINDOW_MS = 7L * 60L * 1000L
