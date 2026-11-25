package com.example.sixtern.ui.profile

import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.sixtern.R
import com.example.sixtern.data.model.Company
import com.example.sixtern.data.model.DAY_ORDER
import com.example.sixtern.databinding.ActivityMentorProfileBinding
import com.example.sixtern.ui.auth.LoginActivity
import com.example.sixtern.ui.auth.MentorNav
import com.example.sixtern.ui.profile.MentorProfileViewModel.Companion.MAX_BENEFITS
import com.example.sixtern.ui.profile.MentorProfileViewModel.Companion.MAX_BENEFIT_LEN
import com.example.sixtern.ui.profile.MentorProfileViewModel.Companion.bitmapToLogoDataUri
import com.example.sixtern.ui.profile.MentorProfileViewModel.Companion.decodeLogoDataUri
import com.example.sixtern.ui.profile.MentorProfileViewModel.Companion.emptyWeek
import com.example.sixtern.ui.profile.MentorProfileViewModel.Companion.fromCompany
import com.example.sixtern.ui.profile.MentorProfileViewModel.Companion.normalizeTime
import com.example.sixtern.ui.profile.MentorProfileViewModel.DayRow

class MentorProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMentorProfileBinding
    private val viewModel: MentorProfileViewModel by viewModels()

    private val benefitInputs = mutableListOf<EditText>()
    private var schedule: MutableList<DayRow> = emptyWeek().toMutableList()
    private val scheduleViews = mutableMapOf<String, ScheduleViews>()
    private var logoDataUri: String = ""
    private var loadedCompanyId: String? = null

    private data class ScheduleViews(
        val status: TextView,
        val startBtn: android.widget.Button,
        val endBtn: android.widget.Button
    )

    private val logoPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        // Decode cepat di main thread, kompresi JPEG berat di background.
        val bmp: android.graphics.Bitmap? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val src = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(src)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membaca gambar", Toast.LENGTH_SHORT).show()
            null
        }
        val bitmap = bmp ?: return@registerForActivityResult
        lifecycleScope.launch(Dispatchers.Default) {
            val dataUri = bitmapToLogoDataUri(bitmap)
            withContext(Dispatchers.Main) {
                logoDataUri = dataUri
                renderLogoPreview()
            }
        }
    }

    private val mapPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val lat = result.data?.getDoubleExtra(MapPickerActivity.EXTRA_LAT, Double.NaN)
            val lng = result.data?.getDoubleExtra(MapPickerActivity.EXTRA_LNG, Double.NaN)
            if (lat != null && lng != null && !lat.isNaN() && !lng.isNaN()) {
                binding.latitudeInput.setText(lat.toString())
                binding.longitudeInput.setText(lng.toString())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMentorProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uid = intent.getStringExtra(LoginActivity.EXTRA_USER_ID).orEmpty()
        val name = intent.getStringExtra(LoginActivity.EXTRA_USER_NAME).orEmpty()

        binding.mentorNav.itemIconTintList = null
        MentorNav.setup(binding.mentorNav, this, R.id.nav_account, uid, name)
        viewModel.load(uid)
        buildScheduleRows()
        ensureBenefitRow("")

        binding.greetingText.text = if (name.isBlank()) {
            getString(R.string.mentor_home_greeting_fallback)
        } else {
            getString(R.string.mentor_home_greeting, name)
        }

        viewModel.company.observe(this) { company ->
            if (company == null) return@observe
            if (loadedCompanyId == company.id) return@observe
            loadedCompanyId = company.id
            if (binding.companyInput.text.isNullOrBlank()) {
                binding.companyInput.setText(company.name)
            }
            if (binding.descriptionInput.text.isNullOrBlank() && company.description.isNotBlank()) {
                binding.descriptionInput.setText(company.description)
            }
            if (binding.slotsInput.text.isNullOrBlank()) {
                val slots = if (company.internshipSlots > 0) company.internshipSlots.toString() else ""
                binding.slotsInput.setText(slots)
            }
            setBenefits(company.benefits)
            schedule = fromCompany(company).toMutableList()
            refreshScheduleRows()
            if (logoDataUri.isBlank() && company.logoUrl.isNotBlank()) {
                logoDataUri = company.logoUrl
                renderLogoPreview()
            }
            company.location?.let { loc ->
                if (binding.latitudeInput.text.isNullOrBlank()) {
                    binding.latitudeInput.setText(loc.latitude.toString())
                }
                if (binding.longitudeInput.text.isNullOrBlank()) {
                    binding.longitudeInput.setText(loc.longitude.toString())
                }
            }
            if (binding.radiusInput.text.isNullOrBlank()) {
                binding.radiusInput.setText(company.radiusMeters.toString())
            }
        }

        viewModel.saving.observe(this) { saving ->
            binding.loadingBar.visibility = if (saving) View.VISIBLE else View.GONE
            binding.saveButton.isEnabled = !saving
        }

        viewModel.message.observe(this) { message ->
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                viewModel.consumeMessage()
            }
        }

        binding.addBenefitButton.setOnClickListener {
            if (benefitInputs.size >= MAX_BENEFITS) {
                Toast.makeText(this, "Maksimal $MAX_BENEFITS benefit", Toast.LENGTH_SHORT).show()
            } else ensureBenefitRow("")
        }
        binding.logoPickButton.setOnClickListener { logoPicker.launch("image/*") }
        binding.mapPickButton.setOnClickListener {
            val intent = Intent(this, MapPickerActivity::class.java)
            binding.latitudeInput.text.toString().toDoubleOrNull()?.let {
                intent.putExtra(MapPickerActivity.EXTRA_LAT, it)
            }
            binding.longitudeInput.text.toString().toDoubleOrNull()?.let {
                intent.putExtra(MapPickerActivity.EXTRA_LNG, it)
            }
            mapPicker.launch(intent)
        }

        binding.saveButton.setOnClickListener {
            syncScheduleFromViews()
            viewModel.saveCompany(
                uid = uid,
                companyName = binding.companyInput.text.toString(),
                description = binding.descriptionInput.text.toString(),
                slotsText = binding.slotsInput.text.toString(),
                benefits = benefitInputs.map { it.text.toString() },
                schedule = schedule.toList(),
                logoDataUri = logoDataUri,
                latitudeText = binding.latitudeInput.text.toString(),
                longitudeText = binding.longitudeInput.text.toString(),
                radiusText = binding.radiusInput.text.toString()
            )
        }

        binding.logoutButton.setOnClickListener {
            viewModel.logout()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    // ---------- Benefit dinamis ----------

    private fun ensureBenefitRow(fill: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            p.topMargin = dp(8)
            layoutParams = p
        }
        val input = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f)
            background = AppCompatResources.getDrawable(context, R.drawable.bg_login_input)
            hint = getString(R.string.benefit_item_hint, benefitInputs.size + 1)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setPadding(dp(16))
            setTextColor(getColor(R.color.login_text_primary))
            setHintTextColor(getColor(R.color.login_text_secondary))
            textSize = 14f
            setText(fill)
            filters = arrayOf(android.text.InputFilter.LengthFilter(MAX_BENEFIT_LEN))
        }
        val del = ImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).also { it.leftMargin = dp(8) }
            background = AppCompatResources.getDrawable(context, R.drawable.bg_icon_circle)
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            contentDescription = getString(R.string.benefit_remove)
            setPadding(dp(10))
        }
        del.setOnClickListener {
            if (benefitInputs.size <= 1) {
                input.setText("")
                Toast.makeText(this, "Minimal 1 baris benefit", Toast.LENGTH_SHORT).show()
            } else {
                binding.benefitsBox.removeView(row)
                benefitInputs.remove(input)
                renumberBenefits()
            }
        }
        row.addView(input)
        row.addView(del)
        binding.benefitsBox.addView(row)
        benefitInputs.add(input)
    }

    private fun setBenefits(items: List<String>) {
        binding.benefitsBox.removeAllViews()
        benefitInputs.clear()
        if (items.isEmpty()) ensureBenefitRow("")
        else items.take(MAX_BENEFITS).forEach { ensureBenefitRow(it) }
    }

    private fun renumberBenefits() {
        benefitInputs.forEachIndexed { i, input ->
            input.hint = getString(R.string.benefit_item_hint, i + 1)
        }
    }

    // ---------- Logo ----------

    private fun renderLogoPreview() {
        val bmp = decodeLogoDataUri(logoDataUri)
        if (bmp != null) {
            binding.logoPreview.setImageBitmap(bmp)
            binding.logoEmptyText.visibility = View.GONE
            binding.logoPickButton.text = getString(R.string.company_logo_change)
        } else {
            binding.logoPreview.setImageDrawable(null)
            binding.logoEmptyText.visibility = View.VISIBLE
            binding.logoPickButton.text = getString(R.string.company_logo_pick)
        }
    }

    // ---------- Jadwal per-hari ----------

    private fun buildScheduleRows() {
        binding.scheduleBox.removeAllViews()
        scheduleViews.clear()
        DAY_ORDER.forEachIndexed { index, day ->
            val row = layoutInflater.inflate(R.layout.item_schedule_day, binding.scheduleBox, false)
            val dayChip: TextView = row.findViewById(R.id.dayChip)
            val status: TextView = row.findViewById(R.id.dayStatus)
            val startBtn: android.widget.Button = row.findViewById(R.id.startButton)
            val endBtn: android.widget.Button = row.findViewById(R.id.endButton)
            val clearBtn: android.widget.Button = row.findViewById(R.id.clearButton)
            dayChip.text = day.take(3)
            dayChip.contentDescription = day
            startBtn.setOnClickListener { pickTime(index, true) }
            endBtn.setOnClickListener { pickTime(index, false) }
            clearBtn.setOnClickListener {
                schedule[index] = schedule[index].copy(start = "", end = "")
                refreshScheduleRows()
            }
            binding.scheduleBox.addView(row)
            scheduleViews[day] = ScheduleViews(status, startBtn, endBtn)
        }
        refreshScheduleRows()
    }

    private fun refreshScheduleRows() {
        schedule.forEach { row ->
            val v = scheduleViews[row.day] ?: return@forEach
            v.startBtn.text = row.start.ifBlank { getString(R.string.start_time_hint) }
            v.endBtn.text = row.end.ifBlank { getString(R.string.end_time_hint) }
            v.status.text = if (row.isOff) getString(R.string.schedule_day_off) else row.run { "$start-" + end }
            v.status.setTextColor(
                getColor(if (row.isOff) R.color.login_text_secondary else R.color.sixtern_green_deep)
            )
        }
    }

    private fun syncScheduleFromViews() {
        // schedule sudah di-update saat TimePicker/clear; normalisasi akhir di sini.
        schedule = schedule.map { DayRow(it.day, normalizeTime(it.start), normalizeTime(it.end)) }.toMutableList()
    }

    private fun pickTime(index: Int, isStart: Boolean) {
        val cur = if (isStart) schedule[index].start else schedule[index].end
        val hh = cur.substringBefore(":").toIntOrNull() ?: 8
        val mm = cur.substringAfter(":", "00").toIntOrNull() ?: 0
        TimePickerDialog(this, { _, h, m ->
            val t = "%02d:%02d".format(h, m)
            schedule[index] = if (isStart) schedule[index].copy(start = t) else schedule[index].copy(end = t)
            refreshScheduleRows()
        }, hh, mm, true).show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
