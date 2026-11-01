package com.example.sltbsmartmove.uis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sltbsmartmove.data.AuthRepository
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener

/**
 * Driver / Conductor dashboard.
 *
 * Shows assigned trips from `driverAssignments/{uid}`, `guest_demo_uid`, and `all_drivers` in real-time.
 */
@Composable
fun DriverDashboard(padding: PaddingValues) {
    val db = FirebaseDatabase.getInstance()

    var assignments by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    var loadingAssignments by remember { mutableStateOf(true) }
    var assignmentError by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }

    // Incident report form state
    var incidentDescription by remember { mutableStateOf("") }
    var incidentLocation by remember { mutableStateOf("") }
    var submittingIncident by remember { mutableStateOf(false) }
    var incidentMessage by remember { mutableStateOf<String?>(null) }
    var incidentIsError by remember { mutableStateOf(false) }

    val uid = AuthRepository.currentUid ?: "guest_demo_uid"

    // Load driver assignments in real-time from user UID, guest_demo_uid, and all_drivers pool
    DisposableEffect(uid, refreshKey) {
        loadingAssignments = true
        assignmentError = null

        val dbRef = FirebaseDatabase.getInstance()
        val assignedMap = mutableMapOf<String, Map<String, String>>()
        val targets = listOf(uid, "guest_demo_uid", "all_drivers").distinct()
        val listeners = mutableListOf<Pair<DatabaseReference, ValueEventListener>>()

        fun updateUi() {
            assignments = assignedMap.values.sortedByDescending { it["createdAt"]?.toLongOrNull() ?: 0L }
            loadingAssignments = false
        }

        targets.forEach { target ->
            val ref = dbRef.getReference("driverAssignments").child(target)
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (child in snapshot.children) {
                        try {
                            val id = child.key ?: continue
                            val map = mapOf(
                                "assignmentId" to id,
                                "routeId" to (child.child("routeId").value?.toString() ?: ""),
                                "busId" to (child.child("busId").value?.toString() ?: ""),
                                "date" to (child.child("date").value?.toString() ?: ""),
                                "time" to (child.child("time").value?.toString() ?: ""),
                                "status" to (child.child("status").value?.toString() ?: ""),
                                "createdAt" to (child.child("createdAt").value?.toString() ?: "0")
                            )
                            assignedMap[id] = map
                        } catch (_: Exception) {}
                    }
                    updateUi()
                }
                override fun onCancelled(error: DatabaseError) {
                    loadingAssignments = false
                    assignmentError = error.message
                }
            }
            ref.addValueEventListener(listener)
            listeners.add(ref to listener)
        }

        onDispose {
            listeners.forEach { (ref, listener) ->
                ref.removeEventListener(listener)
            }
        }
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
                "Driver Dashboard",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // ---- Assigned Trips --------------------------------------------------
        item {
            Text("Your Assigned Trips", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        when {
            loadingAssignments -> {
                item {
                    CircularProgressIndicator()
                    Text("Loading assignments...")
                }
            }
            assignmentError != null -> {
                item {
                    Text(
                        "Could not load assignments: $assignmentError",
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(onClick = { refreshKey++ }) { Text("Retry") }
                }
            }
            assignments.isEmpty() -> {
                item { Text("No trips assigned to you yet. Admin can assign buses from the Admin Dashboard.") }
                item {
                    OutlinedButton(onClick = { refreshKey++ }) { Text("Refresh") }
                }
            }
            else -> {
                items(assignments, key = { it["assignmentId"] ?: "" }) { assignment ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "Route: ${assignment["routeId"]}",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text("Bus: ${assignment["busId"]}")
                            Text("Date: ${assignment["date"]}  Time: ${assignment["time"]}")
                            Text("Status: ${assignment["status"]}")
                        }
                    }
                }
                item { OutlinedButton(onClick = { refreshKey++ }) { Text("Refresh") } }
            }
        }

        // ---- Incident Report Form --------------------------------------------
        item {
            Text(
                "Submit Incident Report",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = incidentLocation,
                    onValueChange = { incidentLocation = it },
                    label = { Text("Location or route") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = incidentDescription,
                    onValueChange = { incidentDescription = it },
                    label = { Text("Describe the incident") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                incidentMessage?.let { msg ->
                    Text(
                        msg,
                        color = if (incidentIsError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                }

                Button(
                    onClick = {
                        if (incidentDescription.isBlank() || incidentLocation.isBlank()) {
                            incidentMessage = "Please fill in location and description."
                            incidentIsError = true
                            return@Button
                        }
                        submittingIncident = true
                        incidentMessage = null

                        val ref = db.getReference("incidentReports").push()
                        val record = mapOf(
                            "reportId" to (ref.key ?: ""),
                            "reportedBy" to uid,
                            "location" to incidentLocation.trim(),
                            "description" to incidentDescription.trim(),
                            "status" to "OPEN",
                            "createdAt" to ServerValue.TIMESTAMP
                        )
                        ref.setValue(record)
                            .addOnSuccessListener {
                                submittingIncident = false
                                incidentDescription = ""
                                incidentLocation = ""
                                incidentMessage = "Incident report submitted successfully."
                                incidentIsError = false
                            }
                            .addOnFailureListener { e ->
                                submittingIncident = false
                                incidentMessage = "Submission failed: ${e.localizedMessage}"
                                incidentIsError = true
                            }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !submittingIncident
                ) {
                    Text(if (submittingIncident) "Submitting..." else "Submit Incident Report")
                }
            }
        }
    }
}
