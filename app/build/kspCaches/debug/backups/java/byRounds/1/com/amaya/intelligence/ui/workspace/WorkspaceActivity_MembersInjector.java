package com.amaya.intelligence.ui.workspace;

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
public final class WorkspaceActivity_MembersInjector implements MembersInjector<WorkspaceActivity> {
  private final Provider<AiSettingsManager> aiSettingsManagerProvider;

  public WorkspaceActivity_MembersInjector(Provider<AiSettingsManager> aiSettingsManagerProvider) {
    this.aiSettingsManagerProvider = aiSettingsManagerProvider;
  }

  public static MembersInjector<WorkspaceActivity> create(
      Provider<AiSettingsManager> aiSettingsManagerProvider) {
    return new WorkspaceActivity_MembersInjector(aiSettingsManagerProvider);
  }

  @Override
  public void injectMembers(WorkspaceActivity instance) {
    injectAiSettingsManager(instance, aiSettingsManagerProvider.get());
  }

  @InjectedFieldSignature("com.amaya.intelligence.ui.workspace.WorkspaceActivity.aiSettingsManager")
  public static void injectAiSettingsManager(WorkspaceActivity instance,
      AiSettingsManager aiSettingsManager) {
    instance.aiSettingsManager = aiSettingsManager;
  }
}
