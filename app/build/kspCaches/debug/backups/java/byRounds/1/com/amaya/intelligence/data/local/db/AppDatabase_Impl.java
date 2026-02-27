package com.amaya.intelligence.data.local.db;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.FtsTableInfo;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import com.amaya.intelligence.data.local.db.dao.ConversationDao;
import com.amaya.intelligence.data.local.db.dao.ConversationDao_Impl;
import com.amaya.intelligence.data.local.db.dao.CronJobDao;
import com.amaya.intelligence.data.local.db.dao.CronJobDao_Impl;
import com.amaya.intelligence.data.local.db.dao.FileDao;
import com.amaya.intelligence.data.local.db.dao.FileDao_Impl;
import com.amaya.intelligence.data.local.db.dao.FileMetadataDao;
import com.amaya.intelligence.data.local.db.dao.FileMetadataDao_Impl;
import com.amaya.intelligence.data.local.db.dao.ProjectDao;
import com.amaya.intelligence.data.local.db.dao.ProjectDao_Impl;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDatabase_Impl extends AppDatabase {
  private volatile ProjectDao _projectDao;

  private volatile FileDao _fileDao;

  private volatile FileMetadataDao _fileMetadataDao;

  private volatile ConversationDao _conversationDao;

  private volatile CronJobDao _cronJobDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(4) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `projects` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `root_path` TEXT NOT NULL, `primary_language` TEXT, `last_scanned_at` INTEGER, `file_count` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `is_active` INTEGER NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_projects_root_path` ON `projects` (`root_path`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `files` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `project_id` INTEGER NOT NULL, `relative_path` TEXT NOT NULL, `file_name` TEXT NOT NULL, `extension` TEXT, `content_hash` TEXT NOT NULL, `size_bytes` INTEGER NOT NULL, `last_modified` INTEGER NOT NULL, `indexed_at` INTEGER NOT NULL, `is_directory` INTEGER NOT NULL, FOREIGN KEY(`project_id`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_files_project_id` ON `files` (`project_id`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_files_relative_path` ON `files` (`relative_path`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_files_content_hash` ON `files` (`content_hash`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_files_extension` ON `files` (`extension`)");
        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `files_fts` USING FTS4(`file_name` TEXT NOT NULL, `relative_path` TEXT NOT NULL, content=`files`)");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_files_fts_BEFORE_UPDATE BEFORE UPDATE ON `files` BEGIN DELETE FROM `files_fts` WHERE `docid`=OLD.`rowid`; END");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_files_fts_BEFORE_DELETE BEFORE DELETE ON `files` BEGIN DELETE FROM `files_fts` WHERE `docid`=OLD.`rowid`; END");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_files_fts_AFTER_UPDATE AFTER UPDATE ON `files` BEGIN INSERT INTO `files_fts`(`docid`, `file_name`, `relative_path`) VALUES (NEW.`rowid`, NEW.`file_name`, NEW.`relative_path`); END");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_files_fts_AFTER_INSERT AFTER INSERT ON `files` BEGIN INSERT INTO `files_fts`(`docid`, `file_name`, `relative_path`) VALUES (NEW.`rowid`, NEW.`file_name`, NEW.`relative_path`); END");
        db.execSQL("CREATE TABLE IF NOT EXISTS `file_metadata` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `file_id` INTEGER NOT NULL, `language` TEXT NOT NULL, `class_names` TEXT, `function_names` TEXT, `imports` TEXT, `loc` INTEGER, `extracted_at` INTEGER NOT NULL, FOREIGN KEY(`file_id`) REFERENCES `files`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_file_metadata_file_id` ON `file_metadata` (`file_id`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_file_metadata_language` ON `file_metadata` (`language`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `conversations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `workspacePath` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `messagesJson` TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `cron_jobs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `prompt` TEXT NOT NULL, `triggerTimeMillis` INTEGER NOT NULL, `recurringType` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `conversationId` INTEGER, `fireCount` INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'a3b261e48d6cddecc4c1b55e0004aab4')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `projects`");
        db.execSQL("DROP TABLE IF EXISTS `files`");
        db.execSQL("DROP TABLE IF EXISTS `files_fts`");
        db.execSQL("DROP TABLE IF EXISTS `file_metadata`");
        db.execSQL("DROP TABLE IF EXISTS `conversations`");
        db.execSQL("DROP TABLE IF EXISTS `cron_jobs`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        db.execSQL("PRAGMA foreign_keys = ON");
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_files_fts_BEFORE_UPDATE BEFORE UPDATE ON `files` BEGIN DELETE FROM `files_fts` WHERE `docid`=OLD.`rowid`; END");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_files_fts_BEFORE_DELETE BEFORE DELETE ON `files` BEGIN DELETE FROM `files_fts` WHERE `docid`=OLD.`rowid`; END");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_files_fts_AFTER_UPDATE AFTER UPDATE ON `files` BEGIN INSERT INTO `files_fts`(`docid`, `file_name`, `relative_path`) VALUES (NEW.`rowid`, NEW.`file_name`, NEW.`relative_path`); END");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_files_fts_AFTER_INSERT AFTER INSERT ON `files` BEGIN INSERT INTO `files_fts`(`docid`, `file_name`, `relative_path`) VALUES (NEW.`rowid`, NEW.`file_name`, NEW.`relative_path`); END");
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsProjects = new HashMap<String, TableInfo.Column>(8);
        _columnsProjects.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProjects.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProjects.put("root_path", new TableInfo.Column("root_path", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProjects.put("primary_language", new TableInfo.Column("primary_language", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProjects.put("last_scanned_at", new TableInfo.Column("last_scanned_at", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProjects.put("file_count", new TableInfo.Column("file_count", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProjects.put("created_at", new TableInfo.Column("created_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsProjects.put("is_active", new TableInfo.Column("is_active", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysProjects = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesProjects = new HashSet<TableInfo.Index>(1);
        _indicesProjects.add(new TableInfo.Index("index_projects_root_path", true, Arrays.asList("root_path"), Arrays.asList("ASC")));
        final TableInfo _infoProjects = new TableInfo("projects", _columnsProjects, _foreignKeysProjects, _indicesProjects);
        final TableInfo _existingProjects = TableInfo.read(db, "projects");
        if (!_infoProjects.equals(_existingProjects)) {
          return new RoomOpenHelper.ValidationResult(false, "projects(com.amaya.intelligence.data.local.db.entity.ProjectEntity).\n"
                  + " Expected:\n" + _infoProjects + "\n"
                  + " Found:\n" + _existingProjects);
        }
        final HashMap<String, TableInfo.Column> _columnsFiles = new HashMap<String, TableInfo.Column>(10);
        _columnsFiles.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFiles.put("project_id", new TableInfo.Column("project_id", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFiles.put("relative_path", new TableInfo.Column("relative_path", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFiles.put("file_name", new TableInfo.Column("file_name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFiles.put("extension", new TableInfo.Column("extension", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFiles.put("content_hash", new TableInfo.Column("content_hash", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFiles.put("size_bytes", new TableInfo.Column("size_bytes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFiles.put("last_modified", new TableInfo.Column("last_modified", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFiles.put("indexed_at", new TableInfo.Column("indexed_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFiles.put("is_directory", new TableInfo.Column("is_directory", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysFiles = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysFiles.add(new TableInfo.ForeignKey("projects", "CASCADE", "NO ACTION", Arrays.asList("project_id"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesFiles = new HashSet<TableInfo.Index>(4);
        _indicesFiles.add(new TableInfo.Index("index_files_project_id", false, Arrays.asList("project_id"), Arrays.asList("ASC")));
        _indicesFiles.add(new TableInfo.Index("index_files_relative_path", false, Arrays.asList("relative_path"), Arrays.asList("ASC")));
        _indicesFiles.add(new TableInfo.Index("index_files_content_hash", false, Arrays.asList("content_hash"), Arrays.asList("ASC")));
        _indicesFiles.add(new TableInfo.Index("index_files_extension", false, Arrays.asList("extension"), Arrays.asList("ASC")));
        final TableInfo _infoFiles = new TableInfo("files", _columnsFiles, _foreignKeysFiles, _indicesFiles);
        final TableInfo _existingFiles = TableInfo.read(db, "files");
        if (!_infoFiles.equals(_existingFiles)) {
          return new RoomOpenHelper.ValidationResult(false, "files(com.amaya.intelligence.data.local.db.entity.FileEntity).\n"
                  + " Expected:\n" + _infoFiles + "\n"
                  + " Found:\n" + _existingFiles);
        }
        final HashSet<String> _columnsFilesFts = new HashSet<String>(2);
        _columnsFilesFts.add("file_name");
        _columnsFilesFts.add("relative_path");
        final FtsTableInfo _infoFilesFts = new FtsTableInfo("files_fts", _columnsFilesFts, "CREATE VIRTUAL TABLE IF NOT EXISTS `files_fts` USING FTS4(`file_name` TEXT NOT NULL, `relative_path` TEXT NOT NULL, content=`files`)");
        final FtsTableInfo _existingFilesFts = FtsTableInfo.read(db, "files_fts");
        if (!_infoFilesFts.equals(_existingFilesFts)) {
          return new RoomOpenHelper.ValidationResult(false, "files_fts(com.amaya.intelligence.data.local.db.entity.FileFtsEntity).\n"
                  + " Expected:\n" + _infoFilesFts + "\n"
                  + " Found:\n" + _existingFilesFts);
        }
        final HashMap<String, TableInfo.Column> _columnsFileMetadata = new HashMap<String, TableInfo.Column>(8);
        _columnsFileMetadata.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFileMetadata.put("file_id", new TableInfo.Column("file_id", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFileMetadata.put("language", new TableInfo.Column("language", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFileMetadata.put("class_names", new TableInfo.Column("class_names", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFileMetadata.put("function_names", new TableInfo.Column("function_names", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFileMetadata.put("imports", new TableInfo.Column("imports", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFileMetadata.put("loc", new TableInfo.Column("loc", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsFileMetadata.put("extracted_at", new TableInfo.Column("extracted_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysFileMetadata = new HashSet<TableInfo.ForeignKey>(1);
        _foreignKeysFileMetadata.add(new TableInfo.ForeignKey("files", "CASCADE", "NO ACTION", Arrays.asList("file_id"), Arrays.asList("id")));
        final HashSet<TableInfo.Index> _indicesFileMetadata = new HashSet<TableInfo.Index>(2);
        _indicesFileMetadata.add(new TableInfo.Index("index_file_metadata_file_id", true, Arrays.asList("file_id"), Arrays.asList("ASC")));
        _indicesFileMetadata.add(new TableInfo.Index("index_file_metadata_language", false, Arrays.asList("language"), Arrays.asList("ASC")));
        final TableInfo _infoFileMetadata = new TableInfo("file_metadata", _columnsFileMetadata, _foreignKeysFileMetadata, _indicesFileMetadata);
        final TableInfo _existingFileMetadata = TableInfo.read(db, "file_metadata");
        if (!_infoFileMetadata.equals(_existingFileMetadata)) {
          return new RoomOpenHelper.ValidationResult(false, "file_metadata(com.amaya.intelligence.data.local.db.entity.FileMetadataEntity).\n"
                  + " Expected:\n" + _infoFileMetadata + "\n"
                  + " Found:\n" + _existingFileMetadata);
        }
        final HashMap<String, TableInfo.Column> _columnsConversations = new HashMap<String, TableInfo.Column>(6);
        _columnsConversations.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsConversations.put("title", new TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsConversations.put("workspacePath", new TableInfo.Column("workspacePath", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsConversations.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsConversations.put("updatedAt", new TableInfo.Column("updatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsConversations.put("messagesJson", new TableInfo.Column("messagesJson", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysConversations = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesConversations = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoConversations = new TableInfo("conversations", _columnsConversations, _foreignKeysConversations, _indicesConversations);
        final TableInfo _existingConversations = TableInfo.read(db, "conversations");
        if (!_infoConversations.equals(_existingConversations)) {
          return new RoomOpenHelper.ValidationResult(false, "conversations(com.amaya.intelligence.data.local.db.entity.ConversationEntity).\n"
                  + " Expected:\n" + _infoConversations + "\n"
                  + " Found:\n" + _existingConversations);
        }
        final HashMap<String, TableInfo.Column> _columnsCronJobs = new HashMap<String, TableInfo.Column>(9);
        _columnsCronJobs.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCronJobs.put("title", new TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCronJobs.put("prompt", new TableInfo.Column("prompt", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCronJobs.put("triggerTimeMillis", new TableInfo.Column("triggerTimeMillis", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCronJobs.put("recurringType", new TableInfo.Column("recurringType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCronJobs.put("isActive", new TableInfo.Column("isActive", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCronJobs.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCronJobs.put("conversationId", new TableInfo.Column("conversationId", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCronJobs.put("fireCount", new TableInfo.Column("fireCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysCronJobs = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesCronJobs = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoCronJobs = new TableInfo("cron_jobs", _columnsCronJobs, _foreignKeysCronJobs, _indicesCronJobs);
        final TableInfo _existingCronJobs = TableInfo.read(db, "cron_jobs");
        if (!_infoCronJobs.equals(_existingCronJobs)) {
          return new RoomOpenHelper.ValidationResult(false, "cron_jobs(com.amaya.intelligence.data.local.db.entity.CronJobEntity).\n"
                  + " Expected:\n" + _infoCronJobs + "\n"
                  + " Found:\n" + _existingCronJobs);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "a3b261e48d6cddecc4c1b55e0004aab4", "c984d1a0d040e5bf188369a814939060");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(1);
    _shadowTablesMap.put("files_fts", "files");
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "projects","files","files_fts","file_metadata","conversations","cron_jobs");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    final boolean _supportsDeferForeignKeys = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP;
    try {
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = FALSE");
      }
      super.beginTransaction();
      if (_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA defer_foreign_keys = TRUE");
      }
      _db.execSQL("DELETE FROM `projects`");
      _db.execSQL("DELETE FROM `files`");
      _db.execSQL("DELETE FROM `files_fts`");
      _db.execSQL("DELETE FROM `file_metadata`");
      _db.execSQL("DELETE FROM `conversations`");
      _db.execSQL("DELETE FROM `cron_jobs`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      if (!_supportsDeferForeignKeys) {
        _db.execSQL("PRAGMA foreign_keys = TRUE");
      }
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(ProjectDao.class, ProjectDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(FileDao.class, FileDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(FileMetadataDao.class, FileMetadataDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(ConversationDao.class, ConversationDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(CronJobDao.class, CronJobDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public ProjectDao projectDao() {
    if (_projectDao != null) {
      return _projectDao;
    } else {
      synchronized(this) {
        if(_projectDao == null) {
          _projectDao = new ProjectDao_Impl(this);
        }
        return _projectDao;
      }
    }
  }

  @Override
  public FileDao fileDao() {
    if (_fileDao != null) {
      return _fileDao;
    } else {
      synchronized(this) {
        if(_fileDao == null) {
          _fileDao = new FileDao_Impl(this);
        }
        return _fileDao;
      }
    }
  }

  @Override
  public FileMetadataDao fileMetadataDao() {
    if (_fileMetadataDao != null) {
      return _fileMetadataDao;
    } else {
      synchronized(this) {
        if(_fileMetadataDao == null) {
          _fileMetadataDao = new FileMetadataDao_Impl(this);
        }
        return _fileMetadataDao;
      }
    }
  }

  @Override
  public ConversationDao conversationDao() {
    if (_conversationDao != null) {
      return _conversationDao;
    } else {
      synchronized(this) {
        if(_conversationDao == null) {
          _conversationDao = new ConversationDao_Impl(this);
        }
        return _conversationDao;
      }
    }
  }

  @Override
  public CronJobDao cronJobDao() {
    if (_cronJobDao != null) {
      return _cronJobDao;
    } else {
      synchronized(this) {
        if(_cronJobDao == null) {
          _cronJobDao = new CronJobDao_Impl(this);
        }
        return _cronJobDao;
      }
    }
  }
}
