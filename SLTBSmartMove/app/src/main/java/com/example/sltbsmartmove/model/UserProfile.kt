package com.example.sltbsmartmove.model

import com.google.firebase.database.IgnoreExtraProperties

/**
 * A user's profile stored under `users/{uid}` in Firebase Realtime Database.
 *
 * The role field is for display only on the client. Authoritative role data is
 * stored separately in `userRoles/{uid}` and cannot be written by clients
 * (enforced by Firebase Security Rules).
 *
 * Allowed roles: "passenger", "driver", "manager", "admin".
 */
@IgnoreExtraProperties
data class UserProfile(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val depotId: String = "",        // depot ID for managers and drivers
    val status: String = "active"    // "active" | "suspended"
)

/**
 * A user's role stored under `userRoles/{uid}` in Firebase Realtime Database.
 *
 * This node can only be written by administrators or trusted backend code.
 * The Security Rules reject any client write to this node unless the writer
 * has role == "admin".
 */
@IgnoreExtraProperties
data class UserRole(
    val role: String = "passenger",  // "passenger" | "driver" | "manager" | "admin"
    val depotId: String = ""
)
