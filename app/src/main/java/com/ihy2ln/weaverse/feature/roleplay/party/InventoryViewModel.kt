package com.ihy2ln.weaverse.feature.roleplay.party

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.RpItem
import com.ihy2ln.weaverse.data.db.entities.decodeItems
import com.ihy2ln.weaverse.data.db.entities.encodeItems
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class CarrierUi(
    val characterId: String,
    val name: String,
    val items: List<RpItem>,
)

data class InventoryUiState(
    val carriers: List<CarrierUi> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val db: WeaverseDatabase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            db.roleplayDao().observeCharacters().collect { characters ->
                _uiState.value = InventoryUiState(
                    carriers = characters.map {
                        CarrierUi(
                            characterId = it.id,
                            name = it.name,
                            items = decodeItems(it.inventoryJson),
                        )
                    },
                    loading = false,
                )
            }
        }
    }

    fun addItem(characterId: String, name: String, quantity: Int, notes: String = "") {
        if (name.isBlank()) return
        editItems(characterId) { items ->
            items + RpItem(
                id = "item-${UUID.randomUUID()}",
                name = name.trim(),
                quantity = quantity.coerceAtLeast(1),
                notes = notes.trim(),
            )
        }
    }

    fun removeItem(characterId: String, itemId: String) {
        editItems(characterId) { items -> items.filterNot { it.id == itemId } }
    }

    private fun editItems(characterId: String, transform: (List<RpItem>) -> List<RpItem>) {
        viewModelScope.launch {
            val character = db.roleplayDao().getCharacter(characterId) ?: return@launch
            val next = transform(decodeItems(character.inventoryJson))
            db.roleplayDao().upsertCharacter(character.copy(inventoryJson = encodeItems(next)))
        }
    }
}
