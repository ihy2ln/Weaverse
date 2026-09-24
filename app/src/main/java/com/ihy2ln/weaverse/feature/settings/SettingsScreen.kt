package com.ihy2ln.weaverse.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.runtime.CompositionLocalProvider
import com.ihy2ln.weaverse.core.ui.theme.LocalAppearanceProfile
import com.ihy2ln.weaverse.core.ui.theme.LocalInkTokens
import com.ihy2ln.weaverse.core.ui.theme.ProfileBackgroundArt
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.SliderDefaults
import com.ihy2ln.weaverse.core.ui.components.glassPanel
import com.ihy2ln.weaverse.core.ui.theme.AppBackdrop
import com.ihy2ln.weaverse.core.ui.theme.BackdropStyle
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusMd
import com.ihy2ln.weaverse.data.settings.UserPreferences
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ihy2ln.weaverse.ai.ModelInfo
import com.ihy2ln.weaverse.data.settings.ExtraPromptSurface
import com.ihy2ln.weaverse.feature.help.HelpScreen
import com.ihy2ln.weaverse.feature.help.WikiScreen
import com.ihy2ln.weaverse.core.ui.components.ExpandableSection
import com.ihy2ln.weaverse.core.ui.components.InkCard
import com.ihy2ln.weaverse.core.ui.components.InkConfirmButton
import com.ihy2ln.weaverse.core.ui.components.InkFilledButton
import com.ihy2ln.weaverse.core.ui.components.InkOutlinedButton
import com.ihy2ln.weaverse.core.ui.components.InkSegmentedPill
import com.ihy2ln.weaverse.core.ui.components.SegmentedOption
import com.ihy2ln.weaverse.core.ui.theme.AppThemeMode
import com.ihy2ln.weaverse.core.ui.theme.AppearanceProfile
import com.ihy2ln.weaverse.core.ui.theme.isDark
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.core.ui.theme.inkRadiusSm
import com.ihy2ln.weaverse.feature.novel.codex.CodexBang
import com.ihy2ln.weaverse.feature.novel.codex.CodexEntryKind
import com.ihy2ln.weaverse.feature.novel.codex.effectiveBangCommands
import com.ihy2ln.weaverse.feature.roleplay.chat.RpgTurnCommands
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription



@Composable

fun SettingsScreen(

    modifier: Modifier = Modifier,

    viewModel: SettingsViewModel = hiltViewModel(),

) {

    val state by viewModel.uiState.collectAsState()

    var appearanceExpanded by rememberSaveable { mutableStateOf(true) }
    var generalExpanded by rememberSaveable { mutableStateOf(false) }
    var promptEntryExpanded by rememberSaveable { mutableStateOf(true) }
    var topicMediaExpanded by rememberSaveable { mutableStateOf(true) }
    var openRouterExpanded by rememberSaveable { mutableStateOf(true) }
    var modelsExpanded by rememberSaveable { mutableStateOf(false) }
    var otherProvidersExpanded by rememberSaveable { mutableStateOf(false) }
    var backupExpanded by rememberSaveable { mutableStateOf(false) }
    var syncExpanded by rememberSaveable { mutableStateOf(true) }
    var peerHost by rememberSaveable { mutableStateOf("") }
    var peerPin by rememberSaveable { mutableStateOf("") }
    var commandsExpanded by rememberSaveable { mutableStateOf(false) }
    var newCommandDraft by rememberSaveable { mutableStateOf("") }
    var newCommandKind by rememberSaveable { mutableStateOf(CodexEntryKind.Other.name) }
    var newStarDraft by rememberSaveable { mutableStateOf("") }
    var newStarDescription by rememberSaveable { mutableStateOf("") }
    var newStarRoll by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var showWiki by rememberSaveable { mutableStateOf(false) }

    val backgroundPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.importBackground(uri)
    }
    val topicMediaFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) viewModel.chooseTopicMediaFolder(uri)
    }
    var topicMediaRootDraft by rememberSaveable(state.prefs.topicMediaLibraryRoot) {
        mutableStateOf(state.prefs.topicMediaLibraryRoot)
    }



    val contentPad = adaptiveContentPadding()

    androidx.compose.foundation.layout.Box(modifier = modifier.fillMaxSize()) {

    Column(

        modifier = Modifier

            .fillMaxSize()

            .verticalScroll(rememberScrollState())

            .padding(contentPad),

    ) {

        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = inkTokens().primaryText,
            modifier = Modifier.padding(top = InkSpacing.sm),
        )
        Text(
            "Make Weaverse yours — every page follows these choices.",
            style = MaterialTheme.typography.bodyMedium,
            color = inkTokens().secondaryText,
            modifier = Modifier.padding(bottom = InkSpacing.lg),
        )

        ExpandableSection(
            title = "Appearance",
            subtitle = "${state.prefs.appearanceProfile.label} · ${wallpaperLabel(state.prefs)} · text ${state.prefs.uiTextScalePercent}%",
            icon = Icons.Default.Palette,
            expanded = appearanceExpanded,
            onToggle = { appearanceExpanded = !appearanceExpanded },
        ) {
            AppearancePreview(state.prefs)

            SettingHeading("Profile", "A whole look — colors, lettering, corners and wallpaper together.")
            // Each card is a miniature of the profile itself: its art, a glass panel,
            // its typeface and its corner shape, so picking one is picking what you see.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = InkSpacing.sm)
                    .height(IntrinsicSize.Min)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm),
            ) {
                AppearanceProfile.entries.forEach { profileEntry ->
                    ProfileCard(
                        profile = profileEntry,
                        mode = state.prefs.themeMode,
                        selected = profileEntry == state.prefs.appearanceProfile,
                        onClick = { viewModel.setAppearanceProfile(profileEntry) },
                    )
                }
            }

            val profile = state.prefs.appearanceProfile
            if (profile != AppearanceProfile.Streaming) {
                SettingHeading(
                    "Theme",
                    if (profile.usesThemeModes) "Light, sepia, dark or true black." else "${profile.label} comes in light and dark.",
                )
                InkSegmentedPill(
                    options = if (profile.usesThemeModes) {
                        listOf(
                            SegmentedOption(AppThemeMode.Light.name, "Light"),
                            SegmentedOption(AppThemeMode.Sepia.name, "Sepia"),
                            SegmentedOption(AppThemeMode.Dark.name, "Dark"),
                            SegmentedOption(AppThemeMode.OledBlack.name, "Black"),
                        )
                    } else {
                        listOf(
                            SegmentedOption(AppThemeMode.Light.name, "Light"),
                            SegmentedOption(AppThemeMode.Dark.name, "Dark"),
                        )
                    },
                    selectedId = if (profile.usesThemeModes) {
                        state.prefs.themeMode.name
                    } else {
                        if (state.prefs.themeMode.isDark) AppThemeMode.Dark.name else AppThemeMode.Light.name
                    },
                    onSelect = { viewModel.setTheme(AppThemeMode.valueOf(it)) },
                    scrollable = true,
                    modifier = Modifier.padding(vertical = InkSpacing.sm),
                )
            }

            SettingHeading("Wallpaper", "Drawn behind every page, Home included.")
            WallpaperGroup("Weaverse") {
                WallpaperTile("None", selected = !state.prefs.profileBackgroundEnabled, onClick = { viewModel.setProfileBackgroundEnabled(false) }) {
                    Box(it.background(inkTokens().background))
                }
                WallpaperTile(
                    "Theme art",
                    selected = state.prefs.profileBackgroundEnabled && BackdropStyle.fromName(state.prefs.backdropStyle) == BackdropStyle.Profile,
                    onClick = { viewModel.setBackdropStyle(BackdropStyle.Profile.name) },
                ) { AppBackdrop(BackdropStyle.Profile, profile, it) }
            }
            listOf("Windows", "Apple").forEach { family ->
                WallpaperGroup(family) {
                    BackdropStyle.entries.filter { it.family == family }.forEach { style ->
                        WallpaperTile(
                            style.label,
                            selected = state.prefs.profileBackgroundEnabled && state.prefs.backdropStyle == style.name,
                            onClick = { viewModel.setBackdropStyle(style.name) },
                        ) { AppBackdrop(style, profile, it) }
                    }
                }
            }

            SettingHeading("Glass", "How much wallpaper shows through panels and pages.")
            LabeledSlider(
                value = state.prefs.glassClarityPercent.toFloat(),
                onValueChange = { viewModel.setGlassClarity(it.roundToInt()) },
                valueRange = 0f..80f,
                start = "Solid",
                end = "Clear",
            )

            SettingHeading("Font size", "Every page, menu and dialog — ${state.prefs.uiTextScalePercent}%.")
            InkSegmentedPill(
                options = listOf(
                    SegmentedOption("90", "Small"),
                    SegmentedOption("100", "Default"),
                    SegmentedOption("115", "Large"),
                    SegmentedOption("130", "Largest"),
                ),
                selectedId = state.prefs.uiTextScalePercent.toString(),
                onSelect = { viewModel.setUiTextScale(it.toInt()) },
                scrollable = true,
                modifier = Modifier.padding(vertical = InkSpacing.xs),
            )
            LabeledSlider(
                value = state.prefs.uiTextScalePercent.toFloat(),
                onValueChange = { viewModel.setUiTextScale((it / 5f).roundToInt() * 5) },
                valueRange = 80f..140f,
                steps = 11,
                start = "A",
                end = "A",
                endLarge = true,
            )

            SettingHeading("Line height", "Space between lines of text — ${"%.2f".format(state.prefs.uiLineSpacing)}×.")
            InkSegmentedPill(
                options = LineSpacingPresets.map { (value, label) -> SegmentedOption(value.toString(), label) },
                selectedId = LineSpacingPresets.firstOrNull { abs(it.first - state.prefs.uiLineSpacing) < 0.01f }?.first?.toString().orEmpty(),
                onSelect = { viewModel.setUiLineSpacing(it.toFloat()) },
                scrollable = true,
                modifier = Modifier.padding(vertical = InkSpacing.xs),
            )
            LabeledSlider(
                value = state.prefs.uiLineSpacing,
                onValueChange = { viewModel.setUiLineSpacing((it * 20f).roundToInt() / 20f) },
                valueRange = 0.85f..1.5f,
                steps = 12,
                start = "Tight",
                end = "Airy",
            )

            SettingHeading("Brightness", "Dims the whole app — ${state.prefs.appBrightnessPercent}%.")
            LabeledSlider(
                value = state.prefs.appBrightnessPercent.toFloat(),
                onValueChange = { viewModel.setAppBrightness(it.toInt()) },
                valueRange = 5f..100f,
                start = "Dim",
                end = "Full",
            )

            SettingHeading(
                "Reader text",
                "The book reader keeps its own size: ${state.prefs.fontSizeSp}sp, " +
                    "line height ${"%.1f".format(state.prefs.lineHeight)}. Also adjustable from the reader.",
            )
            LabeledSlider(
                value = state.prefs.fontSizeSp.toFloat(),
                onValueChange = { viewModel.setFontSize(it.roundToInt()) },
                valueRange = 12f..28f,
                steps = 15,
                start = "A",
                end = "A",
                endLarge = true,
            )
            LabeledSlider(
                value = state.prefs.lineHeight,
                onValueChange = viewModel::setLineHeight,
                valueRange = 1.2f..2.2f,
                steps = 9,
                start = "Tight",
                end = "Airy",
            )

            SettingHeading("Your own wallpaper", "An image, or a video that loops muted. Replaces the wallpaper above.")
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
            title = "Help & friends",
            subtitle = "Wiki manual, quick guide and daily new people",
            icon = Icons.AutoMirrored.Filled.HelpOutline,
            expanded = generalExpanded,
            onToggle = { generalExpanded = !generalExpanded },
            modifier = Modifier.padding(top = InkSpacing.md),
        ) {
            Text(
                "The wiki manual covers every workspace, and the quick guide " +
                    "answers the basics. All six modes and your recently opened content are on Home.",
                style = MaterialTheme.typography.bodySmall,
                color = inkTokens().secondaryText,
            )
            Row(modifier = Modifier.padding(top = InkSpacing.sm)) {
                InkOutlinedButton(
                    label = "Open Wiki manual",
                    onClick = { showWiki = true },
                )
                InkOutlinedButton(
                    label = if (showHelp) "Hide quick guide" else "Quick guide",
                    onClick = { showHelp = !showHelp },
                    modifier = Modifier.padding(start = InkSpacing.sm),
                )
            }
            if (showHelp) {
                HelpScreen(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                )
            }

            SettingHeading(
                "Friends",
                "Write one new person into your friends list each day. Needs an " +
                    "OpenRouter key; skipped silently when you are offline.",
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
        }

        ExpandableSection(
            title = "Composer commands",
            icon = Icons.Default.Terminal,
            subtitle = "Add, remove, and review the ! quick-add commands in every prompt box",
            expanded = commandsExpanded,
            onToggle = { commandsExpanded = !commandsExpanded },
            modifier = Modifier.padding(top = InkSpacing.md),
        ) {
            val commands = effectiveBangCommands(
                custom = state.prefs.customBangCommands,
                removed = state.prefs.removedBangKeywords,
            )
            val builtInKeywords = CodexBang.defaultCommands.map { it.keyword }.toSet()
            Text(
                "Type ! plus a keyword in any prompt box to write the prose and file a codex entry in one line. " +
                    "Removing a built-in hides it (and its aliases); custom keywords accept letters only.",
                style = MaterialTheme.typography.bodySmall,
                color = inkTokens().secondaryText,
            )
            commands.forEach { cmd ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = InkSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            cmd.title + if (cmd.aliases.isNotEmpty()) "  (/${cmd.aliases.joinToString(" /")})" else "",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            cmd.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = inkTokens().secondaryText,
                        )
                    }
                    Text(
                        "Remove",
                        modifier = Modifier
                            .clip(RoundedCornerShape(inkRadiusSm()))
                            .clickable { viewModel.removeBangCommand(cmd.keyword, cmd.keyword in builtInKeywords) }
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                "Add a command",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = InkSpacing.md),
            )
            OutlinedTextField(
                value = newCommandDraft,
                onValueChange = { newCommandDraft = it.filter(Char::isLetter).take(24) },
                label = { Text("New keyword (no !)") },
                supportingText = { Text("Example: magic → !magic writes and files a lore entry") },
                modifier = Modifier.fillMaxWidth().padding(top = InkSpacing.xs),
                singleLine = true,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = InkSpacing.sm),
            ) {
                CodexEntryKind.entries.forEach { kind ->
                    InkSegmentedPill(
                        options = listOf(SegmentedOption(kind.name, kind.label)),
                        selectedId = if (newCommandKind == kind.name) kind.name else "",
                        onSelect = { newCommandKind = kind.name },
                    )
                    if (kind != CodexEntryKind.entries.last()) {
                        Spacer(Modifier.width(InkSpacing.sm))
                    }
                }
            }
            val draft = newCommandDraft.trim().lowercase()
            val taken = effectiveBangCommands(
                custom = state.prefs.customBangCommands,
                removed = emptySet(),
            ).any { it.keyword == draft } ||
                CodexBang.defaultCommands.any { spec -> spec.keyword == draft || draft in spec.aliases }
            Row(modifier = Modifier.padding(top = InkSpacing.sm)) {
                InkFilledButton(
                    label = if (taken) "Keyword taken" else "Add command",
                    onClick = {
                        viewModel.addBangCommand(draft, newCommandKind)
                        newCommandDraft = ""
                    },
                    enabled = draft.isNotBlank() && !taken,
                )
                InkOutlinedButton(
                    label = "Restore defaults",
                    onClick = {
                        viewModel.resetBangCommands()
                        newCommandDraft = ""
                    },
                    modifier = Modifier.padding(start = InkSpacing.sm),
                )
            }

            Text(
                "RPG turn commands (*)",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = InkSpacing.lg),
            )
            Text(
                "Type * plus a keyword in the RPG composer for player turns. Roll commands ask the DM " +
                    "for a dice roll before narrating; the rest are tagged narration turns.",
                style = MaterialTheme.typography.bodySmall,
                color = inkTokens().secondaryText,
            )
            val starCommands = RpgTurnCommands.effectiveCommands(
                custom = state.prefs.customStarCommands,
                removed = state.prefs.removedStarKeywords,
            )
            val builtInStarKeywords = RpgTurnCommands.all.map { it.keyword }.toSet()
            starCommands.forEach { cmd ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = InkSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "*${cmd.keyword}" + if (cmd.requiresRoll) "  · roll" else "",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            cmd.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = inkTokens().secondaryText,
                        )
                    }
                    Text(
                        "Remove",
                        modifier = Modifier
                            .clip(RoundedCornerShape(inkRadiusSm()))
                            .clickable { viewModel.removeStarCommand(cmd.keyword, cmd.keyword in builtInStarKeywords) }
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                "Add a turn command",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = InkSpacing.md),
            )
            OutlinedTextField(
                value = newStarDraft,
                onValueChange = { newStarDraft = it.filter(Char::isLetter).take(24) },
                label = { Text("New keyword (no *)") },
                supportingText = { Text("Example: stealth → *stealth — a sneaky player turn") },
                modifier = Modifier.fillMaxWidth().padding(top = InkSpacing.xs),
                singleLine = true,
            )
            OutlinedTextField(
                value = newStarDescription,
                onValueChange = { newStarDescription = it.take(120) },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth().padding(top = InkSpacing.xs),
                singleLine = true,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = InkSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = newStarRoll, onCheckedChange = { newStarRoll = it })
                Text("Requires a dice roll", style = MaterialTheme.typography.bodyMedium)
            }
            val starDraft = newStarDraft.trim().lowercase()
            val starTaken = RpgTurnCommands.all.any { it.keyword == starDraft } ||
                state.prefs.customStarCommands.any { it.substringBefore('|') == starDraft }
            Row(modifier = Modifier.padding(top = InkSpacing.sm)) {
                InkFilledButton(
                    label = if (starTaken) "Keyword taken" else "Add turn command",
                    onClick = {
                        viewModel.addStarCommand(starDraft, newStarDescription, newStarRoll)
                        newStarDraft = ""
                        newStarDescription = ""
                        newStarRoll = false
                    },
                    enabled = starDraft.isNotBlank() && !starTaken,
                )
                InkOutlinedButton(
                    label = "Restore defaults",
                    onClick = viewModel::resetStarCommands,
                    modifier = Modifier.padding(start = InkSpacing.sm),
                )
            }
        }

        ExpandableSection(
            title = "AI topic media library",
            icon = Icons.Default.PhotoLibrary,
            subtitle = "Attach local pictures or videos when the AI discusses a matching topic",
            expanded = topicMediaExpanded,
            onToggle = { topicMediaExpanded = !topicMediaExpanded },
            modifier = Modifier.padding(top = InkSpacing.md),
        ) {
            Text(
                "Choose a root folder, then make one subfolder per topic: helmet/, people/, landscapes/, objects/, and so on. " +
                    "The folder setting is local to this device, so your phone and computer can use different paths. " +
                    "Only topic names are sent to the model; the path and filenames stay local.",
                style = MaterialTheme.typography.bodySmall,
                color = inkTokens().secondaryText,
            )
            PromptSurfaceToggle(
                label = "Automatically attach matching topic media",
                checked = state.prefs.topicMediaAutoAttach,
                onCheckedChange = viewModel::setTopicMediaAutoAttach,
            )
            OutlinedTextField(
                value = topicMediaRootDraft,
                onValueChange = { topicMediaRootDraft = it },
                label = { Text("Media-library folder or path") },
                supportingText = { Text("Phone: use Choose folder. Computer/local storage: a readable filesystem path also works.") },
                modifier = Modifier.fillMaxWidth().padding(top = InkSpacing.sm),
                singleLine = true,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = InkSpacing.sm),
            ) {
                InkFilledButton(
                    label = "Choose folder",
                    onClick = { topicMediaFolderPicker.launch(null) },
                )
                InkOutlinedButton(
                    label = "Save path",
                    onClick = { viewModel.setTopicMediaLibraryRoot(topicMediaRootDraft) },
                    modifier = Modifier.padding(start = InkSpacing.sm),
                )
                InkOutlinedButton(
                    label = "Refresh",
                    onClick = { viewModel.refreshTopicMedia(topicMediaRootDraft) },
                    modifier = Modifier.padding(start = InkSpacing.sm),
                )
            }
            Text(
                state.topicMediaStatus,
                style = MaterialTheme.typography.bodySmall,
                color = inkTokens().secondaryText,
                modifier = Modifier.padding(top = InkSpacing.sm),
            )
        }

        ExpandableSection(
            title = "Prompt entry",
            icon = Icons.Default.EditNote,
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
            icon = Icons.Default.Cloud,

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
            icon = Icons.Default.SmartToy,

            expanded = modelsExpanded,

            onToggle = { modelsExpanded = !modelsExpanded },

            modifier = Modifier.padding(top = InkSpacing.md),

        ) {

            Text(
                "Writing: ${state.prefs.defaultModelRef}\n" +
                    "Vision/OCR: ${state.prefs.mangaVisionModelRef.ifBlank { "Auto" }}\n" +
                    "Image editing: ${state.prefs.mangaImageModelRef.ifBlank { "Auto" }}\n" +
                    "Each tab now saves its own model; selecting an image model will not replace the writing model.",
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

                    SegmentedOption(ModelListTab.Vision.name, "Vision"),

                    SegmentedOption(ModelListTab.ImageGeneration.name, "Image generation"),

                    SegmentedOption(ModelListTab.TextToSpeech.name, "Text to speech"),

                    SegmentedOption(ModelListTab.All.name, "All"),

                ),

                selectedId = state.modelTab.name,

                onSelect = { viewModel.onModelTab(ModelListTab.valueOf(it)) },

                scrollable = true,

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

                ModelListTab.Vision -> state.visionModels

                ModelListTab.ImageGeneration -> state.imageModels

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
                    if (state.models.isEmpty()) {
                        "No models cached. Save a valid OpenRouter key, then tap Refresh models."
                    } else if (state.modelSearch.isNotBlank()) {
                        "No ${state.modelTab.name.replace(Regex("([a-z])([A-Z])"), "$1 $2").lowercase()} models match ‘${state.modelSearch}’."
                    } else {
                        "No ${state.modelTab.name.replace(Regex("([a-z])([A-Z])"), "$1 $2").lowercase()} models were reported by OpenRouter."
                    },
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
                        selected = when (state.modelTab) {
                            ModelListTab.Vision -> state.prefs.mangaVisionModelRef.endsWith(model.id)
                            ModelListTab.ImageGeneration -> state.prefs.mangaImageModelRef.endsWith(model.id)
                            else -> state.prefs.defaultModelRef.endsWith(model.id)
                        },
                        onClick = { viewModel.selectModelForCurrentTab(model.id, model.available) },
                    )
                }
            }

        }



        ExpandableSection(

            title = "Other providers",
            icon = Icons.Default.Key,

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
            val spend = state.prefs
            if (spend.usageYearMonth.isNotBlank() || spend.usageCostUsd > 0.0) {
                Text(
                    "This month (${spend.usageYearMonth.ifBlank { "—" }}): " +
                        "${com.ihy2ln.weaverse.core.ui.util.UsageFormat.formatCost(spend.usageCostUsd) ?: "$0.00"} · " +
                        "${spend.usagePromptTokens + spend.usageCompletionTokens} tokens",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = InkSpacing.sm),
                )
            }
            if (state.otherProviderModels.isNotEmpty()) {
                Text(
                    "Seeded models (tap to set as default)",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = InkSpacing.sm),
                )
                state.otherProviderModels.forEach { model ->
                    ModelRow(
                        model = model,
                        selected = state.prefs.defaultModelRef == model.id,
                        onClick = { viewModel.selectDefaultModel(model.id, true, model.id.substringBefore('/')) },
                    )
                }
            }

        }



        ExpandableSection(
            title = "Sync through the web version",
            icon = Icons.Default.Sync,
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
                if (state.sync.tlsEnabled) {
                    InkOutlinedButton(
                        label = "TLS on",
                        onClick = { viewModel.setSyncTls(false) },
                        modifier = Modifier.padding(start = InkSpacing.sm),
                    )
                } else {
                    InkConfirmButton(
                        onClick = { viewModel.setSyncTls(true) },
                        label = "TLS off",
                        contentDescription = "Enable self-signed HTTPS for the hub",
                        modifier = Modifier.padding(start = InkSpacing.sm),
                    )
                }
            }
            if (state.sync.certSha256.isNotBlank()) {
                Text(
                    "Pinned cert ${state.sync.certSha256.take(23)}…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = InkSpacing.sm),
                )
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
            if (state.sync.conflicts.isNotEmpty()) {
                Text(
                    "Conflicts (${state.sync.conflicts.size})",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = InkSpacing.md),
                )
                state.sync.conflicts.take(12).forEach { conflict ->
                    Text(
                        "${conflict.tableName} · ${conflict.rowKey}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = InkSpacing.xs),
                    )
                    Row(modifier = Modifier.padding(top = InkSpacing.xs)) {
                        InkConfirmButton(
                            onClick = { viewModel.keepSyncMine(conflict.id) },
                            label = "Keep mine",
                            contentDescription = "Restore the local version of this row",
                        )
                        InkOutlinedButton(
                            label = "Keep theirs",
                            onClick = { viewModel.keepSyncTheirs(conflict.id) },
                            modifier = Modifier.padding(start = InkSpacing.sm),
                        )
                    }
                }
            }

            // ------------------------------------------------ MCP / CLI harnesses
            Text(
                "MCP & CLI harnesses",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = InkSpacing.lg),
            )
            Text(
                "Let Cursor, Claude Code, OpenCode, Codex CLI or any MCP client read your library. " +
                    "Turning on ChatGPT / Codex MCP starts this device's local " +
                    "hub and opens MCP until you turn it off. This does not sign into ChatGPT or enable cloud image editing. Cursor IDE and Cursor CLI (`agent`) " +
                    "share ~/.cursor/mcp.json.",
                style = MaterialTheme.typography.bodySmall,
                color = inkTokens().secondaryText,
            )
            val clipboardMcp = LocalClipboardManager.current
            val mcpEndpoint = "http://${state.sync.lanAddress.ifBlank { "<device-ip>" }}:${state.sync.port}/mcp"
            val codexSetupCommand = "codex mcp add weaverse --url $mcpEndpoint"
            InkCard(modifier = Modifier.fillMaxWidth().padding(top = InkSpacing.sm)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(InkSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ChatGPT / Codex MCP & CLI", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text(
                            mcpEndpoint,
                            style = MaterialTheme.typography.labelSmall,
                            color = inkTokens().secondaryText,
                        )
                        Text(
                            when {
                                state.prefs.codexMcpEnabled && state.sync.hosting -> "Enabled · MCP server is running"
                                state.prefs.codexMcpEnabled -> "Enabled · starting MCP server"
                                else -> "Disabled · MCP requests are rejected"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = inkTokens().secondaryText,
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = state.prefs.codexMcpEnabled,
                        onCheckedChange = viewModel::setCodexMcpEnabled,
                        modifier = Modifier.semantics {
                            contentDescription = "ChatGPT / Codex MCP access"
                        },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = InkSpacing.sm, vertical = InkSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        codexSetupCommand,
                        style = MaterialTheme.typography.labelSmall,
                        color = inkTokens().secondaryText,
                        maxLines = 2,
                        modifier = Modifier.weight(1f),
                    )
                    InkOutlinedButton(
                        label = "Copy setup",
                        onClick = { clipboardMcp.setText(AnnotatedString(codexSetupCommand)) },
                        modifier = Modifier.padding(start = InkSpacing.xs),
                    )
                }
            }
            Text(
                "Only leave Codex MCP enabled on a network you trust. Download and Storyboard-writing " +
                    "tools still require their normal confirmation flow.",
                style = MaterialTheme.typography.labelSmall,
                color = inkTokens().secondaryText,
                modifier = Modifier.padding(top = InkSpacing.xs),
            )
            Text(
                "Endpoint: $mcpEndpoint",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = InkSpacing.xs),
            )
            listOf(
                "Claude Code" to "claude mcp add --transport http weaverse $mcpEndpoint",
                "OpenCode" to "opencode mcp add weaverse --url $mcpEndpoint",
                "Cursor IDE" to "Cursor Settings → MCP → New MCP Server · name: weaverse · type: http · url: $mcpEndpoint · header Authorization: Bearer <sync-password>",
                "Cursor CLI" to "{\"mcpServers\":{\"weaverse\":{\"url\":\"$mcpEndpoint\",\"type\":\"http\",\"headers\":{\"Authorization\":\"Bearer <sync-password>\"}}}} then: agent mcp enable weaverse",
            ).forEach { (harness, command) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = InkSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        harness,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(132.dp),
                    )
                    Text(
                        command,
                        style = MaterialTheme.typography.labelSmall,
                        color = inkTokens().secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    InkOutlinedButton(
                        label = "Copy",
                        onClick = { clipboardMcp.setText(AnnotatedString(command)) },
                        modifier = Modifier.padding(start = InkSpacing.xs),
                    )
                }
            }
            Text(
                "Tools exposed: list_works, list_scenes, read_scene, search_codex, " +
                    "read_codex_entry, list_notes, read_note, find_scene_media, search_manga, " +
                    "list_manga_chapters, queue_manga_download, download_manga_web_link, " +
                    "manga_download_status, import_manga_chapter_to_storyboard.",
                style = MaterialTheme.typography.labelSmall,
                color = inkTokens().secondaryText,
            )
        }

        ExpandableSection(

            title = "Backup & restore",
            icon = Icons.Default.Backup,

            expanded = backupExpanded,

            onToggle = { backupExpanded = !backupExpanded },

            modifier = Modifier.padding(top = InkSpacing.md),

        ) {

            Text(
                "Backup now writes two zip files: one for this phone (Restore) and one for PC (extract into the Weaverse folder that contains data/). Copies also go to Android/data/…/files/backups so you can copy them off the device. Daily auto-backup keeps the last 7 zips.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = InkSpacing.sm),
            )
            Row(modifier = Modifier.padding(bottom = InkSpacing.sm)) {
                if (state.prefs.autoBackupEnabled) {
                    InkOutlinedButton(
                        label = "Daily backup on",
                        onClick = { viewModel.setAutoBackup(false) },
                    )
                } else {
                    InkConfirmButton(
                        onClick = { viewModel.setAutoBackup(true) },
                        label = "Daily backup off",
                        contentDescription = "Enable daily automatic backups",
                    )
                }
            }

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

            InkOutlinedButton(
                label = "Show crash log",
                onClick = viewModel::loadCrashLog,
                modifier = Modifier.padding(top = InkSpacing.sm),
            )
            if (state.crashLogText.isNotBlank()) {
                Text(
                    state.crashLogText.take(2000),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = InkSpacing.sm),
                )
                val clipboard = LocalClipboardManager.current
                InkOutlinedButton(
                    label = "Copy crash log",
                    onClick = { clipboard.setText(AnnotatedString(state.crashLogText)) },
                    modifier = Modifier.padding(top = InkSpacing.xs),
                )
            }

        }

        Spacer(modifier = Modifier.height(AlwaysScrollEndPadding))

    }

    if (showWiki) {
        WikiScreen(
            onClose = { showWiki = false },
            modifier = Modifier.matchParentSize(),
        )
    }

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

private val LineSpacingPresets = listOf(0.9f to "Compact", 1f to "Default", 1.15f to "Relaxed", 1.3f to "Airy")

private fun wallpaperLabel(prefs: UserPreferences): String = when {
    !prefs.profileBackgroundEnabled -> "no wallpaper"
    else -> BackdropStyle.fromName(prefs.backdropStyle).label
}

@Composable
private fun SettingHeading(title: String, detail: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = inkTokens().primaryText,
        modifier = Modifier.padding(top = InkSpacing.lg),
    )
    Text(detail, style = MaterialTheme.typography.bodySmall, color = inkTokens().secondaryText)
}

/** A slider with small end captions, like a phone's display settings. */
@Composable
private fun LabeledSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    start: String,
    end: String,
    steps: Int = 0,
    endLarge: Boolean = false,
) {
    val tokens = inkTokens()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(start, style = MaterialTheme.typography.labelMedium, color = tokens.secondaryText)
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = tokens.activePill,
                activeTrackColor = tokens.activePill,
                inactiveTrackColor = tokens.hover,
                activeTickColor = tokens.activePillLabel.copy(alpha = 0.5f),
                inactiveTickColor = tokens.secondaryText.copy(alpha = 0.4f),
            ),
            modifier = Modifier.weight(1f).padding(horizontal = InkSpacing.sm),
        )
        Text(
            end,
            style = if (endLarge) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelMedium,
            color = tokens.secondaryText,
        )
    }
}

/** A live sample of the current look: wallpaper, glass card and text at the chosen size and spacing. */
@Composable
private fun AppearancePreview(prefs: UserPreferences) {
    val tokens = inkTokens()
    val shape = RoundedCornerShape(inkRadiusMd() * 2)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, tokens.hairline, shape)
            .semantics { contentDescription = "Appearance preview" },
    ) {
        if (prefs.profileBackgroundEnabled) {
            AppBackdrop(BackdropStyle.fromName(prefs.backdropStyle), prefs.appearanceProfile, Modifier.matchParentSize())
            Box(
                Modifier
                    .matchParentSize()
                    .background(tokens.background.copy(alpha = 1f - prefs.glassClarityPercent / 100f * 0.8f)),
            )
        } else {
            Box(Modifier.matchParentSize().background(tokens.background))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = InkSpacing.lg, end = InkSpacing.lg, top = 56.dp, bottom = InkSpacing.lg)
                .glassPanel()
                .padding(InkSpacing.lg),
        ) {
            Text(
                "CONTINUE READING",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = tokens.activePill,
            )
            Text(
                "The Lantern Canal",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = tokens.primaryText,
            )
            Text(
                "Chapter 3 · The lanterns along the canal flickered as Mara stepped onto the bridge, " +
                    "the whole city humming somewhere below her feet.",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.secondaryText,
                modifier = Modifier.padding(top = InkSpacing.xs),
            )
        }
    }
}

@Composable
private fun WallpaperGroup(title: String, tiles: @Composable () -> Unit) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = inkTokens().secondaryText,
        modifier = Modifier.padding(top = InkSpacing.md, bottom = InkSpacing.xs),
    )
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm),
    ) { tiles() }
}

@Composable
private fun WallpaperTile(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    art: @Composable (Modifier) -> Unit,
) {
    val tokens = inkTokens()
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .width(108.dp)
            .clip(shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$label wallpaper" + if (selected) ", selected" else "" },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(shape)
                .border(if (selected) 2.dp else 1.dp, if (selected) tokens.activePill else tokens.hairline, shape),
        ) {
            art(Modifier.matchParentSize())
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(20.dp)
                        .background(tokens.activePill, RoundedCornerShape(999.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = tokens.activePillLabel, modifier = Modifier.size(14.dp))
                }
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) tokens.primaryText else tokens.secondaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp),
        )
    }
}

/** A miniature of [profile]: its own wallpaper art, glass panel, typeface and corners. */
@Composable
private fun ProfileCard(profile: AppearanceProfile, mode: AppThemeMode, selected: Boolean, onClick: () -> Unit) {
    val swatches = profile.tokens(mode)
    val type = profile.typography
    val outer = RoundedCornerShape(maxOf(profile.cornerRadius * 1.5f, 6.dp))
    val inner = RoundedCornerShape(profile.cornerRadius)
    Box(
        modifier = Modifier
            .width(156.dp)
            .fillMaxHeight()
            .clip(outer)
            .border(if (selected) 2.dp else 1.dp, if (selected) inkTokens().activePill else swatches.hairline, outer)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${profile.label} profile" + if (selected) ", active" else "" },
    ) {
        // Classic's art reads the ambient tokens, so hand it this card's palette.
        CompositionLocalProvider(LocalInkTokens provides swatches, LocalAppearanceProfile provides profile) {
            ProfileBackgroundArt(profile, Modifier.matchParentSize())
        }
        Column(
            modifier = Modifier
                .padding(InkSpacing.sm)
                .fillMaxWidth()
                .clip(inner)
                .background(swatches.panel.copy(alpha = 0.78f))
                .border(1.dp, swatches.hairline.copy(alpha = 0.6f), inner)
                .padding(InkSpacing.sm),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(12.dp).background(swatches.activePill, inner))
                Box(Modifier.size(12.dp).background(swatches.primaryText, inner))
                Box(Modifier.size(12.dp).background(swatches.secondaryText, inner))
            }
            Text(
                profile.label,
                style = type.titleMedium,
                fontWeight = FontWeight.Bold,
                color = swatches.primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                profile.blurb,
                style = type.labelSmall,
                color = swatches.secondaryText,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (selected) "✓ Active" else "Use",
                style = type.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (selected) swatches.activePillLabel else swatches.activePill,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clip(inner)
                    .background(if (selected) swatches.activePill else Color.Transparent)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}
