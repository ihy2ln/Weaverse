package com.ihy2ln.weaverse.feature.games.battle.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.feature.games.battle.engine.BattleCard
import com.ihy2ln.weaverse.feature.games.battle.engine.BattleCardKind
import com.ihy2ln.weaverse.feature.games.battle.engine.BattleUnit
import com.ihy2ln.weaverse.feature.games.battle.engine.CardText
import java.io.File

internal val EpColor = Color(0xFF4DA3FF)
internal val ApColor = Color(0xFFFFC145)
internal val HpColor = Color(0xFF58C27D)
internal val FoeColor = Color(0xFFE5604D)
internal val SpColor = Color(0xFFB28CFF)
internal val UltColor = Color(0xFFFF8F3F)
internal val StressColor = Color(0xFFD060A0)
internal val GoldColor = Color(0xFFFFD36A)

@Composable
internal fun Bar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Int = 6) {
    Box(modifier.height(height.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.55f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(color))
    }
}

@Composable
internal fun Pips(filled: Int, max: Int, color: Color, size: Int = 8) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 0 until max) {
            Box(
                Modifier.size(size.dp).clip(CircleShape)
                    .background(if (i < filled) color else Color.Black.copy(alpha = 0.5f))
                    .border(1.dp, color.copy(alpha = 0.7f), CircleShape),
            )
        }
    }
}

@Composable
internal fun Chip(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.85f)).padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

private fun statusChip(name: String, magnitude: Float): Pair<String, Color> = when (name) {
    "AttackUp" -> "ATK↑" to HpColor
    "AttackDown" -> "ATK↓" to FoeColor
    "DefenseUp" -> "DEF↑" to HpColor
    "DefenseDown" -> "DEF↓" to FoeColor
    "Slow" -> "SLOW" to FoeColor
    "Haste" -> "HASTE" to HpColor
    "Poison" -> "PSN" to Color(0xFF8A5CC8)
    "Burn" -> "BRN" to UltColor
    "Regen" -> "REGEN" to HpColor
    "Stun" -> "STUN" to ApColor.copy(alpha = 1f)
    "Taunt" -> "TAUNT" to EpColor
    "Shield" -> "SH ${magnitude.toInt()}" to EpColor
    "IceCounter" -> "COUNTER" to EpColor
    "Broken" -> "BROKEN" to GoldColor.copy(alpha = 0.95f)
    "Charging" -> "CHARGING" to FoeColor
    else -> name to Color.Gray
}

@Composable
internal fun StatusRow(unit: BattleUnit) {
    if (unit.statuses.isEmpty() && !unit.taunting) return
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        unit.statuses.filter { it.name != "Taunt" }.take(3).forEach { s ->
            val (label, color) = statusChip(s.name, s.magnitude)
            Chip(label, color)
        }
        if (unit.taunting) Chip("TAUNT", EpColor)
    }
}

@Composable
internal fun FloatingPopup(popup: Popup, modifier: Modifier = Modifier) {
    val progress = remember(popup.id) { Animatable(0f) }
    LaunchedEffect(popup.id) { progress.animateTo(1f, tween(1100)) }
    val color = when (popup.kind) {
        "damage", "poison" -> Color(0xFFFF6B4A)
        "heal", "regen", "shield" -> Color(0xFF6BE39A)
        "break" -> GoldColor
        else -> Color(0xFF8CC8FF)
    }
    Text(
        popup.text, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp,
        modifier = modifier.offset(y = (-46 * progress.value).dp).alpha(1f - progress.value * progress.value),
    )
}

/** What an enemy will do, as shown on its tile: the damage it would deal now, or the move's name. */
class IntentInfo(val card: BattleCard, val target: BattleUnit?, val damage: Int)

@Composable
internal fun UnitTile(
    unit: BattleUnit,
    dir: File,
    popups: List<Popup>,
    modifier: Modifier = Modifier,
    targetable: Boolean = false,
    focused: Boolean = false,
    preview: Int? = null,
    intent: IntentInfo? = null,
    tileHeight: Int = 130,
    onClick: () -> Unit = {},
) {
    val tokens = inkTokens()
    val border = when {
        targetable -> BorderStroke(2.5.dp, GoldColor)
        focused -> BorderStroke(2.dp, Color.White)
        else -> BorderStroke(1.dp, tokens.hairline)
    }
    Box(
        modifier.height(tileHeight.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1B1F27)).border(border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        val model = CardArt.unit(unit, dir)
        if (model != null) AsyncImage(model = model, contentDescription = unit.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(if (unit.alive) 1f else 0.3f))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))))
        Text(
            unit.name, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = 3.dp, end = if (unit.enemy) 22.dp else 4.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(4.dp)).padding(horizontal = 3.dp),
        )
        if (unit.enemy && unit.alive) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(3.dp).size(20.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.7f))
                    .border(1.dp, if (unit.actionCount <= 1) FoeColor else Color.White.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(unit.actionCount.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            if (unit.rank > 0) Chip(com.ihy2ln.weaverse.feature.games.battle.engine.Bestiary.rankName(unit.rank), Color(0xFF444C5C), Modifier.align(Alignment.TopEnd).padding(top = 26.dp, end = 4.dp))
        }
        intent?.let { i ->
            val text = when {
                i.damage > 0 && i.card.target != com.ihy2ln.weaverse.feature.games.battle.engine.BattleTarget.AllEnemies -> "⚔ ${i.damage}" + (i.target?.let { " → ${it.name.substringBefore(' ')}" } ?: "")
                i.damage > 0 -> "⚔ ${i.damage} all"
                else -> "✦ ${i.card.name}"
            }
            Text(
                text, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 22.dp, start = 4.dp, end = 4.dp)
                    .background((if (i.damage > 0) FoeColor else SpColor).copy(alpha = 0.9f), RoundedCornerShape(6.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            StatusRow(unit)
            if (unit.enemy && unit.maxTenacity > 0 && unit.alive) {
                if (unit.hasStatus("Broken")) Text("BROKEN", color = GoldColor, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                else Pips(unit.tenacity, unit.maxTenacity, GoldColor, 5)
            }
            if (!unit.enemy && unit.alive) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pips(unit.ap, unit.maxAp.coerceAtLeast(unit.ap), ApColor, 7)
                    Pips(unit.ep, unit.maxEp.coerceAtLeast(unit.ep), EpColor, 7)
                }
                Bar(unit.ultimate / 100f, if (unit.ultimate >= 100) GoldColor else UltColor, Modifier.fillMaxWidth(), 4)
                if (unit.collapseRounds > 0) Text("BREAKDOWN", color = StressColor, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                else if (unit.stress > 0) Bar(unit.stress / 100f, StressColor, Modifier.fillMaxWidth(), 3)
            }
            Bar(unit.hp / unit.maxHp.toFloat(), if (unit.enemy) FoeColor else HpColor, Modifier.fillMaxWidth(), 6)
            Text("${unit.hp}/${unit.maxHp}", color = Color.White, fontSize = 9.sp, modifier = Modifier.align(Alignment.End))
        }
        if (!unit.alive) Text("DOWN", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.align(Alignment.Center))
        if (preview != null && preview > 0) {
            Text(
                "−$preview", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp,
                modifier = Modifier.align(Alignment.Center).background(FoeColor.copy(alpha = 0.85f), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp),
            )
        }
        popups.forEach { FloatingPopup(it, Modifier.align(Alignment.Center)) }
    }
}

/** A card face: art, cost, name and rules text. Used in the hand, rewards, drafts and the deck builder. */
@Composable
internal fun CardFace(
    card: BattleCard,
    dir: File,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    dimmed: Boolean = false,
    glow: Boolean = false,
    badge: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val tokens = inkTokens()
    val jd = card.kind == BattleCardKind.Summoner
    val frame = when {
        selected -> BorderStroke(3.dp, Color.White)
        glow -> BorderStroke(2.5.dp, GoldColor)
        jd -> BorderStroke(2.dp, SpColor)
        else -> BorderStroke(1.dp, tokens.hairline)
    }
    Box(
        modifier.width(112.dp).height(164.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1B1F27))
            .border(frame, RoundedCornerShape(10.dp)).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        val model = CardArt.card(card, dir)
        if (model != null) AsyncImage(model = model, contentDescription = card.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(if (dimmed) 0.45f else 1f))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.35f), 0.4f to Color.Transparent, 0.62f to Color.Black.copy(alpha = 0.78f), 1f to Color.Black.copy(alpha = 0.92f))))
        Row(Modifier.align(Alignment.TopStart).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            when (card.kind) {
                BattleCardKind.Ultimate -> Chip("ULT", UltColor)
                BattleCardKind.Awakening -> Chip("AWAKEN", StressColor)
                BattleCardKind.Summoner -> Chip("${maxOf(1, card.ap)} CP", SpColor)
                else -> {
                    CostDot(card.ep.toString(), EpColor)
                    if (card.ap > 0) CostDot(card.ap.toString(), ApColor)
                }
            }
        }
        if (glow) Text("✦", color = GoldColor, fontSize = 16.sp, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
        badge?.let { Chip(it, GoldColor.copy(alpha = 1f), Modifier.align(Alignment.TopEnd).padding(4.dp)) }
        Column(Modifier.align(Alignment.BottomStart).padding(5.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(card.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                CardText.describe(card), color = Color.White.copy(alpha = 0.9f), fontSize = 8.5.sp, lineHeight = 10.sp, maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            val words = CardText.keywords(card)
            if (words.isNotEmpty()) Text(words, color = GoldColor, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun CostDot(text: String, color: Color) {
    Box(Modifier.size(20.dp).clip(CircleShape).background(color).border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape), contentAlignment = Alignment.Center) {
        Text(text, color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = inkTokens().activePill, letterSpacing = 1.2.sp)
}
