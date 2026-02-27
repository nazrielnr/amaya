package com.amaya.intelligence.tools;

import com.amaya.intelligence.domain.security.CommandValidator;
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
public final class ReadFileTool_Factory implements Factory<ReadFileTool> {
  private final Provider<CommandValidator> commandValidatorProvider;

  public ReadFileTool_Factory(Provider<CommandValidator> commandValidatorProvider) {
    this.commandValidatorProvider = commandValidatorProvider;
  }

  @Override
  public ReadFileTool get() {
    return newInstance(commandValidatorProvider.get());
  }

  public static ReadFileTool_Factory create(Provider<CommandValidator> commandValidatorProvider) {
    return new ReadFileTool_Factory(commandValidatorProvider);
  }

  public static ReadFileTool newInstance(CommandValidator commandValidator) {
    return new ReadFileTool(commandValidator);
  }
}
