package com.shani.profilepilot.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shani.profilepilot.data.AppDatabase
import com.shani.profilepilot.R
import com.shani.profilepilot.databinding.ActivityLocationPickerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class LocationPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLocationPickerBinding
    private var accountId: Long = -1

    // Last result from "Detect from proxy IP"
    private var detectedLat = 0.0
    private var detectedLng = 0.0
    private var detectedLabel = ""

    companion object {
        // Matches data layer convention ("OFF", "AUTO", "MANUAL") used by BrowserActivity.
        const val MODE_OFF = "OFF"
        const val MODE_AUTO = "AUTO"
        const val MODE_MANUAL = "MANUAL"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLocationPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        accountId = intent.getLongExtra("account_id", -1)
        if (accountId == -1L) {
            finish()
            return
        }

        // Preload saved mode
        lifecycleScope.launch {
            val account = withContext(Dispatchers.IO) {
                AppDatabase.get(this@LocationPickerActivity).accountDao().byId(accountId)
            }
            if (account == null) {
                finish()
                return@launch
            }
            when (account.locationMode) {
                MODE_AUTO -> binding.radioAuto.isChecked = true
                MODE_MANUAL -> {
                    binding.radioManual.isChecked = true
                    binding.latInput.setText(account.locationLat.toString())
                    binding.lngInput.setText(account.locationLng.toString())
                }
                else -> binding.radioOff.isChecked = true
            }
            updateSections()
        }

        binding.modeGroup.setOnCheckedChangeListener { _, _ -> updateSections() }

        binding.detectButton.setOnClickListener { detectFromProxyIp() }

        binding.saveButton.setOnClickListener {
            val mode = when (binding.modeGroup.checkedRadioButtonId) {
                R.id.radioAuto -> MODE_AUTO
                R.id.radioManual -> MODE_MANUAL
                else -> MODE_OFF
            }
            var lat = 0.0
            var lng = 0.0
            var label = ""
            when (mode) {
                MODE_AUTO -> {
                    lat = detectedLat
                    lng = detectedLng
                    label = detectedLabel
                }
                MODE_MANUAL -> {
                    lat = binding.latInput.text.toString().toDoubleOrNull() ?: 0.0
                    lng = binding.lngInput.text.toString().toDoubleOrNull() ?: 0.0
                    label = "$lat, $lng"
                }
            }
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    val db = AppDatabase.get(this@LocationPickerActivity)
                    val account = db.accountDao().byId(accountId)
                    if (account != null) {
                        db.accountDao().update(
                            account.copy(
                                locationMode = mode,
                                locationLat = lat,
                                locationLng = lng,
                                locationLabel = label
                            )
                        )
                    }
                }
                Toast.makeText(this@LocationPickerActivity, "Location save ho gayi ✓", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun updateSections() {
        binding.autoSection.visibility =
            if (binding.radioAuto.isChecked) View.VISIBLE else View.GONE
        binding.manualSection.visibility =
            if (binding.radioManual.isChecked) View.VISIBLE else View.GONE
    }

    private fun detectFromProxyIp() {
        binding.detectResult.text = "Detect ho raha hai..."
        binding.detectButton.isEnabled = false
        lifecycleScope.launch {
            try {
                // Load the account's proxy (if assigned) so detection uses the PROXY's
                // exit IP — that's the IP Facebook will see, not the phone's own IP.
                val db = AppDatabase.get(this@LocationPickerActivity)
                val proxy = withContext(Dispatchers.IO) {
                    val acc = db.accountDao().byId(accountId)
                    acc?.proxyId?.let { db.proxyDao().byId(it) }
                }
                val json = withContext(Dispatchers.IO) {
                    // v1.4: ipwho.is (free HTTPS, no key). ip-api.com blocks its
                    // HTTPS endpoint for free users, which is why detect always
                    // failed in v1.0-v1.3.
                    val url = java.net.URL("https://ipwho.is/")
                    val conn = if (proxy != null) {
                        val ptype = if (proxy.type == "SOCKS5")
                            java.net.Proxy.Type.SOCKS else java.net.Proxy.Type.HTTP
                        if (proxy.username.isNotEmpty()) {
                            java.net.Authenticator.setDefault(
                                object : java.net.Authenticator() {
                                    override fun getPasswordAuthentication() =
                                        java.net.PasswordAuthentication(
                                            proxy.username,
                                            proxy.password.toCharArray()
                                        )
                                }
                            )
                        }
                        url.openConnection(
                            java.net.Proxy(
                                ptype,
                                java.net.InetSocketAddress(proxy.host, proxy.port)
                            )
                        )
                    } else {
                        url.openConnection()
                    } as java.net.HttpURLConnection
                    try {
                        conn.connectTimeout = 12000
                        conn.readTimeout = 12000
                        conn.inputStream.bufferedReader().readText()
                    } finally {
                        java.net.Authenticator.setDefault(null)
                        conn.disconnect()
                    }
                }
                val o = JSONObject(json)
                if (!o.optBoolean("success", true)) {
                    throw Exception(o.optString("message", "lookup fail"))
                }
                val city = o.optString("city", "")
                val cc = o.optString("country_code", "")
                detectedLat = o.optDouble("latitude", 0.0)
                detectedLng = o.optDouble("longitude", 0.0)
                detectedLabel = "$city, $cc".trim().trimStart(',').trim()
                val src = if (proxy != null) "Proxy IP" else "Phone IP (koi proxy set nahi)"
                binding.detectResult.text = "$src: $detectedLabel ($detectedLat, $detectedLng)"
            } catch (e: Exception) {
                binding.detectResult.text = "Detect fail — internet/proxy check karo"
            } finally {
                binding.detectButton.isEnabled = true
            }
        }
    }
}


