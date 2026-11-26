package com.example.sixtern.data.model

/**
 * Identitas kampus & prodi — dikelola role prodi (pemilik aplikasi).
 * Disimpan sebagai satu dokumen `campus/info` di Firestore.
 */
data class CampusInfo(
    val campusName: String = "",
    val prodiName: String = "",
    val faculty: String = "",
    val address: String = "",
    val website: String = "",
    val description: String = "",
    /** Logo kampus, Base64 kecil (data URI) — pola yang sama dengan logo perusahaan. */
    val logoUrl: String = ""
) {
    companion object {
        const val COLLECTION = "campus"
        const val DOC_ID = "info"
    }
}
