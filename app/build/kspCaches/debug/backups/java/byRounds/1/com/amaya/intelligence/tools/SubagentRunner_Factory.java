package com.amaya.intelligence.tools;

import com.amaya.intelligence.data.remote.api.AiSettingsManager;
import com.amaya.intelligence.data.remote.api.AnthropicProvider;
import com.amaya.intelligence.data.remote.api.GeminiProvider;
import com.amaya.intelligence.data.remote.api.OpenAiProvider;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
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
public final class SubagentRunner_Factory implements Factory<SubagentRunner> {
  private final Provider<AnthropicProvider> anthropicProvider;

  private final Provider<OpenAiProvider> openAiProvider;

  private final Provider<GeminiProvider> geminiProvider;

  private final Provider<AiSettingsManager> settingsManagerProvider;

  private final Provider<ToolExecutor> toolExecutorProvider;

  public SubagentRunner_Factory(Provider<AnthropicProvider> anthropicProvider,
      Provider<OpenAiProvider> openAiProvider, Provider<GeminiProvider> geminiProvider,
      Provider<AiSettingsManager> settingsManagerProvider,
      Provider<ToolExecutor> toolExecutorProvider) {
    this.anthropicProvider = anthropicProvider;
    this.openAiProvider = openAiProvider;
    this.geminiProvider = geminiProvider;
    this.settingsManagerProvider = settingsManagerProvider;
    this.toolExecutorProvider = toolExecutorProvider;
  }

  @Override
  public SubagentRunner get() {
    return newInstance(anthropicProvider.get(), openAiProvider.get(), geminiProvider.get(), settingsManagerProvider.get(), toolExecutorProvider);
  }

  public static SubagentRunner_Factory create(Provider<AnthropicProvider> anthropicProvider,
      Provider<OpenAiProvider> openAiProvider, Provider<GeminiProvider> geminiProvider,
      Provider<AiSettingsManager> settingsManagerProvider,
      Provider<ToolExecutor> toolExecutorProvider) {
    return new SubagentRunner_Factory(anthropicProvider, openAiProvider, geminiProvider, settingsManagerProvider, toolExecutorProvider);
  }

  public static SubagentRunner newInstance(AnthropicProvider anthropicProvider,
      OpenAiProvider openAiProvider, GeminiProvider geminiProvider,
      AiSettingsManager settingsManager, Provider<ToolExecutor> toolExecutorProvider) {
    return new SubagentRunner(anthropicProvider, openAiProvider, geminiProvider, settingsManager, toolExecutorProvider);
  }
}
