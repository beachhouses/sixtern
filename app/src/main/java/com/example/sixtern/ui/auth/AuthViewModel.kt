package com.example.sixtern.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sixtern.data.model.Company
import com.example.sixtern.data.model.SixternUser
import com.example.sixtern.data.repository.AuthRepository
import kotlinx.coroutines.launch

sealed class AuthUiState {
    data object Idle : AuthUiState()
    data object Loading : AuthUiState()
    data class Success(val user: SixternUser) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel(
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _state = MutableLiveData<AuthUiState>(AuthUiState.Idle)
    val state: LiveData<AuthUiState> = _state

    private val _companies = MutableLiveData<List<Company>>(emptyList())
    val companies: LiveData<List<Company>> = _companies

    private val _role = MutableLiveData(SixternUser.ROLE_MAHASISWA)
    val role: LiveData<String> = _role

    fun setRole(role: String) {
        _role.value = role
    }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _state.value = AuthUiState.Error("Email dan kata sandi wajib diisi")
            return
        }
        _state.value = AuthUiState.Loading
        viewModelScope.launch {
            try {
                val user = repository.signIn(email.trim(), password)
                _state.value = AuthUiState.Success(user)
            } catch (e: Exception) {
                _state.value = AuthUiState.Error(friendlyAuthMessage(e))
            }
        }
    }

    fun loadCompanies() {
        viewModelScope.launch {
            try {
                _companies.value = repository.getCompanies()
            } catch (e: Exception) {
                _companies.value = emptyList()
            }
        }
    }

    fun signUpStudent(
        email: String,
        password: String,
        name: String,
        nim: String,
        companyId: String
    ) {
        if (name.isBlank() || nim.isBlank()) {
            _state.value = AuthUiState.Error("Nama dan NIM wajib diisi")
            return
        }
        if (email.isBlank() || password.length < 6) {
            _state.value = AuthUiState.Error("Email wajib diisi dan kata sandi minimal 6 karakter")
            return
        }
        // companyId boleh kosong: tempat magang dipilih nanti di dashboard.
        _state.value = AuthUiState.Loading
        viewModelScope.launch {
            try {
                val user = repository.signUpStudent(
                    email.trim(), password, name.trim(), nim.trim(), companyId
                )
                // Pendaftar baru pending — langsung masuk dalam mode terbatas.
                _state.value = AuthUiState.Success(user)
            } catch (e: Exception) {
                _state.value = AuthUiState.Error(friendlyAuthMessage(e))
            }
        }
    }

    fun signUpMentorBasic(
        email: String,
        password: String,
        name: String
    ) {
        if (name.isBlank()) {
            _state.value = AuthUiState.Error("Nama wajib diisi")
            return
        }
        if (email.isBlank() || password.length < 6) {
            _state.value = AuthUiState.Error("Email wajib diisi dan kata sandi minimal 6 karakter")
            return
        }
        _state.value = AuthUiState.Loading
        viewModelScope.launch {
            try {
                val user = repository.signUpMentorBasic(email.trim(), password, name.trim())
                // Pendaftar baru pending — langsung masuk dalam mode terbatas.
                _state.value = AuthUiState.Success(user)
            } catch (e: Exception) {
                _state.value = AuthUiState.Error(friendlyAuthMessage(e))
            }
        }
    }

    fun resetState() {
        _state.value = AuthUiState.Idle
    }

    /**
     * Ubah pesan teknis Firebase Auth menjadi bahasa yang dimengerti user.
     * Penyebab paling umum login gagal: akun di AGENTS.md belum didaftarkan
     * di Firebase project ini (email/password tidak cocok ATAU dokumen
     * users/{uid} belum ada di Firestore).
     */
    private fun friendlyAuthMessage(e: Exception): String {
        val raw = e.message.orEmpty()
        return when {
            "ACCOUNT_PENDING" in raw ->
                "Pendaftaranmu masih menunggu persetujuan prodi. " +
                    "Coba masuk lagi setelah akunmu disetujui."
            "ACCOUNT_REJECTED" in raw ->
                "Pendaftaran akunmu ditolak oleh prodi. " +
                    "Hubungi prodi untuk informasi lebih lanjut."
            "Profil pengguna tidak ditemukan" in raw ->
                "Akun ini belum terdaftar di aplikasi. " +
                    "Daftar dulu lewat tombol Daftar Mahasiswa / Daftar Mentor."
            "password is invalid" in raw
                || "INVALID_LOGIN_CREDENTIALS" in raw
                || "wrong-password" in raw
                || "user-not-found" in raw
                || "There is no user record" in raw ->
                "Email atau kata sandi salah, atau akun belum terdaftar. " +
                    "Periksa lagi, atau daftar dulu lewat tombol di bawah."
            "badly formatted" in raw || "invalid-email" in raw ->
                "Format email tidak valid. Periksa lagi penulisannya."
            "network" in raw || "Network" in raw || "TIMEOUT" in raw ->
                "Jaringan bermasalah. Periksa koneksi internet lalu coba lagi."
            "too many requests" in raw || "TOO_MANY_ATTEMPTS" in raw ->
                "Terlalu banyak percobaan. Tunggu sebentar lalu coba lagi."
            raw.isNotBlank() -> "Login gagal: $raw"
            else -> "Login gagal, coba lagi"
        }
    }
}
