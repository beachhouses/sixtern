package com.example.sixtern.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.LinearGradient
import android.graphics.Shader
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.sixtern.R
import com.example.sixtern.databinding.ActivitySplashBinding
import com.example.sixtern.ui.auth.LoginActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyTitleGradient(binding)
        playEntrance(binding)

        binding.root.postDelayed({
            binding.splashRoot.startAnimation(
                AnimationUtils.loadAnimation(this, R.anim.splash_out)
            )
            startActivity(Intent(this, LoginActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, R.anim.splash_out)
            finish()
        }, 2000)
    }

    /** Judul SIXTERN gradien ungu -> biru seperti mockup splashscreen.png. */
    private fun applyTitleGradient(binding: ActivitySplashBinding) {
        val title = binding.titleText
        // Shader butuh lebar terukur -> pasang setelah layout selesai.
        title.post {
            if (title.width <= 0) return@post
            val start = ContextCompat.getColor(this, R.color.sixtern_purple)
            val end = ContextCompat.getColor(this, R.color.splash_bg_glow)
            title.paint.shader = LinearGradient(
                0f, 0f, title.width.toFloat(), 0f,
                start, end, Shader.TileMode.CLAMP
            )
            title.invalidate()
        }
    }

    /** Urutan animasi: latar konstelasi -> kartu logo -> judul + subtitle. */
    private fun playEntrance(binding: ActivitySplashBinding) {
        val bgAnim = AnimationUtils.loadAnimation(this, R.anim.splash_bg_in)
        val logoAnim = AnimationUtils.loadAnimation(this, R.anim.splash_logo_in)
        val textAnim = AnimationUtils.loadAnimation(this, R.anim.splash_text_in)

        binding.constellationView.startAnimation(bgAnim)

        logoAnim.startOffset = 150
        binding.logoCard.startAnimation(logoAnim)

        textAnim.startOffset = 550
        binding.titleText.startAnimation(textAnim)

        val subtitleAnim = AnimationUtils.loadAnimation(this, R.anim.splash_text_in)
        subtitleAnim.startOffset = 700
        binding.subtitleText.startAnimation(subtitleAnim)
    }
}

