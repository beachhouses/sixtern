package com.example.sixtern.ui.applicant

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sixtern.R
import com.example.sixtern.data.model.SixternUser
import com.example.sixtern.data.repository.AuthRepository
import com.example.sixtern.databinding.ActivityApplicantDetailBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class ApplicantDetailViewModel(
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _user = MutableLiveData<SixternUser?>()
    val user: LiveData<SixternUser?> = _user

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _result = MutableLiveData<String?>()
    val result: LiveData<String?> = _result

    fun load(uid: String) {
        viewModelScope.launch {
            _loading.value = true
            _user.value = try { repository.getUser(uid) } catch (e: Exception) { null }
            _loading.value = false
        }
    }

    fun accept(uid: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                repository.updateInternshipStatus(uid, SixternUser.STATUS_ACCEPTED, "")
                _user.value = _user.value?.copy(internshipStatus = SixternUser.STATUS_ACCEPTED)
                _result.value = "Peserta berhasil diterima"
            } catch (e: Exception) {
                _result.value = "Gagal menerima peserta: ${e.message}"
            }
            _loading.value = false
        }
    }

    fun reject(uid: String, reason: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                repository.updateInternshipStatus(uid, SixternUser.STATUS_REJECTED, reason)
                _user.value = _user.value?.copy(
                    internshipStatus = SixternUser.STATUS_REJECTED,
                    rejectionReason = reason
                )
                _result.value = "Peserta ditolak"
            } catch (e: Exception) {
                _result.value = "Gagal menolak peserta: ${e.message}"
            }
            _loading.value = false
        }
    }

    fun clearResult() { _result.value = null }
}

class ApplicantDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityApplicantDetailBinding
    private val viewModel: ApplicantDetailViewModel by viewModels()

    companion object {
        const val EXTRA_APPLICANT_UID = "extra_applicant_uid"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityApplicantDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val applicantUid = intent.getStringExtra(EXTRA_APPLICANT_UID).orEmpty()

        binding.backButton.setOnClickListener { finish() }

        viewModel.loading.observe(this) { loading ->
            binding.loadingIndicator.visibility = if (loading) View.VISIBLE else View.GONE
            binding.acceptButton.isEnabled = !loading
            binding.rejectButton.isEnabled = !loading
        }

        viewModel.result.observe(this) { msg ->
            if (msg != null) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                viewModel.clearResult()
            }
        }

        viewModel.user.observe(this) { user ->
            if (user == null) return@observe
            renderProfile(user)
        }

        if (applicantUid.isNotBlank()) viewModel.load(applicantUid)
    }

    private fun renderProfile(user: SixternUser) {
        // Avatar & header
        binding.avatarText.text = user.name.firstOrNull()?.uppercase() ?: "M"
        binding.nameText.text = user.name.ifBlank { "-" }
        binding.nimText.text = user.nim?.takeIf { it.isNotBlank() } ?: "-"
        binding.universityText.text = "${user.university} · ${user.major}"
        binding.emailText.text = user.email.ifBlank { "-" }
        binding.phoneText.text = user.phone.ifBlank { "-" }
        binding.ageText.text = if (user.age.isBlank()) "-" else "${user.age} tahun"

        // Bio
        binding.bioText.text = user.bio.ifBlank { getString(R.string.applicant_bio_empty) }

        // Keahlian
        if (user.skills.isEmpty()) {
            binding.skillsText.text = getString(R.string.applicant_skills_empty)
        } else {
            binding.skillsText.text = user.skills.joinToString(" · ")
        }

        // Periode magang yang diajukan: tanggal masuk & tanggal selesai
        // masing-masing satu baris agar mudah dibaca sebelum Terima/Tolak.
        binding.startDateValueText.text =
            SixternUser.formatInternshipDate(user.internshipStartDate)
        binding.endDateValueText.text =
            SixternUser.formatInternshipDate(user.internshipEndDate)

        // Surat permohonan magang (Base64 data URI) — dibuka via viewer PDF.
        val hasLetter = user.applicationLetterUrl.isNotBlank()
        binding.viewLetterButton.isEnabled = hasLetter
        binding.viewLetterButton.alpha = if (hasLetter) 1f else 0.4f
        binding.letterEmptyText.visibility = if (hasLetter) View.GONE else View.VISIBLE
        binding.viewLetterButton.setOnClickListener {
            if (!hasLetter) return@setOnClickListener
            openApplicationLetter(user.applicationLetterUrl)
        }

        // Portfolio
        val hasPortfolio = user.portfolioUrl.isNotBlank()
        binding.portfolioText.text =
            if (hasPortfolio) user.portfolioUrl else getString(R.string.applicant_portfolio_empty)
        binding.portfolioText.alpha = if (hasPortfolio) 1f else 0.5f

        // CV
        val hasCv = user.cvUrl.isNotBlank()
        binding.viewCvButton.isEnabled = hasCv
        binding.viewCvButton.alpha = if (hasCv) 1f else 0.4f
        binding.viewCvButton.setOnClickListener {
            if (!hasCv) return@setOnClickListener
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(user.cvUrl), "application/pdf")
                    addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.cv_no_app), Toast.LENGTH_SHORT).show()
            }
        }

        // Status pengajuan
        val status = user.internshipStatus
        val statusLabel = when (status) {
            SixternUser.STATUS_ACCEPTED -> getString(R.string.applicant_status_accepted)
            SixternUser.STATUS_REJECTED -> getString(R.string.applicant_status_rejected)
            else -> getString(R.string.applicant_status_pending)
        }
        binding.statusChip.text = statusLabel
        binding.statusChip.setChipBackgroundColorResource(
            when (status) {
                SixternUser.STATUS_ACCEPTED -> R.color.chip_accepted_bg
                SixternUser.STATUS_REJECTED -> R.color.chip_rejected_bg
                else -> R.color.chip_pending_bg
            }
        )

        // Alasan penolakan
        if (status == SixternUser.STATUS_REJECTED && user.rejectionReason.isNotBlank()) {
            binding.rejectionCard.visibility = View.VISIBLE
            binding.rejectionReasonText.text = user.rejectionReason
        } else {
            binding.rejectionCard.visibility = View.GONE
        }

        // Tombol aksi — hanya tampil jika masih pending
        val isPending = status == SixternUser.STATUS_PENDING
        binding.actionButtons.visibility = if (isPending) View.VISIBLE else View.GONE

        binding.acceptButton.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.applicant_accept_confirm_title))
                .setMessage(getString(R.string.applicant_accept_confirm_msg, user.name))
                .setPositiveButton(getString(R.string.applicant_accept_yes)) { _, _ ->
                    viewModel.accept(user.uid)
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }

        binding.rejectButton.setOnClickListener {
            showRejectDialog(user)
        }
    }

    private fun showRejectDialog(user: SixternUser) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_reject_reason, null)
        val reasonInput = dialogView.findViewById<TextInputEditText>(R.id.rejectReasonInput)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.applicant_reject_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.applicant_reject_confirm)) { _, _ ->
                val reason = reasonInput.text?.toString()?.trim().orEmpty()
                viewModel.reject(user.uid, reason)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    /**
     * Buka surat permohonan (data URI Base64 PDF): tulis ke cache lalu buka
     * via FileProvider agar bisa dibaca viewer PDF eksternal.
     */
    private fun openApplicationLetter(dataUri: String) {
        try {
            val base64 = dataUri.substringAfter("base64,", "")
            if (base64.isBlank()) throw IllegalArgumentException("empty")
            val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
            val file = java.io.File(cacheDir, "surat_permohonan_${System.currentTimeMillis()}.pdf")
            file.writeBytes(bytes)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this, "$packageName.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                getString(R.string.applicant_letter_open_error, e.message.orEmpty()),
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
