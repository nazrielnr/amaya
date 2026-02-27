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
import com.amaya.intelligence.data.local.db.entity.CronJobEntity;
import com.amaya.intelligence.data.local.db.entity.CronRecurringType;
import java.lang.Class;
import java.lang.Exception;
import java.lang.IllegalArgumentException;
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
public final class CronJobDao_Impl implements CronJobDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<CronJobEntity> __insertionAdapterOfCronJobEntity;

  private final EntityDeletionOrUpdateAdapter<CronJobEntity> __deletionAdapterOfCronJobEntity;

  private final EntityDeletionOrUpdateAdapter<CronJobEntity> __updateAdapterOfCronJobEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteJobById;

  private final SharedSQLiteStatement __preparedStmtOfUpdateTriggerTime;

  private final SharedSQLiteStatement __preparedStmtOfSetActive;

  private final SharedSQLiteStatement __preparedStmtOfIncrementFireCount;

  public CronJobDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfCronJobEntity = new EntityInsertionAdapter<CronJobEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `cron_jobs` (`id`,`title`,`prompt`,`triggerTimeMillis`,`recurringType`,`isActive`,`createdAt`,`conversationId`,`fireCount`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final CronJobEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getTitle());
        statement.bindString(3, entity.getPrompt());
        statement.bindLong(4, entity.getTriggerTimeMillis());
        statement.bindString(5, __CronRecurringType_enumToString(entity.getRecurringType()));
        final int _tmp = entity.isActive() ? 1 : 0;
        statement.bindLong(6, _tmp);
        statement.bindLong(7, entity.getCreatedAt());
        if (entity.getConversationId() == null) {
          statement.bindNull(8);
        } else {
          statement.bindLong(8, entity.getConversationId());
        }
        statement.bindLong(9, entity.getFireCount());
      }
    };
    this.__deletionAdapterOfCronJobEntity = new EntityDeletionOrUpdateAdapter<CronJobEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `cron_jobs` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final CronJobEntity entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfCronJobEntity = new EntityDeletionOrUpdateAdapter<CronJobEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `cron_jobs` SET `id` = ?,`title` = ?,`prompt` = ?,`triggerTimeMillis` = ?,`recurringType` = ?,`isActive` = ?,`createdAt` = ?,`conversationId` = ?,`fireCount` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final CronJobEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getTitle());
        statement.bindString(3, entity.getPrompt());
        statement.bindLong(4, entity.getTriggerTimeMillis());
        statement.bindString(5, __CronRecurringType_enumToString(entity.getRecurringType()));
        final int _tmp = entity.isActive() ? 1 : 0;
        statement.bindLong(6, _tmp);
        statement.bindLong(7, entity.getCreatedAt());
        if (entity.getConversationId() == null) {
          statement.bindNull(8);
        } else {
          statement.bindLong(8, entity.getConversationId());
        }
        statement.bindLong(9, entity.getFireCount());
        statement.bindLong(10, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteJobById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM cron_jobs WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateTriggerTime = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE cron_jobs SET triggerTimeMillis = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfSetActive = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE cron_jobs SET isActive = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfIncrementFireCount = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE cron_jobs SET fireCount = fireCount + 1 WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertJob(final CronJobEntity job, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfCronJobEntity.insertAndReturnId(job);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteJob(final CronJobEntity job, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfCronJobEntity.handle(job);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateJob(final CronJobEntity job, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfCronJobEntity.handle(job);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteJobById(final long id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteJobById.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, id);
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
          __preparedStmtOfDeleteJobById.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateTriggerTime(final long id, final long newTime,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateTriggerTime.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, newTime);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, id);
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
          __preparedStmtOfUpdateTriggerTime.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object setActive(final long id, final boolean active,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfSetActive.acquire();
        int _argIndex = 1;
        final int _tmp = active ? 1 : 0;
        _stmt.bindLong(_argIndex, _tmp);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, id);
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
          __preparedStmtOfSetActive.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object incrementFireCount(final long id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfIncrementFireCount.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, id);
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
          __preparedStmtOfIncrementFireCount.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<CronJobEntity>> getAllJobs() {
    final String _sql = "SELECT * FROM cron_jobs ORDER BY triggerTimeMillis ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"cron_jobs"}, new Callable<List<CronJobEntity>>() {
      @Override
      @NonNull
      public List<CronJobEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfPrompt = CursorUtil.getColumnIndexOrThrow(_cursor, "prompt");
          final int _cursorIndexOfTriggerTimeMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "triggerTimeMillis");
          final int _cursorIndexOfRecurringType = CursorUtil.getColumnIndexOrThrow(_cursor, "recurringType");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfConversationId = CursorUtil.getColumnIndexOrThrow(_cursor, "conversationId");
          final int _cursorIndexOfFireCount = CursorUtil.getColumnIndexOrThrow(_cursor, "fireCount");
          final List<CronJobEntity> _result = new ArrayList<CronJobEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final CronJobEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpPrompt;
            _tmpPrompt = _cursor.getString(_cursorIndexOfPrompt);
            final long _tmpTriggerTimeMillis;
            _tmpTriggerTimeMillis = _cursor.getLong(_cursorIndexOfTriggerTimeMillis);
            final CronRecurringType _tmpRecurringType;
            _tmpRecurringType = __CronRecurringType_stringToEnum(_cursor.getString(_cursorIndexOfRecurringType));
            final boolean _tmpIsActive;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final Long _tmpConversationId;
            if (_cursor.isNull(_cursorIndexOfConversationId)) {
              _tmpConversationId = null;
            } else {
              _tmpConversationId = _cursor.getLong(_cursorIndexOfConversationId);
            }
            final int _tmpFireCount;
            _tmpFireCount = _cursor.getInt(_cursorIndexOfFireCount);
            _item = new CronJobEntity(_tmpId,_tmpTitle,_tmpPrompt,_tmpTriggerTimeMillis,_tmpRecurringType,_tmpIsActive,_tmpCreatedAt,_tmpConversationId,_tmpFireCount);
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
  public Flow<List<CronJobEntity>> getActiveJobs() {
    final String _sql = "SELECT * FROM cron_jobs WHERE isActive = 1 ORDER BY triggerTimeMillis ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"cron_jobs"}, new Callable<List<CronJobEntity>>() {
      @Override
      @NonNull
      public List<CronJobEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfPrompt = CursorUtil.getColumnIndexOrThrow(_cursor, "prompt");
          final int _cursorIndexOfTriggerTimeMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "triggerTimeMillis");
          final int _cursorIndexOfRecurringType = CursorUtil.getColumnIndexOrThrow(_cursor, "recurringType");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfConversationId = CursorUtil.getColumnIndexOrThrow(_cursor, "conversationId");
          final int _cursorIndexOfFireCount = CursorUtil.getColumnIndexOrThrow(_cursor, "fireCount");
          final List<CronJobEntity> _result = new ArrayList<CronJobEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final CronJobEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpPrompt;
            _tmpPrompt = _cursor.getString(_cursorIndexOfPrompt);
            final long _tmpTriggerTimeMillis;
            _tmpTriggerTimeMillis = _cursor.getLong(_cursorIndexOfTriggerTimeMillis);
            final CronRecurringType _tmpRecurringType;
            _tmpRecurringType = __CronRecurringType_stringToEnum(_cursor.getString(_cursorIndexOfRecurringType));
            final boolean _tmpIsActive;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final Long _tmpConversationId;
            if (_cursor.isNull(_cursorIndexOfConversationId)) {
              _tmpConversationId = null;
            } else {
              _tmpConversationId = _cursor.getLong(_cursorIndexOfConversationId);
            }
            final int _tmpFireCount;
            _tmpFireCount = _cursor.getInt(_cursorIndexOfFireCount);
            _item = new CronJobEntity(_tmpId,_tmpTitle,_tmpPrompt,_tmpTriggerTimeMillis,_tmpRecurringType,_tmpIsActive,_tmpCreatedAt,_tmpConversationId,_tmpFireCount);
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
  public Flow<Integer> getActiveJobCount() {
    final String _sql = "SELECT COUNT(*) FROM cron_jobs WHERE isActive = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"cron_jobs"}, new Callable<Integer>() {
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
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getJobById(final long id, final Continuation<? super CronJobEntity> $completion) {
    final String _sql = "SELECT * FROM cron_jobs WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<CronJobEntity>() {
      @Override
      @Nullable
      public CronJobEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfPrompt = CursorUtil.getColumnIndexOrThrow(_cursor, "prompt");
          final int _cursorIndexOfTriggerTimeMillis = CursorUtil.getColumnIndexOrThrow(_cursor, "triggerTimeMillis");
          final int _cursorIndexOfRecurringType = CursorUtil.getColumnIndexOrThrow(_cursor, "recurringType");
          final int _cursorIndexOfIsActive = CursorUtil.getColumnIndexOrThrow(_cursor, "isActive");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfConversationId = CursorUtil.getColumnIndexOrThrow(_cursor, "conversationId");
          final int _cursorIndexOfFireCount = CursorUtil.getColumnIndexOrThrow(_cursor, "fireCount");
          final CronJobEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpPrompt;
            _tmpPrompt = _cursor.getString(_cursorIndexOfPrompt);
            final long _tmpTriggerTimeMillis;
            _tmpTriggerTimeMillis = _cursor.getLong(_cursorIndexOfTriggerTimeMillis);
            final CronRecurringType _tmpRecurringType;
            _tmpRecurringType = __CronRecurringType_stringToEnum(_cursor.getString(_cursorIndexOfRecurringType));
            final boolean _tmpIsActive;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsActive);
            _tmpIsActive = _tmp != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final Long _tmpConversationId;
            if (_cursor.isNull(_cursorIndexOfConversationId)) {
              _tmpConversationId = null;
            } else {
              _tmpConversationId = _cursor.getLong(_cursorIndexOfConversationId);
            }
            final int _tmpFireCount;
            _tmpFireCount = _cursor.getInt(_cursorIndexOfFireCount);
            _result = new CronJobEntity(_tmpId,_tmpTitle,_tmpPrompt,_tmpTriggerTimeMillis,_tmpRecurringType,_tmpIsActive,_tmpCreatedAt,_tmpConversationId,_tmpFireCount);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }

  private String __CronRecurringType_enumToString(@NonNull final CronRecurringType _value) {
    switch (_value) {
      case ONCE: return "ONCE";
      case DAILY: return "DAILY";
      case WEEKLY: return "WEEKLY";
      default: throw new IllegalArgumentException("Can't convert enum to string, unknown enum value: " + _value);
    }
  }

  private CronRecurringType __CronRecurringType_stringToEnum(@NonNull final String _value) {
    switch (_value) {
      case "ONCE": return CronRecurringType.ONCE;
      case "DAILY": return CronRecurringType.DAILY;
      case "WEEKLY": return CronRecurringType.WEEKLY;
      default: throw new IllegalArgumentException("Can't convert value to enum, unknown value: " + _value);
    }
  }
}
