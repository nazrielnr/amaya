package com.amaya.intelligence.tools;

import android.content.Context;
import com.amaya.intelligence.domain.security.CommandValidator;
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
public final class WriteFileTool_Factory implements Factory<WriteFileTool> {
  private final Provider<CommandValidator> commandValidatorProvider;

  private final Provider<Context> contextProvider;

  public WriteFileTool_Factory(Provider<CommandValidator> commandValidatorProvider,
      Provider<Context> contextProvider) {
    this.commandValidatorProvider = commandValidatorProvider;
    this.contextProvider = contextProvider;
  }

  @Override
  public WriteFileTool get() {
    return newInstance(commandValidatorProvider.get(), contextProvider.get());
  }

  public static WriteFileTool_Factory create(Provider<CommandValidator> commandValidatorProvider,
      Provider<Context> contextProvider) {
    return new WriteFileTool_Factory(commandValidatorProvider, contextProvider);
  }

  public static WriteFileTool newInstance(CommandValidator commandValidator, Context context) {
    return new WriteFileTool(commandValidator, context);
  }
}
