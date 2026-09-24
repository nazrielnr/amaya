package com.amaya.intelligence.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class WorkspacePathResolverTest {
    private fun workspace() = Files.createTempDirectory("workspace").toFile()

    @Test
    fun `relative path resolves inside workspace`() {
        val root = workspace()
        try {
            val result = WorkspacePathResolver.resolve("read_file", mapOf("path" to "src/Main.kt"), root.path).getOrThrow()
            assertEquals(File(root, "src/Main.kt").canonicalPath, result["path"])
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `traversal outside workspace is rejected`() {
        val root = workspace()
        try {
            assertTrue(WorkspacePathResolver.resolve("read_file", mapOf("path" to "../secret"), root.path).isFailure)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `absolute path without workspace is rejected`() {
        val target = Files.createTempFile("outside", ".txt").toFile()
        try {
            assertTrue(WorkspacePathResolver.resolve("read_file", mapOf("path" to target.absolutePath), null).isFailure)
        } finally {
            target.delete()
        }
    }

    @Test
    fun `symlink escape is rejected`() {
        val root = workspace()
        val outside = Files.createTempDirectory("outside").toFile()
        try {
            File(outside, "secret").writeText("secret")
            val link = File(root, "escape").toPath()
            val created = runCatching { Files.createSymbolicLink(link, outside.toPath()) }.isSuccess
            if (created) assertTrue(WorkspacePathResolver.resolve("read_file", mapOf("path" to "escape/secret"), root.path).isFailure)
        } finally {
            root.deleteRecursively()
            outside.deleteRecursively()
        }
    }

    @Test
    fun `list defaults to workspace root`() {
        val root = workspace()
        try {
            val result = WorkspacePathResolver.resolve("list_files", emptyMap(), root.path).getOrThrow()
            assertEquals(root.canonicalPath, result["path"])
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `path aliases resolve correctly to canonical path`() {
        val root = workspace()
        try {
            val result = WorkspacePathResolver.resolve("write_file", mapOf("file_path" to "hello.txt"), root.path).getOrThrow()
            assertEquals(File(root, "hello.txt").canonicalPath, result["path"])
            assertEquals(File(root, "hello.txt").canonicalPath, result["file_path"])
        } finally {
            root.deleteRecursively()
        }
    }
}
