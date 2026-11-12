package io.github.giannoccarol.corollalauncher.apps

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.github.giannoccarol.corollalauncher.databinding.ItemAppBinding

class AppGridAdapter(
    private val onClick: (AppInfo) -> Unit,
    private val onLongClick: (View, AppInfo) -> Unit,
) : RecyclerView.Adapter<AppGridAdapter.Holder>() {

    private var items: List<AppInfo> = emptyList()

    // The list is small and replaced wholesale on every filter change; a diff is not worth it.
    @SuppressLint("NotifyDataSetChanged")
    fun submit(apps: List<AppInfo>) {
        items = apps
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val app = items[position]
        AppIcons.bind(holder.binding.icon, app)
        holder.binding.label.text = app.label
        holder.binding.root.setOnClickListener { onClick(app) }
        holder.binding.root.setOnLongClickListener {
            onLongClick(it, app)
            true
        }
    }

    class Holder(val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root)
}
