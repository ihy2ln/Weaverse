package com.ihy2ln.weaverse.feature.roleplay.party

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.data.db.entities.RpEquipSlot
import com.ihy2ln.weaverse.data.db.entities.RpItem
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
fun InventoryScreen(
    initialCarrierId: String? = null,
    viewModel: InventoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val tokens = inkTokens()
    var addingForCharacterId by remember { mutableStateOf<String?>(null) }
    var equippingFor by remember { mutableStateOf<Pair<String, RpEquipSlot>?>(null) }
    var openCharacterId by rememberSaveable { mutableStateOf<String?>(initialCarrierId) }
    // Keep the writer and wider cast compact on entry; the immediate team stays visible.
    var collapsedGroups by rememberSaveable {
        mutableStateOf(
            setOf(
                CarrierKind.You.name,
                CarrierKind.Npc.name,
                CarrierKind.Enemy.name,
                CarrierKind.Other.name,
            ),
        )
    }
    var collapsedBackpacks by rememberSaveable { mutableStateOf(setOf<String>()) }
    val inventoryListState = rememberLazyListState()
    var draftName by remember { mutableStateOf("") }
    var draftQty by remember { mutableStateOf("1") }
    var draftNotes by remember { mutableStateOf("") }
    var draftWeight by remember { mutableStateOf("0") }
    var draftCost by remember { mutableStateOf("0") }
    var draftTags by remember { mutableStateOf("") }
    var draftAttuned by remember { mutableStateOf(false) }
    var draftSlotSize by remember { mutableStateOf("1") }
    var draftBackpackCapacity by remember { mutableStateOf("12") }
    var draftTemplate by remember { mutableStateOf(InventoryItemTemplate.PackItem) }
    var equipAfterAdding by remember { mutableStateOf<RpEquipSlot?>(null) }
    var draftImageUri by remember { mutableStateOf<Uri?>(null) }
    var imageMenuTarget by remember { mutableStateOf<Pair<String, RpItem>?>(null) }
    var imageTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var inventoryQuery by rememberSaveable { mutableStateOf("") }
    var inventoryFilterName by rememberSaveable { mutableStateOf(InventoryFilter.All.name) }
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val target = imageTarget
        imageTarget = null
        if (uri != null && target != null) viewModel.setItemImage(target.first, target.second, uri)
    }
    val addImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) draftImageUri = uri }
    LaunchedEffect(initialCarrierId) {
        if (!initialCarrierId.isNullOrBlank()) openCharacterId = initialCarrierId
    }
    LaunchedEffect(state.loading) {
        if (!state.loading) inventoryListState.scrollToItem(0)
    }
    LaunchedEffect(initialCarrierId, state.carriers) {
        if (!initialCarrierId.isNullOrBlank()) {
            state.carriers.firstOrNull { it.characterId == initialCarrierId }?.let { carrier ->
                collapsedGroups = collapsedGroups - carrier.kind.name
            }
        }
    }

    fun beginAdding(
        carrierId: String,
        template: InventoryItemTemplate = InventoryItemTemplate.PackItem,
        equipSlot: RpEquipSlot? = null,
    ) {
        draftName = ""
        draftQty = "1"
        draftNotes = ""
        draftWeight = "0"
        draftCost = "0"
        draftTags = ""
        draftAttuned = false
        draftSlotSize = "1"
        draftBackpackCapacity = template.defaultBackpackCapacity.coerceAtLeast(12).toString()
        draftTemplate = template
        equipAfterAdding = equipSlot
        draftImageUri = null
        addingForCharacterId = carrierId
    }

    fun manageItemImage(carrierId: String, item: RpItem, imagePath: String) {
        if (imagePath.isBlank()) {
            imageTarget = carrierId to item.id
            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } else {
            imageMenuTarget = carrierId to item
        }
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

    LazyColumn(state = inventoryListState, modifier = Modifier.fillMaxSize()) {
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
                        onClick = { beginAdding(carrier.characterId) },
                    )
                }
            }
            if (openCharacterId == carrier.characterId) {
                item(key = "ledger-${carrier.characterId}") {
                    InventoryLedger(
                        carrier = carrier,
                        query = inventoryQuery,
                        filter = InventoryFilter.valueOf(inventoryFilterName),
                        onQueryChange = { inventoryQuery = it },
                        onFilterChange = { inventoryFilterName = it.name },
                        onToggleActive = { itemId ->
                            viewModel.toggleItemActive(carrier.characterId, itemId)
                        },
                        onImageClick = { item ->
                            manageItemImage(
                                carrier.characterId,
                                item,
                                carrier.itemImagePaths[item.id].orEmpty(),
                            )
                        },
                    )
                }
                item(key = "equip-${carrier.characterId}") {
                    EquipmentPlate(
                        equipment = carrier.equipment,
                        items = carrier.items,
                        imagePaths = carrier.itemImagePaths,
                        onSlotClick = { slot -> equippingFor = carrier.characterId to slot },
                        onAddForSlot = { slot ->
                            beginAdding(
                                carrier.characterId,
                                inventoryTemplateFor(slot),
                                slot,
                            )
                        },
                        onItemImageClick = { item ->
                            manageItemImage(
                                carrier.characterId,
                                item,
                                carrier.itemImagePaths[item.id].orEmpty(),
                            )
                        },
                    )
                }
            }
            item(key = "backpack-${carrier.characterId}") {
                BackpackPanel(
                    carrier = carrier,
                    collapsed = carrier.characterId in collapsedBackpacks,
                    onToggle = {
                        collapsedBackpacks = if (carrier.characterId in collapsedBackpacks) {
                            collapsedBackpacks - carrier.characterId
                        } else {
                            collapsedBackpacks + carrier.characterId
                        }
                    },
                    onImageClick = { item ->
                        manageItemImage(
                            carrier.characterId,
                            item,
                            carrier.itemImagePaths[item.id].orEmpty(),
                        )
                    },
                    onRemove = { itemId -> viewModel.removeItem(carrier.characterId, itemId) },
                )
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(inkRadiusSm()))
                                .background(tokens.hover)
                                .border(1.dp, tokens.hairline, RoundedCornerShape(inkRadiusSm()))
                                .clickable {
                                    addImagePicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (draftImageUri != null) {
                                AsyncImage(
                                    model = draftImageUri,
                                    contentDescription = "New item picture",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Text("＋\nPicture", style = MaterialTheme.typography.labelSmall, color = tokens.activePill)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (draftImageUri == null) "Add item picture" else "Picture selected",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
                                Text(
                                    if (draftImageUri == null) "Choose" else "Replace",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = tokens.activePill,
                                    modifier = Modifier.clickable {
                                        addImagePicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                        )
                                    },
                                )
                                if (draftImageUri != null) {
                                    Text(
                                        "Remove",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.clickable { draftImageUri = null },
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        "ITEM TEMPLATE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = tokens.secondaryText,
                    )
                    InventoryItemTemplate.entries.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
                            row.forEach { template ->
                                Text(
                                    template.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (draftTemplate == template) {
                                        tokens.activePillLabel
                                    } else {
                                        tokens.primaryText
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(inkRadiusSm()))
                                        .background(
                                            if (draftTemplate == template) tokens.activePill else tokens.panel,
                                        )
                                        .border(1.dp, tokens.hairline, RoundedCornerShape(inkRadiusSm()))
                                        .clickable {
                                            draftTemplate = template
                                            equipAfterAdding = template.equipmentSlot
                                            if (template == InventoryItemTemplate.Backpack &&
                                                draftBackpackCapacity.toIntOrNull() == null
                                            ) {
                                                draftBackpackCapacity = "12"
                                            }
                                        }
                                        .padding(InkSpacing.xs),
                                )
                            }
                            repeat(2 - row.size) { Box(modifier = Modifier.weight(1f)) }
                        }
                    }
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
                    Row(horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
                        OutlinedTextField(
                            value = draftWeight,
                            onValueChange = { draftWeight = it.filter { char -> char.isDigit() || char == '.' }.take(8) },
                            label = { Text("Weight (lb)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = draftCost,
                            onValueChange = { draftCost = it.filter { char -> char.isDigit() || char == '.' }.take(8) },
                            label = { Text("Cost (gp)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        value = draftTags,
                        onValueChange = { draftTags = it },
                        label = { Text("Tags") },
                        supportingText = { Text("Examples: Combat, Utility, Consumable") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = draftNotes,
                        onValueChange = { draftNotes = it },
                        label = { Text("Notes") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        if (draftAttuned) "✓ Attunement required" else "□ No attunement",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (draftAttuned) tokens.activePill else tokens.secondaryText,
                        modifier = Modifier
                            .clickable { draftAttuned = !draftAttuned }
                            .padding(vertical = InkSpacing.xs),
                    )
                    OutlinedTextField(
                        value = draftSlotSize,
                        onValueChange = { draftSlotSize = it.filter(Char::isDigit).take(2) },
                        label = { Text("Backpack slots used per item") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (draftTemplate == InventoryItemTemplate.Backpack) {
                        OutlinedTextField(
                            value = draftBackpackCapacity,
                            onValueChange = { draftBackpackCapacity = it.filter(Char::isDigit).take(3) },
                            label = { Text("Backpack capacity") },
                            supportingText = { Text("How many inventory slots this backpack opens") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
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
                            notes = draftNotes,
                            weightLb = draftWeight.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                            costGp = draftCost.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                            tags = draftTags,
                            attuned = draftAttuned,
                            template = draftTemplate,
                            slotSize = draftSlotSize.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                            backpackCapacity = draftBackpackCapacity.toIntOrNull()
                                ?: draftTemplate.defaultBackpackCapacity,
                            equipAfterAdding = equipAfterAdding,
                            imageUri = draftImageUri,
                        )
                        draftImageUri = null
                        addingForCharacterId = null
                    },
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { addingForCharacterId = null }) { Text("Cancel") }
            },
        )
    }

    imageMenuTarget?.let { (carrierId, item) ->
        val imagePath = state.carriers
            .firstOrNull { it.characterId == carrierId }
            ?.itemImagePaths
            ?.get(item.id)
            .orEmpty()
        AlertDialog(
            onDismissRequest = { imageMenuTarget = null },
            title = { Text("${item.name} picture") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
                    if (imagePath.isNotBlank()) {
                        AsyncImage(
                            model = File(imagePath),
                            contentDescription = "${item.name} picture",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(inkRadiusSm())),
                        )
                    }
                    Text("This picture stays linked to the item everywhere it is carried or equipped.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    imageMenuTarget = null
                    imageTarget = carrierId to item.id
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Replace") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.removeItemImage(carrierId, item.id)
                    imageMenuTarget = null
                }) { Text("Remove picture", color = MaterialTheme.colorScheme.error) }
            },
        )
    }
}

/** Compact tabletop ledger layered above the existing visual gear and backpack views. */
@Composable
private fun InventoryLedger(
    carrier: CarrierUi,
    query: String,
    filter: InventoryFilter,
    onQueryChange: (String) -> Unit,
    onFilterChange: (InventoryFilter) -> Unit,
    onToggleActive: (String) -> Unit,
    onImageClick: (RpItem) -> Unit,
) {
    val tokens = inkTokens()
    val shown = filteredInventory(carrier.items, query, filter)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = InkSpacing.lg, vertical = InkSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.xs),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "WEIGHT CARRIED",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                    color = tokens.secondaryText,
                )
                Text(
                    "${"%.1f".format(inventoryWeight(carrier.items))} lb",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                "${carrier.items.sumOf { it.quantity.coerceAtLeast(1) }} items",
                style = MaterialTheme.typography.labelMedium,
                color = tokens.secondaryText,
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Search items, types, rarities, or tags") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
        ) {
            InventoryFilter.entries.forEach { choice ->
                Text(
                    choice.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (choice == filter) tokens.activePillLabel else tokens.secondaryText,
                    modifier = Modifier
                        .clip(RoundedCornerShape(inkRadiusSm()))
                        .background(if (choice == filter) tokens.activePill else tokens.panel)
                        .border(1.dp, tokens.hairline, RoundedCornerShape(inkRadiusSm()))
                        .clickable { onFilterChange(choice) }
                        .padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xs),
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(inkRadiusSm()))
                .border(1.dp, tokens.hairline, RoundedCornerShape(inkRadiusSm()))
                .horizontalScroll(rememberScrollState()),
        ) {
            InventoryLedgerHeader()
            if (shown.isEmpty()) {
                Text(
                    if (carrier.items.isEmpty()) "No items yet. Use + Item to add equipment or supplies."
                    else "No inventory matches this search and filter.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.secondaryText,
                    modifier = Modifier.width(760.dp).padding(InkSpacing.md),
                )
            } else {
                shown.forEach { item ->
                    InventoryLedgerRow(
                        item = item,
                        imagePath = carrier.itemImagePaths[item.id].orEmpty(),
                        onToggleActive = { onToggleActive(item.id) },
                        onImageClick = { onImageClick(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun InventoryLedgerHeader() {
    val tokens = inkTokens()
    Row(
        modifier = Modifier.width(760.dp).background(tokens.hover).padding(InkSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LedgerCell("ACTIVE", 54, true)
        LedgerCell("NAME", 230, true)
        LedgerCell("WEIGHT", 72, true)
        LedgerCell("QTY", 52, true)
        LedgerCell("COST (GP)", 78, true)
        LedgerCell("NOTES", 250, true)
    }
}

@Composable
private fun InventoryLedgerRow(
    item: RpItem,
    imagePath: String,
    onToggleActive: () -> Unit,
    onImageClick: () -> Unit,
) {
    val tokens = inkTokens()
    Row(
        modifier = Modifier
            .width(760.dp)
            .border(0.5.dp, tokens.hairline)
            .padding(InkSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (item.active) "■" else "□",
            color = if (item.active) tokens.activePill else tokens.secondaryText,
            modifier = Modifier.width(54.dp).clickable(onClick = onToggleActive).padding(8.dp),
        )
        Row(
            modifier = Modifier.width(230.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(inkRadiusSm()))
                    .background(tokens.hover)
                    .clickable(onClick = onImageClick),
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
                    Text("＋", style = MaterialTheme.typography.labelMedium, color = tokens.activePill)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    listOf(item.template, item.tags).filter(String::isNotBlank).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        LedgerCell("${"%.1f".format(item.weightLb)} lb", 72)
        LedgerCell(item.quantity.toString(), 52)
        LedgerCell("%.2f".format(item.costGp), 78)
        LedgerCell(
            listOf(item.notes, if (item.attuned) "Attunement" else "").filter(String::isNotBlank).joinToString(" · "),
            250,
        )
    }
}

@Composable
private fun LedgerCell(text: String, width: Int, header: Boolean = false) {
    val tokens = inkTokens()
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
        color = if (header) tokens.secondaryText else tokens.primaryText,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width.dp).padding(horizontal = 3.dp),
    )
}

@Composable
private fun BackpackPanel(
    carrier: CarrierUi,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onImageClick: (com.ihy2ln.weaverse.data.db.entities.RpItem) -> Unit,
    onRemove: (String) -> Unit,
) {
    val tokens = inkTokens()
    val backpack = equippedBackpack(carrier.items, carrier.equipment)
    val capacity = backpackCapacity(carrier.items, carrier.equipment)
    val contents = backpackContents(carrier.items, carrier.equipment)
    val used = backpackUsedSlots(carrier.items, carrier.equipment)
    val emptySlots = (capacity - used).coerceAtLeast(0).coerceAtMost(24)
    val overBy = (used - capacity).coerceAtLeast(0)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = InkSpacing.lg, vertical = InkSpacing.xs)
            .clip(RoundedCornerShape(inkRadiusSm()))
            .border(1.dp, tokens.hairline, RoundedCornerShape(inkRadiusSm())),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(InkSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (collapsed) "›" else "⌄", color = tokens.secondaryText)
            Text(
                "BACKPACK",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(start = InkSpacing.xs),
            )
            Text(
                backpack?.name ?: "No backpack equipped",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = InkSpacing.sm),
            )
            Text(
                "$used / $capacity slots",
                style = MaterialTheme.typography.labelSmall,
                color = if (overBy > 0) MaterialTheme.colorScheme.error else tokens.activePill,
            )
        }
        if (!collapsed) {
            if (capacity == 0) {
                Text(
                    "Equip a backpack to open storage slots. Unpacked gear remains available below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.secondaryText,
                    modifier = Modifier.padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xs),
                )
            } else if (overBy > 0) {
                Text(
                    "Over capacity by $overBy slots. Equip a larger backpack or remove gear.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xs),
                )
            }
            val cells: List<com.ihy2ln.weaverse.data.db.entities.RpItem?> =
                contents.map { it as com.ihy2ln.weaverse.data.db.entities.RpItem? } + List(emptySlots) { null }
            if (cells.isEmpty()) {
                Text(
                    "The backpack is empty.",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.secondaryText,
                    modifier = Modifier.padding(InkSpacing.sm),
                )
            }
            cells.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = InkSpacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
                ) {
                    row.forEach { item ->
                        if (item == null) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(bottom = InkSpacing.xs)
                                    .height(76.dp)
                                    .clip(RoundedCornerShape(inkRadiusSm()))
                                    .background(tokens.hover)
                                    .border(1.dp, tokens.hairline, RoundedCornerShape(inkRadiusSm())),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("Empty slot", style = MaterialTheme.typography.labelSmall, color = tokens.secondaryText)
                            }
                        } else {
                            BackpackItemCard(
                                item = item,
                                imagePath = carrier.itemImagePaths[item.id].orEmpty(),
                                onImageClick = { onImageClick(item) },
                                onRemove = { onRemove(item.id) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    repeat(2 - row.size) { Box(modifier = Modifier.weight(1f)) }
                }
            }
            if (capacity - used > emptySlots) {
                Text(
                    "+ ${capacity - used - emptySlots} more empty slots",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.secondaryText,
                    modifier = Modifier.padding(InkSpacing.sm),
                )
            }
        }
    }
}

@Composable
private fun BackpackItemCard(
    item: com.ihy2ln.weaverse.data.db.entities.RpItem,
    imagePath: String,
    onImageClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = inkTokens()
    Row(
        modifier = modifier
            .padding(bottom = InkSpacing.xs)
            .height(76.dp)
            .clip(RoundedCornerShape(inkRadiusSm()))
            .background(tokens.panel)
            .border(1.dp, tokens.hairline, RoundedCornerShape(inkRadiusSm()))
            .padding(InkSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(inkRadiusSm()))
                .background(tokens.hover)
                .clickable(onClick = onImageClick),
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
            Text(item.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                "${item.template} · ${item.quantity * item.slotSize.coerceAtLeast(1)} slots",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.secondaryText,
                maxLines = 1,
            )
        }
        Text("−", color = tokens.secondaryText, modifier = Modifier.clickable(onClick = onRemove).padding(4.dp))
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
    onAddForSlot: (RpEquipSlot) -> Unit,
    onItemImageClick: (com.ihy2ln.weaverse.data.db.entities.RpItem) -> Unit,
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
                        } else if (equippedItem != null) {
                            Box(
                                modifier = Modifier
                                    .padding(vertical = 3.dp)
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(inkRadiusSm()))
                                    .background(tokens.hover)
                                    .clickable { onItemImageClick(equippedItem) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("＋ image", style = MaterialTheme.typography.labelSmall, color = tokens.activePill)
                            }
                        }
                        Text(
                            equipped.ifBlank { "—" },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (equipped.isBlank()) tokens.secondaryText else tokens.primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (equippedItem == null) "+ Add item" else "Change · Picture",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.activePill,
                            modifier = Modifier
                                .padding(top = 3.dp)
                                .clickable {
                                    if (equippedItem == null) onAddForSlot(slot) else onItemImageClick(equippedItem)
                                },
                        )
                    }
                }
                // Keep the last row aligned when the slot count is not a multiple of three.
                repeat(3 - row.size) { Box(modifier = Modifier.weight(1f)) }
            }
        }
    }
}
