package com.shani.profilepilot.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shani.profilepilot.data.AppDatabase
import com.shani.profilepilot.databinding.ActivityFolderBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FolderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFolderBinding
    private var folderId: Long = -1
    private lateinit var adapter: AccountAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFolderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        folderId = intent.getLongExtra("folder_id", -1)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Reuse Home's AccountAdapter: accounts only, no folder tiles, no add tile.
        adapter = AccountAdapter(
            this,
            onAccountClick = { accountId ->
                startActivity(
                    Intent(this, BrowserActivity::class.java)
                        .putExtra("account_id", accountId)
                )
            },
            onAccountLongClick = { accountId ->
                AccountOptionsSheet.newInstance(accountId)
                    .show(supportFragmentManager, "opts")
                true
            },
            onFolderClick = {},
            onAddClick = {}
        )
        binding.recycler.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val db = AppDatabase.get(this@FolderActivity)
            val folder = withContext(Dispatchers.IO) { db.folderDao().byId(folderId) }
            if (folder == null) {
                finish()
                return@launch
            }
            binding.toolbar.title = folder.name
            val accounts = withContext(Dispatchers.IO) { db.accountDao().inFolder(folderId) }
            adapter.submit(accounts.map { HomeItem.AccountItem(it) })
        }
    }
}


==========================================
