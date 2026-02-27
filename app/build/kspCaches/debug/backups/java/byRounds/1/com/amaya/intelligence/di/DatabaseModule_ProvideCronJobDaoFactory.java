package com.amaya.intelligence.di;

import com.amaya.intelligence.data.local.db.AppDatabase;
import com.amaya.intelligence.data.local.db.dao.CronJobDao;
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
public final class DatabaseModule_ProvideCronJobDaoFactory implements Factory<CronJobDao> {
  private final Provider<AppDatabase> databaseProvider;

  public DatabaseModule_ProvideCronJobDaoFactory(Provider<AppDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public CronJobDao get() {
    return provideCronJobDao(databaseProvider.get());
  }

  public static DatabaseModule_ProvideCronJobDaoFactory create(
      Provider<AppDatabase> databaseProvider) {
    return new DatabaseModule_ProvideCronJobDaoFactory(databaseProvider);
  }

  public static CronJobDao provideCronJobDao(AppDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideCronJobDao(database));
  }
}
