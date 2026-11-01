package com.example.sltbsmartmove

import com.example.sltbsmartmove.model.BusStop
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BusStopTest {

    private fun stop(lat: Double?, lng: Double?) =
        BusStop(stopId = "s", name = "Test", latitude = lat, longitude = lng)

    @Test
    fun validCoordinates_areAccepted() {
        assertTrue(stop(6.9271, 79.8612).hasValidCoordinates)
        assertTrue(stop(-33.86, 151.21).hasValidCoordinates)
    }

    @Test
    fun missingCoordinates_areRejected() {
        assertFalse(stop(null, null).hasValidCoordinates)
        assertFalse(stop(6.9, null).hasValidCoordinates)
        assertFalse(stop(null, 79.8).hasValidCoordinates)
    }

    @Test
    fun outOfRangeCoordinates_areRejected() {
        assertFalse(stop(91.0, 79.8).hasValidCoordinates)
        assertFalse(stop(6.9, 181.0).hasValidCoordinates)
        assertFalse(stop(-90.5, 0.5).hasValidCoordinates)
    }

    @Test
    fun nanAndInfinity_areRejected() {
        assertFalse(stop(Double.NaN, 79.8).hasValidCoordinates)
        assertFalse(stop(6.9, Double.POSITIVE_INFINITY).hasValidCoordinates)
    }

    @Test
    fun nullIsland_isRejected() {
        assertFalse(stop(0.0, 0.0).hasValidCoordinates)
    }
}
