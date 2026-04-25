package com.devin.gamebot.ui.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devin.gamebot.GameBotApp
import com.devin.gamebot.data.db.entities.Recipe
import com.devin.gamebot.data.db.entities.RecipeStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecipeDetailViewModel(app: Application, private val recipeId: Long) : AndroidViewModel(app) {

    private val repo = GameBotApp.recipeRepo(app)

    private val _recipe = MutableStateFlow<Recipe?>(null)
    val recipe: StateFlow<Recipe?> = _recipe

    val steps: StateFlow<List<RecipeStep>> = repo.observeSteps(recipeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { _recipe.value = repo.get(recipeId) }
    }

    fun rename(newName: String) {
        val current = _recipe.value ?: return
        viewModelScope.launch {
            repo.rename(current, newName)
            _recipe.value = current.copy(name = newName)
        }
    }

    fun saveSteps(updated: List<RecipeStep>) {
        viewModelScope.launch {
            repo.replaceSteps(recipeId, updated)
        }
    }
}
