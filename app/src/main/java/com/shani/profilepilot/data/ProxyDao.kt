package com.shani.profilepilot.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface ProxyDao {

    @Insert
    suspend fun insert(p: ProxyProfile): Long

    @Insert
    suspend fun insertAll(l: List<ProxyProfile>)

    @Update
    suspend fun update(p: ProxyProfile)

    @Query("SELECT * FROM proxies ORDER BY name")
    suspend fun all(): List<ProxyProfile>

    @Query("SELECT * FROM proxies WHERE id = :id")
    suspend fun byId(id: Long): ProxyProfile?

    @Query("DELETE FROM proxies WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM proxies")
    suspend fun deleteAll()
}


