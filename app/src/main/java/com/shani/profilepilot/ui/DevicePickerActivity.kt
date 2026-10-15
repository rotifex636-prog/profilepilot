package com.shani.profilepilot.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import com.shani.profilepilot.data.AppDatabase
import com.shani.profilepilot.data.DeviceModel
import com.shani.profilepilot.data.DeviceModels
import com.shani.profilepilot.databinding.ActivityDevicePickerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DevicePickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDevicePickerBinding
    private var accountId: Long = -1
    private var full: List<DeviceModel> = emptyList()
    private lateinit var adapter: DeviceGroupAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDevicePickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        accountId = intent.getLongExtra("account_id", -1)
        if (accountId == -1L) {
            finish()
            return
        }

        full = DeviceModels.models
        adapter = DeviceGroupAdapter { model -> pickDevice(model) }
        binding.recycler.adapter = adapter
        // v1.5: brand folders
        adapter.setGroups(DeviceModels.brands)

        binding.search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = true

            override fun onQueryTextChange(query: String?): Boolean {
                val q = query.orEmpty()
                if (q.isBlank()) adapter.clearSearch()
                else adapter.setSearchResults(
                    full.filter { it.name.contains(q, ignoreCase = true) }
                )
                return true
            }
        })
    }

    private fun pickDevice(model: DeviceModel) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val db = AppDatabase.get(this@DevicePickerActivity)
                val account = db.accountDao().byId(accountId)
                if (account != null) {
                    db.accountDao().update(
                        account.copy(deviceModel = model.name, userAgent = model.userAgent)
                    )
                }
            }
            Toast.makeText(this@DevicePickerActivity, "Device set: ${model.name}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}


==========================================
