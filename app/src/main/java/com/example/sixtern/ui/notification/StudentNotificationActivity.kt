package com.example.sixtern.ui.notification

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
import com.example.sixtern.data.model.Logbook
import com.example.sixtern.data.model.SixternUser
import com.example.sixtern.data.repository.InternshipRepository
import com.example.sixtern.databinding.ActivityStudentNotificationBinding
import com.example.sixtern.ui.auth.LoginActivity
import com.example.sixtern.ui.home.StudentNav
import com.example.sixtern.ui.logbook.LogbookNotifAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class StudentLogbookNotifViewModel(
    private val repository: InternshipRepository = InternshipRepository()
) : ViewModel() {

    /** Logbook yang sudah direview mentor (terbaru di atas), realtime. */
    private val _reviewed = MutableLiveData<List<Logbook>>(emptyList())
    val reviewed: LiveData<List<Logbook>> = _reviewed

    private var job: Job? = null

    fun load(studentId: String) {
        job?.cancel()
        if (studentId.isBlank()) return
        job = viewModelScope.launch {
            repository.observeLogbooksByStudent(studentId)
                .collectLatest { list ->
                    _reviewed.value = list.filter { it.reviewedAt > 0 }
                        .sortedByDescending { it.reviewedAt }
                }
        }
    }
}

/**
 * Halaman notifikasi mahasiswa: status pengajuan magang + hasil review
 * logbook (disetujui / perlu revisi beserta catatan mentor), realtime.
 */
class StudentNotificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentNotificationBinding
    private val logbookViewModel: StudentLogbookNotifViewModel by viewModels()
    private lateinit var logbookAdapter: LogbookNotifAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentNotificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uid = intent.getStringExtra(LoginActivity.EXTRA_USER_ID).orEmpty()
        val companyId = intent.getStringExtra(EXTRA_COMPANY_ID).orEmpty()
        val status = intent.getStringExtra(EXTRA_STATUS).orEmpty()
        val companyName = intent.getStringExtra(EXTRA_COMPANY_NAME).orEmpty()
        val reason = intent.getStringExtra(EXTRA_REASON).orEmpty()

        binding.studentNotifNav.itemIconTintList = null
        StudentNav.setup(binding.studentNotifNav, this, R.id.nav_notification, uid, companyId)
        // Halaman notifikasi dibuka -> catat waktu lihat & hapus badge.
        com.example.sixtern.util.NotificationPrefs.markSeen(this, uid)
        StudentNav.setBadge(binding.studentNotifNav, 0)

        logbookAdapter = LogbookNotifAdapter()
        binding.logbookNotifRecycler.layoutManager = LinearLayoutManager(this)
        binding.logbookNotifRecycler.adapter = logbookAdapter

        renderNotification(status, companyName, reason)

        logbookViewModel.reviewed.observe(this) { logbooks ->
            logbookAdapter.submitList(logbooks)
            val has = logbooks.isNotEmpty()
            binding.logbookNotifRecycler.visibility = if (has) View.VISIBLE else View.GONE
            binding.logbookNotifEmptyText.visibility = if (has) View.GONE else View.VISIBLE
        }

        if (uid.isNotBlank()) logbookViewModel.load(uid)
    }

    private fun renderNotification(status: String, companyName: String, reason: String) {
        val hasNotif = companyName.isNotBlank() && status.isNotBlank()
            && status != SixternUser.STATUS_PENDING

        if (!hasNotif) {
            // Belum ada notif atau masih pending tanpa perusahaan
            binding.notifEmptyGroup.visibility = View.VISIBLE
            binding.notifCardGroup.visibility = View.GONE

            // Jika pending dengan nama perusahaan, tampilkan kartu menunggu
            if (companyName.isNotBlank() && status == SixternUser.STATUS_PENDING) {
                binding.notifEmptyGroup.visibility = View.GONE
                binding.notifCardGroup.visibility = View.VISIBLE
                binding.notifCardIcon.text = "\u23F3"
                binding.notifCardTitle.text = getString(R.string.student_notif_pending_title)
                binding.notifCardBody.text = getString(R.string.student_notif_pending_body, companyName)
                binding.notifReasonCard.visibility = View.GONE
                binding.notifCardRoot.setBackgroundResource(R.drawable.bg_card_white)
            }
            return
        }

        binding.notifEmptyGroup.visibility = View.GONE
        binding.notifCardGroup.visibility = View.VISIBLE

        when (status) {
            SixternUser.STATUS_ACCEPTED -> {
                binding.notifCardRoot.setBackgroundResource(R.drawable.bg_notice_accepted)
                binding.notifCardIcon.text = "\u2713"
                binding.notifCardTitle.text = getString(R.string.student_notif_accepted_title)
                binding.notifCardBody.text = getString(R.string.student_notif_accepted_body, companyName)
                binding.notifReasonCard.visibility = View.GONE
            }
            SixternUser.STATUS_REJECTED -> {
                binding.notifCardRoot.setBackgroundResource(R.drawable.bg_notice_rejected)
                binding.notifCardIcon.text = "\u2717"
                binding.notifCardTitle.text = getString(R.string.student_notif_rejected_title)
                binding.notifCardBody.text = getString(R.string.student_notif_rejected_body, companyName)
                if (reason.isNotBlank()) {
                    binding.notifReasonCard.visibility = View.VISIBLE
                    binding.notifReasonText.text = getString(R.string.student_notif_rejected_reason, reason)
                } else {
                    binding.notifReasonCard.visibility = View.GONE
                }
            }
            else -> {
                binding.notifEmptyGroup.visibility = View.VISIBLE
                binding.notifCardGroup.visibility = View.GONE
            }
        }
    }

    companion object {
        const val EXTRA_COMPANY_ID = "extra_company_id"
        const val EXTRA_STATUS = "extra_status"
        const val EXTRA_COMPANY_NAME = "extra_company_name"
        const val EXTRA_REASON = "extra_reason"
    }
}
