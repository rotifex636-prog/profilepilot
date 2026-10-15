package com.shani.profilepilot.data

import android.os.SystemClock
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Authenticator
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.Socket
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * WebView-level proxy control (v1.7 patched).
 *
 * KILL-SWITCH CONTRACT: applyProxy false return karta hai jab proxy lag NAHI saki
 * (feature unsupported, exception, YA 3s ke andar confirm nahi hui).
 * v1.7 fix: pehle timeout par bhi true return hota tha => kill-switch bypass.
 */
private val DIRECT_EXECUTOR = Executor { it.run() }

object ProxyHelper {

    fun isProxySupported(): Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)

    /** @return true sirf tab jab override sach me apply/clear ho chuka ho. */
    fun applyProxy(proxy: ProxyProfile?): Boolean {
        return try {
            if (!isProxySupported()) return proxy == null
            val pc = ProxyController.getInstance()
            val latch = CountDownLatch(1)
            if (proxy == null) {
                pc.clearProxyOverride(DIRECT_EXECUTOR) { latch.countDown() }
            } else {
                val scheme = if (proxy.type == "SOCKS5") "socks5://" else "http://"
                val rule = scheme + proxy.host + ":" + proxy.port
                val config = ProxyConfig.Builder()
                    .addProxyRule(rule) // direct fallback jaan-boojh kar nahi
                    .build()
                // NOTE: ProxyConfig me auth nahi hota; HTTP proxy ka user/pass
                // BrowserActivity.onReceivedHttpAuthRequest se jata hai.
                // SOCKS5 + password WebView me support nahi hota.
                pc.setProxyOverride(config, DIRECT_EXECUTOR) { latch.countDown() }
            }
            latch.await(3, TimeUnit.SECONDS) // <-- result return hota hai (timeout = false)
        } catch (_: Exception) {
            false
        }
    }

    fun clearProxy() {
        try {
            if (isProxySupported()) {
                ProxyController.getInstance().clearProxyOverride(DIRECT_EXECUTOR) {}
            }
        } catch (_: Exception) {
        }
    }

    suspend fun testConnection(host: String, port: Int, timeoutMs: Int = 5000): Boolean =
        withContext(Dispatchers.IO) {
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(host, port), timeoutMs)
                    true
                }
            } catch (_: Exception) {
                false
            }
        }

    data class ProxyTestResult(
        val tcpOk: Boolean,
        val tcpLatencyMs: Long,
        val httpOk: Boolean,
        val httpLatencyMs: Long,
        val exitIp: String?,
        val error: String?
    )

    suspend fun testFull(
        type: String,
        host: String,
        port: Int,
        username: String,
        password: String
    ): ProxyTestResult = withContext(Dispatchers.IO) {
        val tcpStart = SystemClock.elapsedRealtime()
        val tcpOk = try {
            Socket().use { s ->
                s.connect(InetSocketAddress(host, port), 5000)
                true
            }
        } catch (_: Exception) {
            false
        }
        val tcpMs = SystemClock.elapsedRealtime() - tcpStart
        if (!tcpOk) {
            return@withContext ProxyTestResult(false, tcpMs, false, 0, null, "TCP connect fail")
        }

        var httpOk = false
        var httpMs = 0L
        var exitIp: String? = null
        var error: String? = null
        try {
            val proxyType = if (type == "SOCKS5") Proxy.Type.SOCKS else Proxy.Type.HTTP
            val proxy = Proxy(proxyType, InetSocketAddress(host, port))
            if (username.isNotEmpty()) {
                Authenticator.setDefault(object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication =
                        PasswordAuthentication(username, password.toCharArray())
                })
            }
            val httpStart = SystemClock.elapsedRealtime()
            val conn = URL("https://api.ipify.org?format=text").openConnection(proxy) as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "ProfilePilot/1.0")
            if (conn.responseCode == 200) {
                exitIp = conn.inputStream.bufferedReader().readText().trim().take(45)
                httpOk = exitIp!!.isNotEmpty()
            } else {
                error = "HTTP ${conn.responseCode}"
            }
            httpMs = SystemClock.elapsedRealtime() - httpStart
            conn.disconnect()
        } catch (e: Exception) {
            error = (e.message ?: "request fail").take(80)
        } finally {
            Authenticator.setDefault(null)
        }
        ProxyTestResult(tcpOk, tcpMs, httpOk, httpMs, exitIp, error)
    }
}


