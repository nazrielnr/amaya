package com.amaya.intelligence.di;

import com.amaya.intelligence.data.local.db.AppDatabase;
import com.amaya.intelligence.data.local.db.dao.FileMetadataDao;
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
public final class DatabaseModule_ProvideFileMetadataDaoFactory implements Factory<FileMetadataDao> {
  private final Provider<AppDatabase> databaseProvider;

  public DatabaseModule_ProvideFileMetadataDaoFactory(Provider<AppDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public FileMetadataDao get() {
    return provideFileMetadataDao(databaseProvider.get());
  }

  public static DatabaseModule_ProvideFileMetadataDaoFactory create(
      Provider<AppDatabase> databaseProvider) {
    return new DatabaseModule_ProvideFileMetadataDaoFactory(databaseProvider);
  }

  public static FileMetadataDao provideFileMetadataDao(AppDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideFileMetadataDao(database));
  }
}
