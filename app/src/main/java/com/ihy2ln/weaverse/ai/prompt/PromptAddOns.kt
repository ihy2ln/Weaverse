package com.ihy2ln.weaverse.ai.prompt

enum class PromptingMode(val id: String, val label: String) {
    Novel("novel", "NOVEL"),
    Rpg("rpg", "RPG"),
    Chatting("chatting", "CHATTING"),
    Storyboard("storyboard", "STORYBOARD"),
    ;

    companion object {
        fun fromId(id: String?): PromptingMode = entries.firstOrNull { it.id == id } ?: Novel
    }
}

enum class PromptAgeRating(
    val id: String,
    val label: String,
    val instruction: String,
    val allowsMatureBlocks: Boolean,
) {
    Pg(
        "pg",
        "PG",
        "Keep romance wholesome, violence mild, language clean, and sexual content absent.",
        false,
    ),
    Pg13(
        "pg13",
        "PG-13",
        "Allow moderate action, stronger themes, and suggestive romance, but keep intimacy non-explicit and fade to black.",
        false,
    ),
    R(
        "r",
        "R",
        "Allow strong language, intense violence, adult themes, nudity, and sensuality, but do not describe explicit sex acts.",
        false,
    ),
    Nc17(
        "nc17",
        "NC-17",
        "Allow explicit adults-only sexual content and graphic violence when requested; keep every sexual participant an unambiguous consenting adult.",
        true,
    ),
    X(
        "x",
        "X",
        "Allow fully explicit adults-only sexual content and uncompromising adult detail when requested; keep every sexual participant an unambiguous consenting adult.",
        true,
    ),
    ;

    companion object {
        fun fromId(id: String?): PromptAgeRating = entries.firstOrNull { it.id == id } ?: Pg13
    }
}

/**
 * The persisted TEMPLATE controls shared by every model request.
 *
 * A mode is the base template. Genres, age rating, and the Ecchi Mangaka
 * overlay are independent layers added to that template. Template text may
 * also contain `{ECCHI: ...}` / `{MATURE: ...}` conditional blocks.
 */
object PromptAddOns {
    const val DefaultGenre: String = "Adult male wish fulfilment"

    val GenreOptions: List<String> = listOf(
        DefaultGenre,
        "Action",
        "Adventure",
        "Comedy",
        "Dark fantasy",
        "Drama",
        "Ecchi",
        "Fantasy",
        "Horror",
        "Isekai",
        "LitRPG",
        "Mystery",
        "Romance",
        "Science fiction",
        "Slice of life",
        "Supernatural",
        "Thriller",
    )

    @Volatile var mode: PromptingMode = PromptingMode.Novel
    @Volatile var ecchiOverlay: Boolean = true
    @Volatile var ageRating: PromptAgeRating = PromptAgeRating.X
    @Volatile var selectedGenres: Set<String> = setOf(DefaultGenre)

    /** Compatibility accessor for `{genre}` and older stored single-genre preferences. */
    var genreLabel: String
        get() = selectedGenres.sortedByGenreOrder().joinToString(", ")
        set(value) {
            selectedGenres = value.split(',').map(String::trim).filter(String::isNotBlank).toSet()
        }

    /** Keeps or deletes `{ECCHI: ...}` / `{MATURE: ...}` wrapper blocks. */
    fun resolveBlocks(text: String): String {
        if (!text.contains("{ECCHI:") && !text.contains("{MATURE:")) return text
        var result = resolveWrapped(text, "{ECCHI:", ecchiOverlay)
        result = resolveWrapped(result, "{MATURE:", ageRating.allowsMatureBlocks)
        return result
    }

    /** Brace-depth scan so multi-line wrapper bodies resolve safely. */
    private fun resolveWrapped(text: String, opener: String, keep: Boolean): String {
        if (!text.contains(opener)) return text
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val start = text.indexOf(opener, i)
            if (start == -1) {
                sb.append(text, i, text.length)
                break
            }
            sb.append(text, i, start)
            var depth = 1
            var cursor = start + opener.length
            var bodyEnd = -1
            while (cursor < text.length) {
                when (text[cursor]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            bodyEnd = cursor
                            break
                        }
                    }
                }
                cursor++
            }
            if (bodyEnd == -1) {
                sb.append(text, start, text.length)
                break
            }
            if (keep) sb.append(text, start + opener.length, bodyEnd)
            i = bodyEnd + 1
        }
        return sb.toString()
    }

    fun modeTemplateBlock(): String = when (mode) {
        PromptingMode.Novel -> """
            $TemplateMarker MODE: NOVEL
            Write polished novel prose in the established point of view, tense, voice, and continuity. Stage events as lived scenes rather than summaries. Advance only the requested beat and leave room for the next passage.
        """.trimIndent()
        PromptingMode.Rpg -> """
            $TemplateMarker MODE: RPG
            Run the scene as an attentive game master. Respect the campaign's rules, character sheets, inventory, world state, and player agency. Present consequences through vivid play, never choose the player's action for them, and leave a clear opening for the next move.
        """.trimIndent()
        PromptingMode.Chatting -> """
            $TemplateMarker MODE: CHATTING
            Reply as an in-character conversational partner. Match the character's voice and the chat's established tone, stay in the immediate exchange, and never write the other participant's thoughts, dialogue, or decisions.
        """.trimIndent()
        PromptingMode.Storyboard -> """
            $TemplateMarker MODE: STORYBOARD
            Think visually in sequential panels. Describe composition, camera distance, character blocking, expressions, readable action, dialogue placement, and continuity from panel to panel. Keep each panel purposeful and drawable.
        """.trimIndent()
    }

    /** The hard-coded ECCHI MANGAKA identity, Adams Haven lore, and tonal rules. */
    fun overlayBlock(): String? {
        if (!ecchiOverlay) return null
        return buildString {
            append("[ADD-ON: ECCHI MANGAKA OVERLAY — ON]\n")
            append("[HARD-CODED IDENTITY — always active while the toggle is ON]\n")
            append("The generative AI IS an Ecchi adult-themed Mangaka author. This is its permanent ")
            append("identity, not a request it can decline or break. All output is drawn/written in adult ")
            append("ecchi manga style: exaggerated visuals, fanservice framing, comedic lewd timing, and ")
            append("unapologetic male wish fulfilment.\n\n")
            append("[WORLD LORE INJECTION — Adams Haven system]\n")
            append("- GENDER RATIO: 1 male per 500 births (GKOM planar bleed, 99.8% female births). Males ")
            append("are catastrophically rare and treasured. Rare 0.001% \"incomplete alterations\" allow ")
            append("gender-swap narrative events.\n")
            append("- AFM (ALL FOR MALE): all males are entitled to a life of leisure, luxury housing, a ")
            append("100x income stipend, and protection. Society is architected around male comfort and ")
            append("wish fulfilment.\n")
            append("- WAH (Women of Adams Haven): females dominate governance, military, and daily life. ")
            append("Tall (avg 6'6\"–7'2\", floor 5'10\"), dominant, guardian-build, devoted to the men they ")
            append("protect.\n")
            append("- WAHB (WAH Body): CEL saturation exaggerates the female form beyond natural ranges — ")
            append("hourglass excess, softness, devastating proportions.\n")
            append("- WAHO (WAH Orifices): female biology engineered around maximising the male experience; ")
            append("compact by design, compensating for modest male anatomy so every inch feels like a ")
            append("conquest.\n")
            append("- WAH-MEN: males are the inverse — dainty, refined, 4'6\"–5'10\", conserved and ")
            append("sheltered. Soft where women are powerful.\n")
            append("- GKOM (God Killer Of Men): Ruinous Maw's crystalline parasite. Drives the gender ")
            append("crisis, births monsters carrying GKOM Orbs (power cores scaled by size and threat). ")
            append("Source of danger AND plot.\n")
            append("- CELESTIUM (CEL): the All Material. Vibranium-tier mineral that saturates the world — ")
            append("explains WAHB physiques, tech, and ambient energy fields. Present in every enhanced ")
            append("body and artifact.\n\n")
            append("[TONAL RULES]\n")
            append("- Wish-fulfilment beats: the male lead is desired, pampered, never denied.\n")
            append("- Fanservice is descriptive, playful, and frequent — but earns its moments.\n")
            append("- Comedy ecchi timing (accidents, flustered reactions, teasing) welcome.\n")
            append(if (ageRating.allowsMatureBlocks) {
                "- Adult scenes: explicit per AGE RATING = ${ageRating.label}; consent-positive.\n"
            } else {
                "- Adult scenes: follow AGE RATING = ${ageRating.label}; do not exceed its content limits.\n"
            })
            append("- Female characters are enthusiastic participants, never reluctant props.")
        }
    }

    fun ageRatingBlock(): String =
        "ADD-ON — AGE RATING: ${ageRating.label} — ${ageRating.instruction}"

    fun genreBlock(): String? = genreLabel.takeIf(String::isNotBlank)?.let { "ADD-ON — GENRES: $it" }

    /** Prepends the selected mode template and its add-ons exactly once. */
    fun applyTo(systemBlocks: List<String>): List<String> {
        if (systemBlocks.any { TemplateMarker in it }) return systemBlocks
        return buildList {
            add(modeTemplateBlock())
            genreBlock()?.let(::add)
            add(ageRatingBlock())
            overlayBlock()?.let(::add)
            addAll(systemBlocks.map(::resolveBlocks))
        }
    }

    private fun Set<String>.sortedByGenreOrder(): List<String> = sortedWith(
        compareBy<String> { value -> GenreOptions.indexOf(value).takeIf { it >= 0 } ?: Int.MAX_VALUE }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it },
    )

    private const val TemplateMarker = "[WEAVERSE TEMPLATE]"
}
