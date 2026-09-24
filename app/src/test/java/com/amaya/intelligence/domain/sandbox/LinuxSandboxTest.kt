package com.amaya.intelligence.domain.sandbox

import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class LinuxSandboxTest {

    @Test
    fun `architectures define correct 32-bit and 64-bit attributes`() {
        assertTrue(LinuxArchitecture.AARCH64.is64Bit)
        assertEquals("aarch64", LinuxArchitecture.AARCH64.alpineArch)
        assertEquals("aarch64", LinuxArchitecture.AARCH64.prootArch)

        assertFalse(LinuxArchitecture.ARMV7.is64Bit)
        assertEquals("armv7", LinuxArchitecture.ARMV7.alpineArch)
        assertEquals("arm", LinuxArchitecture.ARMV7.prootArch)

        assertTrue(LinuxArchitecture.X86_64.is64Bit)
        assertEquals("x86_64", LinuxArchitecture.X86_64.alpineArch)
        assertEquals("x86_64", LinuxArchitecture.X86_64.prootArch)

        assertFalse(LinuxArchitecture.X86.is64Bit)
        assertEquals("x86", LinuxArchitecture.X86.alpineArch)
        assertEquals("i686", LinuxArchitecture.X86.prootArch)
    }

    @Test
    fun `alpine version is pinned to 3_22 to avoid apk-tools 3 breaking under proot`() {
        // Alpine 3.23+ ships apk-tools 3, which uses execveat() in a way proot does
        // not support — `apk update` fails (often as exit code 255 under the sandbox
        // runtime). See termux/proot-distro#532 / #595. Bumping the pin requires a
        // proot version that supports apk-tools 3.
        assertTrue(
            "ALPINE_VERSION=$ALPINE_VERSION must stay on 3.22.x until proot supports apk-tools 3",
            ALPINE_VERSION.startsWith("3.22.")
        )
        assertEquals("v3.22", ALPINE_BRANCH)
    }

    @Test
    fun `minirootfs URLs point to valid pinned Alpine endpoints across mirrors`() {
        for (arch in LinuxArchitecture.entries) {
            val urls = arch.minirootfsUrls
            assertEquals(ALPINE_MIRRORS.size, urls.size)
            for (mirror in ALPINE_MIRRORS) {
                assertTrue(
                    "$mirror must serve the pinned v3.22 minirootfs",
                    urls.contains("$mirror/$ALPINE_BRANCH/releases/${arch.alpineArch}/alpine-minirootfs-$ALPINE_VERSION-${arch.alpineArch}.tar.gz")
                )
            }
            assertTrue(arch.prootBinaryUrl.contains(arch.prootArch))
        }
    }

    @Test
    fun `proot binary URLs point to existing v5_3_0 static release assets`() {
        for (arch in LinuxArchitecture.entries) {
            assertTrue(
                "proot URL must use the v5.3.0 -static assets (v5.4.0 paths 404)",
                arch.prootBinaryUrl.endsWith("proot-v5.3.0-${arch.prootArch}-static")
            )
        }
    }

    @Test
    fun `materializeSh replaces dangling bin sh symlink with busybox copy`() {
        val rootfs = createTempDirectory("alpine-rootfs-").toFile()
        try {
            val binDir = File(rootfs, "bin").apply { mkdirs() }
            val busybox = File(binDir, "busybox").apply { writeText("# fake busybox payload") }
            // Simulate the minirootfs layout: bin/sh is an absolute symlink to a host
            // path that does not exist here. On Android `/bin/busybox` never exists on
            // the host root, so the link dangles; use a random absolute target so the
            // test also dangles on CI runners that happen to ship busybox in /bin.
            val sh = File(binDir, "sh")
            java.nio.file.Files.createSymbolicLink(
                sh.toPath(),
                java.nio.file.Paths.get("/bin/busybox-does-not-exist-${UUID.randomUUID()}")
            )
            assertFalse(sh.exists()) // dangling, exactly the production symptom

            val healed = LinuxSandboxManager.materializeSh(rootfs)

            assertTrue("materializeSh should heal a dangling /bin/sh", healed)
            assertFalse("bin/sh must become a regular file, not a symlink", java.nio.file.Files.isSymbolicLink(sh.toPath()))
            assertTrue(sh.exists())
            assertTrue(sh.canExecute())
            assertEquals(busybox.readText(), sh.readText())
        } finally {
            rootfs.deleteRecursively()
        }
    }

    @Test
    fun `materializeSh fails cleanly without busybox`() {
        val rootfs = createTempDirectory("alpine-rootfs-empty-").toFile()
        try {
            assertFalse(LinuxSandboxManager.materializeSh(rootfs))
            assertFalse(File(rootfs, "bin/sh").exists())
        } finally {
            rootfs.deleteRecursively()
        }
    }

    @Test
    fun `provisionGuestDns installs resolver only when missing or empty`() {
        val rootfs = createTempDirectory("alpine-dns-").toFile()
        try {
            assertTrue(LinuxSandboxManager.provisionGuestDns(rootfs))
            val conf = File(rootfs, "etc/resolv.conf")
            assertTrue(conf.exists())
            assertTrue(conf.readText().contains("nameserver 8.8.8.8"))

            // Empty file (what the minirootfs ships) must be provisioned too.
            conf.writeText("")
            assertTrue(LinuxSandboxManager.provisionGuestDns(rootfs))
            assertTrue(conf.readText().contains("nameserver 1.1.1.1"))

            // A user-provided non-empty resolver config is never overwritten.
            conf.writeText("nameserver 10.0.0.1\n")
            assertTrue(LinuxSandboxManager.provisionGuestDns(rootfs))
            assertEquals("nameserver 10.0.0.1\n", conf.readText())
        } finally {
            rootfs.deleteRecursively()
        }
    }

    @Test
    fun `populateGuestHostsFile creates hosts file with localhost and alpine mirror mappings`() {
        val rootfs = createTempDirectory("alpine-hosts-").toFile()
        try {
            assertTrue(LinuxSandboxManager.populateGuestHostsFile(rootfs, resolveRemote = false))
            val hosts = File(rootfs, "etc/hosts")
            assertTrue(hosts.exists())
            val text = hosts.readText()
            assertTrue(text.contains("127.0.0.1\tlocalhost"))
            assertTrue(text.contains("dl-cdn.alpinelinux.org"))
            assertTrue(text.contains("151.101.2.132"))
        } finally {
            rootfs.deleteRecursively()
        }
    }

    @Test
    fun `HOST_LEAK_ENV_VARS contains critical Android runtime variables`() {
        val leakVars = LinuxSandboxManager.HOST_LEAK_ENV_VARS
        assertTrue(leakVars.contains("ANDROID_ROOT"))
        assertTrue(leakVars.contains("ANDROID_DATA"))
        assertTrue(leakVars.contains("BOOTCLASSPATH"))
        assertTrue(leakVars.contains("LD_PRELOAD"))
    }

    @Test
    fun `architecture detect returns a valid supported architecture`() {
        val detected = LinuxArchitecture.detect()
        assertNotNull(detected)
        assertTrue(detected in LinuxArchitecture.entries)
    }

    @Test
    fun `buildResolvConf includes public fallbacks and options`() {
        val resolv = LinuxSandboxManager.buildResolvConf(null)
        assertTrue(resolv.contains("nameserver 8.8.8.8"))
        assertTrue(resolv.contains("nameserver 1.1.1.1"))
        assertTrue(resolv.contains("options timeout:2 attempts:2"))
    }

    @Test
    fun `buildExecutionArgs includes link2symlink and defaults to root working directory`() {
        val rootfs = File("/tmp/fake_rootfs")
        val args = LinuxSandboxManager.buildExecutionArgs(
            prootPath = "/data/data/com.amaya.intelligence/lib/libproot.so",
            rootfsDir = rootfs,
            command = "git status",
            workspaceDir = null
        )

        assertTrue(args.contains("--kill-on-exit"))
        assertTrue(args.contains("--link2symlink"))
        assertTrue(args.contains("-0"))
        assertTrue(args.contains("-r"))
        assertTrue(args.contains(rootfs.absolutePath))
        val wIndex = args.indexOf("-w")
        assertTrue(wIndex != -1)
        assertEquals("/root", args[wIndex + 1])
        assertTrue(args.contains("git status"))
    }

    @Test
    fun `buildExecutionArgs binds and switches to workspace when directory exists`() {
        val rootfs = File("/tmp/fake_rootfs")
        val tempWorkspace = createTempDirectory("workspace-").toFile()
        try {
            val args = LinuxSandboxManager.buildExecutionArgs(
                prootPath = "/data/data/com.amaya.intelligence/lib/libproot.so",
                rootfsDir = rootfs,
                command = "ls -la",
                workspaceDir = tempWorkspace.absolutePath
            )

            assertTrue(args.contains("${tempWorkspace.absolutePath}:/workspace"))
            val wIndex = args.indexOf("-w")
            assertTrue(wIndex != -1)
            assertEquals("/workspace", args[wIndex + 1])
        } finally {
            tempWorkspace.deleteRecursively()
        }
    }

    @Test
    fun `buildExecutionArgs falls back to system sh when proot is missing`() {
        val rootfs = File("/tmp/fake_rootfs")
        val args = LinuxSandboxManager.buildExecutionArgs(
            prootPath = null,
            rootfsDir = rootfs,
            command = "echo hello",
            workspaceDir = null
        )

        assertEquals(listOf("/system/bin/sh", "-c", "echo hello"), args)
    }

    @Test
    fun `buildExecutionEnv sets PROOT_NO_SECCOMP, PROOT_LOADER, and SSL certificates`() {
        val tmpDir = File("/tmp/proot_tmp")
        val env = LinuxSandboxManager.buildExecutionEnv(
            hasProot = true,
            prootTmpDir = tmpDir,
            prootLoaderPath = "/data/app/lib/arm64/libproot_loader.so"
        )

        assertEquals("/root", env["HOME"])
        assertEquals("1", env["PROOT_NO_SECCOMP"])
        assertEquals("1", env["PROOT_IGNORE_MISSING_BINDINGS"])
        assertEquals("/tmp", env["TMPDIR"])
        assertEquals(tmpDir.absolutePath, env["PROOT_TMP_DIR"])
        assertEquals("/data/app/lib/arm64/libproot_loader.so", env["PROOT_LOADER"])
        assertEquals("/data/app/lib/arm64/libproot_loader.so", env["PROOT_LOADER_32"])
        assertEquals("/data/app/lib/arm64/libproot_loader.so", env["PROOT_LOADER_64"])
        assertEquals("/etc/ssl/certs/ca-certificates.crt", env["SSL_CERT_FILE"])
        assertEquals("/etc/ssl/certs/ca-certificates.crt", env["GIT_SSL_CAINFO"])
        assertEquals("/etc/ssl/certs/ca-certificates.crt", env["CURL_CA_BUNDLE"])
    }

    @Test
    fun `isElfFile recognizes ELF magic and rejects plain files`() {
        val dir = createTempDirectory("elf-check-").toFile()
        try {
            val elf = File(dir, "elf")
            elf.writeBytes(byteArrayOf(0x7f, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte(), 2, 1, 1) + ByteArray(20))
            assertTrue(LinuxSandboxManager.isElfFile(elf))

            val plain = File(dir, "plain")
            plain.writeText("# not an elf")
            assertFalse(LinuxSandboxManager.isElfFile(plain))
            assertFalse(LinuxSandboxManager.isElfFile(File(dir, "missing")))
        } finally {
            dir.deleteRecursively()
        }
    }

    /**
     * Builds a minimal valid ELF64 header whose section-header math yields a
     * 128-byte "file": e_type=ET_EXEC(2), e_shoff=64, e_shentsize=64, e_shnum=1.
     */
    private fun fakeElfExecutable(machine: Int): ByteArray {
        val b = ByteArray(128)
        b[0] = 0x7f; b[1] = 'E'.code.toByte(); b[2] = 'L'.code.toByte(); b[3] = 'F'.code.toByte()
        b[4] = 2 // ELFCLASS64
        b[5] = 1 // little endian
        b[6] = 1 // EI_VERSION
        b[16] = 2; b[17] = 0 // e_type = ET_EXEC
        b[18] = (machine and 0xFF).toByte(); b[19] = ((machine shr 8) and 0xFF).toByte() // e_machine
        b[40] = 64 // e_shoff (u64)
        b[58] = 64; b[59] = 0 // e_shentsize (u16)
        b[60] = 1; b[61] = 0 // e_shnum (u16)
        return b
    }

    @Test
    fun `extractEmbeddedLoader skips false-positive ELF magic and extracts the matching machine`() {
        val dir = createTempDirectory("loader-false-positive-").toFile()
        try {
            val targetMachine = LinuxSandboxManager.elfMachine(LinuxArchitecture.detect())
            val wrongMachine = (targetMachine + 1) and 0xFFFF
            val wrongElf = fakeElfExecutable(wrongMachine)
            val rightElf = fakeElfExecutable(targetMachine)

            // A static binary may contain incidental `ELF` sequences (false
            // positives); the scanner must skip them and find the real loader.
            val source = File(dir, "libproot_fake.so")
            source.writeBytes(
                ByteArray(50) { 0x41 } + wrongElf + ByteArray(20) { 0x42 } + rightElf
            )

            val dest = File(dir, "loader")
            val result = LinuxSandboxManager.extractEmbeddedLoader(source, dest)

            assertTrue("expected the matching-machine loader to be extracted", result)
            assertTrue(dest.exists())
            assertTrue(dest.canExecute())
            assertTrue(dest.length() == rightElf.size.toLong())
            assertTrue(dest.readBytes().contentEquals(rightElf))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `extractEmbeddedLoader rejects candidates with unknown machine values`() {
        val dir = createTempDirectory("loader-wrong-machine-").toFile()
        try {
            val wrongMachine = (LinuxSandboxManager.elfMachine(LinuxArchitecture.detect()) + 1) and 0xFFFF
            val source = File(dir, "libproot_fake.so")
            source.writeBytes(ByteArray(40) { 0x41 } + fakeElfExecutable(wrongMachine))

            val dest = File(dir, "loader")
            assertFalse(LinuxSandboxManager.extractEmbeddedLoader(source, dest))
            assertFalse(dest.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `extractEmbeddedLoader extracts ELF loader from arm64 and armv7 libproot`() {
        val arm64Proot = File("src/main/jniLibs/arm64-v8a/libproot.so")
        val arm7Proot = File("src/main/jniLibs/armeabi-v7a/libproot.so")
        val destDir = createTempDirectory("loader-test-").toFile()
        try {
            if (arm64Proot.exists()) {
                val dest64 = File(destDir, "arm64_loader")
                val res = LinuxSandboxManager.extractEmbeddedLoader(arm64Proot, dest64)
                assertTrue(res)
                assertTrue(dest64.exists())
                assertTrue(dest64.length() > 50000L)
            }
            if (arm7Proot.exists()) {
                val dest7 = File(destDir, "arm7_loader")
                val res = LinuxSandboxManager.extractEmbeddedLoader(arm7Proot, dest7)
                assertTrue(res)
                assertTrue(dest7.exists())
                assertTrue(dest7.length() > 50000L)
            }
        } finally {
            destDir.deleteRecursively()
        }
    }

    @Test
    fun `configureApkRepositories provisions the primary mirror`() {
        val rootfs = createTempDirectory("alpine-repo-").toFile()
        try {
            LinuxSandboxManager.configureApkRepositories(rootfs)
            val repoFile = File(rootfs, "etc/apk/repositories")
            assertTrue(repoFile.exists())
            val content = repoFile.readText()
            val primary = ALPINE_MIRRORS.first()
            assertEquals("$primary/$ALPINE_BRANCH/main\n$primary/$ALPINE_BRANCH/community\n", content)
            // The pinned minirootfs ships ca-certificates-bundle, so HTTPS is safe
            // from first install; regression to the old HTTP rewrite is a downgrade.
            assertTrue("repositories must use HTTPS mirrors", content.startsWith("https://"))
        } finally {
            rootfs.deleteRecursively()
        }
    }

    @Test
    fun `writeApkRepositories rewrites repositories per mirror base`() {
        val rootfs = createTempDirectory("alpine-repo-rewrite-").toFile()
        try {
            LinuxSandboxManager.writeApkRepositories(rootfs, ALPINE_MIRRORS.first())
            val repoFile = File(rootfs, "etc/apk/repositories")
            assertTrue(repoFile.readText().contains("${ALPINE_MIRRORS.first()}/$ALPINE_BRANCH/main"))

            // Walking the mirror list must fully replace the previous attempt's URL.
            LinuxSandboxManager.writeApkRepositories(rootfs, ALPINE_MIRRORS.last())
            val updated = repoFile.readText()
            assertTrue(updated.contains("${ALPINE_MIRRORS.last()}/$ALPINE_BRANCH/main"))
            assertFalse(updated.contains(ALPINE_MIRRORS.first()))
        } finally {
            rootfs.deleteRecursively()
        }
    }

    @Test
    fun `apkFailureLines extracts apk error diagnostics`() {
        val output = """
            fetch https://dl-cdn.alpinelinux.org/alpine/v3.22/main/x86_64/APKINDEX.tar.gz
            ERROR: unable to select packages:
            ERROR: unsatisfiable constraints:
        """.trimIndent()
        val lines = LinuxSandboxManager.apkFailureLines(output)
        assertEquals(2, lines.size)
        assertTrue(lines.all { it.startsWith("ERROR:") })
        assertTrue(LinuxSandboxManager.apkFailureLines("").isEmpty())
    }
}
