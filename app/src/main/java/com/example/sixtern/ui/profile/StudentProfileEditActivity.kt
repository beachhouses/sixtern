package com.example.sixtern.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sixtern.R
import com.example.sixtern.data.model.SixternUser
import com.example.sixtern.data.repository.AuthRepository
import com.example.sixtern.databinding.ActivityStudentProfileEditBinding
import com.example.sixtern.ui.auth.LoginActivity
import kotlinx.coroutines.launch

class StudentProfileEditViewModel(
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _user = MutableLiveData<SixternUser?>()
    val user: LiveData<SixternUser?> = _user

    private val _saving = MutableLiveData(false)
    val saving: LiveData<Boolean> = _saving

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    fun load(uid: String) {
        viewModelScope.launch {
            _user.value = try { repository.getUser(uid) } catch (e: Exception) { null }
        }
    }

    fun save(
        uid: String,
        bio: String,
        age: String,
        phone: String,
        skillsRaw: String,
        portfolioUrl: String,
        cvUrl: String = ""
    ) {
        val skills = skillsRaw.split(",", ";", "\n")
            .map { it.trim() }.filter { it.isNotEmpty() }
        viewModelScope.launch {
            _saving.value = true
            try {
                repository.updateStudentProfile(
                    uid = uid,
                    bio = bio.trim(),
                    age = age.trim(),
                    phone = phone.trim(),
                    skills = skills,
                    portfolioUrl = portfolioUrl.trim(),
                    cvUrl = cvUrl.trim()
                )
                _message.value = "Profil berhasil disimpan"
            } catch (e: Exception) {
                _message.value = "Gagal menyimpan: ${e.message}"
            }
            _saving.value = false
        }
    }

    fun clearMessage() { _message.value = null }
}

class StudentProfileEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentProfileEditBinding
    private val viewModel: StudentProfileEditViewModel by viewModels()
    /** URI file PDF yang dipilih user (belum disimpan ke Firestore). */
    private var selectedCvUri: Uri? = null

    private val pickPdf = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        selectedCvUri = uri
        // Tampilkan nama file sebagai konfirmasi
        val name = getFileName(uri)
        binding.cvFileNameText.text = name
        binding.cvFileNameText.visibility = View.VISIBLE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentProfileEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uid = intent.getStringExtra(LoginActivity.EXTRA_USER_ID).orEmpty()

        binding.backButton.setOnClickListener { finish() }

        viewModel.message.observe(this) { msg ->
            if (msg != null) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                viewModel.clearMessage()
                if (msg.contains("berhasil")) finish()
            }
        }

        viewModel.saving.observe(this) { saving ->
            binding.saveButton.isEnabled = !saving
            binding.loadingIndicator.visibility = if (saving) View.VISIBLE else View.GONE
        }

        viewModel.user.observe(this) { user ->
            if (user == null) return@observe
            binding.bioInput.setText(user.bio)
            binding.ageInput.setText(user.age)
            binding.phoneInput.setText(user.phone)
            binding.skillsInput.setText(user.skills.joinToString(", "))
            binding.portfolioInput.setText(user.portfolioUrl)
            // Tampilkan status CV yang sudah ada
            if (user.cvUrl.isNotBlank()) {
                binding.cvStatusText.text = getString(R.string.cv_uploaded)
                binding.cvStatusText.visibility = View.VISIBLE
            } else {
                binding.cvStatusText.text = getString(R.string.cv_not_uploaded)
                binding.cvStatusText.visibility = View.VISIBLE
            }
        }

        binding.cvUploadButton.setOnClickListener {
            pickPdf.launch("application/pdf")
        }

        binding.saveButton.setOnClickListener {
            // cvUrl: gunakan URI yang baru dipilih (sebagai string URI lokal),
            // atau pertahankan cvUrl lama dari Firestore.
            val existingCvUrl = viewModel.user.value?.cvUrl.orEmpty()
            val cvUrl = selectedCvUri?.toString() ?: existingCvUrl
            viewModel.save(
                uid = uid,
                bio = binding.bioInput.text?.toString().orEmpty(),
                age = binding.ageInput.text?.toString().orEmpty(),
                phone = binding.phoneInput.text?.toString().orEmpty(),
                skillsRaw = binding.skillsInput.text?.toString().orEmpty(),
                portfolioUrl = binding.portfolioInput.text?.toString().orEmpty(),
                cvUrl = cvUrl
            )
        }

        if (uid.isNotBlank()) viewModel.load(uid)
    }

    private fun getFileName(uri: Uri): String {
        var name = "cv.pdf"
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) name = cursor.getString(idx)
            }
        } catch (_: Exception) {}
        return name
    }
}
