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

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import io.kotest.matchers.string.shouldContain
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ClipboardMediaAccessibilityTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun mediaDescriptionKindOnlyClassifiesImageAndVideoItems() {
        assertEquals(ClipboardMediaDescriptionKind.IMAGE, clipboardMediaDescriptionKind(item(ItemType.IMAGE)))
        assertEquals(ClipboardMediaDescriptionKind.VIDEO, clipboardMediaDescriptionKind(item(ItemType.VIDEO)))
        assertEquals(null, clipboardMediaDescriptionKind(item(ItemType.TEXT)))
    }

    @Test
    fun mediaAccessibilityStringsResolveToRealNonBlankLabels() {
        listOf(
            R.string.clipboard__item_description_text,
            R.string.clipboard__item_description_sensitive_text,
            R.string.clipboard__item_description_image,
            R.string.clipboard__item_description_video,
            R.string.clipboard__text_item_a11y,
            R.string.clipboard__text_item_a11y_no_group,
            R.string.clipboard__text_item_sensitive_a11y,
            R.string.clipboard__text_item_sensitive_a11y_no_group,
            R.string.clipboard__media_item_a11y,
            R.string.clipboard__media_item_a11y_no_group,
            R.string.clipboard__item_actions_a11y,
        ).forEach { resId ->
            val value = context.getString(resId)
            assertTrue(value.isNotBlank(), "Clipboard media accessibility string $resId must be non-blank")
            assertTrue(value != "res-$resId", "Clipboard media accessibility string $resId must not use fallback")
        }

        val template = context.getString(R.string.clipboard__media_item_a11y)
        template shouldContain "{media_type}"
        template shouldContain "{group}"
        template shouldContain "{copied_time}"
    }

    @Test
    fun clipboardInputLayoutKeepsMediaTileSemanticsAndDecorativeOverlays() {
        val source = locateClipboardInputLayoutSource().readText()

        source shouldContain "clipboardMediaDescriptionKind(item)"
        source shouldContain "clipboardTextAccessibilityPreview(item.stringRepresentation())"
        source shouldContain "semantics(mergeDescendants = true) { contentDescription = itemA11yDescription }"
        source shouldContain "role = Role.Button"
        source shouldContain "onLongClickLabel = stringRes(R.string.clipboard__item_actions_a11y)"
        source shouldContain "R.string.clipboard__text_item_a11y"
        source shouldContain "R.string.clipboard__text_item_sensitive_a11y"
        source shouldContain "R.string.clipboard__media_item_a11y"
        source shouldContain "R.string.clipboard__media_item_a11y_no_group"
        source shouldContain "contentDescription = null"
        source shouldContain "Icons.Default.Videocam"
    }

    @Test
    fun textAccessibilityPreviewCollapsesWhitespaceAndCapsLongContent() {
        val longText = "alpha\n\nbeta\t" + "x".repeat(TEXT_A11Y_PREVIEW_CHAR_LIMIT + 20)

        val preview = clipboardTextAccessibilityPreview(longText)

        assertTrue("\n" !in preview)
        assertTrue("\t" !in preview)
        assertTrue(preview.length <= TEXT_A11Y_PREVIEW_CHAR_LIMIT + 1)
    }
}

private fun item(type: ItemType): ClipboardItem = ClipboardItem(
    type = type,
    text = if (type == ItemType.TEXT) "hello" else null,
    uri = null,
    creationTimestampMs = 1L,
    isPinned = false,
    mimeTypes = emptyList(),
    isSensitive = false,
)

private fun locateClipboardInputLayoutSource(): File {
    val candidates = listOf(
        "app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardInputLayout.kt",
        "src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardInputLayout.kt",
    )
    return candidates.map(::File).firstOrNull { it.exists() && it.canRead() }
        ?: error("ClipboardInputLayout.kt not reachable from working directory ${File(".").absolutePath}")
}
