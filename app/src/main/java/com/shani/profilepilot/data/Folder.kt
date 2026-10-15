package com.shani.profilepilot.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A named, colored folder tile on Home that groups account tiles. */
@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val colorHex: String = "#F59E0B"
)


