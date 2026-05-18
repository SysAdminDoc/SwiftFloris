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

import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardFileInfo
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType

object ClipboardRestoredFileInfo {
    fun create(item: ClipboardItem, fileId: Long, fileSizeBytes: Long): ClipboardFileInfo? {
        val displayPrefix = when (item.type) {
            ItemType.IMAGE -> "Restored image"
            ItemType.VIDEO -> "Restored video"
            ItemType.TEXT -> return null
        }
        return ClipboardFileInfo(
            id = fileId,
            displayName = "$displayPrefix $fileId",
            size = fileSizeBytes,
            orientation = 0,
            mimeTypes = item.mimeTypes,
        )
    }
}
