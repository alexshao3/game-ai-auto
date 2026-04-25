package com.devin.gamebot.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recorded_frames",
    foreignKeys = [
        ForeignKey(
            entity = RecordingSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class RecordedFrame(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    /** Wall-clock millis since session start. */
    val tOffsetMs: Long,
    /** Zero-based frame number in the session. */
    val ordinal: Int,
    /** Absolute path to the JPEG file for this frame. */
    val path: String,
)
