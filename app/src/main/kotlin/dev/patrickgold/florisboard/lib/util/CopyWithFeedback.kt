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

package dev.patrickgold.florisboard.lib.util

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import dev.patrickgold.florisboard.lib.devtools.flogWarning

/**
 * Copies [text] with [write] and shows one toast saying whether it went through: [copied] when it did, and
 * [failed] for the error when the clipboard threw (its service gone, or a payload the system refused). A
 * failed copy never gets the success toast. Neither the text nor the error's message reaches the log, since
 * these buttons copy signing fingerprints, pairing payloads and audit records.
 *
 * @return whether the copy went through
 */
fun copyWithFeedback(
    context: Context,
    text: String,
    @StringRes copied: Int,
    failed: (Exception) -> CharSequence,
    duration: Int = Toast.LENGTH_SHORT,
    write: (String) -> Unit,
): Boolean {
    try {
        write(text)
    } catch (e: Exception) {
        flogWarning { "Copy to the clipboard failed: ${e.javaClass.name}" }
        Toast.makeText(context, failed(e), duration).show()
        return false
    }
    Toast.makeText(context, copied, duration).show()
    return true
}
