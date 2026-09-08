# Kenney "Tiny Battle" atlas (CC0, addons/kenney_tiny_battle) — 16x16 tiles in
# an 18-column sheet. Drawn straight from the texture rather than through a
# TileMap, because these screens are a Control canvas, not a 2D world.
#
# Every call degrades to `false` if the pack is missing, so the world screens
# keep rendering with their flat colours instead of going blank. Same contract
# the audio and VFX layers already use for their Kenney packs.
class_name Tiles

const PATH := "res://addons/kenney_tiny_battle/tilemap_packed.png"
const SRC := 16

# --- terrain
const GRASS := Vector2i(0, 0)
const GRASS_ALT := Vector2i(1, 0)
const FLOWERS := Vector2i(2, 0)
const WATER := Vector2i(1, 3)
const STONE := Vector2i(0, 6)
const ROAD := Vector2i(2, 6)

# --- the icon row
const DIGIT_ROW := 10
const ICON_QUESTION := Vector2i(10, 10)
const ICON_LOCK := Vector2i(13, 10)
const ICON_FLAG := Vector2i(14, 10)
const ICON_HEART := Vector2i(15, 10)
const ICON_CURSOR := Vector2i(16, 10)

# Per-object art, drawn INSTEAD of an atlas tile when the file exists. This is
# the seam the generated anime-perspective assets drop into: no code change,
# just a PNG named after the def.
#
#   art/world/<def_id>.png        a building, plot or decoration (ON TOP of ground)
#   art/world/under_<def_id>.png a ground decal under that object (soil, shadow)
#   art/world/ground_<id>.png    a screen's walkable ground fill
#
# Draw order in ModeStub is ground -> under-decals -> standing art. Standing
# sprites are BOTTOM-ANCHORED to their footprint and may be taller than it
# (see WorldDefs.overhang_of), which is what gives a 3/4 view its height.
const SPRITE_DIR := "res://art/world/"

static var _tex: Texture2D = null
static var _looked := false
static var _sprites: Dictionary = {}

static func sheet() -> Texture2D:
	if not _looked:
		_looked = true
		if ResourceLoader.exists(PATH):
			_tex = load(PATH)
	return _tex

static func available() -> bool:
	return sheet() != null

# Cached, including the misses: a lookup that failed once must not re-hit the
# filesystem every frame.
static func sprite(name: String) -> Texture2D:
	if _sprites.has(name):
		return _sprites[name]
	var path: String = SPRITE_DIR + name + ".png"
	var t: Texture2D = load(path) if ResourceLoader.exists(path) else null
	_sprites[name] = t
	return t

static func has_sprite(name: String) -> bool:
	return sprite(name) != null

# Call after adding art at runtime; the editor reload path does this for free.
static func forget_sprites() -> void:
	_sprites.clear()

static func region(cell: Vector2i) -> Rect2:
	return Rect2(cell.x * SRC, cell.y * SRC, SRC, SRC)

static func draw_cell(ci: CanvasItem, cell: Vector2i, dest: Rect2,
		tint: Color = Color.WHITE) -> bool:
	var t := sheet()
	if t == null: return false
	ci.draw_texture_rect_region(t, dest, region(cell), tint)
	return true

static func digit(n: int) -> Vector2i:
	return Vector2i(clampi(n, 0, 9), DIGIT_ROW)

# Small right-aligned number, for a plot's countdown to harvest.
static func draw_number(ci: CanvasItem, value: int, at: Vector2, px: float,
		tint: Color = Color.WHITE) -> bool:
	if not available(): return false
	var s := str(maxi(0, value))
	for i in s.length():
		draw_cell(ci, digit(int(s[i])), Rect2(at + Vector2(i * px, 0), Vector2(px, px)), tint)
	return true

# Ground varies without any stored data: same cell, same tile, every frame.
static func ground_for(cell: Vector2i, base: Vector2i) -> Vector2i:
	if base != GRASS: return base
	var h := (cell.x * 73856093) ^ (cell.y * 19349663)
	var m: int = absi(h) % 16
	if m == 0: return FLOWERS
	if m < 5: return GRASS_ALT
	return GRASS
