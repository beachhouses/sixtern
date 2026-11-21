package com.example.sixtern.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.sixtern.R
import com.example.sixtern.data.model.Attendance
import com.example.sixtern.databinding.ActivityStudentHomeBinding
import com.example.sixtern.ui.auth.LoginActivity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class StudentHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentHomeBinding
    private val viewModel: StudentHomeViewModel by viewModels()

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
        val uid = intent.getStringExtra(LoginActivity.EXTRA_USER_ID).orEmpty()
        val name = intent.getStringExtra(LoginActivity.EXTRA_USER_NAME).orEmpty()
        binding.greetingText.text = if (name.isBlank()) "Halo!" else "Halo, $name!"
        viewModel.load(uid)
        requestLocation()
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
    }

    private fun renderLoaded(state: HomeUiState.Loaded) {
        val user = state.user
        binding.nimText.text = user.nim ?: "-"
        binding.universityText.text = user.university + " - " + user.major
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
                binding.radiusStatus.text = getString(R.string.inside_radius)
                binding.radiusStatus.setTextColor(ContextCompat.getColor(this, R.color.sixtern_green))
            }
            false -> {
                binding.radiusStatus.text = getString(R.string.outside_radius)
                binding.radiusStatus.setTextColor(ContextCompat.getColor(this, R.color.sixtern_purple))
            }
            null -> {
                binding.radiusStatus.text = getString(R.string.checking_radius)
                binding.radiusStatus.setTextColor(ContextCompat.getColor(this, R.color.text_muted_on_dark))
            }
        }
        val hasLocation = state.currentLat != null && state.currentLng != null
        binding.checkInButton.isEnabled = hasLocation
        binding.checkOutButton.isEnabled = hasLocation
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
