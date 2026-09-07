package com.ihy2ln.weaverse.feature.roleplay.textgame

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing

private val TycoonInk = Color(0xFF12100E)
private val TycoonPanel = Color(0xFF1C1612)
private val TycoonGold = Color(0xFFE0B24E)
private val TycoonPale = Color(0xFFF4EBDD)
private val TycoonMuted = Color(0xFFC8B9A5)

internal fun tycoonDistrictColor(district: TycoonDistrict): Color = when (district) {
    TycoonDistrict.Residential -> Color(0xFFC4A574)
    TycoonDistrict.Farm -> Color(0xFF6BA86B)
    TycoonDistrict.Commercial -> Color(0xFF5BA8B8)
    TycoonDistrict.Industrial -> Color(0xFF8B6B4A)
    TycoonDistrict.Governates -> Color(0xFF8B6BB0)
}

/** Compose scroll parents report Infinity; NaN/Inf cell sizes crash the lots scene. */
internal fun tycoonFiniteDp(value: Float, fallback: Float): Float =
    if (value.isFinite() && value > 0f) value else fallback

internal fun tycoonCellSizeDp(
    availableWidth: Float,
    availableHeight: Float,
    columns: Int,
    rows: Int,
    zoom: Float,
): Float {
    val cols = columns.coerceAtLeast(1)
    val rowCount = rows.coerceAtLeast(1)
    val width = tycoonFiniteDp(availableWidth, 360f)
    val height = tycoonFiniteDp(availableHeight, 360f)
    val fit = minOf(width / cols, height / rowCount)
    return (fit * zoom.coerceIn(0.7f, 2.2f)).coerceIn(28f, 96f)
}

@Composable
internal fun TycoonScene(
    ui: TextGameUiState,
    node: TextGameNode,
    isChoiceEnabled: (TextGameState, TextGameChoice) -> Boolean,
    dispatch: (TextGameAction) -> Unit,
) {
    val board = ui.game.persistent.tycoon
    val bonuses = remember(board) { tycoonBonuses(board) }
    val counts = remember(board) { tycoonDistrictCounts(board) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var filter by remember { mutableStateOf<TycoonDistrict?>(null) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wide = maxWidth >= 840.dp
        val sidebar: @Composable (Modifier) -> Unit = { sidebarModifier ->
            TycoonSidebar(
                ui = ui,
                node = node,
                board = board,
                bonuses = bonuses,
                counts = counts,
                filter = filter,
                onFilter = { filter = if (filter == it) null else it },
                onZoomIn = { zoom = (zoom + 0.2f).coerceAtMost(2.2f) },
                onZoomOut = { zoom = (zoom - 0.2f).coerceAtLeast(0.7f) },
                isChoiceEnabled = isChoiceEnabled,
                dispatch = dispatch,
                modifier = sidebarModifier,
            )
        }
        if (wide) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm),
            ) {
                TycoonBoardPane(
                    ui = ui,
                    board = board,
                    zoom = zoom,
                    modifier = Modifier.weight(1.45f),
                    dispatch = dispatch,
                )
                sidebar(Modifier.widthIn(min = 260.dp, max = 340.dp))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.sm)) {
                TycoonBoardPane(
                    ui = ui,
                    board = board,
                    zoom = zoom,
                    modifier = Modifier.fillMaxWidth(),
                    dispatch = dispatch,
                )
                sidebar(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun TycoonBoardPane(
    ui: TextGameUiState,
    board: TycoonBoardState,
    zoom: Float,
    modifier: Modifier,
    dispatch: (TextGameAction) -> Unit,
) {
    Card(
        modifier = modifier.border(2.dp, Color(0xFF9B6A31), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = TycoonInk),
        shape = RoundedCornerShape(16.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 280.dp, max = 520.dp).padding(10.dp)) {
            val cell = tycoonCellSizeDp(
                availableWidth = maxWidth.value,
                availableHeight = maxHeight.value,
                columns = board.width,
                rows = board.height,
                zoom = zoom,
            ).dp
            val gridW = cell * board.width.coerceAtLeast(1)
            val gridH = cell * board.height.coerceAtLeast(1)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(gridH)
                    .horizontalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(Modifier.width(gridW).height(gridH)) {
                    TycoonGrid(
                        ui = ui,
                        board = board,
                        cell = cell,
                        dispatch = dispatch,
                    )
                }
            }
        }
    }
}

@Composable
private fun TycoonGrid(
    ui: TextGameUiState,
    board: TycoonBoardState,
    cell: androidx.compose.ui.unit.Dp,
    dispatch: (TextGameAction) -> Unit,
) {
    val occupied = remember(board) { tycoonOccupiedCells(board) }
    val selected = board.selectedBuildingId?.let(::tycoonBuilding)
    Box(Modifier.fillMaxSize()) {
        for (y in 0 until board.height) {
            for (x in 0 until board.width) {
                val district = tycoonDistrictAt(x, y, board.width, board.height)
                val color = tycoonDistrictColor(district)
                val isOriginLabel = isDistrictLabelCell(x, y, board.width, board.height, district)
                val valid = selected != null && tycoonCanPlace(board, selected.id, x, y)
                Box(
                    Modifier
                        .offset(x = cell * x, y = cell * y)
                        .size(cell)
                        .background(color.copy(alpha = 0.18f))
                        .border(0.6.dp, color.copy(alpha = 0.45f))
                        .clickable(enabled = board.selectedBuildingId != null) {
                            board.selectedBuildingId?.let { dispatch(TextGameAction.PlaceTycoonBuilding(it, x, y)) }
                        },
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        if (size.minDimension <= 0f) return@Canvas
                        val step = size.minDimension / 4f
                        for (i in -2..6) {
                            drawLine(
                                color = Color(0x22E8D8B0),
                                start = Offset(-size.width + i * step, 0f),
                                end = Offset(i * step, size.height),
                                strokeWidth = 1f,
                            )
                        }
                    }
                    if (isOriginLabel && (x to y) !in occupied) {
                        Text(
                            district.displayName.uppercase(),
                            color = color,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(3.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                    if (valid) {
                        Box(Modifier.fillMaxSize().background(Color(0x5548C47A)))
                    }
                }
            }
        }
        board.placements.forEach { placement ->
            val def = tycoonBuilding(placement.buildingId) ?: return@forEach
            val selectedHere = board.selectedPlacementId == placement.id
            val color = tycoonDistrictColor(def.district)
            Box(
                Modifier
                    .offset(x = cell * placement.x, y = cell * placement.y)
                    .width(cell * def.width)
                    .height(cell * def.height)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(if (selectedHere) 2.dp else 1.dp, color, RoundedCornerShape(6.dp))
                    .clickable { dispatch(TextGameAction.SelectTycoonPlacement(placement.id)) }
                    .background(TycoonPanel),
            ) {
                AsyncImage(
                    model = "file:///android_asset/${def.artAssetPath}",
                    contentDescription = def.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color(0xCC12100E))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    Text(
                        def.title,
                        color = TycoonPale,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        ui.game.run.lastLog.takeIf { it.isNotBlank() }?.let { log ->
            Text(
                log,
                color = TycoonMuted,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun isDistrictLabelCell(x: Int, y: Int, width: Int, height: Int, district: TycoonDistrict): Boolean {
    for (yy in 0 until height) {
        for (xx in 0 until width) {
            if (tycoonDistrictAt(xx, yy, width, height) == district) return xx == x && yy == y
        }
    }
    return false
}

@Composable
private fun TycoonSidebar(
    ui: TextGameUiState,
    node: TextGameNode,
    board: TycoonBoardState,
    bonuses: TycoonBonuses,
    counts: Map<TycoonDistrict, Int>,
    filter: TycoonDistrict?,
    onFilter: (TycoonDistrict) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    isChoiceEnabled: (TextGameState, TextGameChoice) -> Boolean,
    dispatch: (TextGameAction) -> Unit,
    modifier: Modifier,
) {
    val persistent = ui.game.persistent
    val selectedPlacement = board.placements.firstOrNull { it.id == board.selectedPlacementId }
    val selectedDef = selectedPlacement?.let { tycoonBuilding(it.buildingId) }
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = TycoonPanel),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            Modifier.padding(InkSpacing.md),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("SILVERBROOK SETTLEMENT", color = TycoonGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("${board.width} × ${board.height} lots.", color = TycoonMuted, fontSize = 12.sp)
            Text(
                TycoonDistrict.entries.joinToString(" · ") { "${it.shortLabel} ${counts[it] ?: 0}" },
                color = TycoonPale,
                fontSize = 12.sp,
            )
            Text(
                if (board.hand.isEmpty()) {
                    "No building cards in hand. They come back from delves — or buy one at the Market."
                } else {
                    "In hand: " + board.hand.mapNotNull { tycoonBuilding(it)?.title }.joinToString(", ")
                },
                color = TycoonMuted,
                fontSize = 12.sp,
            )
            Text(
                "+${bonuses.yieldPercent}% yield   +${bonuses.goldPercent}% gold   +${bonuses.comfortPercent}% comfort",
                color = TycoonGold,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Runs completed: ${persistent.battlesWon}   ${persistent.coins} gold",
                color = TycoonPale,
                fontSize = 12.sp,
            )
            Text(
                "Buildings sit where you put them. Trade likes company; homes like neighbours.",
                color = TycoonMuted,
                fontSize = 11.sp,
            )
            Text("Choose a card", color = TycoonGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            board.hand.forEach { id ->
                val def = tycoonBuilding(id) ?: return@forEach
                val selected = board.selectedBuildingId == id
                OutlinedButton(
                    onClick = { dispatch(TextGameAction.SelectTycoonCard(id)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (selected) "▶ ${def.title}  ${def.width}×${def.height}" else "${def.title}  ${def.width}×${def.height}")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                TycoonDistrict.entries.forEach { district ->
                    val on = filter == district
                    OutlinedButton(onClick = { onFilter(district) }) {
                        Text(district.displayName, color = if (on) TycoonGold else TycoonPale, fontSize = 11.sp)
                    }
                }
            }
            tycoonBuildings()
                .filter { !it.starter }
                .filter { filter == null || it.district == filter }
                .forEach { def ->
                    val owned = def.id in board.hand || board.placements.any { it.buildingId == def.id }
                    OutlinedButton(
                        onClick = { dispatch(TextGameAction.TakeTycoonCard(def.id)) },
                        enabled = !owned,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val cost = buildString {
                            append("Take ${def.title}")
                            if (def.coinCost > 0 || def.materialCost > 0) {
                                append(" — ${def.coinCost}c")
                                if (def.materialCost > 0) append(" ${def.materialCost}m")
                            }
                        }
                        Text(cost, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            Text("Grow the charter", color = TycoonGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            val goldCost = tycoonExpandGoldCost(board.expansionsBought)
            OutlinedButton(onClick = { dispatch(TextGameAction.ExpandTycoon(TycoonExpandWay.Gold)) }, modifier = Modifier.fillMaxWidth()) {
                Text("Buy lots — ${goldCost.first}c ${goldCost.second}m")
            }
            OutlinedButton(onClick = { dispatch(TextGameAction.ExpandTycoon(TycoonExpandWay.Dungeon)) }, modifier = Modifier.fillMaxWidth()) {
                Text("Claim delve spoils")
            }
            OutlinedButton(onClick = { dispatch(TextGameAction.ExpandTycoon(TycoonExpandWay.Farm)) }, modifier = Modifier.fillMaxWidth()) {
                Text("Annex with harvest — 3 produce")
            }
            OutlinedButton(onClick = { dispatch(TextGameAction.ExpandTycoon(TycoonExpandWay.Annex)) }, modifier = Modifier.fillMaxWidth()) {
                Text("Guild annex — 8c")
            }
            if (selectedPlacement != null && selectedDef != null && selectedDef.verbs.isNotEmpty()) {
                Text("${selectedDef.title} actions", color = TycoonGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                selectedDef.verbs.forEach { verb ->
                    val choice = TextGameChoice(verb.id, verb.label, verb.destinationNodeId ?: node.id, verb.condition, verb.effects)
                    OutlinedButton(
                        onClick = { dispatch(TextGameAction.UseTycoonBuilding(selectedPlacement.id, verb.id)) },
                        enabled = isChoiceEnabled(ui.game, choice),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(verb.label, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
            }
            node.choices.forEach { choice ->
                val enabled = isChoiceEnabled(ui.game, choice)
                if (enabled || choice.id == "tycoon_crossroads") {
                    Button(
                        onClick = { dispatch(TextGameAction.Choose(choice.id)) },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(choice.label, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onZoomIn, modifier = Modifier.weight(1f)) { Text("Zoom in") }
                OutlinedButton(onClick = onZoomOut, modifier = Modifier.weight(1f)) { Text("Zoom out") }
            }
        }
    }
}
