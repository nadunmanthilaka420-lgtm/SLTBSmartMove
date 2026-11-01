package com.example.sltbsmartmove.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class LocationHelper(context: Context) {

    private val appContext = context.applicationContext

    private val client =
        LocationServices.getFusedLocationProviderClient(context)

    private fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED

    /** True if the user allowed precise OR approximate location. */
    fun hasLocationPermission(context: Context): Boolean =
        isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
                isGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    /** True if the phone's Location switch (GPS / network location) is on. */
    fun isLocationEnabled(): Boolean {
        val manager =
            appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return false
        return LocationManagerCompat.isLocationEnabled(manager)
    }

    @SuppressLint("MissingPermission")
    fun getCurrentLocation(
        onResult: (Location?, String?) -> Unit
    ) {
        // Use GPS-level accuracy only when the user granted precise location.
        val priority =
            if (isGranted(appContext, Manifest.permission.ACCESS_FINE_LOCATION)) {
                Priority.PRIORITY_HIGH_ACCURACY
            } else {
                Priority.PRIORITY_BALANCED_POWER_ACCURACY
            }

        client.getCurrentLocation(priority, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    onResult(location, null)
                } else {
                    onResult(
                        null,
                        "Could not get a location fix. Move to an open area " +
                                "and try again."
                    )
                }
            }
            .addOnFailureListener { error ->
                onResult(
                    null,
                    error.localizedMessage ?: "Could not get your location."
                )
            }
    }
}
