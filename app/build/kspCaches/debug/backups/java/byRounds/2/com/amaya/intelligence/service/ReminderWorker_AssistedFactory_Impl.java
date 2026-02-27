package com.amaya.intelligence.service;

import android.content.Context;
import androidx.work.WorkerParameters;
import dagger.internal.DaggerGenerated;
import dagger.internal.InstanceFactory;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class ReminderWorker_AssistedFactory_Impl implements ReminderWorker_AssistedFactory {
  private final ReminderWorker_Factory delegateFactory;

  ReminderWorker_AssistedFactory_Impl(ReminderWorker_Factory delegateFactory) {
    this.delegateFactory = delegateFactory;
  }

  @Override
  public ReminderWorker create(Context p0, WorkerParameters p1) {
    return delegateFactory.get(p0, p1);
  }

  public static Provider<ReminderWorker_AssistedFactory> create(
      ReminderWorker_Factory delegateFactory) {
    return InstanceFactory.create(new ReminderWorker_AssistedFactory_Impl(delegateFactory));
  }

  public static dagger.internal.Provider<ReminderWorker_AssistedFactory> createFactoryProvider(
      ReminderWorker_Factory delegateFactory) {
    return InstanceFactory.create(new ReminderWorker_AssistedFactory_Impl(delegateFactory));
  }
}
