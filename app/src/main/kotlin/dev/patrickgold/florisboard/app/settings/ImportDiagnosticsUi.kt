/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.app.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import dev.patrickgold.florisboard.R

internal fun copyImportDiagnosticsToClipboard(context: Context, details: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(
        ClipData.newPlainText(
            context.getString(R.string.import_diagnostics__clipboard_label),
            details,
        )
    )
    Toast.makeText(context, R.string.import_diagnostics__copied, Toast.LENGTH_SHORT).show()
}
