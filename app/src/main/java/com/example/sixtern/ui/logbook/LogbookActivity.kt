package com.example.sixtern.ui.logbook

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.sixtern.R
import com.example.sixtern.databinding.ActivityLogbookBinding
import com.example.sixtern.ui.auth.LoginActivity
import com.example.sixtern.ui.home.StudentNav
import com.google.android.material.tabs.TabLayout

class LogbookActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogbookBinding
    private val viewModel: LogbookViewModel by viewModels()
    private lateinit var adapter: LogbookAdapter
    private var studentId: String = ""
    private var companyId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogbookBinding.inflate(layoutInflater)
        setContentView(binding.root)
        studentId = intent.getStringExtra(LoginActivity.EXTRA_USER_ID).orEmpty()
        companyId = intent.getStringExtra(EXTRA_COMPANY_ID).orEmpty()
        adapter = LogbookAdapter(
            onFixClick = { logbook ->
                val intent = Intent(this, LogbookFormActivity::class.java)
                intent.putExtra(LoginActivity.EXTRA_USER_ID, studentId)
                intent.putExtra(EXTRA_COMPANY_ID, viewModel.currentCompanyId())
                intent.putExtra(LogbookFormActivity.EXTRA_ID, logbook.id)
                intent.putExtra(LogbookFormActivity.EXTRA_TITLE, logbook.title)
                intent.putExtra(LogbookFormActivity.EXTRA_DESC, logbook.description)
                // Catatan mentor terakhir ikut dibawa agar form menampilkannya (read-only).
                intent.putExtra(LogbookFormActivity.EXTRA_NOTES, logbook.mentorNotes.orEmpty())
                startActivity(intent)
            },
            onItemClick = { logbook ->
                // Klik kartu -> riwayat penilaian logbook ini.
                val intent = Intent(this, LogbookHistoryActivity::class.java)
                intent.putExtra(LogbookHistoryActivity.EXTRA_LOGBOOK_ID, logbook.id)
                startActivity(intent)
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.logbookNav.itemIconTintList = null
        StudentNav.setup(binding.logbookNav, this, R.id.nav_logbook, studentId, companyId)
        viewModel.init(studentId, companyId)
        setupTabs()
        binding.addButton.setOnClickListener {
            // Wajib membawa uid + companyId: tanpa ini form menganggap
            // mahasiswa belum punya tempat magang dan simpan ditolak.
            val intent = Intent(this, LogbookFormActivity::class.java)
            intent.putExtra(LoginActivity.EXTRA_USER_ID, studentId)
            intent.putExtra(EXTRA_COMPANY_ID, viewModel.currentCompanyId())
            startActivity(intent)
        }
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
        viewModel.logbooks.observe(this) { render() }
        viewModel.filter.observe(this) { render() }
        // Belum punya tempat magang -> kartu pesan; daftar/tab/FAB disembunyikan.
        viewModel.companyReady.observe(this) { ready ->
            if (ready == null) return@observe
            binding.noCompanyCard.visibility = if (ready) View.GONE else View.VISIBLE
            binding.tabLayout.visibility = if (ready) View.VISIBLE else View.GONE
            binding.swipeRefresh.visibility = if (ready) View.VISIBLE else View.GONE
            binding.addButton.visibility = if (ready) View.VISIBLE else View.GONE
            // Nav di-set ulang dengan companyId segar hasil cek Firestore.
            StudentNav.setup(
                binding.logbookNav, this, R.id.nav_logbook, studentId,
                viewModel.currentCompanyId()
            )
        }
        viewModel.message.observe(this) { message ->
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                viewModel.clearMessage()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun setupTabs() {
        val tabs = binding.tabLayout
        tabs.addTab(tabs.newTab().setText("Semua"))
        tabs.addTab(tabs.newTab().setText("Disetujui"))
        tabs.addTab(tabs.newTab().setText("Revisi"))
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.setFilter(
                    when (tab.position) {
                        1 -> LogbookViewModel.FILTER_APPROVED
                        2 -> LogbookViewModel.FILTER_REVISION
                        else -> LogbookViewModel.FILTER_ALL
                    }
                )
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun render() {
        binding.swipeRefresh.isRefreshing = false
        val list = viewModel.filtered()
        adapter.submit(list)
        binding.emptyText.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        val tabs = binding.tabLayout
        tabs.getTabAt(0)?.text = "Semua"
        tabs.getTabAt(1)?.text = "Disetujui (" + viewModel.countApproved() + ")"
        tabs.getTabAt(2)?.text = "Revisi (" + viewModel.countRevision() + ")"
    }

    companion object {
        const val EXTRA_COMPANY_ID = "extra_company_id"
    }
}
