package com.amaya.intelligence.data.repository;

import android.content.Context;
import com.amaya.intelligence.data.local.db.dao.CronJobDao;
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
public final class CronJobRepository_Factory implements Factory<CronJobRepository> {
  private final Provider<Context> contextProvider;

  private final Provider<CronJobDao> daoProvider;

  public CronJobRepository_Factory(Provider<Context> contextProvider,
      Provider<CronJobDao> daoProvider) {
    this.contextProvider = contextProvider;
    this.daoProvider = daoProvider;
  }

  @Override
  public CronJobRepository get() {
    return newInstance(contextProvider.get(), daoProvider.get());
  }

  public static CronJobRepository_Factory create(Provider<Context> contextProvider,
      Provider<CronJobDao> daoProvider) {
    return new CronJobRepository_Factory(contextProvider, daoProvider);
  }

  public static CronJobRepository newInstance(Context context, CronJobDao dao) {
    return new CronJobRepository(context, dao);
  }
}
