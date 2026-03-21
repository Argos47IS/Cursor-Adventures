package com.geodetect.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.geodetect.app.R
import com.geodetect.app.databinding.ActivityFullMetadataBinding
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class FullMetadataActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_METADATA_JSON = "metadata_json"
    }

    private lateinit var binding: ActivityFullMetadataBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFullMetadataBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        val json = intent.getStringExtra(EXTRA_METADATA_JSON) ?: "{}"
        val type = object : TypeToken<Map<String, String>>() {}.type
        val data: Map<String, String> = try {
            Gson().fromJson(json, type) ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }

        val entries = data.entries.toList().sortedBy { it.key }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = MetadataAdapter(entries)
    }

    private class MetadataAdapter(
        private val items: List<Map.Entry<String, String>>
    ) : RecyclerView.Adapter<MetadataAdapter.ViewHolder>() {

        class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
            val tvTag: TextView = view.findViewById(R.id.tvTag)
            val tvValue: TextView = view.findViewById(R.id.tvValue)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_full_metadata, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val entry = items[position]
            holder.tvTag.text = entry.key
            holder.tvValue.text = entry.value
        }

        override fun getItemCount() = items.size
    }
}
