package com.amaya.intelligence.ui.screens.persona.shared

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amaya.intelligence.data.repository.PersonaRepository
import com.amaya.intelligence.data.repository.SimplePersona
import com.amaya.intelligence.ui.res.UiStrings
import com.amaya.intelligence.ui.theme.SectionShape
import kotlinx.coroutines.launch

@Composable
fun SimplePersonaEditor(
    personaRepository: PersonaRepository,
    persona: SimplePersona,
    onPersonaChange: (SimplePersona) -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()

    Surface(
        shape = SectionShape,
        color = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else Color.White,
        tonalElevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            PersonaTextField(
                label = UiStrings.Persona.STYLE_TONE,
                value = persona.tone,
                onValueChange = { onPersonaChange(persona.copy(tone = it)) },
                placeholder = UiStrings.Placeholders.STYLE_TONE_EXAMPLE,
                pills = listOf("Friendly", "Concise", "Professional", "Academic", "Humorous")
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            PersonaTextField(
                label = UiStrings.Persona.CHARACTERISTIC,
                value = persona.characteristic,
                onValueChange = { onPersonaChange(persona.copy(characteristic = it)) },
                placeholder = UiStrings.Placeholders.CHARACTERISTIC_EXAMPLE,
                pills = listOf("Analytical", "Patient", "Creative", "Thorough", "Direct", "Helpful")
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            PersonaTextField(
                label = UiStrings.Persona.CUSTOM_INSTRUCTION,
                value = persona.customInstruction,
                onValueChange = { onPersonaChange(persona.copy(customInstruction = it)) },
                placeholder = UiStrings.Placeholders.CUSTOM_INSTRUCTION_EXAMPLE,
                maxLines = 5
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            PersonaTextField(
                label = UiStrings.Persona.YOUR_NICKNAME,
                value = persona.nickname,
                onValueChange = { onPersonaChange(persona.copy(nickname = it)) },
                placeholder = UiStrings.Placeholders.NICKNAME_EXAMPLE,
                pills = listOf("Boss", "Friend", "User")
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            PersonaTextField(
                label = UiStrings.Persona.MORE_ABOUT_YOU,
                value = persona.aboutYou,
                onValueChange = { onPersonaChange(persona.copy(aboutYou = it)) },
                placeholder = UiStrings.Placeholders.ABOUT_YOU_EXAMPLE,
                maxLines = 5
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    scope.launch {
                        personaRepository.saveSimplePersona(persona)
                        onSaved()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(UiStrings.Actions.SAVE_PERSONA, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
