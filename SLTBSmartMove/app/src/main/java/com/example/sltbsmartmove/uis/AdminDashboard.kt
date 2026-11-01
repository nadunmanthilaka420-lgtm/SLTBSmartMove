package com.example.sltbsmartmove.uis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sltbsmartmove.data.ComplaintRepository
import com.example.sltbsmartmove.data.UserRepository
import com.example.sltbsmartmove.model.Complaint
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import java.text.DateFormat
import java.util.Date

/**
 * Administrator dashboard.
 *
 * Full access for administrators:
 * - Assign buses to drivers using collapsible, scrollable selectors.
 * - Add bus routes with intermediate stops stored in Firebase.
 * - Manage existing routes (Update & Delete).
 * - Database Initializer & Seeder (saves catalog records one by one).
 * - Review, update status, and delete passenger complaints.
 * - Review and manage driver incident reports (`incidentReports`).
 */
@Composable
fun AdminDashboard(padding: PaddingValues) {
    val complaintRepo = remember { ComplaintRepository() }
    val userRepo = remember { UserRepository() }

    var complaints by remember { mutableStateOf<List<Complaint>>(emptyList()) }
    var loadingComplaints by remember { mutableStateOf(true) }
    var complaintError by remember { mutableStateOf<String?>(null) }

    var incidents by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    var loadingIncidents by remember { mutableStateOf(true) }

    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshKey) {
        loadingComplaints = true
        complaintError = null
        complaintRepo.getAllComplaints { list, err ->
            loadingComplaints = false
            if (err != null) {
                complaintError = err
            } else {
                complaints = list.orEmpty()
            }
        }

        loadingIncidents = true
        FirebaseDatabase.getInstance().getReference("incidentReports")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    loadingIncidents = false
                    incidents = snapshot.children.mapNotNull { child ->
                        try {
                            mapOf(
                                "reportId" to child.key.orEmpty(),
                                "reportedBy" to (child.child("reportedBy").value?.toString() ?: ""),
                                "location" to (child.child("location").value?.toString() ?: ""),
                                "description" to (child.child("description").value?.toString() ?: ""),
                                "status" to (child.child("status").value?.toString() ?: ""),
                                "createdAt" to (child.child("createdAt").value?.toString() ?: "0")
                            )
                        } catch (_: Exception) { null }
                    }.sortedByDescending { it["createdAt"]?.toLongOrNull() ?: 0L }
                }
                override fun onCancelled(error: DatabaseError) {
                    loadingIncidents = false
                }
            })
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Admin Dashboard",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // 1. Assign Bus to Driver Card (Scrollable expandable selectors)
        item {
            AssignBusCard()
        }

        // 2. Add Bus Route with Stops Card
        item {
            AddRouteCard()
        }

        // 3. Manage Existing Routes (Update & Delete)
        item {
            AdminManageRoutesSection()
        }

        // 4. Database Seeder Card (saves records one by one)
        item {
            DatabaseSeederCard()
        }

        // 5. Driver Incident Reports Section
        item {
            Text(
                "Driver Incident Reports (${incidents.size})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        when {
            loadingIncidents -> {
                item { CircularProgressIndicator(); Text("Loading incident reports...") }
            }
            incidents.isEmpty() -> {
                item { Text("No incident reports submitted by drivers yet.") }
            }
            else -> {
                items(incidents, key = { it["reportId"] ?: "" }) { incident ->
                    AdminIncidentCard(
                        incident = incident,
                        onStatusUpdated = { refreshKey++ }
                    )
                }
            }
        }

        // 6. Passenger Complaints Section
        item {
            Text(
                "Passenger Complaints (${complaints.size})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        when {
            loadingComplaints -> {
                item {
                    CircularProgressIndicator()
                    Text("Loading complaints...")
                }
            }
            complaintError != null -> {
                item {
                    Text(
                        "Could not load complaints: $complaintError",
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(onClick = { refreshKey++ }) { Text("Retry") }
                }
            }
            complaints.isEmpty() -> {
                item { Text("No complaints have been submitted yet.") }
            }
            else -> {
                items(complaints, key = { it.complaintId }) { complaint ->
                    AdminComplaintCard(
                        complaint = complaint,
                        userRepo = userRepo,
                        onStatusUpdated = { refreshKey++ }
                    )
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { refreshKey++ },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Refresh All")
            }
        }
    }
}

@Composable
private fun AdminManageRoutesSection() {
    val db = FirebaseDatabase.getInstance()
    var routes by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshKey) {
        loading = true
        db.getReference("routes").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                loading = false
                val list = mutableListOf<Map<String, Any>>()
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
                        }

                        list.add(
                            mapOf(
                                "id" to id,
                                "origin" to orig,
                                "destination" to dest,
                                "busType" to type,
                                "departureTime" to time,
                                "fare" to fare,
                                "intermediateStops" to stopsList
                            )
                        )
                    } catch (_: Exception) {}
                }
                routes = list
            }
            override fun onCancelled(error: DatabaseError) {
                loading = false
            }
        })
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Manage Existing Routes (${routes.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            if (loading) {
                CircularProgressIndicator()
            } else if (routes.isEmpty()) {
                Text("No routes found in database.")
            } else {
                routes.forEach { route ->
                    AdminRouteEditCard(route = route, onActionDone = { refreshKey++ })
                }
            }
        }
    }
}

@Composable
private fun AdminRouteEditCard(
    route: Map<String, Any>,
    onActionDone: () -> Unit
) {
    val routeId = route["id"] as? String ?: return
    var origin by remember { mutableStateOf(route["origin"] as? String ?: "") }
    var destination by remember { mutableStateOf(route["destination"] as? String ?: "") }
    var fare by remember { mutableStateOf(route["fare"] as? String ?: "") }
    val stopsList = (route["intermediateStops"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
    var intermediateStops by remember { mutableStateOf(stopsList.joinToString(", ")) }

    var isEditing by remember { mutableStateOf(false) }
    var updating by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (!isEditing) {
                Text(
                    text = "${route["origin"]} → ${route["destination"]}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(text = "Type: ${route["busType"]} | Fare: ${route["fare"]}")
                if (stopsList.isNotEmpty()) {
                    Text(text = "Stops: ${stopsList.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                }

                message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { isEditing = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Edit / Update", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(
                        onClick = {
                            updating = true
                            FirebaseDatabase.getInstance().getReference("routes").child(routeId).removeValue()
                                .addOnSuccessListener {
                                    updating = false
                                    onActionDone()
                                }
                                .addOnFailureListener { updating = false }
                        },
                        enabled = !updating,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Delete", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                Text("Edit Route", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(
                    value = origin,
                    onValueChange = { origin = it },
                    label = { Text("Origin") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = destination,
                    onValueChange = { destination = it },
                    label = { Text("Destination") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = fare,
                    onValueChange = { fare = it },
                    label = { Text("Fare") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = intermediateStops,
                    onValueChange = { intermediateStops = it },
                    label = { Text("Intermediate Stops (comma separated)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            updating = true
                            val stopsArray = intermediateStops.split(",").map { it.trim() }.filter { it.isNotBlank() }
                            val updatedData = mapOf(
                                "id" to routeId,
                                "origin" to origin.trim(),
                                "destination" to destination.trim(),
                                "busType" to (route["busType"] as? String ?: "Intercity Bus"),
                                "departureTime" to (route["departureTime"] as? String ?: "08:00 AM"),
                                "fare" to fare.trim(),
                                "intermediateStops" to stopsArray
                            )
                            FirebaseDatabase.getInstance().getReference("routes").child(routeId).setValue(updatedData)
                                .addOnSuccessListener {
                                    updating = false
                                    isEditing = false
                                    onActionDone()
                                }
                                .addOnFailureListener { updating = false }
                        },
                        enabled = !updating,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(
                        onClick = { isEditing = false },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (updating) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun AddRouteCard() {
    var origin by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var busType by remember { mutableStateOf("Intercity Bus") }
    var departureTime by remember { mutableStateOf("08:00 AM") }
    var fare by remember { mutableStateOf("LKR 300.00") }
    var intermediateStops by remember { mutableStateOf("") }

    var adding by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Add Bus Route with Stops",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Add a route with intermediate stops. Passengers searching via origin, destination, or intermediate stops will find this bus.",
                style = MaterialTheme.typography.bodySmall
            )

            OutlinedTextField(
                value = origin,
                onValueChange = { origin = it },
                label = { Text("Origin (e.g. Colombo)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = destination,
                onValueChange = { destination = it },
                label = { Text("Destination (e.g. Kandy)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = busType,
                onValueChange = { busType = it },
                label = { Text("Bus Type (e.g. Intercity Bus)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = departureTime,
                onValueChange = { departureTime = it },
                label = { Text("Departure Time (e.g. 08:00 AM)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = fare,
                onValueChange = { fare = it },
                label = { Text("Fare (e.g. LKR 350.00)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = intermediateStops,
                onValueChange = { intermediateStops = it },
                label = { Text("Intermediate Stops (comma separated)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            statusMessage?.let {
                Text(
                    it,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick = {
                    if (origin.isBlank() || destination.isBlank()) {
                        statusMessage = "Please fill in origin and destination."
                        isError = true
                        return@Button
                    }
                    adding = true
                    statusMessage = null

                    val db = FirebaseDatabase.getInstance()
                    val routeRef = db.getReference("routes").push()
                    val routeId = routeRef.key ?: "route_${System.currentTimeMillis()}"

                    val stopsList = intermediateStops.split(",").map { it.trim() }.filter { it.isNotBlank() }

                    val routeData = mapOf(
                        "id" to routeId,
                        "origin" to origin.trim(),
                        "destination" to destination.trim(),
                        "busType" to busType.trim(),
                        "departureTime" to departureTime.trim(),
                        "fare" to fare.trim(),
                        "intermediateStops" to stopsList
                    )

                    routeRef.setValue(routeData)
                        .addOnSuccessListener {
                            adding = false
                            statusMessage = "Route successfully added to database with ${stopsList.size} stops!"
                            isError = false
                            origin = ""
                            destination = ""
                            intermediateStops = ""
                        }
                        .addOnFailureListener { e ->
                            adding = false
                            statusMessage = "Failed to add route: ${e.localizedMessage}"
                            isError = true
                        }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !adding
            ) {
                if (adding) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Add Bus Route to Database")
                }
            }
        }
    }
}

@Composable
private fun AdminIncidentCard(
    incident: Map<String, String>,
    onStatusUpdated: () -> Unit
) {
    var updating by remember { mutableStateOf(false) }
    val db = FirebaseDatabase.getInstance()
    val reportId = incident["reportId"].orEmpty()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "Location: ${incident["location"]}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text("Driver: ${incident["reportedBy"]?.take(12)}...")
            Text(incident["description"].orEmpty())
            Text(
                "Submitted: " + try {
                    val ts = incident["createdAt"]?.toLong() ?: 0L
                    if (ts > 0L) DateFormat.getDateTimeInstance().format(Date(ts)) else "unknown"
                } catch (_: Exception) { "unknown" },
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                "Status: ${incident["status"]}",
                fontWeight = FontWeight.SemiBold,
                color = when (incident["status"]) {
                    "OPEN" -> MaterialTheme.colorScheme.primary
                    "RESOLVED" -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (incident["status"] != "RESOLVED") {
                    Button(
                        onClick = {
                            updating = true
                            db.getReference("incidentReports").child(reportId).child("status").setValue("RESOLVED")
                                .addOnSuccessListener {
                                    updating = false
                                    onStatusUpdated()
                                }
                                .addOnFailureListener { updating = false }
                        },
                        enabled = !updating,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Resolve", style = MaterialTheme.typography.bodySmall)
                    }
                }

                OutlinedButton(
                    onClick = {
                        updating = true
                        db.getReference("incidentReports").child(reportId).removeValue()
                            .addOnSuccessListener {
                                updating = false
                                onStatusUpdated()
                            }
                            .addOnFailureListener { updating = false }
                    },
                    enabled = !updating,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Delete", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (updating) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun AssignBusCard() {
    val db = FirebaseDatabase.getInstance()

    var drivers by remember { mutableStateOf<List<Pair<String, String>>>(listOf("guest_demo_uid" to "Demo Driver (guest_demo_uid)")) }
    var buses by remember { mutableStateOf<List<String>>(listOf("NB-1234 (Intercity)", "NC-5678 (Ordinary)", "GA-9012 (Express)")) }
    var routes by remember { mutableStateOf<List<String>>(listOf("Colombo - Kandy", "Negombo - Colombo", "Galle - Colombo")) }

    var selectedDriverUid by remember { mutableStateOf("guest_demo_uid") }
    var selectedDriverLabel by remember { mutableStateOf("Demo Driver (guest_demo_uid)") }
    var selectedBusId by remember { mutableStateOf("NB-1234 (Intercity)") }
    var selectedRouteId by remember { mutableStateOf("Colombo - Kandy") }

    var date by remember { mutableStateOf("2025-02-25") }
    var time by remember { mutableStateOf("08:00 AM") }

    // Scrollable picker expansion states
    var showDriverPicker by remember { mutableStateOf(false) }
    var showBusPicker by remember { mutableStateOf(false) }
    var showRoutePicker by remember { mutableStateOf(false) }

    var assigning by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        db.getReference("users").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<Pair<String, String>>()
                for (child in snapshot.children) {
                    val uid = child.key ?: continue
                    val name = child.child("displayName").value?.toString() ?: "Driver"
                    list.add(uid to "$name ($uid)")
                }
                if (list.isNotEmpty()) {
                    drivers = list
                    selectedDriverUid = list[0].first
                    selectedDriverLabel = list[0].second
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        db.getReference("buses").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<String>()
                for (child in snapshot.children) {
                    val reg = child.child("registrationNo").value?.toString() ?: child.key.orEmpty()
                    val type = child.child("type").value?.toString() ?: ""
                    list.add(if (type.isNotBlank()) "$reg ($type)" else reg)
                }
                if (list.isNotEmpty()) {
                    buses = list
                    selectedBusId = list[0]
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        db.getReference("routes").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<String>()
                for (child in snapshot.children) {
                    val origin = child.child("origin").value?.toString().orEmpty()
                    val dest = child.child("destination").value?.toString().orEmpty()
                    if (origin.isNotBlank() && dest.isNotBlank()) {
                        list.add("$origin - $dest")
                    }
                }
                if (list.isNotEmpty()) {
                    routes = list
                    selectedRouteId = list[0]
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Assign Bus to Driver",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Select driver, bus, and route from database lists:",
                style = MaterialTheme.typography.bodySmall
            )

            // --- Driver Selector ---
            Text("Driver:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(
                onClick = { showDriverPicker = !showDriverPicker; showBusPicker = false; showRoutePicker = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Selected: $selectedDriverLabel  (Tap to change)", style = MaterialTheme.typography.bodySmall)
            }
            if (showDriverPicker) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(drivers) { (uid, label) ->
                            OutlinedButton(
                                onClick = {
                                    selectedDriverUid = uid
                                    selectedDriverLabel = label
                                    showDriverPicker = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selectedDriverUid == uid) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Text(label, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // --- Bus Selector ---
            Text("Bus:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(
                onClick = { showBusPicker = !showBusPicker; showDriverPicker = false; showRoutePicker = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Selected: $selectedBusId  (Tap to change)", style = MaterialTheme.typography.bodySmall)
            }
            if (showBusPicker) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(buses) { bus ->
                            OutlinedButton(
                                onClick = {
                                    selectedBusId = bus
                                    showBusPicker = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selectedBusId == bus) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Text(bus, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // --- Route Selector ---
            Text("Route:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(
                onClick = { showRoutePicker = !showRoutePicker; showDriverPicker = false; showRoutePicker = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Selected: $selectedRouteId  (Tap to change)", style = MaterialTheme.typography.bodySmall)
            }
            if (showRoutePicker) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(routes) { route ->
                            OutlinedButton(
                                onClick = {
                                    selectedRouteId = route
                                    showRoutePicker = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selectedRouteId == route) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Text(route, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = date,
                onValueChange = { date = it },
                label = { Text("Date (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = time,
                onValueChange = { time = it },
                label = { Text("Time (e.g. 08:00 AM)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            statusMessage?.let {
                Text(
                    it,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick = {
                    assigning = true
                    statusMessage = null

                    val dbRef = FirebaseDatabase.getInstance()
                    val assignmentId = "assignment_${System.currentTimeMillis()}"

                    val assignmentData = mapOf(
                        "assignmentId" to assignmentId,
                        "busId" to selectedBusId,
                        "routeId" to selectedRouteId,
                        "date" to date.trim(),
                        "time" to time.trim(),
                        "status" to "ASSIGNED",
                        "createdAt" to ServerValue.TIMESTAMP
                    )

                    dbRef.getReference("driverAssignments").child(selectedDriverUid).child(assignmentId).setValue(assignmentData)
                    dbRef.getReference("driverAssignments").child("guest_demo_uid").child(assignmentId).setValue(assignmentData)
                    dbRef.getReference("driverAssignments").child("all_drivers").child(assignmentId).setValue(assignmentData)
                        .addOnSuccessListener {
                            assigning = false
                            statusMessage = "Bus successfully assigned to driver!"
                            isError = false
                        }
                        .addOnFailureListener { e ->
                            assigning = false
                            statusMessage = "Assignment failed: ${e.localizedMessage}"
                            isError = true
                        }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !assigning
            ) {
                if (assigning) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Assign Bus to Driver")
                }
            }
        }
    }
}

@Composable
private fun DatabaseSeederCard() {
    var seeding by remember { mutableStateOf(false) }
    var seedStatus by remember { mutableStateOf<String?>(null) }
    var seedError by remember { mutableStateOf<String?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Firebase Database Initializer & Seeder",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Saves sample records (stops, routes, buses, depots, announcements) one by one to your Firebase Realtime Database.",
                style = MaterialTheme.typography.bodySmall
            )

            seedStatus?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            seedError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = {
                    seeding = true
                    seedStatus = "Preparing records..."
                    seedError = null

                    val db = FirebaseDatabase.getInstance()

                    val stops = listOf(
                        mapOf("stopId" to "stop_1", "name" to "Colombo Fort Central Bus Stand", "town" to "Colombo", "latitude" to 6.9344, "longitude" to 79.8507, "isDemoData" to true),
                        mapOf("stopId" to "stop_2", "name" to "Negombo Main Bus Stand", "town" to "Negombo", "latitude" to 7.2094, "longitude" to 79.8385, "isDemoData" to true),
                        mapOf("stopId" to "stop_3", "name" to "Kandy Good Shed Bus Station", "town" to "Kandy", "latitude" to 7.2906, "longitude" to 80.6337, "isDemoData" to true),
                        mapOf("stopId" to "stop_4", "name" to "Galle Central Bus Station", "town" to "Galle", "latitude" to 6.0329, "longitude" to 80.2168, "isDemoData" to true),
                        mapOf("stopId" to "stop_5", "name" to "Kurunegala Central Bus Stand", "town" to "Kurunegala", "latitude" to 7.4863, "longitude" to 80.3623, "isDemoData" to true)
                    )

                    val routes = listOf(
                        mapOf("id" to 1, "origin" to "Negombo", "destination" to "Colombo", "busType" to "Ordinary Bus", "departureTime" to "07:00 AM", "fare" to "LKR 150.00"),
                        mapOf("id" to 2, "origin" to "Kandy", "destination" to "Colombo", "busType" to "Intercity Bus", "departureTime" to "08:00 AM", "fare" to "LKR 350.00"),
                        mapOf("id" to 3, "origin" to "Galle", "destination" to "Colombo", "busType" to "Express Bus", "departureTime" to "09:00 AM", "fare" to "LKR 300.00")
                    )

                    val buses = listOf(
                        mapOf("busId" to "bus_1", "registrationNo" to "NB-1234", "type" to "Intercity", "status" to "Active", "depotId" to "COLOMBO_CENTRAL"),
                        mapOf("busId" to "bus_2", "registrationNo" to "NC-5678", "type" to "Ordinary", "status" to "Active", "depotId" to "KANDY_DEPOT"),
                        mapOf("busId" to "bus_3", "registrationNo" to "GA-9012", "type" to "Express", "status" to "Active", "depotId" to "GALLE_DEPOT")
                    )

                    val depots = listOf(
                        mapOf("depotId" to "COLOMBO_CENTRAL", "name" to "Colombo Central Depot", "location" to "Colombo"),
                        mapOf("depotId" to "KANDY_DEPOT", "name" to "Kandy Main Depot", "location" to "Kandy"),
                        mapOf("depotId" to "GALLE_DEPOT", "name" to "Galle Depot", "location" to "Galle")
                    )

                    val announcements = listOf(
                        mapOf("announcementId" to "ann_1", "title" to "New Schedule", "message" to "Timetable updated for Colombo-Kandy route.", "date" to "2025-02-20")
                    )

                    fun seedAnnouncements(index: Int) {
                        if (index < announcements.size) {
                            val ann = announcements[index]
                            val id = ann["announcementId"] as String
                            seedStatus = "Saving announcement ${index + 1} of ${announcements.size}: ${ann["title"]}"
                            db.getReference("announcements").child(id).setValue(ann)
                                .addOnSuccessListener { seedAnnouncements(index + 1) }
                                .addOnFailureListener { e ->
                                    seeding = false
                                    seedError = "Failed at announcement $id: ${e.localizedMessage}"
                                }
                        } else {
                            seeding = false
                            seedStatus = "All records successfully saved to Firebase one by one!"
                        }
                    }

                    fun seedDepots(index: Int) {
                        if (index < depots.size) {
                            val depot = depots[index]
                            val id = depot["depotId"] as String
                            seedStatus = "Saving depot ${index + 1} of ${depots.size}: ${depot["name"]}"
                            db.getReference("depots").child(id).setValue(depot)
                                .addOnSuccessListener { seedDepots(index + 1) }
                                .addOnFailureListener { e ->
                                    seeding = false
                                    seedError = "Failed at depot $id: ${e.localizedMessage}"
                                }
                        } else {
                            seedAnnouncements(0)
                        }
                    }

                    fun seedBuses(index: Int) {
                        if (index < buses.size) {
                            val bus = buses[index]
                            val id = bus["busId"] as String
                            seedStatus = "Saving bus ${index + 1} of ${buses.size}: ${bus["registrationNo"]}"
                            db.getReference("buses").child(id).setValue(bus)
                                .addOnSuccessListener { seedBuses(index + 1) }
                                .addOnFailureListener { e ->
                                    seeding = false
                                    seedError = "Failed at bus $id: ${e.localizedMessage}"
                                }
                        } else {
                            seedDepots(0)
                        }
                    }

                    fun seedRoutes(index: Int) {
                        if (index < routes.size) {
                            val route = routes[index]
                            val id = route["id"].toString()
                            seedStatus = "Saving route ${index + 1} of ${routes.size}: ${route["origin"]} to ${route["destination"]}"
                            db.getReference("routes").child(id).setValue(route)
                                .addOnSuccessListener { seedRoutes(index + 1) }
                                .addOnFailureListener { e ->
                                    seeding = false
                                    seedError = "Failed at route $id: ${e.localizedMessage}"
                                }
                        } else {
                            seedBuses(0)
                        }
                    }

                    fun seedStops(index: Int) {
                        if (index < stops.size) {
                            val stop = stops[index]
                            val id = stop["stopId"] as String
                            seedStatus = "Saving stop ${index + 1} of ${stops.size}: ${stop["name"]}"
                            db.getReference("stops").child(id).setValue(stop)
                                .addOnSuccessListener { seedStops(index + 1) }
                                .addOnFailureListener { e ->
                                    seeding = false
                                    seedError = "Failed at stop $id: ${e.localizedMessage}"
                                }
                        } else {
                            seedRoutes(0)
                        }
                    }

                    seedStops(0)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !seeding
            ) {
                if (seeding) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Save All Records One by One to Firebase")
                }
            }
        }
    }
}

@Composable
private fun AdminComplaintCard(
    complaint: Complaint,
    userRepo: UserRepository,
    onStatusUpdated: () -> Unit
) {
    var updating by remember { mutableStateOf(false) }
    var updateError by remember { mutableStateOf<String?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                complaint.subject.ifBlank { "(no subject)" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text("Category: ${complaint.category}")
            Text("User: ${complaint.userId.take(12)}...")
            Text(complaint.description)
            Text(
                "Submitted: " + if (complaint.createdAt > 0L) {
                    DateFormat.getDateTimeInstance().format(Date(complaint.createdAt))
                } else "unknown",
                style = MaterialTheme.typography.bodySmall
            )

            if (complaint.photoUrl.isNotBlank()) {
                Text(
                    "📷 Photo attached (view URL in Firebase Console)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Status badge
            Text(
                "Status: ${complaint.status}",
                fontWeight = FontWeight.SemiBold,
                color = when (complaint.status) {
                    "SUBMITTED" -> MaterialTheme.colorScheme.primary
                    "REVIEWING" -> MaterialTheme.colorScheme.tertiary
                    "RESOLVED" -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )

            updateError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            // Status update buttons & Delete button
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (complaint.status != "RESOLVED") {
                    if (complaint.status != "REVIEWING") {
                        Button(
                            onClick = {
                                updating = true
                                updateError = null
                                userRepo.updateComplaintStatus(complaint.complaintId, "REVIEWING") { ok, err ->
                                    updating = false
                                    if (ok) onStatusUpdated()
                                    else updateError = err
                                }
                            },
                            enabled = !updating,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reviewing", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Button(
                        onClick = {
                            updating = true
                            updateError = null
                            userRepo.updateComplaintStatus(complaint.complaintId, "RESOLVED") { ok, err ->
                                updating = false
                                if (ok) onStatusUpdated()
                                else updateError = err
                            }
                        },
                        enabled = !updating,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Resolved", style = MaterialTheme.typography.bodySmall)
                    }
                }

                // Delete Complaint button
                OutlinedButton(
                    onClick = {
                        updating = true
                        updateError = null
                        FirebaseDatabase.getInstance()
                            .getReference("complaints")
                            .child(complaint.complaintId)
                            .removeValue()
                            .addOnSuccessListener {
                                updating = false
                                onStatusUpdated()
                            }
                            .addOnFailureListener { e ->
                                updating = false
                                updateError = e.localizedMessage
                            }
                    },
                    enabled = !updating,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Delete", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (updating) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
