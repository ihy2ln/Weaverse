package com.ihy2ln.weaverse.feature.roleplay.rpg

object RpgUiLabels {
    const val Objective = "Current objective"
    const val PartyReady = "Party readiness"
    const val Conditions = "Active conditions and resources"
    const val Consequences = "Recent consequences"
    const val Relationships = "Companion relationship changes"
    const val Crafting = "Crafting summary"
    const val Factions = "Faction summary"
    const val NextGuidance = "What can I do next?"
    const val WriteOwn = "Write your own action"
    const val Confirm = "Confirm action"
    const val Resolve = "Resolve action"
    const val Preview = "Action preview"
    const val Retry = "Retry co-narrator"
    const val Offline = "Offline. Using local chapter choices."
    const val Loading = "Preparing RPG adventure"
    const val Begin = "Begin First Light"
    const val SummonerName = "Summoner name"
    const val CombatHand = "Shared party hand"
    const val EnemyIntent = "Enemy intent"
    const val PlayCard = "Play combat card"
    const val EndRound = "End combat round"
    const val Recap = "Chapter recap"
    const val ContinueRecap = "Continue to recap"
    const val CollectSpoils = "Collect spoils"
    const val CraftSalve = "Craft Lantern Salve"
    const val CustomComposer = "Custom action composer"
}

data class RpgAdventureUiState(
    val campaignId: String = "",
    val campaignTitle: String = "",
    val game: RpgCampaignState = FirstLightChapter.newCampaign(1L),
    val loading: Boolean = true,
    val proposing: Boolean = false,
    val offline: Boolean = false,
    val saveError: String? = null,
    val customDraft: String = "",
    val previewOpen: Boolean = false,
    val detailsExpanded: Boolean = false,
)
