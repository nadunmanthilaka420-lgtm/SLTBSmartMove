package com.example.sltbsmartmove.data

import com.google.firebase.database.IgnoreExtraProperties
import com.google.firebase.database.PropertyName

/**
 * One bus route stored under the `routes` node of Firebase Realtime Database.
 *
 * This is a plain Firebase data class (NOT a Room @Entity). Every property has a
 * default value so Firebase can create an empty instance and then fill it in
 * (`snapshot.getValue(BusRouteEntity::class.java)`). Property names match the
 * JSON field names exactly: do not rename them without also changing the database.
 *
 * DEMONSTRATION DATA: routes, durations and fares in the database are samples
 * and are not verified official SLTB information.
 */
@IgnoreExtraProperties
data class BusRouteEntity(
    var routeId: String = "",
    var routeNumber: String = "",
    var origin: String = "",
    var destination: String = "",
    var via: List<String> = emptyList(),
    var distanceKm: Double = 0.0,
    var estimatedDurationMinutes: Int = 0,
    var busType: String = "",
    var fareLkr: Double? = null,
    var fareNote: String = "",
    var status: String = "",
    // Kotlin turns `isDemoData` into a Java getter `isDemoData()`, which Firebase
    // would read as the field "demoData". The annotations force the real name
    // "isDemoData" so it matches the JSON.
    @get:PropertyName("isDemoData")
    @set:PropertyName("isDemoData")
    var isDemoData: Boolean = true,
    var stopIds: List<String> = emptyList()
)
