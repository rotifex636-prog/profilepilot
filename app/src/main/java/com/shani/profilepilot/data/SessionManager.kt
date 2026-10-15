package com.shani.profilepilot.data

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Owns the single live WebView session and per-account cookie snapshots (v1.7 patched).
 *
 * v1.7 fixes:
 *  - removeAllCookies ka callback ka intezar (pehle nayi cookies bhi delete ho jati thin)
 *  - switchTo: snapshot ke BAAD account DB se dobara read hota hai (purani cookies restore nahi hoti)
 *  - Cookies Domain=.facebook.com ke sath restore hoti hain — pehle host-only ban jati thin,
 *    isi wajah se accountscenter.facebook.com / web.facebook.com par login nazar nahi aata tha
 *  - Mutex: onPause snapshot aur switch ek sath nahi chalte
 *  - Clear/Logout kisi aur account ka live WebView wipe nahi karta
 */
object SessionManager {

    /** Account jo abhi WebView me live hai. Sirf memory me (process restart = koi live session nahi). */
    @Volatile
    var activeAccountId: Long? = null

    private val lock = Mutex()
    private const val COOKIE_MAX_AGE = 31536000 // 1 saal

    private val COOKIE_URLS = listOf(
        "https://m.facebook.com",
        "https://www.facebook.com",
        "https://accountscenter.facebook.com",
        "https://web.facebook.com"
    )

    suspend fun switchTo(context: Context, target: Account) = withContext(Dispatchers.IO) {
        lock.withLock {
            snapshotInternal(context)
            clearInternal()
            // IMPORTANT: snapshot ab DB me naya hai — target ko dobara read karo
            val fresh = AppDatabase.get(context).accountDao().byId(target.id) ?: target
            restoreInternal(fresh)
            activeAccountId = target.id
        }
    }

    suspend fun snapshotActive(context: Context) = withContext(Dispatchers.IO) {
        lock.withLock { snapshotInternal(context) }
    }

    suspend fun restoreAccount(context: Context, account: Account) = withContext(Dispatchers.IO) {
        lock.withLock { restoreInternal(account) }
    }

    suspend fun clearWebData(context: Context) = withContext(Dispatchers.IO) {
        lock.withLock { clearInternal() }
    }

    /**
     * "Clear data": account ka snapshot delete + badges zero.
     * WebView sirf tab wipe hota hai jab ye account hi live ho
     * (warna kisi aur account ki live cookies uD jati aur uska snapshot kharab hota).
     */
    suspend fun clearAccountData(context: Context, account: Account) = withContext(Dispatchers.IO) {
        lock.withLock {
            AppDatabase.get(context).accountDao().resetSession(account.id)
            if (activeAccountId == account.id) clearInternal()
        }
    }

    /** Explicit logout — same mechanics, alag action. */
    suspend fun logout(context: Context, account: Account) = withContext(Dispatchers.IO) {
        clearAccountData(context, account)
    }

    // ---------------- internals (lock ke andar se hi call hon) ----------------

    private suspend fun snapshotInternal(context: Context) {
        val id = activeAccountId ?: return
        val dao = AppDatabase.get(context).accountDao()
        if (dao.byId(id) == null) return
        val cm = CookieManager.getInstance()
        val jar = JSONObject()
        for (u in COOKIE_URLS) jar.put(u, cm.getCookie(u) ?: "")
        dao.setCookies(id, jar.toString()) // sirf cookiesJson — baqi fields safe
    }

    private fun restoreInternal(account: Account) {
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        try {
            val jar = JSONObject(account.cookiesJson)
            val keys = jar.keys()
            while (keys.hasNext()) {
                val url = keys.next()
                val raw = jar.optString(url)
                if (raw.isEmpty()) continue
                val domainPart = if (url.contains("facebook.com")) "; Domain=.facebook.com" else ""
                raw.split(";").forEach { pair ->
                    val p = pair.trim()
                    if (p.isNotEmpty() && p.contains("=")) {
                        cm.setCookie(
                            url,
                            "$p$domainPart; Path=/; Secure; SameSite=None; Max-Age=$COOKIE_MAX_AGE"
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Kharab snapshot: saaf shuru karo, crash nahi.
        }
        cm.flush()
    }

    private suspend fun clearInternal() {
        val cm = CookieManager.getInstance()
        // removeAllCookies ko Looper thread (Main) chahiye — warna crash:
        // "removeAllCookies must be called on a thread with a running Looper"
        withContext(Dispatchers.Main) {
            withTimeoutOrNull(3000) {
                suspendCancellableCoroutine<Unit> { c ->
                    cm.removeAllCookies { if (c.isActive) c.resume(Unit) }
                }
            }
            cm.flush()
        }
        withContext(Dispatchers.Main) {
            try {
                WebStorage.getInstance().deleteAllData()
            } catch (_: Exception) {
            }
        }
    }
}


