package com.opencode.mobile.di

import android.content.Context
import com.opencode.mobile.data.local.db.AppDatabase
import com.opencode.mobile.data.local.db.dao.ConversationDao
import com.opencode.mobile.data.local.db.dao.FileDao
import com.opencode.mobile.data.local.db.dao.FileMetadataDao
import com.opencode.mobile.data.local.db.dao.ProjectDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for database dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return AppDatabase.getInstance(context)
    }
    
    @Provides
    fun provideProjectDao(database: AppDatabase): ProjectDao {
        return database.projectDao()
    }
    
    @Provides
    fun provideFileDao(database: AppDatabase): FileDao {
        return database.fileDao()
    }
    
    @Provides
    fun provideFileMetadataDao(database: AppDatabase): FileMetadataDao {
        return database.fileMetadataDao()
    }
    
    @Provides
    fun provideConversationDao(database: AppDatabase): ConversationDao {
        return database.conversationDao()
    }
}
