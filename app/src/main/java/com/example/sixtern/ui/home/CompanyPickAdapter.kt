package com.example.sixtern.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.sixtern.R
import com.example.sixtern.data.model.Company
import com.example.sixtern.data.model.scheduleSummary
import com.example.sixtern.databinding.ItemCompanyPickBinding
import com.example.sixtern.ui.profile.MentorProfileViewModel

/** Daftar perusahaan di beranda (saat belum punya companyId).
 * Kartu bisa diklik -> buka detail; sisa slot ditampilkan bila tersedia. */
class CompanyPickAdapter(
    private var takenSlots: Map<String, Int> = emptyMap(),
    private val onPick: (Company) -> Unit
) : ListAdapter<Company, CompanyPickAdapter.ViewHolder>(DIFF) {

    fun updateTakenSlots(taken: Map<String, Int>) {
        takenSlots = taken
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCompanyPickBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding, onPick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), takenSlots[getItem(position).id] ?: 0)
    }

    class ViewHolder(
        private val binding: ItemCompanyPickBinding,
        private val onPick: (Company) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private val context get() = binding.root.context

        fun bind(company: Company, taken: Int) {
            val left = (company.internshipSlots - taken).coerceAtLeast(0)
            binding.companyName.text = company.name.ifBlank { "-" }
            binding.companySlots.text = context.getString(
                R.string.company_slots_left, left, company.internshipSlots
            )
            binding.companySchedule.text = company.scheduleSummary()
            val desc = company.description.ifBlank { "" }
            val benefits = company.benefits.joinToString(", ").ifBlank { "-" }
            binding.companyBenefits.text = if (desc.isBlank()) benefits else "$desc\n$benefits"
            // Logo perusahaan (Base64 kecil) -> tampil; kalau kosong pakai ikon bawaan.
            val logo = MentorProfileViewModel.decodeLogoDataUri(company.logoUrl)
            if (logo != null) {
                binding.companyLogo.setImageBitmap(logo)
                binding.companyLogo.setPadding(0, 0, 0, 0)
            } else {
                binding.companyLogo.setImageResource(R.drawable.ic_nav_company)
                val p = (6 * context.resources.displayMetrics.density).toInt()
                binding.companyLogo.setPadding(p, p, p, p)
            }
            binding.root.setOnClickListener { onPick(company) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Company>() {
            override fun areItemsTheSame(old: Company, new: Company): Boolean =
                old.id == new.id

            override fun areContentsTheSame(old: Company, new: Company): Boolean =
                old == new
        }
    }
}
