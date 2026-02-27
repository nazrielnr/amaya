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
public final class UpdateTodoTool_Factory implements Factory<UpdateTodoTool> {
  private final Provider<TodoRepository> todoRepositoryProvider;

  public UpdateTodoTool_Factory(Provider<TodoRepository> todoRepositoryProvider) {
    this.todoRepositoryProvider = todoRepositoryProvider;
  }

  @Override
  public UpdateTodoTool get() {
    return newInstance(todoRepositoryProvider.get());
  }

  public static UpdateTodoTool_Factory create(Provider<TodoRepository> todoRepositoryProvider) {
    return new UpdateTodoTool_Factory(todoRepositoryProvider);
  }

  public static UpdateTodoTool newInstance(TodoRepository todoRepository) {
    return new UpdateTodoTool(todoRepository);
  }
}
