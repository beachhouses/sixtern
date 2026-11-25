package com.example.sixtern.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
        name: String
    ) {
        if (name.isBlank()) {
            _state.value = AuthUiState.Error("Nama lengkap wajib diisi")
            return
        }
        if (email.isBlank() || password.length < 6) {
            _state.value = AuthUiState.Error("Email wajib diisi dan kata sandi minimal 6 karakter")
            return
        }
        _state.value = AuthUiState.Loading
        viewModelScope.launch {
            try {
                val user = repository.signUpMentorBasic(
                    email.trim(), password, name.trim()
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
