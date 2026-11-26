package com.example.sixtern.ui.home

import android.app.Activity
import android.content.Intent
import com.example.sixtern.R
import com.example.sixtern.ui.auth.LoginActivity
import com.example.sixtern.ui.evaluation.EvaluationActivity
import com.example.sixtern.ui.logbook.LogbookActivity
import com.example.sixtern.ui.notification.StudentNotificationActivity
import com.example.sixtern.ui.profile.ProfileActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Bottom nav mahasiswa yang konsisten di semua layar (Beranda, Logbook, Nilai, Profil).
 * Setiap layar memanggil [setup] dengan item yang sedang aktif + uid saat ini.
 */
object StudentNav {

    /**
     * Tampilkan badge notifikasi di tab Notifikasi.
     * count > 0 -> badge merah muncul; count == 0 -> badge dihapus.
     */
    fun setBadge(bar: BottomNavigationView, count: Int) {
        val itemId = bar.menu.findItem(R.id.nav_notification)?.itemId ?: return
        if (count > 0) {
            val badge = bar.getOrCreateBadge(itemId)
            badge.isVisible = true
            badge.number = count
            badge.backgroundColor = 0xFFDC2626.toInt() // merah
        } else {
            bar.removeBadge(itemId)
        }
    }

    /** companyId kosong -> hanya Beranda & Profil yang terbuka, sisanya kembali ke beranda. */
    fun setup(
        bar: BottomNavigationView,
        activity: Activity,
        selectedItemId: Int,
        uid: String,
        companyId: String = "",
        notifStatus: String = "",
        notifCompanyName: String = "",
        notifReason: String = "",
        accountPending: Boolean = false
    ) {
        bar.selectedItemId = selectedItemId
        bar.setOnItemSelectedListener { item ->
            if (item.itemId == selectedItemId) return@setOnItemSelectedListener true
            // Akun belum diverifikasi prodi: hanya Beranda & Profil yang
            // terbuka; Logbook/Nilai/Notifikasi menunggu persetujuan prodi.
            if (accountPending &&
                item.itemId != R.id.nav_home &&
                item.itemId != R.id.nav_profile
            ) {
                android.widget.Toast.makeText(
                    activity,
                    activity.getString(R.string.student_nav_pending_blocked),
                    android.widget.Toast.LENGTH_LONG
                ).show()
                return@setOnItemSelectedListener false
            }
            // Logbook boleh dibuka kapan saja: bila belum punya tempat
            // magang, layarnya sendiri menampilkan kartu "belum ada tempat
            // magang". Hanya Nilai yang tetap terkunci.
            val needsCompany = item.itemId == R.id.nav_evaluation
            // Notifikasi & Profil selalu bisa dibuka tanpa companyId.
            if (needsCompany && companyId.isBlank()) {
                android.widget.Toast.makeText(
                    activity,
                    activity.getString(R.string.need_company_first),
                    android.widget.Toast.LENGTH_LONG
                ).show()
                if (activity !is StudentHomeActivity) {
                    val home = Intent(activity, StudentHomeActivity::class.java)
                    home.putExtra(LoginActivity.EXTRA_USER_ID, uid)
                    activity.startActivity(home)
                    @Suppress("DEPRECATION")
                    activity.overridePendingTransition(0, 0)
                    activity.finish()
                }
                return@setOnItemSelectedListener false
            }
            val intent = when (item.itemId) {
                R.id.nav_home -> Intent(activity, StudentHomeActivity::class.java)
                R.id.nav_logbook -> Intent(activity, LogbookActivity::class.java)
                R.id.nav_evaluation -> Intent(activity, EvaluationActivity::class.java)
                R.id.nav_notification -> Intent(activity, StudentNotificationActivity::class.java).also { i ->
                    i.putExtra(StudentNotificationActivity.EXTRA_STATUS, notifStatus)
                    i.putExtra(StudentNotificationActivity.EXTRA_COMPANY_NAME, notifCompanyName)
                    i.putExtra(StudentNotificationActivity.EXTRA_REASON, notifReason)
                    i.putExtra(StudentNotificationActivity.EXTRA_COMPANY_ID, companyId)
                }
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
