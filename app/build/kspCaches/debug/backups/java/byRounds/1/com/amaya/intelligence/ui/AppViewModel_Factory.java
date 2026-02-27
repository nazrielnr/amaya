package com.amaya.intelligence.ui;

import com.amaya.intelligence.data.repository.CronJobRepository;
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
public final class AppViewModel_Factory implements Factory<AppViewModel> {
  private final Provider<CronJobRepository> cronJobRepositoryProvider;

  public AppViewModel_Factory(Provider<CronJobRepository> cronJobRepositoryProvider) {
    this.cronJobRepositoryProvider = cronJobRepositoryProvider;
  }

  @Override
  public AppViewModel get() {
    return newInstance(cronJobRepositoryProvider.get());
  }

  public static AppViewModel_Factory create(Provider<CronJobRepository> cronJobRepositoryProvider) {
    return new AppViewModel_Factory(cronJobRepositoryProvider);
  }

  public static AppViewModel newInstance(CronJobRepository cronJobRepository) {
    return new AppViewModel(cronJobRepository);
  }
}
