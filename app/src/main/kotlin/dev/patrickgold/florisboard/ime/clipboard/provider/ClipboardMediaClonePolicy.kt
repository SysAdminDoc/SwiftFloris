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

package dev.patrickgold.florisboard.ime.clipboard.provider

import android.content.ContentUris
import android.net.Uri
import org.florisboard.lib.kotlin.tryOrNull

internal object ClipboardMediaClonePolicy {
    fun shouldReadExifOrientation(mediaKind: ClipboardFileStorage.MediaKind): Boolean {
        return mediaKind == ClipboardFileStorage.MediaKind.IMAGE
    }

    fun isValidInsertedFileId(id: Long?): Boolean {
        return id != null && id > 0L
    }

    fun requireValidInsertedUri(uri: Uri?): Uri {
        val insertedUri = requireNotNull(uri) {
            "Clipboard media provider returned no URI"
        }
        val id = tryOrNull { ContentUris.parseId(insertedUri) }
        require(insertedUri.authority == ClipboardMediaProvider.AUTHORITY && isValidInsertedFileId(id)) {
            "Clipboard media provider returned an invalid insert URI: $insertedUri"
        }
        return insertedUri
    }
}
