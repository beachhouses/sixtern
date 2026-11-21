package com.example.sixtern.ui.logbook

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.sixtern.R
import com.example.sixtern.data.model.Logbook
import java.text.SimpleDateFormat
import java.util.Locale

class LogbookAdapter(
    private val onFixClick: (Logbook) -> Unit
) : RecyclerView.Adapter<LogbookAdapter.ViewHolder>() {

    private val items = mutableListOf<Logbook>()
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale("id"))

    fun submit(list: List<Logbook>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_logbook, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val titleText: TextView = view.findViewById(R.id.titleText)
        private val dateText: TextView = view.findViewById(R.id.dateText)
        private val statusText: TextView = view.findViewById(R.id.statusText)
        private val notesText: TextView = view.findViewById(R.id.notesText)
        private val fixButton: Button = view.findViewById(R.id.fixButton)

        fun bind(item: Logbook) {
            titleText.text = item.title
            val date = item.createdAt?.toDate()
            dateText.text = if (date != null) dateFormat.format(date) else "-"
            val context = itemView.context
            when (item.status) {
                Logbook.STATUS_APPROVED -> {
                    statusText.text = context.getString(R.string.status_approved)
                    statusText.setTextColor(ContextCompat.getColor(context, R.color.sixtern_green))
                }
                Logbook.STATUS_REVISION -> {
                    statusText.text = context.getString(R.string.status_revision)
                    statusText.setTextColor(ContextCompat.getColor(context, R.color.sixtern_purple))
                }
                else -> {
                    statusText.text = context.getString(R.string.status_pending)
                    statusText.setTextColor(ContextCompat.getColor(context, R.color.text_muted_on_dark))
                }
            }
            if (!item.mentorNotes.isNullOrBlank()) {
                notesText.visibility = View.VISIBLE
                notesText.text = item.mentorNotes
            } else {
                notesText.visibility = View.GONE
            }
            if (item.status == Logbook.STATUS_REVISION) {
                fixButton.visibility = View.VISIBLE
                fixButton.setOnClickListener { onFixClick(item) }
            } else {
                fixButton.visibility = View.GONE
            }
        }
    }
}
