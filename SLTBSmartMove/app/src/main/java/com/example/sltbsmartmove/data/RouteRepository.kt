package com.example.sltbsmartmove.data

import com.example.sltbsmartmove.model.BusRoute

class RouteRepository {

    fun getRoutes(): List<BusRoute> {
        return listOf(
            BusRoute(
                id = "1",
                origin = "Negombo",
                destination = "Colombo",
                busType = "Ordinary Bus",
                departureTime = "Sample: 07:00 AM",
                fare = "Check current fare",
                intermediateStops = listOf("Seeduwa", "Ja-Ela", "Wattala")
            ),
            BusRoute(
                id = "2",
                origin = "Kandy",
                destination = "Colombo",
                busType = "Intercity Bus",
                departureTime = "Sample: 08:00 AM",
                fare = "Check current fare",
                intermediateStops = listOf("Peradeniya", "Kegalle", "Nittambuwa", "Kadawatha")
            ),
            BusRoute(
                id = "3",
                origin = "Galle",
                destination = "Colombo",
                busType = "Express Bus",
                departureTime = "Sample: 09:00 AM",
                fare = "Check current fare",
                intermediateStops = listOf("Hikkaduwa", "Ambalangoda", "Aluthgama", "Kalutara", "Panadura")
            ),
            BusRoute(
                id = "4",
                origin = "Negombo",
                destination = "Kandy",
                busType = "Ordinary Bus",
                departureTime = "Sample: 10:00 AM",
                fare = "Check current fare",
                intermediateStops = listOf("Minuwangoda", "Gampaha", "Veyangoda", "Kegalle")
            ),
            BusRoute(
                id = "5",
                origin = "Colombo",
                destination = "Galle",
                busType = "Express Bus",
                departureTime = "Sample: 11:00 AM",
                fare = "Check current fare",
                intermediateStops = listOf("Panadura", "Kalutara", "Aluthgama", "Ambalangoda")
            )
        )
    }
}
