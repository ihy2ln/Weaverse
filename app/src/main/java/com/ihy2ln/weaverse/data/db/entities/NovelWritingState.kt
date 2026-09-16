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
)
