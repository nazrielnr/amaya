package com.amaya.intelligence.domain.memory

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryDeduper @Inject constructor() {
    fun isDuplicate(candidate: String, existingText: String): Boolean {
        val normalizedCandidate = normalize(candidate)
        if (normalizedCandidate.isBlank()) return true
        return existingText.lineSequence()
            .map { it.trim().trimStart('-', '*').trim() }
            .filter { it.isNotBlank() }
            .any { existing ->
                val normalizedExisting = normalize(existing)
                normalizedExisting == normalizedCandidate ||
                    (normalizedCandidate.length >= 4 && normalizedExisting.contains(normalizedCandidate)) ||
                    (normalizedExisting.length >= 12 && normalizedCandidate.contains(normalizedExisting)) ||
                    similarity(normalizedExisting, normalizedCandidate) >= 0.92
            }
    }

    /**
     * Checks if candidate fact is on the exact same topic as existing fact but states different
     * or contradictory details (e.g. language preference changes, framework switches, updated commands).
     */
    fun isContradictionOrUpdate(candidate: String, existingText: String): Boolean {
        val normalizedCandidate = normalize(candidate)
        val normalizedExisting = normalize(existingText)
        if (normalizedCandidate.isBlank() || normalizedExisting.isBlank()) return false
        if (normalizedCandidate == normalizedExisting) return false

        val candidateWords = normalizedCandidate.split(' ').filter { it.length > 2 }.toSet()
        val existingWords = normalizedExisting.split(' ').filter { it.length > 2 }.toSet()
        val overlap = candidateWords intersect existingWords
        val union = candidateWords + existingWords
        if (union.isEmpty()) return false

        // If they share common core topic keywords (e.g., "language", "name", "build", "prefer")
        // but have distinct value terms
        val sim = overlap.size.toDouble() / union.size.toDouble()
        if (sim in 0.45..0.91) return true

        val keyTopicMarkers = listOf(
            setOf("language", "bahasa", "english", "indonesia"),
            setOf("call", "name", "nama", "panggil"),
            setOf("build", "command", "gradle", "maven", "npm", "script"),
            setOf("test", "testing", "unit", "robolectric", "junit"),
            setOf("database", "db", "room", "sqlite", "realm", "store"),
            setOf("style", "concise", "verbose", "detail", "brief", "ringkas")
        )
        return keyTopicMarkers.any { group ->
            candidateWords.any { it in group } && existingWords.any { it in group }
        }
    }

    fun dedupeLines(text: String): String {
        val seen = linkedSetOf<String>()
        return text.lineSequence()
            .filter { line ->
                val key = normalize(line.trim().trimStart('-', '*').trim())
                key.isBlank() || seen.add(key)
            }
            .joinToString("\n")
            .trimEnd() + "\n"
    }

    fun normalize(text: String): String = text
        .lowercase()
        .replace(Regex("[^a-z0-9\\p{L}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    fun similarity(a: String, b: String): Double {
        if (a.isBlank() || b.isBlank()) return 0.0
        val left = a.split(' ').toSet()
        val right = b.split(' ').toSet()
        val union = left + right
        if (union.isEmpty()) return 0.0
        return (left intersect right).size.toDouble() / union.size.toDouble()
    }
}

