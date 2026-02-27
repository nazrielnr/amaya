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
public final class McpActivity_MembersInjector implements MembersInjector<McpActivity> {
  private final Provider<AiSettingsManager> aiSettingsManagerProvider;

  public McpActivity_MembersInjector(Provider<AiSettingsManager> aiSettingsManagerProvider) {
    this.aiSettingsManagerProvider = aiSettingsManagerProvider;
  }

  public static MembersInjector<McpActivity> create(
      Provider<AiSettingsManager> aiSettingsManagerProvider) {
    return new McpActivity_MembersInjector(aiSettingsManagerProvider);
  }

  @Override
  public void injectMembers(McpActivity instance) {
    injectAiSettingsManager(instance, aiSettingsManagerProvider.get());
  }

  @InjectedFieldSignature("com.amaya.intelligence.ui.settings.McpActivity.aiSettingsManager")
  public static void injectAiSettingsManager(McpActivity instance,
      AiSettingsManager aiSettingsManager) {
    instance.aiSettingsManager = aiSettingsManager;
  }
}
