package com.example.sixtern

import com.example.sixtern.data.model.Company
import com.example.sixtern.data.model.DailyLogbookStatus
import com.example.sixtern.data.model.SixternUser
import com.example.sixtern.data.model.WorkDaySchedule
import com.example.sixtern.data.model.activeDays
import com.example.sixtern.data.model.dailyLogbookStatus
import com.example.sixtern.data.model.endTimeForToday
import com.example.sixtern.data.model.scheduleSummary
import com.example.sixtern.data.model.timeToMinutes
import com.example.sixtern.data.repository.AuthRepository
import com.example.sixtern.ui.profile.MentorProfileViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun validateInternshipPeriod_acceptsValidRange() {
        assertNull(SixternUser.validateInternshipPeriod("2026-07-01", "2026-12-31"))
    }

    @Test
    fun validateInternshipPeriod_rejectsBlankAndInverted() {
        assertNotNull(SixternUser.validateInternshipPeriod("", "2026-12-31"))
        assertNotNull(SixternUser.validateInternshipPeriod("2026-07-01", ""))
        assertNotNull(SixternUser.validateInternshipPeriod("2026-12-31", "2026-07-01"))
        assertNotNull(SixternUser.validateInternshipPeriod("2026-07-01", "2026-07-01"))
        assertNotNull(SixternUser.validateInternshipPeriod("01-07-2026", "2026-12-31"))
    }

    @Test
    fun formatInternshipPeriod_formatsRangeOrDash() {
        val formatted = SixternUser.formatInternshipPeriod("2026-07-01", "2026-12-31")
        assertTrue(formatted.contains("2026"))
        assertTrue(formatted.contains(" - "))
        assertEquals("-", SixternUser.formatInternshipPeriod("", "2026-12-31"))
    }

    @Test
    fun formatInternshipDate_formatsSingleOrDash() {
        val formatted = SixternUser.formatInternshipDate("2026-07-01")
        assertTrue(formatted.contains("2026"))
        assertEquals("-", SixternUser.formatInternshipDate(""))
    }

    @Test
    fun internshipProgress_countsElapsedTotalAndPercent() {
        val fmt = java.text.SimpleDateFormat(SixternUser.DATE_PATTERN, java.util.Locale.US)
        val mid = fmt.parse("2026-07-15")!!.time + 12 * 60 * 60 * 1000
        val (elapsed, total, percent) =
            SixternUser.internshipProgress("2026-07-01", "2026-07-31", mid)
        assertEquals(31, total)
        assertEquals(15, elapsed)
        assertEquals((15 * 100) / 31, percent)
    }

    @Test
    fun internshipProgress_clampsBeforeAndAfter() {
        val fmt = java.text.SimpleDateFormat(SixternUser.DATE_PATTERN, java.util.Locale.US)
        val before = fmt.parse("2026-06-01")!!.time
        val after = fmt.parse("2026-08-01")!!.time + 12 * 60 * 60 * 1000
        val (e1, t1, p1) = SixternUser.internshipProgress("2026-07-01", "2026-07-31", before)
        assertEquals(31, t1)
        assertEquals(0, e1)
        assertEquals(0, p1)
        val (e2, t2, p2) = SixternUser.internshipProgress("2026-07-01", "2026-07-31", after)
        assertEquals(31, t2)
        assertEquals(31, e2)
        assertEquals(100, p2)
        // Invalid / kosong -> nol semua.
        assertEquals(Triple(0, 0, 0), SixternUser.internshipProgress("", "2026-07-31", before))
    }

    @Test
    fun timeToMinutes_parsesValidAndRejectsInvalid() {
        assertEquals(1020, timeToMinutes("17:00"))
        assertEquals(485, timeToMinutes("08:05"))
        assertEquals(null, timeToMinutes(""))
        assertEquals(null, timeToMinutes("24:00"))
        assertEquals(null, timeToMinutes("8 pagi"))
    }

    @Test
    fun dailyLogbookStatus_doneOpenMissed() {
        assertEquals(
            DailyLogbookStatus.DONE,
            dailyLogbookStatus(hasLogbookToday = true, endTimeToday = "17:00", nowMinutes = 1200)
        )
        assertEquals(
            DailyLogbookStatus.OPEN,
            dailyLogbookStatus(hasLogbookToday = false, endTimeToday = "17:00", nowMinutes = 1019)
        )
        assertEquals(
            DailyLogbookStatus.MISSED,
            dailyLogbookStatus(hasLogbookToday = false, endTimeToday = "17:00", nowMinutes = 1021)
        )
        // Jam batas tidak valid -> anggap masih terbuka.
        assertEquals(
            DailyLogbookStatus.OPEN,
            dailyLogbookStatus(hasLogbookToday = false, endTimeToday = "", nowMinutes = 1400)
        )
    }

    @Test
    fun endTimeForToday_fallsBackToLegacy() {
        val c = Company(endTime = "16:30")
        val today = com.example.sixtern.data.model.todayDayName()
        val scheduled = c.copy(
            workSchedule = mapOf(today to WorkDaySchedule("08:00", "15:45"))
        )
        assertEquals("15:45", scheduled.endTimeForToday())
        assertEquals("16:30", c.endTimeForToday())
    }
}
