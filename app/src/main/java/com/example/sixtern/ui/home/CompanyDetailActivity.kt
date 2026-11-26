package com.example.sixtern.ui.home

import android.app.DatePickerDialog
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Base64
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.sixtern.R
import com.example.sixtern.data.model.Company
import com.example.sixtern.data.model.DAY_ORDER
import com.example.sixtern.data.model.SixternUser
import com.example.sixtern.data.model.scheduleSummary
import com.example.sixtern.data.repository.AuthRepository
import com.example.sixtern.databinding.ActivityCompanyDetailBinding
import com.example.sixtern.ui.auth.LoginActivity
import com.example.sixtern.ui.profile.MentorProfileViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Detail perusahaan: dibuka dari klik kartu di beranda mahasiswa.
 * Tombol ajukan ada di paling bawah halaman.
 */
class CompanyDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCompanyDetailBinding
    private val viewModel: CompanyDetailViewModel by viewModels()

    /** Tanggal periode magang (yyyy-MM-dd) yang dipilih di dialog pengajuan. */
    private var applyStartDate: String = ""
    private var applyEndDate: String = ""
    /** Surat permohonan (data URI Base64) + nama file untuk label dialog. */
    private var applyLetterDataUri: String = ""
    private var applyLetterFileName: String = ""
    /** Callback refresh label yang aktif (null bila dialog sudah ditutup). */
    private var letterLabelRefresher: (() -> Unit)? = null

    /** Pilih file PDF surat permohonan dari penyimpanan. */
    private val pickLetter = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val name = getFileName(uri)
        // Baca bytes di background: PDF bisa sampai 1.5MB.
        lifecycleScope.launch(Dispatchers.IO) {
            val dataUri = pdfToDataUri(uri)
            withContext(Dispatchers.Main) {
                if (dataUri == null) {
                    Toast.makeText(
                        this@CompanyDetailActivity,
                        getString(R.string.apply_letter_too_big, name),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    applyLetterDataUri = dataUri
                    applyLetterFileName = name
                }
                letterLabelRefresher?.invoke()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCompanyDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uid = intent.getStringExtra(LoginActivity.EXTRA_USER_ID).orEmpty()
        val companyId = intent.getStringExtra(EXTRA_COMPANY_ID).orEmpty()

        binding.backButton.setOnClickListener { finish() }

        viewModel.state.observe(this) { state ->
            when (state) {
                is CompanyDetailUiState.Loading -> {
                    binding.loadingBar.visibility = View.VISIBLE
                    binding.contentGroup.visibility = View.GONE
                }
                is CompanyDetailUiState.Loaded -> {
                    binding.loadingBar.visibility = View.GONE
                    binding.contentGroup.visibility = View.VISIBLE
                    renderCompany(
                        state.company, state.taken, state.companyAddress,
                        state.userAppliedCompanyId, state.userStatus, uid
                    )
                }
                is CompanyDetailUiState.Error -> {
                    binding.loadingBar.visibility = View.GONE
                    binding.contentGroup.visibility = View.GONE
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
        viewModel.applyResult.observe(this) { event ->
            event?.let {
                when {
                    it.success -> {
                        Toast.makeText(
                            this,
                            getString(R.string.choose_company_joined, it.companyName.ifBlank { "perusahaan" }),
                            Toast.LENGTH_LONG
                        ).show()
                        setResult(RESULT_OK)
                        finish()
                    }
                    it.message == "cancel_success" -> {
                        Toast.makeText(
                            this,
                            getString(R.string.apply_cancel_success),
                            Toast.LENGTH_LONG
                        ).show()
                        setResult(RESULT_OK)
                        finish()
                    }
                    it.message == "company_unverified" -> {
                        Toast.makeText(
                            this,
                            getString(R.string.company_detail_unverified),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    it.message == "letter_required" -> {
                        Toast.makeText(
                            this,
                            getString(R.string.apply_letter_required),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    else -> Toast.makeText(this, it.message, Toast.LENGTH_LONG).show()
                }
                viewModel.clearApplyResult()
            }
        }
        viewModel.applying.observe(this) { applying ->
            binding.applyBar.visibility = if (applying) View.VISIBLE else View.GONE
            binding.applyButton.isEnabled = !applying
        }

        binding.applyButton.setOnClickListener {
            val loaded = viewModel.state.value as? CompanyDetailUiState.Loaded
                ?: return@setOnClickListener
            val left = AuthRepository.remainingSlots(loaded.company, loaded.taken)
            if (left <= 0) {
                Toast.makeText(this, R.string.company_detail_full, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            showApplyDialog(uid, loaded.company)
        }

        viewModel.load(companyId, uid)
    }

    /**
     * Dialog pengajuan magang: tanggal masuk + tanggal selesai (DatePicker)
     * + upload surat permohonan (PDF, wajib). Tombol Kirim divalidasi di
     * dalam dialog; hasil final tetap lewat applyResult ViewModel.
     */
    private fun showApplyDialog(uid: String, company: Company) {
        // Reset isian tiap dialog dibuka.
        applyStartDate = ""
        applyEndDate = ""
        applyLetterDataUri = ""
        applyLetterFileName = ""

        val dialogView = layoutInflater.inflate(R.layout.dialog_apply_internship, null)
        val descText = dialogView.findViewById<TextView>(R.id.applyDialogDesc)
        val startButton = dialogView.findViewById<Button>(R.id.startDateButton)
        val endButton = dialogView.findViewById<Button>(R.id.endDateButton)
        val letterNameText = dialogView.findViewById<TextView>(R.id.letterFileNameText)
        val letterPickButton = dialogView.findViewById<Button>(R.id.letterPickButton)

        descText.text = getString(
            R.string.apply_dialog_desc, company.name.ifBlank { "-" }
        )

        val displayFmt = SimpleDateFormat("dd MMM yyyy", Locale("id"))
        val storeFmt = SimpleDateFormat(SixternUser.DATE_PATTERN, Locale.US)
        fun isoToDisplay(iso: String, hint: String): String {
            if (iso.isBlank()) return hint
            return runCatching { displayFmt.format(storeFmt.parse(iso)!!) }.getOrNull() ?: iso
        }
        fun refreshDates() {
            startButton.text =
                isoToDisplay(applyStartDate, getString(R.string.apply_start_date_hint))
            endButton.text =
                isoToDisplay(applyEndDate, getString(R.string.apply_end_date_hint))
        }
        fun refreshLetterLabel() {
            if (applyLetterDataUri.isBlank()) {
                letterNameText.text = getString(R.string.apply_letter_empty)
                letterPickButton.text = getString(R.string.apply_letter_pick)
            } else {
                letterNameText.text = getString(
                    R.string.apply_letter_attached, applyLetterFileName.ifBlank { "surat.pdf" }
                )
                letterPickButton.text = getString(R.string.apply_letter_change)
            }
        }
        refreshDates()
        refreshLetterLabel()

        startButton.setOnClickListener {
            pickDate(applyStartDate) { picked ->
                applyStartDate = picked
                refreshDates()
            }
        }
        endButton.setOnClickListener {
            pickDate(applyEndDate) { picked ->
                applyEndDate = picked
                refreshDates()
            }
        }
        letterPickButton.setOnClickListener {
            // Refresh label aktif selama dialog ini terbuka.
            letterLabelRefresher = { refreshLetterLabel() }
            pickLetter.launch("application/pdf")
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.apply_dialog_title)
            .setView(dialogView)
            .setPositiveButton(R.string.apply_submit, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnDismissListener { letterLabelRefresher = null }
        dialog.show()
        // Override agar dialog tidak tertutup saat validasi gagal.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            val periodError =
                SixternUser.validateInternshipPeriod(applyStartDate, applyEndDate)
            if (periodError != null) {
                Toast.makeText(this, periodError, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (applyLetterDataUri.isBlank()) {
                Toast.makeText(
                    this, getString(R.string.apply_letter_required), Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            dialog.dismiss()
            viewModel.applyForCompany(
                uid, company.id, applyStartDate, applyEndDate, applyLetterDataUri
            )
        }
    }

    private fun renderCompany(
        company: Company,
        taken: Int,
        address: String? = null,
        userAppliedCompanyId: String? = null,
        userStatus: String = "",
        uid: String = ""
    ) {
        val left = AuthRepository.remainingSlots(company, taken)
        binding.companyName.text = company.name.ifBlank { "-" }
        binding.companySlots.text = getString(R.string.company_slots_left, left, company.internshipSlots)
        binding.companyScheduleSummary.text = company.scheduleSummary()

        if (company.description.isBlank()) {
            binding.aboutLabel.visibility = View.GONE
            binding.companyDescription.visibility = View.GONE
        } else {
            binding.aboutLabel.visibility = View.VISIBLE
            binding.companyDescription.visibility = View.VISIBLE
            binding.companyDescription.text = company.description
        }

        val logo = MentorProfileViewModel.decodeLogoDataUri(company.logoUrl)
        if (logo != null) {
            binding.companyLogo.setImageBitmap(logo)
            binding.companyLogo.setPadding(0, 0, 0, 0)
        } else {
            binding.companyLogo.setImageResource(R.drawable.ic_nav_company)
            val p = (12 * resources.displayMetrics.density).toInt()
            binding.companyLogo.setPadding(p, p, p, p)
        }

        if (company.benefits.none { it.isNotBlank() }) {
            binding.benefitLabel.visibility = View.GONE
            binding.benefitList.visibility = View.GONE
        } else {
            binding.benefitLabel.visibility = View.VISIBLE
            binding.benefitList.visibility = View.VISIBLE
            binding.benefitList.removeAllViews()
            company.benefits.filter { it.isNotBlank() }.forEach { benefit ->
                val row = layoutInflater.inflate(
                    R.layout.item_benefit_detail, binding.benefitList, false
                )
                row.findViewById<TextView>(R.id.benefitText).text = benefit
                binding.benefitList.addView(row)
            }
        }

        binding.scheduleList.removeAllViews()
        DAY_ORDER.forEach { day ->
            val sched = company.workSchedule[day]
            val start = sched?.start.orEmpty().ifBlank { null }
            val end = sched?.end.orEmpty().ifBlank { null }
            val row = layoutInflater.inflate(R.layout.item_schedule_detail, binding.scheduleList, false)
            row.findViewById<TextView>(R.id.scheduleDay).text = day
            val timeView = row.findViewById<TextView>(R.id.scheduleTime)
            if (start == null && end == null) {
                timeView.text = getString(R.string.schedule_day_off)
                timeView.setTextColor(ContextCompat.getColor(this, R.color.login_text_secondary))
            } else {
                timeView.text = (start ?: "-") + " \u2013 " + (end ?: "-")
                timeView.setTextColor(ContextCompat.getColor(this, R.color.login_text_primary))
            }
            binding.scheduleList.addView(row)
        }

        val loc = company.location
        if (loc == null) {
            binding.locationLabel.visibility = View.GONE
            binding.locationText.visibility = View.GONE
        } else {
            binding.locationLabel.visibility = View.VISIBLE
            binding.locationText.visibility = View.VISIBLE
            // Alamat tulisan dari Nominatim bila ada; koordinat selalu jadi baris kedua.
            val coords = "%.6f, %.6f".format(loc.latitude, loc.longitude)
            binding.locationText.text = if (address.isNullOrBlank()) {
                coords
            } else {
                address + "\n" + coords
            }
        }

        // Perusahaan belum diverifikasi prodi: tidak bisa diajukan.
        if (!company.verified) {
            binding.applyButton.visibility = View.GONE
            binding.cancelButton.visibility = View.GONE
            binding.applyStatusText.visibility = View.VISIBLE
            binding.applyStatusText.text = getString(R.string.company_detail_unverified)
            binding.applyStatusText.setTextColor(
                ContextCompat.getColor(this, R.color.login_text_secondary)
            )
            return
        }
        binding.applyButton.visibility = View.VISIBLE

        // Cek apakah user sudah mengajukan ke perusahaan ini.
        val alreadyAppliedHere = userAppliedCompanyId == company.id
        val alreadyAppliedElsewhere = !userAppliedCompanyId.isNullOrBlank()
            && userAppliedCompanyId != company.id

        when {
            alreadyAppliedHere && userStatus == com.example.sixtern.data.model.SixternUser.STATUS_ACCEPTED -> {
                binding.applyButton.isEnabled = false
                binding.cancelButton.visibility = View.GONE
                binding.applyStatusText.visibility = View.VISIBLE
                binding.applyStatusText.text = getString(R.string.apply_already_accepted)
                binding.applyStatusText.setTextColor(
                    androidx.core.content.ContextCompat.getColor(this, R.color.chip_accepted_bg)
                )
            }
            alreadyAppliedHere && userStatus == com.example.sixtern.data.model.SixternUser.STATUS_REJECTED -> {
                // Ditolak — tombol ajukan aktif kembali agar bisa mengajukan ulang.
                binding.applyButton.isEnabled = left > 0
                binding.cancelButton.visibility = View.GONE
                binding.applyStatusText.visibility = View.VISIBLE
                binding.applyStatusText.text = getString(R.string.apply_rejected_reapply)
                binding.applyStatusText.setTextColor(
                    androidx.core.content.ContextCompat.getColor(this, R.color.chip_rejected_text)
                )
            }
            alreadyAppliedHere -> {
                // Pending — tampilkan pesan + tombol batalkan.
                binding.applyButton.isEnabled = false
                binding.applyStatusText.visibility = View.VISIBLE
                binding.applyStatusText.text = getString(R.string.apply_already_applied)
                binding.applyStatusText.setTextColor(
                    androidx.core.content.ContextCompat.getColor(this, R.color.login_text_secondary)
                )
                binding.cancelButton.visibility = View.VISIBLE
                binding.cancelButton.setOnClickListener {
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.apply_cancel_confirm_title))
                        .setMessage(getString(R.string.apply_cancel_confirm_msg, company.name))
                        .setPositiveButton(getString(R.string.apply_cancel_yes)) { _, _ ->
                            viewModel.cancelApplication(uid)
                        }
                        .setNegativeButton(getString(R.string.cancel), null)
                        .show()
                }
            }
            alreadyAppliedElsewhere -> {
                binding.applyButton.isEnabled = false
                binding.cancelButton.visibility = View.GONE
                binding.applyStatusText.visibility = View.VISIBLE
                binding.applyStatusText.text = getString(R.string.apply_already_other)
                binding.applyStatusText.setTextColor(
                    androidx.core.content.ContextCompat.getColor(this, R.color.login_text_secondary)
                )
            }
            left <= 0 -> {
                binding.applyButton.isEnabled = false
                binding.cancelButton.visibility = View.GONE
                binding.applyStatusText.visibility = View.GONE
            }
            else -> {
                binding.applyButton.isEnabled = true
                binding.cancelButton.visibility = View.GONE
                binding.applyStatusText.visibility = View.GONE
            }
        }
    }

    companion object {
        const val EXTRA_COMPANY_ID = "extra_company_id"
    }

    /** DatePicker -> tanggal "yyyy-MM-dd" lewat callback. */
    private fun pickDate(currentIso: String, onPicked: (String) -> Unit) {
        val cal = Calendar.getInstance()
        if (currentIso.isNotBlank()) {
            runCatching {
                val parsed = SimpleDateFormat(SixternUser.DATE_PATTERN, Locale.US).parse(currentIso)
                if (parsed != null) cal.time = parsed
            }
        }
        DatePickerDialog(
            this,
            { _, y, m, d ->
                onPicked(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d))
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    /** Nama file dari content URI (untuk label surat). */
    private fun getFileName(uri: Uri): String {
        var name = "surat.pdf"
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) name = cursor.getString(idx)
            }
        } catch (_: Exception) { }
        return name
    }

    /**
     * PDF -> data URI Base64. Return null bila ukuran melebihi batas
     * (MAX_LETTER_BYTES) agar hemat kuota dokumen Firestore.
     */
    private fun pdfToDataUri(uri: Uri): String? {
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                if (bytes.isEmpty() || bytes.size > SixternUser.MAX_LETTER_BYTES) return null
                "data:application/pdf;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        } catch (_: Exception) {
            null
        }
    }
}
