package com.example.sixtern.data.repository

import com.example.sixtern.data.model.Attendance
import com.example.sixtern.data.model.Company
import com.example.sixtern.data.model.Evaluation
import com.example.sixtern.data.model.Logbook
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class InternshipRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val authRepository: AuthRepository = AuthRepository()
) {
    suspend fun addLogbook(
        studentId: String,
        companyId: String,
        title: String,
        description: String,
        attachmentUrl: String? = null
    ): Logbook {
        val ref = firestore.collection(Logbook.COLLECTION).document()
        val logbook = Logbook(
            id = ref.id,
            studentId = studentId,
            companyId = companyId,
            title = title,
            description = description,
            createdAt = Timestamp.now(),
            status = Logbook.STATUS_PENDING,
            attachmentUrl = attachmentUrl
        )
        ref.set(logbook).await()
        return logbook
    }

    suspend fun getLogbooks(studentId: String): List<Logbook> {
        // Tanpa orderBy server agar tidak butuh composite index Firestore
        // (penyebab error FAILED_PRECONDITION di beranda); urutkan di memori.
        val snapshot = firestore.collection(Logbook.COLLECTION)
            .whereEqualTo("studentId", studentId)
            .get()
            .await()
        return snapshot.toObjects(Logbook::class.java)
            .sortedByDescending { it.createdAt?.seconds ?: Long.MIN_VALUE }
    }

    suspend fun updateLogbookRevision(
        logbookId: String,
        title: String,
        description: String,
        attachmentUrl: String? = null
    ) {
        val updates = mutableMapOf<String, Any?>(
            "title" to title,
            "description" to description,
            "status" to Logbook.STATUS_PENDING,
            // Reset penanda review: logbook masuk antrian review mentor lagi.
            "reviewedAt" to 0L
        )
        if (attachmentUrl != null) updates["attachmentUrl"] = attachmentUrl
        firestore.collection(Logbook.COLLECTION).document(logbookId)
            .update(updates)
            .await()
    }

    /**
     * Mentor mereview logbook: setujui atau minta revisi + catatan.
     * reviewedAt dipakai mahasiswa untuk badge "review baru".
     */
    suspend fun reviewLogbook(logbookId: String, status: String, mentorNotes: String) {
        firestore.collection(Logbook.COLLECTION).document(logbookId)
            .update(
                mapOf(
                    "status" to status,
                    "mentorNotes" to mentorNotes,
                    "reviewedAt" to System.currentTimeMillis()
                )
            )
            .await()
    }

    /**
     * Absensi HARI INI milik satu mahasiswa: pasangan (masuk, pulang).
     * Dipakai beranda untuk menentukan tombol yang tampil + jam absen.
     * Null berarti belum ada absensi jenis tsb hari ini.
     */
    suspend fun getTodayAttendance(studentId: String): Pair<Attendance?, Attendance?> {
        val snapshot = firestore.collection(Attendance.COLLECTION)
            .whereEqualTo("studentId", studentId)
            .get()
            .await()
        val today = snapshot.toObjects(Attendance::class.java)
            .filter { isSameDay(it.timestamp?.toDate(), java.util.Date()) }
        val checkIn = today.filter { it.type == Attendance.TYPE_IN }
            .maxByOrNull { it.timestamp?.seconds ?: 0 }
        val checkOut = today.filter { it.type == Attendance.TYPE_OUT }
            .maxByOrNull { it.timestamp?.seconds ?: 0 }
        return checkIn to checkOut
    }

    private fun isSameDay(a: java.util.Date?, b: java.util.Date?): Boolean {
        if (a == null || b == null) return false
        val ca = java.util.Calendar.getInstance().apply { time = a }
        val cb = java.util.Calendar.getInstance().apply { time = b }
        return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR) &&
            ca.get(java.util.Calendar.DAY_OF_YEAR) == cb.get(java.util.Calendar.DAY_OF_YEAR)
    }

    /** Baca satu logbook by id — dipakai layar review mentor. */
    suspend fun getLogbookById(logbookId: String): Logbook? {
        val snapshot = firestore.collection(Logbook.COLLECTION)
            .document(logbookId).get().await()
        return if (snapshot.exists()) snapshot.toObject(Logbook::class.java) else null
    }

    /**
     * Logbook semua mahasiswa di satu perusahaan, realtime — dipakai mentor
     * untuk daftar review & badge notifikasi. Tanpa orderBy server agar tidak
     * butuh composite index; urutkan di memori (terbaru di atas).
     */
    fun observeLogbooksByCompany(companyId: String): Flow<List<Logbook>> = callbackFlow {
        if (companyId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registration = firestore.collection(Logbook.COLLECTION)
            .whereEqualTo("companyId", companyId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot.toObjects(Logbook::class.java)
                    .sortedByDescending { it.createdAt?.seconds ?: Long.MIN_VALUE }
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Logbook milik satu mahasiswa, realtime — dipakai halaman notifikasi
     * mahasiswa untuk hasil review (approved/revision + catatan mentor).
     */
    fun observeLogbooksByStudent(studentId: String): Flow<List<Logbook>> = callbackFlow {
        if (studentId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registration = firestore.collection(Logbook.COLLECTION)
            .whereEqualTo("studentId", studentId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot.toObjects(Logbook::class.java)
                    .sortedByDescending { it.createdAt?.seconds ?: Long.MIN_VALUE }
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    suspend fun recordAttendance(
        studentId: String,
        companyId: String,
        type: String,
        photoUrl: String,
        latitude: Double,
        longitude: Double
    ): Attendance {
        // Gunakan authRepository.getCompanies() agar normalisasi workSchedule/startTime
        // sama persis dengan yang dipakai beranda — toObject() langsung tidak bisa
        // deserialize Map<String,WorkDaySchedule> sehingga startTime bisa kosong.
        val company: Company? = try {
            authRepository.getCompanies().firstOrNull { it.id == companyId }
        } catch (e: Exception) {
            null
        }
        val valid = RadiusChecker.isInsideRadius(
            latitude,
            longitude,
            company?.location?.latitude,
            company?.location?.longitude,
            company?.radiusMeters ?: 100.0
        )
        // Deteksi keterlambatan hanya untuk absen masuk.
        val late = computeLate(type, java.util.Date(), company)
        val ref = firestore.collection(Attendance.COLLECTION).document()
        val attendance = Attendance(
            id = ref.id,
            studentId = studentId,
            companyId = companyId,
            type = type,
            timestamp = Timestamp.now(),
            photoUrl = photoUrl,
            location = com.google.firebase.firestore.GeoPoint(latitude, longitude),
            isValid = valid,
            isLate = late.first,
            lateMinutes = late.second
        )
        ref.set(attendance).await()
        return attendance
    }

    /**
     * Hitung status telat absen masuk terhadap jadwal perusahaan pada tanggal
     * record yang diberikan. Dipakai write-time (recordAttendance) dan
     * read-time (getAttendanceByStudent) agar data lama juga tampil benar.
     */
    private fun computeLate(type: String, date: java.util.Date?, company: Company?): Pair<Boolean, Int> {
        if (type != Attendance.TYPE_IN || company == null || date == null) return false to 0
        val cal = java.util.Calendar.getInstance().apply { time = date }
        val nowTotal = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 +
            cal.get(java.util.Calendar.MINUTE)
        // Ambil jam masuk hari record dari workSchedule; fallback ke startTime.
        val dayNames = listOf("Minggu","Senin","Selasa","Rabu","Kamis","Jumat","Sabtu")
        val dayName = dayNames[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]
        val startStr = company.workSchedule[dayName]?.start
            ?.takeIf { it.isNotBlank() } ?: company.startTime.ifBlank { "08:00" }
        val parts = startStr.split(":")
        if (parts.size != 2) return false to 0
        val startTotal = (parts[0].toIntOrNull() ?: 8) * 60 + (parts[1].toIntOrNull() ?: 0)
        return if (nowTotal > startTotal) true to (nowTotal - startTotal) else false to 0
    }

    /**
     * Ambil semua data absensi milik satu mahasiswa, urut terbaru di atas.
     *
     * @param forceServer true = paksa baca dari server (pull-to-refresh) agar
     * tidak disajikan cache lokal yang basi — sama dengan getCompanies().
     * Status telat juga dihitung ulang di sisi baca berdasarkan jadwal
     * perusahaan, sehingga record lama (sebelum fitur isLate) tampil benar.
     */
    suspend fun getAttendanceByStudent(
        studentId: String,
        forceServer: Boolean = false
    ): List<Attendance> {
        val query = firestore.collection(Attendance.COLLECTION)
            .whereEqualTo("studentId", studentId)
        val snapshot = if (forceServer) {
            query.get(com.google.firebase.firestore.Source.SERVER).await()
        } else {
            query.get().await()
        }
        val records = snapshot.toObjects(Attendance::class.java)
            .sortedByDescending { it.timestamp?.seconds ?: Long.MIN_VALUE }
        if (records.isEmpty()) return records
        // Hitung ulang status telat read-time (tanpa menulis ulang ke Firestore).
        val company: Company? = try {
            authRepository.getCompanies(forceServer)
                .firstOrNull { it.id == records.first().companyId }
        } catch (e: Exception) {
            null
        }
        if (company == null) return records
        return records.map { rec ->
            val late = computeLate(rec.type, rec.timestamp?.toDate(), company)
            if (late.first != rec.isLate || late.second != rec.lateMinutes) {
                rec.copy(isLate = late.first, lateMinutes = late.second)
            } else {
                rec
            }
        }
    }

    /** Ambil semua data absensi untuk satu perusahaan, urut terbaru di atas. */
    suspend fun getAttendanceByCompany(companyId: String): List<Attendance> {
        val snapshot = firestore.collection(Attendance.COLLECTION)
            .whereEqualTo("companyId", companyId)
            .get()
            .await()
        return snapshot.toObjects(Attendance::class.java)
            .sortedByDescending { it.timestamp?.seconds ?: Long.MIN_VALUE }
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
