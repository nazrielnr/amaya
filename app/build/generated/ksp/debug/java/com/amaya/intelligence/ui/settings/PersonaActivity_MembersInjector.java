package com.amaya.intelligence.ui.settings;

import com.amaya.intelligence.data.remote.api.AiSettingsManager;
import com.amaya.intelligence.data.repository.PersonaRepository;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class PersonaActivity_MembersInjector implements MembersInjector<PersonaActivity> {
  private final Provider<PersonaRepository> personaRepositoryProvider;

  private final Provider<AiSettingsManager> aiSettingsManagerProvider;

  public PersonaActivity_MembersInjector(Provider<PersonaRepository> personaRepositoryProvider,
      Provider<AiSettingsManager> aiSettingsManagerProvider) {
    this.personaRepositoryProvider = personaRepositoryProvider;
    this.aiSettingsManagerProvider = aiSettingsManagerProvider;
  }

  public static MembersInjector<PersonaActivity> create(
      Provider<PersonaRepository> personaRepositoryProvider,
      Provider<AiSettingsManager> aiSettingsManagerProvider) {
    return new PersonaActivity_MembersInjector(personaRepositoryProvider, aiSettingsManagerProvider);
  }

  @Override
  public void injectMembers(PersonaActivity instance) {
    injectPersonaRepository(instance, personaRepositoryProvider.get());
    injectAiSettingsManager(instance, aiSettingsManagerProvider.get());
  }

  @InjectedFieldSignature("com.amaya.intelligence.ui.settings.PersonaActivity.personaRepository")
  public static void injectPersonaRepository(PersonaActivity instance,
      PersonaRepository personaRepository) {
    instance.personaRepository = personaRepository;
  }

  @InjectedFieldSignature("com.amaya.intelligence.ui.settings.PersonaActivity.aiSettingsManager")
  public static void injectAiSettingsManager(PersonaActivity instance,
      AiSettingsManager aiSettingsManager) {
    instance.aiSettingsManager = aiSettingsManager;
  }
}
