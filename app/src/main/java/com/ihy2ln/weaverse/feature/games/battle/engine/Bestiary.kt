package com.ihy2ln.weaverse.feature.games.battle.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlin.math.max
import kotlin.math.min

@Serializable
class BestiaryCard(
    val id: String = "",
    val name: String = "",
    val target: String = "Enemy",
    val status: String = "",
    val ep: Int = 1,
    val duration: Int = 0,
    val power: Float = 0f,
    val magnitude: Float = 0f,
    val heal: Float = 0f,
    val magic: Boolean = false,
)

@Serializable
class BestiaryForm(
    val id: String = "",
    val name: String = "",
    val flavor: String = "",
    val evolvesTo: String = "",
    val art: String = "",
    val element: String = "Neutral",
    val role: String = "Bruiser",
    val minRank: Int = 1,
    val maxRank: Int = 9,
    val stage: Int = 0,
    val large: Boolean = false,
    val ownArt: Boolean = false,
    val cards: List<BestiaryCard> = emptyList(),
) {
    @Transient
    var family: BestiaryFamily? = null
}

@Serializable
class BestiaryFamily(
    val id: String = "",
    val name: String = "",
    val element: String = "Neutral",
    val role: String = "Bruiser",
    val lore: String = "",
    val large: Boolean = false,
    val minRank: Int = 1,
    val maxRank: Int = 9,
    val themes: List<String> = emptyList(),
    val forms: List<BestiaryForm> = emptyList(),
)

@Serializable
class BestiaryBoss(val region: String = "", val family: String = "", val title: String = "", val theme: String = "")

@Serializable
class BestiaryFile(
    val version: Int = 1,
    val families: List<BestiaryFamily> = emptyList(),
    val bosses: List<BestiaryBoss> = emptyList(),
)

/**
 * The Adams Haven bestiary (bestiary.json, bundled in assets/cardgame). Monsters are variants in families; each family
 * spans a rank range F (1) .. SSR (9) and its forms split that range.
 */
object Bestiary {
    val RANK_NAMES = arrayOf("F", "E", "D", "C", "B", "A", "S", "SS", "SSR")
    const val MAX_RANK = 9
    private val POPULATION = floatArrayOf(40f, 28f, 15f, 9f, 4f, 2f, 1f, .5f, .1f)

    private val familyList = mutableListOf<BestiaryFamily>()
    private val forms = HashMap<String, BestiaryForm>()
    private val bossList = mutableListOf<BestiaryBoss>()

    val loaded: Boolean get() = familyList.isNotEmpty()
    val families: List<BestiaryFamily> get() = familyList
    val bosses: List<BestiaryBoss> get() = bossList

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun loadJson(text: String) = load(json.decodeFromString(BestiaryFile.serializer(), text))

    fun load(file: BestiaryFile?) {
        familyList.clear(); forms.clear(); bossList.clear()
        if (file == null) return
        for (family in file.families) {
            if (family.forms.isEmpty()) continue
            familyList.add(family)
            for (form in family.forms) { form.family = family; forms[form.id] = form }
        }
        bossList.addAll(file.bosses)
    }

    fun rankName(rank: Int): String = RANK_NAMES[rank.coerceIn(1, MAX_RANK) - 1]

    fun form(id: String?): BestiaryForm? = if (id == null) null else forms[id]

    fun family(id: String): BestiaryFamily? = familyList.firstOrNull { it.id == id }

    fun formFor(family: BestiaryFamily, rank: Int): BestiaryForm {
        val r = max(family.minRank, min(family.maxRank, rank))
        return family.forms.firstOrNull { r >= it.minRank && r <= it.maxRank } ?: family.forms.last()
    }

    /** A run depth's natural rank: 1-2 F, 3-4 E, 5-6 D, 7-8 C, 9-10 B, 11 A, 12 S, 13+ SS. */
    fun rankForDepth(depth: Int): Int {
        val d = max(1, depth)
        return if (d <= 10) (d + 1) / 2 else min(8, d - 5)
    }

    fun rollRank(depth: Int, rng: Rng): Int {
        val center = rankForDepth(depth)
        val lo = max(1, center - 1)
        val hi = min(8, center + 1)
        var total = 0f
        for (r in lo..hi) total += POPULATION[r - 1] * (if (r == center) 2f else 1f)
        var pick = rng.nextDouble().toFloat() * total
        for (r in lo..hi) {
            pick -= POPULATION[r - 1] * (if (r == center) 2f else 1f)
            if (pick <= 0) return r
        }
        return center
    }

    /** Forms living in a theme at a rank; "edge" stands in for any theme, and the pool widens until it has monsters. */
    fun spawnable(rank: Int, theme: String?, smallOnly: Boolean = false): List<BestiaryForm> {
        val t = if (theme.isNullOrEmpty()) "edge" else theme
        val list = mutableListOf<BestiaryForm>()
        collect(list, rank, t, smallOnly)
        if (list.size < 3) collect(list, rank, "edge", smallOnly)
        if (list.isEmpty()) collect(list, rank, null, smallOnly)
        if (list.isEmpty()) collect(list, rank, null, false)
        return list
    }

    private fun collect(list: MutableList<BestiaryForm>, rank: Int, theme: String?, smallOnly: Boolean) {
        for (f in familyList) {
            if (theme != null && theme !in f.themes) continue
            for (form in f.forms)
                if (rank >= form.minRank && rank <= form.maxRank && !(smallOnly && form.large) && form !in list) list.add(form)
        }
    }
}
