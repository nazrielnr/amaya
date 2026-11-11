package com.amaya.intelligence.ui.screens.chat.shared

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.amaya.intelligence.R
import java.io.ByteArrayOutputStream
import java.io.File

internal sealed interface ChatAttachment {
    data class Document(val path: String) : ChatAttachment
    data class Image(val base64: String, val mimeType: String, val name: String) : ChatAttachment
}

/** Read picker grants through ContentResolver; a content URI need not expose a filesystem path. */
internal fun loadChatAttachment(context: Context, uri: Uri, imageOnly: Boolean = false): ChatAttachment {
    val resolver = context.contentResolver
    val name = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
        }
    }.getOrNull()?.takeIf(String::isNotBlank) ?: "attachment"
    val mimeType = resolver.getType(uri).orEmpty()
    val image = imageOnly || mimeType.startsWith("image/") ||
        name.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif")
    if (image) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        require(options.outWidth > 0 && options.outHeight > 0) { context.getString(R.string.could_not_read_photo) }
        options.inJustDecodeBounds = false
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 2048) {
            options.inSampleSize *= 2
        }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error(context.getString(R.string.could_not_read_photo))
        try {
            val bytes = ByteArrayOutputStream().use { output ->
                var quality = 85
                do {
                    output.reset()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
                    quality -= 10
                } while (output.size() > 135_000 && quality >= 30)
                output.toByteArray()
            }
            return ChatAttachment.Image(Base64.encodeToString(bytes, Base64.NO_WRAP), "image/jpeg", name.substringBeforeLast('.', name) + ".jpg")
        } finally {
            bitmap.recycle()
        }
    }

    val directory = File.createTempFile("attachment-", "", context.cacheDir).apply {
        check(delete() && mkdir())
    }
    val safeName = name.replace(Regex("[\\p{Cc}/\\\\]"), "_").takeUnless { it == "." || it == ".." }.orEmpty().ifBlank { "attachment" }
    val file = File(directory, safeName)
    try {
        val input = resolver.openInputStream(uri) ?: error(context.getString(R.string.could_not_read_attachment))
        input.use { stream ->
            file.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= 1_000_000) { context.getString(R.string.file_must_be_text_and_at_most_1_mb) }
                    output.write(buffer, 0, count)
                }
            }
        }
        return ChatAttachment.Document(file.absolutePath)
    } catch (error: Exception) {
        directory.deleteRecursively()
        throw error
    }
}
