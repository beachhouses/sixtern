package com.example.sixtern.util

import android.content.Context

/**
 * Penanda "notifikasi sudah dilihat" per user (mahasiswa & mentor).
 * Menyimpan waktu (epoch millis) terakhir user membuka halaman notifikasi.
 * Badge dihitung dengan membandingkan statusUpdatedAt dokumen user dengan
 * nilai ini: lebih baru = ada notifikasi yang belum dilihat.
 */
object NotificationPrefs {

    private const val PREFS_NAME = "sixtern_notification_seen"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Waktu terakhir user membuka halaman notifikasi (0 = belum pernah). */
    fun getSeenAt(context: Context, uid: String): Long =
        if (uid.isBlank()) 0L else prefs(context).getLong("seen_$uid", 0L)

    /** Catat bahwa user baru saja melihat semua notifikasi (waktu sekarang). */
    fun markSeen(context: Context, uid: String) {
        if (uid.isBlank()) return
        prefs(context).edit().putLong("seen_$uid", System.currentTimeMillis()).apply()
    }
}
