package com.hyunh.certy.sgp23

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.hyunh.certy.R
import com.hyunh.certy.databinding.LayoutRspConditionBinding

class ConditionRvAdapter(
        private val model: RspViewModel
): RecyclerView.Adapter<ConditionRvAdapter.ViewHolder>() {

    private val items: List<Rsp.Condition> = mutableListOf()
    private val selectedItems: Set<Int> = mutableSetOf()

    fun update(new: List<Rsp.Condition>) {
        if (items is MutableList) {
            items.clear()
            items.addAll(new)
            notifyDataSetChanged()
        }
    }

    fun updateSelectedItems(new: Set<Int>) {
        if (selectedItems is MutableSet) {
            selectedItems.clear()
            selectedItems.addAll(new)
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = DataBindingUtil.inflate<LayoutRspConditionBinding>(
                LayoutInflater.from(parent.context), R.layout.layout_rsp_condition, parent, false)
        binding.vm = model
        return ViewHolder(binding)
    }

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int) = position

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position, selectedItems)
    }

    class ViewHolder(
            private val binding: LayoutRspConditionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Rsp.Condition, position: Int, selectedItems: Set<Int>) {
            binding.tvConditionId.text = item.id
            binding.tvConditionCondition.text = item.condition
            binding.root.setBackgroundResource(if (selectedItems.contains(position)) {
                R.color.colorItemSelected
            } else {
                R.color.colorForeground
            })
            binding.position = position
        }
    }
}