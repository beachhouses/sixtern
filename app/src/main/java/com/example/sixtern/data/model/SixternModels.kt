package com.example.sixtern.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint

data class Company(
    val id: String = "",
    val mentorId: String = "",
    val name: String = "",
    val bannerUrl: String = "",
    val logoUrl: String = "",
    val internshipSlots: Int = 0,
    val benefits: String = "",
    val workDays: List<String> = emptyList(),
    val startTime: String = "08:00",
    val endTime: String = "17:00",
    val location: GeoPoint? = null,
    val radiusMeters: Double = 100.0
) {
    companion object {
        const val COLLECTION = "companies"
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
