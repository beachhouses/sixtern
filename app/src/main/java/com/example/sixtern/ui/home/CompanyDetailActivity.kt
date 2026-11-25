package com.example.sixtern.ui.home

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.sixtern.R
import com.example.sixtern.data.model.Company
import com.example.sixtern.data.model.DAY_ORDER
import com.example.sixtern.data.model.scheduleSummary
import com.example.sixtern.data.repository.AuthRepository
import com.example.sixtern.databinding.ActivityCompanyDetailBinding
import com.example.sixtern.ui.auth.LoginActivity
import com.example.sixtern.ui.profile.MentorProfileViewModel

/**
 * Detail perusahaan: dibuka dari klik kartu di beranda mahasiswa.
 * Tombol ajukan ada di paling bawah halaman.
 */
class CompanyDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCompanyDetailBinding
    private val viewModel: CompanyDetailViewModel by viewModels()

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
                    renderCompany(state.company, state.taken, state.companyAddress)
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
                if (it.success) {
                    Toast.makeText(
                        this,
                        getString(R.string.choose_company_joined, it.companyName.ifBlank { "perusahaan" }),
                        Toast.LENGTH_LONG
                    ).show()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    Toast.makeText(this, it.message, Toast.LENGTH_LONG).show()
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
            AlertDialog.Builder(this)
                .setTitle(loaded.company.name.ifBlank { getString(R.string.company_detail_title) })
                .setMessage(
                    getString(
                        R.string.company_detail_confirm,
                        loaded.company.name.ifBlank { "-" },
                        loaded.company.scheduleSummary()
                    )
                )
                .setPositiveButton(R.string.company_detail_apply) { _, _ ->
                    viewModel.applyForCompany(uid, loaded.company.id)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        viewModel.load(companyId)
    }

    private fun renderCompany(company: Company, taken: Int, address: String? = null) {
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

        binding.applyButton.isEnabled = left > 0
    }

    companion object {
        const val EXTRA_COMPANY_ID = "extra_company_id"
    }
}
