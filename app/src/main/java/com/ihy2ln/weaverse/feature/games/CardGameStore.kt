package com.ihy2ln.weaverse.feature.games

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.ihy2ln.weaverse.core.media.MediaRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The folder Weaverse and the Adams Haven Card Game share: `weaverse-cards.json` (every novel character's card set,
 * Weaverse's working copy), `novel.json` (the ones switched on for the game, written before each launch and read by
 * the game, see [CardGamePack]), `art/` (their Codex pictures at game size) and the game's own `save.json`.
 */
@Singleton
class CardGameStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val media: MediaRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val lock = Mutex()

    val dir: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "cardgame").apply { mkdirs() }

    private val file: File get() = File(dir, "weaverse-cards.json")

    suspend fun load(): CardGamePack = withContext(Dispatchers.IO) {
        lock.withLock { read() }
    }

    private fun read(): CardGamePack =
        file.takeIf { it.isFile }?.let { runCatching { json.decodeFromString(CardGamePack.serializer(), it.readText()) }.getOrNull() }
            ?: CardGamePack()

    suspend fun upsert(character: CardGameCharacter) = withContext(Dispatchers.IO) {
        lock.withLock {
            val pack = read()
            val list = pack.characters.filterNot { it.id == character.id || (character.codexId.isNotBlank() && it.codexId == character.codexId) }
            write(pack.copy(characters = list + character))
        }
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        lock.withLock {
            val pack = read()
            pack.characters.firstOrNull { it.id == id }?.portrait?.takeIf { it.startsWith("art/") }?.let { File(dir, it).delete() }
            write(pack.copy(characters = pack.characters.filterNot { it.id == id }))
        }
    }

    /** What the game reads: only characters switched on for the game. Written right before launching it. */
    suspend fun exportForGame(): Int = withContext(Dispatchers.IO) {
        lock.withLock {
            val pack = read()
            val live = pack.characters.filter { it.inGame && it.cards.isNotEmpty() }
            File(dir, "novel.json").writeText(json.encodeToString(CardGamePack.serializer(), pack.copy(characters = live)))
            live.size
        }
    }

    private fun write(pack: CardGamePack) {
        val tmp = File(dir, "weaverse-cards.json.tmp")
        tmp.writeText(json.encodeToString(CardGamePack.serializer(), pack))
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    /** The Codex picture's file on disk (for showing it), or null. */
    suspend fun picturePath(mediaId: String?): String? = withContext(Dispatchers.IO) {
        if (mediaId.isNullOrBlank()) return@withContext null
        media.getById(mediaId)?.let { media.resolveFile(it) }?.takeIf { it.isFile }?.absolutePath
    }

    /**
     * Copies a Codex picture into the shared folder at game size (at most 768 px tall) and returns its path relative
     * to the folder ("art/<id>.jpg"), or "" when the picture cannot be read.
     */
    suspend fun copyPortrait(characterId: String, mediaId: String?): String = withContext(Dispatchers.IO) {
        if (mediaId.isNullOrBlank()) return@withContext ""
        val entity = media.getById(mediaId) ?: return@withContext ""
        val source = media.resolveFile(entity)
        if (!source.isFile) return@withContext ""
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outHeight <= 0) return@withContext ""
        var sample = 1
        while (bounds.outHeight / (sample * 2) >= 768) sample *= 2
        val bitmap = BitmapFactory.decodeFile(source.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return@withContext ""
        val scaled = if (bitmap.height > 768) Bitmap.createScaledBitmap(bitmap, bitmap.width * 768 / bitmap.height, 768, true) else bitmap
        val out = File(dir, "art/$characterId.jpg").apply { parentFile?.mkdirs() }
        out.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        "art/$characterId.jpg"
    }
}
