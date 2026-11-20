package com.example.sixtern.ui.splash

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.sixtern.BuildConfig
import com.example.sixtern.R
import com.example.sixtern.databinding.ActivitySplashBinding

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val batch = getString(R.string.batch_label)
        binding.versionBadge.text = "v${BuildConfig.VERSION_NAME} • $batch"
    }
}
