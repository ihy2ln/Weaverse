package com.ihy2ln.weaverse.feature.novel.codex

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ihy2ln.weaverse.core.ui.components.InkDeleteButton
import com.ihy2ln.weaverse.core.ui.components.InkTextButton
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusSm
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.util.parseHexColor
import com.ihy2ln.weaverse.feature.roleplay.friends.CharacterAvatar
import java.io.File

/**
 * Section rule above a codex category — the same wide, letter-spaced small caps
 * the RPG Roster prints above "You" and "Team".
 */
@Composable
fun CodexCategoryHeader(
    label: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAdd: (() -> Unit)? = null,
) {
    val tokens = inkTokens()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(
                start = InkSpacing.lg,
                end = InkSpacing.md,
                top = InkSpacing.lg,
                bottom = InkSpacing.sm,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (expanded) "Collapse" else "Expand",
            modifier = Modifier.size(16.dp),
            tint = tokens.secondaryText,
        )
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            color = tokens.secondaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            modifier = Modifier.padding(start = InkSpacing.xs),
        )
        Text(
            "$count",
            style = MaterialTheme.typography.labelSmall,
            color = tokens.secondaryText,
            modifier = Modifier.padding(start = InkSpacing.sm),
        )
        Box(
            modifier = Modifier
                .padding(start = InkSpacing.sm)
                .weight(1f)
                .height(1.dp)
                .background(tokens.hairline),
        )
        if (onAdd != null) InkTextButton(label = "+", onClick = onAdd)
    }
}

/**
 * A codex entry drawn as a Roster plate: framed portrait, name, the category as
 * its type line, the CLASS / HP / AC strip off the entry's sheet, and what it
 * carries — the same shape the RPG party list uses.
 */
@Composable
fun CodexEntryPlate(
    entry: CodexEntryUi,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val tokens = inkTokens()
    val tint = parseHexColor(entry.colorHex, MaterialTheme.colorScheme.primary)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = InkSpacing.lg, vertical = InkSpacing.xs)
            .clip(RoundedCornerShape(inkRadiusSm()))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                } else {
                    tokens.panel
                },
            )
            .border(1.dp, tokens.hairline, RoundedCornerShape(inkRadiusSm()))
            .clickable(onClick = onClick)
            .padding(InkSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(inkRadiusSm()))
                .border(1.dp, tokens.hairline, RoundedCornerShape(inkRadiusSm())),
            contentAlignment = Alignment.Center,
        ) {
            if (entry.portraitPath.isNotBlank()) {
                AsyncImage(
                    model = File(entry.portraitPath),
                    contentDescription = "${entry.name} portrait",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CharacterAvatar(
                    name = entry.name.ifBlank { "Untitled" },
                    colorHex = entry.avatarColorHex,
                    size = 84.dp,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.name.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = tint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (onDelete != null) {
                    InkDeleteButton(
                        itemName = entry.name.ifBlank { "this entry" },
                        onConfirmedDelete = onDelete,
                    )
                }
            }
            if (entry.typeLine.isNotBlank()) {
                Text(
                    entry.typeLine,
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.sp,
                    color = tokens.activePill,
                )
            }
            if (entry.plainText.isNotBlank()) {
                Text(
                    entry.plainText,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.secondaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = InkSpacing.xs),
                )
            }
            if (entry.chips.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = InkSpacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
                ) {
                    entry.chips.forEach { (label, value) -> CodexStatChip(label, value) }
                }
            }
            if (entry.trailingLine.isNotBlank()) {
                Text(
                    entry.trailingLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = InkSpacing.xs),
                )
            }
            Text(
                entry.kind.ledgerVocabulary()
                    ?.let { "Open sheet & ${it.tabLabel.lowercase()} ›" }
                    ?: "Open sheet ›",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.activePill,
                modifier = Modifier.padding(top = InkSpacing.xs),
            )
        }
    }
}

/** Small-caps label over its value, the way a stat block prints one. */
@Composable
fun CodexStatChip(label: String, value: String) {
    val tokens = inkTokens()
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(inkRadiusSm()))
            .border(1.dp, tokens.hairline, RoundedCornerShape(inkRadiusSm()))
            .padding(horizontal = InkSpacing.sm, vertical = 3.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.5.sp,
            color = tokens.secondaryText,
        )
        Text(
            value.ifBlank { "—" },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
