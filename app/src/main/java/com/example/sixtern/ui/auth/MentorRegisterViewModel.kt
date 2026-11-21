package com.example.sixtern.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sixtern.data.model.Company
import com.example.sixtern.data.model.SixternUser
import com.example.sixtern.data.repository.AuthRepository
import kotlinx.coroutines.launch

class MentorRegisterViewModel(
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _state = MutableLiveData<AuthUiState>(AuthUiState.Idle)
    val state: LiveData<AuthUiState> = _state

    fun signUpMentor(
        email: String,
        password: String,
        name: String,
        companyName: String,
        slotsText: String,
        benefits: String,
        workDays: String,
        startTime: String,
        endTime: String,
        latitudeText: String,
        longitudeText: String,
        radiusText: String
    ) {
        if (name.isBlank()) {
            _state.value = AuthUiState.Error("Nama lengkap wajib diisi")
            return
        }
        if (email.isBlank() || password.length < 6) {
            _state.value = AuthUiState.Error("Email wajib diisi dan kata sandi minimal 6 karakter")
            return
        }
        if (companyName.isBlank()) {
            _state.value = AuthUiState.Error("Nama perusahaan wajib diisi")
            return
        }
        val slots = slotsText.toIntOrNull()
        if (slots == null || slots <= 0) {
            _state.value = AuthUiState.Error("Slot magang harus angka lebih dari 0")
            return
        }
        val latitude = latitudeText.toDoubleOrNull()
        val longitude = longitudeText.toDoubleOrNull()
        if (latitude == null || longitude == null) {
            _state.value = AuthUiState.Error("Koordinat lokasi tidak valid")
            return
        }
        val radius = radiusText.toDoubleOrNull() ?: 100.0
        val days = workDays.split(",", " ", ";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (days.isEmpty()) {
            _state.value = AuthUiState.Error("Isi minimal satu hari kerja")
            return
        }
        _state.value = AuthUiState.Loading
        viewModelScope.launch {
            try {
                val company = Company(
                    name = companyName.trim(),
                    internshipSlots = slots,
                    benefits = benefits.trim(),
                    workDays = days,
                    startTime = startTime.ifBlank { "08:00" },
                    endTime = endTime.ifBlank { "17:00" },
                    location = com.google.firebase.firestore.GeoPoint(latitude, longitude),
                    radiusMeters = radius
                )
                val (user, _) = repository.signUpMentor(
                    email.trim(), password, name.trim(), company
                )
                _state.value = AuthUiState.Success(user)
            } catch (e: Exception) {
                _state.value = AuthUiState.Error(e.message ?: "Pendaftaran gagal, coba lagi")
            }
        }
    }

    fun resetState() {
        _state.value = AuthUiState.Idle
    }
}
