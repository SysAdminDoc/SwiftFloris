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

import org.florisboard.lib.kotlin.MimeTypeFilter

internal object CopyToClipboardUriPolicy {
    // Only `content://` is accepted. A legitimate share-sheet ACTION_SEND of an
    // image always delivers a FileProvider-backed `content://` URI, which the
    // sender must hold (and grant) read permission on. `file://` was previously
    // allowed but has no legitimate sender and is a confused-deputy risk: this
    // activity is exported, so any app could point a `file://` EXTRA_STREAM at
    // one of SwiftFloris' own private files and have the IME (running as its
    // own UID) read and preview-decode it, bypassing the per-scheme content-type
    // recheck that only ran for `content://`. Dropping `file://` closes that
    // hole and guarantees the MIME recheck always applies.
    private val ALLOWED_SCHEMES = setOf("content")

    fun isAllowedScheme(scheme: String?): Boolean {
        scheme ?: return false
        return scheme.lowercase() in ALLOWED_SCHEMES
    }

    fun isContentTypeCompatible(resolvedType: String?, filter: MimeTypeFilter): Boolean {
        resolvedType ?: return true
        return filter.matches(resolvedType)
    }
}
