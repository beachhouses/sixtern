package com.example.sixtern.ui.attendance

import android.os.Bundle
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
import com.example.sixtern.data.model.Attendance
import com.example.sixtern.data.repository.InternshipRepository
import com.example.sixtern.databinding.ActivityAttendanceReportBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ===================== ViewModel =====================

sealed class AttendanceReportState {
    data object Loading : AttendanceReportState()
    data class Loaded(val records: List<Attendance>) : AttendanceReportState()
    data class Error(val message: String) : AttendanceReportState()
}

class AttendanceReportViewModel(
    private val repo: InternshipRepository = InternshipRepository()
) : ViewModel() {

    private val _state = MutableLiveData<AttendanceReportState>(AttendanceReportState.Loading)
    val state: LiveData<AttendanceReportState> = _state

    /**
     * @param fromRefresh true saat dipicu pull-to-refresh: paksa baca server
     * (hindari cache basi) dan jangan ganti ke Loading agar konten lama tetap
     * tampil — spinner SwipeRefreshLayout sudah cukup sebagai indikator.
     */
    fun load(studentId: String, fromRefresh: Boolean = false) {
        if (!fromRefresh) _state.value = AttendanceReportState.Loading
        viewModelScope.launch {
            try {
                val records = repo.getAttendanceByStudent(studentId, forceServer = fromRefresh)
                _state.value = AttendanceReportState.Loaded(records)
            } catch (e: Exception) {
                _state.value = AttendanceReportState.Error(
                    e.message ?: "Gagal memuat data absensi"
                )
            }
        }
    }
}

// ===================== Adapter =====================

class AttendanceReportAdapter : ListAdapter<Attendance, AttendanceReportAdapter.VH>(
    object : DiffUtil.ItemCallback<Attendance>() {
        override fun areItemsTheSame(a: Attendance, b: Attendance) = a.id == b.id
        override fun areContentsTheSame(a: Attendance, b: Attendance) = a == b
    }
) {
    private val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))

    /** 272 -> "4 jam 32 menit"; 45 -> "45 menit"; 60 -> "1 jam". */
    private fun formatDuration(totalMinutes: Int): String {
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "$hours jam $minutes menit"
            hours > 0 -> "$hours jam"
            else -> "$minutes menit"
        }
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val typeBadge: TextView = view.findViewById(R.id.attendanceTypeBadge)
        val timeText: TextView = view.findViewById(R.id.attendanceTime)
        val lateText: TextView = view.findViewById(R.id.attendanceLateStatus)
        val radiusText: TextView = view.findViewById(R.id.attendanceRadiusStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attendance_report, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val ctx = holder.itemView.context

        // Tipe absen
        if (item.type == Attendance.TYPE_IN) {
            holder.typeBadge.text = ctx.getString(R.string.attendance_type_in)
            holder.typeBadge.setBackgroundResource(R.drawable.bg_button_green)
        } else {
            holder.typeBadge.text = ctx.getString(R.string.attendance_type_out)
            holder.typeBadge.setBackgroundResource(R.drawable.bg_login_button)
        }

        // Waktu
        val ts = item.timestamp?.toDate() ?: Date(0)
        holder.timeText.text = sdf.format(ts)

        // Status telat (hanya relevan untuk absen masuk)
        if (item.type == Attendance.TYPE_IN) {
            if (item.isLate) {
                holder.lateText.text = ctx.getString(R.string.attendance_late, formatDuration(item.lateMinutes))
                holder.lateText.setTextColor(
                    ContextCompat.getColor(ctx, R.color.chip_rejected_bg)
                )
            } else {
                holder.lateText.text = ctx.getString(R.string.attendance_ontime)
                holder.lateText.setTextColor(
                    ContextCompat.getColor(ctx, R.color.sixtern_green)
                )
            }
            holder.lateText.visibility = View.VISIBLE
        } else {
            holder.lateText.visibility = View.GONE
        }

        // Status radius
        if (item.isValid) {
            holder.radiusText.text = ctx.getString(R.string.attendance_in_radius)
            holder.radiusText.setTextColor(
                ContextCompat.getColor(ctx, R.color.sixtern_green)
            )
        } else {
            holder.radiusText.text = ctx.getString(R.string.attendance_out_radius)
            holder.radiusText.setTextColor(
                ContextCompat.getColor(ctx, R.color.chip_rejected_bg)
            )
        }
    }
}

// ===================== Activity =====================

/**
 * Laporan absensi satu mahasiswa — dibuka dari tab Peserta mentor.
 * Menampilkan tiap record: tipe (masuk/pulang), waktu, status telat, dan radius.
 */
class AttendanceReportActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_STUDENT_ID = "extra_student_id"
        const val EXTRA_STUDENT_NAME = "extra_student_name"
    }

    private lateinit var binding: ActivityAttendanceReportBinding
    private val viewModel: AttendanceReportViewModel by viewModels()
    private lateinit var adapter: AttendanceReportAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAttendanceReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val studentId = intent.getStringExtra(EXTRA_STUDENT_ID).orEmpty()
        val studentName = intent.getStringExtra(EXTRA_STUDENT_NAME).orEmpty()

        binding.reportTitle.text = getString(R.string.attendance_report_title)
        binding.reportStudentName.text = studentName.ifBlank { "-" }

        binding.backButton.setOnClickListener { finish() }

        binding.swipeRefresh.setColorSchemeResources(R.color.login_button, R.color.sixtern_green)
        binding.swipeRefresh.setOnRefreshListener {
            if (studentId.isNotBlank()) viewModel.load(studentId, fromRefresh = true)
        }

        adapter = AttendanceReportAdapter()
        binding.attendanceRecycler.layoutManager = LinearLayoutManager(this)
        binding.attendanceRecycler.adapter = adapter

        viewModel.state.observe(this) { state ->
            when (state) {
                is AttendanceReportState.Loading -> {
                    binding.loadingBar.visibility = View.VISIBLE
                    binding.emptyState.visibility = View.GONE
                    binding.attendanceRecycler.visibility = View.GONE
                }
                is AttendanceReportState.Loaded -> {
                    binding.loadingBar.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                    if (state.records.isEmpty()) {
                        binding.emptyState.visibility = View.VISIBLE
                        binding.attendanceRecycler.visibility = View.GONE
                        binding.summaryCard.visibility = View.GONE
                    } else {
                        binding.emptyState.visibility = View.GONE
                        binding.attendanceRecycler.visibility = View.VISIBLE
                        adapter.submitList(state.records)
                        // Hitung ringkasan
                        val inRecords = state.records.filter { it.type == Attendance.TYPE_IN }
                        val outRecords = state.records.filter { it.type == Attendance.TYPE_OUT }
                        val lateCount = inRecords.count { it.isLate }
                        val invalidCount = state.records.count { !it.isValid }
                        binding.summaryIn.text = getString(R.string.attendance_summary_in, inRecords.size)
                        binding.summaryOut.text = getString(R.string.attendance_summary_out, outRecords.size)
                        binding.summaryLate.text = getString(R.string.attendance_summary_late, lateCount)
                        binding.summaryInvalid.text = getString(R.string.attendance_summary_invalid, invalidCount)
                        binding.summaryCard.visibility = View.VISIBLE
                    }
                }
                is AttendanceReportState.Error -> {
                    binding.loadingBar.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                    binding.emptyState.visibility = View.VISIBLE
                    binding.attendanceRecycler.visibility = View.GONE
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        if (studentId.isNotBlank()) viewModel.load(studentId)
    }
}
