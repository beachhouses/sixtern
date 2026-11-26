package com.example.sixtern.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sixtern.data.model.Attendance
import com.example.sixtern.data.model.Company
import com.example.sixtern.data.model.Evaluation
import com.example.sixtern.data.model.SixternUser
import com.example.sixtern.data.repository.AuthRepository
import com.example.sixtern.data.repository.InternshipRepository
import com.example.sixtern.data.repository.RadiusChecker
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

sealed class HomeUiState {
    data object Idle : HomeUiState()
    data object Loading : HomeUiState()
    data class Loaded(
        val user: SixternUser,
        val company: Company?,
        val evaluation: Evaluation?,
        val attendanceCount: Int,
        val insideRadius: Boolean?,
        val currentLat: Double?,
        val currentLng: Double?,
        val availableCompanies: List<Company> = emptyList(),
        val companiesError: String? = null,
        /** companyId -> jumlah mahasiswa terdaftar (untuk hitung sisa slot). */
        val takenSlots: Map<String, Int> = emptyMap(),
        /** Absensi masuk hari ini (null = belum absen). */
        val todayCheckIn: Attendance? = null,
        /** Absensi pulang hari ini (null = belum absen pulang). */
        val todayCheckOut: Attendance? = null,
        /** True bila sudah ada logbook yang dibuat hari ini. */
        val logbookDoneToday: Boolean = false
    ) : HomeUiState() {
        // Dashboard penuh hanya tampil jika pengajuan sudah DITERIMA oleh mentor.
        // Status pending/rejected -> mode 2 (daftar perusahaan) tetap tampil.
        val hasCompany: Boolean get() = !user.companyId.isNullOrBlank()
            && company != null
            && user.internshipStatus == SixternUser.STATUS_ACCEPTED
    }
    data class Error(val message: String) : HomeUiState()
    /** Akun ditolak prodi — beranda menolak akses (dialog + kembali ke login). */
    data class AccountBlocked(val pending: Boolean) : HomeUiState()
    /** Akun mahasiswa menunggu verifikasi prodi — hanya bisa melengkapi profil. */
    data class PendingVerification(val user: SixternUser) : HomeUiState()
}

class StudentHomeViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val internshipRepository: InternshipRepository = InternshipRepository()
) : ViewModel() {

    private val _state = MutableLiveData<HomeUiState>(HomeUiState.Idle)
    val state: LiveData<HomeUiState> = _state

    /** Notifikasi hasil pengajuan: null = tidak ada, data class berisi status + alasan. */
    data class ApplicationNotice(
        val accepted: Boolean,
        val companyName: String,
        val reason: String,
        /** Waktu status berubah — dibandingkan dgn waktu "sudah dilihat" (badge). */
        val statusUpdatedAt: Long = 0
    )
    private val _applicationNotice = MutableLiveData<ApplicationNotice?>()
    val applicationNotice: LiveData<ApplicationNotice?> = _applicationNotice

    private var userObserveJob: Job? = null

    /** Status terakhir yang diamati listener — untuk mendeteksi perubahan. */
    private var lastObservedStatus: String? = null

    /**
     * Pasang listener realtime pada dokumen user — saat mentor menerima/menolak
     * (atau mahasiswa mengajukan ulang), notifikasi muncul otomatis tanpa refresh
     * DAN beranda dimuat ulang agar Mode 1 (dashboard) / Mode 2 (daftar perusahaan)
     * berganti sesuai agents.md tanpa perlu keluar-masuk layar.
     */
    fun observeApplicationStatus(uid: String) {
        userObserveJob?.cancel()
        // Emisi pertama setelah pendaftaran ulang tidak memicu reload ganda
        // karena load() sudah memuat data segar bersamaan.
        lastObservedStatus = null
        userObserveJob = viewModelScope.launch {
            authRepository.observeUserByUid(uid).collectLatest { user ->
                if (user == null) return@collectLatest
                val status = user.internshipStatus
                // Amati internshipStatus + accountStatus sekaligus: saat prodi
                // memverifikasi akun, mode terbatas langsung terbuka tanpa
                // perlu login ulang.
                val combined = status + "|" + user.accountStatus
                if (lastObservedStatus != null && lastObservedStatus != combined) {
                    // Status berubah sejak emisi terakhir -> muat ulang beranda.
                    loadData(user.uid)
                }
                lastObservedStatus = combined
                val current = _state.value
                if (status == SixternUser.STATUS_ACCEPTED || status == SixternUser.STATUS_REJECTED) {
                    val companyName = (current as? HomeUiState.Loaded)?.company?.name ?: "perusahaan"
                    _applicationNotice.value = ApplicationNotice(
                        accepted = status == SixternUser.STATUS_ACCEPTED,
                        companyName = companyName,
                        reason = user.rejectionReason,
                        statusUpdatedAt = user.statusUpdatedAt
                    )
                } else {
                    _applicationNotice.value = null
                }
            }
        }
    }

    fun clearApplicationNotice() { _applicationNotice.value = null }

    private val _attendanceResult = MutableLiveData<Attendance?>()
    val attendanceResult: LiveData<Attendance?> = _attendanceResult

    fun load(uid: String, forceServer: Boolean = false) {
        _state.value = HomeUiState.Loading
        observeApplicationStatus(uid)
        loadData(uid, forceServer)
    }

    /**
     * Muat data beranda tanpa memasang ulang listener realtime.
     * Tanpa set Loading agar reload dari observer mulus (konten lama tetap
     * tampil sampai data segar tiba).
     */
    private fun loadData(uid: String, forceServer: Boolean = false) {
        // Simpan state lama dulu: lokasi terakhir dipakai lagi agar kartu
        // absensi tidak "mati sesaat" setiap reload.
        val previous = _state.value
        viewModelScope.launch {
            val user: SixternUser
            try {
                user = authRepository.getUser(uid)
                    ?: throw IllegalStateException("Profil tidak ditemukan")
            } catch (e: Exception) {
                _state.value = HomeUiState.Error(friendlyHomeMessage(e))
                return@launch
            }
            // Akun ditolak prodi: tolak akses (terjadi bila prodi mengubah
            // status selagi sesi user masih aktif; login sudah memblokir lebih awal).
            if (user.accountStatus == SixternUser.ACCOUNT_REJECTED) {
                _state.value = HomeUiState.AccountBlocked(pending = false)
                return@launch
            }
            // Mahasiswa belum diverifikasi prodi: tetap bisa masuk, tetapi
            // beranda hanya menampilkan kartu menunggu + akses melengkapi
            // profil (pilih tempat magang/logbook/nilai belum terbuka).
            if (user.accountStatus == SixternUser.ACCOUNT_PENDING) {
                _state.value = HomeUiState.PendingVerification(user)
                return@launch
            }
            val companyId = user.companyId?.takeIf { it.isNotBlank() }
            var company: Company? = null
            // Muat data perusahaan yang diajukan (untuk tampil di kartu absensi saat accepted).
            if (companyId != null) {
                company = try {
                    authRepository.getCompanies(forceServer).firstOrNull { it.id == companyId }
                } catch (e: Exception) {
                    null
                }
            }
            // Evaluasi & logbook boleh gagal sebagian (aturan/indeks):
            // daftar perusahaan tetap harus tampil, bukan seluruh layar error.
            val evaluation = try {
                internshipRepository.getEvaluation(uid)
            } catch (e: Exception) {
                null
            }
            var loadNotice: String? = null
            val logbooks = try {
                internshipRepository.getLogbooks(uid)
            } catch (e: Exception) {
                loadNotice = friendlyHomeMessage(e)
                emptyList()
            }
            // Badge logbook harian: sudah isi bila ada logbook ber-tanggal hari ini.
            val todayCal = java.util.Calendar.getInstance()
            val logbookDoneToday = logbooks.any { logbook ->
                val date = logbook.createdAt?.toDate() ?: return@any false
                val cal = java.util.Calendar.getInstance().apply { time = date }
                cal.get(java.util.Calendar.YEAR) == todayCal.get(java.util.Calendar.YEAR) &&
                    cal.get(java.util.Calendar.DAY_OF_YEAR) == todayCal.get(java.util.Calendar.DAY_OF_YEAR)
            }
            // Absensi hari ini untuk kartu status + logika tombol masuk/pulang.
            val todayAttendance = try {
                internshipRepository.getTodayAttendance(uid)
            } catch (e: Exception) {
                null to null
            }
                // Tampilkan daftar perusahaan selama belum diterima (pending/rejected/kosong).
                // Jika sudah accepted, mode 1 (dashboard) yang aktif — daftar tidak dibutuhkan.
                val needCompanyList = user.internshipStatus != SixternUser.STATUS_ACCEPTED
                var companiesError: String? = loadNotice
                var available: List<Company> = emptyList()
                var taken: Map<String, Int> = emptyMap()
                if (needCompanyList) {
                    try {
                        // Hanya perusahaan yang sudah diverifikasi prodi yang
                        // boleh dipilih mahasiswa; yang baru dibuat mentor
                        // menunggu persetujuan prodi dulu.
                        available = authRepository.getCompanies(forceServer, verifiedOnly = true)
                        // Sisa slot: hitung pendaftar per perusahaan.
                        taken = try {
                            authRepository.countStudentsByCompany(available.map { it.id })
                        } catch (e: Exception) {
                            emptyMap()
                        }
                    } catch (e: Exception) {
                        companiesError = listOfNotNull(loadNotice, friendlyHomeMessage(e))
                            .distinct().joinToString("\n")
                    }
                }
                _state.value = HomeUiState.Loaded(
                    user = user,
                    company = company,
                    evaluation = evaluation,
                    attendanceCount = logbooks.size,
                    // Pertahankan lokasi terakhir agar tombol absen & radius
                    // tidak mati sesaat setiap beranda di-reload (mis. setelah
                    // absen atau kembali dari layar lain).
                    insideRadius = (previous as? HomeUiState.Loaded)?.insideRadius,
                    currentLat = (previous as? HomeUiState.Loaded)?.currentLat,
                    currentLng = (previous as? HomeUiState.Loaded)?.currentLng,
                    availableCompanies = available,
                    companiesError = companiesError,
                    takenSlots = taken,
                    todayCheckIn = todayAttendance.first,
                    todayCheckOut = todayAttendance.second,
                    logbookDoneToday = logbookDoneToday
                )
                _joining.value = false
        }
    }

    /**
     * Ubah pesan teknis Firebase menjadi bahasa yang dimengerti mahasiswa.
     * Kasus umum di beranda: query logbook butuh composite index Firestore
     * (FAILED_PRECONDITION / "precondition") sehingga load beranda gagal.
     */
    private fun friendlyHomeMessage(e: Exception): String {
        val raw = e.message.orEmpty()
        return when {
            "FAILED_PRECONDITION" in raw || "precondition" in raw.lowercase() ->
                "Gagal memuat beranda karena database butuh penyesuaian. " +
                    "Hubungi mentor/prodi agar index Firestore dibuatkan, lalu coba lagi."
            "PERMISSION_DENIED" in raw || "permission" in raw.lowercase() ->
                "Akses database ditolak. Pastikan akun ini sudah terdaftar " +
                    "dan aturan Firestore mengizinkan membaca datanya."
            "network" in raw.lowercase() || "TIMEOUT" in raw ||
                "UNAVAILABLE" in raw ->
                "Jaringan bermasalah. Periksa koneksi internet lalu coba lagi."
            raw.isNotBlank() -> "Gagal memuat beranda: $raw"
            else -> "Gagal memuat beranda"
        }
    }

    fun updateLocation(lat: Double, lng: Double) {
        // Selalu baca state TERBARU dari LiveData: lokasi GPS datang belakangan
        // setelah loadData selesai, jadi harus memakai company terkini agar
        // status radius + tombol absen langsung aktif tanpa reload manual.
        val current = _state.value
        if (current is HomeUiState.Loaded) {
            val inside = current.company?.let {
                RadiusChecker.isInsideRadius(
                    lat, lng,
                    it.location?.latitude, it.location?.longitude,
                    it.radiusMeters
                )
            }
            _state.value = current.copy(
                insideRadius = inside,
                currentLat = lat,
                currentLng = lng
            )
        }
    }

    fun recordAttendance(type: String) {
        val current = _state.value
        if (current !is HomeUiState.Loaded) return
        val companyId = current.user.companyId
        if (companyId.isNullOrBlank()) {
            _attendanceResult.value = null
            return
        }
        viewModelScope.launch {
            try {
                val attendance = internshipRepository.recordAttendance(
                    studentId = current.user.uid,
                    companyId = companyId,
                    type = type,
                    photoUrl = "",
                    latitude = current.currentLat ?: 0.0,
                    longitude = current.currentLng ?: 0.0
                )
                _attendanceResult.value = attendance
                // Muat ulang tanpa Loading agar tombol masuk/pulang berganti mulus.
                loadData(current.user.uid)
            } catch (e: Exception) {
                _attendanceResult.value = null
            }
        }
    }

    fun clearAttendanceResult() {
        _attendanceResult.value = null
    }

    private val _joining = MutableLiveData<Boolean>(false)
    val joining: LiveData<Boolean> = _joining

    private val _joinResult = MutableLiveData<String?>(null)
    val joinResult: LiveData<String?> = _joinResult

    /** Mahasiswa memilih tempat magang: simpan companyId lalu muat ulang beranda. */
    fun joinCompany(companyId: String) {
        val current = _state.value
        if (current !is HomeUiState.Loaded || _joining.value == true) return
        if (companyId.isBlank()) return
        _joining.value = true
        viewModelScope.launch {
            try {
                val updated = current.user.copy(companyId = companyId)
                authRepository.saveUser(updated)
                _joinResult.value = current.availableCompanies
                    .firstOrNull { it.id == companyId }?.name.orEmpty()
                load(current.user.uid)
            } catch (e: Exception) {
                _joining.value = false
                _state.value = HomeUiState.Error(e.message ?: "Gagal memilih tempat magang")
            }
        }
    }

    fun clearJoinResult() {
        _joinResult.value = null
    }

}
