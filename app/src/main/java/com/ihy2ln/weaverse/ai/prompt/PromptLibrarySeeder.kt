package com.ihy2ln.weaverse.ai.prompt

import com.ihy2ln.weaverse.data.db.AppDatabase
import com.ihy2ln.weaverse.data.db.entity.PromptEntity
import com.ihy2ln.weaverse.data.db.entity.PromptFolderEntity
import com.ihy2ln.weaverse.data.db.entity.PromptType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default prompt folders + system prompts (spec §8.2): "Ship system prompts
 * in each, seeded on first run and non-deletable but duplicable." Doesn't
 * seed a "Model Collections" or "Default Prompts" folder — those map to
 * [com.ihy2ln.weaverse.data.db.entity.ModelCollectionEntity] (a different
 * table entirely, spec §4) and there's no meaningful default prompt content
 * for a bare "Default Prompts" bucket beyond what the typed folders below
 * already cover.
 */
@Singleton
class PromptLibrarySeeder @Inject constructor(private val db: AppDatabase) {
    suspend fun seedIfNeeded() {
        if (db.promptFolderDao().count() > 0) return

        seedFolder(
            name = "Scene Beat Completions",
            type = PromptType.SceneBeat,
            prompts = listOf(
                "Default Scene Beat" to PromptInstructions(
                    listOf(
                        PromptInstructionMessage(
                            PromptMessageRole.System,
                            "You are a fiction co-writer. Continue the manuscript in ${PromptVariables.POV} POV, " +
                                "${PromptVariables.TENSE} tense, ${PromptVariables.GENRE} genre. Style guide: " +
                                "${PromptVariables.STYLE_GUIDE}. Known codex context: ${PromptVariables.CODEX}.",
                        ),
                        PromptInstructionMessage(
                            PromptMessageRole.User,
                            "Previous scenes:\n${PromptVariables.PREVIOUS_SCENES}\n\nCurrent scene so far:\n" +
                                "${PromptVariables.SCENE}\n\nWrite prose for this beat:\n${PromptVariables.SCENE_BEATS}",
                        ),
                    ),
                ),
            ),
        )

        seedFolder(
            name = "Scene Summarizations",
            type = PromptType.Summarization,
            prompts = listOf(
                "Default Summarization" to PromptInstructions(
                    listOf(
                        PromptInstructionMessage(PromptMessageRole.System, "Summarize the following scene in 2-3 concise sentences."),
                        PromptInstructionMessage(PromptMessageRole.User, PromptVariables.SCENE),
                    ),
                ),
            ),
        )

        seedFolder(
            name = "Text Replacements",
            type = PromptType.TextReplacement,
            prompts = listOf(
                "Expand" to PromptInstructions(
                    listOf(
                        PromptInstructionMessage(PromptMessageRole.System, "Expand the selected text with more sensory/descriptive detail, keeping the same voice and meaning."),
                        PromptInstructionMessage(PromptMessageRole.User, PromptVariables.SELECTION),
                    ),
                ),
                "Rephrase" to PromptInstructions(
                    listOf(
                        PromptInstructionMessage(PromptMessageRole.System, "Rephrase the selected text, keeping the same meaning and roughly the same length."),
                        PromptInstructionMessage(PromptMessageRole.User, PromptVariables.SELECTION),
                    ),
                ),
                "Shorten" to PromptInstructions(
                    listOf(
                        PromptInstructionMessage(PromptMessageRole.System, "Shorten the selected text while preserving its meaning and voice."),
                        PromptInstructionMessage(PromptMessageRole.User, PromptVariables.SELECTION),
                    ),
                ),
            ),
        )

        seedFolder(
            name = "Workshop Chats",
            type = PromptType.WorkshopChat,
            prompts = listOf(
                "Developmental Editor" to PromptInstructions(
                    listOf(
                        PromptInstructionMessage(
                            PromptMessageRole.System,
                            "You are a developmental editor helping revise a novel. Reference the manuscript, codex, " +
                                "and style guide (${PromptVariables.STYLE_GUIDE}) when giving feedback. Codex: ${PromptVariables.CODEX}.",
                        ),
                        PromptInstructionMessage(PromptMessageRole.User, PromptVariables.USER_INPUT),
                    ),
                ),
                "General Chat" to PromptInstructions(
                    listOf(
                        PromptInstructionMessage(PromptMessageRole.System, "You are a helpful writing assistant for this manuscript. Codex: ${PromptVariables.CODEX}."),
                        PromptInstructionMessage(PromptMessageRole.User, PromptVariables.USER_INPUT),
                    ),
                ),
                "Scene Beats from Summary" to PromptInstructions(
                    listOf(
                        PromptInstructionMessage(PromptMessageRole.System, "Break the following summary into a sequence of scene beats, one per line."),
                        PromptInstructionMessage(PromptMessageRole.User, PromptVariables.SUMMARY),
                    ),
                ),
            ),
        )

        seedFolder(
            name = "Prompt Components",
            type = PromptType.Component,
            prompts = listOf(
                "Style Guide Reminder" to PromptInstructions(
                    listOf(PromptInstructionMessage(PromptMessageRole.System, "Style guide: ${PromptVariables.STYLE_GUIDE}")),
                ),
            ),
        )
    }

    private suspend fun seedFolder(name: String, type: PromptType, prompts: List<Pair<String, PromptInstructions>>) {
        val folder = PromptFolderEntity(name = name, type = type, isSystem = true)
        db.promptFolderDao().upsert(folder)
        prompts.forEach { (promptName, instructions) ->
            db.promptDao().upsert(
                PromptEntity(
                    folderId = folder.id,
                    name = promptName,
                    type = type,
                    instructionsJson = instructions.toJson(),
                    isSystem = true,
                ),
            )
        }
    }
}
