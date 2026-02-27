package com.amaya.intelligence.data.repository;

import com.amaya.intelligence.data.local.db.dao.FileDao;
import com.amaya.intelligence.data.local.db.dao.FileMetadataDao;
import com.amaya.intelligence.data.local.db.dao.ProjectDao;
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
public final class FileIndexRepository_Factory implements Factory<FileIndexRepository> {
  private final Provider<ProjectDao> projectDaoProvider;

  private final Provider<FileDao> fileDaoProvider;

  private final Provider<FileMetadataDao> fileMetadataDaoProvider;

  public FileIndexRepository_Factory(Provider<ProjectDao> projectDaoProvider,
      Provider<FileDao> fileDaoProvider, Provider<FileMetadataDao> fileMetadataDaoProvider) {
    this.projectDaoProvider = projectDaoProvider;
    this.fileDaoProvider = fileDaoProvider;
    this.fileMetadataDaoProvider = fileMetadataDaoProvider;
  }

  @Override
  public FileIndexRepository get() {
    return newInstance(projectDaoProvider.get(), fileDaoProvider.get(), fileMetadataDaoProvider.get());
  }

  public static FileIndexRepository_Factory create(Provider<ProjectDao> projectDaoProvider,
      Provider<FileDao> fileDaoProvider, Provider<FileMetadataDao> fileMetadataDaoProvider) {
    return new FileIndexRepository_Factory(projectDaoProvider, fileDaoProvider, fileMetadataDaoProvider);
  }

  public static FileIndexRepository newInstance(ProjectDao projectDao, FileDao fileDao,
      FileMetadataDao fileMetadataDao) {
    return new FileIndexRepository(projectDao, fileDao, fileMetadataDao);
  }
}
