package com.amaya.intelligence;

import androidx.hilt.work.HiltWorkerFactory;
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
public final class AmayaApplication_MembersInjector implements MembersInjector<AmayaApplication> {
  private final Provider<HiltWorkerFactory> workerFactoryProvider;

  public AmayaApplication_MembersInjector(Provider<HiltWorkerFactory> workerFactoryProvider) {
    this.workerFactoryProvider = workerFactoryProvider;
  }

  public static MembersInjector<AmayaApplication> create(
      Provider<HiltWorkerFactory> workerFactoryProvider) {
    return new AmayaApplication_MembersInjector(workerFactoryProvider);
  }

  @Override
  public void injectMembers(AmayaApplication instance) {
    injectWorkerFactory(instance, workerFactoryProvider.get());
  }

  @InjectedFieldSignature("com.amaya.intelligence.AmayaApplication.workerFactory")
  public static void injectWorkerFactory(AmayaApplication instance,
      HiltWorkerFactory workerFactory) {
    instance.workerFactory = workerFactory;
  }
}
