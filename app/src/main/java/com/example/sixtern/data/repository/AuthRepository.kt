package com.example.sixtern.data.repository

import com.example.sixtern.data.model.Company
import com.example.sixtern.data.model.DAY_ORDER
import com.example.sixtern.data.model.SixternUser
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun signIn(email: String, password: String): SixternUser {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        val uid = result.user?.uid ?: throw IllegalStateException("UID kosong setelah login")
        return getUser(uid) ?: throw IllegalStateException("Profil pengguna tidak ditemukan")
    }

    suspend fun signUpStudent(
        email: String,
        password: String,
        name: String,
        nim: String,
        companyId: String
    ): SixternUser {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val uid = result.user?.uid ?: throw IllegalStateException("UID kosong setelah daftar")
        val user = SixternUser(
            uid = uid,
            role = SixternUser.ROLE_MAHASISWA,
            name = name,
            email = email,
            nim = nim,
            companyId = companyId.ifBlank { null }
        )
        saveUser(user)
        return user
    }

    suspend fun signUpMentorBasic(
        email: String,
        password: String,
        name: String
    ): SixternUser {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val uid = result.user?.uid ?: throw IllegalStateException("UID kosong setelah daftar")
        val user = SixternUser(
            uid = uid,
            role = SixternUser.ROLE_MENTOR,
            name = name,
            email = email
        )
        saveUser(user)
        return user
    }

    suspend fun signUpMentor(
        email: String,
        password: String,
        name: String,
        company: Company
    ): Pair<SixternUser, Company> {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val uid = result.user?.uid ?: throw IllegalStateException("UID kosong setelah daftar")
        val user = SixternUser(
            uid = uid,
            role = SixternUser.ROLE_MENTOR,
            name = name,
            email = email
        )
        saveUser(user)
        val companyRef = firestore.collection(Company.COLLECTION).document()
        val savedCompany = company.copy(id = companyRef.id, mentorId = uid)
        companyRef.set(savedCompany).await()
        return user to savedCompany
    }

    suspend fun getCompanyByMentor(mentorId: String): Company? {
        val snapshot = firestore.collection(Company.COLLECTION)
            .whereEqualTo("mentorId", mentorId)
            .limit(1)
            .get()
            .await()
        return snapshot.documents.firstOrNull()?.let { normalizeCompany(it.id, it.data) }
    }

    suspend fun saveCompany(company: Company): Company {
        val ref = if (company.id.isBlank()) {
            firestore.collection(Company.COLLECTION).document()
        } else {
            firestore.collection(Company.COLLECTION).document(company.id)
        }
        val saved = company.copy(id = ref.id)
        // Tulis sebagai Map agar field benefits/workSchedule fleksibel
        // (list vs string lama) dan tidak butuh custom serializer.
        val payload = hashMapOf(
            "id" to saved.id,
            "mentorId" to saved.mentorId,
            "name" to saved.name,
            "description" to saved.description,
            "bannerUrl" to saved.bannerUrl,
            "logoUrl" to saved.logoUrl,
            "internshipSlots" to saved.internshipSlots,
            "benefits" to saved.benefits,
            "workDays" to saved.workDays,
            "startTime" to saved.startTime,
            "endTime" to saved.endTime,
            "workSchedule" to saved.workSchedule.mapValues { (_, v) ->
                mapOf("start" to v.start, "end" to v.end)
            },
            "location" to saved.location,
            "radiusMeters" to saved.radiusMeters
        )
        ref.set(payload).await()
        return saved
    }

    suspend fun getUser(uid: String): SixternUser? {
        val snapshot = firestore.collection(SixternUser.COLLECTION).document(uid).get().await()
        return snapshot.toObject(SixternUser::class.java)
    }

    suspend fun saveUser(user: SixternUser) {
        firestore.collection(SixternUser.COLLECTION).document(user.uid).set(user).await()
    }

    /**
     * Daftar mahasiswa pengaju: users ber-role mahasiswa yang companyId-nya
     * menunjuk ke perusahaan ini. Dipakai kartu notifikasi + daftar di
     * beranda mentor. Urut abjad nama agar stabil.
     */
    suspend fun getStudentsByCompany(companyId: String): List<SixternUser> {
        if (companyId.isBlank()) return emptyList()
        val snapshot = firestore.collection(SixternUser.COLLECTION)
            .whereEqualTo("companyId", companyId)
            .get()
            .await()
        return snapshot.documents.mapNotNull { doc ->
            try {
                doc.toObject(SixternUser::class.java)
            } catch (e: Exception) {
                null
            }
        }.filter { it.role == SixternUser.ROLE_MAHASISWA }
            .sortedBy { it.name.lowercase() }
    }

    suspend fun getCompanies(forceServer: Boolean = false): List<Company> {
        val query = firestore.collection(Company.COLLECTION)
        val snapshot = if (forceServer) {
            // Muat Ulang di beranda: paksa baca dari server agar dokumen
            // perusahaan yang baru disimpan mentor langsung terlihat.
            // Tanpa ini Firestore bisa menyajikan cache lokal yang basi.
            query.get(com.google.firebase.firestore.Source.SERVER).await()
        } else {
            query.get().await()
        }
        return snapshot.documents.mapNotNull { normalizeCompany(it.id, it.data) }
    }

    /**
     * Jumlah mahasiswa yang companyId-nya menunjuk ke tiap id perusahaan.
     * Dipakai kartu & detail untuk menghitung "Sisa X dari Y slot".
     */
    suspend fun countStudentsByCompany(companyIds: List<String>): Map<String, Int> {
        if (companyIds.isEmpty()) return emptyMap()
        val snapshot = firestore.collection(SixternUser.COLLECTION)
            .whereIn("companyId", companyIds.take(10))
            .get()
            .await()
        val counts = mutableMapOf<String, Int>()
        for (doc in snapshot.documents) {
            val cid = (doc.getString("companyId")).orEmpty()
            if (cid.isNotBlank()) counts[cid] = (counts[cid] ?: 0) + 1
        }
        return counts
    }

    companion object {
        /** Sisa slot = kuota - pendaftar; minimal 0 agar tak negatif. */
        fun remainingSlots(company: Company, taken: Int): Int =
            (company.internshipSlots - taken).coerceAtLeast(0)
    }

    /** Normalisasi dokumen companies agar format lama & baru sama-sama terbaca. */
    private fun normalizeCompany(id: String, data: Map<String, Any?>?): Company? {
        if (data == null) return null
        @Suppress("UNCHECKED_CAST")
        fun str(key: String): String = (data[key] as? String).orEmpty()
        @Suppress("UNCHECKED_CAST")
        fun strList(key: String): List<String> =
            (data[key] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        fun num(key: String, fallback: Number): Number = (data[key] as? Number) ?: fallback
        val workSchedule = Company.parseSchedule(data["workSchedule"])
        val workDays = strList("workDays").ifEmpty {
            DAY_ORDER.filter { workSchedule[it]?.isActive == true }
        }
        val firstActive = DAY_ORDER.firstOrNull { workSchedule[it]?.isActive == true }
        return Company(
            id = (data["id"] as? String).orEmpty().ifBlank { id },
            mentorId = str("mentorId"),
            name = str("name"),
            description = str("description"),
            bannerUrl = str("bannerUrl"),
            logoUrl = str("logoUrl"),
            internshipSlots = num("internshipSlots", 0).toInt(),
            benefits = Company.parseBenefits(data["benefits"]),
            workDays = workDays,
            startTime = str("startTime")
                .ifBlank { workSchedule[firstActive]?.start.orEmpty() }
                .ifBlank { "08:00" },
            endTime = str("endTime")
                .ifBlank { workSchedule[firstActive]?.end.orEmpty() }
                .ifBlank { "17:00" },
            workSchedule = workSchedule,
            location = data["location"] as? com.google.firebase.firestore.GeoPoint,
            radiusMeters = num("radiusMeters", 100.0).toDouble()
        )
    }

    fun signOut() {
        auth.signOut()
    }

    fun currentUid(): String? = auth.currentUser?.uid
}
