package com.ihy2ln.weaverse.feature.chatting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.RpCharacterEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Backs the "+ Characters" room picker in the Discord channel sidebar. */
@HiltViewModel
class CharacterPickerViewModel @Inject constructor(
    db: WeaverseDatabase,
) : ViewModel() {

    val characters: StateFlow<List<RpCharacterEntity>> = db.roleplayDao()
        .observeCharacters()
        .map { list -> list.sortedBy { it.name.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
