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
        val companyAddress: String? = null,
        /** companyId yang sudah diajukan user (null = belum pernah ajukan). */
        val userAppliedCompanyId: String? = null,
        /** Status pengajuan user: pending/accepted/rejected. */
        val userStatus: String = SixternUser.STATUS_PENDING
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

    fun load(companyId: String, uid: String = "") {
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
                // Cek apakah user sudah mengajukan ke perusahaan mana pun.
                val user = if (uid.isNotBlank()) {
                    try { authRepository.getUser(uid) } catch (e: Exception) { null }
                } else null
                val appliedId = user?.companyId?.takeIf { it.isNotBlank() }
                val userStatus = user?.internshipStatus ?: SixternUser.STATUS_PENDING

                _state.value = CompanyDetailUiState.Loaded(
                    company, taken,
                    userAppliedCompanyId = appliedId,
                    userStatus = userStatus
                )
                // Alamat tulisan dimuat belakangan agar halaman tidak menunggu HTTP.
                val loc = company.location
                if (loc != null) {
                    val address = reverseGeocode(loc.latitude, loc.longitude)
                    val cur = _state.value
                    if (!address.isNullOrBlank() && cur is CompanyDetailUiState.Loaded) {
                        _state.value = cur.copy(companyAddress = address)
                    }
                }
            } catch (e: Exception) {
                _state.value = CompanyDetailUiState.Error(
                    e.message ?: "Gagal memuat detail perusahaan"
                )
            }
        }
    }

    fun applyForCompany(
        uid: String,
        companyId: String,
        startDate: String = "",
        endDate: String = "",
        letterDataUri: String = ""
    ) {
        val current = _state.value
        if (current !is CompanyDetailUiState.Loaded || _applying.value == true) return
        if (uid.isBlank() || companyId.isBlank()) return
        // Perusahaan belum diverifikasi prodi — tidak boleh diajukan.
        if (!current.company.verified) {
            _applyResult.value = ApplyEvent(false, "", "company_unverified")
            return
        }
        // Periode magang wajib valid + surat permohonan wajib ada.
        val periodError = SixternUser.validateInternshipPeriod(startDate, endDate)
        if (periodError != null) {
            _applyResult.value = ApplyEvent(false, "", periodError)
            return
        }
        if (letterDataUri.isBlank()) {
            _applyResult.value = ApplyEvent(false, "", "letter_required")
            return
        }
        _applying.value = true
        viewModelScope.launch {
            try {
                val user: SixternUser = authRepository.getUser(uid)
                    ?: throw IllegalStateException("Profil tidak ditemukan")
                // Re-apply setelah ditolak: reset status ke pending agar mentor
                // meninjau ulang (tombol Terima/Tolak muncul lagi di detail peserta).
                authRepository.saveUser(
                    user.copy(
                        companyId = companyId,
                        internshipStatus = SixternUser.STATUS_PENDING,
                        rejectionReason = "",
                        internshipStartDate = startDate,
                        internshipEndDate = endDate,
                        applicationLetterUrl = letterDataUri,
                        statusUpdatedAt = System.currentTimeMillis()
                    )
                )
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

    fun cancelApplication(uid: String) {
        if (uid.isBlank() || _applying.value == true) return
        _applying.value = true
        viewModelScope.launch {
            try {
                val user = authRepository.getUser(uid)
                    ?: throw IllegalStateException("Profil tidak ditemukan")
                authRepository.saveUser(
                    user.copy(
                        companyId = null,
                        internshipStatus = SixternUser.STATUS_PENDING,
                        rejectionReason = "",
                        statusUpdatedAt = System.currentTimeMillis()
                    )
                )
                _applyResult.value = ApplyEvent(false, "", "cancel_success")
            } catch (e: Exception) {
                _applyResult.value = ApplyEvent(
                    false, "", e.message ?: "Gagal membatalkan pengajuan"
                )
            } finally {
                _applying.value = false
            }
        }
    }
}
