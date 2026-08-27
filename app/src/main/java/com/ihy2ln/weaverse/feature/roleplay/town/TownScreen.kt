package com.ihy2ln.weaverse.feature.roleplay.town

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusSm
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import java.io.File
import kotlin.math.roundToInt

/** How many screens wide the town strip is; the camera pans across it. */
private const val WORLD_SCREENS = 3f

/**
 * A side-scrolling town you walk along. Buildings are hotspots at fixed
 * positions on the strip; stand at a door and you can go in.
 *
 * The backdrop is whatever image you point it at (Settings picks one into the
 * media library) — positions are percentages, so any art of any width lines up.
 * With no image set it draws a simple town so the screen still works.
 */
@Composable
fun TownScreen(viewModel: TownViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val tokens = inkTokens()
    val density = LocalDensity.current
    var showJump by remember { mutableStateOf(false) }

    val backgroundPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) viewModel.onBackgroundPicked(uri) }

    Column(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(inkRadiusSm()))
                .background(Color(0xFF6FB7E8)),
        ) {
            val screenW = maxWidth
            val worldW = screenW * WORLD_SCREENS
            // Camera keeps the player centred, clamped so it never runs off the art.
            val playerX = worldW * (state.playerPercent / 100f)
            val halfScreen = screenW / 2
            val cameraX = (playerX - halfScreen).coerceIn(0.dp, worldW - screenW)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        // Drag anywhere to walk; the world is wider than the screen.
                        detectDragGestures { change, drag ->
                            change.consume()
                            val worldPx = with(density) { worldW.toPx() }
                            viewModel.walk(-drag.x / worldPx * 100f)
                        }
                    },
            ) {
                Box(modifier = Modifier.offset(x = -cameraX)) {
                    if (state.backgroundPath.isNotBlank()) {
                        val context = LocalContext.current
                        AsyncImage(
                            model = remember(state.backgroundPath) {
                                ImageRequest.Builder(context)
                                    .data(File(state.backgroundPath))
                                    .build()
                            },
                            contentDescription = "Town",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier
                                .width(worldW)
                                .fillMaxHeight(),
                        )
                    } else {
                        FallbackTown(modifier = Modifier.width(worldW).fillMaxHeight())
                    }

                    // Doorway markers sit on the strip, so they scroll with the art.
                    TownMap.locations.forEach { location ->
                        val inReach = state.nearby?.id == location.id
                        Column(
                            modifier = Modifier
                                .offset(x = worldW * (location.xPercent / 100f) - 52.dp)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                        ) {
                            Text(
                                location.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .width(104.dp)
                                    .clip(RoundedCornerShape(inkRadiusSm()))
                                    .background(
                                        if (inReach) {
                                            tokens.activePill
                                        } else {
                                            Color.Black.copy(alpha = 0.45f)
                                        },
                                    )
                                    .clickable { viewModel.goTo(location) }
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                            )
                            Box(
                                modifier = Modifier
                                    .padding(bottom = 56.dp, top = 2.dp)
                                    .size(width = 3.dp, height = 18.dp)
                                    .background(
                                        if (inReach) tokens.activePill else Color.Black.copy(alpha = 0.35f),
                                    ),
                            )
                        }
                    }

                    Walker(
                        facingRight = state.facingRight,
                        modifier = Modifier
                            .offset(x = worldW * (state.playerPercent / 100f) - 12.dp)
                            .align(Alignment.BottomStart)
                            .padding(bottom = 40.dp),
                    )
                }
            }

            // Enter prompt only appears when you are actually at a door.
            state.nearby?.let { location ->
                Text(
                    "Enter ${location.name}",
                    style = MaterialTheme.typography.labelLarge,
                    color = tokens.activePillLabel,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(InkSpacing.sm)
                        .clip(RoundedCornerShape(inkRadiusSm()))
                        .background(tokens.activePill)
                        .clickable { viewModel.enter(location) }
                        .padding(horizontal = InkSpacing.md, vertical = InkSpacing.xs),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(InkSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm),
        ) {
            WalkButton("◀", tokens.hover, tokens.primaryText) { viewModel.walk(-3f) }
            WalkButton("▶", tokens.hover, tokens.primaryText) { viewModel.walk(3f) }
            Text(
                "Places",
                style = MaterialTheme.typography.labelMedium,
                color = tokens.activePill,
                modifier = Modifier
                    .clip(RoundedCornerShape(inkRadiusSm()))
                    .clickable { showJump = true }
                    .padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xs),
            )
            Box(modifier = Modifier.weight(1f))
            Text(
                "Backdrop",
                style = MaterialTheme.typography.labelMedium,
                color = tokens.secondaryText,
                modifier = Modifier
                    .clip(RoundedCornerShape(inkRadiusSm()))
                    .clickable {
                        backgroundPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    }
                    .padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xs),
            )
        }

        if (state.status.isNotBlank()) {
            Text(
                state.status,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.secondaryText,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.clearStatus() }
                    .padding(horizontal = InkSpacing.lg, vertical = InkSpacing.xs),
            )
        }
    }

    if (showJump) {
        AlertDialog(
            onDismissRequest = { showJump = false },
            title = { Text("Walk to") },
            text = {
                Column {
                    TownMap.locations.forEach { location ->
                        Text(
                            location.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.goTo(location)
                                    showJump = false
                                }
                                .padding(vertical = InkSpacing.sm),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showJump = false }) { Text("Close") }
            },
        )
    }

    state.openLocation?.let { location ->
        LocationSheet(
            location = location,
            onBuy = viewModel::buy,
            onAct = { viewModel.act(location, it) },
            onDismiss = viewModel::leave,
        )
    }
}

@Composable
private fun WalkButton(glyph: String, bg: Color, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = MaterialTheme.typography.titleMedium, color = tint)
    }
}

/** A small walking figure — enough to read as "you are here" without art assets. */
@Composable
private fun Walker(facingRight: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.width(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color(0xFF2B2B2B)),
        )
        Box(
            modifier = Modifier
                .padding(top = 1.dp)
                .size(width = 14.dp, height = 16.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF3D6FB4)),
        )
        Box(
            modifier = Modifier
                .padding(top = 1.dp)
                .size(width = if (facingRight) 12.dp else 12.dp, height = 8.dp)
                .background(Color(0xFF2B2B2B)),
        )
    }
}

/** Drawn town, used until a backdrop image is chosen. */
@Composable
private fun FallbackTown(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val skyH = size.height * 0.55f
        drawRect(Color(0xFF6FB7E8), size = androidx.compose.ui.geometry.Size(size.width, skyH))
        drawRect(
            Color(0xFF6E8F4E),
            topLeft = androidx.compose.ui.geometry.Offset(0f, skyH * 0.82f),
            size = androidx.compose.ui.geometry.Size(size.width, skyH * 0.22f),
        )
        drawRect(
            Color(0xFFC98B4B),
            topLeft = androidx.compose.ui.geometry.Offset(0f, skyH),
            size = androidx.compose.ui.geometry.Size(size.width, size.height - skyH),
        )
        // Rough building blocks at the doorway positions.
        TownMap.locations.forEach { location ->
            val cx = size.width * (location.xPercent / 100f)
            val w = size.width * 0.055f
            val h = size.height * 0.3f
            drawRect(
                Color(0xFFE8DCC2),
                topLeft = androidx.compose.ui.geometry.Offset(cx - w / 2f, skyH - h),
                size = androidx.compose.ui.geometry.Size(w, h),
            )
            drawRect(
                Color(0xFF9E4B32),
                topLeft = androidx.compose.ui.geometry.Offset(cx - w * 0.6f, skyH - h - h * 0.16f),
                size = androidx.compose.ui.geometry.Size(w * 1.2f, h * 0.16f),
            )
        }
    }
}

@Composable
private fun LocationSheet(
    location: TownLocation,
    onBuy: (ShopGood) -> Unit,
    onAct: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = inkTokens()
    val goods = TownMap.goodsFor(location.kind)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(location.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
                Text(
                    location.blurb,
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.secondaryText,
                )
                if (goods.isNotEmpty()) {
                    Text(
                        "FOR SALE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = tokens.secondaryText,
                        modifier = Modifier.padding(top = InkSpacing.sm),
                    )
                    goods.forEach { good ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(inkRadiusSm()))
                                .border(1.dp, tokens.hairline, RoundedCornerShape(inkRadiusSm()))
                                .clickable { onBuy(good) }
                                .padding(InkSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(good.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    good.note,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.secondaryText,
                                )
                            }
                            Text(
                                "Take",
                                style = MaterialTheme.typography.labelMedium,
                                color = tokens.activePill,
                            )
                        }
                    }
                }
                Text(
                    "HERE YOU CAN",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = tokens.secondaryText,
                    modifier = Modifier.padding(top = InkSpacing.sm),
                )
                location.actions.forEach { action ->
                    Text(
                        action,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.activePill,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAct(action) }
                            .padding(vertical = InkSpacing.xs),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Back to the street") }
        },
    )
}
