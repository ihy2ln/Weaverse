package com.ihy2ln.weaverse.feature.roleplay.town

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.ai.AiGenerationService
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
    /** Location id to imported user image path. */
    val locationImagePaths: Map<String, String> = emptyMap(),
    val openLocationId: String? = null,
    val status: String = "",
    val suggestedBuyAmounts: Map<String, Int> = emptyMap(),
    val suggestingBuyAmountKeys: Set<String> = emptySet(),
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
    private val aiGeneration: AiGenerationService,
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
        TownMap.locations.forEach { location ->
            viewModelScope.launch {
                settings.townLocationMediaId(location.id).collect { mediaId ->
                    val path = mediaId.takeIf { it.isNotBlank() }
                        ?.let { mediaRepository.getById(it) }
                        ?.let { mediaRepository.resolveFile(it).absolutePath }
                        .orEmpty()
                    _uiState.update { state ->
                        state.copy(locationImagePaths = state.locationImagePaths + (location.id to path))
                    }
                }
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

    /** Imports art for a single location card; picking again replaces that slot. */
    fun onLocationImagePicked(locationId: String, uri: android.net.Uri) {
        viewModelScope.launch {
            runCatching { mediaRepository.importFromUri(uri) }
                .onSuccess { media -> settings.setTownLocationMediaId(locationId, media.id) }
                .onFailure { error ->
                    _uiState.update { it.copy(status = "Could not add location picture: ${error.message}") }
                }
        }
    }

    /**
     * Buying puts the item in the active persona's pack, so town shopping feeds
     * the same inventory the Roster and equipment screens read.
     */
    fun buy(good: ShopGood, quantity: Int = 1) {
        viewModelScope.launch {
            val amount = quantity.coerceIn(1, 999)
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
                    if (it.id == existing.id) it.copy(quantity = it.quantity + amount) else it
                }
            } else {
                items + RpItem(
                    id = "item-${UUID.randomUUID()}",
                    name = good.name,
                    quantity = amount,
                    notes = good.note,
                )
            }
            db.roleplayDao().upsertPersona(persona.copy(inventoryJson = encodeItems(next)))
            _uiState.update {
                it.copy(
                    status = "$amount × ${good.name} added to ${persona.name}'s pack " +
                        "for ${good.priceGold * amount} gp.",
                )
            }
        }
    }

    /** Lets the configured model fill the shop quantity box with a practical amount. */
    fun suggestBuyQuantity(location: TownLocation, good: ShopGood) {
        val key = shopGoodKey(location.id, good.name)
        if (key in _uiState.value.suggestingBuyAmountKeys) return
        viewModelScope.launch {
            _uiState.update { it.copy(suggestingBuyAmountKeys = it.suggestingBuyAmountKeys + key) }
            val currentAmount = db.roleplayDao().getPersonas()
                .firstOrNull { it.isDefault }
                ?.let { persona -> decodeItems(persona.inventoryJson) }
                ?.firstOrNull { it.name.equals(good.name, ignoreCase = true) }
                ?.quantity
                ?: 0
            val fallback = suggestedShopQuantity(good.name, currentAmount)
            val suggestion = if (aiGeneration.hasApiKey()) {
                runCatching {
                    aiGeneration.complete(
                        userMessage = "You are a tabletop RPG shopping assistant. The player is at " +
                            "${location.name}. For ${good.name} (${good.note}), costing ${good.priceGold} gp " +
                            "each, they already carry $currentAmount. Return only one sensible purchase " +
                            "quantity from 1 to 99, with no words.",
                        maxTokens = 8,
                        temperature = 0.2,
                    ).text.findFirstPositiveInt()?.coerceIn(1, 99)
                }.getOrNull() ?: fallback
            } else {
                fallback
            }
            _uiState.update {
                it.copy(
                    suggestedBuyAmounts = it.suggestedBuyAmounts + (key to suggestion),
                    suggestingBuyAmountKeys = it.suggestingBuyAmountKeys - key,
                    status = if (aiGeneration.hasApiKey()) {
                        "AI suggested $suggestion × ${good.name}."
                    } else {
                        "Auto-filled $suggestion × ${good.name}; add an AI key for model suggestions."
                    },
                )
            }
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

fun shopGoodKey(locationId: String, goodName: String): String = "$locationId:${goodName.lowercase()}"

fun suggestedShopQuantity(goodName: String, currentAmount: Int): Int = when {
    goodName.contains("ration", ignoreCase = true) -> (7 - currentAmount).coerceIn(1, 7)
    goodName.contains("bandage", ignoreCase = true) -> (5 - currentAmount).coerceIn(1, 5)
    goodName.contains("feed", ignoreCase = true) -> 2
    else -> 1
}

private fun String.findFirstPositiveInt(): Int? = Regex("\\d+").find(this)?.value?.toIntOrNull()
