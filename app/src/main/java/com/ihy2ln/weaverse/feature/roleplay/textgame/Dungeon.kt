package com.ihy2ln.weaverse.feature.roleplay.textgame

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * THE DUNGEON — a place you come back to, not a run you throw away.
 *
 * Faithful Kotlin port of the Godot AdamsHavenCardGame `core/dungeon.gd`:
 * rooms sit on a GRID and join through doors, so "which way" is a question
 * with four possible answers. Sight is limited to where you have been plus
 * one room in each direction — everything past that is fog.
 *
 * Two objects, deliberately separate:
 *   the DUNGEON  — the floors and every room's state. Outlives the trip.
 *   the DELVE    — where you are standing right now. Only meaningful mid-trip.
 */

enum class DungeonKind(val label: String, val glyph: String) {
    Enemy("Enemy", "⚔"),
    Elite("Elite", "‼"),
    Rest("Camp", "⛺"),
    Treasure("Treasure", "✦"),
    Merchant("Merchant", "⚖"),
    Unknown("Unknown", "?"),
    Entrance("Entrance", "⌂"),
    Boss("Boss", "☠"),
    Stairs("Stairs", "▼"),
    ;

    companion object {
        fun fromIndex(index: Int): DungeonKind = entries.getOrElse(index) { Enemy }
    }
}

/** Rooms that hold you until they are won. */
val DungeonKind.isFightKind: Boolean
    get() = this == DungeonKind.Enemy || this == DungeonKind.Elite || this == DungeonKind.Boss

enum class DungeonSight { Hidden, Peeked, Known }

// Doors, as a bitmask. Grid y grows DOWNWARD, so north is -y.
const val DOOR_N = 1
const val DOOR_E = 2
const val DOOR_S = 4
const val DOOR_W = 8

val DOOR_OFFSETS: List<Pair<Int, Pair<Int, Int>>> = listOf(
    DOOR_N to (0 to -1),
    DOOR_E to (1 to 0),
    DOOR_S to (0 to 1),
    DOOR_W to (-1 to 0),
)

private fun doorOpposite(d: Int): Int = when (d) {
    DOOR_N -> DOOR_S
    DOOR_S -> DOOR_N
    DOOR_E -> DOOR_W
    else -> DOOR_E
}

@Serializable
data class DungeonRoom(
    val x: Int,
    val y: Int,
    /** DungeonKind ordinal. */
    val kind: Int = DungeonKind.Enemy.ordinal,
    val doors: Int = 0,
    /** Both survive the trip that set them — the reason a second delve is shorter. */
    val visited: Boolean = false,
    val cleared: Boolean = false,
) {
    fun hasDoor(d: Int): Boolean = (doors and d) != 0
    fun isFight(): Boolean = DungeonKind.fromIndex(kind).isFightKind
    fun blocksExit(): Boolean = isFight() && !cleared
    fun title(): String = DungeonKind.fromIndex(kind).label
}

@Serializable
data class DungeonFloorState(
    val index: Int = 0,
    val sizeX: Int = 6,
    val sizeY: Int = 5,
    val entranceX: Int = 0,
    val entranceY: Int = 0,
    val bossX: Int = 0,
    val bossY: Int = 0,
    val stairsX: Int = 0,
    val stairsY: Int = 0,
    val rooms: List<DungeonRoom> = emptyList(),
) {
    fun room(x: Int, y: Int): DungeonRoom? = rooms.firstOrNull { it.x == x && it.y == y }
    fun fights(): Int = rooms.count { DungeonKind.fromIndex(it.kind).isFightKind }
    fun fightsCleared(): Int = rooms.count { DungeonKind.fromIndex(it.kind).isFightKind && it.cleared }
    fun beaten(): Boolean = room(bossX, bossY)?.cleared == true

    /** Every camp you have actually stood in — re-entry points for the next delve. */
    fun camps(): List<DungeonRoom> = rooms
        .filter { DungeonKind.fromIndex(it.kind) == DungeonKind.Rest && it.visited }
        .sortedWith(compareBy({ it.y }, { it.x }))
}

@Serializable
data class DungeonState(
    val seed: Long = 0L,
    val deepestUnlocked: Int = 0,
    /** -1 means you are not in the dungeon. */
    val floorIndex: Int = -1,
    val atX: Int = 0,
    val atY: Int = 0,
    val floors: List<DungeonFloorState> = emptyList(),
) {
    fun floorAt(index: Int): DungeonFloorState? = floors.getOrNull(index)
    fun currentFloor(): DungeonFloorState? = floorAt(floorIndex)
    fun currentRoom(): DungeonRoom? = currentFloor()?.room(atX, atY)
    fun inDelve(): Boolean = currentFloor() != null

    fun totalFights(): Int = floors.sumOf { it.fights() }

    /** Deeper pays better — how far down you go IS the difficulty dial. */
    fun rewardMultiplier(): Double = 1.0 + REWARD_PER_FLOOR * max(0, floorIndex)

    fun floorName(index: Int = floorIndex): String = "Floor ${(if (index >= 0) index else floorIndex) + 1}"

    companion object {
        const val FLOOR_COUNT = 5
        const val GRID_BASE_X = 6
        const val GRID_BASE_Y = 5
        const val ROOMS_BASE = 12
        const val ROOMS_PER_FLOOR = 2
        const val MIN_FIGHTS_PER_FLOOR = 6
        const val MIN_CAMPS_PER_FLOOR = 2
        const val REWARD_PER_FLOOR = 0.35

        fun gridFor(index: Int): Pair<Int, Int> =
            (GRID_BASE_X + index / 2) to (GRID_BASE_Y + (index + 1) / 2)

        fun roomsFor(index: Int): Int = ROOMS_BASE + ROOMS_PER_FLOOR * index
    }
}

/**
 * The generator + delve rules, ported one-to-one from `core/dungeon.gd`.
 * `generate` builds every floor up front; `startDelve`/`stepTo`/`clearCurrent`
 * drive a trip; fog runs through [DungeonRules.sight].
 */
object DungeonGenerator {
    private const val BRANCH_CHANCE = 0.35
    private const val LOOP_RATIO = 0.18

    private class Rng(seed: Long) {
        private var value = seed
        fun next(): Long {
            value = value * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L
            return value ushr 1
        }
        fun range(until: Int): Int = (next() % until).toInt()
        fun rangeIn(from: Int, untilInclusive: Int): Int = from + range(untilInclusive - from + 1)
        fun chance(p: Double): Boolean = (next() % 10_000L) / 10_000.0 < p
    }

    private class BuilderRoom(val x: Int, val y: Int) {
        var kind: DungeonKind = DungeonKind.Enemy
        var doors: Int = 0
        var visited: Boolean = false
        var cleared: Boolean = false

        fun hasDoor(d: Int): Boolean = (doors and d) != 0
    }

    private class BuilderFloor(val index: Int) {
        val sizeX: Int = DungeonState.gridFor(index).first
        val sizeY: Int = DungeonState.gridFor(index).second
        val rooms = mutableMapOf<String, BuilderRoom>()
        var entrance = 0 to 0
        var boss = 0 to 0
        var stairs = 0 to 0

        fun key(x: Int, y: Int): String = "$x,$y"
        fun room(x: Int, y: Int): BuilderRoom? = rooms[key(x, y)]
        fun put(x: Int, y: Int, room: BuilderRoom) {
            rooms[key(x, y)] = room
        }

        fun cells(): List<Pair<Int, Int>> =
            rooms.values.map { it.x to it.y }.sortedWith(compareBy({ it.second }, { it.first }))
    }

    fun generate(seed: Long, floorCount: Int = DungeonState.FLOOR_COUNT): DungeonState {
        val rng = Rng(seed)
        val floors = (0 until floorCount).map { buildFloor(rng, it) }
        return DungeonState(seed = seed, deepestUnlocked = 0, floorIndex = -1, floors = floors)
    }

    private fun buildFloor(rng: Rng, index: Int): DungeonFloorState {
        val fl = BuilderFloor(index)
        val budget = min(DungeonState.roomsFor(index), fl.sizeX * fl.sizeY - 2)

        // Carve from the entrance outward: a room only exists because a door
        // was cut into it from a room that already existed, so every room has
        // a route back by construction.
        fl.entrance = rng.rangeIn(0, fl.sizeX - 1) to (fl.sizeY - 1)
        fl.put(fl.entrance.first, fl.entrance.second, BuilderRoom(fl.entrance.first, fl.entrance.second).apply { kind = DungeonKind.Entrance })

        val frontier = mutableListOf(fl.entrance)
        while (fl.rooms.size < budget && frontier.isNotEmpty()) {
            // Mostly extend the newest room, occasionally jump back — a spine
            // with branches off it, not a snake and not a blob.
            var i = frontier.size - 1
            if (frontier.size > 1 && rng.chance(BRANCH_CHANCE)) {
                i = rng.rangeIn(0, frontier.size - 2)
            }
            val from = frontier[i]
            val options = freeNeighbours(fl, from.first, from.second)
            if (options.isEmpty()) {
                frontier.removeAt(i)
                continue
            }
            val d = options[rng.range(options.size)]
            val off = DOOR_OFFSETS.first { it.first == d }.second
            val nx = from.first + off.first
            val ny = from.second + off.second
            fl.put(nx, ny, BuilderRoom(nx, ny))
            cutDoor(fl, from.first, from.second, d)
            frontier.add(nx to ny)
        }

        addLoops(rng, fl)
        placeLandmarks(rng, fl)
        assignKinds(rng, fl)
        return DungeonFloorState(
            index = fl.index,
            sizeX = fl.sizeX,
            sizeY = fl.sizeY,
            entranceX = fl.entrance.first,
            entranceY = fl.entrance.second,
            bossX = fl.boss.first,
            bossY = fl.boss.second,
            stairsX = fl.stairs.first,
            stairsY = fl.stairs.second,
            rooms = fl.rooms.values
                .map { DungeonRoom(it.x, it.y, it.kind.ordinal, it.doors, it.visited, it.cleared) }
                .sortedWith(compareBy({ it.y }, { it.x })),
        )
    }

    private fun freeNeighbours(fl: BuilderFloor, x: Int, y: Int): List<Int> = DOOR_OFFSETS.mapNotNull { (d, off) ->
        val nx = x + off.first
        val ny = y + off.second
        if (nx < 0 || ny < 0 || nx >= fl.sizeX || ny >= fl.sizeY) null
        else if (fl.room(nx, ny) != null) null
        else d
    }

    private fun cutDoor(fl: BuilderFloor, x: Int, y: Int, d: Int) {
        val off = DOOR_OFFSETS.first { it.first == d }.second
        val a = fl.room(x, y) ?: return
        val b = fl.room(x + off.first, y + off.second) ?: return
        a.doors = a.doors or d
        b.doors = b.doors or doorOpposite(d)
    }

    private fun addLoops(rng: Rng, fl: BuilderFloor) {
        var want = (fl.rooms.size * LOOP_RATIO).roundToInt()
        val cells = fl.cells()
        var guard = 0
        while (want > 0 && guard < 200) {
            guard += 1
            val cell = cells[rng.range(cells.size)]
            val here = fl.room(cell.first, cell.second) ?: continue
            val shut = DOOR_OFFSETS.mapNotNull { (d, off) ->
                val neighbour = fl.room(cell.first + off.first, cell.second + off.second)
                if (neighbour != null && !here.hasDoor(d)) d else null
            }
            if (shut.isEmpty()) continue
            cutDoor(fl, cell.first, cell.second, shut[rng.range(shut.size)])
            want -= 1
        }
    }

    /** The boss goes as far from the door as the floor allows; stairs one step past it. */
    private fun placeLandmarks(rng: Rng, fl: BuilderFloor) {
        val dist = distances(fl, fl.entrance.first, fl.entrance.second)
        var best = -1
        var pick = fl.entrance
        for (cell in fl.cells()) {
            val d = dist[cell] ?: continue
            if (d <= best) continue
            if (cell == fl.entrance) continue
            if (freeNeighbours(fl, cell.first, cell.second).isEmpty()) continue
            best = d
            pick = cell
        }
        if (best < 0) {
            for (cell in fl.cells()) {
                val d = dist[cell] ?: continue
                if (d > best && cell != fl.entrance) {
                    best = d
                    pick = cell
                }
            }
            fl.boss = pick
            fl.stairs = pick
            return
        }
        fl.boss = pick
        val options = freeNeighbours(fl, pick.first, pick.second)
        val door = options[rng.range(options.size)]
        val off = DOOR_OFFSETS.first { it.first == door }.second
        val sx = pick.first + off.first
        val sy = pick.second + off.second
        fl.put(sx, sy, BuilderRoom(sx, sy))
        cutDoor(fl, pick.first, pick.second, door)
        fl.stairs = sx to sy
    }

    /** Breadth-first hop count from a cell, THROUGH DOORS — also the reachability check. */
    private fun distances(fl: BuilderFloor, fromX: Int, fromY: Int): Map<Pair<Int, Int>, Int> {
        val from = fromX to fromY
        val out = mutableMapOf(from to 0)
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(from)
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            val room = fl.room(cell.first, cell.second) ?: continue
            DOOR_OFFSETS.forEach { (d, off) ->
                if (!room.hasDoor(d)) return@forEach
                val next = (cell.first + off.first) to (cell.second + off.second)
                if (fl.room(next.first, next.second) == null || out.containsKey(next)) return@forEach
                out[next] = (out[cell] ?: 0) + 1
                queue.add(next)
            }
        }
        return out
    }

    private fun assignKinds(rng: Rng, fl: BuilderFloor) {
        fl.room(fl.entrance.first, fl.entrance.second)?.kind = DungeonKind.Entrance
        fl.room(fl.boss.first, fl.boss.second)?.kind = DungeonKind.Boss
        fl.room(fl.stairs.first, fl.stairs.second)?.kind = DungeonKind.Stairs

        val open = fl.cells().filter { cell ->
            cell != fl.entrance && cell != fl.boss && cell != fl.stairs
        }

        // Camps first, spaced along the route in rather than dropped at random.
        val dist = distances(fl, fl.entrance.first, fl.entrance.second)
        val byDepth = open.sortedBy { dist[it] ?: 0 }
        val camps = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until DungeonState.MIN_CAMPS_PER_FLOOR) {
            if (byDepth.isEmpty()) break
            val want = ((byDepth.size - 1).toDouble() * (i.toDouble() + 1.0) /
                (DungeonState.MIN_CAMPS_PER_FLOOR + 1.0)).roundToInt()
            var scan = want.coerceIn(0, byDepth.size - 1)
            var cell = byDepth[scan]
            while (cell in camps && scan < byDepth.size - 1) {
                scan += 1
                cell = byDepth[scan]
            }
            if (cell !in camps) camps.add(cell)
        }
        camps.forEach { cell -> fl.room(cell.first, cell.second)?.kind = DungeonKind.Rest }

        open.forEach { cell ->
            if (cell in camps) return@forEach
            fl.room(cell.first, cell.second)?.kind = rollKind(rng)
        }

        // The floor must be worth several trips; top up fights if the roll was quiet.
        var need = DungeonState.MIN_FIGHTS_PER_FLOOR - fl.rooms.values.count { it.kind.isFightKind }
        if (need <= 0) return
        open.forEach { cell ->
            if (need <= 0) return@forEach
            if (cell in camps) return@forEach
            val room = fl.room(cell.first, cell.second) ?: return@forEach
            if (room.kind.isFightKind) return@forEach
            room.kind = DungeonKind.Enemy
            need -= 1
        }
    }

    private fun rollKind(rng: Rng): DungeonKind {
        val r = (rng.next() % 10_000L) / 10_000.0
        return when {
            r < 0.44 -> DungeonKind.Enemy
            r < 0.60 -> DungeonKind.Elite
            r < 0.72 -> DungeonKind.Treasure
            r < 0.82 -> DungeonKind.Rest
            r < 0.91 -> DungeonKind.Merchant
            else -> DungeonKind.Unknown
        }
    }
}

/** Read/delve rules over an immutable [DungeonState]. */
object DungeonRules {

    /** You see where you have been, and ONE room past it in each direction. */
    fun sight(dungeon: DungeonState, x: Int, y: Int): DungeonSight {
        val floor = dungeon.floorAt(dungeon.floorIndex) ?: return DungeonSight.Hidden
        val room = floor.room(x, y) ?: return DungeonSight.Hidden
        if (room.visited || room.cleared) return DungeonSight.Known
        DOOR_OFFSETS.forEach { (d, off) ->
            if (!room.hasDoor(d)) return@forEach
            val neighbour = floor.room(x + off.first, y + off.second)
            if (neighbour?.visited == true) return DungeonSight.Peeked
        }
        return DungeonSight.Hidden
    }

    fun isVisible(dungeon: DungeonState, x: Int, y: Int): Boolean =
        sight(dungeon, x, y) != DungeonSight.Hidden

    /** Start a trip: `from` must be the entrance or a camp you have stood in. */
    fun startDelve(dungeon: DungeonState, index: Int, fromX: Int = -1, fromY: Int = -1): DungeonState? {
        val floor = dungeon.floorAt(index) ?: return null
        if (index > dungeon.deepestUnlocked) return null
        val entryX = if (fromX < 0) floor.entranceX else fromX
        val entryY = if (fromY < 0) floor.entranceY else fromY
        if ((entryX != floor.entranceX || entryY != floor.entranceY) &&
            floor.camps().none { it.x == entryX && it.y == entryY }
        ) {
            return null
        }
        val rooms = floor.rooms.map { room ->
            if (room.x == entryX && room.y == entryY) {
                room.copy(visited = true, cleared = room.cleared || !DungeonKind.fromIndex(room.kind).isFightKind)
            } else {
                room
            }
        }
        return dungeon.copy(
            floorIndex = index,
            atX = entryX,
            atY = entryY,
            floors = dungeon.floors.map { if (it.index == index) it.copy(rooms = rooms) else it },
        )
    }

    /** Where you may go from here. Empty while a fight is unresolved. */
    fun exits(dungeon: DungeonState): List<DungeonRoom> {
        val floor = dungeon.currentFloor() ?: return emptyList()
        val here = floor.room(dungeon.atX, dungeon.atY) ?: return emptyList()
        if (here.blocksExit()) return emptyList()
        return DOOR_OFFSETS.mapNotNull { (d, off) ->
            if (!here.hasDoor(d)) return@mapNotNull null
            floor.room(dungeon.atX + off.first, dungeon.atY + off.second)
        }.sortedWith(compareBy({ it.y }, { it.x }))
    }

    fun canStepTo(dungeon: DungeonState, x: Int, y: Int): Boolean =
        exits(dungeon).any { it.x == x && it.y == y }

    fun stepTo(dungeon: DungeonState, x: Int, y: Int): DungeonState? {
        if (!canStepTo(dungeon, x, y)) return null
        val floor = dungeon.currentFloor() ?: return dungeon
        val rooms = floor.rooms.map { room ->
            if (room.x == x && room.y == y) room.copy(visited = true) else room
        }
        return dungeon.copy(
            atX = x,
            atY = y,
            floors = dungeon.floors.map { if (it.index == floor.index) it.copy(rooms = rooms) else it },
        )
    }

    /** The room is done with you. A fight room calls this on victory. */
    fun clearCurrent(dungeon: DungeonState): DungeonState {
        val floor = dungeon.currentFloor() ?: return dungeon
        val rooms = floor.rooms.map { room ->
            if (room.x == dungeon.atX && room.y == dungeon.atY) {
                room.copy(visited = true, cleared = true)
            } else {
                room
            }
        }
        var deepest = dungeon.deepestUnlocked
        val here = floor.room(dungeon.atX, dungeon.atY)
        if (here != null && DungeonKind.fromIndex(here.kind) == DungeonKind.Boss) {
            deepest = max(dungeon.deepestUnlocked, min(floor.index + 1, dungeon.floors.size - 1))
        }
        return dungeon.copy(
            deepestUnlocked = deepest,
            floors = dungeon.floors.map { if (it.index == floor.index) it.copy(rooms = rooms) else it },
        )
    }

    /** Walk out from a camp or the entrance only — anywhere else you are committed. */
    fun canRetreat(dungeon: DungeonState): Boolean {
        val room = dungeon.currentRoom() ?: return false
        if (room.blocksExit()) return false
        return DungeonKind.fromIndex(room.kind) == DungeonKind.Rest ||
            DungeonKind.fromIndex(room.kind) == DungeonKind.Entrance
    }

    fun endDelve(dungeon: DungeonState): DungeonState =
        dungeon.copy(floorIndex = -1, atX = 0, atY = 0)

    fun canDescend(dungeon: DungeonState): Boolean {
        val floor = dungeon.currentFloor() ?: return false
        val room = dungeon.currentRoom() ?: return false
        return DungeonKind.fromIndex(room.kind) == DungeonKind.Stairs && floor.beaten()
    }

    fun descend(dungeon: DungeonState): DungeonState? {
        if (!canDescend(dungeon)) return null
        val next = dungeon.floorIndex + 1
        if (dungeon.floorAt(next) == null) return null
        val unlocked = dungeon.copy(deepestUnlocked = max(dungeon.deepestUnlocked, next))
        return startDelve(unlocked, next)
    }
}
