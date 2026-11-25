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
    val radiusMeters: Double = 100.0
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
    val attachmentUrl: String? = null
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
    val isValid: Boolean = false
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
