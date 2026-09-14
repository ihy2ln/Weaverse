# HOME — the Fixer-Upper. The ONLY structure that moves between areas.
#
# The interior runs on the same placement grid as the Farm and the Town, but on
# a LOCAL screen rather than a saved one: the House already tracks its own
# contents, and duplicating them into a second store would be two owners for
# one fact. So `screen_id` stays empty and `local_seed` builds the room.
#
# Relocation is now genuinely a coordinate change — Session.house_relocate lifts
# the House item out of one screen and sets it down in the other. Nothing
# scene-bound or NPC-bound lives inside, which is exactly what makes that legal.
extends ModeStub

const TIERS := ["Shack", "Repaired Homestead", "Cottage", "Expanded House", "Manor"]
const UPGRADE_COST := [80, 160, 320, 640]
const CAPACITY := [2, 4, 6, 9, 12]      # furniture the interior holds, by tier

const CATALOG := {
	"cot":    {"name": "Straw Cot",    "cost": 30,  "buff": "",          "mag": 0.0},
	"hearth": {"name": "Stone Hearth", "cost": 90,  "buff": "Regen",     "mag": 4.0},
	"rack":   {"name": "Weapon Rack",  "cost": 120, "buff": "AttackUp",  "mag": 0.08},
	"banner": {"name": "Vale Banner",  "cost": 120, "buff": "DefenseUp", "mag": 0.08},
	"desk":   {"name": "Writing Desk", "cost": 70,  "buff": "",          "mag": 0.0},
	"rug":    {"name": "Woven Rug",    "cost": 40,  "buff": "",          "mag": 0.0},
}

var placing := ""

func _init() -> void:
	screen_id = ""
	area_name = "HOME — the Fixer-Upper"
	ground_tile = Tiles.STONE

func local_seed(screen: Placement.Screen) -> void:
	screen.w = 12
	screen.h = 7
	screen.place("door", WorldDefs.kind_of("door"), Vector2i(0, 3),
		WorldDefs.size_of("door"), WorldDefs.tags_of("door"))
	screen.place("workbench", WorldDefs.kind_of("workbench"), Vector2i(9, 0),
		WorldDefs.size_of("workbench"), WorldDefs.tags_of("workbench"))

func _house() -> Dictionary:
	var n = _sess()
	if n == null:
		return {"tier": 0, "area": "town", "furniture": []}
	if not n.data.has("house"):
		n.data["house"] = {"tier": 0, "area": "town", "furniture": []}
	return n.data["house"]

func _tier() -> int:
	return int(_house().get("tier", 0))

func _furniture() -> Array:
	return _house().get("furniture", [])

# --------------------------------------------------------------- interaction

# Furniture is not a placement item, so its two gestures — drop one, pick one
# back up — are handled before the grid sees the tap.
func _on_view_gui_input(event: InputEvent) -> void:
	if event is InputEventMouseButton and event.pressed \
			and event.button_index == MOUSE_BUTTON_LEFT:
		var cell := _cell_at(event.position)
		if placing != "":
			_place_at(cell)
			return
		for f in _furniture():
			if int(f.x) == cell.x and int(f.y) == cell.y:
				_remove_at(cell)
				return
	if event is InputEventMouseButton and event.pressed \
			and event.button_index == MOUSE_BUTTON_RIGHT and placing != "":
		placing = ""
		_say("Cancelled.")
		canvas.queue_redraw()
		return
	super._on_view_gui_input(event)

func _occupied(cell: Vector2i) -> bool:
	for f in _furniture():
		if int(f.x) == cell.x and int(f.y) == cell.y: return true
	return false

func _place_at(cell: Vector2i) -> void:
	if not is_walkable(cell) or _occupied(cell):
		_sfx("denied")
		_say("Cannot place there.")
		placing = ""
		canvas.queue_redraw()
		return
	var cap: int = CAPACITY[clampi(_tier(), 0, CAPACITY.size() - 1)]
	if _furniture().size() >= cap:
		_sfx("denied")
		_say("The %s holds only %d pieces. Upgrade first." % [TIERS[_tier()], cap])
		placing = ""
		return
	var n = _sess()
	var cost: int = int(CATALOG[placing]["cost"])
	if n != null and not n.spend("gold", cost):
		_sfx("denied")
		_say("Needs %d gold." % cost)
		placing = ""
		return
	var house := _house()
	house["furniture"] = _furniture() + [{"id": placing, "x": cell.x, "y": cell.y}]
	if n != null: n.save()
	_sfx("card")
	_say("Placed %s." % CATALOG[placing]["name"])
	placing = ""
	canvas.queue_redraw()
	_refresh_info()

func _remove_at(cell: Vector2i) -> void:
	var keep: Array = []
	var removed := ""
	for f in _furniture():
		if int(f.x) == cell.x and int(f.y) == cell.y and removed == "":
			removed = str(f.id)
			continue
		keep.append(f)
	if removed == "": return
	_house()["furniture"] = keep
	var n = _sess()
	if n != null:
		n.grant("gold", int(float(CATALOG[removed]["cost"]) * 0.5))
		n.save()
	_sfx("back")
	_say("Removed %s — half refunded." % CATALOG[removed]["name"])
	canvas.queue_redraw()
	_refresh_info()

# --------------------------------------------------------------- house verbs

func _upgrade_house() -> void:
	var t := _tier()
	if t >= UPGRADE_COST.size():
		_say("Already a Manor.")
		return
	var n = _sess()
	var cost: int = UPGRADE_COST[t]
	if n != null and n.spend("gold", cost):
		_house()["tier"] = t + 1
		n.save()
		_sfx("victory")
		_say("Rebuilt: %s  (-%dg). Capacity now %d." % [TIERS[t + 1], cost, CAPACITY[t + 1]])
	else:
		_sfx("denied")
		_say("Needs %d gold." % cost)
	_refresh_info()

# The House is the only structure that can LEAVE its area. Placement makes that
# a screen id and a coordinate; its contents never move at all, because they
# were never anchored to a scene in the first place.
func _relocate() -> void:
	var n = _sess()
	if n == null: return
	var here := str(_house().get("area", "town"))
	var dest := "farm" if here == "town" else "town"
	if not n.spend("gold", 50):
		_sfx("denied")
		_say("Relocation costs 50 gold.")
		return
	var res: Dictionary = n.house_relocate(dest)
	if not res.ok:
		n.grant("gold", 50)
		_sfx("denied")
		_say(str(res.reason))
		return
	_sfx("open")
	_say("The House now stands in the %s. All %d pieces travelled with it."
		% [dest, _furniture().size()])
	_refresh_info()

func _extra_interact(it) -> void:
	if it.def_id != "workbench": return
	_button("Upgrade the house", _upgrade_house)
	var here := str(_house().get("area", "town"))
	_button("Relocate to the %s (50g)" % ("farm" if here == "town" else "town"), _relocate)
	info.append_text("\n[b]Furniture[/b] — tap a placed piece to remove it.\n")
	for id in CATALOG.keys():
		var e: Dictionary = CATALOG[id]
		var label := "%s  (%dg)" % [e["name"], int(e["cost"])]
		if str(e["buff"]) != "":
			label += "  -> %s" % str(e["buff"])
		_button(label, func():
			placing = id
			_say("Tap a free tile to place the %s." % e["name"])
			canvas.queue_redraw())

func _mode_summary() -> void:
	var t := _tier()
	info.append_text("\nTier: [b]%s[/b]  (%d/%d)\n" % [TIERS[clampi(t, 0, 4)], t, TIERS.size() - 1])
	info.append_text("Standing in: [b]%s[/b]\n" % str(_house().get("area", "town")))
	info.append_text("Furniture: %d / %d\n" % [_furniture().size(), CAPACITY[clampi(t, 0, 4)]])
	info.append_text("\nTap the Workbench to upgrade, relocate, or buy furniture.\n")
	info.append_text("[i]Buffs are deliberately deferred — they are meant to emerge from\n")
	info.append_text("furniture/food/staff combinations, so only the plumbing exists.[/i]\n")
	var buffs := buff_set()
	if not buffs.is_empty():
		info.append_text("\nPlumbing output at run entry: %s\n" % str(buffs))

# The house emits a modifier set at dungeon entry. The RULES are deferred; this
# is only the interface, and it deliberately does not assume flat per-item adds.
func buff_set() -> Dictionary:
	var out := {}
	for f in _furniture():
		var e: Dictionary = CATALOG.get(str(f.id), {})
		var b := str(e.get("buff", ""))
		if b == "": continue
		out[b] = float(out.get(b, 0.0)) + float(e.get("mag", 0.0))
	return out

func _on_view_draw() -> void:
	super._on_view_draw()
	for f in _furniture():
		var e: Dictionary = CATALOG.get(str(f.id), {})
		var r := _cell_rect(Vector2i(int(f.x), int(f.y))).grow(-tile_px * 0.14)
		canvas.draw_rect(r, Color(0.55, 0.42, 0.28))
		canvas.draw_rect(r, Color(0.85, 0.72, 0.5), false, 1.5)
		canvas.draw_string(ThemeDB.fallback_font, r.position + Vector2(3, tile_px * 0.34),
			str(e.get("name", "?")).substr(0, 2),
			HORIZONTAL_ALIGNMENT_LEFT, tile_px, int(tile_px * 0.28), Color(0.12, 0.08, 0.04))
	if placing != "":
		canvas.draw_string(ThemeDB.fallback_font, Vector2(6, 16),
			"PLACING: %s — tap a free tile, right-click to cancel"
				% str(CATALOG[placing]["name"]),
			HORIZONTAL_ALIGNMENT_LEFT, 620, 15, Color(1.0, 0.9, 0.5))
