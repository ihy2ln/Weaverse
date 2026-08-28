package com.ihy2ln.weaverse.feature.roleplay.town

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusMd
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusSm
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import java.io.File

/** Visual town directory with a user-fillable picture slot for every place. */
@Composable
fun TownScreen(viewModel: TownViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val tokens = inkTokens()
    var imageTargetId by remember { mutableStateOf<String?>(null) }
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val target = imageTargetId
        imageTargetId = null
        if (uri != null && target != null) viewModel.onLocationImagePicked(target, uri)
    }
    fun pickFor(locationId: String) {
        imageTargetId = locationId
        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = InkSpacing.lg, vertical = InkSpacing.sm)) {
            Text("Town", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Tap a place to enter. Add your own picture to every location.",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.secondaryText,
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(164.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(InkSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(InkSpacing.sm),
        ) {
            items(TownMap.locations, key = { it.id }) { location ->
                val path = state.locationImagePaths[location.id].orEmpty()
                TownLocationCard(
                    location = location,
                    imagePath = path,
                    onPicture = { if (path.isBlank()) pickFor(location.id) else viewModel.enter(location) },
                    onReplacePicture = { pickFor(location.id) },
                    onOpen = { viewModel.enter(location) },
                )
            }
        }
        if (state.status.isNotBlank()) {
            Text(
                state.status,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.secondaryText,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.clearStatus() }
                    .padding(horizontal = InkSpacing.lg, vertical = InkSpacing.sm),
            )
        }
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
private fun TownLocationCard(
    location: TownLocation,
    imagePath: String,
    onPicture: () -> Unit,
    onReplacePicture: () -> Unit,
    onOpen: () -> Unit,
) {
    val tokens = inkTokens()
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(inkRadiusMd()))
            .background(tokens.panel)
            .border(1.dp, tokens.hairline, RoundedCornerShape(inkRadiusMd())),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .background(
                    Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), tokens.hover),
                    ),
                )
                .clickable(onClickLabel = if (imagePath.isBlank()) "Add picture" else "Enter ${location.name}") {
                    onPicture()
                },
            contentAlignment = Alignment.Center,
        ) {
            if (imagePath.isNotBlank()) {
                AsyncImage(
                    model = File(imagePath),
                    contentDescription = location.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Text(
                    "Tap to enter",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.56f))
                        .padding(InkSpacing.xs),
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("＋", style = MaterialTheme.typography.headlineMedium, color = tokens.activePill)
                    Text("Add picture", style = MaterialTheme.typography.labelMedium, color = tokens.activePill)
                }
            }
        }
        Column(modifier = Modifier.padding(InkSpacing.sm)) {
            Text(
                location.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                location.blurb,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.secondaryText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = InkSpacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    if (imagePath.isBlank()) "Picture" else "Change picture",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.secondaryText,
                    modifier = Modifier.clickable(onClick = onReplacePicture).padding(vertical = 4.dp),
                )
                Text(
                    "Open ›",
                    style = MaterialTheme.typography.labelMedium,
                    color = tokens.activePill,
                    modifier = Modifier.clickable(onClick = onOpen).padding(vertical = 4.dp),
                )
            }
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
                Text(location.blurb, style = MaterialTheme.typography.bodyMedium, color = tokens.secondaryText)
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
                                Text(good.note, style = MaterialTheme.typography.labelSmall, color = tokens.secondaryText)
                            }
                            Text("Take", style = MaterialTheme.typography.labelMedium, color = tokens.activePill)
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
                        modifier = Modifier.fillMaxWidth().clickable { onAct(action) }.padding(vertical = InkSpacing.xs),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Back to town") } },
    )
}
