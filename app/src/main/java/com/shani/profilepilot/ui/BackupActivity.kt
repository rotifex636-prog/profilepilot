package com.shani.profilepilot.ui

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.shani.profilepilot.data.BackupManager
import com.shani.profilepilot.databinding.ActivityBackupBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBackupBinding

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            uri?.let { doExport(it) }
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { confirmImport(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBackupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.exportButton.setOnClickListener {
            val pw = binding.passwordInput.text.toString()
            if (pw.length < 4) {
                Toast.makeText(this, "Password kam az kam 4 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            exportLauncher.launch("profilepilot-backup.bin")
        }

        binding.importButton.setOnClickListener {
            importLauncher.launch(arrayOf("application/octet-stream"))
        }
    }

    private fun doExport(uri: Uri) {
        val pw = binding.passwordInput.text.toString()
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                BackupManager.exportData(this@BackupActivity, uri, pw)
            }
            Toast.makeText(
                this@BackupActivity,
                if (ok) "Backup export ho gaya ✓" else "Export fail ✗",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun confirmImport(uri: Uri) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Import backup?")
            .setMessage("Purana data REPLACE ho jayega.")
            .setPositiveButton("Import") { _, _ -> doImport(uri) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun doImport(uri: Uri) {
        val pw = binding.passwordInput.text.toString()
        // NOTE: no device-lock — the encrypted backup restores on any Android 8+ phone.
        lifecycleScope.launch {
            val res = withContext(Dispatchers.IO) {
                BackupManager.importData(this@BackupActivity, uri, pw)
            }
            Toast.makeText(this@BackupActivity, res.message, Toast.LENGTH_LONG).show()
        }
    }
}


==========================================
