package com.example.sixtern.ui.logbook

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.sixtern.R
import com.example.sixtern.data.model.Logbook
import com.example.sixtern.data.repository.InternshipRepository
import com.example.sixtern.databinding.ActivityLogbookHistoryBinding
import kotlinx.coroutines.launch

// ===================== Model langkah timeline =====================

data class HistoryStep(
    val title: String,
    val timeText: String,
    val note: String = "",
    /** Warna titik: hijau (selesai), kuning (tunggu), merah (revisi). */
    val dotRes: Int = com.example.sixtern.R.drawable.bg_dot_grey
)

/**
 * Susun timeline dari data yang TERSIMPAN di Firestore: dibuat (createdAt),
 * review terakhir (reviewedAt + status + mentorNotes). Putaran review lama
 * memang tidak tersimpan di backend — yang tampil kondisi terkini.
 */
fun buildHistorySteps(logbook: Logbook, context: android.content.Context): List<HistoryStep> {
    val fmt = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale("id"))
    val steps = mutableListOf<HistoryStep>()
    val created = logbook.createdAt?.toDate()
    steps.add(
        HistoryStep(
            title = context.getString(R.string.logbook_history_step_created),
            timeText = if (created != null) fmt.format(created) else "-",
            dotRes = R.drawable.bg_dot_green
        )
    )
    if (logbook.reviewedAt > 0) {
        val time = fmt.format(java.util.Date(logbook.reviewedAt))
        when (logbook.status) {
            Logbook.STATUS_APPROVED -> steps.add(
                HistoryStep(
                    title = context.getString(R.string.logbook_history_step_reviewed_approved),
                    timeText = time,
                    note = logbook.mentorNotes.orEmpty(),
                    dotRes = R.drawable.bg_dot_green
                )
            )
            Logbook.STATUS_REVISION -> steps.add(
                HistoryStep(
                    title = context.getString(R.string.logbook_history_step_reviewed_revision),
                    timeText = time,
                    note = logbook.mentorNotes.orEmpty(),
                    dotRes = R.drawable.bg_chip_revision
                )
            )
            else -> steps.add(
                HistoryStep(
                    title = context.getString(R.string.logbook_history_step_waiting),
                    timeText = time,
                    dotRes = R.drawable.bg_dot_plum
                )
            )
        }
    } else {
        steps.add(
            HistoryStep(
                title = context.getString(R.string.logbook_history_step_waiting),
                timeText = "-",
                dotRes = R.drawable.bg_dot_grey
            )
        )
    }
    return steps
}

// ===================== Adapter =====================

class LogbookHistoryAdapter :
    androidx.recyclerview.widget.ListAdapter<HistoryStep, LogbookHistoryAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_logbook_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    class ViewHolder(view: android.view.View) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        private val dot: View = view.findViewById(R.id.timelineDot)
        private val titleText: android.widget.TextView = view.findViewById(R.id.stepTitleText)
        private val timeText: android.widget.TextView = view.findViewById(R.id.stepTimeText)
        private val noteText: android.widget.TextView = view.findViewById(R.id.stepNoteText)

        fun bind(step: HistoryStep) {
            dot.setBackgroundResource(step.dotRes)
            titleText.text = step.title
            timeText.text = step.timeText
            if (step.note.isBlank()) {
                noteText.visibility = View.GONE
            } else {
                noteText.visibility = View.VISIBLE
                noteText.text = itemView.context.getString(
                    R.string.logbook_card_notes_label
                ) + ": " + step.note
            }
        }
    }

    companion object {
        private val DIFF = object :
            androidx.recyclerview.widget.DiffUtil.ItemCallback<HistoryStep>() {
            override fun areItemsTheSame(old: HistoryStep, new: HistoryStep) =
                old.title == new.title && old.timeText == new.timeText
            override fun areContentsTheSame(old: HistoryStep, new: HistoryStep) = old == new
        }
    }
}

// ===================== ViewModel + Activity =====================

class LogbookHistoryViewModel(
    private val repository: InternshipRepository = InternshipRepository()
) : ViewModel() {

    private val _logbook = MutableLiveData<Logbook?>()
    val logbook: LiveData<Logbook?> = _logbook

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun load(logbookId: String) {
        viewModelScope.launch {
            try {
                _logbook.value = repository.getLogbookById(logbookId)
            } catch (e: Exception) {
                _error.value = e.message ?: "Gagal memuat riwayat"
            }
        }
    }

    fun clearError() { _error.value = null }
}

/**
 * Riwayat penilaian satu logbook: ringkasan + timeline (dibuat -> review
 * terakhir). Bisa dibuka mahasiswa (klik kartu) maupun mentor (klik daftar
 * di detail peserta, plus tombol lompat ke halaman review).
 */
class LogbookHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogbookHistoryBinding
    private val viewModel: LogbookHistoryViewModel by viewModels()
    private lateinit var adapter: LogbookHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogbookHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val logbookId = intent.getStringExtra(EXTRA_LOGBOOK_ID).orEmpty()
        // Mentor yang membuka dari detail peserta boleh lompat ke review.
        val fromMentor = intent.getBooleanExtra(EXTRA_FROM_MENTOR, false)

        binding.backButton.setOnClickListener { finish() }

        adapter = LogbookHistoryAdapter()
        binding.historyRecycler.layoutManager = LinearLayoutManager(this)
        binding.historyRecycler.adapter = adapter

        viewModel.error.observe(this) { msg ->
            if (msg != null) {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                viewModel.clearError()
                finish()
            }
        }

        viewModel.logbook.observe(this) { logbook ->
            if (logbook == null) return@observe
            binding.historyTitleText.text = logbook.title.ifBlank { "-" }
            val date = logbook.createdAt?.toDate()
            binding.historyDateText.text = if (date != null) {
                java.text.SimpleDateFormat("EEEE, dd MMM yyyy", java.util.Locale("id")).format(date)
            } else "-"
            binding.historyDescText.text = RichTextEditor.fromHtml(logbook.description)
            if (logbook.attachmentUrl.isNullOrBlank()) {
                binding.historyAttachmentText.visibility = View.GONE
            } else {
                binding.historyAttachmentText.visibility = View.VISIBLE
                binding.historyAttachmentText.text =
                    getString(R.string.logbook_attachment_attached)
            }
            adapter.submitList(buildHistorySteps(logbook, this))
            // Mentor: tombol ke halaman review logbook ini.
            if (fromMentor) {
                binding.openReviewButton.visibility = View.VISIBLE
                binding.openReviewButton.setOnClickListener {
                    val intent = android.content.Intent(
                        this, LogbookReviewDetailActivity::class.java
                    )
                    intent.putExtra(
                        LogbookReviewDetailActivity.EXTRA_LOGBOOK_ID, logbook.id
                    )
                    startActivity(intent)
                }
            }
        }

        if (logbookId.isNotBlank()) viewModel.load(logbookId)
        else {
            Toast.makeText(this, "Logbook tidak ditemukan", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    companion object {
        const val EXTRA_LOGBOOK_ID = "extra_logbook_id"
        const val EXTRA_FROM_MENTOR = "extra_from_mentor"
    }
}
