package com.example.sixtern.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint

data class WorkDaySchedule(
    val start: String = "",
    val end: String = ""
) {
    val isActive: Boolean get() = start.isNotBlank() || end.isNotBlank()
}

/** Urutan hari untuk form jadwal + tampilan ringkas. */
val DAY_ORDER = listOf(
    "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu"
)

/** Nama hari Indonesia hari ini, mis. "Senin". */
fun todayDayName(): String {
    val cal = java.util.Calendar.getInstance()
    // Calendar: Minggu=1 .. Sabtu=7 -> DAY_ORDER: Senin=0 .. Minggu=6.
    val names = listOf("Minggu", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu")
    return names[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]
}

/** Jam pulang kerja hari ini "HH:MM" dari workSchedule, fallback endTime legacy. */
fun Company.endTimeForToday(): String {
    val today = todayDayName()
    val end = workSchedule[today]?.end?.takeIf { it.isNotBlank() }
    return end ?: endTime.ifBlank { "17:00" }
}

/** "HH:MM" -> menit sejak tengah malam; null bila format tidak valid. */
fun timeToMinutes(raw: String): Int? {
    val parts = raw.trim().split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

/** Status badge logbook harian untuk beranda: belum isi / sudah isi / terlewat. */
enum class DailyLogbookStatus { OPEN, DONE, MISSED }

/**
 * Tentukan status badge dari jam sekarang vs batas jam pulang + apakah
 * logbook hari ini sudah ada. Menit pembanding bisa di-inject untuk unit test.
 */
fun dailyLogbookStatus(
    hasLogbookToday: Boolean,
    endTimeToday: String,
    nowMinutes: Int? = null
): DailyLogbookStatus {
    if (hasLogbookToday) return DailyLogbookStatus.DONE
    val now = nowMinutes ?: run {
        val cal = java.util.Calendar.getInstance()
        cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
    }
    val limit = timeToMinutes(endTimeToday) ?: return DailyLogbookStatus.OPEN
    return if (now > limit) DailyLogbookStatus.MISSED else DailyLogbookStatus.OPEN
}

/** Hari aktif (ada di workDays) atau dari workSchedule bila workDays kosong. */
fun Company.activeDays(): List<String> {
    if (workDays.any { it.isNotBlank() }) return workDays.filter { it.isNotBlank() }
    return DAY_ORDER.filter { workSchedule[it]?.isActive == true }
}

/** Ringkasan jadwal untuk kartu mahasiswa, mis. "Senin - Jumat • 08:00–17:00". */
fun Company.scheduleSummary(): String {
    val active = workSchedule.filterValues { it.isActive }
    if (active.isEmpty()) {
        val days = activeDays().joinToString(", ").ifBlank { "-" }
        return "$days • ${startTime.ifBlank { "-" }}–${endTime.ifBlank { "-" }}"
    }
    val orderedDays = DAY_ORDER.filter { active.containsKey(it) }
    val dayLabel = orderedDays.collapseDayRange()
    // Jam umum = jam yang paling sering dipakai; fallback jam hari aktif pertama.
    val times = active.values.map { it.start.ifBlank { "-" } + "–" + it.end.ifBlank { "-" } }
    val commonTime = times.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        ?: times.firstOrNull().orEmpty()
    return "$dayLabel • $commonTime"
}

/** "Senin, Selasa, Rabu" -> "Senin - Rabu"; non-berurutan tetap koma. */
private fun List<String>.collapseDayRange(): String {
    if (isEmpty()) return "-"
    if (size == 1) return first()
    val idx = map { DAY_ORDER.indexOf(it) }.sorted()
    val contiguous = idx.zipWithNext().all { (a, b) -> b == a + 1 }
    return if (contiguous) "${DAY_ORDER[idx.first()]} - ${DAY_ORDER[idx.last()]}"
    else joinToString(", ")
}

data class Company(
    val id: String = "",
    val mentorId: String = "",
    val name: String = "",
    val description: String = "",
    val bannerUrl: String = "",
    val logoUrl: String = "",
    val internshipSlots: Int = 0,
    val benefits: List<String> = emptyList(),
    val workDays: List<String> = emptyList(),
    val startTime: String = "08:00",
    val endTime: String = "17:00",
    val workSchedule: Map<String, WorkDaySchedule> = emptyMap(),
    val location: GeoPoint? = null,
    val radiusMeters: Double = 100.0,
    /**
     * Verifikasi perusahaan oleh prodi. Default true agar perusahaan lama
     * (tanpa field ini di Firestore) tetap dianggap terverifikasi.
     */
    val verified: Boolean = true
) {
    companion object {
        const val COLLECTION = "companies"

        /** Baca benefits lama (String campur koma/baris) maupun baru (List). */
        fun parseBenefits(raw: Any?): List<String> = when (raw) {
            is List<*> -> raw.filterIsInstance<String>().map { it.trim() }.filter { it.isNotEmpty() }
            is String -> raw.split(",", ";", "\n").map { it.trim() }.filter { it.isNotEmpty() }
            else -> emptyList()
        }

        /** Baca workSchedule mentah dari Firestore (Map of Map) ke typed map. */
        fun parseSchedule(raw: Any?): Map<String, WorkDaySchedule> {
            val map = raw as? Map<*, *> ?: return emptyMap()
            return map.mapNotNull { (k, v) ->
                val day = k as? String ?: return@mapNotNull null
                if (day !in DAY_ORDER) return@mapNotNull null
                val inner = v as? Map<*, *> ?: return@mapNotNull null
                val start = (inner["start"] as? String).orEmpty().trim()
                val end = (inner["end"] as? String).orEmpty().trim()
                if (start.isBlank() && end.isBlank()) return@mapNotNull null
                day to WorkDaySchedule(start, end)
            }.toMap()
        }
    }
}

data class Logbook(
    val id: String = "",
    val studentId: String = "",
    val companyId: String = "",
    val title: String = "",
    val description: String = "",
    val createdAt: Timestamp? = null,
    val status: String = STATUS_PENDING,
    val mentorNotes: String? = null,
    val attachmentUrl: String? = null,
    /** Waktu mentor mereview (epoch millis); 0 = belum pernah / sudah direset saat revisi. */
    val reviewedAt: Long = 0
) {
    companion object {
        const val COLLECTION = "logbooks"
        const val STATUS_PENDING = "pending"
        const val STATUS_APPROVED = "approved"
        const val STATUS_REVISION = "revision"
    }
}

data class Attendance(
    val id: String = "",
    val studentId: String = "",
    val companyId: String = "",
    val type: String = TYPE_IN,
    val timestamp: Timestamp? = null,
    val photoUrl: String = "",
    val location: GeoPoint? = null,
    val isValid: Boolean = false,
    /** True jika absen masuk melewati jam masuk perusahaan. */
    val isLate: Boolean = false,
    /** Selisih menit dari jam masuk; 0 untuk absen pulang atau absen tepat waktu. */
    val lateMinutes: Int = 0
) {
    companion object {
        const val COLLECTION = "attendance"
        const val TYPE_IN = "in"
        const val TYPE_OUT = "out"
    }
}

data class Evaluation(
    val id: String = "",
    val studentId: String = "",
    val mentorScore: Double = 0.0,
    val supervisorScore: Double = 0.0,
    val attendanceLogbookScore: Double = 0.0,
    val totalScore: Double = 0.0,
    val transcriptUrl: String? = null,
    val status: String = STATUS_DRAFT
) {
    companion object {
        const val COLLECTION = "evaluations"
        const val STATUS_DRAFT = "draft"
        const val STATUS_SUBMITTED = "submitted"
        const val STATUS_FINALIZED = "finalized"
    }
}
