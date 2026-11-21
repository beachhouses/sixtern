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
        val currentLng: Double?
    ) : HomeUiState()
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
                val company = user.companyId
                    ?.takeIf { it.isNotBlank() }
                    ?.let { loadCompany(it) }
                val evaluation = internshipRepository.getEvaluation(uid)
                val logbooks = internshipRepository.getLogbooks(uid)
                _state.value = HomeUiState.Loaded(
                    user = user,
                    company = company,
                    evaluation = evaluation,
                    attendanceCount = logbooks.size,
                    insideRadius = null,
                    currentLat = null,
                    currentLng = null
                )
            } catch (e: Exception) {
                _state.value = HomeUiState.Error(e.message ?: "Gagal memuat beranda")
            }
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
}
