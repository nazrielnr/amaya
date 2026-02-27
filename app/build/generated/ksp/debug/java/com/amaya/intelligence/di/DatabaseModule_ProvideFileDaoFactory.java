package com.amaya.intelligence.di;

import com.amaya.intelligence.data.local.db.AppDatabase;
import com.amaya.intelligence.data.local.db.dao.FileDao;
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
public final class DatabaseModule_ProvideFileDaoFactory implements Factory<FileDao> {
  private final Provider<AppDatabase> databaseProvider;

  public DatabaseModule_ProvideFileDaoFactory(Provider<AppDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public FileDao get() {
    return provideFileDao(databaseProvider.get());
  }

  public static DatabaseModule_ProvideFileDaoFactory create(
      Provider<AppDatabase> databaseProvider) {
    return new DatabaseModule_ProvideFileDaoFactory(databaseProvider);
  }

  public static FileDao provideFileDao(AppDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideFileDao(database));
  }
}
