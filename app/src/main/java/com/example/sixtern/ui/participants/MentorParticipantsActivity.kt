package com.example.sixtern.ui.participants

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
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
import com.example.sixtern.data.model.Company
import com.example.sixtern.data.model.SixternUser
import com.example.sixtern.data.repository.AuthRepository
import com.example.sixtern.databinding.ActivityMentorParticipantsBinding
import com.example.sixtern.ui.applicant.ApplicantDetailActivity
import com.example.sixtern.ui.auth.LoginActivity
import com.example.sixtern.ui.auth.MentorNav
import com.example.sixtern.util.NotificationPrefs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MentorParticipantsViewModel(
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _company = MutableLiveData<Company?>()
    val company: LiveData<Company?> = _company

    private val _students = MutableLiveData<List<SixternUser>>(emptyList())
    val students: LiveData<List<SixternUser>> = _students

    private val _loading = MutableLiveData(true)
    val loading: LiveData<Boolean> = _loading

    /** True bila akun/perusahaan mentor belum diverifikasi prodi. */
    private val _waitingVerification = MutableLiveData(false)
    val waitingVerification: LiveData<Boolean> = _waitingVerification

    private val _result = MutableLiveData<String?>()
    val result: LiveData<String?> = _result

    private var observeJob: Job? = null

    fun load(uid: String) {
        _loading.value = true
        observeJob?.cancel()
        viewModelScope.launch {
            // Guard: tab ini hanya terbuka setelah akun & perusahaan
            // diverifikasi prodi (lihat string mentor_account_pending_notice).
            val user = try {
                repository.getUser(uid)
            } catch (e: Exception) {
                null
            }
            val company: Company? = try {
                repository.getCompanyByMentor(uid)
            } catch (e: Exception) {
                null
            }
            _company.value = company
            _waitingVerification.value =
                user?.accountStatus == SixternUser.ACCOUNT_PENDING ||
                    (company != null && !company.verified)
            if (company == null) {
                _students.value = emptyList()
                _loading.value = false
                return@launch
            }
            observeJob = viewModelScope.launch {
                repository.observeStudentsByCompany(company.id)
                    .collectLatest { list ->
                        _students.value = list
                        _loading.value = false
                    }
            }
        }
    }

    fun accept(uid: String) {
        viewModelScope.launch {
            try {
                repository.updateInternshipStatus(uid, SixternUser.STATUS_ACCEPTED, "")
                _result.value = "PESERTA_ACCEPTED"
            } catch (e: Exception) {
                _result.value = e.message ?: "Gagal menerima peserta"
            }
        }
    }

    fun reject(uid: String, reason: String) {
        viewModelScope.launch {
            try {
                repository.updateInternshipStatus(uid, SixternUser.STATUS_REJECTED, reason)
                _result.value = "PESERTA_REJECTED"
            } catch (e: Exception) {
                _result.value = e.message ?: "Gagal menolak peserta"
            }
        }
    }

    fun remove(uid: String) {
        viewModelScope.launch {
            try {
                repository.removeStudentFromCompany(uid)
                _result.value = "PESERTA_REMOVED"
            } catch (e: Exception) {
                _result.value = e.message ?: "Gagal mengeluarkan peserta"
            }
        }
    }

    fun clearResult() {
        _result.value = null
    }
}

/**
 * Kelola Peserta Magang (mentor): daftar mahasiswa yang mengajukan ke
 * perusahaan mentor + filter status + aksi Terima/Tolak/Keluarkan per baris.
 */
class MentorParticipantsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMentorParticipantsBinding
    private val viewModel: MentorParticipantsViewModel by viewModels()
    private lateinit var adapter: ParticipantAdapter

    /** Filter aktif: null = Semua, selainnya status internshipStatus. */
    private var activeFilter: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMentorParticipantsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uid = intent.getStringExtra(LoginActivity.EXTRA_USER_ID).orEmpty()
        val name = intent.getStringExtra(LoginActivity.EXTRA_USER_NAME).orEmpty()

        adapter = ParticipantAdapter(
            onAccept = { viewModel.accept(it.uid) },
            onReject = { showRejectDialog(it) },
            onRemove = { showRemoveDialog(it) },
            onItemClick = { user ->
                val intent = Intent(this, ApplicantDetailActivity::class.java)
                intent.putExtra(ApplicantDetailActivity.EXTRA_APPLICANT_UID, user.uid)
                startActivity(intent)
            },
            onViewAttendance = { user ->
                val intent = Intent(
                    this,
                    com.example.sixtern.ui.attendance.AttendanceReportActivity::class.java
                )
                intent.putExtra(
                    com.example.sixtern.ui.attendance.AttendanceReportActivity.EXTRA_STUDENT_ID,
                    user.uid
                )
                intent.putExtra(
                    com.example.sixtern.ui.attendance.AttendanceReportActivity.EXTRA_STUDENT_NAME,
                    user.name
                )
                startActivity(intent)
            }
        )
        binding.participantsRecycler.layoutManager = LinearLayoutManager(this)
        binding.participantsRecycler.adapter = adapter

        binding.mentorNav.itemIconTintList = null
        MentorNav.setup(binding.mentorNav, this, R.id.nav_participants, uid, name)

        setupChips()

        viewModel.result.observe(this) { msg ->
            if (msg != null) {
                val text = when (msg) {
                    "PESERTA_ACCEPTED" -> getString(R.string.participants_accept_success)
                    "PESERTA_REJECTED" -> getString(R.string.participants_reject_success)
                    "PESERTA_REMOVED" -> getString(R.string.participants_remove_success)
                    else -> msg
                }
                Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
                viewModel.clearResult()
            }
        }

        viewModel.company.observe(this) { render() }
        viewModel.students.observe(this) { render() }
        viewModel.waitingVerification.observe(this) {
            val uidNow = intent.getStringExtra(LoginActivity.EXTRA_USER_ID).orEmpty()
            val nameNow = intent.getStringExtra(LoginActivity.EXTRA_USER_NAME).orEmpty()
            MentorNav.setup(
                binding.mentorNav, this, R.id.nav_participants, uidNow, nameNow,
                actionsBlocked = it
            )
            render()
        }

        if (uid.isNotBlank()) viewModel.load(uid)
    }

    override fun onResume() {
        super.onResume()
        val uid = intent.getStringExtra(LoginActivity.EXTRA_USER_ID).orEmpty()
        if (uid.isNotBlank()) viewModel.load(uid)
    }

    private fun setupChips() {
        val chips: List<Pair<TextView, String?>> = listOf(
            binding.chipAll to null,
            binding.chipPending to SixternUser.STATUS_PENDING,
            binding.chipAccepted to SixternUser.STATUS_ACCEPTED,
            binding.chipRejected to SixternUser.STATUS_REJECTED
        )
        chips.forEach { (chip, filter) ->
            chip.setOnClickListener {
                activeFilter = filter
                render()
            }
        }
    }

    /** Terapkan style aktif/non-aktif + label jumlah pada tiap chip. */
    private fun renderChips(students: List<SixternUser>) {
        val all = binding.chipAll
        val pending = binding.chipPending
        val accepted = binding.chipAccepted
        val rejected = binding.chipRejected

        all.text = getString(R.string.participants_filter_all, students.size)
        pending.text = getString(
            R.string.participants_filter_pending,
            students.count { it.internshipStatus == SixternUser.STATUS_PENDING }
        )
        accepted.text = getString(
            R.string.participants_filter_accepted,
            students.count { it.internshipStatus == SixternUser.STATUS_ACCEPTED }
        )
        rejected.text = getString(
            R.string.participants_filter_rejected,
            students.count { it.internshipStatus == SixternUser.STATUS_REJECTED }
        )

        fun style(chip: TextView, active: Boolean) {
            chip.setBackgroundResource(
                if (active) R.drawable.bg_login_button else R.drawable.bg_login_input
            )
            chip.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (active) R.color.login_button_text else R.color.login_text_secondary
                )
            )
        }
        style(all, activeFilter == null)
        style(pending, activeFilter == SixternUser.STATUS_PENDING)
        style(accepted, activeFilter == SixternUser.STATUS_ACCEPTED)
        style(rejected, activeFilter == SixternUser.STATUS_REJECTED)
    }

    private fun render() {
        val company = viewModel.company.value
        val students = viewModel.students.value.orEmpty()

        renderChips(students)

        // Menunggu verifikasi prodi: tab terkunci, tampilkan pesan menunggu.
        if (viewModel.waitingVerification.value == true) {
            binding.emptyState.visibility = View.VISIBLE
            binding.participantsRecycler.visibility = View.GONE
            binding.emptyTitle.text = getString(R.string.mentor_account_pending_title)
            binding.emptyText.text = getString(R.string.mentor_account_pending_notice)
            binding.emptyIcon.text = "\u23F3"
            MentorNav.setBadge(binding.mentorNav, 0)
            return
        }

        // Badge notifikasi dihitung sama seperti beranda: pending belum-dilihat.
        val uid = intent.getStringExtra(LoginActivity.EXTRA_USER_ID).orEmpty()
        val seenAt = NotificationPrefs.getSeenAt(this, uid)
        val unread = students.count {
            it.internshipStatus == SixternUser.STATUS_PENDING && it.statusUpdatedAt > seenAt
        }
        MentorNav.setBadge(binding.mentorNav, unread)

        val filtered = if (activeFilter == null) {
            students
        } else {
            students.filter { it.internshipStatus == activeFilter }
        }
        adapter.submitList(filtered)

        val hasCompany = company != null
        when {
            !hasCompany -> {
                binding.emptyState.visibility = View.VISIBLE
                binding.participantsRecycler.visibility = View.GONE
                binding.emptyTitle.text = getString(R.string.participants_need_company_title)
                binding.emptyText.text = getString(R.string.participants_need_company)
            }
            students.isEmpty() -> {
                binding.emptyState.visibility = View.VISIBLE
                binding.participantsRecycler.visibility = View.GONE
                binding.emptyTitle.text = getString(R.string.participants_empty_title)
                binding.emptyText.text = getString(R.string.participants_empty)
            }
            filtered.isEmpty() -> {
                binding.emptyState.visibility = View.VISIBLE
                binding.participantsRecycler.visibility = View.GONE
                binding.emptyTitle.text = getString(R.string.participants_empty_filter_title)
                binding.emptyText.text = getString(R.string.participants_empty_filter)
            }
            else -> {
                binding.emptyState.visibility = View.GONE
                binding.participantsRecycler.visibility = View.VISIBLE
            }
        }
    }

    private fun showRejectDialog(user: SixternUser) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_reject_reason, null)
        val reasonInput = dialogView.findViewById<TextInputEditText>(R.id.rejectReasonInput)
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.participants_reject_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.participants_reject)) { _, _ ->
                val reason = reasonInput.text?.toString()?.trim().orEmpty()
                viewModel.reject(user.uid, reason)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showRemoveDialog(user: SixternUser) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.participants_remove_confirm_title))
            .setMessage(getString(R.string.participants_remove_confirm_msg, user.name))
            .setPositiveButton(getString(R.string.participants_remove_yes)) { _, _ ->
                viewModel.remove(user.uid)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}
