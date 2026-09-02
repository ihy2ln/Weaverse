package com.ihy2ln.weaverse.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing
import com.ihy2ln.weaverse.core.ui.theme.inkTokens

/** What the user typed when creating a novel, campaign or storyboard. */
data class NewWorkDetails(
    val title: String,
    val genre: String = "",
    val pov: String = "",
    val tense: String = "",
    val styleGuide: String = "",
    val mainCharacters: List<WorkCharacterOption> = emptyList(),
    val rulesetId: String = "",
    val settingId: String = "",
    val narrativePov: String = "",
    val campaignRoleId: String = "",
    val difficultyId: String = "standard",
    /** Storyboard only: a whole manga/comic file (PDF/CBZ/long strip) to page-split on creation. */
    val mangaFileUri: String = "",
    val mangaFileName: String = "",
)

data class WorkCharacterOption(
    val id: String,
    val name: String,
    val source: String,
)

data class CampaignRulesetTemplate(
    val id: String,
    val label: String,
    val directive: String,
)

data class CampaignSettingTemplate(
    val id: String,
    val label: String,
    val directive: String,
)

data class CampaignPerspectiveTemplate(
    val id: String,
    val label: String,
    val directive: String,
)

data class TextGameDifficultyTemplate(
    val id: String,
    val label: String,
    val description: String,
)

val TextGameDifficultyTemplates = listOf(
    TextGameDifficultyTemplate("story", "Story", "Gentler encounters; full story and haven progression."),
    TextGameDifficultyTemplate("standard", "Standard", "Intended balance across battle, Farm, Town, and Home."),
    TextGameDifficultyTemplate("veteran", "Veteran", "Hardier enemies, sharper intents, and better rewards."),
    TextGameDifficultyTemplate("nightmare", "Nightmare", "Strongest deterministic enemies and rewards."),
)

val CampaignPerspectiveTemplates = listOf(
    CampaignPerspectiveTemplate(
        "third-multiple",
        "Third-person multiple",
        "Use third-person multiple perspective. Follow whichever player character is most relevant to the current beat, clearly anchor every perspective change, and never reveal knowledge that the viewpoint character does not possess.",
    ),
    CampaignPerspectiveTemplate(
        "third-limited",
        "Third-person limited",
        "Use close third-person limited perspective centered on the active player character. Describe only what that character can perceive, infer, remember, or feel unless the scene explicitly changes viewpoint.",
    ),
    CampaignPerspectiveTemplate(
        "first-multiple",
        "First-person rotating",
        "Use first-person perspective and rotate among selected player characters only at clear scene or section boundaries. Identify the new viewpoint immediately and keep each character's voice and knowledge distinct.",
    ),
    CampaignPerspectiveTemplate(
        "second-person",
        "Second person",
        "Address the active player character as you. When several protagonists are present, use names to disambiguate actions and perceptions while keeping the narration player-facing.",
    ),
    CampaignPerspectiveTemplate(
        "omniscient",
        "Omniscient ensemble",
        "Use an omniscient ensemble viewpoint that can move between characters and locations, but preserve suspense by withholding secrets when revealing them would undermine play or player agency.",
    ),
    CampaignPerspectiveTemplate(
        "cinematic",
        "Cinematic",
        "Use a cinematic external viewpoint focused on visible action, environment, dialogue, and staging. Avoid asserting private thoughts unless expressed through behavior or speech.",
    ),
)

val CampaignSettingTemplates = listOf(
    CampaignSettingTemplate(
        "adams-haven",
        "Adams Haven · Elysium Vale",
        "Run Adams Haven as a first-person card-RPG in Elysium Vale. The player is the off-field Summoner who commands a shared hand, grows a Haven from a shack, and links dungeon expeditions, battle-grown crops, Town services, and Home preparation into one progression loop.",
    ),
    CampaignSettingTemplate(
        "high-fantasy",
        "High fantasy",
        "Run a high-fantasy world of ancient kingdoms, dungeon delves, dangerous wilderness, monsters, magic, gods, factions, treasure, and heroic quests. Present locations as explorable spaces and seed meaningful choices, secrets, and consequences.",
    ),
    CampaignSettingTemplate(
        "dark-fantasy",
        "Dark fantasy",
        "Run a morally difficult fantasy world shaped by curses, corruption, scarce safety, frightening magic, compromised factions, and costly victories. Keep danger serious without removing player agency or fair warning.",
    ),
    CampaignSettingTemplate(
        "sword-sorcery",
        "Sword & sorcery",
        "Run a pulpy sword-and-sorcery world of decadent city-states, dangerous ruins, personal ambition, strange cults, mercenary work, and rare unsettling magic. Favor immediate stakes and adventurous momentum over world-saving destiny.",
    ),
    CampaignSettingTemplate(
        "gothic-horror",
        "Gothic horror",
        "Run a gothic-horror setting of isolated communities, decaying estates, family secrets, supernatural dread, investigation, and temptation. Build tension through clues and atmosphere while keeping threats actionable at the table.",
    ),
    CampaignSettingTemplate(
        "urban-fantasy",
        "Urban fantasy",
        "Run a modern city where supernatural communities, hidden magic, institutions, neighborhoods, and mundane life collide. Treat information, favors, territory, and relationships as important adventure resources.",
    ),
    CampaignSettingTemplate(
        "science-fantasy",
        "Science fantasy",
        "Run a science-fantasy world where advanced relics, strange planets or ruins, sorcery, nonhuman cultures, and lost civilizations coexist. Keep technology and magic wondrous but internally consistent.",
    ),
    CampaignSettingTemplate(
        "genshin-impact",
        "Genshin Impact · Teyvat",
        "Run a campaign in Teyvat: seven nations each ruled by an Archon and shaped by an ideal — Mondstadt (Freedom), Liyue (Contracts), Inazuma (Eternity), Sumeru (Wisdom), Fontaine (Justice), Natlan (War), and Snezhnaya (the Tsaritsa's Snow). Mortals blessed with Visions channel one of seven elements (Anemo, Geo, Electro, Dendro, Hydro, Pyro, Cryo); elemental reactions matter in and out of combat. Weave in the Fatui and the Eleven Harbingers as scheming antagonists, the Abyss Order in the ruins below, Celestia's distant watchers, plus Paimon-style traveling-companion banter. Keep the tone bright, adventurous, and food-loving, with courts, guilds, festivals, and ancient civilizations buried beneath each nation.",
    ),
    CampaignSettingTemplate(
        "wuthering-waves",
        "Wuthering Waves · Solaris-3",
        "Run a campaign on Solaris-3 after the Lament: a ruined, echoing world where civilization clings to pockets like Jinzhou and the Midnight Rangers fight back. Players are Resonators whose bodies channel frequencies; Tacet Discords — monstrous echo-beasts born of the Lament — stalk the wilds and can be absorbed as Echoes to borrow their shapes and powers. Factions like the Fractsidus exploit the calamity; Sentinels, Resonance Beacons, and reverberating ruins reward exploration. Favor kinetic, agile combat, sound-and-frequency motifs, mystery-driven storytelling, and the slow restoration of a broken world.",
    ),
    CampaignSettingTemplate(
        "brown-dust-2",
        "Brown Dust 2",
        "Run a campaign in Brown Dust 2's mercenary dark fantasy: a wartorn continent of hardened sellsword companies, scheming nobles and churches, ancient evil stirring beneath politics, and small frontier towns caught between them. Emphasize tactical squad play — a band of specialists with distinct kits working together — bittersweet character drama, morally gray contracts, and vignette-style episodes (a haunted village, a caravan run, an arena scheme). Keep the tone mature but warm: loyal companions, hard choices, and hard-won small victories.",
    ),
    CampaignSettingTemplate(
        "world-of-warcraft",
        "World of Warcraft · Azeroth",
        "Run a campaign on Azeroth: the Alliance and the Horde in uneasy truce, races and cultures from Stormwind to Orgrimmar, Darnassus to Silvermoon. Class fantasies (warrior, mage, paladin, druid, warlock, shaman, rogue, priest, hunter, death knight, demon hunter, monk, evoker) with levelled abilities, dungeons and raids as expedition set-pieces, and iconic threats: the Scourge, the Burning Legion, Old Gods whispering beneath the earth, dragons and their Aspects, and faction politics that can erupt into war at any moment. Grand, high-adventure tone with taverns, mounts, profession crafting, and zones that each tell their own story.",
    ),
    CampaignSettingTemplate(
        "ff14",
        "Final Fantasy XIV · Hydaelyn",
        "Run a campaign on Hydaelyn: the city-states of Eorzea (Gridania's wood, Limsa Lominsa's seas, Ul'dah's sands) and beyond to Ishgard, Doma, and the Garlean Empire. Players are adventurers — potentially the Warrior of Light — blessed with the Echo, able to survive primal tempering and witness the past. Primals summon gods from belief and aether; Ascians scheme across the shards; jobs (paladin, white mage, black mage, dragoon, summoner, sage, reaper, and more) define combat identity. Emphasize found fellowship, duty and sacrifice, aether as the stuff of souls, crystal-centric lore, grand emotional story beats, and small-party tactical play.",
    ),
    CampaignSettingTemplate(
        "elder-scrolls",
        "The Elder Scrolls · Tamriel",
        "Run an open-ended Tamriel campaign of guilds, ruins, competing provinces, Daedric bargains, flexible skills, exploration, crafting, and player-led discovery. Keep factions morally layered and let small local choices reshape later opportunities.",
    ),
    CampaignSettingTemplate(
        "dragon-age",
        "Dragon Age · Thedas",
        "Run a party-driven Thedas campaign of darkspawn, the Fade, templar-mage tension, court politics, companion loyalty, consequential dialogue, and difficult choices whose results return later.",
    ),
    CampaignSettingTemplate(
        "baldurs-gate-3",
        "Baldur's Gate 3 · Forgotten Realms",
        "Run a cinematic Forgotten Realms campaign with interactive environments, companion agendas, branching quests, tactical encounters, unusual problem solving, camp conversations, and consequences that honor player improvisation.",
    ),
    CampaignSettingTemplate(
        "persona",
        "Persona-style urban fantasy",
        "Run a calendar-driven urban fantasy where ordinary daily life, relationships, school or work, and a hidden symbolic dungeon reinforce one another. Social bonds unlock practical adventure benefits without replacing the core mystery.",
    ),
    CampaignSettingTemplate(
        "fire-emblem",
        "Fire Emblem-style war chronicle",
        "Run a character-led war chronicle of rival kingdoms, tactical deployments, class growth, support bonds, political consequences, and a mobile home base. Make every recruited ally distinct in voice and battlefield purpose.",
    ),
    CampaignSettingTemplate(
        "mass-effect",
        "Mass Effect-style space opera",
        "Run a squad-focused galactic space opera with a customizable commander, shipboard Home hub, loyalty missions, species and faction politics, tactical powers, exploration, and decisions that alter later missions.",
    ),
    CampaignSettingTemplate(
        "custom",
        "Custom setting",
        "Use the player's setting details as the authoritative world guide. Infer only what is needed for play, remain internally consistent, and ask or offer choices instead of overwriting established lore.",
    ),
)

val CampaignRulesetTemplates = listOf(
    CampaignRulesetTemplate(
        "adams-haven-card-rpg",
        "Adams Haven Card RPG",
        "Use Adams Haven card rules: the player is the off-field Summoner; deployed allies share one hand; AP limits action count, EP limits card strength, SP powers Summoner support and ultimate timing, enemy intents stay visible, and deterministic local state is authoritative. Battle yields persistent inputs; Farm, Town, and Home convert them into the next expedition's preparation.",
    ),
    CampaignRulesetTemplate(
        "dnd-5e",
        "D&D 5e",
        "Use D&D 5e conventions: d20 ability checks, proficiency, advantage/disadvantage, armor class, hit points, spell slots, conditions, and death saves. Ask for rolls when outcomes are uncertain and state the DC before resolving when appropriate.",
    ),
    CampaignRulesetTemplate(
        "pathfinder-2e",
        "Pathfinder 2e",
        "Use Pathfinder 2e conventions: d20 checks with level-based proficiency, four degrees of success, the three-action economy, reactions, conditions, armor class, saving throws, and encounter-appropriate DCs.",
    ),
    CampaignRulesetTemplate(
        "dnd-3-5",
        "D&D 3.5e",
        "Use D&D 3.5e conventions: d20 checks, skill ranks, base attack bonus, fortitude/reflex/will saves, armor class categories, attacks of opportunity, prepared or spontaneous spellcasting, and tactical modifiers.",
    ),
    CampaignRulesetTemplate(
        "osr",
        "OSR / B/X",
        "Use old-school fantasy conventions: rulings over exhaustive rules, reaction and morale checks, resource pressure, dangerous combat, exploration turns, meaningful encumbrance, and saving throws when danger cannot be avoided.",
    ),
    CampaignRulesetTemplate(
        "pbta",
        "Powered by the Apocalypse",
        "Use Powered by the Apocalypse conventions: fiction-first moves, 2d6 results where 10+ succeeds, 7–9 succeeds with a cost, and 6- invites a game-master move. Never call for a roll unless a move is triggered by the fiction.",
    ),
    CampaignRulesetTemplate(
        "fate-core",
        "Fate Core",
        "Use Fate Core conventions: aspects, invokes and compels, 4dF checks, overcome/create advantage/attack/defend actions, stress, consequences, and success at a cost.",
    ),
    CampaignRulesetTemplate(
        "forged-dark",
        "Forged in the Dark",
        "Use fiction-first action ratings, position and effect, stress, resistance, consequences, clocks, flashbacks, downtime, and crew-scale advancement. State meaningful risk before a roll.",
    ),
    CampaignRulesetTemplate(
        "savage-worlds",
        "Savage Worlds",
        "Use trait dice and Wild Dice, target number 4, raises, bennies, exploding dice, wounds, Shaken, initiative cards, and fast tactical resolution.",
    ),
    CampaignRulesetTemplate(
        "cypher-system",
        "Cypher System",
        "Use difficulty 0–10, player-facing d20 rolls, effort, assets, skill training, Might/Speed/Intellect pools and Edge, intrusions, recovery rolls, and single-use cyphers.",
    ),
    CampaignRulesetTemplate(
        "year-zero",
        "Year Zero Engine",
        "Use attribute-plus-skill dice pools, sixes as successes, pushed rolls at a cost, conditions or damage tied to attributes, scarce resources, and dangerous exploration.",
    ),
    CampaignRulesetTemplate(
        "ironsworn",
        "Ironsworn",
        "Use fiction-first moves, action score versus two challenge dice, strong hit/weak hit/miss outcomes, momentum, progress tracks, vows, supply, health, spirit, and consequence-driven travel.",
    ),
    CampaignRulesetTemplate("custom", "Custom / systemless", "Use only the custom house rules supplied by the player and resolve uncertain actions consistently."),
)

/**
 * How a workspace words its "new work" dialog. The fields are the same because
 * they all end up on the same manuscript record — only the language differs.
 */
data class CreateWorkVocabulary(
    val what: String,
    val titleLabel: String,
    val titlePlaceholder: String,
    val genreLabel: String,
    val povLabel: String,
    val styleLabel: String,
    val styleHint: String,
    val storyboardSpecific: Boolean = false,
    val campaignSpecific: Boolean = false,
    val textGameSpecific: Boolean = false,
) {
    companion object {
        val Novel = CreateWorkVocabulary(
            what = "novel",
            titleLabel = "Title",
            titlePlaceholder = "Untitled Book",
            genreLabel = "Genre",
            povLabel = "Point of view",
            styleLabel = "Style guide",
            styleHint = "Voice, pacing, anything the AI should keep to.",
        )
        val Campaign = CreateWorkVocabulary(
            what = "campaign",
            titleLabel = "Campaign title",
            titlePlaceholder = "Untitled Campaign",
            genreLabel = "Setting",
            povLabel = "Main character(s)",
            styleLabel = "House rules",
            styleHint = "Choose a base rules system, then add campaign-specific rulings, tone, and boundaries.",
            campaignSpecific = true,
        )
        val TextGame = CreateWorkVocabulary(
            what = "text game",
            titleLabel = "Game session title",
            titlePlaceholder = "Adams Haven Session",
            genreLabel = "Game setting",
            povLabel = "Summoner character(s)",
            styleLabel = "Narration and game rules",
            styleHint = "Story tone, content boundaries, card rules, and narration guidance for this session.",
            campaignSpecific = true,
            textGameSpecific = true,
        )
        val Storyboard = CreateWorkVocabulary(
            what = "storyboard",
            titleLabel = "Series title",
            titlePlaceholder = "Untitled Manga",
            genreLabel = "Visual genre",
            povLabel = "Reading direction",
            styleLabel = "Art direction",
            styleHint = "Linework, palette, character design, lettering, and panel rhythm.",
            storyboardSpecific = true,
        )
    }
}

/**
 * Details popup shared by every workspace that can start a new work. Only the
 * title is required; everything else can be filled in later from the editor.
 */
@Composable
fun CreateWorkDialog(
    vocabulary: CreateWorkVocabulary,
    characterOptions: List<WorkCharacterOption> = emptyList(),
    onDismiss: () -> Unit,
    onCreate: (NewWorkDetails) -> Unit,
) {
    val tokens = inkTokens()
    var title by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    var pov by remember { mutableStateOf(if (vocabulary.storyboardSpecific) "Right to left" else "") }
    var tense by remember {
        mutableStateOf(
            when {
                vocabulary.storyboardSpecific -> "Manga"
                vocabulary.textGameSpecific -> "Present tense"
                else -> "Past tense"
            },
        )
    }
    var styleGuide by remember { mutableStateOf("") }
    var selectedCharacterIds by remember { mutableStateOf(setOf<String>()) }
    var rulesetId by remember { mutableStateOf(if (vocabulary.textGameSpecific) "adams-haven-card-rpg" else "dnd-5e") }
    var rulesetMenuOpen by remember { mutableStateOf(false) }
    var settingId by remember { mutableStateOf(if (vocabulary.textGameSpecific) "adams-haven" else "high-fantasy") }
    var settingMenuOpen by remember { mutableStateOf(false) }
    var narrativePovId by remember { mutableStateOf(if (vocabulary.textGameSpecific) "first-summoner" else "third-multiple") }
    var perspectiveMenuOpen by remember { mutableStateOf(false) }
    var campaignRoleId by remember { mutableStateOf("player") }
    var difficultyId by remember { mutableStateOf("standard") }
    var mangaFileUri by remember { mutableStateOf("") }
    var mangaFileName by remember { mutableStateOf("") }
    val isCampaign = vocabulary.campaignSpecific
    val isTextGame = vocabulary.textGameSpecific

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New ${vocabulary.what}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(InkSpacing.sm),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(vocabulary.titleLabel) },
                    placeholder = { Text(vocabulary.titlePlaceholder) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (vocabulary.storyboardSpecific) {
                    Text(
                        "Set up the visual language of the series. You can change layouts page by page later.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.secondaryText,
                    )
                    Text("Format", style = MaterialTheme.typography.labelMedium)
                    InkSegmentedPill(
                        options = listOf(
                            SegmentedOption("Manga", "Manga"),
                            SegmentedOption("Comic", "Comic"),
                            SegmentedOption("Webtoon", "Webtoon"),
                        ),
                        selectedId = tense,
                        onSelect = { tense = it },
                    )
                    Text("Reading direction", style = MaterialTheme.typography.labelMedium)
                    InkSegmentedPill(
                        options = listOf(
                            SegmentedOption("Right to left", "Right → left"),
                            SegmentedOption("Left to right", "Left → right"),
                            SegmentedOption("Vertical", "Vertical"),
                        ),
                        selectedId = pov,
                        onSelect = { pov = it },
                    )
                    OutlinedTextField(
                        value = genre,
                        onValueChange = { genre = it },
                        label = { Text(vocabulary.genreLabel) },
                        placeholder = { Text("Shōnen action, noir, romance, superhero…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Whole manga / comic file (optional)", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "Pick a PDF, CBZ/ZIP, or a long webtoon strip — every page is imported automatically, " +
                            "ready to separate into panels.",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.secondaryText,
                    )
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val mangaFilePicker = androidx.activity.compose.rememberLauncherForActivityResult(
                        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
                    ) { uri ->
                        if (uri != null) {
                            mangaFileUri = uri.toString()
                            mangaFileName = runCatching {
                                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                                    val column = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                    if (cursor.moveToFirst() && column >= 0) cursor.getString(column).orEmpty() else ""
                                } ?: ""
                            }.getOrDefault("")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(InkSpacing.sm),
                    ) {
                        InkOutlinedButton(
                            label = if (mangaFileUri.isBlank()) "Choose file" else "Change file",
                            onClick = {
                                mangaFilePicker.launch(
                                    arrayOf("application/pdf", "application/zip", "application/x-cbz", "image/*"),
                                )
                            },
                        )
                        if (mangaFileUri.isNotBlank()) {
                            InkOutlinedButton(
                                label = "Clear",
                                onClick = { mangaFileUri = ""; mangaFileName = "" },
                            )
                        }
                    }
                    if (mangaFileUri.isNotBlank()) {
                        Text(
                            "Selected: ${mangaFileName.ifBlank { "file" }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.secondaryText,
                            maxLines = 1,
                        )
                    }
                } else if (isCampaign) {
                    Text("Setting template", style = MaterialTheme.typography.labelMedium)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        InkOutlinedButton(
                            label = CampaignSettingTemplates.first { it.id == settingId }.label + " ▾",
                            onClick = { settingMenuOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        DropdownMenu(
                            expanded = settingMenuOpen,
                            onDismissRequest = { settingMenuOpen = false },
                        ) {
                            CampaignSettingTemplates.forEach { template ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(template.label)
                                            Text(
                                                template.directive,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = tokens.secondaryText,
                                                maxLines = 3,
                                            )
                                        }
                                    },
                                    onClick = {
                                        settingId = template.id
                                        settingMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = genre,
                        onValueChange = { genre = it },
                        label = { Text("Setting details") },
                        placeholder = { Text("Kingdom, era, locations, factions, tone…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        if (isTextGame) "Summoner / main character" else "Main character(s)",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    if (characterOptions.isEmpty()) {
                        Text(
                            if (isTextGame) {
                                "Add a persona or character first, or continue with an unnamed first-person Summoner."
                            } else {
                                "Add a persona, roster member, or Characters Codex entry first—or continue without one."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.secondaryText,
                        )
                    } else {
                        var characterQuery by remember { mutableStateOf("") }
                        OutlinedTextField(
                            value = characterQuery,
                            onValueChange = { characterQuery = it },
                            label = { Text("Search characters") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val visibleCharacters = if (characterQuery.isBlank()) {
                            characterOptions
                        } else {
                            characterOptions.filter { it.name.contains(characterQuery, ignoreCase = true) }
                        }
                        if (visibleCharacters.isEmpty()) {
                            Text(
                                "No characters match \"${characterQuery}\".",
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.secondaryText,
                            )
                        } else {
                            // Two visible rows of chips; scrolls for longer casts.
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(84.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(InkSpacing.xs)) {
                                    visibleCharacters.chunked(3).forEach { rowCharacters ->
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(InkSpacing.xs),
                                        ) {
                                            rowCharacters.forEach { option ->
                                                val selected = option.id in selectedCharacterIds
                                                InkChip(
                                                    label = if (selected) "✓ ${option.name}" else option.name,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    selected = selected,
                                                    onClick = {
                                                        selectedCharacterIds = if (selected) {
                                                            selectedCharacterIds - option.id
                                                        } else if (isTextGame) {
                                                            setOf(option.id)
                                                        } else {
                                                            selectedCharacterIds + option.id
                                                        }
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        val selectedCharacters = characterOptions.filter { it.id in selectedCharacterIds }
                        Text(
                            if (selectedCharacters.isEmpty()) {
                                if (isTextGame) {
                                    "Unnamed Summoner selected — narration still remains first-person."
                                } else {
                                    "No character selected — the AI DM will help you create one when play begins."
                                }
                            } else {
                                selectedCharacters.joinToString(" · ") { "${it.name} (${it.source})" }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.secondaryText,
                            maxLines = 2,
                        )
                    }
                    if (isTextGame) {
                        Card(colors = CardDefaults.cardColors(containerColor = tokens.panel)) {
                            Column(Modifier.padding(InkSpacing.sm)) {
                                Text("PLAY AS · SUMMONER / MC", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    "You control the off-field Summoner in first person. Narration always uses present tense; the game and AI never choose your actions.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.secondaryText,
                                )
                            }
                        }
                        Text("Difficulty", style = MaterialTheme.typography.labelMedium)
                        InkSegmentedPill(
                            options = TextGameDifficultyTemplates.map { SegmentedOption(it.id, it.label) },
                            selectedId = difficultyId,
                            onSelect = { difficultyId = it },
                            compact = true,
                        )
                        Text(
                            TextGameDifficultyTemplates.first { it.id == difficultyId }.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.secondaryText,
                        )
                    } else {
                        Text("Play as", style = MaterialTheme.typography.labelMedium)
                        InkSegmentedPill(
                            options = listOf(
                                SegmentedOption("player", "Character(s)"),
                                SegmentedOption("dm", "Dungeon Master"),
                            ),
                            selectedId = campaignRoleId,
                            onSelect = { campaignRoleId = it },
                            compact = true,
                        )
                        Text(
                            if (campaignRoleId == "dm") {
                                "You run the world and rulings; the AI plays the selected party."
                            } else {
                                "You play the selected character(s); the AI runs the world and its cast."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.secondaryText,
                        )
                        Text("Point of view", style = MaterialTheme.typography.labelMedium)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            InkOutlinedButton(
                                label = CampaignPerspectiveTemplates.first { it.id == narrativePovId }.label + " ▾",
                                onClick = { perspectiveMenuOpen = true },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            DropdownMenu(
                                expanded = perspectiveMenuOpen,
                                onDismissRequest = { perspectiveMenuOpen = false },
                            ) {
                                CampaignPerspectiveTemplates.forEach { template ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(template.label)
                                                Text(
                                                    template.directive,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = tokens.secondaryText,
                                                    maxLines = 3,
                                                )
                                            }
                                        },
                                        onClick = {
                                            narrativePovId = template.id
                                            perspectiveMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                        Text("Tense", style = MaterialTheme.typography.labelMedium)
                        InkSegmentedPill(
                            options = listOf(
                                SegmentedOption("Past tense", "Past"),
                                SegmentedOption("Present tense", "Present"),
                                SegmentedOption("Future tense", "Future"),
                            ),
                            selectedId = tense.ifBlank { "Past tense" },
                            onSelect = { tense = it },
                            compact = true,
                        )
                    }
                    Text("Rules system", style = MaterialTheme.typography.labelMedium)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        InkOutlinedButton(
                            label = CampaignRulesetTemplates.first { it.id == rulesetId }.label + " ▾",
                            onClick = { rulesetMenuOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        DropdownMenu(
                            expanded = rulesetMenuOpen,
                            onDismissRequest = { rulesetMenuOpen = false },
                        ) {
                            CampaignRulesetTemplates.forEach { template ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(template.label)
                                            Text(
                                                template.directive,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = tokens.secondaryText,
                                                maxLines = 3,
                                            )
                                        }
                                    },
                                    onClick = {
                                        rulesetId = template.id
                                        rulesetMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = pov,
                        onValueChange = { pov = it },
                        label = { Text(vocabulary.povLabel) },
                        placeholder = { Text("First person, third limited…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Tense", style = MaterialTheme.typography.labelMedium)
                    InkSegmentedPill(
                        options = listOf(
                            SegmentedOption("Past tense", "Past"),
                            SegmentedOption("Present tense", "Present"),
                            SegmentedOption("Future tense", "Future"),
                        ),
                        selectedId = tense.ifBlank { "Past tense" },
                        onSelect = { tense = it },
                    )
                }
                OutlinedTextField(
                    value = styleGuide,
                    onValueChange = { styleGuide = it },
                    label = { Text(if (isCampaign) "Additional house rules" else vocabulary.styleLabel) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    vocabulary.styleHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.secondaryText,
                )
                Text(
                    if (vocabulary.storyboardSpecific) {
                        "Only the series title is required. Main art can be set later and appears in Window."
                    } else {
                        "Only the title is needed now — the rest can be filled in later, " +
                            "and a cover is set from the editor."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.secondaryText,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onCreate(
                    NewWorkDetails(
                        title = title.trim().ifBlank { vocabulary.titlePlaceholder },
                        genre = if (isCampaign) {
                            val settingLabel = CampaignSettingTemplates.first { it.id == settingId }.label
                            listOf(settingLabel, genre.trim()).filter { it.isNotBlank() }.joinToString(" — ")
                        } else genre.trim(),
                        pov = if (isCampaign) {
                            characterOptions.filter { it.id in selectedCharacterIds }.joinToString(", ") { it.name }
                                .ifBlank { if (isTextGame) "Summoner / MC" else "" }
                        } else pov.trim(),
                        tense = if (isTextGame) "Present tense" else tense.trim().ifBlank { if (isCampaign) "Past tense" else "" },
                        styleGuide = if (isCampaign) {
                            listOf(
                                "Setting guidance: ${CampaignSettingTemplates.first { it.id == settingId }.directive}",
                                "Rules guidance: ${CampaignRulesetTemplates.first { it.id == rulesetId }.directive}",
                                if (isTextGame) {
                                    "Perspective guidance: Always narrate in first person from the Summoner/MC's point of view and always use present tense. Never switch tense or viewpoint."
                                } else {
                                    "Perspective guidance: ${CampaignPerspectiveTemplates.first { it.id == narrativePovId }.directive}"
                                },
                                if (isTextGame) {
                                    "User role guidance: The user is the Summoner/MC and alone chooses the Summoner's actions, words, thoughts, and decisions. The AI controls the world and supporting cast but never impersonates the Summoner."
                                } else if (campaignRoleId == "dm") {
                                    "User role guidance: The user is the Dungeon Master and has authority over the world, scenes, NPCs, and rulings. The AI controls the selected player-character party and must respond with their decisions, actions, and dialogue without overriding the user's world narration."
                                } else {
                                    "User role guidance: The user controls the selected player character(s). The AI is the Dungeon Master and controls the world, NPCs, opposition, and consequences without choosing the player's actions."
                                },
                                if (isTextGame) {
                                    "Difficulty: ${TextGameDifficultyTemplates.first { it.id == difficultyId }.label}. ${TextGameDifficultyTemplates.first { it.id == difficultyId }.description}"
                                } else "",
                                styleGuide.trim().takeIf { it.isNotBlank() }?.let { "House rules: $it" }.orEmpty(),
                            ).filter { it.isNotBlank() }.joinToString("\n\n")
                        } else styleGuide.trim(),
                        mainCharacters = characterOptions.filter { it.id in selectedCharacterIds },
                        rulesetId = if (isCampaign) rulesetId else "",
                        settingId = if (isCampaign) settingId else "",
                        narrativePov = if (isTextGame) {
                            "First-person Summoner"
                        } else if (isCampaign) {
                            CampaignPerspectiveTemplates.first { it.id == narrativePovId }.label
                        } else "",
                        campaignRoleId = if (isTextGame) "player" else if (isCampaign) campaignRoleId else "",
                        difficultyId = if (isTextGame) difficultyId else "standard",
                        mangaFileUri = if (vocabulary.storyboardSpecific) mangaFileUri else "",
                        mangaFileName = if (vocabulary.storyboardSpecific) mangaFileName else "",
                    ),
                )
                onDismiss()
            }) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
