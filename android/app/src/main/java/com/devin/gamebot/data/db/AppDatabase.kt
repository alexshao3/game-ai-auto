package com.devin.gamebot.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.devin.gamebot.data.db.dao.RecipeDao
import com.devin.gamebot.data.db.dao.SessionDao
import com.devin.gamebot.data.db.entities.Recipe
import com.devin.gamebot.data.db.entities.RecipeStep
import com.devin.gamebot.data.db.entities.RecordedFrame
import com.devin.gamebot.data.db.entities.RecordingSession
import com.devin.gamebot.data.db.entities.SessionStatus

@Database(
    entities = [
        Recipe::class,
        RecipeStep::class,
        RecordingSession::class,
        RecordedFrame::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(AppDatabase.Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun recipeDao(): RecipeDao
    abstract fun sessionDao(): SessionDao

    class Converters {
        @TypeConverter fun fromStatus(status: SessionStatus): String = status.name
        @TypeConverter fun toStatus(value: String): SessionStatus = SessionStatus.valueOf(value)
    }

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "gamebot.db",
            ).fallbackToDestructiveMigration().build().also { instance = it }
        }
    }
}
