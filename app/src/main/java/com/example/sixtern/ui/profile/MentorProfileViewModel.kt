package com.example.sixtern.ui.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sixtern.data.model.Company
import com.example.sixtern.data.model.DAY_ORDER
import com.example.sixtern.data.model.WorkDaySchedule
import com.example.sixtern.data.model.activeDays
import com.example.sixtern.data.repository.AuthRepository
import com.google.firebase.firestore.GeoPoint
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.launch

/**
 * Tab "Akun" mentor: profil akun + form detail perusahaan.
 * Form BOLEH diisi belakangan -- mentor bebas eksplor Beranda dulu.
 *
 * State dinamis (benefit, jadwal, logo) dipegang Activity lewat helper di sini:
 * - benefit: list string, tambah/hapus bebas, minimal 1 baris tampil.
 * - jadwal: map hari -> jam masuk/pulang; hari tanpa jam = libur.
 * - logo: Base64 kecil (data:image/jpeg;base64,...) agar tanpa Storage berbayar.
 */
class MentorProfileViewModel(
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {

    data class DayRow(
        val day: String,
        val start: String = "",
        val end: String = ""
    ) {
        val isOff: Boolean get() = start.isBlank() && end.isBlank()
    }

    private val _company = MutableLiveData<Company?>()
    val company: LiveData<Company?> = _company

    private val _saving = MutableLiveData(false)
    val saving: LiveData<Boolean> = _saving

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    fun load(uid: String) {
        viewModelScope.launch {
            try {
                _company.value = repository.getCompanyByMentor(uid)
            } catch (e: Exception) {
                _message.value = e.message ?: "Gagal memuat profil perusahaan"
            }
        }
    }

    fun saveCompany(
        uid: String,
        companyName: String,
        description: String,
        slotsText: String,
        benefits: List<String>,
        schedule: List<DayRow>,
        logoDataUri: String,
        latitudeText: String,
        longitudeText: String,
        radiusText: String
    ) {
        if (companyName.isBlank()) {
            _message.value = "Nama perusahaan wajib diisi"
            return
        }
        val slots = slotsText.toIntOrNull()
        if (slots == null || slots <= 0) {
            _message.value = "Slot magang harus angka lebih dari 0"
            return
        }
        val cleanBenefits = benefits.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleanBenefits.size > MAX_BENEFITS) {
            _message.value = "Maksimal $MAX_BENEFITS benefit"
            return
        }
        if (cleanBenefits.any { it.length > MAX_BENEFIT_LEN }) {
            _message.value = "Tiap benefit maksimal $MAX_BENEFIT_LEN karakter"
            return
        }
        val cleanSchedule = schedule.map {
            DayRow(it.day, normalizeTime(it.start), normalizeTime(it.end))
        }
        for (row in cleanSchedule) {
            if (row.start.isNotBlank() && !isValidTime(row.start)) {
                _message.value = "Jam masuk ${row.day} tidak valid (HH:MM)"
                return
            }
            if (row.end.isNotBlank() && !isValidTime(row.end)) {
                _message.value = "Jam pulang ${row.day} tidak valid (HH:MM)"
                return
            }
        }
        val activeSchedule = cleanSchedule.filter { !it.isOff }
        if (activeSchedule.isEmpty()) {
            _message.value = "Isi minimal satu hari kerja"
            return
        }
        val activeDays = DAY_ORDER.filter { day -> activeSchedule.any { it.day == day } }
        val scheduleMap = activeSchedule.associate { it.day to WorkDaySchedule(it.start, it.end) }
        val first = activeSchedule.first()
        val latitude = latitudeText.toDoubleOrNull()
        val longitude = longitudeText.toDoubleOrNull()
        if (latitude == null || longitude == null) {
            _message.value = "Pilih titik lokasi di peta dulu"
            return
        }
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            _message.value = "Koordinat lokasi tidak valid"
            return
        }
        val radius = radiusText.toDoubleOrNull() ?: 100.0
        if (radius <= 0 || radius > 5000) {
            _message.value = "Radius 1-5000 meter"
            return
        }
        _saving.value = true
        viewModelScope.launch {
            try {
                val existing = _company.value
                val company = Company(
                    id = existing?.id.orEmpty(),
                    mentorId = uid,
                    name = companyName.trim(),
                    description = description.trim(),
                    bannerUrl = existing?.bannerUrl.orEmpty(),
                    logoUrl = logoDataUri.ifBlank { existing?.logoUrl.orEmpty() },
                    internshipSlots = slots,
                    benefits = cleanBenefits,
                    workDays = activeDays,
                    startTime = first.start.ifBlank { "08:00" },
                    endTime = first.end.ifBlank { "17:00" },
                    workSchedule = scheduleMap,
                    location = GeoPoint(latitude, longitude),
                    radiusMeters = radius
                )
                _company.value = repository.saveCompany(company)
                _message.value = "Profil perusahaan tersimpan"
            } catch (e: Exception) {
                _message.value = e.message ?: "Gagal menyimpan, coba lagi"
            } finally {
                _saving.value = false
            }
        }
    }

    fun logout() = repository.signOut()

    fun consumeMessage() {
        _message.value = null
    }

    companion object {
        const val MAX_BENEFITS = 10
        const val MAX_BENEFIT_LEN = 100
        private val TIME_REGEX = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

        fun emptyWeek(): List<DayRow> = DAY_ORDER.map { DayRow(it) }

        fun fromCompany(company: Company?): List<DayRow> {
            if (company == null) return emptyWeek()
            val schedule = company.workSchedule
            if (schedule.isNotEmpty()) {
                return DAY_ORDER.map { day ->
                    val s = schedule[day]
                    DayRow(day, s?.start.orEmpty(), s?.end.orEmpty())
                }
            }
            val active = company.activeDays().toSet()
            return DAY_ORDER.map { day ->
                if (day in active) DayRow(day, company.startTime, company.endTime)
                else DayRow(day)
            }
        }

        fun normalizeTime(raw: String): String {
            val t = raw.trim()
            if (t.isEmpty()) return ""
            val parts = t.split(":")
            if (parts.size != 2) return ""
            val hh = parts[0].toIntOrNull() ?: return ""
            val mm = parts[1].toIntOrNull() ?: return ""
            if (hh !in 0..23 || mm !in 0..59) return ""
            return "%02d:%02d".format(hh, mm)
        }

        fun isValidTime(raw: String): Boolean = TIME_REGEX.matches(raw.trim())

        fun bitmapToLogoDataUri(src: Bitmap, maxSide: Int = 256): String {
            val w = src.width.coerceAtLeast(1)
            val h = src.height.coerceAtLeast(1)
            val scale = maxSide.toFloat() / maxOf(w, h).toFloat()
            val bmp = if (scale < 1f) {
                Bitmap.createScaledBitmap(src, (w * scale).toInt(), (h * scale).toInt(), true)
            } else src
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 70, out)
            if (bmp !== src) bmp.recycle()
            return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }

        fun decodeLogoDataUri(dataUri: String): Bitmap? = try {
            val base64 = dataUri.substringAfter("base64,", "")
            if (base64.isBlank()) null
            else {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (e: Exception) {
            null
        }
    }
}
