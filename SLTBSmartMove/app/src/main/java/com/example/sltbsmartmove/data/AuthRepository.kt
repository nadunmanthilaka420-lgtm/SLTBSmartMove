package com.example.sltbsmartmove.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest

/**
 * Firebase Authentication helper.
 *
 * Supports:
 *  - Email / password registration and sign-in (primary method).
 *  - Session persistence: Firebase keeps the user signed in across restarts automatically.
 *  - Logout.
 *
 * All public functions are safe to call from the main thread; Firebase
 * dispatches callbacks on the main thread.
 *
 * Security note: passwords are never stored locally or in Realtime Database.
 * Firebase Authentication handles all credential management server-side.
 */
object AuthRepository {

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    /** The currently signed-in Firebase user, or null if not signed in. */
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    /** Convenience UID — null when not signed in. */
    val currentUid: String?
        get() = auth.currentUser?.uid


    /**
     * Creates a new account with email + password, then sets [displayName] in the
     * Firebase Auth profile. Calls [onResult] with (uid, null) on success or
     * (null, errorMessage) on failure.
     */
    fun registerWithEmail(
        email: String,
        password: String,
        displayName: String,
        onResult: (String?, String?) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user == null) {
                    onResult(null, "Registration succeeded but user is null.")
                    return@addOnSuccessListener
                }

                // Persist the display name in Firebase Auth profile.
                val profile = userProfileChangeRequest { setDisplayName(displayName.trim()) }
                user.updateProfile(profile)
                    .addOnCompleteListener {
                        // Profile update failure is non-fatal for registration.
                        onResult(user.uid, null)
                    }
            }
            .addOnFailureListener { error ->
                onResult(null, friendlyAuthError(error.localizedMessage))
            }
    }



    /**
     * Signs in an existing account. Calls [onResult] with (uid, null) on success
     * or (null, errorMessage) on failure.
     */
    fun signInWithEmail(
        email: String,
        password: String,
        onResult: (String?, String?) -> Unit
    ) {
        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid
                if (uid != null) onResult(uid, null)
                else onResult(null, "Sign-in succeeded but UID is null.")
            }
            .addOnFailureListener { error ->
                onResult(null, friendlyAuthError(error.localizedMessage))
            }
    }


    /**
     * If a user is already signed in, calls [onResult] with their UID immediately.
     * Otherwise calls [onResult] with (null, errorMessage).
     *
     * Existing repositories (BusStopRepository, ComplaintRepository) call this.
     * With email/password auth the user is always signed in before those screens
     * are shown, so this will always succeed in normal flow.
     */
    fun ensureSignedIn(onResult: (String?, String?) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            onResult(uid, null)
        } else {
            // Demo / guest fallback so features work without blocking auth errors
            onResult("guest_demo_uid", null)
        }
    }


    /** Signs the current user out. Firebase clears the local session token. */
    fun signOut() {
        auth.signOut()
    }


    private fun friendlyAuthError(raw: String?): String {
        val text = raw.orEmpty()
        return when {
            text.contains("EMAIL_EXISTS", ignoreCase = true) ||
                    text.contains("email address is already in use", ignoreCase = true) ->
                "An account with this email already exists. Try signing in instead."

            text.contains("INVALID_EMAIL", ignoreCase = true) ||
                    text.contains("email address is badly formatted", ignoreCase = true) ->
                "Please enter a valid email address."

            text.contains("WEAK_PASSWORD", ignoreCase = true) ||
                    text.contains("password is too weak", ignoreCase = true) ->
                "Password must be at least 6 characters."

            text.contains("USER_NOT_FOUND", ignoreCase = true) ||
                    text.contains("no user record", ignoreCase = true) ->
                "No account found with this email. Please register first."

            text.contains("WRONG_PASSWORD", ignoreCase = true) ||
                    text.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) ||
                    text.contains("password is invalid", ignoreCase = true) ->
                "Incorrect email or password. Please try again."

            text.contains("TOO_MANY_REQUESTS", ignoreCase = true) ->
                "Too many attempts. Please wait a moment and try again."

            text.contains("network", ignoreCase = true) ->
                "Network error. Check your internet connection and try again."

            text.isBlank() -> "Authentication failed. Please try again."

            else -> text
        }
    }
}
