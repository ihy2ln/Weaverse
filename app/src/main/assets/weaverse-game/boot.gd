extends Node
## Weaverse Games boot. The first autoload of the boot project in the Weaverse APK.
##
## Official Godot Android runtimes will not start a main pack from outside the APK, so the
## game zip is mounted here instead, in _init: Godot loads and instantiates autoloads one
## at a time, so by the time it loads the game's own autoloads (Session, Sfx, ...) they
## come from the pack. Weaverse writes the pack path into the override file as
## weaverse/pack_path before every launch (AdamsHavenGameActivity).

var mounted := false

func _init() -> void:
	var pack: String = ProjectSettings.get_setting("weaverse/pack_path", "")
	mounted = not pack.is_empty() and ProjectSettings.load_resource_pack(pack, true)
	if not mounted:
		push_error("Weaverse: could not mount the game pack at '%s'." % pack)

func _ready() -> void:
	if not mounted:
		OS.alert("The game data could not be opened. Import the game pack again from Weaverse → Games.", "Adams Haven")
		get_tree().quit()
