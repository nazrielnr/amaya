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
public final class EditFileTool_Factory implements Factory<EditFileTool> {
  private final Provider<Context> contextProvider;

  private final Provider<CommandValidator> commandValidatorProvider;

  public EditFileTool_Factory(Provider<Context> contextProvider,
      Provider<CommandValidator> commandValidatorProvider) {
    this.contextProvider = contextProvider;
    this.commandValidatorProvider = commandValidatorProvider;
  }

  @Override
  public EditFileTool get() {
    return newInstance(contextProvider.get(), commandValidatorProvider.get());
  }

  public static EditFileTool_Factory create(Provider<Context> contextProvider,
      Provider<CommandValidator> commandValidatorProvider) {
    return new EditFileTool_Factory(contextProvider, commandValidatorProvider);
  }

  public static EditFileTool newInstance(Context context, CommandValidator commandValidator) {
    return new EditFileTool(context, commandValidator);
  }
}
