package com.amaya.intelligence.service;

import android.content.Context;
import androidx.work.WorkerParameters;
import com.amaya.intelligence.data.local.db.dao.ConversationDao;
import com.amaya.intelligence.data.remote.api.AiSettingsManager;
import com.amaya.intelligence.data.repository.AiRepository;
import com.amaya.intelligence.data.repository.CronJobRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation"
})
public final class ReminderWorker_Factory {
  private final Provider<ConversationDao> conversationDaoProvider;

  private final Provider<CronJobRepository> cronJobRepositoryProvider;

  private final Provider<AiRepository> aiRepositoryProvider;

  private final Provider<AiSettingsManager> aiSettingsManagerProvider;

  public ReminderWorker_Factory(Provider<ConversationDao> conversationDaoProvider,
      Provider<CronJobRepository> cronJobRepositoryProvider,
      Provider<AiRepository> aiRepositoryProvider,
      Provider<AiSettingsManager> aiSettingsManagerProvider) {
    this.conversationDaoProvider = conversationDaoProvider;
    this.cronJobRepositoryProvider = cronJobRepositoryProvider;
    this.aiRepositoryProvider = aiRepositoryProvider;
    this.aiSettingsManagerProvider = aiSettingsManagerProvider;
  }

  public ReminderWorker get(Context context, WorkerParameters params) {
    return newInstance(context, params, conversationDaoProvider.get(), cronJobRepositoryProvider.get(), aiRepositoryProvider.get(), aiSettingsManagerProvider.get());
  }

  public static ReminderWorker_Factory create(Provider<ConversationDao> conversationDaoProvider,
      Provider<CronJobRepository> cronJobRepositoryProvider,
      Provider<AiRepository> aiRepositoryProvider,
      Provider<AiSettingsManager> aiSettingsManagerProvider) {
    return new ReminderWorker_Factory(conversationDaoProvider, cronJobRepositoryProvider, aiRepositoryProvider, aiSettingsManagerProvider);
  }

  public static ReminderWorker newInstance(Context context, WorkerParameters params,
      ConversationDao conversationDao, CronJobRepository cronJobRepository,
      AiRepository aiRepository, AiSettingsManager aiSettingsManager) {
    return new ReminderWorker(context, params, conversationDao, cronJobRepository, aiRepository, aiSettingsManager);
  }
}
