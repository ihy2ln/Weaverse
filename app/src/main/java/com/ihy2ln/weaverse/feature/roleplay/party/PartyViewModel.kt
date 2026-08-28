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
                fun portraitPath(mediaId: String?): String = mediaId
                    ?.let(mediaById::get)
                    ?.let { mediaRepository.resolveFile(it).absolutePath }
                    .orEmpty()
                PartyUiState(
                    players = personas.map { persona ->
                        PartyMemberUi(
                            id = persona.id,
                            name = persona.name,
                            avatarColorHex = avatarColorHexFor(persona.name, null),
                            summary = persona.description.lineSequence().firstOrNull()?.trim().orEmpty(),
                            personality = "",
                            isPlayer = true,
                            portraitPath = portraitPath(persona.avatarMediaId),
                            isDefaultPersona = persona.isDefault,
                        )
                    },
                    // Roster is the immediate team only — the wider cast lives in Lore.
                    cast = characters.filter { it.inParty }.map { character ->
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
                    bench = characters.filterNot { it.inParty }.map { character ->
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
