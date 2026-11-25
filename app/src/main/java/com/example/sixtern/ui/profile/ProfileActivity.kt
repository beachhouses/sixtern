package com.example.sixtern.ui.profile

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sixtern.data.model.SixternUser
import com.example.sixtern.data.repository.AuthRepository
import com.example.sixtern.R
import com.example.sixtern.databinding.ActivityProfileBinding
import com.example.sixtern.ui.auth.LoginActivity
import com.example.sixtern.ui.home.StudentNav
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {
    private val _user = MutableLiveData<SixternUser?>()
    val user: LiveData<SixternUser?> = _user
    fun load(uid: String) {
        viewModelScope.launch {
            _user.value = try { repository.getUser(uid) } catch (e: Exception) { null }
        }
    }
    fun logout() = repository.signOut()
}

class ProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProfileBinding
    private val viewModel: ProfileViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val uid = intent.getStringExtra(LoginActivity.EXTRA_USER_ID).orEmpty()
        viewModel.load(uid)
        binding.profileNav.itemIconTintList = null
        StudentNav.setup(binding.profileNav, this, R.id.nav_profile, uid)
        viewModel.user.observe(this) { user ->
            if (user != null) {
                binding.avatarText.text = user.name.firstOrNull()?.uppercase() ?: "S"
                binding.nameText.text = user.name
                binding.emailText.text = user.email
                binding.roleText.text = user.role
                binding.nimText.text = user.nim ?: "-"
            }
        }
        binding.logoutButton.setOnClickListener {
            viewModel.logout()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}
