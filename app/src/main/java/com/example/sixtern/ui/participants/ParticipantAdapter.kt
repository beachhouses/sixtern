package com.example.sixtern.ui.participants

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.sixtern.R
import com.example.sixtern.data.model.SixternUser
import com.example.sixtern.databinding.ItemParticipantBinding

/**
 * Daftar peserta magang di layar Kelola Peserta (mentor).
 * Tombol aksi per baris mengikuti status pengajuan:
 * pending -> Terima + Tolak, rejected -> Terima (ubah pikiran),
 * accepted -> Keluarkan.
 */
class ParticipantAdapter(
    private val onAccept: (SixternUser) -> Unit,
    private val onReject: (SixternUser) -> Unit,
    private val onRemove: (SixternUser) -> Unit,
    private val onItemClick: (SixternUser) -> Unit = {},
    private val onViewAttendance: (SixternUser) -> Unit = {}
) : ListAdapter<SixternUser, ParticipantAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemParticipantBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(
            getItem(position),
            onAccept, onReject, onRemove, onItemClick, onViewAttendance
        )
    }

    class ViewHolder(
        private val binding: ItemParticipantBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            user: SixternUser,
            onAccept: (SixternUser) -> Unit,
            onReject: (SixternUser) -> Unit,
            onRemove: (SixternUser) -> Unit,
            onItemClick: (SixternUser) -> Unit,
            onViewAttendance: (SixternUser) -> Unit
        ) {
            binding.participantAvatar.text = user.name.firstOrNull()?.uppercase() ?: "M"
            binding.participantName.text = user.name.ifBlank { "-" }
            val nim = user.nim?.takeIf { it.isNotBlank() } ?: "-"
            val email = user.email.takeIf { it.isNotBlank() }.orEmpty()
            binding.participantDetail.text =
                if (email.isBlank()) nim else "$nim • $email"

            val context = binding.root.context
            val (label, colorRes) = when (user.internshipStatus) {
                SixternUser.STATUS_ACCEPTED ->
                    context.getString(R.string.participants_filter_accepted) to R.color.chip_accepted_bg
                SixternUser.STATUS_REJECTED ->
                    context.getString(R.string.participants_filter_rejected) to R.color.chip_rejected_bg
                else ->
                    context.getString(R.string.participants_filter_pending) to R.color.chip_pending_bg
            }
            binding.participantStatus.text = label
            binding.participantStatus.setBackgroundResource(R.drawable.bg_chip_status)
            binding.participantStatus.background.setTint(
                androidx.core.content.ContextCompat.getColor(context, colorRes)
            )

            when (user.internshipStatus) {
                SixternUser.STATUS_PENDING -> {
                    binding.acceptButton.visibility = View.VISIBLE
                    binding.rejectButton.visibility = View.VISIBLE
                    binding.removeButton.visibility = View.GONE
                    binding.attendanceButton.visibility = View.GONE
                }
                SixternUser.STATUS_REJECTED -> {
                    binding.acceptButton.visibility = View.VISIBLE
                    binding.rejectButton.visibility = View.GONE
                    binding.removeButton.visibility = View.GONE
                    binding.attendanceButton.visibility = View.GONE
                }
                else -> {
                    // STATUS_ACCEPTED: tampilkan tombol Keluarkan + Lihat Absensi
                    binding.acceptButton.visibility = View.GONE
                    binding.rejectButton.visibility = View.GONE
                    binding.removeButton.visibility = View.VISIBLE
                    binding.attendanceButton.visibility = View.VISIBLE
                }
            }

            binding.acceptButton.setOnClickListener { onAccept(user) }
            binding.rejectButton.setOnClickListener { onReject(user) }
            binding.removeButton.setOnClickListener { onRemove(user) }
            binding.attendanceButton.setOnClickListener { onViewAttendance(user) }
            itemView.setOnClickListener { onItemClick(user) }
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
