/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.clipboard

/** Privacy gate for the clipboard's deliberate, temporary sensitive-text reveal. */
internal object ClipboardSensitiveRevealPolicy {
    const val REVEAL_DURATION_MS = 4_000L

    fun canReveal(
        isSensitive: Boolean,
        isDeviceLocked: Boolean,
        isIncognitoMode: Boolean,
    ): Boolean {
        return isSensitive && !isDeviceLocked && !isIncognitoMode
    }
}
