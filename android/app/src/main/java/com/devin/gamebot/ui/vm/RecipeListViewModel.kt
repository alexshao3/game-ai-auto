package com.devin.gamebot.ui.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devin.gamebot.GameBotApp
import com.devin.gamebot.data.db.entities.Recipe
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecipeListViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = GameBotApp.recipeRepo(app)

    val recipes: StateFlow<List<Recipe>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(recipe: Recipe) {
        viewModelScope.launch { repo.delete(recipe) }
    }
}
