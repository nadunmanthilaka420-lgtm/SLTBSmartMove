package com.example.sltbsmartmove.uis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.sltbsmartmove.data.ComplaintRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

private const val MAX_SUBJECT_LENGTH = 120
private const val MAX_DESCRIPTION_LENGTH = 2000
private const val MAX_CATEGORY_LENGTH = 60

private data class UiMessage(val text: String, val isError: Boolean)

@Composable
fun ComplaintScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repository = remember { ComplaintRepository() }

    fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

    var cameraGranted by remember { mutableStateOf(hasCameraPermission()) }
    var cameraDenied by remember { mutableStateOf(false) }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    // Lets us unbind the camera when this screen leaves the composition.
    val cameraProviderHolder = remember { arrayOfNulls<ProcessCameraProvider>(1) }
    val screenDisposed = remember { booleanArrayOf(false) }

    // rememberSaveable: the form survives rotation and process restarts.
    var photoPath by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Bus Defect") }
    var subject by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }

    var message by remember { mutableStateOf<UiMessage?>(null) }
    var capturing by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var reportsRefreshKey by remember { mutableIntStateOf(0) }

    // A restored photo path is only kept if the file still exists and opens.
    LaunchedEffect(Unit) {
        if (photoPath.isNotBlank() && !isReadableImage(File(photoPath))) {
            photoPath = ""
        }
    }

    // Free the camera when leaving the Report tab.
    DisposableEffect(Unit) {
        onDispose {
            screenDisposed[0] = true
            cameraProviderHolder[0]?.unbindAll()
            cameraProviderHolder[0] = null
        }
    }

    // Pick up a permission change made in system Settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        cameraGranted = hasCameraPermission()
        if (cameraGranted) cameraDenied = false
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraGranted = granted
        cameraDenied = !granted
        message = if (granted) {
            null
        } else {
            UiMessage(
                "Camera permission denied. Allow it in app settings to " +
                        "take a photo.",
                isError = true
            )
        }
    }

    // Decode off the main thread, downsized and rotated upright.
    val photoBitmap by produceState<ImageBitmap?>(null, photoPath) {
        value = if (photoPath.isBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                loadUprightBitmap(photoPath, maxSide = 1280)?.asImageBitmap()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Report a Transport Issue",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            "Photograph a damaged seat, broken window, or other bus service issue."
        )

        if (cameraGranted) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                factory = { viewContext ->
                    PreviewView(viewContext).also { previewView ->
                        previewView.scaleType =
                            PreviewView.ScaleType.FILL_CENTER

                        val cameraProviderFuture =
                            ProcessCameraProvider.getInstance(viewContext)

                        cameraProviderFuture.addListener({
                            // The screen may have been closed while the camera
                            // was starting; do not bind in that case.
                            if (screenDisposed[0]) return@addListener

                            try {
                                val cameraProvider = cameraProviderFuture.get()

                                val preview = Preview.Builder()
                                    .build()
                                    .also {
                                        it.surfaceProvider =
                                            previewView.surfaceProvider
                                    }

                                val capture = ImageCapture.Builder()
                                    .setCaptureMode(
                                        ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                                    )
                                    .build()

                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    capture
                                )

                                cameraProviderHolder[0] = cameraProvider
                                imageCapture = capture
                                message = null
                            } catch (error: Exception) {
                                message = UiMessage(
                                    "Could not start camera: " +
                                            (error.localizedMessage
                                                ?: "Unknown error"),
                                    isError = true
                                )
                            }
                        }, ContextCompat.getMainExecutor(viewContext))
                    }
                }
            )

            Button(
                onClick = {
                    val capture = imageCapture

                    if (capture == null) {
                        message = UiMessage(
                            "Camera is still starting. Try again shortly.",
                            isError = true
                        )
                        return@Button
                    }

                    capturing = true
                    message = null

                    val directory = File(
                        context.filesDir,
                        "complaint_photos"
                    )

                    if (!directory.exists() && !directory.mkdirs()) {
                        capturing = false
                        message = UiMessage(
                            "Could not create photo storage directory.",
                            isError = true
                        )
                        return@Button
                    }

                    val photoFile = File(
                        directory,
                        "complaint_${System.currentTimeMillis()}.jpg"
                    )

                    val outputOptions =
                        ImageCapture.OutputFileOptions.Builder(photoFile)
                            .build()

                    capture.takePicture(
                        outputOptions,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(
                                output: ImageCapture.OutputFileResults
                            ) {
                                capturing = false

                                // Do not trust the callback alone: make sure the
                                // file exists, is not empty and can be opened.
                                if (!isReadableImage(photoFile)) {
                                    photoFile.delete()
                                    message = UiMessage(
                                        "Photo capture failed: the saved " +
                                                "image could not be opened.",
                                        isError = true
                                    )
                                    return
                                }

                                // Remove the previous, never-submitted photo.
                                if (photoPath.isNotBlank() &&
                                    photoPath != photoFile.absolutePath
                                ) {
                                    File(photoPath).delete()
                                }

                                photoPath = photoFile.absolutePath
                                message = UiMessage(
                                    "Photo saved successfully.",
                                    isError = false
                                )
                            }

                            override fun onError(
                                exception: ImageCaptureException
                            ) {
                                capturing = false
                                photoFile.delete()
                                message = UiMessage(
                                    "Photo capture failed: " +
                                            (exception.localizedMessage
                                                ?: "Unknown error"),
                                    isError = true
                                )
                            }
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !capturing
            ) {
                Text(if (capturing) "Taking Photo..." else "Take Photo")
            }
        } else {
            Text("Camera permission is required to photograph an issue.")

            Button(
                onClick = {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Grant Camera Permission")
            }

            if (cameraDenied) {
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .apply {
                                    data = Uri.fromParts(
                                        "package",
                                        context.packageName,
                                        null
                                    )
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open App Settings")
                }
            }
        }

        if (capturing) {
            CircularProgressIndicator()
        }

        photoBitmap?.let { bitmap ->
            Text("Photo captured:")
            Image(
                bitmap = bitmap,
                contentDescription = "Captured complaint photo",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )
            Text("Saved locally on this device.")
        }

        OutlinedTextField(
            value = category,
            onValueChange = { category = it.take(MAX_CATEGORY_LENGTH) },
            label = { Text("Category (e.g. Bus Defect)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = subject,
            onValueChange = { subject = it.take(MAX_SUBJECT_LENGTH) },
            label = { Text("Complaint subject") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = description,
            onValueChange = { description = it.take(MAX_DESCRIPTION_LENGTH) },
            label = { Text("Describe the issue") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Text(
            if (photoPath.isBlank()) {
                "Photo: not captured"
            } else {
                "Photo attached: ${File(photoPath).name}"
            },
            style = MaterialTheme.typography.bodySmall
        )

        Button(
            onClick = {
                if (category.isBlank() ||
                    subject.isBlank() ||
                    description.isBlank()
                ) {
                    message = UiMessage(
                        "Enter a category, subject and description.",
                        isError = true
                    )
                    return@Button
                }

                if (photoPath.isBlank()) {
                    message = UiMessage(
                        "Please take a photo before submitting.",
                        isError = true
                    )
                    return@Button
                }

                submitting = true
                message = null

                repository.submitComplaint(
                    category = category.trim(),
                    subject = subject.trim(),
                    description = description.trim(),
                    photoFileName = if (photoPath.isNotBlank()) File(photoPath).name else "",
                    photoPath = photoPath
                ) { success, error ->
                    submitting = false

                    if (success) {
                        message = UiMessage(
                            "Complaint submitted successfully.",
                            isError = false
                        )
                        subject = ""
                        description = ""
                        // The file is kept on the phone: it is the only copy of
                        // the photo, because it is not uploaded.
                        photoPath = ""
                        reportsRefreshKey++
                    } else {
                        message = UiMessage(
                            "Submission failed: " +
                                    (error ?: "Please try again."),
                            isError = true
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !submitting && !capturing
        ) {
            Text(if (submitting) "Submitting..." else "Submit Complaint")
        }

        if (submitting) {
            CircularProgressIndicator()
        }

        message?.let {
            Text(
                text = it.text,
                color = if (it.isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        }

        Text(
            "Photos are uploaded to Firebase Storage when submitted. " +
                    "Reports go to the Firebase Realtime Database. " +
                    "This is a demonstration — reports are not sent to SLTB.",
            style = MaterialTheme.typography.bodySmall
        )

        MyReportsSection(refreshKey = reportsRefreshKey)
    }
}

/** True if the file exists, is not empty and can be decoded as an image. */
private fun isReadableImage(file: File): Boolean {
    if (!file.exists() || file.length() <= 0L) return false

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    return bounds.outWidth > 0 && bounds.outHeight > 0
}

/**
 * Loads a JPEG scaled down so its longest side is about [maxSide] pixels, and
 * rotates it upright using the EXIF orientation that CameraX writes.
 */
private fun loadUprightBitmap(path: String, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxSide ||
        bounds.outHeight / (sample * 2) >= maxSide
    ) {
        sample *= 2
    }

    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = BitmapFactory.decodeFile(path, options) ?: return null

    val degrees = try {
        when (
            ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } catch (ignored: IOException) {
        0f
    }

    if (degrees == 0f) return bitmap

    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(
        bitmap,
        0,
        0,
        bitmap.width,
        bitmap.height,
        matrix,
        true
    )
}
