package com.example.sltbsmartmove.data

import android.net.Uri
import android.util.Log
import com.example.sltbsmartmove.model.Complaint
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import java.io.File

class ComplaintRepository {

    private val complaintsRef =
        FirebaseDatabase.getInstance().getReference("complaints")

    private val storageRef =
        FirebaseStorage.getInstance().getReference("complaint_photos")

    // -------------------------------------------------------------------------
    // Submit a new complaint
    // -------------------------------------------------------------------------

    /**
     * Submits a complaint to Firebase Realtime Database.
     */
    fun submitComplaint(
        category: String,
        subject: String,
        description: String,
        photoFileName: String,
        photoPath: String = "",
        onResult: (Boolean, String?) -> Unit
    ) {
        AuthRepository.ensureSignedIn { uid, _ ->
            val finalUid = uid ?: "guest_demo_uid"

            val newReference = complaintsRef.push()
            val complaintId = newReference.key ?: "complaint_${System.currentTimeMillis()}"

            if (photoPath.isNotBlank()) {
                val photoFile = File(photoPath)
                if (!photoFile.exists() || photoFile.length() == 0L) {
                    saveComplaintRecord(newReference, complaintId, finalUid, category, subject, description, "", "", onResult)
                    return@ensureSignedIn
                }

                val photoRef = storageRef.child("$finalUid/$complaintId.jpg")
                val uploadTask = photoRef.putFile(Uri.fromFile(photoFile))

                uploadTask
                    .addOnFailureListener { error ->
                        Log.w("ComplaintRepo", "Photo upload failed: ${error.localizedMessage}. Saving record without photo.")
                        // Fallback: save record without photo if storage upload fails
                        saveComplaintRecord(
                            newReference = newReference,
                            complaintId = complaintId,
                            uid = finalUid,
                            category = category,
                            subject = subject,
                            description = description,
                            photoFileName = photoFileName,
                            photoUrl = "(photo storage unavailable)",
                            onResult = onResult
                        )
                    }
                    .addOnSuccessListener {
                        photoRef.downloadUrl
                            .addOnSuccessListener { downloadUri ->
                                saveComplaintRecord(
                                    newReference = newReference,
                                    complaintId = complaintId,
                                    uid = finalUid,
                                    category = category,
                                    subject = subject,
                                    description = description,
                                    photoFileName = photoFileName,
                                    photoUrl = downloadUri.toString(),
                                    onResult = onResult
                                )
                            }
                            .addOnFailureListener { _ ->
                                saveComplaintRecord(
                                    newReference = newReference,
                                    complaintId = complaintId,
                                    uid = finalUid,
                                    category = category,
                                    subject = subject,
                                    description = description,
                                    photoFileName = photoFileName,
                                    photoUrl = "(download url failed)",
                                    onResult = onResult
                                )
                            }
                    }
            } else {
                saveComplaintRecord(
                    newReference = newReference,
                    complaintId = complaintId,
                    uid = finalUid,
                    category = category,
                    subject = subject,
                    description = description,
                    photoFileName = "",
                    photoUrl = "",
                    onResult = onResult
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // Private: write the complaint record to Realtime Database
    // -------------------------------------------------------------------------

    private fun saveComplaintRecord(
        newReference: DatabaseReference,
        complaintId: String,
        uid: String,
        category: String,
        subject: String,
        description: String,
        photoFileName: String,
        photoUrl: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val record = mapOf(
            "complaintId" to complaintId,
            "userId" to uid,
            "category" to category,
            "subject" to subject,
            "description" to description,
            "photoFileName" to photoFileName,
            "photoUrl" to photoUrl,
            "status" to "SUBMITTED",
            "createdAt" to ServerValue.TIMESTAMP
        )

        val guard = CallbackGuard(timeoutMs = 15_000L) {
            Log.e("ComplaintRepo", "Complaint write timed out at complaints/$complaintId")
            onResult(false, "Connection timeout. Please check your internet connection and Firebase Realtime Database URL.")
        }

        newReference.setValue(record)
            .addOnSuccessListener {
                Log.d("ComplaintRepo", "SUCCESS: Complaint saved to Firebase Realtime Database at complaints/$complaintId")
                if (guard.complete()) onResult(true, null)
            }
            .addOnFailureListener { error ->
                Log.e("ComplaintRepo", "ERROR: Complaint write failed: ${error.localizedMessage}", error)
                if (guard.complete()) {
                    onResult(false, error.friendlyDatabaseMessage())
                }
            }
    }

    // -------------------------------------------------------------------------
    // Read own complaints
    // -------------------------------------------------------------------------

    /** Loads the signed-in user's own complaints, newest first. */
    fun getMyComplaints(onResult: (List<Complaint>?, String?) -> Unit) {
        AuthRepository.ensureSignedIn { uid, _ ->
            val targetUid = uid ?: "guest_demo_uid"
            val query = complaintsRef.orderByChild("userId").equalTo(targetUid)
            lateinit var listener: ValueEventListener

            val guard = CallbackGuard {
                query.removeEventListener(listener)
                onResult(null, "No response from server.")
            }

            listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!guard.complete()) return

                    val complaints = mutableListOf<Complaint>()
                    for (child in snapshot.children) {
                        try {
                            complaints.add(
                                Complaint(
                                    complaintId = child.key.orEmpty(),
                                    userId = child.text("userId"),
                                    category = child.text("category"),
                                    subject = child.text("subject"),
                                    description = child.text("description"),
                                    photoFileName = child.text("photoFileName"),
                                    photoUrl = child.text("photoUrl"),
                                    status = child.text("status"),
                                    createdAt = child.long("createdAt")
                                )
                            )
                        } catch (ignored: Exception) {
                            // Skip a record that cannot be read.
                        }
                    }

                    onResult(complaints.sortedByDescending { it.createdAt }, null)
                }

                override fun onCancelled(error: DatabaseError) {
                    if (!guard.complete()) return
                    onResult(null, error.friendlyMessage())
                }
            }

            query.addListenerForSingleValueEvent(listener)
        }
    }

    // -------------------------------------------------------------------------
    // Admin: read all complaints
    // -------------------------------------------------------------------------

    /**
     * Loads ALL complaints from Firebase.
     */
    fun getAllComplaints(onResult: (List<Complaint>?, String?) -> Unit) {
        lateinit var listener: ValueEventListener

        val guard = CallbackGuard {
            complaintsRef.removeEventListener(listener)
            onResult(null, "No response from server.")
        }

        listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!guard.complete()) return
                val complaints = snapshot.children.mapNotNull { child ->
                    try {
                        Complaint(
                            complaintId = child.key.orEmpty(),
                            userId = child.text("userId"),
                            category = child.text("category"),
                            subject = child.text("subject"),
                            description = child.text("description"),
                            photoFileName = child.text("photoFileName"),
                            photoUrl = child.text("photoUrl"),
                            status = child.text("status"),
                            createdAt = child.long("createdAt")
                        )
                    } catch (e: Exception) {
                        null
                    }
                }.sortedByDescending { it.createdAt }
                onResult(complaints, null)
            }

            override fun onCancelled(error: DatabaseError) {
                if (!guard.complete()) return
                onResult(null, error.friendlyMessage())
            }
        }

        complaintsRef.addListenerForSingleValueEvent(listener)
    }
}
