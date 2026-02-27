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
import com.amaya.intelligence.data.local.db.entity.FileMetadataEntity;
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

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class FileMetadataDao_Impl implements FileMetadataDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<FileMetadataEntity> __insertionAdapterOfFileMetadataEntity;

  private final EntityDeletionOrUpdateAdapter<FileMetadataEntity> __updateAdapterOfFileMetadataEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteByFileId;

  public FileMetadataDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfFileMetadataEntity = new EntityInsertionAdapter<FileMetadataEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `file_metadata` (`id`,`file_id`,`language`,`class_names`,`function_names`,`imports`,`loc`,`extracted_at`) VALUES (nullif(?, 0),?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final FileMetadataEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getFileId());
        statement.bindString(3, entity.getLanguage());
        if (entity.getClassNames() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getClassNames());
        }
        if (entity.getFunctionNames() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getFunctionNames());
        }
        if (entity.getImports() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getImports());
        }
        if (entity.getLinesOfCode() == null) {
          statement.bindNull(7);
        } else {
          statement.bindLong(7, entity.getLinesOfCode());
        }
        statement.bindLong(8, entity.getExtractedAt());
      }
    };
    this.__updateAdapterOfFileMetadataEntity = new EntityDeletionOrUpdateAdapter<FileMetadataEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `file_metadata` SET `id` = ?,`file_id` = ?,`language` = ?,`class_names` = ?,`function_names` = ?,`imports` = ?,`loc` = ?,`extracted_at` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final FileMetadataEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getFileId());
        statement.bindString(3, entity.getLanguage());
        if (entity.getClassNames() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getClassNames());
        }
        if (entity.getFunctionNames() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getFunctionNames());
        }
        if (entity.getImports() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getImports());
        }
        if (entity.getLinesOfCode() == null) {
          statement.bindNull(7);
        } else {
          statement.bindLong(7, entity.getLinesOfCode());
        }
        statement.bindLong(8, entity.getExtractedAt());
        statement.bindLong(9, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteByFileId = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM file_metadata WHERE file_id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final FileMetadataEntity metadata,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfFileMetadataEntity.insertAndReturnId(metadata);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertAll(final List<FileMetadataEntity> metadata,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfFileMetadataEntity.insert(metadata);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final FileMetadataEntity metadata,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfFileMetadataEntity.handle(metadata);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteByFileId(final long fileId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteByFileId.acquire();
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
          __preparedStmtOfDeleteByFileId.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object getByFileId(final long fileId,
      final Continuation<? super FileMetadataEntity> $completion) {
    final String _sql = "SELECT * FROM file_metadata WHERE file_id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, fileId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<FileMetadataEntity>() {
      @Override
      @Nullable
      public FileMetadataEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfFileId = CursorUtil.getColumnIndexOrThrow(_cursor, "file_id");
          final int _cursorIndexOfLanguage = CursorUtil.getColumnIndexOrThrow(_cursor, "language");
          final int _cursorIndexOfClassNames = CursorUtil.getColumnIndexOrThrow(_cursor, "class_names");
          final int _cursorIndexOfFunctionNames = CursorUtil.getColumnIndexOrThrow(_cursor, "function_names");
          final int _cursorIndexOfImports = CursorUtil.getColumnIndexOrThrow(_cursor, "imports");
          final int _cursorIndexOfLinesOfCode = CursorUtil.getColumnIndexOrThrow(_cursor, "loc");
          final int _cursorIndexOfExtractedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "extracted_at");
          final FileMetadataEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpFileId;
            _tmpFileId = _cursor.getLong(_cursorIndexOfFileId);
            final String _tmpLanguage;
            _tmpLanguage = _cursor.getString(_cursorIndexOfLanguage);
            final String _tmpClassNames;
            if (_cursor.isNull(_cursorIndexOfClassNames)) {
              _tmpClassNames = null;
            } else {
              _tmpClassNames = _cursor.getString(_cursorIndexOfClassNames);
            }
            final String _tmpFunctionNames;
            if (_cursor.isNull(_cursorIndexOfFunctionNames)) {
              _tmpFunctionNames = null;
            } else {
              _tmpFunctionNames = _cursor.getString(_cursorIndexOfFunctionNames);
            }
            final String _tmpImports;
            if (_cursor.isNull(_cursorIndexOfImports)) {
              _tmpImports = null;
            } else {
              _tmpImports = _cursor.getString(_cursorIndexOfImports);
            }
            final Integer _tmpLinesOfCode;
            if (_cursor.isNull(_cursorIndexOfLinesOfCode)) {
              _tmpLinesOfCode = null;
            } else {
              _tmpLinesOfCode = _cursor.getInt(_cursorIndexOfLinesOfCode);
            }
            final long _tmpExtractedAt;
            _tmpExtractedAt = _cursor.getLong(_cursorIndexOfExtractedAt);
            _result = new FileMetadataEntity(_tmpId,_tmpFileId,_tmpLanguage,_tmpClassNames,_tmpFunctionNames,_tmpImports,_tmpLinesOfCode,_tmpExtractedAt);
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
  public Object getByLanguage(final long projectId, final String language,
      final Continuation<? super List<FileMetadataEntity>> $completion) {
    final String _sql = "\n"
            + "        SELECT fm.* FROM file_metadata fm\n"
            + "        INNER JOIN files f ON fm.file_id = f.id\n"
            + "        WHERE f.project_id = ? AND fm.language = ?\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, projectId);
    _argIndex = 2;
    _statement.bindString(_argIndex, language);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<FileMetadataEntity>>() {
      @Override
      @NonNull
      public List<FileMetadataEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfFileId = CursorUtil.getColumnIndexOrThrow(_cursor, "file_id");
          final int _cursorIndexOfLanguage = CursorUtil.getColumnIndexOrThrow(_cursor, "language");
          final int _cursorIndexOfClassNames = CursorUtil.getColumnIndexOrThrow(_cursor, "class_names");
          final int _cursorIndexOfFunctionNames = CursorUtil.getColumnIndexOrThrow(_cursor, "function_names");
          final int _cursorIndexOfImports = CursorUtil.getColumnIndexOrThrow(_cursor, "imports");
          final int _cursorIndexOfLinesOfCode = CursorUtil.getColumnIndexOrThrow(_cursor, "loc");
          final int _cursorIndexOfExtractedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "extracted_at");
          final List<FileMetadataEntity> _result = new ArrayList<FileMetadataEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final FileMetadataEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpFileId;
            _tmpFileId = _cursor.getLong(_cursorIndexOfFileId);
            final String _tmpLanguage;
            _tmpLanguage = _cursor.getString(_cursorIndexOfLanguage);
            final String _tmpClassNames;
            if (_cursor.isNull(_cursorIndexOfClassNames)) {
              _tmpClassNames = null;
            } else {
              _tmpClassNames = _cursor.getString(_cursorIndexOfClassNames);
            }
            final String _tmpFunctionNames;
            if (_cursor.isNull(_cursorIndexOfFunctionNames)) {
              _tmpFunctionNames = null;
            } else {
              _tmpFunctionNames = _cursor.getString(_cursorIndexOfFunctionNames);
            }
            final String _tmpImports;
            if (_cursor.isNull(_cursorIndexOfImports)) {
              _tmpImports = null;
            } else {
              _tmpImports = _cursor.getString(_cursorIndexOfImports);
            }
            final Integer _tmpLinesOfCode;
            if (_cursor.isNull(_cursorIndexOfLinesOfCode)) {
              _tmpLinesOfCode = null;
            } else {
              _tmpLinesOfCode = _cursor.getInt(_cursorIndexOfLinesOfCode);
            }
            final long _tmpExtractedAt;
            _tmpExtractedAt = _cursor.getLong(_cursorIndexOfExtractedAt);
            _item = new FileMetadataEntity(_tmpId,_tmpFileId,_tmpLanguage,_tmpClassNames,_tmpFunctionNames,_tmpImports,_tmpLinesOfCode,_tmpExtractedAt);
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
  public Object searchByClassName(final long projectId, final String className,
      final Continuation<? super List<FileMetadataEntity>> $completion) {
    final String _sql = "\n"
            + "        SELECT fm.* FROM file_metadata fm\n"
            + "        INNER JOIN files f ON fm.file_id = f.id\n"
            + "        WHERE f.project_id = ? \n"
            + "        AND fm.class_names LIKE '%' || ? || '%'\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, projectId);
    _argIndex = 2;
    _statement.bindString(_argIndex, className);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<FileMetadataEntity>>() {
      @Override
      @NonNull
      public List<FileMetadataEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfFileId = CursorUtil.getColumnIndexOrThrow(_cursor, "file_id");
          final int _cursorIndexOfLanguage = CursorUtil.getColumnIndexOrThrow(_cursor, "language");
          final int _cursorIndexOfClassNames = CursorUtil.getColumnIndexOrThrow(_cursor, "class_names");
          final int _cursorIndexOfFunctionNames = CursorUtil.getColumnIndexOrThrow(_cursor, "function_names");
          final int _cursorIndexOfImports = CursorUtil.getColumnIndexOrThrow(_cursor, "imports");
          final int _cursorIndexOfLinesOfCode = CursorUtil.getColumnIndexOrThrow(_cursor, "loc");
          final int _cursorIndexOfExtractedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "extracted_at");
          final List<FileMetadataEntity> _result = new ArrayList<FileMetadataEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final FileMetadataEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpFileId;
            _tmpFileId = _cursor.getLong(_cursorIndexOfFileId);
            final String _tmpLanguage;
            _tmpLanguage = _cursor.getString(_cursorIndexOfLanguage);
            final String _tmpClassNames;
            if (_cursor.isNull(_cursorIndexOfClassNames)) {
              _tmpClassNames = null;
            } else {
              _tmpClassNames = _cursor.getString(_cursorIndexOfClassNames);
            }
            final String _tmpFunctionNames;
            if (_cursor.isNull(_cursorIndexOfFunctionNames)) {
              _tmpFunctionNames = null;
            } else {
              _tmpFunctionNames = _cursor.getString(_cursorIndexOfFunctionNames);
            }
            final String _tmpImports;
            if (_cursor.isNull(_cursorIndexOfImports)) {
              _tmpImports = null;
            } else {
              _tmpImports = _cursor.getString(_cursorIndexOfImports);
            }
            final Integer _tmpLinesOfCode;
            if (_cursor.isNull(_cursorIndexOfLinesOfCode)) {
              _tmpLinesOfCode = null;
            } else {
              _tmpLinesOfCode = _cursor.getInt(_cursorIndexOfLinesOfCode);
            }
            final long _tmpExtractedAt;
            _tmpExtractedAt = _cursor.getLong(_cursorIndexOfExtractedAt);
            _item = new FileMetadataEntity(_tmpId,_tmpFileId,_tmpLanguage,_tmpClassNames,_tmpFunctionNames,_tmpImports,_tmpLinesOfCode,_tmpExtractedAt);
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
  public Object searchByFunctionName(final long projectId, final String functionName,
      final Continuation<? super List<FileMetadataEntity>> $completion) {
    final String _sql = "\n"
            + "        SELECT fm.* FROM file_metadata fm\n"
            + "        INNER JOIN files f ON fm.file_id = f.id\n"
            + "        WHERE f.project_id = ? \n"
            + "        AND fm.function_names LIKE '%' || ? || '%'\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, projectId);
    _argIndex = 2;
    _statement.bindString(_argIndex, functionName);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<FileMetadataEntity>>() {
      @Override
      @NonNull
      public List<FileMetadataEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfFileId = CursorUtil.getColumnIndexOrThrow(_cursor, "file_id");
          final int _cursorIndexOfLanguage = CursorUtil.getColumnIndexOrThrow(_cursor, "language");
          final int _cursorIndexOfClassNames = CursorUtil.getColumnIndexOrThrow(_cursor, "class_names");
          final int _cursorIndexOfFunctionNames = CursorUtil.getColumnIndexOrThrow(_cursor, "function_names");
          final int _cursorIndexOfImports = CursorUtil.getColumnIndexOrThrow(_cursor, "imports");
          final int _cursorIndexOfLinesOfCode = CursorUtil.getColumnIndexOrThrow(_cursor, "loc");
          final int _cursorIndexOfExtractedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "extracted_at");
          final List<FileMetadataEntity> _result = new ArrayList<FileMetadataEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final FileMetadataEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpFileId;
            _tmpFileId = _cursor.getLong(_cursorIndexOfFileId);
            final String _tmpLanguage;
            _tmpLanguage = _cursor.getString(_cursorIndexOfLanguage);
            final String _tmpClassNames;
            if (_cursor.isNull(_cursorIndexOfClassNames)) {
              _tmpClassNames = null;
            } else {
              _tmpClassNames = _cursor.getString(_cursorIndexOfClassNames);
            }
            final String _tmpFunctionNames;
            if (_cursor.isNull(_cursorIndexOfFunctionNames)) {
              _tmpFunctionNames = null;
            } else {
              _tmpFunctionNames = _cursor.getString(_cursorIndexOfFunctionNames);
            }
            final String _tmpImports;
            if (_cursor.isNull(_cursorIndexOfImports)) {
              _tmpImports = null;
            } else {
              _tmpImports = _cursor.getString(_cursorIndexOfImports);
            }
            final Integer _tmpLinesOfCode;
            if (_cursor.isNull(_cursorIndexOfLinesOfCode)) {
              _tmpLinesOfCode = null;
            } else {
              _tmpLinesOfCode = _cursor.getInt(_cursorIndexOfLinesOfCode);
            }
            final long _tmpExtractedAt;
            _tmpExtractedAt = _cursor.getLong(_cursorIndexOfExtractedAt);
            _item = new FileMetadataEntity(_tmpId,_tmpFileId,_tmpLanguage,_tmpClassNames,_tmpFunctionNames,_tmpImports,_tmpLinesOfCode,_tmpExtractedAt);
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
