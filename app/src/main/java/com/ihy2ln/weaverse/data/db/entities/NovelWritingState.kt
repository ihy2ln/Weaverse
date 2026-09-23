package com.ihy2ln.weaverse.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "novel_prompt_drafts")
data class NovelPromptDraft(@PrimaryKey val sceneId: String, val stateJson: String)

@Entity(tableName = "novel_writing_settings")
data class NovelWritingSettings(
    @PrimaryKey val bookId: String,
    val memory: String = "",
    val authorNote: String = "",
    val modelRef: String = "",
    val outputWords: Int = 100,
    /** Solo / Duo / Party / Team chosen in the book's start; drives a hard prompt rule. */
    val companions: String = com.ihy2ln.weaverse.core.story.StoryCompanionMode.Party.id,
    /**
     * The four-step start's saved progress, as JSON, so closing the wizard part-way
     * leaves something to come back to. Blank once the start has been finished, or
     * when it was never begun.
     */
    val startProgress: String = "",
)
