package com.amaya.intelligence.service;

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
public final class BootReceiver_MembersInjector implements MembersInjector<BootReceiver> {
  private final Provider<CronJobRepository> cronJobRepositoryProvider;

  public BootReceiver_MembersInjector(Provider<CronJobRepository> cronJobRepositoryProvider) {
    this.cronJobRepositoryProvider = cronJobRepositoryProvider;
  }

  public static MembersInjector<BootReceiver> create(
      Provider<CronJobRepository> cronJobRepositoryProvider) {
    return new BootReceiver_MembersInjector(cronJobRepositoryProvider);
  }

  @Override
  public void injectMembers(BootReceiver instance) {
    injectCronJobRepository(instance, cronJobRepositoryProvider.get());
  }

  @InjectedFieldSignature("com.amaya.intelligence.service.BootReceiver.cronJobRepository")
  public static void injectCronJobRepository(BootReceiver instance,
      CronJobRepository cronJobRepository) {
    instance.cronJobRepository = cronJobRepository;
  }
}
