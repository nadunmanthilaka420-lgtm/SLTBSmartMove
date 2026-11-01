package com.example.sltbsmartmove.uis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sltbsmartmove.data.SavedRouteRepository

/**
 * Shows the signed-in user's saved route IDs from Firebase and lets them remove entries.
 *
 * Routes are stored as IDs only (the full route data in this build comes from static
 * RouteRepository). A future version can enrich this list by looking up each ID in
 * Firebase when routes are migrated there.
 */
@Composable
fun SavedRoutesScreen(padding: PaddingValues) {
    val repository = remember { SavedRouteRepository() }

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var savedIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshKey) {
        loading = true
        error = null
        repository.getSavedRouteIds { ids, err ->
            loading = false
            if (err != null) {
                error = err
            } else {
                savedIds = ids.orEmpty()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Saved Routes",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp)
        )

        when {
            loading -> {
                CircularProgressIndicator()
                Text("Loading saved routes...")
            }

            error != null -> {
                Text(
                    "Could not load saved routes: $error",
                    color = MaterialTheme.colorScheme.error
                )
                Button(onClick = { refreshKey++ }) { Text("Try Again") }
            }

            savedIds.isEmpty() -> {
                Text("You haven't saved any routes yet.")
                Text(
                    "Visit the Routes tab and save routes you use often.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = { refreshKey++ }) { Text("Refresh") }
            }

            else -> {
                Text("${savedIds.size} saved route(s):")

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(savedIds, key = { it }) { routeId ->
                        var removing by remember { mutableStateOf(false) }
                        var removeError by remember { mutableStateOf<String?>(null) }

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Route ID: $routeId",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )

                                removeError?.let {
                                    Text(
                                        it,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                Button(
                                    onClick = {
                                        removing = true
                                        removeError = null
                                        repository.removeRoute(routeId) { ok, err ->
                                            removing = false
                                            if (ok) {
                                                refreshKey++
                                            } else {
                                                removeError = err ?: "Could not remove route."
                                            }
                                        }
                                    },
                                    enabled = !removing,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer
                                    )
                                ) {
                                    Text(
                                        if (removing) "Removing..." else "Remove",
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
