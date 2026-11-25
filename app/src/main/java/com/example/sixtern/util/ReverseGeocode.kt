package com.example.sixtern.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Reverse-geocode via Nominatim OpenStreetMap (gratis, tanpa API key).
 * Dipakai di [com.example.sixtern.ui.home.CompanyDetailActivity] agar lokasi
 * magang tampil sebagai alamat tulisan, bukan sekadar angka lat/lng.
 * Gagal (offline/timeout/rate-limit) -> null, pemanggil wajib fallback ke koordinat.
 */
suspend fun reverseGeocode(lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
    var conn: HttpURLConnection? = null
    try {
        val url = URL(
            "https://nominatim.openstreetmap.org/reverse" +
                "?format=jsonv2&lat=$lat&lon=$lng&accept-language=id&zoom=18"
        )
        conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            // Nominatim mewajibkan User-Agent yang mengidentifikasi aplikasi.
            setRequestProperty("User-Agent", "SixTern-Android/1.0 (contact: prodi-ti-usu)")
            setRequestProperty("Accept", "application/json")
            connectTimeout = 8000
            readTimeout = 8000
        }
        if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        JSONObject(body).optString("display_name", "").takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    } finally {
        conn?.disconnect()
    }
}
