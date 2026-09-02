package com.ihy2ln.weaverse.feature.roleplay.party

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.core.roleplay.avatarColorHexFor
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.feature.roleplay.characters.decodeRpgSheet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Someone in the party — either the player's persona or a character. */
data class PartyMemberUi(
    val id: String,
    val name: String,
    val avatarColorHex: String,
    val summary: String,
    val personality: String,
    val isPlayer: Boolean,
    val portraitPath: String = "",
    val sheetLabel: String = "",
    val hpLabel: String = "",
    val armorClassLabel: String = "",
    /** Player personas open the persona editor; everyone else the character editor. */
    val isDefaultPersona: Boolean = false,
    /** Stable blank/full sheet created for this player when an adventure begins. */
    val sheetCharacterId: String? = null,
)

data class PartyUiState(
    val players: List<PartyMemberUi> = emptyList(),
    val cast: List<PartyMemberUi> = emptyList(),
    /** Everyone not currently in the team, offered when recruiting. */
    val bench: List<PartyMemberUi> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class PartyViewModel @Inject constructor(
    private val db: WeaverseDatabase,
    private val mediaRepository: MediaRepository,
) : ViewModel() {
    /** Adds or removes someone from the immediate team. */
    fun setInParty(characterId: String, inParty: Boolean) {
        viewModelScope.launch {
            val character = db.roleplayDao().getCharacter(characterId) ?: return@launch
            db.roleplayDao().upsertCharacter(character.copy(inParty = inParty))
        }
    }

    private val _uiState = MutableStateFlow(PartyUiState())
    val uiState: StateFlow<PartyUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                db.roleplayDao().observePersonas(),
                db.roleplayDao().observeCharacters(),
                mediaRepository.observeAll(),
            ) { personas, characters, media ->
                val mediaById = media.associateBy { it.id }
                val playerSheets = characters
                    .filter { it.defaultCodexId?.startsWith("persona:") == true }
                    .associateBy { it.defaultCodexId!!.substringAfter(':') }
                val legacyPlayerNames = personas
                    .filter { playerSheets.containsKey(it.id) }
                    .map { it.name.trim().lowercase() }
                    .toSet()
                val nonPlayerCharacters = characters.filterNot { character ->
                    character.defaultCodexId?.startsWith("persona:") == true ||
                        character.name.trim().lowercase() in legacyPlayerNames
                }
                fun portraitPath(mediaId: String?): String = mediaId
                    ?.let(mediaById::get)
                    ?.let { mediaRepository.resolveFile(it).absolutePath }
                    .orEmpty()
                PartyUiState(
                    players = personas.map { persona ->
                        val character = playerSheets[persona.id]
                        val sheet = character?.let { decodeRpgSheet(it.extensionsJson) }
                        PartyMemberUi(
                            id = persona.id,
                            name = persona.name,
                            avatarColorHex = avatarColorHexFor(persona.name, character?.colorHex),
                            summary = character?.description
                                ?.lineSequence()?.firstOrNull()?.trim()
                                .orEmpty()
                                .ifBlank { persona.description.lineSequence().firstOrNull()?.trim().orEmpty() },
                            personality = character?.personality.orEmpty(),
                            isPlayer = true,
                            portraitPath = portraitPath(character?.avatarMediaId ?: persona.avatarMediaId),
                            sheetLabel = sheet?.let { "${it.characterClass} ${it.level}" }.orEmpty(),
                            hpLabel = sheet?.let { "${it.currentHp}/${it.maxHp}" }.orEmpty(),
                            armorClassLabel = sheet?.armorClass?.toString().orEmpty(),
                            isDefaultPersona = persona.isDefault,
                            sheetCharacterId = character?.id,
                        )
                    },
                    // Roster is the immediate team only — the wider cast lives in Lore.
                    cast = nonPlayerCharacters.filter { it.inParty }.map { character ->
                        val sheet = decodeRpgSheet(character.extensionsJson)
                        PartyMemberUi(
                            id = character.id,
                            name = character.name,
                            avatarColorHex = avatarColorHexFor(character.name, character.colorHex),
                            summary = character.description.lineSequence().firstOrNull()?.trim().orEmpty(),
                            personality = character.personality.lineSequence().firstOrNull()?.trim().orEmpty(),
                            isPlayer = false,
                            portraitPath = portraitPath(character.avatarMediaId),
                            sheetLabel = "${sheet.characterClass} ${sheet.level}",
                            hpLabel = "${sheet.currentHp}/${sheet.maxHp}",
                            armorClassLabel = sheet.armorClass.toString(),
                        )
                    },
                    bench = nonPlayerCharacters.filterNot { it.inParty }.map { character ->
                        val sheet = decodeRpgSheet(character.extensionsJson)
                        PartyMemberUi(
                            id = character.id,
                            name = character.name,
                            avatarColorHex = avatarColorHexFor(character.name, character.colorHex),
                            summary = character.description.lineSequence().firstOrNull()?.trim().orEmpty(),
                            personality = "",
                            isPlayer = false,
                            portraitPath = portraitPath(character.avatarMediaId),
                            sheetLabel = "${sheet.characterClass} ${sheet.level}",
                            hpLabel = "${sheet.currentHp}/${sheet.maxHp}",
                            armorClassLabel = sheet.armorClass.toString(),
                        )
                    },
                    loading = false,
                )
            }.collect { _uiState.value = it }
        }
    }
}
