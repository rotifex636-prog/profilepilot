package com.shani.profilepilot.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One Facebook account slot. Holds every per-account browser setting plus the
 * WebView cookie snapshot (cookiesJson) so switching accounts never mixes sessions.
 */
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val colorHex: String = "#1877F2",
    val emoji: String = "",
    /** "ACTIVE" or "DISABLE" */
    val status: String = "ACTIVE",
    /** null = sits directly on Home grid; otherwise inside this folder */
    val folderId: Long? = null,
    val proxyId: Long? = null,
    /** v1.5: kill-switch toggle — ON ho to proxy fail par page load hi nahi hoga. */
    val killSwitch: Boolean = true,
    val deviceModel: String = "",
    /** Full Chrome-Mobile UA string, from DeviceModels */
    val userAgent: String = "",
    /** "OFF", "AUTO" (matched to proxy IP) or "MANUAL" */
    val locationMode: String = "OFF",
    val locationLat: Double = 0.0,
    val locationLng: Double = 0.0,
    val locationLabel: String = "",
    val startPage: String = "https://m.facebook.com/",
    val desktopMode: Boolean = false,
    val darkMode: Boolean = false,
    /** WebView text zoom percent */
    val textZoom: Int = 100,
    val dataSaver: Boolean = false,
    val notes: String = "",
    /** JSONObject string: {"https://m.facebook.com": "cookie pairs...", ...} */
    val cookiesJson: String = "{}",
    /** Unread Facebook notification (jewel) count, last seen value per Shani's rule */
    val notifCount: Int = 0,
    val notifSeenAt: Long = 0L,
    /** Unread Marketplace/customer message count — separate blue chat-bubble badge */
    val msgCount: Int = 0,
    val msgSeenAt: Long = 0L,
    val lastOpened: Long = 0L
)


==========================================
