package com.shani.profilepilot.data

import androidx.room.TypeConverter

/**
 * Standard Room type converter, registered via @TypeConverters on AppDatabase.
 * (v1.0-v1.2 used @ProvidedTypeConverter + addTypeConverter, which Room
 * rejected at runtime with "Unexpected type converter" -> instant crash
 * when Home opened the database. The crash report caught it.)
 */
class Converters {

    @TypeConverter
    fun fromStringList(v: List<String>): String = v.joinToString("\u0001")

    @TypeConverter
    fun toStringList(s: String): List<String> =
        if (s.isEmpty()) emptyList() else s.split("\u0001")
}


