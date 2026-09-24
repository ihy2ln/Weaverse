# FARM — the Clearing.
#
# Plots are Placement items like everything else, so a plot can be built, moved
# and removed exactly the way a barn can. What is GROWING in one lives in
# FarmSim, keyed by the placement uid: position has one owner, soil has another,
# and dragging a plot across the field never disturbs the crop in it.
#
# Every action still resolves through the Action Layer: an optional minigame
# produces a score, the optional rhythm layer contributes, and a VISIBLE dice
# roll turns the total into an outcome tier. Skipping either layer is valid.
extends ModeStub

var _minigame: Minigame = null
var _pending := {}

func _init() -> void:
	screen_id = "farm"
	area_name = "FARM — the Clearing"
	ground_tile = Tiles.GRASS
	# Level 1 of the Clearing, painted as one field rather than a repeating
	# tile. ground_sprite stays as the fallback if the art is ever missing.
	ground_sprite = "farm"
	backdrop_path = "res://art/landscape/farm.png"

func _farm() -> FarmSim:
	var s = _sess()
	return s.farm if s != null else null

func _plot_of(it) -> FarmSim.Plot:
	var f := _farm()
	if f == null or it == null: return null
	return f.by_uid(it.uid)

# ------------------------------------------------------------- the panel

func _mode_summary() -> void:
	var f := _farm()
	var s = _sess()
	info.append_text("\n[i]Crops grow by BATTLES FOUGHT, not real time.[/i]\n")
	if f != null:
		f.refresh_ready()
		var ready := 0
		var growing := 0
		for p in f.plots:
			if p.soil == FarmSim.Soil.READY: ready += 1
			elif p.soil == FarmSim.Soil.PLANTED: growing += 1
		info.append_text("Battles fought: %d    Growing: %d    Ready: %d\n"
			% [f.battles_fought, growing, ready])
	if s != null:
		var pan: Dictionary = s.pantry()
		if not pan.is_empty():
			var bits: Array = []
			for k in pan.keys(): bits.append("%s x%d" % [k, int(pan[k])])
			info.append_text("Pantry: " + ", ".join(bits) + "\n")
		var ab: Dictionary = s.active_buff()
		if not ab.is_empty():
			info.append_text("[color=#9fe6a0]Packed for the run: %s[/color]\n" % ab["dish"])

func _extra_interact(it) -> void:
	if it.kind == Placement.Kind.PLOT:
		_plot_panel(it)
		return
	if it.def_id == "kitchen":
		_kitchen_panel()

func _plot_panel(it) -> void:
	var f := _farm()
	var p := _plot_of(it)
	if f == null or p == null:
		info.append_text("\n[color=#ff9c9c]This plot has no soil record.[/color]\n")
		return
	f.refresh_ready()
	var uid: int = it.uid
	match p.soil:
		FarmSim.Soil.WILD:
			info.append_text("\nWild ground.\n")
			_button("Till the soil", func(): _work(uid))
		FarmSim.Soil.TILLED:
			info.append_text("\nTilled and waiting.\n")
			_button("Plant something", func(): _work(uid))
		FarmSim.Soil.PLANTED:
			info.append_text("\n[b]%s[/b] — %d battle(s) to go.%s\n" % [
				FarmSim.CROPS[p.crop]["name"], f.battles_remaining(p),
				"  [color=#6fb6ff](watered)[/color]" if p.watered else ""])
			if not p.watered:
				_button("Water it (one battle sooner)", func(): _work(uid))
		FarmSim.Soil.READY:
			info.append_text("\n[color=#ffd76a][b]%s[/b] is ready.[/color]\n"
				% FarmSim.CROPS[p.crop]["name"])
			_button("Harvest", func(): _work(uid))

func _kitchen_panel() -> void:
	var s = _sess()
	if s == null: return
	var pantry: Dictionary = s.pantry()
	if pantry.is_empty():
		info.append_text("\nNothing cooked yet. Harvest a crop first.\n")
		return
	info.append_text("\n[b]Pantry[/b] — pick a dish to carry into the next run:\n")
	for dish in pantry.keys():
		var d: Dictionary = FarmSim.DISHES.get(dish, {})
		_button("%s x%d  ->  %s +%d%% (%d rounds)" % [dish, int(pantry[dish]),
			d.get("status", "?"), int(float(d.get("magnitude", 0)) * 100),
			int(d.get("duration", 0))],
			func():
				if s.consume_dish(dish):
					_sfx("heal")
					_say("%s is packed for the next run." % dish)
				_after_change())

# ------------------------------------------------------------ plot actions

func _work(uid: int) -> void:
	var f := _farm()
	if f == null: return
	f.refresh_ready()
	var p := f.by_uid(uid)
	if p == null: return
	match p.soil:
		FarmSim.Soil.WILD:
			f.till_uid(uid)
			_sfx("card")
			_say("Tilled the plot.")
			_save()
			_after_change()
		FarmSim.Soil.TILLED:
			_begin_action("Planting", uid, "plant")
		FarmSim.Soil.PLANTED:
			if not p.watered:
				f.water_uid(uid)
				_sfx("heal")
				_say("Watered — one battle sooner.")
				_save()
				_after_change()
		FarmSim.Soil.READY:
			_begin_action("Harvest", uid, "harvest")

# The optional minigame. ESC skips it for a base-only roll.
func _begin_action(label: String, uid: int, kind: String) -> void:
	_pending = {"uid": uid, "kind": kind, "label": label}
	var s = _sess()
	if s != null and not bool(s.data.get("settings", {}).get("minigames", true)):
		_resolve_action(0.0)
		return
	if _minigame == null:
		var ps := load("res://scenes/Minigame.tscn")
		_minigame = ps.instantiate()
		add_child(_minigame)
		_minigame.finished.connect(_resolve_action)
	_minigame.begin(label, 1.0)

func _resolve_action(score01: float) -> void:
	var f := _farm()
	if f == null or _pending.is_empty(): return
	var uid: int = int(_pending["uid"])
	var kind: String = _pending["kind"]
	_pending = {}

	var mg := Dice.minigame_modifier(score01)
	var rhythm := 0
	if has_node("/root/Beat"):
		var b = get_node("/root/Beat")
		if b.enabled and b.accuracy() >= 0.75: rhythm = 2
	var roll := Dice.roll_d20(2, mg, rhythm)
	var tier := Dice.tier_for(roll.total)
	_say("[b]%s[/b]  %s  ->  %s" % [kind.capitalize(), roll.text, Dice.TIER_NAMES[tier]])

	if kind == "plant":
		var crops := FarmSim.CROPS.keys()
		var crop: String = crops[randi() % crops.size()]
		var quality: int = clampi(tier, 1, 5)
		f.plant_uid(uid, crop, quality)
		_sfx("card")
		_say("Planted %s at quality %d." % [FarmSim.CROPS[crop]["name"], quality])
	else:
		var got := f.harvest_uid(uid)
		if not got.is_empty():
			var bonus: int = maxi(0, tier - 2)
			var total: int = int(got["amount"]) + bonus
			_sfx("coin")
			_say("Harvested %d x %s -> dish: %s" % [total, got["name"], got["dish"]])
			var s = _sess()
			if s != null:
				s.add_dish(got["dish"], maxi(1, total / 2))
	_save()
	_after_change()

func _save() -> void:
	var s = _sess()
	if s != null: s.save()

# ------------------------------------------------------------------ draw

# Soil state is the whole reason to look at a plot, so it has to read at a
# glance against ANY ground — including a generated soil texture that is
# already brown. So a plot is not a tint on the ground: it is a BED, with its
# own rim and furrows, and the tint only says which of the four states it is in.
const SOIL_FILL := {
	FarmSim.Soil.WILD:    Color(0.20, 0.17, 0.13, 0.55),
	FarmSim.Soil.TILLED:  Color(0.46, 0.32, 0.19, 0.80),
	FarmSim.Soil.PLANTED: Color(0.22, 0.40, 0.20, 0.85),
	FarmSim.Soil.READY:   Color(0.88, 0.72, 0.24, 0.92),
}
const SOIL_RIM := {
	FarmSim.Soil.WILD:    Color(0.36, 0.31, 0.24),
	FarmSim.Soil.TILLED:  Color(0.66, 0.48, 0.29),
	FarmSim.Soil.PLANTED: Color(0.44, 0.72, 0.38),
	FarmSim.Soil.READY:   Color(1.00, 0.88, 0.42),
}

func draw_plot(c: CanvasItem, it, r: Rect2) -> void:
	var f := _farm()
	var p: FarmSim.Plot = f.by_uid(it.uid) if f != null else null
	var soil: int = p.soil if p != null else FarmSim.Soil.WILD
	var inner := r.grow(-maxf(2.0, tile_px * 0.06))

	# A dropped edge first, so the bed reads as cut INTO the ground rather than
	# painted on top of it.
	c.draw_rect(Rect2(inner.position + Vector2(0, tile_px * 0.04), inner.size),
		Color(0, 0, 0, 0.35))
	c.draw_rect(inner, SOIL_FILL[soil])

	# Furrows: what makes worked earth look worked, at any zoom.
	if soil != FarmSim.Soil.WILD:
		var rows := 3
		var step := inner.size.y / float(rows + 1)
		for i in rows:
			var y := inner.position.y + step * float(i + 1)
			c.draw_line(Vector2(inner.position.x + tile_px * 0.10, y),
				Vector2(inner.position.x + inner.size.x - tile_px * 0.10, y),
				Color(0, 0, 0, 0.22), maxf(1.0, tile_px * 0.03))
	c.draw_rect(inner, SOIL_RIM[soil], false, maxf(1.5, tile_px * 0.045))
	if p == null: return

	if p.soil == FarmSim.Soil.PLANTED:
		var d := tile_px * 0.34
		if not Tiles.draw_number(c, f.battles_remaining(p),
				inner.position + Vector2(tile_px * 0.10, tile_px * 0.12), d):
			c.draw_string(ThemeDB.fallback_font,
				inner.position + Vector2(4, tile_px * 0.45),
				str(f.battles_remaining(p)), HORIZONTAL_ALIGNMENT_LEFT,
				tile_px, int(tile_px * 0.3), Color(0.90, 0.97, 0.90))
		if p.watered:
			c.draw_circle(inner.position + Vector2(inner.size.x - tile_px * 0.2,
				tile_px * 0.2), tile_px * 0.08, Color(0.4, 0.7, 1.0))
	elif p.soil == FarmSim.Soil.READY:
		# A ready crop is the one thing on this screen the player must not miss.
		var pip := inner.position + Vector2(inner.size.x * 0.5, inner.size.y * 0.5)
		c.draw_circle(pip, tile_px * 0.19, Color(0.20, 0.15, 0.04, 0.85))
		if not Tiles.draw_cell(c, Tiles.ICON_HEART,
				Rect2(pip - Vector2(tile_px * 0.15, tile_px * 0.15),
					Vector2(tile_px * 0.30, tile_px * 0.30))):
			c.draw_string(ThemeDB.fallback_font, pip + Vector2(-tile_px * 0.08, tile_px * 0.11),
				"!", HORIZONTAL_ALIGNMENT_LEFT, tile_px, int(tile_px * 0.34),
				Color(1.0, 0.95, 0.7))
