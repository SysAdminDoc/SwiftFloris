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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
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
            R.string.clip__reveal_sensitive_item,
            R.string.clip__hide_sensitive_item,
            R.string.clip__mark_item_sensitive,
            R.string.clip__mark_item_not_sensitive,
        ).forEach { resId ->
            val value = context.getString(resId)
            assertTrue(value.isNotBlank(), "Clipboard media accessibility string $resId must be non-blank")
            assertTrue(value != "res-$resId", "Clipboard media accessibility string $resId must not use fallback")
        }

        val template = context.getString(R.string.clipboard__media_item_a11y)
        assertTrue("{media_type}" in template)
        assertTrue("{group}" in template)
        assertTrue("{copied_time}" in template)
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
