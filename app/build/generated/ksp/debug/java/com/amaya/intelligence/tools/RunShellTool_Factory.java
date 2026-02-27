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
public final class RunShellTool_Factory implements Factory<RunShellTool> {
  private final Provider<Context> contextProvider;

  private final Provider<CommandValidator> commandValidatorProvider;

  public RunShellTool_Factory(Provider<Context> contextProvider,
      Provider<CommandValidator> commandValidatorProvider) {
    this.contextProvider = contextProvider;
    this.commandValidatorProvider = commandValidatorProvider;
  }

  @Override
  public RunShellTool get() {
    return newInstance(contextProvider.get(), commandValidatorProvider.get());
  }

  public static RunShellTool_Factory create(Provider<Context> contextProvider,
      Provider<CommandValidator> commandValidatorProvider) {
    return new RunShellTool_Factory(contextProvider, commandValidatorProvider);
  }

  public static RunShellTool newInstance(Context context, CommandValidator commandValidator) {
    return new RunShellTool(context, commandValidator);
  }
}
