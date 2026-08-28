package com.ihy2ln.weaverse.feature.roleplay.party

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.data.db.entities.RpEquipSlot
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusSm
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.alwaysScrollEndSpacer
import java.io.File

/**
 * What the party is carrying, grouped by who carries it. Items are plain
 * name/quantity/notes — deliberately system-agnostic, with no rules behind them.
 */
@Composable
fun InventoryScreen(viewModel: InventoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val tokens = inkTokens()
    var addingForCharacterId by remember { mutableStateOf<String?>(null) }
    var equippingFor by remember { mutableStateOf<Pair<String, RpEquipSlot>?>(null) }
    var openCharacterId by remember { mutableStateOf<String?>(null) }
    // Each group collapses independently; all open to start.
    var collapsedGroups by rememberSaveable { mutableStateOf(setOf<String>()) }
    var draftName by remember { mutableStateOf("") }
    var draftQty by remember { mutableStateOf("1") }
    var imageTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val target = imageTarget
        imageTarget = null
        if (uri != null && target != null) viewModel.setItemImage(target.first, target.second, uri)
    }

    if (!state.loading && state.carriers.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(InkSpacing.lg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No one to carry anything yet. Add a character in Roster first.",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.secondaryText,
            )
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        state.carriers.groupBy { it.kind }.forEach { (kind, carriers) ->
            val groupOpen = kind.name !in collapsedGroups
            item(key = "group-${kind.name}") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            collapsedGroups = if (groupOpen) {
                                collapsedGroups + kind.name
                            } else {
                                collapsedGroups - kind.name
                            }
                        }
                        .padding(
                            start = InkSpacing.md,
                            end = InkSpacing.lg,
                            top = InkSpacing.md,
                            bottom = InkSpacing.xs,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (groupOpen) "⌄" else "›",
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.secondaryText,
                        modifier = Modifier.padding(end = InkSpacing.xs),
                    )
                    Text(
                        kind.label.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = tokens.primaryText,
                    )
                    Text(
                        "${carriers.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.secondaryText,
                        modifier = Modifier.padding(start = InkSpacing.sm),
                    )
                }
            }
            if (!groupOpen) return@forEach
            carriers.forEach { carrier ->
            item(key = "hdr-${carrier.characterId}") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = InkSpacing.lg,
                            end = InkSpacing.lg,
                            top = InkSpacing.lg,
                            bottom = InkSpacing.sm,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        carrier.name.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = if (openCharacterId == carrier.characterId) {
                            tokens.activePill
                        } else {
                            tokens.secondaryText
                        },
                        modifier = Modifier.clickable {
                            openCharacterId = if (openCharacterId == carrier.characterId) {
                                null
                            } else {
                                carrier.characterId
                            }
                        },
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = InkSpacing.sm)
                            .weight(1f)
                            .height(1.dp)
                            .background(tokens.hairline),
                    )
                    InkTextButton(
                        label = "+ Item",
                        onClick = {
                            draftName = ""
                            draftQty = "1"
                            addingForCharacterId = carrier.characterId
                        },
                    )
                }
            }
            if (openCharacterId == carrier.characterId) {
                item(key = "equip-${carrier.characterId}") {
                    EquipmentPlate(
                        equipment = carrier.equipment,
                        items = carrier.items,
                        imagePaths = carrier.itemImagePaths,
                        onSlotClick = { slot -> equippingFor = carrier.characterId to slot },
                    )
                }
            }
            if (carrier.items.isEmpty()) {
                item(key = "empty-${carrier.characterId}") {
                    Text(
                        "Carrying nothing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.secondaryText,
                        modifier = Modifier.padding(
                            horizontal = InkSpacing.lg,
                            vertical = InkSpacing.xs,
                        ),
                    )
                }
            }
            items(carrier.items, key = { "${carrier.characterId}-${it.id}" }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = InkSpacing.lg, vertical = InkSpacing.xs)
                        .clip(RoundedCornerShape(inkRadiusSm()))
                        .background(tokens.panel)
                        .border(1.dp, tokens.hairline, RoundedCornerShape(inkRadiusSm()))
                        .padding(InkSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm),
                ) {
                    val imagePath = carrier.itemImagePaths[item.id].orEmpty()
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(inkRadiusSm()))
                            .background(tokens.hover)
                            .border(1.dp, tokens.hairline, RoundedCornerShape(inkRadiusSm()))
                            .clickable {
                                imageTarget = carrier.characterId to item.id
                                imagePicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (imagePath.isNotBlank()) {
                            AsyncImage(
                                model = File(imagePath),
                                contentDescription = "${item.name} picture",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text("＋\nimage", style = MaterialTheme.typography.labelSmall, color = tokens.activePill)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (item.notes.isNotBlank()) {
                            Text(
                                item.notes,
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.secondaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Text(
                        "×${item.quantity}",
                        style = MaterialTheme.typography.labelMedium,
                        color = tokens.secondaryText,
                    )
                    InkTextButton(
                        label = "−",
                        onClick = { viewModel.removeItem(carrier.characterId, item.id) },
                    )
                }
            }
            }
        }
        alwaysScrollEndSpacer()
    }

    equippingFor?.let { (characterId, slot) ->
        val carried = state.carriers.firstOrNull { it.characterId == characterId }?.items.orEmpty()
        AlertDialog(
            onDismissRequest = { equippingFor = null },
            title = { Text("Equip ${slot.label.lowercase()}") },
            text = {
                if (carried.isEmpty()) {
                    Text(
                        "Nothing to equip yet — add an item with + Item first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.secondaryText,
                    )
                } else {
                    Column {
                        carried.forEach { item ->
                            Text(
                                item.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setEquipment(characterId, slot, item.name)
                                        equippingFor = null
                                    }
                                    .padding(vertical = InkSpacing.sm),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setEquipment(characterId, slot, "")
                    equippingFor = null
                }) { Text("Clear slot") }
            },
            dismissButton = {
                TextButton(onClick = { equippingFor = null }) { Text("Cancel") }
            },
        )
    }

    addingForCharacterId?.let { characterId ->
        AlertDialog(
            onDismissRequest = { addingForCharacterId = null },
            title = { Text("Add item") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
                    OutlinedTextField(
                        value = draftName,
                        onValueChange = { draftName = it },
                        label = { Text("Item") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = draftQty,
                        onValueChange = { draftQty = it.filter(Char::isDigit).take(4) },
                        label = { Text("Quantity") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = draftName.isNotBlank(),
                    onClick = {
                        viewModel.addItem(
                            characterId = characterId,
                            name = draftName,
                            quantity = draftQty.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                        )
                        addingForCharacterId = null
                    },
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { addingForCharacterId = null }) { Text("Cancel") }
            },
        )
    }
}

/**
 * The equipped-gear plate: one small square per slot, labelled and showing what
 * is in it. Tapping a square picks from what the character is carrying.
 */
@Composable
private fun EquipmentPlate(
    equipment: Map<String, String>,
    items: List<com.ihy2ln.weaverse.data.db.entities.RpItem>,
    imagePaths: Map<String, String>,
    onSlotClick: (RpEquipSlot) -> Unit,
) {
    val tokens = inkTokens()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = InkSpacing.lg, vertical = InkSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.xs),
    ) {
        RpEquipSlot.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
                row.forEach { slot ->
                    val equipped = equipment[slot.name].orEmpty()
                    val equippedItem = items.firstOrNull { it.name.equals(equipped, ignoreCase = true) }
                    val imagePath = equippedItem?.let { imagePaths[it.id] }.orEmpty()
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(inkRadiusSm()))
                            .background(tokens.panel)
                            .border(
                                1.dp,
                                if (equipped.isBlank()) tokens.hairline else tokens.activePill,
                                RoundedCornerShape(inkRadiusSm()),
                            )
                            .clickable { onSlotClick(slot) }
                            .padding(InkSpacing.sm),
                    ) {
                        Text(
                            slot.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 1.sp,
                            color = tokens.secondaryText,
                        )
                        if (imagePath.isNotBlank()) {
                            AsyncImage(
                                model = File(imagePath),
                                contentDescription = "$equipped equipment",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .padding(vertical = 3.dp)
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(inkRadiusSm())),
                            )
                        }
                        Text(
                            equipped.ifBlank { "—" },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (equipped.isBlank()) tokens.secondaryText else tokens.primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // Keep the last row aligned when the slot count is not a multiple of three.
                repeat(3 - row.size) { Box(modifier = Modifier.weight(1f)) }
            }
        }
    }
}
