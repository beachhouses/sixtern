package com.example.sixtern.ui.logbook

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import com.example.sixtern.R
import com.example.sixtern.databinding.ActivityLogbookFormBinding
import com.example.sixtern.ui.auth.LoginActivity

class LogbookFormActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogbookFormBinding
    private val viewModel: LogbookViewModel by viewModels()
    private var editingId: String? = null

    /** URI lampiran yang baru dipilih (gambar/PDF, opsional, belum disimpan). */
    private var selectedAttachmentUri: Uri? = null

    /** Pilih gambar atau PDF dari penyimpanan. */
    private val pickFile = registerForActivityResult(
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
        // Deskripsi tersimpan sebagai HTML: tampilkan sebagai teks berformat
        // (Spannable ala Word).
        val descHtml = intent.getStringExtra(EXTRA_DESC).orEmpty()
        binding.descInput.setText(RichTextEditor.fromHtml(descHtml))

        // Catatan mentor terakhir (read-only) — tampil saat membuka revisi.
        val mentorNotes = intent.getStringExtra(EXTRA_NOTES).orEmpty()
        if (mentorNotes.isNotBlank()) {
            binding.mentorNotesLabel.visibility = View.VISIBLE
            binding.mentorNotesText.visibility = View.VISIBLE
            binding.mentorNotesText.text = mentorNotes
        }

        viewModel.init(uid, companyId)

        binding.backButton.setOnClickListener { finish() }

        // Toolbar format ala Word: format teks TERPILIH secara langsung
        // (langsung terlihat tebal/miring/warna di kolom input).
        binding.boldButton.setOnClickListener { RichTextEditor.toggleBold(binding.descInput) }
        binding.italicButton.setOnClickListener { RichTextEditor.toggleItalic(binding.descInput) }
        binding.underlineButton.setOnClickListener { RichTextEditor.toggleUnderline(binding.descInput) }
        binding.bulletButton.setOnClickListener { RichTextEditor.toggleBullets(binding.descInput) }
        binding.clearFormatButton.setOnClickListener { RichTextEditor.clearFormatting(binding.descInput) }
        buildColorRow()

        binding.attachmentButton.setOnClickListener { pickFile.launch("*/*") }
        binding.saveButton.setOnClickListener {
            // Revisi: lampiran baru (bila dipilih) menimpa yang lama.
            // Opsional: null bila tidak memilih file — repository mempertahankan
            // nilai lama (lihat updateLogbookRevision).
            val attachment = selectedAttachmentUri?.toString()
            viewModel.save(
                binding.titleInput.text.toString(),
                // Simpan Spannable kolom sebagai HTML agar format tampil
                // di daftar & review mentor.
                RichTextEditor.toHtml(binding.descInput.text),
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
                // Logbook BARU berhasil dibuat -> pindah ke layar logbook;
                // revisi -> cukup tutup form (sudah di layar logbook).
                if (!viewModel.saving.value!! && viewModel.created.value == true) {
                    viewModel.consumeCreated()
                    val intent = android.content.Intent(this, LogbookActivity::class.java)
                    intent.putExtra(LoginActivity.EXTRA_USER_ID, uid)
                    intent.putExtra(LogbookActivity.EXTRA_COMPANY_ID, companyId)
                    startActivity(intent)
                }
                if (!viewModel.saving.value!!) finish()
            }
        }
    }

    /** Baris pilihan warna teks: lingkaran warna, ketuk untuk mewarnai seleksi. */
    private fun buildColorRow() {
        val row = binding.colorRow
        row.removeAllViews()
        val size = (36 * resources.displayMetrics.density).toInt()
        val margin = (6 * resources.displayMetrics.density).toInt()
        RichTextEditor.TEXT_COLORS.forEach { (label, color) ->
            val dot = android.view.View(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(size, size).also {
                    it.marginEnd = margin
                }
                val bg = AppCompatResources.getDrawable(
                    context, R.drawable.bg_icon_circle
                )?.mutate()
                bg?.let { DrawableCompat.setTint(it, color) }
                background = bg
                contentDescription = label
                isClickable = true
                isFocusable = true
            }
            dot.setOnClickListener { RichTextEditor.applyColor(binding.descInput, color) }
            row.addView(dot)
        }
    }

    private fun queryFileName(uri: Uri): String {
        var name = "lampiran"
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

        /** Alias lama (kompatibel test): delegasi ke RichTextEditor. */
        fun fromHtml(html: String): android.text.Spanned = RichTextEditor.fromHtml(html)

        fun toHtml(raw: String): String {
            if (raw.isBlank()) return ""
            if (RichTextEditor.containsHtmlTag(raw)) return raw
            return RichTextEditor.toHtml(android.text.SpannedString(raw))
        }

        fun containsHtmlTag(raw: String): Boolean = RichTextEditor.containsHtmlTag(raw)
    }
}
