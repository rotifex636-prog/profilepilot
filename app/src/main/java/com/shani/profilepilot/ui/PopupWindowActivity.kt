package com.shani.profilepilot.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.os.Message
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

/**
 * v1.7: REAL popup window (Accounts Center, login exchange wagera).
 *
 * BrowserActivity.onCreateWindow ek pehle se configured WebView yahan chhor jata hai
 * (account ki UA, proxy-auth, third-party cookies sab laga hua). Cookies app-wide shared hain.
 * v1.7 me: popup ke andar window.open() bhi chalta hai, aur band hone par cookies flush hoti hain.
 */
class PopupWindowActivity : AppCompatActivity() {

    companion object {
        /** BrowserActivity.onCreateWindow yahan configured WebView rakhta hai. */
        var pendingWebView: WebView? = null
    }

    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#1877F2"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(8, 8, 8, 8)
        }
        val closeBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(Color.TRANSPARENT)
            setColorFilter(Color.WHITE)
            contentDescription = "Band karo"
            setOnClickListener { finish() }
        }
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ).apply { setMargins(16, 0, 8, 0) }
            progressTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
        }
        topBar.addView(closeBtn)
        topBar.addView(progress)
        root.addView(topBar)

        val container = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            setBackgroundColor(Color.WHITE)
        }
        root.addView(container)
        setContentView(root)

        val wv = pendingWebView ?: WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setSupportMultipleWindows(true)
            settings.javaScriptCanOpenWindowsAutomatically = true
            webViewClient = WebViewClient()
        }
        pendingWebView = null
        webView = wv

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }

            override fun onCloseWindow(window: WebView) {
                finish()
            }

            // Popup ke andar se window.open(): URL isi popup me load karo.
            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message
            ): Boolean {
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                val tmp = WebView(this@PopupWindowActivity)
                tmp.settings.javaScriptEnabled = true
                tmp.settings.userAgentString = wv.settings.userAgentString
                tmp.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        v: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        wv.loadUrl(request.url.toString())
                        v.post {
                            try { v.destroy() } catch (ignored: Exception) {}
                        }
                        return true
                    }
                }
                transport.webView = tmp
                resultMsg.sendToTarget()
                return true
            }
        }

        (wv.parent as? ViewGroup)?.removeView(wv)
        container.addView(
            wv,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        intent.getStringExtra("url")?.let { url ->
            if (url.isNotBlank()) wv.loadUrl(url)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val w = webView
                if (w != null && w.canGoBack()) w.goBack() else finish()
            }
        })
    }

    override fun onDestroy() {
        try {
            CookieManager.getInstance().flush() // login exchange ki cookies disk par
        } catch (ignored: Exception) {
        }
        try {
            webView?.destroy()
        } catch (ignored: Exception) {
        }
        webView = null
        if (pendingWebView != null) pendingWebView = null
        super.onDestroy()
    }
}


==========================================
