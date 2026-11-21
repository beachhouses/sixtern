package com.example.sixtern.ui.home

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.sixtern.databinding.ActivityStudentHomeBinding
import com.example.sixtern.ui.auth.LoginActivity

class StudentHomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityStudentHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val name = intent.getStringExtra(LoginActivity.EXTRA_USER_NAME).orEmpty()
        binding.greetingText.text = if (name.isBlank()) "Halo!" else "Halo, $name!"
    }
}
