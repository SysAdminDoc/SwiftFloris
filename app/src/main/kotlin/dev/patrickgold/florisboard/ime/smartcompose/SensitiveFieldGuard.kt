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

package dev.patrickgold.florisboard.ime.smartcompose

/**
 * ROADMAP §7 N7 — sensitive-field guard for opt-in surfaces.
 *
 * The privacy moat (`§1: zero cloud processing, zero telemetry, all
 * features work offline`) means smart-compose / inline-translation /
 * MCP tool calls are already strictly local. But local-only isn't
 * the same as *zero-leak*: a smart-compose addon's working buffer +
 * the suggestion history surface + the per-app LoRA fine-tuning
 * pipeline (L1.3) all *could* persist text fragments that the user
 * never expected to land in a model state.
 *
 * Android's `InputType` flag system identifies fields the user
 * almost certainly does not want the IME to learn from:
 *
 *  - `TYPE_TEXT_VARIATION_PASSWORD` / `_VISIBLE_PASSWORD` /
 *    `_WEB_PASSWORD` — every password input.
 *  - `TYPE_NUMBER_VARIATION_PASSWORD` — numeric PIN inputs.
 *  - `IME_FLAG_NO_PERSONALIZED_LEARNING` — Gboard/SwiftKey-style
 *    "don't train on this" flag that legitimate apps set on banking
 *    + medical + chat surfaces.
 *  - Any `EditorInfo` with `IME_FLAG_NO_LEARNING` set (older API
 *    25- equivalent of the above).
 *
 * This guard is a single-purpose predicate the IME's typing
 * pipeline asks before calling into any opt-in addon surface
 * (smart-compose, translation, MCP). The check is intentionally a
 * **bitwise int probe** rather than an `EditorInfo` reference so
 * the unit tests don't need Robolectric.
 *
 * Maps directly to the existing CAKI (Content-Aware Keyboard
 * Injection) hardening already in `EditorInstance`; this is the
 * narrower predicate for the addon-call paths.
 */
object SensitiveFieldGuard {

    // Constants mirror android.text.InputType to avoid pulling android-* deps
    // into the test source set. Values come from the Android source.
    private const val TYPE_MASK_CLASS: Int = 0x0000_000f
    private const val TYPE_MASK_VARIATION: Int = 0x0000_0ff0
    private const val TYPE_CLASS_TEXT: Int = 0x0000_0001
    private const val TYPE_CLASS_NUMBER: Int = 0x0000_0002
    private const val TYPE_TEXT_VARIATION_PASSWORD: Int = 0x0000_0080
    private const val TYPE_TEXT_VARIATION_VISIBLE_PASSWORD: Int = 0x0000_0090
    private const val TYPE_TEXT_VARIATION_WEB_PASSWORD: Int = 0x0000_00e0
    private const val TYPE_NUMBER_VARIATION_PASSWORD: Int = 0x0000_0010

    // android.view.inputmethod.EditorInfo flag values.
    private const val IME_FLAG_NO_PERSONALIZED_LEARNING: Int = 0x0100_0000

    /**
     * True when the field identified by [inputType] + [imeOptions]
     * should suppress every opt-in addon call (smart-compose,
     * translation, MCP). Caller routes that decision through its
     * "should I show ghost-text right now?" path.
     */
    fun isSensitive(inputType: Int, imeOptions: Int): Boolean {
        if ((imeOptions and IME_FLAG_NO_PERSONALIZED_LEARNING) != 0) return true
        val cls = inputType and TYPE_MASK_CLASS
        val variation = inputType and TYPE_MASK_VARIATION
        return when (cls) {
            TYPE_CLASS_TEXT -> variation == TYPE_TEXT_VARIATION_PASSWORD ||
                variation == TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == TYPE_TEXT_VARIATION_WEB_PASSWORD
            TYPE_CLASS_NUMBER -> variation == TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    /**
     * Reason the field was flagged, or null when it isn't sensitive.
     * Useful for the dev-log line the IME emits when it suppresses
     * the smart-compose surface.
     */
    fun reasonFor(inputType: Int, imeOptions: Int): String? {
        if ((imeOptions and IME_FLAG_NO_PERSONALIZED_LEARNING) != 0) {
            return "IME_FLAG_NO_PERSONALIZED_LEARNING set"
        }
        val cls = inputType and TYPE_MASK_CLASS
        val variation = inputType and TYPE_MASK_VARIATION
        return when {
            cls == TYPE_CLASS_TEXT && variation == TYPE_TEXT_VARIATION_PASSWORD ->
                "TEXT password field"
            cls == TYPE_CLASS_TEXT && variation == TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ->
                "visible password field"
            cls == TYPE_CLASS_TEXT && variation == TYPE_TEXT_VARIATION_WEB_PASSWORD ->
                "web password field"
            cls == TYPE_CLASS_NUMBER && variation == TYPE_NUMBER_VARIATION_PASSWORD ->
                "numeric PIN field"
            else -> null
        }
    }
}
