# Battle hit feedback — the layer that tells you something HAPPENED.
#
# Split out of BattleScene because it is genuinely separate: it owns no rules,
# reads no battle state, and only knows "this rectangle just lost 31 health".
# BattleScene diffs the HP before and after an action and reports it here.
#
# Three effects, and each answers a different question the player has:
#   a floating number  — how much?
#   a panel flash      — who?
#   a screen shake     — how big a deal was that?
#
# Deliberately a Control that ignores the mouse and sits above everything: it
# must never intercept a click meant for a card or a unit panel.
extends Control
class_name HitFeedback

const RISE := 46.0            # pixels a number floats before it is gone
const LIFE := 0.9             # seconds it takes
const FLASH_FADE := 3.2       # how fast a panel flash decays
const SHAKE_FADE := 5.5

# Colours carry the same meaning everywhere: red hurt, green healed, gold crit.
const COL_DAMAGE := Color(1.00, 0.42, 0.38)
const COL_HEAL := Color(0.52, 0.95, 0.55)
const COL_CRIT := Color(1.00, 0.85, 0.35)
const COL_DOWN := Color(0.80, 0.55, 1.00)

var _numbers: Array = []       # {text, colour, at:Vector2, age, big}
var _flash: Dictionary = {}    # unit id -> {rect, amount, colour}
var shake := 0.0

func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	# Full-rect AND zeroed offsets: an anchors preset alone leaves the size at
	# zero when the node is created in code, which is survivable here only
	# because _draw() is not clipped to it. Do not rely on that.
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	z_index = 100

# --------------------------------------------------------------- reporting

# One HP change on one unit. `rect` is where that unit's panel sits, in this
# node's coordinates, so the number appears over the thing that was hit.
func report(uid: String, delta: int, rect: Rect2, crit: bool = false,
		died: bool = false) -> void:
	if delta == 0 and not died: return
	var col := COL_HEAL if delta > 0 else COL_DAMAGE
	if crit and delta < 0: col = COL_CRIT
	var txt := ("+%d" % delta) if delta > 0 else str(delta)
	if crit and delta < 0: txt += "!"
	_numbers.append({
		"text": txt, "colour": col, "big": crit or absi(delta) >= 60,
		# jittered, so several hits in one action do not stack into one blur
		"at": rect.position + Vector2(rect.size.x * randf_range(0.45, 0.78),
			rect.size.y * 0.42),
		"age": 0.0,
	})
	_flash[uid] = {"rect": rect, "amount": 1.0, "colour": col}
	if died:
		_numbers.append({"text": "DOWN", "colour": COL_DOWN, "big": true,
			"at": rect.position + Vector2(rect.size.x * 0.5, rect.size.y * 0.72),
			"age": 0.0})
	# Shake scales with the fraction of a panel's worth of damage, capped, so a
	# chip hit is felt and a huge one does not throw the screen off the desk.
	if delta < 0:
		shake = minf(1.0, shake + (0.55 if crit else 0.3))
	set_process(true)
	queue_redraw()

func announce(text: String, colour: Color, at: Vector2) -> void:
	_numbers.append({"text": text, "colour": colour, "big": true,
		"at": at, "age": 0.0})
	set_process(true)
	queue_redraw()

func kick(amount: float) -> void:
	shake = minf(1.4, shake + amount)
	set_process(true)

func busy() -> bool:
	return not _numbers.is_empty() or not _flash.is_empty() or shake > 0.001

# The offset BattleScene should apply to its root while things are landing.
func shake_offset() -> Vector2:
	if shake <= 0.001: return Vector2.ZERO
	# Kept small: the top bar sits close to the screen edge, and a big shake
	# clips it rather than reading as impact.
	var mag := shake * 5.0
	return Vector2(randf_range(-mag, mag), randf_range(-mag, mag))

func clear() -> void:
	_numbers.clear()
	_flash.clear()
	shake = 0.0
	queue_redraw()

# ------------------------------------------------------------------ update

func _process(delta: float) -> void:
	var alive: Array = []
	for n in _numbers:
		n["age"] = float(n["age"]) + delta
		if float(n["age"]) < LIFE: alive.append(n)
	_numbers = alive

	var keep := {}
	for uid in _flash.keys():
		var f: Dictionary = _flash[uid]
		f["amount"] = float(f["amount"]) - delta * FLASH_FADE
		if float(f["amount"]) > 0.0: keep[uid] = f
	_flash = keep

	shake = maxf(0.0, shake - delta * SHAKE_FADE)
	queue_redraw()
	if not busy():
		set_process(false)

func _draw() -> void:
	var f := ThemeDB.fallback_font
	for uid in _flash.keys():
		var e: Dictionary = _flash[uid]
		var a: float = clampf(float(e["amount"]), 0.0, 1.0)
		var col: Color = e["colour"]
		var r: Rect2 = e["rect"]
		draw_rect(r, Color(col.r, col.g, col.b, 0.22 * a))
		draw_rect(r, Color(col.r, col.g, col.b, 0.85 * a), false, 2.0)

	for n in _numbers:
		var t: float = clampf(float(n["age"]) / LIFE, 0.0, 1.0)
		# Rises fast then settles, and only fades over the back half, so the
		# number is readable for most of its life instead of ghosting instantly.
		var rise := RISE * (1.0 - pow(1.0 - t, 2.4))
		var alpha: float = 1.0 if t < 0.55 else (1.0 - (t - 0.55) / 0.45)
		var size: int = 30 if bool(n["big"]) else 22
		var col2: Color = n["colour"]
		var at: Vector2 = n["at"] - Vector2(0, rise)
		# a dark backing pass, so a number stays legible over bright art
		draw_string(f, at + Vector2(2, 2), str(n["text"]),
			HORIZONTAL_ALIGNMENT_LEFT, -1, size, Color(0, 0, 0, 0.65 * alpha))
		draw_string(f, at, str(n["text"]),
			HORIZONTAL_ALIGNMENT_LEFT, -1, size,
			Color(col2.r, col2.g, col2.b, alpha))
