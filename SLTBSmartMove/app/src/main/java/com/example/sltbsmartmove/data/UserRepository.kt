package com.example.sltbsmartmove.data

import com.example.sltbsmartmove.model.UserProfile
import com.example.sltbsmartmove.model.UserRole
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * Reads and writes user profile data in Firebase Realtime Database.
 *
 * Database layout:
 *   users/{uid}        — profile (displayName, email, depotId, status)
 *   userRoles/{uid}    — role ("passenger" | "driver" | "manager" | "admin")
 *
 * Security: `userRoles` can only be written by users whose role == "admin"
 * (enforced by Security Rules). All other clients can only read their own role.
 */
class UserRepository {

    private val db = FirebaseDatabase.getInstance()
    private val usersRef = db.getReference("users")
    private val rolesRef = db.getReference("userRoles")

    // -------------------------------------------------------------------------
    // Create profile on first registration
    // -------------------------------------------------------------------------

    /**
     * Writes a new passenger profile to `users/{uid}`.
     * Called once when a user registers. Does not write to `userRoles` — the
     * Security Rules default role for a new user is "passenger".
     */
    fun createProfile(
        uid: String,
        displayName: String,
        email: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val profile = mapOf(
            "uid" to uid,
            "displayName" to displayName,
            "email" to email,
            "depotId" to "",
            "status" to "active"
        )

        // Also write the default role explicitly so the Rules can read it.
        val roleRecord = mapOf("role" to "passenger", "depotId" to "")

        usersRef.child(uid).setValue(profile)
            .addOnSuccessListener {
                // Write the role record — allowed because the user is writing their
                // own node and the Rules permit setting role="passenger" on creation.
                rolesRef.child(uid).setValue(roleRecord)
                    .addOnSuccessListener { onResult(true, null) }
                    .addOnFailureListener { e ->
                        onResult(false, e.friendlyDatabaseMessage())
                    }
            }
            .addOnFailureListener { e ->
                onResult(false, e.friendlyDatabaseMessage())
            }
    }

    // -------------------------------------------------------------------------
    // Read profile
    // -------------------------------------------------------------------------

    /** Reads the profile at `users/{uid}`. */
    fun getProfile(uid: String, onResult: (UserProfile?, String?) -> Unit) {
        val ref = usersRef.child(uid)
        lateinit var listener: ValueEventListener

        val guard = CallbackGuard {
            ref.removeEventListener(listener)
            onResult(null, "No response from server. Check your internet connection.")
        }

        listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!guard.complete()) return
                if (!snapshot.exists()) {
                    onResult(null, null)
                    return
                }
                val profile = UserProfile(
                    uid = uid,
                    displayName = snapshot.text("displayName"),
                    email = snapshot.text("email"),
                    depotId = snapshot.text("depotId"),
                    status = snapshot.text("status").ifBlank { "active" }
                )
                onResult(profile, null)
            }

            override fun onCancelled(error: DatabaseError) {
                if (!guard.complete()) return
                onResult(null, error.friendlyMessage())
            }
        }

        ref.addListenerForSingleValueEvent(listener)
    }

    // -------------------------------------------------------------------------
    // Read role
    // -------------------------------------------------------------------------

    /** Reads the role at `userRoles/{uid}`. Returns "passenger" if not found. */
    fun getRoleForUser(uid: String, onResult: (UserRole) -> Unit) {
        val ref = rolesRef.child(uid)
        lateinit var listener: ValueEventListener

        val guard = CallbackGuard {
            ref.removeEventListener(listener)
            onResult(UserRole()) // default to passenger on timeout
        }

        listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!guard.complete()) return
                val role = UserRole(
                    role = snapshot.text("role").ifBlank { "passenger" },
                    depotId = snapshot.text("depotId")
                )
                onResult(role)
            }

            override fun onCancelled(error: DatabaseError) {
                if (!guard.complete()) return
                onResult(UserRole()) // default to passenger on error
            }
        }

        ref.addListenerForSingleValueEvent(listener)
    }

    // -------------------------------------------------------------------------
    // Admin: read all profiles (admin-only by Security Rules)
    // -------------------------------------------------------------------------

    /** Reads all user profiles. Fails with PERMISSION_DENIED for non-admins. */
    fun getAllProfiles(onResult: (List<UserProfile>?, String?) -> Unit) {
        lateinit var listener: ValueEventListener

        val guard = CallbackGuard {
            usersRef.removeEventListener(listener)
            onResult(null, "No response from server.")
        }

        listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!guard.complete()) return
                val profiles = snapshot.children.mapNotNull { child ->
                    try {
                        UserProfile(
                            uid = child.key.orEmpty(),
                            displayName = child.text("displayName"),
                            email = child.text("email"),
                            depotId = child.text("depotId"),
                            status = child.text("status").ifBlank { "active" }
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                onResult(profiles, null)
            }

            override fun onCancelled(error: DatabaseError) {
                if (!guard.complete()) return
                onResult(null, error.friendlyMessage())
            }
        }

        usersRef.addListenerForSingleValueEvent(listener)
    }

    // -------------------------------------------------------------------------
    // Admin: update complaint status
    // -------------------------------------------------------------------------

    /**
     * Updates the status field of a complaint. Allowed only for admin users
     * (Security Rules enforce this). Valid statuses: SUBMITTED, REVIEWING, RESOLVED.
     */
    fun updateComplaintStatus(
        complaintId: String,
        newStatus: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        FirebaseDatabase.getInstance()
            .getReference("complaints")
            .child(complaintId)
            .child("status")
            .setValue(newStatus)
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { e -> onResult(false, e.friendlyDatabaseMessage()) }
    }
}
