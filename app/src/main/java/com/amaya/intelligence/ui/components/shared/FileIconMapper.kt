package com.amaya.intelligence.ui.components.shared

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Returns a suitable material icon based on the file extension.
 */
fun getFileIcon(fileName: String): ImageVector {
    val ext = fileName.substringAfterLast(".", "").lowercase()
    return when (ext) {
        "kt", "java", "py", "js", "ts", "cpp", "c", "h", "go", "rs", "rb", "php" -> Icons.Default.Code
        "html", "xml", "json", "yaml", "yml", "md", "txt" -> Icons.Default.Description
        "png", "jpg", "jpeg", "gif", "svg", "webp" -> Icons.Default.Image
        "mp4", "mov", "avi", "mkv" -> Icons.Default.Movie
        "mp3", "wav", "flac" -> Icons.Default.MusicNote
        "pdf" -> Icons.Default.PictureAsPdf
        "zip", "tar", "gz", "7z", "rar" -> Icons.Default.Archive
        else -> Icons.Default.InsertDriveFile
    }
}
