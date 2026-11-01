package com.example.sltbsmartmove.model

data class BusRoute(
    val id: String = "",
    val origin: String = "",
    val destination: String = "",
    val busType: String = "",
    val departureTime: String = "",
    val fare: String = "",
    val intermediateStops: List<String> = emptyList()
)
