package com.amaya.intelligence.di;

import com.amaya.intelligence.data.remote.api.AiSettingsManager;
import com.amaya.intelligence.data.remote.api.OpenAiProvider;
import com.squareup.moshi.Moshi;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class AiModule_ProvideOpenAiProviderFactory implements Factory<OpenAiProvider> {
  private final Provider<OkHttpClient> httpClientProvider;

  private final Provider<Moshi> moshiProvider;

  private final Provider<AiSettingsManager> settingsManagerProvider;

  public AiModule_ProvideOpenAiProviderFactory(Provider<OkHttpClient> httpClientProvider,
      Provider<Moshi> moshiProvider, Provider<AiSettingsManager> settingsManagerProvider) {
    this.httpClientProvider = httpClientProvider;
    this.moshiProvider = moshiProvider;
    this.settingsManagerProvider = settingsManagerProvider;
  }

  @Override
  public OpenAiProvider get() {
    return provideOpenAiProvider(httpClientProvider.get(), moshiProvider.get(), settingsManagerProvider.get());
  }

  public static AiModule_ProvideOpenAiProviderFactory create(
      Provider<OkHttpClient> httpClientProvider, Provider<Moshi> moshiProvider,
      Provider<AiSettingsManager> settingsManagerProvider) {
    return new AiModule_ProvideOpenAiProviderFactory(httpClientProvider, moshiProvider, settingsManagerProvider);
  }

  public static OpenAiProvider provideOpenAiProvider(OkHttpClient httpClient, Moshi moshi,
      AiSettingsManager settingsManager) {
    return Preconditions.checkNotNullFromProvides(AiModule.INSTANCE.provideOpenAiProvider(httpClient, moshi, settingsManager));
  }
}
