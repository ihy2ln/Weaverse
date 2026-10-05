package com.ihy2ln.weaverse.feature.games.battle

import android.content.Context
import com.ihy2ln.weaverse.feature.games.CardGameCard
import com.ihy2ln.weaverse.feature.games.CardGameCharacter
import com.ihy2ln.weaverse.feature.games.CardGameStore
import com.ihy2ln.weaverse.feature.games.battle.engine.Bestiary
import com.ihy2ln.weaverse.feature.games.battle.engine.BuiltinContent
import com.ihy2ln.weaverse.feature.games.battle.engine.CardDef
import com.ihy2ln.weaverse.feature.games.battle.engine.Catalog
import com.ihy2ln.weaverse.feature.games.battle.engine.CharacterDef
import com.ihy2ln.weaverse.feature.games.battle.engine.GameLogic
import com.ihy2ln.weaverse.feature.games.battle.engine.GameSave
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** A Weaverse card set as the engine's card/character definitions (same fields). */
fun CardGameCard.toDef() = CardDef(
    id = id, name = name, owner = owner, kind = kind, target = target, ep = ep, ap = ap, draw = draw, epGain = epGain,
    apGain = apGain, duration = duration, power = power, heal = heal, magnitude = magnitude, status = status, magic = magic,
    transferEp = transferEp, transferAp = transferAp, retain = retain, exhaust = exhaust, initiation = initiation,
    text = text, art = art,
)

fun CardGameCharacter.toDef() = CharacterDef(
    id = id, name = name, title = title, role = role, element = element, tier = tier, hp = hp, attack = attack,
    magic = magic, defense = defense, resistance = resistance, speed = speed, crit = crit, critDamage = critDamage,
    portrait = portrait, bonds = bonds, cards = cards.map { it.toDef() }.toMutableList(),
    ultimates = ultimates.map { it.toDef() }.toMutableList(), source = "novel", novel = novel, bio = bio,
)

/**
 * The native card battle's storage and content: loads the bundled bestiary, registers the built-in cast plus every novel
 * character switched on in Games (from [CardGameStore]'s `weaverse-cards.json`), and keeps the player's progress in its
 * own `native_save.json`.
 */
@Singleton
class NativeCardStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cardGame: CardGameStore,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Mutex()

    val dir: File get() = cardGame.dir
    private val saveFile: File get() = File(dir, "native_save.json")

    /** Loads the bestiary once and (re-)registers the cast; call before reading [Catalog]. */
    suspend fun prepare() = withContext(Dispatchers.IO) {
        lock.withLock {
            if (!Bestiary.loaded) {
                Bestiary.loadJson(context.assets.open("cardgame/bestiary.json").bufferedReader().use { it.readText() })
            }
            val novel = cardGame.load().characters.filter { it.inGame && it.cards.isNotEmpty() }.map { it.toDef() }
            Catalog.register(BuiltinContent.characters(), novel)
        }
    }

    /** The saved progress with any new characters welcomed into the collection. Call after [prepare]. */
    suspend fun load(): GameSave = withContext(Dispatchers.IO) {
        lock.withLock {
            val save = saveFile.takeIf { it.isFile }
                ?.let { runCatching { json.decodeFromString(GameSave.serializer(), it.readText()) }.getOrNull() }
                ?: GameSave()
            GameLogic.welcome(save)
            save
        }
    }

    /** Encodes on the caller's thread (the save is mutated there), then writes on IO. */
    suspend fun save(save: GameSave) {
        val text = json.encodeToString(GameSave.serializer(), save)
        withContext(Dispatchers.IO) {
            lock.withLock {
                val tmp = File(dir, "native_save.json.tmp")
                tmp.writeText(text)
                if (!tmp.renameTo(saveFile)) { saveFile.delete(); tmp.renameTo(saveFile) }
            }
        }
    }

    suspend fun reset() = withContext(Dispatchers.IO) { lock.withLock { saveFile.delete() } }
}
