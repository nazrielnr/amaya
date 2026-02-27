package com.amaya.intelligence.tools;

import android.content.Context;
import com.amaya.intelligence.data.repository.PersonaRepository;
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
public final class UpdateMemoryTool_Factory implements Factory<UpdateMemoryTool> {
  private final Provider<Context> contextProvider;

  private final Provider<PersonaRepository> personaRepositoryProvider;

  public UpdateMemoryTool_Factory(Provider<Context> contextProvider,
      Provider<PersonaRepository> personaRepositoryProvider) {
    this.contextProvider = contextProvider;
    this.personaRepositoryProvider = personaRepositoryProvider;
  }

  @Override
  public UpdateMemoryTool get() {
    return newInstance(contextProvider.get(), personaRepositoryProvider.get());
  }

  public static UpdateMemoryTool_Factory create(Provider<Context> contextProvider,
      Provider<PersonaRepository> personaRepositoryProvider) {
    return new UpdateMemoryTool_Factory(contextProvider, personaRepositoryProvider);
  }

  public static UpdateMemoryTool newInstance(Context context, PersonaRepository personaRepository) {
    return new UpdateMemoryTool(context, personaRepository);
  }
}
