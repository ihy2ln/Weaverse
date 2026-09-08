# A card, drawn as a COMPOSITED RUNTIME TEMPLATE — one frame + one art window +
# generated badges. Per the GDD: never hundreds of hand-painted card images.
#
# THE COMPACT FACE is almost all picture. Everything else is a corner:
#
#     name ........................ element
#              ( the move,
#                playing )
#     AP ............................... EP
#
# AP is the blue badge bottom-LEFT, EP the green badge bottom-RIGHT. Costs read
# as the two bottom corners so a hand fanned to overlapping thirds still shows
# both — which is the only part of a card you have to see while choosing one.
#
# THE MOVE PLAYS AS VIDEO. A VideoStreamPlayer is a child of this Control, so it
# inherits the card's transform: it moves, scales and rotates with the card for
# free, because it is parented to it rather than synchronised with it. Godot 4
# decodes Ogg Theora only, so the file is .ogv; a card with no clip falls back to
# its still, and both are pure file drops:
#
# Everything resolves through scenes/Assets.gd, so a picture the PLAYER added
# wins over the one that shipped — core/asset_kinds.gd holds the two trees:
#
#   character/<card_id>     this exact card, still or moving
#   character/<owner_id>    any card that character owns
#   character/<summoner>    their cards, and anything unowned
#
# Decoding is not free, so a clip only plays on a card big enough to read (see
# VIDEO_MIN_SCALE). Small ones show the still. That caps live decoders at a hand
# rather than a hand plus every basic attack.
#
# INPUT is raised as signals rather than handled here: this draws a card, it does
# not know what playing one means. That is BattleScene's business.
extends Control
class_name CardView

# THE CARD IS THE PHOTO'S SHAPE.
#
# The portrait art is 416x740 — 0.562 wide to tall — and the card used to be
# 0.714, so a full-bleed picture could not both fill it and stay whole. Matching
# the art exactly means the figure fills the card edge to edge with nothing
# cropped and no bars, and it comes out taller on screen for the same width.
const W := 230.0
const H := 409.0

# Below this the card is thumbnail-sized; a video there is spent CPU on
# something nobody can make out.
const VIDEO_MIN_SCALE := 0.52
# How long a press has to sit still before it counts as "let me read this".
const HOLD_SECONDS := 0.35
# A press that wanders further than this was a drag, not a hold.
const HOLD_SLOP := 12.0

signal press_started(card: Card, at: Vector2)
signal press_moved(card: Card, at: Vector2)
signal press_ended(card: Card, at: Vector2, velocity: Vector2)
signal hold_begun(card: Card)

var card: Card
var owner_name: String = ""
# A CARD ON THE BOARD IS THE SAME OBJECT AS A CARD IN THE HAND. Only what it
# prints over the picture differs — a unit shows who it is and how it is doing;
# a hand card shows what it costs and what it does. Everything underneath (the
# art lookup, the still/clip policy, the scale ladder, the hover pop) is the
# same code, which is the whole reason for doing it this way rather than a
# second widget that would drift.
var unit: Unit = null
var unit_enemy := false
# THE BOARD'S DRAG IS NOT THIS CONTROL'S. A unit card is pressed and dragged by
# BattleScene._on_unit_input; leaving the hand's press/hold/flick handler live
# on top of that would give one control two gesture owners, which is a bug
# factory rather than a feature.
var gestures_enabled := true
var playable: bool = true
var selected: bool = false
# Draws the back — the full move description — instead of the face.
var flipped: bool = false
# 1.0 = face, 0.0 = edge-on. The flip is scale.x driven through zero with the
# face swapped at the midpoint, which needs no shader and no 3D node.
var flip_turn := 1.0

# Which of this card's bonded allies are on the field right now. A bond the
# player cannot see is a bond they will never build a party around, so it is
# drawn on the face rather than buried in a tooltip.
var bond_present: Dictionary = {}      # unit id -> true

var view_scale := 1.0
# Set on the lifted copy that follows the cursor. It raises no input of its own
# and always plays its video, however small it happens to be.
var is_overlay := false

var _art: TextureRect = null
var _video: VideoStreamPlayer = null
var _pressing := false
var _press_at := Vector2.ZERO
var _press_time := 0.0
var _held := false
var _trail: Array = []                 # [pos, msec] samples, for flick velocity

const ELEMENT_COLORS := {
	ElementChart.E.NEUTRAL:   Color(0.55, 0.57, 0.62),
	ElementChart.E.FIRE:      Color(0.85, 0.35, 0.20),
	ElementChart.E.WATER:     Color(0.25, 0.55, 0.85),
	ElementChart.E.WIND:      Color(0.35, 0.78, 0.62),
	ElementChart.E.EARTH:     Color(0.62, 0.48, 0.28),
	ElementChart.E.LIGHTNING: Color(0.88, 0.78, 0.28),
	ElementChart.E.LIGHT:     Color(0.92, 0.88, 0.70),
	ElementChart.E.DARK:      Color(0.55, 0.32, 0.70),
}

const TYPE_LABEL := {
	Card.Type.SUMMON: "SUMMON", Card.Type.ATTACK: "ATTACK",
	Card.Type.SKILL: "SKILL", Card.Type.ULTIMATE: "ULTIMATE",
	Card.Type.SUMMONER: "SUMMONER  CP", Card.Type.TRANSFER: "TRANSFER",
	Card.Type.RUN: "RUN", Card.Type.CURSE: "CURSE",
}

# The two costs, in the colours asked for. AP is what an action costs you; EP is
# what the power costs. They are different currencies and now they look it.
const COL_AP := Color(0.34, 0.62, 0.98)
const COL_EP := Color(0.40, 0.85, 0.47)
const COL_UNIT_ALLY := Color(0.36, 0.70, 0.95)
const COL_UNIT_ENEMY := Color(0.90, 0.36, 0.34)
const COL_HP := Color(0.42, 0.82, 0.45)
const COL_HP_LOW := Color(0.88, 0.34, 0.30)

func setup(c: Card, owner_display: String) -> void:
	card = c
	owner_name = owner_display
	custom_minimum_size = Vector2(W, H) * view_scale
	pivot_offset = Vector2(W, H) * view_scale * 0.5
	mouse_filter = Control.MOUSE_FILTER_IGNORE if is_overlay else Control.MOUSE_FILTER_STOP
	tooltip_text = "%s\n%s" % [c.display_name, c.description]
	if not c.partners.is_empty():
		tooltip_text += "\n%s  (+%d%% each when they are standing)" % [
			c.bond_text(), int(c.partner_scale * 100.0)]
	_ensure_media()
	queue_redraw()

# A UNIT, drawn as the card it is. No Card object is invented for it: a unit is
# not a card you can play, and giving it a fake one would put it in reach of
# every code path that thinks a CardView means something spendable.
func setup_unit(u: Unit, is_enemy: bool) -> void:
	unit = u
	unit_enemy = is_enemy
	card = null
	gestures_enabled = false
	custom_minimum_size = Vector2(W, H) * view_scale
	pivot_offset = Vector2(W, H) * view_scale * 0.5
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	tooltip_text = "%s — %s\n%d/%d HP" % [u.display_name,
		Roles.name_of(u.role), u.hp, u.max_hp]
	if u.has_taunt():
		tooltip_text += "\nTaunting: must be dealt with before anyone else on this side."
	_ensure_media()
	queue_redraw()

# Cards NEVER overlap by accident: the container reserves exactly
# custom_minimum_size, and every coordinate below is derived from `view_scale`.
# Control.scale is used ONLY for the lift and the flip, both of which are
# supposed to spill over their neighbours.
func set_view_scale(f: float) -> void:
	view_scale = clampf(f, 0.24, 1.6)
	custom_minimum_size = Vector2(W, H) * view_scale
	pivot_offset = Vector2(W, H) * view_scale * 0.5
	_place_media()
	queue_redraw()

func s(v: float) -> float:
	return v * view_scale

func set_state(is_playable: bool, is_selected: bool) -> void:
	playable = is_playable
	selected = is_selected
	_tint_media()
	queue_redraw()

func set_bonds(present: Dictionary) -> void:
	bond_present = present
	queue_redraw()

func bond_live() -> int:
	var n := 0
	for pid in card.partners:
		if bond_present.get(str(pid), false): n += 1
	return n

# ------------------------------------------------------------------- media

# WHICH CARDS MOVE.
#
# A clip plays only where it can actually be watched and only where it earns
# the decoder: the card in play, the card you have picked up, the one you are
# pointing at. Everything else holds the still.
#
# It used to be decided by SIZE alone — any card above a scale threshold got a
# video player. With a packed hand that is every card at once, which is a dozen
# decoders running for pictures nobody is looking at.
#
# `media_live` is set by whoever owns the card: the battle screen turns it on
# for the board and for the card under the pointer, and off for the rest.
var media_live := false

# HOVER POPS THE CARD, without moving it in the layout.
#
# Control.scale is a DRAW-time transform: the card grows over its neighbours and
# the row underneath it never shifts. Growing it by changing view_scale instead
# would resize the node, re-sort the container and shove every other card along
# — which is both ugly and a good way to lose a live gesture.
#
# The pivot is the BOTTOM CENTRE, so it grows upward out of the hand rather than
# down through the floor.
func set_pop(f: float) -> void:
	pivot_offset = Vector2(custom_minimum_size.x * 0.5, custom_minimum_size.y)
	scale = Vector2(f, f)

func set_media_live(on: bool) -> void:
	if media_live == on: return
	media_live = on
	_ensure_media()

func _wants_video() -> bool:
	# Still too small to make out is still not worth a decoder, whatever the
	# caller says.
	return (media_live or is_overlay) and (is_overlay or view_scale >= VIDEO_MIN_SCALE)

func _clip_path() -> String:
	if unit != null:
		var slot: String = unit.species if unit_enemy else unit.id
		for k in (["monster"] if unit_enemy else ["portrait", "character"]):
			var up := Assets.find(k, slot)
			if up != "" and Assets.is_video(up): return up
		return ""
	if card == null or card.id == "": return ""
	for kind in ["portrait", "character"]:
		var p := Assets.find(kind, card.id)
		if p != "" and Assets.is_video(p): return p
	return ""

# The last fallback is WHOEVER THE SUMMONER IS, looked up rather than named.
var summoner_id := Content.DEFAULT_SUMMONER

# PORTRAIT FIRST, character second.
#
# The two folders hold different SHAPES of the same people, and a card wants the
# tall one. art/portrait is 416x740 — near enough this card's own proportions to
# sit in it whole. art/character is 420x294, a wide banner meant for a scene,
# and asking a card for one of those was why every card showed the middle slice
# of a landscape photo with both ends missing.
#
# Within each folder: this exact card, then anything its owner owns, then the
# Summoner. Every step goes through Assets, so a picture the player dropped in
# beats the bundled one at the same step.
func _still_path() -> String:
	if unit != null:
		# A monster is looked up by SPECIES — several of them share one picture —
		# and one of yours by id, which is also how the board tiles found it.
		var kinds: Array = ["monster"] if unit_enemy else ["portrait", "character"]
		for k in kinds:
			var slot: String = unit.species if unit_enemy else unit.id
			var up := Assets.find(k, slot)
			if up != "" and not Assets.is_video(up): return up
		return ""
	if card == null: return ""
	for kind in ["portrait", "character"]:
		for slot in [card.id, card.owner_id, summoner_id]:
			if str(slot) == "": continue
			var p := Assets.find(kind, str(slot))
			if p != "" and not Assets.is_video(p): return p
	return ""

func _ensure_media() -> void:
	var clip := _clip_path()
	if clip != "" and _wants_video():
		if _video == null:
			_video = VideoStreamPlayer.new()
			_video.expand = true
			_video.mouse_filter = Control.MOUSE_FILTER_IGNORE
			# Not every Godot build exposes `loop`; without it the clip is
			# restarted from `finished` instead. Either way it repeats.
			if "loop" in _video: _video.set("loop", true)
			else: _video.finished.connect(func(): if _video != null: _video.play())
			# The picture fills the WHOLE card and the name, element and cost
			# badges are drawn on top of it. A child CanvasItem paints after its
			# parent, so without this the clip covers every one of them.
			_video.show_behind_parent = true
			add_child(_video)
		# A clip the PLAYER added has no import sidecar, so it is opened as a
		# file rather than loaded as a resource. Bundled ones still load().
		if clip.begins_with("res://"):
			_video.stream = load(clip)
		else:
			var vs := VideoStreamTheora.new()
			vs.file = clip
			_video.stream = vs
		if "paused" in _video: _video.set("paused", false)
		if not _video.is_playing(): _video.play()
		if _art != null:
			_art.visible = false
	else:
		if _video != null:
			# PAUSED, not stopped: the still underneath is what shows, and the
			# clip picks up where it left off when this card is looked at again.
			if "paused" in _video: _video.set("paused", true)
			else: _video.stop()
			_video.visible = false
		var still := _still_path()
		if still != "":
			if _art == null:
				_art = TextureRect.new()
				_art.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
				# CENTERED, NOT COVERED. Covered fills the card by throwing away
				# whatever does not fit, and no source art is exactly this
				# card's proportions — so it always cut something off. Fitting
				# shows the WHOLE picture; the card's own dark ground shows at
				# the edges, which reads as a frame rather than as a mistake.
				_art.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
				_art.mouse_filter = Control.MOUSE_FILTER_IGNORE
				_art.show_behind_parent = true      # see the note on _video above
				add_child(_art)
			_art.visible = true
			_art.texture = Assets.texture_at(still)
	_place_media()
	_tint_media()

# The art window is the WHOLE card. Name, element and the two cost badges are
# drawn over it, which is what makes the compact face mostly picture.
func _art_rect() -> Rect2:
	return Rect2(s(3), s(3), s(W - 6), s(H - 6))

func _place_media() -> void:
	var r := _art_rect()
	for n in [_art, _video]:
		if n == null: continue
		n.position = r.position
		n.size = r.size
	# A flipped card is showing its back; the picture is on the other side.
	if _video != null: _video.visible = not flipped and _video.stream != null
	if _art != null: _art.visible = not flipped and _art.texture != null

# Whether this card has a CLIP running rather than a still. The stage holds a
# card with moving art longer than one without: a still is read in half a second
# and a clip that is cut off after that may as well not be there.
func has_clip() -> bool:
	return _video != null and _video.stream != null and _video.visible

# Whether anything is actually showing behind this card right now.
func has_media() -> bool:
	if _video != null and _video.visible and _video.stream != null: return true
	if _art != null and _art.visible and _art.texture != null: return true
	return false

func _tint_media() -> void:
	var c := Color(1, 1, 1) if playable else Color(0.45, 0.45, 0.5)
	if _art != null: _art.modulate = c
	if _video != null: _video.modulate = c

# -------------------------------------------------------------------- flip

func set_flipped(v: bool) -> void:
	if flipped == v: return
	flipped = v
	_place_media()
	queue_redraw()

# Turn the card through edge-on and swap the face at the midpoint. Driven by the
# caller each frame so the whole gesture stays in one place.
func set_turn(t: float) -> void:
	flip_turn = clampf(t, -1.0, 1.0)
	scale = Vector2(maxf(absf(flip_turn), 0.02), 1.0)
	queue_redraw()

# ------------------------------------------------------------------- input

func _gui_input(event: InputEvent) -> void:
	if is_overlay or card == null or not gestures_enabled: return
	if event is InputEventMouseButton:
		var mb := event as InputEventMouseButton
		if mb.button_index != MOUSE_BUTTON_LEFT: return
		if mb.pressed:
			_pressing = true
			_held = false
			_press_at = mb.global_position
			_press_time = 0.0
			_trail = [[mb.global_position, Time.get_ticks_msec()]]
			press_started.emit(card, mb.global_position)
		elif _pressing:
			_pressing = false
			press_ended.emit(card, mb.global_position, _velocity())
	elif event is InputEventMouseMotion and _pressing:
		var mm := event as InputEventMouseMotion
		_sample(mm.global_position)
		press_moved.emit(card, mm.global_position)

func _process(delta: float) -> void:
	if not _pressing or _held or is_overlay or not gestures_enabled: return
	_press_time += delta
	# A press that has not wandered is someone asking to READ the card, not to
	# move it. Wandering first means they are dragging, and a drag must never
	# turn into a flip halfway.
	if _press_time >= HOLD_SECONDS and _dist_moved() < HOLD_SLOP:
		_held = true
		hold_begun.emit(card)

func _dist_moved() -> float:
	if _trail.is_empty(): return 0.0
	return (Vector2(_trail[_trail.size() - 1][0]) - _press_at).length()

func _sample(at: Vector2) -> void:
	_trail.append([at, Time.get_ticks_msec()])
	# Keep only the tail — a flick is the last instant of the gesture, not its
	# average. Averaging over the whole drag reads every throw as a slow one.
	while _trail.size() > 2 and int(Time.get_ticks_msec()) - int(_trail[0][1]) > 90:
		_trail.remove_at(0)

func _velocity() -> Vector2:
	if _trail.size() < 2: return Vector2.ZERO
	var first = _trail[0]
	var last = _trail[_trail.size() - 1]
	var dt: float = float(int(last[1]) - int(first[1])) / 1000.0
	if dt <= 0.0001: return Vector2.ZERO
	return (Vector2(last[0]) - Vector2(first[0])) / dt

# ----------------------------------------------------------------- drawing

func _accent() -> Color:
	if card.type == Card.Type.SUMMONER: return Color(0.85, 0.72, 0.35)
	if card.type == Card.Type.ULTIMATE: return Color(0.95, 0.55, 0.85)
	if card.type == Card.Type.TRANSFER: return Color(0.45, 0.85, 0.95)
	return ELEMENT_COLORS.get(card.element, Color(0.6, 0.6, 0.6))

func _draw() -> void:
	if unit != null:
		_draw_unit()
		return
	if card == null: return
	if flipped:
		_draw_back()
		return
	_draw_face()

# WHAT A UNIT ON THE BOARD SAYS. Who it is, how it is doing, and whether it is
# the one holding the line — three things, because everything else about it is
# one hover away on the card that opens over the board.
func _draw_unit() -> void:
	var cw := s(W)
	var ch := s(H)
	var f := ThemeDB.fallback_font
	var alive: bool = unit.is_alive()
	var tint: Color = COL_UNIT_ENEMY if unit_enemy else COL_UNIT_ALLY
	var dim: float = 1.0 if alive else 0.45

	if not has_media():
		draw_rect(Rect2(0, 0, cw, ch), Color(0.11, 0.12, 0.16).lerp(tint, 0.12))

	# The plate the name and the bar sit on, so both stay readable over whatever
	# the artwork happens to be doing down there.
	_scrim(Rect2(0, ch - s(96), cw, s(96)), false)

	var fs_name := int(maxf(10.0, s(20)))
	draw_string(f, Vector2(s(10), ch - s(58)), unit.display_name.split(" ")[0],
		HORIZONTAL_ALIGNMENT_LEFT, cw - s(20), fs_name, Color(1, 1, 1, dim))
	draw_string(f, Vector2(s(10), ch - s(40)), Roles.name_of(unit.role).to_upper(),
		HORIZONTAL_ALIGNMENT_LEFT, cw - s(20), int(maxf(8.0, s(13))),
		Roles.colour_of(unit.role) * Color(1, 1, 1, dim))

	# HEALTH, as a bar rather than a number: the number is in the readout and on
	# the card, and what the board wants is "how much is left" at a glance.
	var bar := Rect2(s(10), ch - s(28), cw - s(20), s(9))
	draw_rect(bar, Color(0.06, 0.07, 0.10, 0.85))
	var frac: float = 0.0 if unit.max_hp <= 0 else clampf(float(unit.hp) / float(unit.max_hp), 0.0, 1.0)
	if frac > 0.0:
		draw_rect(Rect2(bar.position, Vector2(bar.size.x * frac, bar.size.y)),
			COL_HP_LOW.lerp(COL_HP, clampf(frac * 2.0, 0.0, 1.0)))
	draw_string(f, Vector2(s(10), ch - s(11)), "%d" % unit.hp,
		HORIZONTAL_ALIGNMENT_LEFT, cw - s(20), int(maxf(8.0, s(13))),
		Color(0.86, 0.90, 0.97, dim))

	# ACTIONS LEFT, for your side only — theirs spend on their own turn and a
	# number that ticked down while you watched would read as a threat rather
	# than as information.
	if not unit_enemy and alive:
		draw_string(f, Vector2(0, ch - s(11)), "AP%d  EP%d" % [unit.ap, unit.ep],
			HORIZONTAL_ALIGNMENT_RIGHT, cw - s(10), int(maxf(8.0, s(13))),
			Color(0.80, 0.85, 0.95, dim))

	# THE WALL, marked. This is the one rule the board still enforces, so it is
	# the one badge a unit carries.
	if unit.has_taunt() and alive:
		_taunt_badge(cw, tint)

	# The frame last, over everything, in the side's colour.
	var w: float = maxf(2.0, s(4))
	draw_rect(Rect2(w * 0.5, w * 0.5, cw - w, ch - w),
		tint * Color(1, 1, 1, dim), false, w)

func _taunt_badge(cw: float, tint: Color) -> void:
	var r := s(15)
	var mid := Vector2(cw - r - s(8), r + s(8))
	draw_circle(mid, r, Color(0.05, 0.06, 0.09, 0.85))
	draw_arc(mid, r, 0.0, TAU, 24, tint, maxf(1.5, s(2.5)))
	# A shield, drawn rather than lettered so it reads at any size.
	var h := r * 0.72
	draw_colored_polygon(PackedVector2Array([
		mid + Vector2(0, -h), mid + Vector2(h * 0.8, -h * 0.45),
		mid + Vector2(h * 0.8, h * 0.25), mid + Vector2(0, h),
		mid + Vector2(-h * 0.8, h * 0.25), mid + Vector2(-h * 0.8, -h * 0.45)]),
		tint * Color(1, 1, 1, 0.9))

func _draw_face() -> void:
	var accent := _accent()
	var dim := 1.0 if playable else 0.5
	var cw := s(W)
	var ch := s(H)
	var f := ThemeDB.fallback_font

	# ONLY when there is no picture. The art sits behind this Control (see
	# show_behind_parent), so painting a solid backing unconditionally hides it.
	if not has_media():
		draw_rect(Rect2(0, 0, cw, ch), Color(0.11, 0.12, 0.16).lerp(accent, 0.10))

	# Scrims top and bottom. The corners carry text over arbitrary artwork, and
	# without these the name is unreadable on any light frame of the clip.
	_scrim(Rect2(0, 0, cw, s(50)), true)
	_scrim(Rect2(0, ch - s(84), cw, s(84)), false)

	# --- name, top-left
	var fs_name := int(maxf(10.0, s(18)))
	draw_string(f, Vector2(s(10), s(27)), card.display_name,
		HORIZONTAL_ALIGNMENT_LEFT, cw - s(74), fs_name, Color(1, 1, 1, dim))
	if owner_name != "" and view_scale > 0.45:
		draw_string(f, Vector2(s(10), s(42)), owner_name,
			HORIZONTAL_ALIGNMENT_LEFT, cw - s(74), int(maxf(8.0, s(12))),
			Color(0.78, 0.82, 0.9, dim))

	# --- element, top-right
	_element_badge(cw, dim)

	# --- the two costs, bottom corners
	_cost_badges(cw, ch, dim)

	if view_scale > 0.42:
		draw_string(f, Vector2(0, ch - s(62)), TYPE_LABEL.get(card.type, "?"),
			HORIZONTAL_ALIGNMENT_CENTER, cw, int(maxf(8.0, s(12))),
			Color(0.82, 0.85, 0.92, 0.85 * dim))

	_draw_bond_row(cw, ch, dim)

	if card.level > 1:
		for i in card.level:
			draw_circle(Vector2(s(12) + float(i) * s(9), ch - s(8)), s(3.0),
				Color(1.0, 0.9, 0.5, dim))

	# --- the frame last, over everything
	var border := accent if playable else accent.darkened(0.45)
	draw_rect(Rect2(0, 0, cw, ch), border, false, s(3.0) if selected else s(2.0))
	if selected:
		draw_rect(Rect2(-s(3), -s(3), cw + s(6), ch + s(6)),
			Color(1.0, 0.95, 0.5), false, s(2.0))

# A REAL gradient. This used to be six stacked rectangles at stepped alpha,
# which read as six grey bands across the bottom of every card — draw_rect
# cannot interpolate, so stacking it was never going to be smooth. draw_polygon
# takes a colour per vertex and the GPU interpolates between them: one call,
# no banding, no shader, no texture.
func _scrim(r: Rect2, from_top: bool) -> void:
	var solid := Color(0.03, 0.04, 0.06, 0.85)
	var clear := Color(0.03, 0.04, 0.06, 0.0)
	var near := solid if from_top else clear
	var far := clear if from_top else solid
	draw_polygon(
		PackedVector2Array([
			r.position,
			r.position + Vector2(r.size.x, 0.0),
			r.position + r.size,
			r.position + Vector2(0.0, r.size.y)]),
		PackedColorArray([near, near, far, far]))

# THE ELEMENT, top right. Two forms, because one of them stops working when the
# card gets small: the badge is a labelled box, and below the size its label can
# be read at it becomes a plain filled chip instead.
#
# It used to draw the box at any size and the LABEL only above 0.44, so a small
# card carried an empty outlined rectangle that said nothing at all. A chip of
# the element's own colour says the same thing the label would, in the space
# actually available.
const BADGE_TEXT_SCALE := 0.44

func _element_badge(cw: float, dim: float) -> void:
	if view_scale <= 0.34: return
	var col: Color = ELEMENT_COLORS.get(card.element, Color(0.6, 0.6, 0.6))
	if view_scale <= BADGE_TEXT_SCALE:
		var chip := Rect2(cw - s(30), s(8), s(22), s(12))
		draw_rect(chip, Color(0.05, 0.06, 0.09, 0.85 * dim))
		draw_rect(chip.grow(-maxf(1.0, s(1.4))), col * Color(1, 1, 1, dim))
		return
	var r := Rect2(cw - s(66), s(8), s(58), s(22))
	draw_rect(r, Color(0.05, 0.06, 0.09, 0.85 * dim))
	draw_rect(r, col * Color(1, 1, 1, dim), false, maxf(1.0, s(1.6)))
	if true:
		var txt := ElementChart.name_of(card.element).to_upper()
		# Sized to FIT rather than to a constant. "LIGHTNING" is twice the width
		# of "FIRE", and a fixed size clipped it to LIGHTNIN on every small card.
		var fs: float = minf(s(11), r.size.x / (float(txt.length()) * 0.60))
		draw_string(ThemeDB.fallback_font, Vector2(r.position.x, r.position.y + s(15)),
			txt, HORIZONTAL_ALIGNMENT_CENTER, r.size.x, int(maxf(6.0, fs)),
			col.lightened(0.35) * Color(1, 1, 1, dim))

# AP bottom-LEFT in blue, EP bottom-RIGHT in green. Summoner cards spend Command
# Points and ultimates spend the Summoner's SP, so those take the AP corner and
# say so rather than pretending to be an ordinary cost.
func _cost_badges(cw: float, ch: float, dim: float) -> void:
	var y := ch - s(38)
	if card.type == Card.Type.SUMMONER:
		_badge(Vector2(s(26), y), str(maxi(1, card.ap_cost)), Color(0.85, 0.72, 0.35), dim, "CP")
		return
	if card.type == Card.Type.ULTIMATE:
		_badge(Vector2(s(26), y), str(Battle.ULT_SP_COST), Color(0.95, 0.55, 0.85), dim, "SP")
		return
	if card.ap_cost > 0:
		_badge(Vector2(s(26), y), str(card.ap_cost), COL_AP, dim, "AP")
	if card.ep_cost > 0:
		_badge(Vector2(cw - s(26), y), str(card.ep_cost), COL_EP, dim, "EP")

func _badge(centre: Vector2, label: String, col: Color, dim: float, tag: String) -> void:
	var r := s(19.0)
	# SOLID, not translucent. These sit over arbitrary artwork, and a
	# see-through disc left the digit competing with whatever was behind it.
	draw_circle(centre, r, Color(0.04, 0.05, 0.07, dim))
	draw_arc(centre, r, 0, TAU, 24, col * Color(1, 1, 1, dim), maxf(2.0, s(3.0)))
	# White, not tinted. The ring colour already says which currency this is;
	# the number only has to be legible.
	draw_string(ThemeDB.fallback_font, centre + Vector2(-r, s(8)), label,
		HORIZONTAL_ALIGNMENT_CENTER, r * 2.0, int(maxf(13.0, s(22))),
		Color(1, 1, 1, dim))
	# The tag is what tells you the badge is a COST and which one. It used to
	# vanish below 0.6 scale, which is most of a full hand.
	if view_scale > 0.40:
		draw_string(ThemeDB.fallback_font, centre + Vector2(-r, r + s(13)), tag,
			HORIZONTAL_ALIGNMENT_CENTER, r * 2.0, int(maxf(9.0, s(12))),
			col.lightened(0.30) * Color(1, 1, 1, dim))

func _partner_colour(pid: String) -> Color:
	var u := Content.unit_by_id(pid)
	if u == null: return Color(0.6, 0.6, 0.6)
	return ELEMENT_COLORS.get(u.element, Color(0.6, 0.6, 0.6))

# One pip per bonded ally: filled when they are standing, hollow when they are
# not. A full row means every bond is live and the card is at its best.
func _draw_bond_row(cw: float, ch: float, dim: float) -> void:
	if card == null or card.partners.is_empty(): return
	if view_scale <= 0.34: return
	var live := bond_live()
	var n := card.partners.size()
	var step := s(15)
	var x := cw * 0.5 - step * float(n - 1) * 0.5
	var y := ch - s(46)
	var r := s(5.0)
	for pid in card.partners:
		var col := _partner_colour(str(pid))
		var at := Vector2(x, y)
		if bond_present.get(str(pid), false):
			draw_circle(at, r, col * Color(1, 1, 1, dim))
			draw_arc(at, r, 0, TAU, 16, Color(1, 1, 1, 0.85 * dim), maxf(1.0, s(1.5)))
		else:
			draw_arc(at, r, 0, TAU, 16, col * Color(1, 1, 1, 0.45 * dim), maxf(1.0, s(1.5)))
		x += step
	# The pips alone never said what they were. A caption does: how many of this
	# card's bonded allies are standing, and what that is currently worth.
	if view_scale > 0.40:
		var cap := "BOND %d/%d" % [live, n]
		if live > 0: cap += "   x%.2f" % (1.0 + card.partner_scale * float(live))
		# Red, and outlined. The green sat inside the same value range as most of
		# the card art behind it and disappeared into it; red is the one hue
		# almost none of this artwork uses, so it separates on hue as well as on
		# brightness.
		var bond_col := Color(1.0, 0.34, 0.32, dim) if live > 0 else Color(0.76, 0.50, 0.50, dim)
		for o in [Vector2(-1, 0), Vector2(1, 0), Vector2(0, -1), Vector2(0, 1)]:
			draw_string(ThemeDB.fallback_font, Vector2(0, y - s(10)) + o, cap,
				HORIZONTAL_ALIGNMENT_CENTER, cw, int(maxf(10.0, s(13))),
				Color(0, 0, 0, 0.9 * dim))
		draw_string(ThemeDB.fallback_font, Vector2(0, y - s(10)), cap,
			HORIZONTAL_ALIGNMENT_CENTER, cw, int(maxf(10.0, s(13))), bond_col)

# The back: what the move actually does, at a size meant to be read. Reached by
# pressing and holding, which is why it can afford to be all text.
# ---------------------------------------------------------------- the back
#
# THE BACK IS THE RULES TEXT. Turning a card over is the player asking "what
# does this actually do", and the honest answer is numbers: how much, at whom,
# for how long, and what it is worth with the party currently standing. It used
# to answer with one line of flavour, which is the one thing they were not
# asking.
#
# Everything here is DERIVED, never transcribed. The damage figure comes off
# effective_power() so a levelled card says so; the element line is read out of
# ElementChart rather than written down beside it; the bond count is the live
# one BattleScene hands over. A back that can drift out of step with the rules
# is worse than no back at all.
#
# It is laid out top-down against a cursor and every section is optional, so a
# card with no status and no bond simply has a shorter back rather than a hole
# in the middle of one.

const TARGET_WORDS := {
	Card.Target.SINGLE_ENEMY: "One enemy",
	Card.Target.ALL_ENEMIES: "Every enemy",
	Card.Target.SINGLE_ALLY: "One ally",
	Card.Target.ALL_ALLIES: "The whole party",
	Card.Target.SELF: "Whoever plays it",
	Card.Target.NONE: "No target - plays the moment it is dropped",
}

const COL_HEAD := Color(0.58, 0.63, 0.75)      # section labels
const COL_BODY := Color(0.91, 0.93, 0.98)      # the rules themselves
const COL_FLAV := Color(0.62, 0.66, 0.76)      # flavour, quietly

func _draw_back() -> void:
	var accent := _accent()
	var cw := s(W)
	var ch := s(H)
	var f := ThemeDB.fallback_font
	draw_rect(Rect2(0, 0, cw, ch), Color(0.07, 0.08, 0.11))

	# --- header: what it is called, and whose it is
	draw_rect(Rect2(0, 0, cw, s(40)), accent * Color(1, 1, 1, 0.30))
	draw_string(f, Vector2(s(12), s(27)), card.display_name,
		HORIZONTAL_ALIGNMENT_LEFT, cw - s(24), int(maxf(11.0, s(18))), Color(1, 1, 1))
	var sub: String = str(TYPE_LABEL.get(card.type, "?")).split("  ")[0]
	if owner_name != "": sub += "   " + owner_name
	draw_string(f, Vector2(s(12), s(56)), sub,
		HORIZONTAL_ALIGNMENT_LEFT, cw - s(70), int(maxf(9.0, s(12))), COL_HEAD)
	draw_string(f, Vector2(0, s(56)), ElementChart.name_of(card.element).to_upper(),
		HORIZONTAL_ALIGNMENT_RIGHT, cw - s(12), int(maxf(9.0, s(12))),
		ELEMENT_COLORS.get(card.element, Color(0.7, 0.7, 0.7)))

	# The cost strip is drawn last but its room is reserved first, so a long
	# rules block runs out of space rather than printing over the price.
	var floor_y: float = ch - s(38)
	var y: float = s(70)

	y = _back_head(f, "EFFECT", y, cw, floor_y)
	for line in _effect_lines():
		y = _back_body(f, line, y, cw, floor_y, COL_BODY)

	y = _back_head(f, "TARGET", y, cw, floor_y)
	y = _back_body(f, str(TARGET_WORDS.get(card.target, "?")), y, cw, floor_y, COL_BODY)

	# ORDER OF PRIORITY, not order of interest. Bigger rules text means fewer
	# lines fit, and what runs out of room has to be the part you can play
	# without. The BOND block comes before the element matchup because the live
	# count is the one figure on this card that changes during the fight — an
	# ally going down rewrites it — while the matchup is the same every time.
	if not card.partners.is_empty():
		y = _back_head(f, "BOND", y, cw, floor_y)
		y = _back_body(f, card.bond_text(), y, cw, floor_y, Color(0.62, 0.95, 0.68))
		var live := bond_live()
		var all: bool = live == card.partners.size()
		y = _back_body(f, "%d of %d standing%s" % [live, card.partners.size(),
			"  -  FULL BOND" if all else ""], y, cw, floor_y,
			Color(0.72, 1.0, 0.78) if all else Color(1.0, 0.72, 0.55))
		y = _back_body(f, "+%d%% each, more as the bond deepens"
			% int(card.partner_scale * 100.0), y, cw, floor_y, Color(0.55, 0.78, 0.60))

	var el := _element_note()
	if el != "":
		y = _back_head(f, "ELEMENT", y, cw, floor_y)
		y = _back_body(f, el, y, cw, floor_y, COL_BODY)

	# Flavour last, and only if the rules left room for it. It is the part of
	# the card nobody needs in order to play correctly.
	if card.description != "":
		y = _back_body(f, card.description, y, cw, floor_y, COL_FLAV, 13.0)

	# --- the price, always drawn, always in the same place
	var cost := "AP %d     EP %d" % [card.ap_cost, card.ep_cost]
	if card.type == Card.Type.ULTIMATE: cost = "SP %d" % Battle.ULT_SP_COST
	elif card.type == Card.Type.SUMMONER: cost = "CP %d" % maxi(1, card.ap_cost)
	draw_string(f, Vector2(s(12), ch - s(20)), cost,
		HORIZONTAL_ALIGNMENT_LEFT, cw - s(24), int(maxf(10.0, s(14))), Color(0.85, 0.9, 1.0))
	if card.level > 1:
		draw_string(f, Vector2(0, ch - s(20)), "Level %d" % card.level,
			HORIZONTAL_ALIGNMENT_RIGHT, cw - s(12), int(maxf(9.0, s(12))),
			Color(1.0, 0.9, 0.5))
	draw_rect(Rect2(0, 0, cw, ch), accent, false, s(2.0))

# A section label. Returns the cursor, unmoved, if there is no room left for
# the section it introduces - a heading with nothing under it is worse than a
# section that quietly did not fit.
func _back_head(f: Font, text: String, y: float, cw: float, floor_y: float) -> float:
	var size := int(maxf(10.0, s(12.5)))
	# Room for the HEADING AND A LINE UNDER IT, not just the heading. Reserving
	# only its own height let a section title print with its text clipped off
	# below — a label introducing nothing, which is worse than the section
	# simply not being there.
	if y + s(52) > floor_y: return y
	draw_string(f, Vector2(s(12), y + f.get_ascent(size)), text,
		HORIZONTAL_ALIGNMENT_LEFT, cw - s(24), size, COL_HEAD)
	return y + size + s(5)

# One wrapped paragraph of rules text, clipped to whatever room is left.
func _back_body(f: Font, text: String, y: float, cw: float, floor_y: float,
		col: Color, pt := 15.0) -> float:
	if text == "" or y >= floor_y: return y
	var size := int(maxf(12.0, s(pt)))
	var w: float = cw - s(24)
	var block := f.get_multiline_string_size(text, HORIZONTAL_ALIGNMENT_LEFT, w, size, 3)
	if y + block.y > floor_y: return floor_y      # out of room; stop cleanly
	draw_multiline_string(f, Vector2(s(12), y + f.get_ascent(size)), text,
		HORIZONTAL_ALIGNMENT_LEFT, w, size, 3, col)
	return y + block.y + s(4)

# WHAT IT DOES, in the order it happens. Every figure is read off the card, so a
# levelled or upgraded copy reports its own numbers rather than the base ones.
func _effect_lines() -> Array:
	var out: Array = []
	if card.power > 0.0:
		out.append("Deals %.2fx the caster's %s as damage"
			% [card.effective_power(), "Magic" if card.uses_magic else "Attack"])
	if card.heal > 0.0:
		out.append("Restores %d HP" % int(card.effective_heal()))
	if card.status != "":
		var mag := ""
		if card.status_magnitude > 0.0:
			mag = " at %d%%" % int(round(card.status_magnitude * 100.0))
		out.append("Applies %s%s for %d round%s" % [card.status, mag,
			card.status_duration, "" if card.status_duration == 1 else "s"])
	if card.draw_count > 0:
		out.append("Draws %d card%s" % [card.draw_count,
			"" if card.draw_count == 1 else "s"])
	if card.ep_gain > 0: out.append("Grants +%d EP" % card.ep_gain)
	if card.ap_gain > 0: out.append("Grants +%d AP" % card.ap_gain)
	if card.ep_transfer: out.append("Hands over every EP the caster has left")
	if card.ap_transfer: out.append("Hands over every AP the caster has left")
	if card.synergy == "combo_marked" and card.synergy_scale > 0.0:
		out.append("+%d%% again against a target already carrying DefenseDown"
			% int(round(card.synergy_scale * 100.0)))
	if card.ap_cost == 0 and card.type != Card.Type.SUMMONER:
		out.append("Costs no action - playing it does not end anything")
	if out.is_empty(): out.append(_blurb() if _blurb() != "" else "No effect of its own")
	return out

# READ OFF THE CHART, not written down beside it. Whatever ElementChart says
# today is what the card says today.
func _element_note() -> String:
	if card.element == ElementChart.E.NEUTRAL:
		return "Neutral - no matchup either way"
	var strong := ""
	var weak := ""
	for e in [ElementChart.E.FIRE, ElementChart.E.WATER, ElementChart.E.WIND,
			ElementChart.E.EARTH, ElementChart.E.LIGHTNING, ElementChart.E.LIGHT,
			ElementChart.E.DARK]:
		var m := ElementChart.get_multiplier(card.element, e)
		if m > 1.01: strong = ElementChart.name_of(e)
		elif m < 0.99: weak = ElementChart.name_of(e)
	if strong == "" and weak == "": return ""
	# Light and Dark beat EACH OTHER, so the generic phrasing comes out as
	# "x1.5 into Dark, blunted by Dark", which reads as a bug rather than a rule.
	if strong != "" and strong == weak:
		return "x%.1f into %s - and %s hits back just as hard" % [
			ElementChart.WEAKNESS_MULT, strong, strong]
	var parts: Array = []
	if strong != "": parts.append("x%.1f into %s" % [ElementChart.WEAKNESS_MULT, strong])
	if weak != "": parts.append("blunted by %s" % weak)
	return ", ".join(parts)

func _blurb() -> String:
	if card.description != "":
		return card.description
	var bits: Array = []
	if card.power > 0.0: bits.append("Damage %.1fx" % card.effective_power())
	if card.heal > 0.0: bits.append("Heal %d" % int(card.effective_heal()))
	if card.draw_count > 0: bits.append("Draw %d" % card.draw_count)
	if card.ep_gain > 0: bits.append("+%d EP" % card.ep_gain)
	if card.ap_gain > 0: bits.append("+%d AP" % card.ap_gain)
	if card.ep_transfer: bits.append("Donate EP")
	if card.ap_transfer: bits.append("Donate AP")
	if card.status != "": bits.append(card.status)
	if card.ap_cost == 0 and card.type != Card.Type.SUMMONER:
		bits.append("(no action)")
	return ", ".join(bits)
