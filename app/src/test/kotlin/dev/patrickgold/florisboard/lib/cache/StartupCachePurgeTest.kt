/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.lib.cache

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.file.Files

class StartupCachePurgeTest : FunSpec({

    fun tempCacheDir(): File = Files.createTempDirectory("startup-cache-purge").toFile()

    test("purges the stale entries that existed when the snapshot was taken") {
        val cacheDir = tempCacheDir()
        val staleFile = File(cacheDir, "stale.txt").apply { writeText("old") }
        val staleTree = File(cacheDir, "stale-dir/nested").apply { mkdirs() }
        File(staleTree, "deep.txt").writeText("old")

        val snapshot = StartupCachePurge.snapshotStaleEntries(cacheDir)
        snapshot.map { it.name } shouldContainExactlyInAnyOrder listOf("stale.txt", "stale-dir")

        StartupCachePurge.purge(snapshot) shouldBe emptyList()

        staleFile.exists() shouldBe false
        File(cacheDir, "stale-dir").exists() shouldBe false
        cacheDir.exists() shouldBe true
    }

    test("never deletes an entry created after the snapshot") {
        // This is the whole reason the listing stays on the calling thread:
        // ExtensionManager extracts into the cache moments after startup, and
        // a purge still in flight must not eat what it just wrote.
        val cacheDir = tempCacheDir()
        File(cacheDir, "stale.txt").writeText("old")

        val snapshot = StartupCachePurge.snapshotStaleEntries(cacheDir)

        val extractedAfterSnapshot = File(cacheDir, "extension-workdir").apply { mkdirs() }
        val extractedFile = File(extractedAfterSnapshot, "manifest.json").apply { writeText("{}") }

        StartupCachePurge.purge(snapshot)

        File(cacheDir, "stale.txt").exists() shouldBe false
        extractedAfterSnapshot.exists() shouldBe true
        extractedFile.readText() shouldBe "{}"
    }

    test("treats a missing cache directory as nothing to do") {
        StartupCachePurge.snapshotStaleEntries(null) shouldBe emptyList()

        val absent = File(tempCacheDir(), "does-not-exist")
        StartupCachePurge.snapshotStaleEntries(absent) shouldBe emptyList()
        StartupCachePurge.purge(emptyList()) shouldBe emptyList()
    }

    test("reports entries it could not remove instead of failing silently") {
        val cacheDir = tempCacheDir()
        val vanished = File(cacheDir, "vanished.txt").apply { writeText("x") }

        val snapshot = StartupCachePurge.snapshotStaleEntries(cacheDir)
        // Someone else removed it between the snapshot and the purge. That is
        // not a failure — the entry is gone, which is the desired end state.
        vanished.delete() shouldBe true

        StartupCachePurge.purge(snapshot) shouldBe emptyList()
    }
})
