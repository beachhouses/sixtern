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
    val companyId: String? = null
) {
    companion object {
        const val ROLE_MAHASISWA = "mahasiswa"
        const val ROLE_MENTOR = "mentor"
        const val ROLE_PRODI = "prodi"
        const val COLLECTION = "users"
    }
}
