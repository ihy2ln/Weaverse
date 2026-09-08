# THE PAUSE MENU — Escape, from anywhere, in any scene.
#
# An autoload rather than a node in each scene, for two reasons. It has to exist
# during a battle, on the dungeon board, in town and on the farm without four
# copies drifting apart; and it has to keep running while the rest of the game
# is frozen, which a node inside the paused tree cannot do.
#
# THE TREE IS ACTUALLY PAUSED. get_tree().paused stops every other node's
# _process and _physics_process, so pausing mid-battle really does stop the beat
# clock, the shake, the card animations and the enemy turn — rather than hiding
# a game that is still running underneath. This layer is PROCESS_MODE_ALWAYS,
# which is what exempts it from the freeze it causes.
#
# Settings are written straight into the save's `settings` branch and applied on
# the spot, so what you hear is what gets stored. Volume is the Master bus, in
# decibels, converted from the 0..1 the slider shows — audio is logarithmic and
# a linear slider on a linear scale spends nine tenths of its travel inaudible.
extends CanvasLayer

const DIM := Color(0.03, 0.04, 0.06, 0.82)
const PANEL_W := 460.0

# The on-screen pause button. Escape does not exist on a phone, so without this
# the menu is unreachable on the platform the APK is for.
#
# It lives HERE rather than in each scene's top bar for the same reason the menu
# does: one copy, every scene, nothing to keep in sync — including scenes that
# have no top bar at all.
#
# EDGE_INSET is deliberately large. A phone case overhangs the screen edge, and
# a control flush against it cannot reliably be pressed; the scenes pull their
# own right margin in by the same amount so Leave sits beside this rather than
# under it.
const BUTTON_W := 64.0
const BUTTON_H := 38.0
const EDGE_INSET := 20.0
const TOP_INSET := 10.0

var _root: Control = null
var _body: VBoxContainer = null
var _showing_settings := false

# FILL THE SCREEN THE PLAYER ACTUALLY HAS. The project's 1600x900 is the layout's
# coordinate space, not a window size -- canvas_items draws 2D at the window's own
# resolution and scales the coordinates, so a 1440p window is a SHARPER picture of
# the same layout rather than a blurry one. Left alone, though, the game opens as
# a 1600x900 box in the corner of that monitor.
#
# Not from project.godot, because the screenshot harness drives the window size
# itself and a maximized boot would take that away from it. The user args are the
# tell: tests/shot.gd and the gesture suite always pass a scene after `--`, and
# the game never does.
func _size_window_for_screen() -> void:
	if not OS.get_cmdline_user_args().is_empty(): return
	if DisplayServer.window_get_mode() != DisplayServer.WINDOW_MODE_WINDOWED: return
	var screen: Vector2i = DisplayServer.screen_get_usable_rect(
		DisplayServer.window_get_current_screen()).size
	# Only ever grow into a bigger screen, and never past it.
	if screen.x <= 1600 or screen.y <= 900: return
	DisplayServer.window_set_mode(DisplayServer.WINDOW_MODE_MAXIMIZED)

func _ready() -> void:
	# Runs while everything it pauses does not.
	process_mode = Node.PROCESS_MODE_ALWAYS
	_size_window_for_screen()
	layer = 128
	visible = false
	_build()
	_build_button()
	_apply_stored_volume()

func _sess():
	var r := get_tree().root
	return r.get_node("Session") if r.has_node("Session") else null

func _beat():
	return get_node("/root/Beat") if has_node("/root/Beat") else null

# The button sits on its own layer BELOW the menu, so opening the menu covers it
# rather than leaving a live control floating over the dim.
var _btn_layer: CanvasLayer = null

func _build_button() -> void:
	_btn_layer = CanvasLayer.new()
	_btn_layer.layer = 127
	add_child(_btn_layer)
	var holder := Control.new()
	holder.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	holder.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_btn_layer.add_child(holder)
	var b := Button.new()
	b.text = "❚❚"
	b.tooltip_text = "Pause  (Esc)"
	b.custom_minimum_size = Vector2(BUTTON_W, BUTTON_H)
	b.add_theme_font_size_override("font_size", 16)
	b.set_anchors_preset(Control.PRESET_TOP_RIGHT)
	b.offset_left = -(EDGE_INSET + BUTTON_W)
	b.offset_right = -EDGE_INSET
	b.offset_top = TOP_INSET
	b.offset_bottom = TOP_INSET + BUTTON_H
	b.pressed.connect(open)
	holder.add_child(b)

# --------------------------------------------------------------------- input

func _unhandled_input(event: InputEvent) -> void:
	if not event.is_action_pressed("ui_cancel"): return
	# Never swallow the key: a scene that wants Escape for its own cancel still
	# sees it when the menu is closed.
	toggle()
	get_viewport().set_input_as_handled()

func toggle() -> void:
	if visible: close()
	else: open()

func open() -> void:
	if visible: return
	_showing_settings = false
	_refresh()
	visible = true
	if _btn_layer != null: _btn_layer.visible = false
	get_tree().paused = true

func close() -> void:
	if not visible: return
	visible = false
	if _btn_layer != null: _btn_layer.visible = true
	get_tree().paused = false

# ------------------------------------------------------------------ building

func _build() -> void:
	_root = Control.new()
	_root.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	_root.mouse_filter = Control.MOUSE_FILTER_STOP     # swallow clicks underneath
	add_child(_root)

	var dim := ColorRect.new()
	dim.color = DIM
	dim.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	dim.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_root.add_child(dim)

	var centre := CenterContainer.new()
	centre.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	centre.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_root.add_child(centre)

	var panel := PanelContainer.new()
	panel.custom_minimum_size = Vector2(PANEL_W, 0)
	centre.add_child(panel)

	var pad := MarginContainer.new()
	for side in ["left", "right", "top", "bottom"]:
		pad.add_theme_constant_override("margin_" + side, 22)
	panel.add_child(pad)

	_body = VBoxContainer.new()
	_body.add_theme_constant_override("separation", 8)
	pad.add_child(_body)

func _refresh() -> void:
	for ch in _body.get_children(): ch.queue_free()
	if _showing_settings: _build_settings()
	else: _build_main()

func _title(text: String) -> void:
	var l := Label.new()
	l.text = text
	l.add_theme_font_size_override("font_size", 26)
	l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_body.add_child(l)
	var rule := HSeparator.new()
	_body.add_child(rule)

func _line(text: String, colour := Color(0.62, 0.66, 0.76)) -> void:
	var l := Label.new()
	l.text = text
	l.add_theme_font_size_override("font_size", 12)
	l.add_theme_color_override("font_color", colour)
	l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_body.add_child(l)

func _button(text: String, cb: Callable) -> Button:
	var b := Button.new()
	b.text = text
	b.custom_minimum_size = Vector2(0, 40)
	b.add_theme_font_size_override("font_size", 16)
	b.pressed.connect(cb)
	_body.add_child(b)
	return b

func _build_main() -> void:
	_title("Paused")
	var s = _sess()
	if s != null:
		_line("Day %d    %d gold    %d runs completed" % [
			int(s.data.profile.get("day", 1)),
			int(s.data.inventory.get("gold", 0)),
			int(s.data.profile.get("runs_completed", 0))])
	_button("Resume", close)
	# LEAVING A BATTLE LIVES HERE. It used to be a button in the battle's own top
	# bar, a thumb's width from End Turn, where one slip walked you out of a
	# fight. Every other way out of a screen is already in this menu, and getting
	# here takes a deliberate press - which is the right price for that one.
	var leaver = _leavable_scene()
	if leaver != null:
		_button(str(leaver.leave_label()), func():
			close()
			leaver.leave_battle())
	_button("Settings", func():
		_showing_settings = true
		_refresh())
	_button("Save now", _save_now)
	_button("Asset shelf — add your own art", func():
		close()
		get_tree().change_scene_to_file("res://scenes/AssetShelf.tscn"))
	# Leaving mid-delve does NOT bank the haul — say so rather than let someone
	# find out by losing one.
	var in_delve: bool = s != null and s.dungeon != null and s.dungeon.in_delve()
	_button("Return to town", _to_town)
	if in_delve:
		_line("You are still in the dungeon. Walking out from here keeps the floor's "
			+ "progress, but this trip's haul is only banked by leaving from a camp "
			+ "or the entrance.", Color(1.0, 0.78, 0.45))
	_button("Quit to desktop", _quit)

func _build_settings() -> void:
	_title("Settings")
	var s = _sess()
	var settings: Dictionary = s.data.get("settings", {}) if s != null else {}

	_slider("Volume", float(settings.get("volume", 1.0)), func(v: float):
		_set_volume(v)
		_store("volume", v))

	_check("Rhythm layer", bool(settings.get("rhythm", false)), func(on: bool):
		var b = _beat()
		if b != null: b.enabled = on
		_store("rhythm", on))
	_line("An optional timing bonus. The game is fully playable with it off.")

	_check("Minigames", bool(settings.get("minigames", true)), func(on: bool):
		_store("minigames", on))
	_line("Turns farming and recruitment rolls into a precision-tap. Off means "
		+ "the dice are rolled straight.")

	_button("Back", func():
		_showing_settings = false
		_refresh())

func _slider(label: String, value: float, cb: Callable) -> void:
	var row := HBoxContainer.new()
	var l := Label.new()
	l.text = label
	l.custom_minimum_size = Vector2(120, 0)
	row.add_child(l)
	var sl := HSlider.new()
	sl.min_value = 0.0
	sl.max_value = 1.0
	sl.step = 0.05
	sl.value = value
	sl.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	sl.value_changed.connect(cb)
	row.add_child(sl)
	_body.add_child(row)

func _check(label: String, on: bool, cb: Callable) -> void:
	var cb_node := CheckButton.new()
	cb_node.text = label
	cb_node.button_pressed = on
	cb_node.toggled.connect(cb)
	_body.add_child(cb_node)

# ------------------------------------------------------------------ actions

func _store(key: String, value) -> void:
	var s = _sess()
	if s == null: return
	var settings: Dictionary = s.data.get("settings", {})
	settings[key] = value
	s.data["settings"] = settings
	s.save()

# Audio is logarithmic. A 0..1 slider mapped straight onto decibels spends most
# of its travel doing nothing audible, so it goes through linear_to_db.
func _set_volume(v: float) -> void:
	var bus := AudioServer.get_bus_index("Master")
	if bus < 0: return
	AudioServer.set_bus_mute(bus, v <= 0.001)
	AudioServer.set_bus_volume_db(bus, linear_to_db(clampf(v, 0.001, 1.0)))

func _apply_stored_volume() -> void:
	var s = _sess()
	if s == null: return
	_set_volume(float(s.data.get("settings", {}).get("volume", 1.0)))

func _save_now() -> void:
	var s = _sess()
	if s == null: return
	s.save()
	_line("Saved.", Color(0.62, 0.95, 0.68))

# The scene on screen, IF it is one that knows how to walk out of itself. Asked
# by method rather than by name so this menu never has to hold a list of which
# scenes are battles.
func _leavable_scene():
	var cs = get_tree().current_scene
	if cs == null or not is_instance_valid(cs): return null
	if not (cs.has_method("leave_battle") and cs.has_method("leave_label")): return null
	return cs

func _to_town() -> void:
	close()
	get_tree().change_scene_to_file("res://scenes/Main.tscn")

func _quit() -> void:
	var s = _sess()
	if s != null: s.save()          # never lose a day to a menu button
	get_tree().paused = false
	get_tree().quit()
