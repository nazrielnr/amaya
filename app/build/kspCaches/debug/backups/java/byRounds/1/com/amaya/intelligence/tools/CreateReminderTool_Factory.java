package com.amaya.intelligence.tools;

import android.content.Context;
import com.amaya.intelligence.data.repository.CronJobRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class CreateReminderTool_Factory implements Factory<CreateReminderTool> {
  private final Provider<Context> contextProvider;

  private final Provider<CronJobRepository> cronJobRepositoryProvider;

  public CreateReminderTool_Factory(Provider<Context> contextProvider,
      Provider<CronJobRepository> cronJobRepositoryProvider) {
    this.contextProvider = contextProvider;
    this.cronJobRepositoryProvider = cronJobRepositoryProvider;
  }

  @Override
  public CreateReminderTool get() {
    return newInstance(contextProvider.get(), cronJobRepositoryProvider.get());
  }

  public static CreateReminderTool_Factory create(Provider<Context> contextProvider,
      Provider<CronJobRepository> cronJobRepositoryProvider) {
    return new CreateReminderTool_Factory(contextProvider, cronJobRepositoryProvider);
  }

  public static CreateReminderTool newInstance(Context context,
      CronJobRepository cronJobRepository) {
    return new CreateReminderTool(context, cronJobRepository);
  }
}
