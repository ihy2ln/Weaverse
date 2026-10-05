package com.ihy2ln.weaverse.feature.games.battle

import com.ihy2ln.weaverse.feature.games.battle.engine.BattleCard
import com.ihy2ln.weaverse.feature.games.battle.engine.BattleState
import com.ihy2ln.weaverse.feature.games.battle.engine.BattleTarget
import com.ihy2ln.weaverse.feature.games.battle.engine.BattleUnit
import com.ihy2ln.weaverse.feature.games.battle.engine.Catalog
import kotlin.math.max
import kotlin.math.min

/** A greedy player for tests: ultimates when ready, then the best affordable card per EP+AP spent, decrees at full SP. */
object TestBot {
    // Greedy: ultimates when ready, then the best affordable card per EP+AP spent, decrees at full SP, basic attacks, end turn.
    fun autoPlay(s: BattleState) {
        var safety = 0
        while (!s.finished && s.round <= 60 && safety++ < 5000) {
            if (s.epiphanyCard != null) s.chooseEpiphany(0)
            var acted = false
            for (a in s.allies) {
                if (!a.alive || a.ultimate < 100 || s.sp < s.ultCost(a)) continue
                val ults = Catalog.ultimates(a)
                if (ults.isEmpty()) continue
                val u = ults[0]
                val t = if (u.target == BattleTarget.Enemy) firstExposed(s) else if (u.target == BattleTarget.Self) a else null
                if (s.tryUltimate(a, 0, t)) { acted = true; break }
            }
            if (acted) continue
            if (s.sp >= BattleState.SP_MAX && s.trySummonerUltimate()) continue
            var best: BattleCard? = null
            var bestTarget: BattleUnit? = null
            var bestScore = 0f
            for (c in s.hand) {
                val actor = s.ownerOf(c)
                if (!s.canPay(c, actor)) continue
                for (t in targets(s, c, actor)) {
                    if (!s.isTarget(c, actor, t)) continue
                    val score = score(s, c, actor, t)
                    if (score > bestScore) { bestScore = score; best = c; bestTarget = t }
                }
            }
            if (best != null && s.tryPlay(best, bestTarget)) continue
            for (a in s.allies) if (a.alive && a.ap > 0 && s.tryBasic(a, firstExposed(s))) { acted = true; break }
            if (acted) continue
            s.endTurn()
        }
        if (!s.finished) s.forfeit()
    }

    private fun targets(s: BattleState, c: BattleCard, actor: BattleUnit?): List<BattleUnit?> = when {
        c.target == BattleTarget.AllEnemies || c.target == BattleTarget.AllAllies || c.target == BattleTarget.None -> listOf(null)
        c.target == BattleTarget.Self -> listOf(actor)
        else -> (if (c.target == BattleTarget.Enemy) s.enemies else s.allies) + listOfNotNull(if (c.target == BattleTarget.Enemy) s.enemySummoner else null)
    }

    private fun firstExposed(s: BattleState): BattleUnit? =
        s.enemies.firstOrNull { s.exposed(it) } ?: s.enemySummoner?.takeIf { it.alive }

    private fun score(s: BattleState, c: BattleCard, actor: BattleUnit?, t: BattleUnit?): Float {
        var score = 1f
        if (c.effectivePower > 0 && actor != null)
            for (e in s.enemies) if (e.alive && (c.target == BattleTarget.AllEnemies || e === t)) score += s.previewDamage(c, actor, e).amount
        if (c.effectivePower > 0 && actor != null && t != null && t === s.enemySummoner) score += s.previewDamage(c, actor, t).amount
        if (c.effectiveHeal > 0) {
            var missing = 0f
            for (a in s.allies) if (a.alive && (c.target == BattleTarget.AllAllies || a === t)) missing += a.maxHp - a.hp
            score += min(missing, c.effectiveHeal * (if (c.target == BattleTarget.AllAllies) 3 else 1)) * 1.2f
        }
        if (c.status.isNotEmpty()) score += 12
        if (c.draw > 0) score += 10 * c.draw
        if (c.transferEp) score += if (actor != null && actor.ep > 0 && t != null && t !== actor) 6 else -100
        return score / max(1, c.ep + c.ap)
    }
}
