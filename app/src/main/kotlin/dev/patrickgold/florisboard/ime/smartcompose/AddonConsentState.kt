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
 * ROADMAP matrix #37 — opt-in-addon consent state.
 *
 * MCP enterprise guidance (March 2026) emphasises explicit user consent before any tool invocation. A keyboard
 * IME has an even higher bar than a generic agent: every keystroke flows through this process, so an addon that
 * runs without consent has effectively been silently handed every text field on the device. SwiftFloris already
 * suppresses every opt-in addon on sensitive fields via `SensitiveFieldGuard` (v1.8.17), and per-tool / per-daemon
 * disables shipped in matrix #38 / L7.6b. The consent layer above those is the next defensible control: the
 * user must affirmatively accept before any invocation, even on non-sensitive fields.
 *
 * Three surfaces are gated independently — accepting smart-compose does not auto-accept translation or MCP, and
 * vice versa. The user's first encounter with each surface presents a consent prompt; until they tap an
 * affirmative, every invocation short-circuits to `Response.Suppressed("consent required")` and the audit log
 * records the suppression with the same reason string.
 *
 * Storage shape: per-surface enum prefs (`prefs.privacy.smartComposeConsent` etc.). Default value is
 * [NEEDS_PROMPT] so the consent dialog fires on first use without forcing a Settings round-trip on install.
 */
enum class AddonConsentState {
    /**
     * No decision has been recorded. The router treats this the same as [DENIED] — every invocation
     * short-circuits — but the IME's UI layer is expected to surface a one-time consent prompt the next time
     * the user explicitly invokes the surface (e.g. taps the translation quick action, accepts a smart-compose
     * ghost suggestion, or invokes an MCP tool).
     */
    NEEDS_PROMPT,

    /**
     * The user has affirmatively granted the surface permission to run. Invocations proceed through the
     * existing `SensitiveFieldGuard` / per-daemon / per-tool gates.
     */
    GRANTED,

    /**
     * The user has explicitly rejected the surface. The router short-circuits every invocation and the IME
     * surface never re-prompts (the user can re-enable in Settings → Privacy).
     */
    DENIED;

    /**
     * @return true when the router should forward the invocation to the underlying client; false when the
     *  router should short-circuit to [com.example.Suppressed][AddonInvocationAudit.Outcome.SUPPRESSED]. By
     *  default, only [GRANTED] allows invocations through.
     */
    fun allowsInvocation(): Boolean = this == GRANTED
}
