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
public final class GetFileInfoTool_Factory implements Factory<GetFileInfoTool> {
  private final Provider<CommandValidator> commandValidatorProvider;

  public GetFileInfoTool_Factory(Provider<CommandValidator> commandValidatorProvider) {
    this.commandValidatorProvider = commandValidatorProvider;
  }

  @Override
  public GetFileInfoTool get() {
    return newInstance(commandValidatorProvider.get());
  }

  public static GetFileInfoTool_Factory create(
      Provider<CommandValidator> commandValidatorProvider) {
    return new GetFileInfoTool_Factory(commandValidatorProvider);
  }

  public static GetFileInfoTool newInstance(CommandValidator commandValidator) {
    return new GetFileInfoTool(commandValidator);
  }
}
