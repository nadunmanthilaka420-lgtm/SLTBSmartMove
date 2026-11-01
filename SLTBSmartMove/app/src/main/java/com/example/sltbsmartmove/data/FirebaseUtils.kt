package com.example.sltbsmartmove.data

import android.os.Handler
import android.os.Looper
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import java.util.concurrent.atomic.AtomicBoolean

/** How long to wait for Firebase before telling the user nothing came back. */
internal const val FIREBASE_TIMEOUT_MS = 15_000L

/**
 * Makes sure a Firebase callback is handled exactly once.
 *
 * Realtime Database never answers while the phone is offline (it queues the
 * request instead of failing), so without a timeout the UI would spin forever.
 * Call [complete] when a real answer arrives: only the first caller (the answer
 * or the timeout) gets `true` and should continue.
 */
internal class CallbackGuard(
    timeoutMs: Long = FIREBASE_TIMEOUT_MS,
    onTimeout: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val finished = AtomicBoolean(false)
    private val timeoutRunnable = Runnable {
        if (finished.compareAndSet(false, true)) onTimeout()
    }

    init {
        handler.postDelayed(timeoutRunnable, timeoutMs)
    }

    fun complete(): Boolean {
        val first = finished.compareAndSet(false, true)
        if (first) handler.removeCallbacks(timeoutRunnable)
        return first
    }
}

// ---- Tolerant readers: a wrongly typed field never throws or crashes the app.

internal fun DataSnapshot.text(name: String): String =
    child(name).value?.toString().orEmpty()

internal fun DataSnapshot.number(name: String): Double? =
    (child(name).value as? Number)?.toDouble()

internal fun DataSnapshot.long(name: String): Long =
    (child(name).value as? Number)?.toLong() ?: 0L

internal fun DataSnapshot.flag(name: String, default: Boolean): Boolean =
    (child(name).value as? Boolean) ?: default

// ---- Human-readable errors

internal fun DatabaseError.friendlyMessage(): String = when (code) {
    DatabaseError.PERMISSION_DENIED ->
        "Permission denied. Make sure: (1) Anonymous sign-in is enabled in " +
                "Firebase Console → Authentication → Sign-in method, and " +
                "(2) the Database Rules have been published in " +
                "Firebase Console → Realtime Database → Rules."
    DatabaseError.NETWORK_ERROR, DatabaseError.DISCONNECTED ->
        "Network error. Check your internet connection and try again."
    else -> message
}

internal fun Throwable.friendlyDatabaseMessage(): String {
    val text = localizedMessage.orEmpty()
    return when {
        text.contains("permission denied", ignoreCase = true) ->
            "Permission denied. Check that sign-in worked and that the " +
                    "Firebase Database Rules allow this action."
        text.isBlank() -> "Unknown Firebase error."
        else -> text
    }
}
