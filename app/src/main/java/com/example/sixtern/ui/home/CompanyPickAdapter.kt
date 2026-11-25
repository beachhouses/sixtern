package com.example.sixtern.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.sixtern.R
import com.example.sixtern.data.model.Company
import com.example.sixtern.databinding.ItemCompanyPickBinding

/** Daftar perusahaan yang bisa dipilih mahasiswa di beranda (saat belum punya companyId). */
class CompanyPickAdapter(
    private val onPick: (Company) -> Unit
) : ListAdapter<Company, CompanyPickAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCompanyPickBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding, onPick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemCompanyPickBinding,
        private val onPick: (Company) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private val context get() = binding.root.context

        fun bind(company: Company) {
            binding.companyName.text = company.name.ifBlank { "-" }
            binding.companySlots.text = context.getString(
                R.string.choose_company_slots, company.internshipSlots
            )
            val days = company.workDays.filter { it.isNotBlank() }.joinToString(", ")
            binding.companySchedule.text = context.getString(
                R.string.choose_company_schedule,
                days.ifBlank { "-" },
                company.startTime.ifBlank { "-" },
                company.endTime.ifBlank { "-" }
            )
            binding.companyBenefits.text = company.benefits.ifBlank { "-" }
            binding.pickButton.setOnClickListener { onPick(company) }
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
