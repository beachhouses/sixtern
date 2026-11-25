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
        val availableCompanies: List<Company> = emptyList()
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

    fun load(uid: String) {
        _state.value = HomeUiState.Loading
        viewModelScope.launch {
            try {
                val user = authRepository.getUser(uid)
                    ?: throw IllegalStateException("Profil tidak ditemukan")
                val companyId = user.companyId?.takeIf { it.isNotBlank() }
                val company = companyId?.let { loadCompany(it) }
                val evaluation = internshipRepository.getEvaluation(uid)
                val logbooks = internshipRepository.getLogbooks(uid)
                // Mahasiswa tanpa tempat magang: tampilkan daftar perusahaan yang bisa dipilih.
                val available = if (companyId == null) {
                    loadCompanies()
                } else {
                    emptyList()
                }
                _state.value = HomeUiState.Loaded(
                    user = user,
                    company = company,
                    evaluation = evaluation,
                    attendanceCount = logbooks.size,
                    insideRadius = null,
                    currentLat = null,
                    currentLng = null,
                    availableCompanies = available
                )
                _joining.value = false
            } catch (e: Exception) {
                _state.value = HomeUiState.Error(e.message ?: "Gagal memuat beranda")
            }
        }
    }

    private suspend fun loadCompanies(): List<Company> {
        return try {
            authRepository.getCompanies()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun loadCompany(companyId: String): Company? {
        return try {
            authRepository.getCompanies().firstOrNull { it.id == companyId }
        } catch (e: Exception) {
            null
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
