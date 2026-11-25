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
        val takenSlots: Map<String, Int> = emptyMap()
    ) : HomeUiState() {
        val hasCompany: Boolean get() = !user.companyId.isNullOrBlank() && company != null
    }
    data class Error(val message: String) : HomeUiState()
}

class StudentHomeViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val internshipRepository: InternshipRepository = InternshipRepository()
) : ViewModel() {

    private val _state = MutableLiveData<HomeUiState>(HomeUiState.Idle)
    val state: LiveData<HomeUiState> = _state

    private val _attendanceResult = MutableLiveData<Attendance?>()
    val attendanceResult: LiveData<Attendance?> = _attendanceResult

    fun load(uid: String, forceServer: Boolean = false) {
        _state.value = HomeUiState.Loading
        viewModelScope.launch {
            val user: SixternUser
            try {
                user = authRepository.getUser(uid)
                    ?: throw IllegalStateException("Profil tidak ditemukan")
            } catch (e: Exception) {
                _state.value = HomeUiState.Error(friendlyHomeMessage(e))
                return@launch
            }
            val companyId = user.companyId?.takeIf { it.isNotBlank() }
            var company: Company? = null
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
                // Mahasiswa tanpa tempat magang: tampilkan daftar perusahaan yang bisa dipilih.
                var companiesError: String? = loadNotice
                var available: List<Company> = emptyList()
                var taken: Map<String, Int> = emptyMap()
                if (companyId == null) {
                    try {
                        available = authRepository.getCompanies(forceServer)
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
                    insideRadius = null,
                    currentLat = null,
                    currentLng = null,
                    availableCompanies = available,
                    companiesError = companiesError,
                    takenSlots = taken
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
