package com.amaya.intelligence.data.local.db.dao

import androidx.room.*
import com.amaya.intelligence.data.local.db.entity.CronJobEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CronJobDao {

    @Query("SELECT * FROM cron_jobs ORDER BY triggerTimeMillis ASC")
    fun getAllJobs(): Flow<List<CronJobEntity>>

    @Query("SELECT * FROM cron_jobs WHERE isActive = 1 ORDER BY triggerTimeMillis ASC")
    fun getActiveJobs(): Flow<List<CronJobEntity>>

    @Query("SELECT COUNT(*) FROM cron_jobs WHERE isActive = 1")
    fun getActiveJobCount(): Flow<Int>

    @Query("SELECT * FROM cron_jobs WHERE id = :id")
    suspend fun getJobById(id: Long): CronJobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: CronJobEntity): Long

    @Update
    suspend fun updateJob(job: CronJobEntity)

    @Delete
    suspend fun deleteJob(job: CronJobEntity)

    @Query("DELETE FROM cron_jobs WHERE id = :id")
    suspend fun deleteJobById(id: Long)

    @Query("UPDATE cron_jobs SET triggerTimeMillis = :newTime WHERE id = :id")
    suspend fun updateTriggerTime(id: Long, newTime: Long)

    @Query("UPDATE cron_jobs SET isActive = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)

    @Query("UPDATE cron_jobs SET fireCount = fireCount + 1 WHERE id = :id")
    suspend fun incrementFireCount(id: Long)
}
