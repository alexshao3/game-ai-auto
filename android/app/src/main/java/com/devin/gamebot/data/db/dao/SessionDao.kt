package com.devin.gamebot.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.devin.gamebot.data.db.entities.RecordedFrame
import com.devin.gamebot.data.db.entities.RecordingSession
import com.devin.gamebot.data.db.entities.SessionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Query("SELECT * FROM recording_sessions ORDER BY startedAtMs DESC")
    fun observeAll(): Flow<List<RecordingSession>>

    @Query("SELECT * FROM recording_sessions WHERE id = :id")
    suspend fun findById(id: Long): RecordingSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: RecordingSession): Long

    @Update
    suspend fun updateSession(session: RecordingSession)

    @Query("UPDATE recording_sessions SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: SessionStatus)

    @Insert
    suspend fun insertFrame(frame: RecordedFrame): Long

    @Insert
    suspend fun insertFrames(frames: List<RecordedFrame>)

    @Query("SELECT * FROM recorded_frames WHERE sessionId = :sessionId ORDER BY ordinal ASC")
    suspend fun getFramesForSession(sessionId: Long): List<RecordedFrame>

    @Query("SELECT COUNT(*) FROM recorded_frames WHERE sessionId = :sessionId")
    suspend fun countFrames(sessionId: Long): Int
}
