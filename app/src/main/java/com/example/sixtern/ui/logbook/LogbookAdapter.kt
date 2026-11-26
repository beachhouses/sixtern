package com.example.sixtern.ui.logbook

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.sixtern.R
import com.example.sixtern.data.model.Logbook
import java.text.SimpleDateFormat
import java.util.Locale

class LogbookAdapter(
    private val onFixClick: (Logbook) -> Unit,
    private val onItemClick: (Logbook) -> Unit = {}
) : RecyclerView.Adapter<LogbookAdapter.ViewHolder>() {

    private val items = mutableListOf<Logbook>()

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
        private val dayText: TextView = view.findViewById(R.id.dayText)
        private val monthText: TextView = view.findViewById(R.id.monthText)
        private val titleText: TextView = view.findViewById(R.id.titleText)
        private val statusText: TextView = view.findViewById(R.id.statusText)
        private val notesBox: LinearLayout = view.findViewById(R.id.notesBox)
        private val notesText: TextView = view.findViewById(R.id.notesText)
        private val descText: TextView = view.findViewById(R.id.descText)
        private val attachmentIcon: ImageView = view.findViewById(R.id.attachmentIcon)
        private val attachmentInfoText: TextView = view.findViewById(R.id.attachmentInfoText)
        private val fixButton: Button = view.findViewById(R.id.fixButton)
        private val dayFormat = SimpleDateFormat("dd", Locale("id"))
        private val monthFormat = SimpleDateFormat("MMM", Locale("id"))

        fun bind(item: Logbook) {
            val context = itemView.context
            titleText.text = item.title.ifBlank { "-" }
            // Lingkaran tanggal: tanggal + bulan singkat (mis. "12" / "Nov").
            val date = item.createdAt?.toDate()
            if (date != null) {
                dayText.text = dayFormat.format(date)
                monthText.text = monthFormat.format(date).replaceFirstChar { it.uppercase() }
            } else {
                dayText.text = "--"
                monthText.text = "---"
            }
            // Deskripsi: potong 140 karakter agar kartu tetap ramping.
            descText.text = RichTextEditor.fromHtml(stripForCard(item.description))
            // Info lampiran (nama file bila URI content, atau tandai ada).
            if (item.attachmentUrl.isNullOrBlank()) {
                attachmentIcon.visibility = View.GONE
                attachmentInfoText.visibility = View.GONE
            } else {
                attachmentIcon.visibility = View.VISIBLE
                attachmentInfoText.visibility = View.VISIBLE
                attachmentInfoText.text = context.getString(
                    R.string.logbook_card_attachment,
                    attachmentDisplayName(item.attachmentUrl)
                )
            }
            // Chip status: background + teks berwarna sesuai status.
            val (label, bgRes, textColor) = when (item.status) {
                Logbook.STATUS_APPROVED -> Triple(
                    context.getString(R.string.status_approved),
                    R.drawable.bg_login_badge, R.color.chip_logbook_approved_text
                )
                Logbook.STATUS_REVISION -> Triple(
                    context.getString(R.string.status_revision),
                    R.drawable.bg_chip_revision, R.color.chip_logbook_revision_text
                )
                else -> Triple(
                    context.getString(R.string.status_pending),
                    R.drawable.bg_chip_pending, R.color.chip_logbook_pending_text
                )
            }
            statusText.text = label
            statusText.setBackgroundResource(bgRes)
            statusText.setTextColor(ContextCompat.getColor(context, textColor))
            if (item.mentorNotes.isNullOrBlank()) {
                notesBox.visibility = View.GONE
            } else {
                notesBox.visibility = View.VISIBLE
                notesText.text = item.mentorNotes
            }
            if (item.status == Logbook.STATUS_REVISION) {
                fixButton.visibility = View.VISIBLE
                fixButton.setOnClickListener { onFixClick(item) }
            } else {
                fixButton.visibility = View.GONE
            }
            // Klik kartu (di luar tombol revisi) -> riwayat penilaian.
            // Klik tombol tetap ke tombolnya sendiri, tidak memicu klik kartu.
            itemView.setOnClickListener { onItemClick(item) }
        }

        /**
         * Siapkan deskripsi untuk kartu: dari HTML -> teks polos, potong
         * 140 karakter agar kartu ramping (detail penuh ada di form revisi).
         */
        private fun stripForCard(html: String): String {
            if (html.isBlank()) return ""
            val plain = try {
                @Suppress("DEPRECATION")
                android.text.Html.fromHtml(html).toString().trim()
            } catch (e: Exception) {
                html
            }.replace(Regex("\\s+"), " ")
            return if (plain.length > 140) plain.take(137).trimEnd() + "..." else plain
        }

        /** Nama file lampiran untuk label: URI content -> segmen akhir; URL -> "terlampir". */
        private fun attachmentDisplayName(url: String): String {
            return try {
                val uri = android.net.Uri.parse(url)
                (uri.lastPathSegment ?: url).takeIf { it.isNotBlank() } ?: url
            } catch (e: Exception) {
                url
            }
        }
    }
}
