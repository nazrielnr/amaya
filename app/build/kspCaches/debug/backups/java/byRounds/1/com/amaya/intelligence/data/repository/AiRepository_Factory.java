package com.amaya.intelligence.data.repository;

import com.amaya.intelligence.data.remote.api.AiSettingsManager;
import com.amaya.intelligence.data.remote.api.AnthropicProvider;
import com.amaya.intelligence.data.remote.api.GeminiProvider;
import com.amaya.intelligence.data.remote.api.OpenAiProvider;
import com.amaya.intelligence.data.remote.mcp.McpClientManager;
import com.amaya.intelligence.data.remote.mcp.McpToolExecutor;
import com.amaya.intelligence.tools.ToolExecutor;
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
public final class AiRepository_Factory implements Factory<AiRepository> {
  private final Provider<AnthropicProvider> anthropicProvider;

  private final Provider<OpenAiProvider> openAiProvider;

  private final Provider<GeminiProvider> geminiProvider;

  private final Provider<AiSettingsManager> settingsManagerProvider;

  private final Provider<ToolExecutor> toolExecutorProvider;

  private final Provider<McpToolExecutor> mcpToolExecutorProvider;

  private final Provider<FileIndexRepository> fileIndexRepositoryProvider;

  private final Provider<PersonaRepository> personaRepositoryProvider;

  private final Provider<McpClientManager> mcpClientManagerProvider;

  public AiRepository_Factory(Provider<AnthropicProvider> anthropicProvider,
      Provider<OpenAiProvider> openAiProvider, Provider<GeminiProvider> geminiProvider,
      Provider<AiSettingsManager> settingsManagerProvider,
      Provider<ToolExecutor> toolExecutorProvider,
      Provider<McpToolExecutor> mcpToolExecutorProvider,
      Provider<FileIndexRepository> fileIndexRepositoryProvider,
      Provider<PersonaRepository> personaRepositoryProvider,
      Provider<McpClientManager> mcpClientManagerProvider) {
    this.anthropicProvider = anthropicProvider;
    this.openAiProvider = openAiProvider;
    this.geminiProvider = geminiProvider;
    this.settingsManagerProvider = settingsManagerProvider;
    this.toolExecutorProvider = toolExecutorProvider;
    this.mcpToolExecutorProvider = mcpToolExecutorProvider;
    this.fileIndexRepositoryProvider = fileIndexRepositoryProvider;
    this.personaRepositoryProvider = personaRepositoryProvider;
    this.mcpClientManagerProvider = mcpClientManagerProvider;
  }

  @Override
  public AiRepository get() {
    return newInstance(anthropicProvider.get(), openAiProvider.get(), geminiProvider.get(), settingsManagerProvider.get(), toolExecutorProvider.get(), mcpToolExecutorProvider.get(), fileIndexRepositoryProvider.get(), personaRepositoryProvider.get(), mcpClientManagerProvider.get());
  }

  public static AiRepository_Factory create(Provider<AnthropicProvider> anthropicProvider,
      Provider<OpenAiProvider> openAiProvider, Provider<GeminiProvider> geminiProvider,
      Provider<AiSettingsManager> settingsManagerProvider,
      Provider<ToolExecutor> toolExecutorProvider,
      Provider<McpToolExecutor> mcpToolExecutorProvider,
      Provider<FileIndexRepository> fileIndexRepositoryProvider,
      Provider<PersonaRepository> personaRepositoryProvider,
      Provider<McpClientManager> mcpClientManagerProvider) {
    return new AiRepository_Factory(anthropicProvider, openAiProvider, geminiProvider, settingsManagerProvider, toolExecutorProvider, mcpToolExecutorProvider, fileIndexRepositoryProvider, personaRepositoryProvider, mcpClientManagerProvider);
  }

  public static AiRepository newInstance(AnthropicProvider anthropicProvider,
      OpenAiProvider openAiProvider, GeminiProvider geminiProvider,
      AiSettingsManager settingsManager, ToolExecutor toolExecutor, McpToolExecutor mcpToolExecutor,
      FileIndexRepository fileIndexRepository, PersonaRepository personaRepository,
      McpClientManager mcpClientManager) {
    return new AiRepository(anthropicProvider, openAiProvider, geminiProvider, settingsManager, toolExecutor, mcpToolExecutor, fileIndexRepository, personaRepository, mcpClientManager);
  }
}
