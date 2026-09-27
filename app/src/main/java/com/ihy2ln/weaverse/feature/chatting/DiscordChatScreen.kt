package com.ihy2ln.weaverse.feature.chatting

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.HeadsetOff
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.components.mergeSpokenText
import com.ihy2ln.weaverse.core.ui.components.rememberSpeechToText
import com.ihy2ln.weaverse.core.ui.util.parseHexColor
import com.ihy2ln.weaverse.feature.prompt.PromptModelPickerDialog
import com.ihy2ln.weaverse.feature.roleplay.friends.CharacterAvatar

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
) = DiscordTheme {
    val state by viewModel.uiState.collectAsState()
    val colors = discordColors()

    // Keep the VM's selection in step with the shell-owned state. When both props change
    // together (e.g. opening a recent conversation in a different server from Home),
    // selectedRoomId is already the new target here, so the server switch skips its own
    // "return to the last room" lookup instead of racing to override this room choice.
    LaunchedEffect(selectedServerId) {
        viewModel.selectServer(selectedServerId, autoRestoreLastRoom = selectedRoomId == null)
    }
    LaunchedEffect(selectedRoomId) {
        viewModel.selectRoom(selectedRoomId)
    }

    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var channelDialogOpen by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteRoomId by rememberSaveable { mutableStateOf<String?>(null) }
    var modelsOpen by rememberSaveable { mutableStateOf(false) }
    var modelSearch by rememberSaveable { mutableStateOf("") }
    var memberListOpen by rememberSaveable { mutableStateOf(false) }
    var sidePanel by rememberSaveable { mutableStateOf(SidePanel.None) }
    var status by rememberSaveable { mutableStateOf(DiscordStatus.Online) }
    var muted by rememberSaveable { mutableStateOf(false) }
    var deafened by rememberSaveable { mutableStateOf(false) }
    var jumpToMessageId by remember { mutableStateOf<String?>(null) }
    val startDictate = rememberSpeechToText { spoken ->
        viewModel.onInputChange(mergeSpokenText(viewModel.currentInput(), spoken))
    }
    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.attachMedia(uris)
    }
    LaunchedEffect(state.mediaPickRequestId) {
        if (state.mediaPickRequestId > 0L) {
            mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        }
    }
    LaunchedEffect(state.selectedRoomId) { sidePanel = SidePanel.None }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Landscape on a phone is wide enough to show the room list beside the
        // conversation, so it uses the two-pane layout even under the 700dp bar.
        val scaledWidth = maxWidth / LocalDensity.current.fontScale
        val landscape = maxWidth > maxHeight
        val compact = if (landscape) scaledWidth < 560.dp else scaledWidth < 700.dp
        val roomyEnoughForMembers = scaledWidth >= 980.dp
        var channelsOpen by rememberSaveable { mutableStateOf(selectedRoomId == null) }
        // Back-to-list must also clear the actual room selection, not just toggle this
        // local flag — otherwise the room list still shows the old room as selected, and
        // tapping it again is a no-op that just reopens the same conversation.
        val backToRoomList = { channelsOpen = true; onRoomSelect(null) }
        val openDirectMessages = {
            channelsOpen = true
            onRoomSelect(null)
            onServerSelect(null)
            viewModel.openDmContacts()
        }
        val sidePanelOverlay = sidePanel != SidePanel.None && (compact || !roomyEnoughForMembers)
        androidx.activity.compose.BackHandler(sidePanel != SidePanel.None) { sidePanel = SidePanel.None }
        androidx.activity.compose.BackHandler(compact && !channelsOpen && sidePanel == SidePanel.None) {
            backToRoomList()
        }
        LaunchedEffect(state.selectedRoomId) {
            channelsOpen = state.selectedRoomId == null
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.chat)
                // Without these the pane runs under the navigation bar and keyboard, which
                // in landscape pushed the prompt window off the bottom of the screen.
                .navigationBarsPadding()
                .imePadding(),
        ) {
            if ((!compact || channelsOpen) && !(compact && state.dmContactsOpen)) {
                ServerRail(
                    state = state,
                    selectedServerId = selectedServerId,
                    onOpenDirectMessages = openDirectMessages,
                    onSelect = onServerSelect,
                )
                Column(
                    modifier = (if (compact) Modifier.weight(1f) else Modifier.width(240.dp))
                        .fillMaxHeight()
                        .background(colors.sidebar),
                ) {
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
                        onFindConversation = openDirectMessages,
                        onOpenFriends = onOpenFriends,
                        onMarkRead = viewModel::markServerRead,
                        modifier = Modifier.weight(1f),
                    )
                    UserPanel(
                        name = state.personaName,
                        status = status,
                        onStatusChange = { status = it },
                        muted = muted,
                        deafened = deafened,
                        onToggleMute = { muted = !muted; if (!muted) deafened = false },
                        onToggleDeafen = { deafened = !deafened; muted = deafened },
                        onOpenProfile = {
                            viewModel.openProfile(null, state.personaName, "", isYou = true)
                            sidePanel = SidePanel.Profile
                        },
                    )
                }
            }
            if (state.dmContactsOpen) {
                DmContactsPane(
                    state = state,
                    onPick = viewModel::openDirectMessage,
                    onClose = viewModel::closeDmContacts,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            } else if (!compact || !channelsOpen) {
                if (sidePanelOverlay) {
                    SidePanelContent(
                        panel = sidePanel,
                        state = state,
                        viewModel = viewModel,
                        status = status,
                        onClose = { sidePanel = SidePanel.None },
                        onJump = { id -> jumpToMessageId = id; sidePanel = SidePanel.None },
                        onOpenProfile = { sidePanel = SidePanel.Profile },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                } else {
                    MessagePane(
                        state = state,
                        viewModel = viewModel,
                        compact = compact,
                        onBack = backToRoomList,
                        onOpenFriends = onOpenFriends,
                        onOpenDirectMessages = openDirectMessages,
                        onModelClick = { modelsOpen = true },
                        onMicTap = { if (!state.isStreaming) startDictate() },
                        onTogglePanel = { panel ->
                            if (panel == SidePanel.Members && roomyEnoughForMembers) {
                                memberListOpen = !memberListOpen
                            } else {
                                sidePanel = if (sidePanel == panel) SidePanel.None else panel
                            }
                        },
                        onOpenProfile = { sidePanel = SidePanel.Profile },
                        jumpToMessageId = jumpToMessageId,
                        onJumpHandled = { jumpToMessageId = null },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    if (roomyEnoughForMembers && sidePanel != SidePanel.None) {
                        SidePanelContent(
                            panel = sidePanel,
                            state = state,
                            viewModel = viewModel,
                            status = status,
                            onClose = { sidePanel = SidePanel.None },
                            onJump = { id -> jumpToMessageId = id },
                            onOpenProfile = { sidePanel = SidePanel.Profile },
                            modifier = Modifier.width(300.dp).fillMaxHeight(),
                        )
                    } else if (roomyEnoughForMembers && memberListOpen && state.selectedRoom != null &&
                        state.selectedRoom?.kind != ROOM_KIND_DM
                    ) {
                        MemberListPanel(
                            state = state,
                            status = status,
                            onOpenProfile = { m ->
                                viewModel.openProfile(m.characterId, m.name, m.colorHex)
                                sidePanel = SidePanel.Profile
                            },
                            onOpenYou = {
                                viewModel.openProfile(null, state.personaName, "", isYou = true)
                                sidePanel = SidePanel.Profile
                            },
                            onClose = null,
                            modifier = Modifier.width(240.dp).fillMaxHeight(),
                        )
                    }
                }
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
            title = { Text("Create Channel") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "CHANNEL NAME",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.muted,
                    )
                    OutlinedTextField(
                        value = channelName,
                        // Discord channel names are lowercase and hyphenated.
                        onValueChange = { channelName = it.lowercase().replace(' ', '-') },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Tag, contentDescription = null) },
                        placeholder = { Text("new-channel") },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createChannel(channelName)
                        channelDialogOpen = false
                    },
                    enabled = channelName.isNotBlank(),
                ) { Text("Create Channel") }
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
                }) { Text("Delete", color = colors.red) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteRoomId = null }) { Text("Cancel") }
            },
        )
    }
}

/** Right-hand panels Discord opens from the channel header. */
enum class SidePanel { None, Members, Pinned, Search, Profile }

@Composable
private fun SidePanelContent(
    panel: SidePanel,
    state: DiscordChatUiState,
    viewModel: DiscordChatViewModel,
    status: DiscordStatus,
    onClose: () -> Unit,
    onJump: (String) -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier,
) {
    when (panel) {
        SidePanel.Members -> MemberListPanel(
            state = state,
            status = status,
            onOpenProfile = { m ->
                viewModel.openProfile(m.characterId, m.name, m.colorHex)
                onOpenProfile()
            },
            onOpenYou = {
                viewModel.openProfile(null, state.personaName, "", isYou = true)
                onOpenProfile()
            },
            onClose = onClose,
            modifier = modifier,
        )
        SidePanel.Pinned -> PinnedMessagesPanel(
            state = state,
            onJump = onJump,
            onUnpin = viewModel::togglePin,
            onClose = onClose,
            modifier = modifier,
        )
        SidePanel.Search -> SearchMessagesPanel(
            state = state,
            viewModel = viewModel,
            onJump = onJump,
            onClose = onClose,
            modifier = modifier,
        )
        SidePanel.Profile -> ProfilePanel(
            profile = state.profile,
            status = status,
            onMessage = { id -> onClose(); viewModel.closeProfile(); viewModel.openDirectMessage(id) },
            onRemove = { id -> viewModel.removeMember(id); viewModel.closeProfile(); onClose() },
            onClose = { viewModel.closeProfile(); onClose() },
            modifier = modifier,
        )
        SidePanel.None -> Unit
    }
}

// ------------------------------------------------------------------ server rail

@Composable
private fun ServerRail(
    state: DiscordChatUiState,
    selectedServerId: String?,
    onOpenDirectMessages: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    val colors = discordColors()
    Column(
        modifier = Modifier
            .width(72.dp)
            .fillMaxHeight()
            .background(colors.rail)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RailItem(
            selected = selectedServerId == null,
            unread = state.dmUnread,
            background = colors.blurple,
            idleBackground = colors.elevated,
            onClick = { onSelect(null) },
            description = "Direct Messages home",
        ) { selected ->
            Icon(
                Icons.Filled.SportsEsports,
                contentDescription = null,
                tint = if (selected) Color.White else colors.text,
                modifier = Modifier.size(28.dp),
            )
        }
        RailItem(
            selected = false,
            unread = 0,
            background = colors.blurple,
            idleBackground = colors.elevated,
            onClick = onOpenDirectMessages,
            description = "Start a direct message",
        ) {
            Icon(
                Icons.Outlined.MailOutline,
                contentDescription = null,
                tint = colors.text,
                modifier = Modifier.size(24.dp),
            )
        }
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(colors.divider),
        )
        state.servers.forEach { server ->
            val tint = parseHexColor(server.colorHex, colors.blurple)
            RailItem(
                selected = selectedServerId == server.bookId,
                unread = state.serverUnread[server.bookId] ?: 0,
                background = tint,
                idleBackground = tint.copy(alpha = if (colors.dark) 0.55f else 0.75f),
                onClick = { onSelect(server.bookId) },
                description = server.title,
            ) {
                Text(
                    server.monogram,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
        }
    }
}

/** A rail icon: circle when idle, rounded square when selected, with Discord's left pill. */
@Composable
private fun RailItem(
    selected: Boolean,
    unread: Int,
    background: Color,
    idleBackground: Color,
    onClick: () -> Unit,
    description: String,
    content: @Composable (Boolean) -> Unit,
) {
    val colors = discordColors()
    val corner by animateDpAsState(if (selected) 16.dp else 24.dp, label = "rail-corner")
    val pill by animateDpAsState(
        when {
            selected -> 40.dp
            unread > 0 -> 8.dp
            else -> 0.dp
        },
        label = "rail-pill",
    )
    Box(Modifier.fillMaxWidth().height(48.dp)) {
        if (pill > 0.dp) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(4.dp)
                    .height(pill)
                    .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                    .background(colors.header),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(48.dp)
                .clip(RoundedCornerShape(corner))
                .background(if (selected) background else idleBackground)
                .clickable(onClick = onClick)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) { content(selected) }
        if (unread > 0) {
            UnreadBadge(
                count = unread,
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-6).dp, y = 2.dp),
                ring = colors.rail,
            )
        }
    }
}

@Composable
fun UnreadBadge(count: Int, modifier: Modifier = Modifier, ring: Color = Color.Transparent) {
    val colors = discordColors()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ring)
            .padding(3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.red)
            .padding(horizontal = 5.dp, vertical = 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

// --------------------------------------------------------------- channel sidebar

@Composable
private fun ChannelSidebar(
    state: DiscordChatUiState,
    onRoomSelect: (String?) -> Unit,
    onOpenRecent: (DiscordRoomUi) -> Unit,
    onAddChannel: () -> Unit,
    onAddCharacter: () -> Unit,
    onDeleteRoom: (String) -> Unit,
    onFindConversation: () -> Unit,
    onOpenFriends: () -> Unit,
    onMarkRead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = discordColors()
    var collapsed by rememberSaveable { mutableStateOf(setOf<String>()) }
    fun toggle(key: String) {
        collapsed = if (key in collapsed) collapsed - key else collapsed + key
    }
    Column(modifier = modifier.fillMaxWidth()) {
        val server = state.selectedServer
        if (server == null) {
            // Home: search box, Friends, then the DM list.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Find or start a conversation",
                    fontSize = 13.sp,
                    color = colors.muted,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.rail)
                        .clickable(onClick = onFindConversation)
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                )
            }
            HorizontalHairline()
        } else {
            ServerHeader(
                server = server,
                onAddChannel = onAddChannel,
                onAddCharacter = onAddCharacter,
                onMarkRead = onMarkRead,
            )
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
            if (server == null) {
                item(key = "friends") {
                    SidebarNavRow(icon = Icons.Filled.People, label = "Friends", onClick = onOpenFriends)
                }
                item(key = "dm-header") {
                    CategoryHeader(
                        label = "Direct Messages",
                        collapsed = false,
                        onToggle = null,
                        onAdd = onFindConversation,
                    )
                }
                if (state.directMessages.isEmpty()) {
                    item(key = "dm-empty") { SidebarHint("No DMs yet — tap + and pick someone to write to.") }
                }
                items(state.directMessages, key = { "dm-" + it.chatId }) { room ->
                    DmRow(
                        room = room,
                        selected = room.chatId == state.selectedRoomId,
                        onClick = { onRoomSelect(room.chatId) },
                        onLongClick = { onDeleteRoom(room.chatId) },
                    )
                }
                val others = state.recentConversations.filter { it.kind != ROOM_KIND_DM }
                if (others.isNotEmpty()) {
                    item(key = "recent-header") {
                        CategoryHeader("Recent Conversations", "recent" in collapsed, onToggle = { toggle("recent") })
                    }
                    if ("recent" !in collapsed) {
                        items(others, key = { "recent-" + it.chatId }) { room ->
                            ChannelRow(
                                room = room,
                                selected = room.chatId == state.selectedRoomId,
                                subtitle = room.serverTitle,
                                onClick = { onOpenRecent(room) },
                                onLongClick = {},
                            )
                        }
                    }
                }
                // Recent is capped and sorted by activity, so every server's channels and
                // character sub-rooms are also listed in full underneath it.
                state.serverSections.forEach { section ->
                    val key = "section-" + section.bookId
                    item(key = key) { CategoryHeader(section.title, key in collapsed, onToggle = { toggle(key) }) }
                    if (key !in collapsed) {
                        items(section.channels + section.characterRooms, key = { key + it.chatId }) { room ->
                            ChannelRow(
                                room = room,
                                selected = room.chatId == state.selectedRoomId,
                                onClick = { onOpenRecent(room) },
                                onLongClick = {},
                            )
                        }
                    }
                }
            } else {
                item(key = "welcome") {
                    SidebarNavRow(icon = Icons.Filled.Tag, label = "Browse Channels", onClick = onAddChannel)
                }
                item(key = "text-header") {
                    CategoryHeader(
                        "Text Channels",
                        "text" in collapsed,
                        onToggle = { toggle("text") },
                        onAdd = onAddChannel,
                    )
                }
                val channels = state.rooms.filter { it.kind == ROOM_KIND_CHANNEL }
                // Collapsed categories still show the open channel and anything unread, like Discord.
                items(
                    channels.filter { "text" !in collapsed || it.chatId == state.selectedRoomId || it.unread > 0 },
                    key = { "ch-" + it.chatId },
                ) { room ->
                    ChannelRow(
                        room = room,
                        selected = room.chatId == state.selectedRoomId,
                        onClick = { onRoomSelect(room.chatId) },
                        onLongClick = { onDeleteRoom(room.chatId) },
                    )
                }
                item(key = "char-header") {
                    CategoryHeader(
                        "Characters",
                        "chars" in collapsed,
                        onToggle = { toggle("chars") },
                        onAdd = onAddCharacter,
                    )
                }
                val characterRooms = state.rooms.filter { it.kind == ROOM_KIND_CHARACTER }
                if (characterRooms.isEmpty()) {
                    item(key = "char-empty") { SidebarHint("Tap + to give a character a room here.") }
                }
                items(
                    characterRooms.filter { "chars" !in collapsed || it.chatId == state.selectedRoomId || it.unread > 0 },
                    key = { "cr-" + it.chatId },
                ) { room ->
                    ChannelRow(
                        room = room,
                        selected = room.chatId == state.selectedRoomId,
                        onClick = { onRoomSelect(room.chatId) },
                        onLongClick = { onDeleteRoom(room.chatId) },
                    )
                }
            }
        }
    }
}

/** Server name banner with Discord's dropdown of server actions. */
@Composable
private fun ServerHeader(
    server: DiscordServerUi,
    onAddChannel: () -> Unit,
    onAddCharacter: () -> Unit,
    onMarkRead: () -> Unit,
) {
    val colors = discordColors()
    val tint = parseHexColor(server.colorHex, colors.blurple)
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.85f), tint.copy(alpha = 0.25f))))
                .clickable { menuOpen = true },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    server.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Server menu", tint = Color.White)
            }
            Text(
                if (server.workType == "campaign") "Campaign server" else "Novel server",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("Mark As Read") }, onClick = { menuOpen = false; onMarkRead() })
            DropdownMenuItem(
                text = { Text("Create Channel") },
                leadingIcon = { Icon(Icons.Filled.Tag, null) },
                onClick = { menuOpen = false; onAddChannel() },
            )
            DropdownMenuItem(
                text = { Text("Invite a Character") },
                leadingIcon = { Icon(Icons.Outlined.PersonAdd, null) },
                onClick = { menuOpen = false; onAddCharacter() },
            )
        }
    }
    HorizontalHairline()
}

@Composable
fun HorizontalHairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(discordColors().rail.copy(alpha = 0.6f)))
}

@Composable
private fun SidebarNavRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = discordColors()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.muted, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colors.muted)
    }
}

@Composable
private fun CategoryHeader(
    label: String,
    collapsed: Boolean,
    onToggle: (() -> Unit)?,
    onAdd: (() -> Unit)? = null,
) {
    val colors = discordColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, start = 4.dp, end = 8.dp, bottom = 4.dp)
            .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onToggle != null) {
            Icon(
                if (collapsed) Icons.Filled.KeyboardArrowRight else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = colors.muted,
                modifier = Modifier.size(14.dp),
            )
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(
            label.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
            color = colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 2.dp),
        )
        if (onAdd != null) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "Add to $label",
                tint = colors.muted,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onAdd),
            )
        }
    }
}

@Composable
private fun SidebarHint(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = discordColors().muted,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelRow(
    room: DiscordRoomUi,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    subtitle: String = "",
) {
    val colors = discordColors()
    val unread = room.unread > 0 && !selected
    Box(Modifier.fillMaxWidth()) {
        if (unread) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(4.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                    .background(colors.header),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 1.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (selected) colors.selected else Color.Transparent)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (room.kind == ROOM_KIND_CHANNEL) {
                Icon(Icons.Filled.Tag, contentDescription = null, tint = colors.muted, modifier = Modifier.size(20.dp))
            } else {
                CharacterAvatar(name = room.name, colorHex = room.avatarColorHex, size = 22.dp)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    room.name,
                    fontSize = 15.sp,
                    fontWeight = if (selected || unread) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected || unread) colors.header else colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(subtitle, fontSize = 11.sp, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (room.unread > 0 && !selected) UnreadBadge(room.unread)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DmRow(
    room: DiscordRoomUi,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = discordColors()
    val unread = room.unread > 0 && !selected
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) colors.selected else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusAvatar(name = room.name, colorHex = room.avatarColorHex, size = 32.dp, status = presenceFor(room.name))
        Column(Modifier.weight(1f)) {
            Text(
                room.name,
                fontSize = 15.sp,
                fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                color = if (selected || unread) colors.header else colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (room.preview.isNotBlank()) {
                Text(
                    room.preview,
                    fontSize = 12.sp,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (unread) UnreadBadge(room.unread)
    }
}

/** Avatar with Discord's presence dot cut into its bottom-right corner. */
@Composable
fun StatusAvatar(
    name: String,
    colorHex: String,
    size: Dp,
    status: DiscordStatus?,
    ring: Color = discordColors().sidebar,
) {
    val colors = discordColors()
    Box {
        CharacterAvatar(name = name, colorHex = colorHex, size = size)
        if (status != null) {
            val dot = (size.value * 0.36f).coerceAtLeast(10f).dp
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .size(dot)
                    .clip(CircleShape)
                    .background(ring)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(colors.statusColor(status)),
            ) {
                if (status == DiscordStatus.Invisible) {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(dot / 3)
                            .clip(CircleShape)
                            .background(ring),
                    )
                }
            }
        }
    }
}

/** Characters don't really go offline; a stable per-name mix of presences keeps the list lively. */
fun presenceFor(name: String): DiscordStatus = when (Math.floorMod(name.hashCode(), 7)) {
    0 -> DiscordStatus.Idle
    1 -> DiscordStatus.DoNotDisturb
    else -> DiscordStatus.Online
}

/** Bottom-left user panel: avatar with status, name, mute, deafen and settings. */
@Composable
private fun UserPanel(
    name: String,
    status: DiscordStatus,
    onStatusChange: (DiscordStatus) -> Unit,
    muted: Boolean,
    deafened: Boolean,
    onToggleMute: () -> Unit,
    onToggleDeafen: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    val colors = discordColors()
    var statusMenu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.panel)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Row(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { statusMenu = true }
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusAvatar(
                    name = name,
                    colorHex = com.ihy2ln.weaverse.core.roleplay.avatarColorHexFor(name, null),
                    size = 32.dp,
                    status = status,
                    ring = colors.panel,
                )
                Column(Modifier.width(96.dp)) {
                    Text(
                        name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.header,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(status.label, fontSize = 12.sp, color = colors.muted, maxLines = 1)
                }
            }
            DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                DropdownMenuItem(
                    text = { Text("View Profile") },
                    onClick = { statusMenu = false; onOpenProfile() },
                )
                DiscordStatus.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        leadingIcon = {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(colors.statusColor(option)),
                            )
                        },
                        onClick = { statusMenu = false; onStatusChange(option) },
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        PanelIcon(
            if (muted) Icons.Filled.MicOff else Icons.Filled.Mic,
            if (muted) "Unmute" else "Mute",
            tint = if (muted) colors.red else colors.muted,
            onClick = onToggleMute,
        )
        PanelIcon(
            if (deafened) Icons.Filled.HeadsetOff else Icons.Filled.Headset,
            if (deafened) "Undeafen" else "Deafen",
            tint = if (deafened) colors.red else colors.muted,
            onClick = onToggleDeafen,
        )
        PanelIcon(Icons.Filled.Settings, "User settings", tint = colors.muted, onClick = { statusMenu = true })
    }
}

@Composable
private fun PanelIcon(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

// ----------------------------------------------------------------- message pane

@Composable
private fun MessagePane(
    state: DiscordChatUiState,
    viewModel: DiscordChatViewModel,
    compact: Boolean,
    onBack: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenDirectMessages: () -> Unit,
    onModelClick: () -> Unit,
    onMicTap: () -> Unit,
    onTogglePanel: (SidePanel) -> Unit,
    onOpenProfile: () -> Unit,
    jumpToMessageId: String?,
    onJumpHandled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = discordColors()
    Column(modifier = modifier.background(colors.chat)) {
        val room = state.selectedRoom
        ChannelHeader(
            room = room,
            compact = compact,
            pinnedCount = state.pinnedMessages.size,
            hasPendingMedia = state.hasPendingMedia,
            onBack = onBack,
            onPickMedia = viewModel::requestMediaPick,
            onTogglePanel = onTogglePanel,
            onOpenDirectMessages = onOpenDirectMessages.takeIf { state.selectedServerId != null },
            onOpenFriends = onOpenFriends,
        )

        if (room == null) {
            EmptyPaneHint(state)
        } else {
            DiscordMessageList(
                state = state,
                viewModel = viewModel,
                onOpenProfile = { characterId, name, colorHex, isYou ->
                    viewModel.openProfile(characterId, name, colorHex, isYou)
                    onOpenProfile()
                },
                jumpToMessageId = jumpToMessageId,
                onJumpHandled = onJumpHandled,
                modifier = Modifier.weight(1f),
            )
            TypingIndicator(state)
            state.replyingTo?.let { reply ->
                ReplyBanner(reply = reply, onCancel = viewModel::cancelReply)
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
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ChannelHeader(
    room: DiscordRoomUi?,
    compact: Boolean,
    pinnedCount: Int,
    hasPendingMedia: Boolean,
    onBack: () -> Unit,
    onPickMedia: () -> Unit,
    onTogglePanel: (SidePanel) -> Unit,
    onOpenDirectMessages: (() -> Unit)?,
    onOpenFriends: () -> Unit,
) {
    val colors = discordColors()
    var topicOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(colors.chat)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (compact) {
                IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Back to conversations" }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = colors.muted)
                }
            }
            when (room?.kind) {
                ROOM_KIND_CHANNEL -> Icon(Icons.Filled.Tag, null, tint = colors.muted, modifier = Modifier.size(22.dp))
                null -> Unit
                else -> StatusAvatar(room.name, room.avatarColorHex, 24.dp, presenceFor(room.name), ring = colors.chat)
            }
            Spacer(Modifier.width(8.dp))
            Row(
                Modifier
                    .weight(1f)
                    .clickable(enabled = room?.topic?.isNotBlank() == true) { topicOpen = !topicOpen },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    room?.name ?: "Welcome",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.header,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!compact && room != null && room.topic.isNotBlank()) {
                    Box(Modifier.padding(horizontal = 10.dp).width(1.dp).height(20.dp).background(colors.divider))
                    Text(
                        room.topic,
                        fontSize = 13.sp,
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (room != null) {
                HeaderIcon(
                    Icons.Outlined.Image,
                    "Send a picture",
                    tint = if (hasPendingMedia) colors.blurple else colors.muted,
                    onClick = onPickMedia,
                )
                Box {
                    HeaderIcon(Icons.Filled.PushPin, "Pinned messages", tint = colors.muted) {
                        onTogglePanel(SidePanel.Pinned)
                    }
                    if (pinnedCount > 0) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = (-4).dp, y = 6.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(colors.red),
                        )
                    }
                }
                if (room.kind != ROOM_KIND_DM) {
                    HeaderIcon(Icons.Filled.People, "Member list", tint = colors.muted) {
                        onTogglePanel(SidePanel.Members)
                    }
                }
                HeaderIcon(Icons.Filled.Search, "Search", tint = colors.muted) { onTogglePanel(SidePanel.Search) }
            }
            if (onOpenDirectMessages != null && !compact) {
                HeaderIcon(Icons.Outlined.MailOutline, "Direct Messages", tint = colors.muted, onClick = onOpenDirectMessages)
            }
            HeaderIcon(Icons.Outlined.PersonAdd, "Friends", tint = colors.muted, onClick = onOpenFriends)
        }
        if (topicOpen && room != null && room.topic.isNotBlank()) {
            Text(
                room.topic,
                fontSize = 13.sp,
                color = colors.text,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.sidebar)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.rail.copy(alpha = 0.5f)))
    }
}

@Composable
private fun HeaderIcon(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ReplyBanner(reply: DiscordReplyPreviewUi, onCancel: () -> Unit) {
    val colors = discordColors()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .background(colors.sidebar)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            androidx.compose.ui.text.buildAnnotatedString {
                append("Replying to ")
                pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = colors.header))
                append(reply.authorName)
                pop()
            },
            fontSize = 13.sp,
            color = colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Cancel reply", tint = colors.muted, modifier = Modifier.size(16.dp))
        }
    }
}

/** Everyone in the codex, as people you can start a direct message with. */
@Composable
private fun DmContactsPane(
    state: DiscordChatUiState,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = discordColors()
    var search by rememberSaveable { mutableStateOf("") }
    val shown = state.dmContacts.filter { it.name.contains(search, true) }
    Column(modifier = modifier.background(colors.chat)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = "Close contacts" }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = colors.muted)
            }
            Text("New Message", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.header)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.rail)
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("To:", fontSize = 14.sp, color = colors.muted)
            androidx.compose.foundation.text.BasicTextField(
                value = search,
                onValueChange = { search = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = colors.text, fontSize = 15.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.blurple),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 10.dp),
            )
        }
        CategoryHeader("Suggested", collapsed = false, onToggle = null)
        if (state.dmContacts.isEmpty()) {
            SidebarHint("No codex characters yet — add some in the Codex.")
        } else if (shown.isEmpty()) {
            SidebarHint("Nobody matches \"$search\".")
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            items(shown, key = { it.characterId }) { contact ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(contact.characterId) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatusAvatar(contact.name, contact.colorHex, 36.dp, presenceFor(contact.name), ring = colors.chat)
                    Column(Modifier.weight(1f)) {
                        Text(
                            contact.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.header,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            contact.name.lowercase().replace(' ', '_'),
                            fontSize = 12.sp,
                            color = colors.muted,
                            maxLines = 1,
                        )
                    }
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .border(2.dp, colors.muted, CircleShape),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyPaneHint(state: DiscordChatUiState) {
    val colors = discordColors()
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(colors.elevated),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (state.selectedServerId == null) Icons.Filled.SportsEsports else Icons.Filled.Tag,
                    contentDescription = null,
                    tint = colors.muted,
                    modifier = Modifier.size(40.dp),
                )
            }
            Text(
                if (state.selectedServerId == null) "No one's around to play with Wumpus." else "Pick a channel",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = colors.header,
            )
            Text(
                if (state.selectedServerId == null) {
                    "Choose a DM, or pick a work's server from the rail to chat about it."
                } else {
                    "Choose a text channel, or open a character's room and @mention them anywhere."
                },
                fontSize = 14.sp,
                color = colors.muted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
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
        title = { Text("Invite a character") },
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
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CharacterAvatar(
                                name = character.name,
                                colorHex = com.ihy2ln.weaverse.core.roleplay.avatarColorHexFor(
                                    character.name,
                                    character.colorHex,
                                ),
                                size = 32.dp,
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
