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
public final class GeminiProvider_Factory implements Factory<GeminiProvider> {
  private final Provider<OkHttpClient> httpClientProvider;

  private final Provider<Moshi> moshiProvider;

  private final Provider<Function0<AiSettings>> settingsProvider;

  public GeminiProvider_Factory(Provider<OkHttpClient> httpClientProvider,
      Provider<Moshi> moshiProvider, Provider<Function0<AiSettings>> settingsProvider) {
    this.httpClientProvider = httpClientProvider;
    this.moshiProvider = moshiProvider;
    this.settingsProvider = settingsProvider;
  }

  @Override
  public GeminiProvider get() {
    return newInstance(httpClientProvider.get(), moshiProvider.get(), settingsProvider.get());
  }

  public static GeminiProvider_Factory create(Provider<OkHttpClient> httpClientProvider,
      Provider<Moshi> moshiProvider, Provider<Function0<AiSettings>> settingsProvider) {
    return new GeminiProvider_Factory(httpClientProvider, moshiProvider, settingsProvider);
  }

  public static GeminiProvider newInstance(OkHttpClient httpClient, Moshi moshi,
      Function0<AiSettings> settingsProvider) {
    return new GeminiProvider(httpClient, moshi, settingsProvider);
  }
}
