# Battle presentation. Owns NOTHING but display + input — all rules live in
# core/battle.gd, so this layer can be rewritten without touching a single rule.
#
# LAYOUT is the Hearthstone/Slay-the-Spire shape: enemies in a row across the
# top, your board in a row beneath them, the hand along the bottom, the Summoner
# in the hero-power slot on the left, the log in a narrow drawer on the right.
# The room's own illustration sits behind all of it.
#
# INPUT is drag-and-drop. Press a card and a copy lifts off the hand and follows
# the cursor; drop it on a legal target to play it. Two gestures come off the
# same press:
#
#   FLICK — release fast, aimed at the target, and the throw earns the SAME
#           bonus an on-beat play does (core/flick.gd). One reward for playing
#           well, not two competing ones.
#   HOLD  — keep still and the lifted card turns over and grows, showing what
#           the move actually does. Releasing puts it back; reading is free.
#
# Clicking a card then clicking a target still works. Drag is the better way,
# not the only way.
extends Control

var battle := Battle.new()
var selected_card: Card = null
var selected_actor: Unit = null      # resolved automatically from card ownership

var _beat_pulse := 0.0
var _beat_hit := 0.0            # fades an execution confirmation
var _beat_hit_text := ""
# Panel rects by unit id, captured as the panels are built, so hit feedback and
# ability effects can be drawn OVER the unit that was actually hit.
var _panels: Dictionary = {}         # unit id -> Control
var _fx: HitFeedback = null

# --- the lifted card
var _lift: CardView = null           # the copy that follows the cursor
var _lift_card: Card = null
var _lift_from := Vector2.ZERO       # where the press began, for flick aim
var _lift_at := Vector2.ZERO         # where the pointer is now
var _lift_scale := 1.0
var _holding := false                # this press turned into a read
var _flip_t := 1.0                   # 1 face, through 0 edge-on, to -1 back
var _hover_target: Unit = null

# --- gestures on the party's own cards
# A unit card IS its basic attack now, so it carries the same gestures every
# other card does: drag it onto a target to swing, hold it to read it. The one
# it adds is a double tap, which opens the art out and folds it back.
var _u_press := {}                   # unit id -> {at, elapsed, moved, lifted}
var _u_last_tap := {}                # unit id -> seconds since a completed tap
var _expanded := {}                  # unit id -> art opened out

# The rows SPREAD across the full width rather than packing to one side. Each
# board gets an equal share of the row and sits in the middle of it, so three
# creatures and six are both evenly spaced and neither leaves a third of the
# screen empty. Centring packed everything into the middle; left-aligning
# packed it against the left and moved the dead space to the right. Equal
# shares is the only one of the three that has no dead end.

# One unit board at full size. Everything scales off the window from here.
#
# THE COLUMN IS ZERO-SUM. Top bar, two unit rows, the prompt and the hand share
# one screen height, so a bigger card can only come out of the boards — there is
# nowhere else for it to come from. The boards gave up 36px each and the hand
# took all of it, which is the right way round: a board is a few numbers you
# glance at, a card is a paragraph you have to read.
const UNIT_W := 186.0
const UNIT_H := 244.0
# The portrait band, sized so the picture FILLS THE CARD'S WIDTH at its own
# aspect. The art is portrait at roughly 0.68 wide-to-tall, so a band this deep
# shows all of it with barely a margin either side — a shallower one either
# crops the character at the waist or strands them between black bars.
const PORTRAIT_H := 158.0
const PORTRAIT_BIG := 360.0
# A second tap inside this window is a double tap, not two taps.
const DOUBLE_TAP := 0.28
# The Summoner's portrait is shown WHOLE, so this is a height budget rather than
# a crop window: the picture is fitted inside it at its own aspect. JD is about
# 0.56 wide-to-tall, so at this height he takes roughly 170 of the column's 250
# and the rest is margin — which is the price of not cutting the player's own
# character in half.
# What the health bar and its number cost a Summoner's board. Taken off the
# portrait band rather than added to the panel, so both rows stay one board
# tall and the hand's height budget does not have to move.
# How big a held card grows. A hold is someone reading the rules text on the
# back, so this is sized for READING rather than for a flourish - at 1.55 the
# back was a wall of six-point type and the gesture defeated its own purpose.
const HOLD_SCALE := 2.15
const LIFT_SCALE := 1.18             # how big a dragged card gets
# Over a legal target the card eases back a little so the board underneath it
# stays readable -- but only a little. THE CARD IN THE AIR IS THE MAIN EVENT
# while it is in the air: at half size it stopped being the thing you were
# looking at and the gesture lost its subject.
const DROP_SCALE := 0.88

# THE HAND IS ONE RUN, CENTRED AT THE BOTTOM. It used to be five little hands,
# each anchored under the character whose cards they were. That read well and
# spread them across the entire width; packed into the middle they read as what
# they are — your hand, one thing, in reach of one thumb.
#
# Whose card is whose survives the change without the spacing: every card
# carries its owner's name and wears their colour on its border.
# THE WHOLE HAND IS ONE FAN, the way a hand of cards actually sits in a fist:
# every card overlapping the one before it, the run tilting through an arc, the
# middle riding highest. Owners used to be separated by a POSITIVE gap, which
# broke the run into little flat blocks with air between them -- so the gap is
# negative now: one owner's last card is overlapped by the next owner's first
# exactly as much as two cards of the same owner overlap.
# HOW MUCH OF ITS ROW THE FAN SHOULD COVER. The hand's SCALE belongs to the
# column solver -- a wider hand must therefore come from overlapping LESS, not
# from bigger cards. The whole run has a closed form:
#
#   total = W * hs * (N - overlap * (N - 1))
#
# so the overlap needed for a given width is solved rather than guessed. One
# owner's cards overlap the next by exactly as much as two of the same owner's
# do, which is what keeps the run reading as one hand.
const HAND_FILL := 0.75

# THE FAN. Cards in a stack tilt and rise the way a hand of cards held in one
# hand does, instead of sitting in a flat row like a shop shelf.
#
# It is deliberately SLIGHT. A real fan spreads far enough that the buried cards
# show only a sliver, and every one of these has a name, two costs and a bond
# row that have to stay readable — so this is a few degrees and a few pixels,
# enough to read as cards rather than as tiles.
#
# The lift is exempt: a card in the air is a card you are aiming, and a tilted
# one would fight the cursor.
const FAN_DEGREES := 2.1           # tilt between one card and the next
const FAN_RISE := 16.0             # how far the middle of the fan lifts
const FAN_MAX_DEGREES := 8.0       # tilt of the outermost card, however many
const HAND_EDGE := 4.0
# THE HAND IS TWO RUNS. Theirs at the far left, under their own board; yours
# centred in what is left. The cards cost different things -- CP against AP --
# and they are played for different reasons, so they are two hands rather than
# one hand with a colour code in it.
const HAND_SPLIT_GAP := 64.0
# THE CARD YOU ARE POINTING AT COMES UP OUT OF THE ROW. In a packed hand the
# card you want is half covered by the one after it, so something has to lift it
# clear — a little higher, a little bigger, drawn over its neighbours, and
# playing its clip if it has one. Reading a card should not cost a click.
const HAND_RAISE := 34.0
# TWICE THE SIZE. The info panel used to carry the big picture; the card you
# are pointing at carries it now, which is why that panel could be condensed
# down to a strip and the field given the room instead.
const HAND_HOVER_SCALE := 2.0

# THE DRAW PILE SITS AT THE END OF THE HAND, in the strip of row the boards
# never reached — the party line stops short of the log column, so the hand was
# anchored to it and stopped short too, leaving the right end of the row empty.
# The stacks now spread across everything up to the deck, and the deck stands in
# what is left.

# THE TURN ORDER IS FACES. A row of names says who is next; a row of portraits
# says it at a glance, which is the entire job of the thing.
const ORDER_SHOWN := 7
const ORDER_ICON := 40.0
const ORDER_ICON_UP := 54.0        # whoever is acting is bigger than the queue

# THE TWO HEADS, AND HOW MUCH IS LEFT OF THEM. One pennant over each Summoner,
# in that side's colour - blue for yours, red for theirs - and it doubles as
# their health: full at full health, draining away to nothing as they are worn
# down. They are the win condition on both sides, so how much is left of them is
# the single most important number on the screen, and it belongs somewhere your
# eye passes anyway rather than in a bar you have to go and look at.
#
# ONLY the Summoners carry one. Every board having a pennant made the row into a
# line of bunting and said nothing you could not already tell from which half of
# the screen you were looking at.
#
# Drawn on the overlay rather than inside the board, so it sits OUTSIDE the
# panel and cannot fight the portrait or the name for the same pixels.
const TEAM_TRI_W := 30.0
const TEAM_TRI_H := 20.0
const COL_TEAM_ALLY := Color(0.36, 0.70, 0.95)
const COL_TEAM_ENEMY := Color(0.90, 0.36, 0.34)

# THE LOG IS A QUARTER OF THE SCREEN, not a column down the whole right edge.
# It is a running record you glance at, and it scrolls, so height past this is
# spent showing lines nobody is reading.
const LOG_STRIP_H := 42.0
const LOG_W_FRAC := 0.22
const LOG_H_FRAC := 0.25
const LOG_W_MIN := 300.0
const LOG_W_MAX := 430.0

# THE LOG IS A STRIP IN THE TOP BAR and the readout is a strip in the bottom
# corner. Both used to be a column down the right-hand side, which is a quarter
# of the screen's width spent on two things you read a line of at a time -- the
# field wanted that width far more than they did.
@onready var log_box: RichTextLabel = $Root/TopBar/LogPanel/Log
@onready var log_panel: PanelContainer = $Root/TopBar/LogPanel
@onready var detail_box: VBoxContainer = $DetailStrip/Detail
@onready var detail_strip: PanelContainer = $DetailStrip
# The hand is a PLAIN CONTROL, not a row container. Each owner's cards are
# placed by hand, centred under the character they belong to — see _layout_hand.
@onready var hand_box: Control = $Root/HandRow
# THE CLEARANCE, AS A REAL NODE. It was only ever a term in the column budget,
# and a term in a budget is not a gap: Body expands to fill whatever is left, so
# the party line sat flush against the top of the hand however much room the
# arithmetic had set aside for air. This is the air.
@onready var hand_gap: Control = $Root/HandGap
# THE RAILS. Two columns either side of the field, each divided into three
# bands -- a block the height of a rank, an expanding middle, another block the
# height of a rank. Each head stands in the TOP block of its own side's rail,
# the powers and the piles live below them, and the identical height budgets
# keep the two rails in step without anything reading a global_position.
@onready var left_rail: VBoxContainer = $Root/Body/LeftRail
@onready var left_top: Control = $Root/Body/LeftRail/Top
@onready var left_bottom: Control = $Root/Body/LeftRail/Bottom
@onready var right_rail: VBoxContainer = $Root/Body/RightRail
@onready var right_top: Control = $Root/Body/RightRail/Top
@onready var right_mid: Control = $Root/Body/RightRail/Mid
@onready var right_bottom: Control = $Root/Body/RightRail/Bottom
# The two grids stand SIDE BY SIDE now -- yours on the left of the field,
# theirs mirrored on the right -- with the stage in the column of air between
# them.
@onready var ally_box: Control = $Root/Body/Field/AllyGrid
@onready var enemy_box: Control = $Root/Body/Field/EnemyGrid
# How much of a card the next one in its stack covers. The name and the AP cost
# both live in the LEFT half of a card, so a buried card still says whose it is
# and what it costs — which is what makes stacking cheap rather than lossy.
# A buried card still shows its name and its AP cost, both of which live in the
# LEFT of a card — so a deeper overlap costs nothing to read and buys width the
# cards themselves can have instead.
# The TIGHTEST the fan is ever packed. _solve_overlap opens it up from here when
# there is width going spare, and never past it when there is not.
const STACK_OVERLAP := 0.66
@onready var status_bar: Label = $Root/TopBar/Centre/Col/Status
@onready var order_box: HBoxContainer = $Root/TopBar/Centre/Col/Order
@onready var end_turn_btn: Button = $Root/TopBar/EndRound
@onready var auto_btn: Button = $Root/TopBar/Auto
@onready var prompt: Label = $Root/Prompt
@onready var title_label: Label = $Root/TopBar/Title
@onready var beat_dot: Control = $Root/TopBar/BeatDot
@onready var overlay: Control = $Overlay
@onready var room_bg: Control = $RoomBG

func _ready() -> void:
	_fx = HitFeedback.new()
	add_child(_fx)
	battle.log_line.connect(_on_log)
	battle.enemy_played.connect(_on_enemy_played)
	var sess = _sess()
	var enemies: Array
	var node_name := "Corrupted Grove"
	if sess != null and sess.dungeon != null and sess.dungeon.current_room() != null:
		var n = sess.dungeon.current_room()
		# Depth picks the rank band; the room's own cell seeds the roll, so
		# walking out and back in does not reshuffle what is waiting.
		enemies = Content.encounter_for(n.kind, n.kind == Dungeon.Kind.BOSS,
			sess.party_level(), sess.dungeon.floor_i,
			sess.dungeon.seed_value + sess.dungeon.at.x * 733 + sess.dungeon.at.y * 9871)
		node_name = n.title()
		_room_kind = n.kind
		# The room you are standing in is the room you fight in. Same seed and
		# cell the board used, so the picture matches what the map showed.
		RoomArt.apply(room_bg, n.kind, sess.dungeon.seed_value, sess.dungeon.at)
	else:
		# The showcase fight rolls out of the BESTIARY like a dungeon room does
		# — same band, same size roll, same head promotion — rather than from
		# the hand-written Vale list, whose creatures have no faces to stand in
		# the summoner box.
		enemies = Content.encounter_for(Dungeon.Kind.ENEMY, false, 1, 1, 7)
		_room_kind = Dungeon.Kind.ENEMY
		RoomArt.apply(room_bg, Dungeon.Kind.ENEMY, 7, Vector2i.ZERO)
	title_label.text = "SILVERWOOD FOREST — %s" % node_name
	var party: Array = sess.build_party() if sess != null 		else Content.roster() + Content.recruitable()
	# WHO STANDS, AND WHO WAITS. The field takes the first few and the rest are
	# the bench — a party wider than the line is what makes the Reserve button
	# something other than decoration. A party of four has nobody in reserve and
	# nothing about the fight changes, which is the point: the bench costs the
	# player nothing until they have one.
	var allies: Array = party.slice(0, Battle.FIELD_MAX)
	var bench: Array = party.slice(Battle.FIELD_MAX)
	var deck: Array = sess.build_deck() if sess != null else Content.starting_deck()
	# BOTH HEADS, ON THE FIELD. Yours is always there and can always be reached;
	# theirs is the strongest body in the room, pulled off the board and promoted
	# — every fight is ABOUT somebody, and their face is what stands in the
	# summoner box. Cut them down and the rest folds.
	# Set BEFORE setup, since setup opens the round and the round can end on
	# either of them.
	var lvl: int = sess.party_level() if sess != null else 1
	battle.summoner = Content.summoner_unit(_summoner_id(), lvl)
	var is_boss: bool = _room_kind == Dungeon.Kind.BOSS
	var leader: Unit = Content.strongest_of(enemies)
	if leader != null:
		enemies.erase(leader)
		Content.promote_leader(leader, is_boss)
		battle.enemy_summoner = leader
	battle.setup(allies, enemies, deck, randi(),
		sess.bond_ranks() if sess != null else {}, bench)
	if battle.enemy_summoner != null:
		_on_log("[color=#ff9c9c]%s is directing this one. Cut them down and the rest folds.[/color]"
			% battle.enemy_summoner.display_name)
	# The session judges the contract on who actually fell, so it needs to know
	# who was in the fight.
	if sess != null: sess.last_enemies = enemies
	var live := 0
	var possible := 0
	var standing := _living_ally_ids()
	for dc in deck:
		for pid in dc.partners:
			possible += 1
			if standing.has(str(pid)): live += 1
	_on_log("Deck: %d cards from %d units    Bonds live: %d/%d"
		% [deck.size(), party.size(), live, possible])
	if not bench.is_empty():
		var names: Array = []
		for r in bench: names.append(r.display_name.split(" ")[0])
		_on_log("In reserve: %s" % ", ".join(names))
	# Farm food buff: applied to every deployed unit at run start.
	if sess != null:
		var buff: Dictionary = sess.active_buff()
		if not buff.is_empty():
			for u in battle.allies:
				u.apply_status(str(buff["status"]), float(buff["magnitude"]),
					int(buff["duration"]))
			_on_log("Dish carried in: [b]%s[/b] — %s on the whole party" %
				[buff["dish"], buff["status"]])
	# Auto mode is remembered between fights: somebody who wants the game to
	# play itself wants that on the next room too, not once.
	if sess != null:
		auto_on = bool(sess.data.get("settings", {}).get("auto_battle", false))
	auto_btn.button_pressed = auto_on
	_style_auto()
	if has_node("/root/Beat"):
		get_node("/root/Beat").beat.connect(_on_beat)
	get_viewport().size_changed.connect(_on_window_resized)
	overlay.draw.connect(_on_overlay_draw)
	log_panel.gui_input.connect(_on_log_input)
	log_panel.mouse_filter = Control.MOUSE_FILTER_STOP
	_size_log()
	refresh()

# The room this fight is in, which is what decides whether it has a commander.
var _room_kind: int = Dungeon.Kind.ENEMY

func _on_window_resized() -> void:
	_rows_sized = false        # the tiles must be measured against the new width
	_size_log()
	_rebuild_hand()

# A quarter of the screen, and no more. The rest of the column is empty air the
# hand and the party row are welcome to grow into.
# A STRIP, NOT A COLUMN. It shows the last couple of lines and scrolls itself;
# tapping it opens the whole history down over the field and tapping it again
# puts it away. Everything the column used to show is still in there.
# THREE HEIGHTS. Collapsed it is the current action and nothing else, which is
# what you actually read mid-fight; a double click doubles it to catch the line
# before; a press and hold opens half the screen for the whole history. Anything
# finer than three states is a control nobody would learn.
enum LogSize { ONE, TWO, HALF }
var _log_size: int = LogSize.ONE
var _log_last_click := 0.0
var _log_press_at := 0.0
const LOG_DOUBLE_TAP := 0.30
const LOG_HOLD := 0.45

func _log_height() -> float:
	match _log_size:
		LogSize.HALF: return maxf(220.0, get_viewport_rect().size.y * 0.5)
		LogSize.TWO: return LOG_STRIP_H * 2.0
		_: return LOG_STRIP_H

func _size_log() -> void:
	var vp := get_viewport_rect().size
	var w: float = clampf(vp.x * LOG_W_FRAC, LOG_W_MIN, LOG_W_MAX)
	log_panel.custom_minimum_size = Vector2(w, LOG_STRIP_H)
	log_panel.offset_left = -(w + 222.0)
	log_panel.offset_right = -222.0
	log_panel.offset_top = 0.0
	log_panel.offset_bottom = _log_height()

func _on_log_input(event: InputEvent) -> void:
	if not (event is InputEventMouseButton): return
	if event.button_index != MOUSE_BUTTON_LEFT: return
	var now: float = float(Time.get_ticks_msec()) / 1000.0
	if event.pressed:
		_log_press_at = now
		return
	# A HOLD IS DECIDED ON RELEASE rather than by a timer: a timer that fires
	# while the button is still down opens the panel under the finger holding it.
	if now - _log_press_at >= LOG_HOLD:
		_log_size = LogSize.ONE if _log_size == LogSize.HALF else LogSize.HALF
	elif now - _log_last_click <= LOG_DOUBLE_TAP:
		_log_size = LogSize.ONE if _log_size == LogSize.TWO else LogSize.TWO
	else:
		_log_size = LogSize.ONE
	_log_last_click = now
	_size_log()

func _sess():
	var r := get_tree().root
	return r.get_node("Session") if r.has_node("Session") else null

func _beat():
	return get_node("/root/Beat") if has_node("/root/Beat") else null

func _sfx(key: String) -> void:
	if has_node("/root/Sfx"): get_node("/root/Sfx").play(key)

# The round turning over is the one moment the board changes underneath you —
# statuses tick, everyone refills, the order is rebuilt and fresh cards arrive.
# Without a beat of animation on it, all of that happens between two frames and
# reads as the screen glitching.
var _round_shown := 0

func _announce_round() -> void:
	if battle.round_num == _round_shown or battle.finished: return
	_round_shown = battle.round_num
	if _fx == null: return
	var vp := get_viewport_rect().size
	_fx.announce("ROUND %d" % battle.round_num, Color(1.0, 0.90, 0.55),
		Vector2(vp.x * 0.5 - 60.0, vp.y * 0.34))
	_sfx("select")

func _on_beat(_i: int) -> void:
	_beat_pulse = 1.0
	_sfx("beat")

func _process(delta: float) -> void:
	_beat_pulse = maxf(0.0, _beat_pulse - delta * 3.0)
	if _beat_hit > 0.0:
		_beat_hit = maxf(0.0, _beat_hit - delta * 0.9)
	if beat_dot: beat_dot.queue_redraw()
	if _notice_t > 0.0:
		_notice_t -= delta
		if _notice_t <= 0.0:
			_notice = ""
			if prompt != null: prompt.text = _prompt_text()
	_tick_rings()
	_place_bench_ghost()
	_guard_gestures(delta)
	_tick_auto(delta)
	_tick_unit_holds(delta)
	_animate_lift(delta)
	# Their side's round, replayed one card at a time. Waits for the stage to be
	# free — your own plays displace it — and for a beat between entries, so a
	# whole room's turn reads as a sequence rather than a shuffle.
	if _replaying:
		_replay_gap -= delta
		if _enemy_replay.is_empty() or battle.finished:
			_replaying = false
			_enemy_replay.clear()
		elif _staged == null and _replay_gap <= 0.0:
			_stage_enemy_next()
	# The card SIZE is derived from how wide one board's share of the party row
	# is, and that row has no width at all on the frame the scene is built. Watch
	# it, and rebuild the hand once it has one - otherwise the very first hand of
	# a fight is sized off a guess and never corrected.
	# ONE SHOT, not a watch. Tiles are sized from the grid's width and adding
	# them changes that width, so comparing the two every frame rebuilt the
	# whole field forever — and a rebuild whose predecessor has not been freed
	# yet leaves TWO Summoner boards stacked in the column, with the pennant
	# drawn over the lower one. It also meant the field was rebuilt sixty
	# times a second for nothing.
	if not _rows_sized and _lift_card == null and ally_box.size.x > 4.0:
		_rows_sized = true
		refresh()
	elif _lift_card == null and absf(ally_box.size.x - _hand_row_w) > 2.0:
		_rebuild_hand()
	if overlay != null: overlay.queue_redraw()
	# The stacks chase the boards they belong to, and the boards move whenever
	# the party row is rebuilt or the window changes shape. Doing it here rather
	# than once after a rebuild means it is correct on the frame the layout
	# solver finally settles, which is never the frame the rebuild happened on.
	_layout_hand()
	_fan_hand()
	# Shake goes through the CANVAS TRANSFORM, not a node position. `Root` is a
	# fully anchored container: moving it fights the layout solver and pushes
	# the hand bar off the bottom of the screen. Shifting the canvas moves
	# everything already composited, costs no layout pass, and returns to
	# exactly zero on its own.
	if _fx != null:
		var vp := get_viewport()
		if vp != null:
			var xf := vp.canvas_transform
			xf.origin = _fx.shake_offset()
			vp.canvas_transform = xf

# Everyone's HP right now, AND where their panel currently sits. Both sides: an
# enemy round hurts the party, and a heal is worth showing too.
#
# The rects are captured HERE, before the action, and not after — refresh()
# rebuilds every panel, and a freshly built Control has no valid size until the
# layout solver has run on the next frame. Reading it straight after a refresh
# puts every number at the origin, sized zero.
func _snapshot_hp() -> Dictionary:
	var hp := {}
	var rects := {}
	for u in battle.field_units():
		hp[u.id] = u.hp
		var panel = _panels.get(u.id, null)
		if panel != null and is_instance_valid(panel) and panel.size.x > 1.0:
			rects[u.id] = Rect2(panel.global_position - global_position, panel.size)
	return {"hp": hp, "rects": rects}

# Turn "HP changed" into something the player can actually see. Called AFTER a
# refresh, because the panels these numbers sit over are rebuilt every time.
func _report_hp(snap: Dictionary) -> void:
	if _fx == null: return
	var before: Dictionary = snap.get("hp", {})
	var rects: Dictionary = snap.get("rects", {})
	for u in battle.field_units():
		if not before.has(u.id): continue
		var delta: int = u.hp - int(before[u.id])
		var died: bool = int(before[u.id]) > 0 and u.hp <= 0
		if delta == 0 and not died: continue
		if died: _sfx("down")
		if not rects.has(u.id): continue
		# A hit worth a third of a health bar reads as a crit whether or not the
		# dice said so — the feedback should match the felt severity.
		var crit: bool = delta < 0 and float(-delta) / maxf(1.0, float(u.max_hp)) > 0.33
		_fx.report(u.id, delta, rects[u.id], crit, died)
	if battle.finished and battle.victory:
		_sfx("victory")

func _on_log(t: String) -> void:
	log_box.append_text(t + "\n")
	log_box.scroll_to_line(log_box.get_line_count() - 1)

# ------------------------------------------------------------ team pennants

func _on_overlay_draw() -> void:
	_draw_stage_plinth()
	for u in [battle.summoner, battle.enemy_summoner]:
		if u == null: continue
		var panel = _panels.get(u.id, null)
		if panel == null or not is_instance_valid(panel): continue
		var r: Rect2 = (panel as Control).get_global_rect()
		if r.size.x < 2.0: continue
		_draw_pennant(r, u)

# THE EMPTY STAGE IS STILL MARKED. A hole between three cards and two, with
# nothing in it, reads as a layout bug -- which is exactly what the floor dots
# under an empty place exist to prevent, and this is the same argument at the
# scale of the whole middle. A hairline and an arc at its foot, in the colour the
# hand's own divider already uses, so the two marks belong to one language.
#
# Drawn on the OVERLAY rather than being a node: a node in the field would take a
# row of the column, and the column is zero-sum.
func _draw_stage_plinth() -> void:
	var sr := _stage_rect()
	if sr.size.x < 4.0 or overlay == null: return
	var r := Rect2(sr.position - overlay.global_position, sr.size)
	if _staged != null and is_instance_valid(_staged):
		# Only while something is standing on it, so a played card reads against
		# a quiet ground instead of fighting the room illustration.
		overlay.draw_rect(r, Color(0.04, 0.05, 0.07, 0.42))
	overlay.draw_rect(r, Color(0.96, 0.90, 0.72, 0.10), false, 1.5)
	overlay.draw_arc(Vector2(r.get_center().x, r.end.y - 14.0), r.size.x * 0.30,
		0.0, TAU, 32, Color(0.96, 0.90, 0.72, 0.09), 1.0)

# Points DOWN at the board it belongs to, sitting just above it, and EMPTIES as
# that Summoner is worn down.
#
# The fill grows from the point upward, so what you are reading is how far the
# colour has climbed rather than an area you would have to estimate. At full
# health it is the whole triangle; at none it is nothing, with the outline left
# behind so an empty one still reads as a thing that used to be full rather than
# as a pennant that failed to draw.
func _draw_pennant(r: Rect2, u: Unit) -> void:
	var w := TEAM_TRI_W
	var h := TEAM_TRI_H
	var o: Vector2 = overlay.global_position
	var cx: float = r.position.x + r.size.x * 0.5 - o.x
	# The tip TOUCHES the board it belongs to. A gap here put the triangle up
	# into the row label above it, which is the one thing on that line it must
	# not sit on.
	var top: float = r.position.y - o.y - h
	var col: Color = COL_TEAM_ENEMY if u.is_enemy else COL_TEAM_ALLY
	var apex := Vector2(cx, top + h)
	var full := PackedVector2Array([
		Vector2(cx - w * 0.5, top), Vector2(cx + w * 0.5, top), apex])

	# A dark copy underneath, because the room illustration behind this is busy
	# and a flat colour on its own disappears into the brighter parts of it.
	var back := PackedVector2Array()
	for v in full: back.append(v + Vector2(0, 2.0))
	overlay.draw_colored_polygon(back, Color(0, 0, 0, 0.6))

	var frac: float = 0.0
	if u.max_hp > 0: frac = clampf(float(u.hp) / float(u.max_hp), 0.0, 1.0)
	if frac > 0.001:
		var base_y: float = top + h * (1.0 - frac)
		var half: float = w * 0.5 * frac
		overlay.draw_colored_polygon(PackedVector2Array([
			Vector2(cx - half, base_y), Vector2(cx + half, base_y), apex]), col)
	# The empty part still has to be visible, or a Summoner in trouble looks
	# like a Summoner with no pennant.
	overlay.draw_polyline(PackedVector2Array([full[0], full[1], full[2], full[0]]),
		col * Color(1, 1, 1, 0.85), 1.5)

# ------------------------------------------------------------------ auto mode
#
# THE GAME TAKES YOUR TURN. One move at a time, spaced far enough apart to
# watch — a whole round resolving between two frames is not a fight, it is a
# result, and the point of watching is seeing what happened.
#
# The POLICY lives in core/autoplay.gd, not here. This is only the clock: pick a
# move, play it, wait, and hand the turn over when there is nothing left worth
# doing. That split is what lets the smoke test exercise the same brain the
# player is watching.
#
# It stops the moment you switch it off, and it never runs while a card is in
# your hand mid-drag — auto taking a turn out from under a gesture is exactly
# the kind of thing that strands a card on the cursor.
const AUTO_STEP := 0.55        # seconds between one auto move and the next
const AUTO_HANDOVER := 0.75    # a longer beat before it ends the turn

var auto_on := false
var _auto_t := 0.0

func _on_auto_toggled(on: bool) -> void:
	auto_on = on
	_auto_t = AUTO_STEP
	_style_auto()
	var sess = _sess()
	if sess != null:
		var st: Dictionary = sess.data.get("settings", {})
		st["auto_battle"] = on
		sess.data["settings"] = st
		sess.save()
	_sfx("select")
	refresh()

func _style_auto() -> void:
	if auto_btn == null: return
	auto_btn.text = "AUTO  ●" if auto_on else "AUTO"
	auto_btn.modulate = Color(1.0, 0.86, 0.42) if auto_on else Color(1, 1, 1)
	auto_btn.tooltip_text = ("The game plays your side for you, a move at a time. "
		+ "Switch it off at any point and the turn is yours again.")

func _tick_auto(delta: float) -> void:
	if not auto_on or battle.finished: return
	if not battle.is_player_turn(): return
	# Never mid-gesture, and never over the top of a panel the player has open.
	if _lift_card != null or selected_card != null: return
	if _reserve_ui != null and is_instance_valid(_reserve_ui): return
	_auto_t -= delta
	if _auto_t > 0.0: return
	var mv := AutoPlay.next_move(battle)
	if mv.is_empty():
		_auto_t = AUTO_HANDOVER
		_on_end_round_pressed()
		return
	_auto_t = AUTO_STEP
	var hp_before := _snapshot_hp()
	var target: Unit = mv.get("target", null)
	var rect := _panel_rect(target)
	if AutoPlay.take_move(battle, mv):
		if str(mv.get("kind", "")) == "ultimate":
			_sfx("ultimate")
			_fx.kick(0.9)
		else:
			var c: Card = mv["card"]
			_spawn_vfx(c, target, rect)
			if c.effective_heal() > 0.0: _sfx("heal")
			elif c.effective_power() > 0.0: _sfx("hit")
			else: _sfx("card")
	refresh()
	_report_hp(hp_before)

# --------------------------------------------------------- rhythm indicator

func _on_beat_dot_draw() -> void:
	var b = _beat()
	if b == null: return
	var c := beat_dot
	var w: float = c.size.x
	var h: float = c.size.y
	# HORIZONTAL. The tempo used to bounce up a tall column down the left edge;
	# in a top bar there is no height to bounce in, so it runs along a line
	# instead and the beat is the sweep rather than the climb.
	var ph: float = b.phase()
	var bounce: float = abs(sin(PI * ph))
	var cy: float = h * 0.5
	var cx: float = 14.0 + bounce * maxf(10.0, w - 88.0)
	var acc: float = b.accuracy()
	var col := Color(0.45,0.5,0.6).lerp(Color(0.4,1.0,0.65), acc)
	c.draw_line(Vector2(8, cy), Vector2(w - 74.0, cy), Color(0.3,0.33,0.4), 2.0)
	c.draw_circle(Vector2(cx, cy), 9.0 + 4.0 * _beat_pulse, col)
	if acc >= 0.75:
		c.draw_arc(Vector2(cx, cy), 15.0, 0, TAU, 24, Color(0.5,1.0,0.7,0.7), 2.0)
	c.draw_string(ThemeDB.fallback_font, Vector2(w - 66.0, cy + 4.0),
		"%.0f BPM" % b.bpm, HORIZONTAL_ALIGNMENT_LEFT, 70.0, 11, Color(0.6,0.65,0.75))
	# quiet confirmation that an action landed well, however it was earned
	if _beat_hit > 0.0 and _beat_hit_text != "":
		var a: float = clampf(_beat_hit, 0.0, 1.0)
		c.draw_arc(Vector2(cx, cy), 20.0 + (1.0 - a) * 14.0, 0, TAU, 26,
			Color(0.55, 1.0, 0.75, a * 0.8), 2.0)
		c.draw_string(ThemeDB.fallback_font, Vector2(8.0, h + 12.0), _beat_hit_text,
			HORIZONTAL_ALIGNMENT_LEFT, w + 40, 13, Color(0.6, 1.0, 0.78, a))

# ------------------------------------------------------------------ refresh

func refresh() -> void:
	# The round and the two commander meters. The QUEUE used to live on this
	# line as a run of first names; it is a row of faces now — see
	# _rebuild_order_strip — because "who is next" is a thing you glance at
	# fifty times a fight and read never.
	# CP and SP are BOTH ON THE SUMMONER'S BOARD, which is where they are spent
	# from and which is now a card you can read at a glance -- printing them here
	# as well was a second copy taking the room the turn order wanted.
	status_bar.text = "Round %d%s" % [battle.round_num,
		("   — BATTLE OVER —" if battle.finished else "")]
	_rebuild_order_strip()
	_rebuild_detail()
	# Ending the round with somebody unused is almost always a mistake and never
	# a plan, so the button that does it says how many.
	var idle: int = battle.unspent_allies()
	end_turn_btn.text = "End Turn  →" if (idle <= 0 or battle.finished) \
		else "End Turn  →   (%d unused)" % idle
	end_turn_btn.disabled = battle.finished
	_size_rails()
	_rebuild_rails()
	_rebuild_units(ally_box, battle.allies, false)
	_rebuild_units(enemy_box, battle.enemies, true)
	_rebuild_hand()
	_refresh_reserve_ui()
	if battle.finished: _close_discard()
	if battle.finished: _close_dossier()
	_refresh_end_ui()
	_announce_round()
	prompt.text = _notice if _notice != "" else _prompt_text()

# What the prompt says when nothing has just gone wrong.
func _prompt_text() -> String:
	if battle.finished:
		return ("VICTORY — leave to claim your haul."
			if battle.victory else "DEFEAT — your haul is at risk.")
	if selected_card != null:
		var who := selected_actor.display_name if selected_actor != null else "Summoner"
		return "%s  (%s) — choose a target." % [selected_card.display_name, who]
	if not battle.is_player_turn():
		return "The other side is moving..."
	if auto_on:
		return "AUTO - the game is taking your turn. Switch it off to take over."
	var left: int = battle.unspent_allies()
	if left <= 0:
		return "Everyone has moved. End Turn to hand it over."
	# Named for whoever is next, but the whole side can act — the old line said
	# "X's turn", which is exactly the impression that was wrong.
	var turn_u: Unit = battle.current_unit()
	var who := (" — %s is fastest" % turn_u.display_name.split(" ")[0]) if turn_u != null else ""
	return ("Your move: %d unit%s still to act%s. Drag a card to play it, or drag a "
		+ "party member onto an enemy to swing — or onto one of your own cells to move there.") % [
		left, "" if left == 1 else "s", who]

# Something the player needs told RIGHT NOW — almost always why a play was
# refused. It survives the refreshes that follow it and then expires quietly.
const NOTICE_SECONDS := 3.0
var _notice := ""
var _notice_t := 0.0

func _say(text: String) -> void:
	_notice = text
	_notice_t = NOTICE_SECONDS
	if prompt != null: prompt.text = text

# ------------------------------------------------------------- the turn order
#
# A ROW OF FACES, left to right, whoever is acting first and biggest. Names told
# you the order and nothing else; a portrait tells you the order AND which side
# it belongs to AND which of two identical creatures it is, in the same space.
#
# It is rebuilt from `battle.order` every refresh rather than cached, because a
# Haste or a Slow landing this round genuinely reshuffles it.
func _rebuild_order_strip() -> void:
	for c in order_box.get_children(): c.queue_free()
	var up: Unit = battle.current_unit()
	# WHO HAS A MOVE LEFT — yours that have not spent their AP, then theirs.
	# It used to list everyone from the current slot onward, which on a
	# side-based round said nothing you could act on; this answers "have I
	# forgotten somebody", which is the question the round actually poses.
	var queue: Array = battle.upcoming()
	var shown := 0
	var waiting := queue.size()
	for qu in queue:
		if shown >= ORDER_SHOWN: break
		shown += 1
		order_box.add_child(_order_chip(qu, qu == up, shown))
	if waiting > shown:
		var more := Label.new()
		more.add_theme_font_size_override("font_size", 12)
		more.add_theme_color_override("font_color", Color(0.62, 0.66, 0.76))
		more.text = "+%d" % (waiting - shown)
		more.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
		order_box.add_child(more)

# One face in the queue. The frame carries the side — blue for yours, red for
# theirs — and gold when it is that unit's turn right now.
func _order_chip(u: Unit, is_up: bool, place: int) -> Control:
	var sz: float = ORDER_ICON_UP if is_up else ORDER_ICON
	var frame := PanelContainer.new()
	frame.custom_minimum_size = Vector2(sz, sz)
	frame.mouse_filter = Control.MOUSE_FILTER_PASS
	var sb := StyleBoxFlat.new()
	sb.bg_color = Color(0.06, 0.07, 0.10, 0.85)
	sb.set_border_width_all(3 if is_up else 2)
	sb.border_color = (Color(1.0, 0.84, 0.32) if is_up
		else (Color(0.85, 0.36, 0.34) if u.is_enemy else Color(0.36, 0.70, 0.95)))
	sb.set_corner_radius_all(6)
	sb.set_content_margin_all(2)
	frame.add_theme_stylebox_override("panel", sb)
	frame.tooltip_text = "%d. %s%s" % [place, u.display_name,
		"  — acting now" if is_up else ""]

	var face: Texture2D = Assets.texture("monster", u.species) if u.is_enemy 		else Assets.texture("portrait", u.id)
	if face != null:
		var pic := TextureRect.new()
		pic.texture = face
		pic.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		# COVERED, deliberately, and the one place in this scene that crops. A
		# chip this small showing a whole portrait would be a person-shaped
		# smudge between two bars; a crop of the face is legible.
		pic.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_COVERED
		pic.mouse_filter = Control.MOUSE_FILTER_IGNORE
		frame.clip_contents = true
		frame.add_child(pic)
	else:
		var ini := Label.new()
		ini.text = u.display_name.substr(0, 1).to_upper()
		ini.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		ini.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
		ini.mouse_filter = Control.MOUSE_FILTER_IGNORE
		frame.add_child(ini)
	if not is_up:
		frame.modulate = Color(1, 1, 1, 0.8)
	return frame

# THE SIZE OF A BOARD IS A PROPERTY OF THE ROW, not of the window. It used to be
# the window alone, so a row of four and a row of seven drew the same 186px
# board: the short row spread its four over the whole width with a board's worth
# of air between each, and the long row ran off the end.
#
# Now each row takes the biggest board that fits it and packs them against the
# left. The party line, being the shorter one, ends up with noticeably LARGER
# boards than the enemy line — which is the right way round, because those are
# the ones whose art you are meant to be looking at.

# THE GRID IS ZONES, NOT CARDS. Three rows a side and two sides is SIX rows of
# boards on one screen; at the size a board used to be that is twice the height
# there is. So a cell is a compact tile — a face, a name and a health bar — and
# the CARD it stands for is drawn large in the detail panel beside the field.
#
# That is the trade the grid forces, and it is the right way round: the tiles
# carry position, which is what the grid is for, and the panel carries the art,
# which is what wants the room.
# FOUR ROWS OF BOARDS NOW, not six — dropping the third row is what buys this.
# A tile can finally be most of a card rather than a stripe with a name on it.
#
# Trimmed from 132 to hand the difference to the hand: the column is zero-sum,
# and a card you have to read is worth more height than a tile you glance at.
# The tile heights are no longer a constant. A fixed 118 made a party board a
# thumbnail on every screen taller than the minimum, which is what "the unit
# cards are half size" was: the column had height going spare and nothing was
# claiming it. So the two grids now DIVIDE what is actually left after the hand
# and the chrome, and your side takes the larger share -- your boards are the
# ones being read, theirs are the ones being pointed at.
# The art's own shape, wide-to-tall. Every tile is cut to it so a fitted picture
# fills the card edge to edge with no letterbox and no crop.
const CARD_ASPECT := 0.68
# A BOARD CARD IS A TOKEN, not a card face. Everything a full card carries is
# one hover away already, so the thing standing on the board only has to answer
# "who is this and how are they doing" -- picture, name, health. Dropping the
# role, the numbers and the "behind X" line off it is what lets it be smaller
# than the width allows instead of fighting for every pixel of it: the board
# reads as a board again rather than as eight competing card faces.
# BARELY BIGGER THAN A UNIT CARD. Big enough that the board reads as the one
# that matters, close enough that the line does not read as two classes of card.
const SUMMONER_CARD_SCALE := 1.08
# Air between the two lines, so they read as facing each other rather than as
# one block of ten.
const RANK_GAP := 18.0
# What the hand asks for before the boards are measured, and the floor it will
# give ground down to.
# THE VIEWPORT IS 1600x900 WHATEVER THE WINDOW IS -- project.godot stretches
# canvas_items from that base, so a 1920 window is the same layout upscaled.
# There is one screen size to design for, and BOARD_SCALE_MAX never binds at it:
# the number that decides how big a unit card is, is this one. The hand asks for
# less, the boards get the difference.
const HAND_SCALE_TARGET := 0.56
const HAND_SCALE_MIN := 0.50
const BOARD_SCALE_MIN := 0.46
# A ceiling as well as a floor: with one rank a side the equation would happily
# hand the boards everything the hand did not take, and a card taller than a
# third of the screen stops being a board and starts being a splash screen.
const BOARD_SCALE_MAX := 0.74
# Between one place in the line and the next.
const SLOT_GAP_MIN := 10.0

# RAIL WIDTHS COME OFF THE BOARD SCALE AND NOTHING ELSE. _process rebuilds the
# hand whenever the field's width moves by two pixels; a rail whose width came
# from the field's width would drive that watcher round in circles forever.
# _board_scale is a pure function of the window's HEIGHT, so this cannot.
const RAIL_PAD := 10.0
const DETAIL_H := 250.0
const RAIL_SUMMONER_SCALE := 0.80
const RAIL_RIGHT_SCALE := 1.20

func _left_rail_w() -> float:
	return clampf(CardView.W * _board_scale() * RAIL_SUMMONER_SCALE + RAIL_PAD * 2.0,
		150.0, 260.0)

func _right_rail_w() -> float:
	return clampf(CardView.W * _board_scale() * RAIL_RIGHT_SCALE + RAIL_PAD * 2.0,
		190.0, 260.0)

# What is left for the two grids, once the rails and Body's separations are
# taken out. Derived rather than measured, so it is right on the first frame --
# a container's width is zero until the layout solver has run at least once.
func _field_w() -> float:
	var vp := get_viewport_rect().size
	return maxf(320.0, vp.x - ROOT_MARGIN - _left_rail_w() - _right_rail_w()
		- BODY_SEPARATION * 2.0)

const BODY_SEPARATION := 28.0

# TWO GRIDS, SIDE BY SIDE. Yours stands on the left of the field and theirs on
# the right, each a 2x3 block — three across, two deep — and the stage is the
# column of air BETWEEN them, where a played card goes to be seen. The lines no
# longer split around the stage; the stage is simply what the gap between the
# two grids is.
const STAGE_PAD := 12.0            # air inside the stage, around the card

func _lane_pitch() -> float:
	var cw: float = CardView.W * _board_scale()
	return cw + _slot_gap(cw)

# The gap between two places in a grid, and the one between a grid and the
# stage. Capped at a third of a card: past that the grid stops reading as a
# grid and becomes six cards that happen to share a rectangle.
func _slot_gap(cw: float) -> float:
	return clampf(cw * 0.16, SLOT_GAP_MIN, cw * 0.34)

# One grid's width, from its own lane count.
func _grid_w(cw: float, lanes: int) -> float:
	return float(lanes) * cw + float(maxi(0, lanes - 1)) * _slot_gap(cw)

func _slot_x(lane: int, is_enemy: bool = false) -> float:
	var cw: float = CardView.W * _board_scale()
	var x: float = float(lane) * _lane_pitch()
	if is_enemy:
		# THEIRS ARE MIRRORED, so the two grids face each other: their lane 0
		# stands nearest the middle, over your lane 0.
		var lanes: int = battle.enemy_board.lanes
		x = float(lanes - 1 - lane) * _lane_pitch()
	return x

# GLOBAL, and Rect2() until the layout has solved -- the same "there is nothing
# to aim at yet" answer _panel_rect and _deck_rect already give, met for the
# fourth time on this screen. Every caller checks it.
func _stage_rect() -> Rect2:
	if _line_ally == null or not is_instance_valid(_line_ally): return Rect2()
	if _line_enemy == null or not is_instance_valid(_line_enemy): return Rect2()
	if _line_ally.size.x < 4.0 or _line_enemy.size.x < 4.0: return Rect2()
	var left: float = _line_ally.global_position.x + _line_ally.size.x
	var right: float = _line_enemy.global_position.x
	var top: float = minf(_line_ally.global_position.y, _line_enemy.global_position.y)
	var bot: float = maxf(_line_ally.global_position.y + _line_ally.size.y,
		_line_enemy.global_position.y + _line_enemy.size.y)
	if right - left < 40.0 or bot - top < 40.0: return Rect2()
	return Rect2(Vector2(left, top), Vector2(right - left, bot - top))

# The biggest card that fits the stage on BOTH axes, so the stage decides the
# size rather than a constant that needs re-tuning every time the column moves.
func _stage_card_scale() -> float:
	var r := _stage_rect()
	if r.size.x < 4.0: return 0.0
	return minf((r.size.x - STAGE_PAD * 2.0) / CardView.W,
		(r.size.y - STAGE_PAD * 2.0) / CardView.H)

# The two lines, kept so the stage can measure the band they enclose.
# WHAT THE STAGE IS FOR. A card played used to simply vanish out of the hand --
# the numbers moved on the board and a line appeared in the log, and the card
# itself, the thing with the art and the name on it, was never seen. Now it goes
# to the middle, at reading size, plays its clip if it has one, and leaves for
# the discard.
#
# A COPY, on the overlay. The real cards are laid out by _fan_hand every frame
# and freed wholesale by _rebuild_hand; tweening one would be two things writing
# the same position, and the refresh that follows every play would delete it
# mid-flight. This is the same reason the draw flight animates a copy.
const STAGE_FLY_IN := 0.22
const STAGE_FLY_OUT := 0.24
const STAGE_HOLD := 0.45           # long enough to read the name
const STAGE_HOLD_CLIP := 1.30      # long enough to watch the clip move
const STAGE_HOLD_AUTO := 0.15      # AUTO_STEP is 0.55; do not fall behind the game
const STAGE_Z := 200               # over the board; under the draw flight and the lift

var _staged: CardView = null
var _staged_tween: Tween = null

var _line_ally: Control = null
var _line_enemy: Control = null
# Air between the line and the top of the fan. The fan's focused card rises out
# of the row by HAND_RAISE, and without this it rises into the party's feet.
const HAND_CLEARANCE_FRAC := 0.063
const HAND_CLEARANCE_MIN := 44.0
const HAND_CLEARANCE_MAX := 68.0
# The width their place takes out of the field, in lanes. RESERVED ON BOTH
# SIDES even when a room has no enemy Summoner: skipping it there made their
# lanes wider than yours and the two lines stopped facing each other.
# NOTHING OVERLAPS. Tucking the back rank behind the front one was the only way
# to fit four ranks of half-again-bigger cards into one screen height, and it
# was refused: a card you can only half see is not a card. So the ranks are
# spaced clear of each other and the size is whatever four clear ranks plus the
# hand can actually be given -- which is what the number below is measuring.
# THE RANKS ARE STAGGERED. The back rank steps half a lane sideways, and once
# it does the two ranks can close up vertically without any two cards touching
# -- half a lane is wider than a card, so a card in the back rank passes between
# two in the front. That is where the room for bigger cards comes from, and
# nothing is covered by anything.
# EXACTLY HALF A LANE, and there is no arguing with it. A staggered card has to
# clear the front-rank card on BOTH sides of it, and those two clearances are
# step and (lane - step) -- so anything other than half a lane makes one of them
# smaller and the cards start touching on that side. Half a lane is the widest
# the gap can be, and half a lane is therefore the widest a card can be.
# A card must stay narrower than the gap it passes through, or the stagger stops
# being clearance and starts being an overlap.
# BOTH LINES ARE THE SAME SIZE. Their cards used to take the smaller share and
# ended up reading as action cards parked on the board -- but a unit is a unit
# whichever side of the field it stands on, and the two lines have to compare at
# a glance. Equal shares of what the column can spare, and the party's own cards
# give up the difference: there is nowhere else for it to come from.

# Labels over each grid, the gap between them, and the grids' own separations.
# The labels over each grid, the gap between the two of them, and the grids'
# own separations. UNDERSTATED, this is a column that overflows: the tiles take
# height that is not there and the hand is pushed off the bottom of the screen.
# Root's own top and bottom offsets, the top bar, and the column's separations.
# The prompt line used to be in here too; it is gone from the screen entirely.
const CHROME_H := 116.0
# The grids own separations and the gap between them. The two row labels used to
# be in here as well -- "GKOM CORRUPTED" over one line and "PARTY" over the
# other, saying what the colour of every border on that line already said.

# The card, height first: the column is the scarce axis, so height is what the
# budget hands out and width follows from the aspect. A lane narrower than that
# shrinks BOTH -- a card made to fit its lane by growing fatter would be
# cropping the picture again under another name.
# THE COLUMN IS THE ONLY BINDING AXIS NOW, and this is the whole of it:
#
#   vp.y = CHROME_H + 2*board + RANK_GAP + hand
#
# One rank a side instead of two means five slots across a widescreen field come
# to roughly half its width, so a card's width never decides anything any more —
# which is what eleven rebuilds of this screen were fighting. The board scale is
# a pure function of the window's HEIGHT, and the window's height is known on
# the first frame, so the board no longer has to be built twice.
# Returns (board scale, hand scale). ONE equation with two unknowns, solved in
# one place: the two used to be worked out independently and the sum was
# whatever it happened to be, which is how the hand ended up over the board and
# off the bottom of the screen in the same build.
#
# The hand gives ground before the boards do — the boards are what is being read
# and a hand at half size is still legible.
func _solve_scales() -> Vector2:
	var vp := get_viewport_rect().size
	var hs: float = HAND_SCALE_TARGET
	var rows := _board_rows()
	for _step in 8:
		var left: float = vp.y - CHROME_H - float(rows - 1) * RANK_GAP \
			- _hand_clearance() - _hand_height_at(hs)
		var bs: float = left / (float(rows) * CardView.H)
		if bs >= BOARD_SCALE_MIN or hs <= HAND_SCALE_MIN:
			return Vector2(clampf(bs, 0.24, BOARD_SCALE_MAX), hs)
		hs -= 0.04
	return Vector2(BOARD_SCALE_MIN, HAND_SCALE_MIN)

# THE TALLEST BOARD ON THE FIELD, which is the one the card size has to fit.
# Yours is always two ranks; theirs grows with the room — three ranks deep when
# the room fields more than six — and both grids share one card size, so the
# deeper one is what the scale is solved against.
func _board_rows() -> int:
	if battle == null: return 2
	return maxi(battle.board.rows, battle.enemy_board.rows)

func _board_scale() -> float:
	return _solve_scales().x

# The overlap that makes N cards cover HAND_FILL of the row they have. Clamped at
# both ends: never looser than 0.28, because eight cards spread over the whole
# screen read as a picket fence rather than a hand, and never tighter than the
# packed default.
func _solve_overlap(n: int, hs: float, span: float) -> float:
	if n < 2: return STACK_OVERLAP
	var want: float = HAND_FILL * span / maxf(1.0, CardView.W * hs)
	return clampf((float(n) - want) / float(n - 1), 0.28, STACK_OVERLAP)

# The overlap the hand was last built at. Everything that measures the run --
# the stack separation, the group gap, the spread -- has to agree with it or the
# groups drift apart from the cards inside them.
var _overlap_cached := STACK_OVERLAP

# What a hand at this scale actually stands, tilted corners included — the outer
# cards of a fan reach lower than the middle ones, and forgetting that is how
# the hand ended up hanging off the bottom of the screen.
# AIR BETWEEN THE LINE AND THE FAN, and it has to be bigger than the fan's own
# movement or the two touch. The focused card rises HAND_RAISE out of the row and
# the arc lifts the middle FAN_RISE on top of that -- 50px of rise, into a gap
# that was a flat 40. That is why the hand was resting against the party's feet
# with a third of the screen empty on either side.
#
# The HOVER POP is exempt on purpose: set_pop(2.0) stands a card 254px above the
# row, and clearing that would eat the board. It is transient, z-ordered and the
# thing you are looking at.
func _hand_clearance() -> float:
	return clampf(get_viewport_rect().size.y * HAND_CLEARANCE_FRAC,
		HAND_CLEARANCE_MIN, HAND_CLEARANCE_MAX)

func _hand_height_at(hs: float) -> float:
	return (CardView.H + sin(deg_to_rad(FAN_MAX_DEGREES)) * CardView.W) * hs


# How far past the window's own scale a board may grow when its row is short.
# Without a ceiling a party of two would draw boards half the screen tall.
# AND THE ROOM HAS TO COME FROM SOMEWHERE. A board grows in both directions, so
# a short row that grew to fill its width also grew tall enough to push the hand
# off the bottom of the screen — which is what a three-creature room did.
#
# The column is the real constraint, and the hand gets first claim on it: the
# card faces are the thing being read, and soon the thing carrying moving art.
# Whatever height is left over after the hand and the chrome, the two rows split.
const HAND_MIN_H := 104.0
# How much of its available size the hand actually takes.
const HAND_TRIM := 0.90

# The scale currently being built with. _unit_panel and _summoner_panel read it
# rather than taking it as an argument, because every font size inside them is
# derived from it too.
var _panel_f := 1.0
# The exact footprint a Summoner's board is built to: a unit card's, so the two
# read as the same class of thing standing in different places.
var _panel_size := Vector2.ZERO



func _unit_scale() -> float:
	return _panel_f

var _rows_sized := false
# Your side's cells by "row,lane", so a drag can find the one it was dropped on.
var _cells: Dictionary = {}

# The cell of yours under this point, or (-1,-1). Empty cells count: dropping
# somebody into a hole is the ordinary way to move.
func _ally_cell_under(at: Vector2) -> Vector2i:
	for key in _cells:
		var c = _cells[key]
		if c == null or not is_instance_valid(c): continue
		if not (c as Control).get_global_rect().has_point(at): continue
		var parts: PackedStringArray = str(key).split(",")
		return Vector2i(int(parts[0]), int(parts[1]))
	return Vector2i(-1, -1)

# An empty cell is still drawn. A grid that only showed occupied zones would be
# a line again, and where the holes are is exactly what you are reading.

# The rails' blocks are given exactly a rank's height, which is what puts them in
# register with the grids. Called from refresh(), beside the grid builders.
func _size_rails() -> void:
	if left_rail == null: return
	var bs := _board_scale()
	var chh: float = CardView.H * bs
	left_rail.custom_minimum_size = Vector2(_left_rail_w(), 0)
	right_rail.custom_minimum_size = Vector2(_right_rail_w(), 0)
	for block in [left_top, left_bottom, right_top, right_bottom]:
		block.custom_minimum_size = Vector2(0, chh)
	# The readout was anchored to the screen's bottom-right corner, which is now
	# the bench's corner. It steps inboard of the rail rather than sitting on it.
	# THE READOUT MOVES INTO THE RAIL, under the deck. Every other place it could
	# go is now somebody's: the corner it sat in is the hand's, the band above
	# that is the line's, and a panel wide enough to want the middle would be
	# sitting on the board. In the rail it is narrow and tall, which suits a list
	# of short lines, and it is the only thing down there.
	if detail_strip != null:
		detail_strip.offset_left = -(_right_rail_w() - RAIL_PAD)
		detail_strip.offset_right = -RAIL_PAD
		detail_strip.offset_bottom = -14.0
		detail_strip.offset_top = -(14.0 + DETAIL_H)

# Torn down and rebuilt with the boards, so there is no incremental-update path
# to drift out of step with the fight.
# A PILE, DRAWN AS A PILE. One builder for both of them: the deck and the
# discard are the same object seen from opposite ends, and drawing them the same
# way is what makes "spent" and "left" read as two halves of one number.
#
# It draws itself rather than being assembled out of labels, because the stack of
# edges behind the face is what says how much is left before you read the digits.
func _pile_chip(is_deck: bool, w: float, h: float) -> Button:
	var chip := Button.new()
	chip.custom_minimum_size = Vector2(w, h)
	chip.flat = true
	chip.focus_mode = Control.FOCUS_NONE
	chip.mouse_filter = Control.MOUSE_FILTER_STOP
	chip.draw.connect(_draw_pile.bind(chip, is_deck))
	if is_deck:
		var lines: Array = ["The team's deck — %d left, %d spent."
			% [battle.draw_remaining(), battle.used_count()],
			"One deck for the whole party. It reshuffles from the discard when it",
			"runs dry, and a card belonging to somebody who is down is put back.",
			"The party holds %d between them; the Summoner holds %d of their own."
				% [Battle.ALLY_HAND_MAX, Battle.SUMMONER_HAND_MAX], "",
			"Still in the deck:"]
		for row in battle.pile_breakdown():
			lines.append("  %s: %d" % [row["who"], row["left"]])
		chip.tooltip_text = "\n".join(lines)
	else:
		chip.tooltip_text = "%d cards spent. They come back when the deck runs dry." \
			% battle.used_count()
	return chip

func _draw_pile(chip: Control, is_deck: bool) -> void:
	if battle == null: return
	var w: float = chip.size.x
	var h: float = chip.size.y
	var n: int = battle.draw_remaining() if is_deck else battle.used_count()
	var f := ThemeDB.fallback_font
	var k: float = clampf(h / 120.0, 0.5, 1.2)          # the chip's own scale
	var tint: Color = Color(0.62, 0.68, 0.85) if is_deck else Color(0.72, 0.60, 0.60)

	# The stack thins as it empties: three edges when it is full, one when it is
	# nearly out, none at all when it is.
	var leaves: int = 0
	if n > 0: leaves = clampi(int(ceil(float(n) / 4.0)), 1, DECK_LEAVES)
	var step: float = maxf(2.0, 3.0 * k)
	var face := Rect2(0.0, 0.0, w, h)
	for i in range(leaves, 0, -1):
		var o: float = float(i) * step
		chip.draw_rect(Rect2(-o, -o, w, h), Color(0.10, 0.11, 0.15))
		chip.draw_rect(Rect2(-o, -o, w, h), Color(0.38, 0.42, 0.55, 0.9), false, maxf(1.0, 2.0 * k))
	if n > 0:
		chip.draw_rect(face, Color(0.13, 0.14, 0.19))
		chip.draw_rect(face, tint, false, maxf(1.0, 2.0 * k))
	else:
		# An empty pile is still drawn, as an outline. A pile that vanished would
		# read as a bug rather than as the thing it is.
		chip.draw_rect(face, Color(0, 0, 0, 0.35))
		chip.draw_rect(face, Color(0.5, 0.42, 0.42, 0.8), false, maxf(1.0, 2.0 * k))
	var big := int(maxf(15.0, 30.0 * k))
	var small := int(maxf(8.0, 11.0 * k))
	chip.draw_string(f, Vector2(0.0, h * 0.56), str(n), HORIZONTAL_ALIGNMENT_CENTER, w, big,
		Color(0.92, 0.94, 0.99) if n > 0 else Color(1.0, 0.62, 0.62))
	chip.draw_string(f, Vector2(0.0, h - 6.0 * k), "DECK" if is_deck else "SPENT",
		HORIZONTAL_ALIGNMENT_CENTER, w, small, Color(0.62, 0.66, 0.76))

# THE SPENT PILE, OPENED OUT. "What is left in the deck" is a question the count
# can only half answer; the other half is what has already gone, and a pile you
# can read is the cheapest way to say it.
#
# It is built on the OVERLAY, never inside hand_box. _rebuild_hand frees every
# child of that node on any refresh, and the gesture suite walks it expecting
# stacks of CardViews — a panel parented there would be destroyed by the next
# draw and would lie to the tests in the meantime.
var _discard_ui: Control = null

# The log opens DOWNWARD over the right rail when it is held. Anything the
# player reaches for in a rail ducks it back to one line first, rather than
# leaving them to close a panel they did not mean to open over the thing they
# were aiming at.
func _duck_log() -> void:
	if _log_size == LogSize.ONE: return
	_log_size = LogSize.ONE
	_size_log()

func _toggle_discard() -> void:
	_duck_log()
	if _discard_ui != null and is_instance_valid(_discard_ui):
		_close_discard()
		return
	if battle == null or battle.discard.is_empty(): return
	_open_discard()

func _open_discard() -> void:
	_close_discard()
	var vp := get_viewport_rect().size
	var root := Control.new()
	root.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	root.mouse_filter = Control.MOUSE_FILTER_STOP
	root.z_index = 250
	overlay.add_child(root)
	_discard_ui = root

	var dim := ColorRect.new()
	dim.color = Color(0.03, 0.04, 0.06, 0.72)
	dim.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	dim.mouse_filter = Control.MOUSE_FILTER_STOP
	dim.gui_input.connect(func(e):
		if e is InputEventMouseButton and e.pressed: _close_discard())
	root.add_child(dim)

	# Inset from the overlay's own rect rather than centred by hand: the overlay
	# is not the screen, and a position worked out in screen coordinates lands
	# wherever the overlay happens to start.
	var panel := PanelContainer.new()
	panel.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	panel.offset_left = vp.x * 0.12
	panel.offset_right = -vp.x * 0.12
	panel.offset_top = vp.y * 0.13
	panel.offset_bottom = -vp.y * 0.13
	var psb := StyleBoxFlat.new()
	psb.bg_color = Color(0.06, 0.07, 0.10, 0.98)
	psb.set_corner_radius_all(14)
	psb.set_content_margin_all(12.0)
	psb.border_color = Color(0.34, 0.38, 0.48)
	psb.set_border_width_all(2)
	panel.add_theme_stylebox_override("panel", psb)
	root.add_child(panel)

	var col := VBoxContainer.new()
	col.add_theme_constant_override("separation", 8)
	panel.add_child(col)

	var head := Label.new()
	head.add_theme_font_size_override("font_size", 16)
	head.text = "  %d cards spent — they come back when the deck runs dry" \
		% battle.discard.size()
	col.add_child(head)

	var scroll := ScrollContainer.new()
	scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	col.add_child(scroll)
	var flow := HFlowContainer.new()
	flow.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	flow.add_theme_constant_override("h_separation", 8)
	flow.add_theme_constant_override("v_separation", 8)
	scroll.add_child(flow)
	# NEWEST FIRST: what was just spent is what you are asking about.
	var shown := battle.discard.duplicate()
	shown.reverse()
	for c in shown:
		var cv := _card_view(c, _hand_view_scale() * 0.72)
		cv.is_overlay = true
		cv.mouse_filter = Control.MOUSE_FILTER_IGNORE
		cv.set_state(true, false)
		flow.add_child(cv)

	var close := Button.new()
	close.text = "Close"
	close.custom_minimum_size = Vector2(0, 34)
	close.pressed.connect(_close_discard)
	col.add_child(close)

func _close_discard() -> void:
	if _discard_ui != null and is_instance_valid(_discard_ui):
		_discard_ui.queue_free()
	_discard_ui = null

# EVERYTHING YOU CAN FIRE RIGHT NOW, in one column. The Decree used to be a
# button that appeared inside the Summoner's board when its meter filled, and a
# unit's ultimate a button inside a panel you had to open — so "what can I do"
# was a thing you discovered rather than a place you look. Nothing ready is
# still a row, greyed, with the meter on it: an empty column would collapse and
# change the rail's width, and a rail that changes width restarts the layout.
func _build_powers(into: Control) -> void:
	if into == null or battle == null: return
	var plate := PanelContainer.new()
	plate.set_anchors_and_offsets_preset(Control.PRESET_TOP_WIDE)
	plate.offset_left = RAIL_PAD
	plate.offset_right = -RAIL_PAD
	plate.grow_vertical = Control.GROW_DIRECTION_END
	var sb := StyleBoxFlat.new()
	sb.bg_color = Color(0.05, 0.06, 0.09, 0.80)
	sb.set_corner_radius_all(10)
	sb.set_content_margin_all(8.0)
	sb.border_color = Color(0.30, 0.34, 0.44, 0.9)
	sb.set_border_width_all(1)
	plate.add_theme_stylebox_override("panel", sb)
	into.add_child(plate)
	var col := VBoxContainer.new()
	col.size_flags_vertical = Control.SIZE_SHRINK_BEGIN
	col.add_theme_constant_override("separation", 6)
	plate.add_child(col)

	var head := Label.new()
	head.add_theme_font_size_override("font_size", 11)
	head.add_theme_color_override("font_color", Color(0.70, 0.74, 0.85))
	head.text = "POWERS   SP %d/%d" % [battle.sp, Battle.SUMMONER_SP_MAX]
	col.add_child(head)

	var decree := Button.new()
	decree.add_theme_font_size_override("font_size", 12)
	decree.custom_minimum_size = Vector2(0, 34)
	decree.text = "★ DECREE"
	decree.disabled = battle.finished or not battle.summoner_ultimate_ready()
	decree.tooltip_text = ("The Summoner's own ultimate. Ready at a full SP meter."
		if decree.disabled else "Fire the Summoner's ultimate.")
	decree.pressed.connect(func():
		_duck_log()
		_on_summoner_ultimate())
	col.add_child(decree)

	var any := false
	for u in battle.allies:
		if not u.is_alive() or not u.ultimate_ready(): continue
		any = true
		var b := Button.new()
		b.add_theme_font_size_override("font_size", 11)
		b.custom_minimum_size = Vector2(0, 32)
		b.clip_text = true
		var ult := Content.ultimate_for(u)
		b.text = "★ %s" % (ult.display_name if ult != null else u.display_name)
		b.disabled = not battle.can_fire_ultimate(u)
		b.tooltip_text = ("%s is ready. It spends %d of the Summoner's SP."
			% [u.display_name, Battle.ULT_SP_COST]) if not b.disabled \
			else ("%s is ready, but the Summoner has %d SP of the %d it costs."
				% [u.display_name, battle.sp, Battle.ULT_SP_COST])
		b.pressed.connect(func():
			_duck_log()
			_on_unit_ultimate(u))
		col.add_child(b)

	if not any:
		var none := Label.new()
		none.add_theme_font_size_override("font_size", 10)
		none.add_theme_color_override("font_color", Color(0.55, 0.58, 0.66))
		none.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		none.text = "No unit ultimate is charged. They fill by acting."
		col.add_child(none)

# THE BENCH, ON THE BOARD. Who stands on the line was a decision buried behind a
# button that opened a modal over the fight; here it is a short column of cards
# you drag onto whoever they are relieving. The button stays as the header of
# that column -- it is the only swap route with tests on it, and a drag is not a
# reason to delete the door that works.
func _build_bench(into: Control, avail_h: float) -> void:
	if into == null or battle == null: return
	var col := VBoxContainer.new()
	col.set_anchors_and_offsets_preset(Control.PRESET_TOP_WIDE)
	col.offset_left = RAIL_PAD
	col.offset_right = -RAIL_PAD
	col.add_theme_constant_override("separation", 4)
	into.add_child(col)

	var head := Button.new()
	head.add_theme_font_size_override("font_size", 10)
	head.custom_minimum_size = Vector2(0, 26)
	head.text = "⇄ RESERVE  (%d)" % battle.reserves.size()
	head.disabled = battle.reserves.is_empty() or battle.finished
	head.tooltip_text = ("Drag one onto somebody on the line to trade places, or"
		+ " press for the full list.\nWhoever walks on arrives with no AP left this"
		+ " round — unless they are replacing someone who is down, and then it is"
		+ " free.")
	head.pressed.connect(func():
		_duck_log()
		_open_reserve())
	col.add_child(head)
	if battle.reserves.is_empty(): return

	var rows := HFlowContainer.new()
	rows.add_theme_constant_override("h_separation", 4)
	rows.add_theme_constant_override("v_separation", 4)
	col.add_child(rows)

	# Sized to fit the strip the deck leaves above it, two to a row.
	var inner: float = maxf(60.0, _right_rail_w() - RAIL_PAD * 2.0)
	var bw: float = (inner - 4.0) * 0.5
	var bench_scale: float = minf(bw / CardView.W,
		(avail_h - 34.0) / CardView.H)
	# A card too small to recognise is not worth the pixels: on a short screen
	# the strip above the deck cannot hold one, and the header alone -- which
	# opens the full list -- says everything a two-inch thumbnail would.
	if bench_scale < 0.15: return
	bench_scale = minf(bench_scale, 0.42)
	for r in battle.reserves:
		rows.add_child(_bench_card(r, bench_scale))

func _bench_card(u: Unit, bench_scale: float) -> Control:
	var hit := Button.new()
	hit.custom_minimum_size = Vector2(CardView.W, CardView.H) * bench_scale
	hit.flat = true
	hit.focus_mode = Control.FOCUS_NONE
	hit.disabled = battle.finished
	hit.tooltip_text = "%s — %s, %d/%d HP.\nDrag onto somebody on the line to trade." \
		% [u.display_name, Roles.name_of(u.role), u.hp, u.max_hp]
	hit.gui_input.connect(_on_bench_input.bind(u))
	var cv := CardView.new()
	cv.set_view_scale(bench_scale)
	cv.summoner_id = _summoner_id()
	cv.setup_unit(u, false)
	cv.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	hit.add_child(cv)
	if not u.is_alive(): hit.modulate = Color(0.55, 0.55, 0.60)
	return hit

# ------------------------------------------------------------ dragging a body
#
# A PARALLEL DRAG, deliberately not the card lift. _lift/_lift_card are
# Card-shaped end to end -- they are set up from a Card, they are released into
# _commit(card, actor, target), and _rebuild_hand refuses to run while one is
# live. A reserve has no card and its drop calls swap_in, so reusing that
# machinery would mean minting a fake Card and branching five functions that are
# currently honest.
var _bench_drag: Unit = null
var _bench_ghost: CardView = null
var _bench_from := Vector2.ZERO
var _bench_at := Vector2.ZERO

func _on_bench_input(event: InputEvent, u: Unit) -> void:
	if battle == null or battle.finished or u == null: return
	if event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT:
		if event.pressed:
			_close_discard()
			_duck_log()
			_bench_from = event.global_position
			_bench_at = _bench_from
			_bench_drag = u
		else:
			_drop_bench(event.global_position)
	elif event is InputEventMouseMotion and _bench_drag == u:
		_bench_at = event.global_position
		if _bench_ghost == null and \
				_bench_at.distance_to(_bench_from) > CardView.HOLD_SLOP:
			_raise_bench_ghost(u)

func _raise_bench_ghost(u: Unit) -> void:
	_bench_ghost = CardView.new()
	_bench_ghost.is_overlay = true
	_bench_ghost.summoner_id = _summoner_id()
	_bench_ghost.set_view_scale(_board_scale() * 0.7)
	_bench_ghost.setup_unit(u, false)
	_bench_ghost.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_bench_ghost.z_index = 400
	overlay.add_child(_bench_ghost)
	_sfx("select")

# Follows the pointer, beside the card lift rather than through it.
func _place_bench_ghost() -> void:
	if _bench_ghost == null or not is_instance_valid(_bench_ghost): return
	var sz: Vector2 = _bench_ghost.custom_minimum_size
	_bench_ghost.size = sz
	_bench_ghost.position = _bench_at - overlay.global_position - sz * 0.5

func _clear_bench_drag() -> void:
	_bench_drag = null
	if _bench_ghost != null and is_instance_valid(_bench_ghost):
		_bench_ghost.queue_free()
	_bench_ghost = null

func _drop_bench(at: Vector2) -> void:
	var who: Unit = _bench_drag
	_clear_bench_drag()
	if who == null: return
	var cell := _ally_cell_under(at)
	if cell.x < 0: return                       # dropped on nothing: no swap, no noise
	var standing: Unit = battle.board.at(cell.x, cell.y)
	if standing == null:
		# A SWAP IS A TRADE. swap_in needs somebody to trade with, and there is no
		# rules call for walking a reserve into an empty place.
		_say("Drop them onto somebody already on the line — a swap is a trade.")
		return
	if not battle.can_swap(standing, who):
		_say("Not this round — a paid swap waits for your side of the turn order.")
		return
	_do_swap(standing, who)

func _rebuild_rails() -> void:
	if left_rail == null: return
	for block in [left_top, left_bottom, right_top, right_mid, right_bottom]:
		for c in block.get_children(): c.queue_free()
	# EACH HEAD OVER ITS OWN SIDE. Yours stands in the left rail, over your
	# grid; theirs stands in the right rail, over theirs. The block keeps its
	# height even when a room has no enemy Summoner, or the two rails fall out
	# of register with each other and everything below slides.
	_rail_summoner(false, left_top)
	_rail_summoner(true, right_top)
	_build_powers(right_mid)
	# THE TWO PILES SIT AT THE FEET OF THE TWO RAILS, one either side: what is
	# left on the right where the hand comes from, what is spent on the left.
	var bs := _board_scale()
	var chh: float = CardView.H * bs
	# The discard has the block to itself now, and takes a strip of it.
	var d_h: float = clampf(chh * 0.45, 48.0, 130.0)
	var d_w: float = maxf(60.0, _left_rail_w() - RAIL_PAD * 2.0)
	_discard_chip = _pile_chip(false, d_w, d_h)
	var dis := _discard_chip
	dis.position = Vector2(RAIL_PAD, 0.0)
	dis.size = Vector2(d_w, d_h)
	dis.pressed.connect(_toggle_discard)
	left_bottom.add_child(dis)

	var k_w: float = maxf(70.0, _right_rail_w() - RAIL_PAD * 2.0)
	# The block is one card tall and two things share it: the pile takes a third,
	# the bench gets the rest. A pile big enough to read is not big enough to
	# need half.
	var k_h: float = clampf(chh * 0.34, 60.0, 110.0)
	_deck_chip = _pile_chip(true, k_w, k_h)
	_deck_chip.position = Vector2(RAIL_PAD, chh - k_h)
	_deck_chip.size = Vector2(k_w, k_h)
	right_bottom.add_child(_deck_chip)
	# The pile's stack of edges is drawn ABOVE its own rect, so the bench has to
	# stop short of the chip by more than nothing.
	_build_bench(right_bottom, maxf(40.0, chh - k_h - 22.0))

func _rebuild_units(box: Control, _units: Array, is_enemy: bool) -> void:
	# The expanded card is a copy of a card that is about to be freed.
	_hide_unit_zoom()
	for c in box.get_children(): c.queue_free()
	var b: Board = battle.enemy_board if is_enemy else battle.board
	var bs := _board_scale()
	var cw: float = CardView.W * bs
	var chh: float = CardView.H * bs
	var gap := _slot_gap(cw)
	if not is_enemy: _cells.clear()

	# A 2x3 GRID A SIDE, placed from arithmetic rather than by a container, and
	# drawn from the board's OWN shape so the rules and the screen cannot
	# disagree about where anybody is standing. Deriving the x's also means the
	# grid is right on the FIRST frame -- a container's children have no size
	# until the layout solver has run, which is the trap this screen has fallen
	# into three times.
	var grid := Control.new()
	grid.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var gw: float = _grid_w(cw, b.lanes)
	var gh: float = float(b.rows) * chh + float(maxi(0, b.rows - 1)) * RANK_GAP
	grid.size = Vector2(gw, gh)
	box.custom_minimum_size = Vector2(gw, gh)
	box.add_child(grid)
	if is_enemy: _line_enemy = grid
	else: _line_ally = grid
	for r in b.rows:
		for l in b.lanes:
			var slot := Control.new()
			# THEIRS ARE MIRRORED, so the two grids face each other across the
			# stage: their lane 0 stands nearest the middle, over your lane 0.
			var col: int = (b.lanes - 1 - l) if is_enemy else l
			slot.position = Vector2(float(col) * (cw + gap),
				float(r) * (chh + RANK_GAP))
			slot.size = Vector2(cw, chh)
			slot.custom_minimum_size = Vector2(cw, chh)
			slot.mouse_filter = Control.MOUSE_FILTER_IGNORE
			var u: Unit = b.at(r, l)
			if u == null:
				# An empty place is a mark on the floor, and a target while
				# something is in the air — see _draw_zone_ring.
				slot.draw.connect(_draw_zone_ring.bind(slot, is_enemy))
			else:
				slot.add_child(_unit_card(u, is_enemy, b, bs))
			# EVERY place of yours is remembered, empty or not — a move needs to
			# know where the holes are, not just where the bodies are.
			if not is_enemy: _cells["%d,%d" % [r, l]] = slot
			grid.add_child(slot)

# A UNIT, AS THE CARD IT IS. The picture, the name, the health and the wall
# badge, drawn by CardView so the board and the hand share one renderer, one art
# lookup and one media policy rather than drifting apart.
func _unit_card(u: Unit, is_enemy: bool, b: Board, bs: float) -> Control:
	var alive: bool = u.is_alive()
	var exposed: bool = b.is_exposed(u)
	# A Button under the card, because a unit takes a drop, a click-to-target
	# and a drag exactly as it always did — CardView draws, this takes input.
	var hit := Button.new()
	hit.custom_minimum_size = Vector2(CardView.W, CardView.H) * bs
	hit.flat = true
	hit.disabled = not alive or battle.finished
	hit.gui_input.connect(_on_unit_input.bind(u))
	hit.mouse_entered.connect(func():
		_set_focus(u)
		_show_unit_zoom(u, hit))
	hit.mouse_exited.connect(_hide_unit_zoom)
	_panels[u.id] = hit          # HitFeedback and Vfx aim at this rect

	var cv := CardView.new()
	cv.set_view_scale(bs)
	cv.summoner_id = _summoner_id()
	cv.setup_unit(u, is_enemy)
	cv.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	hit.add_child(cv)

	var offered: Card = _lift_card if _lift_card != null else selected_card
	hit.modulate = _tint_for(u, offered, _actor_for(offered) if offered != null else null)
	# SHIELDED IS DRAWN DIFFERENTLY, because "why can I not click that" has to
	# have an answer you can see.
	if not exposed and alive: hit.modulate *= Color(0.74, 0.74, 0.80)
	return hit

func _placing() -> bool:
	# A unit dragged off its own card carries a card too (its basic swing), so
	# this one test covers both of those gestures; a body coming off the bench
	# carries nothing, and is the third.
	return _lift_card != null or selected_card != null or _bench_drag != null

func _draw_zone_ring(c: Control, is_enemy: bool) -> void:
	var r: float = minf(c.size.x, c.size.y) * 0.42
	if r < 4.0: return
	var mid := c.size * 0.5
	var tint: Color = COL_TEAM_ENEMY if is_enemy else COL_TEAM_ALLY
	if not _placing():
		# A FLAT MARK ON THE FLOOR. Drawing nothing at all was quieter but it
		# also hid the shape of the line: with the left lane empty and unmarked,
		# a party standing in lanes two to four reads as a line shoved to the
		# right rather than as a line with a gap at one end of it.
		c.draw_arc(mid, r * 0.34, 0.0, TAU, 20, tint * Color(1, 1, 1, 0.13), 1.0)
		return
	c.draw_circle(mid, r, Color(0.06, 0.07, 0.10, 0.35))
	c.draw_arc(mid, r, 0.0, TAU, 40, tint * Color(1, 1, 1, 0.30), 2.0)
	# A second, tighter ring so an empty place still reads as a MARKED place
	# rather than as a smudge on the floor.
	c.draw_arc(mid, r * 0.62, 0.0, TAU, 32, tint * Color(1, 1, 1, 0.14), 1.0)

# THE SUMMONER STANDS IN THE RAIL, at the outside edge of the screen, in the top
# block of its own side's rail — yours on the left over your grid, theirs on the
# right over theirs. It used to stand at the inside end of the line itself,
# which cost the line a place's worth of width and put the two boards nose to
# nose in the middle of the field.
#
# The move is cheap for one reason: HitFeedback, Vfx, _panel_rect, _unit_under
# (drop targeting), _apply_target_glow and the pennant draw all aim at
# _panels[u.id]'s RECT, which _summoner_panel still writes. Nothing that points
# at a Summoner had to change -- the rect simply moved.
func _rail_summoner(is_enemy: bool, into: Control) -> void:
	var u: Unit = battle.enemy_summoner if is_enemy else battle.summoner
	if u == null or into == null: return
	var bs := _board_scale()
	var card := Vector2(CardView.W, CardView.H) * bs * RAIL_SUMMONER_SCALE
	_panel_f = card.y / UNIT_H
	_panel_size = card
	var panel := _summoner_panel(null if not is_enemy else u)
	panel.size = card
	# Bottom-aligned in the block, so their board's feet are on the same line as
	# the rank's -- and a pennant above it has room to draw without reaching the
	# top bar.
	panel.position = Vector2((into.size.x - card.x) * 0.5,
		maxf(0.0, into.size.y - card.y))
	into.add_child(panel)

# The Summoner is off-field and untargetable, so they get the hero-power slot
# rather than a place on the board: CP (their per-round actions), SP (the slow
# burn), and their own ultimate. Support only.
# THE SUMMONER'S BOARD. Both sides get one, and it is a real board now rather
# than a box of meters bolted to the end of the line: it has health, it takes a
# hit, and the fight ends the moment it is emptied.
#
# `who` null means your own, which is the one that also carries the CP/SP
# controls, the Decree and the bench. Theirs is the same board with none of the
# levers - you do not command it, you kill it.
func _summoner_panel(who: Unit = null) -> Control:
	var mine: bool = who == null
	var u: Unit = battle.summoner if mine else who
	var f := _unit_scale()
	# A Button, like every other board, because it has to accept a drop and a
	# click-to-target exactly the way a unit does. A panel that merely looked
	# targetable would be the worst of both.
	var panel := Button.new()
	panel.custom_minimum_size = _panel_size if _panel_size.x > 4.0 		else Vector2(UNIT_W * f, UNIT_H * f)
	panel.clip_contents = true
	panel.flat = false
	panel.disabled = battle.finished or (u != null and not u.is_alive())
	if u != null:
		_panels[u.id] = panel
		panel.gui_input.connect(_on_summoner_input.bind(u))
	# THE PICTURE IS THE CARD HERE TOO, with the meters and the levers riding on
	# a plate along the bottom of it. Stacked above them in a band of its own,
	# the art had a quarter of the board and the player's own character was the
	# smallest face on the screen.
	# THEIRS IS THE ROOM'S OWN HEAD — the promoted monster, filed under its
	# species — so what stands in their summoner box is the thing you are
	# actually fighting, not a nameless dark rectangle.
	var face: Texture2D = Assets.texture("character", _summoner_id()) if mine 		else Assets.texture("monster", u.species)
	if face == null and not mine:
		face = Assets.texture("monster", "enemy_summoner")
	if face != null:
		var bg := TextureRect.new()
		bg.texture = face
		bg.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		bg.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
		bg.mouse_filter = Control.MOUSE_FILTER_IGNORE
		bg.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
		panel.add_child(bg)
	var plate := PanelContainer.new()
	plate.mouse_filter = Control.MOUSE_FILTER_PASS
	plate.set_anchors_and_offsets_preset(Control.PRESET_BOTTOM_WIDE)
	plate.grow_vertical = Control.GROW_DIRECTION_BEGIN
	plate.offset_left = 2.0
	plate.offset_right = -2.0
	plate.offset_bottom = -2.0
	var plate_sb := StyleBoxFlat.new()
	plate_sb.bg_color = Color(0.03, 0.04, 0.07, 0.86)
	plate_sb.set_corner_radius_all(10)
	plate_sb.set_content_margin_all(4.0)
	plate.add_theme_stylebox_override("panel", plate_sb)
	panel.add_child(plate)
	var col := VBoxContainer.new()
	col.mouse_filter = Control.MOUSE_FILTER_PASS
	col.add_theme_constant_override("separation", 1)
	plate.add_child(col)

	# ONLY IF THERE IS ONE. An empty TextureRect still reserves its band, which
	# left the enemy's head floating in the middle of a slab of nothing - the
	# same trap the unit boards already learned to avoid.
	#
	# The band is SHORTER than a unit's by exactly what the health row costs, so
	# a Summoner's board is still one board tall and the Reserve button beneath
	# it does not get clipped off the bottom. Yours is still shown WHOLE rather
	# than cropped: it is the player's own face.

	var name_l := Label.new()
	name_l.add_theme_font_size_override("font_size", int(11 * f))
	name_l.add_theme_color_override("font_color",
		Color(0.95, 0.82, 0.45) if mine else Color(1.0, 0.62, 0.62))
	name_l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	name_l.mouse_filter = Control.MOUSE_FILTER_IGNORE
	name_l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	name_l.text = "★ %s" % (u.display_name if u != null else Content.summoner_name(_summoner_id()))
	col.add_child(name_l)

	# HEALTH, first and largest. It is the win condition on both sides now, so
	# it is the number the board is actually about.
	if u != null:
		var hp_bar := ProgressBar.new()
		hp_bar.max_value = u.max_hp
		hp_bar.value = u.hp
		hp_bar.custom_minimum_size = Vector2(0, 8)
		hp_bar.show_percentage = false
		hp_bar.mouse_filter = Control.MOUSE_FILTER_IGNORE
		var hb := StyleBoxFlat.new()
		hb.bg_color = Color(0.95, 0.78, 0.35) if mine else Color(0.85, 0.34, 0.34)
		hp_bar.add_theme_stylebox_override("fill", hb)
		col.add_child(hp_bar)
		# ONE LINE, NOT FOUR ROWS. Every row on the plate is a row taken off the
		# picture, and the picture is what makes this a card rather than a meter
		# box -- so the numbers are packed onto as few lines as they will go.
		var hp_l := Label.new()
		hp_l.add_theme_font_size_override("font_size", int(11 * f))
		hp_l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		hp_l.mouse_filter = Control.MOUSE_FILTER_IGNORE
		hp_l.clip_text = true
		hp_l.text = "%d/%d" % [u.hp, u.max_hp] if not mine 			else "%d/%d   CP %d/%d   SP %d" % [u.hp, u.max_hp,
				battle.cp, battle.max_cp, battle.sp]
		col.add_child(hp_l)
		var st: String = u.status_summary()
		if st != "":
			var stl := Label.new()
			stl.add_theme_font_size_override("font_size", int(11 * f))
			stl.add_theme_color_override("font_color", Color(0.7, 0.95, 0.7))
			stl.mouse_filter = Control.MOUSE_FILTER_IGNORE
			stl.text = st
			col.add_child(stl)

	if not mine:
		var note := Label.new()
		note.add_theme_font_size_override("font_size", int(11 * f))
		note.add_theme_color_override("font_color", Color(1.0, 0.78, 0.45))
		note.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		note.mouse_filter = Control.MOUSE_FILTER_IGNORE
		note.text = "▶ Commands the field. Cut them down and it ends."
		col.add_child(note)
		panel.tooltip_text = ("%s heads the other side. Board-wide cards do not reach "
			+ "them — a single-target attack does. Drop them and the fight is over.") % u.display_name
		panel.modulate = _tint_for(u, _lift_card if _lift_card != null else selected_card,
			_actor_for(_lift_card if _lift_card != null else selected_card))
		return panel

	# --- from here down it is YOUR board: the levers only you have.
	# Both meters on ONE line. The panel used to spend four wrapped lines saying
	# what two numbers say, and the art is what wants that room.

	# SP is the Summoner's own meter — this is their ultimate bar.
	var sp_bar := ProgressBar.new()
	sp_bar.max_value = Battle.SUMMONER_SP_MAX
	sp_bar.value = battle.sp
	sp_bar.custom_minimum_size = Vector2(0, 6)
	sp_bar.show_percentage = false
	sp_bar.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var sb := StyleBoxFlat.new()
	sb.bg_color = Color(1.0, 0.82, 0.35) if battle.summoner_ultimate_ready() else Color(0.75, 0.6, 0.25)
	sp_bar.add_theme_stylebox_override("fill", sb)
	col.add_child(sp_bar)

	# What the letters mean lives on the hover now rather than in a line of body
	# text that was read once and then cost room for the rest of the fight.
	panel.tooltip_text = ("%s, your Summoner. On the field and KILLABLE — lose them "
		+ "and the fight is lost, whoever is still standing.\n"
		+ "CP - command points, spent by Summoner cards. Refills every round.\n"
		+ "SP - support points. An ally's ultimate spends %d of them.") % [
			Content.summoner_name(_summoner_id()), Battle.ULT_SP_COST]

	# DECREE AND THE BENCH LIVE IN THE RIGHT RAIL NOW. They were buttons bolted
	# to the bottom of this board, and they are the reason it could not be a card
	# -- a board carrying two levers cannot also be a portrait. What is left is a
	# card: the picture, the name, the health and the two meters.
	panel.modulate = _tint_for(u, _lift_card if _lift_card != null else selected_card,
		_actor_for(_lift_card if _lift_card != null else selected_card))
	return panel

# A Summoner's board takes a drop and a click-to-target, and nothing else. There
# is no drag off it: a Summoner has no basic attack to swing, and no art to
# open out.
func _on_summoner_input(event: InputEvent, u: Unit) -> void:
	if battle.finished or not (event is InputEventMouseButton): return
	var mb := event as InputEventMouseButton
	if mb.button_index != MOUSE_BUTTON_LEFT or mb.pressed: return
	# Mid-drag this is the drop, and _on_press_ended already resolves it from
	# the pointer position - let it, or the card gets played twice.
	if _lift_card != null: return
	if selected_card != null:
		_on_unit_pressed(u)

# ------------------------------------------------------------------ the bench
#
# Every pairing is one button, because the alternative - pick a reserve, then
# pick who they replace - is two clicks and a mode to be stuck in. With at most
# a handful either side the grid is small enough to just show.

var _reserve_ui: Control = null

func _open_reserve() -> void:
	if battle.reserves.is_empty(): return
	_close_reserve()
	_sfx("select")
	_reserve_ui = Control.new()
	_reserve_ui.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	_reserve_ui.mouse_filter = Control.MOUSE_FILTER_STOP
	add_child(_reserve_ui)

	var dim := ColorRect.new()
	dim.color = Color(0.03, 0.04, 0.06, 0.78)
	dim.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	dim.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_reserve_ui.add_child(dim)

	var centre := CenterContainer.new()
	centre.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	centre.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_reserve_ui.add_child(centre)

	var panel := PanelContainer.new()
	panel.custom_minimum_size = Vector2(560, 0)
	centre.add_child(panel)
	var pad := MarginContainer.new()
	for side in ["left", "right", "top", "bottom"]:
		pad.add_theme_constant_override("margin_" + side, 18)
	panel.add_child(pad)
	var col := VBoxContainer.new()
	col.name = "Body"
	col.add_theme_constant_override("separation", 8)
	pad.add_child(col)
	_fill_reserve(col)

func _close_reserve() -> void:
	if _reserve_ui != null and is_instance_valid(_reserve_ui):
		_reserve_ui.queue_free()
	_reserve_ui = null

# Rebuilt after a swap, so the panel shows the board as it is now rather than as
# it was when it opened. Closed outright once the fight ends.
func _refresh_reserve_ui() -> void:
	if _reserve_ui == null or not is_instance_valid(_reserve_ui): return
	if battle.finished or battle.reserves.is_empty():
		_close_reserve()
		return
	var body := _reserve_ui.find_child("Body", true, false)
	if body == null: return
	for ch in body.get_children(): ch.queue_free()
	_fill_reserve(body)

func _fill_reserve(col: Control) -> void:
	var head := Label.new()
	head.text = "RESERVE"
	head.add_theme_font_size_override("font_size", 22)
	head.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	col.add_child(head)
	var note := Label.new()
	note.add_theme_font_size_override("font_size", 12)
	note.add_theme_color_override("font_color", Color(0.66, 0.70, 0.80))
	note.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	note.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	note.text = ("Whoever steps onto the line arrives with no AP left this round. "
		+ "Replacing somebody who is down costs nothing.")
	col.add_child(note)
	col.add_child(HSeparator.new())

	for r in battle.reserves:
		col.add_child(_reserve_row(r))

	col.add_child(HSeparator.new())
	var back := Button.new()
	back.text = "Back to the fight"
	back.custom_minimum_size = Vector2(0, 36)
	back.pressed.connect(_close_reserve)
	col.add_child(back)

func _reserve_row(r: Unit) -> Control:
	var row := VBoxContainer.new()
	row.add_theme_constant_override("separation", 3)
	var head := HBoxContainer.new()
	head.add_theme_constant_override("separation", 8)
	row.add_child(head)

	var face := Assets.texture("portrait", r.id)
	if face != null:
		var pic := TextureRect.new()
		pic.texture = face
		pic.custom_minimum_size = Vector2(44, 44)
		pic.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		pic.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_COVERED
		pic.mouse_filter = Control.MOUSE_FILTER_IGNORE
		head.add_child(pic)

	var who := Label.new()
	who.add_theme_font_size_override("font_size", 15)
	who.text = "%s    HP %d/%d    %s / %s" % [r.display_name, r.hp, r.max_hp,
		Tiers.name_of(r.tier), ElementChart.name_of(r.element)]
	who.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	head.add_child(who)

	if not r.is_alive():
		var down := Label.new()
		down.add_theme_font_size_override("font_size", 12)
		down.add_theme_color_override("font_color", Color(1.0, 0.55, 0.55))
		down.text = "  down — cannot take the field"
		row.add_child(down)
		return row

	var swaps := HBoxContainer.new()
	swaps.add_theme_constant_override("separation", 6)
	row.add_child(swaps)
	for a in battle.allies:
		var b := Button.new()
		b.add_theme_font_size_override("font_size", 12)
		var free: bool = battle.swap_is_free(a)
		b.text = "↔ %s%s" % [a.display_name.split(" ")[0], "  (free)" if free else ""]
		b.disabled = not battle.can_swap(a, r)
		if b.disabled:
			b.tooltip_text = "Not now — a paid swap waits for your side of the turn order."
		b.pressed.connect(_do_swap.bind(a, r))
		swaps.add_child(b)
	return row

func _do_swap(out_u: Unit, in_u: Unit) -> void:
	if not battle.swap_in(out_u, in_u): return
	_sfx("select")
	selected_card = null
	selected_actor = null
	_clear_lift()
	if _fx != null:
		_fx.announce(in_u.display_name.split(" ")[0].to_upper() + " IN",
			Color(0.6, 0.95, 1.0), _side_centre(ally_box))
	# Deferred: this runs from a button inside the reserve panel, and the
	# refresh rebuilds that panel.
	_refresh_soon()

# ------------------------------------------------------ the end of the fight
#
# WHAT YOU WON, WHERE YOU ARE LOOKING. The verdict used to be a word in the
# prompt line and the haul was never shown at all - `last_rewards` was written
# after every fight and read by nothing, so the reward for clearing a room was
# a number you could only find by walking back to the board and adding up the
# running total yourself.
#
# THE FIGHT IS RESOLVED WHEN IT ENDS, not when you leave. That is what makes
# there be anything to show. It also closes a hole: the outcome used to be
# banked on the way out, so quitting from the pause menu after a defeat threw
# the defeat away with it.

var _end_ui: Control = null
var _resolved := false

# Bank the outcome exactly once. Everything the reward screen shows comes out
# of this, so it has to run before the screen is built rather than on the way
# out of the scene.
func _resolve_if_finished() -> void:
	if not battle.finished or _resolved: return
	_resolved = true
	var sess = _sess()
	if sess == null or sess.dungeon == null or not sess.dungeon.in_delve(): return
	# The bench carries its HP to the next room like everyone else, but exp and
	# bonds are for the people who actually stood in it.
	for r in battle.reserves:
		sess.party_hp[r.id] = r.hp
	sess.resolve_battle(battle.victory, battle.allies)

func _refresh_end_ui() -> void:
	if not battle.finished:
		if _end_ui != null and is_instance_valid(_end_ui):
			_end_ui.queue_free()
		_end_ui = null
		return
	_resolve_if_finished()
	# Built once and left alone. Rebuilding it every refresh would restart the
	# read every time anything on the board twitched.
	if _end_ui != null and is_instance_valid(_end_ui): return
	_close_reserve()
	_clear_lift()
	_build_end_ui()

func _build_end_ui() -> void:
	_end_ui = Control.new()
	_end_ui.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	_end_ui.mouse_filter = Control.MOUSE_FILTER_STOP
	add_child(_end_ui)

	var dim := ColorRect.new()
	dim.color = Color(0.02, 0.03, 0.05, 0.80)
	dim.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	dim.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_end_ui.add_child(dim)

	var centre := CenterContainer.new()
	centre.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	centre.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_end_ui.add_child(centre)

	var panel := PanelContainer.new()
	panel.custom_minimum_size = Vector2(520, 0)
	centre.add_child(panel)
	var pad := MarginContainer.new()
	for side in ["left", "right", "top", "bottom"]:
		pad.add_theme_constant_override("margin_" + side, 22)
	panel.add_child(pad)
	var col := VBoxContainer.new()
	col.add_theme_constant_override("separation", 6)
	pad.add_child(col)

	# --- the verdict, as big as it deserves to be
	var won: bool = battle.victory
	var title := Label.new()
	title.text = "VICTORY" if won else "DEFEAT"
	title.add_theme_font_size_override("font_size", 46)
	title.add_theme_color_override("font_color",
		Color(1.0, 0.86, 0.42) if won else Color(1.0, 0.42, 0.42))
	title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	col.add_child(title)

	var why := Label.new()
	why.add_theme_font_size_override("font_size", 13)
	why.add_theme_color_override("font_color", Color(0.70, 0.75, 0.85))
	why.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	why.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	why.text = _verdict_line()
	col.add_child(why)
	col.add_child(HSeparator.new())

	if won: _build_rewards(col)
	else: _build_losses(col)

	col.add_child(HSeparator.new())
	var leave := Button.new()
	leave.text = leave_label()
	leave.custom_minimum_size = Vector2(0, 46)
	leave.add_theme_font_size_override("font_size", 18)
	leave.pressed.connect(leave_battle)
	col.add_child(leave)

# WHY it ended, which is not always obvious - a Summoner falling ends a fight
# with most of the board still standing, and without a word here that reads as
# the game deciding on its own.
func _verdict_line() -> String:
	if battle.enemy_summoner != null and not battle.enemy_summoner.is_alive():
		return "%s has fallen. The rest of them scatter." % battle.enemy_summoner.display_name
	if battle.summoner != null and not battle.summoner.is_alive():
		return "%s has fallen. The party has nobody left to command them." % battle.summoner.display_name
	if battle.victory:
		return "Nothing of theirs is left standing."
	return "The whole party is down."

# WHAT THE ROOM PAID. Straight out of last_rewards, which resolve_battle has
# just written - this is the first screen in the game that has ever shown it.
func _build_rewards(col: VBoxContainer) -> void:
	var sess = _sess()
	var rw: Dictionary = sess.last_rewards if sess != null else {}
	if rw.is_empty():
		_end_line(col, "A practice bout. Nothing to carry out of it.",
			Color(0.62, 0.66, 0.76), 13, HORIZONTAL_ALIGNMENT_CENTER)
		return

	_end_head(col, "THIS ROOM PAID")
	var any := false
	for k in ["exp", "gold", "essence", "ore", "hide"]:
		var v: int = int(rw.get(k, 0))
		if v <= 0: continue
		any = true
		_end_row(col, k.to_upper(), str(v))
	if str(rw.get("card", "")) != "":
		any = true
		_end_row(col, "CARD", "%s  (new)" % str(rw.get("card_name", rw["card"])),
			Color(1.0, 0.86, 0.45))
	if not any:
		_end_line(col, "Nothing but the experience.", Color(0.62, 0.66, 0.76), 13)

	var levelled: Array = rw.get("levelled", [])
	if not levelled.is_empty():
		var names: Array = []
		for uid in levelled: names.append(_short_name(str(uid)))
		_end_head(col, "LEVELLED")
		_end_line(col, ", ".join(names), Color(0.72, 1.0, 0.78), 14)

	var bonded: Array = rw.get("bonded", [])
	if not bonded.is_empty():
		_end_head(col, "BONDS DEEPENED")
		for b in bonded:
			_end_line(col, "%s and %s — %s" % [_short_name(str(b.get("a", ""))),
				_short_name(str(b.get("b", ""))), str(b.get("name", ""))],
				Color(0.72, 1.0, 0.78), 13)

	# The haul is not yours yet, and that is the whole tension of the dungeon.
	_end_line(col, "Carried out of the dungeon only if you make it back to a camp "
		+ "or the entrance.", Color(1.0, 0.78, 0.45), 12, HORIZONTAL_ALIGNMENT_CENTER)

# WHAT THE TRIP COST. Read from last_report rather than from run_haul, because
# resolving a defeat ends the delve and empties the haul in the same breath -
# reading it afterwards always says you were carrying nothing.
func _build_losses(col: VBoxContainer) -> void:
	var sess = _sess()
	if sess == null:
		_end_line(col, "Nothing was at stake.", Color(0.62, 0.66, 0.76), 13,
			HORIZONTAL_ALIGNMENT_CENTER)
		return
	var rep: Dictionary = sess.last_report
	var kept: Dictionary = rep.get("kept", {})
	var lost: Dictionary = rep.get("lost", {})
	_end_head(col, "THE TRIP ENDS HERE")

	var carried := false
	for k in ["exp", "gold", "essence", "ore", "hide"]:
		if int(kept.get(k, 0)) > 0:
			carried = true
			_end_row(col, "%s KEPT" % k.to_upper(), str(int(kept[k])),
				Color(0.72, 1.0, 0.78))
	for k2 in ["gold", "essence", "ore", "hide"]:
		if int(lost.get(k2, 0)) > 0:
			carried = true
			_end_row(col, "%s LOST" % k2.to_upper(), str(int(lost[k2])),
				Color(1.0, 0.55, 0.55))
	if not carried:
		_end_line(col, "You were not carrying anything worth losing.",
			Color(0.62, 0.66, 0.76), 13)
	elif bool(rep.get("used_cya", false)):
		_end_line(col, "A CYA item burned to bring the whole haul home.",
			Color(0.72, 1.0, 0.78), 12, HORIZONTAL_ALIGNMENT_CENTER)
	else:
		_end_line(col, "EXP always survives, and the Safe Pocket held what it could.",
			Color(1.0, 0.78, 0.45), 12, HORIZONTAL_ALIGNMENT_CENTER)

func _end_head(col: VBoxContainer, text: String) -> void:
	var l := Label.new()
	l.text = text
	l.add_theme_font_size_override("font_size", 12)
	l.add_theme_color_override("font_color", Color(0.58, 0.63, 0.75))
	col.add_child(l)

func _end_row(col: VBoxContainer, key: String, value: String,
		tint := Color(0.92, 0.94, 0.99)) -> void:
	var row := HBoxContainer.new()
	var k := Label.new()
	k.text = key
	k.custom_minimum_size = Vector2(130, 0)
	k.add_theme_font_size_override("font_size", 15)
	k.add_theme_color_override("font_color", Color(0.72, 0.77, 0.88))
	row.add_child(k)
	var v := Label.new()
	v.text = value
	v.add_theme_font_size_override("font_size", 16)
	v.add_theme_color_override("font_color", tint)
	row.add_child(v)
	col.add_child(row)

func _end_line(col: VBoxContainer, text: String, tint: Color, pt: int,
		align := HORIZONTAL_ALIGNMENT_LEFT) -> void:
	var l := Label.new()
	l.text = text
	l.add_theme_font_size_override("font_size", maxi(9, pt - 1))
	l.add_theme_color_override("font_color", tint)
	l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	l.horizontal_alignment = align
	col.add_child(l)

# A unit id back to the name a player would recognise.
func _short_name(uid: String) -> String:
	for u in battle.allies + battle.reserves:
		if u.id == uid: return u.display_name.split(" ")[0]
	return uid.capitalize()

# --------------------------------------------------- reading the other side
#
# THE BACK OF THEIR CARD. A creature showed one line — the move it had committed
# to this round — and nothing else, so the only way to learn that a Vale Husk
# also carries Hollow Wail was to be hit by it.
#
# This is their whole kit, laid out like a card back: what each move does, what
# it costs them, and which one they have already chosen. Same two gestures a
# card in your hand answers to, so there is nothing new to learn:
#
#   HOLD        turn their board over while you hold it
#   DOUBLE TAP  pin it open, and again to put it down
#
# The numbers are ESTIMATES and say so. Real damage runs through defence, the
# element chart and a crit roll; promising an exact figure here would be a lie
# a third of the time.

var _dossier: Control = null
var _dossier_for := ""
var _dossier_held := false

func _open_dossier(u: Unit, held: bool) -> void:
	if u == null: return
	_close_dossier()
	_dossier_for = u.id
	_dossier_held = held
	_sfx("select")

	_dossier = Control.new()
	_dossier.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	# A pinned one swallows the next click to close itself; a held one must not,
	# or it eats the release that is supposed to end the hold.
	_dossier.mouse_filter = Control.MOUSE_FILTER_IGNORE if held else Control.MOUSE_FILTER_STOP
	if not held:
		_dossier.gui_input.connect(func(e):
			if e is InputEventMouseButton and (e as InputEventMouseButton).pressed:
				_close_dossier())
	add_child(_dossier)

	var dim := ColorRect.new()
	dim.color = Color(0.02, 0.03, 0.05, 0.62)
	dim.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	dim.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_dossier.add_child(dim)

	var centre := CenterContainer.new()
	centre.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	centre.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_dossier.add_child(centre)

	var panel := PanelContainer.new()
	panel.custom_minimum_size = Vector2(560, 0)
	panel.mouse_filter = Control.MOUSE_FILTER_IGNORE
	centre.add_child(panel)
	var pad := MarginContainer.new()
	for side in ["left", "right", "top", "bottom"]:
		pad.add_theme_constant_override("margin_" + side, 20)
	panel.add_child(pad)
	var col := VBoxContainer.new()
	col.add_theme_constant_override("separation", 5)
	pad.add_child(col)
	_fill_dossier(col, u)

func _close_dossier() -> void:
	if _dossier != null and is_instance_valid(_dossier):
		_dossier.queue_free()
	_dossier = null
	_dossier_for = ""
	_dossier_held = false

func _fill_dossier(col: VBoxContainer, u: Unit) -> void:
	var head := Label.new()
	head.text = u.display_name
	head.add_theme_font_size_override("font_size", 26)
	head.add_theme_color_override("font_color", Color(1.0, 0.62, 0.62))
	head.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	col.add_child(head)

	var sub := Label.new()
	sub.add_theme_font_size_override("font_size", 14)
	sub.add_theme_color_override("font_color", Color(0.70, 0.75, 0.85))
	sub.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	sub.text = "%s / %s     HP %d/%d     SPD %d" % [Tiers.name_of(u.tier),
		ElementChart.name_of(u.element), u.hp, u.max_hp, int(u.effective_speed())]
	col.add_child(sub)

	var el := _dossier_element_note(u)
	if el != "":
		var eln := Label.new()
		eln.add_theme_font_size_override("font_size", 13)
		eln.add_theme_color_override("font_color", Color(0.82, 0.86, 0.95))
		eln.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		eln.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		eln.text = el
		col.add_child(eln)

	var st: String = u.status_summary()
	if st != "":
		var stl := Label.new()
		stl.add_theme_font_size_override("font_size", 13)
		stl.add_theme_color_override("font_color", Color(0.7, 0.95, 0.7))
		stl.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
		stl.text = st
		col.add_child(stl)
	col.add_child(HSeparator.new())

	# WHAT IT HAS ALREADY CHOSEN. The board says this too, but on a screen you
	# opened to compare moves it belongs beside the ones it was chosen from.
	var committed: Card = battle.intents.get(u.id, null)
	_end_head(col, "THIS ROUND")
	_end_line(col, ("▶ " + battle.intent_text(u)) if committed != null else "waiting",
		Color(1.0, 0.78, 0.45), 16)

	_end_head(col, "EVERYTHING IT CAN DO")
	var pool: Array = Content.enemy_cards_for(u)
	if pool.is_empty():
		_end_line(col, "Nothing on record.", Color(0.62, 0.66, 0.76), 14)
	for c in pool:
		col.add_child(_dossier_move(c, u, committed))

	_end_line(col, "Damage is an estimate — defence, the element matchup and a "
		+ "crit roll all land on top of it.", Color(0.62, 0.66, 0.76), 12,
		HORIZONTAL_ALIGNMENT_CENTER)
	col.add_child(HSeparator.new())
	_end_line(col, "Hold a creature to read it, double-tap to pin it open.",
		Color(0.58, 0.63, 0.75), 12, HORIZONTAL_ALIGNMENT_CENTER)

func _dossier_move(c: Card, u: Unit, committed: Card) -> Control:
	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", 10)
	var chosen: bool = committed != null and committed.id == c.id

	var nm := Label.new()
	nm.custom_minimum_size = Vector2(170, 0)
	nm.add_theme_font_size_override("font_size", 15)
	nm.add_theme_color_override("font_color",
		Color(1.0, 0.86, 0.45) if chosen else Color(0.92, 0.94, 0.99))
	nm.text = ("▶ " if chosen else "   ") + c.display_name
	row.add_child(nm)

	var what := Label.new()
	what.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	what.add_theme_font_size_override("font_size", 14)
	what.add_theme_color_override("font_color", Color(0.82, 0.86, 0.95))
	var bits: Array = []
	if c.effective_power() > 0.0:
		var est := int(c.effective_power() * (u.magic if c.uses_magic else u.attack))
		bits.append("~%d dmg%s" % [est,
			"  ALL" if c.target == Card.Target.ALL_ENEMIES else ""])
	if c.effective_heal() > 0.0: bits.append("heals %d" % int(c.effective_heal()))
	if c.status != "":
		bits.append("%s for %d" % [c.status, c.status_duration])
	if bits.is_empty(): bits.append(ElementChart.name_of(c.element))
	what.text = ", ".join(bits)
	row.add_child(what)

	var cost := Label.new()
	cost.add_theme_font_size_override("font_size", 13)
	cost.add_theme_color_override("font_color", Color(0.68, 0.72, 0.82))
	cost.text = "%d AP  %d EP" % [c.ap_cost, c.ep_cost]
	row.add_child(cost)
	return row

# What THEIR element means for the fight, read off the chart both ways: what
# they hit hard, and what hits them hard.
func _dossier_element_note(u: Unit) -> String:
	if u.element == ElementChart.E.NEUTRAL: return ""
	var hurts := ""
	var hurt_by := ""
	for e in [ElementChart.E.FIRE, ElementChart.E.WATER, ElementChart.E.WIND,
			ElementChart.E.EARTH, ElementChart.E.LIGHTNING, ElementChart.E.LIGHT,
			ElementChart.E.DARK]:
		if ElementChart.get_multiplier(u.element, e) > 1.01: hurts = ElementChart.name_of(e)
		if ElementChart.get_multiplier(e, u.element) > 1.01: hurt_by = ElementChart.name_of(e)
	# Light and Dark beat EACH OTHER, so the generic phrasing names the same
	# element twice and reads as a bug rather than a rule.
	if hurts != "" and hurts == hurt_by:
		return "%s cuts through it, and it cuts back just as hard" % hurt_by
	var parts: Array = []
	if hurt_by != "": parts.append("%s cuts through it" % hurt_by)
	if hurts != "": parts.append("it cuts through %s" % hurts)
	return "  ·  ".join(parts)

# ------------------------------------------------------- the detail panel
#
# THE CARD THE TILE STANDS FOR, drawn at a size worth looking at.
#
# The grid had to shrink a board to a tile — six rows of them do not fit on one
# screen otherwise — and a tile cannot carry a portrait, a stat block, an intent
# and a status list. So it carries position, which is what the grid is FOR, and
# this carries everything else.
#
# It follows the pointer: whatever you are hovering is what it shows. With
# nothing hovered it falls back to whoever is up, so it is never blank and never
# stale.

var _focus: Unit = null

# HOVER EXPANDS THE CARD. A board tile is small on purpose -- ten of them and a
# hand share one screen -- so the way to actually LOOK at one is to point at it
# and have it grow out of the row, over everything else, at a size the picture
# was drawn for. The same node is where a unit's clip will play once there are
# clips: it is already the one surface that owns the whole card.
var _zoom: Control = null
var _zoom_unit: Unit = null
const ZOOM_SCALE := 2.05
const ZOOM_MAX_H := 470.0

func _show_unit_zoom(u: Unit, from: Control) -> void:
	if u == null or from == null or not is_instance_valid(from): return
	# NOT WHILE A CARD IS IN THE AIR. Dragging a card over a target is aiming at
	# it, not asking to read it -- the board blowing up under the cursor hides
	# the very cells you are choosing between.
	if _lift_card != null or selected_card != null: return
	if _zoom_unit == u and _zoom != null and is_instance_valid(_zoom): return
	_hide_unit_zoom()
	var face: Texture2D = Assets.texture("monster", u.species) if u.is_enemy 		else Assets.texture("portrait", u.id)
	if face == null: return
	_zoom_unit = u
	var h: float = minf(from.size.y * ZOOM_SCALE, ZOOM_MAX_H)
	var w: float = h * CARD_ASPECT
	# A plain Control, not a PanelContainer: a container stretches every child to
	# its own rect, which is how the name ended up printed across the middle of
	# the picture instead of along the bottom of it.
	var box := Control.new()
	box.mouse_filter = Control.MOUSE_FILTER_IGNORE
	box.custom_minimum_size = Vector2(w, h)
	box.size = Vector2(w, h)
	box.z_index = 200
	var sb := StyleBoxFlat.new()
	sb.bg_color = Color(0.05, 0.06, 0.09, 0.98)
	sb.set_border_width_all(3)
	sb.border_color = COL_TEAM_ENEMY if u.is_enemy else COL_TEAM_ALLY
	sb.set_corner_radius_all(16)
	sb.set_content_margin_all(6.0)
	var frame := Panel.new()
	frame.mouse_filter = Control.MOUSE_FILTER_IGNORE
	frame.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	frame.add_theme_stylebox_override("panel", sb)
	box.add_child(frame)
	var pic := TextureRect.new()
	pic.texture = face
	pic.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	pic.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	pic.mouse_filter = Control.MOUSE_FILTER_IGNORE
	pic.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	pic.offset_left = 7.0
	pic.offset_top = 7.0
	pic.offset_right = -7.0
	pic.offset_bottom = -7.0
	box.add_child(pic)
	var name_row := Label.new()
	name_row.mouse_filter = Control.MOUSE_FILTER_IGNORE
	name_row.add_theme_font_size_override("font_size", 15)
	name_row.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	name_row.vertical_alignment = VERTICAL_ALIGNMENT_BOTTOM
	name_row.text = "%s   %d/%d" % [u.display_name, u.hp, u.max_hp]
	name_row.set_anchors_and_offsets_preset(Control.PRESET_BOTTOM_WIDE)
	name_row.grow_vertical = Control.GROW_DIRECTION_BEGIN
	name_row.offset_top = -30.0
	name_row.offset_bottom = -8.0
	box.add_child(name_row)
	# ON TOP OF THE WHOLE SCREEN, not inside the grid: a card that expanded
	# inside its own cell would be clipped by it, and one that expanded inside
	# the row would shove every other card sideways.
	add_child(box)
	_zoom = box
	# Centred on the tile it grew out of, then pushed back inside the window --
	# a card on the top row or the far lane would otherwise open off-screen.
	var vp := get_viewport_rect().size
	var mid: Vector2 = from.global_position + from.size * 0.5 - global_position
	var at := Vector2(mid.x - w * 0.5, mid.y - h * 0.5)
	at.x = clampf(at.x, 6.0, maxf(6.0, vp.x - w - 6.0))
	at.y = clampf(at.y, 6.0, maxf(6.0, vp.y - h - 6.0))
	box.position = at

# The screenshot harness's way in: synthetic hover cannot reach the window, so
# the expanded card has to be openable by name to be verifiable at all.
func preview_unit(id: String) -> void:
	for u in battle.allies + battle.enemies:
		if u.id == id or u.display_name.begins_with(id):
			var tile = _panels.get(u.id)
			if tile != null and is_instance_valid(tile): _show_unit_zoom(u, tile)
			return

func _hide_unit_zoom() -> void:
	_zoom_unit = null
	if _zoom != null and is_instance_valid(_zoom): _zoom.queue_free()
	_zoom = null

func _set_focus(u: Unit) -> void:
	if _focus == u: return
	_focus = u
	_rebuild_detail()

func _detail_unit() -> Unit:
	if _focus != null and _focus.is_alive(): return _focus
	if _hover_target != null: return _hover_target
	var up := battle.current_unit()
	if up != null: return up
	return battle.summoner

func _rebuild_detail() -> void:
	if detail_box == null: return
	for c in detail_box.get_children(): c.queue_free()
	var u := _detail_unit()
	if u == null: return
	var is_enemy: bool = u.is_enemy

	# NO PICTURE HERE ANY MORE. The card you point at grows to twice its size
	# over the hand, and the board tiles carry a face each — a third copy of the
	# same art in a side panel was the widest thing on screen doing the least.
	# What is left is the numbers, tight, so the field can have the width.
	_detail_line(u.display_name, 15,
		Color(1.0, 0.62, 0.62) if is_enemy else Color(0.75, 0.90, 1.0))
	# ROLE FIRST, in its own colour — it is the one word that answers "why is
	# this one here", and it is what decides where they stand.
	_detail_line(Roles.name_of(u.role), 12, Roles.colour_of(u.role))
	# WHETHER THE ROLE IS PAYING. Nobody is forced anywhere, so the only way to
	# know a Ranger has wandered into the front line is to be told.
	# CONDENSED. In a corner strip there is room for the things the card does
	# NOT already say: whether the role is paying, where they stand, and what
	# they are about to do. The long version of each is the tooltip's job.
	var bt := Roles.bonus_text(u.role)
	if bt != "" and battle.board_of(u) != null:
		if battle.in_position(u):
			_detail_line("In position", 10, Color(0.72, 1.0, 0.78))
		else:
			_detail_line("Out of position", 10, Color(1.0, 0.72, 0.55))
	_detail_line("SPD %d" % int(u.effective_speed()), 10, Color(0.68, 0.72, 0.82))

	var hp := ProgressBar.new()
	hp.max_value = u.max_hp
	hp.value = u.hp
	hp.custom_minimum_size = Vector2(0, 9)
	hp.show_percentage = false
	detail_box.add_child(hp)
	_detail_line("%d/%d" % [u.hp, u.max_hp], 12, Color(0.92, 0.94, 0.99))

	var b := battle.board_of(u)
	if b != null:
		var cell := b.find(u)
		if cell.x >= 0:
			_detail_line("Lane %d" % [cell.y + 1],
				10, Color(0.72, 0.77, 0.88))
		var wall := b.shielded_by(u)
		if wall != null:
			_detail_line("Behind %s" % wall.display_name.split(" ")[0], 10,
				Color(1.0, 0.78, 0.45))
		elif u.is_alive():
			_detail_line("In reach", 10, Color(0.72, 1.0, 0.78))
	elif battle.is_summoner(u):
		_detail_line("Off the grid", 10, Color(0.72, 0.77, 0.88))

	if not is_enemy and not battle.is_summoner(u):
		_detail_line("AP%d EP%d UP%d" % [u.ap, u.ep, u.ultimate], 11,
			Color(0.85, 0.90, 1.0))
		if u.ultimate_ready() and not battle.finished:
			var ub2 := Button.new()
			ub2.text = "★ ULT — %d SP" % Battle.ULT_SP_COST
			ub2.add_theme_font_size_override("font_size", 11)
			ub2.pressed.connect(_on_unit_ultimate.bind(u))
			detail_box.add_child(ub2)

	if is_enemy and u.is_alive() and not battle.finished and not battle.is_summoner(u):
		_detail_line("▶ " + battle.intent_text(u), 11, Color(1.0, 0.78, 0.45))

	var st: String = u.status_summary()
	if st != "":
		_detail_line(st, 10, Color(0.7, 0.95, 0.7))

func _detail_line(text: String, pt: int, tint: Color) -> void:
	var l := Label.new()
	l.text = text
	l.add_theme_font_size_override("font_size", pt)
	l.add_theme_color_override("font_color", tint)
	# A SQUARE PANEL IN THE CORNER reads down, not along, and a line too long
	# for it wraps rather than pushing the panel out over the hand.
	l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	l.mouse_filter = Control.MOUSE_FILTER_IGNORE
	detail_box.add_child(l)

# Who the Summoner IS. Stored on the profile rather than hardcoded, so choosing
# one at the start of a campaign is a value to write, not a code change.
func _summoner_id() -> String:
	var sess = _sess()
	if sess == null: return Content.DEFAULT_SUMMONER
	return str(sess.data.get("profile", {}).get("summoner", Content.DEFAULT_SUMMONER))

func _on_summoner_ultimate() -> void:
	var sum_before := _snapshot_hp()
	if battle.fire_summoner_ultimate():
		_sfx("ultimate")
		Vfx.burst(self, _side_centre(ally_box), ElementChart.E.LIGHT, true)
		_fx.kick(1.2)
		_fx.announce("DECREE", HitFeedback.COL_CRIT, _side_centre(ally_box))
		_refresh_soon()
		_report_hp(sum_before)

func _on_unit_ultimate(u: Unit) -> void:
	var ult_before := _snapshot_hp()
	var rect := _panel_rect(u)
	if battle.fire_ultimate(u):
		_sfx("ultimate")
		var card := Content.ultimate_for(u)
		var side := enemy_box if card.target != Card.Target.ALL_ALLIES else ally_box
		Vfx.burst(self, _side_centre(side), card.element, true)
		_fx.kick(1.1)
		_fx.announce(card.display_name.to_upper(), HitFeedback.COL_CRIT,
			rect.position + Vector2(8, 6))
		_refresh_soon()
		_report_hp(ult_before)

func _side_centre(box: Control) -> Vector2:
	return box.global_position - global_position + box.size * Vector2(0.5, 0.45)

func _panel_rect(u: Unit) -> Rect2:
	# NULL IS A REAL ANSWER HERE, not a mistake to be caught in testing. A
	# Summoner card is played by nobody at nobody: "Hush the Corrupt" has no
	# actor and no single target, so both arguments that reach this are null and
	# there is genuinely no one board to point at. The zero-size rect is the
	# same "nothing to aim at" every caller already handles for a board that has
	# not been laid out yet.
	if u == null: return Rect2(get_viewport_rect().size * 0.5, Vector2.ZERO)
	var panel = _panels.get(u.id, null)
	if panel == null or not is_instance_valid(panel) or panel.size.x < 1.0:
		return Rect2(get_viewport_rect().size * 0.5, Vector2.ZERO)
	return Rect2(panel.global_position - global_position, panel.size)

# A unit board: portrait, name, health, and whatever that side needs to show.
# Vertical, so a row of them reads as a battle line rather than a list.
func _unit_panel(u: Unit, is_enemy: bool) -> Control:
	var f := _unit_scale()
	var b := Button.new()
	var alive: bool = u.is_alive()
	# Enemies are stat blocks, not a board of portraits, so they take the height
	# their content actually needs rather than matching the party row.
	var big: bool = bool(_expanded.get(u.id, false))
	var extra: float = (PORTRAIT_BIG - PORTRAIT_H) if big else 0.0
	b.custom_minimum_size = Vector2(UNIT_W * f,
		(UNIT_H + extra) * f)
	b.disabled = not alive or battle.finished
	# gui_input, NOT pressed: this card carries four gestures and `pressed` only
	# knows about one of them. Button keeps its own hover and press styling.
	b.gui_input.connect(_on_unit_input.bind(u))
	b.clip_contents = true
	_panels[u.id] = b

	var col := VBoxContainer.new()
	col.mouse_filter = Control.MOUSE_FILTER_IGNORE
	col.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	col.add_theme_constant_override("separation", 1)
	b.add_child(col)

	# Only if there IS a portrait. An empty TextureRect still reserves its
	# height, which left every enemy board with a blank slab on top of it.
	# A monster's portrait is its own card art, looked up by SPECIES rather than
	# by unit id — two of the same creature in one fight have different ids.
	var face: Texture2D = Assets.texture("monster", u.species) if is_enemy \
		else Assets.texture("portrait", u.id)
	if face != null:
		var pic := TextureRect.new()
		pic.custom_minimum_size = Vector2(0, (PORTRAIT_BIG if big else PORTRAIT_H) * f)
		pic.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		# CENTERED, not COVERED. Covered filled the band by cropping, which cut
		# every character off at the waist — the whole picture shows now, sized
		# to fit rather than trimmed to shape.
		pic.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
		pic.mouse_filter = Control.MOUSE_FILTER_IGNORE
		pic.texture = face
		col.add_child(pic)

	var l1 := Label.new()
	l1.add_theme_font_size_override("font_size", int(13 * f))
	l1.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var lv_txt := ""
	if not is_enemy and u.level > 1: lv_txt = " Lv%d" % u.level
	l1.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	l1.text = ("%s%s" % [u.display_name, lv_txt]) if is_enemy 		else ("%s%s" % [u.display_name.split(" ")[0], lv_txt])
	col.add_child(l1)

	var l0 := Label.new()
	l0.add_theme_font_size_override("font_size", int(10 * f))
	l0.add_theme_color_override("font_color", Color(0.68, 0.72, 0.82))
	l0.mouse_filter = Control.MOUSE_FILTER_IGNORE
	l0.text = "%s / %s" % [Tiers.name_of(u.tier), ElementChart.name_of(u.element)]
	col.add_child(l0)

	var hp_bar := ProgressBar.new()
	hp_bar.max_value = u.max_hp
	hp_bar.value = u.hp
	hp_bar.custom_minimum_size = Vector2(0, 11)
	hp_bar.show_percentage = false
	hp_bar.mouse_filter = Control.MOUSE_FILTER_IGNORE
	col.add_child(hp_bar)

	var l2 := Label.new()
	l2.add_theme_font_size_override("font_size", int(12 * f))
	l2.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var t2 := "HP %d/%d" % [u.hp, u.max_hp]
	if not is_enemy:
		t2 += "   AP %d  EP %d" % [u.ap, u.ep]
	l2.text = t2
	col.add_child(l2)

	# ULTIMATE METER — the unit charges it; the Summoner spends SP to fire.
	if not is_enemy:
		var ult := ProgressBar.new()
		ult.max_value = Unit.ULT_MAX
		ult.value = u.ultimate
		ult.custom_minimum_size = Vector2(0, 9)
		ult.show_percentage = false
		ult.mouse_filter = Control.MOUSE_FILTER_IGNORE
		var sb := StyleBoxFlat.new()
		sb.bg_color = (Color(1.0, 0.75, 0.25) if u.ultimate_ready() else Color(0.55, 0.35, 0.75))
		ult.add_theme_stylebox_override("fill", sb)
		col.add_child(ult)
		# The unit's own charge is UP — ULTIMATE POINTS. It is filled by
		# fighting; the Summoner's SP is what releases it. Two meters, two
		# names, so "which bar is which" is never a question.
		var upl := Label.new()
		upl.add_theme_font_size_override("font_size", int(10 * f))
		upl.mouse_filter = Control.MOUSE_FILTER_IGNORE
		upl.add_theme_color_override("font_color",
			Color(1.0, 0.85, 0.4) if u.ultimate_ready() else Color(0.68, 0.62, 0.82))
		upl.text = "UP %d/%d%s" % [u.ultimate, Unit.ULT_MAX,
			"  READY" if u.ultimate_ready() else ""]
		col.add_child(upl)
		if u.ultimate_ready() and not battle.finished:
			var ub := Button.new()
			ub.text = "★ ULTIMATE — %d SP" % Battle.ULT_SP_COST
			ub.add_theme_font_size_override("font_size", int(10 * f))
			ub.pressed.connect(_on_unit_ultimate.bind(u))
			col.add_child(ub)

	# ENEMY INTENT — non-negotiable per the GDD.
	if is_enemy and alive and not battle.finished:
		var intent := Label.new()
		intent.add_theme_font_size_override("font_size", int(11 * f))
		intent.add_theme_color_override("font_color", Color(1.0, 0.78, 0.45))
		intent.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		intent.mouse_filter = Control.MOUSE_FILTER_IGNORE
		intent.text = "▶ " + battle.intent_text(u)
		col.add_child(intent)

	var st: String = u.status_summary()
	if st != "":
		var l3 := Label.new()
		l3.add_theme_font_size_override("font_size", int(11 * f))
		l3.add_theme_color_override("font_color", Color(0.7,0.95,0.7))
		l3.mouse_filter = Control.MOUSE_FILTER_IGNORE
		l3.text = st
		col.add_child(l3)

	# What is being offered decides what lights up. During a drag that is the
	# card in the air; outside one it is the card that was clicked. Same rule as
	# _apply_target_glow uses, because it IS that rule.
	var offered: Card = _lift_card if _lift_card != null else selected_card
	b.modulate = _tint_for(u, offered, _actor_for(offered) if offered != null else null)
	return b

# Cards never overlap: the scale is derived from the width actually available,
# so the hand fits at any window size. There is no basics row to share it with
# any more — a basic attack is dragged off the unit's own card.
const ROOT_MARGIN := 24.0         # Root's own left+right offsets
# End Turn moved to the top bar, so the hand row is the hand's alone — this is
# only the breathing room kept at either end of it.
const END_TURN_W := 24.0

# `spread` is how many CARD WIDTHS the row actually needs once the stacks
# overlap — a group of four at half overlap is 2.5 widths, not four.
func _hand_scale(spread: float, groups: int) -> float:
	if spread <= 0.0: return 1.0
	var vp := get_viewport_rect().size
	var avail: float = maxf(320.0, vp.x - ROOT_MARGIN - END_TURN_W)
	var wanted: float = spread * CardView.W + float(groups) * 16.0 + 28.0
	var by_w: float = avail / maxf(1.0, wanted)
	# THE HAND GETS WHAT IS LEFT, and the sum has to be honest: when it is not,
	# the whole column overflows and Godot grows it in BOTH directions, which
	# takes the top bar off the top of the screen rather than the hand off the
	# bottom. Every term below is a real node's real minimum height.
	#
	#   chrome  Root's own top and bottom offsets, the top bar, the prompt line,
	#           and the three separations the column puts between its children.
	#   rows    the two unit rows and their labels, plus the extra height the
	#           Summoner's board carries for the Reserve button.
	var chrome := CHROME_H
	# SIX ROWS OF TILES when both sides stand two deep, fewer tiles of the same
	# size when their room runs three ranks. Tiles are a fixed height — that is
	# the whole reason the grid fits at all — so this is arithmetic rather than
	# an estimate.
	var br: int = _board_rows()
	var rows: float = float(br) * CardView.H * _board_scale() 		+ float(maxi(0, br - 1)) * RANK_GAP
	var left: float = maxf(150.0, vp.y - rows - chrome)
	# Against the card's TILTED height, not its upright one -- the fan's outer
	# cards stand taller than CardView.H and that difference is exactly what was
	# hanging off the bottom edge.
	var by_h: float = (left - 6.0) / (CardView.H
		+ sin(deg_to_rad(FAN_MAX_DEGREES)) * CardView.W)
	# HAND_TRIM keeps the run a shade under whatever it could have: the boards
	# above it are the thing being read now, and a hand packed a little tighter
	# than its own ceiling reads as one run rather than as a second row of boards.
	return clampf(minf(by_w, by_h) * HAND_TRIM, 0.26, 0.86)

# Cards in the hand are grouped BY OWNER and half-stacked within each group, in
# the order those owners stand on the board. Where a card sits in the row
# therefore says whose it is before you read a word of it, and twelve cards take
# the space six used to.
func _hand_groups() -> Array:
	var order: Array = [Battle.SUMMONER_KEY]
	for u in battle.allies: order.append(u.id)
	var by_owner := {}
	for card in battle.hand:
		var k := Battle.owner_key(card)
		if not by_owner.has(k): by_owner[k] = []
		by_owner[k].append(card)
	var out: Array = []
	for k in order:
		if by_owner.has(k): out.append(by_owner[k])
	# Anything owned by nobody on the field still has to be reachable.
	for k2 in by_owner:
		if not (k2 in order): out.append(by_owner[k2])
	return out

# The same grouping, paired with WHOSE each group is. The layout needs the owner
# to find the board to sit under, and the divider needs it to know where one
# person's cards stop and the next person's start.
func _hand_group_keys() -> Array:
	var order: Array = [Battle.SUMMONER_KEY]
	for u in battle.allies: order.append(u.id)
	var by_owner := {}
	for card in battle.hand:
		var k := Battle.owner_key(card)
		if not by_owner.has(k): by_owner[k] = []
		by_owner[k].append(card)
	var out: Array = []
	for k in order:
		if by_owner.has(k): out.append(k)
	for k2 in by_owner:
		if not (k2 in order): out.append(k2)
	return out

# The biggest a card may be and still leave every stack sitting clear of its
# neighbours. One board's share of the party line is what a stack has to fit
# inside; the widest stack is what decides the scale for all of them, because a
# row of cards at two different sizes reads as a mistake.
# The biggest card that still lets every stack fit end to end in the row.
func _hand_fill_scale(spread: float, groups: int) -> float:
	var room: float = _hand_span() - HAND_EDGE * 2.0 - float(maxi(0, groups - 1)) * _hand_gap()
	if room < 40.0 or spread <= 0.0: return 1.0
	return clampf(room / (spread * CardView.W), 0.26, 0.86)


func _hand_metrics(groups: Array) -> float:
	var spread := 0.0
	for g in groups:
		spread += 1.0 + float((g as Array).size() - 1) * (1.0 - _overlap_cached)
	return spread

# One entry per owner with cards in hand: the stack itself, whose it is, and the
# rectangle it ended up occupying. The rects are what the divider is drawn from,
# so the line always lands in the real gap rather than at a guessed position.
var _hand_slots: Array = []
# The card under the pointer. Only ever decides which one is lifted clear of the
# row and which one is allowed to run its clip.
var _hover_card: Card = null
# The party row's width when the hand was last built. The card scale is derived
# from it, so a change here means the hand is the wrong size.
var _hand_row_w := -1.0

# WHERE A CARD CAME FROM. A hand that simply has one more card in it than it did
# a moment ago is a hand you have to re-read to understand; a card that visibly
# comes off the deck is a hand you glanced at. This is presentation only — the
# rules dealt the card long before this runs.
#
# It animates a COPY, face down, over the overlay. The real card is laid out by
# the fan every frame (see _fan_hand), so tweening the real one would be two
# things writing the same position and the fan would win.
var _seen_cards: Dictionary = {}         # Card -> true, everything already dealt
const DRAW_FLIGHT := 0.26
const DRAW_FLIGHT_MAX := 5               # never more than a handful at once

# WHERE A DRAWN CARD FLIES FROM. The real chip once it exists, and the right end
# of the hand row before it does — on the very first build the rails have not
# been laid out yet, and a flight from (0,0) reads as a card thrown in from off
# the top corner of the screen.
var _deck_chip: Control = null
# The spent pile, kept for the same reason the deck is: a flight needs somewhere
# real to go, and "wherever the rail put it" is only knowable after it is built.
var _discard_chip: Control = null

func _deck_rect() -> Rect2:
	if _deck_chip != null and is_instance_valid(_deck_chip) and _deck_chip.size.x > 4.0:
		return _deck_chip.get_global_rect()
	var hs := _hand_view_scale()
	var w: float = CardView.W * hs * 0.62
	return Rect2(hand_box.global_position + Vector2(hand_box.size.x - w, 0.0),
		Vector2(w, CardView.H * hs * 0.80))

func _fly_draw(card: Card, to: Rect2, delay: float) -> void:
	if card == null or overlay == null: return
	var cv := CardView.new()
	cv.is_overlay = true
	cv.summoner_id = _summoner_id()
	cv.set_view_scale(_hand_view_scale())
	cv.setup(card, "")
	cv.set_flipped(true)                  # it arrives face down and turns over
	cv.mouse_filter = Control.MOUSE_FILTER_IGNORE
	cv.z_index = 300
	overlay.add_child(cv)
	var from := _deck_rect()
	cv.position = from.position - overlay.global_position
	cv.scale = Vector2(0.6, 0.6)
	cv.modulate.a = 0.0
	var t := create_tween()
	t.tween_interval(delay)
	t.tween_property(cv, "modulate:a", 1.0, 0.06)
	t.parallel().tween_property(cv, "position",
		to.position - overlay.global_position, DRAW_FLIGHT).set_trans(Tween.TRANS_CUBIC)
	t.parallel().tween_property(cv, "scale", Vector2.ONE, DRAW_FLIGHT)
	t.tween_callback(func(): if is_instance_valid(cv): cv.set_flipped(false))
	t.tween_property(cv, "modulate:a", 0.0, 0.10)
	t.tween_callback(func(): if is_instance_valid(cv): cv.queue_free())

# WHERE THE PLAYED CARD LEAVES FROM: its own view in the hand, or the unit's
# board when the card never had one -- a basic swing is dragged off the unit
# itself and has no card in the fan to fly out of.
func _stage_origin(card: Card, actor: Unit) -> Rect2:
	var v := _view_for(card)
	if v != null and is_instance_valid(v) and v.size.x > 4.0:
		return v.get_global_rect()
	var pr := _panel_rect(actor)
	if pr.size.x > 1.0: return Rect2(pr.position + global_position, pr.size)
	var vp := get_viewport_rect().size
	return Rect2(vp * Vector2(0.5, 0.85), Vector2.ZERO)

func _stage_play(card: Card, from: Rect2, hold_want: float = -1.0) -> void:
	if card == null or overlay == null: return
	var sr := _stage_rect()
	if sr.size.x < 4.0: return
	var big := _stage_card_scale()
	if big <= 0.0: return
	# THE NEWEST CARD IS THE SUBJECT. Played quickly, cards displace each other
	# rather than queueing: a queue puts the stage seconds behind the board, and
	# what you want to see is what just happened, not what happened before it.
	_clear_stage(true)

	var cv := CardView.new()
	cv.is_overlay = true          # which is also what lets the clip play at any size
	cv.summoner_id = _summoner_id()
	cv.mouse_filter = Control.MOUSE_FILTER_IGNORE
	cv.z_index = STAGE_Z
	var who := ""
	for u in battle.allies:
		if u.id == card.owner_id: who = u.display_name.split(" ")[0]
	if card.type == Card.Type.SUMMONER: who = Content.summoner_name(_summoner_id())
	cv.set_view_scale(_hand_view_scale())
	cv.setup(card, who)
	cv.set_bonds(_living_ally_ids())
	cv.set_state(true, false)
	overlay.add_child(cv)
	_staged = cv

	var o: Vector2 = overlay.global_position
	cv.position = from.position - o
	var to: Vector2 = sr.position - o + Vector2(
		(sr.size.x - CardView.W * big) * 0.5, (sr.size.y - CardView.H * big) * 0.5)
	var hold: float = hold_want
	if hold < 0.0:
		hold = STAGE_HOLD
		if auto_on: hold = STAGE_HOLD_AUTO
		elif cv.has_clip(): hold = STAGE_HOLD_CLIP

	var t := create_tween()
	_staged_tween = t
	t.tween_property(cv, "position", to, STAGE_FLY_IN) \
		.set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT)
	t.parallel().tween_method(
		func(f: float): if is_instance_valid(cv): cv.set_view_scale(f),
		_hand_view_scale(), big, STAGE_FLY_IN)
	t.tween_interval(hold)
	# Out to the spent pile, which is where it actually went.
	t.tween_property(cv, "position", _discard_rect().position - o, STAGE_FLY_OUT) \
		.set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_IN)
	t.parallel().tween_property(cv, "scale", Vector2(0.25, 0.25), STAGE_FLY_OUT)
	t.parallel().tween_property(cv, "modulate:a", 0.0, STAGE_FLY_OUT)
	t.tween_callback(func():
		if is_instance_valid(cv): cv.queue_free()
		if _staged == cv: _staged = null)

func _clear_stage(fast: bool) -> void:
	if _staged_tween != null and _staged_tween.is_valid(): _staged_tween.kill()
	_staged_tween = null
	if _staged == null or not is_instance_valid(_staged):
		_staged = null
		return
	var old := _staged
	_staged = null
	if not fast:
		old.queue_free()
		return
	# A short bow-out rather than vanishing, so a fast exchange still reads as
	# one card leaving and another arriving.
	var t := create_tween()
	t.tween_property(old, "modulate:a", 0.0, 0.10)
	t.parallel().tween_property(old, "scale", Vector2(0.7, 0.7), 0.10)
	t.tween_callback(func(): if is_instance_valid(old): old.queue_free())

# ------------------------------------------------------------ their side, staged
#
# THE OTHER SIDE'S ROUND IS SOMETHING YOU WATCH. Handing the turn over resolves
# every enemy action in the rules in one go — the board they leave behind is
# already final — but the board alone never said WHO did what. Each play the
# enemy side made arrives here as a record, and the replay stages them one at a
# time in the middle, fastest first, exactly the treatment your own cards get:
# the creature's card flies out of their line, holds long enough to read, and
# leaves for the discard while the effect lands on whoever it hit.
#
# It is a REPLAY by design rather than a slow-down of the rules: the fight's
# truth is settled before the first card flies, so nothing here can stall or
# desync the game — and the player can act, or hand the turn over again, while
# the last of their opponents' moves is still making its entrance.
var _enemy_replay: Array = []
var _replaying := false
var _replay_gap := 0.0

func _on_enemy_played(what: Dictionary) -> void:
	_enemy_replay.append(what)

func _begin_enemy_replay() -> void:
	if _enemy_replay.is_empty(): return
	_replaying = true
	_replay_gap = 0.05

func _stage_enemy_next() -> void:
	if battle.finished:
		_enemy_replay.clear()
		_replaying = false
		return
	var what: Dictionary = _enemy_replay.pop_front()
	var card: Card = what.get("card", null)
	var actor: Unit = what.get("actor", null)
	var target: Unit = what.get("target", null)
	_replay_gap = 0.12
	# The commander's ultimate is weather, not a card: no flying card, just the
	# screen-shaking landing and the sound.
	if card == null:
		_fx.kick(0.9)
		_sfx("ultimate")
		_replay_gap = 0.45
		return
	var from: Rect2 = _panel_rect(actor) if actor != null else Rect2()
	if from.size.x < 4.0: from = _stage_rect()
	# A whole room moves after yours does, so theirs plays faster than yours:
	# long enough to read the card, short enough that a ten-body round does not
	# hold the stage for half a minute.
	_stage_play(card, from, 0.35 if _enemy_replay.size() < 5 else 0.18)
	if target != null:
		_spawn_vfx(card, target, _panel_rect(target))
	if card.effective_heal() > 0.0: _sfx("heal")
	elif card.effective_power() > 0.0: _sfx("hit")
	else: _sfx("card")

# Where the spent pile is, for the flight out. Mirrors _deck_rect exactly,
# including its "the rail is not built yet" fallback.
func _discard_rect() -> Rect2:
	if _discard_chip != null and is_instance_valid(_discard_chip) \
			and _discard_chip.size.x > 4.0:
		return _discard_chip.get_global_rect()
	return Rect2(hand_box.global_position, Vector2(40.0, 40.0))

# Anything in hand this frame that was not in hand last time it was built came
# off the deck. Called after the hand has been laid out, so the destination is
# the card's real resting place rather than a guess at it.
func _fly_new_cards() -> void:
	if battle == null or _lift_card != null: return
	var flown := 0
	for stack in hand_box.get_children():
		for ch in stack.get_children():
			var cv := ch as CardView
			if cv == null or cv.card == null: continue
			if _seen_cards.has(cv.card): continue
			_seen_cards[cv.card] = true
			if flown < DRAW_FLIGHT_MAX:
				# A card arriving while one is still going out crosses it in the
				# same half second; let the stage have its entrance first.
				var wait: float = float(flown) * 0.05
				if _staged != null and is_instance_valid(_staged): wait += STAGE_FLY_IN
				_fly_draw(cv.card, cv.get_global_rect(), wait)
				flown += 1
	# Cards that have left the hand are forgotten, so one that comes back round
	# after a reshuffle flies again.
	for c in _seen_cards.keys():
		if not (c in battle.hand): _seen_cards.erase(c)

# HOW MANY TIMES THE HAND HAS BEEN REBUILT. A layout that measures itself against
# something that measures back rebuilds forever, and it looks like a stutter
# rather than an error -- this is the number that says so.
var _hand_rebuilds := 0
# Where the two runs part, in hand-row coordinates. -1 when the Summoner is
# holding nothing and there is only one run to draw.
var _hand_split_x := -1.0

func _rebuild_hand() -> void:
	_hand_rebuilds += 1
	if hand_gap != null:
		hand_gap.custom_minimum_size = Vector2(0, _hand_clearance())
	# Never while a card is being pressed: freeing the view mid-gesture cuts off
	# its motion and release, and the lifted copy is left stranded on screen.
	if _lift_card != null: return
	for c in hand_box.get_children(): c.queue_free()
	_hand_slots = []
	var groups := _hand_groups()
	var keys := _hand_group_keys()
	if groups.is_empty():
		_hand_row_w = ally_box.size.x
		hand_box.queue_redraw()
		return
	_hand_row_w = ally_box.size.x
	# WIDTH FIRST, then height. Packed left, the row's whole span is available to
	# the cards, so the size is whatever fills it — capped by the height the
	# column can spare, which is usually the one that binds.
	# THE SOLVER DECIDES THE HEIGHT; the width only ever makes it smaller. A hand
	# that sized itself independently of the board is a hand that overlaps it.
	#
	# _hand_fill_scale is deliberately NOT in this minimum any more. Its whole job
	# was shrinking cards until the run fitted the row, and the run is now made to
	# fit by spacing instead -- leaving it in would shrink the cards and then
	# spread them, which is the opposite of the point.
	var hs: float = minf(_solve_scales().y, _hand_scale(1.0, groups.size()))
	# THE MEASUREMENT THAT MAKES A COLLISION IMPOSSIBLE. The last attempt at
	# splitting this hand put the Summoner's cards at one end and centred
	# everybody else's, with two rules that never checked each other -- and they
	# overlapped outright.
	#
	# One inequality fixes it, using the closed form the fan already has: a run
	# of N cards at overlap ov is W*hs*(N - ov*(N-1)) wide WHATEVER the grouping.
	# So the party's overlap is solved against the room left when the Summoner's
	# run is at its LOOSEST -- _solve_overlap's own 0.28 floor. The run actually
	# built is narrower than the one budgeted for, so the room the party gets can
	# only be bigger than the room it was fitted to. It cannot go the other way.
	var ns := 0
	for c0 in battle.hand:
		if Battle.owner_key(c0) == Battle.SUMMONER_KEY: ns += 1
	var w_card: float = CardView.W * hs
	var sum_w_max: float = 0.0 if ns == 0 \
		else w_card * (float(ns) - 0.28 * float(maxi(0, ns - 1)))
	var room_min: float = maxf(80.0, _hand_span() - HAND_EDGE * 2.0 - sum_w_max
		- (HAND_SPLIT_GAP if ns > 0 else 0.0))
	_overlap_cached = _solve_overlap(battle.hand.size() - ns, hs, room_min)
	var spread := _hand_metrics(groups)
	_hand_view_scale_cached = hs
	# NO SLACK UNDER THE HAND. The row is the last thing in the column, so any
	# height it reserves past the cards themselves is a strip of empty screen
	# below them -- the cards should very nearly touch the bottom edge.
	# Room for the TILTED corners as well as the upright height: the outer cards
	# of a fan reach lower than the middle ones, and at +2 they were cut off by
	# the bottom of the screen.
	hand_box.custom_minimum_size = Vector2(0,
		CardView.H * hs + 2.0 + sin(deg_to_rad(FAN_MAX_DEGREES)) * CardView.W * hs)
	for i in groups.size():
		var g: Array = groups[i]
		var stack := HBoxContainer.new()
		# A negative separation is the overlap. Later children draw on top, so
		# the rightmost card of a stack is the one fully visible and the one
		# that takes the click.
		stack.add_theme_constant_override("separation",
			int(-CardView.W * hs * _overlap_cached))
		stack.mouse_filter = Control.MOUSE_FILTER_PASS
		hand_box.add_child(stack)
		for card in g:
			stack.add_child(_card_view(card, hs))
		var gw: float = CardView.W * hs * (1.0 + float(g.size() - 1) * (1.0 - _overlap_cached))
		stack.size = Vector2(gw, CardView.H * hs)
		_hand_slots.append({"node": stack, "key": str(keys[i]), "w": gw,
			"h": CardView.H * hs, "x": 0.0,
			"run": 0 if str(keys[i]) == Battle.SUMMONER_KEY else 1})
	_layout_hand()
	_fan_hand()
	# Deferred: the stacks were positioned this frame but their children have
	# not been sorted yet, so a rect read now would be the previous layout's.
	call_deferred("_fly_new_cards")

# THE HAND IS PACKED LEFT AND TIGHT, one owner's stack after another with a
# hairline between them.
#
# It used to centre each stack on its owner's board. That read well and cost too
# much: a board's worth of air opened up between every group, the row never
# reached the end of itself, and every card had to shrink to pay for the gaps.
# Whose card is whose was never really carried by the alignment anyway — it is
# on the card, in the name and the coloured border, and the divider between
# groups says where one person's cards stop.
#
# Spending that width on the CARDS instead is the trade: they are what you have
# to read, and they are what will be carrying moving art.
func _layout_hand() -> void:
	if _hand_slots.is_empty(): return
	var w: float = _hand_span()
	if w < 4.0: return
	var left: float = _hand_left()
	var right: float = left + w - HAND_EDGE
	_hand_split_x = -1.0

	# THEIRS AT THE FAR LEFT, under their own board in the rail; YOURS centred in
	# what is left. Two runs, and the second is measured against where the first
	# actually ENDED rather than against the row -- which is the one thing the
	# previous attempt did not do, and exactly where it collided.
	var cursor: float = left + HAND_EDGE
	var sum_end: float = cursor
	for e in _hand_slots:
		if int(e.get("run", 1)) != 0: continue
		e["x"] = cursor
		cursor += float(e["w"]) + _hand_gap()
		sum_end = float(e["x"]) + float(e["w"])

	var party_left: float = left + HAND_EDGE
	if sum_end > left + HAND_EDGE:
		party_left = sum_end + HAND_SPLIT_GAP
		_hand_split_x = sum_end + HAND_SPLIT_GAP * 0.5

	var total := 0.0
	var n_party := 0
	for e2 in _hand_slots:
		if int(e2.get("run", 1)) == 0: continue
		total += float(e2["w"])
		n_party += 1
	total += _hand_gap() * float(maxi(0, n_party - 1))

	# maxf(0.0, ...) IS THE SECOND HALF OF THE GUARANTEE. However wide the party's
	# run measures, it starts at party_left or to the right of it: a run too wide
	# for its room overflows to the RIGHT, into the rail's margin, and can never
	# reach back across the divider.
	var pc: float = party_left + maxf(0.0, (right - party_left - total) * 0.5)
	for e3 in _hand_slots:
		if int(e3.get("run", 1)) == 0: continue
		e3["x"] = pc
		pc += float(e3["w"]) + _hand_gap()

	for e4 in _hand_slots:
		var n4 = e4["node"]
		if n4 == null or not is_instance_valid(n4): continue
		n4.position = Vector2(float(e4["x"]), 0.0)
		n4.size = Vector2(float(e4["w"]), float(e4["h"]))
	hand_box.queue_redraw()
	_raise_focused_card()
	hand_box.queue_redraw()

# Lift whichever card is armed or under the pointer clear of its neighbours, and
# let it — and only it — play its clip.
#
# z_index rather than reordering the children: moving a node while a gesture is
# live on it is exactly how a drag gets orphaned, which this screen has already
# been bitten by twice.
func _raise_focused_card() -> void:
	var want: Card = selected_card if selected_card != null else _hover_card
	for e in _hand_slots:
		var stack = e["node"]
		if stack == null or not is_instance_valid(stack): continue
		var lifted := false
		for ch in stack.get_children():
			var cv := ch as CardView
			if cv == null: continue
			var on: bool = want != null and cv.card == want
			if cv.has_method("set_media_live"): cv.set_media_live(on)
			if cv.has_method("set_pop"): cv.set_pop(HAND_HOVER_SCALE if on else 1.0)
			# IN FRONT OF ITS OWN NEIGHBOURS, not just its own stack. Cards later
			# in a stack paint after the ones before them, so the card that grew
			# was being covered by the next one along -- which is what put a
			# small second card in the corner of the big one.
			cv.z_index = 20 if on else 0
			if on: lifted = true
		stack.z_index = 8 if lifted else 0
		stack.position.y = -HAND_RAISE if lifted else 0.0

func _hand_gap() -> float:
	return -CardView.W * _hand_view_scale() * _overlap_cached

func _hand_left() -> float:
	return _left_rail_w() + BODY_SEPARATION

func _hand_span() -> float:
	return maxf(120.0, hand_box.size.x - _left_rail_w() - _right_rail_w()
		- BODY_SEPARATION * 2.0)

# The scale the hand was last built at, which the deck matches so it reads as
# one of the same cards rather than a widget parked beside them.
var _hand_view_scale_cached := 0.5

func _hand_view_scale() -> float:
	return _hand_view_scale_cached

# Tilt a stack into a shallow fan, centred on its middle card. Pivots are set to
# the BOTTOM of each card so they splay from a held corner rather than spinning
# about their own middles.
# EVERY CARD IN THE HAND, in order, whoever owns it. The fan is a property of
# the hand rather than of one owner's stack -- fanning the stacks separately is
# what made the row read as several small flat hands laid side by side.
# THE TWO RUNS FAN SEPARATELY. One arc across both would put the Summoner's
# three cards at the extreme end of a fifteen-card wedge -- maximum tilt, least
# rise -- reading as a piece that broke off the party's hand rather than as a
# hand of its own.
func _hand_runs() -> Array:
	var runs: Array = [[], []]
	for e in _hand_slots:
		var stack = e["node"]
		if stack == null or not is_instance_valid(stack): continue
		var r: int = 0 if int(e.get("run", 1)) == 0 else 1
		for ch in stack.get_children():
			var cv := ch as CardView
			if cv != null: runs[r].append(cv)
	return runs

func _hand_cards_in_order() -> Array:
	var out: Array = []
	for e in _hand_slots:
		var stack = e["node"]
		if stack == null or not is_instance_valid(stack): continue
		for ch in stack.get_children():
			var cv := ch as CardView
			if cv != null: out.append(cv)
	return out

func _fan_hand() -> void:
	for run in _hand_runs():
		_fan_run(run)

func _fan_run(cards: Array) -> void:
	var n: int = cards.size()
	if n < 2: return
	var mid: float = float(n - 1) * 0.5
	for i in n:
		var cv: CardView = cards[i]
		var t: float = (float(i) - mid) / maxf(1.0, mid)
		# Pivoted at the BOTTOM, so the run splays from a held corner instead of
		# each card spinning about its own middle.
		cv.pivot_offset = Vector2(cv.custom_minimum_size.x * 0.5,
			cv.custom_minimum_size.y)
		# CAPPED at the ends. Per-card degrees times half the hand is a wedge by
		# the time twelve cards are out -- the outer ones swing up over the prompt
		# line and their corners leave the row.
		cv.rotation_degrees = t * clampf(FAN_DEGREES * mid, 0.0, FAN_MAX_DEGREES)
		# An arc, not a wedge: the middle of the hand rides highest and the ends
		# drop away, which is the shape a fist full of cards actually makes.
		# ASSIGNED, not subtracted: the layout runs every frame, and a relative
		# nudge would walk the hand off the top of the screen in a second.
		cv.position.y = -(1.0 - t * t) * FAN_RISE

func _fan(stack: HBoxContainer) -> void:
	var n: int = stack.get_child_count()
	if n < 2: return
	var mid: float = float(n - 1) * 0.5
	for i in n:
		var cv := stack.get_child(i) as CardView
		if cv == null: continue
		var t: float = float(i) - mid
		cv.pivot_offset = Vector2(cv.custom_minimum_size.x * 0.5,
			cv.custom_minimum_size.y)
		cv.rotation_degrees = t * FAN_DEGREES
		# The middle of the fan sits highest, which is what stops the tilt
		# reading as a row that has come loose.
		cv.position.y -= (1.0 - absf(t) / maxf(1.0, mid)) * FAN_RISE

# THE DIVIDER. A hairline of light on a dark backing, in the gap between two
# owners' stacks - two tones so it stays visible over card art at one end of it
# and over the room illustration at the other. It is drawn rather than inserted
# as a node so it can sit in a gap whose width the layout decides.
func _on_hand_draw() -> void:
	var h: float = hand_box.size.y
	# THE ONE DIVIDER THAT MEANS SOMETHING, with the reason written either side
	# of it. The rule between two owners' stacks never actually draws -- every
	# gap in a run is negative, they overlap -- but the gap between the two RUNS
	# is real, and without a label it is a hole rather than a rule.
	if _hand_split_x > 0.0:
		var sx: float = _hand_split_x
		hand_box.draw_line(Vector2(sx, h * 0.06), Vector2(sx, h * 0.94),
			Color(0, 0, 0, 0.85), 7.0)
		hand_box.draw_line(Vector2(sx, h * 0.06), Vector2(sx, h * 0.94),
			Color(0.96, 0.90, 0.72, 0.85), 2.0)
		var f := ThemeDB.fallback_font
		# In the colours the two costs already wear on the cards themselves, so
		# the tag and the badge agree rather than being two separate codes.
		# INSIDE THE GAP, both of them: the row is wall-to-wall cards and the
		# hand's own canvas draws BEHIND its children, so a label a hair outside
		# the gap is a label behind a card.
		hand_box.draw_string(f, Vector2(sx - 44.0, h * 0.11), "CP",
			HORIZONTAL_ALIGNMENT_RIGHT, 38.0, 13, Color(0.85, 0.72, 0.35, 0.9))
		hand_box.draw_string(f, Vector2(sx + 6.0, h * 0.11), "AP",
			HORIZONTAL_ALIGNMENT_LEFT, 38.0, 13, CardView.COL_AP * Color(1, 1, 1, 0.9))
	if _hand_slots.size() < 2: return
	for i in range(_hand_slots.size() - 1):
		var a: Dictionary = _hand_slots[i]
		var b: Dictionary = _hand_slots[i + 1]
		# The gap between the two RUNS is the split, and it has already been
		# drawn with its labels; without this it gets a second, unlabelled rule
		# a few pixels away from the first.
		if int(a.get("run", 1)) != int(b.get("run", 1)): continue
		var left: float = float(a["x"]) + float(a["w"])
		var right: float = float(b["x"])
		if right <= left: continue        # crowded together; no room for a rule
		var x: float = (left + right) * 0.5
		var top: float = h * 0.06
		var bot: float = h * 0.94
		hand_box.draw_line(Vector2(x, top), Vector2(x, bot), Color(0, 0, 0, 0.85), 7.0)
		hand_box.draw_line(Vector2(x, top), Vector2(x, bot), Color(0.96, 0.90, 0.72, 0.85), 2.0)

# THE DRAW PILE. Face down, at the end of the hand, sized and angled like the
# cards in front of it so it reads as the rest of the same deck rather than as a
# counter someone bolted on.
#
# The number is the one that matters — how many cards the party has left to pull
# — with what has already been used underneath it, because the two together are
# what tell you a reshuffle is coming. The piles have always reshuffled from the
# discard when they ran dry; nothing on screen ever said so, which left "why did
# I stop drawing" unanswerable.
const DECK_LEAVES := 3             # how many card edges the stack shows

# Recomputed per redraw: a bond is only worth what it is worth RIGHT NOW, and
# an ally going down has to show on every card that named them.
func _living_ally_ids() -> Dictionary:
	var out := {}
	for u in battle.allies:
		if u.is_alive(): out[u.id] = true
	return out

func _card_view(card: Card, view_scale: float = 1.0) -> CardView:
	var cv := CardView.new()
	var who := ""
	for u in battle.allies:
		if u.id == card.owner_id: who = u.display_name.split(" ")[0]
	if card.type == Card.Type.SUMMONER: who = Content.summoner_name(_summoner_id())
	cv.summoner_id = _summoner_id()
	cv.set_view_scale(view_scale)
	cv.setup(card, who)
	cv.set_bonds(_living_ally_ids())
	var actor := _actor_for(card)
	var ok := battle.can_play(card, actor)
	cv.set_state(ok and not battle.finished, card == selected_card)
	cv.mouse_entered.connect(func():
		if _hover_card != card:
			_hover_card = card
			_raise_focused_card())
	cv.mouse_exited.connect(func():
		if _hover_card == card:
			_hover_card = null
			_raise_focused_card())
	cv.press_started.connect(_on_press_started)
	cv.press_moved.connect(_on_press_moved)
	cv.press_ended.connect(_on_press_ended)
	cv.hold_begun.connect(_on_hold_begun)
	# The card being dragged is shown in the air instead of in the hand.
	if _lift_card != null and card == _lift_card:
		cv.modulate = Color(1, 1, 1, 0.25)
	return cv

# The card knows whose it is, so picking an "acting unit" separately is
# redundant — resolve it here instead of asking.
func _actor_for(card: Card) -> Unit:
	if card == null: return null
	if card.type == Card.Type.SUMMONER:
		return null
	if card.owner_id != "":
		for u in battle.allies:
			if u.id == card.owner_id and u.is_alive():
				return u
		return null
	for u in battle.allies:                    # unowned: first who can pay
		if battle.can_play(card, u):
			return u
	return null

# --------------------------------------------------------- drag, hold, flick

# NOTHING here may call refresh(). refresh() rebuilds the hand, which frees the
# very CardView the press came from — and a freed view sends no more motion or
# release, so the lifted copy is orphaned on screen and the drag silently dies.
# That is a rebuild during a live gesture; the fix is to touch only what changed.
func _on_press_started(card: Card, at: Vector2) -> void:
	_close_discard()
	if battle.finished: return
	_lift_card = card
	# Whatever was expanded under the pointer gets out of the way of the drag.
	_hide_unit_zoom()
	_lift_from = at
	_lift_at = at
	_holding = false
	_flip_t = 1.0
	_hover_target = null
	var groups := _hand_groups()
	_lift_scale = _hand_scale(_hand_metrics(groups), maxi(1, groups.size()))
	_lift = CardView.new()
	# Over everything: the board, the fan, and any expanded card.
	_lift.is_overlay = true
	_lift.summoner_id = _summoner_id()
	_lift.set_view_scale(_lift_scale * LIFT_SCALE)
	var who := ""
	for u in battle.allies:
		if u.id == card.owner_id: who = u.display_name.split(" ")[0]
	_lift.setup(card, who)
	_lift.set_bonds(_living_ally_ids())
	_lift.set_state(battle.can_play(card, _actor_for(card)) and not battle.finished, true)
	overlay.add_child(_lift)
	_lift.z_index = 400
	_place_lift(at)
	# The card is in the air, so the one in the hand is shown as its empty slot.
	var src := _view_for(card)
	if src != null: src.modulate = Color(1, 1, 1, 0.25)
	_apply_target_glow()

func _on_press_moved(_card: Card, at: Vector2) -> void:
	if _lift == null: return
	_lift_at = at
	_place_lift(at)
	var was := _hover_target
	_hover_target = _unit_under(at)
	if was != _hover_target:
		_apply_target_glow()
		if _hover_target != null: _set_focus(_hover_target)

# The CardView currently showing this card, in the hand or the basics row.
func _view_for(card: Card) -> CardView:
	for stack in hand_box.get_children():
		for ch in stack.get_children():
			if ch is CardView and (ch as CardView).card == card:
				return ch
	return null

# A unit's always-available swing — 1 AP, no EP. Taken from the battle rather
# than rebuilt here, so it is the same card the rules already know about.
func _basic_for(u: Unit) -> Card:
	for card in battle.basic_attacks():
		if card.owner_id == u.id:
			return card
	return null

func _on_hold_begun(_card: Card) -> void:
	if _lift == null: return
	_holding = true
	_sfx("select")

func _on_press_ended(card: Card, at: Vector2, velocity: Vector2) -> void:
	var was_holding := _holding
	var lift_from := _lift_from
	_clear_lift()
	var src := _view_for(card)
	if src != null: src.modulate = Color(1, 1, 1, 1)
	if battle.finished: return
	# A hold was someone reading the card. Reading costs nothing and plays
	# nothing — releasing simply puts it back.
	if was_holding:
		_apply_target_glow()
		return
	var actor := _actor_for(card)
	var target := _unit_under(at)
	if target != null and battle.is_valid_target(card, actor, target):
		var aim: Vector2 = _panel_rect(target).get_center() + global_position - lift_from
		_commit(card, actor, target, Flick.accuracy_of(velocity, aim))
		return
	# Dropped on nothing. A card that needs no target plays where it lands;
	# anything else falls back to the click-then-click path.
	if card.target in [Card.Target.NONE, Card.Target.ALL_ENEMIES,
			Card.Target.ALL_ALLIES, Card.Target.SELF]:
		if (at - lift_from).length() > CardView.HOLD_SLOP:
			_commit(card, actor, actor, Flick.accuracy_of(velocity, at - lift_from))
			return
	# A DROP that went somewhere illegal — an attack card let go over your own
	# party, say — puts the card back and clears the board. It must not fall
	# through to "selected": that latched an untargetable card to the cursor,
	# and since _commit refused every click after it, nothing could shake it off.
	if (at - lift_from).length() > CardView.HOLD_SLOP:
		selected_card = null
		selected_actor = null
		_refresh_soon()
		return
	# Barely moved, so it was a click rather than a drag.
	_on_card_tapped(card)

# Centred on the pointer. Always placed from the pointer position rather than
# from its own last position, so a scale change cannot make it drift.
func _place_lift(at: Vector2) -> void:
	if _lift == null: return
	var sz: Vector2 = _lift.custom_minimum_size
	_lift.size = sz
	_lift.position = at - overlay.global_position - sz * 0.5

# The lift grows and turns over while held, and settles back when it is not.
func _animate_lift(delta: float) -> void:
	if _lift == null: return
	var want_turn: float = -1.0 if _holding else 1.0
	_flip_t = move_toward(_flip_t, want_turn, delta * 7.0)
	_lift.set_turn(_flip_t)
	# The face swaps at edge-on, which is what sells it as one object turning
	# rather than two pictures cross-fading.
	_lift.set_flipped(_flip_t < 0.0)
	var want_scale: float = _lift_scale * LIFT_SCALE
	if _holding:
		want_scale = _lift_scale * HOLD_SCALE
	elif _hover_target != null:
		want_scale = _lift_scale * DROP_SCALE
	if absf(_lift.view_scale - want_scale) > 0.005:
		_lift.set_view_scale(move_toward(_lift.view_scale, want_scale, delta * 2.4))
	_place_lift(_lift_at)

func _clear_lift() -> void:
	if _lift != null and is_instance_valid(_lift):
		_lift.queue_free()
	_lift = null
	_lift_card = null
	_holding = false
	_hover_target = null
	_lift_synthetic = false

# A gesture that never got its release. The card stays glued to the cursor, and
# because a live gesture deliberately blocks the hand from rebuilding, the whole
# screen then reads as frozen: cards do not move, the hand does not refresh, and
# nothing you click has any effect.
#
# It is not worth chasing every way the release can go missing - a view freed
# mid-drag, a press that started over a control that was rebuilt under it, the
# pointer leaving the window. THE BUTTON IS UP, so nothing may still be holding
# a card. That single fact is checked every frame and it closes the whole class.
var _lift_synthetic := false          # the harness lifts cards with no mouse

# Read from _input rather than from Input.is_mouse_button_pressed, because
# _input sees every event before the GUI does — including a touch on a phone,
# which is the platform this actually ships on.
var _pointer_down := false
var _pointer_up_for := 0.0

func _input(event: InputEvent) -> void:
	if event is InputEventMouseButton:
		var mb := event as InputEventMouseButton
		if mb.button_index == MOUSE_BUTTON_LEFT:
			_pointer_down = mb.pressed
	elif event is InputEventScreenTouch:
		_pointer_down = (event as InputEventScreenTouch).pressed

# A grace period, not an instant snap: the release event and this check can land
# in either order within a frame, and clearing a gesture one frame early would
# eat legitimate drops.
const GESTURE_GRACE := 0.25

# Whether the rings were showing on the last frame, so the cells are redrawn on
# the frame that changes and not on every frame.
var _rings_shown := false

func _tick_rings() -> void:
	var now := _placing()
	if now == _rings_shown: return
	_rings_shown = now
	for key in _cells:
		var slot = _cells[key]
		if slot == null or not is_instance_valid(slot): continue
		for ch in (slot as Control).get_children():
			(ch as Control).queue_redraw()

func _guard_gestures(delta: float) -> void:
	if _pointer_down:
		_pointer_up_for = 0.0
		return
	_pointer_up_for += delta
	if _lift_synthetic: return
	if _lift_card == null and _u_press.is_empty() and _bench_drag == null: return
	if _pointer_up_for < GESTURE_GRACE: return
	_u_press.clear()
	# A body left in the air by a release that went missing is the same bug as a
	# card left in the air, and it gets the same guarantee.
	_clear_bench_drag()
	# A dossier opened by a hold whose release went missing would sit there over
	# the board with nothing left to close it.
	if _dossier_held: _close_dossier()
	if _lift_card == null: return
	_clear_lift()
	selected_card = null
	selected_actor = null
	refresh()

# A refresh asked for from inside a gesture or a button press. refresh() frees
# and rebuilds every card view and every unit board - including, quite often,
# the very control whose signal is running right now. Deferring it lets that
# control finish handling its own event before it is taken apart.
var _refresh_queued := false

func _refresh_soon() -> void:
	if _refresh_queued: return
	_refresh_queued = true
	_run_queued_refresh.call_deferred()

func _run_queued_refresh() -> void:
	_refresh_queued = false
	refresh()

func _unit_under(at: Vector2) -> Unit:
	for u in battle.field_units():
		var panel = _panels.get(u.id, null)
		if panel == null or not is_instance_valid(panel): continue
		if panel.get_global_rect().has_point(at):
			return u
	return null

# Only the tint changes, so this RE-TINTS the panels that already exist rather
# than rebuilding them. Rebuilding mid-gesture would free the panels the drop
# test reads and churn the whole board once per pointer move.
func _apply_target_glow() -> void:
	var offered: Card = _lift_card if _lift_card != null else selected_card
	var actor: Unit = _actor_for(offered) if offered != null else null
	for u in battle.field_units():
		var panel = _panels.get(u.id, null)
		if panel == null or not is_instance_valid(panel): continue
		panel.modulate = _tint_for(u, offered, actor)

func _tint_for(u: Unit, offered: Card, actor: Unit) -> Color:
	if not u.is_alive(): return Color(0.4, 0.4, 0.4)
	if offered == null or not battle.is_valid_target(offered, actor, u):
		return Color(1, 1, 1)
	if u == _hover_target: return Color(1.0, 0.95, 0.55)
	return Color(1.0, 0.72, 0.72) if u.is_enemy else Color(0.7, 1.0, 0.75)

# ------------------------------------------------------------- click fallback

func _on_card_tapped(card: Card) -> void:
	if battle.finished: return
	var actor := _actor_for(card)
	# A CARD YOU CANNOT PLAY NEVER GETS ARMED. Arming one put the screen into
	# "choose a target" for a play that was going to be refused whatever was
	# chosen, which is indistinguishable from the game having hung. Say why
	# instead, and leave the board alone.
	if not battle.can_play(card, actor):
		selected_card = null
		selected_actor = null
		_say(_why_refused(card, actor))
		_sfx("select")
		_refresh_soon()
		return
	_sfx("select")
	# No target needed — it resolves on the spot, so it is never "armed". Arming
	# it first and clearing it inside _commit meant anything going wrong in
	# between left the card latched to the cursor with nothing able to shake it
	# off, which is how one bad play froze the whole screen.
	if card.target == Card.Target.NONE or card.target == Card.Target.ALL_ENEMIES \
		or card.target == Card.Target.ALL_ALLIES or card.target == Card.Target.SELF:
		selected_card = null
		selected_actor = null
		_commit(card, actor, actor, 0.0)
		return
	selected_card = card
	selected_actor = actor
	_refresh_soon()

# Four gestures, and none of them wait on another:
#   drag        swing this unit's basic attack at whatever you drop it on
#   hold        turn the card over and read it
#   double tap  open the art out, and again to fold it back
#   tap         choose this unit as the target of a card already picked up
# There is deliberately no single-tap ACTION. If there were, every tap would
# have to wait a quarter second to find out whether a second one was coming.
func _on_unit_input(event: InputEvent, u: Unit) -> void:
	if battle.finished or not u.is_alive(): return
	var mine: bool = not u.is_enemy
	if event is InputEventMouseButton:
		var mb := event as InputEventMouseButton
		if mb.button_index != MOUSE_BUTTON_LEFT: return
		if mb.pressed:
			# ENEMIES TAKE A PRESS TOO now, so a hold can turn their board over
			# and show what they can actually do. They used to register nothing
			# at all, which is why the only thing you ever learned about a
			# creature was the one move it had committed to this round.
			if _lift_card == null:
				_u_press[u.id] = {"at": mb.global_position, "elapsed": 0.0,
					"moved": false, "lifted": false}
			return
		var st: Dictionary = _u_press.get(u.id, {})
		_u_press.erase(u.id)
		if _lift_card != null and bool(st.get("lifted", false)):
			# WHERE IT LANDED DECIDES WHAT IT WAS. Dropped on one of your own
			# cells, the drag was a unit walking; dropped anywhere else it was
			# the swing it started as. One gesture, two meanings, and the board
			# says which without the player choosing a mode first.
			if not u.is_enemy:
				var cell := _ally_cell_under(mb.global_position)
				if cell.x >= 0 and _try_move(u, cell):
					return
			var from: Vector2 = st.get("at", mb.global_position)
			var secs: float = maxf(0.016, float(st.get("elapsed", 0.1)))
			_on_press_ended(_lift_card, mb.global_position,
				(mb.global_position - from) / secs)
			return
		if _holding:
			_clear_lift()
			return
		# A dossier opened by HOLDING closes when the hold ends, exactly as a
		# held card turns back over on release. One pinned by a double tap does
		# not — see below.
		if _dossier_held and _dossier_for == u.id:
			_close_dossier()
			return
		# A card is already in hand and looking for a target: this is that.
		if selected_card != null:
			_on_unit_pressed(u)
			return
		var now: float = float(Time.get_ticks_msec()) / 1000.0
		if now - float(_u_last_tap.get(u.id, -9.0)) <= DOUBLE_TAP:
			_u_last_tap.erase(u.id)
			if mine:
				_expanded[u.id] = not bool(_expanded.get(u.id, false))
				_sfx("select")
				_refresh_soon()
			else:
				# Double tap PINS their dossier open, so you can read a long
				# move list without keeping a finger down on it.
				if _dossier_for == u.id: _close_dossier()
				else: _open_dossier(u, false)
			return
		_u_last_tap[u.id] = now
	elif event is InputEventMouseMotion and _u_press.has(u.id):
		var st2: Dictionary = _u_press[u.id]
		var at: Vector2 = (event as InputEventMouseMotion).global_position
		if bool(st2["lifted"]):
			_on_press_moved(_lift_card, at)
			return
		if (at - Vector2(st2["at"])).length() > CardView.HOLD_SLOP:
			if u.is_enemy:
				st2["moved"] = true      # a drag off an enemy board is nothing
				return
			var basic := _basic_for(u)
			if basic == null or not battle.can_play(basic, u): return
			st2["moved"] = true
			st2["lifted"] = true
			_on_press_started(basic, Vector2(st2["at"]))
			_on_press_moved(basic, at)

# Walk a unit to a cell, if the rules allow it. Returns whether the drag was
# consumed as a move — false means it was not one, and the caller should carry
# on treating it as the swing it started as.
func _try_move(u: Unit, cell: Vector2i) -> bool:
	var here := battle.board.find(u)
	if here == cell: return false                  # dropped back where it was
	if not battle.can_move(u):
		_clear_lift()
		_say(_why_cannot_move(u))
		_refresh_soon()
		return true
	if not battle.move_unit(u, cell.x, cell.y):
		return false
	_clear_lift()
	_sfx("select")
	var moved_to := battle.board.find(u)
	if moved_to.x >= 0:
		var tint: Color = Color(0.72, 1.0, 0.78) if battle.in_position(u) \
			else Color(1.0, 0.78, 0.45)
		_fx.announce("LANE %d" % (moved_to.y + 1), tint,
			_side_centre(ally_box))
	_refresh_soon()
	return true

# Why a walk was refused. "Nothing happened" is the same experience as a bug.
func _why_cannot_move(u: Unit) -> String:
	if battle.finished: return "The fight is over."
	if not battle.is_player_turn(): return "It is not your side's move."
	if u.ap <= 0:
		return "%s has already acted this round — moving costs an action." \
			% u.display_name.split(" ")[0]
	return "%s cannot move right now." % u.display_name.split(" ")[0]

# Holding a unit card turns it over, exactly as holding any other card does.
func _tick_unit_holds(delta: float) -> void:
	for uid in _u_press.keys():
		var st: Dictionary = _u_press[uid]
		st["elapsed"] = float(st["elapsed"]) + delta
		if bool(st["lifted"]) or bool(st["moved"]): continue
		if float(st["elapsed"]) < CardView.HOLD_SECONDS: continue
		var u := _unit_by_id(str(uid))
		if u == null:
			st["moved"] = true
			continue
		# Holding one of THEIRS turns their board over instead of lifting a
		# card off it — there is no card to lift, and what you wanted was to
		# read them.
		if u.is_enemy:
			st["moved"] = true
			_open_dossier(u, true)
			continue
		var basic := _basic_for(u)
		if basic == null:
			st["moved"] = true          # nothing to show; do not keep trying
			continue
		st["lifted"] = true
		_on_press_started(basic, Vector2(st["at"]))
		_on_hold_begun(basic)

func _unit_by_id(uid: String) -> Unit:
	for u in battle.field_units():
		if u.id == uid: return u
	return null

func _on_unit_pressed(u: Unit) -> void:
	if selected_card == null or battle.finished:
		return
	_commit(selected_card, selected_actor, u, 0.0)

# `flick` is 0..1 on the same scale as rhythm accuracy. The better of the two is
# what pays — there is ONE reward for playing a card well.
func _commit(card: Card, actor: Unit, target: Unit, flick := 0.0) -> void:
	if not battle.is_valid_target(card, actor, target):
		# Let the card GO. Keeping it selected here meant a card that could not
		# legally hit anything stayed armed for every later click, and each one
		# came back to this same line.
		_say("%s cannot be aimed at that." % card.display_name)
		selected_card = null
		selected_actor = null
		_refresh_soon()
		return
	var was_ult := card.type == Card.Type.ULTIMATE
	var hp_before := _snapshot_hp()
	var target_rect := _panel_rect(target)
	# Captured BEFORE the play, like the rects above it: the refresh that follows
	# frees the view this card is flying out of.
	var from_rect := _stage_origin(card, actor)
	# THE CARD GOES DOWN EITHER WAY. Clearing this only on success is what froze
	# the screen: a play the rules refused left the card armed, the prompt stuck
	# on "choose a target", and every click on a target refused and re-armed it.
	# Nothing may survive a commit still latched to the cursor.
	selected_card = null
	selected_actor = null
	if battle.play(card, actor, target):
		_award_execution(actor, flick)
		# Presentation only, and deliberately AFTER the rules have resolved --
		# nothing waits for the tween, and a card that was refused never flies.
		_stage_play(card, from_rect)
		_spawn_vfx(card, target, target_rect)
		if was_ult: _sfx("ultimate")
		elif card.heal > 0.0: _sfx("heal")
		elif card.power > 0.0: _sfx("hit")
		else: _sfx("card")
		if was_ult: _fx.kick(0.9)
	else:
		# Refused. Say WHY — "nothing happened" is the same experience as a bug,
		# and the player has no other way to find out.
		_say(_why_refused(card, actor))
		_sfx("select")
	# Deferred: _commit is reached from a card's own press handler and from a
	# unit board's, and refresh() frees both of those.
	_refresh_soon()
	# The rects these numbers sit over were captured BEFORE the action, so this
	# does not care whether the rebuild has happened yet.
	_report_hp(hp_before)

# Why can_play() said no. Read in the same order the rule checks things, so the
# sentence the player sees is the reason the engine actually had.
func _why_refused(card: Card, actor: Unit) -> String:
	if battle.finished: return "The fight is over."
	if card.type == Card.Type.SUMMONER:
		var need: int = maxi(1, card.ap_cost)
		if battle.cp < need:
			return "%s needs %d CP and the Summoner has %d. CP refills next round." % [
				card.display_name, need, battle.cp]
		return "%s cannot be played right now." % card.display_name
	if actor == null or not actor.is_alive():
		return "%s has nobody able to play it." % card.display_name
	var up: Unit = battle.current_unit()
	if battle.enforce_turn_order and up != null and actor != up:
		return "It is %s's turn, not %s's." % [up.display_name.split(" ")[0],
			actor.display_name.split(" ")[0]]
	if card.type == Card.Type.ULTIMATE:
		if not actor.ultimate_ready():
			return "%s's gauge is not full yet." % actor.display_name.split(" ")[0]
		if battle.sp < Battle.ULT_SP_COST:
			return "An ultimate costs %d SP and you have %d." % [
				Battle.ULT_SP_COST, battle.sp]
	if actor.ap < card.ap_cost:
		return "%s needs %d AP and %s has %d." % [card.display_name, card.ap_cost,
			actor.display_name.split(" ")[0], actor.ap]
	if actor.ep < card.ep_cost:
		return "%s needs %d EP and %s has %d." % [card.display_name, card.ep_cost,
			actor.display_name.split(" ")[0], actor.ep]
	return "%s cannot be played right now." % card.display_name

# Rhythm and flick feed the SAME payout. Whichever the player earned more of is
# the one that counts, so neither gesture is required and neither stacks.
func _award_execution(actor: Unit, flick: float) -> void:
	var b = _beat()
	if actor == null: return
	var beat_acc: float = b.accuracy() if (b != null and b.enabled) else 0.0
	var acc: float = maxf(beat_acc, flick)
	if acc <= 0.0: return
	var bonus: int = b.bonus_ep(acc) if b != null else 0
	var how: String = Flick.label(flick) if flick >= beat_acc else b.label()
	if bonus > 0:
		actor.ep += bonus
		_beat_hit = 1.0
		_beat_hit_text = "%s  +%d EP" % [how, bonus]
		_on_log("  ♪ %s — +%d EP" % [how, bonus])
	else:
		_beat_hit = 0.7
		_beat_hit_text = how if how != "" else "close"

# The ability fires ON THE UNIT BEING HIT, not in the middle of a column. The
# rect is captured before the action for the same reason the HP numbers are:
# refresh() rebuilds every panel and a fresh Control has no size until the
# layout solver has run.
func _spawn_vfx(card: Card, target: Unit, rect: Rect2) -> void:
	if card.power <= 0.0 and card.heal <= 0.0 and card.status == "": return
	var at := rect.get_center() if rect.size.x > 1.0 else _side_centre(enemy_box)
	var wide: bool = card.target in [Card.Target.ALL_ENEMIES, Card.Target.ALL_ALLIES]
	var big: bool = card.type == Card.Type.ULTIMATE or wide
	# A card that hits everyone fires on everyone, so the effect matches the
	# rule rather than only illustrating one victim of it.
	if wide:
		var side: Array = battle.enemies if card.target == Card.Target.ALL_ENEMIES else battle.allies
		for u in side:
			if not u.is_alive() and u.hp <= 0: continue
			Vfx.burst(self, _panel_rect(u).get_center(), card.element, false)
	else:
		Vfx.burst(self, at, card.element, big)
	if card.power > 0.0 and card.element in [ElementChart.E.WIND, ElementChart.E.FIRE]:
		Vfx.slash(self, at)
	# A status is a thing that happened TO someone; naming it over them is the
	# cheapest way to make "why did my damage drop" answerable.
	if card.status != "" and target != null:
		_fx.announce(card.status.to_upper(), HitFeedback.COL_CRIT,
			rect.position + Vector2(6, 4))

# Right-click puts down whatever is armed. Every selection mode needs a way out
# that is not "find something legal to click".
func _unhandled_input(event: InputEvent) -> void:
	if not (event is InputEventMouseButton): return
	var mb := event as InputEventMouseButton
	if not mb.pressed or mb.button_index != MOUSE_BUTTON_RIGHT: return
	if selected_card == null and _lift_card == null: return
	_clear_lift()
	selected_card = null
	selected_actor = null
	refresh()

func _on_end_round_pressed() -> void:
	if battle.finished: return
	selected_card = null; selected_actor = null
	_clear_lift()
	var hp_before := _snapshot_hp()
	# One TURN, not the whole round. Enemies whose turn comes up next take it
	# inside end_turn(), so control returns when it is the player's move again.
	battle.end_turn()
	if battle.finished and not battle.victory: _sfx("down")
	# Everything their side did is already resolved; this is where the screen
	# starts TELLING you about it.
	_begin_enemy_replay()
	refresh()
	_report_hp(hp_before)

# Harness seam, same reason ModeStub.select_def() exists: synthetic input cannot
# reach the game window here, so the only way to SEE hit feedback in a
# screenshot is to ask the scene to take a turn. Plays the first attack it can
# against the first living enemy. Not reachable from any button.
func demo_action(arg: String = "") -> bool:
	if battle.finished: return false
	var living: Array = battle.enemies.filter(func(u): return u.is_alive())
	if living.is_empty(): return false
	for card in battle.hand.duplicate():
		if card.power <= 0.0: continue
		var actor := _actor_for(card)
		if actor == null or not battle.can_play(card, actor): continue
		_commit(card, actor, living[0], 1.0 if arg == "flick" else 0.0)
		return true
	return false

# Harness seam: lift a card and, optionally, hold it. Lets a screenshot show the
# drag and the flipped back, neither of which synthetic input can reach.
func demo_lift(arg: String = "") -> bool:
	if battle.hand.is_empty(): return false
	# "attack" picks one that actually swings, so a capture of the back can show
	# the damage, status and element lines rather than a support card's two.
	var card: Card = battle.hand[0]
	if arg.begins_with("attack"):
		for c in battle.hand:
			if c.power > 0.0 and c.status != "": card = c; break
			if c.power > 0.0 and card.power <= 0.0: card = c
	var origin := global_position + get_viewport_rect().size * Vector2(0.42, 0.62)
	_on_press_started(card, origin)
	_lift_synthetic = true
	if arg.ends_with("hold"):
		_on_hold_begun(card)
		# Jump the animation to its end so a capture on the next frame is not
		# of a card halfway through turning over.
		_flip_t = -1.0
		if _lift != null:
			_lift.set_turn(-1.0)
			_lift.set_flipped(true)
			_lift.set_view_scale(_lift_scale * HOLD_SCALE)
			_place_lift(origin)
	else:
		_on_press_moved(card, origin)
	return true

# Harness seam: run a WHOLE gesture — press, drag across the board, release over
# an enemy — and report what survived it. The check that matters is that the
# pressed CardView is still alive after the press: it was being freed by a
# refresh mid-gesture, which cut off every later motion and release event and
# left the lifted copy stranded on the hand.
func demo_drag(arg: String = "") -> String:
	if battle.hand.is_empty(): return "no hand"
	var living: Array = battle.enemies.filter(func(u): return u.is_alive())
	if living.is_empty(): return "no enemies"
	var card: Card = null
	var view: CardView = null
	for stack in hand_box.get_children():
		for ch in stack.get_children():
			var cv := ch as CardView
			if cv == null: continue
			if cv.card.target != Card.Target.SINGLE_ENEMY: continue
			if cv.card.power <= 0.0: continue
			if not battle.can_play(cv.card, _actor_for(cv.card)): continue
			card = cv.card
			view = cv
			break
		if view != null: break
	if view == null: return "no playable single-target attack in hand"

	var hand_before: int = battle.hand.size()
	var start: Vector2 = view.global_position + view.custom_minimum_size * 0.5
	var target: Unit = living[0]
	var panel = _panels.get(target.id, null)
	var dest: Vector2 = (panel as Control).get_global_rect().get_center()

	_on_press_started(card, start)
	var survived := is_instance_valid(view) and view.is_inside_tree()
	var lifted := _lift != null
	_on_press_moved(card, start.lerp(dest, 0.5))
	_on_press_moved(card, dest)
	var hovering: bool = _hover_target == target
	var hp_before: int = target.hp
	# Released fast and straight at the target — a flick.
	_on_press_ended(card, dest, (dest - start).normalized() * 2400.0)

	var out := "source card survived the press: %s | lifted: %s | hover found target: %s"
	out += " | card left the hand: %s | target lost HP: %s | lift cleared: %s"
	return out % [survived, lifted, hovering, battle.hand.size() < hand_before,
		target.hp < hp_before, _lift == null]

# Harness seam: drag a PARTY CARD onto an enemy, through the real input handler
# with real events, and report each stage. This is the path that replaced the
# basics row, so it needs to be exercised rather than assumed.
func demo_unit(arg: String = "") -> String:
	var foes: Array = battle.enemies.filter(func(x): return x.is_alive())
	if foes.is_empty(): return "no enemies"
	var actor: Unit = null
	for a in battle.allies:
		var bc := _basic_for(a)
		if a.is_alive() and bc != null and battle.can_play(bc, a):
			actor = a
			break
	if actor == null: return "no ally can swing right now"
	var panel = _panels.get(actor.id, null)
	var foe: Unit = foes[0]
	var target_panel = _panels.get(foe.id, null)
	if panel == null or target_panel == null: return "panels not laid out yet"
	var from: Vector2 = (panel as Control).get_global_rect().get_center()
	var to: Vector2 = (target_panel as Control).get_global_rect().get_center()
	var hp0: int = foe.hp
	var ap0: int = actor.ap

	var down := InputEventMouseButton.new()
	down.button_index = MOUSE_BUTTON_LEFT
	down.pressed = true
	down.global_position = from
	_on_unit_input(down, actor)
	var move := InputEventMouseMotion.new()
	move.global_position = from.lerp(to, 0.5)
	_on_unit_input(move, actor)
	var lifted: bool = _lift_card != null
	var move2 := InputEventMouseMotion.new()
	move2.global_position = to
	_on_unit_input(move2, actor)
	var hovering: bool = _hover_target == foe
	var up := InputEventMouseButton.new()
	up.button_index = MOUSE_BUTTON_LEFT
	up.pressed = false
	up.global_position = to
	_on_unit_input(up, actor)

	return "basic lifted off the party card: %s | hover found enemy: %s | enemy lost HP: %s | AP spent: %s | lift cleared: %s" % [
		lifted, hovering, foe.hp < hp0, actor.ap < ap0, _lift == null]

# Harness seam: drop an ATTACK card on your own party — the illegal drop that
# used to latch the card to the cursor with no way to shake it off.
func demo_bad_drop(arg: String = "") -> String:
	var mate: Unit = null
	for a in battle.allies:
		if a.is_alive(): mate = a; break
	if mate == null: return "no ally"
	var card: Card = null
	var view: CardView = null
	for stack in hand_box.get_children():
		for ch in stack.get_children():
			var cv := ch as CardView
			if cv != null and cv.card.target == Card.Target.SINGLE_ENEMY:
				card = cv.card; view = cv; break
		if view != null: break
	if view == null: return "no single-target attack in hand"
	var start: Vector2 = view.global_position + view.custom_minimum_size * 0.5
	var dest: Vector2 = (_panels[mate.id] as Control).get_global_rect().get_center()
	var hand0: int = battle.hand.size()
	_on_press_started(card, start)
	_on_press_moved(card, dest)
	_on_press_ended(card, dest, (dest - start).normalized() * 900.0)
	# Then click that same ally, which is what used to jam.
	_on_unit_pressed(mate)
	return "card stayed in hand: %s | nothing latched to the cursor: %s | lift cleared: %s | ally unhurt: %s" % [
		battle.hand.size() == hand0, selected_card == null, _lift == null, mate.is_alive()]

# Harness seam: bench the last of the party and open the Reserve panel, so a
# screenshot can show it. A four-strong party has no bench of its own, and
# recruiting a fifth is several screens away from here.
# Harness seam: the numbers the layout actually solved to. Every check on this
# screen used to be somebody measuring a screenshot with their eyes, which is how
# a card was "about 140 wide" for three passes while the solver thought it was
# 170. It prints; shot.gd echoes whatever a demo_ method returns.
func demo_metrics(arg: String = "") -> String:
	var vp := get_viewport_rect().size
	var sc := _solve_scales()
	var cw: float = CardView.W * sc.x
	var grid_w: float = _grid_w(cw, battle.board.lanes)
	var sr := _stage_rect()
	return ("vp=%dx%d board=%.3f hand=%.3f card=%.0fx%.0f rails=%.0f/%.0f "
		+ "field=%.0f grid=%.0f (%.0f%% of field) gap=%.0f clearance=%.0f "
		+ "handspan=%.0f stage=%.0f,%.0f %.0fx%.0f stagecx=%.0f screencx=%.0f "
		+ "stagecard=%.3f rebuilds=%d overlay=%d "
		+ "handsum=%.0f..%.0f handparty=%.0f..%.0f handclear=%.0f overlap=%.3f"
		) % [vp.x, vp.y, sc.x, sc.y, cw,
		CardView.H * sc.x, _left_rail_w(), _right_rail_w(), _field_w(), grid_w,
		100.0 * grid_w / maxf(1.0, _field_w()), _slot_gap(cw), _hand_clearance(),
		_hand_span(), sr.position.x, sr.position.y, sr.size.x, sr.size.y,
		sr.get_center().x, vp.x * 0.5, _stage_card_scale(), _hand_rebuilds,
		overlay.get_child_count() if overlay != null else -1,
		_run_span(0).x, _run_span(0).y, _run_span(1).x, _run_span(1).y,
		_run_clearance(), _overlap_cached]

# Where a run starts and ends, in hand-row coordinates. Zero-width when that run
# is empty, which is a real answer: the Summoner often holds nothing.
func _run_span(run: int) -> Vector2:
	var lo := INF
	var hi := -INF
	for e in _hand_slots:
		if int(e.get("run", 1)) != run: continue
		lo = minf(lo, float(e["x"]))
		hi = maxf(hi, float(e["x"]) + float(e["w"]))
	if lo == INF: return Vector2.ZERO
	return Vector2(lo, hi)

# THE NUMBER THAT SAYS THE TWO HANDS ARE NOT TOUCHING. It must never be
# negative, in any hand the game can deal -- that is the whole regression guard
# for the collision this split is a second attempt at.
func _run_clearance() -> float:
	var a := _run_span(0)
	var b := _run_span(1)
	if a == Vector2.ZERO or b == Vector2.ZERO: return INF
	return b.x - a.y

# Harness seam: the two things the rails moved that a screenshot cannot check.
#
# A Summoner that moved into the rail is still a DROP TARGET at its new rect,
# and a body dragged off the bench still lands through swap_in. Both are pure UI
# paths -- _panels -> _unit_under, and _bench_drag -> _ally_cell_under ->
# _do_swap -- so neither is reachable from the rules suites, and synthetic input
# cannot reach a standalone window.
#
# It AWAITS A FRAME after seeding, because a container's children have no size
# until the layout solver has run: dropping in the same frame as the rebuild
# hit-tests a row of cells that are all still sitting at the origin, and every
# drop resolves to the first of them. That is the same first-frame trap the
# board and the hand each had, met a third time.
func demo_verify(arg: String = "") -> String:
	_verify_run()
	return "running; findings print as VERIFY lines"

func _verify_run() -> void:
	var out: Array = []
	if battle.enemy_summoner == null:
		battle.enemy_summoner = Content.enemy_summoner_unit(0, 1, false)
		refresh()
	_seed_bench()
	await get_tree().process_frame
	await get_tree().process_frame

	# 1. their Summoner, wherever the rail put it, is what a drop finds
	var es: Unit = battle.enemy_summoner
	var es_panel = _panels.get(es.id, null)
	if es_panel == null or not is_instance_valid(es_panel):
		out.append("FAIL enemy Summoner has no panel")
	else:
		var mid: Vector2 = (es_panel as Control).get_global_rect().get_center()
		var found: Unit = _unit_under(mid)
		out.append(("OK rail target=%s" % found.display_name) if found == es
			else "FAIL target=%s" % ("null" if found == null else found.display_name))
		var swing: Card = Content.basic_attack_for(battle.allies[0])
		out.append("OK reachable" if battle.is_valid_target(swing, battle.allies[0], es)
			else "FAIL unreachable")

	# 2. the bench drag, at each of its four endings
	if battle.reserves.is_empty():
		out.append("SKIP no bench")
		print("VERIFY  ", " | ".join(out))
		return
	var r: Unit = battle.reserves[0]
	var taken := Vector2i(-1, -1)
	var hole := Vector2i(-1, -1)
	for key in _cells:
		var parts: PackedStringArray = str(key).split(",")
		var cell := Vector2i(int(parts[0]), int(parts[1]))
		if battle.board.at(cell.x, cell.y) != null: taken = cell
		else: hole = cell

	_bench_drag = r
	_drop_bench(Vector2(-500.0, -500.0))
	out.append("OK void-drop refused" if battle.reserves.has(r)
		else "FAIL void-drop swapped")

	if hole.x >= 0:
		_bench_drag = r
		_drop_bench((_cells["%d,%d" % [hole.x, hole.y]] as Control)
			.get_global_rect().get_center())
		out.append("OK hole-drop refused" if battle.reserves.has(r)
			else "FAIL hole-drop swapped")

	if taken.x >= 0:
		var standing: Unit = battle.board.at(taken.x, taken.y)
		_bench_drag = r
		_drop_bench((_cells["%d,%d" % [taken.x, taken.y]] as Control)
			.get_global_rect().get_center())
		var swapped: bool = battle.board.has(r) and not battle.board.has(standing)
		out.append(("OK swap %s <- %s" % [standing.display_name.split(" ")[0],
			r.display_name.split(" ")[0]]) if swapped else "FAIL swap did not happen")

	out.append("OK nothing held" if _bench_drag == null and _bench_ghost == null
		else "FAIL drag left live")

	# 3. THE CELLS ARE WHERE THE ARITHMETIC SAYS, on both sides. A grid that
	# drifts off its arithmetic is a silent failure -- everything still works,
	# the board just stops facing itself -- so it is checked rather than looked
	# at. Theirs is MIRRORED, so the mirror of the arithmetic is what it must
	# match.
	var off := 0.0
	var ab: Board = battle.board
	var eb: Board = battle.enemy_board
	for c_r in ab.rows:
		for c_l in ab.lanes:
			var slot = _cells.get("%d,%d" % [c_r, c_l], null)
			if slot == null or not is_instance_valid(slot): continue
			off = maxf(off, absf((slot as Control).position.x - _slot_x(c_l, false)))
	out.append("OK ally cells placed" if off <= 1.0
		else "FAIL ally cell off by %.1f" % off)
	var eoff := 0.0
	if _line_enemy != null and is_instance_valid(_line_enemy):
		for e_r in eb.rows:
			for e_l in eb.lanes:
				var idx := e_r * eb.lanes + e_l
				if idx >= _line_enemy.get_child_count(): continue
				var e_slot := _line_enemy.get_child(idx) as Control
				if e_slot == null: continue
				eoff = maxf(eoff, absf(e_slot.position.x - _slot_x(e_l, true)))
				eoff = maxf(eoff, absf(e_slot.position.y
					- float(e_r) * (CardView.H * _board_scale() + RANK_GAP)))
	out.append("OK enemy cells placed" if eoff <= 1.0
		else "FAIL enemy cell off by %.1f" % eoff)
	# THE TWO GRIDS FLANK THE STAGE: theirs to the right of yours, with the
	# column of air the stage needs standing clear between them.
	if _line_enemy != null and is_instance_valid(_line_enemy) \
			and _line_ally != null and is_instance_valid(_line_ally):
		var gap2: float = _line_enemy.global_position.x \
			- (_line_ally.global_position.x + _line_ally.size.x)
		out.append("OK grids flank the stage (%.0f)" % gap2 if gap2 >= 40.0
			else "FAIL stage collapsed to %.0f" % gap2)

	# 4. NOTHING STANDS ON THE STAGE. The gap is arithmetic, so an off-by-one in
	# _slot_x would put a card in the middle of it and nothing would complain.
	var sr := _stage_rect()
	if sr.size.x > 4.0:
		var hits := 0
		for key in _cells:
			var c = _cells[key]
			if c != null and is_instance_valid(c) \
					and sr.intersects((c as Control).get_global_rect()): hits += 1
		for uid in _panels:
			var pn = _panels[uid]
			if pn != null and is_instance_valid(pn) \
					and sr.intersects((pn as Control).get_global_rect()): hits += 1
		out.append("OK stage clear" if hits == 0 else "FAIL %d things on the stage" % hits)

	# 5. THE TWO HANDS DO NOT MEET, dealt to both ceilings. This is the whole
	# reason the split is measured rather than positioned: the last attempt at
	# it collided, and a screenshot of a four-card hand would not have shown it.
	battle.draw_for(Battle.SUMMONER_KEY, Battle.SUMMONER_HAND_MAX)
	for a2 in battle.allies:
		battle.draw_for(a2.id, Battle.ALLY_HAND_MAX)
	refresh()
	await get_tree().process_frame
	await get_tree().process_frame
	var clear := _run_clearance()
	out.append("OK hands clear by %.0f (%d+%d cards)"
		% [clear, _run_cards(0), _run_cards(1)] if clear > 0.0
		else "FAIL hands overlap by %.0f" % -clear)
	print("VERIFY  ", " | ".join(out))

# How many cards are actually in a run, for the report -- "clear by 160" means
# nothing without knowing how full the hand was when it was measured.
func _run_cards(run: int) -> int:
	var n := 0
	for e in _hand_slots:
		if int(e.get("run", 1)) != run: continue
		var stack = e["node"]
		if stack != null and is_instance_valid(stack): n += stack.get_child_count()
	return n

# Harness seam: park a card ON the stage with the tween killed, because the
# flight is nearly a second long and the screenshot harness captures 0.4s after
# it calls -- which lands mid-flight and says nothing about either end of it.
# `clip` prefers a card whose art actually moves.
func demo_stage(arg: String = "") -> String:
	if battle.hand.is_empty(): return "no hand"
	var card: Card = battle.hand[0]
	if arg.begins_with("clip"):
		for c in battle.hand:
			var probe := CardView.new()
			probe.summoner_id = _summoner_id()
			probe.is_overlay = true
			probe.set_view_scale(0.6)
			probe.setup(c, "")
			var moving: bool = probe.has_clip()
			probe.queue_free()
			if moving:
				card = c
				break
	_stage_play(card, _stage_origin(card, _actor_for(card)))
	if _staged == null: return "stage refused (rect %s)" % _stage_rect()
	if _staged_tween != null and _staged_tween.is_valid(): _staged_tween.kill()
	_staged_tween = null
	var sr := _stage_rect()
	var big := _stage_card_scale()
	_staged.set_view_scale(big)
	_staged.position = sr.position - overlay.global_position + Vector2(
		(sr.size.x - CardView.W * big) * 0.5, (sr.size.y - CardView.H * big) * 0.5)
	return "staged %s clip=%s scale=%.2f rect=%.0f,%.0f %.0fx%.0f" % [
		card.display_name, _staged.has_clip(), big,
		sr.position.x, sr.position.y, sr.size.x, sr.size.y]

# Harness seam: spend a few cards and open the pile, so a screenshot can show
# the one panel that only exists after something has been played.
func demo_discard(arg: String = "") -> String:
	for _i in 4:
		if battle.hand.is_empty(): break
		var c: Card = battle.hand[0]
		battle.hand.erase(c)
		battle.discard.append(c)
	refresh()
	_open_discard()
	return "spent: %d" % battle.used_count()

# Harness seam: bench somebody WITHOUT opening the panel, so a screenshot can
# show the bench standing in its rail rather than the modal that covers it.
func demo_bench(arg: String = "") -> String:
	_seed_bench()
	return "reserves: %d" % battle.reserves.size()

func _seed_bench() -> void:
	if battle.reserves.is_empty():
		if battle.allies.size() < 2: return
		var last: Unit = battle.allies[battle.allies.size() - 1]
		battle.allies.erase(last)
		battle.reserves.append(last)
		battle.order.erase(last)
		# AND OFF THE BOARD. Benching somebody who is still standing in a lane
		# is not a state the game can reach -- it made the seam show the same
		# character twice, on the line and in the rail, and any swap aimed at
		# them was refused because swap_in wants somebody who is in `allies`.
		battle.board.remove(last)
		# A reserve holds no cards, so the seam has to take theirs back too or
		# the screenshot shows a hand nobody on the field can play.
		for c in battle.hand.duplicate():
			if Battle.owner_key(c) == last.id: battle.hand.erase(c)
		refresh()

func demo_reserve(arg: String = "") -> String:
	_seed_bench()
	_open_reserve()
	return "reserves: %d | panel open: %s" % [battle.reserves.size(), _reserve_ui != null]

# Harness seam: give the other side a Summoner, so a screenshot can show an
# elite. The standalone fallback encounter is an ordinary room and never has one.
func demo_commander(arg: String = "") -> String:
	if battle.enemy_summoner == null:
		battle.enemy_summoner = Content.enemy_summoner_unit(0, 1, arg == "boss")
		refresh()
	# "hurt" knocks both heads down so a capture can show the pennant gauges
	# part-empty. It writes straight to the units, so it is a screenshot aid and
	# nothing a rule should ever be inferred from.
	if arg.find("hurt") >= 0:
		battle.enemy_summoner.hp = int(battle.enemy_summoner.max_hp * 0.35)
		if battle.summoner != null:
			battle.summoner.hp = int(battle.summoner.max_hp * 0.7)
		refresh()
	return "their head: %s  %d HP | commanding: %s" % [
		battle.enemy_summoner.display_name, battle.enemy_summoner.hp,
		battle.commander_active()]

# Harness seam: turn one of THEIR boards over, since a hold cannot be
# synthesised here.
func demo_dossier(arg: String = "") -> String:
	var foe: Unit = null
	for e in battle.enemies:
		if e.is_alive(): foe = e; break
	if foe == null: return "no living enemy"
	_open_dossier(foe, false)
	return "dossier: %s | open: %s" % [foe.display_name, _dossier != null]

# Harness seam: switch auto mode on, since a toggle cannot be clicked here.
func demo_auto(arg: String = "") -> String:
	auto_btn.button_pressed = arg != "off"
	_on_auto_toggled(arg != "off")
	return "auto=%s" % auto_on

# Harness seam: end the fight now, either way, so a screenshot can show the
# verdict and the reward screen. Nothing reaches this from a button.
func demo_finish(arg: String = "") -> String:
	if arg == "lose":
		for u in battle.allies + battle.reserves: u.hp = 0
		if battle.summoner != null: battle.summoner.hp = 0
	else:
		for e in battle.enemies: e.hp = 0
		if battle.enemy_summoner != null: battle.enemy_summoner.hp = 0
	battle._check_end()
	refresh()
	return "finished=%s victory=%s resolved=%s" % [
		battle.finished, battle.victory, _resolved]

# Harness seam: open the pause menu, since Escape cannot be synthesised here.
func demo_pause(arg: String = "") -> String:
	if not has_node("/root/Pause"): return "no Pause autoload"
	var pm = get_node("/root/Pause")
	pm.open()
	return "menu visible: %s | tree paused: %s" % [pm.visible, get_tree().paused]

func _exit_tree() -> void:
	# A tween outlives the node it writes to unless it is told not to.
	_clear_stage(false)
	# Never leave the canvas offset behind for the next scene.
	var vp := get_viewport()
	if vp != null:
		var xf := vp.canvas_transform
		xf.origin = Vector2.ZERO
		vp.canvas_transform = xf

# LEAVING IS IN THE PAUSE MENU. It was a button in the top bar, one slip of the
# thumb from End Turn, and it walks you out of a fight. The pause menu is where
# every other way out of a screen already lives, and it costs a deliberate press
# to reach - which is the right price for this one.
func leave_battle() -> void:
	_on_back_pressed()

func leave_label() -> String:
	# Said AFTER the outcome is banked, so it describes where the button goes
	# rather than what is at stake - the screen above it has already settled
	# that, and "your haul is at risk" is simply not true any more by the time
	# anyone reads it.
	if not battle.finished: return "Leave the battle"
	var sess = _sess()
	var still_in: bool = sess != null and sess.dungeon != null and sess.dungeon.in_delve()
	return "Back to the dungeon" if still_in else "Back to town"

func _on_back_pressed() -> void:
	# The outcome was banked the moment the fight ended - see
	# _resolve_if_finished - so this only decides where you come out. A defeat
	# has already ended the delve by now, which is why it leads to town.
	_resolve_if_finished()
	var sess = _sess()
	if sess != null and sess.dungeon != null and sess.dungeon.in_delve():
		get_tree().change_scene_to_file("res://scenes/Dungeon.tscn")
	else:
		get_tree().change_scene_to_file("res://scenes/Main.tscn")
