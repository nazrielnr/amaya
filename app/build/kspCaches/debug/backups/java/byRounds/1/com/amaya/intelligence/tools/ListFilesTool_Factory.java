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
public final class ListFilesTool_Factory implements Factory<ListFilesTool> {
  private final Provider<CommandValidator> commandValidatorProvider;

  public ListFilesTool_Factory(Provider<CommandValidator> commandValidatorProvider) {
    this.commandValidatorProvider = commandValidatorProvider;
  }

  @Override
  public ListFilesTool get() {
    return newInstance(commandValidatorProvider.get());
  }

  public static ListFilesTool_Factory create(Provider<CommandValidator> commandValidatorProvider) {
    return new ListFilesTool_Factory(commandValidatorProvider);
  }

  public static ListFilesTool newInstance(CommandValidator commandValidator) {
    return new ListFilesTool(commandValidator);
  }
}
