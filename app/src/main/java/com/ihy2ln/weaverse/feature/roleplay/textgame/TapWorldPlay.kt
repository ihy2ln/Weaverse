package com.ihy2ln.weaverse.feature.roleplay.textgame

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import kotlin.math.roundToInt

private enum class FarmTool(val label: String, val glyph: String) {
    Hand("Hand", "✋"),
    Till("Till", "🪓"),
    Plant("Plant", "🌱"),
    Water("Water", "💧"),
    Harvest("Harvest", "🌾"),
    Expand("Expand", "📐"),
}

/** Instant Farmville tap — skip the timing minigame. */
private const val TAP_SCORE = 0.65f

@Composable
fun TapWorldPlay(
    ui: TextGameUiState,
    node: TextGameNode,
    isChoiceEnabled: (TextGameState, TextGameChoice) -> Boolean,
    dispatch: (TextGameAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val farmScene = isFarmWorldNode(node.id)
    val persistent = ui.game.persistent
    val farm = remember(persistent.farm, persistent.battlesWon, persistent.farmLevel, persistent.flags) {
        FarmRules.live(persistent)
    }
    val town = remember(persistent.town, persistent.flags, persistent.townLevel) {
        TownRules.live(persistent)
    }
    var selected by remember(node.id) { mutableStateOf<TextGameHotspot?>(null) }
    var movingId by remember(node.id) { mutableStateOf<String?>(null) }
    var tool by rememberSaveable(node.id) { mutableStateOf(FarmTool.Hand.name) }
    var cropId by rememberSaveable(node.id) { mutableStateOf(FarmRules.CROPS.first().id) }
    val farmTool = FarmTool.entries.firstOrNull { it.name == tool } ?: FarmTool.Hand

    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
        TapWorldHud(ui.game, farmScene, town)
        Box(Modifier.fillMaxWidth().weight(1f, fill = true)) {
            TapWorldMap(
                node = node,
                farm = farm,
                town = town,
                flags = persistent.flags,
                townLevel = persistent.townLevel,
                farmLevel = persistent.farmLevel,
                homeLevel = persistent.homeLevel,
                battlesWon = persistent.battlesWon,
                farmScene = farmScene,
                selectedId = selected?.id,
                movingId = movingId,
                onPlotTap = { plotId ->
                    selected = null
                    applyFarmTool(farmTool, plotId, cropId, ui, node, farm, isChoiceEnabled, dispatch)
                },
                onHarvestPlot = { plotId ->
                    selected = null
                    dispatch(TextGameAction.FarmHarvest(plotId, TAP_SCORE))
                },
                onCollect = { buildingId ->
                    selected = null
                    dispatch(TextGameAction.TownCollect(buildingId))
                },
                onTownPlot = { plotId ->
                    val relocating = movingId
                    if (relocating != null) {
                        dispatch(TextGameAction.TownMove(relocating, plotId))
                        movingId = null
                        selected = null
                    } else {
                        val def = TownRules.defOnPlot(town, plotId, persistent.flags, persistent.townLevel)
                        if (def != null) {
                            applyTownOrFarmLot(
                                hotspot = TownRules.hotspot(def, persistent.homeLevel),
                                node = node,
                                ui = ui,
                                farmScene = false,
                                isChoiceEnabled = isChoiceEnabled,
                                dispatch = dispatch,
                                onSelect = { selected = it },
                            )
                        }
                    }
                },
                onMoveBuilding = { buildingId, plotId ->
                    dispatch(TextGameAction.TownMove(buildingId, plotId))
                    movingId = null
                    selected = null
                },
                onCancelMove = { movingId = null },
                onHotspot = { hotspot ->
                    when (hotspot.resolvedKind()) {
                        TextGameHotspotKind.Exit -> {
                            selected = null
                            if (hotspot.choiceId.isNotBlank()) dispatch(TextGameAction.Choose(hotspot.choiceId))
                        }
                        TextGameHotspotKind.Plot -> Unit
                        TextGameHotspotKind.Npc -> selected = hotspot
                        TextGameHotspotKind.Building, TextGameHotspotKind.Location -> {
                            applyTownOrFarmLot(
                                hotspot = hotspot,
                                node = node,
                                ui = ui,
                                farmScene = farmScene,
                                isChoiceEnabled = isChoiceEnabled,
                                dispatch = dispatch,
                                onSelect = { selected = it },
                            )
                        }
                    }
                },
            )
            if (ui.game.run.lastLog.isNotBlank()) {
                Text(
                    ui.game.run.lastLog,
                    color = Color(0xFFFFF4DC),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xCC1A140E))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val picked = selected
        when {
            picked?.resolvedKind() == TextGameHotspotKind.Npc -> NpcTalkSheet(
                hotspot = picked,
                node = node,
                state = ui.game,
                isChoiceEnabled = isChoiceEnabled,
                dispatch = dispatch,
                onClose = { selected = null },
            )
            picked?.resolvedKind() == TextGameHotspotKind.Building ||
                picked?.resolvedKind() == TextGameHotspotKind.Location -> BuildingSheet(
                hotspot = picked,
                node = node,
                ui = ui,
                farm = farm,
                town = town,
                farmScene = farmScene,
                isChoiceEnabled = isChoiceEnabled,
                dispatch = dispatch,
                onClose = { selected = null },
                onMoveRequest = if (farmScene) {
                    null
                } else {
                    {
                        movingId = picked.buildingId ?: picked.id
                        selected = null
                    }
                },
            )
            farmScene -> Unit
        }
    }
}

@Composable
private fun TapWorldHud(state: TextGameState, farmScene: Boolean, town: TownState) {
    val p = state.persistent
    val ready = if (!farmScene) {
        TownRules.BUILDINGS.count { TownRules.canCollect(town, it, p.flags, p.townLevel, p.battlesWon) }
    } else {
        0
    }
    val line = if (farmScene) {
        "Coin ${p.coins}   Seed ${p.seeds}   Produce ${p.harvest}   Farm ${p.farmLevel}"
    } else {
        buildString {
            append("Coin ${p.coins}   Ore ${p.materials}   Town ${p.townLevel}")
            if (ready > 0) append("   Tribute $ready")
        }
    }
    Text(
        line,
        color = Color(0xFFE8DCC4),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xCC1A140C), Color(0xAA2A2218))))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun TapWorldMap(
    node: TextGameNode,
    farm: FarmState,
    town: TownState,
    flags: List<String>,
    townLevel: Int,
    farmLevel: Int,
    homeLevel: Int,
    battlesWon: Int,
    farmScene: Boolean,
    selectedId: String?,
    movingId: String? = null,
    onPlotTap: (Int) -> Unit,
    onHarvestPlot: (Int) -> Unit,
    onCollect: (String) -> Unit,
    onTownPlot: (String) -> Unit = {},
    onMoveBuilding: (String, String) -> Unit = { _, _ -> },
    onCancelMove: () -> Unit = {},
    onHotspot: (TextGameHotspot) -> Unit,
) {
    val bounce = rememberInfiniteTransition(label = "collect-bounce")
    val lift by bounce.animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "collect-lift",
    )
    val cleared = "farm_cleared" in flags || farm.plots.isNotEmpty()
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp)),
    ) {
        if (farmScene) {
            FarmIsoBoard(
                node = node,
                farm = farm,
                flags = flags,
                farmLevel = farmLevel,
                cleared = cleared,
                selectedId = selectedId,
                lift = lift,
                onPlotTap = onPlotTap,
                onHarvestPlot = onHarvestPlot,
                onHotspot = onHotspot,
            )
        } else {
            TownYardBoard(
                node = node,
                town = town,
                flags = flags,
                townLevel = townLevel,
                homeLevel = homeLevel,
                battlesWon = battlesWon,
                selectedId = selectedId,
                movingId = movingId,
                lift = lift,
                onCollect = onCollect,
                onTownPlot = onTownPlot,
                onMoveBuilding = onMoveBuilding,
                onHotspot = onHotspot,
                onCancelMove = onCancelMove,
            )
        }
    }
}

@Composable
private fun FarmIsoBoard(
    node: TextGameNode,
    farm: FarmState,
    flags: List<String>,
    farmLevel: Int,
    cleared: Boolean,
    selectedId: String?,
    lift: Float,
    onPlotTap: (Int) -> Unit,
    onHarvestPlot: (Int) -> Unit,
    onHotspot: (TextGameHotspot) -> Unit,
) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val canvasW = with(density) { maxWidth.toPx() }
        val canvasH = with(density) { maxHeight.toPx() }
        val tileW = canvasW / 9.5f
        val tileH = tileW / 2f
        val origin = IsoGrid.gridOrigin(canvasW, canvasH, tileW = tileW, tileH = tileH)
        val visiblePlots = remember(farm.plots) {
            FarmLayout.PLOT_CELLS.filter { cell ->
                val plot = farm.plots.firstOrNull { it.id == cell.plotId }
                plot != null || cell.plotId == 0 || cell.plotId == farm.plots.size
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val skyH = size.height * 0.18f
            drawRect(Color(0xFF8EC8E8), size = androidx.compose.ui.geometry.Size(size.width, skyH))
            for (sum in 0 until IsoGrid.FARM_COLS + IsoGrid.FARM_ROWS) {
                for (col in 0 until IsoGrid.FARM_COLS) {
                    val row = sum - col
                    if (row !in 0 until IsoGrid.FARM_ROWS) continue
                    val center = IsoGrid.cellCenter(col, row, origin.x, origin.y, tileW, tileH)
                    val plotCell = FarmLayout.PLOT_CELLS.firstOrNull { it.col == col && it.row == row }
                    val plot = plotCell?.let { pc -> farm.plots.firstOrNull { it.id == pc.plotId } }
                    val visible = plotCell == null || visiblePlots.any { it.col == col && it.row == row }
                    if (!visible) continue
                    val fill = when {
                        plotCell == null -> if ((col + row) % 3 == 0) Color(0xFF5E9238) else Color(0xFF6FA344)
                        plot == null -> Color(0xAA5C3A1E)
                        else -> plotFill(plot)
                    }
                    drawIsoDiamond(center, tileW, tileH, fill, plotRim(plot))
                }
            }
        }
        visiblePlots.forEach { cell ->
            val plot = farm.plots.firstOrNull { it.id == cell.plotId }
            val center = IsoGrid.cellCenter(cell.col, cell.row, origin.x, origin.y, tileW, tileH)
            val selected = selectedId == "farm_plot_${cell.plotId}"
            val harvestReady = plot?.soil == FarmSoil.Ready
            BoardOverlay(
                centerX = center.x,
                centerY = center.y - tileH * 0.15f,
                widthDp = with(density) { tileW.toDp() },
                density = density,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (harvestReady) {
                        CollectBubble(label = "!", fill = Color(0xFFF2C14A), lift = lift, onClick = { onHarvestPlot(cell.plotId) })
                    }
                    PlotTapChip(onClick = { onPlotTap(cell.plotId) })
                }
            }
        }
        FarmBuildings.LOTS.filter { FarmBuildings.isVisible(it, flags, farmLevel, cleared) }.forEach { def ->
            val status = FarmBuildings.status(def, flags, farmLevel, cleared)
            val anchor = IsoGrid.rectBottomAnchor(def.footprint, origin.x, origin.y, tileW, tileH)
            val buildingH = with(density) { (tileH * 3.2f).toDp() }
            val buildingW = with(density) { (tileW * 1.6f).toDp() }
            BoardOverlay(
                centerX = anchor.x,
                centerY = anchor.y,
                widthDp = buildingW,
                density = density,
                anchorBottom = true,
                heightDp = buildingH,
            ) {
                YardLot(
                    label = def.name,
                    artPath = if (status == TownLotStatus.Built) def.artAssetPath else "",
                    empty = status == TownLotStatus.Empty,
                    selected = selectedId == def.id,
                    onClick = { onHotspot(FarmBuildings.hotspot(def)) },
                )
            }
        }
        node.hotspots.filter { it.resolvedKind() == TextGameHotspotKind.Npc }.forEach { npc ->
            val allowed = npc.buildingId.isNullOrBlank() ||
                FarmBuildings.def(npc.buildingId)?.let {
                    FarmBuildings.status(it, flags, farmLevel, cleared) == TownLotStatus.Built
                } == true
            if (!allowed) return@forEach
            val anchor = npc.buildingId?.let(FarmBuildings::def)?.let { def ->
                IsoGrid.rectBottomAnchor(def.footprint, origin.x, origin.y, tileW, tileH)
            } ?: IsoGrid.cellCenter(1, 2, origin.x, origin.y, tileW, tileH)
            BoardOverlay(centerX = anchor.x, centerY = anchor.y - tileH, widthDp = 88.dp, density = density) {
                NamePlate(label = npc.label, selected = selectedId == npc.id, onClick = { onHotspot(npc) })
            }
        }
        val crossroads = IsoGrid.cellCenter(3, 6, origin.x, origin.y, tileW, tileH)
        val townRoad = IsoGrid.cellCenter(6, 5, origin.x, origin.y, tileW, tileH)
        node.hotspots.filter { it.resolvedKind() == TextGameHotspotKind.Exit }.forEach { exit ->
            val anchor = if (exit.id.contains("town")) townRoad else crossroads
            BoardOverlay(centerX = anchor.x, centerY = anchor.y + tileH * 0.4f, widthDp = 96.dp, density = density) {
                NamePlate(label = exit.label, selected = false, onClick = { onHotspot(exit) })
            }
        }
    }
}

@Composable
private fun TownYardBoard(
    node: TextGameNode,
    town: TownState,
    flags: List<String>,
    townLevel: Int,
    homeLevel: Int,
    battlesWon: Int,
    selectedId: String?,
    movingId: String?,
    lift: Float,
    onCollect: (String) -> Unit,
    onTownPlot: (String) -> Unit,
    onMoveBuilding: (String, String) -> Unit,
    onHotspot: (TextGameHotspot) -> Unit,
    onCancelMove: () -> Unit,
) {
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val canvasW = with(density) { maxWidth.toPx() }
        val canvasH = with(density) { maxHeight.toPx() }
        val imageRect = remember(canvasW, canvasH) { TownYard.fittedImageRect(canvasW, canvasH) }
        val pad = remember(imageRect) { TownYard.padSize(imageRect) }
        val padW = with(density) { pad.x.toDp() }
        val padH = with(density) { pad.y.toDp() }
        val buildingW = with(density) { (pad.x * 1.45f).toDp() }
        val buildingH = with(density) { (pad.y * 3.2f).toDp() }
        AsyncImage(
            model = "file:///android_asset/${TownYard.BACKDROP}",
            contentDescription = "Town lot",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Canvas(Modifier.fillMaxSize()) {
            TownYard.PLOTS.forEach { plot ->
                val center = TownYard.plotCenter(plot, imageRect)
                val occupant = TownRules.defOnPlot(town, plot.id, flags, townLevel)
                val empty = occupant != null && TownRules.status(occupant, flags, townLevel) == TownLotStatus.Empty
                val fill = when {
                    movingId != null && occupant?.id == movingId -> Color(0x66E8D4A0)
                    empty || (movingId != null && occupant == null) -> Color(0xAA6A4A28)
                    occupant != null -> Color(0x556A4A28)
                    else -> Color(0x446A4A28)
                }
                val rim = when {
                    movingId != null -> Color(0xCCE8D4A0)
                    empty -> Color(0xCCB89A62)
                    else -> Color(0x88B89A62)
                }
                drawIsoDiamond(center, pad.x, pad.y, fill, rim)
            }
        }
        TownYard.PLOTS.forEach { plot ->
            val center = TownYard.plotCenter(plot, imageRect)
            BoardOverlay(
                centerX = center.x,
                centerY = center.y,
                widthDp = padW,
                density = density,
                heightDp = padH,
                centered = true,
            ) {
                Box(Modifier.fillMaxSize().clickable { onTownPlot(plot.id) })
            }
        }
        TownRules.BUILDINGS
            .filter { TownRules.isVisible(it, flags, townLevel) }
            .sortedBy { def ->
                TownYard.plot(TownRules.visualPlot(town, def, flags, townLevel))
                    ?.let { TownYard.plotNorm(it).y }
                    ?: 0.5f
            }
            .forEach { def ->
                val status = TownRules.status(def, flags, townLevel)
                if (status != TownLotStatus.Built) return@forEach
                val plot = TownYard.plot(TownRules.visualPlot(town, def, flags, townLevel)) ?: return@forEach
                val pos = TownYard.plotCenter(plot, imageRect)
                val collectReady = TownRules.canCollect(town, def, flags, townLevel, battlesWon)
                val delta = if (dragId == def.id) dragDelta else Offset.Zero
                BoardOverlay(
                    centerX = pos.x + delta.x,
                    centerY = pos.y + delta.y,
                    widthDp = buildingW,
                    density = density,
                    anchorBottom = true,
                    heightDp = buildingH + 32.dp,
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(def.id, imageRect) {
                                val slop = viewConfiguration.touchSlop
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    var dragged = false
                                    var amount = Offset.Zero
                                    val finished = drag(down.id) { change ->
                                        amount += change.positionChange()
                                        change.consume()
                                        if (!dragged && amount.getDistance() > slop) {
                                            dragged = true
                                            dragId = def.id
                                        }
                                        if (dragged) dragDelta = amount
                                    }
                                    if (!finished) {
                                        dragId = null
                                        dragDelta = Offset.Zero
                                    } else if (dragged) {
                                        val dest = TownYard.nearestPlot(pos.x + amount.x, pos.y + amount.y, imageRect)
                                        onMoveBuilding(def.id, dest.id)
                                        dragId = null
                                        dragDelta = Offset.Zero
                                    } else {
                                        onHotspot(TownRules.hotspot(def, homeLevel))
                                    }
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        if (collectReady) {
                            CollectBubble(label = "◆", fill = Color(0xFFD4B46A), lift = lift, onClick = { onCollect(def.id) })
                        }
                        YardLot(
                            label = TownRules.displayName(def, homeLevel),
                            artPath = TownRules.spritePath(def, homeLevel),
                            empty = false,
                            selected = selectedId == def.id || movingId == def.id,
                            onClick = { onHotspot(TownRules.hotspot(def, homeLevel)) },
                            consumeClick = false,
                        )
                    }
                }
            }
        node.hotspots.filter { it.resolvedKind() == TextGameHotspotKind.Npc }.forEach { npc ->
            val required = npc.buildingId
            val allowed = required.isNullOrBlank() ||
                TownRules.def(required)?.let { TownRules.isBuilt(it, flags, townLevel) } == true
            if (!allowed) return@forEach
            val def = TownRules.def(required.orEmpty())
            val plotId = def?.let { TownRules.visualPlot(town, it, flags, townLevel) }
            val buildingPos = plotId?.let { TownYard.plot(it) }?.let { TownYard.plotCenter(it, imageRect) }
                ?: TownYard.position(required.orEmpty(), canvasW, canvasH)
            BoardOverlay(
                centerX = buildingPos.x,
                centerY = buildingPos.y - pad.y * 1.6f,
                widthDp = 88.dp,
                density = density,
            ) {
                NamePlate(label = npc.label, selected = selectedId == npc.id, onClick = { onHotspot(npc) })
            }
        }
        if (movingId != null) {
            BoardOverlay(centerX = canvasW * 0.50f, centerY = canvasH * 0.08f, widthDp = 220.dp, density = density) {
                NamePlate(label = "Tap a plot to place — or cancel", selected = true, onClick = onCancelMove)
            }
        }
        node.hotspots.filter { it.resolvedKind() == TextGameHotspotKind.Exit }.forEach { exit ->
            val pos = Offset(imageRect.left + imageRect.width * 0.50f, imageRect.bottom - imageRect.height * 0.06f)
            BoardOverlay(centerX = pos.x, centerY = pos.y, widthDp = 120.dp, density = density) {
                NamePlate(label = exit.label, selected = false, onClick = { onHotspot(exit) })
            }
        }
    }
}

@Composable
private fun BoxScope.BoardOverlay(
    centerX: Float,
    centerY: Float,
    widthDp: Dp,
    density: androidx.compose.ui.unit.Density,
    anchorBottom: Boolean = false,
    heightDp: Dp = 0.dp,
    centered: Boolean = false,
    content: @Composable () -> Unit,
) {
    val wPx = with(density) { widthDp.toPx() }
    val hPx = with(density) { if (heightDp > 0.dp) heightDp.toPx() else 48.dp.toPx() }
    val top = when {
        centered -> centerY - hPx / 2f
        anchorBottom -> centerY - hPx
        else -> centerY - with(density) { 24.dp.toPx() }
    }
    Box(
        Modifier
            .align(Alignment.TopStart)
            .offset { IntOffset((centerX - wPx / 2f).roundToInt(), top.roundToInt()) }
            .width(widthDp)
            .then(if (heightDp > 0.dp) Modifier.height(heightDp) else Modifier),
        contentAlignment = when {
            centered -> Alignment.Center
            anchorBottom -> Alignment.BottomCenter
            else -> Alignment.TopCenter
        },
    ) {
        content()
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIsoDiamond(
    center: Offset,
    tileW: Float,
    tileH: Float,
    fill: Color,
    rim: Color,
) {
    val halfW = tileW / 2f
    val halfH = tileH / 2f
    val path = Path().apply {
        moveTo(center.x, center.y - halfH)
        lineTo(center.x + halfW, center.y)
        lineTo(center.x, center.y + halfH)
        lineTo(center.x - halfW, center.y)
        close()
    }
    drawPath(path, fill)
    drawPath(path, rim, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))
}

@Composable
private fun YardLot(
    label: String,
    artPath: String,
    empty: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    consumeClick: Boolean = true,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (consumeClick) Modifier.clickable(onClick = onClick) else Modifier,
    ) {
        if (!empty && artPath.isNotBlank()) {
            AsyncImage(
                model = "file:///android_asset/$artPath",
                contentDescription = label,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .height(104.dp)
                    .fillMaxWidth()
                    .then(if (selected) Modifier.border(1.dp, Color(0xCCE8D4A0), RoundedCornerShape(4.dp)) else Modifier),
            )
        } else {
            Box(
                Modifier
                    .size(72.dp, 40.dp)
                    .then(if (selected) Modifier.border(1.dp, Color(0xCCE8D4A0), RoundedCornerShape(2.dp)) else Modifier),
            )
        }
    }
}

@Composable
private fun NamePlate(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = Color(0xFFE8DCC4),
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(Color(0xB31A140C))
            .border(1.dp, if (selected) Color(0xCCE8D4A0) else Color(0x66C4B089), RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun PlotTapChip(
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(52.dp, 28.dp)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun CollectBubble(
    label: String,
    fill: Color,
    lift: Float,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .offset(y = (-lift).dp)
            .size(28.dp)
            .clip(CircleShape)
            .background(fill)
            .border(2.dp, Color.White.copy(alpha = .9f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color(0xFF2A1A08), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun BuildingSheet(
    hotspot: TextGameHotspot,
    node: TextGameNode,
    ui: TextGameUiState,
    farm: FarmState,
    town: TownState,
    farmScene: Boolean,
    isChoiceEnabled: (TextGameState, TextGameChoice) -> Boolean,
    dispatch: (TextGameAction) -> Unit,
    onClose: () -> Unit,
    onMoveRequest: (() -> Unit)? = null,
) {
    val tokens = inkTokens()
    val buildingId = hotspot.buildingId
    val def = buildingId?.let(TownRules::def)
    val persistent = ui.game.persistent
    val status = def?.let { TownRules.status(it, persistent.flags, persistent.townLevel) }
    val collectReady = def != null && TownRules.canCollect(
        town, def, persistent.flags, persistent.townLevel, persistent.battlesWon,
    )
    val actions = buildingActions(node, hotspot)
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xF21A1614)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            Modifier.padding(InkSpacing.sm).heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(InkSpacing.xs),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (farmScene) "FARM LOT" else "TOWN LOT",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFE0B24E),
                    )
                    Text(hotspot.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        when (status) {
                            TownLotStatus.Empty -> {
                                val cost = def?.let(TownRules::costLabel).orEmpty()
                                if (cost.isNotBlank()) "Empty earth. Raise this building for $cost."
                                else "Empty earth. Tap to raise a building."
                            }
                            TownLotStatus.Locked -> "This plot opens after the lot grows."
                            TownLotStatus.Built -> if (collectReady) {
                                "Tribute waits on the roof. Drag the building onto another plot, or tap Move."
                            } else {
                                "Quiet until the next fight. Enter, or drag this building onto another plot."
                            }
                            null -> "Tap to work this building."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.secondaryText,
                    )
                }
                OutlinedButton(onClick = onClose) { Text("Close") }
            }
            if (collectReady && buildingId != null) {
                Button(
                    onClick = { dispatch(TextGameAction.TownCollect(buildingId)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Collect") }
            }
            if (onMoveRequest != null && status == TownLotStatus.Built) {
                OutlinedButton(
                    onClick = onMoveRequest,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Move to another plot") }
            }
            actions.forEach { choice ->
                val enabled = isChoiceEnabled(ui.game, choice)
                val visit = def?.visitChoiceId == choice.id || choice.id == hotspot.choiceId
                Button(
                    onClick = {
                        dispatch(TextGameAction.Choose(choice.id))
                        onClose()
                    },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (visit && enabled) "Enter — ${choice.label}" else choice.label)
                }
            }
            if (farmScene && buildingId == "kitchen" && (farm.pantry.isNotEmpty() || farm.packedDish != null)) {
                farm.packedDish?.let {
                    Text("Packed: $it", color = Color(0xFF9FE6A0), style = MaterialTheme.typography.labelSmall)
                }
                farm.pantry.forEach { (dish, count) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("$dish ×$count", color = Color(0xFFF1E5D1), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                        OutlinedButton(onClick = { dispatch(TextGameAction.FarmPackDish(dish)) }) { Text("Pack") }
                    }
                }
            }
        }
    }
}

@Composable
private fun NpcTalkSheet(
    hotspot: TextGameHotspot,
    node: TextGameNode,
    state: TextGameState,
    isChoiceEnabled: (TextGameState, TextGameChoice) -> Boolean,
    dispatch: (TextGameAction) -> Unit,
    onClose: () -> Unit,
) {
    val tokens = inkTokens()
    val visit = hotspot.choiceId.takeIf { it.isNotBlank() }?.let { id -> node.choices.firstOrNull { it.id == id } }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xF2141018)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            Modifier.padding(InkSpacing.md),
            verticalArrangement = Arrangement.spacedBy(InkSpacing.sm),
        ) {
            Text("CONVERSATION", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE8A0D0))
            Text(
                hotspot.npcName.ifBlank { hotspot.label },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                hotspot.talkProse.ifBlank { "I wait for them to speak." },
                style = MaterialTheme.typography.bodyLarge,
            )
            if (visit != null) {
                Button(
                    onClick = {
                        dispatch(TextGameAction.Choose(visit.id))
                        onClose()
                    },
                    enabled = isChoiceEnabled(state, visit),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(visit.label) }
            }
            OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text("I say goodbye")
            }
            Text(
                "Talk is the visual novel. Lots and plots stay on the map.",
                color = tokens.secondaryText,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private fun applyTownOrFarmLot(
    hotspot: TextGameHotspot,
    node: TextGameNode,
    ui: TextGameUiState,
    farmScene: Boolean,
    isChoiceEnabled: (TextGameState, TextGameChoice) -> Boolean,
    dispatch: (TextGameAction) -> Unit,
    onSelect: (TextGameHotspot?) -> Unit,
) {
    val id = hotspot.buildingId ?: hotspot.id
    if (farmScene) {
        val farmDef = FarmBuildings.def(id)
        val cleared = "farm_cleared" in ui.game.persistent.flags || ui.game.persistent.farm.plots.isNotEmpty()
        val status = farmDef?.let {
            FarmBuildings.status(it, ui.game.persistent.flags, ui.game.persistent.farmLevel, cleared)
        }
        if (status == TownLotStatus.Empty && farmDef != null) {
            val choice = node.choices.firstOrNull { it.id == farmDef.buildChoiceId }
            if (choice != null && isChoiceEnabled(ui.game, choice)) {
                onSelect(null)
                dispatch(TextGameAction.Choose(choice.id))
                return
            }
        }
        onSelect(hotspot)
        return
    }
    val def = TownRules.def(id) ?: run {
        onSelect(hotspot)
        return
    }
    val flags = ui.game.persistent.flags
    val townLevel = ui.game.persistent.townLevel
    val status = TownRules.status(def, flags, townLevel)
    when (status) {
        TownLotStatus.Empty -> {
            val choice = def.buildChoiceId?.let { choiceId -> node.choices.firstOrNull { it.id == choiceId } }
            if (choice != null && isChoiceEnabled(ui.game, choice)) {
                onSelect(null)
                dispatch(TextGameAction.Choose(choice.id))
            } else {
                onSelect(hotspot)
            }
        }
        TownLotStatus.Built -> onSelect(hotspot)
        TownLotStatus.Locked -> onSelect(hotspot)
    }
}

private fun applyFarmTool(
    tool: FarmTool,
    plotId: Int,
    cropId: String,
    ui: TextGameUiState,
    node: TextGameNode,
    farm: FarmState,
    isChoiceEnabled: (TextGameState, TextGameChoice) -> Boolean,
    dispatch: (TextGameAction) -> Unit,
) {
    val plot = farm.plots.firstOrNull { it.id == plotId }
    if (plot == null || tool == FarmTool.Expand) {
        expandOrClear(plotId, node, ui.game, isChoiceEnabled, dispatch)
        return
    }
    when (tool) {
        FarmTool.Hand -> when (plot.soil) {
            FarmSoil.Wild -> dispatch(TextGameAction.FarmTill(plot.id))
            FarmSoil.Tilled -> if (ui.game.persistent.seeds > 0) {
                dispatch(TextGameAction.FarmPlant(plot.id, TAP_SCORE, cropId))
            }
            FarmSoil.Planted -> if (!plot.watered) dispatch(TextGameAction.FarmWater(plot.id))
            FarmSoil.Ready -> dispatch(TextGameAction.FarmHarvest(plot.id, TAP_SCORE))
        }
        FarmTool.Till -> dispatch(TextGameAction.FarmTill(plot.id))
        FarmTool.Plant -> if (plot.soil == FarmSoil.Tilled && ui.game.persistent.seeds > 0) {
            dispatch(TextGameAction.FarmPlant(plot.id, TAP_SCORE, cropId))
        } else if (plot.soil == FarmSoil.Wild) {
            dispatch(TextGameAction.FarmTill(plot.id))
        }
        FarmTool.Water -> dispatch(TextGameAction.FarmWater(plot.id))
        FarmTool.Harvest -> dispatch(TextGameAction.FarmHarvest(plot.id, TAP_SCORE))
        FarmTool.Expand -> expandOrClear(plotId, node, ui.game, isChoiceEnabled, dispatch)
    }
}

private fun expandOrClear(
    plotId: Int,
    node: TextGameNode,
    state: TextGameState,
    isChoiceEnabled: (TextGameState, TextGameChoice) -> Boolean,
    dispatch: (TextGameAction) -> Unit,
) {
    val ids = if (plotId == 0) {
        listOf("clear_plot", "upgrade_farm_l2", "upgrade_farm_l3")
    } else {
        listOf("upgrade_farm_l2", "upgrade_farm_l3", "clear_plot")
    }
    val choice = ids.mapNotNull { id -> node.choices.firstOrNull { it.id == id } }
        .firstOrNull { isChoiceEnabled(state, it) }
    if (choice != null) dispatch(TextGameAction.Choose(choice.id))
    else if (plotId == 0) dispatch(TextGameAction.FarmTill(0))
}

private fun buildingActions(node: TextGameNode, hotspot: TextGameHotspot): List<TextGameChoice> {
    val id = hotspot.buildingId ?: hotspot.id
    val def = TownRules.def(id)
    val wanted = buildSet {
        def?.buildChoiceId?.let(::add)
        def?.visitChoiceId?.let(::add)
        hotspot.choiceId.takeIf { it.isNotBlank() }?.let(::add)
        when (id) {
            "market" -> addAll(listOf("build_market", "buy_seed", "buy_supplies", "sell_produce", "buy_coat", "return_visit_market"))
            "frosted_mug" -> addAll(listOf("build_inn", "visit_frosted_mug"))
            "plaza" -> addAll(listOf("town_plaza", "town_l5"))
            "deck_hall" -> addAll(listOf("build_deck_hall", "visit_deck_hall", "return_visit_deck"))
            "guild_hall" -> add("town_l3")
            "workshop" -> add("town_l4")
            "kitchen" -> addAll(listOf("visit_kitchen", "upgrade_farm_l2"))
            "barn" -> addAll(listOf("visit_barn", "upgrade_farm_l3"))
            "house" -> add("town_house")
        }
    }
    return node.choices.filter { it.id in wanted }.distinctBy { it.id }
}

private fun plotLabel(farm: FarmState, plot: FarmPlot?, hotspot: TextGameHotspot): String {
    val crop = plot?.let { FarmRules.crop(it.cropId) }
    return when {
        plot == null -> if (hotspot.farmPlotId == 0) "Clear" else "Expand"
        plot.soil == FarmSoil.Wild -> "Till"
        plot.soil == FarmSoil.Tilled -> "Plant"
        plot.soil == FarmSoil.Planted && !plot.watered -> "Water"
        plot.soil == FarmSoil.Planted -> "${crop?.name ?: "Crop"} · ${FarmRules.battlesRemaining(farm, plot)}"
        plot.soil == FarmSoil.Ready -> "Harvest"
        else -> hotspot.label
    }
}

private fun plotFill(plot: FarmPlot?): Color = when (plot?.soil) {
    FarmSoil.Wild -> Color(0xE65C3A1E)
    FarmSoil.Tilled -> Color(0xE67A4E24)
    FarmSoil.Planted -> Color(0xE82E6A32)
    FarmSoil.Ready -> Color(0xF0C49218)
    null -> Color(0xE6281C12)
}

private fun plotRim(plot: FarmPlot?): Color = when (plot?.soil) {
    FarmSoil.Wild -> Color(0xFFA87A4A)
    FarmSoil.Tilled -> Color(0xFFC9A06A)
    FarmSoil.Planted -> Color(0xFF70B861)
    FarmSoil.Ready -> Color(0xFFFFE06B)
    null -> Color(0x66E8D8B0)
}
