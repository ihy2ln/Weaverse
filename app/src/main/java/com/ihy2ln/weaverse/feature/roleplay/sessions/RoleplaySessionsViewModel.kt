package com.ihy2ln.weaverse.feature.roleplay.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.data.db.entity.RpCharacterEntity
import com.ihy2ln.weaverse.data.db.entity.RpChatEntity
import com.ihy2ln.weaverse.data.repo.RoleplayRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Backs the rail's Sessions tab (Revision 02 §1.4): every roleplay chat across every character,
 * newest first, so the rail can jump straight into any of them without leaving whichever
 * destination is currently open. */
@HiltViewModel
class RoleplaySessionsViewModel @Inject constructor(
    roleplayRepository: RoleplayRepository,
) : ViewModel() {
    val chats: StateFlow<List<RpChatEntity>> = roleplayRepository.observeChats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val characters: StateFlow<List<RpCharacterEntity>> = roleplayRepository.observeCharacters()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
