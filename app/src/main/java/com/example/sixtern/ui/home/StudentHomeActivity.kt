package com.example.sixtern.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.sixtern.R
import com.example.sixtern.data.model.Attendance
import com.example.sixtern.data.model.Company
import com.example.sixtern.data.model.endTimeForToday
import com.example.sixtern.databinding.ActivityStudentHomeBinding
import com.example.sixtern.ui.auth.LoginActivity
import com.example.sixtern.ui.evaluation.EvaluationActivity
import com.example.sixtern.ui.logbook.LogbookActivity
import com.example.sixtern.ui.logbook.LogbookFormActivity
import com.example.sixtern.ui.profile.ProfileActivity
import com.example.sixtern.ui.profile.StudentProfileEditActivity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class StudentHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentHomeBinding
    private val viewModel: StudentHomeViewModel by viewModels()
    private lateinit var companyAdapter: CompanyPickAdapter

    /** Penanda dialog "akun belum terverifikasi" sedang/telah tampil. */
    private var blockedDialogShown = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) {
            fetchLocation()
        } else {
            Toast.makeText(this, getString(R.string.location_denied), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        companyAdapter = CompanyPickAdapter { company -> openCompanyDetail(company) }
        binding.companyRecycler.layoutManager = LinearLayoutManager(this)
        binding.companyRecycler.adapter = companyAdapter
        val uid = intent.getStringExtra(LoginActivity.EXTRA_USER_ID).orEmpty()
        val name = intent.getStringExtra(LoginActivity.EXTRA_USER_NAME).orEmpty()
        binding.greetingText.text = if (name.isBlank()) "Halo!" else "Halo, $name!"
        viewModel.load(uid)
        requestLocation()
        binding.homeMenuBar.itemIconTintList = null
        // Setup nav diperbarui tiap kali state/notice berubah agar data notif terbaru diteruskan.
        fun refreshNav() {
            val state = viewModel.state.value
            val notice = viewModel.applicationNotice.value
            val companyId = if (state is HomeUiState.Loaded) state.user.companyId.orEmpty() else ""
            val notifStatus = notice?.let {
                if (it.accepted) com.example.sixtern.data.model.SixternUser.STATUS_ACCEPTED
                else com.example.sixtern.data.model.SixternUser.STATUS_REJECTED
            } ?: if ((state as? HomeUiState.Loaded)?.user?.companyId.isNullOrBlank()) ""
                else com.example.sixtern.data.model.SixternUser.STATUS_PENDING
            val notifCompanyName = notice?.companyName
                ?: (state as? HomeUiState.Loaded)?.company?.name.orEmpty()
            val notifReason = notice?.reason.orEmpty()
            val pending = state is HomeUiState.PendingVerification
            StudentNav.setup(
                binding.homeMenuBar, this, R.id.nav_home, uid, companyId,
                notifStatus, notifCompanyName, notifReason,
                accountPending = pending
            )
        }
        viewModel.state.observe(this) { refreshNav() }
        viewModel.applicationNotice.observe(this) { refreshNav() }
        StudentNav.setup(binding.homeMenuBar, this, R.id.nav_home, uid)
        binding.pendingEditProfileButton.setOnClickListener {
            val intent = Intent(this, StudentProfileEditActivity::class.java)
            intent.putExtra(LoginActivity.EXTRA_USER_ID, currentUid())
            startActivity(intent)
        }
        binding.quickLogbook.setOnClickListener {
            val intent = Intent(this, LogbookActivity::class.java)
            intent.putExtra(LoginActivity.EXTRA_USER_ID, currentUid())
            intent.putExtra(LogbookActivity.EXTRA_COMPANY_ID, currentCompanyId())
            startActivity(intent)
        }
        binding.quickEval.setOnClickListener {
            val intent = Intent(this, EvaluationActivity::class.java)
            intent.putExtra(LoginActivity.EXTRA_USER_ID, currentUid())
            startActivity(intent)
        }
        binding.quickProfile.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            intent.putExtra(LoginActivity.EXTRA_USER_ID, currentUid())
            startActivity(intent)
        }
        binding.checkInButton.setOnClickListener {
            doAttendance(Attendance.TYPE_IN)
        }
        binding.checkOutButton.setOnClickListener {
            doAttendance(Attendance.TYPE_OUT)
        }
        binding.retryCompaniesButton.setOnClickListener {
            val uid = currentUid()
            if (uid.isNotBlank()) viewModel.load(uid, forceServer = true)
        }
        viewModel.state.observe(this) { state ->
            when (state) {
                is HomeUiState.Idle -> Unit
                is HomeUiState.Loading -> binding.loadingBar.visibility = View.VISIBLE
                is HomeUiState.PendingVerification -> {
                    binding.loadingBar.visibility = View.GONE
                    renderPendingVerification(state.user.name)
                }
                is HomeUiState.Loaded -> {
                    binding.loadingBar.visibility = View.GONE
                    renderLoaded(state)
                    // GPS kadang kalah cepat dari loadData: setiap data segar
                    // tiba, minta lokasi ulang agar radius + tombol absen
                    // langsung terisi tanpa harus reload manual.
                    requestLocation()
                }
                is HomeUiState.Error -> {
                    binding.loadingBar.visibility = View.GONE
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
                is HomeUiState.AccountBlocked -> {
                    binding.loadingBar.visibility = View.GONE
                    if (blockedDialogShown) return@observe
                    blockedDialogShown = true
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.account_pending_title))
                        .setMessage(
                            getString(
                                if (state.pending) R.string.account_pending_message
                                else R.string.account_rejected_message
                            )
                        )
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            startActivity(Intent(this, LoginActivity::class.java))
                            finish()
                        }
                        .show()
                }
            }
        }
        viewModel.attendanceResult.observe(this) { attendance ->
            if (attendance != null) {
                val message = if (attendance.isValid) {
                    getString(R.string.attendance_valid, attendance.type)
                } else {
                    getString(R.string.attendance_outside)
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                viewModel.clearAttendanceResult()
            }
        }
        viewModel.joinResult.observe(this) { name ->
            if (name != null) {
                Toast.makeText(
                    this,
                    getString(R.string.choose_company_joined, name.ifBlank { "perusahaan" }),
                    Toast.LENGTH_LONG
                ).show()
                viewModel.clearJoinResult()
            }
        }
        viewModel.joining.observe(this) { joining ->
            binding.joiningBar.visibility = if (joining) View.VISIBLE else View.GONE
        }

        viewModel.applicationNotice.observe(this) { notice ->
            // Badge di tab Notifikasi: hanya muncul bila belum pernah dilihat
            // (status berubah SETELAH terakhir user membuka halaman notifikasi).
            val seenAt = com.example.sixtern.util.NotificationPrefs.getSeenAt(this, currentUid())
            val unseen = notice != null && notice.statusUpdatedAt > seenAt
            StudentNav.setBadge(binding.homeMenuBar, if (unseen) 1 else 0)

            if (notice == null) {
                binding.applicationNoticeCard.visibility = View.GONE
                return@observe
            }
            binding.applicationNoticeCard.visibility = View.VISIBLE
            if (notice.accepted) {
                binding.applicationNoticeCard.setBackgroundResource(R.drawable.bg_notice_accepted)
                binding.noticeIcon.text = "✓"
                binding.noticeTitle.text = getString(R.string.notice_accepted_title)
                binding.noticeBody.text = getString(R.string.notice_accepted_body, notice.companyName)
                binding.noticeReason.visibility = View.GONE
            } else {
                binding.applicationNoticeCard.setBackgroundResource(R.drawable.bg_notice_rejected)
                binding.noticeIcon.text = "✗"
                binding.noticeTitle.text = getString(R.string.notice_rejected_title)
                binding.noticeBody.text = getString(R.string.notice_rejected_body, notice.companyName)
                if (notice.reason.isNotBlank()) {
                    binding.noticeReason.visibility = View.VISIBLE
                    binding.noticeReason.text = getString(R.string.notice_rejected_reason, notice.reason)
                } else {
                    binding.noticeReason.visibility = View.GONE
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Kembali dari layar lain: muat ulang data + lokasi agar kartu absensi
        // (radius, jam, tombol) selalu segar tanpa reload manual.
        val uid = currentUid()
        if (uid.isNotBlank()) {
            viewModel.load(uid)
            requestLocation()
        }
    }

    private fun currentUid(): String =
        intent.getStringExtra(LoginActivity.EXTRA_USER_ID).orEmpty()

    private fun currentCompanyId(): String {
        val state = viewModel.state.value
        return if (state is HomeUiState.Loaded) state.user.companyId.orEmpty() else ""
    }

    private fun renderLoaded(state: HomeUiState.Loaded) {
        // Keluar dari mode pending (mis. setelah diverifikasi prodi tanpa login ulang)
        // -> pastikan grup pending disembunyikan lagi.
        binding.verificationGroup.visibility = View.GONE
        val user = state.user
        binding.avatarText.text = user.name.firstOrNull()?.uppercase() ?: "S"
        binding.nimText.text = user.nim ?: "-"
        binding.universityText.text = user.university + " - " + user.major
        renderInternshipProgress(user)
        if (state.hasCompany) {
            // MODE 1: sudah magang -> dashboard penuh.
            binding.dashboardGroup.visibility = View.VISIBLE
            binding.companyPickerGroup.visibility = View.GONE
            renderDashboard(state)
        } else {
            // MODE 2: belum memilih -> daftar perusahaan yang bisa dipilih.
            binding.dashboardGroup.visibility = View.GONE
            binding.companyPickerGroup.visibility = View.VISIBLE
            companyAdapter.updateTakenSlots(state.takenSlots)
            companyAdapter.submitList(state.availableCompanies)
            val isEmpty = state.availableCompanies.isEmpty()
            binding.companyEmptyCard.visibility = if (isEmpty) View.VISIBLE else View.GONE
            binding.companyRecycler.visibility = if (isEmpty) View.GONE else View.VISIBLE
            // Pesan error load daftar ikut tampil di kartu kosong bila ada.
            if (isEmpty && !state.companiesError.isNullOrBlank()) {
                binding.companyEmptyText.text = state.companiesError
            } else {
                binding.companyEmptyText.text = getString(R.string.choose_company_empty)
            }
        }
    }

    private fun renderPendingVerification(displayName: String) {
        val name = displayName.ifBlank {
            intent.getStringExtra(LoginActivity.EXTRA_USER_NAME).orEmpty()
        }
        binding.greetingText.text = if (name.isBlank()) "Halo!" else "Halo, $name!"
        binding.avatarText.text = name.firstOrNull()?.uppercase() ?: "S"
        binding.dashboardGroup.visibility = View.GONE
        binding.companyPickerGroup.visibility = View.GONE
        binding.applicationNoticeCard.visibility = View.GONE
        binding.verificationGroup.visibility = View.VISIBLE
    }

    /**
     * Progres magang di bawah kartu sapaan: ProgressBar + teks "Hari ke-X
     * dari Y hari (Z%)" dari tanggal masuk - tanggal selesai pengajuan.
     * Hanya tampil di Mode 1 (sudah diterima); sembunyi bila tanpa periode.
     */
    private fun renderInternshipProgress(user: com.example.sixtern.data.model.SixternUser) {
        val (elapsed, total, percent) =
            com.example.sixtern.data.model.SixternUser.internshipProgress(
                user.internshipStartDate, user.internshipEndDate
            )
        if (total <= 0) {
            binding.internshipProgressGroup.visibility = View.GONE
            return
        }
        binding.internshipProgressGroup.visibility = View.VISIBLE
        binding.internshipProgressBar.progress = percent
        val today = System.currentTimeMillis()
        val parser = java.text.SimpleDateFormat(
            com.example.sixtern.data.model.SixternUser.DATE_PATTERN, java.util.Locale.US
        )
        val startMillis = runCatching { parser.parse(user.internshipStartDate)?.time ?: 0L }.getOrDefault(0L)
        val endMillis = runCatching { parser.parse(user.internshipEndDate)?.time ?: 0L }.getOrDefault(0L)
        binding.internshipProgressText.text = when {
            today < startMillis -> getString(
                R.string.internship_progress_not_started,
                com.example.sixtern.data.model.SixternUser.formatInternshipDate(user.internshipStartDate)
            )
            today > endMillis -> getString(R.string.internship_progress_finished, total)
            else -> getString(R.string.internship_progress_days, elapsed, total, percent)
        }
    }

    private fun renderDashboard(state: HomeUiState.Loaded) {
        val company = state.company
        if (company != null) {
            binding.companyText.text = company.name
            binding.scheduleText.text = company.startTime + " - " + company.endTime
        } else {
            binding.companyText.text = getString(R.string.no_company)
            binding.scheduleText.text = "-"
        }
        renderDailyLogbookBadge(state)
        binding.attendanceCountText.text = state.attendanceCount.toString()
        val score = state.evaluation?.mentorScore
        binding.mentorScoreText.text = if (score != null) score.toString() else "-"

        // Baris tanggal hari ini (kolom 1) + jam masuk-pulang (kolom 2).
        val dateFmt = java.text.SimpleDateFormat("EEEE, dd MMM", java.util.Locale("id"))
        binding.todayDateText.text = dateFmt.format(java.util.Date())
        val timeFmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale("id"))
        val inTime = state.todayCheckIn?.timestamp?.toDate()?.let { timeFmt.format(it) }
        val outTime = state.todayCheckOut?.timestamp?.toDate()?.let { timeFmt.format(it) }
        binding.todayTimesText.text = when {
            inTime != null && outTime != null -> "$inTime - $outTime"
            inTime != null -> "$inTime - -"
            else -> getString(R.string.attendance_not_yet)
        }

        // Tombol bergantian: belum masuk -> Masuk; sudah masuk -> Pulang;
        // sudah keduanya -> teks "absensi selesai".
        val hasIn = state.todayCheckIn != null
        val hasOut = state.todayCheckOut != null
        when {
            hasIn && hasOut -> {
                binding.checkInButton.visibility = View.GONE
                binding.checkOutButton.visibility = View.GONE
                binding.attendanceDoneText.visibility = View.VISIBLE
            }
            hasIn -> {
                binding.checkInButton.visibility = View.GONE
                binding.checkOutButton.visibility = View.VISIBLE
                binding.attendanceDoneText.visibility = View.GONE
            }
            else -> {
                binding.checkInButton.visibility = View.VISIBLE
                binding.checkOutButton.visibility = View.GONE
                binding.attendanceDoneText.visibility = View.GONE
            }
        }

        when (state.insideRadius) {
            true -> {
                binding.radiusDot.setBackgroundResource(R.drawable.bg_dot_green)
                binding.radiusStatus.text = getString(R.string.inside_radius)
                binding.radiusStatus.setTextColor(ContextCompat.getColor(this, R.color.sixtern_green))
            }
            false -> {
                binding.radiusDot.setBackgroundResource(R.drawable.bg_dot_plum)
                binding.radiusStatus.text = getString(R.string.outside_radius)
                binding.radiusStatus.setTextColor(ContextCompat.getColor(this, R.color.login_button))
            }
            null -> {
                binding.radiusDot.setBackgroundResource(R.drawable.bg_dot_grey)
                binding.radiusStatus.text = getString(R.string.checking_radius)
                binding.radiusStatus.setTextColor(ContextCompat.getColor(this, R.color.login_text_secondary))
            }
        }
        val hasLocation = state.currentLat != null && state.currentLng != null
        binding.checkInButton.isEnabled = hasLocation
        binding.checkOutButton.isEnabled = hasLocation
    }

    /**
     * Badge shortcut logbook harian di bawah kartu absensi: tampil bila belum
     * isi logbook hari ini (status OPEN/MISSED). Klik -> halaman buat logbook.
     * Batas pengisian = jam pulang kerja hari ini dari jadwal perusahaan.
     */
    private fun renderDailyLogbookBadge(state: HomeUiState.Loaded) {
        val company = state.company
        if (company == null) {
            binding.dailyLogbookCard.visibility = View.GONE
            return
        }
        val limit = company.endTimeForToday()
        val status = com.example.sixtern.data.model.dailyLogbookStatus(
            hasLogbookToday = state.logbookDoneToday,
            endTimeToday = limit
        )
        when (status) {
            com.example.sixtern.data.model.DailyLogbookStatus.DONE -> {
                binding.dailyLogbookCard.visibility = View.GONE
            }
            com.example.sixtern.data.model.DailyLogbookStatus.OPEN -> {
                binding.dailyLogbookCard.visibility = View.VISIBLE
                binding.dailyLogbookStatus.text =
                    getString(R.string.daily_logbook_open, limit)
                binding.dailyLogbookCard.setOnClickListener { openLogbookForm() }
            }
            com.example.sixtern.data.model.DailyLogbookStatus.MISSED -> {
                binding.dailyLogbookCard.visibility = View.VISIBLE
                binding.dailyLogbookStatus.text =
                    getString(R.string.daily_logbook_missed, limit)
                // Batas lewat: tetap bisa dibuka, tapi beri tahu dulu.
                binding.dailyLogbookCard.setOnClickListener {
                    Toast.makeText(
                        this,
                        getString(R.string.daily_logbook_expired_toast, limit),
                        Toast.LENGTH_LONG
                    ).show()
                    openLogbookForm()
                }
            }
        }
    }

    /** Buka halaman buat logbook (form baru, bukan daftar). */
    private fun openLogbookForm() {
        val intent = Intent(this, LogbookFormActivity::class.java)
        intent.putExtra(LoginActivity.EXTRA_USER_ID, currentUid())
        intent.putExtra(LogbookActivity.EXTRA_COMPANY_ID, currentCompanyId())
        startActivity(intent)
    }

    private val companyDetailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Kembali dari detail setelah mengajukan -> muat ulang beranda (Mode 1).
        if (result.resultCode == RESULT_OK) {
            val uid = currentUid()
            if (uid.isNotBlank()) viewModel.load(uid)
        }
    }

    /** Klik kartu perusahaan -> buka halaman detail + ajukan dari sana. */
    private fun openCompanyDetail(company: Company) {
        val intent = Intent(this, CompanyDetailActivity::class.java)
        intent.putExtra(LoginActivity.EXTRA_USER_ID, currentUid())
        intent.putExtra(CompanyDetailActivity.EXTRA_COMPANY_ID, company.id)
        companyDetailLauncher.launch(intent)
    }

    /**
     * Jika mahasiswa di luar radius, tampilkan dialog konfirmasi dulu.
     * Absen tetap tersimpan dengan isValid = false setelah dikonfirmasi.
     */
    private fun doAttendance(type: String) {
        val state = viewModel.state.value
        val insideRadius = (state as? HomeUiState.Loaded)?.insideRadius
        if (insideRadius == false) {
            val typeLabel = if (type == Attendance.TYPE_IN)
                getString(R.string.check_in) else getString(R.string.check_out)
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.attendance_outside_radius_title))
                .setMessage(getString(R.string.attendance_outside_radius_msg, typeLabel))
                .setPositiveButton(getString(R.string.attendance_outside_radius_confirm)) { _, _ ->
                    viewModel.recordAttendance(type)
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        } else {
            viewModel.recordAttendance(type)
        }
    }

    /** Throttle fetch GPS: terakhir kali lokasi diminta (epoch millis). */
    private var lastLocationRequestAt: Long = 0

    private fun requestLocation() {
        // GPS diminta dari banyak titik (onCreate, onResume, tiap Loaded):
        // batasi minimal jeda 10 detik agar tidak spam sensor/baterai.
        val now = System.currentTimeMillis()
        if (now - lastLocationRequestAt < 10_000) return
        lastLocationRequestAt = now
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            fetchLocation()
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    private fun fetchLocation() {
        try {
            val client = LocationServices.getFusedLocationProviderClient(this)
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        viewModel.updateLocation(location.latitude, location.longitude)
                    }
                }
        } catch (e: SecurityException) {
            Toast.makeText(this, getString(R.string.location_denied), Toast.LENGTH_SHORT).show()
        }
    }
}
