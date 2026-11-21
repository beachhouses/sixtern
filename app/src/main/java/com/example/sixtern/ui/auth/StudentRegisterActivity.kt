package com.example.sixtern.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.sixtern.data.model.Company
import com.example.sixtern.databinding.ActivityStudentRegisterBinding
import com.example.sixtern.ui.home.StudentHomeActivity

class StudentRegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentRegisterBinding
    private val viewModel: AuthViewModel by viewModels()
    private var companies: List<Company> = emptyList()
    private var selectedCompanyId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        viewModel.loadCompanies()

        viewModel.companies.observe(this) { list ->
            companies = list
            val names = list.map { it.name.ifBlank { it.id } }
            val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
            binding.companyInput.setAdapter(adapter)
        }

        binding.companyInput.setOnItemClickListener { _, _, position, _ ->
            selectedCompanyId = companies.getOrNull(position)?.id.orEmpty()
        }

        binding.registerButton.setOnClickListener {
            viewModel.signUpStudent(
                email = binding.emailInput.text.toString(),
                password = binding.passwordInput.text.toString(),
                name = binding.nameInput.text.toString(),
                nim = binding.nimInput.text.toString(),
                companyId = selectedCompanyId
            )
        }

        viewModel.state.observe(this) { state ->
            when (state) {
                is AuthUiState.Idle -> showLoading(false)
                is AuthUiState.Loading -> showLoading(true)
                is AuthUiState.Success -> {
                    showLoading(false)
                    val intent = Intent(this, StudentHomeActivity::class.java)
                    intent.putExtra(LoginActivity.EXTRA_USER_NAME, state.user.name)
                    intent.putExtra(LoginActivity.EXTRA_USER_ID, state.user.uid)
                    startActivity(intent)
                    finish()
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
