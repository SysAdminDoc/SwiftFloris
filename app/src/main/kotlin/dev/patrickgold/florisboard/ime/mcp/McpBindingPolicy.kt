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

package dev.patrickgold.florisboard.ime.mcp

/**
 * Whether this build can bind an MCP daemon at all.
 *
 * The keyboard has no audited production action that dispatches through MCP, so
 * `FlorisImeService` starts no lifecycle and pins both registries empty: merely starting the
 * keyboard cannot discover or bind a daemon. That was the right call, but it left Settings → MCP
 * offering discovery review, trust actions and per-daemon toggles that govern nothing, so a user
 * who enabled a daemon there had every reason to believe something turned on.
 *
 * This is the single place that decides, so the service and the Settings screen cannot drift
 * apart, and `McpBindingPolicyTest` pins the two together.
 */
object McpBindingPolicy {
    /**
     * True while daemon binding is parked. Flip this to false in the same change that gives the
     * keyboard a real audited MCP action, not before — the Settings screen reads it to decide
     * whether to tell the user that nothing is bound.
     */
    const val IS_PARKED: Boolean = true

    /** Whether [FlorisImeService] should construct and start an [McpServiceLifecycle]. */
    fun shouldStartLifecycle(): Boolean = !IS_PARKED

    /**
     * Whether Settings should warn that daemon binding and dispatch cannot happen in this build.
     *
     * Trust review, enable/disable toggles and pin resets stay usable and keep persisting while
     * parked: the user is curating state that takes effect when binding returns, and wiping that
     * work would be worse than showing it. The banner is what makes the difference visible.
     */
    fun showsParkedNotice(): Boolean = IS_PARKED
}
