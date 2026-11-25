package com.example.sixtern.ui.auth

import android.app.Activity
import android.content.Intent
import com.example.sixtern.R
import com.example.sixtern.ui.profile.MentorProfileActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Bottom nav mentor yang simpel: Beranda + Akun.
 * Berbeda dengan StudentNav (4 tab + guard companyId), mentor bebas
 * berpindah kapan saja — profil perusahaan di tab Akun boleh diisi belakangan.
 */
object MentorNav {

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
                R.id.nav_home -> Intent(activity, MentorHomeActivity::class.java)
                R.id.nav_account -> Intent(activity, MentorProfileActivity::class.java)
                else -> return@setOnItemSelectedListener false
            }
            intent.putExtra(LoginActivity.EXTRA_USER_ID, uid)
            intent.putExtra(LoginActivity.EXTRA_USER_NAME, userName)
            activity.startActivity(intent)
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(0, 0)
            if (activity !is MentorHomeActivity) activity.finish()
            true
        }
    }
}
