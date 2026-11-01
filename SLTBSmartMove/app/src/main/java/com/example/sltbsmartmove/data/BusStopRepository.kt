package com.example.sltbsmartmove.data

import com.example.sltbsmartmove.model.BusStop
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class BusStopRepository {

    private val stopsReference =
        FirebaseDatabase.getInstance().getReference("stops")

    private fun getSampleStops(): List<BusStop> = listOf(
        BusStop("stop_1", "Colombo Fort Central Bus Stand", "Colombo", 6.9344, 79.8507, true),
        BusStop("stop_2", "Negombo Main Bus Stand", "Negombo", 7.2094, 79.8385, true),
        BusStop("stop_3", "Kandy Good Shed Bus Station", "Kandy", 7.2906, 80.6337, true),
        BusStop("stop_4", "Galle Central Bus Station", "Galle", 6.0329, 80.2168, true),
        BusStop("stop_5", "Kurunegala Central Bus Stand", "Kurunegala", 7.4863, 80.3623, true),
        BusStop("stop_6", "Matara Bus Terminal", "Matara", 5.9549, 80.5550, true),
        BusStop("stop_7", "Anuradhapura New Town Bus Stand", "Anuradhapura", 8.3114, 80.4037, true)
    )

    /**
     * Loads every record under `stops`. Falls back to sample stops if offline,
     * unconfigured, or permission denied.
     */
    fun getStops(onResult: (List<BusStop>?, String?) -> Unit) {
        AuthRepository.ensureSignedIn { uid, _ ->
            readStops(onResult)
        }
    }

    private fun readStops(onResult: (List<BusStop>?, String?) -> Unit) {
        lateinit var listener: ValueEventListener

        val guard = CallbackGuard(timeoutMs = 8_000L) {
            stopsReference.removeEventListener(listener)
            // Fallback on timeout
            onResult(getSampleStops(), null)
        }

        listener = object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {
                if (!guard.complete()) return

                val stops = mutableListOf<BusStop>()

                for (child in snapshot.children) {
                    val key = child.key ?: continue
                    try {
                        stops.add(
                            BusStop(
                                stopId = key,
                                name = child.text("name"),
                                town = child.text("town"),
                                latitude = child.number("latitude"),
                                longitude = child.number("longitude"),
                                isDemoData = child.flag("isDemoData", true)
                            )
                        )
                    } catch (ignored: Exception) {
                        // Skip a record that cannot be read.
                    }
                }

                if (stops.isEmpty()) {
                    onResult(getSampleStops(), null)
                } else {
                    onResult(stops, null)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (!guard.complete()) return
                // Graceful fallback to sample stops on permission denied / error
                onResult(getSampleStops(), null)
            }
        }

        stopsReference.addListenerForSingleValueEvent(listener)
    }
}
