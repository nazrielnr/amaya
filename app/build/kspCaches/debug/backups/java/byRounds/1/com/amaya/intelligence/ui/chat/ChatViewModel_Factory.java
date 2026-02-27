package com.amaya.intelligence.ui.chat;

import com.amaya.intelligence.data.local.db.dao.ConversationDao;
import com.amaya.intelligence.data.remote.api.AiSettingsManager;
import com.amaya.intelligence.data.repository.AiRepository;
import com.amaya.intelligence.data.repository.CronJobRepository;
import com.amaya.intelligence.data.repository.PersonaRepository;
import com.amaya.intelligence.tools.TodoRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class ChatViewModel_Factory implements Factory<ChatViewModel> {
  private final Provider<AiRepository> aiRepositoryProvider;

  private final Provider<ConversationDao> conversationDaoProvider;

  private final Provider<AiSettingsManager> aiSettingsManagerProvider;

  private final Provider<CronJobRepository> cronJobRepositoryProvider;

  private final Provider<PersonaRepository> personaRepositoryProvider;

  private final Provider<TodoRepository> todoRepositoryProvider;

  public ChatViewModel_Factory(Provider<AiRepository> aiRepositoryProvider,
      Provider<ConversationDao> conversationDaoProvider,
      Provider<AiSettingsManager> aiSettingsManagerProvider,
      Provider<CronJobRepository> cronJobRepositoryProvider,
      Provider<PersonaRepository> personaRepositoryProvider,
      Provider<TodoRepository> todoRepositoryProvider) {
    this.aiRepositoryProvider = aiRepositoryProvider;
    this.conversationDaoProvider = conversationDaoProvider;
    this.aiSettingsManagerProvider = aiSettingsManagerProvider;
    this.cronJobRepositoryProvider = cronJobRepositoryProvider;
    this.personaRepositoryProvider = personaRepositoryProvider;
    this.todoRepositoryProvider = todoRepositoryProvider;
  }

  @Override
  public ChatViewModel get() {
    return newInstance(aiRepositoryProvider.get(), conversationDaoProvider.get(), aiSettingsManagerProvider.get(), cronJobRepositoryProvider.get(), personaRepositoryProvider.get(), todoRepositoryProvider.get());
  }

  public static ChatViewModel_Factory create(Provider<AiRepository> aiRepositoryProvider,
      Provider<ConversationDao> conversationDaoProvider,
      Provider<AiSettingsManager> aiSettingsManagerProvider,
      Provider<CronJobRepository> cronJobRepositoryProvider,
      Provider<PersonaRepository> personaRepositoryProvider,
      Provider<TodoRepository> todoRepositoryProvider) {
    return new ChatViewModel_Factory(aiRepositoryProvider, conversationDaoProvider, aiSettingsManagerProvider, cronJobRepositoryProvider, personaRepositoryProvider, todoRepositoryProvider);
  }

  public static ChatViewModel newInstance(AiRepository aiRepository,
      ConversationDao conversationDao, AiSettingsManager aiSettingsManager,
      CronJobRepository cronJobRepository, PersonaRepository personaRepository,
      TodoRepository todoRepository) {
    return new ChatViewModel(aiRepository, conversationDao, aiSettingsManager, cronJobRepository, personaRepository, todoRepository);
  }
}
