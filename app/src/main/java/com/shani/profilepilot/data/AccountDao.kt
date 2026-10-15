package com.shani.profilepilot.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface AccountDao {

    @Insert
    suspend fun insert(a: Account): Long

    @Insert
    suspend fun insertAll(l: List<Account>)

    @Update
    suspend fun update(a: Account)

    /** Home grid: accounts not inside any folder */
    @Query("SELECT * FROM accounts WHERE folderId IS NULL ORDER BY name")
    suspend fun homeAccounts(): List<Account>

    @Query("SELECT * FROM accounts WHERE folderId = :fid ORDER BY name")
    suspend fun inFolder(fid: Long): List<Account>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun byId(id: Long): Account?

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM accounts WHERE folderId IS NULL")
    suspend fun homeCount(): Int

    /** v1.7: poori table ka count (folder wale accounts bhi) — 50 ki limit ke liye */
    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun totalCount(): Int

    @Query("SELECT * FROM accounts")
    suspend fun all(): List<Account>

    @Query("DELETE FROM accounts")
    suspend fun deleteAll()

    // ---------- v1.7 targeted updates: ek field badlo, baqi purane overwrite na hon ----------

    @Query("UPDATE accounts SET cookiesJson = :cookies WHERE id = :id")
    suspend fun setCookies(id: Long, cookies: String)

    @Query("UPDATE accounts SET notifCount = :n, notifSeenAt = :t WHERE id = :id")
    suspend fun setNotif(id: Long, n: Int, t: Long)

    @Query("UPDATE accounts SET msgCount = :m, msgSeenAt = :t WHERE id = :id")
    suspend fun setMsg(id: Long, m: Int, t: Long)

    @Query("UPDATE accounts SET cookiesJson = '{}', notifCount = 0, msgCount = 0 WHERE id = :id")
    suspend fun resetSession(id: Long)

    @Query("UPDATE accounts SET lastOpened = :t WHERE id = :id")
    suspend fun setLastOpened(id: Long, t: Long)
}


