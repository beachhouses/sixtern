package com.example.sixtern.ui.logbook

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.sixtern.R
import com.example.sixtern.databinding.ActivityLogbookFormBinding
import com.example.sixtern.ui.auth.LoginActivity

class LogbookFormActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogbookFormBinding
    private val viewModel: LogbookViewModel by viewModels()
    private var editingId: String? = null

    /** URI lampiran PDF yang baru dipilih (belum disimpan ke Firestore). */
    private var selectedAttachmentUri: Uri? = null

    private val pickPdf = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        selectedAttachmentUri = uri
        val name = queryFileName(uri)
        binding.attachmentNameText.text = getString(R.string.logbook_attachment_name, name)
        binding.attachmentNameText.visibility = View.VISIBLE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogbookFormBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val uid = intent.getStringExtra(LoginActivity.EXTRA_USER_ID).orEmpty()
        val companyId = intent.getStringExtra(LogbookActivity.EXTRA_COMPANY_ID).orEmpty()
        editingId = intent.getStringExtra(EXTRA_ID)
        binding.titleInput.setText(intent.getStringExtra(EXTRA_TITLE).orEmpty())
        binding.descInput.setText(intent.getStringExtra(EXTRA_DESC).orEmpty())

        // Catatan mentor terakhir (read-only) — tampil saat membuka revisi.
        val mentorNotes = intent.getStringExtra(EXTRA_NOTES).orEmpty()
        if (mentorNotes.isNotBlank()) {
            binding.mentorNotesLabel.visibility = View.VISIBLE
            binding.mentorNotesText.visibility = View.VISIBLE
            binding.mentorNotesText.text = mentorNotes
        }

        viewModel.init(uid, companyId)

        binding.attachmentButton.setOnClickListener { pickPdf.launch("application/pdf") }
        binding.saveButton.setOnClickListener {
            // Revisi: lampiran baru (bila dipilih) menimpa yang lama.
            val attachment = selectedAttachmentUri?.toString()
            viewModel.save(
                binding.titleInput.text.toString(),
                binding.descInput.text.toString(),
                editingId,
                attachment
            )
        }
        viewModel.saving.observe(this) { saving ->
            binding.loadingBar.visibility = if (saving) View.VISIBLE else View.GONE
            binding.saveButton.isEnabled = !saving
        }
        viewModel.message.observe(this) { message ->
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                viewModel.clearMessage()
                if (!viewModel.saving.value!!) finish()
            }
        }
    }

    private fun queryFileName(uri: Uri): String {
        var name = "lampiran.pdf"
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = cursor.getString(idx)
                }
            }
        } catch (e: Exception) {
            // Biarkan nama default.
        }
        return name
    }

    companion object {
        const val EXTRA_ID = "extra_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_DESC = "extra_desc"
        const val EXTRA_NOTES = "extra_notes"
    }
}
