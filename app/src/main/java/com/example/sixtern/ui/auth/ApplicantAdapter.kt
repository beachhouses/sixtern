package com.example.sixtern.ui.auth

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.sixtern.data.model.SixternUser
import com.example.sixtern.databinding.ItemApplicantBinding

/**
 * Daftar mahasiswa pengaju magang di beranda mentor.
 * Tiap baris: avatar inisial + nama + NIM + email.
 */
class ApplicantAdapter : ListAdapter<SixternUser, ApplicantAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemApplicantBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemApplicantBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(user: SixternUser) {
            binding.applicantAvatar.text =
                user.name.firstOrNull()?.uppercase() ?: "M"
            binding.applicantName.text = user.name.ifBlank { "-" }
            val nim = user.nim?.takeIf { it.isNotBlank() } ?: "-"
            val email = user.email.takeIf { it.isNotBlank() }.orEmpty()
            binding.applicantDetail.text =
                if (email.isBlank()) nim else "$nim • $email"
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SixternUser>() {
            override fun areItemsTheSame(old: SixternUser, new: SixternUser): Boolean =
                old.uid == new.uid

            override fun areContentsTheSame(old: SixternUser, new: SixternUser): Boolean =
                old == new
        }
    }
}
