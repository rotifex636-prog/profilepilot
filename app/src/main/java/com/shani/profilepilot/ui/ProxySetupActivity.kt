package com.shani.profilepilot.ui

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shani.profilepilot.data.AppDatabase
import com.shani.profilepilot.data.ProxyHelper
import com.shani.profilepilot.data.ProxyProfile
import com.shani.profilepilot.databinding.ActivityProxySetupBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProxySetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProxySetupBinding
    private var accountId: Long = -1
    private var existingProxyId: Long? = null
    private val types = arrayOf("HTTP", "SOCKS5")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProxySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        accountId = intent.getLongExtra("account_id", -1)
        if (accountId == -1L) {
            finish()
            return
        }

        binding.typeSpinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, types)

        // v1.5: kill-switch toggle + saved proxies ki list (select karne ke liye)
        var spinnerReady = false
        var savedProxies: List<ProxyProfile> = emptyList()
        lifecycleScope.launch {
            val db = AppDatabase.get(this@ProxySetupActivity)
            val account = withContext(Dispatchers.IO) { db.accountDao().byId(accountId) }
            if (account == null) {
                finish()
                return@launch
            }
            savedProxies = withContext(Dispatchers.IO) { db.proxyDao().all() }
            binding.killSwitchToggle.isChecked = account.killSwitch

            val labels = mutableListOf("+ Nayi proxy banao")
            labels.addAll(savedProxies.map { "${it.name} (${it.type} ${it.host}:${it.port})" })
            binding.savedProxySpinner.adapter = ArrayAdapter(
                this@ProxySetupActivity,
                android.R.layout.simple_spinner_dropdown_item,
                labels
            )
            // Account ki current proxy select dikhao
            val curIdx = savedProxies.indexOfFirst { it.id == account.proxyId }
            if (curIdx >= 0) binding.savedProxySpinner.setSelection(curIdx + 1)
            spinnerReady = true
        }

        binding.savedProxySpinner.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long
                ) {
                    if (!spinnerReady) return
                    if (pos == 0) {
                        // Nayi proxy: form khali karo
                        existingProxyId = null
                        binding.profileNameInput.setText("")
                        binding.hostInput.setText("")
                        binding.portInput.setText("")
                        binding.usernameInput.setText("")
                        binding.passwordInput.setText("")
                        binding.typeSpinner.setSelection(0)
                    } else {
                        val proxy = savedProxies[pos - 1]
                        existingProxyId = proxy.id
                        binding.profileNameInput.setText(proxy.name)
                        binding.hostInput.setText(proxy.host)
                        binding.portInput.setText(proxy.port.toString())
                        binding.usernameInput.setText(proxy.username)
                        binding.passwordInput.setText(proxy.password)
                        binding.typeSpinner.setSelection(
                            types.indexOf(proxy.type).coerceAtLeast(0)
                        )
                    }
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }

        binding.testButton.setOnClickListener {
            val host = binding.hostInput.text.toString().trim()
            val port = binding.portInput.text.toString().trim().toIntOrNull()
            val type = binding.typeSpinner.selectedItem as String
            val user = binding.usernameInput.text.toString().trim()
            val pass = binding.passwordInput.text.toString()
            if (host.isEmpty() || port == null || port !in 1..65535) {
                Toast.makeText(this, "Host/port theek likho", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            binding.testButton.isEnabled = false
            binding.testResultText.visibility = View.GONE
            lifecycleScope.launch {
                val r = withContext(Dispatchers.IO) {
                    ProxyHelper.testFull(type, host, port, user, pass)
                }
                binding.testButton.isEnabled = true
                val sb = StringBuilder()
                if (!r.tcpOk) {
                    sb.append("✗ Proxy connect nahi ho saka\n")
                    r.error?.let { sb.append(it) }
                } else {
                    sb.append("✓ Proxy connect: ${r.tcpLatencyMs}ms\n")
                    if (r.httpOk) {
                        sb.append("✓ Internet via proxy: ${r.httpLatencyMs}ms\n")
                        sb.append("IP: ${r.exitIp}\n")
                        if (r.httpLatencyMs < 1500) {
                            sb.append("✓ Speed theek hai — use kar sakte ho")
                        } else {
                            sb.append("⚠ Slow hai (${r.httpLatencyMs}ms) — behtar proxy lagao, warna net slow lagega")
                        }
                    } else {
                        sb.append("✗ Proxy se internet nahi chala\n")
                        r.error?.let { sb.append(it) }
                        sb.append("\nDNS ya username/password ka masla ho sakta hai")
                    }
                }
                binding.testResultText.text = sb.toString()
                binding.testResultText.visibility = View.VISIBLE
            }
        }

        binding.saveButton.setOnClickListener {
            val name = binding.profileNameInput.text.toString().trim().ifEmpty { "Proxy" }
            val host = binding.hostInput.text.toString().trim()
            val port = binding.portInput.text.toString().trim().toIntOrNull()
            val user = binding.usernameInput.text.toString().trim()
            val pass = binding.passwordInput.text.toString()
            val type = binding.typeSpinner.selectedItem as String
            if (host.isEmpty() || port == null || port !in 1..65535) {
                Toast.makeText(this, "Host/port theek likho", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                val db = AppDatabase.get(this@ProxySetupActivity)
                val newId = withContext(Dispatchers.IO) {
                    val profile = ProxyProfile(
                        id = existingProxyId ?: 0L,
                        name = name,
                        type = type,
                        host = host,
                        port = port,
                        username = user,
                        password = pass
                    )
                    if (existingProxyId != null) {
                        db.proxyDao().update(profile)
                        existingProxyId!!
                    } else {
                        db.proxyDao().insert(profile)
                    }
                }
                val killOn = binding.killSwitchToggle.isChecked
                withContext(Dispatchers.IO) {
                    val account = db.accountDao().byId(accountId)
                    if (account != null) {
                        db.accountDao().update(
                            account.copy(proxyId = newId, killSwitch = killOn)
                        )
                    }
                }
                Toast.makeText(this@ProxySetupActivity, "Proxy save ho gaya ✓", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        binding.noProxyButton.setOnClickListener {
            lifecycleScope.launch {
                val db = AppDatabase.get(this@ProxySetupActivity)
                withContext(Dispatchers.IO) {
                    val account = db.accountDao().byId(accountId)
                    if (account != null) db.accountDao().update(account.copy(proxyId = null))
                }
                Toast.makeText(this@ProxySetupActivity, "Proxy hata diya", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}


==========================================
