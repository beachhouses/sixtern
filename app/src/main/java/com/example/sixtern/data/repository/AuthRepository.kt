package com.example.sixtern.data.repository

import com.example.sixtern.data.model.Company
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

    suspend fun getUser(uid: String): SixternUser? {
        val snapshot = firestore.collection(SixternUser.COLLECTION).document(uid).get().await()
        return snapshot.toObject(SixternUser::class.java)
    }

    suspend fun saveUser(user: SixternUser) {
        firestore.collection(SixternUser.COLLECTION).document(user.uid).set(user).await()
    }

    suspend fun getCompanies(): List<Company> {
        val snapshot = firestore.collection(Company.COLLECTION).get().await()
        return snapshot.toObjects(Company::class.java)
    }

    fun signOut() {
        auth.signOut()
    }

    fun currentUid(): String? = auth.currentUser?.uid
}
