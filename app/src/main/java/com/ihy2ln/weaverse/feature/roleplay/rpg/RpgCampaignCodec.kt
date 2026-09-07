package com.ihy2ln.weaverse.feature.roleplay.rpg

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object RpgCampaignCodec {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    fun encode(state: RpgCampaignState): String = json.encodeToString(state)

    fun decode(raw: String): RpgCampaignState {
        if (raw.isBlank() || raw == "{}") return FirstLightChapter.newCampaign(seed = 1L)
        val parsed = runCatching { json.decodeFromString<RpgCampaignState>(raw) }
            .getOrDefault(FirstLightChapter.newCampaign(seed = 1L))
        return migrate(parsed)
    }

    fun migrate(state: RpgCampaignState): RpgCampaignState {
        val withSchema = if (state.schemaVersion <= 0) {
            state.copy(schemaVersion = RPG_SCHEMA_VERSION)
        } else {
            state
        }
        val companions = withSchema.companions.ifEmpty {
            listOf(RpgCompanionBond(id = FirstLightChapter.MIRA_ID, name = "Mira", level = 1))
        }
        val factions = withSchema.factions.ifEmpty {
            listOf(RpgFactionStanding(id = FirstLightChapter.WARDENS_ID, name = "Haven Wardens"))
        }
        val crafting = if (withSchema.crafting.recipes.isEmpty()) {
            FirstLightChapter.newCampaign(withSchema.rngSeed).crafting.copy(
                materials = withSchema.crafting.materials,
                preparedItems = withSchema.crafting.preparedItems,
            )
        } else {
            withSchema.crafting
        }
        val party = withSchema.party.ifEmpty {
            FirstLightChapter.newCampaign(withSchema.rngSeed).party
        }
        val scene = if (withSchema.scene.id.isBlank()) FirstLightChapter.onboardingScene() else withSchema.scene
        return withSchema.copy(
            schemaVersion = RPG_SCHEMA_VERSION,
            companions = companions,
            factions = factions,
            crafting = crafting,
            party = party,
            scene = scene,
            progress = withSchema.progress.copy(
                chapterId = withSchema.progress.chapterId.ifBlank { FirstLightChapter.ID },
                chapterTitle = withSchema.progress.chapterTitle.ifBlank { FirstLightChapter.TITLE },
            ),
        )
    }
}
