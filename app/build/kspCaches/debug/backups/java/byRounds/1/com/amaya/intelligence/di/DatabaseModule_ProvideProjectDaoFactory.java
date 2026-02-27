package com.amaya.intelligence.di;

import com.amaya.intelligence.data.local.db.AppDatabase;
import com.amaya.intelligence.data.local.db.dao.ProjectDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class DatabaseModule_ProvideProjectDaoFactory implements Factory<ProjectDao> {
  private final Provider<AppDatabase> databaseProvider;

  public DatabaseModule_ProvideProjectDaoFactory(Provider<AppDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public ProjectDao get() {
    return provideProjectDao(databaseProvider.get());
  }

  public static DatabaseModule_ProvideProjectDaoFactory create(
      Provider<AppDatabase> databaseProvider) {
    return new DatabaseModule_ProvideProjectDaoFactory(databaseProvider);
  }

  public static ProjectDao provideProjectDao(AppDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideProjectDao(database));
  }
}
