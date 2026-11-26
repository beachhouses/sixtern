package com.example.sixtern.ui.logbook

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.sixtern.R
import com.example.sixtern.data.model.Logbook
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Daftar logbook menunggu review di halaman notifikasi mentor.
 * Tiap baris: avatar inisial mahasiswa, nama, judul logbook, tanggal,
 * tombol Review -> LogbookReviewDetailActivity.
 */
class LogbookReviewAdapter(
    private val onReview: (Logbook) -> Unit
) : ListAdapter<Logbook, LogbookReviewAdapter.VH>(DIFF) {

    /** uid mahasiswa -> nama (join dari koleksi users). */
    private var studentNames: Map<String, String> = emptyMap()

    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id"))

    fun submit(list: List<Logbook>, names: Map<String, String>) {
        studentNames = names
        submitList(list)
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val avatar: TextView = view.findViewById(R.id.logbookAvatar)
        val studentName: TextView = view.findViewById(R.id.logbookStudentName)
        val title: TextView = view.findViewById(R.id.logbookTitle)
        val date: TextView = view.findViewById(R.id.logbookDate)
        val reviewButton: TextView = view.findViewById(R.id.reviewButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_logbook_review, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val name = studentNames[item.studentId] ?: "-"
        holder.avatar.text = name.firstOrNull()?.uppercase() ?: "M"
        holder.studentName.text = name
        holder.title.text = item.title
        val date = item.createdAt?.toDate()
        holder.date.text = if (date != null) dateFormat.format(date) else "-"
        holder.reviewButton.setOnClickListener { onReview(item) }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Logbook>() {
            override fun areItemsTheSame(a: Logbook, b: Logbook) = a.id == b.id
            override fun areContentsTheSame(a: Logbook, b: Logbook) = a == b
        }
    }
}

/**
 * Daftar hasil review logbook di halaman notifikasi mahasiswa.
 * Baris dengan reviewedAt > 0 dan status approved/revision.
 */
class LogbookNotifAdapter : ListAdapter<Logbook, LogbookNotifAdapter.VH>(DIFF) {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id"))

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: TextView = view.findViewById(R.id.notifIcon)
        val title: TextView = view.findViewById(R.id.notifTitle)
        val body: TextView = view.findViewById(R.id.notifBody)
        val time: TextView = view.findViewById(R.id.notifTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_logbook_notif, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val ctx = holder.itemView.context

        if (item.status == Logbook.STATUS_APPROVED) {
            holder.icon.text = "\u2713"
            holder.icon.setBackgroundResource(R.drawable.bg_notice_accepted)
            holder.title.text = ctx.getString(R.string.student_notif_logbook_approved, item.title)
            holder.title.setTextColor(ContextCompat.getColor(ctx, R.color.sixtern_green_deep))
        } else {
            holder.icon.text = "\u26A0"
            holder.icon.setBackgroundResource(R.drawable.bg_notice_rejected)
            holder.title.text = ctx.getString(R.string.student_notif_logbook_revised, item.title)
            holder.title.setTextColor(ContextCompat.getColor(ctx, R.color.chip_rejected_text))
        }

        val notes = item.mentorNotes.orEmpty()
        holder.body.text = if (notes.isNotBlank()) {
            ctx.getString(R.string.logbook_notif_notes, notes)
        } else {
            item.description
        }

        holder.time.text = dateFormat.format(java.util.Date(item.reviewedAt))
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Logbook>() {
            override fun areItemsTheSame(a: Logbook, b: Logbook) = a.id == b.id
            override fun areContentsTheSame(a: Logbook, b: Logbook) = a == b
        }
    }
}
