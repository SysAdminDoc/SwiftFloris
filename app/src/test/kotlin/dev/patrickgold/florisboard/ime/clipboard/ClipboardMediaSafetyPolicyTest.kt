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
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardMediaClonePolicy
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardMediaProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

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

    test("provider metadata writes require a ready DAO instead of nullable async drops") {
        val source = locateClipboardMediaProviderSource().readText()

        source shouldContain "requireClipboardFilesDao().insert(fileInfo)"
        source shouldContain "requireClipboardFilesDao().delete(id)"
        source shouldNotContain "clipboardFilesDao?.insert(fileInfo)"
        source shouldNotContain "clipboardFilesDao?.delete(id)"
    }

    test("clipboard media is encrypted before it reaches app-private storage") {
        val storageSource = locateClipboardFileStorageSource().readText()
        val cryptoSource = locateClipboardMediaEncryptionSource().readText()

        storageSource shouldContain "ClipboardMediaEncryption.encrypt"
        storageSource shouldContain "migratePlaintextFiles"
        storageSource shouldContain "openDecryptedTempFile"
        storageSource shouldContain "copyDecryptedTo"
        cryptoSource shouldContain "TinkStringPreferenceCrypto"
        cryptoSource shouldContain "clipboard_media_key"
        cryptoSource shouldContain "swiftfloris_clipboard_media_aes_v1"
    }

    test("clipboard previews consume provider descriptors instead of raw ciphertext files") {
        val source = locateClipboardInputLayoutSource().readText()

        source shouldContain "openFileDescriptor(uri, \"r\")"
        source shouldContain "getScaledFrameAtTime"
        source shouldNotContain "ClipboardFileStorage.getFileForId"
        source shouldNotContain "BitmapFactory.decodeFile("
    }

    test("history-disabled primary replacement closes only provider-backed media") {
        ClipboardPrimaryClipCleanupPolicy.shouldCloseProviderBackedPrimaryClipUri(
            uriString = "content://${ClipboardMediaProvider.AUTHORITY}/clips/images/7",
            historyEnabled = false,
        ) shouldBe true
        ClipboardPrimaryClipCleanupPolicy.shouldCloseProviderBackedPrimaryClipUri(
            uriString = "content://external.provider/images/7",
            historyEnabled = false,
        ) shouldBe false
        ClipboardPrimaryClipCleanupPolicy.shouldCloseReplacedPrimaryClip(
            replacedItem = ClipboardItem.text("plain text"),
            historyEnabled = false,
        ) shouldBe false
        ClipboardPrimaryClipCleanupPolicy.shouldCloseProviderBackedPrimaryClipUri(
            uriString = "content://${ClipboardMediaProvider.AUTHORITY}/clips/images/7",
            historyEnabled = true,
        ) shouldBe false
    }

    test("clipboard palette media previews decode off the composition thread") {
        val source = locateClipboardInputLayoutSource().readText()

        source shouldContain "produceState<ClipboardMediaPreviewResult>"
        source shouldContain "withContext(Dispatchers.IO)"
        source shouldContain "ClipboardPreviewImagePolicy.sampleSizeForPreview"
        source shouldContain "MediaMetadataRetriever.OPTION_CLOSEST_SYNC"
        source shouldNotContain "val bitmap = remember(id)"
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

    test("preview policy samples large images to the preview budget") {
        ClipboardPreviewImagePolicy.sampleSizeForPreview(1024, 768) shouldBe 1
        ClipboardPreviewImagePolicy.sampleSizeForPreview(4096, 2048) shouldBe 4
        ClipboardPreviewImagePolicy.sampleSizeForPreview(2048, 4096) shouldBe 4
    }

    test("preview policy scales video thumbnail targets to the preview budget") {
        ClipboardPreviewImagePolicy.scaledPreviewBounds(1024, 768) shouldBe
            ClipboardPreviewImagePolicy.PreviewBounds(1024, 768)
        ClipboardPreviewImagePolicy.scaledPreviewBounds(4096, 2048) shouldBe
            ClipboardPreviewImagePolicy.PreviewBounds(1024, 512)
        ClipboardPreviewImagePolicy.scaledPreviewBounds(2048, 4096) shouldBe
            ClipboardPreviewImagePolicy.PreviewBounds(512, 1024)
    }

    test("copy-to-clipboard activity does not auto-preview content provider URIs") {
        CopyToClipboardPreviewPolicy.shouldAutoPreviewSharedImageUriScheme("content") shouldBe false
        CopyToClipboardPreviewPolicy.shouldAutoPreviewSharedImageUriScheme("CONTENT") shouldBe false
    }

    test("copy-to-clipboard activity keeps non-provider image URI previews eligible") {
        CopyToClipboardPreviewPolicy.shouldAutoPreviewSharedImageUriScheme("file") shouldBe true
        CopyToClipboardPreviewPolicy.shouldAutoPreviewSharedImageUriScheme(null) shouldBe false
    }
})

private fun locateClipboardInputLayoutSource(): File {
    val candidates = listOf(
        "app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardInputLayout.kt",
        "src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardInputLayout.kt",
    )
    return candidates.map(::File).firstOrNull { it.exists() && it.canRead() }
        ?: error("ClipboardInputLayout.kt not reachable from working directory ${File(".").absolutePath}")
}

private fun locateClipboardMediaProviderSource(): File {
    val candidates = listOf(
        "app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardMediaProvider.kt",
        "src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardMediaProvider.kt",
    )
    return candidates.map(::File).firstOrNull { it.exists() && it.canRead() }
        ?: error("ClipboardMediaProvider.kt not reachable from working directory ${File(".").absolutePath}")
}

private fun locateClipboardFileStorageSource(): File {
    val candidates = listOf(
        "app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardFileStorage.kt",
        "src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardFileStorage.kt",
    )
    return candidates.map(::File).firstOrNull { it.exists() && it.canRead() }
        ?: error("ClipboardFileStorage.kt not reachable from working directory ${File(".").absolutePath}")
}

private fun locateClipboardMediaEncryptionSource(): File {
    val candidates = listOf(
        "app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardMediaEncryption.kt",
        "src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardMediaEncryption.kt",
    )
    return candidates.map(::File).firstOrNull { it.exists() && it.canRead() }
        ?: error("ClipboardMediaEncryption.kt not reachable from working directory ${File(".").absolutePath}")
}
