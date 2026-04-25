package com.devin.gamebot.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.devin.gamebot.data.db.entities.Recipe
import com.devin.gamebot.data.db.entities.RecipeStep
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {

    @Query("SELECT * FROM recipes ORDER BY updatedAtMs DESC")
    fun observeAll(): Flow<List<Recipe>>

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun findById(id: Long): Recipe?

    @Query("SELECT * FROM recipe_steps WHERE recipeId = :recipeId ORDER BY ordinal ASC")
    fun observeStepsForRecipe(recipeId: Long): Flow<List<RecipeStep>>

    @Query("SELECT * FROM recipe_steps WHERE recipeId = :recipeId ORDER BY ordinal ASC")
    suspend fun getStepsForRecipe(recipeId: Long): List<RecipeStep>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipe(recipe: Recipe): Long

    @Update
    suspend fun updateRecipe(recipe: Recipe)

    @Delete
    suspend fun deleteRecipe(recipe: Recipe)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSteps(steps: List<RecipeStep>)

    @Query("DELETE FROM recipe_steps WHERE recipeId = :recipeId")
    suspend fun deleteStepsForRecipe(recipeId: Long)

    @Transaction
    suspend fun replaceRecipeSteps(recipeId: Long, steps: List<RecipeStep>) {
        deleteStepsForRecipe(recipeId)
        insertSteps(steps.map { it.copy(recipeId = recipeId) })
    }
}
