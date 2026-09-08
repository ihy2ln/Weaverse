# TOWN — Silverbrook Settlement, eastern edge of Silverwood Forest.
#
# Every building here is a Placement item on the "town" screen, so the Guild,
# the Inn and the Deck Hall can all be picked up and set down somewhere better.
# Only the Forest Gate is bolted down: it is a hole in the world boundary, not
# a building, and WorldDefs marks it unmovable.
extends ModeStub

var _recruit_target := ""
var _minigame = null

# The Frosted Mug doubles as where bonds are built, so it holds a two-step
# "who with who" selection.
var _bond_a := ""
var _bond_b := ""

func _init() -> void:
	screen_id = "town"
	area_name = "SILVERBROOK SETTLEMENT"
	ground_tile = Tiles.GRASS
	# Level 1 of the settlement: the crossroads. Chosen over the single-road
	# variants because it is symmetric — a lone road running one way implies a
	# direction the buildings would have to respect, and the player puts those
	# wherever they like.
	backdrop_path = "res://art/landscape/town.png"

func _mode_summary() -> void:
	var s = _sess()
	if s == null: return
	info.append_text("\nRuns completed: %d    Day %d\n"
		% [int(_prof(s).get("runs_completed", 0)), int(_prof(s).get("day", 1))])
	info.append_text("[i]Buildings sit where you put them. Trade likes company; homes like neighbours.[/i]\n")

func _extra_interact(it) -> void:
	var sess = _sess()
	match it.def_id:
		"deck_hall":
			if sess == null: return
			var deck: Array = sess.build_deck()
			info.append_text("\n[b]Run deck:[/b] %d cards from %d deployed units.\n"
				% [deck.size(), sess.build_party().size()])
			_write_bond_summary(deck)
			_button("Open the Deck Hall", func():
				get_tree().change_scene_to_file("res://scenes/DeckHall.tscn"))
		"market":
			if sess == null: return
			info.append_text("\nGold: %d\nCYA charms held: %d\n"
				% [_gold(), int(_prof(sess).get("cya", 0))])
			info.append_text("\n[b]Move Tomes[/b] — %dg. Gold buys the STUDY, never the result:\n"
				% sess.tome_cost())
			info.append_text("[i]which move it turns out to be is still their class pool's call.[/i]\n")
			for uid in _roster(sess):
				var who := str(uid)
				var known: int = sess.known_moves(who).size()
				var pool: int = Moves.pool_for(Moves.class_of(who)).size()
				var tb := _button("Tome for %s  (knows %d/%d)"
					% [_name_of(who), known, pool], func():
					var res: Dictionary = sess.buy_move_tome(who)
					if res.get("ok", false):
						_sfx("victory")
						_say("%s %s: [b]%s[/b] (%s)."
							% [_name_of(who), str(res.label), str(res.name), str(res.rarity)])
					else:
						_sfx("denied")
						_say(str(res.reason))
					_after_change())
				if known >= pool or sess.tome_cost() > _gold():
					tb.disabled = true
			info.append_text("\n")
			_button("Buy CYA charm (60g)", func():
				if sess.spend("gold", 60):
					_prof(sess)["cya"] = int(_prof(sess).get("cya", 0)) + 1
					sess.save()
					_sfx("coin")
					_say("Charm bought.")
				else:
					_sfx("denied")
					_say("Not enough gold.")
				_after_change())
		"guild_hall":
			if sess == null: return
			info.append_text("\nRoster: %s\n" % ", ".join(_roster(sess)))
			_contract_board(sess)
			_promotion_panel(sess)
			_recruit_panel(sess)
		"frosted_mug":
			info.append_text("\nHelda's hearth. Where the party mends, and where they get to know each other.\n")
			if sess == null: return
			_button("Rest the party", func():
				if sess.rest_party():
					_sfx("heal")
					_say("Everyone is on their feet — and a night under one roof brought them closer.")
				else:
					_sfx("denied")
					_say("Not enough gold to take a room.")
				_after_change())
			_bond_panel(sess)
		"house":
			info.append_text("\nYour own roof, standing in the town.\n")
			_button("Go inside", func():
				get_tree().change_scene_to_file("res://scenes/Home.tscn"))
		"gate":
			if sess == null: return
			var dg: Dungeon = sess.dungeon
			if dg == null: return
			info.append_text("\n[b]The Silverwood[/b] — how far down are you going?\n")
			info.append_text("[i]A floor holds more fights than one trip can carry out. Clear what you can, make camp, and walk back with it — the floor remembers.[/i]\n")
			var job = sess.accepted_mission()
			if job != null:
				info.append_text("\n  Carrying: [color=#ffd76a]%s[/color] (%dg on completion)\n"
					% [job.title, job.reward_gold()])
			else:
				info.append_text("\n  [color=#8a8a92]No contract taken — the Guild board is where the work is.[/color]\n")
			# Only the floors you have opened. Depth is earned by beating the
			# floor above it, so the list growing IS the progress.
			for fi in range(0, dg.deepest_unlocked + 1):
				var idx: int = fi
				var pr: Dictionary = dg.floor_progress(fi)
				info.append_text("\n  [b]%s[/b] — %d of %d fights cleared%s\n" % [
					dg.floor_name(fi), int(pr.get("cleared", 0)), int(pr.get("fights", 0)),
					"   [color=#9fe6a0]BEATEN[/color]" if bool(pr.get("beaten", false)) else ""])
				info.append_text("  [color=#8a8a92]Rewards x%.2f this deep.[/color]\n"
					% (1.0 + Dungeon.REWARD_PER_FLOOR * float(fi)))
				_button("Go down to %s" % dg.floor_name(fi), func():
					if sess.start_delve(idx, Vector2i(-1, -1), sess.accepted_mission()):
						get_tree().change_scene_to_file("res://scenes/Dungeon.tscn"))
				# A camp you have reached BECOMES AN ENTRANCE. It is a way in and
				# a way out from then on, which is what stops the fourth trip
				# onto a floor opening with a walk through rooms holding nothing.
				var camp_list: Array = dg.floor_at(fi).camps()
				if camp_list.is_empty():
					info.append_text("  [color=#8a8a92]No camp reached yet — reach one and it becomes a second way in.[/color]
")
				for ci in camp_list.size():
					var cell: Vector2i = camp_list[ci]
					var label := "Camp %d" % (ci + 1)
					_button("   go in at %s instead" % label, func():
						if sess.start_delve(idx, cell, sess.accepted_mission()):
							get_tree().change_scene_to_file("res://scenes/Dungeon.tscn"))

# The Guild is where the bond web actually gets built, so it says so.
func _recruit_panel(sess) -> void:
	var owned: Array = _roster(sess)
	var any := false
	for u in Content.recruitable():
		if u.id in owned: continue
		any = true
		var uid: String = u.id
		var allies: Array = []
		for b in Content.bonds_of(uid):
			allies.append(String(b).capitalize())
		info.append_text("\n[b]%s[/b] — %s\n" % [u.display_name, u.get_meta("blurb", "")])
		info.append_text("  [color=#9fe6a0]Bonds with %s[/color]\n" % ", ".join(allies))
		_button("Recruit %s" % u.display_name, func(): _begin_recruit(uid))
	if not any:
		info.append_text("\nEveryone in the Vale already answers to you.\n")

# What the party's bonds are worth, summarised where the deck is read.
func _write_bond_summary(deck: Array) -> void:
	var sess = _sess()
	if sess == null: return
	var present := {}
	for u in sess.build_party():
		present[u.id] = true
	var live := 0
	var possible := 0
	for c in deck:
		for p in c.partners:
			possible += 1
			if present.has(str(p)): live += 1
	if possible == 0: return
	info.append_text("[b]Bonds active:[/b] %d of %d [color=#9fe6a0](%d%%)[/color]\n"
		% [live, possible, int(100.0 * float(live) / float(possible))])
	info.append_text("[i]Every card is stronger for each bonded ally on the field. Recruiting is the deckbuilding.[/i]\n")

# Recruiting runs the same Action Layer pipeline as everything else: an optional
# minigame, the rhythm layer, then a VISIBLE dice roll.
func _begin_recruit(uid: String) -> void:
	_recruit_target = uid
	var sess = _sess()
	if sess != null and not bool(sess.data.get("settings", {}).get("minigames", true)):
		_finish_recruit(0.0)
		return
	if _minigame == null:
		_minigame = load("res://scenes/Minigame.tscn").instantiate()
		add_child(_minigame)
		_minigame.finished.connect(_finish_recruit)
	_minigame.begin("Negotiating the contract", 1.2)

func _finish_recruit(score01: float) -> void:
	var sess = _sess()
	if sess == null or _recruit_target == "": return
	var uid := _recruit_target
	_recruit_target = ""
	var rhythm := 0
	if has_node("/root/Beat"):
		var b = get_node("/root/Beat")
		if b.enabled and b.accuracy() >= 0.75: rhythm = 2
	var roll := Dice.roll_d20(1, Dice.minigame_modifier(score01), rhythm)
	var tier := Dice.tier_for(roll.total)
	var res: Dictionary = sess.try_recruit(uid, tier)
	_say("[b]Recruitment[/b]  %s -> %s" % [roll.text, Dice.TIER_NAMES[tier]])
	if res.ok:
		_sfx("victory")
		var u := Content.unit_by_id(uid)
		info.append_text("[color=#9fe6a0]%s joined. Every card bonded to them just got stronger.[/color]\n"
			% (u.display_name if u else uid))
	else:
		_sfx("denied")
		info.append_text("[color=#ff9c9c]%s[/color]\n" % res.reason)
	_after_change()


# The save can still be empty when a screen opens, so every read of it goes
# through a default. A side panel is not worth crashing a scene over.
func _prof(s) -> Dictionary:
	return s.data.get("profile", {}) if s != null else {}

func _roster(s) -> Array:
	return s.data.get("roster", []) if s != null else []

# ------------------------------------------------------------------- bonds
#
# Bonds deepen on their own from fighting together. This is the DELIBERATE
# track: pick two people and spend time (and gold) on them. Everything here is
# day-capped in BondLedger, so it is a thing you do across a run rather than an
# afternoon of grinding.

func _bond_panel(sess) -> void:
	var roster: Array = _roster(sess)
	info.append_text("\n[b]Time together[/b]\n")
	if roster.size() < 2:
		info.append_text("[color=#8a8a92]You need two people in the party for that.[/color]\n")
		return
	if not (_bond_a in roster): _bond_a = ""
	if not (_bond_b in roster): _bond_b = ""

	if _bond_a == "" or _bond_b == "":
		var picking: String = "first" if _bond_a == "" else "second"
		info.append_text("Choose the %s of the pair.\n" % picking)
		if _bond_a != "":
			info.append_text("  With: [b]%s[/b]\n" % _name_of(_bond_a))
		for uid in roster:
			if uid == _bond_a: continue
			var pick: String = str(uid)
			_button(_name_of(pick), func():
				if _bond_a == "": _bond_a = pick
				else: _bond_b = pick
				_after_change())
		if _bond_a != "":
			_button("< back", func():
				_bond_a = ""
				_after_change())
		return

	var prog: Dictionary = sess.bond_progress(_bond_a, _bond_b)
	var bonded: bool = Content.bonded(_bond_a, _bond_b)
	info.append_text("[b]%s[/b] and [b]%s[/b]\n"
		% [_name_of(_bond_a), _name_of(_bond_b)])
	info.append_text("  %s  [color=#9fe6a0]rank %d/%d[/color]\n"
		% [str(prog.name), int(prog.rank), BondLedger.MAX_RANK])
	if bool(prog.maxed):
		info.append_text("  %s\n" % _bar(1.0))
		info.append_text("  [color=#ffd76a]As close as they get.[/color]\n")
	else:
		info.append_text("  %s  %d / %d\n"
			% [_bar(float(prog.fraction)), int(prog.into), int(prog.needed)])
	# Whether this pair actually pays off in battle is the thing worth saying.
	if bonded:
		info.append_text("  [color=#9fe6a0]Their cards name each other — every rank makes those cards hit harder.[/color]\n")
	else:
		info.append_text("  [color=#8a8a92]No card names this pair, so rank buys nothing in battle yet.[/color]\n")

	for kind in BondLedger.CHOSEN:
		var k: String = str(kind)
		var cost: int = BondLedger.cost_of(k)
		var gain: int = int(BondLedger.SOURCES[k]["xp"])
		var b := _button("%s  (%dg, +%d)" % [BondLedger.verb_of(k), cost, gain], func():
			var res: Dictionary = sess.share_bond(_bond_a, _bond_b, k)
			if res.get("ok", false):
				_sfx("heal" if k == "date" else "coin")
				_say(str(res.label) + ".")
				if res.get("rank_up", false):
					_sfx("victory")
					info.append_text("  [color=#ffd76a]They are now %s.[/color]\n" % str(res.name))
			else:
				_sfx("denied")
				_say(str(res.reason))
			_after_change())
		if not sess.can_share_bond(_bond_a, _bond_b, k):
			b.disabled = true
			b.text += "   (done today)"
		elif cost > _gold():
			b.disabled = true

	_button("< choose someone else", func():
		_bond_a = ""
		_bond_b = ""
		_after_change())

func _bar(f: float) -> String:
	var filled: int = int(round(clampf(f, 0.0, 1.0) * 12.0))
	return "[" + "=".repeat(filled) + ".".repeat(12 - filled) + "]"

func _name_of(uid: String) -> String:
	var u := Content.unit_by_id(uid)
	return u.display_name if u != null else uid

# --------------------------------------------------------------- promotion
#
# TIER is the codex rank, and the Guild is what grants it — Kaela runs this
# place, so promoting the people under her is exactly what it should do. A
# promotion raises every stat AND teaches a move, which is what makes saving
# for one worth it.

func _promotion_panel(sess) -> void:
	info.append_text("\n[b]Promotion[/b] — the Guild raises a rank, and a rank teaches a move.\n")
	for uid in _roster(sess):
		var who := str(uid)
		var tier_now: String = Tiers.name_of(sess.char_tier(who))
		var check: Dictionary = sess.can_promote(who)
		var lvl: int = sess.char_level(who)
		var need: int = sess.promote_level_required(who)
		var cost: int = sess.promote_cost(who)
		info.append_text("  [b]%s[/b]  rank [color=#ffd76a]%s[/color]  ·  lv%d/%d  ·  %dg\n"
			% [_name_of(who), tier_now, lvl, need, cost])
		var pb := _button("Promote %s to %s  (%dg)"
			% [_short_name(who), Tiers.name_of(sess.char_tier(who) + 1), cost], func():
			var res: Dictionary = sess.promote(who)
			if res.get("ok", false):
				_sfx("victory")
				_say("[b]%s[/b] is promoted %s -> %s."
					% [_name_of(who), str(res.from), str(res.to)])
				var t: Dictionary = res.get("taught", {})
				if t.get("ok", false):
					info.append_text("  [color=#9fe6a0]%s %s: [b]%s[/b] (%s).[/color]\n"
						% [_short_name(who), str(t.label), str(t.name), str(t.rarity)])
			else:
				_sfx("denied")
				_say(str(res.reason))
			_after_change())
		if not check.get("ok", false):
			pb.disabled = true

func _short_name(uid: String) -> String:
	return _name_of(uid).split(" ")[0]

# ---------------------------------------------------------- the job board
#
# What the run is FOR. The Gate decides how far in; this decides why. Posted
# per day, so it is the same board every time you walk in that morning.

func _contract_board(sess) -> void:
	var taken = sess.accepted_mission()
	info.append_text("\n[b]Contract board[/b]\n")
	if taken != null:
		info.append_text("  Accepted: [color=#ffd76a]%s[/color]\n" % taken.title)
		info.append_text("  [color=#8a8a92]%s[/color]\n" % taken.blurb)
		info.append_text("  Pays [b]%dg[/b] on completion. Take it in at the Forest Gate.\n"
			% taken.reward_gold())
		_button("Hand the contract back", func():
			sess.clear_mission()
			_sfx("back")
			_say("Returned to the board.")
			_after_change())
		return
	info.append_text("[i]Posted this morning. One at a time.[/i]\n")
	for m in sess.mission_board():
		var job: Mission = m
		info.append_text("\n  [b]%s[/b]  [color=#9fe6a0](%s, %dg)[/color]\n"
			% [job.title, job.kind_name(), job.reward_gold()])
		info.append_text("  [color=#8a8a92]%s[/color]\n" % job.blurb)
		_button("Accept: %s" % job.title, func():
			sess.accept_mission(job)
			_sfx("card")
			_say("Contract accepted.")
			_after_change())
