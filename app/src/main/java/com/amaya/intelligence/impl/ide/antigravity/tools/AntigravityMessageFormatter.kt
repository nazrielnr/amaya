package com.amaya.intelligence.impl.ide.antigravity.tools

/**
 * Formatter specifically for Antigravity chat messages.
 * Antigravity IDE sends file references as [filename.kt](file:///c:/path/to/file).
 * The Android app cannot navigate to local PC file paths via the URI handler,
 * so this formatter intercepts the message and converts these raw links
 * into neatly highlighted text (e.g. **filename.kt**) before rendering.
 */
object AntigravityMessageFormatter {
    private val FILE_LINK_REGEX = Regex("""\[([^\]]+)]\(file:///[^)]+\)""")

    fun format(content: String): String {
        return FILE_LINK_REGEX.replace(content) { matchResult ->
            val label = matchResult.groupValues[1]
            "**$label**"
        }
    }
}
