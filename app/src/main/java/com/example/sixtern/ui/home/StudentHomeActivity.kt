package com.example.sixtern.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.sixtern.R
import com.example.sixtern.data.model.Attendance
import com.example.sixtern.data.model.Company
import com.example.sixtern.databinding.ActivityStudentHomeBinding
import com.example.sixtern.ui.auth.LoginActivity
import com.example.sixtern.ui.evaluation.EvaluationActivity
import com.example.sixtern.ui.logbook.LogbookActivity
import com.example.sixtern.ui.profile.ProfileActivity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class StudentHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentHomeBinding
    private val viewModel: StudentHomeViewModel by viewModels()
    private lateinit var companyAdapter: CompanyPickAdapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) {
            fetchLocation()
        } else {
            Toast.makeText(this, getString(R.string.location_denied), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        companyAdapter = CompanyPickAdapter { company -> confirmAndJoin(company) }
        binding.companyRecycler.layoutManager = LinearLayoutManager(this)
        binding.companyRecycler.adapter = companyAdapter
        val uid = intent.getStringExtra(LoginActivity.EXTRA_USER_ID).orEmpty()
        val name = intent.getStringExtra(LoginActivity.EXTRA_USER_NAME).orEmpty()
        binding.greetingText.text = if (name.isBlank()) "Halo!" else "Halo, $name!"
        viewModel.load(uid)
        requestLocation()
        binding.homeMenuBar.itemIconTintList = null
        viewModel.state.observe(this) { state ->
            if (state is HomeUiState.Loaded) {
                StudentNav.setup(
                    binding.homeMenuBar, this, R.id.nav_home, uid, state.user.companyId.orEmpty()
                )
            }
        }
        StudentNav.setup(binding.homeMenuBar, this, R.id.nav_home, uid)
        binding.quickLogbook.setOnClickListener {
            val intent = Intent(this, LogbookActivity::class.java)
            intent.putExtra(LoginActivity.EXTRA_USER_ID, currentUid())
            intent.putExtra(LogbookActivity.EXTRA_COMPANY_ID, currentCompanyId())
            startActivity(intent)
        }
        binding.quickEval.setOnClickListener {
            val intent = Intent(this, EvaluationActivity::class.java)
            intent.putExtra(LoginActivity.EXTRA_USER_ID, currentUid())
            startActivity(intent)
        }
        binding.quickProfile.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            intent.putExtra(LoginActivity.EXTRA_USER_ID, currentUid())
            startActivity(intent)
        }
        binding.checkInButton.setOnClickListener {
            viewModel.recordAttendance(Attendance.TYPE_IN)
        }
        binding.checkOutButton.setOnClickListener {
            viewModel.recordAttendance(Attendance.TYPE_OUT)
        }
        viewModel.state.observe(this) { state ->
            when (state) {
                is HomeUiState.Idle -> Unit
                is HomeUiState.Loading -> binding.loadingBar.visibility = View.VISIBLE
                is HomeUiState.Loaded -> {
                    binding.loadingBar.visibility = View.GONE
                    renderLoaded(state)
                }
                is HomeUiState.Error -> {
                    binding.loadingBar.visibility = View.GONE
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
            }
        }
        viewModel.attendanceResult.observe(this) { attendance ->
            if (attendance != null) {
                val message = if (attendance.isValid) {
                    getString(R.string.attendance_valid, attendance.type)
                } else {
                    getString(R.string.attendance_outside)
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                viewModel.clearAttendanceResult()
            }
        }
        viewModel.joinResult.observe(this) { name ->
            if (name != null) {
                Toast.makeText(
                    this,
                    getString(R.string.choose_company_joined, name.ifBlank { "perusahaan" }),
                    Toast.LENGTH_LONG
                ).show()
                viewModel.clearJoinResult()
            }
        }
        viewModel.joining.observe(this) { joining ->
            binding.joiningBar.visibility = if (joining) View.VISIBLE else View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        val uid = currentUid()
        if (uid.isNotBlank()) viewModel.load(uid)
    }

    private fun currentUid(): String =
        intent.getStringExtra(LoginActivity.EXTRA_USER_ID).orEmpty()

    private fun currentCompanyId(): String {
        val state = viewModel.state.value
        return if (state is HomeUiState.Loaded) state.user.companyId.orEmpty() else ""
    }

    private fun renderLoaded(state: HomeUiState.Loaded) {
        val user = state.user
        binding.avatarText.text = user.name.firstOrNull()?.uppercase() ?: "S"
        binding.nimText.text = user.nim ?: "-"
        binding.universityText.text = user.university + " - " + user.major
        if (state.hasCompany) {
            // MODE 1: sudah magang -> dashboard penuh.
            binding.dashboardGroup.visibility = View.VISIBLE
            binding.companyPickerGroup.visibility = View.GONE
            renderDashboard(state)
        } else {
            // MODE 2: belum memilih -> daftar perusahaan yang bisa dipilih.
            binding.dashboardGroup.visibility = View.GONE
            binding.companyPickerGroup.visibility = View.VISIBLE
            companyAdapter.submitList(state.availableCompanies)
            binding.companyEmptyText.visibility =
                if (state.availableCompanies.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun renderDashboard(state: HomeUiState.Loaded) {
        val company = state.company
        if (company != null) {
            binding.companyText.text = company.name
            binding.scheduleText.text = company.startTime + " - " + company.endTime
        } else {
            binding.companyText.text = getString(R.string.no_company)
            binding.scheduleText.text = "-"
        }
        binding.attendanceCountText.text = state.attendanceCount.toString()
        val score = state.evaluation?.mentorScore
        binding.mentorScoreText.text = if (score != null) score.toString() else "-"
        when (state.insideRadius) {
            true -> {
                binding.radiusDot.setBackgroundResource(R.drawable.bg_dot_green)
                binding.radiusStatus.text = getString(R.string.inside_radius)
                binding.radiusStatus.setTextColor(ContextCompat.getColor(this, R.color.sixtern_green))
            }
            false -> {
                binding.radiusDot.setBackgroundResource(R.drawable.bg_dot_plum)
                binding.radiusStatus.text = getString(R.string.outside_radius)
                binding.radiusStatus.setTextColor(ContextCompat.getColor(this, R.color.login_button))
            }
            null -> {
                binding.radiusDot.setBackgroundResource(R.drawable.bg_dot_grey)
                binding.radiusStatus.text = getString(R.string.checking_radius)
                binding.radiusStatus.setTextColor(ContextCompat.getColor(this, R.color.login_text_secondary))
            }
        }
        val hasLocation = state.currentLat != null && state.currentLng != null
        binding.checkInButton.isEnabled = hasLocation
        binding.checkOutButton.isEnabled = hasLocation
    }

    private fun confirmAndJoin(company: Company) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(company.name.ifBlank { getString(R.string.choose_company_title) })
            .setMessage(
                getString(
                    R.string.choose_company_schedule,
                    company.workDays.filter { it.isNotBlank() }.joinToString(", ").ifBlank { "-" },
                    company.startTime.ifBlank { "-" },
                    company.endTime.ifBlank { "-" }
                )
            )
            .setPositiveButton(R.string.choose_company_join) { _, _ ->
                viewModel.joinCompany(company.id)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun requestLocation() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            fetchLocation()
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    private fun fetchLocation() {
        try {
            val client = LocationServices.getFusedLocationProviderClient(this)
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        viewModel.updateLocation(location.latitude, location.longitude)
                    }
                }
        } catch (e: SecurityException) {
            Toast.makeText(this, getString(R.string.location_denied), Toast.LENGTH_SHORT).show()
        }
    }
}
