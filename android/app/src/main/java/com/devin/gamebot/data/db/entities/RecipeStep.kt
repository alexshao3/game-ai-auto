package com.devin.gamebot.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One step in a [Recipe]. Stored separately so the user can edit individual steps
 * without rewriting the whole recipe.
 *
 * `intent` is the natural-language description of what the user wants done at this
 * step (e.g. "tap the 'Phần thưởng' icon at the top right"). The executor passes
 * this string + a fresh screenshot to the VLM to ground the intent into coordinates.
 *
 * `expectAfter` is the natural-language description of the screen that should appear
 * after the action; used by the executor to verify success or detect getting stuck.
 */
@Entity(
    tableName = "recipe_steps",
    foreignKeys = [
        ForeignKey(
            entity = Recipe::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recipeId")],
)
data class RecipeStep(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    /** Zero-based execution order. */
    val ordinal: Int,
    /** What the user wants to happen, in natural language. */
    val intent: String,
    /** What screen the executor expects to see after the action succeeds. */
    val expectAfter: String? = null,
    /** Optional user notes, e.g. voice annotation transcript. */
    val notes: String? = null,
    /** Recommended action verb hint for the executor: tap, swipe, longPress, wait. */
    val actionHint: String = "tap",
)
