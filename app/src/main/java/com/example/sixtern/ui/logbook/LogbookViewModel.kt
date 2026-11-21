package com.example.sixtern.ui.logbook

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sixtern.data.model.Logbook
import com.example.sixtern.data.repository.InternshipRepository
import kotlinx.coroutines.launch

class LogbookViewModel(
    private val repository: InternshipRepository = InternshipRepository()
) : ViewModel() {

    private val _logbooks = MutableLiveData<List<Logbook>>(emptyList())
    val logbooks: LiveData<List<Logbook>> = _logbooks

    private val _filter = MutableLiveData(FILTER_ALL)
    val filter: LiveData<String> = _filter

    private val _saving = MutableLiveData(false)
    val saving: LiveData<Boolean> = _saving

    private val _message = MutableLiveData<String?>(null)
    val message: LiveData<String?> = _message

    private var studentId: String = ""
    private var companyId: String = ""

    fun init(studentId: String, companyId: String) {
        this.studentId = studentId
        this.companyId = companyId
        refresh()
    }

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

    fun save(title: String, description: String, editingId: String?) {
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
                if (editingId.isNullOrBlank()) {
                    repository.addLogbook(studentId, companyId, title.trim(), description.trim())
                    _message.value = "Logbook tersimpan"
                } else {
                    repository.updateLogbookRevision(editingId, title.trim(), description.trim())
                    _message.value = "Revisi terkirim"
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

    companion object {
        const val FILTER_ALL = "all"
        const val FILTER_APPROVED = "approved"
        const val FILTER_REVISION = "revision"
    }
}
