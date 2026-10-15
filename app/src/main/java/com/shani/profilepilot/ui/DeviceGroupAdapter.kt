package com.shani.profilepilot.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.shani.profilepilot.data.DeviceModel
import com.shani.profilepilot.databinding.ItemDeviceBrandBinding
import com.shani.profilepilot.databinding.ItemDeviceRowBinding

/**
 * v1.5: device models brand folders me — header dabao to folder khulta/band hota hai.
 * Search chalne par flat filtered list dikhti hai (bina folders ke).
 */
class DeviceGroupAdapter(
    private val onPick: (DeviceModel) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed interface Row {
        data class Brand(val name: String, val count: Int, val expanded: Boolean) : Row
        data class Device(val model: DeviceModel) : Row
    }

    private var groups: List<Pair<String, List<DeviceModel>>> = emptyList()
    private val expanded = mutableSetOf<String>()
    private var flatSearch: List<DeviceModel>? = null
    private var rows: List<Row> = emptyList()

    fun setGroups(g: List<Pair<String, List<DeviceModel>>>) {
        groups = g
        flatSearch = null
        rebuild()
    }

    fun setSearchResults(models: List<DeviceModel>) {
        flatSearch = models
        rebuild()
    }

    fun clearSearch() {
        flatSearch = null
        rebuild()
    }

    private fun rebuild() {
        val flat = flatSearch
        rows = if (flat != null) {
            flat.map { Row.Device(it) }
        } else {
            buildList {
                for ((brand, models) in groups) {
                    val isOpen = brand in expanded
                    add(Row.Brand(brand, models.size, isOpen))
                    if (isOpen) models.forEach { add(Row.Device(it)) }
                }
            }
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int =
        if (rows[position] is Row.Brand) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == 0) {
            BrandVH(ItemDeviceBrandBinding.inflate(inf, parent, false))
        } else {
            DeviceVH(ItemDeviceRowBinding.inflate(inf, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Brand -> {
                val h = holder as BrandVH
                h.binding.brandName.text = "${row.name} (${row.count})"
                h.binding.brandArrow.text = if (row.expanded) "▲" else "▼"
                h.binding.root.setOnClickListener {
                    if (row.expanded) expanded.remove(row.name)
                    else expanded.add(row.name)
                    rebuild()
                }
            }
            is Row.Device -> {
                val h = holder as DeviceVH
                h.binding.deviceName.text = row.model.name
                h.binding.deviceModel.text = row.model.userAgent
                h.binding.root.setOnClickListener { onPick(row.model) }
            }
        }
    }

    override fun getItemCount(): Int = rows.size

    inner class BrandVH(val binding: ItemDeviceBrandBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class DeviceVH(val binding: ItemDeviceRowBinding) :
        RecyclerView.ViewHolder(binding.root)
}


