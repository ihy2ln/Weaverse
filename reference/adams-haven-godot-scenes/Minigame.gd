# Precision-tap minigame — Action Layer §3. ONE-AND-DONE, per action.
# Returns a normalized Score01 through one interface, so new actions need no
# new plumbing. ALWAYS SKIPPABLE: skipping yields 0 and the base band alone
# must still be sufficient.
extends Control
class_name Minigame

signal finished(score01: float)

var running := false
var marker := 0.0
var dir := 1.0
var speed := 1.35
var sweet_centre := 0.5
var sweet_width := 0.16
var label_text := "Harvest"

@onready var bar: Control = $Panel/Bar
@onready var title: Label = $Panel/Title
@onready var hint: Label = $Panel/Hint

func begin(action_label: String, difficulty: float = 1.0) -> void:
	label_text = action_label
	speed = 1.05 + 0.5 * difficulty
	sweet_width = clampf(0.20 - 0.05 * difficulty, 0.07, 0.20)
	sweet_centre = randf_range(0.3, 0.7)
	marker = 0.0
	dir = 1.0
	running = true
	visible = true
	if title: title.text = "%s — tap in the band" % label_text
	if hint: hint.text = "SPACE / click to strike     ESC to skip (no bonus, no penalty)"

func _process(delta: float) -> void:
	if not running: return
	marker += dir * speed * delta
	if marker >= 1.0: marker = 1.0; dir = -1.0
	elif marker <= 0.0: marker = 0.0; dir = 1.0
	if bar: bar.queue_redraw()

func _unhandled_input(e: InputEvent) -> void:
	if not running: return
	if e is InputEventKey and e.pressed and not e.echo:
		if e.keycode == KEY_ESCAPE:
			_stop(0.0)
		elif e.keycode == KEY_SPACE:
			_strike()
	elif e is InputEventMouseButton and e.pressed and e.button_index == MOUSE_BUTTON_LEFT:
		_strike()

func _strike() -> void:
	var dist: float = absf(marker - sweet_centre)
	var half: float = sweet_width * 0.5
	var score: float = 0.0
	if dist <= half:
		score = 1.0 - (dist / half) * 0.35     # 0.65..1.0 inside the band
	else:
		score = maxf(0.0, 0.45 - (dist - half))
	_stop(clampf(score, 0.0, 1.0))

func _stop(score: float) -> void:
	running = false
	visible = false
	finished.emit(score)

func _on_bar_draw() -> void:
	var w: float = bar.size.x
	var h: float = bar.size.y
	bar.draw_rect(Rect2(0, 0, w, h), Color(0.14,0.15,0.19))
	var sx: float = (sweet_centre - sweet_width * 0.5) * w
	bar.draw_rect(Rect2(sx, 0, sweet_width * w, h), Color(0.30,0.75,0.45,0.85))
	var cx: float = sweet_centre * w
	bar.draw_rect(Rect2(cx - 1.5, 0, 3, h), Color(0.7,1.0,0.8))
	bar.draw_rect(Rect2(marker * w - 2, -4, 4, h + 8), Color(1.0,0.9,0.45))
