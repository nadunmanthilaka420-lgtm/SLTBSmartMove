package com.example.sltbsmartmove.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.sltbsmartmove.data.RouteRepository
import com.example.sltbsmartmove.model.BusRoute
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class RouteViewModel(
    private val repository: RouteRepository = RouteRepository()
) {
    var origin by mutableStateOf("")
        private set

    var destination by mutableStateOf("")
        private set

    var selectedTab by mutableStateOf("Home")
        private set

    var hasSearched by mutableStateOf(false)
        private set

    var routesList by mutableStateOf<List<BusRoute>>(repository.getRoutes())
        private set

    init {
        val dbRef = FirebaseDatabase.getInstance().getReference("routes")
        dbRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val loaded = mutableListOf<BusRoute>()
                for (child in snapshot.children) {
                    try {
                        val id = child.key ?: continue
                        val orig = child.child("origin").value?.toString().orEmpty()
                        val dest = child.child("destination").value?.toString().orEmpty()
                        val type = child.child("busType").value?.toString().orEmpty()
                        val time = child.child("departureTime").value?.toString().orEmpty()
                        val fare = child.child("fare").value?.toString().orEmpty()

                        val stopsList = mutableListOf<String>()
                        val stopsChild = child.child("intermediateStops")
                        if (stopsChild.exists()) {
                            for (s in stopsChild.children) {
                                s.value?.toString()?.let { stopsList.add(it) }
                            }
                        } else {
                            val stopsStr = child.child("stops").value?.toString().orEmpty()
                            if (stopsStr.isNotBlank()) {
                                stopsList.addAll(stopsStr.split(",").map { it.trim() })
                            }
                        }

                        loaded.add(
                            BusRoute(
                                id = id,
                                origin = orig,
                                destination = dest,
                                busType = type,
                                departureTime = time,
                                fare = fare,
                                intermediateStops = stopsList
                            )
                        )
                    } catch (_: Exception) {}
                }
                if (loaded.isNotEmpty()) {
                    routesList = loaded
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    val searchResults: List<BusRoute>
        get() {
            val origQuery = origin.trim()
            val destQuery = destination.trim()

            return routesList.filter { route ->
                val originMatch = origQuery.isBlank() ||
                        route.origin.contains(origQuery, ignoreCase = true) ||
                        route.intermediateStops.any { it.contains(origQuery, ignoreCase = true) }

                val destMatch = destQuery.isBlank() ||
                        route.destination.contains(destQuery, ignoreCase = true) ||
                        route.intermediateStops.any { it.contains(destQuery, ignoreCase = true) }

                originMatch && destMatch
            }
        }

    fun updateOrigin(value: String) {
        origin = value
        hasSearched = false
    }

    fun updateDestination(value: String) {
        destination = value
        hasSearched = false
    }

    fun searchRoutes() {
        hasSearched = true
    }

    fun selectTab(tab: String) {
        selectedTab = tab
    }

    fun getAllRoutes(): List<BusRoute> = routesList
}
