package com.example.sixtern.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.sixtern.R
import com.example.sixtern.databinding.ActivityMentorRegisterBinding

class MentorRegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMentorRegisterBinding
    private val viewModel: MentorRegisterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMentorRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.registerButton.setOnClickListener {
            viewModel.signUpMentor(
                email = binding.emailInput.text.toString(),
                password = binding.passwordInput.text.toString(),
                name = binding.nameInput.text.toString()
            )
        }

        viewModel.state.observe(this) { state ->
            when (state) {
                is AuthUiState.Idle -> showLoading(false)
                is AuthUiState.Loading -> showLoading(true)
                is AuthUiState.Success -> {
                    showLoading(false)
                    // Pendaftar baru menunggu verifikasi prodi — arahkan ke
                    // login; mentor tetap bisa masuk dalam mode terbatas
                    // (Beranda, Notifikasi, Akun) sambil menunggu disetujui.
                    Toast.makeText(this, getString(R.string.register_mentor_pending_toast), Toast.LENGTH_LONG).show()
                    startActivity(Intent(this, LoginActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                    viewModel.resetState()
                }
                is AuthUiState.Error -> {
                    showLoading(false)
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                    viewModel.resetState()
                }
            }
        }
    }

    private fun showLoading(loading: Boolean) {
        binding.loadingBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.registerButton.isEnabled = !loading
    }
}
