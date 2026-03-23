package com.amaya.intelligence.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.amaya.intelligence.data.local.db.dao.ConversationDao
import com.amaya.intelligence.data.local.db.dao.CronJobDao
import com.amaya.intelligence.data.local.db.dao.FileDao
import com.amaya.intelligence.data.local.db.dao.FileMetadataDao
import com.amaya.intelligence.data.local.db.dao.ProjectDao
import com.amaya.intelligence.data.local.db.entity.ConversationEntity
import com.amaya.intelligence.data.local.db.entity.CronJobEntity
import com.amaya.intelligence.data.local.db.entity.FileEntity
import com.amaya.intelligence.data.local.db.entity.FileFtsEntity
import com.amaya.intelligence.data.local.db.entity.FileMetadataEntity
import com.amaya.intelligence.data.local.db.entity.ProjectEntity

/**
 * Main Room database for the AI Coding Agent.
 */
@Database(
    entities = [
        ProjectEntity::class,
        FileEntity::class,
        FileFtsEntity::class,
        FileMetadataEntity::class,
        ConversationEntity::class,
        CronJobEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun projectDao(): ProjectDao
    abstract fun fileDao(): FileDao
    abstract fun fileMetadataDao(): FileMetadataDao
    abstract fun conversationDao(): ConversationDao
    abstract fun cronJobDao(): CronJobDao
    
    companion object {
        private const val DATABASE_NAME = "Amaya_db"
        
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        /**
         * Get or create the database instance.
         * Uses double-checked locking for thread safety.
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }
        
        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
