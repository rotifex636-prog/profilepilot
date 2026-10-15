package com.shani.profilepilot.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One saved proxy. Exactly ONE proxy is active at a time app-wide; each account
 * remembers its own proxyId and it is applied on switch (see ProxyHelper).
 */
@Entity(tableName = "proxies")
data class ProxyProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    /** "HTTP" or "SOCKS5" */
    val type: String = "HTTP",
    val host: String = "",
    val port: Int = 8080,
    val username: String = "",
    val password: String = ""
)


==========================================
