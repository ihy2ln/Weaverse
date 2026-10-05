package com.ihy2ln.weaverse.feature.games.adventure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.ai.AiGenerationService
import com.ihy2ln.weaverse.feature.games.battle.NativeCardStore
import com.ihy2ln.weaverse.feature.games.battle.engine.BattleState
import com.ihy2ln.weaverse.feature.games.battle.engine.Catalog
import com.ihy2ln.weaverse.feature.games.battle.engine.GameSave
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import kotlin.random.Random

enum class AdvScreen { Loading, Hub, Scene, Shop, End }

data class AdventureUi(
    val screen: AdvScreen = AdvScreen.Loading,
    val tick: Int = 0,
    /** Waiting for the storyteller (AI) to write the next scene. */
    val busy: Boolean = false,
    val message: String = "",
    val showItems: Boolean = false,
    /** Choosing who drinks a Healing Draught. */
    val drinking: Boolean = false,
    /** The kind of the fight being played on the battle screen, or null. */
    val fighting: String? = null,
)

/**
 * Games -> Adventure: a text adventure told by the AI (or the offline storyteller) where choices lead to Card Battles,
 * a barter screen, camps and loot. The engine owns every number; the AI only writes the story.
 */
@HiltViewModel
class AdventureViewModel @Inject constructor(
    private val cards: NativeCardStore,
    private val store: AdventureStore,
    private val ai: AiGenerationService,
) : ViewModel() {
    private val _ui = MutableStateFlow(AdventureUi())
    val ui: StateFlow<AdventureUi> = _ui.asStateFlow()

    var adv: AdvSave = AdvSave()
        private set
    var base: GameSave = GameSave()
        private set
    var hasSave = false
        private set
    val dir: File get() = cards.dir

    init { refresh() }

    private fun bump(block: (AdventureUi) -> AdventureUi = { it }) = _ui.update { block(it).copy(tick = it.tick + 1) }

    private fun persist() { viewModelScope.launch { store.save(adv) } }

    /** Loads the cast, the Card Battle save (party and deck) and any adventure in progress. */
    fun refresh() {
        viewModelScope.launch {
            cards.prepare()
            base = cards.load()
            val saved = store.load()
            if (saved != null) adv = saved
            hasSave = saved != null && (saved.active || saved.over)
            bump { it.copy(screen = if (it.screen == AdvScreen.Loading) AdvScreen.Hub else it.screen) }
        }
    }

    fun partyNames(): List<String> = base.party.mapNotNull { Catalog.get(it)?.name }

    fun resume() {
        if (!hasSave) return
        bump { it.copy(screen = if (adv.over) AdvScreen.End else if (adv.shop != null) AdvScreen.Shop else AdvScreen.Scene, message = "") }
    }

    fun begin(premise: String) {
        adv = AdventureRules.begin(base, premise, Random.nextInt(1, Int.MAX_VALUE))
        hasSave = true
        persist()
        // The opening scene comes from the storyteller when there is a premise; the offline one is already in place.
        bump { it.copy(screen = AdvScreen.Scene, message = "") }
        if (premise.isNotBlank()) tellNext(null)
    }

    fun quit() {
        adv = AdvSave()
        hasSave = false
        viewModelScope.launch { store.clear() }
        bump { it.copy(screen = AdvScreen.Hub, message = "", showItems = false) }
    }

    /** Asks the storyteller for the next scene; falls back to the offline one if there is no AI or it fails. */
    private fun tellNext(action: String?) {
        if (adv.over) { persist(); bump { it.copy(screen = AdvScreen.End, busy = false) }; return }
        bump { it.copy(busy = true, screen = AdvScreen.Scene) }
        viewModelScope.launch {
            var note = ""
            val scene = try {
                val answer = ai.complete(AdventureRules.scenePrompt(adv, partyNames(), action), maxTokens = 900, temperature = .9).text
                AdventureRules.parseScene(answer, adv) ?: run { note = "The storyteller's answer was unreadable; the offline storyteller continues."; null }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (e: Exception) {
                note = "No storyteller available (${e.message?.take(60) ?: "AI error"}); the offline storyteller continues."
                null
            }
            adv.scene = scene ?: OfflineDirector.scene(adv)
            persist()
            bump { it.copy(busy = false, message = note) }
        }
    }

    /** The player picked a choice. [onBattle] gets the fight to run on the battle screen. */
    fun pick(choice: Choice, onBattle: (BattleState, String) -> Unit) {
        if (_ui.value.busy || adv.over) return
        when (val step = AdventureRules.choose(adv, choice, base)) {
            is AdventureRules.Step.Battle -> {
                val state = AdventureRules.startBattle(adv, base, step.kind)
                if (state == null) { bump { it.copy(message = "Could not start the fight.") }; return }
                persist()
                bump { it.copy(fighting = step.kind) }
                onBattle(state, step.kind)
            }
            AdventureRules.Step.Shop -> { persist(); bump { it.copy(screen = AdvScreen.Shop, message = "") } }
            AdventureRules.Step.Narrate -> tellNext(choice.text)
            AdventureRules.Step.Done -> bump { it.copy(screen = AdvScreen.End) }
        }
    }

    /** A free-text action: the story moves on with what the player wrote. */
    fun act(text: String) {
        val t = text.trim()
        if (t.isBlank() || _ui.value.busy) return
        AdventureRules.choose(adv, Choice(t, ChoiceKind.TALK), base)
        tellNext(t)
    }

    /** A fight the adventure started has finished. */
    fun battleDone(state: BattleState, kind: String) {
        val line = AdventureRules.finishBattle(adv, state, kind)
        persist()
        bump { it.copy(message = line, fighting = null) }
        tellNext(null)
    }

    // ---- items ---------------------------------------------------------------------------------------------------------

    fun toggleItems() = bump { it.copy(showItems = !it.showItems, drinking = false) }

    fun useItem(id: String, target: String? = null) {
        if (id == "potion" && target == null) { bump { it.copy(drinking = true) }; return }
        val ok = AdventureRules.use(adv, id, target)
        persist()
        bump { it.copy(drinking = false, message = if (ok) "Used ${AdventureRules.item(id)?.name}." else "That would do nothing right now.") }
    }

    // ---- shop ----------------------------------------------------------------------------------------------------------

    fun haggle(index: Int) { val line = AdventureRules.haggle(adv, index); persist(); bump { it.copy(message = line) } }

    fun buy(index: Int) { AdventureRules.buy(adv, index); persist(); bump { it.copy(message = adv.shop?.mood.orEmpty()) } }

    fun sell(id: String) { AdventureRules.sell(adv, id); persist(); bump { it.copy(message = adv.shop?.mood.orEmpty()) } }

    fun leaveShop() {
        AdventureRules.leaveShop(adv)
        persist()
        tellNext(null)
    }

    fun toHub() = bump { it.copy(screen = AdvScreen.Hub, message = "") }
}
