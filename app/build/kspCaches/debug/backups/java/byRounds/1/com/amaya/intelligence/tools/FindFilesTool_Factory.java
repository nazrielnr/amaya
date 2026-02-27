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
public final class FindFilesTool_Factory implements Factory<FindFilesTool> {
  private final Provider<CommandValidator> commandValidatorProvider;

  public FindFilesTool_Factory(Provider<CommandValidator> commandValidatorProvider) {
    this.commandValidatorProvider = commandValidatorProvider;
  }

  @Override
  public FindFilesTool get() {
    return newInstance(commandValidatorProvider.get());
  }

  public static FindFilesTool_Factory create(Provider<CommandValidator> commandValidatorProvider) {
    return new FindFilesTool_Factory(commandValidatorProvider);
  }

  public static FindFilesTool newInstance(CommandValidator commandValidator) {
    return new FindFilesTool(commandValidator);
  }
}
