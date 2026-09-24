package com.amaya.intelligence.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Project Intelligence System phase D: the Android capability matrix.
 *
 * Turns the project's build config into a machine-readable compatibility constraint set so the
 * agent reasons about "does this work on EVERY declared target" instead of "does this work at all".
 * Static ABI knowledge (bitness, NEON) is combined with what is parsed from the workspace build
 * files (minSdk/targetSdk/compileSdk/ndkVersion/abiFilters) plus observed build outcomes. The
 * rendered matrix is injected into the prompt as project-level context, not a memory.
 */
data class AbiCapability(
    val name: String,
    val bitness: Int,
    val neon: Boolean,
    /** Whether the project declares this ABI in abiFilters (implied arm64-v8a/armeabi-v7a otherwise). */
    val declared: Boolean,
    val nativeLibraries: List<String> = emptyList(),
    val tested: Boolean = false,
    val lastVerifiedAt: Long? = null
)

data class AndroidCapabilityMatrix(
    val workspacePath: String,
    val androidProject: Boolean = false,
    val compileSdk: Int? = null,
    val minSdk: Int? = null,
    val targetSdk: Int? = null,
    val ndkVersion: String? = null,
    val buildSystem: String = "gradle",
    val abis: List<AbiCapability> = emptyList(),
    val nativeCodePresent: Boolean = false,
    /** True when native libs are 16KB-aligned (NDK r28+); null when not native or unknown. */
    val pageSize16KbReady: Boolean? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun empty(workspacePath: String) = AndroidCapabilityMatrix(workspacePath = workspacePath)
    }
}

interface AndroidCapabilityRepository {
    /** Parse the workspace build config (cached per workspace on disk) into a capability matrix. */
    suspend fun matrixFor(workspacePath: String): AndroidCapabilityMatrix
    /** Record a verified build outcome: marks declared ABIs tested on success. */
    suspend fun recordBuildOutcome(workspacePath: String, successful: Boolean): Result<Unit>
    suspend fun renderForContext(workspacePath: String?): String
}

@Singleton
class FileAndroidCapabilityRepository @Inject constructor(
    @ApplicationContext context: Context
) : AndroidCapabilityRepository {
    private val file = File(context.filesDir, "memory/android-capability.jsonl")
    private val fileLock = Any()

    override suspend fun matrixFor(workspacePath: String): AndroidCapabilityMatrix = withContext(Dispatchers.IO) {
        val matrix = parseWorkspace(workspacePath)
        synchronized(fileLock) {
            val cached = readAll().firstOrNull { it.workspacePath == workspacePath }
            val merged = mergeCachedOutcomes(matrix, cached)
            val records = readAll().toMutableList()
            val index = records.indexOfFirst { it.workspacePath == workspacePath }
            if (index >= 0) records[index] = merged else records.add(merged)
            writeAll(records)
            merged
        }
    }

    override suspend fun recordBuildOutcome(workspacePath: String, successful: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            synchronized(fileLock) {
                runCatching {
                    val records = readAll().toMutableList()
                    val index = records.indexOfFirst { it.workspacePath == workspacePath }
                    val current = if (index >= 0) records[index] else parseWorkspace(workspacePath)
                    val now = System.currentTimeMillis()
                    val updated = if (successful) {
                        current.copy(
                            abis = current.abis.map { it.copy(tested = true, lastVerifiedAt = now) },
                            updatedAt = now
                        )
                    } else {
                        current.copy(
                            abis = current.abis.map { it.copy(tested = false) },
                            updatedAt = now
                        )
                    }
                    if (index >= 0) records[index] = updated else records.add(updated)
                    writeAll(records)
                }
            }
        }

    override suspend fun renderForContext(workspacePath: String?): String = withContext(Dispatchers.IO) {
        if (workspacePath.isNullOrBlank()) return@withContext ""
        val matrix = matrixFor(workspacePath)
        if (!matrix.androidProject) return@withContext ""
        buildString {
            appendLine("# Android Compatibility — Context Only")
            appendLine("This project's declared Android target constraints. Reason about solutions against ALL of these, not just the host device.")
            appendLine()
            val sdkParts = buildList {
                matrix.compileSdk?.let { add("compileSdk $it") }
                matrix.minSdk?.let { add("minSdk $it") }
                matrix.targetSdk?.let { add("targetSdk $it") }
            }
            if (sdkParts.isNotEmpty()) appendLine("- SDK: ${sdkParts.joinToString(" | ")}")
            matrix.ndkVersion?.let { appendLine("- NDK: $it") }
            appendLine("- Build system: ${matrix.buildSystem}")
            appendLine("- Native code: ${if (matrix.nativeCodePresent) "present" else "none detected"}")
            if (matrix.abis.isNotEmpty()) {
                appendLine("- Declared ABIs:")
                matrix.abis.forEach { abi ->
                    val bits = "${abi.bitness}-bit"
                    val neon = if (abi.neon) "NEON" else "no NEON"
                    val state = when {
                        abi.tested -> "verified ✓"
                        abi.declared -> "declared (untested)"
                        else -> "not declared"
                    }
                    val libs = abi.nativeLibraries.take(3).joinToString(", ")
                    appendLine("  - ${abi.name} ($bits, $neon): $state${if (libs.isNotBlank()) " — native: $libs" else ""}")
                }
            }
            matrix.pageSize16KbReady?.let { ready ->
                appendLine("- 16 KB page-size: ${if (ready) "ready (native libs 16KB-aligned)" else "NOT ready — rebuild native libs with NDK r28+ for 16KB-aligned alignment (Android 15+ devices)"}")
            } ?: matrix.nativeCodePresent.let {
                appendLine("- 16 KB page-size: verify native libraries are 16KB-aligned (Android 15+ devices may use 16KB pages)")
            }
        }.trim()
    }

    // ====================================================================
    // Parsing
    // ====================================================================

    private fun parseWorkspace(workspacePath: String): AndroidCapabilityMatrix {
        val root = File(workspacePath)
        if (!root.isDirectory) return AndroidCapabilityMatrix.empty(workspacePath)
        val gradleFiles = findFiles(root, setOf("build.gradle", "build.gradle.kts"))
        val androidProject = gradleFiles.any { file ->
            file.readTextSafe().contains("com.android.application", ignoreCase = true)
        }
        if (!androidProject) return AndroidCapabilityMatrix(workspacePath = workspacePath, androidProject = false)

        val allGradle = (gradleFiles + findFiles(root, setOf("gradle.properties", "settings.gradle", "settings.gradle.kts")))
            .flatMap { it.readTextSafe().lineSequence().toList() }
        val appGradle = gradleFiles.map { it.readTextSafe() }
        val manifest = findFiles(root, setOf("AndroidManifest.xml")).firstOrNull()?.readTextSafe().orEmpty()

        val compileSdk = firstInt(appGradle, COMPILE_SDK_PATTERNS) ?: firstInt(listOf(manifest), COMPILE_SDK_PATTERNS)
        val minSdk = firstInt(allGradle + listOf(manifest), MIN_SDK_PATTERNS)
        val targetSdk = firstInt(allGradle + listOf(manifest), TARGET_SDK_PATTERNS)
        val ndkVersion = firstString(allGradle, NDK_VERSION_PATTERNS)

        val abiNames = parseAbiFilters(allGradle).ifEmpty { DEFAULT_ABI_FILTERS }
        val nativeLibraries = discoverNativeLibraries(root)
        val nativeCodePresent = nativeLibraries.isNotEmpty() ||
            allGradle.any { it.contains("externalNativeBuild", ignoreCase = true) } ||
            findFiles(root, setOf("CMakeLists.txt")).isNotEmpty()

        val abis = abiNames.map { name ->
            val known = KNOWN_ABIS[name]
            AbiCapability(
                name = name,
                bitness = known?.bitness ?: 64,
                neon = known?.neon ?: true,
                declared = name in abiNames,
                nativeLibraries = nativeLibraries.filter { it.contains(name, ignoreCase = true) }.take(4),
                tested = false
            )
        }

        val pageSize16KbReady = when {
            !nativeCodePresent -> true
            ndkVersion != null -> parseNdkMajor(ndkVersion) >= NDK_16KB_ALIGNED_MAJOR
            else -> null
        }
        return AndroidCapabilityMatrix(
            workspacePath = workspacePath,
            androidProject = true,
            compileSdk = compileSdk,
            minSdk = minSdk,
            targetSdk = targetSdk,
            ndkVersion = ndkVersion,
            buildSystem = "gradle",
            abis = abis,
            nativeCodePresent = nativeCodePresent,
            pageSize16KbReady = pageSize16KbReady,
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun mergeCachedOutcomes(parsed: AndroidCapabilityMatrix, cached: AndroidCapabilityMatrix?): AndroidCapabilityMatrix {
        if (cached == null) return parsed
        val outcomeById = cached.abis.associateBy { it.name }
        return parsed.copy(
            abis = parsed.abis.map { abi ->
                val previous = outcomeById[abi.name]
                if (previous != null) abi.copy(tested = previous.tested, lastVerifiedAt = previous.lastVerifiedAt) else abi
            },
            pageSize16KbReady = parsed.pageSize16KbReady ?: cached.pageSize16KbReady
        )
    }

    private fun parseAbiFilters(lines: List<String>): List<String> {
        val found = mutableListOf<String>()
        for (line in lines) {
            if (!line.contains("abiFilters", ignoreCase = true)) continue
            KNOWN_ABIS.keys.forEach { abi ->
                if (line.contains(abi, ignoreCase = true) && abi !in found) found += abi
            }
        }
        return found
    }

    private fun discoverNativeLibraries(root: File): List<String> {
        val libDirs = findFiles(root, setOf("jniLibs"), directories = true) +
            findFiles(root, setOf("libs"), directories = true)
        return libDirs.flatMap { dir ->
            dir.listFiles().orEmpty().filter { it.name.endsWith(".so") }.map { "${dir.name}/${it.name}" }
        }.distinct()
    }

    private fun parseNdkMajor(version: String): Int {
        val match = Regex("r(\\d+)").find(version) ?: return 0
        return match.groupValues[1].toIntOrNull() ?: 0
    }

    private fun firstInt(sources: List<String>, patterns: List<Regex>): Int? {
        for (source in sources) {
            for (pattern in patterns) {
                pattern.find(source)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun firstString(sources: List<String>, patterns: List<Regex>): String? {
        for (source in sources) {
            for (pattern in patterns) {
                pattern.find(source)?.groupValues?.getOrNull(1)?.trim('"', '\'')?.takeIf(String::isNotBlank)?.let { return it }
            }
        }
        return null
    }

    private fun File.readTextSafe(): String = runCatching { readText() }.getOrDefault("")

    private fun findFiles(root: File, names: Set<String>, directories: Boolean = false): List<File> {
        val result = mutableListOf<File>()
        val stack = ArrayDeque<File>()
        stack.add(root)
        var scanned = 0
        while (stack.isNotEmpty() && scanned < MAX_SCAN_FILES) {
            val dir = stack.removeLast()
            val children = dir.listFiles().orEmpty()
            for (child in children) {
                scanned++
                if (child.isDirectory) {
                    if (directories && child.name in names) result.add(child)
                    if (!child.name.startsWith(".") && child.name !in SKIP_DIRS) stack.add(child)
                } else if (!directories && child.name in names) {
                    result.add(child)
                }
            }
        }
        return result.take(MAX_MATCHED_FILES)
    }

    private fun readAll(): List<AndroidCapabilityMatrix> = runCatching {
        if (!file.exists()) return emptyList()
        file.readLines().mapNotNull { line -> runCatching { JSONObject(line).toMatrix() }.getOrNull() }
    }.getOrDefault(emptyList())

    private fun writeAll(records: List<AndroidCapabilityMatrix>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(records.joinToString("\n") { it.toJson().toString() } + if (records.isEmpty()) "" else "\n")
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    private fun AndroidCapabilityMatrix.toJson(): JSONObject = JSONObject()
        .put("workspacePath", workspacePath)
        .put("androidProject", androidProject)
        .put("compileSdk", compileSdk)
        .put("minSdk", minSdk)
        .put("targetSdk", targetSdk)
        .put("ndkVersion", ndkVersion)
        .put("buildSystem", buildSystem)
        .put("nativeCodePresent", nativeCodePresent)
        .put("pageSize16KbReady", pageSize16KbReady)
        .put("updatedAt", updatedAt)
        .put("abis", JSONArray(abis.map { abi ->
            JSONObject()
                .put("name", abi.name)
                .put("bitness", abi.bitness)
                .put("neon", abi.neon)
                .put("declared", abi.declared)
                .put("nativeLibraries", JSONArray(abi.nativeLibraries))
                .put("tested", abi.tested)
                .put("lastVerifiedAt", abi.lastVerifiedAt)
        }))

    private fun JSONObject.toMatrix(): AndroidCapabilityMatrix = AndroidCapabilityMatrix(
        workspacePath = optString("workspacePath"),
        androidProject = optBoolean("androidProject"),
        compileSdk = optIntOrNull("compileSdk"),
        minSdk = optIntOrNull("minSdk"),
        targetSdk = optIntOrNull("targetSdk"),
        ndkVersion = optString("ndkVersion").takeIf(String::isNotBlank),
        buildSystem = optString("buildSystem", "gradle"),
        nativeCodePresent = optBoolean("nativeCodePresent"),
        pageSize16KbReady = if (has("pageSize16KbReady") && !isNull("pageSize16KbReady")) optBoolean("pageSize16KbReady") else null,
        updatedAt = optLong("updatedAt", System.currentTimeMillis()),
        abis = optJSONArray("abis")?.let { array ->
            List(array.length()) { index ->
                val abi = array.optJSONObject(index) ?: return@List null
                AbiCapability(
                    name = abi.optString("name"),
                    bitness = abi.optInt("bitness", 64),
                    neon = abi.optBoolean("neon", true),
                    declared = abi.optBoolean("declared", true),
                    nativeLibraries = abi.optJSONArray("nativeLibraries")?.let { libs ->
                        List(libs.length()) { libs.optString(it) }.filter(String::isNotBlank)
                    }.orEmpty(),
                    tested = abi.optBoolean("tested", false),
                    lastVerifiedAt = if (abi.has("lastVerifiedAt") && !abi.isNull("lastVerifiedAt")) abi.optLong("lastVerifiedAt") else null
                )
            }.filterNotNull()
        }.orEmpty()
    )

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    companion object {
        private const val MAX_SCAN_FILES = 800
        private const val MAX_MATCHED_FILES = 12
        private const val NDK_16KB_ALIGNED_MAJOR = 28
        /** NDK r28 is the first release that defaults to 16 KB alignment for native code. */
        private val SKIP_DIRS = setOf("build", ".gradle", ".idea", ".git", "node_modules", ".kotlin")
        /** Conservative defaults when abiFilters is absent (the AGP defaults). */
        private val DEFAULT_ABI_FILTERS = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        private val COMPILE_SDK_PATTERNS = listOf(
            Regex("compileSdk\\s*=\\s*(\\d+)"),
            Regex("compileSdkVersion\\s+(\\d+)")
        )
        private val MIN_SDK_PATTERNS = listOf(
            Regex("minSdk\\s*=\\s*(\\d+)"),
            Regex("minSdkVersion\\s+(\\d+)"),
            Regex("android:minSdkVersion=\"(\\d+)\"")
        )
        private val TARGET_SDK_PATTERNS = listOf(
            Regex("targetSdk\\s*=\\s*(\\d+)"),
            Regex("targetSdkVersion\\s+(\\d+)"),
            Regex("android:targetSdkVersion=\"(\\d+)\"")
        )
        private val NDK_VERSION_PATTERNS = listOf(
            Regex("ndkVersion\\s*=\\s*\"([^\"]+)\""),
            Regex("ndkVersion\\s+\"([^\"]+)\""),
            Regex("ndk.version\\s*=\\s*(\\S+)"),
            Regex("android\\.ndkVersion\\s*=\\s*\"([^\"]+)\"")
        )
        /** Static ABI knowledge: bitness and NEON availability per Android ABI. */
        private val KNOWN_ABIS = mapOf(
            "armeabi-v7a" to AbiKnowledge(bitness = 32, neon = true),
            "arm64-v8a" to AbiKnowledge(bitness = 64, neon = true),
            "x86" to AbiKnowledge(bitness = 32, neon = false),
            "x86_64" to AbiKnowledge(bitness = 64, neon = false)
        )
    }
}

private data class AbiKnowledge(
    val bitness: Int,
    val neon: Boolean
)
