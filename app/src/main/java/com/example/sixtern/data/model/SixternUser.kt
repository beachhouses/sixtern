package com.example.sixtern.data.model

data class SixternUser(
    val uid: String = "",
    val role: String = ROLE_MAHASISWA,
    val name: String = "",
    val email: String = "",
    val nim: String? = null,
    val major: String = "Teknologi Informasi",
    val university: String = "Universitas Sumatera Utara",
    val photoUrl: String? = null,
    val companyId: String? = null,
    // Profil tambahan mahasiswa
    val bio: String = "",
    val age: String = "",
    val phone: String = "",
    val skills: List<String> = emptyList(),
    val portfolioUrl: String = "",
    val cvUrl: String = "",
    // Status pengajuan magang
    val internshipStatus: String = STATUS_PENDING,
    val rejectionReason: String = "",
    /** Waktu (epoch millis) status pengajuan terakhir berubah — untuk badge "belum dilihat". */
    val statusUpdatedAt: Long = 0,
    // Periode magang yang diajukan mahasiswa (+ surat permohonan wajib).
    /** Tanggal mulai magang, format yyyy-MM-dd (mis. "2026-07-01"). */
    val internshipStartDate: String = "",
    /** Tanggal selesai magang, format yyyy-MM-dd (mis. "2026-12-31"). */
    val internshipEndDate: String = "",
    /**
     * Surat permohonan magang (PDF, data URI Base64) — wajib saat mengajukan.
     * Pola sama seperti logo perusahaan: disimpan ringan agar tanpa Storage
     * berbayar (maks ~1.5MB file asli).
     */
    val applicationLetterUrl: String = "",
    /**
     * Status verifikasi akun oleh prodi. Default "verified" agar akun lama
     * (tanpa field ini di Firestore) tetap bisa masuk. Pendaftaran baru
     * selalu dimulai dari "pending" sampai disetujui prodi.
     */
    val accountStatus: String = ACCOUNT_VERIFIED
) {
    companion object {
        const val ROLE_MAHASISWA = "mahasiswa"
        const val ROLE_MENTOR = "mentor"
        const val ROLE_PRODI = "prodi"
        const val COLLECTION = "users"

        const val STATUS_PENDING = "pending"
        const val STATUS_ACCEPTED = "accepted"
        const val STATUS_REJECTED = "rejected"

        /** Format tanggal periode magang: yyyy-MM-dd (mis. "2026-07-01"). */
        const val DATE_PATTERN = "yyyy-MM-dd"

        /** Batas ukuran surat permohonan (file asli): 1.5MB agar hemat Firestore. */
        const val MAX_LETTER_BYTES = 1_500_000

        /**
         * Validasi periode magang: kedua tanggal wajib ada, format yyyy-MM-dd,
         * tanggal mulai harus sebelum tanggal selesai.
         * @return null bila valid, pesan error (Indonesia) bila tidak.
         */
        fun validateInternshipPeriod(start: String, end: String): String? {
            if (start.isBlank() || end.isBlank()) return "Tanggal masuk dan tanggal selesai magang wajib diisi"
            val fmt = java.text.SimpleDateFormat(DATE_PATTERN, java.util.Locale.US).apply {
                isLenient = false
            }
            val startDate = try { fmt.parse(start) } catch (e: Exception) { null }
            val endDate = try { fmt.parse(end) } catch (e: Exception) { null }
            if (startDate == null || endDate == null) return "Format tanggal magang tidak valid"
            if (!startDate.before(endDate)) return "Tanggal masuk magang harus sebelum tanggal selesai"
            return null
        }

        /**
         * Format tampilan periode magang: "01 Jul 2026 - 31 Des 2026".
         * Salah satu kosong -> "-".
         */
        fun formatInternshipPeriod(start: String, end: String): String {
            if (start.isBlank() || end.isBlank()) return "-"
            return try {
                val parser = java.text.SimpleDateFormat(DATE_PATTERN, java.util.Locale.US)
                val display = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale("id"))
                val s = parser.parse(start) ?: return "$start - $end"
                val e = parser.parse(end) ?: return "$start - $end"
                display.format(s) + " - " + display.format(e)
            } catch (e: Exception) {
                "$start - $end"
            }
        }

        /**
         * Format satu tanggal "yyyy-MM-dd" -> "01 Jul 2026".
         * Kosong / tidak valid -> "-".
         */
        fun formatInternshipDate(iso: String): String {
            if (iso.isBlank()) return "-"
            return try {
                val parser = java.text.SimpleDateFormat(DATE_PATTERN, java.util.Locale.US)
                val display = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale("id"))
                val date = parser.parse(iso) ?: return iso
                display.format(date)
            } catch (e: Exception) {
                iso
            }
        }

        /**
         * Progres magang dari periode [startIso] - [endIso] terhadap [todayMillis].
         * @return Triple(hariBerjalan, totalHari, persen 0-100).
         * - Tanggal kosong/tidak valid -> Triple(0, 0, 0).
         * - Hari berjalan dihitung inklusif mulai tanggal masuk, dibatasi 0..total.
         */
        fun internshipProgress(
            startIso: String,
            endIso: String,
            todayMillis: Long = System.currentTimeMillis()
        ): Triple<Int, Int, Int> {
            return try {
                if (startIso.isBlank() || endIso.isBlank()) return Triple(0, 0, 0)
                val parser = java.text.SimpleDateFormat(DATE_PATTERN, java.util.Locale.US).apply {
                    isLenient = false
                }
                val start = parser.parse(startIso)?.time ?: return Triple(0, 0, 0)
                val end = parser.parse(endIso)?.time ?: return Triple(0, 0, 0)
                if (end < start) return Triple(0, 0, 0)
                val dayMs = 24L * 60 * 60 * 1000
                // Samakan ke tengah malam agar perbandingan hari stabil.
                fun midnight(millis: Long): Long {
                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
                    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    cal.set(java.util.Calendar.MINUTE, 0)
                    cal.set(java.util.Calendar.SECOND, 0)
                    cal.set(java.util.Calendar.MILLISECOND, 0)
                    return cal.timeInMillis
                }
                val total = ((midnight(end) - midnight(start)) / dayMs).toInt() + 1
                if (total <= 0) return Triple(0, 0, 0)
                val elapsed = (((midnight(todayMillis) - midnight(start)) / dayMs).toInt() + 1)
                    .coerceIn(0, total)
                val percent = ((elapsed * 100) / total).coerceIn(0, 100)
                Triple(elapsed, total, percent)
            } catch (e: Exception) {
                Triple(0, 0, 0)
            }
        }

        const val ACCOUNT_PENDING = "pending"
        const val ACCOUNT_VERIFIED = "verified"
        const val ACCOUNT_REJECTED = "rejected"
    }
}
