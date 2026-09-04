package com.ihy2ln.weaverse.feature.roleplay.rpg

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.sync.adams.AdamsHavenRpgCatalog
import com.ihy2ln.weaverse.sync.adams.RpgCard
import com.ihy2ln.weaverse.sync.adams.RpgScene
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class RpgScenesUiState(
    val scenes: List<RpgScene> = AdamsHavenRpgCatalog.scenes.sortedBy { it.sortOrder },
    val cards: List<RpgCard> = AdamsHavenRpgCatalog.cards,
    val characterIds: Map<String, String> = emptyMap(),
)

@HiltViewModel
class RpgScenesViewModel @Inject constructor(
    db: WeaverseDatabase,
) : ViewModel() {
    val uiState: StateFlow<RpgScenesUiState> = db.roleplayDao().observeCharacters()
        .map { characters ->
            val byName = characters.associate { it.name.lowercase() to it.id }
            val resolved = AdamsHavenRpgCatalog.cards.associate { card ->
                card.id to (characters.firstOrNull { it.id == card.id }?.id
                    ?: byName[card.name.lowercase()]
                    ?: card.id)
            }
            RpgScenesUiState(characterIds = resolved)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RpgScenesUiState())
}
