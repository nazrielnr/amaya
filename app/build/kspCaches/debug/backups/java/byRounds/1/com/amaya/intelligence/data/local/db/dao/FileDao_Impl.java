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
import com.amaya.intelligence.data.local.db.entity.FileEntity;
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
public final class FileDao_Impl implements FileDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<FileEntity> __insertionAdapterOfFileEntity;

  private final EntityDeletionOrUpdateAdapter<FileEntity> __deletionAdapterOfFileEntity;

  private final EntityDeletionOrUpdateAdapter<FileEntity> __updateAdapterOfFileEntity;

  private final SharedSQLiteStatement __preparedStmtOfUpdateFileHash;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  private final SharedSQLiteStatement __preparedStmtOfDeleteByProject;

  private final SharedSQLiteStatement __preparedStmtOfDeleteStaleFiles;

  public FileDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfFileEntity = new EntityInsertionAdapter<FileEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `files` (`id`,`project_id`,`relative_path`,`file_name`,`extension`,`content_hash`,`size_bytes`,`last_modified`,`indexed_at`,`is_directory`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final FileEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getProjectId());
        statement.bindString(3, entity.getRelativePath());
        statement.bindString(4, entity.getFileName());
        if (entity.getExtension() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getExtension());
        }
        statement.bindString(6, entity.getContentHash());
        statement.bindLong(7, entity.getSizeBytes());
        statement.bindLong(8, entity.getLastModified());
        statement.bindLong(9, entity.getIndexedAt());
        final int _tmp = entity.isDirectory() ? 1 : 0;
        statement.bindLong(10, _tmp);
      }
    };
    this.__deletionAdapterOfFileEntity = new EntityDeletionOrUpdateAdapter<FileEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `files` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final FileEntity entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfFileEntity = new EntityDeletionOrUpdateAdapter<FileEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `files` SET `id` = ?,`project_id` = ?,`relative_path` = ?,`file_name` = ?,`extension` = ?,`content_hash` = ?,`size_bytes` = ?,`last_modified` = ?,`indexed_at` = ?,`is_directory` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final FileEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getProjectId());
        statement.bindString(3, entity.getRelativePath());
        statement.bindString(4, entity.getFileName());
        if (entity.getExtension() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getExtension());
        }
        statement.bindString(6, entity.getContentHash());
        statement.bindLong(7, entity.getSizeBytes());
        statement.bindLong(8, entity.getLastModified());
        statement.bindLong(9, entity.getIndexedAt());
        final int _tmp = entity.isDirectory() ? 1 : 0;
        statement.bindLong(10, _tmp);
        statement.bindLong(11, entity.getId());
      }
    };
    this.__preparedStmtOfUpdateFileHash = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "\n"
                + "        UPDATE files \n"
                + "        SET content_hash = ?, \n"
                + "            size_bytes = ?, \n"
                + "            last_modified = ?,\n"
                + "            indexed_at = ?\n"
                + "        WHERE id = ?\n"
                + "    ";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM files WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteByProject = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM files WHERE project_id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteStaleFiles = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM files WHERE project_id = ? AND indexed_at < ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final FileEntity file, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfFileEntity.insertAndReturnId(file);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertAll(final List<FileEntity> files,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfFileEntity.insert(files);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final FileEntity file, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfFileEntity.handle(file);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final FileEntity file, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfFileEntity.handle(file);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateFileHash(final long fileId, final String hash, final long size,
      final long lastModified, final long indexedAt, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateFileHash.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, hash);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, size);
        _argIndex = 3;
        _stmt.bindLong(_argIndex, lastModified);
        _argIndex = 4;
        _stmt.bindLong(_argIndex, indexedAt);
        _argIndex = 5;
        _stmt.bindLong(_argIndex, fileId);
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
          __preparedStmtOfUpdateFileHash.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteById(final long fileId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteById.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, fileId);
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
  public Object deleteByProject(final long projectId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteByProject.acquire();
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
          __preparedStmtOfDeleteByProject.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteStaleFiles(final long projectId, final long scanStartTime,
      final Continuation<? super Integer> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteStaleFiles.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, projectId);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, scanStartTime);
        try {
          __db.beginTransaction();
          try {
            final Integer _result = _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteStaleFiles.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<FileEntity>> observeByProject(final long projectId) {
    final String _sql = "SELECT * FROM files WHERE project_id = ? ORDER BY relative_path";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, projectId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"files"}, new Callable<List<FileEntity>>() {
      @Override
      @NonNull
      public List<FileEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfProjectId = CursorUtil.getColumnIndexOrThrow(_cursor, "project_id");
          final int _cursorIndexOfRelativePath = CursorUtil.getColumnIndexOrThrow(_cursor, "relative_path");
          final int _cursorIndexOfFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "file_name");
          final int _cursorIndexOfExtension = CursorUtil.getColumnIndexOrThrow(_cursor, "extension");
          final int _cursorIndexOfContentHash = CursorUtil.getColumnIndexOrThrow(_cursor, "content_hash");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "size_bytes");
          final int _cursorIndexOfLastModified = CursorUtil.getColumnIndexOrThrow(_cursor, "last_modified");
          final int _cursorIndexOfIndexedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "indexed_at");
          final int _cursorIndexOfIsDirectory = CursorUtil.getColumnIndexOrThrow(_cursor, "is_directory");
          final List<FileEntity> _result = new ArrayList<FileEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final FileEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpProjectId;
            _tmpProjectId = _cursor.getLong(_cursorIndexOfProjectId);
            final String _tmpRelativePath;
            _tmpRelativePath = _cursor.getString(_cursorIndexOfRelativePath);
            final String _tmpFileName;
            _tmpFileName = _cursor.getString(_cursorIndexOfFileName);
            final String _tmpExtension;
            if (_cursor.isNull(_cursorIndexOfExtension)) {
              _tmpExtension = null;
            } else {
              _tmpExtension = _cursor.getString(_cursorIndexOfExtension);
            }
            final String _tmpContentHash;
            _tmpContentHash = _cursor.getString(_cursorIndexOfContentHash);
            final long _tmpSizeBytes;
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            final long _tmpLastModified;
            _tmpLastModified = _cursor.getLong(_cursorIndexOfLastModified);
            final long _tmpIndexedAt;
            _tmpIndexedAt = _cursor.getLong(_cursorIndexOfIndexedAt);
            final boolean _tmpIsDirectory;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsDirectory);
            _tmpIsDirectory = _tmp != 0;
            _item = new FileEntity(_tmpId,_tmpProjectId,_tmpRelativePath,_tmpFileName,_tmpExtension,_tmpContentHash,_tmpSizeBytes,_tmpLastModified,_tmpIndexedAt,_tmpIsDirectory);
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
  public Object getByProject(final long projectId,
      final Continuation<? super List<FileEntity>> $completion) {
    final String _sql = "SELECT * FROM files WHERE project_id = ? ORDER BY relative_path";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, projectId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<FileEntity>>() {
      @Override
      @NonNull
      public List<FileEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfProjectId = CursorUtil.getColumnIndexOrThrow(_cursor, "project_id");
          final int _cursorIndexOfRelativePath = CursorUtil.getColumnIndexOrThrow(_cursor, "relative_path");
          final int _cursorIndexOfFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "file_name");
          final int _cursorIndexOfExtension = CursorUtil.getColumnIndexOrThrow(_cursor, "extension");
          final int _cursorIndexOfContentHash = CursorUtil.getColumnIndexOrThrow(_cursor, "content_hash");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "size_bytes");
          final int _cursorIndexOfLastModified = CursorUtil.getColumnIndexOrThrow(_cursor, "last_modified");
          final int _cursorIndexOfIndexedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "indexed_at");
          final int _cursorIndexOfIsDirectory = CursorUtil.getColumnIndexOrThrow(_cursor, "is_directory");
          final List<FileEntity> _result = new ArrayList<FileEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final FileEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpProjectId;
            _tmpProjectId = _cursor.getLong(_cursorIndexOfProjectId);
            final String _tmpRelativePath;
            _tmpRelativePath = _cursor.getString(_cursorIndexOfRelativePath);
            final String _tmpFileName;
            _tmpFileName = _cursor.getString(_cursorIndexOfFileName);
            final String _tmpExtension;
            if (_cursor.isNull(_cursorIndexOfExtension)) {
              _tmpExtension = null;
            } else {
              _tmpExtension = _cursor.getString(_cursorIndexOfExtension);
            }
            final String _tmpContentHash;
            _tmpContentHash = _cursor.getString(_cursorIndexOfContentHash);
            final long _tmpSizeBytes;
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            final long _tmpLastModified;
            _tmpLastModified = _cursor.getLong(_cursorIndexOfLastModified);
            final long _tmpIndexedAt;
            _tmpIndexedAt = _cursor.getLong(_cursorIndexOfIndexedAt);
            final boolean _tmpIsDirectory;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsDirectory);
            _tmpIsDirectory = _tmp != 0;
            _item = new FileEntity(_tmpId,_tmpProjectId,_tmpRelativePath,_tmpFileName,_tmpExtension,_tmpContentHash,_tmpSizeBytes,_tmpLastModified,_tmpIndexedAt,_tmpIsDirectory);
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
  public Object getById(final long fileId, final Continuation<? super FileEntity> $completion) {
    final String _sql = "SELECT * FROM files WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, fileId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<FileEntity>() {
      @Override
      @Nullable
      public FileEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfProjectId = CursorUtil.getColumnIndexOrThrow(_cursor, "project_id");
          final int _cursorIndexOfRelativePath = CursorUtil.getColumnIndexOrThrow(_cursor, "relative_path");
          final int _cursorIndexOfFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "file_name");
          final int _cursorIndexOfExtension = CursorUtil.getColumnIndexOrThrow(_cursor, "extension");
          final int _cursorIndexOfContentHash = CursorUtil.getColumnIndexOrThrow(_cursor, "content_hash");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "size_bytes");
          final int _cursorIndexOfLastModified = CursorUtil.getColumnIndexOrThrow(_cursor, "last_modified");
          final int _cursorIndexOfIndexedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "indexed_at");
          final int _cursorIndexOfIsDirectory = CursorUtil.getColumnIndexOrThrow(_cursor, "is_directory");
          final FileEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpProjectId;
            _tmpProjectId = _cursor.getLong(_cursorIndexOfProjectId);
            final String _tmpRelativePath;
            _tmpRelativePath = _cursor.getString(_cursorIndexOfRelativePath);
            final String _tmpFileName;
            _tmpFileName = _cursor.getString(_cursorIndexOfFileName);
            final String _tmpExtension;
            if (_cursor.isNull(_cursorIndexOfExtension)) {
              _tmpExtension = null;
            } else {
              _tmpExtension = _cursor.getString(_cursorIndexOfExtension);
            }
            final String _tmpContentHash;
            _tmpContentHash = _cursor.getString(_cursorIndexOfContentHash);
            final long _tmpSizeBytes;
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            final long _tmpLastModified;
            _tmpLastModified = _cursor.getLong(_cursorIndexOfLastModified);
            final long _tmpIndexedAt;
            _tmpIndexedAt = _cursor.getLong(_cursorIndexOfIndexedAt);
            final boolean _tmpIsDirectory;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsDirectory);
            _tmpIsDirectory = _tmp != 0;
            _result = new FileEntity(_tmpId,_tmpProjectId,_tmpRelativePath,_tmpFileName,_tmpExtension,_tmpContentHash,_tmpSizeBytes,_tmpLastModified,_tmpIndexedAt,_tmpIsDirectory);
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
  public Object getByPath(final long projectId, final String path,
      final Continuation<? super FileEntity> $completion) {
    final String _sql = "SELECT * FROM files WHERE project_id = ? AND relative_path = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, projectId);
    _argIndex = 2;
    _statement.bindString(_argIndex, path);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<FileEntity>() {
      @Override
      @Nullable
      public FileEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfProjectId = CursorUtil.getColumnIndexOrThrow(_cursor, "project_id");
          final int _cursorIndexOfRelativePath = CursorUtil.getColumnIndexOrThrow(_cursor, "relative_path");
          final int _cursorIndexOfFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "file_name");
          final int _cursorIndexOfExtension = CursorUtil.getColumnIndexOrThrow(_cursor, "extension");
          final int _cursorIndexOfContentHash = CursorUtil.getColumnIndexOrThrow(_cursor, "content_hash");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "size_bytes");
          final int _cursorIndexOfLastModified = CursorUtil.getColumnIndexOrThrow(_cursor, "last_modified");
          final int _cursorIndexOfIndexedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "indexed_at");
          final int _cursorIndexOfIsDirectory = CursorUtil.getColumnIndexOrThrow(_cursor, "is_directory");
          final FileEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpProjectId;
            _tmpProjectId = _cursor.getLong(_cursorIndexOfProjectId);
            final String _tmpRelativePath;
            _tmpRelativePath = _cursor.getString(_cursorIndexOfRelativePath);
            final String _tmpFileName;
            _tmpFileName = _cursor.getString(_cursorIndexOfFileName);
            final String _tmpExtension;
            if (_cursor.isNull(_cursorIndexOfExtension)) {
              _tmpExtension = null;
            } else {
              _tmpExtension = _cursor.getString(_cursorIndexOfExtension);
            }
            final String _tmpContentHash;
            _tmpContentHash = _cursor.getString(_cursorIndexOfContentHash);
            final long _tmpSizeBytes;
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            final long _tmpLastModified;
            _tmpLastModified = _cursor.getLong(_cursorIndexOfLastModified);
            final long _tmpIndexedAt;
            _tmpIndexedAt = _cursor.getLong(_cursorIndexOfIndexedAt);
            final boolean _tmpIsDirectory;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsDirectory);
            _tmpIsDirectory = _tmp != 0;
            _result = new FileEntity(_tmpId,_tmpProjectId,_tmpRelativePath,_tmpFileName,_tmpExtension,_tmpContentHash,_tmpSizeBytes,_tmpLastModified,_tmpIndexedAt,_tmpIsDirectory);
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
  public Object getByExtension(final long projectId, final String ext,
      final Continuation<? super List<FileEntity>> $completion) {
    final String _sql = "SELECT * FROM files WHERE project_id = ? AND extension = ? ORDER BY relative_path";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, projectId);
    _argIndex = 2;
    _statement.bindString(_argIndex, ext);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<FileEntity>>() {
      @Override
      @NonNull
      public List<FileEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfProjectId = CursorUtil.getColumnIndexOrThrow(_cursor, "project_id");
          final int _cursorIndexOfRelativePath = CursorUtil.getColumnIndexOrThrow(_cursor, "relative_path");
          final int _cursorIndexOfFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "file_name");
          final int _cursorIndexOfExtension = CursorUtil.getColumnIndexOrThrow(_cursor, "extension");
          final int _cursorIndexOfContentHash = CursorUtil.getColumnIndexOrThrow(_cursor, "content_hash");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "size_bytes");
          final int _cursorIndexOfLastModified = CursorUtil.getColumnIndexOrThrow(_cursor, "last_modified");
          final int _cursorIndexOfIndexedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "indexed_at");
          final int _cursorIndexOfIsDirectory = CursorUtil.getColumnIndexOrThrow(_cursor, "is_directory");
          final List<FileEntity> _result = new ArrayList<FileEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final FileEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpProjectId;
            _tmpProjectId = _cursor.getLong(_cursorIndexOfProjectId);
            final String _tmpRelativePath;
            _tmpRelativePath = _cursor.getString(_cursorIndexOfRelativePath);
            final String _tmpFileName;
            _tmpFileName = _cursor.getString(_cursorIndexOfFileName);
            final String _tmpExtension;
            if (_cursor.isNull(_cursorIndexOfExtension)) {
              _tmpExtension = null;
            } else {
              _tmpExtension = _cursor.getString(_cursorIndexOfExtension);
            }
            final String _tmpContentHash;
            _tmpContentHash = _cursor.getString(_cursorIndexOfContentHash);
            final long _tmpSizeBytes;
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            final long _tmpLastModified;
            _tmpLastModified = _cursor.getLong(_cursorIndexOfLastModified);
            final long _tmpIndexedAt;
            _tmpIndexedAt = _cursor.getLong(_cursorIndexOfIndexedAt);
            final boolean _tmpIsDirectory;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsDirectory);
            _tmpIsDirectory = _tmp != 0;
            _item = new FileEntity(_tmpId,_tmpProjectId,_tmpRelativePath,_tmpFileName,_tmpExtension,_tmpContentHash,_tmpSizeBytes,_tmpLastModified,_tmpIndexedAt,_tmpIsDirectory);
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
  public Object getDirectories(final long projectId,
      final Continuation<? super List<FileEntity>> $completion) {
    final String _sql = "SELECT * FROM files WHERE project_id = ? AND is_directory = 1 ORDER BY relative_path";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, projectId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<FileEntity>>() {
      @Override
      @NonNull
      public List<FileEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfProjectId = CursorUtil.getColumnIndexOrThrow(_cursor, "project_id");
          final int _cursorIndexOfRelativePath = CursorUtil.getColumnIndexOrThrow(_cursor, "relative_path");
          final int _cursorIndexOfFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "file_name");
          final int _cursorIndexOfExtension = CursorUtil.getColumnIndexOrThrow(_cursor, "extension");
          final int _cursorIndexOfContentHash = CursorUtil.getColumnIndexOrThrow(_cursor, "content_hash");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "size_bytes");
          final int _cursorIndexOfLastModified = CursorUtil.getColumnIndexOrThrow(_cursor, "last_modified");
          final int _cursorIndexOfIndexedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "indexed_at");
          final int _cursorIndexOfIsDirectory = CursorUtil.getColumnIndexOrThrow(_cursor, "is_directory");
          final List<FileEntity> _result = new ArrayList<FileEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final FileEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpProjectId;
            _tmpProjectId = _cursor.getLong(_cursorIndexOfProjectId);
            final String _tmpRelativePath;
            _tmpRelativePath = _cursor.getString(_cursorIndexOfRelativePath);
            final String _tmpFileName;
            _tmpFileName = _cursor.getString(_cursorIndexOfFileName);
            final String _tmpExtension;
            if (_cursor.isNull(_cursorIndexOfExtension)) {
              _tmpExtension = null;
            } else {
              _tmpExtension = _cursor.getString(_cursorIndexOfExtension);
            }
            final String _tmpContentHash;
            _tmpContentHash = _cursor.getString(_cursorIndexOfContentHash);
            final long _tmpSizeBytes;
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            final long _tmpLastModified;
            _tmpLastModified = _cursor.getLong(_cursorIndexOfLastModified);
            final long _tmpIndexedAt;
            _tmpIndexedAt = _cursor.getLong(_cursorIndexOfIndexedAt);
            final boolean _tmpIsDirectory;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsDirectory);
            _tmpIsDirectory = _tmp != 0;
            _item = new FileEntity(_tmpId,_tmpProjectId,_tmpRelativePath,_tmpFileName,_tmpExtension,_tmpContentHash,_tmpSizeBytes,_tmpLastModified,_tmpIndexedAt,_tmpIsDirectory);
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
  public Object getCountByProject(final long projectId,
      final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM files WHERE project_id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, projectId);
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
  public Object getTotalSizeByProject(final long projectId,
      final Continuation<? super Long> $completion) {
    final String _sql = "SELECT SUM(size_bytes) FROM files WHERE project_id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, projectId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Long>() {
      @Override
      @Nullable
      public Long call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Long _result;
          if (_cursor.moveToFirst()) {
            final Long _tmp;
            if (_cursor.isNull(0)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getLong(0);
            }
            _result = _tmp;
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
  public Object searchByName(final long projectId, final String query, final int limit,
      final Continuation<? super List<FileEntity>> $completion) {
    final String _sql = "\n"
            + "        SELECT f.* FROM files f\n"
            + "        INNER JOIN files_fts fts ON f.rowid = fts.rowid\n"
            + "        WHERE files_fts MATCH ?\n"
            + "        AND f.project_id = ?\n"
            + "        ORDER BY f.file_name\n"
            + "        LIMIT ?\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 3);
    int _argIndex = 1;
    _statement.bindString(_argIndex, query);
    _argIndex = 2;
    _statement.bindLong(_argIndex, projectId);
    _argIndex = 3;
    _statement.bindLong(_argIndex, limit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<FileEntity>>() {
      @Override
      @NonNull
      public List<FileEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfProjectId = CursorUtil.getColumnIndexOrThrow(_cursor, "project_id");
          final int _cursorIndexOfRelativePath = CursorUtil.getColumnIndexOrThrow(_cursor, "relative_path");
          final int _cursorIndexOfFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "file_name");
          final int _cursorIndexOfExtension = CursorUtil.getColumnIndexOrThrow(_cursor, "extension");
          final int _cursorIndexOfContentHash = CursorUtil.getColumnIndexOrThrow(_cursor, "content_hash");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "size_bytes");
          final int _cursorIndexOfLastModified = CursorUtil.getColumnIndexOrThrow(_cursor, "last_modified");
          final int _cursorIndexOfIndexedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "indexed_at");
          final int _cursorIndexOfIsDirectory = CursorUtil.getColumnIndexOrThrow(_cursor, "is_directory");
          final List<FileEntity> _result = new ArrayList<FileEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final FileEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpProjectId;
            _tmpProjectId = _cursor.getLong(_cursorIndexOfProjectId);
            final String _tmpRelativePath;
            _tmpRelativePath = _cursor.getString(_cursorIndexOfRelativePath);
            final String _tmpFileName;
            _tmpFileName = _cursor.getString(_cursorIndexOfFileName);
            final String _tmpExtension;
            if (_cursor.isNull(_cursorIndexOfExtension)) {
              _tmpExtension = null;
            } else {
              _tmpExtension = _cursor.getString(_cursorIndexOfExtension);
            }
            final String _tmpContentHash;
            _tmpContentHash = _cursor.getString(_cursorIndexOfContentHash);
            final long _tmpSizeBytes;
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            final long _tmpLastModified;
            _tmpLastModified = _cursor.getLong(_cursorIndexOfLastModified);
            final long _tmpIndexedAt;
            _tmpIndexedAt = _cursor.getLong(_cursorIndexOfIndexedAt);
            final boolean _tmpIsDirectory;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsDirectory);
            _tmpIsDirectory = _tmp != 0;
            _item = new FileEntity(_tmpId,_tmpProjectId,_tmpRelativePath,_tmpFileName,_tmpExtension,_tmpContentHash,_tmpSizeBytes,_tmpLastModified,_tmpIndexedAt,_tmpIsDirectory);
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
  public Object searchByPath(final long projectId, final String query, final int limit,
      final Continuation<? super List<FileEntity>> $completion) {
    final String _sql = "\n"
            + "        SELECT f.* FROM files f\n"
            + "        INNER JOIN files_fts fts ON f.rowid = fts.rowid\n"
            + "        WHERE files_fts MATCH ?\n"
            + "        AND f.project_id = ?\n"
            + "        ORDER BY f.relative_path\n"
            + "        LIMIT ?\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 3);
    int _argIndex = 1;
    _statement.bindString(_argIndex, query);
    _argIndex = 2;
    _statement.bindLong(_argIndex, projectId);
    _argIndex = 3;
    _statement.bindLong(_argIndex, limit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<FileEntity>>() {
      @Override
      @NonNull
      public List<FileEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfProjectId = CursorUtil.getColumnIndexOrThrow(_cursor, "project_id");
          final int _cursorIndexOfRelativePath = CursorUtil.getColumnIndexOrThrow(_cursor, "relative_path");
          final int _cursorIndexOfFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "file_name");
          final int _cursorIndexOfExtension = CursorUtil.getColumnIndexOrThrow(_cursor, "extension");
          final int _cursorIndexOfContentHash = CursorUtil.getColumnIndexOrThrow(_cursor, "content_hash");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "size_bytes");
          final int _cursorIndexOfLastModified = CursorUtil.getColumnIndexOrThrow(_cursor, "last_modified");
          final int _cursorIndexOfIndexedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "indexed_at");
          final int _cursorIndexOfIsDirectory = CursorUtil.getColumnIndexOrThrow(_cursor, "is_directory");
          final List<FileEntity> _result = new ArrayList<FileEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final FileEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpProjectId;
            _tmpProjectId = _cursor.getLong(_cursorIndexOfProjectId);
            final String _tmpRelativePath;
            _tmpRelativePath = _cursor.getString(_cursorIndexOfRelativePath);
            final String _tmpFileName;
            _tmpFileName = _cursor.getString(_cursorIndexOfFileName);
            final String _tmpExtension;
            if (_cursor.isNull(_cursorIndexOfExtension)) {
              _tmpExtension = null;
            } else {
              _tmpExtension = _cursor.getString(_cursorIndexOfExtension);
            }
            final String _tmpContentHash;
            _tmpContentHash = _cursor.getString(_cursorIndexOfContentHash);
            final long _tmpSizeBytes;
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            final long _tmpLastModified;
            _tmpLastModified = _cursor.getLong(_cursorIndexOfLastModified);
            final long _tmpIndexedAt;
            _tmpIndexedAt = _cursor.getLong(_cursorIndexOfIndexedAt);
            final boolean _tmpIsDirectory;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsDirectory);
            _tmpIsDirectory = _tmp != 0;
            _item = new FileEntity(_tmpId,_tmpProjectId,_tmpRelativePath,_tmpFileName,_tmpExtension,_tmpContentHash,_tmpSizeBytes,_tmpLastModified,_tmpIndexedAt,_tmpIsDirectory);
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
  public Object getHash(final long projectId, final String path,
      final Continuation<? super String> $completion) {
    final String _sql = "SELECT content_hash FROM files WHERE project_id = ? AND relative_path = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, projectId);
    _argIndex = 2;
    _statement.bindString(_argIndex, path);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<String>() {
      @Override
      @Nullable
      public String call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final String _result;
          if (_cursor.moveToFirst()) {
            if (_cursor.isNull(0)) {
              _result = null;
            } else {
              _result = _cursor.getString(0);
            }
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
  public Object getAllHashes(final long projectId,
      final Continuation<? super List<FileHashPair>> $completion) {
    final String _sql = "SELECT relative_path, content_hash FROM files WHERE project_id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, projectId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<FileHashPair>>() {
      @Override
      @NonNull
      public List<FileHashPair> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfRelativePath = 0;
          final int _cursorIndexOfContentHash = 1;
          final List<FileHashPair> _result = new ArrayList<FileHashPair>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final FileHashPair _item;
            final String _tmpRelativePath;
            _tmpRelativePath = _cursor.getString(_cursorIndexOfRelativePath);
            final String _tmpContentHash;
            _tmpContentHash = _cursor.getString(_cursorIndexOfContentHash);
            _item = new FileHashPair(_tmpRelativePath,_tmpContentHash);
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
  public Object getRecentlyModified(final long projectId, final int limit,
      final Continuation<? super List<FileEntity>> $completion) {
    final String _sql = "\n"
            + "        SELECT * FROM files \n"
            + "        WHERE project_id = ? \n"
            + "        ORDER BY last_modified DESC \n"
            + "        LIMIT ?\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, projectId);
    _argIndex = 2;
    _statement.bindLong(_argIndex, limit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<FileEntity>>() {
      @Override
      @NonNull
      public List<FileEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfProjectId = CursorUtil.getColumnIndexOrThrow(_cursor, "project_id");
          final int _cursorIndexOfRelativePath = CursorUtil.getColumnIndexOrThrow(_cursor, "relative_path");
          final int _cursorIndexOfFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "file_name");
          final int _cursorIndexOfExtension = CursorUtil.getColumnIndexOrThrow(_cursor, "extension");
          final int _cursorIndexOfContentHash = CursorUtil.getColumnIndexOrThrow(_cursor, "content_hash");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "size_bytes");
          final int _cursorIndexOfLastModified = CursorUtil.getColumnIndexOrThrow(_cursor, "last_modified");
          final int _cursorIndexOfIndexedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "indexed_at");
          final int _cursorIndexOfIsDirectory = CursorUtil.getColumnIndexOrThrow(_cursor, "is_directory");
          final List<FileEntity> _result = new ArrayList<FileEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final FileEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpProjectId;
            _tmpProjectId = _cursor.getLong(_cursorIndexOfProjectId);
            final String _tmpRelativePath;
            _tmpRelativePath = _cursor.getString(_cursorIndexOfRelativePath);
            final String _tmpFileName;
            _tmpFileName = _cursor.getString(_cursorIndexOfFileName);
            final String _tmpExtension;
            if (_cursor.isNull(_cursorIndexOfExtension)) {
              _tmpExtension = null;
            } else {
              _tmpExtension = _cursor.getString(_cursorIndexOfExtension);
            }
            final String _tmpContentHash;
            _tmpContentHash = _cursor.getString(_cursorIndexOfContentHash);
            final long _tmpSizeBytes;
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            final long _tmpLastModified;
            _tmpLastModified = _cursor.getLong(_cursorIndexOfLastModified);
            final long _tmpIndexedAt;
            _tmpIndexedAt = _cursor.getLong(_cursorIndexOfIndexedAt);
            final boolean _tmpIsDirectory;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsDirectory);
            _tmpIsDirectory = _tmp != 0;
            _item = new FileEntity(_tmpId,_tmpProjectId,_tmpRelativePath,_tmpFileName,_tmpExtension,_tmpContentHash,_tmpSizeBytes,_tmpLastModified,_tmpIndexedAt,_tmpIsDirectory);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
