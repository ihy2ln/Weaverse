package com.ihy2ln.weaverse.feature.roleplay.town

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.data.db.entities.RpItem
import com.ihy2ln.weaverse.data.db.entities.decodeItems
import com.ihy2ln.weaverse.data.db.entities.encodeItems
import com.ihy2ln.weaverse.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class TownUiState(
    /** Where the player stands along the map, 0–100. */
    val playerPercent: Float = 8f,
    val facingRight: Boolean = true,
    /** Absolute path of the town backdrop, or blank to draw the fallback. */
    val backgroundPath: String = "",
    val openLocationId: String? = null,
    val status: String = "",
) {
    val nearby: TownLocation? get() = TownMap.nearest(playerPercent)
    val openLocation: TownLocation?
        get() = TownMap.locations.firstOrNull { it.id == openLocationId }
}

@HiltViewModel
class TownViewModel @Inject constructor(
    private val db: WeaverseDatabase,
    private val mediaRepository: MediaRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TownUiState())
    val uiState: StateFlow<TownUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settings.preferences.collect { prefs ->
                val path = prefs.townBackgroundMediaId
                    .takeIf { it.isNotBlank() }
                    ?.let { id ->
                        mediaRepository.getById(id)?.let { mediaRepository.resolveFile(it).absolutePath }
                    }
                    .orEmpty()
                _uiState.update { it.copy(backgroundPath = path) }
            }
        }
    }

    /** Walk by [delta] percent of the map, clamped to the strip. */
    fun walk(delta: Float) {
        _uiState.update {
            it.copy(
                playerPercent = (it.playerPercent + delta).coerceIn(0f, 100f),
                facingRight = if (delta == 0f) it.facingRight else delta > 0,
            )
        }
    }

    /** Jump straight to a building — the map is long and walking is optional. */
    fun goTo(location: TownLocation) {
        _uiState.update {
            it.copy(
                playerPercent = location.xPercent,
                facingRight = location.xPercent >= it.playerPercent,
            )
        }
    }

    fun enter(location: TownLocation) =
        _uiState.update { it.copy(openLocationId = location.id, status = "") }

    fun leave() = _uiState.update { it.copy(openLocationId = null) }

    fun clearStatus() = _uiState.update { it.copy(status = "") }

    /** Imports the picked image into the media library and makes it the backdrop. */
    fun onBackgroundPicked(uri: android.net.Uri) {
        viewModelScope.launch {
            runCatching { mediaRepository.importFromUris(listOf(uri)) }
                .getOrNull()
                ?.firstOrNull()
                ?.let { settings.setTownBackgroundMediaId(it.id) }
        }
    }

    /**
     * Buying puts the item in the active persona's pack, so town shopping feeds
     * the same inventory the Roster and equipment screens read.
     */
    fun buy(good: ShopGood) {
        viewModelScope.launch {
            val personas = db.roleplayDao().getPersonas()
            val persona = personas.firstOrNull { it.isDefault } ?: personas.firstOrNull()
            if (persona == null) {
                _uiState.update {
                    it.copy(status = "Create a persona first — there is nobody to carry it.")
                }
                return@launch
            }
            val items = decodeItems(persona.inventoryJson)
            val existing = items.firstOrNull { it.name.equals(good.name, ignoreCase = true) }
            val next = if (existing != null) {
                items.map {
                    if (it.id == existing.id) it.copy(quantity = it.quantity + 1) else it
                }
            } else {
                items + RpItem(
                    id = "item-${UUID.randomUUID()}",
                    name = good.name,
                    quantity = 1,
                    notes = good.note,
                )
            }
            db.roleplayDao().upsertPersona(persona.copy(inventoryJson = encodeItems(next)))
            _uiState.update { it.copy(status = "${good.name} added to ${persona.name}'s pack.") }
        }
    }

    /** Free-text action (talk, ask, listen) — recorded as a status line for now. */
    fun act(location: TownLocation, action: String) {
        _uiState.update { it.copy(status = "$action — ${location.name}. ${location.blurb}") }
    }

    suspend fun defaultPersonaName(): String =
        db.roleplayDao().getPersonas().firstOrNull { it.isDefault }?.name.orEmpty()

    suspend fun currentBackgroundMediaId(): String =
        settings.preferences.first().townBackgroundMediaId
}
