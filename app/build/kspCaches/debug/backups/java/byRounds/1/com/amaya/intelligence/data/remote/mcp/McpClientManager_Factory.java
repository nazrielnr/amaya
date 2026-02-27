package com.amaya.intelligence.data.remote.mcp;

import com.amaya.intelligence.data.remote.api.AiSettingsManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import okhttp3.OkHttpClient;

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
public final class McpClientManager_Factory implements Factory<McpClientManager> {
  private final Provider<OkHttpClient> httpClientProvider;

  private final Provider<AiSettingsManager> settingsManagerProvider;

  public McpClientManager_Factory(Provider<OkHttpClient> httpClientProvider,
      Provider<AiSettingsManager> settingsManagerProvider) {
    this.httpClientProvider = httpClientProvider;
    this.settingsManagerProvider = settingsManagerProvider;
  }

  @Override
  public McpClientManager get() {
    return newInstance(httpClientProvider.get(), settingsManagerProvider.get());
  }

  public static McpClientManager_Factory create(Provider<OkHttpClient> httpClientProvider,
      Provider<AiSettingsManager> settingsManagerProvider) {
    return new McpClientManager_Factory(httpClientProvider, settingsManagerProvider);
  }

  public static McpClientManager newInstance(OkHttpClient httpClient,
      AiSettingsManager settingsManager) {
    return new McpClientManager(httpClient, settingsManager);
  }
}
