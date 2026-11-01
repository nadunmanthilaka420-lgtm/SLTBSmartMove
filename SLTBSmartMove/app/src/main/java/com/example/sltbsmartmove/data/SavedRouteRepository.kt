package com.example.sltbsmartmove.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * Manages a passenger's saved routes under `savedRoutes/{uid}/{routeId}`.
 *
 * The value stored is the route ID string itself (with `true` as the node value
 * for compactness). Ordering is by key (push order) so latest saved routes
 * appear first after reversal.
 *
 * Security Rules allow each user to read and write only their own subtree.
 */
class SavedRouteRepository {

    private val db = FirebaseDatabase.getInstance()

    // -------------------------------------------------------------------------
    // Save a route
    // -------------------------------------------------------------------------

    fun saveRoute(routeId: String, onResult: (Boolean, String?) -> Unit) {
        AuthRepository.ensureSignedIn { uid, authError ->
            if (uid == null) {
                onResult(false, authError ?: "Not signed in.")
                return@ensureSignedIn
            }

            db.getReference("savedRoutes").child(uid).child(routeId)
                .setValue(true)
                .addOnSuccessListener { onResult(true, null) }
                .addOnFailureListener { e -> onResult(false, e.friendlyDatabaseMessage()) }
        }
    }

    // -------------------------------------------------------------------------
    // Remove a saved route
    // -------------------------------------------------------------------------

    fun removeRoute(routeId: String, onResult: (Boolean, String?) -> Unit) {
        AuthRepository.ensureSignedIn { uid, authError ->
            if (uid == null) {
                onResult(false, authError ?: "Not signed in.")
                return@ensureSignedIn
            }

            db.getReference("savedRoutes").child(uid).child(routeId)
                .removeValue()
                .addOnSuccessListener { onResult(true, null) }
                .addOnFailureListener { e -> onResult(false, e.friendlyDatabaseMessage()) }
        }
    }

    // -------------------------------------------------------------------------
    // Load saved route IDs
    // -------------------------------------------------------------------------

    /**
     * Returns the list of saved route IDs for the signed-in user.
     * Returns them in the order they were saved (newest first after reversal).
     */
    fun getSavedRouteIds(onResult: (List<String>?, String?) -> Unit) {
        AuthRepository.ensureSignedIn { uid, authError ->
            if (uid == null) {
                onResult(null, authError ?: "Not signed in.")
                return@ensureSignedIn
            }

            val ref = db.getReference("savedRoutes").child(uid)
            lateinit var listener: ValueEventListener

            val guard = CallbackGuard {
                ref.removeEventListener(listener)
                onResult(null, "No response from server. Check your internet connection.")
            }

            listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!guard.complete()) return
                    val ids = snapshot.children.mapNotNull { it.key }.reversed()
                    onResult(ids, null)
                }

                override fun onCancelled(error: DatabaseError) {
                    if (!guard.complete()) return
                    onResult(null, error.friendlyMessage())
                }
            }

            ref.addListenerForSingleValueEvent(listener)
        }
    }
}
