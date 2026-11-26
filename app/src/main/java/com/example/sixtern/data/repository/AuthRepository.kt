package com.example.sixtern.data.repository

import com.example.sixtern.data.model.CampusInfo
import com.example.sixtern.data.model.Company
import com.example.sixtern.data.model.DAY_ORDER
import com.example.sixtern.data.model.SixternUser
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun signIn(email: String, password: String): SixternUser {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        val uid = result.user?.uid ?: throw IllegalStateException("UID kosong setelah login")
        val user = getUser(uid) ?: throw IllegalStateException("Profil pengguna tidak ditemukan")
        // Akun baru menunggu persetujuan prodi sebelum fitur penuh terbuka.
        // Mahasiswa & mentor yang masih pending TETAP boleh masuk: aplikasi
        // mengarahkan mereka ke mode terbatas (mahasiswa: hanya melengkapi
        // profil; mentor: kartu menunggu verifikasi) sampai prodi menyetujui.
        when (user.accountStatus) {
            SixternUser.ACCOUNT_PENDING ->
                if (user.role == SixternUser.ROLE_PRODI) {
                    throw IllegalStateException("ACCOUNT_PENDING")
                }
            SixternUser.ACCOUNT_REJECTED -> throw IllegalStateException("ACCOUNT_REJECTED")
        }
        return user
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
            companyId = companyId.ifBlank { null },
            // Pendaftar baru selalu pending: bisa login tetapi mode terbatas
            // (hanya melengkapi profil) sampai prodi menyetujui.
            accountStatus = SixternUser.ACCOUNT_PENDING
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
            email = email,
            // Pendaftar baru selalu pending: tetap bisa login dalam mode
            // terbatas (Beranda, Notifikasi, Akun) sampai prodi menyetujui.
            accountStatus = SixternUser.ACCOUNT_PENDING
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
            email = email,
            accountStatus = SixternUser.ACCOUNT_VERIFIED
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
            "radiusMeters" to saved.radiusMeters,
            // Perusahaan baru belum terverifikasi; yang lama mempertahankan statusnya.
            "verified" to if (company.id.isBlank()) false else saved.verified
        )
        ref.set(payload).await()
        return saved
    }

    suspend fun getUser(uid: String): SixternUser? {
        val snapshot = firestore.collection(SixternUser.COLLECTION).document(uid).get().await()
        return snapshot.toObject(SixternUser::class.java)
    }

    suspend fun saveUser(user: SixternUser) {
        // Tulis sebagai Map eksplisit agar field accountStatus selalu tersimpan
        // ke Firestore — tidak bergantung pada default value serializer Kotlin.
        val payload = hashMapOf(
            "uid" to user.uid,
            "role" to user.role,
            "name" to user.name,
            "email" to user.email,
            "nim" to (user.nim ?: ""),
            "major" to user.major,
            "university" to user.university,
            "photoUrl" to (user.photoUrl ?: ""),
            "companyId" to (user.companyId ?: ""),
            "bio" to user.bio,
            "age" to user.age,
            "phone" to user.phone,
            "skills" to user.skills,
            "portfolioUrl" to user.portfolioUrl,
            "cvUrl" to user.cvUrl,
            "internshipStatus" to user.internshipStatus,
            "rejectionReason" to user.rejectionReason,
            "statusUpdatedAt" to user.statusUpdatedAt,
            // Periode magang + surat permohonan (wajib diisi saat mengajukan).
            "internshipStartDate" to user.internshipStartDate,
            "internshipEndDate" to user.internshipEndDate,
            "applicationLetterUrl" to user.applicationLetterUrl,
            "accountStatus" to user.accountStatus
        )
        firestore.collection(SixternUser.COLLECTION).document(user.uid).set(payload).await()
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

    /**
     * Versi realtime dari getStudentsByCompany.
     * Firestore addSnapshotListener push update setiap ada perubahan dokumen
     * di koleksi users yang companyId-nya cocok — tanpa perlu polling.
     */
    /** Update field profil tambahan mahasiswa (bio, age, phone, skills, portfolioUrl). */
    suspend fun updateStudentProfile(
        uid: String,
        bio: String,
        age: String,
        phone: String,
        skills: List<String>,
        portfolioUrl: String,
        cvUrl: String = ""
    ) {
        firestore.collection(SixternUser.COLLECTION).document(uid).update(
            mapOf(
                "bio" to bio,
                "age" to age,
                "phone" to phone,
                "skills" to skills,
                "portfolioUrl" to portfolioUrl,
                "cvUrl" to cvUrl
            )
        ).await()
    }

    /** Observe dokumen user (mahasiswa) secara realtime — untuk notifikasi status pengajuan. */
    fun observeUserByUid(uid: String): Flow<SixternUser?> = callbackFlow {
        if (uid.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val registration = firestore.collection(SixternUser.COLLECTION).document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) {
                    trySend(null)
                    return@addSnapshotListener
                }
                val user = try { snapshot.toObject(SixternUser::class.java) } catch (e: Exception) { null }
                trySend(user)
            }
        awaitClose { registration.remove() }
    }

    /** Update status pengajuan magang mahasiswa (accepted/rejected) + alasan tolak. */
    suspend fun updateInternshipStatus(uid: String, status: String, reason: String) {
        firestore.collection(SixternUser.COLLECTION).document(uid).update(
            mapOf(
                "internshipStatus" to status,
                "rejectionReason" to reason,
                "statusUpdatedAt" to System.currentTimeMillis()
            )
        ).await()
    }

    /**
     * Batalkan / reset status pengajuan: companyId & periode + surat dihapus,
     * status direset pending agar mahasiswa bebas mengajukan ke tempat lain.
     */
    suspend fun removeStudentFromCompany(uid: String) {
        firestore.collection(SixternUser.COLLECTION).document(uid).update(
            mapOf(
                "companyId" to FieldValue.delete(),
                "internshipStatus" to SixternUser.STATUS_PENDING,
                "rejectionReason" to "",
                "internshipStartDate" to "",
                "internshipEndDate" to "",
                "applicationLetterUrl" to "",
                "statusUpdatedAt" to System.currentTimeMillis()
            )
        ).await()
    }

    fun observeStudentsByCompany(companyId: String): Flow<List<SixternUser>> = callbackFlow {
        if (companyId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registration = firestore.collection(SixternUser.COLLECTION)
            .whereEqualTo("companyId", companyId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot.documents.mapNotNull { doc ->
                    try { doc.toObject(SixternUser::class.java) } catch (e: Exception) { null }
                }.filter { it.role == SixternUser.ROLE_MAHASISWA }
                    .sortedBy { it.name.lowercase() }
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Daftar semua perusahaan.
     * @param verifiedOnly true = hanya perusahaan yang sudah disetujui prodi
     * (dipakai daftar pilihan mahasiswa; perusahaan baru mentor selalu
     * menunggu verifikasi dan tidak boleh dipilih mahasiswa).
     */
    suspend fun getCompanies(
        forceServer: Boolean = false,
        verifiedOnly: Boolean = false
    ): List<Company> {
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
            .filter { !verifiedOnly || it.verified }
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

    // ===================== Fungsi untuk role Prodi =====================

    /** Daftar user per role, realtime (mahasiswa / mentor / prodi). */
    fun observeUsersByRole(role: String): Flow<List<SixternUser>> = callbackFlow {
        if (role.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registration = firestore.collection(SixternUser.COLLECTION)
            .whereEqualTo("role", role)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot.documents.mapNotNull { doc ->
                    try { doc.toObject(SixternUser::class.java) } catch (e: Exception) { null }
                }.sortedBy { it.name.lowercase() }
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    /** Setujui / tolak pendaftaran akun (mahasiswa & mentor) oleh prodi. */
    suspend fun updateAccountStatus(uid: String, status: String) {
        firestore.collection(SixternUser.COLLECTION).document(uid)
            .update(mapOf("accountStatus" to status)).await()
    }

    /** Daftar semua perusahaan, realtime — untuk verifikasi & statistik prodi. */
    fun observeCompaniesRealtime(): Flow<List<Company>> = callbackFlow {
        val registration = firestore.collection(Company.COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snapshot.documents.mapNotNull { normalizeCompany(it.id, it.data) }
                    .sortedBy { it.name.lowercase() }
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    /** Verifikasi / batalkan verifikasi perusahaan oleh prodi. */
    suspend fun updateCompanyVerified(companyId: String, verified: Boolean) {
        firestore.collection(Company.COLLECTION).document(companyId)
            .update(mapOf("verified" to verified)).await()
    }

    /** Identitas kampus (nama, prodi, logo) — dokumen tunggal campus/info. */
    suspend fun getCampusInfo(): CampusInfo? {
        val doc = firestore.collection(CampusInfo.COLLECTION)
            .document(CampusInfo.DOC_ID).get().await()
        return if (doc.exists()) doc.toObject(CampusInfo::class.java) else null
    }

    suspend fun saveCampusInfo(info: CampusInfo) {
        firestore.collection(CampusInfo.COLLECTION)
            .document(CampusInfo.DOC_ID).set(info).await()
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
            radiusMeters = num("radiusMeters", 100.0).toDouble(),
            verified = data["verified"] as? Boolean ?: true
        )
    }

    fun signOut() {
        auth.signOut()
    }

    fun currentUid(): String? = auth.currentUser?.uid
}
