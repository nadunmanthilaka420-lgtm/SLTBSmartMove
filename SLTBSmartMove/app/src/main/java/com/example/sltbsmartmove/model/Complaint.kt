package com.example.sltbsmartmove.model

/**
 * A passenger complaint stored under `complaints/{complaintId}`.
 *
 * Photo handling in this demo version:
 * - [photoFileName] is just the file name of the picture on the phone that sent
 *   the report. The image itself is NOT uploaded.
 * - [photoUrl] stays empty until Firebase Storage upload is added. If it is not
 *   blank, the image has been uploaded.
 */
data class Complaint(
    val complaintId: String = "",
    val userId: String = "",
    val category: String = "",
    val subject: String = "",
    val description: String = "",
    val photoFileName: String = "",
    val photoUrl: String = "",
    val status: String = "SUBMITTED",
    val createdAt: Long = 0L
) {
    val photoUploaded: Boolean
        get() = photoUrl.isNotBlank()
}
