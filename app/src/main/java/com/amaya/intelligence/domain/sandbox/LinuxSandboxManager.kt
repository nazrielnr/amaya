package com.amaya.intelligence.domain.sandbox

import android.content.Context
import android.net.ConnectivityManager
import com.amaya.intelligence.util.debugLog
import com.amaya.intelligence.util.errorLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the embedded PRoot + Alpine Linux sandbox environment.
 * Allows executing arbitrary Linux tools (Python, Node.js, Git, GCC, C/C++)
 * inside an unprivileged Android container on both 32-bit and 64-bit devices without root.
 */
@Singleton
class LinuxSandboxManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "LinuxSandboxManager"
        private const val SANDBOX_DIR_NAME = "linux_sandbox"
        private const val ALPINE_DIR_NAME = "alpine"
        private const val BIN_DIR_NAME = "bin"
        private const val DEFAULT_RESOLV_CONF = "nameserver 8.8.8.8\nnameserver 1.1.1.1\n"

        /**
         * Host environment variables that can mislead or contaminate guest Linux binaries
         * when PRoot inherits Android process environment.
         */
        val HOST_LEAK_ENV_VARS = listOf(
            "ANDROID_ROOT",
            "ANDROID_DATA",
            "ANDROID_STORAGE",
            "BOOTCLASSPATH",
            "DEX2OATBOOTCLASSPATH",
            "EXTERNAL_STORAGE",
            "ASEC_MOUNTPOINT",
            "LD_PRELOAD"
        )

        /**
         * Re-create `<rootfs>/bin/sh` as a plain copy of `<rootfs>/bin/busybox`.
         * The minirootfs ships `bin/sh` as a symlink to the absolute host path
         * `/bin/busybox`, which dangles once extracted into app-private storage
         * (there is no `/bin` at the Android host root). Busybox dispatches on
         * argv[0], so a copy named `sh` behaves exactly like the ash shell.
         *
         * Returns true when `bin/sh` exists (or was healed) and is executable.
         * Visible for testing.
         */
        internal fun materializeSh(rootfsDir: File): Boolean {
            val busybox = File(rootfsDir, "bin/busybox")
            val sh = File(rootfsDir, "bin/sh")
            if (!busybox.exists()) return false
            try {
                sh.delete() // remove dangling (or valid) symlink
                busybox.copyTo(sh, overwrite = true)
                sh.setExecutable(true, false)
                sh.setReadable(true, false)
            } catch (e: Exception) {
                return false
            }
            return sh.exists()
        }

        /**
         * Builds a robust resolv.conf content. Combines device active network DNS servers
         * (e.g. Wi-Fi router / cellular carrier DNS) with reliable public DNS fallbacks
         * and options to prevent DNS hang on restricted networks.
         */
        internal fun buildResolvConf(context: Context? = null): String {
            val dnsList = mutableListOf<String>()
            if (context != null) {
                try {
                    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    val activeNetwork = cm?.activeNetwork
                    if (activeNetwork != null) {
                        val lp = cm.getLinkProperties(activeNetwork)
                        lp?.dnsServers?.forEach { addr ->
                            val host = addr.hostAddress
                            if (!host.isNullOrBlank() && !host.contains(":")) {
                                dnsList.add(host)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
            dnsList.add("8.8.8.8")
            dnsList.add("1.1.1.1")
            dnsList.add("9.9.9.9")
            dnsList.add("8.8.4.4")

            return buildString {
                dnsList.distinct().forEach { ip ->
                    appendLine("nameserver $ip")
                }
                appendLine("options timeout:2 attempts:2")
            }
        }

        /**
         * Pre-populates the guest `/etc/hosts` file with localhost entries and resolved/fallback
         * IP addresses for Alpine mirrors.
         *
         * Reason: On Android mobile networks (4G/5G) and enterprise Wi-Fi, direct UDP queries
         * to external DNS servers (port 53) are commonly blocked by ISP firewalls.
         * In Alpine Linux, musl libc always queries `/etc/hosts` before attempting UDP DNS.
         * By pre-populating `/etc/hosts`, `apk`, `git`, and other guest binaries connect directly
         * over TCP 443/80 without triggering blocked port 53 UDP queries.
         */
        internal fun populateGuestHostsFile(rootfsDir: File, resolveRemote: Boolean = false): Boolean {
            return try {
                val etcDir = File(rootfsDir, "etc")
                etcDir.mkdirs()
                val hostsFile = File(etcDir, "hosts")

                val lines = mutableListOf(
                    "127.0.0.1\tlocalhost",
                    "::1\tlocalhost"
                )

                // Known reliable Fastly Anycast and mirror IPs for Alpine
                val staticFallbackIps = mapOf(
                    "dl-cdn.alpinelinux.org" to listOf(
                        "151.101.2.132",
                        "151.101.66.132",
                        "151.101.130.132",
                        "151.101.194.132"
                    ),
                    "mirrors.edge.kernel.org" to listOf("213.196.21.55"),
                    "ftp.halifax.rwth-aachen.de" to listOf("137.226.34.46"),
                    "alpine.ethz.ch" to listOf("129.132.89.152"),
                    "mirror.csclub.uwaterloo.ca" to listOf("129.97.134.71"),
                    "mirrors.tuna.tsinghua.edu.cn" to listOf("101.6.15.130")
                )

                val mirrorDomains = listOf(
                    "dl-cdn.alpinelinux.org",
                    "mirrors.edge.kernel.org",
                    "ftp.halifax.rwth-aachen.de",
                    "alpine.ethz.ch",
                    "mirror.csclub.uwaterloo.ca",
                    "mirrors.tuna.tsinghua.edu.cn"
                )

                val resolved = mutableMapOf<String, MutableList<String>>()

                if (resolveRemote) {
                    for (domain in mirrorDomains) {
                        try {
                            val addresses = java.net.InetAddress.getAllByName(domain)
                            for (addr in addresses) {
                                val ip = addr.hostAddress
                                if (!ip.isNullOrBlank() && !ip.contains(":")) {
                                    resolved.getOrPut(domain) { mutableListOf() }.add(ip)
                                }
                            }
                        } catch (_: Exception) {
                            // Offline or network error; fall back to static list below
                        }
                    }
                }

                // Add static fallback IPs if remote resolution did not resolve the domain
                staticFallbackIps.forEach { (domain, ips) ->
                    if (resolved[domain].isNullOrEmpty()) {
                        resolved[domain] = ips.toMutableList()
                    }
                }

                resolved.forEach { (domain, ips) ->
                    ips.distinct().forEach { ip ->
                        lines.add("$ip\t$domain")
                    }
                }

                hostsFile.writeText(lines.joinToString("\n") + "\n")
                hostsFile.exists() && hostsFile.length() > 0L
            } catch (e: Exception) {
                false
            }
        }

        /**
         * Guarantees the guest can resolve DNS: installs `etc/resolv.conf` with
         * active network and public resolvers when missing or empty. The minirootfs ships an empty
         * resolv.conf, and without it `apk update` fails every download.
         * Existing non-empty custom configurations are left untouched. Visible for testing.
         */
        internal fun provisionGuestDns(rootfsDir: File, context: Context? = null): Boolean {
            return try {
                populateGuestHostsFile(rootfsDir, resolveRemote = false)
                val resolvConf = File(rootfsDir, "etc/resolv.conf")
                if (resolvConf.exists() && resolvConf.length() > 0L) {
                    val currentContent = resolvConf.readText()
                    // If it was the legacy default (8.8.8.8 and 1.1.1.1 only with no carrier DNS),
                    // upgrade it with active network DNS if available.
                    if (currentContent == DEFAULT_RESOLV_CONF && context != null) {
                        resolvConf.writeText(buildResolvConf(context))
                    }
                    return true
                }
                resolvConf.parentFile?.mkdirs()
                resolvConf.writeText(buildResolvConf(context))
                resolvConf.exists() && resolvConf.length() > 0L
            } catch (e: Exception) {
                false
            }
        }

        /**
         * Builds the APK repositories file from a specific mirror base. Visible for testing.
         */
        internal fun writeApkRepositories(rootfsDir: File, mirrorBase: String) {
            val apkDir = File(rootfsDir, "etc/apk")
            apkDir.mkdirs()
            val repos = File(apkDir, "repositories")
            repos.writeText(
                "$mirrorBase/$ALPINE_BRANCH/main\n$mirrorBase/$ALPINE_BRANCH/community\n"
            )
        }

        /**
         * Provisions the repositories file with the preferred mirror. Kept for
         * install bootstrap; package installs additionally walk [ALPINE_MIRRORS]
         * when a mirror is unreachable. Visible for testing.
         */
        internal fun configureApkRepositories(rootfsDir: File) {
            writeApkRepositories(rootfsDir, ALPINE_MIRRORS.first())
        }

        /**
         * Builds the command arguments for PRoot or direct shell execution.
         */
        internal fun buildExecutionArgs(
            prootPath: String?,
            rootfsDir: File,
            command: String,
            workspaceDir: String?,
            prootTmpDir: File? = null
        ): List<String> {
            return if (prootPath != null) {
                val list = mutableListOf(
                    prootPath,
                    "--kill-on-exit",
                    "--link2symlink",
                    "-0",
                    "-r", rootfsDir.absolutePath,
                    "-b", "/dev",
                    "-b", "/proc",
                    "-b", "/sys"
                )
                if (prootTmpDir != null) {
                    list.add("-b")
                    list.add("${prootTmpDir.absolutePath}:/tmp")
                }
                if (workspaceDir != null && File(workspaceDir).exists()) {
                    list.add("-b")
                    list.add("$workspaceDir:/workspace")
                    list.add("-w")
                    list.add("/workspace")
                } else {
                    list.add("-w")
                    list.add("/root")
                }
                list.add("/bin/sh")
                list.add("-c")
                list.add(command)
                list
            } else {
                listOf("/system/bin/sh", "-c", command)
            }
        }

        /**
         * Builds the environment variables for command execution.
         */
        internal fun buildExecutionEnv(
            hasProot: Boolean,
            prootTmpDir: File,
            prootLoaderPath: String? = null,
            is64Bit: Boolean? = null
        ): Map<String, String> {
            return buildMap {
                put("HOME", "/root")
                put("USER", "root")
                put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
                put("TERM", "xterm-256color")
                put("LANG", "C.UTF-8")
                put("SHELL", "/bin/sh")
                if (hasProot) {
                    put("TMPDIR", "/tmp")
                    put("PROOT_TMP_DIR", prootTmpDir.absolutePath)
                    put("PROOT_NO_SECCOMP", "1")
                    put("PROOT_IGNORE_MISSING_BINDINGS", "1")
                    if (!prootLoaderPath.isNullOrBlank()) {
                        put("PROOT_LOADER", prootLoaderPath)
                        when (is64Bit) {
                            true -> put("PROOT_LOADER_64", prootLoaderPath)
                            false -> put("PROOT_LOADER_32", prootLoaderPath)
                            null -> {
                                put("PROOT_LOADER_32", prootLoaderPath)
                                put("PROOT_LOADER_64", prootLoaderPath)
                            }
                        }
                    }
                    put("SSL_CERT_FILE", "/etc/ssl/certs/ca-certificates.crt")
                    put("GIT_SSL_CAINFO", "/etc/ssl/certs/ca-certificates.crt")
                    put("CURL_CA_BUNDLE", "/etc/ssl/certs/ca-certificates.crt")
                }
            }
        }

        /**
         * Maps a [LinuxArchitecture] to its ELF `e_machine` value so embedded loader
         * candidates can be validated before extraction. Visible for testing.
         */
        internal fun elfMachine(arch: LinuxArchitecture): Int = when (arch) {
            LinuxArchitecture.AARCH64 -> 183 // EM_AARCH64
            LinuxArchitecture.ARMV7 -> 40 // EM_ARM
            LinuxArchitecture.X86_64 -> 62 // EM_X86_64
            LinuxArchitecture.X86 -> 3 // EM_386
        }

        /**
         * e_machine values of the Linux architectures this sandbox supports.
         * An embedded loader candidate must carry one of these; incidental `ELF`
         * byte sequences inside a static binary carry garbage values (e.g. 50307)
         * or EM_NONE (0) and are rejected. The device's own architecture is always
         * in this set, but accepting the other known values also lets unit tests
         * extract an arm64 loader on an x86_64 CI runner.
         */
        private val KNOWN_LINUX_MACHINES = setOf(3, 40, 62, 183)

        /**
         * Returns true when the file starts with an ELF magic number.
         * Used to reject stale/corrupt loader files before handing them to PRoot.
         */
        internal fun isElfFile(file: File): Boolean {
            return try {
                if (!file.exists() || file.length() < 16L) return false
                val bytes = ByteArray(4)
                FileInputStream(file).use { input ->
                    if (input.read(bytes) != 4) return false
                }
                bytes[0] == 0x7f.toByte() && bytes[1] == 'E'.code.toByte() &&
                    bytes[2] == 'L'.code.toByte() && bytes[3] == 'F'.code.toByte()
            } catch (e: Exception) {
                false
            }
        }

        /**
         * Extracts the `ERROR:` lines from an apk transcript so mirror-walk failure
         * messages stay readable instead of dumping the whole retry log.
         */
        internal fun apkFailureLines(output: String): List<String> = output.lineSequence()
            .filter { it.startsWith("ERROR:") }
            .toList()

        /**
         * Extracts the embedded ELF loader from a PRoot binary into a target file.
         * Used when the loader is bundled inside PRoot and needs to be placed in an
         * executable location. Visible for testing.
         *
         * The scan validates every ELF candidate instead of trusting the first magic
         * number: static binaries can contain incidental `ELF` byte sequences that
         * are not loaders (e.g. the official x86_64 build has a false positive near
         * the start), and handing PRoot a garbage loader makes it silently fall back
         * to extracting its own loader and exec'ing it via `/proc/self/fd/N` — which
         * Android denies with EACCES.
         */
        internal fun extractEmbeddedLoader(sourceProot: File, destFile: File): Boolean {
            return try {
                if (!sourceProot.exists()) return false
                val bytes = sourceProot.readBytes()
                val elfMagic = byteArrayOf(0x7f, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte())
                var loaderOffset = -1
                var totalSize = 0L
                var i = 4
                while (i < bytes.size - 64) {
                    if (bytes[i] == elfMagic[0] &&
                        bytes[i + 1] == elfMagic[1] &&
                        bytes[i + 2] == elfMagic[2] &&
                        bytes[i + 3] == elfMagic[3]
                    ) {
                        val is64Bit = bytes[i + 4] == 2.toByte()
                        val machine = (ByteBuffer.wrap(bytes, i + 18, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF)
                        val eType = (ByteBuffer.wrap(bytes, i + 16, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF)
                        val size: Long = if (is64Bit) {
                            val shOff = ByteBuffer.wrap(bytes, i + 40, 8).order(ByteOrder.LITTLE_ENDIAN).long
                            val shEntSize = (ByteBuffer.wrap(bytes, i + 58, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF).toLong()
                            val shNum = (ByteBuffer.wrap(bytes, i + 60, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF).toLong()
                            shOff + (shEntSize * shNum)
                        } else {
                            val shOff = (ByteBuffer.wrap(bytes, i + 32, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL)
                            val shEntSize = (ByteBuffer.wrap(bytes, i + 46, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF).toLong()
                            val shNum = (ByteBuffer.wrap(bytes, i + 48, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF).toLong()
                            shOff + (shEntSize * shNum)
                        }
                        if (machine in KNOWN_LINUX_MACHINES && eType == 2 /* ET_EXEC */ && size > 0 && i + size <= bytes.size) {
                            loaderOffset = i
                            totalSize = size
                            break
                        }
                    }
                    i++
                }
                if (loaderOffset == -1) return false

                destFile.parentFile?.mkdirs()
                FileOutputStream(destFile).use { out ->
                    out.write(bytes, loaderOffset, totalSize.toInt())
                }
                destFile.setExecutable(true, false)
                destFile.setReadable(true, false)
                destFile.exists() && destFile.length() > 0L
            } catch (e: Exception) {
                false
            }
        }
    }

    private val sandboxBaseDir: File
        get() = File(context.filesDir, SANDBOX_DIR_NAME)

    val rootfsDir: File
        get() = File(sandboxBaseDir, ALPINE_DIR_NAME)

    val binDir: File
        get() = File(sandboxBaseDir, BIN_DIR_NAME)

    val prootFile: File
        get() = File(binDir, "proot")

    /**
     * The PRoot binary bundled inside the APK as a jniLib (`libproot.so`).
     * Android 10+ (W^X) forbids executing binaries from app-private storage,
     * but files in [ApplicationInfo.nativeLibraryDir] are still executable, so
     * we prefer the bundled copy and keep the downloaded `bin/proot` as fallback
     * (e.g. for x86 debug builds that carry no bundled `libproot.so`).
     */
    private val bundledProotFile: File?
        get() {
            val f = File(context.applicationInfo.nativeLibraryDir, "libproot.so")
            return if (f.exists() && f.canExecute()) f else null
        }

    /**
     * The PRoot loader binary bundled inside the APK as a jniLib (`libproot_loader.so`).
     * Bundling the loader directly in `nativeLibraryDir` eliminates W^X / SELinux
     * execution failures when PRoot attempts to extract and run its loader in temporary directories.
     */
    val bundledProotLoaderFile: File?
        get() {
            val f = File(context.applicationInfo.nativeLibraryDir, "libproot_loader.so")
            return if (f.exists() && f.canExecute()) f else null
        }

    /**
     * Returns the effective PRoot loader executable, preferring the bundled native library
     * and falling back to an on-demand extracted loader binary if necessary.
     */
    fun getOrExtractProotLoader(): File? {
        val bundled = bundledProotLoaderFile
        if (bundled != null) return bundled

        val fallback = File(binDir, "libproot_loader.so")
        if (fallback.exists() && fallback.canExecute() && isElfFile(fallback)) {
            return fallback
        }
        // Stale or corrupt extraction from an earlier run: remove it so a fresh
        // extraction never has to overwrite a file that `exists()` blocks.
        if (fallback.exists() && !isElfFile(fallback)) {
            fallback.delete()
        }

        val sourceProot = bundledProotFile ?: prootFile.takeIf { it.exists() }
        if (sourceProot != null) {
            if (extractEmbeddedLoader(sourceProot, fallback)) {
                return fallback
            }
        }
        return null
    }

    private val _status = MutableStateFlow<SandboxStatus>(SandboxStatus.NotInstalled)
    val status: StateFlow<SandboxStatus> = _status.asStateFlow()

    init {
        checkStatus()
    }

    /**
     * Check current state of the sandbox on disk.
     */
    fun checkStatus(): SandboxStatus {
        val arch = LinuxArchitecture.detect()
        val isRootfsReady = isReady()
        val isProotReady = bundledProotFile != null || (prootFile.exists() && prootFile.canExecute())

        val newStatus = if (isRootfsReady) {
            SandboxStatus.Ready(
                architecture = arch,
                rootfsPath = rootfsDir.absolutePath,
                prootAvailable = isProotReady,
                details = "Alpine Linux $ALPINE_VERSION (${arch.displayName})"
            )
        } else {
            SandboxStatus.NotInstalled
        }

        _status.value = newStatus
        return newStatus
    }

    /**
     * The minirootfs ships `bin/sh` as a symlink to the absolute host path
     * `/bin/busybox`, which dangles once extracted into app-private storage
     * (there is no `/bin` on the Android host root). `busybox` itself is a
     * regular file inside the rootfs, so it is the reliable readiness marker.
     */
    fun isReady(): Boolean {
        val busybox = File(rootfsDir, "bin/busybox")
        if (!rootfsDir.exists() || !busybox.exists()) return false
        val sh = File(rootfsDir, "bin/sh")
        if (!sh.exists()) {
            // Heal installs where the shipped absolute /bin/sh symlink dangles.
            if (!materializeSh(rootfsDir)) return false
        }
        return true
    }

    /**
     * Installs Alpine Linux minirootfs and PRoot binary for the target architecture.
     * Supports both 64-bit (ARM64, x86_64) and 32-bit (ARMv7, x86) ABIs.
     */
    suspend fun install(
        onProgress: (stage: String, progress: Float) -> Unit = { _, _ -> }
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val architecture = LinuxArchitecture.detect()
        try {
            debugLog(TAG, "Starting Alpine Linux installation for ${architecture.displayName}")
            _status.value = SandboxStatus.Installing("Initializing directories...", 0.05f)
            onProgress("Initializing directories...", 0.05f)

            if (!sandboxBaseDir.exists()) sandboxBaseDir.mkdirs()
            if (!binDir.exists()) binDir.mkdirs()
            if (rootfsDir.exists()) rootfsDir.deleteRecursively()
            rootfsDir.mkdirs()

            // 1. Download Alpine minirootfs (.tar.gz)
            val rootfsTarGz = File(sandboxBaseDir, "alpine-minirootfs.tar.gz")
            _status.value = SandboxStatus.Installing("Downloading Alpine Linux rootfs...", 0.15f)
            onProgress("Downloading Alpine Linux rootfs...", 0.15f)

            val downloadSuccess = downloadFileWithProgress(
                urls = architecture.minirootfsUrls,
                destination = rootfsTarGz,
                progressStart = 0.15f,
                progressEnd = 0.55f,
                stageName = "Downloading Alpine Linux rootfs (~4MB)...",
                onProgress = onProgress
            )

            if (!downloadSuccess || !rootfsTarGz.exists() || rootfsTarGz.length() < 1024L) {
                throw IOException("Failed to download Alpine rootfs archive from CDN mirrors")
            }

            // 2. Extract minirootfs
            _status.value = SandboxStatus.Installing("Extracting root filesystem...", 0.60f)
            onProgress("Extracting root filesystem...", 0.60f)

            extractTarGz(rootfsTarGz, rootfsDir) { extractProgress ->
                val overall = 0.60f + (extractProgress * 0.25f)
                _status.value = SandboxStatus.Installing("Extracting packages...", overall)
                onProgress("Extracting packages...", overall)
            }

            // Cleanup tar.gz to save device space
            rootfsTarGz.delete()

            // 3. Configure network DNS resolver
            _status.value = SandboxStatus.Installing("Configuring network & DNS...", 0.88f)
            onProgress("Configuring network & DNS...", 0.88f)
            setupDnsResolver()
            setupApkRepositories()

            // 4. Download and setup PRoot binary
            _status.value = SandboxStatus.Installing("Configuring PRoot binary...", 0.92f)
            onProgress("Configuring PRoot binary...", 0.92f)
            setupProotBinary(architecture)

            // 5. Make system binaries executable and heal the /bin/sh symlink
            fixExecutablePermissions(rootfsDir)
            materializeSh(rootfsDir)

            val readyStatus = SandboxStatus.Ready(
                architecture = architecture,
                rootfsPath = rootfsDir.absolutePath,
                prootAvailable = bundledProotFile != null || (prootFile.exists() && prootFile.canExecute()),
                details = "Alpine Linux $ALPINE_VERSION (${architecture.displayName})"
            )
            _status.value = readyStatus
            onProgress("Installation complete!", 1.0f)
            debugLog(TAG, "Alpine Linux sandbox successfully installed at ${rootfsDir.absolutePath}")
            Result.success(Unit)
        } catch (e: Exception) {
            errorLog(TAG, "Failed to install Alpine Linux sandbox: ${e.message}", e)
            val errorStatus = SandboxStatus.Error(e.message ?: "Installation failed", e)
            _status.value = errorStatus
            Result.failure(e)
        }
    }

    /**
     * Uninstalls the sandbox and frees all storage.
     */
    suspend fun uninstall(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            debugLog(TAG, "Uninstalling sandbox directory ${sandboxBaseDir.absolutePath}")
            sandboxBaseDir.deleteRecursively()
            _status.value = SandboxStatus.NotInstalled
            Result.success(Unit)
        } catch (e: Exception) {
            errorLog(TAG, "Failed to uninstall sandbox", e)
            Result.failure(e)
        }
    }

    /**
     * Builds execution command and environment for running a shell command inside the sandbox.
     */
    fun buildExecution(
        command: String,
        workspaceDir: String?
    ): Pair<List<String>, Map<String, String>> {
        val rootfsReady = File(rootfsDir, "bin/busybox").exists()
        val prootTmpDir = File(sandboxBaseDir, "tmp")
        prootTmpDir.mkdirs() // writable host dir for PRoot's loader extraction & guest /tmp bind
        try {
            prootTmpDir.setReadable(true, false)
            prootTmpDir.setWritable(true, false)
            prootTmpDir.setExecutable(true, false)
        } catch (_: Exception) {}

        if (rootfsReady) {
            File(rootfsDir, "tmp").mkdirs()
            File(rootfsDir, "root").mkdirs()
            ensureGuestDns()
            materializeSh(rootfsDir)
            healGuestLibraries(rootfsDir)
        }

        val prootPath = bundledProotFile?.absolutePath
            ?: prootFile.takeIf { it.exists() && it.canExecute() }?.absolutePath
        val hasProot = prootPath != null

        val cmdList = buildExecutionArgs(prootPath, rootfsDir, command, workspaceDir, prootTmpDir)

        val loaderFile = getOrExtractProotLoader()
        val envMap = buildExecutionEnv(
            hasProot = hasProot,
            prootTmpDir = prootTmpDir,
            prootLoaderPath = loaderFile?.absolutePath,
            is64Bit = LinuxArchitecture.detect().is64Bit
        )

        return Pair(cmdList, envMap)
    }

    /**
     * Helper to run an `apk` package installation command (e.g. `apk add --no-cache git curl`).
     *
     * Alpine mirrors go down independently of the one that served the rootfs, so the
     * install walks [ALPINE_MIRRORS], rewriting `etc/apk/repositories` before each
     * attempt until one answers (the pattern Kai's LinuxInstaller uses).
     */
    suspend fun runApkAdd(packageName: String): Result<String> = withContext(Dispatchers.IO) {
        checkStatus()
        if (!isReady()) {
            return@withContext Result.failure(IllegalStateException("Alpine Linux sandbox is not installed"))
        }

        // 1. Ensure guest DNS is configured with active network DNS and pre-resolved hosts
        ensureGuestDns()
        populateGuestHostsFile(rootfsDir, resolveRemote = true)

        // 2. Clear stale apk lock if any from previously interrupted operations
        val lockFile = File(rootfsDir, "lib/apk/db/lock")
        if (lockFile.exists()) {
            lockFile.delete()
        }

        // 3. For git / curl, ensure ca-certificates is installed alongside them so HTTPS operations work immediately
        val targetPackages = if (packageName.contains("git") || packageName.contains("curl")) {
            if (!packageName.contains("ca-certificates")) {
                "$packageName ca-certificates"
            } else {
                packageName
            }
        } else {
            packageName
        }

        val cmd = if (targetPackages.contains("ca-certificates")) {
            "apk --timeout 20 update && apk --timeout 60 add --no-cache $targetPackages && (which update-ca-certificates >/dev/null 2>&1 && update-ca-certificates || true)"
        } else {
            "apk --timeout 20 update && apk --timeout 60 add --no-cache $targetPackages"
        }

        val (execCmd, envMap) = buildExecution(cmd, null)

        try {
            // Mirrors go down independently of the one that served the rootfs
            // (and of each other), so rewrite etc/apk/repositories per attempt
            // and walk the list until one answers — the Kai installer pattern.
            // Also include HTTP mirror fallbacks if HTTPS handshake fails due to clock mismatch.
            val candidateMirrors = buildList {
                addAll(ALPINE_MIRRORS)
                add("http://dl-cdn.alpinelinux.org/alpine")
                add("http://mirrors.edge.kernel.org/alpine")
            }

            var lastOutput = ""
            var succeeded = false
            for (mirror in candidateMirrors) {
                currentCoroutineContext().ensureActive()
                writeApkRepositories(rootfsDir, mirror)
                val (exitCode, outputStr) = execSandboxCommand(execCmd, envMap, timeoutSeconds = 60)
                lastOutput = outputStr
                if (exitCode == 0) {
                    succeeded = true
                    break
                }
                debugLog(TAG, "apk failed on mirror $mirror (exit $exitCode); trying next mirror")
            }

            if (succeeded) {
                checkStatus()
                Result.success(lastOutput)
            } else {
                val failureDetail = apkFailureLines(lastOutput).joinToString("\n")
                    .ifBlank { lastOutput.takeLast(600).ifBlank { "no output captured" } }
                Result.failure(
                    IOException(
                        "Package installation failed on all ${candidateMirrors.size} Alpine mirrors:\n$failureDetail"
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Runs a prepared sandbox command, returning its exit code and merged
     * stdout/stderr output. Called once per mirror attempt by [runApkAdd].
     */
    private suspend fun execSandboxCommand(
        execCmd: List<String>,
        envMap: Map<String, String>,
        timeoutSeconds: Long
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        val processBuilder = ProcessBuilder(execCmd)
        processBuilder.environment().apply {
            putAll(envMap)
            HOST_LEAK_ENV_VARS.forEach { remove(it) }
        }
        processBuilder.redirectErrorStream(true)

        val process = processBuilder.start()

        val output = StringBuilder()
        val readerJob = async(Dispatchers.IO) {
            try {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        if (output.isNotEmpty()) output.append('\n')
                        output.append(line)
                        line = reader.readLine()
                    }
                }
            } catch (_: Exception) {}
        }

        val exited = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!exited) {
            process.destroyForcibly()
            readerJob.cancel()
            return@withContext Pair(-1, "Command timed out after $timeoutSeconds seconds. Network may be unreachable.")
        }

        readerJob.await()
        Pair(process.exitValue(), output.toString().trim())
    }

    /**
     * Ensures the guest can resolve DNS before executing commands in the rootfs.
     * Cheap and idempotent; safe to call before every exec.
     */
    private fun ensureGuestDns() {
        if (!provisionGuestDns(rootfsDir, context)) {
            debugLog(TAG, "Guest DNS provisioning skipped (rootfs not writable?)")
        }
    }

    private fun setupDnsResolver() {
        val etcDir = File(rootfsDir, "etc")
        etcDir.mkdirs()
        val resolvConf = File(etcDir, "resolv.conf")
        resolvConf.writeText(buildResolvConf(context))
        populateGuestHostsFile(rootfsDir, resolveRemote = true)
    }

    internal fun ensureApkRepositories() {
        configureApkRepositories(rootfsDir)
    }

    private fun setupApkRepositories() {
        ensureApkRepositories()
    }

    private suspend fun setupProotBinary(architecture: LinuxArchitecture) {
        if (bundledProotFile != null || (prootFile.exists() && prootFile.canExecute())) {
            if (bundledProotFile != null) {
                debugLog(TAG, "Using bundled PRoot native library: ${bundledProotFile?.absolutePath}")
            }
            return
        }

        try {
            binDir.mkdirs()
            val request = Request.Builder().url(architecture.prootBinaryUrl).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.byteStream()?.use { input ->
                        FileOutputStream(prootFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    prootFile.setExecutable(true, false)
                    debugLog(TAG, "PRoot binary downloaded and set executable: ${prootFile.absolutePath}")
                } else {
                    debugLog(TAG, "PRoot remote binary returned ${response.code}; will use container fallback")
                }
            }
        } catch (e: Exception) {
            debugLog(TAG, "PRoot download skipped: ${e.message}")
        }
    }

    /**
     * Ensures all musl runtime dynamic linkers and shared libraries have execute permissions.
     * Guarantees that dynamically-linked guest binaries (like busybox and apk) can be loaded.
     */
    internal fun healGuestLibraries(rootfsDir: File) {
        val libDirs = listOf(File(rootfsDir, "lib"), File(rootfsDir, "usr/lib"))
        for (dir in libDirs) {
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { f ->
                    if (f.isFile && (f.name.startsWith("ld-musl") || f.name.startsWith("libc.musl") || f.name.endsWith(".so") || f.name.contains(".so."))) {
                        f.setExecutable(true, false)
                        f.setReadable(true, false)
                    }
                }
            }
        }
    }

    private fun fixExecutablePermissions(dir: File) {
        val execDirs = listOf(
            File(dir, "bin"),
            File(dir, "sbin"),
            File(dir, "usr/bin"),
            File(dir, "usr/sbin"),
            File(dir, "lib"),
            File(dir, "usr/lib")
        )
        for (d in execDirs) {
            if (d.exists() && d.isDirectory) {
                d.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        file.setExecutable(true, false)
                        file.setReadable(true, false)
                    }
                }
            }
        }
    }

    private fun downloadFileWithProgress(
        urls: List<String>,
        destination: File,
        progressStart: Float,
        progressEnd: Float,
        stageName: String,
        onProgress: (String, Float) -> Unit
    ): Boolean {
        for (targetUrl in urls) {
            try {
                val request = Request.Builder().url(targetUrl).build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use

                    val body = response.body ?: return@use
                    val contentLength = body.contentLength()
                    val inputStream = body.byteStream()

                    destination.parentFile?.mkdirs()
                    FileOutputStream(destination).use { outputStream ->
                        val buffer = ByteArray(32 * 1024)
                        var bytesRead: Int
                        var totalRead = 0L

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalRead += bytesRead

                            if (contentLength > 0) {
                                val downloadRatio = totalRead.toFloat() / contentLength.toFloat()
                                val currentOverall = progressStart + (downloadRatio * (progressEnd - progressStart))
                                onProgress(stageName, currentOverall)
                            }
                        }
                    }
                    if (destination.exists() && destination.length() > 1024L) {
                        return true
                    }
                }
            } catch (e: Exception) {
                debugLog(TAG, "Failed downloading from $targetUrl: ${e.message}, trying backup...")
            }
        }
        return false
    }

    private fun extractTarGz(
        tarGzFile: File,
        destDir: File,
        onProgress: (Float) -> Unit
    ) {
        val totalBytes = tarGzFile.length()
        var processedBytes = 0L

        FileInputStream(tarGzFile).use { fis ->
            BufferedInputStream(fis).use { bis ->
                GzipCompressorInputStream(bis).use { gzis ->
                    TarArchiveInputStream(gzis).use { tarIn ->
                        var entry: TarArchiveEntry? = tarIn.nextTarEntry
                        while (entry != null) {
                            val targetFile = File(destDir, entry.name)

                            // Security check: Zip Slip prevention
                            if (!targetFile.canonicalPath.startsWith(destDir.canonicalPath)) {
                                entry = tarIn.nextTarEntry
                                continue
                            }

                            if (entry.isDirectory) {
                                targetFile.mkdirs()
                            } else if (entry.isSymbolicLink) {
                                try {
                                    targetFile.parentFile?.mkdirs()
                                    if (targetFile.exists()) targetFile.delete()
                                    Files.createSymbolicLink(
                                        targetFile.toPath(),
                                        Paths.get(entry.linkName)
                                    )
                                } catch (_: Exception) {
                                    // Ignored if target filesystem doesn't allow symlink
                                }
                            } else {
                                targetFile.parentFile?.mkdirs()
                                FileOutputStream(targetFile).use { out ->
                                    tarIn.copyTo(out)
                                }
                                if (entry.name.startsWith("bin/") ||
                                    entry.name.startsWith("sbin/") ||
                                    entry.name.startsWith("usr/bin/") ||
                                    entry.name.startsWith("usr/sbin/") ||
                                    entry.name.startsWith("lib/") ||
                                    entry.name.startsWith("usr/lib/") ||
                                    (entry.mode and 0b001_001_001) != 0
                                ) {
                                    targetFile.setExecutable(true, false)
                                    targetFile.setReadable(true, false)
                                }
                            }

                            processedBytes += entry.size
                            if (totalBytes > 0) {
                                val ratio = (processedBytes.toFloat() / (totalBytes * 3.5f)).coerceIn(0f, 1f)
                                onProgress(ratio)
                            }

                            entry = tarIn.nextTarEntry
                        }
                    }
                }
            }
        }
    }
}
