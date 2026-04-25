package com.devin.gamebot.ui.nav

/**
 * Top-level navigation routes. Keep these typed and constant so screens
 * (and tests) reference them without typos.
 */
object Routes {
    const val RecipeList = "recipes"
    const val Recorder = "recorder"
    const val RecipeDetail = "recipes/{recipeId}"

    fun recipeDetail(id: Long) = "recipes/$id"
}
