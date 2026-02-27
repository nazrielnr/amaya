package com.amaya.intelligence.domain.security;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class CommandValidator_Factory implements Factory<CommandValidator> {
  private final Provider<Context> contextProvider;

  public CommandValidator_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public CommandValidator get() {
    return newInstance(contextProvider.get());
  }

  public static CommandValidator_Factory create(Provider<Context> contextProvider) {
    return new CommandValidator_Factory(contextProvider);
  }

  public static CommandValidator newInstance(Context context) {
    return new CommandValidator(context);
  }
}
