package com.example.sixtern.ui.prodi

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
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
import com.example.sixtern.data.model.CampusInfo
import com.example.sixtern.data.repository.AuthRepository
import com.example.sixtern.databinding.ActivityProdiProfileBinding
import com.example.sixtern.ui.auth.LoginActivity
import com.example.sixtern.ui.profile.MentorProfileViewModel
import kotlinx.coroutines.launch

class ProdiProfileViewModel(
    private val repository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _campus = MutableLiveData<CampusInfo?>()
    val campus: LiveData<CampusInfo?> = _campus

    private val _saving = MutableLiveData(false)
    val saving: LiveData<Boolean> = _saving

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    private val _user = MutableLiveData<com.example.sixtern.data.model.SixternUser?>()
    val user: LiveData<com.example.sixtern.data.model.SixternUser?> = _user

    fun loadUser(uid: String) {
        viewModelScope.launch {
            _user.value = try { repository.getUser(uid) } catch (e: Exception) { null }
        }
    }

    fun loadCampus() {
        viewModelScope.launch {
            _campus.value = try { repository.getCampusInfo() } catch (e: Exception) { null }
        }
    }

    fun save(info: CampusInfo) {
        viewModelScope.launch {
            _saving.value = true
            try {
                repository.saveCampusInfo(info)
                _message.value = "SAVED"
            } catch (e: Exception) {
                _message.value = e.message ?: "Gagal menyimpan profil kampus"
            }
            _saving.value = false
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun signOut() = repository.signOut()
}

/**
 * Profil kampus milik prodi (pemilik aplikasi): logo kampus, nama kampus,
 * nama prodi, dan info lainnya. Tersimpan di dokumen campus/info.
 */
class ProdiProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProdiProfileBinding
    private val viewModel: ProdiProfileViewModel by viewModels()

    /** Logo Base64 yang baru dipilih (belum disimpan). */
    private var pendingLogo: String? = null

    private val pickLogo = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        try {
            val source = BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
            val dataUri = MentorProfileViewModel.bitmapToLogoDataUri(source, 256)
            pendingLogo = dataUri
            showLogo(dataUri)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.cv_upload_error, e.message ?: ""), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProdiProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uid = intent.getStringExtra(LoginActivity.EXTRA_USER_ID).orEmpty()
        val name = intent.getStringExtra(LoginActivity.EXTRA_USER_NAME).orEmpty()

        binding.prodiNav.itemIconTintList = null
        ProdiNav.setup(binding.prodiNav, this, R.id.nav_profile, uid, name)

        binding.accountNameText.text = name.ifBlank { "-" }

        viewModel.message.observe(this) { msg ->
            if (msg != null) {
                val text = if (msg == "SAVED") {
                    getString(R.string.prodi_profile_saved)
                } else msg
                Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
                viewModel.consumeMessage()
            }
        }

        viewModel.saving.observe(this) { saving ->
            binding.saveButton.isEnabled = !saving
            binding.loadingIndicator.visibility = if (saving) View.VISIBLE else View.GONE
        }

        viewModel.campus.observe(this) { info ->
            if (info == null) return@observe
            binding.campusNameInput.setText(info.campusName)
            binding.prodiNameInput.setText(info.prodiName)
            binding.facultyInput.setText(info.faculty)
            binding.addressInput.setText(info.address)
            binding.websiteInput.setText(info.website)
            binding.descriptionInput.setText(info.description)
            if (info.logoUrl.isNotBlank()) showLogo(info.logoUrl)
        }

        binding.logoButton.setOnClickListener { pickLogo.launch("image/*") }

        binding.saveButton.setOnClickListener {
            val existing = viewModel.campus.value
            val info = CampusInfo(
                campusName = binding.campusNameInput.text?.toString()?.trim().orEmpty(),
                prodiName = binding.prodiNameInput.text?.toString()?.trim().orEmpty(),
                faculty = binding.facultyInput.text?.toString()?.trim().orEmpty(),
                address = binding.addressInput.text?.toString()?.trim().orEmpty(),
                website = binding.websiteInput.text?.toString()?.trim().orEmpty(),
                description = binding.descriptionInput.text?.toString()?.trim().orEmpty(),
                logoUrl = pendingLogo ?: existing?.logoUrl.orEmpty()
            )
            if (info.campusName.isBlank() || info.prodiName.isBlank()) {
                Toast.makeText(this, getString(R.string.prodi_profile_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.save(info)
        }

        binding.logoutButton.setOnClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.prodi_logout_confirm))
                .setPositiveButton(getString(R.string.logout)) { _, _ ->
                    viewModel.signOut()
                    startActivity(
                        Intent(this, LoginActivity::class.java)
                    )
                    finish()
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }

        // Email akun prodi untuk header.
        viewModel.user.observe(this) { user ->
            binding.accountEmailText.text = user?.email.orEmpty().ifBlank { "-" }
        }
        if (uid.isNotBlank()) viewModel.loadUser(uid)

        viewModel.loadCampus()
    }

    private fun showLogo(dataUri: String) {
        val bmp = MentorProfileViewModel.decodeLogoDataUri(dataUri)
        if (bmp != null) {
            binding.campusLogo.setImageBitmap(bmp)
            binding.campusLogo.setPadding(0, 0, 0, 0)
        }
    }
}
