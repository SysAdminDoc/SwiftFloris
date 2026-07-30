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

import android.content.ClipData
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType

/**
 * The single text-retention boundary for clipboard history.
 *
 * Live system text above [MAX_RETAINED_UTF8_BYTES] remains available to the
 * explicit Paste action, but must not become a [ClipboardItem]. Keeping the
 * decision here also prevents oversized text from reaching sensitivity
 * classification, history deduplication, search, backup, or encryption.
 */
internal object ClipboardTextRetentionPolicy {
    const val MAX_RETAINED_UTF8_BYTES: Int = 64 * 1024

    /**
     * Returns whether [text] fits in the retained-history UTF-8 budget.
     *
     * This does not allocate an encoded copy. Inputs longer than the byte cap
     * are rejected from their UTF-16 length alone, so 120 KiB and multi-MiB
     * clips take constant time and do not walk the full payload.
     */
    fun shouldRetain(text: CharSequence?): Boolean {
        if (text == null) return true
        if (text.length > MAX_RETAINED_UTF8_BYTES) return false
        if (text.length <= MAX_RETAINED_UTF8_BYTES / 4) return true

        var utf8Bytes = 0
        var index = 0
        while (index < text.length) {
            val current = text[index]
            utf8Bytes += when {
                current.code <= 0x7F -> 1
                current.code <= 0x7FF -> 2
                current.isHighSurrogate() &&
                    index + 1 < text.length &&
                    text[index + 1].isLowSurrogate() -> {
                    index += 1
                    4
                }
                current.isSurrogate() -> 1
                else -> 3
            }
            if (utf8Bytes > MAX_RETAINED_UTF8_BYTES) return false
            index += 1
        }
        return true
    }

    fun shouldRetain(item: ClipboardItem): Boolean = shouldRetain(item.text)

    /**
     * Returns the original live text only when it must bypass retained state.
     * Media clips are not converted into paste-only text even if they expose
     * optional text metadata.
     */
    fun pasteOnlyTextOrNull(data: ClipData): CharSequence? {
        if (ClipboardItem.typeOf(data) != ItemType.TEXT) return null
        val text = data.getItemAt(0).text ?: return null
        return text.takeUnless(::shouldRetain)
    }
}
