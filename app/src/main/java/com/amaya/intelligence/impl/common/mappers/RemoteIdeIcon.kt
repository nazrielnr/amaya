package com.amaya.intelligence.impl.common.mappers

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.vector.ImageVector
import com.amaya.intelligence.R

object RemoteIdeIcon {
    data class Spec(
        val resId: Int? = null,
        val imageVector: ImageVector? = null,
        val tintable: Boolean
    )

    fun resolve(ideId: String, isDarkTheme: Boolean): Spec? {
        return when (ideId.lowercase()) {
            "antigravity" -> Spec(resId = R.drawable.ic_ide_antigravity, tintable = true)
            "cursor" -> Spec(resId = R.drawable.ic_ide_cursor, tintable = true)
            "windsurf" -> Spec(resId = R.drawable.ic_ide_windsurf, tintable = true)
            "githubcopilot" -> Spec(resId = R.drawable.ic_ide_githubcopilot, tintable = true)
            "opencode" -> Spec(resId = R.drawable.ic_ide_opencode, tintable = true)
            "trae" -> Spec(resId = R.drawable.ic_ide_trae, tintable = true)
            "codex" -> Spec(resId = R.drawable.ic_ide_codex, tintable = true)
            else -> null
        }
    }
}
