package com.devin.gamebot.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A reusable, intent-based task description generated from one user demonstration.
 *
 * The actual ordered steps live in [RecipeStep] rows referencing [id]. Coordinates
 * are intentionally NOT stored here — recipes are intent-based so the executor can
 * adapt to UI changes by re-grounding intents against the current screen via VLM.
 */
@Entity(tableName = "recipes")
data class Recipe(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String? = null,
    /** Source recording session id, kept for traceability and re-generation. */
    val sourceSessionId: Long? = null,
    /** Optional package name of the game/app this recipe targets, for filtering. */
    val gamePackage: String? = null,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
)
