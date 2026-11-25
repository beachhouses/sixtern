package com.example.sixtern.ui.evaluation

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.sixtern.R
import com.example.sixtern.databinding.ActivityEvaluationBinding
import com.example.sixtern.ui.auth.LoginActivity
import com.example.sixtern.ui.home.StudentNav

class EvaluationActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEvaluationBinding
    private val viewModel: EvaluationViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEvaluationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val uid = intent.getStringExtra(LoginActivity.EXTRA_USER_ID).orEmpty()
        viewModel.load(uid)
        binding.evalNav.itemIconTintList = null
        StudentNav.setup(binding.evalNav, this, R.id.nav_evaluation, uid)
        viewModel.companyId.observe(this) { companyId ->
            StudentNav.setup(binding.evalNav, this, R.id.nav_evaluation, uid, companyId)
        }
        viewModel.evaluation.observe(this) { evaluation ->
            if (evaluation != null) {
                binding.totalText.text = evaluation.totalScore.toString()
                binding.mentorText.text = "Pembimbing lapangan: " + evaluation.mentorScore
                binding.supervisorText.text = "Dosen pembimbing: " + evaluation.supervisorScore
                binding.attendanceText.text = "Presensi dan logbook: " + evaluation.attendanceLogbookScore
            }
        }
        viewModel.userName.observe(this) { name -> binding.nameText.text = name }
        viewModel.message.observe(this) { message ->
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                viewModel.clearMessage()
            }
        }
        binding.downloadButton.setOnClickListener { viewModel.downloadPdf(this) }
        binding.submitButton.setOnClickListener {
            Toast.makeText(this, "Dokumen dikirim ke prodi", Toast.LENGTH_SHORT).show()
        }
    }
}
