package com.ihy2ln.weaverse.feature.prompt

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusMd
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import com.ihy2ln.weaverse.feature.novel.codex.BangCommandInfo
import com.ihy2ln.weaverse.feature.novel.codex.effectiveBangCommands
import com.ihy2ln.weaverse.feature.roleplay.chat.RpgTurnCommand
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Exposes the effective `!` command list (defaults + custom − removed) so the
 * composer popup always matches what is configured in Settings.
 */
@HiltViewModel
class BangCommandsViewModel @Inject constructor(
    settings: SettingsRepository,
) : ViewModel() {
    val commands: StateFlow<List<BangCommandInfo>> = settings.preferences
        .map { prefs -> effectiveBangCommands(prefs.customBangCommands, prefs.removedBangKeywords) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = effectiveBangCommands(),
        )

    val starCommands: StateFlow<List<RpgTurnCommand>> = settings.preferences
        .map { prefs ->
            com.ihy2ln.weaverse.feature.roleplay.chat.RpgTurnCommands.effectiveCommands(
                prefs.customStarCommands,
                prefs.removedStarKeywords,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = com.ihy2ln.weaverse.feature.roleplay.chat.RpgTurnCommands.effectiveCommands(),
        )
}

/** One selectable row in the command preview popup. */
data class CommandPreviewRow(
    /** The command as typed, e.g. "!location", "/". */
    val trigger: String,
    val description: String,
)

/**
 * Popup window that appears the moment a command/hotkey key is pressed,
 * floating above the composer: every possible command with a description.
 * Tapping a row fills its trigger into the composer.
 */
@Composable
fun CommandPreviewPopup(
    rows: List<CommandPreviewRow>,
    onSelect: (CommandPreviewRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (rows.isEmpty()) return
    val tokens = inkTokens()
    Column(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(inkRadiusMd()))
            .clip(RoundedCornerShape(inkRadiusMd()))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, tokens.hairline, RoundedCornerShape(inkRadiusMd()))
            .heightIn(max = 220.dp)
            .verticalScroll(rememberScrollState())
            .padding(vertical = InkSpacing.xxs),
    ) {
        rows.forEach { row ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(row) }
                    .padding(horizontal = InkSpacing.md, vertical = InkSpacing.xs),
            ) {
                Text(
                    row.trigger,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    row.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.secondaryText,
                )
            }
        }
        Text(
            "Type to filter · tap a command",
            style = MaterialTheme.typography.labelSmall,
            color = tokens.secondaryText.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = InkSpacing.md, vertical = InkSpacing.xs),
        )
    }
}
