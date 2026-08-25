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

import dev.patrickgold.florisboard.lib.devtools.flogError
import org.florisboard.lib.kotlin.UserFacingError

/**
 * Returns a bounded, single-line summary of this failure for a toast, a notice
 * card or a dialog, and writes the whole thing to the log on the way past.
 *
 * The two halves belong together. Bounding the message is what keeps a hostile
 * archive entry name, or a nested cause chain, from filling the screen; logging
 * the full stack trace is what keeps that bounding from destroying the only
 * copy of the detail somebody will need to diagnose it. Sanitizing without
 * logging trades one problem for another, so callers get both or neither.
 *
 * [fallback] is shown when the failure carries no usable message. Resolve it
 * during composition, not inside the callback.
 */
fun Throwable?.summarizeForUser(fallback: String): String {
    if (this != null) {
        flogError { stackTraceToString() }
    }
    return UserFacingError.summarize(this, fallback)
}
