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

package dev.patrickgold.florisboard.ime.clipboard

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.florisboard.lib.kotlin.mimeTypeFilterOf

class CopyToClipboardUriPolicyTest : FunSpec({
    val imageFilter = mimeTypeFilterOf("image/*")

    test("allows content scheme") {
        CopyToClipboardUriPolicy.isAllowedScheme("content") shouldBe true
    }

    test("rejects file scheme (confused-deputy guard for the exported activity)") {
        CopyToClipboardUriPolicy.isAllowedScheme("file") shouldBe false
        CopyToClipboardUriPolicy.isAllowedScheme("FILE") shouldBe false
    }

    test("allows content scheme case-insensitive") {
        CopyToClipboardUriPolicy.isAllowedScheme("CONTENT") shouldBe true
        CopyToClipboardUriPolicy.isAllowedScheme("Content") shouldBe true
    }

    test("rejects null scheme") {
        CopyToClipboardUriPolicy.isAllowedScheme(null) shouldBe false
    }

    test("rejects http scheme") {
        CopyToClipboardUriPolicy.isAllowedScheme("http") shouldBe false
        CopyToClipboardUriPolicy.isAllowedScheme("https") shouldBe false
    }

    test("rejects javascript scheme") {
        CopyToClipboardUriPolicy.isAllowedScheme("javascript") shouldBe false
    }

    test("rejects data scheme") {
        CopyToClipboardUriPolicy.isAllowedScheme("data") shouldBe false
    }

    test("rejects custom scheme") {
        CopyToClipboardUriPolicy.isAllowedScheme("myapp") shouldBe false
    }

    test("content type compatible when provider reports image type") {
        CopyToClipboardUriPolicy.isContentTypeCompatible("image/png", imageFilter) shouldBe true
        CopyToClipboardUriPolicy.isContentTypeCompatible("image/jpeg", imageFilter) shouldBe true
        CopyToClipboardUriPolicy.isContentTypeCompatible("image/webp", imageFilter) shouldBe true
    }

    test("content type compatible when provider reports null") {
        CopyToClipboardUriPolicy.isContentTypeCompatible(null, imageFilter) shouldBe true
    }

    test("content type rejects non-image types") {
        CopyToClipboardUriPolicy.isContentTypeCompatible("text/plain", imageFilter) shouldBe false
        CopyToClipboardUriPolicy.isContentTypeCompatible("application/pdf", imageFilter) shouldBe false
        CopyToClipboardUriPolicy.isContentTypeCompatible("video/mp4", imageFilter) shouldBe false
    }
})
