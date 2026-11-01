package com.example.sltbsmartmove.model


data class BusStop(
    val stopId: String = "",
    val name: String = "",
    val town: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isDemoData: Boolean = true
) {
    /**
     * True only when both coordinates exist, are real numbers in range, and are
     * not the placeholder point (0, 0) in the Atlantic Ocean.
     */
    val hasValidCoordinates: Boolean
        get() {
            val lat = latitude
            val lng = longitude
            return lat != null && lng != null &&
                    lat.isFinite() && lng.isFinite() &&
                    lat in -90.0..90.0 &&
                    lng in -180.0..180.0 &&
                    !(lat == 0.0 && lng == 0.0)
        }
}
