// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: GPL-3.0-or-later

package kinetickk.gradle.pokeball

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PokeballSnapshotCheckoutTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun ordinaryAndDetachedWorktreeRootsAreBothValidCheckouts() {
        val repository = Files.createDirectory(temporaryDirectory.resolve("repository"))
        runGit(repository, "init")
        runGit(
            repository,
            "-c", "user.name=Snapshot fixture", "-c", "user.email=snapshot@example.test",
            "-c", "commit.gpgSign=false", "commit", "--allow-empty", "-m", "Snapshot fixture",
        )
        val worktree = temporaryDirectory.resolve("detached")
        runGit(repository, "worktree", "add", "--detach", worktree.toString(), "HEAD")

        requireSnapshotCheckout(repository)
        requireSnapshotCheckout(worktree)
        assertTrue(Files.isRegularFile(worktree.resolve(".git")))

        val failure = assertFailsWith<IllegalArgumentException> { verifySnapshot(worktree) }
        assertTrue("HEAD mismatch" in failure.message.orEmpty())
    }

    @Test
    fun plainDirectoriesAndForgedGitFilesDoNotBecomeSnapshots() {
        assertFailsWith<IllegalArgumentException> { requireSnapshotCheckout(temporaryDirectory) }
        temporaryDirectory.resolve(".git").writeText("gitdir: missing-metadata\n")
        assertFailsWith<IllegalStateException> { requireSnapshotCheckout(temporaryDirectory) }
    }
}
