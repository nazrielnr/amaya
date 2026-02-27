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
public final class AgentsActivity_MembersInjector implements MembersInjector<AgentsActivity> {
  private final Provider<AiSettingsManager> aiSettingsManagerProvider;

  public AgentsActivity_MembersInjector(Provider<AiSettingsManager> aiSettingsManagerProvider) {
    this.aiSettingsManagerProvider = aiSettingsManagerProvider;
  }

  public static MembersInjector<AgentsActivity> create(
      Provider<AiSettingsManager> aiSettingsManagerProvider) {
    return new AgentsActivity_MembersInjector(aiSettingsManagerProvider);
  }

  @Override
  public void injectMembers(AgentsActivity instance) {
    injectAiSettingsManager(instance, aiSettingsManagerProvider.get());
  }

  @InjectedFieldSignature("com.amaya.intelligence.ui.settings.AgentsActivity.aiSettingsManager")
  public static void injectAiSettingsManager(AgentsActivity instance,
      AiSettingsManager aiSettingsManager) {
    instance.aiSettingsManager = aiSettingsManager;
  }
}
