package com.amaya.intelligence.di

import com.amaya.intelligence.data.repository.BrainSettingsRepository
import com.amaya.intelligence.data.repository.DataStoreBrainSettingsRepository
import com.amaya.intelligence.data.repository.FileMemoryRepository
import com.amaya.intelligence.data.repository.FilePendingProposalRepository
import com.amaya.intelligence.data.repository.FileRecommendationRepository
import com.amaya.intelligence.data.repository.FileSessionMemoryRepository
import com.amaya.intelligence.data.repository.FileSkillRepository
import com.amaya.intelligence.data.repository.MaintenanceScheduler
import com.amaya.intelligence.data.repository.ManualMaintenanceScheduler
import com.amaya.intelligence.data.repository.MemoryRepository
import com.amaya.intelligence.data.repository.PendingProposalRepository
import com.amaya.intelligence.data.repository.FilePrimedStateRepository
import com.amaya.intelligence.data.repository.PrimedStateRepository
import com.amaya.intelligence.data.repository.AndroidCapabilityRepository
import com.amaya.intelligence.data.repository.FileAndroidCapabilityRepository
import com.amaya.intelligence.data.repository.FileProjectStateRepository
import com.amaya.intelligence.data.repository.ProjectStateRepository
import com.amaya.intelligence.data.repository.RecommendationRepository
import com.amaya.intelligence.data.repository.SessionMemoryRepository
import com.amaya.intelligence.data.repository.SkillUsageLogRepository
import com.amaya.intelligence.data.repository.FileSkillUsageLogRepository
import com.amaya.intelligence.data.repository.SessionSummarizer
import com.amaya.intelligence.data.repository.SkillRepository
import com.amaya.intelligence.data.repository.DataStoreTerminalSettingsRepository
import com.amaya.intelligence.data.repository.TerminalSettingsRepository
import com.amaya.intelligence.data.repository.DeterministicSessionSummarizer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindSkillRepository(impl: FileSkillRepository): SkillRepository

    @Binds
    @Singleton
    abstract fun bindSessionMemoryRepository(impl: FileSessionMemoryRepository): SessionMemoryRepository

    @Binds
    @Singleton
    abstract fun bindMemoryRepository(impl: FileMemoryRepository): MemoryRepository

    @Binds
    @Singleton
    abstract fun bindPendingProposalRepository(impl: FilePendingProposalRepository): PendingProposalRepository

    @Binds
    @Singleton
    abstract fun bindPrimedStateRepository(impl: FilePrimedStateRepository): PrimedStateRepository

    @Binds
    @Singleton
    abstract fun bindProjectStateRepository(impl: FileProjectStateRepository): ProjectStateRepository

    @Binds
    @Singleton
    abstract fun bindAndroidCapabilityRepository(impl: FileAndroidCapabilityRepository): AndroidCapabilityRepository

    @Binds
    @Singleton
    abstract fun bindRecommendationRepository(impl: FileRecommendationRepository): RecommendationRepository

    @Binds
    @Singleton
    abstract fun bindSkillUsageLogRepository(impl: FileSkillUsageLogRepository): SkillUsageLogRepository

    @Binds
    @Singleton
    abstract fun bindBrainSettingsRepository(impl: DataStoreBrainSettingsRepository): BrainSettingsRepository

    @Binds
    @Singleton
    abstract fun bindTerminalSettingsRepository(impl: DataStoreTerminalSettingsRepository): TerminalSettingsRepository

    @Binds
    @Singleton
    abstract fun bindSessionSummarizer(impl: DeterministicSessionSummarizer): SessionSummarizer

    @Binds
    @Singleton
    abstract fun bindMaintenanceScheduler(impl: ManualMaintenanceScheduler): MaintenanceScheduler

    @Binds
    @Singleton
    abstract fun bindAgentToolRegistry(impl: com.amaya.intelligence.tools.DefaultAgentToolRegistry): com.amaya.intelligence.tools.AgentToolRegistry
}
