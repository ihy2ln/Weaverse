# Shared foundation for every world screen — Farm, Town and Home.
#
# All three run on ONE system now. A screen is a `Placement.Screen`: a grid of
# `Placed` items, where a crop plot, a building, a resident and a decoration are
# the same kind of thing — a footprint the player can put down, walk to, and
# MOVE later. Placement lives in core/ as pure logic; this file only draws it
# and turns taps into verbs.
#
# `screen_id` names the persistent screen to show ("farm", "town"). An empty
# screen id builds a throwaway screen in memory instead — the Home interior
# uses that, because its contents are furniture the House already tracks.
extends Control
class_name ModeStub

const TILE_MAX := 72
const TILE_MIN := 18
const STEP_TIME := 0.13            # seconds per tile

@export var screen_id := ""
@export var area_name := "Area"
@export var ground_tile := Tiles.GRASS
# Optional: an art/world/ground_<id>.png that replaces the atlas ground tile as
# the walkable surface. Standing art and the player draw on top; under-decals
# (art/world/under_<def_id>.png) sit between the two. This one TILES: it is
# drawn once per cell, so it must be a repeating texture.
@export var ground_sprite := ""

# Optional: ONE painted image behind the whole grid, drawn to cover it. Use this
# for art with a composition — a road, a river, a worked field — which would be
# nonsense repeated in every cell. Takes priority over ground_sprite.
@export var backdrop_path := ""

var sc: Placement.Screen = null
var tile_px: float = TILE_MAX
var grid_origin := Vector2.ZERO     # the grid is centred in the view

var player := Vector2i(1, 1)
var path: Array = []               # remaining steps being walked
var walk_from := Vector2i(1, 1)
var walk_t := 0.0
var walking := false
var pending_interact = null        # item to use once we arrive

# What the next tap on the grid means. "" is the normal walk/inspect mode.
var mode := ""                     # "" | "build" | "move"
var build_def := ""
var move_uid := 0
var selected_uid := 0
var hover := Vector2i(-1, -1)

@onready var canvas: Control = $Root/Body/View
@onready var info: RichTextLabel = $Root/Body/Side/Col/Info
@onready var actions: VBoxContainer = $Root/Body/Side/Col/Scroll/Actions
@onready var title: Label = $Root/TopBar/Title

func _ready() -> void:
	title.text = area_name
	_load_screen()
	_reset_player()
	canvas.queue_redraw()
	_refresh_info()

func _load_screen() -> void:
	var s = _sess()
	if screen_id != "" and s != null:
		sc = s.screen(screen_id)
	if sc == null:
		# No session, or a mode with no persistent screen: a local grid.
		sc = Placement.Screen.new()
		sc.id = screen_id
		sc.title = area_name
		local_seed(sc)

# Overridden by a mode that owns a throwaway screen.
func local_seed(_screen: Placement.Screen) -> void:
	pass

func _sess():
	var r := get_tree().root
	return r.get_node("Session") if r.has_node("Session") else null

func _persistent() -> bool:
	return screen_id != "" and _sess() != null

# Drop the player on the first walkable tile, so re-entering a screen can never
# strand them inside something that was moved there while they were away.
func _reset_player() -> void:
	if is_walkable(player): return
	for y in sc.h:
		for x in sc.w:
			if is_walkable(Vector2i(x, y)):
				player = Vector2i(x, y)
				walk_from = player
				return

# ------------------------------------------------------------------- grid

func grid_w() -> int:
	return sc.w if sc != null else 1

func grid_h() -> int:
	return sc.h if sc != null else 1

# A screen that grows must still fit the view, so the TILES shrink rather than
# the world scrolling. Expansion is then visible the instant it happens.
func _fit_tiles() -> void:
	var avail: Vector2 = canvas.size
	if avail.x < 8.0 or avail.y < 8.0:
		tile_px = TILE_MAX
		grid_origin = Vector2.ZERO
		return
	tile_px = clampf(minf(avail.x / float(grid_w()), avail.y / float(grid_h())),
		TILE_MIN, TILE_MAX)
	var used := Vector2(tile_px * float(grid_w()), tile_px * float(grid_h()))
	grid_origin = ((avail - used) * 0.5).floor().max(Vector2.ZERO)

func _cell_at(pos: Vector2) -> Vector2i:
	var local := pos - grid_origin
	if local.x < 0.0 or local.y < 0.0: return Vector2i(-1, -1)
	return Vector2i(int(local.x / tile_px), int(local.y / tile_px))

func _cell_rect(cell: Vector2i, size: Vector2i = Vector2i.ONE) -> Rect2:
	return Rect2(grid_origin + Vector2(cell.x * tile_px, cell.y * tile_px),
		Vector2(size.x * tile_px, size.y * tile_px))

# The centre of a cell, in view coordinates.
func _cell_centre(cell: Vector2i) -> Vector2:
	return grid_origin + Vector2((float(cell.x) + 0.5) * tile_px,
		(float(cell.y) + 0.5) * tile_px)

func _in_bounds(p: Vector2i) -> bool:
	return p.x >= 0 and p.y >= 0 and p.x < grid_w() and p.y < grid_h()

# Crop plots are walkable — you stand ON a plot to work it. Everything else with
# a footprint is solid, and you walk up beside it.
func _blocks(it) -> bool:
	return it != null and it.kind != Placement.Kind.PLOT

func is_walkable(p: Vector2i) -> bool:
	if not _in_bounds(p): return false
	return not _blocks(sc.item_at(p))

# BFS path solve — shared by every world screen.
func find_path(from: Vector2i, to: Vector2i) -> Array:
	if not is_walkable(to) or from == to:
		return []
	var frontier := [from]
	var came := {_ckey(from): null}
	var dirs := [Vector2i(1, 0), Vector2i(-1, 0), Vector2i(0, 1), Vector2i(0, -1)]
	while not frontier.is_empty():
		var cur: Vector2i = frontier.pop_front()
		if cur == to: break
		for d in dirs:
			var nxt: Vector2i = cur + d
			if not is_walkable(nxt): continue
			var k := _ckey(nxt)
			if came.has(k): continue
			came[k] = cur
			frontier.append(nxt)
	if not came.has(_ckey(to)):
		return []
	var out: Array = []
	var c := to
	while c != from:
		out.push_front(c)
		c = came[_ckey(c)]
	return out

func _ckey(v: Vector2i) -> String:
	return "%d,%d" % [v.x, v.y]

# The reachable tile closest to an item, so tapping a building walks you to it.
func _approach_tile(it) -> Vector2i:
	if it.kind == Placement.Kind.PLOT and is_walkable(it.pos):
		return it.pos
	var best := player
	var best_d := 1 << 30
	for x in range(it.pos.x - 1, it.pos.x + it.size.x + 1):
		for y in range(it.pos.y - 1, it.pos.y + it.size.y + 1):
			var c := Vector2i(x, y)
			if not is_walkable(c): continue
			var d: int = absi(c.x - player.x) + absi(c.y - player.y)
			if d < best_d:
				best_d = d
				best = c
	return best

# ------------------------------------------------------------------ input

func _on_view_gui_input(event: InputEvent) -> void:
	if event is InputEventMouseMotion:
		var h := _cell_at(event.position)
		if h != hover:
			hover = h
			if mode != "": canvas.queue_redraw()
		return
	if event is InputEventMouseButton and event.pressed:
		if event.button_index == MOUSE_BUTTON_RIGHT:
			_cancel_mode()
			return
		if event.button_index != MOUSE_BUTTON_LEFT:
			return
		var cell := _cell_at(event.position)
		if not _in_bounds(cell): return
		match mode:
			"build": _do_build(cell)
			"move": _do_move(cell)
			_: _tap(cell)

func _tap(cell: Vector2i) -> void:
	var it = sc.item_at(cell)
	if it != null:
		var spot := _approach_tile(it)
		if spot == player:
			_select(it)
		else:
			var pa := find_path(player, spot)
			if pa.is_empty(): _select(it)     # unreachable: still show its panel
			else: _begin_walk(pa, it)
		return
	_begin_walk(find_path(player, cell), null)

func _begin_walk(p: Array, target) -> void:
	if p.is_empty():
		if target != null: _select(target)
		return
	path = p
	walk_from = player
	walk_t = 0.0
	walking = true
	pending_interact = target
	set_process(true)

func _process(delta: float) -> void:
	if not walking:
		set_process(false)
		return
	walk_t += delta / STEP_TIME
	while walk_t >= 1.0 and not path.is_empty():
		walk_t -= 1.0
		walk_from = player
		player = path.pop_front()
		if has_node("/root/Sfx") and (path.size() % 2) == 0:
			get_node("/root/Sfx").play("select", -26.0)
	if path.is_empty():
		walking = false
		walk_t = 0.0
		if pending_interact != null:
			var t = pending_interact
			pending_interact = null
			_select(t)
		else:
			_refresh_info()
	canvas.queue_redraw()

func _draw_pos() -> Vector2:
	var a := _cell_centre(walk_from)
	var b := _cell_centre(player)
	if not walking: return b
	return a.lerp(b, clampf(walk_t, 0.0, 1.0))

# -------------------------------------------------------------- the verbs

func _do_build(cell: Vector2i) -> void:
	var did := build_def
	_cancel_mode()
	if not _persistent():
		_say("Nothing can be built here.")
		return
	var res: Dictionary = _sess().world_build(screen_id, did, cell)
	if res.ok:
		_sfx("card")
		_say("Built %s for %dg." % [WorldDefs.display_name(did), int(res.cost)])
	else:
		_sfx("denied")
		_say(str(res.reason))
	_after_change()

func _do_move(cell: Vector2i) -> void:
	var uid := move_uid
	_cancel_mode()
	if not _persistent(): return
	var res: Dictionary = _sess().world_move(screen_id, uid, cell)
	if res.ok:
		_sfx("card")
		_say("Moved.")
		selected_uid = uid
	else:
		_sfx("denied")
		_say(str(res.reason))
	_after_change()

func _cancel_mode() -> void:
	mode = ""
	build_def = ""
	move_uid = 0
	canvas.queue_redraw()

# A placement change can strand the player inside a new footprint, so re-seat
# them before anything tries to path.
func _after_change() -> void:
	path = []
	walking = false
	_reset_player()
	canvas.queue_redraw()
	var it = sc.by_uid(selected_uid) if selected_uid > 0 else null
	if it != null: _select(it)
	else: _refresh_info()

# -------------------------------------------------------------- the panel

func _clear_actions() -> void:
	for c in actions.get_children():
		c.queue_free()

func _button(txt: String, cb: Callable) -> Button:
	var b := Button.new()
	b.text = txt
	b.pressed.connect(cb)
	actions.add_child(b)
	return b

func _say(t: String) -> void:
	info.append_text("\n" + t + "\n")

func _sfx(k: String) -> void:
	if has_node("/root/Sfx"): get_node("/root/Sfx").play(k)

# Defensive on purpose: a world screen can be built before the session has
# finished loading its save, and a panel is not worth crashing a scene over.
func _gold() -> int:
	var s = _sess()
	if s == null: return 0
	return int(s.data.get("inventory", {}).get("gold", 0))

func _select(it) -> void:
	if it == null:
		_refresh_info()
		return
	selected_uid = it.uid
	_clear_actions()
	info.clear()
	info.append_text("[b]%s[/b]\n%s\n" % [WorldDefs.display_name(it.def_id),
		WorldDefs.verb(it.def_id)])
	if not it.tags.is_empty():
		info.append_text("[i]Tags: %s[/i]\n" % ", ".join(it.tags))
	_write_bonuses(it)
	if _persistent() and WorldDefs.movable(it.def_id):
		var uid: int = it.uid
		_button("Move it", func():
			mode = "move"
			move_uid = uid
			_say("Tap where it should stand. Right-click to cancel.")
			canvas.queue_redraw())
		var cost: int = WorldDefs.cost_of(it.def_id)
		if cost > 0:
			_button("Remove (+%dg)" % int(float(cost) * 0.5), func():
				var res: Dictionary = _sess().world_remove(screen_id, uid)
				if res.ok:
					_sfx("back")
					selected_uid = 0
					_say("Removed — %dg back." % int(res.refund))
				else:
					_sfx("denied")
					_say(str(res.reason))
				_after_change())
	_extra_interact(it)

# Overridden by each mode for its per-building panels.
func _extra_interact(_it) -> void:
	pass

# Open a building's panel by name, without a click. This exists because
# synthetic input cannot reach the window in this environment, so it is the
# only way to SEE a panel in a screenshot — and it is the same seam a future
# "take me to the Guild" deep link would use.
func select_def(def_id: String) -> bool:
	if sc == null: return false
	for it in sc.items:
		if it.def_id == def_id:
			player = _approach_tile(it)
			walk_from = player
			_select(it)
			canvas.queue_redraw()
			return true
	return false

# Adjacency is invisible unless it is spelled out, and a bonus the player cannot
# see is a bonus they will never build around.
func _write_bonuses(it) -> void:
	var bonuses := Placement.bonuses_for(sc, it)
	if bonuses.is_empty():
		info.append_text("\n[color=#8a8a92]No adjacency bonus yet — stand it next to something related.[/color]\n")
		return
	info.append_text("\n[b]Adjacency[/b]\n")
	for b in bonuses:
		var gains: Array = []
		for k in b.keys():
			if k in ["label", "with", "key"]: continue
			gains.append("+%d%% %s" % [int(float(b[k]) * 100.0), k])
		info.append_text("  [color=#9fe6a0]%s[/color] with %s — %s\n" %
			[str(b["label"]), WorldDefs.display_name(str(b["with"])), ", ".join(gains)])

func _refresh_info() -> void:
	_clear_actions()
	info.clear()
	info.append_text("[b]%s[/b]\n" % area_name)
	info.append_text("%d x %d   ·   Gold: %d\n" % [grid_w(), grid_h(), _gold()])
	info.append_text("Tap a tile to walk. Tap anything placed to inspect, move or remove it.\n")
	var totals := Placement.screen_totals(sc)
	if not totals.is_empty():
		info.append_text("\n[b]This screen contributes[/b]\n")
		for k in totals.keys():
			info.append_text("  +%d%% %s\n" % [int(float(totals[k]) * 100.0), k])
	_mode_summary()
	if _persistent():
		_build_menu()
		_expand_menu()

# Each mode adds its own lines here.
func _mode_summary() -> void:
	pass

func _build_menu() -> void:
	var options := WorldDefs.buildable_on(screen_id)
	if options.is_empty(): return
	info.append_text("\n[b]Build[/b] — pick one, then tap a free tile.\n")
	for did in options:
		var cost: int = WorldDefs.cost_of(did)
		var b := _button("%s  (%dg)" % [WorldDefs.display_name(did), cost], func():
			mode = "build"
			build_def = did
			_say("Tap a free tile to place the %s. Right-click to cancel."
				% WorldDefs.display_name(did))
			canvas.queue_redraw())
		b.disabled = cost > _gold()

# Screens expand INDEPENDENTLY — clearing ground in the Farm does nothing for
# the Town, which is the entire reason they are separate screens.
func _expand_menu() -> void:
	var cost := sc.expand_cost()
	info.append_text("\n[b]Clear more ground[/b] — %dg\n" % cost)
	var wide := _button("Widen  (%d to %d)" % [sc.w, sc.expanded_w(2)], func(): _expand(2, 0))
	wide.disabled = not sc.can_expand(2, 0) or cost > _gold()
	var tall := _button("Deepen  (%d to %d)" % [sc.h, sc.expanded_h(2)], func(): _expand(0, 2))
	tall.disabled = not sc.can_expand(0, 2) or cost > _gold()

func _expand(dw: int, dh: int) -> void:
	var res: Dictionary = _sess().world_expand(screen_id, dw, dh)
	if res.ok:
		_sfx("victory")
		_say("Ground cleared — now %d x %d for %dg." % [int(res.w), int(res.h), int(res.cost)])
	else:
		_sfx("denied")
		_say(str(res.reason))
	selected_uid = 0
	_after_change()

# ------------------------------------------------------------------- draw

# Three passes, then back-to-front standing art:
#   1. ground fill — the walkable surface (atlas tile or art/world/ground_<id>.png)
#   2. under-decals — art/world/under_<def_id>.png, flat on the footprint
#   3. standing art + player, sorted by the bottom of each footprint
# In a 3/4 view a building is drawn TALLER than the ground it stands on, so
# whatever is lower on the screen must be painted later or it ends up behind
# a roof it should be standing in front of. The player is one more thing in
# that order, not a layer above it.
func _on_view_draw() -> void:
	if sc == null: return
	_fit_tiles()
	var c := canvas
	_draw_ground(c)
	_draw_under(c)

	var ordered := sc.items.duplicate()
	ordered.sort_custom(func(a, b):
		return WorldDefs.baseline(a.pos, a.size) < WorldDefs.baseline(b.pos, b.size))
	var player_base := _draw_pos().y / tile_px - grid_origin.y / tile_px + 0.5
	var placed_player := false
	for it in ordered:
		if not placed_player and float(WorldDefs.baseline(it.pos, it.size)) > player_base:
			_draw_player(c)
			placed_player = true
		_draw_item(c, it)
	if not placed_player:
		_draw_player(c)

	_draw_links(c)          # over the items: underneath, they were invisible
	_draw_overlay(c)
	for step in path:
		c.draw_circle(_cell_centre(step), tile_px * 0.09, Color(0.4, 0.8, 1, 0.45))

func _draw_player(c: CanvasItem) -> void:
	var pp := _draw_pos()
	c.draw_circle(pp + Vector2(0, tile_px * 0.22), tile_px * 0.26, Color(0, 0, 0, 0.28))
	c.draw_circle(pp, tile_px * 0.30, Color(0.4, 0.9, 1))
	c.draw_arc(pp, tile_px * 0.30, 0, TAU, 20, Color(0.85, 0.97, 1.0), 2.0)

func _draw_ground(c: CanvasItem) -> void:
	# One painted image across the whole grid, if this screen has one.
	if _backdrop() != null:
		_draw_backdrop(c)
		return
	var custom: Texture2D = Tiles.sprite("ground_" + ground_sprite) if ground_sprite != "" else null
	var has_tiles := Tiles.available()
	for x in grid_w():
		for y in grid_h():
			var cell := Vector2i(x, y)
			var r := _cell_rect(cell)
			if custom != null:
				c.draw_texture_rect(custom, r, false)
			elif has_tiles:
				Tiles.draw_cell(c, Tiles.ground_for(cell, ground_tile), r)
			else:
				c.draw_rect(Rect2(r.position + Vector2.ONE, r.size - Vector2(2, 2)),
					Color(0.16, 0.18, 0.22))

var _backdrop_tex: Texture2D = null
var _backdrop_looked := false

func _backdrop() -> Texture2D:
	if not _backdrop_looked:
		_backdrop_looked = true
		if backdrop_path != "" and ResourceLoader.exists(backdrop_path):
			_backdrop_tex = load(backdrop_path)
	return _backdrop_tex

# COVER the grid: scaled to fill it and centre-cropped, so the art never
# stretches out of proportion and never leaves a gap at the edge. The grid is
# what gets covered, not the whole panel — the playable area and the painted
# ground have to be the same rectangle or the two disagree about where things
# stand.
func _draw_backdrop(c: CanvasItem) -> void:
	var tex := _backdrop()
	if tex == null: return
	var area := Rect2(grid_origin,
		Vector2(float(grid_w()) * tile_px, float(grid_h()) * tile_px))
	var ts: Vector2 = tex.get_size()
	if ts.x < 1.0 or ts.y < 1.0 or area.size.x < 1.0 or area.size.y < 1.0: return
	# Centre-crop by choosing the SOURCE region rather than oversizing the
	# destination: the art fills the grid without stretching, and nothing
	# spills outside it, so no clipping is needed.
	var want := area.size.x / area.size.y
	var have := ts.x / ts.y
	var src := Rect2(Vector2.ZERO, ts)
	if have > want:
		var cw := ts.y * want
		src = Rect2((ts.x - cw) * 0.5, 0.0, cw, ts.y)
	else:
		var ch := ts.x / want
		src = Rect2(0.0, (ts.y - ch) * 0.5, ts.x, ch)
	c.draw_texture_rect_region(tex, area, src)
	# A light wash so buildings and the player stay readable on painted ground.
	c.draw_rect(area, Color(0.06, 0.07, 0.10, 0.18))

# Ground-level art that sits ON the walkable surface and UNDER standing sprites.
# Drop art/world/under_<def_id>.png next to the object's own sprite; no
# registration. Drawn in grid order, not by baseline — these are flat decals.
func _draw_under(c: CanvasItem) -> void:
	if sc == null: return
	for it in sc.items:
		var art := Tiles.sprite("under_" + it.def_id)
		if art == null: continue
		c.draw_texture_rect(art, _cell_rect(it.pos, it.size), false)

# The rectangle a thing is DRAWN into: as wide as its footprint, as tall as its
# footprint plus its overhang, sitting on the same bottom edge.
func _sprite_rect(it) -> Rect2:
	var cells := WorldDefs.draw_cells(it.pos, it.size, it.def_id)
	return _cell_rect(cells.position, cells.size)

func _draw_item(c: CanvasItem, it) -> void:
	var r := _cell_rect(it.pos, it.size)
	if it.kind == Placement.Kind.PLOT and not Tiles.has_sprite(it.def_id):
		draw_plot(c, it, r)
	else:
		# Generated art first, atlas tile second, flat block last — so dropping
		# a PNG into art/world/ replaces a tile with no code change at all.
		var art := Tiles.sprite(it.def_id)
		if art != null:
			c.draw_texture_rect(art, _sprite_rect(it), false)
		else:
			var tile: Vector2i = WorldDefs.tile_of(it.def_id)
			var drawn := false
			if tile != WorldDefs.NO_TILE:
				drawn = Tiles.draw_cell(c, tile, r.grow(-tile_px * 0.06))
			if not drawn:
				c.draw_rect(r.grow(-2.0), Color(0.45, 0.35, 0.25))
				c.draw_rect(r.grow(-2.0), Color(0.8, 0.7, 0.5), false, 2.0)
	# The selection outline traces the FOOTPRINT, never the sprite: what is
	# highlighted has to be the ground the thing actually occupies.
	if it.uid == selected_uid:
		c.draw_rect(r.grow(-1.0), Color(1.0, 0.9, 0.45), false, 2.0)

# Plots are drawn by their SOIL, not by a sprite — their state is the whole
# reason to look at them. The Farm overrides this; it knows the crop.
func draw_plot(c: CanvasItem, _it, r: Rect2) -> void:
	c.draw_rect(r.grow(-3.0), Color(0.30, 0.24, 0.18))
	c.draw_rect(r.grow(-3.0), Color(0, 0, 0, 0.35), false, 1.0)

# A faint thread between every pair earning an adjacency bonus, so the layout
# the player built is legible at a glance instead of buried in a side panel.
func _draw_links(c: CanvasItem) -> void:
	var seen := {}
	for it in sc.items:
		for other in sc.neighbours(it):
			var key := "%d-%d" % [mini(it.uid, other.uid), maxi(it.uid, other.uid)]
			if seen.has(key): continue
			if Placement.pair_bonus(it, other).is_empty(): continue
			seen[key] = true
			c.draw_line(_centre(it), _centre(other), Color(0.55, 0.95, 0.6, 0.35),
				maxf(1.0, tile_px * 0.05))

func _centre(it) -> Vector2:
	return grid_origin + Vector2((float(it.pos.x) + float(it.size.x) * 0.5) * tile_px,
		(float(it.pos.y) + float(it.size.y) * 0.5) * tile_px)

# While building or moving, the tile under the cursor says yes or no BEFORE the
# click, so placing something is never a guess.
func _draw_overlay(c: CanvasItem) -> void:
	if mode == "": return
	if not _in_bounds(hover): return
	var size := Vector2i.ONE
	var ignore := -1
	if mode == "build":
		size = WorldDefs.size_of(build_def)
	else:
		var it = sc.by_uid(move_uid)
		if it != null:
			size = it.size
			ignore = it.uid
	var okay := sc.can_place(Rect2i(hover, size), ignore)
	var col := Color(0.4, 1.0, 0.5, 0.30) if okay else Color(1.0, 0.35, 0.35, 0.30)
	c.draw_rect(_cell_rect(hover, size), col)
	c.draw_rect(_cell_rect(hover, size), Color(col.r, col.g, col.b, 0.9), false, 2.0)

func _on_back_pressed() -> void:
	get_tree().change_scene_to_file("res://scenes/Main.tscn")
