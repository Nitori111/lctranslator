package com.example.lctranslator.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.lctranslator.R

data class Message(val original: String, var translated: String = "…")

class MessageAdapter(private val items: List<Message>) :
    RecyclerView.Adapter<MessageAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvOriginal: TextView = v.findViewById(R.id.tvOriginal)
        val tvTranslated: TextView = v.findViewById(R.id.tvTranslated)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
    )

    override fun onBindViewHolder(h: VH, pos: Int) {
        h.tvOriginal.text = items[pos].original
        h.tvTranslated.text = items[pos].translated
    }

    override fun getItemCount() = items.size

    fun updateLastTranslation(text: String) {
        if (items.isEmpty()) return
        val last = items.size - 1
        (items as MutableList)[last] = items[last].copy(translated = text)
        notifyItemChanged(last)
    }
}
