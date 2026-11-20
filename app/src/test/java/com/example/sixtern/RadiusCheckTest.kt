package com.example.sixtern

import com.example.sixtern.data.repository.RadiusChecker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadiusCheckTest {

    @Test
    fun insideRadius_returnsTrue() {
        // Titik ~11 meter dari pusat (0,0) dengan radius 100 m
        assertTrue(RadiusChecker.isInsideRadius(0.0001, 0.0, 0.0, 0.0, 100.0))
    }

    @Test
    fun outsideRadius_returnsFalse() {
        // Titik ~1.1 km dari pusat dengan radius 100 m
        assertFalse(RadiusChecker.isInsideRadius(0.01, 0.0, 0.0, 0.0, 100.0))
    }

    @Test
    fun missingCompanyLocation_returnsFalse() {
        assertFalse(RadiusChecker.isInsideRadius(0.0, 0.0, null, null, 100.0))
    }
}
