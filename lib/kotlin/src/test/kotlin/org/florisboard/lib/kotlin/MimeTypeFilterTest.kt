/*
 * Copyright (C) 2025 The FlorisBoard Contributors
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

package org.florisboard.lib.kotlin

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class MimeTypeFilterTest : FunSpec({
    context("basic matching") {
        context("valid matches") {
            withData(
                "font/woff2",
                "image/png",
                "application/x-font-otf",
            ) { mimeType ->
                val filter = mimeTypeFilterOf(mimeType)
                filter.matches(mimeType) shouldBe true
            }
        }

        test("null does not match") {
            val filter = mimeTypeFilterOf("image/png")
            filter.matches(null) shouldBe false
        }

        test("empty string does not match") {
            val filter = mimeTypeFilterOf("image/png")
            filter.matches("") shouldBe false
        }

        test("blank string does not match") {
            val filter = mimeTypeFilterOf("image/png")
            filter.matches("   ") shouldBe false
        }
    }

    context("wildcard matching") {
        context("should match type=any subtype=any") {
            val filter = mimeTypeFilterOf("*/*")
            withData(
                "image/png",
                "image/jpeg",
                "font/woff2",
                "application/x-font-otf",
            ) { mimeType ->
                filter.matches(mimeType) shouldBe true
            }
        }

        context("should not match type=any subtype=any") {
            val filter = mimeTypeFilterOf("*/*")
            withData(
                nameFn = { "`$it`" },
                "",
                "   ",
                "/",
                "   /",
                "/    ",
                "image/",
                "/jpeg",
                "image/   ",
                "   /jpeg",
                "image/png/jpeg",
                "image-jpeg",
            ) { mimeType ->
                filter.matches(mimeType) shouldBe false
            }
        }

        context("should match type=image subtype=any") {
            val filter = mimeTypeFilterOf("image/*")
            withData(
                "image/png",
                "image/jpeg",
            ) { mimeType ->
                filter.matches(mimeType) shouldBe true
            }
        }

        test("legacy otf file should work with wildcard filters") {
            // https://github.com/florisboard/florisboard/issues/2957
            val filter = mimeTypeFilterOf(
                "font/*",
                "application/font-*",
                "application/x-font-*",
                "application/vnd.ms-fontobject",
            )
            filter.matches("application/x-font-otf") shouldBe true
        }

        test("should match type=any subtype=font-any") {
            val filter = mimeTypeFilterOf(
                "*/x-font-*",
            )
            filter.matches("application/x-font-otf") shouldBe true
        }

        test("should match type=application subtype=any-font-any") {
            val filter = mimeTypeFilterOf(
                "application/*-font-*",
            )
            filter.matches("application/x-font-otf") shouldBe true
        }

        test("should match type=any-application-any subtype=any-font-any") {
            val filter = mimeTypeFilterOf(
                "*-application-*/*-font-*",
            )
            filter.matches("x-application-custom/x-font-otf") shouldBe true
        }
    }

    context("aggregate matching") {
        val imageFilter = mimeTypeFilterOf("image/*")

        test("matchesAll requires a non-empty list where every entry matches") {
            imageFilter.matchesAll(null) shouldBe false
            imageFilter.matchesAll(emptyList()) shouldBe false
            imageFilter.matchesAll(listOf("image/png", "image/webp")) shouldBe true
            imageFilter.matchesAll(listOf("image/png", "text/plain")) shouldBe false
            imageFilter.matchesAll(listOf("image/png", null)) shouldBe false
            imageFilter.matchesAll(listOf("image/png", "image/")) shouldBe false
        }

        test("matchesAny accepts a single matching entry and ignores non-matching entries") {
            imageFilter.matchesAny(null) shouldBe false
            imageFilter.matchesAny(emptyList()) shouldBe false
            imageFilter.matchesAny(listOf("text/plain", "image/png", null)) shouldBe true
            imageFilter.matchesAny(listOf("text/plain", null, "image/")) shouldBe false
        }

        test("matchesOne requires exactly one matching entry") {
            imageFilter.matchesOne(null) shouldBe false
            imageFilter.matchesOne(emptyList()) shouldBe false
            imageFilter.matchesOne(listOf("image/png")) shouldBe true
            imageFilter.matchesOne(listOf("text/plain", "image/png", null)) shouldBe true
            imageFilter.matchesOne(listOf("image/png", "image/webp")) shouldBe false
            imageFilter.matchesOne(listOf("text/plain", null)) shouldBe false
        }
    }

    context("contract details") {
        test("constructor does not print compiled filters to stdout") {
            captureStdout {
                mimeTypeFilterOf("image/*", "application/font-*")
            } shouldBe ""
        }

        test("matching remains case-sensitive") {
            val filter = mimeTypeFilterOf("image/*", "application/font-*")

            filter.matches("image/png") shouldBe true
            filter.matches("IMAGE/PNG") shouldBe false
            filter.matches("application/font-woff") shouldBe true
            filter.matches("application/FONT-woff") shouldBe false
        }

        test("fragment wildcards are intentionally broader than AndroidX whole-fragment wildcards") {
            val filter = mimeTypeFilterOf(
                "application/font-*",
                "application/x-font-*",
                "*/x-font-*",
            )

            filter.matchesAll(listOf("application/font-woff", "application/x-font-otf")) shouldBe true
            filter.matchesAny(listOf("text/plain", "application/x-font-ttf")) shouldBe true
            filter.matchesOne(listOf("application/font-woff", "text/plain")) shouldBe true
            filter.matchesOne(listOf("application/font-woff", "application/x-font-otf")) shouldBe false
        }
    }
})

private fun captureStdout(block: () -> Unit): String {
    val originalOut = System.out
    val buffer = ByteArrayOutputStream()
    return try {
        PrintStream(buffer, true, Charsets.UTF_8.name()).use { capture ->
            System.setOut(capture)
            block()
            capture.flush()
        }
        buffer.toString(Charsets.UTF_8.name())
    } finally {
        System.setOut(originalOut)
    }
}
