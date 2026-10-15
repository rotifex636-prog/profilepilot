package com.shani.profilepilot.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Message
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.shani.profilepilot.data.Account
import com.shani.profilepilot.data.AppDatabase
import com.shani.profilepilot.data.ProxyHelper
import com.shani.profilepilot.data.ProxyProfile
import com.shani.profilepilot.data.SessionManager
import com.shani.profilepilot.databinding.ActivityBrowserBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Per-account WebView browser (v1.7 patched).
 *
 * v1.7 fixes (Account Center / popup / login problems):
 *  - javaScriptCanOpenWindowsAutomatically = true (window.open bina isky Accounts Center nahi khulta tha)
 *  - UA se "; wv" hata diya (Facebook WebView pehchan kar features band kar deta hai)
 *  - Popup ko account ki UA + proxy-auth + cookies milti hain; popup ke andar popup bhi chalta hai
 *  - Sheet se "Open" dabane par ab SessionManager.switchTo khud hota hai (pehle doosre account ki cookies aa jati thin)
 *  - fb:// / intent:// / fb-messenger:// native app kholne ke bajaye web par jate hain (IP leak band)
 *  - AUTO/MANUAL location ab document-start par inject hoti hai (asli GPS leak nahi)
 *  - File chooser ko storage permission nahi chahiye (system picker)
 *  - Back button OnBackPressedCallback se
 */
class BrowserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBrowserBinding
    private var accountId: Long = -1
    private var currentAccount: Account? = null
    private var currentProxy: ProxyProfile? = null
    private var webView: WebView? = null
    private var hidden = false

    /** Is account ke liye final UA — popup me bhi yahi lagti hai. */
    private var currentUa: String = ""
    private var locationJs: String? = null
    private var docStartInstalled = false

    // ---------- file upload (system picker, permission ki zaroorat nahi) ----------
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            filePathCallback?.onReceiveValue(uris)
            filePathCallback = null
        }

    companion object {
        private const val MENU_HIDE = 2001
        private const val MENU_CLOSE = 2002
        private const val MENU_CLEAR_DATA = 2003
        private const val MENU_REFRESH = 2004
        private const val MENU_BROWSER_INFO = 2005
        private const val MENU_ACCOUNTS_CENTER = 2006
        private const val MENU_LOGIN_SESSIONS = 2007
        private const val MENU_MESSENGER = 2008

        /**
         * false (default) = fb:// intent:// fb-messenger:// links native app me NAHI khulte
         * (native app proxy ke bahar chalti hai => IP leak + doosre account ka login).
         * true karoge to purana v1.6.0 behavior wapas aa jayega.
         */
        private const val ALLOW_NATIVE_APPS = false

        private const val FB_HOME = "https://m.facebook.com/"
        private const val WEB_MESSAGES = "https://m.facebook.com/messages/"
        private const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0.0.0 Safari/537.36"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        accountId = intent.getLongExtra("account_id", -1)
        if (accountId == -1L) {
            finish()
            return
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { doClose() }
        binding.resumeButton.setOnClickListener { setHidden(false) }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val wv = webView
                if (wv != null && wv.canGoBack() && !hidden) wv.goBack() else doClose()
            }
        })

        lifecycleScope.launch {
            val db = AppDatabase.get(this@BrowserActivity)
            val account = withContext(Dispatchers.IO) { db.accountDao().byId(accountId) }
            if (account == null) {
                finish()
                return@launch
            }
            val proxy = withContext(Dispatchers.IO) {
                account.proxyId?.let { db.proxyDao().byId(it) }
            }
            binding.toolbar.title = account.name
            setupWebView(account, proxy)
        }
    }

    // ------------------------------------------------------------------
    // Setup
    // ------------------------------------------------------------------

    private fun setupWebView(account: Account, proxy: ProxyProfile?) {
        currentAccount = account
        currentProxy = proxy
        lifecycleScope.launch {
            // 1) SESSION ISOLATION: agar ye account abhi live nahi hai to pehle switch karo.
            //    (AccountOptionsSheet / Settings se "Open" par pehle ye step hota hi nahi tha.)
            if (SessionManager.activeAccountId != account.id) {
                withContext(Dispatchers.IO) { SessionManager.switchTo(this@BrowserActivity, account) }
            }

            // 2) PROXY: kisi bhi page se pehle (IO par, 3s tak block ho sakta hai)
            val proxyOk = withContext(Dispatchers.IO) {
                if (account.proxyId != null) {
                    proxy != null && ProxyHelper.applyProxy(proxy)
                } else {
                    ProxyHelper.clearProxy()
                    true
                }
            }
            if (account.proxyId != null && !proxyOk) {
                if (account.killSwitch) {
                    MaterialAlertDialogBuilder(this@BrowserActivity)
                        .setTitle("Proxy error")
                        .setMessage(
                            "Is account ka proxy connect nahi ho saka. " +
                                "Kill-switch ON hai: direct internet use nahi hoga."
                        )
                        .setPositiveButton("OK") { _, _ -> finish() }
                        .setCancelable(false)
                        .show()
                    return@launch
                } else {
                    ProxyHelper.clearProxy()
                    Toast.makeText(
                        this@BrowserActivity,
                        "Proxy fail — kill-switch OFF hai, direct net use ho raha hai",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            // 3) WebView
            val wv = WebViewPool.obtain(this@BrowserActivity)
            webView = wv
            attachWebView(wv)
            configureMain(wv, account)

            val startUrl = intent.getStringExtra("start_url")
                ?: account.startPage.ifEmpty { FB_HOME }
            wv.loadUrl(startUrl)
        }
    }

    private fun attachWebView(wv: WebView) {
        (wv.parent as? ViewGroup)?.removeView(wv)
        binding.webContainer.removeAllViews()
        binding.webContainer.addView(
            wv,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    private fun configureMain(wv: WebView, account: Account) {
        applySettings(wv, account)
        wv.webViewClient = AccountWebViewClient()
        wv.webChromeClient = AccountWebChromeClient()

        // Location override: document-start par inject (sab se bharosemand), warna onPageStarted.
        locationJs = locationScript(account)
        docStartInstalled = false
        val js = locationJs
        if (js != null && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            try {
                WebViewCompat.addDocumentStartJavaScript(wv, js, setOf("*"))
                docStartInstalled = true
            } catch (ignored: Exception) {
            }
        }
    }

    /** Facebook "; wv" wali UA par WebView samajh kar kai cheezein band kar deta hai — hata do. */
    private fun buildUserAgent(account: Account): String {
        if (account.desktopMode) return DESKTOP_UA
        if (account.userAgent.isNotEmpty()) return account.userAgent
        return WebSettings.getDefaultUserAgent(this)
            .replace("; wv", "")
            .replace(" Version/4.0", "")
    }

    private fun applySettings(wv: WebView, account: Account) {
        val s: WebSettings = wv.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        // Accounts Center jaisi screens window.open() use karti hain:
        s.setSupportMultipleWindows(true)
        s.javaScriptCanOpenWindowsAutomatically = true // <-- ye pehle missing tha
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.cacheMode = WebSettings.LOAD_DEFAULT

        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        try {
            cm.setAcceptThirdPartyCookies(wv, true)
        } catch (ignored: Exception) {
        }

        // Har dafa explicitly set (pichle account ki settings na reh jayen)
        currentUa = buildUserAgent(account)
        s.userAgentString = currentUa
        s.textZoom = account.textZoom
        s.loadsImagesAutomatically = !account.dataSaver
        s.blockNetworkImage = account.dataSaver
        s.useWideViewPort = true
        s.loadWithOverviewMode = account.desktopMode
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.mediaPlaybackRequiresUserGesture = false
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(s, account.darkMode)
        }
    }

    private fun locationScript(account: Account): String? {
        if (account.locationMode == "OFF") return null
        val lat = account.locationLat
        val lng = account.locationLng
        if (lat.isNaN() || lng.isNaN() || (lat == 0.0 && lng == 0.0)) return null
        return """
            (function(){
              try {
                var lat=$lat, lng=$lng;
                function pos(){ return {coords:{latitude:lat,longitude:lng,accuracy:20,
                  altitude:null,altitudeAccuracy:null,heading:null,speed:null},timestamp:Date.now()}; }
                var g = {
                  getCurrentPosition:function(ok){ if(ok) setTimeout(function(){ ok(pos()); },0); },
                  watchPosition:function(ok){ if(ok) setTimeout(function(){ ok(pos()); },0); return 1; },
                  clearWatch:function(){}
                };
                Object.defineProperty(navigator,'geolocation',{value:g,configurable:true});
              } catch(e) {}
            })();
        """.trimIndent()
    }

    // ------------------------------------------------------------------
    // Link handling (shared by main + popup)
    // ------------------------------------------------------------------

    /** true = link hum ne sambhal liya (WebView na load kare). */
    private fun handleUrl(view: WebView, url: String): Boolean {
        val lower = url.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://")) return false
        if (lower.startsWith("about:") || lower.startsWith("data:") ||
            lower.startsWith("blob:") || lower.startsWith("javascript:")
        ) return false
        if (lower.startsWith("file:") || lower.startsWith("content:")) return true
        if (lower.startsWith("tel:") || lower.startsWith("mailto:") || lower.startsWith("sms:")) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (ignored: Exception) {
            }
            return true
        }
        if (ALLOW_NATIVE_APPS) return openExternalLink(url)

        // Isolation: native app proxy ke bahar chalti hai — web equivalent par bhejo.
        if (lower.startsWith("intent:")) {
            val fallback = try {
                Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                    .getStringExtra("browser_fallback_url")
            } catch (ignored: Exception) {
                null
            }
            if (!fallback.isNullOrEmpty() &&
                (fallback.startsWith("https://") || fallback.startsWith("http://"))
            ) {
                view.loadUrl(fallback)
                return true
            }
        }
        if (lower.contains("messenger") || lower.contains("/messages")) {
            view.loadUrl(WEB_MESSAGES)
            return true
        }
        Toast.makeText(this, "Ye link app me khulta hai — isolation ke liye band hai", Toast.LENGTH_SHORT).show()
        return true
    }

    /** Sirf ALLOW_NATIVE_APPS = true par use hota hai (purana v1.6.0 behavior). */
    private fun openExternalLink(url: String): Boolean {
        try {
            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            intent.component = null
            intent.selector = null
            startActivity(intent)
            return true
        } catch (ignored: Exception) {
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            return true
        } catch (ignored: Exception) {
        }
        Toast.makeText(this, "Ye link kholne wali app installed nahi hai", Toast.LENGTH_SHORT).show()
        return true
    }

    // ------------------------------------------------------------------
    // WebViewClient (main)
    // ------------------------------------------------------------------

    private fun proxyAuth(handler: HttpAuthHandler, host: String) {
        val p = currentProxy
        // Credentials SIRF proxy ko do — kisi aam website ko nahi.
        if (p != null && p.username.isNotEmpty() && host.equals(p.host, ignoreCase = true)) {
            handler.proceed(p.username, p.password)
        } else {
            handler.cancel()
        }
    }

    private inner class AccountWebViewClient : WebViewClient() {

        override fun onReceivedHttpAuthRequest(
            view: WebView, handler: HttpAuthHandler, host: String, realm: String
        ) = proxyAuth(handler, host)

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            handleUrl(view, request.url.toString())

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel()
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            lifecycleScope.launch(Dispatchers.Main) {
                Toast.makeText(
                    this@BrowserActivity,
                    "Browser crash hua — dobara load ho raha hai",
                    Toast.LENGTH_SHORT
                ).show()
                recreateCrashedWebView()
            }
            return true
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            val js = locationJs
            if (js != null && !docStartInstalled) view.evaluateJavascript(js, null)
        }

        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            CookieManager.getInstance().flush()
            if (url.contains("facebook.com")) runBadgeScrape(accountId)
            if (url.contains("notifications")) {
                lifecycleScope.launch(Dispatchers.IO) {
                    AppDatabase.get(this@BrowserActivity).accountDao().setNotif(accountId, 0, System.currentTimeMillis())
                }
            }
            if (url.contains("messages") || url.contains("messenger")) {
                lifecycleScope.launch(Dispatchers.IO) {
                    AppDatabase.get(this@BrowserActivity).accountDao().setMsg(accountId, 0, System.currentTimeMillis())
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Popup (Accounts Center etc.)
    // ------------------------------------------------------------------

    private fun popupClient(): WebViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean =
            handleUrl(v, request.url.toString())

        override fun onReceivedHttpAuthRequest(
            view: WebView, handler: HttpAuthHandler, host: String, realm: String
        ) = proxyAuth(handler, host)

        override fun onReceivedSslError(v: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel()
        }

        override fun onPageFinished(view: WebView, url: String) {
            CookieManager.getInstance().flush()
        }
    }

    private fun configurePopup(popup: WebView) {
        val s = popup.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.javaScriptCanOpenWindowsAutomatically = true
        s.setSupportMultipleWindows(true)
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.mediaPlaybackRequiresUserGesture = false
        if (currentUa.isNotEmpty()) s.userAgentString = currentUa // account ki UA, "; wv" ke baghair
        s.textZoom = currentAccount?.textZoom ?: 100
        s.useWideViewPort = true
        try {
            CookieManager.getInstance().setAcceptThirdPartyCookies(popup, true)
        } catch (ignored: Exception) {
        }
        popup.webViewClient = popupClient()
    }

    private inner class AccountWebChromeClient : WebChromeClient() {

        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message
        ): Boolean {
            val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
            val popup = WebView(this@BrowserActivity)
            configurePopup(popup)
            PopupWindowActivity.pendingWebView = popup
            try {
                startActivity(Intent(this@BrowserActivity, PopupWindowActivity::class.java))
            } catch (e: Exception) {
                PopupWindowActivity.pendingWebView = null
                try { popup.destroy() } catch (ignored: Exception) {}
                return false
            }
            transport.webView = popup
            resultMsg.sendToTarget()
            return true
        }

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            val bar = binding.progressBar
            if (newProgress in 1..99) {
                if (bar.visibility != View.VISIBLE) bar.visibility = View.VISIBLE
                bar.progress = newProgress
            } else {
                bar.visibility = View.GONE
            }
        }

        override fun onGeolocationPermissionsShowPrompt(
            origin: String,
            callback: GeolocationPermissions.Callback
        ) {
            // Asli GPS tab hi do jab hamari fake location inject ho rahi ho.
            callback.invoke(origin, locationJs != null, false)
        }

        override fun onShowFileChooser(
            view: WebView,
            callback: ValueCallback<Array<Uri>>,
            params: FileChooserParams
        ): Boolean {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = callback
            return try {
                filePickerLauncher.launch(params.createIntent())
                true
            } catch (e: Exception) {
                filePathCallback = null
                callback.onReceiveValue(null)
                false
            }
        }
    }

    // ------------------------------------------------------------------
    // Badges (last-seen counts, sirf browser khula ho tab update hote hain)
    // ------------------------------------------------------------------
    private val JS_BADGES = """
        (function(){
          function c(keys){
            var ls=document.querySelectorAll('a[href]');
            for(var i=0;i<ls.length;i++){
              var h=ls[i].getAttribute('href')||'';
              for(var k=0;k<keys.length;k++){
                if(h.indexOf(keys[k])!==-1){
                  var t=ls[i].innerText||'';
                  var mt=t.match(/\d+/);
                  if(mt) return parseInt(mt[0],10);
                  var b=ls[i].querySelector('span');
                  if(b){
                    var m2=(b.innerText||'').match(/\d+/);
                    if(m2) return parseInt(m2[0],10);
                  }
                  return 0;
                }
              }
            }
            return -1;
          }
          var n=c(['notifications']);
          var m=c(['messages','messenger']);
          return n+'|'+m;
        })()
    """.trimIndent()

    private fun runBadgeScrape(id: Long) {
        val wv = webView ?: return
        wv.evaluateJavascript(JS_BADGES) { result ->
            val clean = result?.trim()?.trim('"') ?: return@evaluateJavascript
            val parts = clean.split("|")
            if (parts.size != 2) return@evaluateJavascript
            val n = parts[0].toIntOrNull() ?: return@evaluateJavascript
            val m = parts[1].toIntOrNull() ?: return@evaluateJavascript
            lifecycleScope.launch(Dispatchers.IO) {
                // Targeted update: cookiesJson wagera overwrite nahi hota
                val dao = AppDatabase.get(this@BrowserActivity).accountDao()
                val now = System.currentTimeMillis()
                if (n >= 0) dao.setNotif(id, n, now)
                if (m >= 0) dao.setMsg(id, m, now)
            }
        }
    }

    // ------------------------------------------------------------------
    // Hide / close
    // ------------------------------------------------------------------

    private fun setHidden(hide: Boolean) {
        hidden = hide
        val wv = webView ?: return
        if (hide) {
            wv.onPause()
            wv.pauseTimers()
            binding.hiddenOverlay.visibility = View.VISIBLE
        } else {
            wv.onResume()
            wv.resumeTimers()
            binding.hiddenOverlay.visibility = View.GONE
        }
    }

    private fun doClose() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { SessionManager.snapshotActive(this@BrowserActivity) }
            WebViewPool.detach()
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        // App background/kill se pehle cookies DB me mehfooz (login kabhi zaya na ho).
        // NOTE: WebView.onPause() yahan jaan-boojh kar nahi — popup/login exchange ko timers chahiye.
        val app = applicationContext
        CoroutineScope(Dispatchers.IO).launch { SessionManager.snapshotActive(app) }
    }

    // ------------------------------------------------------------------
    // Menu
    // ------------------------------------------------------------------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_HIDE, 0, "Hide").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        menu.add(0, MENU_CLOSE, 1, "Close").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        menu.add(0, MENU_CLEAR_DATA, 2, "Clear data").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(0, MENU_REFRESH, 3, "Refresh").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(0, MENU_BROWSER_INFO, 4, "Browser info").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(0, MENU_ACCOUNTS_CENTER, 5, "Accounts Center").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(0, MENU_LOGIN_SESSIONS, 6, "Login Sessions").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(0, MENU_MESSENGER, 7, "Messenger").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            MENU_HIDE -> { setHidden(true); true }
            MENU_CLOSE -> { doClose(); true }
            MENU_CLEAR_DATA -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Clear data?")
                    .setMessage("Is account ka browser data clear ho jayega (logout ho jayega).")
                    .setPositiveButton("Clear") { _, _ -> clearDataAndReload() }
                    .setNegativeButton("Cancel", null)
                    .show()
                true
            }
            MENU_REFRESH -> { webView?.reload(); true }
            MENU_BROWSER_INFO -> { showBrowserInfo(); true }
            MENU_ACCOUNTS_CENTER -> {
                webView?.loadUrl("https://accountscenter.facebook.com/?__wblt=1")
                true
            }
            MENU_LOGIN_SESSIONS -> {
                webView?.loadUrl("https://m.facebook.com/settings/security/")
                true
            }
            MENU_MESSENGER -> {
                if (ALLOW_NATIVE_APPS) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("fb-messenger://")))
                    } catch (ignored: Exception) {
                        webView?.loadUrl("https://www.messenger.com/")
                    }
                } else {
                    // Web Messenger — isi account ki session, isi proxy se
                    webView?.loadUrl(WEB_MESSAGES)
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun recreateCrashedWebView() {
        val account = currentAccount ?: return
        val crashedUrl = webView?.url
        try {
            binding.webContainer.removeAllViews()
        } catch (ignored: Exception) {
        }
        val wv = WebViewPool.replace(this)
        webView = wv
        attachWebView(wv)
        configureMain(wv, account)
        wv.loadUrl(crashedUrl ?: account.startPage.ifEmpty { FB_HOME })
    }

    private fun showBrowserInfo() {
        val account = currentAccount ?: return
        val proxy = currentProxy
        val locMode = when (account.locationMode) {
            "AUTO" -> "Auto (proxy IP se)"
            "MANUAL" -> "Manual (${account.locationLabel.ifEmpty { "${account.locationLat}, ${account.locationLng}" }})"
            else -> "Off"
        }
        val info = StringBuilder()
        info.append("Account: ${account.name}\n")
        info.append("Proxy: ${proxy?.let { "${it.name} (${it.type} ${it.host}:${it.port})" } ?: "koi nahi (direct)"}\n")
        info.append("Kill-switch: ${if (account.killSwitch) "ON" else "OFF"}\n")
        info.append("Device: ${account.deviceModel.ifEmpty { "default (phone ka apna)" }}\n")
        info.append("Location: $locMode\n")
        info.append("Start page: ${account.startPage.ifEmpty { FB_HOME }}\n")
        info.append("Desktop mode: ${if (account.desktopMode) "on" else "off"}\n")
        info.append("FB dark mode: ${if (account.darkMode) "on" else "off"}\n")
        info.append("Text size: ${account.textZoom}%\n")
        info.append("Data saver: ${if (account.dataSaver) "on" else "off"}\n")
        info.append("User agent (asal lagi hui):\n$currentUa")
        MaterialAlertDialogBuilder(this)
            .setTitle("Browser info")
            .setMessage(info.toString())
            .setPositiveButton("OK", null)
            .show()
    }

    private fun clearDataAndReload() {
        val account = currentAccount ?: return
        lifecycleScope.launch {
            SessionManager.clearAccountData(this@BrowserActivity, account)
            WebViewPool.deepClear()
            webView?.loadUrl(account.startPage.ifEmpty { FB_HOME })
            Toast.makeText(this@BrowserActivity, "Data clear ho gaya", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        WebViewPool.detach()
        super.onDestroy()
    }
}


==========================================
