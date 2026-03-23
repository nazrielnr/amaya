package com.amaya.intelligence.ui.components.shared

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.amaya.intelligence.domain.models.ToolCategory
import com.amaya.intelligence.domain.models.ToolExecution
import java.util.LinkedHashMap

@Composable
internal fun FileTypeHeaderIcon(
    filePath: String,
    modifier: Modifier = Modifier,
    resolvedAssetName: String? = null
) {
    val context = LocalContext.current
    val assetNames = remember { loadFileTypeIconAssetNames(context) }
    val assetName = resolvedAssetName ?: remember(filePath, assetNames) {
        resolveFileTypeIconAssetName(filePath, assetNames)
    } ?: return
    val imageRequest = remember(context, assetName) {
        ImageRequest.Builder(context)
            .data(Uri.parse("file:///android_asset/icons/$assetName"))
            .decoderFactory(SvgDecoder.Factory())
            .crossfade(false)
            .build()
    }

    AsyncImage(
        model = imageRequest,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
    )
}

private object FileTypeIconAssetCache {
    @Volatile
    var assetNames: Set<String>? = null
}

private object FileTypeIconResolveCache {
    private const val MAX_ENTRIES = 1024
    private val lock = Any()
    private val map = object : LinkedHashMap<String, String>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    fun get(path: String): String? = synchronized(lock) { map[path] }
    fun put(path: String, assetName: String) = synchronized(lock) { map[path] = assetName }
}

internal fun loadFileTypeIconAssetNames(context: android.content.Context): Set<String> {
    FileTypeIconAssetCache.assetNames?.let { return it }

    synchronized(FileTypeIconAssetCache) {
        FileTypeIconAssetCache.assetNames?.let { return it }

        val loaded = runCatching {
            context.assets.list("icons")
                ?.asSequence()
                ?.filter { it.startsWith("file_type_") && it.endsWith(".svg") }
                ?.toSet()
                ?: emptySet()
        }.getOrDefault(emptySet())

        FileTypeIconAssetCache.assetNames = loaded
        return loaded
    }
}

internal fun resolveFileTypeIconAssetName(filePath: String, assetNames: Set<String>): String? {
    FileTypeIconResolveCache.get(filePath)?.let { return it }

    val fileName = filePath.substringAfterLast('/').substringAfterLast('\\').trim()
    if (fileName.isBlank()) return null

    val normalizedFileName = normalizeFileTypeKey(fileName)
    val normalizedStem = normalizeFileTypeKey(fileName.substringBeforeLast('.', fileName))
    val normalizedPath = normalizeFileTypeKey(filePath)
    val ext = fileName.substringAfterLast('.', "").trim().lowercase()

    val candidateBaseNames = linkedSetOf<String>()
    candidateBaseNames += resolveSpecialFileTypeCandidates(normalizedFileName, normalizedStem, normalizedPath)
    if (ext.isNotBlank()) {
        candidateBaseNames += normalizeFileTypeKey(ext)
        candidateBaseNames += resolveFileTypeAliases(ext)
    }
    candidateBaseNames += normalizedStem
    candidateBaseNames += normalizedFileName

    candidateBaseNames.forEach { candidate ->
        findBestAssetMatch(candidate, assetNames)?.let {
            FileTypeIconResolveCache.put(filePath, it)
            return it
        }
    }

    return assetNames.firstOrNull { asset ->
        val base = normalizeAssetKey(asset)
        candidateBaseNames.any { candidate ->
            base == candidate || base.startsWith("${candidate}_") || base.startsWith("${candidate}2")
        }
    }?.also { FileTypeIconResolveCache.put(filePath, it) }
}

internal fun prefetchFileTypeIcons(context: android.content.Context, filePaths: List<String>) {
    if (filePaths.isEmpty()) return

    val assetNames = loadFileTypeIconAssetNames(context)
    if (assetNames.isEmpty()) return

    val uniqueAssets = linkedSetOf<String>()
    filePaths
        .asSequence()
        .distinct()
        .take(120)
        .forEach { path ->
            resolveFileTypeIconAssetName(path, assetNames)?.let { uniqueAssets += it }
        }

    if (uniqueAssets.isEmpty()) return

    val imageLoader = context.imageLoader
    uniqueAssets.forEach { assetName ->
        imageLoader.enqueue(
            ImageRequest.Builder(context)
                .data(Uri.parse("file:///android_asset/icons/$assetName"))
                .decoderFactory(SvgDecoder.Factory())
                .size(20)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.DISABLED)
                .networkCachePolicy(CachePolicy.DISABLED)
                .build()
        )
    }
}

private fun resolveFileTypeAliases(ext: String): List<String> {
    return when (ext) {
        "kt", "kts", "kotlin" -> listOf("kotlin")
        "java" -> listOf("java", "jar")
        "class" -> listOf("class", "java")
        "js", "jsx", "mjs", "cjs", "javascript" -> listOf("js_official", "js", "dotjs", "reactjs")
        "ts", "tsx", "typescript" -> listOf("typescript_official", "typescript", "reactts", "reacttemplate")
        "json", "json5", "jsonc" -> listOf("json_official", "json", "json_schema", "jsonld")
        "md", "markdown", "mdx" -> listOf("markdown", "mdx")
        "yml", "yaml" -> listOf("yaml_official", "yaml", "yamllint")
        "py", "pyw" -> listOf("python", "pyenv", "pytyped", "pytest")
        "html", "htm" -> listOf("html", "antlers_html")
        "css" -> listOf("css", "css2")
        "scss" -> listOf("scss", "sass")
        "sass" -> listOf("sass")
        "less" -> listOf("less")
        "xml" -> listOf("xml", "xaml")
        "gradle" -> listOf("gradle", "gradle2")
        "sh", "bash", "zsh", "fish", "shell" -> listOf("shell")
        "ps1", "psm1", "psd1" -> listOf("powershell2", "powershell", "powershell_psm", "powershell_psd")
        "rb" -> listOf("ruby")
        "go" -> listOf("go_package", "go")
        "rs" -> listOf("rust_toolchain", "rust")
        "sql" -> listOf("sql", "db", "sqlite")
        "hcl", "tf", "tfvars" -> listOf("terraform", "hashicorp")
        "groovy" -> listOf("groovy2", "groovy")
        "toml" -> listOf("toml", "cargo", "bunfig", "poetry")
        "ini" -> listOf("ini")
        "properties" -> listOf("ini")
        "txt" -> listOf("text")
        else -> emptyList()
    }
}

private fun resolveSpecialFileTypeCandidates(
    normalizedFileName: String,
    normalizedStem: String,
    normalizedPath: String
): List<String> {
    return when {
        normalizedPath.contains("_github_workflows_") -> listOf("github")
        normalizedPath.contains("_buildkite_") || normalizedStem.startsWith("buildkite") -> listOf("buildkite")
        normalizedStem == "vagrantfile" -> listOf("vagrant")
        normalizedStem.startsWith("vault") -> listOf("hashicorp", "terraform")

        normalizedFileName == "azure_pipelines_yml" || normalizedFileName == "azure_pipelines_yaml" -> listOf("azurepipelines", "azure")
        normalizedFileName == "gitlab_ci_yml" || normalizedFileName == "gitlab_ci_yaml" -> listOf("gitlab")
        normalizedPath.contains("_circleci_") -> listOf("circleci")
        normalizedFileName == "bitbucket_pipelines_yml" || normalizedFileName == "bitbucket_pipelines_yaml" -> listOf("bitbucketpipeline")
        normalizedFileName == "drone_yml" || normalizedFileName == "drone_yaml" -> listOf("drone")
        normalizedFileName == "travis_yml" || normalizedFileName == "travis_yaml" -> listOf("travis")
        normalizedStem == "jenkinsfile" || normalizedStem.startsWith("jenkins") -> listOf("jenkins")

        normalizedStem.startsWith("prometheus") || normalizedStem.startsWith("alertmanager") -> listOf("prometheus")
        normalizedStem.startsWith("datadog") -> listOf("datadog")
        normalizedStem.startsWith("snyk") -> listOf("snyk")
        normalizedStem.startsWith("codeql") -> listOf("codeql")

        normalizedFileName == "composer_json" || normalizedStem == "composer" || normalizedStem == "composer_lock" -> {
            listOf("composer")
        }

        normalizedFileName == "deno_json" || normalizedFileName == "deno_jsonc" || normalizedStem == "deno" || normalizedStem == "deno_lock" -> {
            listOf("deno")
        }

        normalizedStem == "tiltfile" -> listOf("tiltfile")
        normalizedStem == "helmfile" || normalizedStem == "chart" || normalizedStem == "charts" -> listOf("helm")

        normalizedStem.startsWith("dockerfile") || normalizedStem.startsWith("compose") || normalizedStem.contains("docker_compose") -> {
            listOf("docker", "docker2", "dockertest", "dockertest2")
        }

        normalizedFileName == "package_json" || normalizedStem == "package_json" -> {
            listOf("package", "npm", "npmpackagejsonlint")
        }

        normalizedFileName == "package_lock_json" || normalizedFileName == "npm_package_json" || normalizedStem == "package_lock" -> {
            listOf("npm", "package")
        }

        normalizedFileName == "pnpm_lock_yaml" || normalizedFileName == "pnpm_lock_yml" || normalizedStem == "pnpm_lock" -> {
            listOf("pnpm")
        }

        normalizedFileName == "yarn_lock" -> listOf("yarn")
        normalizedFileName == "bun_lockb" || normalizedStem == "bunfig" || normalizedStem == "bunfig_toml" -> listOf("bunfig", "bun")
        normalizedStem.startsWith("tsconfig") -> listOf("tsconfig_official", "tsconfig")
        normalizedStem.startsWith("jsconfig") -> listOf("jsconfig", "js_official", "js")
        normalizedStem.startsWith("eslint") || normalizedStem.contains("eslintrc") || normalizedStem.contains("eslintconfig") -> listOf("eslint", "eslint2")
        normalizedStem.startsWith("prettier") || normalizedStem.contains("prettierrc") -> listOf("prettier")
        normalizedStem.startsWith("babel") || normalizedStem.contains("babelrc") -> listOf("babel", "babel2")
        normalizedStem.startsWith("astro") -> listOf("astroconfig", "astro")
        normalizedStem.startsWith("cypress") -> {
            if (normalizedStem.contains("spec") || normalizedStem.contains("test")) {
                listOf("cypress_spec", "cypress")
            } else {
                listOf("cypress", "cypress_spec")
            }
        }
        normalizedStem.startsWith("playwright") -> listOf("playwright")
        normalizedStem.startsWith("vitest") -> listOf("vitest")
        normalizedStem.startsWith("vite") -> listOf("vite")
        normalizedStem.startsWith("next") -> listOf("next")
        normalizedStem.startsWith("nuxt") -> listOf("nuxt")
        normalizedStem.startsWith("tailwind") -> listOf("tailwind", "ng_tailwind")
        normalizedStem.startsWith("storybook") -> listOf("storybook")
        normalizedStem.startsWith("rollup") -> listOf("rollup")
        normalizedStem.startsWith("webpack") -> listOf("webpack")
        normalizedStem.startsWith("turbo") -> listOf("turbo")
        normalizedStem.startsWith("nx") -> listOf("nx")
        normalizedStem.startsWith("devcontainer") || normalizedStem.contains("devcontainer") -> listOf("devcontainer")
        normalizedStem.startsWith("mcp") -> listOf("mcp")
        normalizedStem.startsWith("editorconfig") -> listOf("editorconfig")
        normalizedStem == "env" || normalizedStem.startsWith("dotenv") || normalizedStem.startsWith("envrc") -> listOf("dotenv")
        normalizedStem.contains("gitignore") || normalizedStem.contains("gitattributes") || normalizedStem == "git" || normalizedStem.startsWith("git") -> listOf("git", "git2")
        normalizedStem.startsWith("npmrc") || normalizedStem.startsWith("node") || normalizedStem == "nvmrc" || normalizedStem == "node_version" || normalizedStem == "node-version" -> listOf("node", "node2", "nodemon", "npm")
        normalizedStem.startsWith("license") -> listOf("license", "licensebat")
        normalizedStem.startsWith("readme") || normalizedStem == "changelog" -> listOf("markdown")
        normalizedStem.startsWith("pyproject") || normalizedStem.startsWith("poetry") -> listOf("poetry", "python")
        normalizedStem.startsWith("requirements") || normalizedStem.startsWith("pipfile") || normalizedStem.startsWith("setup") -> listOf("python", "pip")
        normalizedStem.startsWith("tox") -> listOf("tox", "python")
        normalizedStem.startsWith("mypy") -> listOf("mypy", "python")
        normalizedStem.startsWith("pytest") -> listOf("pytest", "python")
        normalizedStem.startsWith("coverage") -> listOf("coverage")
        normalizedStem.startsWith("ruff") -> listOf("python")
        normalizedStem.startsWith("python_version") || normalizedStem.startsWith("pythonversion") -> listOf("python")
        normalizedStem.startsWith("cargo") -> listOf("cargo")
        normalizedStem.startsWith("rust_toolchain") || normalizedStem.startsWith("rustfmt") || normalizedStem.startsWith("clippy") -> listOf("rust_toolchain", "rust")
        normalizedStem == "go_work" || normalizedStem == "go_work_sum" -> listOf("go_work", "go_package", "go")
        normalizedStem.startsWith("go") -> listOf("go_package", "go")
        normalizedStem.startsWith("gradle") -> listOf("gradle", "gradle2")
        normalizedStem.startsWith("androidmanifest") || normalizedStem == "manifest" -> listOf("manifest", "manifest_bak", "manifest_skip")
        normalizedStem.startsWith("build_gradle") || normalizedStem.startsWith("settings_gradle") || normalizedStem.startsWith("gradle_properties") || normalizedStem.startsWith("local_properties") -> listOf("gradle", "gradle2", "maven")
        normalizedStem.startsWith("pom") -> listOf("maven")
        normalizedStem.startsWith("proguard") -> listOf("ini", "maven")
        normalizedStem == "lint" || normalizedStem.startsWith("lint_") -> listOf("xml", "ini")
        normalizedStem.startsWith("detekt") || normalizedStem.startsWith("ktlint") -> listOf("yaml_official", "yaml", "ini")
        normalizedStem.startsWith("google_services") -> listOf("json", "json_official")
        normalizedStem.startsWith("docker") -> listOf("docker", "docker2")
        normalizedStem.startsWith("package_json") -> listOf("npm", "package", "npmpackagejsonlint")
        normalizedStem.startsWith("package_lock") || normalizedStem.startsWith("npm_package_json") || normalizedStem == "shrinkwrap" -> listOf("npm", "package")
        normalizedStem.startsWith("yarn_lock") -> listOf("yarn")
        normalizedStem.startsWith("pnpm_lock") -> listOf("pnpm")
        normalizedStem.startsWith("pnpm_workspace") -> listOf("pnpm")
        normalizedStem.startsWith("bun_lock") || normalizedStem.startsWith("bunfig") -> listOf("bun", "bunfig")
        else -> emptyList()
    }
}

private fun findBestAssetMatch(candidate: String, assetNames: Set<String>): String? {
    val normalizedCandidate = normalizeFileTypeKey(candidate)
    if (normalizedCandidate.isBlank()) return null

    val preferredVariants = listOf(
        "file_type_${normalizedCandidate}.svg",
        "file_type_light_${normalizedCandidate}.svg",
        "file_type_${normalizedCandidate}_official.svg",
        "file_type_${normalizedCandidate}2.svg"
    )
    preferredVariants.firstOrNull { it in assetNames }?.let { return it }

    return assetNames.firstOrNull { asset ->
        val base = normalizeAssetKey(asset)
        base == normalizedCandidate || base.startsWith("${normalizedCandidate}_") || base.startsWith("${normalizedCandidate}2")
    }
}

private fun normalizeAssetKey(assetName: String): String {
    val withoutPrefix = when {
        assetName.startsWith("file_type_light_") -> assetName.removePrefix("file_type_light_")
        assetName.startsWith("file_type_") -> assetName.removePrefix("file_type_")
        else -> assetName
    }

    return withoutPrefix
        .removeSuffix(".svg")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
}

private fun normalizeFileTypeKey(value: String): String {
    return value
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
}

internal fun resolveFileTypeSourcePath(execution: ToolExecution): String? {
    return listOfNotNull(
        execution.arguments["path"]?.toString(),
        execution.arguments["AbsolutePath"]?.toString(),
        execution.arguments["filePath"]?.toString(),
        execution.arguments["File"]?.toString(),
        execution.arguments["TargetFile"]?.toString(),
        execution.arguments["DirectoryPath"]?.toString(),
        execution.arguments["cwd"]?.toString()
    ).firstOrNull { it.isNotBlank() }
}

internal fun shouldUseFileTypeIcon(execution: ToolExecution): Boolean {
    val path = resolveFileTypeSourcePath(execution) ?: return false
    val fileName = path.substringAfterLast('/').substringAfterLast('\\').trim()
    if (fileName.isBlank()) return false

    val normalizedStem = normalizeFileTypeKey(fileName.substringBeforeLast('.', fileName))
    val normalizedFileName = normalizeFileTypeKey(fileName)
    val normalizedPath = normalizeFileTypeKey(path)
    val ext = fileName.substringAfterLast('.', "").lowercase()

    if (execution.uiMetadata?.category != ToolCategory.FILE_IO) return false

    return ext in setOf(
        "kt", "kts", "java", "class", "ts", "tsx", "js", "jsx", "mjs", "cjs",
        "json", "json5", "jsonc", "md", "markdown", "mdx", "yml", "yaml",
        "py", "pyw", "html", "htm", "css", "scss", "sass", "less",
        "xml", "gradle", "sh", "bash", "zsh", "fish", "ps1", "psm1", "psd1",
        "rb", "go", "rs", "groovy", "toml", "ini", "properties", "txt",
        "sql", "hcl", "tf", "tfvars"
    ) || resolveSpecialFileTypeCandidates(normalizedFileName, normalizedStem, normalizedPath).isNotEmpty()
}