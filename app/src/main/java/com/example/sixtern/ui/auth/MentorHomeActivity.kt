package com.example.sixtern.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.sixtern.R
import com.example.sixtern.data.model.Company
import com.example.sixtern.data.model.SixternUser
import com.example.sixtern.data.repository.AuthRepository
import com.example.sixtern.databinding.ActivityMentorHomeBinding
import com.example.sixtern.ui.profile.MentorProfileActivity
import kotlinx.coroutines.launch

/**
 * Beranda mentor yang simpel: sapaan + status ringkas + bottom nav (Beranda + Akun).
 * Detail perusahaan BOLEH diisi belakangan lewat tab Akun (MentorProfileActivity),
 * jadi mentor bebas eksplor Beranda dulu tanpa dipaksa isi form saat login.
 */
class MentorHomeViewModel(
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _company = MutableLiveData<Company?>()
    val company: LiveData<Company?> = _company

    private val _applicants = MutableLiveData<List<SixternUser>>(emptyList())
    val applicants: LiveData<List<SixternUser>> = _applicants

    fun load(uid: String) {
        viewModelScope.launch {
            val company = try {
                repository.getCompanyByMentor(uid)
            } catch (e: Exception) {
                null
            }
            _company.value = company
            _applicants.value = try {
                if (company == null) emptyList()
                else repository.getStudentsByCompany(company.id)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}

class MentorHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMentorHomeBinding
    private val viewModel: MentorHomeViewModel by viewModels()
    private lateinit var applicantAdapter: ApplicantAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMentorHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applicantAdapter = ApplicantAdapter()
        binding.applicantsRecycler.layoutManager = LinearLayoutManager(this)
        binding.applicantsRecycler.adapter = applicantAdapter

        val uid = intent.getStringExtra(LoginActivity.EXTRA_USER_ID).orEmpty()
        val name = intent.getStringExtra(LoginActivity.EXTRA_USER_NAME).orEmpty()

        binding.greetingText.text = if (name.isBlank()) {
            getString(R.string.mentor_home_greeting_fallback)
        } else {
            getString(R.string.mentor_home_greeting, name)
        }

        binding.mentorNav.itemIconTintList = null
        MentorNav.setup(binding.mentorNav, this, R.id.nav_home, uid, name)

        viewModel.company.observe(this) { company ->
            binding.companyStatusText.text = if (company == null) {
                getString(R.string.mentor_home_company_empty)
            } else {
                getString(
                    R.string.mentor_home_company_done,
                    company.name,
                    company.workDays.joinToString(", ")
                )
            }
            renderApplicants(
                company != null,
                viewModel.applicants.value.orEmpty()
            )
        }

        viewModel.applicants.observe(this) { applicants ->
            renderApplicants(
                viewModel.company.value != null,
                applicants.orEmpty()
            )
        }

        binding.companyButton.setOnClickListener {
            val intent = Intent(this, MentorProfileActivity::class.java)
            intent.putExtra(LoginActivity.EXTRA_USER_ID, uid)
            intent.putExtra(LoginActivity.EXTRA_USER_NAME, name)
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun onResume() {
        super.onResume()
        // Pengajuan baru masuk kapan saja -> muat ulang tiap kembali ke beranda.
        val uid = intent.getStringExtra(LoginActivity.EXTRA_USER_ID).orEmpty()
        if (uid.isNotBlank()) viewModel.load(uid)
    }

    /**
     * Kartu notifikasi tampil hanya bila ada pengaju; daftar disembunyikan
     * bila perusahaan belum diisi agar mentor paham harus lengkapi profil dulu.
     */
    private fun renderApplicants(hasCompany: Boolean, applicants: List<SixternUser>) {
        applicantAdapter.submitList(applicants)
        val count = applicants.size
        binding.applicantsNoticeCard.visibility =
            if (count > 0) View.VISIBLE else View.GONE
        if (count > 0) {
            binding.applicantsBadge.text = count.toString()
            binding.applicantsNoticeText.text =
                getString(R.string.mentor_applicants_notice, count)
        }
        binding.applicantsRecycler.visibility =
            if (hasCompany && count > 0) View.VISIBLE else View.GONE
        binding.applicantsEmptyText.visibility =
            if (hasCompany && count > 0) View.GONE else View.VISIBLE
        if (!hasCompany) {
            binding.applicantsEmptyText.text =
                getString(R.string.mentor_applicants_need_company)
        } else if (count == 0) {
            binding.applicantsEmptyText.text =
                getString(R.string.mentor_applicants_empty)
        }
    }
}
