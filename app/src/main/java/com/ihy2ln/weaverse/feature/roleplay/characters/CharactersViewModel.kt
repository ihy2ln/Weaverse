package com.ihy2ln.weaverse.feature.roleplay.characters

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.core.media.MediaImporter
import com.ihy2ln.weaverse.core.media.MediaPaths
import com.ihy2ln.weaverse.data.db.entity.RpCharacterEntity
import com.ihy2ln.weaverse.data.repo.MediaRepository
import com.ihy2ln.weaverse.data.repo.RoleplayRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Backs the Characters screen (spec §9/§11), including PNG character-card
 * import/export (spec §13 acceptance criterion). [importCard] reads the
 * picked URI's raw bytes directly for [CharacterCardCodec.decode] rather
 * than going through [MediaImporter] first — `MediaImporter` re-compresses
 * (and can re-encode to JPEG) any image over `ImageDownscaler.MAX_LONG_EDGE`,
 * which would silently strip the embedded `chara` chunk before it's ever
 * read. The avatar is imported separately, through the normal pipeline,
 * since *that* copy is fine to downscale.
 */
@HiltViewModel
class CharactersViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val roleplayRepository: RoleplayRepository,
    val mediaRepository: MediaRepository,
    private val mediaImporter: MediaImporter,
) : ViewModel() {
    val characters: StateFlow<List<RpCharacterEntity>> = roleplayRepository.observeCharacters()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createCharacter(name: String) {
        viewModelScope.launch { roleplayRepository.upsertCharacter(RpCharacterEntity(name = name)) }
    }

    fun update(character: RpCharacterEntity) {
        viewModelScope.launch { roleplayRepository.upsertCharacter(character) }
    }

    fun delete(character: RpCharacterEntity) {
        viewModelScope.launch { roleplayRepository.deleteCharacter(character) }
    }

    suspend fun importAvatar(uri: Uri) = mediaImporter.importFromUri(uri)

    fun setAvatar(character: RpCharacterEntity, mediaId: String) {
        viewModelScope.launch { roleplayRepository.upsertCharacter(character.copy(avatarMediaId = mediaId)) }
    }

    /** Reads [uri] as a character card PNG; returns null (and imports nothing) if it has no `chara` chunk. */
    suspend fun importCard(uri: Uri): RpCharacterEntity? = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
        val decoded = CharacterCardCodec.decode(bytes) ?: return@withContext null
        val avatarMediaId = runCatching { mediaImporter.importFromUri(uri) }.getOrNull()?.id
        val character = decoded.copy(avatarMediaId = avatarMediaId)
        roleplayRepository.upsertCharacter(character)
        character
    }

    /** Builds the exportable card PNG bytes for [character] — caller writes them wherever the user picked. */
    suspend fun exportCard(character: RpCharacterEntity): ByteArray = withContext(Dispatchers.IO) {
        val basePng = buildBasePng(character)
        CharacterCardCodec.encode(character, basePng)
    }

    private suspend fun buildBasePng(character: RpCharacterEntity): ByteArray {
        val avatar = character.avatarMediaId?.let { mediaRepository.getById(it) }
        val bitmap = avatar?.let { BitmapFactory.decodeFile(MediaPaths.resolve(context, it.relativePath).path) }
            ?: Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.DKGRAY) }
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        bitmap.recycle()
        return output.toByteArray()
    }
}
