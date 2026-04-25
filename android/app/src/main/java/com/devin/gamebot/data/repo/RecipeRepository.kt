package com.devin.gamebot.data.repo

import com.devin.gamebot.data.db.AppDatabase
import com.devin.gamebot.data.db.entities.Recipe
import com.devin.gamebot.data.db.entities.RecipeStep
import kotlinx.coroutines.flow.Flow

class RecipeRepository(private val db: AppDatabase) {

    fun observeAll(): Flow<List<Recipe>> = db.recipeDao().observeAll()

    fun observeSteps(recipeId: Long): Flow<List<RecipeStep>> =
        db.recipeDao().observeStepsForRecipe(recipeId)

    suspend fun get(recipeId: Long): Recipe? = db.recipeDao().findById(recipeId)

    suspend fun getSteps(recipeId: Long): List<RecipeStep> =
        db.recipeDao().getStepsForRecipe(recipeId)

    /** Inserts a recipe + its steps in one transaction. Returns the new recipeId. */
    suspend fun insertRecipeWithSteps(recipe: Recipe, steps: List<RecipeStep>): Long {
        val id = db.recipeDao().insertRecipe(recipe)
        db.recipeDao().insertSteps(steps.map { it.copy(recipeId = id) })
        return id
    }

    suspend fun replaceSteps(recipeId: Long, steps: List<RecipeStep>) {
        db.recipeDao().replaceRecipeSteps(recipeId, steps)
        db.recipeDao().updateRecipe(
            db.recipeDao().findById(recipeId)!!.copy(updatedAtMs = System.currentTimeMillis())
        )
    }

    suspend fun rename(recipe: Recipe, newName: String) {
        db.recipeDao().updateRecipe(
            recipe.copy(name = newName, updatedAtMs = System.currentTimeMillis())
        )
    }

    suspend fun delete(recipe: Recipe) {
        db.recipeDao().deleteRecipe(recipe)
    }
}
