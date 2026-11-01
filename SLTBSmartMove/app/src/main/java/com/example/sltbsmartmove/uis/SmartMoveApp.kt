package com.example.sltbsmartmove.uis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sltbsmartmove.viewmodel.AuthViewModel
import com.example.sltbsmartmove.viewmodel.RouteViewModel

/**
 * Root composable.
 *
 * Auth gate: if no user is signed in, shows the Login or Register screen.
 * Once signed in, shows the main Scaffold with the bottom navigation bar.
 *
 * Role-based tabs:
 * - Driver: Home, Routes, Assigned (DriverDashboard), Profile
 * - Admin: Home, Routes, Nearby, Report, Saved, Profile, Admin (AdminDashboard)
 * - Passenger: Home, Routes, Nearby, Report, Saved, Profile
 */
@Composable
fun SmartMoveApp(authViewModel: AuthViewModel) {
    val routeViewModel = remember { RouteViewModel() }

    // Auth screens (no bottom nav)
    if (authViewModel.currentUser == null && !authViewModel.isGuestMode) {
        AuthGate(authViewModel)
        return
    }

    val role = authViewModel.userRole

    val tabs = when (role) {
        "driver" -> listOf("Home", "Routes", "Assigned", "Profile")
        "admin" -> listOf("Home", "Routes", "Profile", "Admin")
        else -> listOf("Home", "Routes", "Nearby", "Report", "Saved", "Profile")
    }

    // Ensure selected tab is valid for current role
    if (routeViewModel.selectedTab !in tabs) {
        routeViewModel.selectTab("Home")
    }

    // Main app scaffold with Red Theme styling
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = routeViewModel.selectedTab == tab,
                        onClick = {
                            routeViewModel.selectTab(tab)
                        },
                        icon = { Text(tab.take(1)) },
                        label = { Text(tab) }
                    )
                }
            }
        }
    ) { innerPadding ->

        when (routeViewModel.selectedTab) {

            "Home" -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Text(
                            text = "SLTB SmartMove",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(text = "Smart public transport for Sri Lanka")
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Find Your Bus",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                OutlinedTextField(
                                    value = routeViewModel.origin,
                                    onValueChange = { routeViewModel.updateOrigin(it) },
                                    label = { Text("From town") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = routeViewModel.destination,
                                    onValueChange = { routeViewModel.updateDestination(it) },
                                    label = { Text("To town") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Button(
                                    onClick = { routeViewModel.searchRoutes() },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Search Buses") }
                            }
                        }
                    }

                    item {
                        Text(
                            text = if (routeViewModel.hasSearched) "Search Results" else "Popular Sample Routes",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (routeViewModel.hasSearched) {
                        if (routeViewModel.searchResults.isEmpty()) {
                            item { Text("No matching sample routes found. Try another town.") }
                        } else {
                            items(routeViewModel.searchResults, key = { it.id }) { route ->
                                RouteCard(route)
                            }
                        }
                    } else {
                        items(routeViewModel.getAllRoutes().take(3), key = { it.id }) { route ->
                            RouteCard(route)
                        }
                    }

                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Sample information only. Actual SLTB schedules and fares are not connected yet.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            "Routes" -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "All Routes",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(routeViewModel.getAllRoutes(), key = { it.id }) { route ->
                        RouteCard(route)
                    }
                }
            }

            "Nearby" -> NearbyStopsScreen()

            "Report" -> ComplaintScreen()

            "Saved" -> SavedRoutesScreen(padding = innerPadding)

            "Assigned" -> DriverDashboard(padding = innerPadding)

            "Admin" -> AdminDashboard(padding = innerPadding)

            "Profile" -> ProfileScreen(
                viewModel = authViewModel,
                padding = innerPadding,
                onOpenDashboard = { role ->
                    if (role == "admin") routeViewModel.selectTab("Admin")
                    if (role == "driver") routeViewModel.selectTab("Assigned")
                }
            )
        }
    }
}

// ---- Auth gate composable ---------------------------------------------------

/** Full-screen Login / Register flow, shown when no user is signed in. */
@Composable
private fun AuthGate(viewModel: AuthViewModel) {
    var showRegister by remember { mutableStateOf(false) }

    if (showRegister) {
        RegisterScreen(
            viewModel = viewModel,
            onNavigateToLogin = { showRegister = false }
        )
    } else {
        LoginScreen(
            viewModel = viewModel,
            onNavigateToRegister = { showRegister = true }
        )
    }
}
