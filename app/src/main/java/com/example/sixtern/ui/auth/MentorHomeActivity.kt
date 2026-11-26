package com.example.sixtern.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.sixtern.R
import com.example.sixtern.data.model.Company
import com.example.sixtern.data.model.SixternUser
import com.example.sixtern.data.repository.AuthRepository
import com.example.sixtern.databinding.ActivityMentorHomeBinding
import com.example.sixtern.ui.profile.MentorProfileActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Beranda mentor yang simpel: sapaan + status ringkas + bottom nav (Beranda,
 * Peserta, Notifikasi, Akun). Detail perusahaan BOLEH diisi belakangan lewat
 * tab Akun (MentorProfileActivity), jadi mentor bebas eksplor Beranda dulu
 * tanpa dipaksa isi form saat login. Mentor yang belum diverifikasi prodi
 * (akun / perusahaan) tetap bisa membuka Beranda (status + update info
 * perusahaan), Notifikasi (menerima status verifikasi) dan Akun — hanya
 * tab Peserta yang menunggu persetujuan prodi.
 */
class MentorHomeViewModel(
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _company = MutableLiveData<Company?>()
    val company: LiveData<Company?> = _company

    private val _applicants = MutableLiveData<List<SixternUser>>(emptyList())
    val applicants: LiveData<List<SixternUser>> = _applicants

    /** True bila akun mentor belum diverifikasi prodi (mode terbatas). */
    private val _accountPending = MutableLiveData(false)
    val accountPending: LiveData<Boolean> = _accountPending

    /** Akun ditolak prodi — beranda hanya menampilkan pesan + tombol keluar. */
    private val _accountRejected = MutableLiveData(false)
    val accountRejected: LiveData<Boolean> = _accountRejected

    // Job aktif listener realtime — dibatalkan saat load() dipanggil ulang.
    private var observeJob: Job? = null
    private var accountJob: Job? = null

    fun load(uid: String) {
        viewModelScope.launch {
            val user = try {
                repository.getUser(uid)
            } catch (e: Exception) {
                null
            }
            _accountPending.value = user?.accountStatus == SixternUser.ACCOUNT_PENDING
            _accountRejected.value = user?.accountStatus == SixternUser.ACCOUNT_REJECTED

            // Pantau status akun realtime: verifikasi/penolakan prodi langsung
            // tampil tanpa perlu login ulang.
            accountJob?.cancel()
            accountJob = viewModelScope.launch {
                repository.observeUserByUid(uid).collectLatest { observed ->
                    _accountPending.value =
                        observed?.accountStatus == SixternUser.ACCOUNT_PENDING
                    _accountRejected.value =
                        observed?.accountStatus == SixternUser.ACCOUNT_REJECTED
                }
            }

            val company = try {
                repository.getCompanyByMentor(uid)
            } catch (e: Exception) {
                null
            }
            _company.value = company

            // Batalkan listener lama sebelum pasang yang baru.
            observeJob?.cancel()
            if (company == null) {
                _applicants.value = emptyList()
                return@launch
            }
            observeJob = viewModelScope.launch {
                repository.observeStudentsByCompany(company.id)
                    .collectLatest { list -> _applicants.value = list }
            }
        }
    }

    fun logout() = repository.signOut()
}

class MentorHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMentorHomeBinding
    private val viewModel: MentorHomeViewModel by viewModels()
    private lateinit var applicantAdapter: ApplicantAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMentorHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applicantAdapter = ApplicantAdapter { user ->
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
        binding.applicantsRecycler.layoutManager = LinearLayoutManager(this)
        binding.applicantsRecycler.adapter = applicantAdapter

        val uid = intent.getStringExtra(LoginActivity.EXTRA_USER_ID).orEmpty()
        val name = intent.getStringExtra(LoginActivity.EXTRA_USER_NAME).orEmpty()

        binding.greetingText.text = if (name.isBlank()) {
            getString(R.string.mentor_home_greeting_fallback)
        } else {
            getString(R.string.mentor_home_greeting, name)
        }

        binding.mentorNav.itemIconTintList = null
        MentorNav.setup(binding.mentorNav, this, R.id.nav_home, uid, name)

        viewModel.company.observe(this) { company ->
            binding.companyStatusText.text = if (company == null) {
                getString(R.string.mentor_home_company_empty)
            } else {
                getString(
                    R.string.mentor_home_company_done,
                    company.name,
                    company.workDays.joinToString(", ")
                )
            }
            // Status verifikasi prodi: perusahaan baru mentor belum tampil
            // ke mahasiswa sampai prodi menyetujuinya.
            if (company == null) {
                binding.companyVerifiedText.visibility = View.GONE
            } else {
                binding.companyVerifiedText.visibility = View.VISIBLE
                if (company.verified) {
                    binding.companyVerifiedText.text =
                        getString(R.string.mentor_company_verified)
                    binding.companyVerifiedText.setTextColor(
                        androidx.core.content.ContextCompat.getColor(
                            this, R.color.sixtern_green_deep
                        )
                    )
                } else {
                    binding.companyVerifiedText.text =
                        getString(R.string.mentor_company_pending)
                    binding.companyVerifiedText.setTextColor(
                        androidx.core.content.ContextCompat.getColor(
                            this, R.color.login_text_secondary
                        )
                    )
                }
            }
            renderWaitingState(uid, name)
            renderApplicants(
                company != null,
                viewModel.applicants.value.orEmpty()
            )
        }

        viewModel.applicants.observe(this) { applicants ->
            renderApplicants(
                viewModel.company.value != null,
                applicants.orEmpty()
            )
        }

        viewModel.accountPending.observe(this) { renderWaitingState(uid, name) }
        viewModel.accountRejected.observe(this) { renderWaitingState(uid, name) }

        // Profil perusahaan tetap bisa diedit walau belum diverifikasi prodi.
        binding.waitingManageButton.setOnClickListener {
            val intent = Intent(this, MentorProfileActivity::class.java)
            intent.putExtra(LoginActivity.EXTRA_USER_ID, uid)
            intent.putExtra(LoginActivity.EXTRA_USER_NAME, name)
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

        binding.waitingLogoutButton.setOnClickListener {
            viewModel.logout()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        binding.companyButton.setOnClickListener {
            val intent = Intent(this, MentorProfileActivity::class.java)
            intent.putExtra(LoginActivity.EXTRA_USER_ID, uid)
            intent.putExtra(LoginActivity.EXTRA_USER_NAME, name)
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun onResume() {
        super.onResume()
        // Pengajuan baru masuk kapan saja -> muat ulang tiap kembali ke beranda.
        val uid = intent.getStringExtra(LoginActivity.EXTRA_USER_ID).orEmpty()
        if (uid.isNotBlank()) viewModel.load(uid)
    }

    /** True bila akun atau perusahaan mentor masih menunggu verifikasi prodi. */
    private fun isWaiting(): Boolean {
        val accountPending = viewModel.accountPending.value == true
        val company = viewModel.company.value
        return accountPending || (company != null && !company.verified)
    }

    /**
     * Status menunggu verifikasi prodi: kartu tunggu tampil di atas, kartu
     * perusahaan TETAP tampil (mentor tetap bisa update info perusahaan),
     * kartu pengajuan disembunyikan, hanya navigasi Peserta yang diblokir.
     * Verifikasi prodi (realtime) langsung membuka mode penuh tanpa login ulang.
     */
    private fun renderWaitingState(uid: String, name: String) {
        val rejected = viewModel.accountRejected.value == true
        val waiting = isWaiting()
        binding.waitingCard.visibility = if (waiting || rejected) View.VISIBLE else View.GONE
        // Kartu perusahaan tetap terbuka agar mentor bisa melengkapi /
        // memperbarui info perusahaan sambil menunggu verifikasi prodi.
        binding.companyStatusCard.visibility = View.VISIBLE
        if (waiting || rejected) {
            val accountPending = viewModel.accountPending.value == true
            val company = viewModel.company.value
            binding.waitingMessageText.text = when {
                rejected -> getString(R.string.account_rejected_message)
                accountPending -> getString(R.string.mentor_account_pending_message)
                else -> getString(
                    R.string.mentor_company_waiting_message,
                    company?.name.orEmpty().ifBlank { "perusahaan" }
                )
            }
        }
        MentorNav.setup(
            binding.mentorNav, this, R.id.nav_home, uid, name,
            actionsBlocked = waiting || rejected
        )
    }

    /**
     * Kartu pengajuan tampil hanya bila ada pengaju, TETAPI disembunyikan
     * selama akun/perusahaan menunggu verifikasi prodi (kelola peserta belum
     * boleh). Daftar pengajuan normal (perusahaan belum diisi) disembunyikan
     * agar mentor paham harus lengkapi profil dulu.
     */
    private fun renderApplicants(hasCompany: Boolean, applicants: List<SixternUser>) {
        // Menunggu verifikasi prodi: pengelolaan peserta belum boleh dibuka.
        if (isWaiting()) {
            binding.applicantsNoticeCard.visibility = View.GONE
            binding.applicantsCard.visibility = View.GONE
            MentorNav.setBadge(binding.mentorNav, 0)
            return
        }
        binding.applicantsCard.visibility = View.VISIBLE
        applicantAdapter.submitList(applicants)
        val count = applicants.size

        // Badge = pengajuan pending yang BELUM dilihat mentor (lebih baru dari
        // waktu terakhir membuka halaman notifikasi), bukan total pengaju.
        val mentorUid = intent.getStringExtra(LoginActivity.EXTRA_USER_ID).orEmpty()
        val seenAt = com.example.sixtern.util.NotificationPrefs.getSeenAt(this, mentorUid)
        val unread = applicants.count {
            it.internshipStatus == SixternUser.STATUS_PENDING && it.statusUpdatedAt > seenAt
        }
        MentorNav.setBadge(binding.mentorNav, unread)

        binding.applicantsNoticeCard.visibility =
            if (count > 0) View.VISIBLE else View.GONE
        if (count > 0) {
            binding.applicantsBadge.text = count.toString()
            binding.applicantsNoticeText.text =
                getString(R.string.mentor_applicants_notice, count)
        }
        binding.applicantsRecycler.visibility =
            if (hasCompany && count > 0) View.VISIBLE else View.GONE
        binding.applicantsEmptyText.visibility =
            if (hasCompany && count > 0) View.GONE else View.VISIBLE
        if (!hasCompany) {
            binding.applicantsEmptyText.text =
                getString(R.string.mentor_applicants_need_company)
        } else if (count == 0) {
            binding.applicantsEmptyText.text =
                getString(R.string.mentor_applicants_empty)
        }
    }
}
