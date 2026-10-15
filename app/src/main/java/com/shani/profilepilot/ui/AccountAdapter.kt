package com.shani.profilepilot.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.shani.profilepilot.R
import com.shani.profilepilot.data.Account
import com.shani.profilepilot.data.Folder
import com.shani.profilepilot.data.SessionManager
import com.shani.profilepilot.databinding.ItemAccountTileBinding
import com.shani.profilepilot.databinding.ItemAddTileBinding
import com.shani.profilepilot.databinding.ItemFolderTileBinding

/** Home grid rows: account tiles, folder tiles, then the add tile. */
sealed interface HomeItem {
    data class AccountItem(val account: Account) : HomeItem
    data class FolderItem(val folder: Folder, val count: Int) : HomeItem
    data object AddItem : HomeItem
}

class AccountAdapter(
    private val context: Context,
    private val onAccountClick: (Long) -> Unit,
    private val onAccountLongClick: (Long) -> Boolean,
    private val onFolderClick: (Long) -> Unit,
    private val onAddClick: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<HomeItem> = emptyList()

    companion object {
        private const val TYPE_ACCOUNT = 0
        private const val TYPE_FOLDER = 1
        private const val TYPE_ADD = 2
    }

    fun submit(newItems: List<HomeItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is HomeItem.AccountItem -> TYPE_ACCOUNT
        is HomeItem.FolderItem -> TYPE_FOLDER
        is HomeItem.AddItem -> TYPE_ADD
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_FOLDER -> FolderVH(ItemFolderTileBinding.inflate(inflater, parent, false))
            TYPE_ADD -> AddVH(ItemAddTileBinding.inflate(inflater, parent, false))
            else -> AccountVH(ItemAccountTileBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is HomeItem.AccountItem -> (holder as AccountVH).bind(item.account)
            is HomeItem.FolderItem -> (holder as FolderVH).bind(item.folder, item.count)
            is HomeItem.AddItem -> (holder as AddVH).bind()
        }
    }

    // ---------- account tile ----------

    inner class AccountVH(private val b: ItemAccountTileBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(account: Account) {
            val density = context.resources.displayMetrics.density

            // Tile background: account color, 15dp corners (set in code).
            val baseColor = try {
                Color.parseColor(account.colorHex)
            } catch (_: Exception) {
                ContextCompat.getColor(context, R.color.fb_blue)
            }
            b.tileFrame.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(baseColor)
                cornerRadius = 15f * density
            }

            // Status dot: green = ACTIVE, red = DISABLE.
            val dotColor = if (account.status == "ACTIVE") {
                ContextCompat.getColor(context, R.color.status_green)
            } else {
                ContextCompat.getColor(context, R.color.status_red)
            }
            b.statusDot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(dotColor)
                setStroke((2 * density).toInt(), ContextCompat.getColor(context, R.color.colorBg))
            }

            // Proxy lock badge: only when a proxy is assigned.
            b.proxyBadge.visibility = if (account.proxyId != null) View.VISIBLE else View.GONE

            // Red notification badge.
            if (account.notifCount > 0) {
                b.badgeNotif.visibility = View.VISIBLE
                b.badgeNotif.text = if (account.notifCount > 99) "99+" else account.notifCount.toString()
            } else {
                b.badgeNotif.visibility = View.GONE
            }

            // Blue message badge.
            if (account.msgCount > 0) {
                b.badgeMsg.visibility = View.VISIBLE
                b.badgeMsg.text = if (account.msgCount > 99) "99+" else account.msgCount.toString()
            } else {
                b.badgeMsg.visibility = View.GONE
            }

            b.accountName.text = account.name

            // Active / Disable label.
            if (account.status == "ACTIVE") {
                b.statusLabel.text = "Active"
                b.statusLabel.setTextColor(ContextCompat.getColor(context, R.color.status_green))
            } else {
                b.statusLabel.text = "Disable"
                b.statusLabel.setTextColor(ContextCompat.getColor(context, R.color.status_red))
            }

            // Blue ring on the currently open account.
            b.tileFrame.foreground =
                if (account.id == SessionManager.activeAccountId) {
                    ContextCompat.getDrawable(context, R.drawable.tile_ring)
                } else {
                    null
                }

            b.root.setOnClickListener { onAccountClick(account.id) }
            b.root.setOnLongClickListener { onAccountLongClick(account.id) }
        }
    }

    // ---------- folder tile ----------

    inner class FolderVH(private val b: ItemFolderTileBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(folder: Folder, count: Int) {
            b.folderName.text = folder.name
            b.folderCount.text = if (count > 99) "99+" else count.toString()
            b.root.setOnClickListener { onFolderClick(folder.id) }
        }
    }

    // ---------- add tile ----------

    inner class AddVH(private val b: ItemAddTileBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind() {
            // Dashed border box, built in code.
            val density = context.resources.displayMetrics.density
            b.addBox.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.TRANSPARENT)
                setStroke(
                    (2 * density).toInt(),
                    ContextCompat.getColor(context, R.color.fb_blue),
                    6f * density,
                    4f * density
                )
                cornerRadius = 15f * density
            }
            b.root.setOnClickListener { onAddClick() }
        }
    }
}


==========================================
