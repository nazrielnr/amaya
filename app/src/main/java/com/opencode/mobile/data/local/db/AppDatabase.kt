package com.opencode.mobile.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.opencode.mobile.data.local.db.dao.ConversationDao
import com.opencode.mobile.data.local.db.dao.FileDao
import com.opencode.mobile.data.local.db.dao.FileMetadataDao
import com.opencode.mobile.data.local.db.dao.ProjectDao
import com.opencode.mobile.data.local.db.entity.ConversationEntity
import com.opencode.mobile.data.local.db.entity.FileEntity
import com.opencode.mobile.data.local.db.entity.FileFtsEntity
import com.opencode.mobile.data.local.db.entity.FileMetadataEntity
import com.opencode.mobile.data.local.db.entity.ProjectEntity

/**
 * Main Room database for the AI Coding Agent.
 */
@Database(
    entities = [
        ProjectEntity::class,
        FileEntity::class,
        FileFtsEntity::class,
        FileMetadataEntity::class,
        ConversationEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun projectDao(): ProjectDao
    abstract fun fileDao(): FileDao
    abstract fun fileMetadataDao(): FileMetadataDao
    abstract fun conversationDao(): ConversationDao
    
    companion object {
        private const val DATABASE_NAME = "opencode_db"
        
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
