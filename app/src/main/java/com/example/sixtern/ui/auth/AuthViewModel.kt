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
                _state.value = AuthUiState.Error(e.message ?: "Login gagal, coba lagi")
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
        if (companyId.isBlank()) {
            _state.value = AuthUiState.Error("Pilih tempat magang terlebih dahulu")
            return
        }
        _state.value = AuthUiState.Loading
        viewModelScope.launch {
            try {
                val user = repository.signUpStudent(
                    email.trim(), password, name.trim(), nim.trim(), companyId
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
