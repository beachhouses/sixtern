package com.example.sixtern.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sixtern.data.model.Company
import com.example.sixtern.data.model.SixternUser
import com.example.sixtern.data.repository.AuthRepository
import com.example.sixtern.util.reverseGeocode
import kotlinx.coroutines.launch

sealed class CompanyDetailUiState {
    data object Loading : CompanyDetailUiState()
    data class Loaded(
        val company: Company,
        val taken: Int,
        val companyAddress: String? = null
    ) : CompanyDetailUiState()
    data class Error(val message: String) : CompanyDetailUiState()
}

data class ApplyEvent(val success: Boolean, val companyName: String, val message: String)

class CompanyDetailViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _state = MutableLiveData<CompanyDetailUiState>(CompanyDetailUiState.Loading)
    val state: LiveData<CompanyDetailUiState> = _state

    private val _applying = MutableLiveData(false)
    val applying: LiveData<Boolean> = _applying

    private val _applyResult = MutableLiveData<ApplyEvent?>()
    val applyResult: LiveData<ApplyEvent?> = _applyResult

    fun load(companyId: String) {
        if (companyId.isBlank()) {
            _state.value = CompanyDetailUiState.Error("Perusahaan tidak ditemukan")
            return
        }
        _state.value = CompanyDetailUiState.Loading
        viewModelScope.launch {
            try {
                val company = authRepository.getCompanies()
                    .firstOrNull { it.id == companyId }
                    ?: throw IllegalStateException("Perusahaan tidak ditemukan")
                val taken = try {
                    authRepository.countStudentsByCompany(listOf(companyId))[companyId] ?: 0
                } catch (e: Exception) {
                    0
                }
                _state.value = CompanyDetailUiState.Loaded(company, taken)
                // Alamat tulisan dimuat belakangan agar halaman tidak menunggu HTTP.
                val loc = company.location
                if (loc != null) {
                    val address = reverseGeocode(loc.latitude, loc.longitude)
                    if (!address.isNullOrBlank() &&
                        _state.value is CompanyDetailUiState.Loaded
                    ) {
                        _state.value = CompanyDetailUiState.Loaded(company, taken, address)
                    }
                }
            } catch (e: Exception) {
                _state.value = CompanyDetailUiState.Error(
                    e.message ?: "Gagal memuat detail perusahaan"
                )
            }
        }
    }

    fun applyForCompany(uid: String, companyId: String) {
        val current = _state.value
        if (current !is CompanyDetailUiState.Loaded || _applying.value == true) return
        if (uid.isBlank() || companyId.isBlank()) return
        _applying.value = true
        viewModelScope.launch {
            try {
                val user: SixternUser = authRepository.getUser(uid)
                    ?: throw IllegalStateException("Profil tidak ditemukan")
                authRepository.saveUser(user.copy(companyId = companyId))
                _applyResult.value = ApplyEvent(true, current.company.name, "")
            } catch (e: Exception) {
                _applyResult.value = ApplyEvent(
                    false, "", e.message ?: "Gagal mengajukan magang"
                )
            } finally {
                _applying.value = false
            }
        }
    }

    fun clearApplyResult() {
        _applyResult.value = null
    }
}
