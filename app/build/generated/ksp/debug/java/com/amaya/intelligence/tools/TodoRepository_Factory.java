package com.amaya.intelligence.tools;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class TodoRepository_Factory implements Factory<TodoRepository> {
  @Override
  public TodoRepository get() {
    return newInstance();
  }

  public static TodoRepository_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static TodoRepository newInstance() {
    return new TodoRepository();
  }

  private static final class InstanceHolder {
    private static final TodoRepository_Factory INSTANCE = new TodoRepository_Factory();
  }
}
