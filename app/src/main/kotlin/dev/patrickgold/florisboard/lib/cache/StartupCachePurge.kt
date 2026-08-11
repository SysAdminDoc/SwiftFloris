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

import java.io.File

/**
 * Clears leftover cache from a previous process without blocking IME startup.
 *
 * `Application.onCreate` used to call `cacheDir.deleteContentsRecursively()`
 * directly, so every cold start paid a full recursive delete on the main
 * thread whose cost scales with whatever the last run left behind. Upstream
 * FlorisBoard reports the same defect class in issue #3300.
 *
 * ## Why the listing stays synchronous
 *
 * Deleting the whole directory from a background coroutine would race the rest
 * of startup: `ExtensionManager.init()` extracts into the cache from its own
 * IO scope moments later, and a purge still in flight would delete files it
 * had just written.
 *
 * So the two halves are split. [snapshotStaleEntries] lists the directory's
 * immediate children — one directory read, no traversal — on the calling
 * thread, which fixes the set of entries that predate this process. Only that
 * set is handed to [purge], which does the expensive recursive work off-thread.
 * Anything created after the snapshot is not in it and cannot be deleted, so
 * this is both faster than the old behaviour and strictly safer than a naive
 * async delete.
 */
object StartupCachePurge {
    /**
     * The immediate children of [cacheDir] as of now. Cheap enough for the
     * main thread; see the class doc for why it must run there.
     */
    fun snapshotStaleEntries(cacheDir: File?): List<File> {
        if (cacheDir == null) return emptyList()
        return cacheDir.listFiles()?.toList().orEmpty()
    }

    /**
     * Recursively delete every entry in [staleEntries]. Intended to run on an
     * IO dispatcher. Returns the entries that could not be fully removed, so
     * the caller can log them rather than fail silently — a cache entry that
     * survives is a disk-usage leak, not a crash.
     */
    fun purge(staleEntries: List<File>): List<File> {
        val failures = mutableListOf<File>()
        for (entry in staleEntries) {
            val deleted = runCatching { entry.deleteRecursively() }.getOrDefault(false)
            if (!deleted && entry.exists()) {
                failures += entry
            }
        }
        return failures
    }
}
