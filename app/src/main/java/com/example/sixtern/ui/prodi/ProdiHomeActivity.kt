package com.example.sixtern.ui.prodi

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sixtern.R
import com.example.sixtern.data.model.Company
import com.example.sixtern.data.model.SixternUser
import com.example.sixtern.data.repository.AuthRepository
import com.example.sixtern.databinding.ActivityProdiHomeBinding
import com.example.sixtern.ui.auth.LoginActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Ringkasan statistik untuk beranda prodi. */
data class ProdiStats(
    val studentsActive: Int = 0,
    val studentsPending: Int = 0,
    val mentorsActive: Int = 0,
    val mentorsPending: Int = 0,
    val companiesActive: Int = 0,
    val companiesPending: Int = 0
)

class ProdiHomeViewModel(
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _stats = MutableLiveData(ProdiStats())
    val stats: LiveData<ProdiStats> = _stats

    private val _loading = MutableLiveData(true)
    val loading: LiveData<Boolean> = _loading

    private var studentsJob: Job? = null
    private var mentorsJob: Job? = null
    private var companiesJob: Job? = null

    private var students: List<SixternUser> = emptyList()
    private var mentors: List<SixternUser> = emptyList()
    private var companies: List<Company> = emptyList()

    fun load() {
        _loading.value = true
        studentsJob?.cancel()
        mentorsJob?.cancel()
        companiesJob?.cancel()

        studentsJob = viewModelScope.launch {
            repository.observeUsersByRole(SixternUser.ROLE_MAHASISWA).collectLatest {
                students = it
                publish()
            }
        }
        mentorsJob = viewModelScope.launch {
            repository.observeUsersByRole(SixternUser.ROLE_MENTOR).collectLatest {
                mentors = it
                publish()
            }
        }
        companiesJob = viewModelScope.launch {
            repository.observeCompaniesRealtime().collectLatest {
                companies = it
                publish()
            }
        }
    }

    private fun publish() {
        _loading.value = false
        _stats.value = ProdiStats(
            studentsActive = students.count { it.accountStatus == SixternUser.ACCOUNT_VERIFIED },
            studentsPending = students.count { it.accountStatus == SixternUser.ACCOUNT_PENDING },
            mentorsActive = mentors.count { it.accountStatus == SixternUser.ACCOUNT_VERIFIED },
            mentorsPending = mentors.count { it.accountStatus == SixternUser.ACCOUNT_PENDING },
            companiesActive = companies.count { it.verified },
            companiesPending = companies.count { !it.verified }
        )
    }
}

/**
 * Beranda prodi (pemilik aplikasi): statistik mahasiswa, mentor,
 * dan perusahaan — semuanya realtime.
 */
class ProdiHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProdiHomeBinding
    private val viewModel: ProdiHomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProdiHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uid = intent.getStringExtra(LoginActivity.EXTRA_USER_ID).orEmpty()
        val name = intent.getStringExtra(LoginActivity.EXTRA_USER_NAME).orEmpty()
        if (name.isNotBlank()) {
            binding.greetingText.text = getString(R.string.prodi_home_greeting, name)
        }

        binding.prodiNav.itemIconTintList = null
        ProdiNav.setup(binding.prodiNav, this, R.id.nav_home, uid, name)

        viewModel.loading.observe(this) { loading ->
            binding.loadingBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.stats.observe(this) { stats ->
            binding.studentsActiveCount.text = stats.studentsActive.toString()
            binding.studentsPendingCount.text = stats.studentsPending.toString()
            binding.mentorsActiveCount.text = stats.mentorsActive.toString()
            binding.mentorsPendingCount.text = stats.mentorsPending.toString()
            binding.companiesActiveCount.text = stats.companiesActive.toString()
            binding.companiesPendingCount.text = stats.companiesPending.toString()
        }

        viewModel.load()
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }
}
