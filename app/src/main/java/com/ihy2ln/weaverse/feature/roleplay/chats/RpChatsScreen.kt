package com.ihy2ln.weaverse.feature.roleplay.chats

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.ColorPickerDialog
import com.ihy2ln.weaverse.core.ui.EmptyState
import com.ihy2ln.weaverse.core.ui.InkCard
import com.ihy2ln.weaverse.core.ui.Spacing
import com.ihy2ln.weaverse.core.ui.parseHex
import com.ihy2ln.weaverse.core.ui.toHex
import com.ihy2ln.weaverse.data.db.entity.RpChatEntity
import com.ihy2ln.weaverse.data.db.entity.RpCharacterEntity
import com.ihy2ln.weaverse.data.db.entity.RpDisplayMode
import com.ihy2ln.weaverse.data.db.entity.RpMessageEntity
import com.ihy2ln.weaverse.data.db.entity.RpMessageRole
import com.ihy2ln.weaverse.data.db.entity.RpPersonaEntity
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Roleplay Chats screen (spec §8/§10/§11): chat list, then a real streaming conversation.
 * [initialChatId] opens straight into that chat's conversation — set when navigated here from
 * the rail's Sessions tab (Revision 02 §1.4) rather than the in-screen chat list. */
@Composable
fun RpChatsScreen(modifier: Modifier = Modifier, initialChatId: String? = null, viewModel: RpChatsViewModel = hiltViewModel()) {
    val selectedChatId by viewModel.selectedChatId.collectAsState()

    LaunchedEffect(initialChatId) {
        initialChatId?.let { viewModel.selectChat(it) }
    }

    if (selectedChatId == null) {
        ChatListView(modifier = modifier, viewModel = viewModel)
    } else {
        ConversationView(modifier = modifier, viewModel = viewModel, onBack = { viewModel.selectChat(null) })
    }
}

@Composable
private fun ChatListView(modifier: Modifier, viewModel: RpChatsViewModel) {
    val chats by viewModel.chats.collectAsState()
    val characters by viewModel.characters.collectAsState()
    val personas by viewModel.personas.collectAsState()
    var newChatDialogOpen by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().padding(Spacing.lg)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Chats", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = { newChatDialogOpen = true }, enabled = characters.isNotEmpty()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Chat", modifier = Modifier.padding(start = Spacing.xs))
            }
        }

        if (chats.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Forum,
                title = if (characters.isEmpty()) "Create a character first" else "No chats yet",
                subtitle = if (characters.isEmpty()) {
                    "Head to the Characters tab, then start a chat with them."
                } else {
                    "Start a conversation with one of your characters."
                },
                actionLabel = if (characters.isNotEmpty()) "New chat" else null,
                onAction = { newChatDialogOpen = true }.takeIf { characters.isNotEmpty() },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(top = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(items = chats, key = { it.id }) { chat ->
                    ChatRow(
                        chat = chat,
                        characterName = characters.firstOrNull { it.id == chat.characterId }?.name,
                        onClick = { viewModel.selectChat(chat.id) },
                        onDelete = { viewModel.deleteChat(chat) },
                    )
                }
            }
        }
    }

    if (newChatDialogOpen) {
        NewChatDialog(
            characters = characters,
            personas = personas,
            onDismiss = { newChatDialogOpen = false },
            onCreate = { characterId, personaId -> viewModel.createChat(characterId, personaId); newChatDialogOpen = false },
        )
    }
}

@Composable
private fun ChatRow(chat: RpChatEntity, characterName: String?, onClick: () -> Unit, onDelete: () -> Unit) {
    InkCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(chat.title, style = MaterialTheme.typography.titleMedium)
                if (characterName != null) {
                    Text(characterName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete ${chat.title}")
            }
        }
    }
}

@Composable
private fun NewChatDialog(
    characters: List<RpCharacterEntity>,
    personas: List<RpPersonaEntity>,
    onDismiss: () -> Unit,
    onCreate: (characterId: String, personaId: String?) -> Unit,
) {
    var selectedCharacter by remember { mutableStateOf(characters.firstOrNull()) }
    var selectedPersona by remember { mutableStateOf(personas.firstOrNull { it.isDefault } ?: personas.firstOrNull()) }
    var characterMenuOpen by remember { mutableStateOf(false) }
    var personaMenuOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New chat") },
        text = {
            Column {
                Text("Character", style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { characterMenuOpen = true }) { Text(selectedCharacter?.name ?: "Choose a character") }
                DropdownMenu(expanded = characterMenuOpen, onDismissRequest = { characterMenuOpen = false }) {
                    characters.forEach { character ->
                        DropdownMenuItem(text = { Text(character.name) }, onClick = { selectedCharacter = character; characterMenuOpen = false })
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text("Persona", style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { personaMenuOpen = true }) { Text(selectedPersona?.name ?: "Default") }
                DropdownMenu(expanded = personaMenuOpen, onDismissRequest = { personaMenuOpen = false }) {
                    personas.forEach { persona ->
                        DropdownMenuItem(text = { Text(persona.name) }, onClick = { selectedPersona = persona; personaMenuOpen = false })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedCharacter?.let { onCreate(it.id, selectedPersona?.id) } },
                enabled = selectedCharacter != null,
            ) { Text("Start") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ConversationView(modifier: Modifier, viewModel: RpChatsViewModel, onBack: () -> Unit) {
    val chat by viewModel.currentChat.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val streamingText by viewModel.streamingText.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val characters by viewModel.characters.collectAsState()
    val personas by viewModel.personas.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val currentProfile by viewModel.currentProfile.collectAsState()
    val selectedModelId by viewModel.selectedModelId.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    var input by remember { mutableStateOf("") }
    var editTarget by remember { mutableStateOf<RpMessageEntity?>(null) }
    var displayMenuExpanded by remember { mutableStateOf(false) }
    var colorSettingsOpen by remember { mutableStateOf(false) }
    var profileMenuExpanded by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var connectionRowExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current

    val currentChatValue = chat
    val character = characters.firstOrNull { it.id == currentChatValue?.characterId }
    val characterName = character?.name ?: "Character"
    val personaName = personas.firstOrNull { it.id == currentChatValue?.personaId }?.name ?: "You"
    val characterColor = character?.colorHex?.let(::parseHex) ?: MaterialTheme.colorScheme.primary
    val displayMode = currentChatValue?.displayMode ?: RpDisplayMode.Messenger

    LaunchedEffect(messages.size, streamingText) {
        val lastIndex = messages.size + if (streamingText != null) 1 else 0
        if (lastIndex > 0) listState.animateScrollToItem(lastIndex - 1)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back to chats")
            }
            Text(currentChatValue?.title.orEmpty(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            // Connection settings collapse behind one small icon by default — a profile label
            // and a model id can both be long, and showing both expanded at all times crowded
            // out the rest of the header (reported: "taking up less of the ui"). Tap to reveal
            // the compact pickers on the row below.
            IconButton(onClick = { connectionRowExpanded = !connectionRowExpanded }) {
                Icon(
                    Icons.Filled.SmartToy,
                    contentDescription = if (connectionRowExpanded) "Hide connection settings" else "Show connection settings",
                    tint = if (connectionRowExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { displayMenuExpanded = true }) {
                    Icon(Icons.Filled.Tune, contentDescription = "Display mode")
                }
                DropdownMenu(expanded = displayMenuExpanded, onDismissRequest = { displayMenuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Chat messenger") },
                        onClick = { displayMenuExpanded = false; viewModel.setDisplayMode(RpDisplayMode.Messenger) },
                    )
                    DropdownMenuItem(
                        text = { Text("Dungeon master") },
                        onClick = { displayMenuExpanded = false; viewModel.setDisplayMode(RpDisplayMode.DungeonMaster) },
                    )
                    if (displayMode == RpDisplayMode.DungeonMaster) {
                        DropdownMenuItem(
                            text = { Text("Narration/speech/OOC colours…") },
                            leadingIcon = { Icon(Icons.Filled.Palette, contentDescription = null) },
                            onClick = { displayMenuExpanded = false; colorSettingsOpen = true },
                        )
                    }
                }
            }
        }
        if (connectionRowExpanded) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    TextButton(onClick = { profileMenuExpanded = true }, contentPadding = PaddingValues(horizontal = Spacing.xs)) {
                        Text(currentProfile.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(expanded = profileMenuExpanded, onDismissRequest = { profileMenuExpanded = false }) {
                        if (profiles.isEmpty()) {
                            DropdownMenuItem(text = { Text("No connection profiles — add one in Settings") }, onClick = { profileMenuExpanded = false }, enabled = false)
                        }
                        profiles.forEach { profile ->
                            DropdownMenuItem(
                                text = { Text(profile.label) },
                                onClick = { profileMenuExpanded = false; viewModel.selectProfile(profile.id) },
                            )
                        }
                    }
                }
                // A real picker sourced from this profile's own fetched model list (the same
                // call "Test connection" makes) — replaces the previous hardcoded "default"
                // model id, which was never valid for any real provider.
                Box(modifier = Modifier.weight(1f)) {
                    TextButton(onClick = { modelMenuExpanded = true }, enabled = availableModels.isNotEmpty(), contentPadding = PaddingValues(horizontal = Spacing.xs)) {
                        val label = when {
                            selectedModelId.isNotBlank() -> selectedModelId
                            availableModels.isNotEmpty() -> "Auto (${availableModels.first().id})"
                            else -> "Fetching models…"
                        }
                        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(expanded = modelMenuExpanded, onDismissRequest = { modelMenuExpanded = false }) {
                        availableModels.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model.displayName.ifBlank { model.id }) },
                                onClick = { modelMenuExpanded = false; viewModel.selectModelId(model.id) },
                            )
                        }
                    }
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            itemsIndexed(items = messages, key = { _, message -> message.id }) { index, message ->
                val speaker = speakerName(message.role, characterName, personaName)
                if (displayMode == RpDisplayMode.Messenger) {
                    RpMessageBubbleMessenger(
                        message = message,
                        speakerName = speaker,
                        accentColor = characterColor,
                        isLast = index == messages.lastIndex,
                        onCycleSwipe = { direction -> viewModel.cycleSwipe(message.swipeGroupId, direction) },
                        onRegenerate = viewModel::regenerate,
                        onEdit = { editTarget = message },
                        onCopy = { clipboardManager.setText(AnnotatedString(message.plainText)) },
                        onDelete = { viewModel.deleteMessage(message) },
                        observeSwipeGroup = viewModel::observeSwipeGroup,
                    )
                } else {
                    RpMessageProse(
                        message = message,
                        speakerName = speaker,
                        speakerColor = if (message.role == RpMessageRole.Char) characterColor else MaterialTheme.colorScheme.onSurface,
                        narrationColor = currentChatValue?.narrationColorHex?.let(::parseHex) ?: MaterialTheme.colorScheme.tertiary,
                        speechColor = currentChatValue?.speechColorHex?.let(::parseHex) ?: MaterialTheme.colorScheme.onSurface,
                        oocColor = currentChatValue?.oocColorHex?.let(::parseHex) ?: MaterialTheme.colorScheme.onSurfaceVariant,
                        isLast = index == messages.lastIndex,
                        onCycleSwipe = { direction -> viewModel.cycleSwipe(message.swipeGroupId, direction) },
                        onRegenerate = viewModel::regenerate,
                        onEdit = { editTarget = message },
                        onCopy = { clipboardManager.setText(AnnotatedString(message.plainText)) },
                        onDelete = { viewModel.deleteMessage(message) },
                        observeSwipeGroup = viewModel::observeSwipeGroup,
                    )
                }
            }
            streamingText?.let { text ->
                item(key = "streaming") {
                    if (displayMode == RpDisplayMode.Messenger) StreamingBubble(text) else StreamingProse(text)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Say something…") },
                enabled = !isSending,
            )
            IconButton(
                onClick = { val text = input; input = ""; viewModel.sendMessage(text) },
                enabled = !isSending && input.isNotBlank(),
            ) {
                Icon(Icons.Filled.Send, contentDescription = "Send")
            }
        }
    }

    editTarget?.let { message ->
        EditMessageDialog(
            initialText = message.plainText,
            onDismiss = { editTarget = null },
            onSave = { newText -> viewModel.editMessage(message, newText); editTarget = null },
        )
    }

    if (colorSettingsOpen && currentChatValue != null) {
        ProseColorSettingsDialog(
            chat = currentChatValue,
            onSetNarration = viewModel::setNarrationColor,
            onSetSpeech = viewModel::setSpeechColor,
            onSetOoc = viewModel::setOocColor,
            onDismiss = { colorSettingsOpen = false },
        )
    }
}

/** Bubble layout (spec §9.A): user right-aligned in the accent colour, character left-aligned on
 * a neutral surface, each with a small avatar, speaker name, and timestamp. The avatar is a
 * colour-filled initial circle rather than a resolved [com.ihy2ln.weaverse.data.db.entity.MediaEntity]
 * image — avatar *image* rendering for roleplay characters/personas isn't wired into any screen
 * yet (a pre-existing gap, not introduced here); tracked alongside rev02-10b. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RpMessageBubbleMessenger(
    message: RpMessageEntity,
    speakerName: String,
    accentColor: Color,
    isLast: Boolean,
    onCycleSwipe: (Int) -> Unit,
    onRegenerate: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    observeSwipeGroup: (String) -> Flow<List<RpMessageEntity>>,
) {
    val isUser = message.role == RpMessageRole.User
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        if (!isUser) {
            AvatarCircle(speakerName, accentColor)
            Spacer(modifier = Modifier.width(Spacing.xs))
        }
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(speakerName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    formatTimestamp(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.xs),
                )
            }
            var longPressMenuExpanded by remember(message.id) { mutableStateOf(false) }
            Box {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isUser) accentColor else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .padding(top = Spacing.xxs)
                        .combinedClickable(onClick = {}, onLongClick = { longPressMenuExpanded = true }),
                ) {
                    Text(
                        message.plainText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    )
                }
                MessageLongPressMenu(
                    expanded = longPressMenuExpanded,
                    onDismiss = { longPressMenuExpanded = false },
                    onEdit = onEdit,
                    onCopy = onCopy,
                    onDelete = onDelete,
                )
            }
            MessageActionRow(
                message = message,
                isLast = isLast,
                onCycleSwipe = onCycleSwipe,
                onRegenerate = onRegenerate,
                onEdit = onEdit,
                onCopy = onCopy,
                onDelete = onDelete,
                observeSwipeGroup = observeSwipeGroup,
            )
        }
        if (isUser) {
            Spacer(modifier = Modifier.width(Spacing.xs))
            AvatarCircle(speakerName, accentColor)
        }
    }
}

@Composable
private fun AvatarCircle(speakerName: String, color: Color) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            speakerName.firstOrNull()?.uppercase().orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
        )
    }
}

/** Full-width prose layout (spec §9.B): no bubbles, speaker name as a small coloured header,
 * automatic `*narration*`/`"speech"`/`[OOC]` styling via [buildProseAnnotatedString]. Stat/
 * inventory sidebar blocks and a dedicated non-character-card narrator persona aren't built —
 * tracked as a follow-up (rev02-10b); `RpMessageRole.Narrator` messages still render sensibly
 * here using the narration colour, since that role already existed in the schema. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RpMessageProse(
    message: RpMessageEntity,
    speakerName: String,
    speakerColor: Color,
    narrationColor: Color,
    speechColor: Color,
    oocColor: Color,
    isLast: Boolean,
    onCycleSwipe: (Int) -> Unit,
    onRegenerate: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    observeSwipeGroup: (String) -> Flow<List<RpMessageEntity>>,
) {
    var longPressMenuExpanded by remember(message.id) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
        Text(
            speakerName,
            style = MaterialTheme.typography.labelMedium,
            color = if (message.role == RpMessageRole.Narrator) narrationColor else speakerColor,
        )
        Box {
            Text(
                buildProseAnnotatedString(
                    message.plainText,
                    narrationColor = narrationColor,
                    speechColor = speechColor,
                    oocColor = oocColor,
                    bodyColor = MaterialTheme.colorScheme.onSurface,
                ),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .padding(top = Spacing.xxs)
                    .combinedClickable(onClick = {}, onLongClick = { longPressMenuExpanded = true }),
            )
            MessageLongPressMenu(
                expanded = longPressMenuExpanded,
                onDismiss = { longPressMenuExpanded = false },
                onEdit = onEdit,
                onCopy = onCopy,
                onDelete = onDelete,
            )
        }
        Row(modifier = Modifier.padding(top = Spacing.xs)) {
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
            if (message.role == RpMessageRole.Char) {
                val swipeGroup by remember(message.swipeGroupId) { observeSwipeGroup(message.swipeGroupId) }.collectAsState(initial = emptyList())
                if (swipeGroup.size > 1) {
                    IconButton(onClick = { onCycleSwipe(-1) }) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous reply") }
                    val activeIndex = swipeGroup.indexOfFirst { it.isActiveSwipe }.coerceAtLeast(0)
                    Text("${activeIndex + 1}/${swipeGroup.size}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = Spacing.sm))
                    IconButton(onClick = { onCycleSwipe(1) }) { Icon(Icons.Filled.ChevronRight, contentDescription = "Next reply") }
                }
                if (isLast) {
                    IconButton(onClick = onRegenerate) { Icon(Icons.Filled.Refresh, contentDescription = "Regenerate") }
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
        }
    }
}

/** Press-and-hold on a message bubble/passage opens this — Edit/Copy/Delete alongside the
 * already-visible icon row, not instead of it (both trigger the same actions). */
@Composable
private fun MessageLongPressMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Edit") },
            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
            onClick = { onDismiss(); onEdit() },
        )
        DropdownMenuItem(
            text = { Text("Copy") },
            leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
            onClick = { onDismiss(); onCopy() },
        )
        DropdownMenuItem(
            text = { Text("Delete") },
            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            onClick = { onDismiss(); onDelete() },
        )
    }
}

@Composable
private fun MessageActionRow(
    message: RpMessageEntity,
    isLast: Boolean,
    onCycleSwipe: (Int) -> Unit,
    onRegenerate: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    observeSwipeGroup: (String) -> Flow<List<RpMessageEntity>>,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.xxs)) {
        IconButton(onClick = onCopy) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp)) }
        IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp)) }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp)) }
        if (message.role == RpMessageRole.Char) {
            val swipeGroup by remember(message.swipeGroupId) { observeSwipeGroup(message.swipeGroupId) }.collectAsState(initial = emptyList())
            if (swipeGroup.size > 1) {
                IconButton(onClick = { onCycleSwipe(-1) }) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous reply") }
                val activeIndex = swipeGroup.indexOfFirst { it.isActiveSwipe }.coerceAtLeast(0)
                Text("${activeIndex + 1}/${swipeGroup.size}", style = MaterialTheme.typography.labelSmall)
                IconButton(onClick = { onCycleSwipe(1) }) { Icon(Icons.Filled.ChevronRight, contentDescription = "Next reply") }
            }
            if (isLast) {
                IconButton(onClick = onRegenerate) { Icon(Icons.Filled.Refresh, contentDescription = "Regenerate") }
            }
        }
    }
}

@Composable
private fun StreamingBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(
                text.ifEmpty { "…" },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )
        }
    }
}

@Composable
private fun StreamingProse(text: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
        Text("…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text.ifEmpty { "…" }, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun EditMessageDialog(initialText: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit message") },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ProseColorSettingsDialog(
    chat: RpChatEntity,
    onSetNarration: (String?) -> Unit,
    onSetSpeech: (String?) -> Unit,
    onSetOoc: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var pickerTarget by remember { mutableStateOf<ProseColorTarget?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dungeon master colours") },
        text = {
            Column {
                ColorSettingRow("Narration (*asterisks*)", chat.narrationColorHex, MaterialTheme.colorScheme.tertiary) {
                    pickerTarget = ProseColorTarget.Narration
                }
                ColorSettingRow("Speech (\"quotes\")", chat.speechColorHex, MaterialTheme.colorScheme.onSurface) {
                    pickerTarget = ProseColorTarget.Speech
                }
                ColorSettingRow("OOC ([brackets])", chat.oocColorHex, MaterialTheme.colorScheme.onSurfaceVariant) {
                    pickerTarget = ProseColorTarget.Ooc
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )

    pickerTarget?.let { target ->
        val defaultColor = when (target) {
            ProseColorTarget.Narration -> MaterialTheme.colorScheme.tertiary
            ProseColorTarget.Speech -> MaterialTheme.colorScheme.onSurface
            ProseColorTarget.Ooc -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        val currentHex = when (target) {
            ProseColorTarget.Narration -> chat.narrationColorHex
            ProseColorTarget.Speech -> chat.speechColorHex
            ProseColorTarget.Ooc -> chat.oocColorHex
        }
        ColorPickerDialog(
            initialColor = currentHex?.let(::parseHex) ?: defaultColor,
            onDismiss = { pickerTarget = null },
            onColorSelected = { color ->
                when (target) {
                    ProseColorTarget.Narration -> onSetNarration(color.toHex())
                    ProseColorTarget.Speech -> onSetSpeech(color.toHex())
                    ProseColorTarget.Ooc -> onSetOoc(color.toHex())
                }
                pickerTarget = null
            },
        )
    }
}

private enum class ProseColorTarget { Narration, Speech, Ooc }

@Composable
private fun ColorSettingRow(label: String, hex: String?, defaultColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(hex?.let(::parseHex) ?: defaultColor, CircleShape),
        )
        TextButton(onClick = onClick) { Text("Change") }
    }
}

private fun formatTimestamp(epochMillis: Long): String =
    SimpleDateFormat("HH:mm", Locale.ROOT).format(Date(epochMillis))

private fun speakerName(role: RpMessageRole, characterName: String, personaName: String): String = when (role) {
    RpMessageRole.User -> personaName
    RpMessageRole.Char -> characterName
    RpMessageRole.System -> "System"
    RpMessageRole.Narrator -> "Narrator"
}
