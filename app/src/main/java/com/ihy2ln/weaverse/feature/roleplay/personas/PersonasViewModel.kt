package com.ihy2ln.weaverse.feature.roleplay.personas

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.core.media.MediaImporter
import com.ihy2ln.weaverse.data.db.entity.MediaEntity
import com.ihy2ln.weaverse.data.db.entity.RpPersonaEntity
import com.ihy2ln.weaverse.data.repo.MediaRepository
import com.ihy2ln.weaverse.data.repo.RoleplayRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the Personas screen (spec §9/§11: reusable user personas for roleplay chats). */
@HiltViewModel
class PersonasViewModel @Inject constructor(
    private val roleplayRepository: RoleplayRepository,
    val mediaRepository: MediaRepository,
    private val mediaImporter: MediaImporter,
) : ViewModel() {
    val personas: StateFlow<List<RpPersonaEntity>> = roleplayRepository.observePersonas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createPersona(name: String) {
        viewModelScope.launch {
            roleplayRepository.upsertPersona(RpPersonaEntity(name = name, isDefault = personas.value.isEmpty()))
        }
    }

    fun updateDescription(persona: RpPersonaEntity, description: String) {
        viewModelScope.launch { roleplayRepository.upsertPersona(persona.copy(description = description)) }
    }

    fun setDefault(persona: RpPersonaEntity) {
        viewModelScope.launch {
            personas.value.filter { it.isDefault && it.id != persona.id }
                .forEach { roleplayRepository.upsertPersona(it.copy(isDefault = false)) }
            roleplayRepository.upsertPersona(persona.copy(isDefault = true))
        }
    }

    fun delete(persona: RpPersonaEntity) {
        viewModelScope.launch { roleplayRepository.deletePersona(persona) }
    }

    suspend fun importAvatar(uri: Uri): MediaEntity = mediaImporter.importFromUri(uri)

    fun setAvatar(persona: RpPersonaEntity, mediaId: String) {
        viewModelScope.launch { roleplayRepository.upsertPersona(persona.copy(avatarMediaId = mediaId)) }
    }
}
