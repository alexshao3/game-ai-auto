package com.devin.gamebot.ui.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devin.gamebot.GameBotApp
import com.devin.gamebot.data.db.entities.RecipeStep
import com.devin.gamebot.data.db.entities.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Drives the Recorder screen state machine:
 *
 *   Idle  -> startSession()       -> Preparing (UI prompts MediaProjection)
 *   Preparing -> sessionStarted() -> Recording (service streams frames)
 *   Recording -> stopAndAnalyse() -> Generating (frames POSTed to backend)
 *   Generating -> on success      -> Done(recipeId)
 *              -> on failure      -> Error(message)
 */
class RecorderViewModel(app: Application) : AndroidViewModel(app) {

    sealed interface State {
        data object Idle : State
        data class Preparing(val sessionId: Long, val framesDir: File, val name: String) : State
        data class Recording(val sessionId: Long, val framesDir: File, val name: String) : State
        data class Generating(val sessionId: Long, val name: String) : State
        data class Done(val recipeId: Long) : State
        data class Error(val message: String) : State
    }

    private val recordingRepo = GameBotApp.recordingRepo(app)
    private val recipeRepo = GameBotApp.recipeRepo(app)
    private val backend = GameBotApp.backendClient()

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    fun startSession(name: String, onReady: (sessionId: Long, framesDir: File) -> Unit) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val baseDir = File(context.filesDir, "recordings")
            val sessionDir = File(baseDir, "session_${System.currentTimeMillis()}")
            val sessionId = recordingRepo.startSession(name, sessionDir)
            _state.value = State.Preparing(sessionId, sessionDir, name)
            onReady(sessionId, sessionDir)
        }
    }

    fun sessionStarted() {
        val s = _state.value as? State.Preparing ?: return
        _state.value = State.Recording(s.sessionId, s.framesDir, s.name)
    }

    /** Called when the user taps Stop in the UI or via the notification. */
    fun stopAndAnalyse() {
        val s = _state.value as? State.Recording ?: return
        viewModelScope.launch {
            recordingRepo.finishSession(s.sessionId)
            _state.value = State.Generating(s.sessionId, s.name)
            recordingRepo.setStatus(s.sessionId, SessionStatus.GENERATING)
            try {
                val frames = recordingRepo.frames(s.sessionId)
                    .map { File(it.path) }
                    .filter { it.exists() }
                if (frames.isEmpty()) {
                    _state.value = State.Error("Không có frame nào được lưu")
                    recordingRepo.setStatus(s.sessionId, SessionStatus.FAILED, "no frames")
                    return@launch
                }
                val response = backend.generateRecipe(frames, s.name)
                val recipeId = recipeRepo.insertRecipeWithSteps(
                    recipe = com.devin.gamebot.data.db.entities.Recipe(
                        name = response.recipe.name.ifBlank { s.name },
                        description = response.recipe.description,
                        sourceSessionId = s.sessionId,
                    ),
                    steps = response.recipe.steps.map {
                        RecipeStep(
                            recipeId = 0, // overwritten by repository
                            ordinal = it.ordinal,
                            intent = it.intent,
                            expectAfter = it.expectAfter,
                            notes = it.notes,
                            actionHint = it.actionHint,
                        )
                    },
                )
                recordingRepo.attachRecipeId(s.sessionId, recipeId)
                _state.value = State.Done(recipeId)
            } catch (t: Throwable) {
                recordingRepo.setStatus(s.sessionId, SessionStatus.FAILED, t.message)
                _state.value = State.Error(t.message ?: "unknown error")
            }
        }
    }

    fun reset() {
        _state.value = State.Idle
    }
}
