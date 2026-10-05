package com.ihy2ln.weaverse.feature.games.battle.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ihy2ln.weaverse.feature.games.battle.NativeCardStore
import com.ihy2ln.weaverse.feature.games.battle.engine.BattleCard
import com.ihy2ln.weaverse.feature.games.battle.engine.BattleState
import com.ihy2ln.weaverse.feature.games.battle.engine.BattleTarget
import com.ihy2ln.weaverse.feature.games.battle.engine.BattleUnit
import com.ihy2ln.weaverse.feature.games.battle.engine.Catalog
import com.ihy2ln.weaverse.feature.games.battle.engine.GameLogic
import com.ihy2ln.weaverse.feature.games.battle.engine.GameSave
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import kotlin.random.Random

enum class CardScreen { Loading, Hub, Party, Run, Battle, Draft }

/** A number or word that floats up from a unit after something lands on it. */
class Popup(val id: Int, val unitId: String, val text: String, val kind: String)

/**
 * What the screen shows. The engine's own objects (the save, the battle) are mutable and live in the view model; [tick]
 * changes after every action so Compose reads them again.
 */
data class CardBattleUi(
    val screen: CardScreen = CardScreen.Loading,
    val tick: Int = 0,
    val message: String = "",
    /** The card waiting for a target (or a confirm), and the fighter whose actions are open. */
    val pending: BattleCard? = null,
    val focus: BattleUnit? = null,
    val popups: List<Popup> = emptyList(),
    val showLog: Boolean = false,
    val confirmLeave: Boolean = false,
)

@HiltViewModel
class CardBattleViewModel @Inject constructor(
    private val store: NativeCardStore,
) : ViewModel() {
    private val _ui = MutableStateFlow(CardBattleUi())
    val ui: StateFlow<CardBattleUi> = _ui.asStateFlow()

    var save: GameSave = GameSave()
        private set
    var battle: BattleState? = null
        private set
    val dir: File get() = store.dir
    private var popupId = 0

    /** Set while the Adventure runs a fight through this screen: called with the finished battle. */
    private var external: ((BattleState) -> Unit)? = null

    /** The reserve whose partner assist is waiting for a target ([assist] sets pending to its move). */
    private var assistFor: BattleUnit? = null

    init { refresh() }

    private fun bump(block: (CardBattleUi) -> CardBattleUi = { it }) = _ui.update { block(it).copy(tick = it.tick + 1) }

    private fun persist() { viewModelScope.launch { store.save(save) } }

    private fun seed(): Int = Random.nextInt(1, Int.MAX_VALUE)

    /** (Re)loads the cast and the save. Never while a fight is on: the cast is shared by the running battle. */
    fun refresh() {
        if (battle != null) return
        viewModelScope.launch {
            store.prepare()
            save = store.load()
            val screen = if (save.draft.isNotEmpty() && !save.run.active) CardScreen.Draft else CardScreen.Hub
            bump { it.copy(screen = if (it.screen == CardScreen.Loading || it.screen == CardScreen.Hub) screen else it.screen) }
        }
    }

    fun show(screen: CardScreen) = bump { it.copy(screen = screen, pending = null, focus = null, message = "") }

    // ---- collection ---------------------------------------------------------------------------------------------------

    fun toggleParty(id: String) { GameLogic.toggleParty(save, id); persist(); bump() }

    fun moveParty(id: String, delta: Int) {
        val i = save.party.indexOf(id)
        val j = i + delta
        if (save.run.active || i < 0 || j < 0 || j >= save.party.size) return
        save.party[i] = save.party[j].also { save.party[j] = save.party[i] }
        persist(); bump()
    }

    fun toggleDeck(id: String) { GameLogic.toggleDeck(save, id); persist(); bump() }

    // ---- run ------------------------------------------------------------------------------------------------------------

    fun startRun() {
        GameLogic.startRun(save, seed())
        persist()
        show(CardScreen.Run)
    }

    fun abandonRun() { GameLogic.abandon(save); persist(); show(if (save.draft.isNotEmpty()) CardScreen.Draft else CardScreen.Hub) }

    fun rest() { GameLogic.rest(save); persist(); bump() }

    fun takeReward(id: String) { GameLogic.takeReward(save, id); persist(); bump() }

    fun skipReward() { GameLogic.skipReward(save); persist(); bump() }

    fun fight() {
        val b = GameLogic.startBattle(save) ?: return
        battle = b
        _ui.update { CardBattleUi(screen = CardScreen.Battle, tick = it.tick + 1, message = b.lastMessage) }
    }

    /** Runs a fight the Adventure built, on the same battle screen; [onDone] gets the finished battle (win, loss or withdrawn). */
    fun startExternal(state: BattleState, onDone: (BattleState) -> Unit) {
        external = onDone
        battle = state
        _ui.update { CardBattleUi(screen = CardScreen.Battle, tick = it.tick + 1, message = state.lastMessage) }
    }

    /** After the result banner: carry health and epiphanies over, then on to the next node or the draft. */
    fun finishFight() {
        val b = battle ?: return
        external?.let { callback ->
            external = null
            battle = null
            show(CardScreen.Hub)
            callback(b)
            return
        }
        if (b.withdrawn) { leaveFight(); return }
        GameLogic.finishBattle(save, b, seed())
        battle = null
        persist()
        val next = if (save.run.active) CardScreen.Run else if (save.draft.isNotEmpty()) CardScreen.Draft else CardScreen.Hub
        if (next == CardScreen.Hub) GameLogic.closeDraft(save)
        show(next)
    }

    /** Leaves a fight without a result: the run is abandoned, like walking away from the table. */
    fun leaveFight() {
        if (external != null) { battle?.forfeit(); finishFight(); return }
        battle = null
        GameLogic.abandon(save)
        persist()
        show(if (save.draft.isNotEmpty()) CardScreen.Draft else CardScreen.Hub)
    }

    fun askLeave(ask: Boolean) = bump { it.copy(confirmLeave = ask) }

    fun draft(row: Int, id: String) { GameLogic.draft(save, row, id); persist(); bump() }

    fun closeDraft() { GameLogic.closeDraft(save); persist(); show(CardScreen.Hub) }

    // ---- battle ---------------------------------------------------------------------------------------------------------

    private fun say(text: String) = bump { it.copy(message = text) }

    /** Picks a card to play: self cards play at once, group cards wait for a confirm, single-target cards for a target. */
    fun selectCard(card: BattleCard) {
        val b = battle ?: return
        if (b.finished || b.epiphanyCard != null) return
        val current = _ui.value.pending
        if (current == null || current.id != card.id) assistFor = null
        if (current != null && current.id == card.id) {
            // Tapping the waiting card again plays a group card, or puts a single-target card back.
            if (groupTarget(card)) play(card, null) else bump { it.copy(pending = null) }
            return
        }
        val actor = b.ownerOf(card)
        if (!b.canPay(card, actor)) {
            say(why(b, card, actor))
            return
        }
        if (card.target == BattleTarget.Self) play(card, actor) else bump { it.copy(pending = card, message = prompt(card)) }
    }

    private fun groupTarget(card: BattleCard) =
        card.target == BattleTarget.AllEnemies || card.target == BattleTarget.AllAllies || card.target == BattleTarget.None

    private fun prompt(card: BattleCard) = when {
        groupTarget(card) -> "Tap ${card.name} again to play it."
        card.target == BattleTarget.Ally -> "Pick an ally for ${card.name}."
        else -> "Pick a target for ${card.name}."
    }

    private fun why(b: BattleState, card: BattleCard, actor: BattleUnit?): String = when {
        card.kind == com.ihy2ln.weaverse.feature.games.battle.engine.BattleCardKind.Summoner -> "JD needs more CP for ${card.name}."
        actor == null || !actor.alive -> "${card.name} can't be played."
        actor.strained(card) -> "${actor.name} is in breakdown: only 1 EP cards."
        card.kind == com.ihy2ln.weaverse.feature.games.battle.engine.BattleCardKind.Ultimate ->
            if (actor.ultimate < 100) "${actor.name}'s gauge isn't full." else "Needs ${b.ultCost(actor)} SP."
        actor.ap < card.ap -> "${actor.name} has no AP left."
        else -> "${actor.name} needs ${card.ep} EP."
    }

    /** A tap on a unit: the waiting card's target, or else open that fighter's actions. */
    fun tapUnit(unit: BattleUnit) {
        val b = battle ?: return
        val pending = _ui.value.pending
        if (pending != null && b.isTarget(pending, b.ownerOf(pending), unit)) { play(pending, unit); return }
        if (!unit.enemy) bump { it.copy(focus = if (it.focus === unit) null else unit, pending = null) }
        else bump { it.copy(pending = null) }
    }

    fun confirmPending() {
        val card = _ui.value.pending ?: return
        if (groupTarget(card)) play(card, null)
    }

    fun cancelPending() { assistFor = null; bump { it.copy(pending = null) } }

    private fun play(card: BattleCard, target: BattleUnit?) {
        val b = battle ?: return
        val before = b.factSerial
        val ok = b.tryPlay(card, target)
        afterAction(b, before, if (ok) b.lastMessage else "Can't do that.")
    }

    fun playBasic(actor: BattleUnit) = selectCard(Catalog.basic(actor))

    fun playGuard(actor: BattleUnit) = selectCard(Catalog.guard(actor))

    fun playUltimate(actor: BattleUnit, choice: Int) {
        val card = Catalog.ultimates(actor).getOrNull(choice) ?: return
        selectCard(card)
    }

    fun playAwakening(actor: BattleUnit) {
        val card = Catalog.awakening(actor) ?: return
        selectCard(card)
    }

    fun decree() {
        val b = battle ?: return
        val before = b.factSerial
        val ok = b.trySummonerUltimate()
        afterAction(b, before, if (ok) b.lastMessage else "The decree needs ${BattleState.SP_MAX} SP.")
    }

    fun swap(field: BattleUnit, reserve: BattleUnit) {
        val b = battle ?: return
        val before = b.factSerial
        val ok = b.trySwap(field, reserve)
        afterAction(b, before, if (ok) b.lastMessage else "Can't swap.")
    }

    /** A reserve's partner assist: needs a target when their move picks one. */
    fun assist(reserve: BattleUnit) {
        val b = battle ?: return
        if (!b.canPartnerAssist(reserve)) { say("${reserve.name} can't assist (2 SP, once a battle)."); return }
        val move = b.partnerMove(reserve) ?: return
        when (move.target) {
            BattleTarget.Enemy, BattleTarget.Ally -> { assistFor = reserve; bump { it.copy(pending = move, message = prompt(move)) } }
            else -> {
                val before = b.factSerial
                val ok = b.tryPartnerAssist(reserve, null)
                afterAction(b, before, if (ok) b.lastMessage else "Can't assist.")
            }
        }
    }

    /** Target chosen for a waiting partner assist (called instead of [tapUnit]'s normal play). */
    private fun assistTarget(unit: BattleUnit): Boolean {
        val b = battle ?: return false
        val reserve = assistFor ?: return false
        val move = _ui.value.pending ?: return false
        if (!move.name.endsWith("(Assist)")) return false
        val before = b.factSerial
        val ok = b.tryPartnerAssist(reserve, unit)
        assistFor = null
        if (!ok) { say("Not a valid target."); return true }
        afterAction(b, before, b.lastMessage)
        return true
    }

    fun endTurn() {
        val b = battle ?: return
        if (b.finished || b.epiphanyCard != null) return
        val before = b.factSerial
        b.endTurn()
        afterAction(b, before, b.lastMessage)
    }

    fun chooseEpiphany(index: Int) {
        val b = battle ?: return
        val before = b.factSerial
        if (b.chooseEpiphany(index)) afterAction(b, before, b.lastMessage)
    }

    fun toggleLog() = bump { it.copy(showLog = !it.showLog) }

    private fun afterAction(b: BattleState, before: Int, message: String) {
        val fresh = b.facts.takeLast(minOf(b.facts.size, b.factSerial - before))
        val popups = fresh.mapNotNull { f ->
            val text = when (f.kind) {
                "damage" -> (if (f.crit) "-${f.amount}!" else "-${f.amount}")
                "poison" -> "-${f.amount}"
                "heal", "regen" -> "+${f.amount}"
                "shield" -> "+${f.amount} shield"
                "blocked" -> "Blocked"
                "break" -> "BREAK"
                "interrupt" -> "!"
                "status" -> f.status.ifBlank { null }
                else -> null
            }
            text?.let { Popup(++popupId, if (f.kind == "interrupt") (f.actor ?: f.target).id else f.target.id, it, f.kind) }
        }
        val ids = popups.map { it.id }.toSet()
        assistFor = null
        bump { it.copy(pending = null, focus = if (it.focus?.alive == true || b.finished) it.focus else null, message = message, popups = it.popups + popups) }
        if (ids.isNotEmpty()) viewModelScope.launch {
            delay(1300)
            _ui.update { u -> u.copy(popups = u.popups.filterNot { it.id in ids }) }
        }
    }

    /** Taps on a unit while a partner assist is waiting for its target. */
    fun tapUnitOrAssist(unit: BattleUnit) {
        if (assistFor != null && assistTarget(unit)) return
        tapUnit(unit)
    }

    override fun onCleared() { battle = null }
}
