package com.ihy2ln.weaverse.feature.roleplay.rpg

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.core.ui.components.InkCard
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.alwaysScrollEndSpacer
import com.ihy2ln.weaverse.feature.roleplay.chat.roleplayModeSubtitle
import com.ihy2ln.weaverse.sync.adams.RpgCard
import com.ihy2ln.weaverse.sync.adams.RpgScene

@Composable
fun RpgScenesScreen(
    onSceneClick: (String) -> Unit,
    onCharacterClick: (String) -> Unit = {},
    viewModel: RpgScenesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val tokens = inkTokens()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 220.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(InkSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(InkSpacing.md),
        verticalArrangement = Arrangement.spacedBy(InkSpacing.md),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text("Adams Haven RPG", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Scenes and gacha cards from the lane-tactics game — farm, town, and three-lane battles.",
                    color = tokens.secondaryText,
                    modifier = Modifier.padding(top = InkSpacing.xs, bottom = InkSpacing.sm),
                )
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("Scenes", style = MaterialTheme.typography.titleMedium)
        }
        items(state.scenes, key = { it.id }) { scene ->
            SceneCard(scene = scene, onClick = { onSceneClick(scene.id) })
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "Cards",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = InkSpacing.sm),
            )
        }
        items(state.cards, key = { it.id }) { card ->
            val openId = state.characterIds[card.id] ?: card.id
            CardTile(card = card, onClick = { onCharacterClick(openId) })
        }
        alwaysScrollEndSpacer()
    }
}

@Composable
private fun SceneCard(scene: RpgScene, onClick: () -> Unit) {
    val tokens = inkTokens()
    InkCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Text(
            roleplayModeSubtitle(scene.displayMode),
            color = tokens.secondaryText,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            scene.title.removePrefix("RPG · "),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = InkSpacing.xs),
        )
        Text(
            scene.location,
            color = tokens.secondaryText,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = InkSpacing.xxs),
        )
        Text(
            scene.blurb,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = InkSpacing.sm),
        )
    }
}

@Composable
private fun CardTile(card: RpgCard, onClick: () -> Unit) {
    val tokens = inkTokens()
    val meta = listOfNotNull(card.classType, card.element, card.age).joinToString(" · ")
    InkCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Text(card.name, style = MaterialTheme.typography.titleMedium)
        if (meta.isNotBlank()) {
            Text(meta, color = tokens.secondaryText, fontSize = 12.sp)
        }
        Text(
            card.description.take(140),
            style = MaterialTheme.typography.bodySmall,
            color = tokens.secondaryText,
            modifier = Modifier.padding(top = InkSpacing.sm),
            maxLines = 4,
        )
    }
}
