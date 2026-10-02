package com.ihy2ln.weaverse.feature.chatting

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.HeadsetOff
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ihy2ln.weaverse.core.ui.util.parseHexColor
import com.ihy2ln.weaverse.feature.roleplay.friends.CharacterAvatar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ------------------------------------------------------------------ shared bits

@Composable
private fun FieldLabel(text: String) {
    Text(text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = discordColors().muted)
}

@Composable
private fun DiscordField(value: String, onChange: (String) -> Unit, hint: String, singleLine: Boolean = true, minHeight: Int = 0) {
    val colors = discordColors()
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        textStyle = TextStyle(color = colors.header, fontSize = 15.sp),
        cursorBrush = SolidColor(colors.blurple),
        modifier = Modifier.fillMaxWidth().heightIn(min = minHeight.dp).clip(RoundedCornerShape(8.dp))
            .background(colors.rail).padding(horizontal = 12.dp, vertical = 11.dp),
        decorationBox = { inner -> if (value.isEmpty()) Text(hint, color = colors.muted, fontSize = 15.sp); inner() },
    )
}

/** The server icon: emoji or monogram on its color, as the rail shows it. */
@Composable
fun ServerIcon(title: String, emoji: String, colorHex: String, size: Int = 48) {
    val colors = discordColors()
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(parseHexColor(colorHex, colors.blurple)),
        contentAlignment = Alignment.Center,
    ) {
        if (emoji.isNotBlank()) Text(emoji, fontSize = (size * .5f).sp)
        else Text(title.trim().take(1).uppercase().ifEmpty { "?" }, fontSize = (size * .4f).sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorSwatches(selected: String, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ROLE_COLORS.forEach { hex ->
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(parseHexColor(hex, Color.Gray)).clickable { onPick(hex) },
                contentAlignment = Alignment.Center,
            ) { if (hex.equals(selected, true)) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun CheckRow(checked: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val colors = discordColors()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.weight(1f)) { content() }
        Box(
            Modifier.size(22.dp).clip(RoundedCornerShape(6.dp))
                .background(if (checked) colors.blurple else Color.Transparent)
                .border(2.dp, if (checked) colors.blurple else colors.muted, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) { if (checked) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
    }
}

/** Pick people from every character; an empty pick means everyone. */
@Composable
private fun PeoplePicker(people: List<DiscordMemberUi>, picked: Set<String>, onChange: (Set<String>) -> Unit) {
    val colors = discordColors()
    var query by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DiscordField(query, { query = it }, "Search people")
        CheckRow(picked.isEmpty(), onClick = { onChange(emptySet()) }) {
            Text("Everyone in the codex", color = colors.header, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        if (people.isEmpty()) Text("Loading people…", color = colors.muted, fontSize = 13.sp)
        people.filter { query.isBlank() || it.name.contains(query, true) }.forEach { person ->
            CheckRow(person.characterId in picked, onClick = {
                onChange(if (person.characterId in picked) picked - person.characterId else picked + person.characterId)
            }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CharacterAvatar(name = person.name, colorHex = person.colorHex, size = 28.dp)
                    Text(person.name, color = colors.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Pick codex entries; an empty pick means the whole codex. */
@Composable
private fun CodexPicker(codex: List<CodexItemUi>, picked: Set<String>, onChange: (Set<String>) -> Unit) {
    val colors = discordColors()
    var query by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CheckRow(picked.isEmpty(), onClick = { onChange(emptySet()) }) {
            Column {
                Text("The whole codex", color = colors.header, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("The AI follows every entry, as it does in your novels.", color = colors.muted, fontSize = 12.sp)
            }
        }
        Text("Or only these entries:", color = colors.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        DiscordField(query, { query = it }, "Search the codex")
        codex.filter { query.isBlank() || it.name.contains(query, true) || it.category.contains(query, true) }
            .groupBy { it.category.ifBlank { "Other" } }
            .forEach { (category, items) ->
                Text(category.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.muted, modifier = Modifier.padding(top = 8.dp))
                items.forEach { item ->
                    CheckRow(item.id in picked, onClick = { onChange(if (item.id in picked) picked - item.id else picked + item.id) }) {
                        Text(item.name, color = colors.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
    }
}

@Composable
private fun FullScreenDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DiscordTheme { Box(Modifier.fillMaxSize().background(discordColors().sidebar)) { content() } }
    }
}

@Composable
private fun DialogTopBar(title: String, onClose: () -> Unit, action: Pair<String, () -> Unit>? = null, actionEnabled: Boolean = true) {
    val colors = discordColors()
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close", tint = colors.header) }
        Text(title, color = colors.header, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        action?.let { (label, onClick) ->
            TextButton(onClick = onClick, enabled = actionEnabled) {
                Text(label, color = if (actionEnabled) colors.blurple else colors.muted, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ------------------------------------------------------------------ create / settings

/** Discord's "Create Your Server": name, icon, who's in it and what its AI knows. */
@Composable
fun CreateServerDialog(
    state: DiscordChatUiState,
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String, colorHex: String, emoji: String, memberIds: List<String>, codexIds: List<String>) -> Unit,
) {
    val colors = discordColors()
    var name by rememberSaveable { mutableStateOf("") }
    var emoji by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableStateOf(ROLE_COLORS[2]) }
    var members by remember { mutableStateOf(emptySet<String>()) }
    var codex by remember { mutableStateOf(emptySet<String>()) }
    FullScreenDialog(onDismiss) {
        Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
            DialogTopBar("Create Your Server", onDismiss, "Create" to {
                onCreate(name, description, color, emoji, members.toList(), codex.toList())
            }, actionEnabled = name.isNotBlank())
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "A server of your own: not tied to a novel or campaign. Its people talk with the same codex-driven AI as every other server.",
                    color = colors.muted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ServerIcon(name, emoji, color, size = 80) }
                FieldLabel("Server name")
                DiscordField(name, { name = it }, "My Server")
                FieldLabel("Icon emoji (optional)")
                DiscordField(emoji, { emoji = it.take(4) }, "🐉")
                FieldLabel("Color")
                ColorSwatches(color) { color = it }
                FieldLabel("What it's about")
                DiscordField(description, { description = it }, "A guild hall for the party between quests", singleLine = false, minHeight = 70)
                FieldLabel("Who's in it")
                PeoplePicker(state.people, members) { members = it }
                FieldLabel("What its AI knows")
                CodexPicker(state.codex, codex) { codex = it }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private enum class SettingsTab(val label: String) { Overview("Overview"), Members("Members"), Codex("Codex"), Roles("Roles") }

/** Server Settings: overview, who's in it, what its AI knows, and roles. */
@Composable
fun ServerSettingsDialog(
    state: DiscordChatUiState,
    server: DiscordServerUi,
    viewModel: DiscordChatViewModel,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit,
) {
    val colors = discordColors()
    var tab by rememberSaveable { mutableStateOf(SettingsTab.Overview) }
    var name by rememberSaveable(server.bookId) { mutableStateOf(server.title) }
    var emoji by rememberSaveable(server.bookId) { mutableStateOf(server.emoji) }
    var description by rememberSaveable(server.bookId) { mutableStateOf(server.description) }
    var color by rememberSaveable(server.bookId) { mutableStateOf(server.colorHex) }
    var members by remember(server.bookId) { mutableStateOf(state.serverMemberIds.toSet()) }
    var codex by remember(server.bookId) { mutableStateOf(state.serverCodexIds.toSet()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var newRole by remember { mutableStateOf("") }
    var newRoleColor by remember { mutableStateOf(ROLE_COLORS[0]) }
    val tabs = SettingsTab.entries.filter { it != SettingsTab.Members || server.isOwn }
    FullScreenDialog(onDismiss) {
        Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
            DialogTopBar("Server Settings", onDismiss, "Save" to {
                viewModel.updateServer(name, description, color, emoji, members.toList(), codex.toList())
                onDismiss()
            })
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tabs.forEach { t ->
                    Text(
                        t.label,
                        color = if (t == tab) Color.White else colors.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (t == tab) colors.blurple else colors.elevated)
                            .clickable { tab = t }.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (tab) {
                    SettingsTab.Overview -> {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ServerIcon(name, emoji, color, size = 80) }
                        FieldLabel("Server name")
                        if (server.isOwn) DiscordField(name, { name = it }, "Server name")
                        else Text("${server.title} — named after its ${if (server.workType == "campaign") "campaign" else "novel"}", color = colors.text, fontSize = 15.sp)
                        FieldLabel("Icon emoji")
                        DiscordField(emoji, { emoji = it.take(4) }, "None: show the first letter")
                        FieldLabel("Color")
                        ColorSwatches(color) { color = it }
                        FieldLabel("Description")
                        DiscordField(description, { description = it }, "What this server is about", singleLine = false, minHeight = 70)
                        if (server.isOwn) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Delete Server",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.red)
                                    .clickable { confirmDelete = true }.padding(12.dp),
                            )
                        }
                    }
                    SettingsTab.Members -> {
                        Text("Everyone picked here gets a room and can be pulled into any channel.", color = colors.muted, fontSize = 13.sp)
                        LaunchedEffect(Unit) { if (state.people.isEmpty()) viewModel.loadPeople() }
                        PeoplePicker(state.people, members) { members = it }
                    }
                    SettingsTab.Codex -> {
                        Text(
                            "Replies in this server obey these codex entries as canon, just like Novel and RPG. New entries made from this server's #codex channel join the list.",
                            color = colors.muted, fontSize = 13.sp,
                        )
                        CodexPicker(state.codex, codex) { codex = it }
                    }
                    SettingsTab.Roles -> {
                        Text("Roles color names in chat and group the member list. The highest role a member has wins.", color = colors.muted, fontSize = 13.sp)
                        state.roles.forEachIndexed { index, role ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.elevated).padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(Modifier.size(14.dp).clip(CircleShape).background(parseHexColor(role.colorHex, colors.blurple)))
                                Column(Modifier.weight(1f)) {
                                    Text(role.name, color = parseHexColor(role.colorHex, colors.header), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                    val count = state.memberRoles.count { (_, ids) -> role.id in ids }
                                    Text("$count member${if (count == 1) "" else "s"}", color = colors.muted, fontSize = 12.sp)
                                }
                                IconButton(onClick = { viewModel.moveRole(role.id, up = true) }, enabled = index > 0) {
                                    Icon(Icons.Filled.KeyboardArrowUp, "Move up", tint = colors.muted)
                                }
                                IconButton(onClick = { viewModel.moveRole(role.id, up = false) }, enabled = index < state.roles.lastIndex) {
                                    Icon(Icons.Filled.KeyboardArrowDown, "Move down", tint = colors.muted)
                                }
                                IconButton(onClick = { viewModel.deleteRole(role.id) }) { Icon(Icons.Filled.Delete, "Delete role", tint = colors.red) }
                            }
                        }
                        FieldLabel("New role")
                        DiscordField(newRole, { newRole = it }, "Moderator")
                        ColorSwatches(newRoleColor) { newRoleColor = it }
                        Text(
                            "Create Role",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(if (newRole.isBlank()) colors.elevated else colors.blurple)
                                .clickable(enabled = newRole.isNotBlank()) { viewModel.addRole(newRole, newRoleColor); newRole = "" }
                                .padding(12.dp),
                        )
                        Text("Give someone a role from their profile: tap their name, then a role.", color = colors.muted, fontSize = 13.sp)
                    }
                }
            }
        }
        if (confirmDelete) AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete '${server.title}'?") },
            text = { Text("Every channel, thread and message in it is deleted. The characters and codex stay. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.deleteServer(server.bookId) { onDeleted() } }) { Text("Delete Server", color = colors.red) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** Create Channel with Discord's channel types. */
@Composable
fun CreateChannelDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    val colors = discordColors()
    var name by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(ROOM_KIND_CHANNEL) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Channel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldLabel("Channel type")
                listOf(
                    Triple(ROOM_KIND_CHANNEL, Icons.Filled.Tag, "Text" to "Send messages, pictures, GIFs and opinions"),
                    Triple(ROOM_KIND_FORUM, Icons.Filled.Forum, "Forum" to "A place for organized posts, each with its own replies"),
                    Triple(ROOM_KIND_VOICE, Icons.AutoMirrored.Filled.VolumeUp, "Voice" to "Hang out and hear everyone talk out loud"),
                ).forEach { (type, icon, labels) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (kind == type) colors.selected else colors.elevated)
                            .clickable { kind = type }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(icon, null, tint = colors.muted)
                        Column(Modifier.weight(1f)) {
                            Text(labels.first, fontWeight = FontWeight.SemiBold, color = colors.header)
                            Text(labels.second, fontSize = 12.sp, color = colors.muted)
                        }
                        Box(Modifier.size(18.dp).clip(CircleShape).border(2.dp, if (kind == type) colors.blurple else colors.muted, CircleShape), contentAlignment = Alignment.Center) {
                            if (kind == type) Box(Modifier.size(9.dp).clip(CircleShape).background(colors.blurple))
                        }
                    }
                }
                FieldLabel("Channel name")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = if (kind == ROOM_KIND_VOICE) it else it.lowercase().replace(' ', '-') },
                    singleLine = true,
                    placeholder = { Text(if (kind == ROOM_KIND_VOICE) "Lounge" else "new-channel") },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, kind) }, enabled = name.isNotBlank()) { Text("Create Channel") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun CreateThreadDialog(message: DiscordMessageUi, onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    val colors = discordColors()
    var name by rememberSaveable { mutableStateOf(message.text.replace('\n', ' ').take(48)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Thread") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${message.authorName}: ${message.text.take(140)}", color = colors.muted, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                FieldLabel("Thread name")
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
                Text("Everyone in the channel comes along. Replies in the thread stay on its topic.", color = colors.muted, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name) }) { Text("Create Thread") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Save a chat message into the codex, as a new entry or added to one, so the AI treats it as canon. */
@Composable
fun SaveToCodexDialog(
    state: DiscordChatUiState,
    message: DiscordMessageUi,
    onDismiss: () -> Unit,
    onNew: (name: String, categoryId: String, text: String) -> Unit,
    onAppend: (entryId: String, text: String) -> Unit,
) {
    val colors = discordColors()
    var text by rememberSaveable { mutableStateOf(message.text) }
    var mode by rememberSaveable { mutableStateOf("append") }
    var name by rememberSaveable { mutableStateOf("") }
    val defaultCategory = state.codexCategories.firstOrNull { it.second.equals("Lore", true) }?.first ?: state.codexCategories.firstOrNull()?.first.orEmpty()
    var categoryId by rememberSaveable { mutableStateOf(defaultCategory) }
    // The speaker's own entry is the likeliest home for what they just said.
    val speakerEntry = state.codex.firstOrNull { it.name.equals(message.authorName, true) }
    var target by rememberSaveable { mutableStateOf(speakerEntry?.id) }
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save to Codex") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The AI treats codex entries as canon in every server, novel and campaign.", color = colors.muted, fontSize = 12.sp)
                OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 2, maxLines = 6, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("append" to "Add to an entry", "new" to "New entry").forEach { (key, label) ->
                        Text(label, fontSize = 13.sp, color = if (mode == key) Color.White else colors.text,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (mode == key) colors.blurple else colors.elevated)
                                .clickable { mode = key }.padding(horizontal = 12.dp, vertical = 7.dp))
                    }
                }
                if (mode == "new") {
                    OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, placeholder = { Text("Entry name") }, modifier = Modifier.fillMaxWidth())
                    FieldLabel("Category")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.codexCategories.forEach { (id, label) ->
                            Text(label, fontSize = 13.sp, color = if (categoryId == id) Color.White else colors.text,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (categoryId == id) colors.blurple else colors.elevated)
                                    .clickable { categoryId = id }.padding(horizontal = 12.dp, vertical = 7.dp))
                        }
                    }
                } else {
                    OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, placeholder = { Text("Find an entry") }, modifier = Modifier.fillMaxWidth())
                    val options = state.codex.filter { query.isBlank() || it.name.contains(query, true) }
                        .sortedByDescending { it.id == speakerEntry?.id }.take(12)
                    options.forEach { item ->
                        CheckRow(target == item.id, onClick = { target = item.id }) {
                            Column {
                                Text(item.name, color = colors.header, fontSize = 14.sp)
                                Text(item.category, color = colors.muted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val ready = text.isNotBlank() && if (mode == "new") name.isNotBlank() && categoryId.isNotBlank() else target != null
            TextButton(onClick = {
                if (mode == "new") onNew(name, categoryId, text) else target?.let { onAppend(it, text) }
            }, enabled = ready) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ------------------------------------------------------------------ channel panes

/** A forum channel: its posts as cards, and New Post. */
@Composable
fun ForumPane(
    state: DiscordChatUiState,
    room: DiscordRoomUi,
    onOpenPost: (String) -> Unit,
    onNewPost: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = discordColors()
    var composing by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    val posts = state.rooms.filter { it.parentRoomId == room.chatId }
        .filter { query.isBlank() || it.name.contains(query, true) }
        .sortedByDescending { it.lastMessageAt }
    val format = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    Column(modifier.background(colors.chat)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { DiscordField(query, { query = it }, "Search or create a post…") }
            Text(
                "New Post",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(colors.blurple)
                    .clickable { composing = true; if (title.isBlank()) title = query }.padding(horizontal = 14.dp, vertical = 11.dp),
            )
        }
        if (composing) {
            Column(
                Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.elevated).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DiscordField(title, { title = it }, "Post title")
                DiscordField(body, { body = it }, "Write your post — everyone here can reply", singleLine = false, minHeight = 90)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
                    TextButton(onClick = { composing = false }) { Text("Cancel", color = colors.muted) }
                    TextButton(onClick = { onNewPost(title, body); composing = false; title = ""; body = ""; query = "" }, enabled = title.isNotBlank() || body.isNotBlank()) {
                        Text("Post", color = colors.blurple, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (posts.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Forum, null, tint = colors.muted, modifier = Modifier.size(56.dp))
                    Text("No posts yet", color = colors.header, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    Text(room.topic.ifBlank { "Start the first post in #${room.name}." }, color = colors.muted, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
            items(posts, key = { it.chatId }) { post ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.elevated).clickable { onOpenPost(post.chatId) }.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(post.name, color = colors.header, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Forum, null, tint = colors.muted, modifier = Modifier.size(14.dp))
                        Text("Last activity ${format.format(Date(post.lastMessageAt))}", color = colors.muted, fontSize = 12.sp)
                        if (post.unread > 0) UnreadBadge(post.unread)
                    }
                }
            }
        }
    }
}

/** A voice channel: everyone's tiles, who's talking, and the captions of what was said. */
@Composable
fun VoiceStage(state: DiscordChatUiState, room: DiscordRoomUi, viewModel: DiscordChatViewModel, modifier: Modifier = Modifier) {
    val colors = discordColors()
    val connected = state.voice.roomId == room.chatId
    Column(modifier.background(Color(0xFF111214))) {
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val tiles = listOf(Triple<String?, String, String>(null, state.personaName, com.ihy2ln.weaverse.core.roleplay.avatarColorHexFor(state.personaName, null))) +
                state.members.map { Triple(it.characterId, it.name, it.colorHex) }
            tiles.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (id, name, colorHex) ->
                        val speaking = connected && id != null && state.voice.speakingCharacterId == id
                        val pulse by animateFloatAsState(if (speaking) 1.06f else 1f, label = "speaking")
                        Column(
                            Modifier.weight(1f).height(130.dp).clip(RoundedCornerShape(12.dp))
                                .background(parseHexColor(colorHex, colors.blurple).copy(alpha = .35f))
                                .border(if (speaking) 3.dp else 0.dp, if (speaking) colors.green else Color.Transparent, RoundedCornerShape(12.dp)),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Box(Modifier.scale(pulse)) { CharacterAvatar(name = name, colorHex = colorHex, size = 64.dp) }
                            Text(
                                if (id == null) "$name (you)" else name,
                                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp, start = 6.dp, end = 6.dp),
                            )
                            if (!connected && id == null) Text("Not connected", color = colors.muted, fontSize = 11.sp)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            val captions = state.messages.filter { !it.isSystem }.takeLast(8)
            if (captions.isNotEmpty()) {
                Text("CAPTIONS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.muted, modifier = Modifier.padding(top = 8.dp))
                captions.forEach { m ->
                    Text(
                        buildString { append(m.authorName); append(": "); append(m.text) },
                        color = if (connected && m.authorCharacterId != null && m.authorCharacterId == state.voice.speakingCharacterId) Color.White else colors.text,
                        fontSize = 14.sp,
                    )
                }
            }
            if (state.isStreaming) Text("Someone's about to speak…", color = colors.muted, fontSize = 13.sp)
        }
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!connected) {
                Text(
                    "Join Voice",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(colors.green).clickable { viewModel.joinVoice() }
                        .padding(horizontal = 28.dp, vertical = 12.dp),
                )
            } else {
                VoiceButton(if (state.voice.deafened) Icons.Filled.HeadsetOff else Icons.Filled.Headset, "Deafen", if (state.voice.deafened) colors.red else colors.elevated) {
                    viewModel.toggleDeafen()
                }
                VoiceButton(Icons.Filled.CallEnd, "Disconnect", colors.red) { viewModel.leaveVoice() }
            }
        }
    }
}

@Composable
private fun VoiceButton(icon: ImageVector, description: String, background: Color, onClick: () -> Unit) {
    Box(Modifier.size(52.dp).clip(CircleShape).background(background).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, description, tint = Color.White)
    }
}

/** The server's #codex channel: the canon its AI follows, to browse, open and add to. */
@Composable
fun CodexChannelPane(
    state: DiscordChatUiState,
    viewModel: DiscordChatViewModel,
    onOpenEntry: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = discordColors()
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    val defaultCategory = state.codexCategories.firstOrNull { it.second.equals("Lore", true) }?.first ?: state.codexCategories.firstOrNull()?.first.orEmpty()
    var categoryId by rememberSaveable { mutableStateOf(defaultCategory) }
    val entries = state.serverCodex.filter { query.isBlank() || it.name.contains(query, true) || it.text.contains(query, true) }
    LazyColumn(modifier.background(colors.chat), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = colors.header, modifier = Modifier.size(32.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${state.selectedServer?.title ?: "Server"} codex", color = colors.header, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (state.serverCodexIds.isEmpty()) "Everyone here follows the whole codex · ${state.codex.size} entries"
                            else "This server's AI follows these ${state.serverCodex.size} entries",
                            color = colors.muted, fontSize = 13.sp,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { DiscordField(query, { query = it }, "Search the codex") }
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(colors.blurple).clickable { adding = !adding }, contentAlignment = Alignment.Center) {
                        Icon(if (adding) Icons.Filled.Close else Icons.Filled.Add, "New entry", tint = Color.White)
                    }
                }
                if (adding) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.elevated).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DiscordField(name, { name = it }, "Entry name")
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.codexCategories.forEach { (id, label) ->
                                Text(label, fontSize = 13.sp, color = if (categoryId == id) Color.White else colors.text,
                                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (categoryId == id) colors.blurple else colors.rail)
                                        .clickable { categoryId = id }.padding(horizontal = 12.dp, vertical = 7.dp))
                            }
                        }
                        DiscordField(text, { text = it }, "What's true about it", singleLine = false, minHeight = 80)
                        Text(
                            "Add to Codex",
                            color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                .background(if (name.isBlank() || categoryId.isBlank()) colors.rail else colors.blurple)
                                .clickable(enabled = name.isNotBlank() && categoryId.isNotBlank()) {
                                    viewModel.createCodexEntry(name, categoryId, text)
                                    name = ""; text = ""; adding = false
                                }.padding(10.dp),
                        )
                    }
                }
            }
        }
        if (entries.isEmpty()) item { Text("Nothing here yet.", color = colors.muted, fontSize = 14.sp, modifier = Modifier.padding(16.dp)) }
        entries.groupBy { it.category.ifBlank { "Other" } }.forEach { (category, list) ->
            item(key = "cat-$category") {
                Text("${category.uppercase()} — ${list.size}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.muted, modifier = Modifier.padding(top = 10.dp, start = 4.dp))
            }
            items(list, key = { it.id }) { item ->
                val open = expanded == item.id
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.elevated)
                        .clickable { expanded = if (open) null else item.id }.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(parseHexColor(item.colorHex ?: "", colors.blurple)))
                        Text(item.name, color = colors.header, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, tint = colors.muted)
                    }
                    Text(item.text.ifBlank { "No details yet." }, color = colors.text, fontSize = 14.sp, maxLines = if (open) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
                    if (open && onOpenEntry != null) {
                        Text("Open in Codex", color = colors.link, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { onOpenEntry(item.id) }.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}

/** Search every room in the server, Discord-style. */
@Composable
fun ServerSearchPanel(
    state: DiscordChatUiState,
    viewModel: DiscordChatViewModel,
    onOpen: (roomId: String, messageId: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = discordColors()
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query) {
        kotlinx.coroutines.delay(250)
        viewModel.searchServer(query)
    }
    Column(modifier.background(colors.sidebar)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close search", tint = colors.header) }
            Text("Search ${state.selectedServer?.title.orEmpty()}", color = colors.header, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(colors.rail).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = colors.text, fontSize = 15.sp),
                cursorBrush = SolidColor(colors.blurple),
                modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                decorationBox = { inner -> if (query.isEmpty()) Text("Search every channel", color = colors.muted, fontSize = 15.sp); inner() },
            )
            Icon(Icons.Filled.Search, null, tint = colors.muted, modifier = Modifier.size(18.dp))
        }
        if (query.isNotBlank()) Text("${state.serverSearch.size} RESULTS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.muted, modifier = Modifier.padding(16.dp, 10.dp))
        LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.serverSearch, key = { it.message.id }) { hit ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colors.chat).clickable { onOpen(hit.roomId, hit.message.id) }.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("# ${hit.roomName}", color = colors.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CharacterAvatar(name = hit.message.authorName, colorHex = hit.message.authorColorHex, size = 24.dp)
                        Text(hit.message.authorName, color = parseHexColor(hit.message.authorColorHex, colors.header), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(viewModel.timestampFull(hit.message.createdAt), color = colors.muted, fontSize = 11.sp)
                    }
                    Text(hit.message.text, color = colors.text, fontSize = 14.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Role chips on a profile: tap to give or take a role. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoleToggles(roles: List<ServerRole>, assigned: List<String>, onToggle: (String) -> Unit) {
    val colors = discordColors()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        roles.forEach { role ->
            val has = role.id in assigned
            Row(
                Modifier.clip(RoundedCornerShape(4.dp)).background(if (has) colors.elevated else Color.Transparent)
                    .border(1.dp, if (has) Color.Transparent else colors.divider, RoundedCornerShape(4.dp))
                    .clickable { onToggle(role.id) }.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(parseHexColor(role.colorHex, colors.blurple)))
                Text(role.name, fontSize = 12.sp, color = if (has) colors.header else colors.muted)
                if (!has) Icon(Icons.Filled.Add, null, tint = colors.muted, modifier = Modifier.size(12.dp))
            }
        }
    }
}

/** Sidebar icon for a room kind. */
fun roomKindIcon(kind: String): ImageVector? = when (kind) {
    ROOM_KIND_CHANNEL -> Icons.Filled.Tag
    ROOM_KIND_FORUM -> Icons.Filled.Forum
    ROOM_KIND_VOICE -> Icons.AutoMirrored.Filled.VolumeUp
    ROOM_KIND_CODEX -> Icons.AutoMirrored.Filled.MenuBook
    ROOM_KIND_THREAD -> Icons.Filled.Tag
    else -> null
}
