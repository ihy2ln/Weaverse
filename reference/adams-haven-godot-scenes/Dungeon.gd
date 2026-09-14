# The dungeon board. Room choice, non-combat room resolution, and the
# end-of-trip learning pick. Rules live in core/dungeon.gd + core/game_session.gd.
#
# The board is a GRID, drawn like a tabletop map: square rooms joined by
# corridors where doors were cut. What you can see is limited to where you have
# been plus one room in each direction out of it, so the greater part of a fresh
# floor is blank matting.
#
# The blank matting is drawn for EVERY grid cell, occupied or not. That is
# deliberate: if fog were drawn only over rooms that exist, the shape of the
# unexplored floor would be readable straight off the board and there would be
# nothing left to discover.
#
# MAP ART — all of it is a file drop, exactly like art/world/. Replace a PNG and
# the map uses it; delete it and the map falls back.
#
#   art/ui/<kind>.png            one per room kind
#   art/map/floor.webp           the explored floor
#   art/map/fog.webp             the unexplored floor
#   art/battle/<category>/       the room you are standing in — see
#                                scenes/RoomArt.gd and art/battle/categories.json
extends Control

const ICON_FOR := {
	Dungeon.Kind.ENEMY: "enemy", Dungeon.Kind.ELITE: "elite",
	Dungeon.Kind.REST: "rest", Dungeon.Kind.TREASURE: "treasure",
	Dungeon.Kind.MERCHANT: "merchant", Dungeon.Kind.UNKNOWN: "unknown",
	Dungeon.Kind.ENTRANCE: "entrance", Dungeon.Kind.BOSS: "boss",
	Dungeon.Kind.STAIRS: "stairs",
}

# THE BATTLEMAT. Two prints of the same 20x20 parchment: one lit, one in
# shadow. Explored rooms are patches of the lit one; everything else is the
# shadowed one. Using two prints of the SAME sheet is what makes the fog read as
# unlit floor rather than as a grey rectangle laid over the map.
# Measured off the printed gridlines rather than guessed: 20 cells of 99.22px
# inside a 32px bleed on a 2048px sheet. One board cell is drawn as exactly ONE
# PRINTED CELL, so the paper grain and the printed rules line up with the rooms
# instead of floating behind them at some unrelated scale.
const MAT_INSET := 32.0
const MAT_CELL := 99.22
const MAT_CELLS := 20

# Wall thickness as a fraction of a cell, and how much of an edge a doorway
# leaves open.
const WALL_W := 0.055
const DOOR_GAP := 0.44

static func _icon(kind: int) -> Texture2D:
	return Assets.texture("ui", str(ICON_FOR.get(kind, "unknown")))

# Which corner of the 20x20 sheet this floor is cut from. Seeded, so a floor
# always shows the same piece of paper, and two floors rarely show the same one.
func _mat_offset(fl: Dungeon.Floor_) -> Vector2i:
	var room_x: int = maxi(0, MAT_CELLS - fl.size.x)
	var room_y: int = maxi(0, MAT_CELLS - fl.size.y)
	return Vector2i(
		absi(Dungeon._mix(seed_of() + 401 + fl.index * 97)) % (room_x + 1),
		absi(Dungeon._mix(seed_of() + 907 + fl.index * 31)) % (room_y + 1))

func seed_of() -> int:
	var dg := _dungeon()
	return dg.seed_value if dg != null else 0

# A cell of the printed sheet, as an AtlasTexture. Both prints are cut with the
# same maths, which is the only reason a lit patch lines up with the shadowed
# sheet under it.
func _mat_slice(tex: Texture2D, off: Vector2i, cell: Vector2i, span := Vector2i.ONE) -> AtlasTexture:
	if tex == null: return null
	var at := AtlasTexture.new()
	at.atlas = tex
	at.region = Rect2(
		MAT_INSET + float(off.x + cell.x) * MAT_CELL,
		MAT_INSET + float(off.y + cell.y) * MAT_CELL,
		MAT_CELL * float(span.x), MAT_CELL * float(span.y))
	return at

# End of trip: you pick WHO studies what it taught you.
var pending_learn: Array = []
var _pending_survived := true

# Room icons are TextureRect NODES, not draw_texture_rect calls.
#
# Immediate-mode texture drawing on this Control renders every texture as a flat
# white fill — verified against a known-good RGB texture and a plain draw_rect in
# the same pass, so it is the draw call and not the assets. Every TextureRect in
# the project renders correctly, so the icons use those.
var _icon_layer: Control = null
# The paper. Sits BEFORE the map Control so the walls, badges and labels the map
# draws land on top of it — a CanvasItem paints its own _draw() first and its
# children after, so a mat added as a child of the map would cover the map.
var _mat_layer: Control = null

# Board geometry, recomputed whenever the panel changes size. Kept as fields
# because the draw pass, the icon pass and the click handler all need the same
# numbers and must not disagree about them.
var _cell := 0.0
var _origin := Vector2.ZERO

@onready var map_view: Control = $Root/Body/MapPanel/Map
@onready var room_shot: Control = $Root/Body/Side/RoomShot
@onready var info: RichTextLabel = $Root/Body/Side/Info
# The actions SCROLL. A fight room now lists its exits as well as the fight, and
# a fixed column silently pushed the Fight button off the bottom of the screen —
# the one control the room existed for.
@onready var choices: VBoxContainer = $Root/Body/Side/ChoiceScroll/Choices
@onready var title_label: Label = $Root/TopBar/Title

func _sess():
	var r := get_tree().root
	return r.get_node("Session") if r.has_node("Session") else null

func _dungeon() -> Dungeon:
	var s = _sess()
	return s.dungeon if s != null else null

func _ready() -> void:
	var s = _sess()
	if s == null:
		return
	if s.dungeon == null:
		s._load_dungeon()
	if not s.dungeon.in_delve():
		s.start_delve(0)
	map_view.draw.connect(_draw_board)
	map_view.gui_input.connect(_on_board_input)
	map_view.mouse_filter = Control.MOUSE_FILTER_STOP
	_icon_layer = Control.new()
	_icon_layer.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_icon_layer.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	map_view.add_child(_icon_layer)
	# The map Control has no size until the layout solver has run, so a rebuild
	# during _ready() puts every icon at the origin. Rebuilding on resize is
	# what actually places them the first time.
	map_view.resized.connect(_rebuild_icons)
	map_view.resized.connect(_rebuild_mat)
	_mat_layer = Control.new()
	_mat_layer.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_mat_layer.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	map_view.get_parent().add_child(_mat_layer)
	map_view.get_parent().move_child(_mat_layer, 0)
	# The project draws pixel art with NEAREST so it stays crisp at 1:1. Room
	# icons are the exception: they are 64px art shown smaller, and nearest
	# sampling at a downscale drops most of the pixels.
	#
	# LINEAR, not LINEAR_WITH_MIPMAPS: these textures are imported without
	# mipmaps, and asking for a level that was never generated samples a flat
	# average — every icon came out as a featureless grey disc.
	map_view.texture_filter = CanvasItem.TEXTURE_FILTER_LINEAR
	refresh()

# Lay the paper. The shadowed sheet goes down whole, in ONE node; the lit sheet
# goes down a cell at a time over the rooms you have seen.
#
# Textures are nodes rather than draw_texture_rect calls for the reason recorded
# above: immediate-mode texture drawing on this Control renders solid white.
func _rebuild_mat() -> void:
	if _mat_layer == null: return
	for ch in _mat_layer.get_children():
		ch.queue_free()
	var dg := _dungeon()
	if dg == null: return
	var fl: Dungeon.Floor_ = dg.current_floor()
	if fl == null: return
	_measure()
	if _cell <= 0.0: return
	var lit := RoomArt.mat(false)
	var dark := RoomArt.mat(true)
	var off := _mat_offset(fl)

	if dark != null:
		var base := TextureRect.new()
		base.texture = _mat_slice(dark, off, Vector2i.ZERO, fl.size)
		base.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		base.stretch_mode = TextureRect.STRETCH_SCALE
		base.mouse_filter = Control.MOUSE_FILTER_IGNORE
		base.texture_filter = CanvasItem.TEXTURE_FILTER_LINEAR
		base.position = _origin
		base.size = Vector2(_cell * float(fl.size.x), _cell * float(fl.size.y))
		_mat_layer.add_child(base)

	if lit == null: return
	for cell in fl.cells():
		var vis: int = dg.sight(cell)
		if vis == Dungeon.Sight.HIDDEN: continue
		var patch := TextureRect.new()
		patch.texture = _mat_slice(lit, off, cell)
		patch.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		patch.stretch_mode = TextureRect.STRETCH_SCALE
		patch.mouse_filter = Control.MOUSE_FILTER_IGNORE
		patch.texture_filter = CanvasItem.TEXTURE_FILTER_LINEAR
		var r := _cell_rect(cell)
		patch.position = r.position
		patch.size = r.size
		# A peeked room is glimpsed, not lit: the paper comes up part way so it
		# reads as somewhere you can see into rather than somewhere you have been.
		var a: float = 1.0 if vis == Dungeon.Sight.KNOWN else 0.45
		var tint := Color(1, 1, 1, a)
		if fl.room(cell).cleared:
			tint = Color(0.86, 1.0, 0.88, a)     # done with, and it shows
		patch.modulate = tint
		_mat_layer.add_child(patch)

# ------------------------------------------------------------------ geometry

func _measure() -> void:
	var dg := _dungeon()
	var fl: Dungeon.Floor_ = dg.current_floor() if dg != null else null
	if fl == null or map_view.size.x < 2.0: return
	var pad := 10.0
	var avail := map_view.size - Vector2(pad, pad) * 2.0
	_cell = minf(avail.x / float(fl.size.x), avail.y / float(fl.size.y))
	var total := Vector2(_cell * float(fl.size.x), _cell * float(fl.size.y))
	_origin = Vector2(pad, pad) + (avail - total) * 0.5

# A room is exactly one printed cell. No inset: the walls are drawn ON the cell
# boundary, and a doorway is a gap left in one, which is how a dungeon map reads.
func _cell_rect(cell: Vector2i) -> Rect2:
	return Rect2(_origin + Vector2(cell) * _cell, Vector2(_cell, _cell))

func _cell_centre(cell: Vector2i) -> Vector2:
	return _origin + (Vector2(cell) + Vector2(0.5, 0.5)) * _cell

func _cell_at(pos: Vector2) -> Vector2i:
	if _cell <= 0.0: return Vector2i(-1, -1)
	var rel := (pos - _origin) / _cell
	return Vector2i(int(floor(rel.x)), int(floor(rel.y)))

# ------------------------------------------------------------------- drawing
#
# The paper is already down (see _rebuild_mat). What is drawn here is everything
# that is NOT paper: the walls between rooms, the doorways left open in them,
# and the marks that say where you are and what is still waiting.

const COL_WALL := Color(0.07, 0.05, 0.04, 0.92)      # ink on parchment
const COL_HERE := Color(0.30, 0.88, 1.00)
const COL_REACH := Color(1.00, 0.82, 0.28)
const COL_FIGHT := Color(0.92, 0.30, 0.28)

func _draw_board() -> void:
	var dg := _dungeon()
	if dg == null: return
	var fl: Dungeon.Floor_ = dg.current_floor()
	if fl == null: return
	_measure()
	if _cell <= 0.0: return
	var c := map_view
	var reach: Array = dg.exits()

	# Walls first, so the highlights below sit on top of them.
	for cell in fl.cells():
		if dg.sight(cell) == Dungeon.Sight.HIDDEN: continue
		for d in Dungeon.DOOR_DIR:
			_draw_edge(c, cell, int(d), fl.room(cell).has_door(int(d)))

	for cell in fl.cells():
		var vis: int = dg.sight(cell)
		if vis == Dungeon.Sight.HIDDEN: continue
		var room: Dungeon.Room = fl.room(cell)
		var rect := _cell_rect(cell)

		# Where you stand and where you may step are marked INSIDE the room, as
		# a band on the floor. Outlining the cell instead would fight the walls
		# for the same pixels and lose.
		if cell == dg.at:
			_floor_mark(c, rect, COL_HERE, 0.55)
		elif cell in reach:
			_floor_mark(c, rect, COL_REACH, 0.34)

		# Kinds with no PNG yet get a drawn glyph rather than an empty square.
		# Dropping art/icons/node_entrance.png or node_stairs.png in replaces it.
		if _icon(room.kind) == null:
			_draw_glyph(c, room.kind, rect, vis == Dungeon.Sight.KNOWN)

		# A room still holding a fight says so, even when you can only peek in.
		if room.is_fight() and not room.cleared:
			var r := _cell * 0.055
			c.draw_circle(rect.position + Vector2(rect.size.x - r * 2.4, r * 2.4), r,
				COL_FIGHT)

	_draw_labels(c, dg, fl, reach)

# Stand-in art. Vector only — immediate-mode TEXTURE drawing is what is broken
# on this Control, not drawing itself.
func _draw_glyph(c: CanvasItem, kind: int, rect: Rect2, lit: bool) -> void:
	var mid := rect.get_center()
	var r := _cell * 0.17
	var col := Color(0.96, 0.90, 0.72, 1.0 if lit else 0.55)
	var w: float = maxf(2.0, _cell * 0.035)
	match kind:
		Dungeon.Kind.STAIRS:
			# Three steps going down and away.
			for i in 3:
				var y := mid.y - r + r * float(i) * 0.72
				var x := mid.x - r + r * float(i) * 0.55
				c.draw_line(Vector2(x, y), Vector2(x + r * 1.15, y), col, w)
				c.draw_line(Vector2(x + r * 1.15, y),
					Vector2(x + r * 1.15, y + r * 0.72), col, w)
		Dungeon.Kind.ENTRANCE:
			# An arch, and the way back out through it.
			c.draw_arc(mid + Vector2(0, r * 0.35), r, PI, TAU, 18, col, w)
			c.draw_line(mid + Vector2(-r, r * 0.35), mid + Vector2(-r, r), col, w)
			c.draw_line(mid + Vector2(r, r * 0.35), mid + Vector2(r, r), col, w)
			c.draw_line(mid + Vector2(0, r * 0.9), mid + Vector2(0, -r * 0.2), col, w)
			c.draw_line(mid + Vector2(0, -r * 0.2), mid + Vector2(-r * 0.4, r * 0.25), col, w)
			c.draw_line(mid + Vector2(0, -r * 0.2), mid + Vector2(r * 0.4, r * 0.25), col, w)
		_:
			c.draw_arc(mid, r, 0, TAU, 20, col, w)

# One side of a cell. A door leaves the middle of the edge open and keeps the
# stubs at either end, which is what makes an opening read as a doorway rather
# than as a missing wall.
func _draw_edge(c: CanvasItem, cell: Vector2i, d: int, has_door: bool) -> void:
	var r := _cell_rect(cell)
	var a: Vector2
	var b: Vector2
	match d:
		Dungeon.DOOR_N: a = r.position; b = r.position + Vector2(r.size.x, 0)
		Dungeon.DOOR_S: a = r.position + Vector2(0, r.size.y); b = r.position + r.size
		Dungeon.DOOR_W: a = r.position; b = r.position + Vector2(0, r.size.y)
		_:              a = r.position + Vector2(r.size.x, 0); b = r.position + r.size
	var w: float = maxf(2.0, _cell * WALL_W)
	if not has_door:
		c.draw_line(a, b, COL_WALL, w)
		return
	var stub: float = (1.0 - DOOR_GAP) * 0.5
	c.draw_line(a, a.lerp(b, stub), COL_WALL, w)
	c.draw_line(b.lerp(a, stub), b, COL_WALL, w)

# A soft band inside the room, brighter towards its edge.
func _floor_mark(c: CanvasItem, rect: Rect2, col: Color, strength: float) -> void:
	var inset: float = _cell * 0.10
	var band := Rect2(rect.position + Vector2(inset, inset),
		rect.size - Vector2(inset, inset) * 2.0)
	c.draw_rect(band, Color(col.r, col.g, col.b, strength * 0.22))
	c.draw_rect(band, Color(col.r, col.g, col.b, strength), false,
		maxf(2.0, _cell * 0.035))

# ONLY the rooms you can act on are named: where you stand, and where you may go
# next. Naming all of them is clutter competing with the one question this
# screen asks, and the icon already says what a room is.
func _draw_labels(c: CanvasItem, dg: Dungeon, fl: Dungeon.Floor_, reach: Array) -> void:
	var f := ThemeDB.fallback_font
	for cell in fl.cells():
		var here: bool = (cell == dg.at)
		if not here and not (cell in reach): continue
		var room: Dungeon.Room = fl.room(cell)
		var label: String = room.title()
		if room.cleared and not here: label = "cleared"
		var rect := _cell_rect(cell)
		var lp := Vector2(rect.position.x, rect.position.y + rect.size.y - _cell * 0.10)
		var fs := int(clampf(_cell * 0.16, 9.0, 15.0))
		# A full outline, not a drop shadow: parchment is light in places and
		# scorched in others, and a shadow only helps against one of those.
		for o in [Vector2(-1, 0), Vector2(1, 0), Vector2(0, -1), Vector2(0, 1)]:
			c.draw_string(f, lp + o, label, HORIZONTAL_ALIGNMENT_CENTER,
				rect.size.x, fs, Color(0, 0, 0, 0.92))
		c.draw_string(f, lp, label, HORIZONTAL_ALIGNMENT_CENTER, rect.size.x, fs,
			Color(1.0, 1.0, 1.0) if here else Color(1.0, 0.94, 0.78))

# One TextureRect per visible room, repositioned on every refresh. Cheap: a
# floor is a couple of dozen rooms and it rebuilds only when the board changes.
func _rebuild_icons() -> void:
	if _icon_layer == null: return
	if map_view.size.x < 2.0 or map_view.size.y < 2.0: return
	for ch in _icon_layer.get_children():
		ch.queue_free()
	var dg := _dungeon()
	if dg == null: return
	var fl: Dungeon.Floor_ = dg.current_floor()
	if fl == null: return
	_measure()
	if _cell <= 0.0: return
	var reach: Array = dg.exits()
	for cell in fl.cells():
		var vis: int = dg.sight(cell)
		if vis == Dungeon.Sight.HIDDEN: continue
		var room: Dungeon.Room = fl.room(cell)
		var tex := _icon(room.kind)
		if tex == null: continue
		var sz: float = _cell * 0.46
		var tr := TextureRect.new()
		tr.texture = tex
		tr.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		tr.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
		tr.mouse_filter = Control.MOUSE_FILTER_IGNORE
		tr.texture_filter = CanvasItem.TEXTURE_FILTER_LINEAR
		tr.size = Vector2(sz, sz)
		tr.position = _cell_centre(cell) - Vector2(sz, sz) * 0.5 - Vector2(0, _cell * 0.06)
		# Peeked rooms are dimmed rather than hidden: knowing a Boss is through
		# that door is exactly the information the one-room peek exists to give.
		var lit: bool = vis == Dungeon.Sight.KNOWN or cell == dg.at or cell in reach
		tr.modulate = Color(1, 1, 1, 1.0 if lit else 0.55)
		_icon_layer.add_child(tr)

# --------------------------------------------------------------------- input

func _on_board_input(event: InputEvent) -> void:
	if not (event is InputEventMouseButton): return
	var mb := event as InputEventMouseButton
	if not mb.pressed or mb.button_index != MOUSE_BUTTON_LEFT: return
	var dg := _dungeon()
	if dg == null or not dg.in_delve(): return
	var cell := _cell_at(mb.position)
	var fl: Dungeon.Floor_ = dg.current_floor()
	if fl == null or not fl.has(cell): return
	if dg.can_step_to(cell):
		_step(cell)
		return
	# A room further off is walkable only when everything between here and it
	# is already cleared. Walking back to a camp should cost clicks, not a
	# re-fight; walking INTO the unknown should not be one click either.
	if dg.sight(cell) != Dungeon.Sight.KNOWN: return
	var path: Array = dg.route(dg.at, cell)
	if path.is_empty(): return
	for step_cell in path:
		var r: Dungeon.Room = fl.room(step_cell)
		if r == null or not r.cleared: return
	for step_cell2 in path:
		if not dg.step_to(step_cell2): break
	_sess().save()
	refresh()

func _step(cell: Vector2i) -> void:
	var dg := _dungeon()
	if dg == null or not dg.step_to(cell): return
	_sfx("select")
	_sess().save()
	refresh()

func _sfx(k: String) -> void:
	if has_node("/root/Sfx"): get_node("/root/Sfx").play(k)

# ------------------------------------------------------------------- refresh

func refresh() -> void:
	var s = _sess()
	var dg: Dungeon = s.dungeon if s != null else null
	map_view.queue_redraw()
	_rebuild_mat.call_deferred()
	_rebuild_icons.call_deferred()
	for c in choices.get_children(): c.queue_free()
	info.clear()

	if dg == null or not dg.in_delve():
		title_label.text = "THE SILVERWOOD"
		room_shot.visible = false
		info.append_text("[b]You are back above ground.[/b]\n")
		_button("Return to Silverbrook", _leave)
		return

	var fl: Dungeon.Floor_ = dg.current_floor()
	var room: Dungeon.Room = dg.current_room()
	var pr: Dictionary = dg.floor_progress(dg.floor_i)
	title_label.text = "THE SILVERWOOD — %s" % dg.floor_name().to_upper()
	# What the room you are standing in actually looks like. Seeded off the
	# room's cell, so it is the same picture every time you come back to it.
	room_shot.visible = RoomArt.apply(room_shot, room.kind, dg.seed_value, dg.at)

	info.append_text("[b]%s[/b]\n" % room.title())
	info.append_text("[color=#8a8a92]%s — %d of %d fights cleared. Rewards x%.2f down here.[/color]\n\n"
		% [dg.floor_name(), int(pr.get("cleared", 0)), int(pr.get("fights", 0)),
			dg.reward_multiplier()])
	_write_objective(s)
	info.append_text("Haul this trip — carried out only if you make it back:\n")
	for k in ["exp", "gold", "essence", "ore", "hide"]:
		var v: int = int(s.run_haul.get(k, 0))
		if v > 0: info.append_text("  %s: %d\n" % [k.to_upper(), v])
	info.append_text("\nSafe Pocket: %d slots   CYA items: %d\n" % [
		int(s.data.profile.get("safe_pocket", 0)), int(s.data.profile.get("cya", 0))])
	info.append_text("[i]Die without a CYA item and only what fits the pocket survives. EXP always survives.[/i]\n")

	if not pending_learn.is_empty():
		_learning_choices(s)
		return

	# What this room still wants from you.
	if not room.cleared:
		match room.kind:
			Dungeon.Kind.ENEMY, Dungeon.Kind.ELITE, Dungeon.Kind.BOSS:
				_button("Fight  %s" % ("(BOSS)" if room.kind == Dungeon.Kind.BOSS else ""),
					_enter_battle)
			Dungeon.Kind.REST:
				_button("Make camp — heal the party", _do_rest)
				_button("Upgrade a move instead", _do_upgrade)
			Dungeon.Kind.TREASURE:
				_button("Open the cache", _do_treasure)
			Dungeon.Kind.MERCHANT:
				_button("Trade (buy a CYA item — 60g)", _do_merchant)
			Dungeon.Kind.UNKNOWN:
				_button("Investigate", _do_unknown)

	if room.is_fight() and not room.cleared:
		info.append_text("
[color=#ffd76a]It has not seen you yet — but it is "
			+ "between you and everything past this room. Fight it, or go back the "
			+ "way you came.[/color]
")
	var ways: Array = dg.exits()
	for cell in ways:
			var nxt: Dungeon.Room = fl.room(cell)
			var name_txt: String = nxt.title() if dg.sight(cell) != Dungeon.Sight.HIDDEN else "?"
			if nxt.cleared: name_txt += " (cleared)"
			_button("Go %s — %s" % [_compass(cell - dg.at), name_txt],
				_step.bind(cell), _icon(nxt.kind))

	if dg.can_descend():
		_button("↓ Take the stairs down to %s" % dg.floor_name(dg.floor_i + 1), _descend)
	elif room.kind == Dungeon.Kind.STAIRS:
		info.append_text("\n[color=#8a8a92]The way down is barred until the floor's boss is dealt with.[/color]\n")

	# Retreating is the ONLY way to bank a haul. It is why camps matter, and
	# why pushing one room further is a real decision rather than a free one.
	if dg.can_retreat():
		if room.kind == Dungeon.Kind.REST:
			info.append_text("
[color=#9fe6a0]This camp is yours now — it counts as an entrance, so later trips can start here.[/color]
")
		_button("Leave the dungeon — bank the haul", _retreat)
	else:
		info.append_text("\n[color=#8a8a92]No way out from here. Reach a camp or the entrance to walk out with what you are carrying.[/color]\n")

static func _compass(delta: Vector2i) -> String:
	if delta == Vector2i(0, -1): return "north"
	if delta == Vector2i(0, 1): return "south"
	if delta == Vector2i(1, 0): return "east"
	if delta == Vector2i(-1, 0): return "west"
	return "on"

func _button(txt: String, cb: Callable, icon: Texture2D = null) -> Button:
	var b := Button.new()
	b.text = txt
	b.custom_minimum_size = Vector2(300, 36)
	if icon != null:
		b.icon = icon
		b.expand_icon = true
		b.add_theme_constant_override("icon_max_width", 24)
	b.pressed.connect(cb)
	choices.add_child(b)
	return b

# --------------------------------------------------------------- room actions

func _enter_battle() -> void:
	get_tree().change_scene_to_file("res://scenes/Battle.tscn")

func _do_rest() -> void:
	# A Gauntlet contract is void the moment you stop, so the trip has to know.
	var s = _sess()
	if s != null: s.record_rest()
	for u in Content.roster():
		if s.party_hp.has(u.id):
			s.party_hp[u.id] = u.max_hp
	s.dungeon.clear_current()
	s.save()
	info.append_text("\nThe party makes camp. HP restored.\n")
	refresh()

# Upgrading levels an EQUIPPED MOVE, which is what build_deck() actually reads.
# It used to walk a card collection nothing looked up, so the room did nothing.
func _do_upgrade() -> void:
	var s = _sess()
	var best: Dictionary = {}
	for o in s.upgradeable_moves():
		if int(o.level) >= Card.MAX_LEVEL: continue
		if best.is_empty() or int(o.level) < int(best.level):
			best = o
	if best.is_empty():
		info.append_text("\nEvery equipped move is already at its ceiling.\n")
	elif s.upgrade_card(str(best.move)):
		var u := Content.unit_by_id(str(best.unit))
		var tpl := Moves.template(Moves.class_of(str(best.unit)), str(best.move))
		info.append_text("\nUpgraded [b]%s[/b] to level %d for %s.\n"
			% [str(tpl.get("name", best.move)), int(best.level) + 1,
				(u.display_name if u != null else str(best.unit))])
	s.dungeon.clear_current()
	s.save()
	refresh()

func _do_treasure() -> void:
	var s = _sess()
	s.run_haul["essence"] = int(s.run_haul.get("essence", 0)) + 2
	s.run_haul["gold"] = int(s.run_haul.get("gold", 0)) + 35
	# a temporary RUN card, discarded when the trip ends
	var pool := Content.synergy_cards()
	if not pool.is_empty():
		var c: Card = pool[randi() % pool.size()]
		s.run_deck_extra.append(c)
		info.append_text("\nFound a run card: [b]%s[/b] (this trip only)\n" % c.display_name)
	s.dungeon.clear_current()
	s.save()
	refresh()

func _do_merchant() -> void:
	var s = _sess()
	if s.spend("gold", 60):
		s.data.profile["cya"] = int(s.data.profile.get("cya", 0)) + 1
		info.append_text("\nBought a CYA charm. Your haul is insured once.\n")
	else:
		info.append_text("\nNot enough gold.\n")
	s.dungeon.clear_current()
	s.save()
	refresh()

func _do_unknown() -> void:
	var s = _sess()
	var roll := Dice.roll_d20()
	var outcome := Dice.tier_for(roll.total)
	info.append_text("\n[b]Dice:[/b] %s → %s\n" % [roll.text, Dice.TIER_NAMES[outcome]])
	if outcome >= Dice.Tier.SUCCESS:
		s.run_haul["gold"] = int(s.run_haul.get("gold", 0)) + 25 * (outcome)
		info.append_text("A cached supply crate. +%d gold.\n" % (25 * outcome))
	else:
		info.append_text("An ambush — the party is worn down.\n")
		for k in s.party_hp.keys():
			s.party_hp[k] = maxi(1, int(s.party_hp[k]) - 15)
	s.dungeon.clear_current()
	s.save()
	refresh()

func _descend() -> void:
	var s = _sess()
	# The haul is NOT banked here — going deeper is the same trip continuing,
	# and everything you are carrying stays at risk.
	if s.dungeon.descend():
		_sfx("victory")
		s.save()
		refresh()

func _retreat() -> void:
	_offer_learning(true)

func _leave() -> void:
	get_tree().change_scene_to_file("res://scenes/Main.tscn")

func _on_back_pressed() -> void:
	# Walking out of the SCENE is not walking out of the dungeon: the trip is
	# still live and the haul is still at risk. Only _retreat() banks it.
	_leave()

# ------------------------------------------------------- end of trip

func _offer_learning(survived: bool) -> void:
	var s = _sess()
	pending_learn = []
	for uid in s.data.get("roster", []):
		var who := str(uid)
		if s.known_moves(who).size() < Moves.pool_for(Moves.class_of(who)).size():
			pending_learn.append(who)
	if pending_learn.is_empty():
		_finish(survived)
	else:
		_pending_survived = survived
		refresh()

func _learning_choices(s) -> void:
	info.append_text("\n[b]WHAT THE TRIP TAUGHT — pick who studies it[/b]\n")
	info.append_text("[i]You choose who. Which move it turns out to be is their class pool's call.[/i]\n")
	for uid in pending_learn:
		var who := str(uid)
		var u := Content.unit_by_id(who)
		var known: int = s.known_moves(who).size()
		var pool: int = Moves.pool_for(Moves.class_of(who)).size()
		var b := _button("%s  —  %s, knows %d/%d"
			% [(u.display_name if u != null else who),
				Moves.CLASS_NAMES[Moves.class_of(who)], known, pool],
			_take_learn.bind(who))
		if known >= pool and b != null:
			b.disabled = true

func _take_learn(uid: String) -> void:
	var s = _sess()
	var res: Dictionary = s.find_move(uid)
	pending_learn = []
	if res.get("ok", false):
		_sfx("victory")
	_finish(_pending_survived)
	if res.get("ok", false):
		var u := Content.unit_by_id(uid)
		info.append_text("\n[color=#9fe6a0]%s %s: [b]%s[/b] (%s).[/color]\n"
			% [(u.display_name if u != null else uid), str(res.label),
				str(res.name), str(res.rarity)])
	else:
		info.append_text("\n%s\n" % str(res.get("reason", "")))

func _finish(survived: bool) -> void:
	var s = _sess()
	var report: Dictionary = s.end_delve(survived)
	info.clear()
	info.append_text("[b]Back above ground.[/b]\n")
	info.append_text("Kept: %s\n" % str(report.kept))
	if not report.lost.is_empty():
		info.append_text("[color=#ff8888]Lost: %s[/color]\n" % str(report.lost))
	if report.used_cya:
		info.append_text("A CYA charm burned — haul protected.\n")
	refresh()

# Harness seam, same reason ModeStub.select_def() and BattleScene.demo_action()
# exist: synthetic input cannot reach the game window, so the only way to SEE a
# part-explored board in a screenshot is to ask the scene to walk one. Prefers
# rooms it has not been in, or it just paces between the same two.
# Not reachable from any button.
func demo_walk(arg: String = "6") -> int:
	var dg := _dungeon()
	if dg == null or not dg.in_delve(): return 0
	var moved := 0
	for _i in maxi(1, int(arg)):
		dg.clear_current()
		var ways: Array = dg.exits()
		if ways.is_empty(): break
		var pick: Vector2i = ways[0]
		for w in ways:
			if not dg.current_floor().room(w).visited:
				pick = w
				break
		if not dg.step_to(pick): break
		moved += 1
	refresh()
	return moved

# Harness seam: walk to the nearest UNFOUGHT room and stop in the doorway, so a
# screenshot of Battle.tscn afterwards is a real dungeon fight rather than the
# standalone fallback encounter.
func demo_to_fight(_arg: String = "") -> String:
	var dg := _dungeon()
	if dg == null or not dg.in_delve(): return "no delve"
	var fl: Dungeon.Floor_ = dg.current_floor()
	var best := Vector2i(-1, -1)
	var best_d := 99999
	var dist: Dictionary = dg.reachable_from_entrance(dg.floor_i)
	for cell in fl.cells():
		var r: Dungeon.Room = fl.room(cell)
		if not r.is_fight() or r.cleared: continue
		var d: int = int(dist.get(cell, 99999))
		if d < best_d: best_d = d; best = cell
	if best == Vector2i(-1, -1): return "no unfought room"
	for step_cell in dg.route(dg.at, best):
		dg.clear_current()          # walk THROUGH what is on the way
		if not dg.step_to(step_cell): return "blocked"
	_sess().save()
	refresh()
	return "standing in %s at %s" % [dg.current_room().title(), str(dg.at)]

# The contract, and how far along it is. Shown in every room, because a cull you
# can bank early is a decision you make DURING the trip, not at the end.
func _write_objective(s) -> void:
	if s == null or s.mission == null: return
	var p: Dictionary = s.mission_progress()
	if p.is_empty(): return
	info.append_text("[b]Contract:[/b] %s\n" % str(p.get("title", "")))
	if bool(p.get("failed", false)):
		info.append_text("  [color=#ff9c9c]%s[/color]\n" % str(p.get("text", "")))
	elif bool(p.get("done", false)):
		info.append_text("  [color=#9fe6a0]Done — %s. %dg waiting when you walk out.[/color]\n"
			% [str(p.get("text", "")), int(p.get("reward", 0))])
	else:
		info.append_text("  [color=#ffd76a]%s[/color]\n" % str(p.get("text", "")))
	info.append_text("\n")
