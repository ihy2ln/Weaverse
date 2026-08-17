package com.ihy2ln.weaverse.core.media

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** The four import entry points from spec §7: pick image, pick video, pick both, or capture with the camera. */
data class MediaPickerActions(
    val pickImage: () -> Unit,
    val pickVideo: () -> Unit,
    val pickImagesAndVideos: () -> Unit,
    val captureImage: () -> Unit,
)

/** Wraps `ActivityResultContracts.PickVisualMedia`/`TakePicture` (spec §7) — [onPicked] receives raw URIs; hand them to [MediaImporter]. */
@Composable
fun rememberMediaPickerActions(onPicked: (List<Uri>) -> Unit): MediaPickerActions {
    val context = LocalContext.current

    val singlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let { onPicked(listOf(it)) } }

    val multiPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris -> if (uris.isNotEmpty()) onPicked(uris) }

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success -> if (success) pendingCameraUri?.let { onPicked(listOf(it)) } }

    return remember(context) {
        MediaPickerActions(
            pickImage = {
                singlePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            pickVideo = {
                singlePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            },
            pickImagesAndVideos = {
                multiPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            },
            captureImage = {
                val uri = createCameraCaptureUri(context)
                pendingCameraUri = uri
                cameraLauncher.launch(uri)
            },
        )
    }
}
