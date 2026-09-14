package com.ihy2ln.weaverse.feature.roleplay.textgame

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlin.math.roundToInt

@Composable
fun HavenBoardOverlay(
    boardKind: HavenBoardKind,
    havenBoard: HavenBoardState,
    dispatch: (TextGameAction) -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    BoxWithConstraints(modifier) {
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    val placed = remember(havenBoard.placed, boardKind) {
        HavenBoardRules.placedOn(havenBoard, boardKind)
    }
    val hand = remember(havenBoard.hand, boardKind) {
        HavenBoardRules.handForBoard(havenBoard, boardKind) +
            havenBoard.hand.filter { HavenBoardRules.def(it)?.kind == HavenCardKind.Upgrade }
    }
    var dragCardId by remember { mutableStateOf<String?>(null) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    var dragFromHand by remember { mutableStateOf(false) }
    val boardWidth = maxWidth
    val boardHeight = maxHeight

    placed.forEach { card ->
        val def = HavenBoardRules.def(card.cardId) ?: return@forEach
        val stackCount = card.upgradeIds.size
        val delta = if (dragCardId == card.cardId && !dragFromHand) dragDelta else Offset.Zero
        val centerX = boardWidth * card.x + with(density) { delta.x.toDp() }
        val centerY = boardHeight * card.y + with(density) { delta.y.toDp() }
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset(
                    x = (centerX - 36.dp).coerceAtLeast(0.dp),
                    y = (centerY - 52.dp).coerceAtLeast(0.dp),
                )
                .width(72.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .pointerInput(card.cardId) {
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
                                    dragCardId = card.cardId
                                    dragFromHand = false
                                }
                                if (dragged) dragDelta = amount
                            }
                            dragCardId = null
                            dragDelta = Offset.Zero
                            if (finished && dragged) {
                                val nx = (card.x + amount.x / size.width).coerceIn(0.08f, 0.92f)
                                val ny = (card.y + amount.y / size.height).coerceIn(0.12f, 0.88f)
                                dispatch(TextGameAction.MoveHavenCard(card.cardId, nx, ny))
                            } else if (finished) {
                                dispatch(TextGameAction.EnterHavenRoom(card.cardId))
                            }
                        }
                    },
            ) {
                if (stackCount > 0) {
                    Text(
                        "+$stackCount",
                        color = Color(0xFFE8D4A0),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color(0xCC1A140C), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
                AsyncImage(
                    model = "file:///android_asset/${def.cardArtPath}",
                    contentDescription = def.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .height(88.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .border(1.dp, Color(0xCCB89A62), RoundedCornerShape(4.dp)),
                )
            }
        }
    }

    if (hand.isNotEmpty()) {
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xCC1A140C))
                .padding(vertical = 6.dp, horizontal = 4.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Hand",
                color = Color(0xFFE0B24E),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 4.dp, end = 2.dp),
            )
            hand.forEach { cardId ->
                val def = HavenBoardRules.def(cardId) ?: return@forEach
                val isUpgrade = def.kind == HavenCardKind.Upgrade
                Box(
                    Modifier
                        .size(width = 56.dp, height = 78.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .border(1.dp, Color(0x88E8D4A0), RoundedCornerShape(4.dp))
                        .pointerInput(cardId, boardKind) {
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
                                        dragCardId = cardId
                                        dragFromHand = true
                                        dragDelta = amount
                                    }
                                    if (dragged) dragDelta = amount
                                }
                                dragCardId = null
                                dragDelta = Offset.Zero
                                if (!finished || !dragged) return@awaitEachGesture
                                val nx = (amount.x / size.width).coerceIn(0.08f, 0.92f)
                                val ny = ((size.height - 80f + amount.y) / size.height).coerceIn(0.12f, 0.88f)
                                if (isUpgrade) {
                                    val target = placed.minByOrNull { p ->
                                        val cx = p.x * size.width
                                        val cy = p.y * size.height
                                        val dx = amount.x - cx
                                        val dy = (size.height - 80f + amount.y) - cy
                                        dx * dx + dy * dy
                                    }
                                    if (target != null && def.stacksOn == target.cardId) {
                                        dispatch(TextGameAction.StackHavenUpgrade(target.cardId, cardId))
                                    }
                                } else {
                                    dispatch(TextGameAction.PlaceHavenCard(cardId, boardKind.id, nx, ny))
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = "file:///android_asset/${def.cardArtPath}",
                        contentDescription = def.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    dragCardId?.let { id ->
        val def = HavenBoardRules.def(id) ?: return@let
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset {
                    IntOffset(
                        dragDelta.x.roundToInt(),
                        dragDelta.y.roundToInt(),
                    )
                }
                .size(width = 64.dp, height = 90.dp)
                .clip(RoundedCornerShape(4.dp))
                .border(2.dp, Color(0xFFE8D4A0), RoundedCornerShape(4.dp)),
        ) {
            AsyncImage(
                model = "file:///android_asset/${def.cardArtPath}",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    Text(
        "Drag cards onto the plot · tap a building to enter",
        color = Color(0xCCF1E5D1),
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 6.dp)
            .background(Color(0x991A140C), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
    }
}
