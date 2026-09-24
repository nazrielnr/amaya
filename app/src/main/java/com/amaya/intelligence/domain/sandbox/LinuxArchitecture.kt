package com.amaya.intelligence.domain.sandbox

import android.os.Build

// Pin the Alpine release the sandbox was validated against. Kai (the reference
// implementation) caps its Alpine version at 3.22 because 3.23+ ships apk-tools 3,
// which uses execveat() in a way proot does not support — `apk update` fails under
// the sandbox runtime. See termux/proot-distro#532 / #595.
const val ALPINE_VERSION = "3.22.5"
const val ALPINE_BRANCH = "v3.22"

/**
 * Official Alpine mirrors, best first. Mirrors go down independently of the one
 * that served the rootfs, so package installation walks this list, rewriting
 * `etc/apk/repositories` until one answers.
 */
val ALPINE_MIRRORS = listOf(
    "https://dl-cdn.alpinelinux.org/alpine",
    "http://dl-cdn.alpinelinux.org/alpine",
    "https://mirrors.edge.kernel.org/alpine",
    "http://mirrors.edge.kernel.org/alpine",
    "https://ftp.halifax.rwth-aachen.de/alpine",
    "https://alpine.ethz.ch/alpine",
    "https://mirror.csclub.uwaterloo.ca/alpine",
    "https://mirrors.tuna.tsinghua.edu.cn/alpine",
)

/**
 * Supported Linux architectures for Alpine Linux rootfs and PRoot binary.
 * Supports both 64-bit (aarch64, x86_64) and 32-bit (armv7, x86) Android devices.
 */
enum class LinuxArchitecture(
    val androidAbi: String,
    val alpineArch: String,
    val prootArch: String,
    val displayName: String,
    val is64Bit: Boolean
) {
    AARCH64(
        androidAbi = "arm64-v8a",
        alpineArch = "aarch64",
        prootArch = "aarch64",
        displayName = "ARM64 (64-bit)",
        is64Bit = true
    ),
    ARMV7(
        androidAbi = "armeabi-v7a",
        alpineArch = "armv7",
        prootArch = "arm",
        displayName = "ARMv7 (32-bit)",
        is64Bit = false
    ),
    X86_64(
        androidAbi = "x86_64",
        alpineArch = "x86_64",
        prootArch = "x86_64",
        displayName = "x86_64 (64-bit Intel/AMD)",
        is64Bit = true
    ),
    X86(
        androidAbi = "x86",
        alpineArch = "x86",
        prootArch = "i686",
        displayName = "x86 (32-bit Intel)",
        is64Bit = false
    );

    /**
     * All Alpine minirootfs download URLs, best mirror first.
     * Ultra-lightweight (~3-5MB compressed, ~15MB uncompressed).
     */
    val minirootfsUrls: List<String>
        get() = ALPINE_MIRRORS.map { base ->
            "$base/$ALPINE_BRANCH/releases/$alpineArch/alpine-minirootfs-$ALPINE_VERSION-$alpineArch.tar.gz"
        }

    /**
     * Static PRoot binary URL for non-root execution inside Android sandbox.
     *
     * Upstream publishes `-static` builds only for v5.3.0 in aarch64/arm/x86_64
     * flavors; the i686 variant does not exist and 32-bit x86 devices gracefully
     * fall back to the host shell (see LinuxSandboxManager.buildExecution).
     */
    val prootBinaryUrl: String
        get() = "https://github.com/proot-me/proot/releases/download/v5.3.0/proot-v5.3.0-$prootArch-static"

    companion object {
        /**
         * Detect the device architecture based on Android [Build.SUPPORTED_ABIS].
         */
        fun detect(): LinuxArchitecture {
            val supported = Build.SUPPORTED_ABIS ?: emptyArray()
            // 1. First prioritize 64-bit ABIs so 64-bit devices use native 64-bit rootfs (preventing 32-bit time64 seccomp traps)
            for (abi in supported) {
                when {
                    abi.equals("arm64-v8a", ignoreCase = true) -> return AARCH64
                    abi.startsWith("arm64", ignoreCase = true) -> return AARCH64
                    abi.equals("x86_64", ignoreCase = true) -> return X86_64
                }
            }
            // 2. Fall back to 32-bit ABIs on 32-bit hardware
            for (abi in supported) {
                when {
                    abi.equals("armeabi-v7a", ignoreCase = true) -> return ARMV7
                    abi.startsWith("armeabi", ignoreCase = true) -> return ARMV7
                    abi.equals("x86", ignoreCase = true) -> return X86
                }
            }
            return AARCH64
        }
    }
}
