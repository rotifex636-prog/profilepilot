package com.shani.profilepilot.ui

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView

/**
 * v1.7: ab ye "pool" nahi balki WebView ka seedha lifecycle manager hai.
 *
 * Purani problem: ek static WebView ek Activity ke context se bana tha, aur har nayi
 * BrowserActivity par destroy + dobara bana (to prewarm bekaar tha) aur purani Activity leak hoti thi.
 * Ab: har BrowserActivity apna taaza WebView leti hai, aur detach() par WebView poori tarah
 * destroy ho jata hai (page, JS timers sab band). Pehla khulna tez rakhne ke liye
 * prewarm() sirf Chromium/CookieManager init kar deta hai.
 */
object WebViewPool {
    private var wv: WebView? = null
    private var appCtx: Context? = null

    /** Home par call hota hai: Chromium engine + cookie store pehle se tayyar. */
    @Synchronized
    fun prewarm(activity: Activity) {
        appCtx = activity.applicationContext
        try {
            CookieManager.getInstance() // WebView engine init
        } catch (ignored: Exception) {
        }
    }

    /** Naya, saaf WebView is Activity ke liye. Purana (agar bacha ho) destroy. */
    @Synchronized
    fun obtain(activity: Activity): WebView {
        appCtx = activity.applicationContext
        destroyCurrent()
        val w = WebView(activity)
        wv = w
        return w
    }

    /** Crash recovery: purana khatam, taaza banao. */
    @Synchronized
    fun replace(activity: Activity): WebView = obtain(activity)

    /** Close/destroy par: page band, timers band, WebView destroy. */
    @Synchronized
    fun detach() {
        destroyCurrent()
    }

    private fun destroyCurrent() {
        val w = wv ?: return
        wv = null
        val r = Runnable {
            try {
                (w.parent as? ViewGroup)?.removeView(w)
                w.stopLoading()
                w.loadUrl("about:blank")
                w.onPause()
                w.destroy()
            } catch (ignored: Exception) {
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) r.run()
        else Handler(Looper.getMainLooper()).post(r)
    }

    /**
     * DEEP CLEAR: cache + form data + history. Cache app-wide hai, isliye WebView na ho
     * to ek temporary WebView se bhi saaf ho jata hai (BrowserSettings se call par bhi kaam karta hai).
     */
    fun deepClear() {
        val r = Runnable {
            try {
                val live = wv
                if (live != null) {
                    live.clearCache(true)
                    live.clearFormData()
                    live.clearHistory()
                } else {
                    val ctx = appCtx ?: return@Runnable
                    val tmp = WebView(ctx)
                    tmp.clearCache(true)
                    tmp.clearFormData()
                    tmp.clearHistory()
                    tmp.destroy()
                }
            } catch (ignored: Exception) {
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) r.run()
        else Handler(Looper.getMainLooper()).post(r)
    }
}


