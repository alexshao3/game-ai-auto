package com.devin.gamebot.data.repo

import com.devin.gamebot.data.db.AppDatabase
import com.devin.gamebot.data.db.entities.RecordedFrame
import com.devin.gamebot.data.db.entities.RecordingSession
import com.devin.gamebot.data.db.entities.SessionStatus
import java.io.File

class RecordingRepository(private val db: AppDatabase) {

    suspend fun startSession(name: String, framesDir: File): Long {
        framesDir.mkdirs()
        val now = System.currentTimeMillis()
        val session = RecordingSession(
            name = name,
            startedAtMs = now,
            framesDir = framesDir.absolutePath,
            status = SessionStatus.RECORDING,
        )
        return db.sessionDao().insertSession(session)
    }

    suspend fun finishSession(sessionId: Long) {
        val s = db.sessionDao().findById(sessionId) ?: return
        db.sessionDao().updateSession(
            s.copy(finishedAtMs = System.currentTimeMillis(), status = SessionStatus.STOPPED)
        )
    }

    suspend fun setStatus(sessionId: Long, status: SessionStatus, error: String? = null) {
        val s = db.sessionDao().findById(sessionId) ?: return
        db.sessionDao().updateSession(s.copy(status = status, errorMessage = error))
    }

    suspend fun attachRecipeId(sessionId: Long, recipeId: Long) {
        val s = db.sessionDao().findById(sessionId) ?: return
        db.sessionDao().updateSession(s.copy(recipeId = recipeId, status = SessionStatus.READY))
    }

    suspend fun saveFrame(sessionId: Long, ordinal: Int, tOffsetMs: Long, file: File) {
        db.sessionDao().insertFrame(
            RecordedFrame(
                sessionId = sessionId,
                tOffsetMs = tOffsetMs,
                ordinal = ordinal,
                path = file.absolutePath,
            )
        )
    }

    suspend fun frames(sessionId: Long): List<RecordedFrame> =
        db.sessionDao().getFramesForSession(sessionId)

    suspend fun getSession(sessionId: Long): RecordingSession? =
        db.sessionDao().findById(sessionId)
}
