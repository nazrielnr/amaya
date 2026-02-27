package com.amaya.intelligence.ui.settings;

import com.amaya.intelligence.data.repository.CronJobRepository;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class CronJobActivity_MembersInjector implements MembersInjector<CronJobActivity> {
  private final Provider<CronJobRepository> cronJobRepositoryProvider;

  public CronJobActivity_MembersInjector(Provider<CronJobRepository> cronJobRepositoryProvider) {
    this.cronJobRepositoryProvider = cronJobRepositoryProvider;
  }

  public static MembersInjector<CronJobActivity> create(
      Provider<CronJobRepository> cronJobRepositoryProvider) {
    return new CronJobActivity_MembersInjector(cronJobRepositoryProvider);
  }

  @Override
  public void injectMembers(CronJobActivity instance) {
    injectCronJobRepository(instance, cronJobRepositoryProvider.get());
  }

  @InjectedFieldSignature("com.amaya.intelligence.ui.settings.CronJobActivity.cronJobRepository")
  public static void injectCronJobRepository(CronJobActivity instance,
      CronJobRepository cronJobRepository) {
    instance.cronJobRepository = cronJobRepository;
  }
}
