package com.example.sltbsmartmove.uis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.sltbsmartmove.data.ComplaintRepository
import com.example.sltbsmartmove.model.Complaint
import java.text.DateFormat
import java.util.Date

/**
 * Lists the signed-in user's own complaints read back from Firebase.
 * Change [refreshKey] to reload (e.g. after a new submission).
 */
@Composable
fun MyReportsSection(refreshKey: Int) {
    val repository = remember { ComplaintRepository() }

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reports by remember { mutableStateOf<List<Complaint>>(emptyList()) }
    var retryTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshKey, retryTick) {
        loading = true
        error = null

        repository.getMyComplaints { list, errorMessage ->
            loading = false
            if (errorMessage != null) {
                error = errorMessage
            } else {
                reports = list.orEmpty()
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "My Reports",
            style = MaterialTheme.typography.titleLarge
        )

        when {
            loading -> {
                CircularProgressIndicator()
                Text("Loading your reports...")
            }

            error != null -> {
                Text(
                    "Could not load reports: $error",
                    color = MaterialTheme.colorScheme.error
                )
                Button(onClick = { retryTick++ }) {
                    Text("Try Again")
                }
            }

            reports.isEmpty() -> {
                Text("You have not submitted any reports yet.")
                OutlinedButton(onClick = { retryTick++ }) {
                    Text("Refresh")
                }
            }

            else -> {
                reports.forEach { report ->
                    ReportCard(report)
                }
                OutlinedButton(onClick = { retryTick++ }) {
                    Text("Refresh")
                }
            }
        }
    }
}

@Composable
private fun ReportCard(report: Complaint) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                report.subject.ifBlank { "(no subject)" },
                style = MaterialTheme.typography.titleMedium
            )
            Text("Category: ${report.category}")
            Text(report.description)
            Text("Status: ${report.status}")
            Text(
                if (report.createdAt > 0L) {
                    "Submitted: " + DateFormat.getDateTimeInstance()
                        .format(Date(report.createdAt))
                } else {
                    "Submitted: date unavailable"
                },
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                when {
                    report.photoUploaded -> "Photo: uploaded"
                    report.photoFileName.isNotBlank() ->
                        "Photo: kept on the phone that sent the report " +
                                "(not uploaded)"
                    else -> "Photo: none"
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
