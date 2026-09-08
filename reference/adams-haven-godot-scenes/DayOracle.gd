# DAY ORACLE — asks Claude for the day's fiction, and never lets it break the game.
#
# One call per in-game day, cached into the save. Everything it returns is
# TREATED AS UNTRUSTED DATA: it is parsed, validated against the rules the game
# already has, and thrown away whole if anything is off. The fallback is a real
# playable day, not an error screen, so the game is identical in shape whether
# the network is there or not.
#
# The division of labour is the point:
#   the model writes the FICTION — names, prose, framing, which of the existing
#                                  contract kinds fits the day
#   the game owns the RULES      — what a mission can be, what it pays, what
#                                  counts as done
#
# A generated mission is checked by Mission.is_valid_dict(); a generated story
# by Story.is_valid(). Neither can invent a kind, a creature, a reward or a
# dangling branch. The worst a bad reply can do is get discarded.
#
# This is the ONLY file in the project that touches the network, and nothing in
# core/ knows it exists.
extends Node
class_name DayOracle

const ENDPOINT := "https://api.anthropic.com/v1/messages"
const MODEL := "claude-opus-5"
const API_VERSION := "2023-06-01"
const MAX_TOKENS := 4000
const TIMEOUT_SECONDS := 25.0

# Emitted with the validated day, or with {} when the day could not be
# generated. A listener that only handles the happy path is a bug: the empty
# case is normal, not exceptional.
signal day_ready(content: Dictionary)

var _http: HTTPRequest = null
var _pending_day := 0

# The key never lives in the project. Environment first so a developer can
# export it for a session; then a file outside version control for convenience.
static func api_key() -> String:
	var env := OS.get_environment("ANTHROPIC_API_KEY")
	if env.strip_edges() != "": return env.strip_edges()
	var path := "user://anthropic_key.txt"
	if FileAccess.file_exists(path):
		var f := FileAccess.open(path, FileAccess.READ)
		if f != null:
			var k := f.get_as_text().strip_edges()
			f.close()
			return k
	return ""

static func available() -> bool:
	return api_key() != ""

# ------------------------------------------------------------------ asking

func request_day(day: int, context: Dictionary) -> void:
	_pending_day = day
	if not available():
		# No key is a normal state, not a failure. Say so once and move on.
		day_ready.emit({})
		return
	if _http == null:
		_http = HTTPRequest.new()
		_http.timeout = TIMEOUT_SECONDS
		add_child(_http)
		_http.request_completed.connect(_on_reply)
	var body := {
		"model": MODEL,
		"max_tokens": MAX_TOKENS,
		"messages": [{"role": "user", "content": _prompt(day, context)}],
	}
	var headers := PackedStringArray([
		"content-type: application/json",
		"x-api-key: %s" % api_key(),
		"anthropic-version: %s" % API_VERSION,
	])
	var err := _http.request(ENDPOINT, headers, HTTPClient.METHOD_POST,
		JSON.stringify(body))
	if err != OK:
		push_warning("DayOracle: could not send request (%d)" % err)
		day_ready.emit({})

func _prompt(day: int, context: Dictionary) -> String:
	# The prompt states the WHOLE contract, including the enum values, because
	# anything outside them is rejected on arrival and the day falls back. It is
	# cheaper to be precise here than to throw away good prose over a bad enum.
	var roster := ", ".join(context.get("roster", []))
	var species: Array = []
	for k in Content.SPECIES.keys():
		species.append("%s (%s)" % [k, Content.SPECIES[k]])
	return """You are writing one day of content for Adams Haven, a card-battler roguelite.

SETTING. Elysium Vale, a high-fantasy world. The player commands the Silverbrook
Adventure Guild on the eastern edge of Silverwood Forest. The forest is being
corrupted by GKOM: twisted, monstrous beings embodying dark magic, losing their
sanity. Tone: quiet, weathered, ominous. Not whimsical, not grand.

TODAY. In-game day %d. The player's roster: %s.

Write TWO things and return them as a single JSON object, nothing else — no
markdown fence, no commentary.

1. "board": exactly 3 Guild contracts, each an object:
   - "kind": 0 = Sweep (reach the far end), 1 = Hunt (kill a named quarry),
     2 = Cull (kill N of one species), 3 = Gauntlet (win N fights, no resting)
   - "species": REQUIRED for kind 1 and 2, and must be exactly one of:
     %s
   - "count": REQUIRED for kind 2 and 3. An integer from 2 to 8.
   - "title": under 80 characters, concrete, no flourish.
   - "blurb": one or two sentences, under 240 characters.
   Make the three contracts different KINDS from each other.

2. "story": a short choose-your-own-adventure scene for this morning:
   - "title": under 90 characters
   - "start": the id of the opening beat
   - "beats": 2 to 5 objects, each with:
       "id" (short slug), "title", "prose" (under 1200 characters),
       "choices": 0 to 4 objects with "text" and "goto" (a beat id, or "" to
       end the scene). A beat with no choices ends the scene.
     Every "goto" must name a beat that exists. One choice may carry a
     "mission" object shaped exactly like a board contract above.

Return only the JSON object: {"board": [...], "story": {...}}""" % [
		day, roster if roster != "" else "unnamed", "\n     ".join(species)]

# ------------------------------------------------------------- receiving

func _on_reply(_result: int, code: int, _headers: PackedStringArray,
		body: PackedByteArray) -> void:
	var content := _parse(code, body)
	day_ready.emit(content)

# Everything that can go wrong here is expected: no network, a rate limit, a
# refusal, prose wrapped around the JSON, a missing field. Each one ends the
# same way — an empty dictionary, and the caller falls back.
func _parse(code: int, body: PackedByteArray) -> Dictionary:
	if code != 200:
		push_warning("DayOracle: HTTP %d — falling back to a rolled day." % code)
		return {}
	var envelope = JSON.parse_string(body.get_string_from_utf8())
	if typeof(envelope) != TYPE_DICTIONARY:
		push_warning("DayOracle: reply was not JSON.")
		return {}
	if str(envelope.get("stop_reason", "")) == "refusal":
		push_warning("DayOracle: the model declined this prompt.")
		return {}
	var text := ""
	for block in envelope.get("content", []):
		if typeof(block) == TYPE_DICTIONARY and str(block.get("type", "")) == "text":
			text += str(block.get("text", ""))
	if text.strip_edges() == "": return {}

	var parsed = JSON.parse_string(_unfence(text))
	if typeof(parsed) != TYPE_DICTIONARY:
		push_warning("DayOracle: the day was not a JSON object.")
		return {}
	return validated(parsed)

# The model is asked for bare JSON, but a fence is the single most likely way a
# good reply arrives slightly wrong, and it is trivial to survive.
static func _unfence(t: String) -> String:
	var s := t.strip_edges()
	if s.begins_with("```"):
		var nl := s.find("\n")
		if nl >= 0: s = s.substr(nl + 1)
		if s.ends_with("```"): s = s.substr(0, s.length() - 3)
	return s.strip_edges()

# The gate. Anything that does not survive this is dropped, and a dropped part
# does not take the rest of the day with it: a bad story still leaves a usable
# board, and vice versa.
static func validated(raw: Dictionary) -> Dictionary:
	var out := {}
	var board: Array = []
	for m in raw.get("board", []):
		if typeof(m) != TYPE_DICTIONARY: continue
		var why := Mission.problem_with_dict(m)
		if why != "":
			push_warning("DayOracle: rejected a contract — %s" % why)
			continue
		board.append(m)
	if not board.is_empty(): out["board"] = board

	var st = raw.get("story", null)
	if typeof(st) == TYPE_DICTIONARY:
		var sp := Story.problem_with(st)
		if sp == "":
			out["story"] = st
		else:
			push_warning("DayOracle: rejected the story — %s" % sp)
	return out
