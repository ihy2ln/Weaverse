package com.ihy2ln.weaverse.feature.roleplay.party

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.RpEquipSlot
import com.ihy2ln.weaverse.data.db.entities.RpItem
import com.ihy2ln.weaverse.data.db.entities.decodeEquipment
import com.ihy2ln.weaverse.data.db.entities.encodeEquipment
import com.ihy2ln.weaverse.data.db.entities.decodeItems
import com.ihy2ln.weaverse.data.db.entities.encodeItems
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** Who a carrier is, which decides where they sort in the list. */
enum class CarrierKind(val label: String) {
    You("You"),
    Team("Team"),
    Roster("Roster"),
}

data class CarrierUi(
    val characterId: String,
    val name: String,
    val items: List<RpItem>,
    /** RpEquipSlot.name -> item name. */
    val equipment: Map<String, String> = emptyMap(),
    val itemImagePaths: Map<String, String> = emptyMap(),
    val kind: CarrierKind = CarrierKind.Roster,
)

data class InventoryUiState(
    val carriers: List<CarrierUi> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val db: WeaverseDatabase,
    private val mediaRepository: MediaRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                db.roleplayDao().observePersonas(),
                db.roleplayDao().observeCharacters(),
                mediaRepository.observeAll(),
            ) { personas, characters, media ->
                val mediaById = media.associateBy { it.id }
                fun imagePaths(items: List<RpItem>): Map<String, String> = items.mapNotNull { item ->
                    item.imageMediaId
                        ?.let(mediaById::get)
                        ?.let { item.id to mediaRepository.resolveFile(it).absolutePath }
                }.toMap()
                // You first, then the team you are travelling with, then everyone else.
                val you = personas.map {
                    val items = decodeItems(it.inventoryJson)
                    CarrierUi(
                        characterId = it.id,
                        name = it.name,
                        items = items,
                        equipment = decodeEquipment(it.equipmentJson),
                        itemImagePaths = imagePaths(items),
                        kind = CarrierKind.You,
                    )
                }
                val rest = characters.map {
                    val items = decodeItems(it.inventoryJson)
                    CarrierUi(
                        characterId = it.id,
                        name = it.name,
                        items = items,
                        equipment = decodeEquipment(it.equipmentJson),
                        itemImagePaths = imagePaths(items),
                        kind = if (it.inParty) CarrierKind.Team else CarrierKind.Roster,
                    )
                }
                InventoryUiState(
                    carriers = (you + rest).sortedWith(
                        compareBy({ it.kind.ordinal }, { it.name.lowercase() }),
                    ),
                    loading = false,
                )
            }.collect { _uiState.value = it }
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

    fun setItemImage(characterId: String, itemId: String, uri: Uri) {
        viewModelScope.launch {
            val media = runCatching { mediaRepository.importFromUri(uri) }.getOrNull() ?: return@launch
            updateItems(characterId) { items ->
                items.map { item -> if (item.id == itemId) item.copy(imageMediaId = media.id) else item }
            }
        }
    }

    /** Equips [itemName] in [slot]; a blank name clears the slot. */
    fun setEquipment(carrierId: String, slot: RpEquipSlot, itemName: String) {
        editEquipment(carrierId) { current ->
            val next = current.toMutableMap()
            if (itemName.isBlank()) next.remove(slot.name) else next[slot.name] = itemName.trim()
            next
        }
    }

    // A carrier is either a persona (You) or a character, so every edit tries both.
    private fun editItems(carrierId: String, transform: (List<RpItem>) -> List<RpItem>) {
        viewModelScope.launch { updateItems(carrierId, transform) }
    }

    private suspend fun updateItems(carrierId: String, transform: (List<RpItem>) -> List<RpItem>) {
        db.roleplayDao().getPersona(carrierId)?.let { persona ->
            val next = transform(decodeItems(persona.inventoryJson))
            db.roleplayDao().upsertPersona(persona.copy(inventoryJson = encodeItems(next)))
            return
        }
        val character = db.roleplayDao().getCharacter(carrierId) ?: return
        val next = transform(decodeItems(character.inventoryJson))
        db.roleplayDao().upsertCharacter(character.copy(inventoryJson = encodeItems(next)))
    }

    private fun editEquipment(
        carrierId: String,
        transform: (Map<String, String>) -> Map<String, String>,
    ) {
        viewModelScope.launch {
            db.roleplayDao().getPersona(carrierId)?.let { persona ->
                val next = transform(decodeEquipment(persona.equipmentJson))
                db.roleplayDao().upsertPersona(persona.copy(equipmentJson = encodeEquipment(next)))
                return@launch
            }
            val character = db.roleplayDao().getCharacter(carrierId) ?: return@launch
            val next = transform(decodeEquipment(character.equipmentJson))
            db.roleplayDao().upsertCharacter(character.copy(equipmentJson = encodeEquipment(next)))
        }
    }
}
