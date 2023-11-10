package com.cellwatch.data.local.util

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.datetime.Instant

class InstantConverter {
    @TypeConverter
    fun timestampToInstant(timestamp: Long?): Instant? =
        timestamp?.let(Instant::fromEpochMilliseconds)

    @TypeConverter
    fun instantToTimestamp(instant: Instant?): Long? =
        instant?.toEpochMilliseconds()
}

inline fun <reified T> Gson.fromJson(json: String) =
    fromJson<T>(json, object : TypeToken<T>() {}.type)

class ListConverter {
    @TypeConverter
    fun fromStringList(value: List<String>): String {

        return Gson().toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return try {
            Gson().fromJson<List<String>>(value) //using extension function
        } catch (e: Exception) {
            arrayListOf()
        }
    }
}