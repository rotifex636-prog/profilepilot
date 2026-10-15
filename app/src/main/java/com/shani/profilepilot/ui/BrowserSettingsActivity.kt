package com.shani.profilepilot.ui

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.shani.profilepilot.data.Account
import com.shani.profilepilot.data.AppDatabase
import com.shani.profilepilot.data.SessionManager
import com.shani.profilepilot.data.SettingsManager
import com.shani.profilepilot.R
import com.shani.profilepilot.databinding.ActivityBrowserSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BrowserSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBrowserSettingsBinding
    private var accountId: Long = -1
    private var account: Account? = null

    private val startPages = listOf("Feed", "Reels", "Watch", "Custom…")
    private val startUrls = mapOf(
        "Feed" to "https://m.facebook.com/",
        "Reels" to "https://m.facebook.com/reels/",
        "Watch" to "https://m.facebook.com/watch/"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBrowserSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        accountId = intent.getLongExtra("account_id", -1)
        if (accountId == -1L) {
            finish()
            return
        }

        // (a) App theme — preloaded from SettingsManager, writes through immediately
        var themeReady = false
        lifecycleScope.launch {
            val theme = SettingsManager.get(this@BrowserSettingsActivity).themeFlow.first()
            if (theme == "light_clean") binding.themeLightBtn.isChecked = true
            else binding.themeDarkBtn.isChecked = true
            themeReady = true
        }
        binding.themeGroup.setOnCheckedChangeListener { _, checkedId ->
            if (!themeReady) return@setOnCheckedChangeListener
            val theme = if (checkedId == R.id.themeDarkBtn) "dark_pro" else "light_clean"
                lifecycleScope.launch {
                    SettingsManager.get(this@BrowserSettingsActivity).setTheme(theme)
                    // v1.4 fix: actually apply the mode — recreate() alone kept the old UI
                    AppCompatDelegate.setDefaultNightMode(
                        if (theme == "dark_pro") AppCompatDelegate.MODE_NIGHT_YES
                        else AppCompatDelegate.MODE_NIGHT_NO
                    )
                    recreate()
                }
        }

        // (b) Start page spinner
        binding.startPageSpinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_item, startPages)

        lifecycleScope.launch {
            val db = AppDatabase.get(this@BrowserSettingsActivity)
            val acc = withContext(Dispatchers.IO) { db.accountDao().byId(accountId) }
            if (acc == null) {
                finish()
                return@launch
            }
            account = acc
            bindAccount(acc)
        }

        // (g) Clear data
        binding.clearDataButton.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Clear data?")
                .setMessage("Is account ka sara browser data clear ho jayega — cookies, cache, saved session. Is account logout ho jayega.")
                .setPositiveButton("Clear") { _, _ ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            val acc = account ?: return@withContext
                            SessionManager.clearAccountData(this@BrowserSettingsActivity, acc)
                        }
                        // v1.5 deep clear: pooled WebView ka cache/form/history bhi —
                        // Facebook ka koi hidden data na bache. (Settings mehfooz.)
                        WebViewPool.deepClear()
                        Toast.makeText(
                            this@BrowserSettingsActivity,
                            "Data clear ho gaya ✓",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // (h) Marketplace helper — shortcut into Facebook's own notification settings
        // NOTE: the actual notification toggles belong to Facebook; this button only
        // opens FB's own settings page inside this account's browser. Shani's tip:
        // keep ONLY Marketplace notifications on, everything else off, so the tile
        // badge effectively counts marketplace messages.
        binding.marketplaceButton.setOnClickListener {
            val intent = Intent(this, BrowserActivity::class.java)
                .putExtra("account_id", accountId)
                .putExtra("start_url", "https://m.facebook.com/settings/notifications")
            startActivity(intent)
        }
    }

    private fun bindAccount(acc: Account) {
        // (b) Start page: match saved URL to a preset, else Custom
        val preset = startUrls.entries.firstOrNull { it.value == acc.startPage }?.key
        var spinnerReady = false
        binding.startPageSpinner.setSelection(
            if (preset != null) startPages.indexOf(preset) else startPages.indexOf("Custom…")
        )
        binding.startPageSpinner.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: android.view.View?,
                    position: Int,
                    id: Long
                ) {
                    if (!spinnerReady) {
                        spinnerReady = true
                        return
                    }
                    val choice = startPages[position]
                    if (choice == "Custom…") showCustomStartPageDialog()
                    else updateAccount { copy(startPage = startUrls[choice] ?: acc.startPage) }
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }

        // (c) Mobile / Desktop
        binding.desktopSwitch.isChecked = acc.desktopMode
        binding.desktopSwitch.setOnCheckedChangeListener { _, checked ->
            updateAccount { copy(desktopMode = checked) }
        }

        // (d) Dark mode
        binding.darkSwitch.isChecked = acc.darkMode
        binding.darkSwitch.setOnCheckedChangeListener { _, checked ->
            updateAccount { copy(darkMode = checked) }
        }

        // (e) Text size slider 50–200, step 10
        binding.textZoomSlider.value = acc.textZoom.toFloat().coerceIn(50f, 200f)
        binding.textZoomLabel.text = "Text size: ${acc.textZoom}%"
        binding.textZoomSlider.addOnChangeListener { _, value, _ ->
            binding.textZoomLabel.text = "Text size: ${value.toInt()}%"
        }
        binding.textZoomSlider.addOnSliderTouchListener(
            object : com.google.android.material.slider.Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: com.google.android.material.slider.Slider) = Unit
                override fun onStopTrackingTouch(slider: com.google.android.material.slider.Slider) {
                    updateAccount { copy(textZoom = slider.value.toInt()) }
                }
            }
        )

        // (f) Data saver
        binding.dataSaverSwitch.isChecked = acc.dataSaver
        binding.dataSaverSwitch.setOnCheckedChangeListener { _, checked ->
            updateAccount { copy(dataSaver = checked) }
        }
    }

    private fun showCustomStartPageDialog() {
        val input = EditText(this).apply {
            hint = "https://..."
            setText(account?.startPage.orEmpty())
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Custom start page")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) updateAccount { copy(startPage = url) }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Mutate the cached account and write through to DB on IO. */
    private fun updateAccount(mutator: Account.() -> Account) {
        // v1.7: DB se taaza row read karke badlo (purana cached account cookies overwrite na kare)
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.get(this@BrowserSettingsActivity).accountDao()
            val fresh = dao.byId(accountId) ?: return@launch
            val updated = fresh.mutator()
            dao.update(updated)
            account = updated
        }
    }
}


