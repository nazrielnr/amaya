package com.amaya.intelligence.data.local.dao

import androidx.room.*
import com.amaya.intelligence.data.local.entity.CronJobEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CronJobDao {
    @Query("SELECT * FROM cron_jobs")
    fun getAllJobs(): Flow<List<CronJobEntity>>

    @Query("SELECT * FROM cron_jobs WHERE is_active = 1")
    fun getActiveCronJobs(): Flow<List<CronJobEntity>>

    @Query("SELECT COUNT(*) FROM cron_jobs WHERE is_active = 1")
    fun getActiveJobCount(): Flow<Int>

    @Query("SELECT * FROM cron_jobs WHERE id = :id")
    suspend fun getCronJobById(id: Long): CronJobEntity?

    @Query("SELECT * FROM cron_jobs WHERE id = :id")
    suspend fun getJobById(id: Long): CronJobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCronJob(cronJob: CronJobEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(cronJob: CronJobEntity): Long

    @Update
    suspend fun updateCronJob(cronJob: CronJobEntity)

    @Update
    suspend fun updateJob(cronJob: CronJobEntity)

    @Delete
    suspend fun deleteCronJob(cronJob: CronJobEntity)

    @Query("DELETE FROM cron_jobs WHERE id = :id")
    suspend fun deleteJobById(id: Long)

    @Query("UPDATE cron_jobs SET is_active = :isActive WHERE id = :id")
    suspend fun setActive(id: Long, isActive: Boolean)

    @Query("UPDATE cron_jobs SET is_active = :isActive WHERE id = :id")
    suspend fun updateCronJobStatus(id: Long, isActive: Boolean)

    @Query("UPDATE cron_jobs SET fire_count = fire_count + 1 WHERE id = :id")
    suspend fun incrementFireCount(id: Long)

    @Query("UPDATE cron_jobs SET trigger_time_millis = :triggerTimeMillis WHERE id = :id")
    suspend fun updateTriggerTime(id: Long, triggerTimeMillis: Long)

    @Query("SELECT * FROM cron_jobs WHERE is_active = 1")
    fun getActiveJobs(): Flow<List<CronJobEntity>>
}
