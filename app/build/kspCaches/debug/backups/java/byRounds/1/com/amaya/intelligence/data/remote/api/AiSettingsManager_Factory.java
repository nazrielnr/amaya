package com.amaya.intelligence.data.remote.api;

import android.content.Context;
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
public final class AiSettingsManager_Factory implements Factory<AiSettingsManager> {
  private final Provider<Context> contextProvider;

  public AiSettingsManager_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public AiSettingsManager get() {
    return newInstance(contextProvider.get());
  }

  public static AiSettingsManager_Factory create(Provider<Context> contextProvider) {
    return new AiSettingsManager_Factory(contextProvider);
  }

  public static AiSettingsManager newInstance(Context context) {
    return new AiSettingsManager(context);
  }
}
