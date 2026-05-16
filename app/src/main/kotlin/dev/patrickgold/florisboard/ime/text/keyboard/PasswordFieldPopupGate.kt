/*
 * Copyright (C) 2026 The SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.text.keyboard

import dev.patrickgold.florisboard.ime.text.key.KeyVariation

/**
 * ROADMAP §6 N13.3 — pure predicate that suppresses long-press popups whenever the active editor
 * variation is `PASSWORD` (which covers Android `TYPE_TEXT_VARIATION_PASSWORD`,
 * `TYPE_TEXT_VARIATION_VISIBLE_PASSWORD`, `TYPE_TEXT_VARIATION_WEB_PASSWORD`, and
 * `TYPE_NUMBER_VARIATION_PASSWORD` per `EditorInstance.handleStartInputView`).
 *
 * Kept as a separate object so the gate is unit-testable without spinning up Robolectric.
 */
object PasswordFieldPopupGate {
    fun shouldSuppressPopups(activeVariation: KeyVariation): Boolean {
        return activeVariation == KeyVariation.PASSWORD
    }
}
