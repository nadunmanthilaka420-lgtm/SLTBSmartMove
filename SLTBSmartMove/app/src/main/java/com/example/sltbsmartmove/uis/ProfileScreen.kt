package com.example.sltbsmartmove.uis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sltbsmartmove.viewmodel.AuthViewModel

/**
 * Profile tab — shows the current user's details, their role badge, and a
 * prominent Logout button.
 */
@Composable
fun ProfileScreen(
    viewModel: AuthViewModel,
    padding: PaddingValues,
    onOpenDashboard: (role: String) -> Unit
) {
    val user = viewModel.currentUser
    val profile = viewModel.profile

    val displayName = user?.displayName?.ifBlank { null } ?: profile?.displayName ?: "User"
    val email = user?.email?.ifBlank { null } ?: profile?.email ?: ""
    val uid = user?.uid ?: profile?.uid ?: "demo_uid"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "My Profile",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "User ID: ${uid.take(12)}...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val currentRole = viewModel.userRole
        RoleBadge(role = currentRole)

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { viewModel.logout() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("Sign Out", color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RoleBadge(role: String) {
    val (label, description) = when (role) {
        "driver" -> "Driver / Conductor" to "Access to assigned trips and incident reports."
        "admin" -> "Administrator" to "Full access to all complaints, routes, and management features."
        else -> "Passenger" to "Access to routes, nearby stops, reports, and saved routes."
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Role: $label",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
}
