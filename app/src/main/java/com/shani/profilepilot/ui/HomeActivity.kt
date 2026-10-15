package com.shani.profilepilot.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.shani.profilepilot.R
import com.shani.profilepilot.data.AppDatabase
import com.shani.profilepilot.data.SessionManager
import com.shani.profilepilot.data.SettingsManager
import com.shani.profilepilot.databinding.ActivityHomeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Home: dense 5-column grid of account tiles + folder tiles + add tile.
 * Data loads in onResume so badge counts and names stay fresh after
 * returning from the browser or any settings screen.
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var adapter: AccountAdapter
    private var currentTheme = "dark_pro"

    companion object {
        private const val MENU_THEME = 1001
        private const val MENU_NEW_FOLDER = 1002
        private const val MENU_BACKUP = 1003
        const val MAX_ACCOUNTS = 50
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // v1.5 SPEED: shared WebView pehle se banao — pehla browser tez khulega
        binding.root.post { WebViewPool.prewarm(this@HomeActivity) }

        adapter = AccountAdapter(
            context = this,
            onAccountClick = { id -> openAccount(id) },
            onAccountLongClick = { id ->
                AccountOptionsSheet.newInstance(id).show(supportFragmentManager, "opts")
                true
            },
            onFolderClick = { folderId ->
                startActivity(
                    Intent(this, FolderActivity::class.java)
                        .putExtra("folder_id", folderId)
                )
            },
            onAddClick = { openAddAccount() }
        )
        binding.grid.layoutManager = GridLayoutManager(this, 5)
        binding.grid.adapter = adapter

        // Theme icon must reflect the stored theme before the menu draws.
        lifecycleScope.launch {
            currentTheme = SettingsManager.get(this@HomeActivity).themeFlow.first()
            invalidateOptionsMenu()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshGrid()
    }

    private fun refreshGrid() {
        lifecycleScope.launch {
            val db = AppDatabase.get(this@HomeActivity)
            val (accounts, folders, counts) = withContext(Dispatchers.IO) {
                val accs = db.accountDao().homeAccounts()
                val folds = db.folderDao().all()
                val perFolder = folds.associate { f -> f.id to db.accountDao().inFolder(f.id).size }
                Triple(accs, folds, perFolder)
            }

            val rows = ArrayList<HomeItem>(folders.size + accounts.size + 1)
            folders.forEach { f -> rows.add(HomeItem.FolderItem(f, counts[f.id] ?: 0)) }
            accounts.forEach { a -> rows.add(HomeItem.AccountItem(a)) }
            if (accounts.size < MAX_ACCOUNTS) rows.add(HomeItem.AddItem)

            binding.counter.text = "${accounts.size} / $MAX_ACCOUNTS"
            adapter.submit(rows)
        }
    }

    private fun openAccount(id: Long) {
        lifecycleScope.launch {
            val db = AppDatabase.get(this@HomeActivity)
            val account = withContext(Dispatchers.IO) { db.accountDao().byId(id) } ?: return@launch
            withContext(Dispatchers.IO) {
                db.accountDao().setLastOpened(account.id, System.currentTimeMillis())
                SessionManager.switchTo(this@HomeActivity, account)
            }
            startActivity(
                Intent(this@HomeActivity, BrowserActivity::class.java)
                    .putExtra("account_id", account.id)
            )
        }
    }

    /**
     * Add tile: simple name dialog -> insert the new account slot on Home.
     * Proxy, device model and the rest are configured later from the
     * account's options sheet (ProxySetupActivity etc.).
     */
    private fun openAddAccount() {
        val input = android.widget.EditText(this).apply {
            hint = "Account name"
            setSingleLine()
        }
        val padded = android.widget.FrameLayout(this).apply {
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("New account")
            .setView(padded)
            .setPositiveButton("Add") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        AppDatabase.get(this@HomeActivity).accountDao()
                            .insert(com.shani.profilepilot.data.Account(name = name))
                    }
                    refreshGrid()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------- toolbar menu (built in code; no menu XML dependency) ----------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_THEME, 0, "Theme").apply {
            icon = ContextCompat.getDrawable(
                this@HomeActivity,
                if (currentTheme == "dark_pro") R.drawable.ic_sun else R.drawable.ic_moon
            )
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        menu.add(0, MENU_NEW_FOLDER, 1, "New folder").apply {
            icon = ContextCompat.getDrawable(this@HomeActivity, R.drawable.ic_folder)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        }
        menu.add(0, MENU_BACKUP, 2, "Backup").apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            MENU_THEME -> {
                lifecycleScope.launch {
                    val next = if (currentTheme == "dark_pro") "light_clean" else "dark_pro"
                    withContext(Dispatchers.IO) {
                        SettingsManager.get(this@HomeActivity).setTheme(next)
                    }
                    // THIS was missing in v1.0-v1.3: saving the preference alone
                    // never flips the UI. setDefaultNightMode applies it (and
                    // recreates activities itself).
                    AppCompatDelegate.setDefaultNightMode(
                        if (next == "dark_pro") AppCompatDelegate.MODE_NIGHT_YES
                        else AppCompatDelegate.MODE_NIGHT_NO
                    )
                    recreate()
                }
                true
            }
            MENU_NEW_FOLDER -> {
                NewFolderDialog().show(supportFragmentManager, "nf")
                true
            }
            MENU_BACKUP -> {
                startActivity(Intent(this, BackupActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}


==========================================
