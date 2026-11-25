package com.example.sixtern

import com.example.sixtern.data.model.Company
import com.example.sixtern.data.model.WorkDaySchedule
import com.example.sixtern.data.model.activeDays
import com.example.sixtern.data.model.scheduleSummary
import com.example.sixtern.data.repository.AuthRepository
import com.example.sixtern.ui.profile.MentorProfileViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanyScheduleTest {

    @Test
    fun scheduleSummary_collapsesContiguousDays() {
        val c = Company(
            workSchedule = mapOf(
                "Senin" to WorkDaySchedule("08:00", "17:00"),
                "Selasa" to WorkDaySchedule("08:00", "17:00"),
                "Rabu" to WorkDaySchedule("08:00", "17:00")
            )
        )
        assertEquals("Senin - Rabu \u2022 08:00\u201317:00", c.scheduleSummary())
    }

    @Test
    fun scheduleSummary_emptyFallsBackToLegacy() {
        val c = Company(
            workDays = listOf("Senin", "Selasa"),
            startTime = "09:00",
            endTime = "15:00"
        )
        assertTrue(c.scheduleSummary().contains("Senin, Selasa"))
        assertTrue(c.scheduleSummary().contains("09:00"))
    }

    @Test
    fun parseBenefits_readsLegacyString() {
        assertEquals(
            listOf("Uang saku", "Makan", "Transport", "Lain"),
            Company.parseBenefits("Uang saku, Makan;Transport\nLain")
        )
    }

    @Test
    fun normalizeTime_padsAndRejects() {
        assertEquals("08:05", MentorProfileViewModel.normalizeTime("8:5"))
        assertEquals("", MentorProfileViewModel.normalizeTime("25:00"))
        assertEquals("", MentorProfileViewModel.normalizeTime(""))
    }

    @Test
    fun isValidTime_checksRange() {
        assertTrue(MentorProfileViewModel.isValidTime("08:00"))
        assertFalse(MentorProfileViewModel.isValidTime("24:00"))
        assertFalse(MentorProfileViewModel.isValidTime("8 pagi"))
    }

    @Test
    fun fromCompany_mapsLegacyWorkDays() {
        val rows = MentorProfileViewModel.fromCompany(
            Company(workDays = listOf("Senin"), startTime = "08:00", endTime = "17:00")
        )
        assertEquals("08:00", rows.first { it.day == "Senin" }.start)
        assertTrue(rows.first { it.day == "Minggu" }.isOff)
    }

    @Test
    fun activeDays_prefersWorkDays() {
        val c = Company(workDays = listOf("Senin"))
        assertEquals(listOf("Senin"), c.activeDays())
    }

    @Test
    fun remainingSlots_subtractsTakenAndClampsAtZero() {
        val c = Company(internshipSlots = 5)
        assertEquals(3, AuthRepository.remainingSlots(c, 2))
        assertEquals(0, AuthRepository.remainingSlots(c, 9))
        assertEquals(5, AuthRepository.remainingSlots(c, 0))
    }
}
