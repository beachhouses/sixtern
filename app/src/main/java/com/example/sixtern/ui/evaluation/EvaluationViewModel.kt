package com.example.sixtern.ui.evaluation

import android.content.ContentValues
import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sixtern.data.model.Evaluation
import com.example.sixtern.data.repository.AuthRepository
import com.example.sixtern.data.repository.InternshipRepository
import kotlinx.coroutines.launch
import java.io.OutputStream

class EvaluationViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val internshipRepository: InternshipRepository = InternshipRepository()
) : ViewModel() {

    private val _evaluation = MutableLiveData<Evaluation?>()
    val evaluation: LiveData<Evaluation?> = _evaluation

    private val _userName = MutableLiveData("")
    val userName: LiveData<String> = _userName

    private val _message = MutableLiveData<String?>(null)
    val message: LiveData<String?> = _message

    fun load(uid: String) {
        viewModelScope.launch {
            try {
                val user = authRepository.getUser(uid)
                _userName.value = user?.name.orEmpty()
                _evaluation.value = internshipRepository.getEvaluation(uid)
            } catch (e: Exception) {
                _message.value = e.message ?: "Gagal memuat evaluasi"
            }
        }
    }

    fun downloadPdf(context: Context) {
        val evaluation = _evaluation.value
        if (evaluation == null) {
            _message.value = "Belum ada nilai"
            return
        }
        try {
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            val titlePaint = Paint().apply {
                textSize = 22f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bodyPaint = Paint().apply { textSize = 14f }
            var y = 80f
            canvas.drawText("Transkrip Magang SixTern", 40f, y, titlePaint)
            y += 36f
            canvas.drawText("Nama: ${_userName.value}", 40f, y, bodyPaint)
            y += 28f
            canvas.drawText("Pembimbing lapangan: ${evaluation.mentorScore}", 40f, y, bodyPaint)
            y += 24f
            canvas.drawText("Dosen pembimbing: ${evaluation.supervisorScore}", 40f, y, bodyPaint)
            y += 24f
            canvas.drawText("Presensi dan logbook: ${evaluation.attendanceLogbookScore}", 40f, y, bodyPaint)
            y += 24f
            canvas.drawText("Total: ${evaluation.totalScore}", 40f, y, bodyPaint)
            document.finishPage(page)
            val fileName = "transkrip-sixtern-${System.currentTimeMillis()}.pdf"
            val output = openOutput(context, fileName)
                ?: throw IllegalStateException("Tidak bisa membuat file")
            output.use { document.writeTo(it) }
            document.close()
            _message.value = "Transkrip tersimpan: $fileName"
        } catch (e: Exception) {
            _message.value = e.message ?: "Gagal membuat PDF"
        }
    }

    private fun openOutput(context: Context, fileName: String): OutputStream? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver
                .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return null
            context.contentResolver.openOutputStream(uri)
        } else {
            @Suppress("DEPRECATION")
            val file = java.io.File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName
            )
            java.io.FileOutputStream(file)
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
