package com.example.sixtern.ui.logbook

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.sixtern.databinding.ActivityLogbookFormBinding
import com.example.sixtern.ui.auth.LoginActivity

class LogbookFormActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogbookFormBinding
    private val viewModel: LogbookViewModel by viewModels()
    private var editingId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogbookFormBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val uid = intent.getStringExtra(LoginActivity.EXTRA_USER_ID).orEmpty()
        val companyId = intent.getStringExtra(LogbookActivity.EXTRA_COMPANY_ID).orEmpty()
        editingId = intent.getStringExtra(EXTRA_ID)
        binding.titleInput.setText(intent.getStringExtra(EXTRA_TITLE).orEmpty())
        binding.descInput.setText(intent.getStringExtra(EXTRA_DESC).orEmpty())
        viewModel.init(uid, companyId)
        binding.saveButton.setOnClickListener {
            viewModel.save(
                binding.titleInput.text.toString(),
                binding.descInput.text.toString(),
                editingId
            )
        }
        viewModel.saving.observe(this) { saving ->
            binding.loadingBar.visibility = if (saving) View.VISIBLE else View.GONE
            binding.saveButton.isEnabled = !saving
        }
        viewModel.message.observe(this) { message ->
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                viewModel.clearMessage()
                if (!viewModel.saving.value!!) finish()
            }
        }
    }

    companion object {
        const val EXTRA_ID = "extra_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_DESC = "extra_desc"
    }
}
