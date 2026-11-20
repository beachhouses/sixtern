package com.example.sixtern.data.repository

import com.example.sixtern.data.model.Attendance
import com.example.sixtern.data.model.Company
import com.example.sixtern.data.model.Evaluation
import com.example.sixtern.data.model.Logbook
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class InternshipRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun addLogbook(
        studentId: String,
        companyId: String,
        title: String,
        description: String
    ): Logbook {
        val ref = firestore.collection(Logbook.COLLECTION).document()
        val logbook = Logbook(
            id = ref.id,
            studentId = studentId,
            companyId = companyId,
            title = title,
            description = description,
            createdAt = Timestamp.now(),
            status = Logbook.STATUS_PENDING
        )
        ref.set(logbook).await()
        return logbook
    }

    suspend fun getLogbooks(studentId: String): List<Logbook> {
        val snapshot = firestore.collection(Logbook.COLLECTION)
            .whereEqualTo("studentId", studentId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()
        return snapshot.toObjects(Logbook::class.java)
    }

    suspend fun updateLogbookRevision(logbookId: String, title: String, description: String) {
        firestore.collection(Logbook.COLLECTION).document(logbookId)
            .update(
                mapOf(
                    "title" to title,
                    "description" to description,
                    "status" to Logbook.STATUS_PENDING
                )
            )
            .await()
    }

    suspend fun recordAttendance(
        studentId: String,
        companyId: String,
        type: String,
        photoUrl: String,
        latitude: Double,
        longitude: Double
    ): Attendance {
        val companySnapshot = firestore.collection(Company.COLLECTION)
            .document(companyId).get().await()
        val company = companySnapshot.toObject(Company::class.java)
        val valid = RadiusChecker.isInsideRadius(
            latitude,
            longitude,
            company?.location?.latitude,
            company?.location?.longitude,
            company?.radiusMeters ?: 100.0
        )
        val ref = firestore.collection(Attendance.COLLECTION).document()
        val attendance = Attendance(
            id = ref.id,
            studentId = studentId,
            companyId = companyId,
            type = type,
            timestamp = Timestamp.now(),
            photoUrl = photoUrl,
            location = com.google.firebase.firestore.GeoPoint(latitude, longitude),
            isValid = valid
        )
        ref.set(attendance).await()
        return attendance
    }

    suspend fun getEvaluation(studentId: String): Evaluation? {
        val snapshot = firestore.collection(Evaluation.COLLECTION)
            .whereEqualTo("studentId", studentId)
            .limit(1)
            .get()
            .await()
        return snapshot.toObjects(Evaluation::class.java).firstOrNull()
    }
}
