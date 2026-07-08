/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.editor

import android.net.Uri
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardMediaProvider

internal object ClipboardCommitMediaPolicy {
    fun providerFileId(uri: Uri?): Long? {
        return providerFileId(uri?.authority, uri?.lastPathSegment)
    }

    fun providerFileId(authority: String?, lastPathSegment: String?): Long? {
        if (authority != ClipboardMediaProvider.AUTHORITY) return null
        return lastPathSegment?.toLongOrNull()?.takeIf { it > 0L }
    }
}
