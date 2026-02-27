package com.amaya.intelligence.tools;

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
public final class InvokeSubagentsTool_Factory implements Factory<InvokeSubagentsTool> {
  private final Provider<SubagentRunner> subagentRunnerProvider;

  public InvokeSubagentsTool_Factory(Provider<SubagentRunner> subagentRunnerProvider) {
    this.subagentRunnerProvider = subagentRunnerProvider;
  }

  @Override
  public InvokeSubagentsTool get() {
    return newInstance(subagentRunnerProvider.get());
  }

  public static InvokeSubagentsTool_Factory create(
      Provider<SubagentRunner> subagentRunnerProvider) {
    return new InvokeSubagentsTool_Factory(subagentRunnerProvider);
  }

  public static InvokeSubagentsTool newInstance(SubagentRunner subagentRunner) {
    return new InvokeSubagentsTool(subagentRunner);
  }
}
