package com.onelineaday.dailydiary.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class BackupArchiveManagerTest {

    @Test
    fun `valid relative archive paths succeed`() {
        val validPaths = listOf(
            "manifest.json",
            "files/550e8400-e29b-41d4-a716-446655440000.jpg",
            "files/550e8400-e29b-41d4-a716-446655440001.m4a",
            "files/550e8400-e29b-41d4-a716-446655440002.mp4"
        )

        for (path in validPaths) {
            val result = BackupArchiveManager.validateArchivePath(path)
            assertEquals(path, result)
        }
    }

    @Test
    fun `parent traversal with double dots is rejected`() {
        val maliciousPaths = listOf(
            "../etc/passwd",
            "files/../../secret.txt",
            "files/../files/something.jpg",
            "..",
            "files/.."
        )

        for (path in maliciousPaths) {
            try {
                BackupArchiveManager.validateArchivePath(path)
                fail("Expected IllegalArgumentException for path traversal: $path")
            } catch (e: IllegalArgumentException) {
                // Expected
            }
        }
    }

    @Test
    fun `backslash path separators are rejected`() {
        val windowsStylePaths = listOf(
            "..\\..\\Windows\\System32",
            "files\\malicious.jpg",
            "files\\..\\outside.txt"
        )

        for (path in windowsStylePaths) {
            try {
                BackupArchiveManager.validateArchivePath(path)
                fail("Expected IllegalArgumentException for backslash path: $path")
            } catch (e: IllegalArgumentException) {
                // Expected
            }
        }
    }

    @Test
    fun `absolute paths are rejected`() {
        val absolutePaths = listOf(
            "/data/data/com.onelineaday.dailydiary/databases/journal.db",
            "/etc/hosts",
            "/files/image.jpg"
        )

        for (path in absolutePaths) {
            try {
                BackupArchiveManager.validateArchivePath(path)
                fail("Expected IllegalArgumentException for absolute path: $path")
            } catch (e: IllegalArgumentException) {
                // Expected
            }
        }
    }

    @Test
    fun `drive-qualified paths are rejected`() {
        val drivePaths = listOf(
            "C:/Windows/System32/cmd.exe",
            "D:\\data\\file.txt"
        )

        for (path in drivePaths) {
            try {
                BackupArchiveManager.validateArchivePath(path)
                fail("Expected IllegalArgumentException for drive path: $path")
            } catch (e: IllegalArgumentException) {
                // Expected
            }
        }
    }

    @Test
    fun `blank paths are rejected`() {
        val blankPaths = listOf("", "   ", "\t")

        for (path in blankPaths) {
            try {
                BackupArchiveManager.validateArchivePath(path)
                fail("Expected IllegalArgumentException for blank path: $path")
            } catch (e: IllegalArgumentException) {
                // Expected
            }
        }
    }

    @Test
    fun `valid UUID backup IDs succeed`() {
        val id = "550e8400-e29b-41d4-a716-446655440000"
        assertEquals(
            id,
            BackupArchiveManager.validateFileId(id)
        )
    }

    @Test
    fun `manifest file IDs cannot traverse`() {
        val invalid = listOf(
            "../outside",
            "..",
            "files/../x",
            "/absolute",
            "C:/Windows",
            "550e8400-e29b-41d4-a716-446655440000/../../x",
            ""
        )

        invalid.forEach { id ->
            try {
                BackupArchiveManager.validateFileId(id)
                fail("Expected invalid ID: $id")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test
    fun `non UUID backup IDs are rejected`() {
        val invalid = listOf(
            "abc",
            "123",
            "not-a-uuid",
            "550e8400e29b41d4a716446655440000"
        )

        invalid.forEach { id ->
            try {
                BackupArchiveManager.validateFileId(id)
                fail("Expected invalid ID: $id")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
    }
}
