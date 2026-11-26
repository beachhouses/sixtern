package com.example.sixtern.ui.notification

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.sixtern.R
import com.example.sixtern.data.model.Company
import com.example.sixtern.data.model.Logbook
import com.example.sixtern.data.model.SixternUser
import com.example.sixtern.data.repository.AuthRepository
import com.example.sixtern.data.repository.InternshipRepository
import com.example.sixtern.databinding.ActivityMentorNotificationBinding
import com.example.sixtern.ui.auth.ApplicantAdapter
import com.example.sixtern.ui.auth.LoginActivity
import com.example.sixtern.ui.auth.MentorNav
import com.example.sixtern.ui.logbook.LogbookReviewAdapter
import com.example.sixtern.ui.logbook.LogbookReviewDetailActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MentorNotificationViewModel(
    private val repository: AuthRepository = AuthRepository(),
    private val internshipRepository: InternshipRepository = InternshipRepository()
) : ViewModel() {

    private val _applicants = MutableLiveData<List<SixternUser>>(emptyList())
    val applicants: LiveData<List<SixternUser>> = _applicants

    /** Status verifikasi akun (pending) + perusahaan (belum verified), realtime. */
    private val _accountPending = MutableLiveData(false)
    val accountPending: LiveData<Boolean> = _accountPending

    /** True bila akun & perusahaan sudah lolos verifikasi prodi. */
    private val _fullyVerified = MutableLiveData(true)
    val fullyVerified: LiveData<Boolean> = _fullyVerified

    /** Logbook menunggu review (pending) di perusahaan mentor, realtime. */
    private val _pendingLogbooks = MutableLiveData<List<Logbook>>(emptyList())
    val pendingLogbooks: LiveData<List<Logbook>> = _pendingLogbooks
    /** uid -> nama mahasiswa (untuk label daftar review). */
    private val _studentNames = MutableLiveData<Map<String, String>>(emptyMap())
    val studentNames: LiveData<Map<String, String>> = _studentNames

    private val _loading = MutableLiveData(true)
    val loading: LiveData<Boolean> = _loading

    private var applicantsJob: Job? = null
    private var logbooksJob: Job? = null
    private var accountJob: Job? = null

    fun load(uid: String) {
        _loading.value = true
        applicantsJob?.cancel()
        logbooksJob?.cancel()
        accountJob?.cancel()
        viewModelScope.launch {
            // Status verifikasi akun mentor (realtime): verifikasi prodi
            // langsung membuka daftar pengajuan tanpa login ulang.
            try {
                val user = repository.getUser(uid)
                _accountPending.value =
                    user?.accountStatus == SixternUser.ACCOUNT_PENDING
            } catch (e: Exception) {
                _accountPending.value = false
            }
            accountJob = viewModelScope.launch {
                repository.observeUserByUid(uid).collectLatest { observed ->
                    _accountPending.value =
                        observed?.accountStatus == SixternUser.ACCOUNT_PENDING
                    updateVerifiedState()
                }
            }
            val company: Company? = try {
                repository.getCompanyByMentor(uid)
            } catch (e: Exception) {
                null
            }
            updateVerifiedState(company)
            if (company == null) {
                _applicants.value = emptyList()
                _pendingLogbooks.value = emptyList()
                _loading.value = false
                return@launch
            }
            // Nama mahasiswa untuk label daftar review.
            _studentNames.value = try {
                repository.getStudentsByCompany(company.id)
                    .associate { it.uid to it.name }
            } catch (e: Exception) {
                emptyMap()
            }
            // Listener realtime pengajuan magang.
            applicantsJob = viewModelScope.launch {
                repository.observeStudentsByCompany(company.id)
                    .collectLatest { list ->
                        _applicants.value = list
                        _loading.value = false
                    }
            }
            // Listener realtime logbook menunggu review.
            logbooksJob = viewModelScope.launch {
                internshipRepository.observeLogbooksByCompany(company.id)
                    .collectLatest { list ->
                        _pendingLogbooks.value = list.filter {
                            it.status == Logbook.STATUS_PENDING
                        }
                        _loading.value = false
                    }
            }
        }
    }

    /** Perusahaan terakhir yang dimuat (untuk hitung status verifikasi). */
    private var lastCompany: Company? = null

    private fun updateVerifiedState(company: Company? = lastCompany) {
        lastCompany = company
        val accountWaiting = _accountPending.value == true
        val companyWaiting = company != null && !company.verified
        _fullyVerified.value = !accountWaiting && !companyWaiting
    }
}

class MentorNotificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMentorNotificationBinding
    private val viewModel: MentorNotificationViewModel by viewModels()
    private lateinit var adapter: ApplicantAdapter
    private lateinit var logbookAdapter: LogbookReviewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMentorNotificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uid = intent.getStringExtra(LoginActivity.EXTRA_USER_ID).orEmpty()
        val name = intent.getStringExtra(LoginActivity.EXTRA_USER_NAME).orEmpty()

        adapter = ApplicantAdapter { user ->
            val intent = android.content.Intent(
                this,
                com.example.sixtern.ui.applicant.ApplicantDetailActivity::class.java
            )
            intent.putExtra(
                com.example.sixtern.ui.applicant.ApplicantDetailActivity.EXTRA_APPLICANT_UID,
                user.uid
            )
            startActivity(intent)
        }
        binding.notificationRecycler.layoutManager = LinearLayoutManager(this)
        binding.notificationRecycler.adapter = adapter

        logbookAdapter = LogbookReviewAdapter { logbook ->
            val intent = android.content.Intent(this, LogbookReviewDetailActivity::class.java)
            intent.putExtra(LogbookReviewDetailActivity.EXTRA_LOGBOOK_ID, logbook.id)
            startActivity(intent)
        }
        binding.logbookRecycler.layoutManager = LinearLayoutManager(this)
        binding.logbookRecycler.adapter = logbookAdapter

        binding.mentorNav.itemIconTintList = null
        MentorNav.setup(binding.mentorNav, this, R.id.nav_notification, uid, name)
        // Halaman notifikasi dibuka -> catat waktu lihat agar badge hilang.
        com.example.sixtern.util.NotificationPrefs.markSeen(this, uid)

        viewModel.loading.observe(this) { loading ->
            binding.loadingIndicator.visibility = if (loading) View.VISIBLE else View.GONE
        }

        // Status verifikasi prodi: kartu tunggu tampil, daftar pengajuan &
        // logbook disembunyikan sampai akun+perusahaan disetujui. Tab ini
        // tetap terbuka agar mentor bisa menerima status verifikasi.
        viewModel.accountPending.observe(this) { renderVerification(uid, name) }
        viewModel.fullyVerified.observe(this) { renderVerification(uid, name) }

        viewModel.applicants.observe(this) { list ->
            adapter.submitList(list)
            val count = list.size
            binding.notificationBadgeCard.visibility =
                if (count > 0) View.VISIBLE else View.GONE
            if (count > 0) {
                binding.notificationBadgeCount.text = count.toString()
                binding.notificationBadgeText.text =
                    getString(R.string.mentor_applicants_notice, count)
            }
            binding.notificationRecycler.visibility =
                if (count > 0) View.VISIBLE else View.GONE
            binding.notificationEmptyText.visibility =
                if (count > 0) View.GONE else View.VISIBLE
            renderBadge()
        }

        viewModel.pendingLogbooks.observe(this) { logbooks ->
            val names = viewModel.studentNames.value.orEmpty()
            logbookAdapter.submit(logbooks, names)
            val hasLogbooks = logbooks.isNotEmpty()
            binding.logbookRecycler.visibility =
                if (hasLogbooks) View.VISIBLE else View.GONE
            binding.logbookEmptyText.visibility =
                if (hasLogbooks) View.GONE else View.VISIBLE
            binding.logbookNoticeText.visibility =
                if (hasLogbooks) View.VISIBLE else View.GONE
            if (hasLogbooks) {
                binding.logbookNoticeText.text =
                    getString(R.string.mentor_notif_logbook_notice, logbooks.size)
            }
            renderBadge()
        }

        if (uid.isNotBlank()) viewModel.load(uid)
    }

    override fun onResume() {
        super.onResume()
        // Kembali dari layar review -> muat ulang agar daftar pending segar.
        val uid = intent.getStringExtra(LoginActivity.EXTRA_USER_ID).orEmpty()
        if (uid.isNotBlank()) viewModel.load(uid)
    }

    /**
     * Badge nav = pengajuan magang unread + logbook pending yang dikirim
     * setelah terakhir mentor membuka halaman notifikasi.
     */
    private fun renderBadge() {
        val verified = viewModel.fullyVerified.value == true
        val uid = intent.getStringExtra(LoginActivity.EXTRA_USER_ID).orEmpty()
        if (!verified) {
            MentorNav.setBadge(binding.mentorNav, 0)
            return
        }
        val seenAt = com.example.sixtern.util.NotificationPrefs.getSeenAt(this, uid)
        val unreadApplicants = viewModel.applicants.value.orEmpty().count {
            it.internshipStatus == SixternUser.STATUS_PENDING && it.statusUpdatedAt > seenAt
        }
        val newLogbooks = viewModel.pendingLogbooks.value.orEmpty().count {
            (it.createdAt?.seconds ?: 0) * 1000 > seenAt
        }
        MentorNav.setBadge(binding.mentorNav, unreadApplicants + newLogbooks)
    }

    /**
     * Kartu status verifikasi: menunggu -> kartu tampil, daftar pengajuan &
     * logbook disembunyikan; lolos -> kartu hilang, daftar tampil normal.
     * Mentor menerima status verifikasi prodi lewat tab ini.
     */
    private fun renderVerification(uid: String, name: String) {
        val waiting = viewModel.fullyVerified.value != true
        binding.pendingVerificationCard.visibility =
            if (waiting) View.VISIBLE else View.GONE
        if (waiting) {
            binding.pendingVerificationText.text =
                if (viewModel.accountPending.value == true) {
                    getString(R.string.mentor_account_pending_message)
                } else {
                    getString(R.string.mentor_account_pending_notice)
                }
        }
        binding.notificationBadgeCard.visibility = View.GONE
        binding.notifListCard.visibility = if (waiting) View.GONE else View.VISIBLE
        binding.logbookCard.visibility = if (waiting) View.GONE else View.VISIBLE
        MentorNav.setup(
            binding.mentorNav, this, R.id.nav_notification, uid, name,
            actionsBlocked = waiting
        )
    }
}
