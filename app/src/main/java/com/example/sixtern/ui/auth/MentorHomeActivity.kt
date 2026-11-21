package com.example.sixtern.ui.auth

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.sixtern.databinding.ActivityMentorHomeBinding
import com.example.sixtern.ui.auth.LoginActivity

class MentorHomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMentorHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val name = intent.getStringExtra(LoginActivity.EXTRA_USER_NAME).orEmpty()
        binding.greetingText.text = if (name.isBlank()) "Halo, Mentor!" else "Halo, $name!"
    }
}
