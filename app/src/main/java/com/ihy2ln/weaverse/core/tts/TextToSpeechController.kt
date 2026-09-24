package com.ihy2ln.weaverse.core.tts

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Speaks text via Android system TTS, with optional OpenRouter audio file playback.
 */
@Singleton
class TextToSpeechController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var tts: TextToSpeech? = null
    private val ready = AtomicBoolean(false)
    private var mediaPlayer: MediaPlayer? = null

    fun ensureReady(onReady: ((Boolean) -> Unit)? = null) {
        if (tts != null) {
            onReady?.invoke(ready.get())
            return
        }
        tts = TextToSpeech(context) { status ->
            val ok = status == TextToSpeech.SUCCESS
            ready.set(ok)
            if (ok) {
                tts?.language = Locale.getDefault()
            }
            onReady?.invoke(ok)
        }
    }

    fun speak(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        ensureReady { ok ->
            if (!ok) return@ensureReady
            stop()
            tts?.speak(trimmed, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
        }
    }

    fun speakParagraphs(paragraphs: List<String>, onProgress: (Int) -> Unit) {
        if (paragraphs.isEmpty()) return
        ensureReady { ok ->
            if (!ok) return@ensureReady
            stop()
            tts?.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        val idx = utteranceId?.removePrefix("p-")?.toIntOrNull() ?: return
                        onProgress(idx)
                    }
                    override fun onDone(utteranceId: String?) = Unit
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) = Unit
                },
            )
            paragraphs.forEachIndexed { index, para ->
                val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                tts?.speak(para, mode, null, "p-$index")
            }
        }
    }

    fun stop() {
        tts?.stop()
        mediaPlayer?.run {
            runCatching { stop() }
            runCatching { release() }
        }
        mediaPlayer = null
    }

    suspend fun playAudioFile(file: File) = withContext(Dispatchers.Main) {
        stop()
        suspendCancellableCoroutine { cont ->
            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener {
                    start()
                    if (cont.isActive) cont.resume(Unit)
                }
                setOnErrorListener { _, _, _ ->
                    if (cont.isActive) cont.resume(Unit)
                    true
                }
                setOnCompletionListener {
                    release()
                    mediaPlayer = null
                }
                prepareAsync()
            }
            mediaPlayer = player
            cont.invokeOnCancellation {
                runCatching { player.stop() }
                runCatching { player.release() }
                mediaPlayer = null
            }
        }
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        ready.set(false)
    }
}
