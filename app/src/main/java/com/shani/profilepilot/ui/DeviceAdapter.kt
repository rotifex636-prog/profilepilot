package com.shani.profilepilot.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.shani.profilepilot.data.DeviceModel
import com.shani.profilepilot.databinding.ItemDeviceRowBinding

class DeviceAdapter(
    private var items: List<DeviceModel>,
    private val onPick: (DeviceModel) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.VH>() {

    inner class VH(val binding: ItemDeviceRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemDeviceRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val model = items[position]
        holder.binding.deviceName.text = model.name
        holder.binding.deviceModel.text = model.userAgent
        holder.binding.root.setOnClickListener { onPick(model) }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<DeviceModel>) {
        items = newItems
        notifyDataSetChanged()
    }
}


==========================================
