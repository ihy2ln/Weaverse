package com.ihy2ln.weaverse.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.ihy2ln.weaverse.core.ui.util.AlwaysScrollEndPadding
import com.ihy2ln.weaverse.core.ui.util.adaptiveContentPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.ai.ModelInfo
import com.ihy2ln.weaverse.data.settings.ExtraPromptSurface
import com.ihy2ln.weaverse.core.ui.components.ExpandableSection
import com.ihy2ln.weaverse.core.ui.components.AppearanceSection
import com.ihy2ln.weaverse.core.ui.components.InkCard
import com.ihy2ln.weaverse.core.ui.components.InkConfirmButton
import com.ihy2ln.weaverse.core.ui.components.InkFilledButton
import com.ihy2ln.weaverse.core.ui.components.InkHsvColorWheel
import com.ihy2ln.weaverse.core.ui.components.InkOutlinedButton
import com.ihy2ln.weaverse.core.ui.components.InkSegmentedPill
import com.ihy2ln.weaverse.core.ui.components.SegmentedOption
import com.ihy2ln.weaverse.core.ui.theme.AppThemeMode
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.theme.toHexString
import com.ihy2ln.weaverse.core.ui.util.parseHexColor



@Composable

fun SettingsScreen(

    modifier: Modifier = Modifier,

    viewModel: SettingsViewModel = hiltViewModel(),

) {

    val state by viewModel.uiState.collectAsState()

    var appearanceExpanded by rememberSaveable { mutableStateOf(false) }
    var promptEntryExpanded by rememberSaveable { mutableStateOf(true) }
    var openRouterExpanded by rememberSaveable { mutableStateOf(true) }
    var modelsExpanded by rememberSaveable { mutableStateOf(false) }
    var otherProvidersExpanded by rememberSaveable { mutableStateOf(false) }
    var backupExpanded by rememberSaveable { mutableStateOf(false) }
    var syncExpanded by rememberSaveable { mutableStateOf(true) }
    var peerHost by rememberSaveable { mutableStateOf("") }
    var peerPin by rememberSaveable { mutableStateOf("") }
    var selectedSection by rememberSaveable { mutableStateOf(AppearanceSection.Chrome.name) }

    val backgroundPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.importBackground(uri)
    }



    val contentPad = adaptiveContentPadding()

    Column(

        modifier = modifier

            .fillMaxSize()

            .verticalScroll(rememberScrollState())

            .padding(contentPad),

    ) {

        ExpandableSection(

            title = "Appearance",

            expanded = appearanceExpanded,

            onToggle = { appearanceExpanded = !appearanceExpanded },

        ) {

            InkSegmentedPill(

                options = AppThemeMode.entries.map { SegmentedOption(it.name, it.name) },

                selectedId = state.prefs.themeMode.name,

                onSelect = { viewModel.setTheme(AppThemeMode.valueOf(it)) },

                modifier = Modifier.padding(vertical = InkSpacing.sm),

            )

            Text("Font size: ${state.prefs.fontSizeSp}sp")

            Slider(

                value = state.prefs.fontSizeSp.toFloat(),

                onValueChange = { viewModel.setFontSize(it.toInt()) },

                valueRange = 12f..28f,

                steps = 15,

            )

            Text("Line height: ${"%.1f".format(state.prefs.lineHeight)}")

            Slider(

                value = state.prefs.lineHeight,

                onValueChange = viewModel::setLineHeight,

                valueRange = 1.2f..2.2f,

                steps = 9,

            )

            Text(
                "Overall brightness: ${state.prefs.appBrightnessPercent}%",
                modifier = Modifier.padding(top = InkSpacing.sm),
            )
            Text(
                "Dims the whole app UI (independent of section colors)",
                style = MaterialTheme.typography.bodySmall,
                color = inkTokens().secondaryText,
            )
            Slider(
                value = state.prefs.appBrightnessPercent.toFloat(),
                onValueChange = { viewModel.setAppBrightness(it.toInt()) },
                valueRange = 5f..100f,
            )

            Text("Section colors", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = InkSpacing.md))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = InkSpacing.sm),
            ) {
                InkSegmentedPill(
                    options = AppearanceSection.entries.map { SegmentedOption(it.name, it.label) },
                    selectedId = selectedSection,
                    onSelect = { selectedSection = it },
                )
            }

            val section = AppearanceSection.valueOf(selectedSection)

            val sectionKey = section.storageKey

            val appearance = when (section) {

                AppearanceSection.Chrome -> state.prefs.appearance.chrome

                AppearanceSection.Rail -> state.prefs.appearance.rail

                AppearanceSection.Content -> state.prefs.appearance.content

                AppearanceSection.Page -> state.prefs.appearance.page

                AppearanceSection.ChatBubble -> state.prefs.appearance.chatBubble

            }

            val fallback = when (section) {

                AppearanceSection.Chrome -> inkTokens().background

                AppearanceSection.Rail -> inkTokens().panel

                AppearanceSection.Content -> inkTokens().background

                AppearanceSection.Page -> inkTokens().page

                AppearanceSection.ChatBubble -> inkTokens().hover

            }

            val currentColor = parseHexColor(appearance.colorHex, fallback)

                .copy(alpha = appearance.opacityPercent / 100f)

            InkHsvColorWheel(
                selected = currentColor,
                onSelect = { color ->
                    viewModel.setSectionAppearance(sectionKey, color.toHexString(), (color.alpha * 100).toInt())
                },
                opacityPercent = appearance.opacityPercent,
                onOpacityChange = { pct ->
                    viewModel.setSectionAppearance(sectionKey, appearance.colorHex.ifBlank { currentColor.toHexString() }, pct)
                },
            )
            InkOutlinedButton(
                label = "Reset section colors",
                onClick = viewModel::resetAppearanceColors,
                modifier = Modifier.padding(top = InkSpacing.sm),
            )

            Text(
                "Friends",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = InkSpacing.md),
            )
            Text(
                "Write one new person into your friends list each day. Needs an " +
                    "OpenRouter key; skipped silently when you're offline.",
                style = MaterialTheme.typography.bodySmall,
                color = inkTokens().secondaryText,
            )
            Row(modifier = Modifier.padding(top = InkSpacing.sm)) {
                if (state.prefs.dailyCharactersEnabled) {
                    InkOutlinedButton(
                        label = "Daily people on",
                        onClick = { viewModel.setDailyCharactersEnabled(false) },
                    )
                } else {
                    InkConfirmButton(
                        onClick = { viewModel.setDailyCharactersEnabled(true) },
                        label = "Daily people off",
                        contentDescription = "Generate one new character per day",
                    )
                }
            }

            Text(
                "Background media",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = InkSpacing.md),
            )
            Text(
                "Shell wallpaper (image applied; video stored)",
                style = MaterialTheme.typography.bodySmall,
                color = inkTokens().secondaryText,
            )
            Text(
                "Current: ${state.backgroundLabel}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = InkSpacing.xs),
            )
            if (state.backgroundNote.isNotBlank()) {
                Text(
                    state.backgroundNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = inkTokens().secondaryText,
                    modifier = Modifier.padding(top = InkSpacing.xs),
                )
            }
            Row(modifier = Modifier.padding(top = InkSpacing.sm)) {
                InkFilledButton(
                    label = "Add media",
                    onClick = {
                        backgroundPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                        )
                    },
                )
                InkOutlinedButton(
                    label = "Clear",
                    onClick = viewModel::clearBackground,
                    modifier = Modifier.padding(start = InkSpacing.sm),
                )
            }
        }

        ExpandableSection(
            title = "Prompt entry",
            subtitle = "PROMPT box is always on; extra generators stay in this menu",
            expanded = promptEntryExpanded,
            onToggle = { promptEntryExpanded = !promptEntryExpanded },
            modifier = Modifier.padding(top = InkSpacing.md),
        ) {
            Text(
                "The compact PROMPT box is always on. Turn on any extra generator you still want. They are not deleted.",
                style = MaterialTheme.typography.bodySmall,
                color = inkTokens().secondaryText,
                modifier = Modifier.padding(bottom = InkSpacing.sm),
            )
            val extras = state.prefs.extraPromptSurfaces
            PromptSurfaceToggle(
                label = "Inline writing",
                checked = extras.inlineWriting,
                onCheckedChange = { viewModel.setExtraPromptSurface(ExtraPromptSurface.InlineWriting, it) },
            )
            PromptSurfaceToggle(
                label = "SCENE BEAT card",
                checked = extras.sceneBeatCard,
                onCheckedChange = { viewModel.setExtraPromptSurface(ExtraPromptSurface.SceneBeatCard, it) },
            )
            PromptSurfaceToggle(
                label = "Continue under last line",
                checked = extras.continuation,
                onCheckedChange = { viewModel.setExtraPromptSurface(ExtraPromptSurface.Continuation, it) },
            )
            PromptSurfaceToggle(
                label = "Chat composer",
                checked = extras.chatComposer,
                onCheckedChange = { viewModel.setExtraPromptSurface(ExtraPromptSurface.ChatComposer, it) },
            )
            PromptSurfaceToggle(
                label = "Roleplay / AI · \\ manual",
                checked = extras.roleplayButtons,
                onCheckedChange = { viewModel.setExtraPromptSurface(ExtraPromptSurface.RoleplayButtons, it) },
            )
        }

        ExpandableSection(

            title = "AI Connections — OpenRouter",

            subtitle = "Key validated via GET /api/v1/key",

            expanded = openRouterExpanded,

            onToggle = { openRouterExpanded = !openRouterExpanded },

            modifier = Modifier.padding(top = InkSpacing.md),

        ) {

            OutlinedTextField(

                value = state.openRouterKey,

                onValueChange = viewModel::onOpenRouterKey,

                label = { Text("OpenRouter API key") },

                modifier = Modifier.fillMaxWidth(),

                singleLine = true,

            )

            Row(modifier = Modifier.padding(top = InkSpacing.sm)) {

                InkConfirmButton(

                    onClick = viewModel::saveOpenRouterKey,

                    enabled = !state.isValidatingKey,

                    contentDescription = "Save and validate API key",

                )

                InkOutlinedButton(

                    label = "Test connection",

                    onClick = viewModel::testConnection,

                    enabled = !state.isValidatingKey,

                    modifier = Modifier.padding(start = InkSpacing.sm),

                )

            }

            if (state.isValidatingKey) {

                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = InkSpacing.sm))

            }

            if (state.keyStatus.isNotBlank()) {

                Text(

                    state.keyStatus,

                    modifier = Modifier.padding(top = InkSpacing.sm),

                    color = if (state.keyStatusIsError) MaterialTheme.colorScheme.error

                    else MaterialTheme.colorScheme.onSurfaceVariant,

                )

            }

            state.openRouterKeyInfo?.let { info ->

                InkCard(modifier = Modifier.padding(top = InkSpacing.sm)) {

                    Text("Key info (from OpenRouter)", style = MaterialTheme.typography.labelLarge)

                    info.label?.let { Text("Label: $it") }

                    info.usage?.let { Text("Usage: $it") }

                    info.limit?.let { Text("Limit: $it") }

                    info.limitRemaining?.let { Text("Remaining: $it") }

                    info.isFreeTier?.let { Text("Free tier: $it") }

                    info.rateLimit?.let { rl ->

                        Text("Rate limit: ${rl.requests ?: "?"} / ${rl.interval ?: "?"}")

                    }

                }

            }

        }



        ExpandableSection(

            title = "Models",

            expanded = modelsExpanded,

            onToggle = { modelsExpanded = !modelsExpanded },

            modifier = Modifier.padding(top = InkSpacing.md),

        ) {

            Text(
                "Default: ${state.prefs.defaultModelRef}. Writing lists every OpenRouter text model after Refresh (search to filter).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(modifier = Modifier.padding(vertical = InkSpacing.sm)) {

                InkOutlinedButton(

                    label = if (state.isRefreshingModels) "Refreshing…" else "Refresh models",

                    onClick = viewModel::refreshModels,

                    enabled = !state.isRefreshingModels,

                )

            }

            InkSegmentedPill(

                options = listOf(

                    SegmentedOption(ModelListTab.Writing.name, "Writing"),

                    SegmentedOption(ModelListTab.TextToSpeech.name, "Text to speech"),

                    SegmentedOption(ModelListTab.All.name, "All"),

                ),

                selectedId = state.modelTab.name,

                onSelect = { viewModel.onModelTab(ModelListTab.valueOf(it)) },

            )

            OutlinedTextField(

                value = state.modelSearch,

                onValueChange = viewModel::onModelSearch,

                label = { Text("Search models") },

                modifier = Modifier.fillMaxWidth().padding(top = InkSpacing.sm),

                singleLine = true,

            )

            val list = when (state.modelTab) {

                ModelListTab.Writing -> state.writingModels

                ModelListTab.TextToSpeech -> state.ttsModels

                ModelListTab.All -> state.models

            }.filter { model ->
                state.modelSearch.isBlank() ||
                    model.id.contains(state.modelSearch, ignoreCase = true) ||
                    model.displayName.contains(state.modelSearch, ignoreCase = true) ||
                    model.tags.any { it.contains(state.modelSearch, ignoreCase = true) }
            }
            if (list.isEmpty()) {
                Text(
                    "No models cached. Save a valid OpenRouter key, then tap Refresh models.",
                    modifier = Modifier.padding(top = InkSpacing.sm),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "${list.size} models",
                    modifier = Modifier.padding(top = InkSpacing.sm),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                list.forEach { model ->
                    ModelRow(
                        model = model,
                        selected = state.prefs.defaultModelRef.endsWith(model.id),
                        onClick = { viewModel.selectDefaultModel(model.id, model.available) },
                    )
                }
            }

        }



        ExpandableSection(

            title = "Other providers",

            subtitle = "Stored locally, not validated here",

            expanded = otherProvidersExpanded,

            onToggle = { otherProvidersExpanded = !otherProvidersExpanded },

            modifier = Modifier.padding(top = InkSpacing.md),

        ) {

            OutlinedTextField(

                value = state.anthropicKey,

                onValueChange = viewModel::onAnthropicKey,

                label = { Text("Anthropic API key") },

                modifier = Modifier.fillMaxWidth(),

            )

            OutlinedTextField(

                value = state.openAiKey,

                onValueChange = viewModel::onOpenAiKey,

                label = { Text("OpenAI API key") },

                modifier = Modifier.fillMaxWidth().padding(top = InkSpacing.sm),

            )

            OutlinedTextField(

                value = state.geminiKey,

                onValueChange = viewModel::onGeminiKey,

                label = { Text("Gemini API key") },

                modifier = Modifier.fillMaxWidth().padding(top = InkSpacing.sm),

            )

            InkConfirmButton(

                onClick = viewModel::saveOtherKeys,

                label = "Save keys",

                contentDescription = "Save other keys",

                modifier = Modifier.padding(top = InkSpacing.sm),

            )

        }



        ExpandableSection(
            title = "Sync through the web version",
            expanded = syncExpanded,
            onToggle = { syncExpanded = !syncExpanded },
            modifier = Modifier.padding(top = InkSpacing.md),
        ) {
            val context = LocalContext.current
            Text(
                "Three versions: Android APK, desktop EXE, and the web hub. Sync always goes through the web page. Open that link, copy the password it shows, then Push or Pull.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = InkSpacing.sm),
            )
            OutlinedTextField(
                value = peerHost.ifBlank { state.sync.peerHost },
                onValueChange = {
                    peerHost = it
                    viewModel.setSyncPeer(it, peerPin.ifBlank { state.sync.peerPin })
                },
                label = { Text("Web link (from desktop or this phone)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = InkSpacing.sm),
                singleLine = true,
            )
            Row(modifier = Modifier.padding(bottom = InkSpacing.sm)) {
                InkConfirmButton(
                    onClick = {
                        val typed = peerHost.ifBlank { state.sync.peerHost }
                        if (typed.isNotBlank()) {
                            viewModel.setSyncPeer(typed, peerPin.ifBlank { state.sync.peerPin })
                        }
                        val url = viewModel.suggestedWebUrl()
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    },
                    label = "Open web sync",
                    contentDescription = "Open the web sync hub",
                )
                if (state.sync.hosting) {
                    InkOutlinedButton(
                        label = "Stop web hub",
                        onClick = viewModel::stopSyncHost,
                        modifier = Modifier.padding(start = InkSpacing.sm),
                    )
                } else {
                    InkConfirmButton(
                        onClick = viewModel::startSyncHost,
                        label = "Start web hub",
                        contentDescription = "Start the web sync hub on this phone",
                        modifier = Modifier.padding(start = InkSpacing.sm),
                    )
                }
            }
            Row(modifier = Modifier.padding(bottom = InkSpacing.sm)) {
                if (state.sync.autoSync) {
                    InkOutlinedButton(
                        label = "Auto-sync on",
                        onClick = { viewModel.setAutoSync(false) },
                    )
                } else {
                    InkConfirmButton(
                        onClick = { viewModel.setAutoSync(true) },
                        label = "Auto-sync off",
                        contentDescription = "Enable automatic web sync",
                    )
                }
            }
            OutlinedTextField(
                value = peerPin.ifBlank { state.sync.peerPin },
                onValueChange = {
                    peerPin = it
                    viewModel.setSyncPeer(peerHost.ifBlank { state.sync.peerHost }, it)
                },
                label = { Text("Password from the web page") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = InkSpacing.sm),
                singleLine = true,
            )
            Row {
                InkConfirmButton(
                    onClick = viewModel::pushSyncToPeer,
                    label = "Push to web",
                    contentDescription = "Push library to the web hub",
                )
                InkConfirmButton(
                    onClick = viewModel::pullSyncFromPeer,
                    label = "Pull from web",
                    contentDescription = "Pull library from the web hub",
                    modifier = Modifier.padding(start = InkSpacing.sm),
                )
            }
            if (state.sync.statusText.isNotBlank()) {
                Text(
                    state.sync.statusText,
                    modifier = Modifier.padding(top = InkSpacing.sm),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.sync.lastError.isNotBlank()) {
                Text(
                    state.sync.lastError,
                    modifier = Modifier.padding(top = InkSpacing.xs),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        ExpandableSection(

            title = "Backup & restore",

            expanded = backupExpanded,

            onToggle = { backupExpanded = !backupExpanded },

            modifier = Modifier.padding(top = InkSpacing.md),

        ) {

            Text(
                "Backup now writes two zip files: one for this phone (Restore) and one for PC (extract into the Weaverse folder that contains data/). Copies also go to Android/data/…/files/backups so you can copy them off the device.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = InkSpacing.sm),
            )

            Row {

                InkConfirmButton(

                    onClick = viewModel::exportBackup,

                    label = "Backup now",

                    contentDescription = "Backup app database",

                )

                InkConfirmButton(

                    onClick = viewModel::restoreBackup,

                    label = "Restore",

                    contentDescription = "Restore latest backup",

                    modifier = Modifier.padding(start = InkSpacing.sm),

                )

            }

            if (state.exportStatus.isNotBlank()) {

                Text(

                    state.exportStatus,

                    modifier = Modifier.padding(top = InkSpacing.sm),

                    color = MaterialTheme.colorScheme.onSurfaceVariant,

                )

            }

        }

        Spacer(modifier = Modifier.height(AlwaysScrollEndPadding))

    }

}



@Composable
private fun ModelRow(
    model: ModelInfo,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val muted = !model.available
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
            )
            .clickable(enabled = model.available, onClick = onClick)
            .padding(InkSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                buildString {
                    append(model.displayName)
                    if (model.tags.isNotEmpty()) {
                        append(" · ")
                        append(model.tags.joinToString(" · "))
                    }
                },
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (muted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.onSurface,
            )
            val price = buildString {
                append(model.id)
                model.contextLength?.let { append(" · ctx $it") }
                val p = model.promptPricePerMillion
                val c = model.completionPricePerMillion
                if (p != null || c != null) {
                    append(" · $")
                    append(p?.let { "%.2f".format(it) } ?: "?")
                    append(" / $")
                    append(c?.let { "%.2f".format(it) } ?: "?")
                    append(" per M")
                }
                if (muted) append(" · unavailable")
            }
            Text(
                price,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (muted) 0.4f else 1f),
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PromptSurfaceToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = InkSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                checkmarkColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )
        Text(label, color = MaterialTheme.colorScheme.onSurface)
    }
}


