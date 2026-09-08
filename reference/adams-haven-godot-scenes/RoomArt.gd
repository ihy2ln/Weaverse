# The picture behind a dungeon room — still or moving — resolved from the art
# folder and the category manifest beside it.
#
#   art/dungeon/categories.json     which kinds draw on which categories
#   art/dungeon/rooms/<category>/   the art itself: .webp/.png/.jpg, and .ogv
#   art/dungeon/mat/                the battlemat prints
#
# A CATEGORY IS A FOLDER. Adding one is making a folder and naming it in the
# manifest; removing one is deleting the name; re-pointing one is editing a
# string. None of the three touches code, and the rules that decide whether an
# edit makes sense live in core/room_catalog.gd where they are tested.
#
# A player can override the manifest without editing game files at all by
# writing user://dungeon_categories.json — that wins when it is present.
#
# Two rules make the pick a pure file drop:
#
#   1. Folders are POOLED. A kind may name several, so dropping in another pack
#      widens the variety with no code change.
#   2. The variant is picked from the dungeon seed and the room's CELL, not
#      stored. A room looks like itself on every visit and after every reload,
#      two rooms of a kind rarely match, and nothing goes in the save to keep
#      that true.
#
# Presentation only — core/ has no idea this exists.
class_name RoomArt

const ROOMS_DIR := "res://art/battle/"
const MANIFEST := ROOMS_DIR + "categories.json"
const USER_MANIFEST := "user://dungeon_categories.json"
# A category folder the player added lives beside the shipped ones, under the
# same name — see core/asset_kinds.gd.
const USER_ROOMS_DIR := "user://assets/battle/"

const VIDEO_EXT := ["ogv"]
const IMAGE_EXT := ["webp", "png", "jpg", "jpeg"]

static var _kinds := {}          # kind -> Array of category names
static var _pool := {}           # kind -> Array of {path, video}
static var _loaded := false

# ------------------------------------------------------------------ loading

static func reload() -> void:
	_loaded = false
	_kinds = {}
	_pool = {}
	_load()

static func _read_manifest() -> Dictionary:
	for path in [USER_MANIFEST, MANIFEST]:
		if not FileAccess.file_exists(path): continue
		var f := FileAccess.open(path, FileAccess.READ)
		if f == null: continue
		var txt := f.get_as_text()
		f.close()
		var parsed = JSON.parse_string(txt)
		if typeof(parsed) != TYPE_DICTIONARY:
			push_warning("RoomArt: %s is not valid JSON — using the built-in categories." % path)
			continue
		# Every complaint is reported and then survived: a bad entry costs one
		# kind its artwork, never the whole board.
		for why in RoomCatalog.problems_with(parsed):
			push_warning("RoomArt: %s — %s" % [path, why])
		return parsed
	return {}

static func _load() -> void:
	if _loaded: return
	_loaded = true
	_kinds = RoomCatalog.from_dict(_read_manifest())
	for kind in _kinds:
		var found: Array = []
		var cats: Array = _kinds[kind]
		# A kind with no categories at all still looks in a folder named after
		# itself, so dropping art into rooms/<kind>/ works with no manifest.
		if cats.is_empty() and not _kinds.has(kind):
			cats = [RoomCatalog.kind_key(int(kind))]
		for cat in cats:
			# Both trees, the player's first. A category is a folder name, so
			# adding art to one is dropping a file into it.
			for dir in [USER_ROOMS_DIR + str(cat), ROOMS_DIR + str(cat)]:
				var d := DirAccess.open(dir)
				if d == null: continue
				for raw in d.get_files():
					var f := _strip(raw)
					var ext := f.get_extension().to_lower()
					var is_video: bool = ext in VIDEO_EXT
					if not is_video and not (ext in IMAGE_EXT): continue
					var p: String = dir + "/" + f
					if not ResourceLoader.exists(p) and not FileAccess.file_exists(p):
						continue
					var already := false
					for e in found:
						if e["path"] == p: already = true
					if not already:
						found.append({"path": p, "video": is_video})
		# Stable order, so a seed always resolves to the same picture.
		found.sort_custom(func(a, b): return str(a["path"]) < str(b["path"]))
		_pool[kind] = found

# The editor lists `x.webp` and `x.webp.import`; an export lists `x.webp.remap`.
# Normalise all three to the resource path the game actually loads.
static func _strip(name: String) -> String:
	var f := name
	for suffix in [".import", ".remap"]:
		if f.ends_with(suffix):
			f = f.substr(0, f.length() - suffix.length())
	return f

# ------------------------------------------------------------------ picking

# Which file this room gets, as {"path": String, "video": bool}, or {} when the
# kind has no art. Deterministic in the dungeon seed and the room's cell.
static func pick(kind: int, seed_value: int, cell: Vector2i) -> Dictionary:
	_load()
	var pool: Array = _pool.get(kind, [])
	if pool.is_empty(): return {}
	var i: int = absi(Dungeon._mix(seed_value * 31 + cell.x * 7919 + cell.y * 104729)) \
		% pool.size()
	return pool[i]

# The still for a room, or null when its pick is a video (or there is nothing).
static func for_room(kind: int, seed_value: int, cell: Vector2i) -> Texture2D:
	var e := pick(kind, seed_value, cell)
	if e.is_empty() or bool(e["video"]): return null
	return load(str(e["path"]))

# The clip for a room, or "" when its pick is a still.
static func clip_for(kind: int, seed_value: int, cell: Vector2i) -> String:
	var e := pick(kind, seed_value, cell)
	if e.is_empty() or not bool(e["video"]): return ""
	return str(e["path"])

# How many variants a kind can draw on. Used by the art report in the tests, so
# a pack that failed to import is noticed rather than silently falling back.
static func variants(kind: int) -> int:
	_load()
	return int((_pool.get(kind, []) as Array).size())

static func categories_for(kind: int) -> Array:
	_load()
	return (_kinds.get(kind, []) as Array).duplicate()

# --------------------------------------------------------------- mounting

# Fill `host` with this room's picture, whichever kind it turns out to be, and
# return true if anything was mounted. Callers keep a plain Control in the scene
# and never have to care whether a given room is a still or a clip.
static func apply(host: Control, kind: int, seed_value: int, cell: Vector2i) -> bool:
	if host == null: return false
	for ch in host.get_children():
		ch.queue_free()
	var e := pick(kind, seed_value, cell)
	if e.is_empty(): return false
	var node: Control
	if bool(e["video"]):
		var vp := VideoStreamPlayer.new()
		var vpath := str(e["path"])
		if vpath.begins_with("res://"):
			vp.stream = load(vpath)
		else:
			var vs := VideoStreamTheora.new()
			vs.file = vpath
			vp.stream = vs
		vp.expand = true
		if "loop" in vp: vp.set("loop", true)
		else: vp.finished.connect(func(): vp.play())
		vp.play()
		node = vp
	else:
		var tr := TextureRect.new()
		tr.texture = Assets.texture_at(str(e["path"]))
		tr.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		tr.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_COVERED
		node = tr
	node.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	node.mouse_filter = Control.MOUSE_FILTER_IGNORE
	node.texture_filter = CanvasItem.TEXTURE_FILTER_LINEAR
	host.add_child(node)
	return true

# ------------------------------------------------------------------ the mat

static func mat(fog: bool) -> Texture2D:
	return Assets.texture("map", "fog" if fog else "floor")
