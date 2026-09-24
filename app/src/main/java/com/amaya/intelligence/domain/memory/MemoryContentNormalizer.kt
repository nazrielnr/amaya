package com.amaya.intelligence.domain.memory

import javax.inject.Inject
import javax.inject.Singleton

data class NormalizedMemoryText(
    val title: String,
    val content: String,
    val reason: String
)

/**
 * Converts raw user/tool memory text into a durable, professional memory record.
 * Stored memory should read like a concise fact, not like a copied command.
 */
@Singleton
class MemoryContentNormalizer @Inject constructor() {
    fun normalize(
        content: String,
        type: MemoryType,
        action: MemoryAction,
        reason: String,
        requestedTitle: String? = null
    ): NormalizedMemoryText {
        val cleaned = cleanCommandText(content)
        val normalizedContent = summarizeDurableFact(cleaned, type)
        return NormalizedMemoryText(
            title = normalizeTitle(requestedTitle, normalizedContent, type, action),
            content = normalizedContent,
            reason = normalizedReason(normalizedContent, type, action, reason)
        )
    }

    private fun normalizeTitle(requestedTitle: String?, content: String, type: MemoryType, action: MemoryAction): String {
        val cleanRequested = requestedTitle
            ?.replace(Regex("(?i)^update[_ -]?memory$"), "")
            ?.replace(Regex("\\s+"), " ")
            ?.trim(' ', '.', ',', ';', ':')
            ?.takeIf { it.isNotBlank() }
        if (cleanRequested != null) return cleanRequested.take(80)
        val lower = content.lowercase()
        return when {
            "user's name" in lower -> "User name"
            "prefers" in lower && "responses" in lower -> "Response language preference"
            "works at" in lower -> "Workplace context"
            type == MemoryType.USER_PROFILE -> "User profile"
            type == MemoryType.WORKSPACE_FACT -> "Workspace fact"
            type == MemoryType.DECISION -> "Project decision"
            else -> content.removeSuffix(".").take(80)
        }
    }

    private fun summarizeDurableFact(text: String, type: MemoryType): String {
        val clean = text.trim()
        extractUserName(clean)?.let { return "The user's name is $it." }
        extractLanguagePreference(clean)?.let { return "The user prefers $it for responses." }
        extractResponseDetail(clean)?.let { return "The user prefers $it responses." }
        extractWorkplace(clean)?.let { return "The user works at $it." }
        extractNickname(clean)?.let { return "The user prefers to be called $it." }

        val withoutPrefix = clean
            .replace(Regex("(?i)^user\\s+preference/profile:\\s*"), "")
            .replace(Regex("(?i)^workspace\\s+fact:\\s*"), "")
            .replace(Regex("(?i)^project\\s+memory:\\s*"), "")
            .replace(Regex("(?i)^important\\s+memory:\\s*"), "")
            .trim()

        return when (type) {
            MemoryType.USER_PROFILE -> rewriteFirstPerson(withoutPrefix).toSentence()
            MemoryType.WORKSPACE_FACT -> rewriteWorkspaceInstruction(withoutPrefix).toSentence()
            else -> withoutPrefix.toSentence()
        }
    }

    private fun cleanCommandText(text: String): String {
        var cleaned = text
            .replace(Regex("(?is)<think>.*?</think>"), "")
            .replace(Regex("(?is)<think>.*"), "")
            .replace(Regex("(?is)</think>"), "")
            .replace(Regex("(?i)^\\s*user\\s+asked/discussed:\\s*"), "")
            .replace(Regex("(?i)^\\s*outcome:\\s*"), "")
            .replace(Regex("(?i)^\\s*user\\s+preference/profile:\\s*"), "")
            .replace(Regex("(?i)^\\s*workspace\\s+fact:\\s*"), "")
            .replace(Regex("(?i)^\\s*important\\s+memory:\\s*"), "")
            .trim()

        val commandPatterns = listOf(
            "please remember that", "please remember", "remember that", "remember",
            "tolong ingat bahwa", "tolong ingat", "ingat bahwa", "ingat",
            "mulai sekarang", "from now on", "from now",
            "please note that", "catat bahwa", "catat"
        )
        commandPatterns.forEach { command ->
            cleaned = cleaned
                .replace(Regex("(?i)^\\s*${Regex.escape(command)}[:,]?\\s*"), "")
                .replace(Regex("(?i)\\s+${Regex.escape(command)}[.!?]*\\s*$"), "")
        }
        return cleaned
            .replace(Regex("\\s+"), " ")
            .trim(' ', '.', ',', ';', ':')
    }

    fun isInstructionLike(text: String): Boolean {
        val clean = text.trim().lowercase()
        if (clean.startsWith("the user ") || clean.startsWith("the workspace ") || clean.startsWith("the project ")) return false
        return INSTRUCTION_PREFIX.containsMatchIn(clean)
    }

    private fun rewriteWorkspaceInstruction(text: String): String = when {
        text.matches(Regex("(?i)^use\\s+.+")) -> "The workspace uses ${text.replaceFirst(Regex("(?i)^use\\s+"), "")}"
        text.matches(Regex("(?i)^run\\s+.+")) -> "The workspace command is ${text.replaceFirst(Regex("(?i)^run\\s+"), "")}"
        else -> text
    }

    private fun rewriteFirstPerson(text: String): String {
        var clean = text.trim()
        clean = clean.replace(Regex("(?i)^saya\\s+"), "The user ")
        clean = clean.replace(Regex("(?i)^aku\\s+"), "The user ")
        clean = clean.replace(Regex("(?i)^my\\s+"), "The user's ")
        return clean
    }

    private fun extractUserName(text: String): String? {
        val patterns = listOf(
            Regex("(?i)\\bnamaku\\s+([\\p{L}][\\p{L} .'-]{0,40})"),
            Regex("(?i)\\bnama\\s+ku\\s+([\\p{L}][\\p{L} .'-]{0,40})"),
            Regex("(?i)\\bnama\\s+saya\\s+([\\p{L}][\\p{L} .'-]{0,40})"),
            Regex("(?i)\\bmy\\s+name\\s+is\\s+([A-Za-z][A-Za-z .'-]{0,40})")
        )
        return cleanCapture(patterns.firstNotNullOfOrNull { it.find(text)?.groupValues?.getOrNull(1) })
    }

    private fun extractNickname(text: String): String? {
        val patterns = listOf(
            Regex("(?i)\\bpanggil\\s+(?:aku|saya)\\s+([\\p{L}][\\p{L} .'-]{0,40})"),
            Regex("(?i)\\bcall\\s+me\\s+([A-Za-z][A-Za-z .'-]{0,40})")
        )
        return cleanCapture(patterns.firstNotNullOfOrNull { it.find(text)?.groupValues?.getOrNull(1) })
    }

    private fun extractLanguagePreference(text: String): String? {
        val lower = text.lowercase()
        val language = when {
            "inggris" in lower || "english" in lower -> "English"
            "indonesia" in lower || "bahasa indonesia" in lower -> "Indonesian"
            "jawa" in lower || "javanese" in lower -> "Javanese"
            else -> null
        } ?: return null
        val hasLanguageIntent = listOf("bahasa", "language", "jawab", "answer", "reply", "respond", "pakai", "gunakan", "use").any { it in lower }
        return if (hasLanguageIntent) language else null
    }

    private fun extractResponseDetail(text: String): String? {
        val lower = text.lowercase()
        return when {
            listOf("concise", "concisely", "ringkas", "singkat", "brief").any { it in lower } -> "concise"
            listOf("detailed", "detail", "rinci", "lengkap", "verbose").any { it in lower } -> "detailed"
            else -> null
        }
    }

    private fun extractWorkplace(text: String): String? {
        val lower = text.lowercase()
        if (!listOf("kerja", "work", "works", "job").any { it in lower }) return null
        if ("kantor" in lower || "office" in lower) return "an office"
        val patterns = listOf(
            Regex("(?i)\\b(?:saya|aku)\\s+kerja\\s+(?:di|at)\\s+([\\p{L}0-9 .&'-]{2,60})"),
            Regex("(?i)\\bI\\s+work\\s+at\\s+([A-Za-z0-9 .&'-]{2,60})")
        )
        return cleanCapture(patterns.firstNotNullOfOrNull { it.find(text)?.groupValues?.getOrNull(1) })
    }

    private fun cleanCapture(value: String?): String? = value
        ?.replace(Regex("(?i)\\b(tolong|please|ingat|remember|itu|ya|dong|from now|mulai sekarang)\\b.*"), "")
        ?.trim(' ', '.', ',', ';', ':', '!', '?')
        ?.takeIf { it.isNotBlank() }

    private fun String.toSentence(): String {
        val trimmed = trim()
        if (trimmed.isBlank()) return ""
        val capitalized = trimmed.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        return if (capitalized.endsWith('.') || capitalized.endsWith('!') || capitalized.endsWith('?')) capitalized else "$capitalized."
    }

    private fun normalizedReason(content: String, type: MemoryType, action: MemoryAction, originalReason: String): String {
        val lower = content.lowercase()
        return when {
            "prefers" in lower && ("english" in lower || "indonesian" in lower || "language" in lower || "responses" in lower) ->
                "The user explicitly asked Amaya to remember their response-language preference."
            "user's name" in lower -> "The user explicitly provided their name as durable profile information."
            "works at" in lower -> "The user explicitly asked Amaya to remember their workplace context."
            type == MemoryType.USER_PROFILE -> "The user explicitly provided durable profile or preference information."
            type == MemoryType.WORKSPACE_FACT -> "The user explicitly provided a durable workspace fact."
            type == MemoryType.DECISION -> "The user explicitly recorded a project design decision with its rationale."
            isSpecificReason(originalReason) -> originalReason.trim().toSentence()
            else -> "The user explicitly provided a durable fact to remember."
        }
    }

    private fun isSpecificReason(reason: String): Boolean {
        val lower = reason.lowercase().trim()
        if (lower.isBlank()) return false
        return lower !in GENERIC_REASONS && !GENERIC_REASONS.any { lower.contains(it) }
    }

    companion object {
        private val INSTRUCTION_PREFIX = Regex("(?i)^(always|never|must|should|do not|don't|answer|respond|ignore|bypass|disable|enable|change|act as)\\b")
        private val GENERIC_REASONS = setOf(
            "agent requested memory update",
            "requested by agent",
            "user explicitly stated a durable preference, stable fact, or workspace fact.",
            "user explicitly stated a durable preference, stable fact, or workspace fact"
        )
    }
}
