# The Deck Hall — where the run deck is actually decided.
#
# It is not a card list any more. The run deck is the union of every deployed
# unit's EQUIPPED MOVES plus the Summoner's kit, so the only thing there is to
# decide here is who carries which moves in their slots. The old DP-budget card
# picker went out with the budget it served — it was writing a `deck` key that
# nothing has read since move slots replaced it.
#
# The third column is the whole point. A card is stronger for each BONDED ally
# still standing beside it, so what a move is worth depends on who you brought
# and how well they know each other. Putting the moves and the bonds in
# separate screens would hide the one decision that connects them.
extends Control

var picked := ""      # the roster member currently being edited

@onready var roster_box: VBoxContainer = $Root/Body/Roster/List/V
@onready var moves_box: VBoxContainer = $Root/Body/Moves/List/V
@onready var bonds_box: VBoxContainer = $Root/Body/Bonds/List/V
@onready var roster_head: RichTextLabel = $Root/Body/Roster/H
@onready var moves_head: RichTextLabel = $Root/Body/Moves/H
@onready var bonds_head: RichTextLabel = $Root/Body/Bonds/H
@onready var summary: RichTextLabel = $Root/Summary

func _sess():
	var r := get_tree().root
	return r.get_node("Session") if r.has_node("Session") else null

func _ready() -> void:
	var s = _sess()
	if s != null:
		var r: Array = s.data.get("roster", [])
		if not r.is_empty(): picked = str(r[0])
	refresh()

func _sfx(k: String) -> void:
	if has_node("/root/Sfx"): get_node("/root/Sfx").play(k)

func _name_of(uid: String) -> String:
	var u := Content.unit_by_id(uid)
	return u.display_name if u != null else uid

func _short(uid: String) -> String:
	return _name_of(uid).split(" ")[0]

# ------------------------------------------------------------------ refresh

func refresh() -> void:
	for n in roster_box.get_children(): n.queue_free()
	for n in moves_box.get_children(): n.queue_free()
	for n in bonds_box.get_children(): n.queue_free()
	var s = _sess()
	if s == null:
		summary.clear()
		summary.append_text("[b]DECK HALL[/b]\nNo session — nothing to edit.")
		return
	_write_summary(s)
	_write_roster(s)
	_write_moves(s)
	_write_bonds(s)

func _write_summary(s) -> void:
	var party: Array = s.build_party()
	var deck: Array = s.build_deck()
	var used := 0
	var total := 0
	for u in party:
		used += s.equipped_moves(u.id).size()
		total += s.slots_for(u.id)
	var live := 0
	var possible := 0
	var present := {}
	for u in party: present[u.id] = true
	# Average rank across the bonds that are actually live. A running TOTAL was
	# the obvious thing to show and the wrong one: it has no ceiling, so it
	# reads as a score rather than as "how well does this party know itself".
	var depth := 0
	var ranked := 0
	for c in deck:
		for pid in c.partners:
			possible += 1
			if present.has(str(pid)):
				live += 1
				if c.owner_id != "":
					depth += s.bonds.rank(c.owner_id, str(pid))
					ranked += 1
	var avg := (float(depth) / float(ranked)) if ranked > 0 else 0.0
	summary.clear()
	summary.append_text("[b]RUN DECK[/b]  %d cards — every deployed unit's equipped moves, plus the Summoner's kit.\n"
		% deck.size())
	summary.append_text("Slots filled: [b]%d / %d[/b] across %d deployed.    " % [used, total, party.size()])
	if possible > 0:
		summary.append_text("Bonds live: [color=#9fe6a0]%d / %d[/color]    Average bond rank: [color=#ffd76a]%.1f / %d[/color]\n"
			% [live, possible, avg, BondLedger.MAX_RANK])
	else:
		summary.append_text("\n")
	summary.append_text("[i]Slots are the only deck constraint. A move is worth more when the allies its card names are on the field — deepen those bonds at the Frosted Mug.[/i]")

func _write_roster(s) -> void:
	roster_head.clear()
	roster_head.append_text("[b]ROSTER[/b]\n[i]Everyone deployed contributes their slots.[/i]")
	for uid in s.data.get("roster", []):
		var id := str(uid)
		var lvl: int = s.char_level(id)
		var used: int = s.equipped_moves(id).size()
		var slots: int = s.slots_for(id)
		var b := Button.new()
		b.custom_minimum_size = Vector2(0, 40)
		b.text = "%s   lv%d   %s   %d/%d" % [_short(id), lvl,
			Moves.CLASS_NAMES[Moves.class_of(id)], used, slots]
		if id == picked:
			b.modulate = Color(0.72, 1.0, 0.78)
		elif used < slots:
			b.modulate = Color(1.0, 0.88, 0.6)      # an empty slot is wasted deck
		b.pressed.connect(func():
			picked = id
			_sfx("select")
			refresh())
		roster_box.add_child(b)

func _write_moves(s) -> void:
	moves_head.clear()
	if picked == "":
		moves_head.append_text("[b]MOVES[/b]\nPick someone.")
		return
	var u := Content.unit_by_id(picked)
	if u == null: return
	var equipped: Array = s.equipped_moves(picked)
	var slots: int = s.slots_for(picked)
	moves_head.append_text("[b]%s — MOVES[/b]\n" % _name_of(picked))
	moves_head.append_text("[i]%d of %d slots filled. Learned at random; you choose what to equip.[/i]"
		% [equipped.size(), slots])

	for mid in s.known_moves(picked):
		var move_id := str(mid)
		var card := Moves.to_card(move_id, u)
		if card == null: continue
		var is_on: bool = move_id in equipped
		var b := Button.new()
		b.custom_minimum_size = Vector2(0, 46)
		b.add_theme_font_size_override("font_size", 12)
		var bits: Array = []
		if card.ep_cost > 0: bits.append("%d EP" % card.ep_cost)
		bits.append("no action" if card.ap_cost == 0 else "%d AP" % card.ap_cost)
		if card.power > 0.0: bits.append("%.1fx" % card.power)
		if card.heal > 0.0: bits.append("heal %d" % int(card.heal))
		if card.draw_count > 0: bits.append("draw %d" % card.draw_count)
		if card.status != "": bits.append(card.status)
		b.text = "%s  %s\n%s   ·   bond +%d%% per ally" % [
			"[EQUIPPED]" if is_on else "         ", card.display_name,
			", ".join(bits), int(card.partner_scale * 100.0)]
		if is_on:
			b.modulate = Color(0.72, 1.0, 0.78)
		elif equipped.size() >= slots:
			b.disabled = true          # no room until something comes out
		b.pressed.connect(func():
			if is_on: s.unequip(picked, move_id)
			else: s.equip(picked, move_id)
			s.save()
			_sfx("card")
			refresh())
		moves_box.add_child(b)

# The bond column: who this character's cards name, how deep each of those runs
# and — the part that matters — whether that ally is actually deployed.
func _write_bonds(s) -> void:
	bonds_head.clear()
	if picked == "":
		bonds_head.append_text("[b]BONDS[/b]")
		return
	var partners: Array = Content.bonds_of(picked)
	bonds_head.append_text("[b]%s — BONDS[/b]\n" % _short(picked))
	bonds_head.append_text("[i]Every card they own is stronger for each of these standing.[/i]")
	var roster: Array = s.data.get("roster", [])
	for pid in partners:
		var other := str(pid)
		var prog: Dictionary = s.bond_progress(picked, other)
		var deployed: bool = other in roster
		var panel := VBoxContainer.new()
		var l := RichTextLabel.new()
		l.bbcode_enabled = true
		l.fit_content = true
		l.custom_minimum_size = Vector2(0, 78)
		var head := "[b]%s[/b]  —  %s [color=#9fe6a0](rank %d/%d)[/color]\n" % [
			_name_of(other), str(prog.name), int(prog.rank), BondLedger.MAX_RANK]
		var bar := _bar(float(prog.fraction))
		var body := ""
		if bool(prog.maxed):
			body = "  %s  [color=#ffd76a]as close as they get[/color]\n" % bar
		else:
			body = "  %s  %d / %d to rank %d\n" % [bar, int(prog.into), int(prog.needed),
				int(prog.rank) + 1]
		# The number that actually lands in a fight.
		var mult := 1.0 + Battle.BOND_RANK_STEP * float(prog.rank)
		if deployed:
			body += "  [color=#9fe6a0]Deployed — worth x%.2f of the card's bond bonus.[/color]" % mult
		else:
			body += "  [color=#ff9c9c]Not recruited — this bond pays nothing yet.[/color]"
		l.append_text(head + body)
		panel.add_child(l)
		bonds_box.add_child(panel)
	if partners.is_empty():
		var none := RichTextLabel.new()
		none.bbcode_enabled = true
		none.fit_content = true
		none.append_text("[color=#8a8a92]No card names an ally for this character.[/color]")
		bonds_box.add_child(none)

func _bar(f: float) -> String:
	var filled: int = int(round(clampf(f, 0.0, 1.0) * 16.0))
	return "[" + "=".repeat(filled) + ".".repeat(16 - filled) + "]"

func _on_back_pressed() -> void:
	get_tree().change_scene_to_file("res://scenes/Town.tscn")
