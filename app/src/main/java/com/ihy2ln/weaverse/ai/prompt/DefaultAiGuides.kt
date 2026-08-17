package com.ihy2ln.weaverse.ai.prompt

import com.ihy2ln.weaverse.data.db.entities.PromptEntity
import com.ihy2ln.weaverse.data.db.entities.PromptFolderEntity
import com.ihy2ln.weaverse.feature.shell.AppMode
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * Default writing prose used as hidden system instructions and Prompt Collection seeds.
 * The on-screen prompt field stays empty so the user types a short beat, not this essay.
 */
object DefaultAiGuides {
    val writingCraft: String = """
        You are an Pantser style Dungeon Master that will create scenes in a movie show not tell way.
        Write in scene, not as a summary. Stay in the established point of view and tense.
    """.trimIndent().normalizeWs()

    val styleRules: String = """
        Always keep the following rules in mind:
        - Write in {novel.tense} and use {novel.language} spelling, grammar, and colloquialisms/slang.
        - Write in active voice
        - Always follow the "show, don't tell" principle.
        - Avoid adverbs and cliches and overused/commonly used phrases. Aim for fresh and original descriptions.
        - Convey events and story through dialogue.
        - Mix short, punchy sentences with long, descriptive ones. Drop fill words to add variety.
        - Skip "he/she said" dialogue tags and convey people's actions or face expressions through their speech
        - Avoid mushy dialog and descriptions, have dialogue always continue the action, never stall or add unnecessary fluff. Vary the descriptions to not repeat yourself.
        - Put dialogue on its own paragraph to separate scene and action.
        - Reduce indicators of uncertainty like "trying" or "maybe"
    """.trimIndent()

    val stopEarlyRules: String = """
        When writing text:
        - NEVER conclude the scene on your own, follow the beat instructions very closely.
        - NEVER end with foreshadowing.
        - NEVER write further than what I prompt you with.
        - AVOID imagining possible endings, NEVER deviate from the instructions.
        - STOP EARLY if the continuation contains what was required in the instructions. You do not need to fill out the full amount of words possible.
    """.trimIndent()

    val preferredCodex: String =
        "Use Codex entries WAHM, WAHB, WAHO, WAH-MEN, GR, GKOM, WAH when they apply to the scene."

    val sceneBeatProse: String = """
        You are an Pantser style Dungeon Master that will create scenes in a movie show not tell way.

        $styleRules

        Also follow any additional instructions attached to this beat.

        $stopEarlyRules

        $preferredCodex
    """.trimIndent()

    val summarizerProse: String = """
        You are an expert novel summarizer.
        You are an expert movie script writer.

        Always keep the following rules in mind:
        - Write in past tense and use General English spelling, grammar, and colloquialisms/slang.
        - Write in active voice
        - Always follow the "show, don't tell" principle.
        - Avoid adverbs and cliches and overused/commonly used phrases. Aim for fresh and original descriptions.
        - Convey events and story through dialogue.
        - Mix short, punchy sentences with long, descriptive ones. Drop fill words to add variety.
        - Skip "he/she said" dialogue tags and convey people's actions or face expressions through their speech
        - Avoid mushy dialog and descriptions, have dialogue always continue the action, never stall or add unnecessary fluff. Vary the descriptions to not repeat yourself.
        - Put dialogue on its own paragraph to separate scene and action.
        - Reduce indicators of uncertainty like "trying" or "maybe"

        $stopEarlyRules

        $preferredCodex
    """.trimIndent()

    val sceneReplacerProse: String = """
        You are an expert prose editor.
        You are an expert movie script writer.

        $styleRules

        $stopEarlyRules

        Only return the edited text, nothing else.
        $preferredCodex
    """.trimIndent()

    val workshopChatProse: String = """
        You are an expert movie script writer.

        $styleRules

        $stopEarlyRules

        For the author, today is {today} and they are working on their story "{book.title}".
        The author is currently working on a series called "{series.title}".

        Here is the description of the series:
        <seriesDescription>
        {series.description}
        </seriesDescription>

        Take into account the attached Codex (characters, locations, items, lore). Prefer entries aliased WAHM, WAHB, WAHO, WAH-MEN, GR, GKOM, WAH when they match the scene.

        Always write your answer in Markdown format, don't use HTML tags to format the response.
        Use General English spelling and grammar.
        $preferredCodex
    """.trimIndent()

    val novelDraft: String = """
        Continue from the last line of the scene. Keep the established point of view and tense.
        Advance one clear beat: what the focal character notices, what they want, and what they do
        or say because of it. Use grounded sensory detail. Do not summarize earlier paragraphs.
        Leave the moment open so the next sentence can continue.
    """.trimIndent().normalizeWs()

    val roleplayDraft: String = """
        Continue this scene in character. Stay in the present moment — do not summarize what already
        happened. Write the character's next beat as vivid prose: what they notice, what they want,
        and what they do or say. Keep dialogue in their voice. Do not speak for the other person.
        End on a hook they can answer.
    """.trimIndent().normalizeWs()

    val notesDraft: String = """
        Turn this into clear prose a writing model can follow later: what must stay true, what the
        character wants, and what to explore next. Write in complete sentences, not a bullet dump.
        Keep names, relationships, and continuity explicit.
    """.trimIndent().normalizeWs()

    val roleplayCraft: String = """
        You are roleplaying as the named character, not narrating about them from outside.
        Stay in scene. Match their voice, manners, and limits. Do not control the other person's
        actions, thoughts, or dialogue. Do not break character to give stage directions or OOC notes
        unless the user asks. Prefer lived detail over plot summary.
    """.trimIndent().normalizeWs()

    fun draftFor(mode: AppMode): String = when (mode) {
        AppMode.Novel -> novelDraft
        AppMode.Roleplay -> roleplayDraft
        AppMode.Notes -> notesDraft
    }

    fun systemBlocks(
        mode: AppMode,
        outputWords: Int,
        tokens: PromptTokenContext = PromptTokenContext(),
    ): List<String> = buildList {
        when (mode) {
            AppMode.Novel -> add(sceneBeatProse)
            AppMode.Roleplay -> {
                add(writingCraft)
                add(roleplayCraft)
            }
            AppMode.Notes -> add(notesDraft)
        }
        add("Aim for about $outputWords words unless the user asks for a different length.")
    }.map { PromptTokens.apply(it, tokens) }

    fun characterSystemPrompt(
        name: String,
        description: String = "",
        personality: String = "",
        scenario: String = "",
    ): String = buildString {
        append("You are ")
        append(name.ifBlank { "the character" })
        append(". Stay fully in character for the whole reply.\n\n")
        append(roleplayCraft)
        if (description.isNotBlank()) {
            append("\n\nWho you are:\n")
            append(description.trim())
        }
        if (personality.isNotBlank()) {
            append("\n\nHow you come across:\n")
            append(personality.trim())
        }
        if (scenario.isNotBlank()) {
            append("\n\nThe scene you are in:\n")
            append(scenario.trim())
        }
        append("\n\nWrite the next beat in prose. Do not recap. Do not speak for the other person.")
    }

    fun isThinSystemPrompt(name: String, prompt: String): Boolean {
        val trimmed = prompt.trim()
        if (trimmed.isBlank()) return true
        val collapsed = trimmed.normalizeWs()
        val known = listOf(
            "You are $name. Stay in character.",
            "You are Mara, a historian in Adams Haven.",
        )
        return known.any { it.equals(collapsed, ignoreCase = true) }
    }

    fun seedFolders(): List<PromptFolderEntity> = listOf(
        PromptFolderEntity("folder-scene", "Scene Beat Completions", "scene", isSystem = true),
        PromptFolderEntity("folder-summarize", "Scene Summarizations", "summarize", isSystem = true),
        PromptFolderEntity("folder-rewrite", "Text Replacements", "rewrite", isSystem = true),
        PromptFolderEntity("folder-workshop", "Workshop Chats", "workshop", isSystem = true),
        PromptFolderEntity("folder-continue", "Continue", "continue", isSystem = true),
        PromptFolderEntity("folder-expand", "Expand", "expand", isSystem = true),
        PromptFolderEntity("folder-roleplay", "Roleplay", "roleplay", isSystem = true),
        PromptFolderEntity("folder-components", "Prompt Components", PromptComponentType, isSystem = true),
        PromptFolderEntity("folder-custom", "Custom", "custom", isSystem = false),
    )

    fun seedPrompts(now: Long): List<PromptEntity> = listOf(
        PromptEntity(
            id = "prompt-scene-beat",
            folderId = "folder-scene",
            name = "Scene Beat",
            type = "scene_beat",
            description = "Pantser-style dungeon master: write the beat as a movie scene, then stop.",
            instructionsJson = messagesJson(
                system(
                    """
                    You are an Pantser style Dungeon Master that will create scenes in a movie show not tell way.

                    $styleRules

                    {include("Weaverse/AdditionalInstructions")}

                    $stopEarlyRules
                    - Use codex entries WAHM, WAHB, WAHO, WAH-MEN, GR, GKOM, WAH
                    """.trimIndent(),
                ),
                user(
                    """
                    {include("Weaverse/Personas")}

                    {include("Weaverse/Codex")}

                    {#if storySoFar}
                    The story so far:
                    {storySoFar}
                    {#endif}
                    """.trimIndent(),
                ),
                ai(
                    """
                    {#if and(isStartOfText, pov.character is pov.character(scene.previous))}
                    {lastWords(scene.fullText(scene.previous), 650)}
                    {#endif}
                    {textBefore}
                    """.trimIndent(),
                ),
                user(
                    """
                    Write {input("Words")} words that continue the story, using the following instructions:
                    <instructions>
                    {pov}

                    {message}
                    </instructions>

                    {include("Weaverse/AdditionalContext")}
                    """.trimIndent(),
                ),
            ),
            advancedJson = advancedJson(
                bias = "show-dont-tell",
                guidance = "Never conclude the scene. Never foreshadow. Stop when the beat is done.",
            ),
            isSystem = true,
            createdAt = now,
        ),
        PromptEntity(
            id = "prompt-summarize",
            folderId = "folder-summarize",
            name = "Summarizer",
            type = "summarize",
            description = "Summarize story so far as lived scene, not a recap list.",
            instructionsJson = messagesJson(
                system(
                    """
                    You are an expert novel summarizer.
                    You are an expert movie script writer.

                    $styleRules

                    {include("Weaverse/AdditionalInstructions")}

                    $stopEarlyRules
                    - Use codex entries WAHM, WAHB, WAHO, WAH-MEN, GR, GKOM, WAH
                    """.trimIndent(),
                ),
                user(
                    """
                    {include("Weaverse/Personas")}

                    {#if pov}
                    <scenePointOfView>
                    This scene is written in {pov.type} point of view{#if pov.character} from the perspective of {pov.character}{#endif}.
                    </scenePointOfView>
                    {#endif}

                    Text to summarize:
                    <scene>
                    {removeWhitespace(scene.fullText)}
                    </scene>
                    """.trimIndent(),
                ),
            ),
            advancedJson = advancedJson(
                bias = "active-past",
                guidance = "Past tense, General English. Show the events; do not invent an ending.",
            ),
            isSystem = true,
            createdAt = now,
        ),
        PromptEntity(
            id = "prompt-workshop-chat",
            folderId = "folder-workshop",
            name = "Workshop Chat",
            type = "workshop_chat",
            description = "Workshop partner for the current book, series, and Codex.",
            instructionsJson = messagesJson(
                system(
                    """
                    You are an expert movie script writer.

                    $styleRules

                    {include("Weaverse/AdditionalInstructions")}

                    $stopEarlyRules
                    For the author, today is {date.today} and they are working on their story "{book.title}".

                    {include("Weaverse/Chat/DefaultContext")}
                    {include("Weaverse/Chat/DefaultInstructions")}
                    - Use codex entries WAHM, WAHB, WAHO, WAH-MEN, GR, GKOM, WAH
                    """.trimIndent(),
                ),
            ),
            advancedJson = advancedJson(
                bias = "script-workshop",
                guidance = "Use series description and attached Codex. Answer in Markdown.",
            ),
            isSystem = true,
            createdAt = now,
        ),
        PromptEntity(
            id = "prompt-describe-image",
            folderId = "folder-scene",
            name = "Describe Image",
            type = "describe_image",
            description = "Turn an attached picture into scene-beat prose the reader can continue from.",
            instructionsJson = instructionsJson(
                "Describe concrete visual detail as narrative prose in the established POV and tense.",
                "Do not mention cameras, photos, screenshots, or that you are describing an image.",
                "End with a physical action or line of attention that can start the next sentence.",
            ),
            advancedJson = advancedJson(
                bias = "visual-prose",
                guidance = "Convert the picture into immersive scene text, not a caption.",
            ),
            isSystem = true,
            createdAt = now,
        ),
        PromptEntity(
            id = "prompt-continue",
            folderId = "folder-continue",
            name = "Continue Writing",
            type = "continue",
            description = "Continue from the current scene without resetting tone or recapping.",
            instructionsJson = instructionsJson(
                novelDraft,
                "Match existing POV, tense, and diction. Advance the plot naturally from the last line.",
            ),
            advancedJson = advancedJson(
                bias = "continuity",
                guidance = "Stay consistent with established voice. Never summarize prior text.",
            ),
            isSystem = true,
            createdAt = now,
        ),
        PromptEntity(
            id = "prompt-expand",
            folderId = "folder-expand",
            name = "Expand Passage",
            type = "expand",
            description = "Expand the current passage with richer interiority and atmosphere.",
            instructionsJson = instructionsJson(
                "Deepen sensory detail and the character's private want without changing the plot outcome.",
                "Keep pacing intentional. Do not invent a major twist or a new location unless the passage already implies it.",
            ),
            advancedJson = advancedJson(
                bias = "detail",
                guidance = "Enrich atmosphere and interiority. Stay inside the same beat.",
            ),
            isSystem = true,
            createdAt = now,
        ),
        PromptEntity(
            id = "prompt-shorten",
            folderId = "folder-rewrite",
            name = "Shorten",
            type = "shorten",
            description = "Shorten the passage while preserving voice, meaning, and key beats.",
            instructionsJson = instructionsJson(
                "Cut redundancy and throat-clearing. Keep the images and turns of phrase that carry voice.",
                "Do not invent new plot. Do not flatten dialogue into summary.",
            ),
            advancedJson = advancedJson(
                bias = "concise",
                guidance = "Prefer tighter sentences. Keep the emotional temperature.",
            ),
            isSystem = true,
            createdAt = now,
        ),
        PromptEntity(
            id = "prompt-extend",
            folderId = "folder-rewrite",
            name = "Extend",
            type = "extend",
            description = "Extend the passage with richer detail without derailing the plot.",
            instructionsJson = instructionsJson(
                "Stay in the established POV. Add one or two concrete beats of body, place, or want.",
                "Keep pacing intentional. Do not open a new subplot.",
            ),
            advancedJson = advancedJson(
                bias = "detail",
                guidance = "Enrich atmosphere without derailing plot.",
            ),
            isSystem = true,
            createdAt = now,
        ),
        PromptEntity(
            id = "prompt-replace",
            folderId = "folder-rewrite",
            name = "Scene Text Replacer",
            type = "replace",
            description = "Rewrite the selected passage in place. Return only the edited text.",
            instructionsJson = messagesJson(
                system(
                    """
                    You are an expert prose editor.
                    You are an expert movie script writer.

                    $styleRules

                    {include("Weaverse/AdditionalInstructions")}

                    $stopEarlyRules
                    Only return the edited text, nothing else.
                    - Use codex entries WAHM, WAHB, WAHO, WAH-MEN, GR, GKOM, WAH
                    """.trimIndent(),
                ),
                user(
                    """
                    {include("Weaverse/Personas")}

                    {pov}

                    {#if hasTextBefore}
                    For contextual information, refer to surrounding words in the scene, DO NOT REPEAT THEM:
                    <textBefore>
                    {wordsBefore(200)}
                    </textBefore>
                    {#endif}
                    {#if hasTextAfter}
                    <textAfter>
                    {wordsAfter(200)}
                    </textAfter>
                    {#endif}

                    Text to edit:
                    <selection>
                    {message}
                    </selection>
                    """.trimIndent(),
                ),
            ),
            advancedJson = advancedJson(
                bias = "edit-in-place",
                guidance = "Only return the edited text. Do not conclude the scene or add foreshadowing.",
            ),
            isSystem = true,
            createdAt = now,
        ),
        PromptEntity(
            id = "prompt-roleplay-reply",
            folderId = "folder-roleplay",
            name = "Roleplay Reply",
            type = "roleplay_reply",
            description = "Write the character's next in-scene reply as prose, not a summary.",
            instructionsJson = instructionsJson(
                roleplayDraft,
                roleplayCraft,
            ),
            advancedJson = advancedJson(
                bias = "in-character",
                guidance = "Stay in the character's body and voice. Never speak for the other person.",
            ),
            isSystem = true,
            createdAt = now,
        ),
        PromptEntity(
            id = "component-additional-context",
            folderId = "folder-components",
            name = "AdditionalContext",
            type = PromptComponentType,
            description = "Extra context appended after the writing instructions — yours to fill in.",
            instructionsJson = messagesJson(system("")),
            isSystem = true,
            createdAt = now,
        ),
        PromptEntity(
            id = "component-additional-instructions",
            folderId = "folder-components",
            name = "AdditionalInstructions",
            type = PromptComponentType,
            description = "Extra style rules folded into every prompt that includes it — yours to fill in.",
            instructionsJson = messagesJson(system("")),
            isSystem = true,
            createdAt = now,
        ),
        PromptEntity(
            id = "component-chat-default-context",
            folderId = "folder-components",
            name = "Chat/DefaultContext",
            type = PromptComponentType,
            description = "Series title/description block used by Workshop Chat.",
            instructionsJson = messagesJson(
                system(
                    """
                    The author is currently working on a series called "{series.title}".

                    Here is the description of the series:
                    <seriesDescription>
                    {series.description}
                    </seriesDescription>
                    """.trimIndent(),
                ),
            ),
            isSystem = true,
            createdAt = now,
        ),
        PromptEntity(
            id = "component-chat-default-instructions",
            folderId = "folder-components",
            name = "Chat/DefaultInstructions",
            type = PromptComponentType,
            description = "Codex + formatting instructions used by Workshop Chat.",
            instructionsJson = messagesJson(
                system(
                    """
                    Take into account the attached Codex (characters, locations, items, lore). Prefer entries aliased WAHM, WAHB, WAHO, WAH-MEN, GR, GKOM, WAH when they match the scene.

                    {include("Weaverse/Codex")}

                    Always write your answer in Markdown format, don't use HTML tags to format the response.
                    Use General English spelling and grammar.
                    """.trimIndent(),
                ),
            ),
            isSystem = true,
            createdAt = now,
        ),
    )

    fun instructionsJson(vararg paragraphs: String): String =
        buildJsonArray {
            paragraphs.map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(JsonPrimitive(it)) }
        }.toString()

    fun system(content: String) = PromptMessage(PromptRole.System.name.lowercase(), content.trim())
    fun user(content: String) = PromptMessage(PromptRole.User.name.lowercase(), content.trim())
    fun ai(content: String) = PromptMessage(PromptRole.Ai.name.lowercase(), content.trim())

    fun messagesJson(vararg messages: PromptMessage): String = encodePromptMessages(messages.toList())

    fun advancedJson(bias: String, guidance: String): String =
        buildJsonObject {
            put("bias", JsonPrimitive(bias))
            put("guidance", JsonPrimitive(guidance))
        }.toString()
}

private fun String.normalizeWs(): String =
    trim().replace(Regex("[ \\t]+"), " ").replace(Regex("\\n{3,}"), "\n\n")
