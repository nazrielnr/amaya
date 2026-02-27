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
public final class CreateDirectoryTool_Factory implements Factory<CreateDirectoryTool> {
  private final Provider<CommandValidator> commandValidatorProvider;

  public CreateDirectoryTool_Factory(Provider<CommandValidator> commandValidatorProvider) {
    this.commandValidatorProvider = commandValidatorProvider;
  }

  @Override
  public CreateDirectoryTool get() {
    return newInstance(commandValidatorProvider.get());
  }

  public static CreateDirectoryTool_Factory create(
      Provider<CommandValidator> commandValidatorProvider) {
    return new CreateDirectoryTool_Factory(commandValidatorProvider);
  }

  public static CreateDirectoryTool newInstance(CommandValidator commandValidator) {
    return new CreateDirectoryTool(commandValidator);
  }
}
