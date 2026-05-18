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

import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardFileStorage
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardMediaClonePolicy
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ClipboardMediaSafetyPolicyTest : FunSpec({
    test("clipboard image clone cap stays at 32 MiB") {
        ClipboardFileStorage.MediaKind.IMAGE.maxCloneBytes shouldBe 32L * 1024L * 1024L
    }

    test("clipboard video clone cap stays at 128 MiB") {
        ClipboardFileStorage.MediaKind.VIDEO.maxCloneBytes shouldBe 128L * 1024L * 1024L
    }

    test("provider insert result rejects sentinel or missing file ids") {
        ClipboardMediaClonePolicy.isValidInsertedFileId(null) shouldBe false
        ClipboardMediaClonePolicy.isValidInsertedFileId(-1L) shouldBe false
        ClipboardMediaClonePolicy.isValidInsertedFileId(0L) shouldBe false
        ClipboardMediaClonePolicy.isValidInsertedFileId(1L) shouldBe true
    }

    test("provider reads EXIF orientation for images only") {
        ClipboardMediaClonePolicy.shouldReadExifOrientation(ClipboardFileStorage.MediaKind.IMAGE) shouldBe true
        ClipboardMediaClonePolicy.shouldReadExifOrientation(ClipboardFileStorage.MediaKind.VIDEO) shouldBe false
    }

    test("preview policy accepts bounded image dimensions") {
        ClipboardPreviewImagePolicy.requireSupportedBounds(
            ClipboardPreviewImagePolicy.MAX_DECODE_BITMAP_SIDE,
            ClipboardPreviewImagePolicy.MAX_DECODE_BITMAP_SIDE,
        )
    }

    test("preview policy rejects unknown image dimensions") {
        shouldThrow<IllegalArgumentException> {
            ClipboardPreviewImagePolicy.requireSupportedBounds(0, 128)
        }
    }

    test("preview policy rejects oversized image dimensions before decode") {
        shouldThrow<IllegalArgumentException> {
            ClipboardPreviewImagePolicy.requireSupportedBounds(
                ClipboardPreviewImagePolicy.MAX_DECODE_BITMAP_SIDE + 1,
                128,
            )
        }
    }
})
