package com.ihy2ln.weaverse.feature.roleplay.presets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.ai.AIRequestParams
import com.ihy2ln.weaverse.core.text.DocumentJson
import com.ihy2ln.weaverse.data.db.entity.PresetEntity
import com.ihy2ln.weaverse.data.repo.PromptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import javax.inject.Inject

/** Backs the Presets screen (spec §9/§11: sampler + instruct-template presets, shared across modes). */
@HiltViewModel
class PresetsViewModel @Inject constructor(
    private val promptRepository: PromptRepository,
) : ViewModel() {
    val presets: StateFlow<List<PresetEntity>> = promptRepository.observePresets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun paramsFor(preset: PresetEntity): AIRequestParams =
        runCatching { DocumentJson.decodeFromString<AIRequestParams>(preset.paramsJson) }.getOrDefault(AIRequestParams())

    fun createPreset(name: String) {
        viewModelScope.launch {
            promptRepository.upsertPreset(PresetEntity(name = name, paramsJson = DocumentJson.encodeToString(AIRequestParams())))
        }
    }

    fun updateParams(preset: PresetEntity, params: AIRequestParams) {
        viewModelScope.launch {
            promptRepository.upsertPreset(preset.copy(paramsJson = DocumentJson.encodeToString(params)))
        }
    }

    fun rename(preset: PresetEntity, name: String) {
        viewModelScope.launch { promptRepository.upsertPreset(preset.copy(name = name)) }
    }

    fun delete(preset: PresetEntity) {
        viewModelScope.launch { promptRepository.deletePreset(preset) }
    }
}
