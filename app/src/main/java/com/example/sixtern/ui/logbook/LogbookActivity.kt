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
        adapter = LogbookAdapter { logbook ->
            val intent = Intent(this, LogbookFormActivity::class.java)
            intent.putExtra(LogbookFormActivity.EXTRA_ID, logbook.id)
            intent.putExtra(LogbookFormActivity.EXTRA_TITLE, logbook.title)
            intent.putExtra(LogbookFormActivity.EXTRA_DESC, logbook.description)
            startActivity(intent)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.logbookNav.itemIconTintList = null
        StudentNav.setup(binding.logbookNav, this, R.id.nav_logbook, studentId, companyId)
        viewModel.init(studentId, companyId)
        setupTabs()
        binding.addButton.setOnClickListener {
            startActivity(Intent(this, LogbookFormActivity::class.java))
        }
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
        viewModel.logbooks.observe(this) { render() }
        viewModel.filter.observe(this) { render() }
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
