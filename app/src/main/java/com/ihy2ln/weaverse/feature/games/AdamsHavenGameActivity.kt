package com.ihy2ln.weaverse.feature.games

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import org.godotengine.godot.GodotActivity
import java.io.File
import java.util.zip.ZipFile

/**
 * Runs the Adams Haven Godot game — the same engine and the same exported project as the
 * standalone APK — from the pack [GamePackStore] installed.
 *
 * Declared in its own `:game` process in the manifest. Godot's native engine can start only
 * once per process and force-quits the process on exit, so keeping it out of the main process
 * means leaving the game returns to Weaverse instead of closing it, and a crash in the game
 * never takes an unsaved chapter with it.
 *
 * Official Godot runtimes refuse `--main-pack` from outside the APK, so Godot starts the small
 * boot project in the APK's assets (`project.binary` + `weaverse-game/boot.gd`). That project
 * reads its settings override from `user://weaverse-game/override.cfg`, which is written here
 * before the engine starts: the game's own settings from the pack, plus where the pack is.
 * The boot autoload then mounts the pack before the game's autoloads load.
 */
class AdamsHavenGameActivity : GodotActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val ready = runCatching { writeOverride() }.isSuccess
        super.onCreate(savedInstanceState)
        if (!ready) {
            Toast.makeText(this, "Adams Haven's game data is missing. Import the game pack in Games.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun getCommandLine(): MutableList<String> {
        val args = super.getCommandLine().toMutableList()
        // The standalone export bakes these into assets/_cl_; the boot project has no such file.
        args += listOf(
            "--xr_mode_regular",
            "--xr-mode", "off",
            "--fullscreen",
            "--background_color", "#000000",
        )
        // The standalone build renders with Vulkan (the Mobile renderer), and so does this on a
        // phone. x86 emulators such as MuMu start Vulkan but cannot present its frames — the
        // standalone APK is a black screen there too — so they get Godot's OpenGL renderer.
        if (Build.SUPPORTED_ABIS.firstOrNull()?.startsWith("x86") == true) {
            args += listOf("--rendering-driver", "opengl3")
        }
        return args
    }

    /** Godot's `user://` on Android is [getFilesDir]. */
    private fun writeOverride() {
        val pack = GamePackStore.packFile(this)
        val settings = ZipFile(pack).use { zip ->
            val entry = zip.getEntry(GamePackStore.OVERRIDE_ENTRY) ?: error("pack has no ${GamePackStore.OVERRIDE_ENTRY}")
            zip.getInputStream(entry).bufferedReader().readText()
        }
        val target = File(filesDir, GamePackStore.OVERRIDE_ENTRY)
        target.parentFile?.mkdirs()
        val path = pack.absolutePath.replace("\\", "\\\\").replace("\"", "\\\"")
        target.writeText(settings.trimEnd() + "\n\n[weaverse]\n\npack_path=\"$path\"\n")
    }
}
