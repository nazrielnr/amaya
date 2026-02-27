package com.amaya.intelligence.data.remote.api;

import com.squareup.moshi.Moshi;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import kotlin.jvm.functions.Function0;
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
public final class AnthropicProvider_Factory implements Factory<AnthropicProvider> {
  private final Provider<OkHttpClient> httpClientProvider;

  private final Provider<Moshi> moshiProvider;

  private final Provider<Function0<AiSettings>> settingsProvider;

  public AnthropicProvider_Factory(Provider<OkHttpClient> httpClientProvider,
      Provider<Moshi> moshiProvider, Provider<Function0<AiSettings>> settingsProvider) {
    this.httpClientProvider = httpClientProvider;
    this.moshiProvider = moshiProvider;
    this.settingsProvider = settingsProvider;
  }

  @Override
  public AnthropicProvider get() {
    return newInstance(httpClientProvider.get(), moshiProvider.get(), settingsProvider.get());
  }

  public static AnthropicProvider_Factory create(Provider<OkHttpClient> httpClientProvider,
      Provider<Moshi> moshiProvider, Provider<Function0<AiSettings>> settingsProvider) {
    return new AnthropicProvider_Factory(httpClientProvider, moshiProvider, settingsProvider);
  }

  public static AnthropicProvider newInstance(OkHttpClient httpClient, Moshi moshi,
      Function0<AiSettings> settingsProvider) {
    return new AnthropicProvider(httpClient, moshi, settingsProvider);
  }
}
