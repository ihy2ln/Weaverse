package com.ihy2ln.weaverse.ai.providers

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line

/**
 * Anthropic, OpenAI-compatible, and Gemini all stream `data: {...}` lines
 * over a plain `text/event-stream` body — this reads just that part of SSE
 * (no `event:`/`id:`/retry: handling, none of these APIs need it) so each
 * provider only has to parse its own JSON payload shape.
 */
internal suspend fun ByteReadChannel.forEachSseDataLine(onData: suspend (String) -> Unit) {
    while (!isClosedForRead) {
        val line = readUTF8Line() ?: break
        if (!line.startsWith("data:")) continue
        val data = line.removePrefix("data:").trim()
        if (data.isEmpty() || data == "[DONE]") continue
        onData(data)
    }
}
