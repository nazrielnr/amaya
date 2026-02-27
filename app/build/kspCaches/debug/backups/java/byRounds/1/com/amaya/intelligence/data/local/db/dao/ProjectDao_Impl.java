package com.amaya.intelligence.data.local.db.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.amaya.intelligence.data.local.db.entity.ProjectEntity;
import java.lang.Boolean;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class ProjectDao_Impl implements ProjectDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ProjectEntity> __insertionAdapterOfProjectEntity;

  private final EntityDeletionOrUpdateAdapter<ProjectEntity> __deletionAdapterOfProjectEntity;

  private final EntityDeletionOrUpdateAdapter<ProjectEntity> __updateAdapterOfProjectEntity;

  private final SharedSQLiteStatement __preparedStmtOfUpdateScanStatus;

  private final SharedSQLiteStatement __preparedStmtOfSetActiveProject;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public ProjectDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfProjectEntity = new EntityInsertionAdapter<ProjectEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `projects` (`id`,`name`,`root_path`,`primary_language`,`last_scanned_at`,`file_count`,`created_at`,`is_active`) VALUES (nullif(?, 0),?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ProjectEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getName());
        statement.bindString(3, entity.getRootPath());
        if (entity.getPrimaryLanguage() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getPrimaryLanguage());
        }
        if (entity.getLastScannedAt() == null) {
          statement.bindNull(5);
        } else {
          statement.bindLong(5, entity.getLastScannedAt());
        }
        statement.bindLong(6, entity.getFileCount());
        statement.bindLong(7, entity.getCreatedAt());
        final int _tmp = entity.isActive() ? 1 : 0;
        statement.bindLong(8, _tmp);
      }
    };
    this.__deletionAdapterOfProjectEntity = new EntityDeletionOrUpdateAdapter<ProjectEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `projects` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ProjectEntity entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfProjectEntity = new EntityDeletionOrUpdateAdapter<ProjectEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `projects` SET `id` = ?,`name` = ?,`root_path` = ?,`primary_language` = ?,`last_scanned_at` = ?,`file_count` = ?,`created_at` = ?,`is_active` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ProjectEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getName());
        statement.bindString(3, entity.getRootPath());
        if (entity.getPrimaryLanguage() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getPrimaryLanguage());
        }
        if (entity.getLastScannedAt() == null) {
          statement.bindNull(5);
        } else {
          statement.bindLong(5, entity.getLastScannedAt());
        }
        statement.bindLong(6, entity.getFileCount());
        statement.bindLong(7, entity.getCreatedAt());
        final int _tmp = entity.isActive() ? 1 : 0;
        statement.bindLong(8, _tmp);
        statement.bindLong(9, entity.getId());
      }
    };
    this.__preparedStmtOfUpdateScanStatus = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "\n"
                + "        UPDATE projects \n"
                + "        SET last_scanned_at = ?, file_count = ? \n"
                + "        WHERE id = ?\n"
                + "    ";
        return _query;
      }
    };
    this.__preparedStmtOfSetActiveProject = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE projects SET is_active = (id = ?)";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM projects WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM projects";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final ProjectEntity project, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfProjectEntity.insertAndReturnId(project);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertAll(final List<ProjectEntity> projects,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfProjectEntity.insert(projects);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final ProjectEntity project, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfProjectEntity.handle(project);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final ProjectEntity project, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfProjectEntity.handle(project);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateScanStatus(final long projectId, final long scannedAt, final int fileCount,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateScanStatus.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, scannedAt);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, fileCount);
        _argIndex = 3;
        _stmt.bindLong(_argIndex, projectId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateScanStatus.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object setActiveProject(final long projectId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfSetActiveProject.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, projectId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfSetActiveProject.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteById(final long projectId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteById.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, projectId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteById.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAll(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAll.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteAll.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<ProjectEntity>> observeAll() {
    final String _sql = "SELECT * FROM projects ORDER BY created_at DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"projects"}, new Callable<List<ProjectEntity>>() {
      @Override
      @NonNull
      public List<ProjectEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfRootPath = CursorUtil.getColumnIndexOrThrow(_cursor, "root_path");
          final int _cursorIndexOfPrimaryLanguage = CursorUtil.getColumnIndexOrThrow(_cursor, "primary_language");
          final int _cursorIndexOfLastScannedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "last_scanned_at");
          final int _cursorIndexOfFileCount = CursorUtil.getColumnIndexOrThrow(_cursor, "file_count");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "is_active");
          final List<ProjectEntity> _result = new ArrayList<ProjectEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ProjectEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpRootPath;
            _tmpRootPath = _cursor.getString(_cursorIndexOfRootPath);
            final String _tmpPrimaryLanguage;
            if (_cursor.isNull(_cursorIndexOfPrimaryLanguage)) {
              _tmpPrimaryLanguage = null;
            } else {
              _tmpPrimaryLanguage = _cursor.getString(_cursorIndexOfPrimaryLanguage);
            }
            final Long _tmpLastScannedAt;
            if (_cursor.isNull(_cursorIndexOfLastScannedAt)) {
              _tmpLastScannedAt = null;
            } else {
              _tmpLastScannedAt = _cursor.getLong(_cursorIndexOfLastScannedAt);
            }
            final int _tmpFileCount;
            _tmpFileCount = _cursor.getInt(_cursorIndexOfFileCount);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final boolean _tmpIsActive;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp != 0;
            _item = new ProjectEntity(_tmpId,_tmpName,_tmpRootPath,_tmpPrimaryLanguage,_tmpLastScannedAt,_tmpFileCount,_tmpCreatedAt,_tmpIsActive);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getAll(final Continuation<? super List<ProjectEntity>> $completion) {
    final String _sql = "SELECT * FROM projects ORDER BY created_at DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ProjectEntity>>() {
      @Override
      @NonNull
      public List<ProjectEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfRootPath = CursorUtil.getColumnIndexOrThrow(_cursor, "root_path");
          final int _cursorIndexOfPrimaryLanguage = CursorUtil.getColumnIndexOrThrow(_cursor, "primary_language");
          final int _cursorIndexOfLastScannedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "last_scanned_at");
          final int _cursorIndexOfFileCount = CursorUtil.getColumnIndexOrThrow(_cursor, "file_count");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "is_active");
          final List<ProjectEntity> _result = new ArrayList<ProjectEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ProjectEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpRootPath;
            _tmpRootPath = _cursor.getString(_cursorIndexOfRootPath);
            final String _tmpPrimaryLanguage;
            if (_cursor.isNull(_cursorIndexOfPrimaryLanguage)) {
              _tmpPrimaryLanguage = null;
            } else {
              _tmpPrimaryLanguage = _cursor.getString(_cursorIndexOfPrimaryLanguage);
            }
            final Long _tmpLastScannedAt;
            if (_cursor.isNull(_cursorIndexOfLastScannedAt)) {
              _tmpLastScannedAt = null;
            } else {
              _tmpLastScannedAt = _cursor.getLong(_cursorIndexOfLastScannedAt);
            }
            final int _tmpFileCount;
            _tmpFileCount = _cursor.getInt(_cursorIndexOfFileCount);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final boolean _tmpIsActive;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp != 0;
            _item = new ProjectEntity(_tmpId,_tmpName,_tmpRootPath,_tmpPrimaryLanguage,_tmpLastScannedAt,_tmpFileCount,_tmpCreatedAt,_tmpIsActive);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getById(final long projectId,
      final Continuation<? super ProjectEntity> $completion) {
    final String _sql = "SELECT * FROM projects WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, projectId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<ProjectEntity>() {
      @Override
      @Nullable
      public ProjectEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfRootPath = CursorUtil.getColumnIndexOrThrow(_cursor, "root_path");
          final int _cursorIndexOfPrimaryLanguage = CursorUtil.getColumnIndexOrThrow(_cursor, "primary_language");
          final int _cursorIndexOfLastScannedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "last_scanned_at");
          final int _cursorIndexOfFileCount = CursorUtil.getColumnIndexOrThrow(_cursor, "file_count");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "is_active");
          final ProjectEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpRootPath;
            _tmpRootPath = _cursor.getString(_cursorIndexOfRootPath);
            final String _tmpPrimaryLanguage;
            if (_cursor.isNull(_cursorIndexOfPrimaryLanguage)) {
              _tmpPrimaryLanguage = null;
            } else {
              _tmpPrimaryLanguage = _cursor.getString(_cursorIndexOfPrimaryLanguage);
            }
            final Long _tmpLastScannedAt;
            if (_cursor.isNull(_cursorIndexOfLastScannedAt)) {
              _tmpLastScannedAt = null;
            } else {
              _tmpLastScannedAt = _cursor.getLong(_cursorIndexOfLastScannedAt);
            }
            final int _tmpFileCount;
            _tmpFileCount = _cursor.getInt(_cursorIndexOfFileCount);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final boolean _tmpIsActive;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp != 0;
            _result = new ProjectEntity(_tmpId,_tmpName,_tmpRootPath,_tmpPrimaryLanguage,_tmpLastScannedAt,_tmpFileCount,_tmpCreatedAt,_tmpIsActive);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<ProjectEntity> observeById(final long projectId) {
    final String _sql = "SELECT * FROM projects WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, projectId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"projects"}, new Callable<ProjectEntity>() {
      @Override
      @Nullable
      public ProjectEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfRootPath = CursorUtil.getColumnIndexOrThrow(_cursor, "root_path");
          final int _cursorIndexOfPrimaryLanguage = CursorUtil.getColumnIndexOrThrow(_cursor, "primary_language");
          final int _cursorIndexOfLastScannedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "last_scanned_at");
          final int _cursorIndexOfFileCount = CursorUtil.getColumnIndexOrThrow(_cursor, "file_count");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "is_active");
          final ProjectEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpRootPath;
            _tmpRootPath = _cursor.getString(_cursorIndexOfRootPath);
            final String _tmpPrimaryLanguage;
            if (_cursor.isNull(_cursorIndexOfPrimaryLanguage)) {
              _tmpPrimaryLanguage = null;
            } else {
              _tmpPrimaryLanguage = _cursor.getString(_cursorIndexOfPrimaryLanguage);
            }
            final Long _tmpLastScannedAt;
            if (_cursor.isNull(_cursorIndexOfLastScannedAt)) {
              _tmpLastScannedAt = null;
            } else {
              _tmpLastScannedAt = _cursor.getLong(_cursorIndexOfLastScannedAt);
            }
            final int _tmpFileCount;
            _tmpFileCount = _cursor.getInt(_cursorIndexOfFileCount);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final boolean _tmpIsActive;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp != 0;
            _result = new ProjectEntity(_tmpId,_tmpName,_tmpRootPath,_tmpPrimaryLanguage,_tmpLastScannedAt,_tmpFileCount,_tmpCreatedAt,_tmpIsActive);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getByPath(final String path,
      final Continuation<? super ProjectEntity> $completion) {
    final String _sql = "SELECT * FROM projects WHERE root_path = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, path);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<ProjectEntity>() {
      @Override
      @Nullable
      public ProjectEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfRootPath = CursorUtil.getColumnIndexOrThrow(_cursor, "root_path");
          final int _cursorIndexOfPrimaryLanguage = CursorUtil.getColumnIndexOrThrow(_cursor, "primary_language");
          final int _cursorIndexOfLastScannedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "last_scanned_at");
          final int _cursorIndexOfFileCount = CursorUtil.getColumnIndexOrThrow(_cursor, "file_count");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "is_active");
          final ProjectEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpRootPath;
            _tmpRootPath = _cursor.getString(_cursorIndexOfRootPath);
            final String _tmpPrimaryLanguage;
            if (_cursor.isNull(_cursorIndexOfPrimaryLanguage)) {
              _tmpPrimaryLanguage = null;
            } else {
              _tmpPrimaryLanguage = _cursor.getString(_cursorIndexOfPrimaryLanguage);
            }
            final Long _tmpLastScannedAt;
            if (_cursor.isNull(_cursorIndexOfLastScannedAt)) {
              _tmpLastScannedAt = null;
            } else {
              _tmpLastScannedAt = _cursor.getLong(_cursorIndexOfLastScannedAt);
            }
            final int _tmpFileCount;
            _tmpFileCount = _cursor.getInt(_cursorIndexOfFileCount);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final boolean _tmpIsActive;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp != 0;
            _result = new ProjectEntity(_tmpId,_tmpName,_tmpRootPath,_tmpPrimaryLanguage,_tmpLastScannedAt,_tmpFileCount,_tmpCreatedAt,_tmpIsActive);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getActiveProject(final Continuation<? super ProjectEntity> $completion) {
    final String _sql = "SELECT * FROM projects WHERE is_active = 1 LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<ProjectEntity>() {
      @Override
      @Nullable
      public ProjectEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfRootPath = CursorUtil.getColumnIndexOrThrow(_cursor, "root_path");
          final int _cursorIndexOfPrimaryLanguage = CursorUtil.getColumnIndexOrThrow(_cursor, "primary_language");
          final int _cursorIndexOfLastScannedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "last_scanned_at");
          final int _cursorIndexOfFileCount = CursorUtil.getColumnIndexOrThrow(_cursor, "file_count");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "is_active");
          final ProjectEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpRootPath;
            _tmpRootPath = _cursor.getString(_cursorIndexOfRootPath);
            final String _tmpPrimaryLanguage;
            if (_cursor.isNull(_cursorIndexOfPrimaryLanguage)) {
              _tmpPrimaryLanguage = null;
            } else {
              _tmpPrimaryLanguage = _cursor.getString(_cursorIndexOfPrimaryLanguage);
            }
            final Long _tmpLastScannedAt;
            if (_cursor.isNull(_cursorIndexOfLastScannedAt)) {
              _tmpLastScannedAt = null;
            } else {
              _tmpLastScannedAt = _cursor.getLong(_cursorIndexOfLastScannedAt);
            }
            final int _tmpFileCount;
            _tmpFileCount = _cursor.getInt(_cursorIndexOfFileCount);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final boolean _tmpIsActive;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp != 0;
            _result = new ProjectEntity(_tmpId,_tmpName,_tmpRootPath,_tmpPrimaryLanguage,_tmpLastScannedAt,_tmpFileCount,_tmpCreatedAt,_tmpIsActive);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<ProjectEntity> observeActiveProject() {
    final String _sql = "SELECT * FROM projects WHERE is_active = 1 LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"projects"}, new Callable<ProjectEntity>() {
      @Override
      @Nullable
      public ProjectEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfRootPath = CursorUtil.getColumnIndexOrThrow(_cursor, "root_path");
          final int _cursorIndexOfPrimaryLanguage = CursorUtil.getColumnIndexOrThrow(_cursor, "primary_language");
          final int _cursorIndexOfLastScannedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "last_scanned_at");
          final int _cursorIndexOfFileCount = CursorUtil.getColumnIndexOrThrow(_cursor, "file_count");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "created_at");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "is_active");
          final ProjectEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpRootPath;
            _tmpRootPath = _cursor.getString(_cursorIndexOfRootPath);
            final String _tmpPrimaryLanguage;
            if (_cursor.isNull(_cursorIndexOfPrimaryLanguage)) {
              _tmpPrimaryLanguage = null;
            } else {
              _tmpPrimaryLanguage = _cursor.getString(_cursorIndexOfPrimaryLanguage);
            }
            final Long _tmpLastScannedAt;
            if (_cursor.isNull(_cursorIndexOfLastScannedAt)) {
              _tmpLastScannedAt = null;
            } else {
              _tmpLastScannedAt = _cursor.getLong(_cursorIndexOfLastScannedAt);
            }
            final int _tmpFileCount;
            _tmpFileCount = _cursor.getInt(_cursorIndexOfFileCount);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final boolean _tmpIsActive;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp != 0;
            _result = new ProjectEntity(_tmpId,_tmpName,_tmpRootPath,_tmpPrimaryLanguage,_tmpLastScannedAt,_tmpFileCount,_tmpCreatedAt,_tmpIsActive);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getCount(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM projects";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object existsByPath(final String path, final Continuation<? super Boolean> $completion) {
    final String _sql = "SELECT EXISTS(SELECT 1 FROM projects WHERE root_path = ?)";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, path);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Boolean>() {
      @Override
      @NonNull
      public Boolean call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Boolean _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp != 0;
          } else {
            _result = false;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
