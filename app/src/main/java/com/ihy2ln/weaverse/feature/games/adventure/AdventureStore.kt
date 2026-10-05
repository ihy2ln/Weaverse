package com.ihy2ln.weaverse.feature.games.adventure

import com.ihy2ln.weaverse.feature.games.battle.NativeCardStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** The adventure's save, `cardgame/adventure_save.json`, beside the Card Battle save. */
@Singleton
class AdventureStore @Inject constructor(private val cards: NativeCardStore) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Mutex()
    private val file: File get() = File(cards.dir, "adventure_save.json")

    suspend fun load(): AdvSave? = withContext(Dispatchers.IO) {
        lock.withLock { file.takeIf { it.isFile }?.let { runCatching { json.decodeFromString(AdvSave.serializer(), it.readText()) }.getOrNull() } }
    }

    /** Encodes on the caller's thread (the save is mutated there), then writes on IO. */
    suspend fun save(adv: AdvSave) {
        val text = json.encodeToString(AdvSave.serializer(), adv)
        withContext(Dispatchers.IO) {
            lock.withLock {
                val tmp = File(cards.dir, "adventure_save.json.tmp")
                tmp.writeText(text)
                if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
            }
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) { lock.withLock { file.delete() } }
}
