package com.example.sixtern.ui.auth

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import com.example.sixtern.R
import com.example.sixtern.ui.notification.MentorNotificationActivity
import com.example.sixtern.ui.participants.MentorParticipantsActivity
import com.example.sixtern.ui.profile.MentorProfileActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Bottom nav mentor: Beranda + Peserta + Notifikasi + Akun.
 * Mentor yang belum diverifikasi prodi tetap boleh membuka Beranda
 * (kartu status + update profil perusahaan), Notifikasi (menerima status
 * verifikasi) dan Akun — hanya tab Peserta yang menunggu persetujuan prodi.
 * Tab Notifikasi menampung semua notifikasi mentor (pengajuan magang, dsb.)
 * dengan badge jumlah di ikonnya.
 */
object MentorNav {

    fun setup(
        bar: BottomNavigationView,
        activity: Activity,
        selectedItemId: Int,
        uid: String,
        userName: String = "",
        actionsBlocked: Boolean = false
    ) {
        bar.selectedItemId = selectedItemId
        bar.setOnItemSelectedListener { item ->
            if (item.itemId == selectedItemId) return@setOnItemSelectedListener true
            // Menunggu verifikasi prodi (akun / perusahaan): Beranda,
            // Notifikasi & Akun tetap terbuka (profil perusahaan boleh
            // diedit/diperbarui, status verifikasi diterima di Notifikasi);
            // hanya Peserta yang menunggu persetujuan prodi.
            if (actionsBlocked && item.itemId == R.id.nav_participants) {
                Toast.makeText(
                    activity,
                    R.string.mentor_nav_pending_blocked,
                    Toast.LENGTH_LONG
                ).show()
                return@setOnItemSelectedListener false
            }
            val intent = when (item.itemId) {
                R.id.nav_home -> Intent(activity, MentorHomeActivity::class.java)
                R.id.nav_participants -> Intent(activity, MentorParticipantsActivity::class.java)
                R.id.nav_notification -> Intent(activity, MentorNotificationActivity::class.java)
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

    /**
     * Tampilkan badge jumlah notifikasi (pengajuan magang) di ikon Notifikasi.
     * Panggil dari activity mana pun yang memakai nav ini setelah data dimuat.
     */
    fun setBadge(bar: BottomNavigationView, count: Int) {
        val itemId = bar.menu.findItem(R.id.nav_notification)?.itemId ?: return
        if (count > 0) {
            val badge = bar.getOrCreateBadge(itemId)
            badge.isVisible = true
            badge.number = count
            badge.backgroundColor = 0xFF2D6A4C.toInt()
        } else {
            bar.removeBadge(itemId)
        }
    }
}
