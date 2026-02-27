package com.amaya.intelligence.data.remote.mcp;

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
public final class McpToolExecutor_Factory implements Factory<McpToolExecutor> {
  private final Provider<ToolExecutor> toolExecutorProvider;

  private final Provider<McpClientManager> mcpClientManagerProvider;

  public McpToolExecutor_Factory(Provider<ToolExecutor> toolExecutorProvider,
      Provider<McpClientManager> mcpClientManagerProvider) {
    this.toolExecutorProvider = toolExecutorProvider;
    this.mcpClientManagerProvider = mcpClientManagerProvider;
  }

  @Override
  public McpToolExecutor get() {
    return newInstance(toolExecutorProvider.get(), mcpClientManagerProvider.get());
  }

  public static McpToolExecutor_Factory create(Provider<ToolExecutor> toolExecutorProvider,
      Provider<McpClientManager> mcpClientManagerProvider) {
    return new McpToolExecutor_Factory(toolExecutorProvider, mcpClientManagerProvider);
  }

  public static McpToolExecutor newInstance(ToolExecutor toolExecutor,
      McpClientManager mcpClientManager) {
    return new McpToolExecutor(toolExecutor, mcpClientManager);
  }
}
