package com.ihy2ln.weaverse.feature.roleplay.campaign

import com.ihy2ln.weaverse.data.db.dao.RoleplayDao
import com.ihy2ln.weaverse.data.db.entities.RpgCampaignSaveEntity
import com.ihy2ln.weaverse.feature.roleplay.combat.RpgCombatRuleset
import kotlinx.serialization.json.Json

class RpgCampaignRepository(private val roleplayDao: RoleplayDao) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun saveRpgCampaign(state: RpgCampaignState) {
        roleplayDao.upsertRpgCampaignSave(
            RpgCampaignSaveEntity(state.campaignId, state.schemaVersion, json.encodeToString(RpgCampaignState.serializer(), state), System.currentTimeMillis()),
        )
    }

    suspend fun restoreRpgCampaign(campaignId: String, legacyModeId: String? = null, legacyRuleSystemId: String? = null): RpgCampaignState {
        val saved = roleplayDao.getRpgCampaignSave(campaignId)
        if (saved != null) return runCatching {
            json.decodeFromString(RpgCampaignState.serializer(), saved.stateJson).copy(schemaVersion = CURRENT_RPG_SCHEMA)
        }.getOrElse { createRpgCampaign(campaignId, legacyModeId ?: RpgCombatRuleset.DndD20.id, legacyRuleSystemId ?: "dnd-5e") }
        return createRpgCampaign(campaignId, legacyModeId ?: RpgCombatRuleset.DndD20.id, legacyRuleSystemId ?: "dnd-5e")
    }
}
