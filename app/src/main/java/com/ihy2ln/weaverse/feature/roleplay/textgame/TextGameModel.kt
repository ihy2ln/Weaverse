package com.ihy2ln.weaverse.feature.roleplay.textgame

import kotlinx.serialization.Serializable

/* Adams Haven art records shared by the Codex, RPG scenes and the Pictures library. */

@Serializable
data class TextGameSceneAsset(
    val id: String,
    /** Logical lookup key such as crossroads, town, farm, dungeon, or battle. */
    val sceneTypes: List<String>,
    val mediaId: String,
    val artAssetPath: String,
    val width: Int,
    val height: Int,
    val displayName: String,
    val category: String,
    val tags: List<String>,
)

@Serializable
data class TextGameCollectibleCard(
    val id: String,
    val title: String,
    val category: String,
    /** Stable ID registered in WeaverVerse's shared Pictures database. */
    val mediaId: String,
    val artAssetPath: String,
    /**
     * Optional muted looping MP4 for the card face (Ken Burns / FMV).
     * When present, battle and library UIs prefer it over [artAssetPath].
     */
    val motionAssetPath: String? = null,
    /** Stable media ID for the motion file in the shared library. */
    val motionMediaId: String? = null,
)
