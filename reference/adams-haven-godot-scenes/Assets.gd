# ASSETS — where a picture, clip or sound actually comes from at runtime.
#
# One rule, applied everywhere: A FILE THE PLAYER ADDED WINS OVER THE ONE THAT
# SHIPPED. Both trees are the same shape (see core/asset_kinds.gd), so
# resolution is: look in user://assets/<dir>/<slot>.<ext> for every extension
# the kind accepts, then in res://art/<dir>/<slot>.<ext>, then give up.
#
# Nothing is ever overwritten. Adding a picture for a monster writes a new file
# into user storage and the bundled one stays exactly where it was — so
# "put it back" is deleting the file you added, and that is what remove() does.
#
# WHY IMAGES ARE LOADED BY HAND. Anything under res:// went through Godot's
# importer at build time and loads with load(). Anything under user:// did not:
# it is a file that appeared after the game shipped, so it has no .import
# sidecar and load() will not touch it. Image.load_from_file + ImageTexture is
# the path that works for both, and it is why this file exists rather than the
# call sites just building a string.
#
# Presentation only — core/ has no idea this exists.
class_name Assets

# Textures the game has already built from user files. Rebuilding an ImageTexture
# every time a panel redraws would decode a PNG per frame.
static var _cache := {}

static func clear_cache() -> void:
	_cache = {}

# ------------------------------------------------------------------ finding

# The path the game should use for this slot, or "" when nothing is filed under
# it. User files are checked first — that is the whole point.
static func find(kind: String, slot: String) -> String:
	if not AssetKinds.has_kind(kind) or not AssetKinds.is_valid_slot(slot): return ""
	for ext in AssetKinds.accepts(kind):
		var p := AssetKinds.user_path(kind, slot, ext)
		if FileAccess.file_exists(p): return p
	for ext2 in AssetKinds.accepts(kind):
		var q := AssetKinds.game_path(kind, slot, ext2)
		if ResourceLoader.exists(q): return q
	return ""

static func has(kind: String, slot: String) -> bool:
	return find(kind, slot) != ""

# True when the player has put their own file over this slot.
static func is_overridden(kind: String, slot: String) -> bool:
	for ext in AssetKinds.accepts(kind):
		if FileAccess.file_exists(AssetKinds.user_path(kind, slot, ext)): return true
	return false

# ------------------------------------------------------------------ loading

# A texture for this slot, whichever tree it came from, or null.
static func texture(kind: String, slot: String) -> Texture2D:
	var path := find(kind, slot)
	if path == "": return null
	return texture_at(path)

static func texture_at(path: String) -> Texture2D:
	if path == "": return null
	if _cache.has(path): return _cache[path]
	var tex: Texture2D = null
	if path.begins_with("res://"):
		if ResourceLoader.exists(path): tex = load(path)
	else:
		# A file that arrived after the build has no import sidecar, so it has
		# to be decoded by hand rather than loaded as a resource.
		var img := Image.new()
		if img.load(path) == OK:
			tex = ImageTexture.create_from_image(img)
	if tex != null: _cache[path] = tex
	return tex

static func is_video(path: String) -> bool:
	return path.get_extension().to_lower() in AssetKinds.VIDEO_EXT

# A sound for this slot, or null. Same two-tree rule.
static func sound(kind: String, slot: String) -> AudioStream:
	var path := find(kind, slot)
	if path == "": return null
	if _cache.has(path): return _cache[path]
	var stream: AudioStream = null
	if path.begins_with("res://"):
		if ResourceLoader.exists(path): stream = load(path)
	else:
		# Godot can build an Ogg or MP3 stream from raw bytes; WAV cannot be
		# loaded this way, so a .wav added by the player is reported rather
		# than silently ignored.
		var bytes := FileAccess.get_file_as_bytes(path)
		if bytes.is_empty(): return null
		match path.get_extension().to_lower():
			"ogg": stream = AudioStreamOggVorbis.load_from_buffer(bytes)
			"mp3":
				var mp3 := AudioStreamMP3.new()
				mp3.data = bytes
				stream = mp3
			_:
				push_warning("Assets: %s cannot be loaded after the build — " % path
					+ "convert it to .ogg or .mp3.")
				return null
	if stream != null: _cache[path] = stream
	return stream

# ---------------------------------------------------------------- importing

# Copy a file the player picked into the slot it is for. Returns
# {ok, path, reason} — never throws, because every failure here is something
# the player should be told rather than something the game should fall over on.
static func install(kind: String, slot: String, src: String) -> Dictionary:
	var ext := src.get_extension()
	var why := AssetKinds.problem_with(kind, slot, ext)
	if why != "": return {"ok": false, "path": "", "reason": why}
	if not FileAccess.file_exists(src):
		return {"ok": false, "path": "", "reason": "there is no file at %s" % src}

	var dir := AssetKinds.user_dir(kind)
	var err := DirAccess.make_dir_recursive_absolute(ProjectSettings.globalize_path(dir))
	if err != OK and not DirAccess.dir_exists_absolute(ProjectSettings.globalize_path(dir)):
		return {"ok": false, "path": "", "reason": "could not make %s" % dir}

	# One file per slot, whatever it was before. Two files with the same name
	# and different extensions would both match and the winner would be
	# whichever extension the kind happens to list first.
	remove(kind, slot)

	var bytes := FileAccess.get_file_as_bytes(src)
	if bytes.is_empty():
		return {"ok": false, "path": "", "reason": "%s is empty or unreadable" % src}
	var dst := AssetKinds.user_path(kind, slot, ext)
	var f := FileAccess.open(dst, FileAccess.WRITE)
	if f == null:
		return {"ok": false, "path": "", "reason": "could not write %s" % dst}
	f.store_buffer(bytes)
	f.close()
	_cache.erase(dst)
	return {"ok": true, "path": dst, "reason": ""}

# Take the player's file back out, whatever extension it was. The bundled one
# reappears by itself, because it was never touched.
static func remove(kind: String, slot: String) -> bool:
	var gone := false
	for ext in AssetKinds.accepts(kind):
		var p := AssetKinds.user_path(kind, slot, ext)
		if FileAccess.file_exists(p):
			DirAccess.remove_absolute(ProjectSettings.globalize_path(p))
			_cache.erase(p)
			gone = true
	return gone

# Every slot the player has filed something under, for this kind.
static func installed(kind: String) -> Array:
	var out: Array = []
	var dir := AssetKinds.user_dir(kind)
	var d := DirAccess.open(dir)
	if d == null: return out
	for f in d.get_files():
		var slot := f.get_basename()
		if not (slot in out): out.append(slot)
	out.sort()
	return out

# How much room the player's own files are taking, in bytes. Shown in the
# importer so "why is this install so big" has an answer.
static func user_bytes() -> int:
	var total := 0
	for kind in AssetKinds.kind_ids():
		var d := DirAccess.open(AssetKinds.user_dir(kind))
		if d == null: continue
		for f in d.get_files():
			var fa := FileAccess.open(AssetKinds.user_dir(kind) + "/" + f, FileAccess.READ)
			if fa != null:
				total += fa.get_length()
				fa.close()
	return total
