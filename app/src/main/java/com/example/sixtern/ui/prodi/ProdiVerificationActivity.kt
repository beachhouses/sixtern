package com.example.sixtern.ui.prodi

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.sixtern.R
import com.example.sixtern.data.model.Company
import com.example.sixtern.data.model.SixternUser
import com.example.sixtern.data.repository.AuthRepository
import com.example.sixtern.databinding.ActivityProdiVerificationBinding
import com.example.sixtern.databinding.ItemVerifyCompanyBinding
import com.example.sixtern.databinding.ItemVerifyUserBinding
import com.example.sixtern.ui.auth.LoginActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// ===================== Adapter: verifikasi user =====================

class VerifyUserAdapter(
    private val onAccept: (SixternUser) -> Unit,
    private val onReject: (SixternUser) -> Unit
) : ListAdapter<SixternUser, VerifyUserAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemVerifyUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position), onAccept, onReject)

    class ViewHolder(private val binding: ItemVerifyUserBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            user: SixternUser,
            onAccept: (SixternUser) -> Unit,
            onReject: (SixternUser) -> Unit
        ) {
            binding.verifyAvatar.text = user.name.firstOrNull()?.uppercase() ?: "?"
            binding.verifyName.text = user.name.ifBlank { "-" }
            val nim = user.nim?.takeIf { it.isNotBlank() }
            binding.verifyDetail.text = listOfNotNull(nim, user.email.takeIf { it.isNotBlank() })
                .joinToString(" • ").ifBlank { "-" }

            val context = binding.root.context
            val (label, colorRes) = when (user.accountStatus) {
                SixternUser.ACCOUNT_VERIFIED ->
                    context.getString(R.string.prodi_status_verified) to R.color.chip_accepted_bg
                SixternUser.ACCOUNT_REJECTED ->
                    context.getString(R.string.prodi_status_rejected) to R.color.chip_rejected_bg
                else ->
                    context.getString(R.string.prodi_status_pending) to R.color.chip_pending_bg
            }
            binding.verifyStatus.text = label
            binding.verifyStatus.background.setTint(
                ContextCompat.getColor(context, colorRes)
            )

            when (user.accountStatus) {
                SixternUser.ACCOUNT_PENDING -> {
                    binding.verifyActions.visibility = View.VISIBLE
                    binding.acceptButton.visibility = View.VISIBLE
                    binding.rejectButton.visibility = View.VISIBLE
                }
                SixternUser.ACCOUNT_REJECTED -> {
                    // Prodi berubah pikiran: tawarkan terima ulang.
                    binding.verifyActions.visibility = View.VISIBLE
                    binding.acceptButton.visibility = View.VISIBLE
                    binding.rejectButton.visibility = View.GONE
                }
                else -> {
                    binding.verifyActions.visibility = View.GONE
                }
            }
            binding.acceptButton.setOnClickListener { onAccept(user) }
            binding.rejectButton.setOnClickListener { onReject(user) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SixternUser>() {
            override fun areItemsTheSame(old: SixternUser, new: SixternUser) = old.uid == new.uid
            override fun areContentsTheSame(old: SixternUser, new: SixternUser) = old == new
        }
    }
}

// ===================== Adapter: verifikasi perusahaan =====================

class VerifyCompanyAdapter(
    private val onVerify: (Company) -> Unit
) : ListAdapter<Company, VerifyCompanyAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemVerifyCompanyBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position), onVerify)

    class ViewHolder(private val binding: ItemVerifyCompanyBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(company: Company, onVerify: (Company) -> Unit) {
            binding.companyName.text = company.name.ifBlank { "-" }
            binding.companyDetail.text =
                company.description.ifBlank { company.workDays.joinToString(", ") }

            val context = binding.root.context
            val (label, colorRes) = if (company.verified) {
                context.getString(R.string.prodi_status_verified) to R.color.chip_accepted_bg
            } else {
                context.getString(R.string.prodi_status_pending) to R.color.chip_pending_bg
            }
            binding.companyStatus.text = label
            binding.companyStatus.background.setTint(ContextCompat.getColor(context, colorRes))

            // Logo Base64 kecil bila ada; kalau kosong pakai ikon default.
            val logo = decodeLogo(company.logoUrl)
            if (logo != null) {
                binding.companyLogo.setImageBitmap(logo)
                binding.companyLogo.setPadding(0, 0, 0, 0)
            } else {
                binding.companyLogo.setImageResource(R.drawable.ic_nav_company)
                val p = (10 * binding.root.resources.displayMetrics.density).toInt()
                binding.companyLogo.setPadding(p, p, p, p)
            }

            binding.companyActions.visibility = if (company.verified) View.GONE else View.VISIBLE
            binding.verifyButton.setOnClickListener { onVerify(company) }
        }

        private fun decodeLogo(dataUri: String): android.graphics.Bitmap? = try {
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

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Company>() {
            override fun areItemsTheSame(old: Company, new: Company) = old.id == new.id
            override fun areContentsTheSame(old: Company, new: Company) = old == new
        }
    }
}

// ===================== ViewModel =====================

/** Kategori daftar verifikasi yang sedang aktif. */
enum class VerifyTab { STUDENTS, MENTORS, COMPANIES }

class ProdiVerificationViewModel(
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {

    val students = MutableLiveData<List<SixternUser>>(emptyList())
    val mentors = MutableLiveData<List<SixternUser>>(emptyList())
    val companies = MutableLiveData<List<Company>>(emptyList())

    private val _result = MutableLiveData<String?>()
    val result: LiveData<String?> = _result

    private var studentsJob: Job? = null
    private var mentorsJob: Job? = null
    private var companiesJob: Job? = null

    fun load() {
        studentsJob?.cancel()
        mentorsJob?.cancel()
        companiesJob?.cancel()
        studentsJob = viewModelScope.launch {
            repository.observeUsersByRole(SixternUser.ROLE_MAHASISWA)
                .collectLatest { students.value = it }
        }
        mentorsJob = viewModelScope.launch {
            repository.observeUsersByRole(SixternUser.ROLE_MENTOR)
                .collectLatest { mentors.value = it }
        }
        companiesJob = viewModelScope.launch {
            repository.observeCompaniesRealtime().collectLatest { companies.value = it }
        }
    }

    fun acceptUser(uid: String) {
        viewModelScope.launch {
            try {
                repository.updateAccountStatus(uid, SixternUser.ACCOUNT_VERIFIED)
                _result.value = "USER_VERIFIED"
            } catch (e: Exception) {
                _result.value = e.message ?: "Gagal memverifikasi akun"
            }
        }
    }

    fun rejectUser(uid: String) {
        viewModelScope.launch {
            try {
                repository.updateAccountStatus(uid, SixternUser.ACCOUNT_REJECTED)
                _result.value = "USER_REJECTED"
            } catch (e: Exception) {
                _result.value = e.message ?: "Gagal menolak akun"
            }
        }
    }

    fun verifyCompany(companyId: String) {
        viewModelScope.launch {
            try {
                repository.updateCompanyVerified(companyId, true)
                _result.value = "COMPANY_VERIFIED"
            } catch (e: Exception) {
                _result.value = e.message ?: "Gagal memverifikasi perusahaan"
            }
        }
    }

    fun clearResult() {
        _result.value = null
    }
}

// ===================== Activity =====================

/**
 * Verifikasi pendaftaran mahasiswa, mentor, dan perusahaan oleh prodi.
 * Daftar realtime; aksi per baris langsung menyimpan ke Firestore.
 */
class ProdiVerificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProdiVerificationBinding
    private val viewModel: ProdiVerificationViewModel by viewModels()

    private lateinit var userAdapter: VerifyUserAdapter
    private lateinit var companyAdapter: VerifyCompanyAdapter

    private var activeTab: VerifyTab = VerifyTab.STUDENTS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProdiVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uid = intent.getStringExtra(LoginActivity.EXTRA_USER_ID).orEmpty()
        val name = intent.getStringExtra(LoginActivity.EXTRA_USER_NAME).orEmpty()

        userAdapter = VerifyUserAdapter(
            onAccept = { viewModel.acceptUser(it.uid) },
            onReject = { showRejectConfirm(it) }
        )
        companyAdapter = VerifyCompanyAdapter(
            onVerify = { viewModel.verifyCompany(it.id) }
        )
        binding.verifyRecycler.layoutManager = LinearLayoutManager(this)

        binding.prodiNav.itemIconTintList = null
        ProdiNav.setup(binding.prodiNav, this, R.id.nav_verify, uid, name)

        binding.chipStudents.setOnClickListener { activeTab = VerifyTab.STUDENTS; render() }
        binding.chipMentors.setOnClickListener { activeTab = VerifyTab.MENTORS; render() }
        binding.chipCompanies.setOnClickListener { activeTab = VerifyTab.COMPANIES; render() }

        viewModel.result.observe(this) { msg ->
            if (msg != null) {
                val text = when (msg) {
                    "USER_VERIFIED" -> getString(R.string.prodi_verify_user_success)
                    "USER_REJECTED" -> getString(R.string.prodi_verify_user_rejected)
                    "COMPANY_VERIFIED" -> getString(R.string.prodi_verify_company_success)
                    else -> msg
                }
                Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
                viewModel.clearResult()
            }
        }

        viewModel.students.observe(this) { render() }
        viewModel.mentors.observe(this) { render() }
        viewModel.companies.observe(this) { render() }

        viewModel.load()
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }

    private fun render() {
        val students = viewModel.students.value.orEmpty()
        val mentors = viewModel.mentors.value.orEmpty()
        val companies = viewModel.companies.value.orEmpty()

        // Chip: label + jumlah menunggu.
        fun style(chip: TextView, label: String, pendingCount: Int, active: Boolean) {
            chip.text = "$label ($pendingCount)"
            chip.setBackgroundResource(
                if (active) R.drawable.bg_login_button else R.drawable.bg_login_input
            )
            chip.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (active) R.color.login_button_text else R.color.login_text_secondary
                )
            )
        }
        style(
            binding.chipStudents, getString(R.string.prodi_filter_students),
            students.count { it.accountStatus == SixternUser.ACCOUNT_PENDING },
            activeTab == VerifyTab.STUDENTS
        )
        style(
            binding.chipMentors, getString(R.string.prodi_filter_mentors),
            mentors.count { it.accountStatus == SixternUser.ACCOUNT_PENDING },
            activeTab == VerifyTab.MENTORS
        )
        style(
            binding.chipCompanies, getString(R.string.prodi_filter_companies),
            companies.count { !it.verified },
            activeTab == VerifyTab.COMPANIES
        )

        // Urutkan: yang menunggu di atas.
        when (activeTab) {
            VerifyTab.STUDENTS -> {
                binding.verifyRecycler.adapter = userAdapter
                userAdapter.submitList(students.sortedBy { it.accountStatus != SixternUser.ACCOUNT_PENDING })
            }
            VerifyTab.MENTORS -> {
                binding.verifyRecycler.adapter = userAdapter
                userAdapter.submitList(mentors.sortedBy { it.accountStatus != SixternUser.ACCOUNT_PENDING })
            }
            VerifyTab.COMPANIES -> {
                binding.verifyRecycler.adapter = companyAdapter
                companyAdapter.submitList(companies.sortedBy { it.verified })
            }
        }

        val empty = when (activeTab) {
            VerifyTab.STUDENTS -> students.isEmpty()
            VerifyTab.MENTORS -> mentors.isEmpty()
            VerifyTab.COMPANIES -> companies.isEmpty()
        }
        binding.emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        binding.verifyRecycler.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun showRejectConfirm(user: SixternUser) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.participants_reject_title))
            .setMessage(getString(R.string.prodi_reject_user_msg, user.name))
            .setPositiveButton(getString(R.string.participants_reject)) { _, _ ->
                viewModel.rejectUser(user.uid)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}
