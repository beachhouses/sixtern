package com.example.sixtern.ui.logbook

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sixtern.R
import com.example.sixtern.data.model.Logbook
import com.example.sixtern.data.model.SixternUser
import com.example.sixtern.data.repository.AuthRepository
import com.example.sixtern.data.repository.InternshipRepository
import com.example.sixtern.databinding.ActivityLogbookReviewDetailBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ===================== ViewModel =====================

sealed class LogbookReviewState {
    data object Loading : LogbookReviewState()
    data class Loaded(val logbook: Logbook, val student: SixternUser?) : LogbookReviewState()
    data class Error(val message: String) : LogbookReviewState()
}

class LogbookReviewViewModel(
    private val internshipRepo: InternshipRepository = InternshipRepository(),
    private val authRepo: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _state = MutableLiveData<LogbookReviewState>(LogbookReviewState.Loading)
    val state: LiveData<LogbookReviewState> = _state

    private val _saving = MutableLiveData(false)
    val saving: LiveData<Boolean> = _saving

    private val _result = MutableLiveData<String?>()
    val result: LiveData<String?> = _result

    fun load(logbookId: String) {
        _state.value = LogbookReviewState.Loading
        viewModelScope.launch {
            try {
                val logbook = internshipRepo.getLogbookById(logbookId)
                    ?: throw IllegalStateException("Logbook tidak ditemukan")
                val student = try {
                    authRepo.getUser(logbook.studentId)
                } catch (e: Exception) {
                    null
                }
                _state.value = LogbookReviewState.Loaded(logbook, student)
            } catch (e: Exception) {
                _state.value = LogbookReviewState.Error(
                    e.message ?: "Gagal memuat logbook"
                )
            }
        }
    }

    fun submitReview(logbookId: String, status: String, notes: String) {
        if (_saving.value == true) return
        _saving.value = true
        viewModelScope.launch {
            try {
                internshipRepo.reviewLogbook(logbookId, status, notes)
                _result.value = if (status == Logbook.STATUS_APPROVED) "REVIEW_APPROVED"
                else "REVIEW_REVISED"
            } catch (e: Exception) {
                _result.value = e.message ?: "Gagal menyimpan review"
            } finally {
                _saving.value = false
            }
        }
    }

    fun clearResult() {
        _result.value = null
    }
}

// ===================== Activity =====================

/**
 * Detail logbook untuk mentor: baca catatan mahasiswa lalu setujui
 * atau minta revisi + catatan mentor. Hasil review muncul sebagai
 * notifikasi di aplikasi mahasiswa (badge + halaman notifikasi).
 */
class LogbookReviewDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LOGBOOK_ID = "extra_logbook_id"
    }

    private lateinit var binding: ActivityLogbookReviewDetailBinding
    private val viewModel: LogbookReviewViewModel by viewModels()
    private var logbookId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogbookReviewDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        logbookId = intent.getStringExtra(EXTRA_LOGBOOK_ID).orEmpty()
        binding.backButton.setOnClickListener { finish() }

        binding.approveButton.setOnClickListener { confirmApprove() }
        binding.reviseButton.setOnClickListener { confirmRevise() }

        viewModel.saving.observe(this) { saving ->
            binding.loadingBar.visibility = if (saving) View.VISIBLE else View.GONE
            binding.approveButton.isEnabled = !saving
            binding.reviseButton.isEnabled = !saving
        }

        viewModel.result.observe(this) { msg ->
            if (msg != null) {
                val text = when (msg) {
                    "REVIEW_APPROVED" -> getString(R.string.logbook_review_approved_msg)
                    "REVIEW_REVISED" -> getString(R.string.logbook_review_revised_msg)
                    else -> msg
                }
                Toast.makeText(this, text, Toast.LENGTH_LONG).show()
                viewModel.clearResult()
                if (msg.startsWith("REVIEW_")) finish()
            }
        }

        viewModel.state.observe(this) { state ->
            when (state) {
                is LogbookReviewState.Loading -> {
                    binding.loadingBar.visibility = View.VISIBLE
                }
                is LogbookReviewState.Loaded -> render(state)
                is LogbookReviewState.Error -> {
                    binding.loadingBar.visibility = View.GONE
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }

        if (logbookId.isNotBlank()) viewModel.load(logbookId)
    }

    private fun render(state: LogbookReviewState.Loaded) {
        binding.loadingBar.visibility = View.GONE
        val logbook = state.logbook
        val student = state.student

        binding.studentNameText.text = student?.name ?: "-"
        binding.logbookTitleText.text = logbook.title
        val date = logbook.createdAt?.toDate()
        binding.logbookDateText.text = if (date != null) {
            SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id")).format(date)
        } else "-"
        binding.logbookDescText.text = logbook.description.ifBlank { "-" }

        if (!logbook.attachmentUrl.isNullOrBlank()) {
            binding.attachmentText.visibility = View.VISIBLE
            binding.attachmentText.text = getString(R.string.logbook_attachment_attached)
        } else {
            binding.attachmentText.visibility = View.GONE
        }

        // Pre-isi catatan lama bila mentor pernah memberi catatan.
        if (!logbook.mentorNotes.isNullOrBlank()) {
            binding.notesInput.setText(logbook.mentorNotes)
        }
    }

    private fun confirmApprove() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.logbook_review_approve_confirm_title))
            .setMessage(getString(R.string.logbook_review_approve_confirm_msg))
            .setPositiveButton(getString(R.string.logbook_review_approve)) { _, _ ->
                viewModel.submitReview(
                    logbookId,
                    Logbook.STATUS_APPROVED,
                    binding.notesInput.text?.toString()?.trim().orEmpty()
                )
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun confirmRevise() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.logbook_review_revise_confirm_title))
            .setMessage(getString(R.string.logbook_review_revise_confirm_msg))
            .setPositiveButton(getString(R.string.logbook_review_revise)) { _, _ ->
                viewModel.submitReview(
                    logbookId,
                    Logbook.STATUS_REVISION,
                    binding.notesInput.text?.toString()?.trim().orEmpty()
                )
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}
