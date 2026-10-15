package com.shani.profilepilot.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shani.profilepilot.data.AppDatabase
import com.shani.profilepilot.databinding.ActivityNotesBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotesBinding
    private var accountId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        accountId = intent.getLongExtra("account_id", -1)
        if (accountId == -1L) {
            finish()
            return
        }

        lifecycleScope.launch {
            val account = withContext(Dispatchers.IO) {
                AppDatabase.get(this@NotesActivity).accountDao().byId(accountId)
            }
            if (account == null) {
                finish()
                return@launch
            }
            binding.notesInput.setText(account.notes)
        }

        binding.saveButton.setOnClickListener {
            val notes = binding.notesInput.text.toString()
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    val db = AppDatabase.get(this@NotesActivity)
                    val account = db.accountDao().byId(accountId)
                    if (account != null) db.accountDao().update(account.copy(notes = notes))
                }
                Toast.makeText(this@NotesActivity, "Notes save ho gayin ✓", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}


==========================================
