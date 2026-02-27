package com.amaya.intelligence.ui;

import com.amaya.intelligence.data.remote.api.AiSettingsManager;
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
public final class MainActivity_MembersInjector implements MembersInjector<MainActivity> {
  private final Provider<AiSettingsManager> aiSettingsManagerProvider;

  public MainActivity_MembersInjector(Provider<AiSettingsManager> aiSettingsManagerProvider) {
    this.aiSettingsManagerProvider = aiSettingsManagerProvider;
  }

  public static MembersInjector<MainActivity> create(
      Provider<AiSettingsManager> aiSettingsManagerProvider) {
    return new MainActivity_MembersInjector(aiSettingsManagerProvider);
  }

  @Override
  public void injectMembers(MainActivity instance) {
    injectAiSettingsManager(instance, aiSettingsManagerProvider.get());
  }

  @InjectedFieldSignature("com.amaya.intelligence.ui.MainActivity.aiSettingsManager")
  public static void injectAiSettingsManager(MainActivity instance,
      AiSettingsManager aiSettingsManager) {
    instance.aiSettingsManager = aiSettingsManager;
  }
}
