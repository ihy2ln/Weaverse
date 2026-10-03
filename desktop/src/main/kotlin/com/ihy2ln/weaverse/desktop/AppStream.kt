package com.ihy2ln.weaverse.desktop

import kotlinx.serialization.Serializable
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStream
import java.util.concurrent.TimeUnit

@Serializable
data class AppStreamStatus(
    val state: String,
    val detail: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val apk: String = "",
)

/** A tap, drag, key or text from the browser; positions are 0..1 of the screen. */
@Serializable
data class AppStreamInput(
    val t: String,
    val x: Double = 0.0,
    val y: Double = 0.0,
    val x2: Double = 0.0,
    val y2: Double = 0.0,
    val ms: Int = 0,
    val key: Int = 0,
    val text: String = "",
)

/**
 * The real Weaverse APK, live in the browser: Weaverse Desktop runs it on a dedicated headless
 * Android emulator on this PC, streams the screen as H.264 (the browser decodes it with
 * WebCodecs) and plays taps, drags, keys and typing back through adb. Whatever APK is newest in
 * the configured folder is installed before the app opens, so the browser always shows the
 * current build.
 */
class AppStream(private val config: DesktopConfig) {
    @Volatile private var state = AppStreamStatus("off", "Not started")
    @Volatile private var installedApk = ""
    private var emulator: Process? = null
    private var shell: Process? = null
    private var shellIn: BufferedWriter? = null
    private var recorder: Process? = null
    private val lock = Any()

    private val serial get() = "emulator-${config.streamEmulatorPort}"

    fun status(): AppStreamStatus = state

    /** Boots the emulator if needed, installs the newest APK and opens Weaverse. Runs in the background. */
    fun start() {
        synchronized(lock) {
            if (state.state == "booting" || state.state == "installing") return
            state = AppStreamStatus("booting", "Starting the Android emulator…")
        }
        Thread {
            try {
                val sdk = sdkDir() ?: error("Android SDK not found. Set androidSdk in sync-config.json.")
                if (!isBooted()) {
                    if (emulator?.isAlive != true) {
                        val exe = File(sdk, "emulator/emulator" + if (isWindows) ".exe" else "")
                        require(exe.isFile) { "No emulator at ${exe.absolutePath}" }
                        emulator = ProcessBuilder(
                            exe.absolutePath, "-avd", config.streamAvd, "-port", config.streamEmulatorPort.toString(),
                            "-no-window", "-no-audio", "-no-boot-anim", "-no-snapshot-save", "-gpu", "host",
                        ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
                    }
                    val deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(4)
                    while (!isBooted()) {
                        if (System.currentTimeMillis() > deadline) error("The emulator did not boot within 4 minutes")
                        if (emulator?.isAlive == false) error("The emulator stopped while booting (is the ${config.streamAvd} AVD set up?)")
                        Thread.sleep(2000)
                    }
                }
                installNewestApk()
                adb("shell", "monkey", "-p", config.streamPackage, "-c", "android.intent.category.LAUNCHER", "1")
                val (w, h) = screenSize()
                state = AppStreamStatus("ready", "Weaverse is running", w, h, installedApk.substringAfterLast(File.separatorChar).substringBefore("@"))
            } catch (failure: Throwable) {
                state = AppStreamStatus("error", failure.message ?: failure.javaClass.simpleName)
            }
        }.apply { isDaemon = true }.start()
    }

    fun stop() {
        synchronized(lock) {
            recorder?.destroyForcibly(); recorder = null
            shell?.destroyForcibly(); shell = null; shellIn = null
        }
        runCatching { adb("emu", "kill") }
        emulator?.destroy()
        emulator = null
        state = AppStreamStatus("off", "Stopped")
    }

    /** Copies the live screen (raw H.264) into [out] until the browser goes away. One viewer at a time. */
    fun streamVideo(out: OutputStream) {
        val process = synchronized(lock) {
            recorder?.destroyForcibly()
            ProcessBuilder(
                adbExe(), "-s", serial, "exec-out", "screenrecord", "--output-format=h264",
                "--time-limit", "0", "--bit-rate", "6000000", "-",
            ).redirectError(ProcessBuilder.Redirect.DISCARD).start().also { recorder = it }
        }
        try {
            val buffer = ByteArray(64 * 1024)
            val input = process.inputStream
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                out.flush()
            }
        } finally {
            process.destroyForcibly()
            synchronized(lock) { if (recorder === process) recorder = null }
        }
    }

    fun input(event: AppStreamInput) {
        val (w, h) = state.width.takeIf { it > 0 }?.let { it to state.height } ?: screenSize()
        fun px(v: Double, size: Int) = (v.coerceIn(0.0, 1.0) * (size - 1)).toInt()
        val command = when (event.t) {
            "tap" -> "input tap ${px(event.x, w)} ${px(event.y, h)}"
            "long" -> "input swipe ${px(event.x, w)} ${px(event.y, h)} ${px(event.x, w)} ${px(event.y, h)} 650"
            "swipe" -> "input swipe ${px(event.x, w)} ${px(event.y, h)} ${px(event.x2, w)} ${px(event.y2, h)} ${event.ms.coerceIn(60, 2000)}"
            "key" -> "input keyevent ${event.key.coerceIn(0, 400)}"
            "text" -> shellText(event.text)?.let { "input text $it" }
            else -> null
        } ?: return
        synchronized(lock) {
            if (shell?.isAlive != true) {
                shell = ProcessBuilder(adbExe(), "-s", serial, "shell")
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
                shellIn = shell!!.outputStream.bufferedWriter()
            }
            shellIn!!.apply { write(command); write("\n"); flush() }
        }
    }

    /** `input text` takes one shell word: spaces become %s, everything else is quoted. ASCII only. */
    private fun shellText(text: String): String? {
        val ascii = text.filter { it.code in 32..126 }
        if (ascii.isEmpty()) return null
        return "'" + ascii.replace(" ", "%s").replace("'", "'\\''") + "'"
    }

    private fun installNewestApk() {
        val dir = File(config.streamApkDir)
        val newest = dir.listFiles { f -> f.isFile && f.extension.equals("apk", true) }
            ?.maxByOrNull { it.lastModified() }
            ?: return
        val marker = newest.absolutePath + "@" + newest.lastModified()
        if (marker == installedApk) return
        state = AppStreamStatus("installing", "Installing ${newest.name}…")
        val result = adb("install", "-r", "-d", newest.absolutePath)
        if (!result.contains("Success")) error("Could not install ${newest.name}: ${result.takeLast(300)}")
        installedApk = marker
    }

    private fun isBooted(): Boolean = runCatching { adb("shell", "getprop", "sys.boot_completed").trim() == "1" }.getOrDefault(false)

    private fun screenSize(): Pair<Int, Int> {
        val text = runCatching { adb("shell", "wm", "size") }.getOrDefault("")
        val match = Regex("(\\d+)x(\\d+)").findAll(text).lastOrNull()
        return match?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() } ?: (720 to 1280)
    }

    private fun adb(vararg args: String): String {
        val process = ProcessBuilder(listOf(adbExe(), "-s", serial) + args).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor(3, TimeUnit.MINUTES)
        return output
    }

    private fun adbExe(): String =
        sdkDir()?.let { File(it, "platform-tools/adb" + if (isWindows) ".exe" else "") }?.takeIf(File::isFile)?.absolutePath ?: "adb"

    private fun sdkDir(): File? = listOfNotNull(
        config.androidSdk.takeIf { it.isNotBlank() },
        System.getenv("ANDROID_HOME"),
        System.getenv("ANDROID_SDK_ROOT"),
        System.getenv("LOCALAPPDATA")?.let { "$it\\Android\\Sdk" },
        "S:\\Android",
    ).map(::File).firstOrNull { File(it, "platform-tools").isDirectory }

    private companion object {
        val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
    }
}
