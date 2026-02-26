package com.opencode.mobile.data.repository

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
