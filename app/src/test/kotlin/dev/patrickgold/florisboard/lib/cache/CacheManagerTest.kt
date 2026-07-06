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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class CacheManagerTest : FunSpec({
    test("import cache has a hard per-file byte budget") {
        CacheManager.MaxImportFileBytes shouldBe 256L * 1024L * 1024L
    }

    test("import cache has hard batch count and byte budgets") {
        CacheManager.MaxImportUriCount shouldBe 32
        CacheManager.MaxImportBatchBytes shouldBe 512L * 1024L * 1024L

        CacheManager.requireImportUriCount(CacheManager.MaxImportUriCount)
        shouldThrow<IllegalStateException> {
            CacheManager.requireImportUriCount(CacheManager.MaxImportUriCount + 1)
        }

        CacheManager.addImportBatchBytes(CacheManager.MaxImportBatchBytes - 1L, 1L) shouldBe
            CacheManager.MaxImportBatchBytes
        shouldThrow<IllegalStateException> {
            CacheManager.requireImportBatchCapacity(CacheManager.MaxImportBatchBytes, 1L)
        }
        shouldThrow<IllegalStateException> {
            CacheManager.requireImportBatchCapacity(0L, CacheManager.MaxImportBatchBytes + 1L)
        }
    }

    test("sanitizeImportFileName strips path traversal and unsafe characters") {
        CacheManager.sanitizeImportFileName("../evil:name?.flex", "fallback.flex") shouldBe "evil_name_.flex"
        CacheManager.sanitizeImportFileName("..\\nested\\theme.flex", "fallback.flex") shouldBe "theme.flex"
    }

    test("sanitizeImportFileName falls back for blank or unusable names") {
        CacheManager.sanitizeImportFileName("...", "fallback.flex") shouldBe "fallback.flex"
        CacheManager.sanitizeImportFileName(null, "fallback.flex") shouldBe "fallback.flex"
    }

    test("uniqueImportFileName preserves multiple same-named imports instead of overwriting") {
        val dir = Files.createTempDirectory("cache-imports").toFile()
        try {
            dir.resolve("theme-2.flex").writeText("existing")

            CacheManager.uniqueImportFileName(
                fileName = "theme.flex",
                usedNames = setOf("theme.flex"),
                dir = dir,
            ) shouldBe "theme-3.flex"
        } finally {
            dir.deleteRecursively()
        }
    }
})
