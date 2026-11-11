package com.amaya.intelligence.data.repository

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReferenceDocumentRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun import(ownerType: String, ownerId: Long, uri: Uri): Result<String> = runCatching {
        val displayName = context.contentResolver.query(uri, arrayOf("_display_name"), null, null, null)?.use { cursor ->
            cursor.takeIf { it.moveToFirst() }?.getString(0)
        }?.takeIf(String::isNotBlank) ?: "reference.txt"
        val safeName = sanitizeReferenceFileName(displayName)
        val directory = File(context.filesDir, "references/$ownerType/$ownerId").apply { mkdirs() }
        val target = File(directory, "${System.currentTimeMillis()}_$safeName")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(8_192)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= MAX_FILE_BYTES) { "Reference must be at most 1 MB" }
                        output.write(buffer, 0, read)
                    }
                }
            } ?: error("Could not open reference")
            target.absolutePath
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
    }

    suspend fun saveManual(ownerType: String, ownerId: Long, name: String, content: String): Result<String> = runCatching {
        val clean = content.trim()
        require(clean.isNotBlank()) { "Reference content is required" }
        require(clean.toByteArray().size <= MAX_FILE_BYTES) { "Reference must be at most 1 MB" }
        val safeName = sanitizeReferenceFileName(name.trim().ifBlank { "note" })
        val directory = File(context.filesDir, "references/$ownerType/$ownerId").apply { mkdirs() }
        File(directory, "${System.currentTimeMillis()}_$safeName.txt").apply { writeText(clean) }.absolutePath
    }

    fun appendPath(json: String, path: String): String {
        val values = parsePaths(json).filter { File(it).exists() }.toMutableList()
        if (path !in values) values += path
        return JSONArray(values.takeLast(MAX_REFERENCES)).toString()
    }

    fun parsePaths(json: String): List<String> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    }.getOrDefault(emptyList())

    fun remove(ownerType: String, ownerId: Long, json: String, path: String): Result<String> = runCatching {
        val directory = File(context.filesDir, "references/$ownerType/$ownerId").canonicalFile
        val target = File(path).canonicalFile
        require(target.parentFile == directory) { "Reference does not belong to this owner" }
        require(target.delete() || !target.exists()) { "Could not delete reference" }
        JSONArray(parsePaths(json).filter { it != path && File(it).exists() }.takeLast(MAX_REFERENCES)).toString()
    }

    fun deleteOwner(ownerType: String, ownerId: Long) {
        File(context.filesDir, "references/$ownerType/$ownerId").deleteRecursively()
    }

    fun context(json: String): String? {
        val text = parsePaths(json).filter { File(it).isFile }.take(MAX_CONTEXT_REFERENCES).mapNotNull { path ->
            runCatching {
                val file = File(path)
                "<reference name=\"${file.name}\">\n${file.readText().take(MAX_REFERENCE_CHARS)}\n</reference>"
            }.getOrNull()
        }
        return text.takeIf(List<String>::isNotEmpty)?.joinToString("\n\n")
    }

    private companion object {
        const val MAX_FILE_BYTES = 1_000_000L
        const val MAX_REFERENCES = 20
        const val MAX_CONTEXT_REFERENCES = 3
        const val MAX_REFERENCE_CHARS = 8_000
    }
}

/** Keep user-visible Unicode names while excluding separators and control characters. */
internal fun sanitizeReferenceFileName(name: String): String =
    name.replace(Regex("[^\\p{L}\\p{M}\\p{N}._ -]"), "_")
