package com.example.sltbsmartmove.uis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.sltbsmartmove.data.BusStopRepository
import com.example.sltbsmartmove.location.LocationHelper
import com.example.sltbsmartmove.model.BusStop

private data class NearbyStop(
    val stop: BusStop,
    val distanceMetres: Float
)

@Composable
fun NearbyStopsScreen() {
    val context = LocalContext.current
    val locationHelper = remember { LocationHelper(context) }
    val repository = remember { BusStopRepository() }

    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }
    var locationOff by remember { mutableStateOf(false) }
    var nearbyStops by remember { mutableStateOf(emptyList<NearbyStop>()) }

    fun loadNearbyStops() {
        if (!locationHelper.hasLocationPermission(context)) {
            permissionDenied = true
            message = "Allow location access to find nearby bus stops."
            return
        }

        permissionDenied = false
        nearbyStops = emptyList()
        info = null

        if (!locationHelper.isLocationEnabled()) {
            locationOff = true
            message = "Location services are turned off. " +
                    "Turn them on to find nearby bus stops."
            return
        }

        locationOff = false
        loading = true
        message = null

        locationHelper.getCurrentLocation { location, locationError ->
            if (location == null) {
                loading = false
                message = locationError
                    ?: "Unable to get your location. Please try again."
                return@getCurrentLocation
            }

            repository.getStops { stops, databaseError ->
                loading = false

                if (databaseError != null || stops == null) {
                    message = "Could not load bus stops: " +
                            (databaseError ?: "Unknown error")
                    return@getStops
                }

                val validStops = stops.mapNotNull { stop ->
                    val latitude = stop.latitude
                    val longitude = stop.longitude

                    if (!stop.hasValidCoordinates ||
                        latitude == null ||
                        longitude == null
                    ) {
                        null
                    } else {
                        val distance = FloatArray(1)
                        Location.distanceBetween(
                            location.latitude,
                            location.longitude,
                            latitude,
                            longitude,
                            distance
                        )

                        NearbyStop(stop, distance[0])
                    }
                }.sortedBy { it.distanceMetres }

                nearbyStops = validStops

                val skipped = stops.size - validStops.size

                if (validStops.isEmpty()) {
                    message = if (stops.isEmpty()) {
                        "No bus stops found in the database."
                    } else {
                        "None of the ${stops.size} bus stops has valid " +
                                "coordinates yet, so distances cannot be shown."
                    }
                } else if (skipped > 0) {
                    info = "$skipped stop(s) hidden: missing or invalid coordinates."
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted =
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (granted) {
            loadNearbyStops()
        } else {
            permissionDenied = true
            message =
                "Location permission was denied. You can still browse routes."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Nearby Bus Stops",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            "Find bus stops near your current location. " +
                    "Your location is used for this search."
        )

        Button(
            onClick = {
                val fine = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                val coarse = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (fine || coarse) {
                    loadNearbyStops()
                } else {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading
        ) {
            Text("Find Nearby Stops")
        }

        if (loading) {
            CircularProgressIndicator()
            Text("Getting location and loading stops...")
        }

        if (permissionDenied) {
            Text(
                "Location access is optional. You can use other app features " +
                        "without it. Enable permission in app settings if needed."
            )

            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts(
                                "package",
                                context.packageName,
                                null
                            )
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }
            ) {
                Text("Open App Settings")
            }
        }

        if (locationOff) {
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            ) {
                Text("Open Location Settings")
            }
        }

        message?.let { errorMessage ->
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error
            )

            Button(
                onClick = { loadNearbyStops() },
                enabled = !loading
            ) {
                Text("Try Again")
            }
        }

        info?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(nearbyStops, key = { it.stop.stopId }) { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            item.stop.name,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text("Town: ${item.stop.town}")
                        Text("Distance: ${formatDistance(item.distanceMetres)}")

                        if (item.stop.isDemoData) {
                            Text(
                                "Demonstration stop — verify its location.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDistance(metres: Float): String {
    return if (metres < 1000) {
        "${metres.toInt()} m away"
    } else {
        String.format(java.util.Locale.getDefault(), "%.1f km away", metres / 1000)
    }
}
