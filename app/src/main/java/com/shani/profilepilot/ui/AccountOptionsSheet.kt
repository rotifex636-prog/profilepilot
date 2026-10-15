package com.shani.profilepilot.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.shani.profilepilot.R
import com.shani.profilepilot.data.Account
import com.shani.profilepilot.data.AppDatabase
import com.shani.profilepilot.data.Folder
import com.shani.profilepilot.data.SessionManager
import com.shani.profilepilot.databinding.SheetAccountOptionsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bottom sheet with per-account quick actions.
 * Each row dismisses the sheet and opens its target screen with "account_id".
 */
class AccountOptionsSheet : BottomSheetDialogFragment() {

    private var _binding: SheetAccountOptionsBinding? = null
    private val binding get() = _binding!!
    private var accountId: Long = -1

    companion object {
        fun newInstance(accountId: Long): AccountOptionsSheet =
            AccountOptionsSheet().apply {
                arguments = Bundle().apply { putLong("account_id", accountId) }
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accountId = arguments?.getLong("account_id", -1) ?: -1
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetAccountOptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Header: name + last badge-update time.
        lifecycleScope.launch {
            val db = AppDatabase.get(requireContext())
            val account = withContext(Dispatchers.IO) { db.accountDao().byId(accountId) }
            if (account == null) {
                dismiss()
                return@launch
            }
            binding.accountName.text = account.name
            val lastSeen = maxOf(account.notifSeenAt, account.msgSeenAt)
            binding.badgeInfo.text = if (lastSeen > 0) {
                val fmt = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
                "Badge updated: ${fmt.format(Date(lastSeen))}"
            } else {
                getString(R.string.badge_never)
            }
        }

        binding.rowOpen.setOnClickListener { go(BrowserActivity::class.java) }
        binding.rowProxy.setOnClickListener { go(ProxySetupActivity::class.java) }
        binding.rowDevice.setOnClickListener { go(DevicePickerActivity::class.java) }
        binding.rowLocation.setOnClickListener { go(LocationPickerActivity::class.java) }
        binding.rowBrowser.setOnClickListener { go(BrowserSettingsActivity::class.java) }
        binding.rowRename.setOnClickListener { go(RenameStyleActivity::class.java) }
        binding.rowNotes.setOnClickListener { go(NotesActivity::class.java) }
        binding.rowMove.setOnClickListener { showMoveDialog() }
        binding.rowLogout.setOnClickListener { confirmLogout() }
    }

    private fun go(target: Class<*>) {
        dismiss()
        startActivity(
            Intent(requireContext(), target).putExtra("account_id", accountId)
        )
    }

    private fun showMoveDialog() {
        lifecycleScope.launch {
            val db = AppDatabase.get(requireContext())
            val (account, folders) = withContext(Dispatchers.IO) {
                db.accountDao().byId(accountId) to db.folderDao().all()
            }
            if (account == null) {
                dismiss()
                return@launch
            }
            val labels = ArrayList<String>(folders.size + 2)
            labels.add("Home")
            folders.forEach { labels.add(it.name) }
            labels.add("+ New folder…")

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Move to folder")
                .setItems(labels.toTypedArray()) { _, which ->
                    when {
                        which == 0 -> moveTo(account, null)
                        which <= folders.size -> moveTo(account, folders[which - 1].id)
                        else -> createFolderAndMove(account)
                    }
                }
                .show()
        }
    }

    private fun moveTo(account: Account, folderId: Long?) {
        lifecycleScope.launch(Dispatchers.IO) {
            AppDatabase.get(requireContext()).accountDao()
                .update(account.copy(folderId = folderId))
        }
        dismiss()
        Toast.makeText(requireContext(), "Moved", Toast.LENGTH_SHORT).show()
    }

    private fun createFolderAndMove(account: Account) {
        lifecycleScope.launch {
            val db = AppDatabase.get(requireContext())
            val newId = withContext(Dispatchers.IO) {
                db.folderDao().insert(Folder(name = "New folder", colorHex = "#F59E0B"))
            }
            withContext(Dispatchers.IO) {
                db.accountDao().update(account.copy(folderId = newId))
            }
            dismiss()
            Toast.makeText(requireContext(), "Moved", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Logout?")
            .setMessage("Is account ka saved session delete ho jayega.")
            .setPositiveButton("Logout") { _, _ ->
                lifecycleScope.launch {
                    val ctx = requireContext().applicationContext
                    val db = AppDatabase.get(ctx)
                    val account = withContext(Dispatchers.IO) { db.accountDao().byId(accountId) }
                    if (account != null) {
                        withContext(Dispatchers.IO) { SessionManager.logout(ctx, account) }
                    }
                    dismiss()
                    Toast.makeText(ctx, "Logged out", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}


==========================================
