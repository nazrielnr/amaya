package com.amaya.intelligence.ui.settings;

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
public final class SettingsActivity_MembersInjector implements MembersInjector<SettingsActivity> {
  private final Provider<AiSettingsManager> aiSettingsManagerProvider;

  public SettingsActivity_MembersInjector(Provider<AiSettingsManager> aiSettingsManagerProvider) {
    this.aiSettingsManagerProvider = aiSettingsManagerProvider;
  }

  public static MembersInjector<SettingsActivity> create(
      Provider<AiSettingsManager> aiSettingsManagerProvider) {
    return new SettingsActivity_MembersInjector(aiSettingsManagerProvider);
  }

  @Override
  public void injectMembers(SettingsActivity instance) {
    injectAiSettingsManager(instance, aiSettingsManagerProvider.get());
  }

  @InjectedFieldSignature("com.amaya.intelligence.ui.settings.SettingsActivity.aiSettingsManager")
  public static void injectAiSettingsManager(SettingsActivity instance,
      AiSettingsManager aiSettingsManager) {
    instance.aiSettingsManager = aiSettingsManager;
  }
}
