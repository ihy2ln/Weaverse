package com.ihy2ln.weaverse.feature.roleplay.chat

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MangaAiTaskState(
    val chatId: String = "",
    val action: String = "",
    val current: Int = 0,
    val total: Int = 0,
    val itemProgress: Float = 0f,
    val status: String = "",
    val running: Boolean = false,
)

/** Keeps an in-progress manga edit alive after its editor ViewModel is cleared. */
@Singleton
class MangaAiBackgroundRunner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(MangaAiTaskState())
    val state: StateFlow<MangaAiTaskState> = _state.asStateFlow()
    private var activeJob: Job? = null

    @Synchronized
    fun start(
        chatId: String,
        action: String,
        sourceState: StateFlow<RoleplayChatUiState>,
        work: suspend () -> Unit,
    ): Job? {
        if (_state.value.running) return null
        try {
            MangaAiForegroundService.start(context, action)
        } catch (error: Exception) {
            Log.e("MangaAiBackground", "Could not start foreground service", error)
            _state.value = MangaAiTaskState(chatId = chatId, status =
                "Could not start background processing: ${error.message ?: "Android refused the service"}")
            return null
        }
        _state.value = MangaAiTaskState(chatId = chatId, action = action, running = true)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val mirror = launch {
                sourceState.collect { ui ->
                    val next = MangaAiTaskState(
                        chatId = chatId,
                        action = ui.mangaEditAction.ifBlank { action },
                        current = ui.mangaEditCurrent,
                        total = ui.mangaEditTotal,
                        itemProgress = ui.mangaEditItemProgress,
                        status = ui.storyboardStatus,
                        running = true,
                    )
                    _state.value = next
                    runCatching { MangaAiForegroundService.update(context, next) }
                        .onFailure { Log.w("MangaAiBackground", "Could not update notification", it) }
                }
            }
            try {
                work()
            } finally {
                withContext(NonCancellable) {
                    mirror.cancelAndJoin()
                    val finalStatus = sourceState.value.storyboardStatus
                    MangaAiForegroundService.stop(context)
                    synchronized(this@MangaAiBackgroundRunner) {
                        activeJob = null
                        _state.update {
                            it.copy(
                                status = finalStatus.ifBlank { it.status },
                                running = false,
                                current = 0,
                                total = 0,
                                itemProgress = 0f,
                            )
                        }
                    }
                }
            }
        }
        activeJob = job
        job.start()
        return job
    }

    @Synchronized
    fun stop(chatId: String) {
        if (_state.value.chatId == chatId) activeJob?.cancel()
    }
}
