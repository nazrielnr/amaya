package com.amaya.intelligence.data.repository;

import android.content.Context;
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
public final class PersonaRepository_Factory implements Factory<PersonaRepository> {
  private final Provider<Context> contextProvider;

  public PersonaRepository_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public PersonaRepository get() {
    return newInstance(contextProvider.get());
  }

  public static PersonaRepository_Factory create(Provider<Context> contextProvider) {
    return new PersonaRepository_Factory(contextProvider);
  }

  public static PersonaRepository newInstance(Context context) {
    return new PersonaRepository(context);
  }
}
