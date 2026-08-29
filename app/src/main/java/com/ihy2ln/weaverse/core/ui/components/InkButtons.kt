package com.ihy2ln.weaverse.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing

private val ConfirmIconSize = 20.dp
private val UnenabledTint @Composable get() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

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

/**
 * Prompt ✓ action: tap confirms; long-press opens a symbol-only menu combining
 * ✓ confirm, ↻ retry/resubmit, and » continue. Without extra actions it behaves
 * as a plain ✓ button.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PromptActionMenuButton(
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onRetry: (() -> Unit)? = null,
    onContinue: (() -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val hasMenu = onRetry != null || onContinue != null
    Box(modifier) {
        Box(
            modifier = Modifier
                .size(PromptActionButtonSize)
                .combinedClickable(
                    onClick = { if (enabled) onConfirm() },
                    // Hold opens the menu even when the box is empty, so continue
                    // and retry stay reachable without typing first.
                    onLongClick = { if (hasMenu) menuOpen = true },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = if (hasMenu) {
                    "Confirm — hold for retry and continue"
                } else {
                    "Confirm"
                },
                modifier = Modifier.size(PromptActionIconSize),
                tint = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            Row(
                modifier = Modifier.padding(horizontal = InkSpacing.xs, vertical = InkSpacing.xxs),
                horizontalArrangement = Arrangement.spacedBy(InkSpacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { menuOpen = false; onConfirm() }, enabled = enabled) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Confirm",
                        tint = if (enabled) MaterialTheme.colorScheme.primary else UnenabledTint,
                    )
                }
                if (onRetry != null) {
                    IconButton(onClick = { menuOpen = false; onRetry() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (onContinue != null) {
                    IconButton(onClick = { menuOpen = false; onContinue() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Continue",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * RPG composer control: tap starts voice input; hold opens a symbol-only menu
 * combining add-media (+), roll (dice), new roster character (👤+), new
 * inventory item (bag), and microphone (🎤) into one spot.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ComposerMenuButton(
    onMicTap: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onAdd: (() -> Unit)? = null,
    onRoll: (() -> Unit)? = null,
    onAddCharacter: (() -> Unit)? = null,
    onAddItem: (() -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val hasMenu = onAdd != null || onRoll != null || onAddCharacter != null || onAddItem != null
    Box(modifier) {
        Box(
            modifier = Modifier
                .size(PromptActionButtonSize)
                .combinedClickable(
                    onClick = { if (enabled) onMicTap() },
                    onLongClick = { if (enabled && hasMenu) menuOpen = true },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Voice — hold for add media, roll, roster, and inventory",
                modifier = Modifier.size(PromptActionIconSize),
                tint = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    UnenabledTint
                },
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            Row(
                modifier = Modifier.padding(horizontal = InkSpacing.xs, vertical = InkSpacing.xxs),
                horizontalArrangement = Arrangement.spacedBy(InkSpacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onAdd != null) {
                    IconButton(onClick = { menuOpen = false; onAdd() }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add media",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (onRoll != null) {
                    IconButton(onClick = { menuOpen = false; onRoll() }) {
                        Icon(
                            imageVector = Icons.Default.Casino,
                            contentDescription = "Roll",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (onAddCharacter != null) {
                    IconButton(onClick = { menuOpen = false; onAddCharacter() }) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = "Add roster character",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (onAddItem != null) {
                    IconButton(onClick = { menuOpen = false; onAddItem() }) {
                        Icon(
                            imageVector = Icons.Default.ShoppingBag,
                            contentDescription = "Add inventory item",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                IconButton(onClick = { menuOpen = false; onMicTap() }) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
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
