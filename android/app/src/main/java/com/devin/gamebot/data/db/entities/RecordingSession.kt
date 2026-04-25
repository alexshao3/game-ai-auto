package com.devin.gamebot.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A recording session produced when the user demonstrates a task. Frames are stored
 * on disk under [framesDir] (one JPEG per frame) and rows in `recorded_frames` link
 * back here. After analysis, [recipeId] points to the generated [Recipe].
 */
@Entity(tableName = "recording_sessions")
data class RecordingSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startedAtMs: Long,
    val finishedAtMs: Long? = null,
    /** Absolute path to the directory containing frame JPEGs for this session. */
    val framesDir: String,
    val status: SessionStatus = SessionStatus.RECORDING,
    val recipeId: Long? = null,
    val errorMessage: String? = null,
)

enum class SessionStatus {
    RECORDING,
    STOPPED,
    GENERATING,
    READY,
    FAILED,
}
