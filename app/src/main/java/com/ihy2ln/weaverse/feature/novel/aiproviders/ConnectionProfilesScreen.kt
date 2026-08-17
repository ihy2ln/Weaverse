package com.ihy2ln.weaverse.feature.novel.aiproviders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.ai.providers.OpenRouterKeyInfo
import com.ihy2ln.weaverse.core.ui.EmptyState
import com.ihy2ln.weaverse.core.ui.InkCard
import com.ihy2ln.weaverse.core.ui.Spacing
import com.ihy2ln.weaverse.data.db.entity.AIProviderType
import com.ihy2ln.weaverse.data.db.entity.ConnectionProfileEntity

/** AI provider connection management (spec §8.1), reachable from Settings. */
@Composable
fun ConnectionProfilesScreen(
    modifier: Modifier = Modifier,
    viewModel: ConnectionProfilesViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsState()
    val testResults by viewModel.testResults.collectAsState()
    val keyInfo by viewModel.keyInfo.collectAsState()
    var addDialogOpen by remember { mutableStateOf(false) }
    var editKeyTarget by remember { mutableStateOf<ConnectionProfileEntity?>(null) }

    Column(modifier = modifier.fillMaxWidth().padding(Spacing.lg)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("AI Providers", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = { addDialogOpen = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Add", modifier = Modifier.padding(start = Spacing.xs))
            }
        }
        Spacer(modifier = Modifier.height(Spacing.md))

        if (profiles.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Add,
                title = "No connection profiles yet",
                subtitle = "Add Anthropic, OpenRouter, an OpenAI-compatible endpoint, or Gemini to enable real generations.",
                actionLabel = "Add provider",
                onAction = { addDialogOpen = true },
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(items = profiles, key = { it.id }) { profile ->
                    ConnectionProfileRow(
                        profile = profile,
                        hasApiKey = viewModel.hasApiKey(profile.id),
                        testState = testResults[profile.id],
                        keyInfo = keyInfo[profile.id],
                        onTest = { viewModel.testConnection(profile) },
                        onDelete = { viewModel.deleteProfile(profile) },
                        onFetchKeyInfo = { viewModel.fetchKeyInfo(profile) },
                        onEditKey = { editKeyTarget = profile },
                    )
                }
            }
        }
    }

    if (addDialogOpen) {
        AddConnectionProfileDialog(
            onDismiss = { addDialogOpen = false },
            onCreate = { type, label, baseUrl, apiKey ->
                viewModel.addProfile(type, label, baseUrl, apiKey)
                addDialogOpen = false
            },
        )
    }

    editKeyTarget?.let { profile ->
        EditApiKeyDialog(
            profileLabel = profile.label,
            onDismiss = { editKeyTarget = null },
            onSave = { newKey ->
                viewModel.updateApiKey(profile, newKey)
                editKeyTarget = null
            },
        )
    }
}

@Composable
private fun ConnectionProfileRow(
    profile: ConnectionProfileEntity,
    hasApiKey: Boolean,
    testState: ConnectionTestState?,
    keyInfo: OpenRouterKeyInfo?,
    onTest: () -> Unit,
    onDelete: () -> Unit,
    onFetchKeyInfo: () -> Unit,
    onEditKey: () -> Unit,
) {
    InkCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(profile.label, style = MaterialTheme.typography.titleMedium)
                    Text(profile.providerType.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(profile.baseUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row {
                    IconButton(onClick = onEditKey) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit API key for ${profile.label}")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete ${profile.label}")
                    }
                }
            }
            if (!hasApiKey) {
                Text(
                    "No API key — generations on this profile will fail until one is added.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.sm)) {
                TextButton(onClick = onTest, enabled = testState != ConnectionTestState.Testing) {
                    Text("Test connection")
                }
                when (testState) {
                    is ConnectionTestState.Testing -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    is ConnectionTestState.Success -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            "${testState.modelCount} models",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = Spacing.xs),
                        )
                    }
                    is ConnectionTestState.Failure -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(
                            testState.message,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = Spacing.xs),
                        )
                    }
                    null -> Unit
                }
            }
            if (profile.providerType == AIProviderType.OpenRouter && hasApiKey) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (keyInfo != null) {
                        Column {
                            keyInfo.label?.let {
                                Text("Key label: $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                if (keyInfo.limitRemaining != null) "Credits remaining: ${keyInfo.limitRemaining}" else "Credits: unlimited (no spending cap on this key)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (keyInfo.isFreeTier == true) {
                                Text("Free-tier key", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (keyInfo.rateLimitRequests != null && keyInfo.rateLimitInterval != null) {
                                Text(
                                    "Rate limit: ${keyInfo.rateLimitRequests} requests / ${keyInfo.rateLimitInterval}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        TextButton(onClick = onFetchKeyInfo) { Text("Check key info") }
                    }
                }
            }
        }
    }
}

/** Plain [DropdownMenu] anchored to a button rather than `ExposedDropdownMenuBox` — the latter's
 * `menuAnchor()` modifier has changed shape across Compose Material3 releases, and this simpler,
 * long-stable combination avoids depending on the exact overload our BOM version has. */
@Composable
private fun AddConnectionProfileDialog(
    onDismiss: () -> Unit,
    onCreate: (AIProviderType, String, String, String) -> Unit,
) {
    var providerType by remember { mutableStateOf(AIProviderType.Anthropic) }
    var expanded by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf("") }
    var baseUrl by remember(providerType) { mutableStateOf(defaultBaseUrlFor(providerType)) }
    var apiKey by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add connection profile") },
        text = {
            Column {
                Text("Provider", style = MaterialTheme.typography.labelLarge)
                Box(modifier = Modifier.padding(top = Spacing.xs)) {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(providerType.name, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        AIProviderType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = { providerType = type; expanded = false },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(providerType, label, baseUrl, apiKey) }) {
                Text("Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Fixes a real gap: the only way to set a key was creating a brand-new profile — an existing
 * profile with a wrong/expired/mistyped key had no way to fix it short of delete-and-recreate. */
@Composable
private fun EditApiKeyDialog(
    profileLabel: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var apiKey by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit API key") },
        text = {
            Column {
                Text(
                    "For \"$profileLabel\". The current key is never shown here — paste a new one to replace it, or leave blank and save to clear it. A new key is verified against the provider before it's stored — if verification fails, the old key (if any) is kept and the real error is shown below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(apiKey) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
