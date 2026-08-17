package com.ihy2ln.weaverse.core.media

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * A `content://` URI under the `camera_captures` cache-path declared in
 * `res/xml/file_paths.xml` / the manifest's FileProvider — where the camera
 * app writes the photo it captures (spec §7: "camera capture").
 */
fun createCameraCaptureUri(context: Context): Uri {
    val capturesDir = File(context.cacheDir, "captures").apply { mkdirs() }
    val file = File(capturesDir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
