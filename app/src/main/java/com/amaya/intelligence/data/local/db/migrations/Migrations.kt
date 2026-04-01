package com.amaya.intelligence.data.local.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Template for Room Migration.
 * 
 * Rules for SAFE Migration:
 * 1. Always use version numbers in the name (e.g., MIGRATION_1_2).
 * 2. Use raw SQL for transformations.
 * 3. Handle NULL vs NOT NULL carefully.
 * 4. Provide DEFAULT values for new columns.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Example: Adding a new column to projects table
        // db.execSQL("ALTER TABLE projects ADD COLUMN description TEXT DEFAULT '' NOT NULL")
        
        // Example: Creating a new table
        /*
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `new_table` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `data` TEXT NOT NULL
            )
        """)
        */
    }
}

/**
 * Migration for Version 5 to 6.
 * Standardizing column names to snake_case.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        LogMigration.d("Starting migration 5 -> 6: Renaming columns to snake_case")
        
        // 1. Conversations: workspacePath -> workspace_path, etc.
        db.execSQL("ALTER TABLE conversations RENAME COLUMN workspacePath TO workspace_path")
        db.execSQL("ALTER TABLE conversations RENAME COLUMN createdAt TO created_at")
        db.execSQL("ALTER TABLE conversations RENAME COLUMN updatedAt TO updated_at")
        db.execSQL("ALTER TABLE conversations RENAME COLUMN messagesJson TO messages_json")

        // 2. Cron Jobs: triggerTimeMillis -> trigger_time_millis, etc.
        db.execSQL("ALTER TABLE cron_jobs RENAME COLUMN triggerTimeMillis TO trigger_time_millis")
        db.execSQL("ALTER TABLE cron_jobs RENAME COLUMN recurringType TO recurring_type")
        db.execSQL("ALTER TABLE cron_jobs RENAME COLUMN isActive TO is_active")
        db.execSQL("ALTER TABLE cron_jobs RENAME COLUMN createdAt TO created_at")
        db.execSQL("ALTER TABLE cron_jobs RENAME COLUMN conversationId TO conversation_id")
        db.execSQL("ALTER TABLE cron_jobs RENAME COLUMN fireCount TO fire_count")
        db.execSQL("ALTER TABLE cron_jobs RENAME COLUMN sessionMode TO session_mode")

        LogMigration.d("Migration 5 -> 6 completed successfully")
    }
}

object LogMigration {
    fun d(message: String) {
        android.util.Log.d("RoomMigration", message)
    }
}
