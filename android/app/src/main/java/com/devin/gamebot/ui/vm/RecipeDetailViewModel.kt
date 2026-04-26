package com.devin.gamebot.ui.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devin.gamebot.GameBotApp
import com.devin.gamebot.data.db.entities.Recipe
import com.devin.gamebot.data.db.entities.RecipeStep
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecipeDetailViewModel(app: Application, private val recipeId: Long) : AndroidViewModel(app) {

    private val repo = GameBotApp.recipeRepo(app)

    private val _recipe = MutableStateFlow<Recipe?>(null)
    val recipe: StateFlow<Recipe?> = _recipe.asStateFlow()

    val steps: StateFlow<List<RecipeStep>> = repo.observeSteps(recipeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Cancels any in-flight rename DB write so a fast typist never has an
     *  older write commit *after* a newer one and revert the visible state. */
    private var renameJob: Job? = null

    init {
        viewModelScope.launch { _recipe.value = repo.get(recipeId) }
    }

    /**
     * Update the visible name immediately (optimistic) and debounce the DB
     * write. If the user keeps typing, the previous coroutine is cancelled
     * before its DB call completes, so only the latest value is persisted.
     */
    fun rename(newName: String) {
        val current = _recipe.value ?: return
        // Optimistic update — UI never reverts because the StateFlow is
        // updated synchronously, before any suspend points.
        _recipe.value = current.copy(name = newName)
        renameJob?.cancel()
        renameJob = viewModelScope.launch {
            delay(RENAME_DEBOUNCE_MS)
            repo.rename(current, newName)
        }
    }

    fun saveSteps(updated: List<RecipeStep>) {
        viewModelScope.launch {
            repo.replaceSteps(recipeId, updated)
        }
    }

    private companion object {
        const val RENAME_DEBOUNCE_MS = 400L
    }
}
