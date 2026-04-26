package com.devin.gamebot.data.api.models

import kotlinx.serialization.Serializable

/**
 * Wire-format models shared with the FastAPI backend. Keep these in sync with
 * the routers under `backend/app/routers/`.
 */

@Serializable
data class VisionResponse(
    val text: String,
    val json: kotlinx.serialization.json.JsonElement? = null,
)

@Serializable
data class RecipeStepDto(
    val ordinal: Int,
    val intent: String,
    val expectAfter: String? = null,
    val notes: String? = null,
    val actionHint: String = "tap",
)

@Serializable
data class RecipeDto(
    val name: String,
    val description: String? = null,
    val steps: List<RecipeStepDto>,
)

@Serializable
data class GenerateRecipeResponse(
    val recipe: RecipeDto,
    val rawText: String? = null,
)
