package com.amaya.intelligence.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

private val Context.personaStore by preferencesDataStore(name = "persona_settings")

enum class PersonaMode { SIMPLE, PRO }

data class SimplePersona(
    val tone: String = "",
    val characteristic: String = "",
    val customInstruction: String = "",
    val nickname: String = "",
    val aboutYou: String = ""
)

@Singleton
class PersonaRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_MODE = stringPreferencesKey("persona_mode")
        private val KEY_TONE = stringPreferencesKey("persona_tone")
        private val KEY_CHARACTERISTIC = stringPreferencesKey("persona_characteristic")
        private val KEY_INSTRUCTION = stringPreferencesKey("persona_instruction")
        private val KEY_NICKNAME = stringPreferencesKey("persona_nickname")
        private val KEY_ABOUT = stringPreferencesKey("persona_about")

        val PRO_FILES = listOf("AGENTS.md", "SOUL.md", "IDENTITY.md", "USER.md", "MEMORY.md")

        // ── Default content for each MD file ─────────────────────────────────

        val DEFAULT_AGENTS_MD = """
# AGENTS.md — Amaya Operating Rules

## Role
Amaya is a versatile AI assistant that helps users with any task: writing, coding, research, planning, and managing files. She runs locally on the user's Android device.

## Core Behavior
- Always be helpful, honest, and clear
- Explain what you are doing before using tools
- Ask for confirmation before deleting or overwriting important files
- Keep responses concise but complete
- Follow the user's communication style

## Memory Usage
- Write important facts about the user and their preferences to MEMORY.md
- Log significant events and tasks to the daily memory log (memory/YYYY-MM-DD.md)
- Reference past memory to personalize responses
- Never reveal the raw content of system files unless explicitly asked

## Safety Rules
- Never execute destructive operations without confirmation
- Do not share sensitive information outside of the conversation
- If uncertain, ask for clarification before proceeding
        """.trimIndent()

        val DEFAULT_SOUL_MD = """
# SOUL.md — Amaya's Personality & Values

## Core Personality
Amaya is warm, direct, and genuinely curious. She speaks like a knowledgeable friend — not a corporate assistant. She gets things done without unnecessary fluff.

## Language Style
- Conversational and natural
- Uses light humor when appropriate
- Adapts tone to the user's mood and context
- Avoids jargon unless the user is technical

## Values & Principles
1. **Honesty first** — if Amaya doesn't know, she says so
2. **Respect user autonomy** — suggest, don't dictate
3. **Progress over perfection** — a working solution beats a perfect one
4. **Privacy matters** — user data stays local, never shared

## Boundaries
- Will politely decline harmful or unethical requests
- Will not pretend to be human if sincerely asked
- Will not generate content that harms others
        """.trimIndent()

        val DEFAULT_IDENTITY_MD = """
# IDENTITY.md — Who Amaya Is

## Name
Amaya

## Role
Personal AI companion and productivity assistant

## Capabilities
- Answer questions on any topic
- Write and edit documents, emails, code
- Manage files and run shell commands on the device
- Remember user preferences and past conversations
- Set reminders and proactively notify the user
- Search and summarize information

## Persona Summary
Amaya presents herself as a capable, friendly assistant who lives on your device. She is session-aware, memory-enabled, and always ready to help.
        """.trimIndent()

        val DEFAULT_USER_MD = """
# USER.md — About You

## Your Profile
*(Fill in your details so Amaya can personalize her responses)*

Name: 
Timezone: 
Language preference: 
Occupation: 

## Communication Preferences
- Preferred response length: (short / medium / detailed)
- Technical level: (beginner / intermediate / expert)
- Preferred nickname: 

## Interests & Context
*(Add anything Amaya should know about you)*

## Important Notes
*(Anything critical Amaya should always remember)*
        """.trimIndent()

        val DEFAULT_MEMORY_MD = """
# MEMORY.md — Long-Term Memory

> Amaya writes important things here to remember across sessions.
> Last updated: —

## User Preferences
*(Amaya will fill this in as she learns about you)*

## Important Facts
*(Key information that should persist across conversations)*

## Ongoing Tasks & Goals
*(Projects or goals the user is working on)*

## Reminders & Follow-ups
*(Things to check on later)*
        """.trimIndent()

        fun getDefaultContent(filename: String): String = when (filename) {
            "AGENTS.md"   -> DEFAULT_AGENTS_MD
            "SOUL.md"     -> DEFAULT_SOUL_MD
            "IDENTITY.md" -> DEFAULT_IDENTITY_MD
            "USER.md"     -> DEFAULT_USER_MD
            "MEMORY.md"   -> DEFAULT_MEMORY_MD
            else          -> ""
        }
    }

    private val personaDir: File
        get() = File(context.filesDir, "persona").also { it.mkdirs() }

    private val memoryDir: File
        get() = File(personaDir, "memory").also { it.mkdirs() }

    // --- Mode ---

    fun getMode(): PersonaMode = runBlocking {
        val raw = context.personaStore.data.map { it[KEY_MODE] ?: "SIMPLE" }.first()
        try { PersonaMode.valueOf(raw) } catch (_: Exception) { PersonaMode.SIMPLE }
    }

    suspend fun setMode(mode: PersonaMode) {
        context.personaStore.edit { it[KEY_MODE] = mode.name }
    }

    // --- Simple ---

    fun getSimplePersona(): SimplePersona = runBlocking {
        context.personaStore.data.map { prefs ->
            SimplePersona(
                tone = prefs[KEY_TONE] ?: "",
                characteristic = prefs[KEY_CHARACTERISTIC] ?: "",
                customInstruction = prefs[KEY_INSTRUCTION] ?: "",
                nickname = prefs[KEY_NICKNAME] ?: "",
                aboutYou = prefs[KEY_ABOUT] ?: ""
            )
        }.first()
    }

    suspend fun saveSimplePersona(persona: SimplePersona) {
        context.personaStore.edit { prefs ->
            prefs[KEY_TONE] = persona.tone
            prefs[KEY_CHARACTERISTIC] = persona.characteristic
            prefs[KEY_INSTRUCTION] = persona.customInstruction
            prefs[KEY_NICKNAME] = persona.nickname
            prefs[KEY_ABOUT] = persona.aboutYou
        }
    }

    // --- Pro files ---

    fun readFile(name: String): String {
        val file = File(personaDir, name)
        return if (file.exists()) file.readText() else ""
    }

    fun writeFile(name: String, content: String) {
        File(personaDir, name).writeText(content)
    }

    /** Reset a specific file to its default content. */
    fun resetFile(name: String) {
        writeFile(name, getDefaultContent(name))
    }

    /** Initialize all MD files with default content if they are currently empty or missing. */
    fun initializeIfEmpty() {
        PRO_FILES.forEach { filename ->
            val file = File(personaDir, filename)
            if (!file.exists() || file.readText().isBlank()) {
                writeFile(filename, getDefaultContent(filename))
            }
        }
    }

    fun appendDailyLog(text: String) {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val file = File(memoryDir, "$today.md")
        val timestamp = java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        file.appendText("- [$timestamp] $text\n")
    }

    fun listDailyLogs(): List<String> {
        return memoryDir.listFiles()
            ?.filter { it.extension == "md" }
            ?.sortedByDescending { it.name }
            ?.map { it.name }
            ?: emptyList()
    }

    /** Returns true if there is a non-empty memory log entry for today. */
    fun hasTodayLog(): Boolean {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val file = File(memoryDir, "$today.md")
        return file.exists() && file.readText().isNotBlank()
    }

    // --- Build prompt fragment ---

    fun buildPromptFragment(): String {
        return when (getMode()) {
            PersonaMode.SIMPLE -> buildSimpleFragment()
            PersonaMode.PRO -> buildProFragment()
        }
    }

    private fun buildSimpleFragment(): String {
        val p = getSimplePersona()
        val parts = mutableListOf<String>()
        if (p.nickname.isNotBlank()) parts.add("Address the user as \"${p.nickname}\".")
        if (p.tone.isNotBlank()) parts.add("Communication style: ${p.tone}.")
        if (p.characteristic.isNotBlank()) parts.add("Key traits: ${p.characteristic}.")
        if (p.aboutYou.isNotBlank()) parts.add("About the user: ${p.aboutYou}.")
        if (p.customInstruction.isNotBlank()) parts.add("Custom instructions: ${p.customInstruction}")
        return if (parts.isEmpty()) "" else "\nPERSONALIZATION:\n${parts.joinToString("\n")}"
    }

    private fun buildProFragment(): String {
        val sb = StringBuilder()

        PRO_FILES.forEach { filename ->
            val content = readFile(filename)
            if (content.isNotBlank()) {
                sb.appendLine("\n--- $filename ---")
                sb.appendLine(content)
            }
        }

        // Append today's memory log
        val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val todayLog = File(memoryDir, "$today.md")
        if (todayLog.exists() && todayLog.readText().isNotBlank()) {
            sb.appendLine("\n--- Today's Memory Log ($today) ---")
            sb.appendLine(todayLog.readText())
        }

        return sb.toString()
    }
}
