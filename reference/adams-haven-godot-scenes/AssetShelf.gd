# THE ASSET SHELF — add your own pictures, clips and sounds, from inside the game.
#
# Three steps, in this order, because each one narrows the next:
#
#   1. a CATEGORY   what kind of thing this is       (battle, monster, ...)
#   2. a SLOT       what it is FOR                   (a monster, a card, a room)
#   3. a FILE       the thing itself
#
# Picking the slot before the file is what makes this different from a folder.
# The game never asks you where to put something: it already knows, because the
# slot IS the filename. Choose "monster" then "Cobalt Burrower" and whatever you
# pick lands where the game looks for that creature's card.
#
# Nothing is overwritten. Files land in user://assets/, the bundled art stays
# exactly where it is, and Remove puts the original back by deleting yours —
# see scenes/Assets.gd for the resolution rule.
extends Control

const ROW_H := 44

var _kind := ""
var _slot := ""
var _dialog: FileDialog = null

@onready var kinds_box: VBoxContainer = $Root/Body/Kinds/List
@onready var slots_box: VBoxContainer = $Root/Body/Slots/ScrollBox/List
@onready var detail: RichTextLabel = $Root/Body/Detail/Info
@onready var actions: VBoxContainer = $Root/Body/Detail/Actions
@onready var preview: Control = $Root/Body/Detail/Preview
@onready var title_label: Label = $Root/TopBar/Title
@onready var used_label: Label = $Root/TopBar/Used

func _sess():
	var r := get_tree().root
	return r.get_node("Session") if r.has_node("Session") else null

func _ready() -> void:
	_build_kinds()
	_refresh()

func _build_kinds() -> void:
	for c in kinds_box.get_children(): c.queue_free()
	for kind in AssetKinds.kind_ids():
		var b := Button.new()
		b.text = AssetKinds.label_of(kind)
		b.custom_minimum_size = Vector2(0, ROW_H)
		b.toggle_mode = true
		b.button_pressed = (kind == _kind)
		var k: String = kind
		b.pressed.connect(func():
			_kind = k
			_slot = ""
			_refresh())
		kinds_box.add_child(b)

# ------------------------------------------------------------------- slots
#
# What a slot MEANS depends on the category, so the list comes from the game
# rather than from the folder: you are choosing a monster, not typing a filename.
func _slots_for(kind: String) -> Array:
	match AssetKinds.slots_source(kind):
		"monster":
			return Content.monster_ids()
		"unit":
			var out: Array = []
			for u in Content.roster(): out.append(u.id)
			for u2 in Content.recruitable(): out.append(u2.id)
			for sid in Content.summoner_ids(): out.append(sid)
			return _unique(out)
		"room_category":
			var cats: Array = []
			for k in RoomCatalog.DEFAULT:
				for cname in RoomCatalog.DEFAULT[k]: cats.append(str(cname))
			return _unique(cats)
		"map_piece":
			return ["floor", "fog"]
		"screen":
			return ["town", "farm", "house"]
		"icon":
			var icons: Array = []
			for k2 in Dungeon.KIND_NAMES: icons.append(str(k2).to_lower())
			return _unique(icons)
		"card":
			var cards: Array = []
			for cd in Content.starting_deck(): cards.append(cd.id)
			return _unique(cards)
		_:
			# A free-form category still lists whatever is already filed there.
			return Assets.installed(kind)

static func _unique(a: Array) -> Array:
	var out: Array = []
	for x in a:
		if not (str(x) in out): out.append(str(x))
	out.sort()
	return out

func _refresh() -> void:
	used_label.text = "Your files: %.1f MB" % (float(Assets.user_bytes()) / 1048576.0)
	for c in slots_box.get_children(): c.queue_free()
	for c in actions.get_children(): c.queue_free()
	for c in preview.get_children(): c.queue_free()
	detail.clear()

	if _kind == "":
		title_label.text = "ASSET SHELF"
		detail.append_text("[b]Add your own art, clips and sounds.[/b]\n\n")
		detail.append_text("Pick a category on the left, then the thing it is for, "
			+ "then the file. The game already knows where it goes.\n\n")
		detail.append_text("[i]Your files live beside the game's, never on top of "
			+ "them. Removing one puts the original back.[/i]\n")
		return

	title_label.text = "ASSET SHELF — %s" % AssetKinds.label_of(_kind).to_upper()
	var slots := _slots_for(_kind)
	for slot in slots:
		var b := Button.new()
		var mine: bool = Assets.is_overridden(_kind, str(slot))
		var any: bool = Assets.has(_kind, str(slot))
		b.text = "%s%s" % [str(slot), "   ★ yours" if mine else ("" if any else "   (empty)")]
		b.custom_minimum_size = Vector2(0, ROW_H)
		b.toggle_mode = true
		b.button_pressed = (str(slot) == _slot)
		if mine:
			b.add_theme_color_override("font_color", Color(0.62, 0.95, 0.68))
		elif not any:
			b.add_theme_color_override("font_color", Color(0.60, 0.62, 0.70))
		var sl := str(slot)
		b.pressed.connect(func():
			_slot = sl
			_refresh())
		slots_box.add_child(b)

	detail.append_text("[i]%s[/i]\n\n" % AssetKinds.hint_of(_kind))
	detail.append_text("Takes: %s\n" % ", ".join(AssetKinds.accepts(_kind)))
	if _slot == "":
		detail.append_text("\nPick what this is for.\n")
		return

	var path := Assets.find(_kind, _slot)
	var mine_now := Assets.is_overridden(_kind, _slot)
	detail.append_text("\n[b]%s[/b]\n" % _slot)
	if path == "":
		detail.append_text("[color=#8a8a92]Nothing here yet.[/color]\n")
	else:
		detail.append_text("%s\n[color=#8a8a92]%s[/color]\n" % [
			"[color=#9fe6a0]Your file[/color]" if mine_now else "Shipped with the game",
			path])
	_build_preview(path)

	_action("Choose a file…", _pick_file)
	if mine_now:
		var rm := _action("Remove mine (put the original back)", func():
			Assets.remove(_kind, _slot)
			Assets.clear_cache()
			RoomArt.reload()
			_refresh())
		rm.add_theme_color_override("font_color", Color(1.0, 0.72, 0.72))

func _action(txt: String, cb: Callable) -> Button:
	var b := Button.new()
	b.text = txt
	b.custom_minimum_size = Vector2(0, 42)
	b.pressed.connect(cb)
	actions.add_child(b)
	return b

func _build_preview(path: String) -> void:
	if path == "": return
	if path.get_extension().to_lower() in AssetKinds.AUDIO_EXT:
		var play := Button.new()
		play.text = "▶  Play it"
		play.custom_minimum_size = Vector2(0, 38)
		play.pressed.connect(func():
			var stream := Assets.sound(_kind, _slot)
			if stream == null: return
			var p := AudioStreamPlayer.new()
			p.stream = stream
			add_child(p)
			p.finished.connect(func(): p.queue_free())
			p.play())
		preview.add_child(play)
		return
	var tex := Assets.texture_at(path)
	if tex == null: return
	var tr := TextureRect.new()
	tr.texture = tex
	tr.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	tr.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	tr.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	tr.mouse_filter = Control.MOUSE_FILTER_IGNORE
	preview.add_child(tr)

# ------------------------------------------------------------------ picking

func _pick_file() -> void:
	if _dialog == null:
		_dialog = FileDialog.new()
		_dialog.file_mode = FileDialog.FILE_MODE_OPEN_FILE
		# ACCESS_FILESYSTEM, not ACCESS_RESOURCES: the point is to bring in a
		# file from OUTSIDE the game.
		_dialog.access = FileDialog.ACCESS_FILESYSTEM
		_dialog.use_native_dialog = true
		_dialog.file_selected.connect(_on_file_chosen)
		add_child(_dialog)
	var globs := PackedStringArray()
	var exts: Array = AssetKinds.accepts(_kind)
	var pattern := ""
	for e in exts:
		pattern += ("*." + str(e)) if pattern == "" else (",*." + str(e))
	globs.append("%s ; %s" % [pattern, AssetKinds.label_of(_kind)])
	_dialog.filters = globs
	_dialog.popup_centered_ratio(0.8)

func _on_file_chosen(path: String) -> void:
	var res: Dictionary = Assets.install(_kind, _slot, path)
	if bool(res["ok"]):
		# Everything that caches a texture has to be told, or the old picture
		# stays on screen and the import looks like it did nothing.
		Assets.clear_cache()
		RoomArt.reload()
		_say("[color=#9fe6a0]Added.[/color] %s" % str(res["path"]))
	else:
		_say("[color=#ff9c9c]Not added — %s[/color]" % str(res["reason"]))
	_refresh()

var _message := ""

func _say(t: String) -> void:
	_message = t

func _on_back_pressed() -> void:
	get_tree().change_scene_to_file("res://scenes/Main.tscn")

# Harness seam: install a file without a dialog, so the import path can be
# exercised in a test rather than only by hand.
func demo_install(arg: String = "") -> String:
	var parts := arg.split("|")
	if parts.size() < 3: return "usage: kind|slot|path"
	var res: Dictionary = Assets.install(parts[0], parts[1], parts[2])
	Assets.clear_cache()
	_kind = parts[0]
	_slot = parts[1]
	_refresh()
	return "ok=%s path=%s reason=%s" % [res["ok"], res["path"], res["reason"]]
