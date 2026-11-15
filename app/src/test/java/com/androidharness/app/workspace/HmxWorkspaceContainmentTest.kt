package com.androidharness.app.workspace

import com.androidharness.app.tools.ToolFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.nio.file.Files

class HmxWorkspaceContainmentTest {
    @Test fun externalSymlinksCannotResolveThroughWorkspaceTools() {
        val directory = Files.createTempDirectory("hmx-containment")
        try {
            val root = Files.createDirectory(directory.resolve("workspace"))
            val outside = Files.createDirectory(directory.resolve("outside"))
            Files.writeString(outside.resolve("secret.txt"), "unchanged")
            Files.createSymbolicLink(root.resolve("escape"), outside)
            try {
                FileFs(root.toFile()).resolve("escape/secret.txt").writeText("changed")
                fail("Expected external symlink to be rejected")
            } catch (_: ToolFailure) {
                assertEquals("unchanged", Files.readString(outside.resolve("secret.txt")))
            }
            Files.delete(root.resolve("escape"))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test fun symlinksInsideWorkspaceRemainUsable() {
        val directory = Files.createTempDirectory("hmx-internal-link")
        try {
            Files.writeString(directory.resolve("target.txt"), "hello")
            Files.createSymbolicLink(directory.resolve("link.txt"), directory.resolve("target.txt"))
            assertEquals("hello", FileFs(directory.toFile()).resolve("link.txt").readText())
            Files.delete(directory.resolve("link.txt"))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
