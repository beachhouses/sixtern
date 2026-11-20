package com.example.sixtern.data.repository

object RadiusChecker {
    fun isInsideRadius(
        lat: Double,
        lng: Double,
        companyLat: Double?,
        companyLng: Double?,
        radiusMeters: Double
    ): Boolean {
        if (companyLat == null || companyLng == null) return false
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(companyLat - lat)
        val dLng = Math.toRadians(companyLng - lng)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(companyLat)) *
            Math.sin(dLng / 2) * Math.sin(dLng / 2)
        val distance = 2 * earthRadius * Math.asin(Math.sqrt(a))
        return distance <= radiusMeters
    }
}
