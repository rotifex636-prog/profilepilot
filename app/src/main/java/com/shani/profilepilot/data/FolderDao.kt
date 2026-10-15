package com.shani.profilepilot.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface FolderDao {

    @Insert
    suspend fun insert(f: Folder): Long

    @Insert
    suspend fun insertAll(l: List<Folder>)

    @Query("SELECT * FROM folders ORDER BY name")
    suspend fun all(): List<Folder>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun byId(id: Long): Folder?

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM folders")
    suspend fun deleteAll()
}


==========================================
