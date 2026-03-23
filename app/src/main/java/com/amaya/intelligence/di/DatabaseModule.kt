package com.amaya.intelligence.di

import android.content.Context
import com.amaya.intelligence.data.local.db.AppDatabase
import com.amaya.intelligence.data.local.db.dao.ConversationDao
import com.amaya.intelligence.data.local.db.dao.CronJobDao
import com.amaya.intelligence.data.local.db.dao.FileDao
import com.amaya.intelligence.data.local.db.dao.FileMetadataDao
import com.amaya.intelligence.data.local.db.dao.ProjectDao
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
    @Singleton
    fun provideProjectDao(database: AppDatabase): ProjectDao {
        return database.projectDao()
    }

    @Provides
    @Singleton
    fun provideFileDao(database: AppDatabase): FileDao {
        return database.fileDao()
    }

    @Provides
    @Singleton
    fun provideFileMetadataDao(database: AppDatabase): FileMetadataDao {
        return database.fileMetadataDao()
    }

    @Provides
    @Singleton
    fun provideConversationDao(database: AppDatabase): ConversationDao {
        return database.conversationDao()
    }

    @Provides
    @Singleton
    fun provideCronJobDao(database: AppDatabase): CronJobDao {
        return database.cronJobDao()
    }
}
