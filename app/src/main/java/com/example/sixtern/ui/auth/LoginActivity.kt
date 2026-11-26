package com.example.sixtern.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.sixtern.R
import com.example.sixtern.data.model.SixternUser
import com.example.sixtern.databinding.ActivityLoginBinding
import com.example.sixtern.ui.home.StudentHomeActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Satu pintu: role TIDAK dipilih di login, ikut data Firestore user saat signIn.

        binding.loginButton.setOnClickListener {
            viewModel.signIn(
                binding.emailInput.text.toString(),
                binding.passwordInput.text.toString()
            )
        }

        binding.registerLink.setOnClickListener {
            startActivity(Intent(this, StudentRegisterActivity::class.java))
        }

        binding.mentorRegisterLink.setOnClickListener {
            startActivity(Intent(this, MentorRegisterActivity::class.java))
        }

        viewModel.state.observe(this) { state ->
            when (state) {
                is AuthUiState.Idle -> showLoading(false)
                is AuthUiState.Loading -> showLoading(true)
                is AuthUiState.Success -> {
                    showLoading(false)
                    openHome(state.user)
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
        binding.loginButton.isEnabled = !loading
    }

    private fun openHome(user: SixternUser) {
        Toast.makeText(this, getString(R.string.login_welcome, user.name), Toast.LENGTH_SHORT).show()
        val intent = when (user.role) {
            SixternUser.ROLE_MENTOR -> Intent(this, MentorHomeActivity::class.java)
            SixternUser.ROLE_PRODI ->
                Intent(this, com.example.sixtern.ui.prodi.ProdiHomeActivity::class.java)
            else -> Intent(this, StudentHomeActivity::class.java)
        }
        intent.putExtra(EXTRA_USER_NAME, user.name)
        intent.putExtra(EXTRA_USER_ID, user.uid)
        startActivity(intent)
        finish()
    }

    companion object {
        const val EXTRA_USER_NAME = "extra_user_name"
        const val EXTRA_USER_ID = "extra_user_id"
    }
}
