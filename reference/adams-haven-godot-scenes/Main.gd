# Mode router — the ModeRouter the design docs call for. Loads Battle / Town /
# Home / Farm without any mode referencing another mode's scene objects.
extends Control

@onready var info: RichTextLabel = $Root/Info

func _ready() -> void:
	info.clear()
	info.append_text("[b]ADAMS HAVEN CARD GAME[/b] — prototype\n")
	info.append_text("Elysium Vale · Silverbrook Settlement, eastern edge of Silverwood Forest\n\n")
	var s = _session()
	if s != null:
		info.append_text("Day %d   Gold %d   I-Points %d\n" % [
			s.profile.day, s.inventory.gold, s.profile.ipoints])
		info.append_text("Roster: %s\n" % ", ".join(s.roster))
		info.append_text("Town tier %d   House tier %d (%s)\n" % [
			s.town.tier, s.house.tier, s.house.area])
	info.append_text("\n[i]Tier ladder: F E D C B A S SR SSR Omega (codex canon)[/i]")

# Autoloads exist at runtime but not under --headless --script, so resolve safely.
func _session():
	var root := get_tree().root
	if root.has_node("Session"):
		return root.get_node("Session").data
	return null

func _go(path: String) -> void:
	get_tree().change_scene_to_file(path)

func _on_battle_pressed() -> void: _go("res://scenes/Dungeon.tscn")
func _on_town_pressed() -> void:   _go("res://scenes/Town.tscn")
func _on_home_pressed() -> void:   _go("res://scenes/Home.tscn")
func _on_farm_pressed() -> void:   _go("res://scenes/Farm.tscn")
func _on_settings_pressed() -> void: _go("res://scenes/Settings.tscn")
func _on_quit_pressed() -> void:   get_tree().quit()
