# Settings. The GDD requires the slice to be completable with minigames off,
# rhythm off, and both off — so every optional layer has a real switch here,
# and turning one off contributes exactly 0 rather than a penalty.
extends Control

@onready var body: VBoxContainer = $Root/Body
@onready var info: RichTextLabel = $Root/Info

func _sess():
	var r := get_tree().root
	return r.get_node("Session") if r.has_node("Session") else null

func _ready() -> void:
	refresh()

func _settings() -> Dictionary:
	var s = _sess()
	if s == null: return {}
	if not s.data.has("settings"): s.data["settings"] = {}
	return s.data["settings"]

func _put(key: String, v) -> void:
	var s = _sess()
	if s == null: return
	var st: Dictionary = _settings()
	st[key] = v
	s.data["settings"] = st
	s.save()
	_apply()
	refresh()

func _apply() -> void:
	var st := _settings()
	if has_node("/root/Beat"):
		var b = get_node("/root/Beat")
		b.enabled = bool(st.get("rhythm", false))
		b.bpm = float(st.get("bpm", 100.0))

func refresh() -> void:
	for c in body.get_children(): c.queue_free()
	var st := _settings()

	_toggle("Minigames", "minigames", bool(st.get("minigames", true)),
		"Short skill games on farm/town actions. Off = base outcome band only.")
	_toggle("Rhythm layer", "rhythm", bool(st.get("rhythm", false)),
		"Continuous beat. On-beat actions refund EP. Off contributes exactly 0.")

	var row := HBoxContainer.new()
	var l := Label.new()
	l.text = "Tempo"
	l.custom_minimum_size = Vector2(180, 0)
	row.add_child(l)
	for bpm in [80, 100, 120, 140]:
		var b := Button.new()
		b.text = str(bpm)
		b.custom_minimum_size = Vector2(60, 30)
		if int(st.get("bpm", 100)) == bpm:
			b.modulate = Color(0.7, 1.0, 0.75)
		b.pressed.connect(func(): _put("bpm", float(bpm)))
		row.add_child(b)
	body.add_child(row)

	var sfxrow := HBoxContainer.new()
	var sl := Label.new()
	sl.text = "Sound"
	sl.custom_minimum_size = Vector2(180, 0)
	sfxrow.add_child(sl)
	for v in [0, 50, 100]:
		var b2 := Button.new()
		b2.text = "%d%%" % v
		b2.custom_minimum_size = Vector2(60, 30)
		if int(st.get("volume", 100)) == v:
			b2.modulate = Color(0.7, 1.0, 0.75)
		b2.pressed.connect(func():
			_put("volume", v)
			AudioServer.set_bus_volume_db(0,
				-80.0 if v == 0 else linear_to_db(float(v) / 100.0)))
		sfxrow.add_child(b2)
	body.add_child(sfxrow)

	info.clear()
	info.append_text("[b]Optional layers[/b]\n")
	info.append_text("Both are bonuses, never requirements. With everything off the game is\n")
	info.append_text("still completable — the base outcome band alone is sufficient.\n\n")
	var mg: bool = bool(st.get("minigames", true))
	var rh: bool = bool(st.get("rhythm", false))
	info.append_text("Minigames: %s    Rhythm: %s\n" % [
		("on" if mg else "off"), ("on" if rh else "off")])
	if not mg and not rh:
		info.append_text("[color=#9fe6a0]Both off — pure card game. Fully playable.[/color]\n")

func _toggle(label: String, key: String, value: bool, note: String) -> void:
	var row := HBoxContainer.new()
	var l := Label.new()
	l.text = label
	l.custom_minimum_size = Vector2(180, 0)
	row.add_child(l)
	var b := Button.new()
	b.text = "ON" if value else "OFF"
	b.custom_minimum_size = Vector2(80, 30)
	b.modulate = Color(0.7, 1.0, 0.75) if value else Color(0.8, 0.8, 0.85)
	b.pressed.connect(func(): _put(key, not value))
	row.add_child(b)
	var n := Label.new()
	n.text = note
	n.add_theme_font_size_override("font_size", 11)
	n.add_theme_color_override("font_color", Color(0.65, 0.7, 0.8))
	row.add_child(n)
	body.add_child(row)

func _on_back_pressed() -> void:
	get_tree().change_scene_to_file("res://scenes/Main.tscn")
