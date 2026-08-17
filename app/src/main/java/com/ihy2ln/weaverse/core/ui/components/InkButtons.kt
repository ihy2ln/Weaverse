package com.ihy2ln.weaverse.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing

private val ConfirmIconSize = 20.dp

/** Filled button with explicit onPrimary label color (fixes invisible text on dark fills). */
@Composable
fun InkFilledButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = inkFilledColors(),
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Primary confirm / accept / save control: always shows a ✓ with onPrimary contrast.
 * Use [label] when the action needs a word (e.g. Export / Restore); omit for icon-only.
 */
@Composable
fun InkConfirmButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    contentDescription: String = label ?: "Confirm",
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = inkFilledColors(),
        contentPadding = if (label == null) {
            PaddingValues(horizontal = 14.dp, vertical = 10.dp)
        } else {
            ButtonDefaults.ContentPadding
        },
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = contentDescription,
            modifier = Modifier
                .padding(end = if (label != null) 6.dp else 0.dp)
                .size(ConfirmIconSize),
            tint = MaterialTheme.colorScheme.onPrimary,
        )
        if (label != null) {
            Text(
                label,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Outlined button with readable label on any theme. */
@Composable
fun InkOutlinedButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        ),
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun InkTextButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    TextButton(
        onClick = onClick,
        modifier = if (compact) modifier.heightIn(min = 28.dp, max = 32.dp) else modifier,
        enabled = enabled,
        contentPadding = if (compact) {
            PaddingValues(horizontal = InkSpacing.sm, vertical = 0.dp)
        } else {
            ButtonDefaults.TextButtonContentPadding
        },
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
        ),
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val PromptActionIconSize = 18.dp
private val PromptActionButtonSize = 32.dp

/** Compact ✓ for Accept / Add / Generate inside the prompt box. */
@Composable
fun InkCheckIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String = "Accept",
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(PromptActionButtonSize),
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = contentDescription,
            modifier = Modifier.size(PromptActionIconSize),
            tint = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
        )
    }
}

/** Compact X for Clear inside the prompt box. */
@Composable
fun InkClearIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String = "Clear",
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(PromptActionButtonSize),
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = contentDescription,
            modifier = Modifier.size(PromptActionIconSize),
            tint = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
        )
    }
}

/** Large tap targets for the global / AI and \\ manual prompt commands. */
@Composable
fun PromptCommandButtons(
    onAi: () -> Unit,
    onManual: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
    ) {
        InkFilledButton(
            label = "/ AI",
            onClick = onAi,
            enabled = enabled,
            modifier = Modifier.heightIn(min = 48.dp),
        )
        InkOutlinedButton(
            label = "\\ manual",
            onClick = onManual,
            enabled = enabled,
            modifier = Modifier.heightIn(min = 48.dp),
        )
    }
}

@Composable
private fun inkFilledColors() = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.primary,
    contentColor = MaterialTheme.colorScheme.onPrimary,
    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
    disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f),
)
