package com.example.sltbsmartmove.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.sltbsmartmove.data.AuthRepository
import com.example.sltbsmartmove.data.UserRepository
import com.example.sltbsmartmove.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

/**
 * Holds authentication state and drives the Login / Register / Profile screens.
 *
 * Auth state is the source of truth for whether to show the login gate or the
 * main app. [currentUser] is updated whenever Firebase notifies us of a session
 * change (login, logout, or app restart).
 */
class AuthViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val userRepository = UserRepository()



    /** The currently signed-in Firebase user, or null. */
    var currentUser: FirebaseUser? by mutableStateOf(auth.currentUser)
        private set

    /** The loaded profile (role, displayName, etc.) for the signed-in user. */
    var profile: UserProfile? by mutableStateOf(null)
        private set

    /** The authenticated role of the current user. Defaults to "passenger". */
    var userRole: String by mutableStateOf("passenger")
        private set

    /** True when signed in via Guest / Demo Mode (bypasses Firebase auth errors). */
    var isGuestMode by mutableStateOf(false)
        private set



    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var displayName by mutableStateOf("")

    // ---- Loading / error state ----------------------------------------------

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage: String? by mutableStateOf(null)
        private set

    /** True while a login or register network call is in progress. */
    var isAuthInProgress by mutableStateOf(false)
        private set

    // ---- Firebase auth listener ---------------------------------------------

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        currentUser = user
        if (user != null) {
            loadProfile(user.uid)
        } else {
            profile = null
        }
    }

    init {
        auth.addAuthStateListener(authListener)
        // If already signed in (session restored), load the profile now.
        auth.currentUser?.uid?.let { loadProfile(it) }
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authListener)
    }

    // ---- Actions ------------------------------------------------------------

    /** Validates inputs and attempts email/password sign-in. */
    fun login() {
        val emailVal = email.trim()
        val passwordVal = password

        if (emailVal.isBlank()) {
            errorMessage = "Please enter your email address."
            return
        }
        if (passwordVal.isBlank()) {
            errorMessage = "Please enter your password."
            return
        }

        isAuthInProgress = true
        errorMessage = null

        AuthRepository.signInWithEmail(emailVal, passwordVal) { uid, error ->
            isAuthInProgress = false
            if (uid == null) {
                errorMessage = error ?: "Sign-in failed. Please try again."
            }
            // On success the authListener will update currentUser and load profile.
        }
    }

    /** Validates inputs and creates a new account. */
    fun register() {
        val nameVal = displayName.trim()
        val emailVal = email.trim()
        val passwordVal = password

        if (nameVal.isBlank()) {
            errorMessage = "Please enter your name."
            return
        }
        if (emailVal.isBlank()) {
            errorMessage = "Please enter your email address."
            return
        }
        if (!emailVal.contains("@")) {
            errorMessage = "Please enter a valid email address."
            return
        }
        if (passwordVal.length < 6) {
            errorMessage = "Password must be at least 6 characters."
            return
        }

        isAuthInProgress = true
        errorMessage = null

        AuthRepository.registerWithEmail(emailVal, passwordVal, nameVal) { uid, error ->
            isAuthInProgress = false
            if (uid == null) {
                errorMessage = error ?: "Registration failed. Please try again."
                return@registerWithEmail
            }

            // Create a Passenger profile in the database on first registration.
            userRepository.createProfile(uid, nameVal, emailVal) { _, dbError ->
                if (dbError != null) {
                    // Non-fatal: auth succeeded; profile will be created lazily.
                }
            }
            // authListener will update currentUser.
        }
    }

    /** Signs the current user out. Clears all form fields. */
    fun logout() {
        AuthRepository.signOut()
        isGuestMode = false
        email = ""
        password = ""
        displayName = ""
        errorMessage = null
        profile = null
    }

    /** Signs in as a guest/demo user with the specified role ("passenger", "driver", "admin"). */
    fun loginAsGuest(role: String = "passenger") {
        isGuestMode = true
        userRole = role
        displayName = when (role) {
            "driver" -> "Demo Driver"
            "admin" -> "Demo Admin"
            else -> "Demo Passenger"
        }
        profile = UserProfile(
            uid = "guest_demo_uid",
            displayName = displayName,
            email = "demo@sltbsmartmove.lk",
            depotId = "COLOMBO_CENTRAL",
            status = "active"
        )
        errorMessage = null
    }

    /** Clears any displayed error message. */
    fun clearError() {
        errorMessage = null
    }

    // ---- Private helpers ----------------------------------------------------

    private fun loadProfile(uid: String) {
        userRepository.getProfile(uid) { p, _ ->
            profile = p
        }
        userRepository.getRoleForUser(uid) { roleRecord ->
            userRole = roleRecord.role
        }
    }
}
