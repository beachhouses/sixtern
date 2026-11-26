package com.example.sixtern.ui.logbook

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sixtern.data.model.Logbook
import com.example.sixtern.data.model.SixternUser
import com.example.sixtern.data.repository.AuthRepository
import com.example.sixtern.data.repository.InternshipRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LogbookViewModel(
    private val repository: InternshipRepository = InternshipRepository(),
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _logbooks = MutableLiveData<List<Logbook>>(emptyList())
    val logbooks: LiveData<List<Logbook>> = _logbooks

    private val _filter = MutableLiveData(FILTER_ALL)
    val filter: LiveData<String> = _filter

    private val _saving = MutableLiveData(false)
    val saving: LiveData<Boolean> = _saving

    private val _message = MutableLiveData<String?>(null)
    val message: LiveData<String?> = _message

    /** True tepat setelah logbook BARU berhasil dibuat (bukan revisi). */
    private val _created = MutableLiveData(false)
    val created: LiveData<Boolean> = _created

    private var studentId: String = ""
    private var companyId: String = ""

    /**
     * True bila mahasiswa sudah diterima & punya tempat magang (hasil cek
     * data user terbaru dari Firestore). Layar memakai ini untuk memilih
     * antara daftar logbook normal vs kartu "belum ada tempat magang".
     */
    private val _companyReady = MutableLiveData<Boolean>()
    val companyReady: LiveData<Boolean> = _companyReady

    fun init(studentId: String, intentCompanyId: String) {
        this.studentId = studentId
        // Selalu cek data user terbaru — intent bisa basi atau tidak
        // membawa companyId (tombol + pernah membuka form tanpa extra).
        viewModelScope.launch {
            val user = try {
                authRepository.getUser(studentId)
            } catch (e: Exception) {
                null
            }
            val accepted = user?.internshipStatus == SixternUser.STATUS_ACCEPTED
            val freshCompanyId = user?.companyId.orEmpty()
            companyId = if (accepted && freshCompanyId.isNotBlank()) freshCompanyId else ""
            _companyReady.value = companyId.isNotBlank()
            refresh()
            // Listener realtime: status Disetujui/Revisi berubah otomatis
            // saat mentor mereview — tanpa perlu keluar-masuk layar.
            startObserving()
        }
    }

    /** companyId segar hasil cek Firestore — dipakai intent ke form & nav. */
    fun currentCompanyId(): String = companyId

    fun setFilter(filter: String) {
        _filter.value = filter
    }

    fun filtered(): List<Logbook> {
        val list = _logbooks.value.orEmpty()
        return when (_filter.value) {
            FILTER_APPROVED -> list.filter { it.status == Logbook.STATUS_APPROVED }
            FILTER_REVISION -> list.filter { it.status == Logbook.STATUS_REVISION }
            else -> list
        }
    }

    fun countApproved(): Int =
        _logbooks.value.orEmpty().count { it.status == Logbook.STATUS_APPROVED }

    fun countRevision(): Int =
        _logbooks.value.orEmpty().count { it.status == Logbook.STATUS_REVISION }

    /** Listener realtime logbook mahasiswa — status review mentor langsung tampil. */
    private var observeJob: Job? = null

    private fun startObserving() {
        observeJob?.cancel()
        if (studentId.isBlank()) return
        observeJob = viewModelScope.launch {
            repository.observeLogbooksByStudent(studentId)
                .collectLatest { list -> _logbooks.value = list }
        }
    }

    fun refresh() {
        if (studentId.isBlank()) return
        viewModelScope.launch {
            try {
                _logbooks.value = repository.getLogbooks(studentId)
            } catch (e: Exception) {
                _message.value = e.message ?: "Gagal memuat logbook"
            }
        }
    }

    fun save(title: String, description: String, editingId: String?, attachmentUrl: String? = null) {
        if (title.isBlank()) {
            _message.value = "Judul wajib diisi"
            return
        }
        if (description.isBlank()) {
            _message.value = "Keterangan wajib diisi"
            return
        }
        _saving.value = true
        viewModelScope.launch {
            try {
                // Logbook baru butuh companyId; bila masih kosong, cek ulang
                // data user (mungkin intent form tidak membawanya).
                if (editingId.isNullOrBlank() && companyId.isBlank()) {
                    val user = try {
                        authRepository.getUser(studentId)
                    } catch (e: Exception) {
                        null
                    }
                    val accepted = user?.internshipStatus == SixternUser.STATUS_ACCEPTED
                    companyId = if (accepted) user?.companyId.orEmpty() else ""
                }
                if (editingId.isNullOrBlank() && companyId.isBlank()) {
                    _message.value = "Pilih tempat magang dulu di beranda"
                    _saving.value = false
                    return@launch
                }
                if (editingId.isNullOrBlank()) {
                    repository.addLogbook(
                        studentId, companyId, title.trim(), description.trim(), attachmentUrl
                    )
                    _message.value = "Logbook terkirim, menunggu review mentor"
                    _created.value = true
                } else {
                    repository.updateLogbookRevision(
                        editingId, title.trim(), description.trim(), attachmentUrl
                    )
                    _message.value = "Revisi terkirim, menunggu review mentor"
                }
                _logbooks.value = repository.getLogbooks(studentId)
            } catch (e: Exception) {
                _message.value = e.message ?: "Gagal menyimpan"
            } finally {
                _saving.value = false
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    /** Konsumsi event pindah ke layar logbook setelah form selesai. */
    fun consumeCreated() {
        _created.value = false
    }

    companion object {
        const val FILTER_ALL = "all"
        const val FILTER_APPROVED = "approved"
        const val FILTER_REVISION = "revision"
    }
}
