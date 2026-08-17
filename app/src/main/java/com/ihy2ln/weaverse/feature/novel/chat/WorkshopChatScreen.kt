package com.ihy2ln.weaverse.feature.novel.chat



import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.verticalScroll

import androidx.compose.material3.AlertDialog

import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.Text

import androidx.compose.material3.TextButton

import androidx.compose.runtime.Composable

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.collectAsState

import androidx.compose.runtime.getValue

import androidx.compose.ui.Modifier

import androidx.compose.ui.text.style.LineHeightStyle

import androidx.compose.ui.unit.sp

import androidx.hilt.navigation.compose.hiltViewModel

import com.ihy2ln.weaverse.core.ui.components.ChatComposerRow

import com.ihy2ln.weaverse.core.ui.components.CollapsibleUsageStrip

import com.ihy2ln.weaverse.core.ui.components.InkCard

import com.ihy2ln.weaverse.core.ui.components.InkChip

import com.ihy2ln.weaverse.core.ui.components.InkTextButton

import com.ihy2ln.weaverse.core.ui.theme.CodexCharacters

import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.util.alwaysScrollEndSpacer

import com.ihy2ln.weaverse.core.ui.util.parseHexColor



@Composable

fun WorkshopChatScreen(

    threadId: String? = null,

    viewModel: WorkshopChatViewModel = hiltViewModel(),

) {

    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(threadId) {

        viewModel.selectThread(threadId ?: state.threadId)

    }

    val compactStyle = MaterialTheme.typography.bodyMedium.copy(

        lineHeight = 20.sp,

        lineHeightStyle = LineHeightStyle(

            alignment = LineHeightStyle.Alignment.Center,

            trim = LineHeightStyle.Trim.None,

        ),

    )

    Column(modifier = Modifier.fillMaxSize().padding(InkSpacing.lg)) {

        Text("Workshop Chats", style = MaterialTheme.typography.titleLarge)

        LazyColumn(modifier = Modifier.weight(1f)) {

            items(state.messages, key = { it.id }) { message ->

                InkCard(modifier = Modifier.padding(vertical = InkSpacing.xs)) {

                    Text(message.role.uppercase(), style = MaterialTheme.typography.labelMedium)

                    Text(message.plainText, style = compactStyle, modifier = Modifier.padding(top = InkSpacing.xxs))

                }

            }

            if (state.isStreaming && state.streamingText.isNotBlank()) {

                item("streaming") {

                    InkCard(modifier = Modifier.padding(vertical = InkSpacing.xs)) {

                        Text("ASSISTANT · streaming", style = MaterialTheme.typography.labelMedium)

                        Text(state.streamingText, style = compactStyle, modifier = Modifier.padding(top = InkSpacing.xxs))

                    }

                }

            }

            alwaysScrollEndSpacer()

        }

        Row(

            modifier = Modifier

                .fillMaxWidth()

                .padding(vertical = InkSpacing.sm),

        ) {

            state.contextChips.forEach { chip ->

                InkChip(

                    label = chip.name,

                    color = parseHexColor(chip.colorHex, CodexCharacters),

                    onRemove = { viewModel.removeChip(chip.entryId) },

                    modifier = Modifier.padding(end = InkSpacing.xs),

                )

            }

            InkTextButton(label = "+ Codex", onClick = viewModel::openCodexPicker)

        }

        if (state.showPreview && state.previewPrompt.isNotBlank()) {

            InkCard(modifier = Modifier.padding(bottom = InkSpacing.sm)) {

                Text("Preview prompt", style = MaterialTheme.typography.labelLarge)

                Text(state.previewPrompt, style = MaterialTheme.typography.bodySmall)

            }

        }

        if (state.errorMessage.isNotBlank()) {

            Text(

                state.errorMessage,

                color = MaterialTheme.colorScheme.error,

                modifier = Modifier.padding(bottom = InkSpacing.sm),

            )

        }

        CollapsibleUsageStrip(usageText = state.lastUsage)

        if (state.showExtraPromptSurfaces) {

            ChatComposerRow(

                value = state.input,

                onValueChange = viewModel::onInputChange,

                onSend = viewModel::send,

                placeholder = "Message the workshop…",

                sendLabel = "Send",

                enabled = !state.isStreaming,

                onClear = { viewModel.onInputChange("") },

                modifier = Modifier.padding(top = InkSpacing.sm),

            )

        }

        InkTextButton(

            label = if (state.showPreview) "Hide preview" else "Preview prompt",

            onClick = viewModel::togglePreview,

        )

    }

    if (state.showCodexPicker) {

        AlertDialog(

            onDismissRequest = viewModel::dismissCodexPicker,

            title = { Text("Include codex entry") },

            text = {

                Column(

                    modifier = Modifier

                        .fillMaxWidth()

                        .verticalScroll(rememberScrollState()),

                ) {

                    state.codexEntries.forEach { entry ->

                        Row(

                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,

                            modifier = Modifier.fillMaxWidth(),

                        ) {

                            androidx.compose.material3.Checkbox(

                                checked = entry.included,

                                onCheckedChange = { checked ->

                                    viewModel.toggleCodexEntry(entry.id, checked)

                                },

                                colors = androidx.compose.material3.CheckboxDefaults.colors(

                                    checkedColor = MaterialTheme.colorScheme.primary,

                                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,

                                    checkmarkColor = MaterialTheme.colorScheme.onPrimary,

                                ),

                            )

                            TextButton(onClick = { viewModel.toggleCodexEntry(entry.id, !entry.included) }) {

                                Text(entry.name, color = MaterialTheme.colorScheme.onSurface)

                            }

                        }

                    }

                }

            },

            confirmButton = {

                TextButton(onClick = viewModel::dismissCodexPicker) { Text("Close") }

            },

        )

    }

}


