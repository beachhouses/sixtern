package com.example.sixtern.ui.home

import android.app.Activity
import android.content.Intent
import com.example.sixtern.R
import com.example.sixtern.ui.auth.LoginActivity
import com.example.sixtern.ui.evaluation.EvaluationActivity
import com.example.sixtern.ui.logbook.LogbookActivity
import com.example.sixtern.ui.profile.ProfileActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Bottom nav mahasiswa yang konsisten di semua layar (Beranda, Logbook, Nilai, Profil).
 * Setiap layar memanggil [setup] dengan item yang sedang aktif + uid saat ini.
 */
object StudentNav {

    fun setup(bar: BottomNavigationView, activity: Activity, selectedItemId: Int, uid: String) {
        bar.selectedItemId = selectedItemId
        bar.setOnItemSelectedListener { item ->
            if (item.itemId == selectedItemId) return@setOnItemSelectedListener true
            val intent = when (item.itemId) {
                R.id.nav_home -> Intent(activity, StudentHomeActivity::class.java)
                R.id.nav_logbook -> Intent(activity, LogbookActivity::class.java)
                R.id.nav_evaluation -> Intent(activity, EvaluationActivity::class.java)
                R.id.nav_profile -> Intent(activity, ProfileActivity::class.java)
                else -> return@setOnItemSelectedListener false
            }
            intent.putExtra(LoginActivity.EXTRA_USER_ID, uid)
            activity.startActivity(intent)
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(0, 0)
            if (activity !is StudentHomeActivity) activity.finish()
            true
        }
    }
}
