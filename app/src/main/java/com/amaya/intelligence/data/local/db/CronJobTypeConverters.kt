package com.amaya.intelligence.data.local.db

import androidx.room.TypeConverter
import com.amaya.intelligence.data.local.entity.CronRecurringType
import com.amaya.intelligence.data.local.entity.CronSessionMode

class CronJobTypeConverters {
    @TypeConverter
    fun fromRecurringType(value: CronRecurringType?): String? = value?.name

    @TypeConverter
    fun toRecurringType(value: String?): CronRecurringType? = value?.let(CronRecurringType::valueOf)

    @TypeConverter
    fun fromSessionMode(value: CronSessionMode?): String? = value?.name

    @TypeConverter
    fun toSessionMode(value: String?): CronSessionMode? = value?.let(CronSessionMode::valueOf)
}