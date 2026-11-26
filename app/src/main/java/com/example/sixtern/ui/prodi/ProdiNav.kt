package com.example.sixtern.ui.prodi

import android.app.Activity
import android.content.Intent
import com.example.sixtern.R
import com.example.sixtern.ui.auth.LoginActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Bottom nav prodi (pemilik aplikasi): Beranda / Verifikasi / Profil.
 * Prodi bebas berpindah tab kapan saja.
 */
object ProdiNav {

    fun setup(
        bar: BottomNavigationView,
        activity: Activity,
        selectedItemId: Int,
        uid: String,
        userName: String = ""
    ) {
        bar.selectedItemId = selectedItemId
        bar.setOnItemSelectedListener { item ->
            if (item.itemId == selectedItemId) return@setOnItemSelectedListener true
            val intent = when (item.itemId) {
                R.id.nav_home -> Intent(activity, ProdiHomeActivity::class.java)
                R.id.nav_verify -> Intent(activity, ProdiVerificationActivity::class.java)
                R.id.nav_profile -> Intent(activity, ProdiProfileActivity::class.java)
                else -> return@setOnItemSelectedListener false
            }
            intent.putExtra(LoginActivity.EXTRA_USER_ID, uid)
            intent.putExtra(LoginActivity.EXTRA_USER_NAME, userName)
            activity.startActivity(intent)
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(0, 0)
            if (activity !is ProdiHomeActivity) activity.finish()
            true
        }
    }
}
