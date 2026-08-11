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

package dev.patrickgold.florisboard.ime.security

import android.content.Context
import android.os.Build
import android.security.advancedprotection.AdvancedProtectionManager
import androidx.annotation.ChecksSdkIntAtLeast

/**
 * Reacts to Android Advanced Protection Mode (AAPM).
 *
 * AAPM is the platform's declared signal that this user is at elevated risk —
 * it is the setting journalists, activists and similar targets are told to
 * turn on. Google's guidance is that apps query it and disable their own risky
 * behaviour rather than waiting to be told. A keyboard sees every character
 * the user types, so it is close to the top of the list of apps that should
 * respond.
 *
 * SwiftFloris already holds no `INTERNET` permission, so there is no network
 * behaviour to withdraw. What remains is local retention and trust surface:
 * learning from what is typed, keeping clipboard history on disk, and letting
 * new third-party packages enrol as addons or MCP daemons. Under AAPM all
 * three are held at their safest setting regardless of the user's saved
 * preferences, and the privacy posture screen says so.
 *
 * Nothing here changes behaviour below API 36 — [isSupported] is false, the
 * decision is [Decision.Unrestricted], and the manifest permission is inert.
 */
object AdvancedProtectionPolicy {
    /** First platform release exposing `AdvancedProtectionManager`. */
    const val MinimumApiLevel: Int = Build.VERSION_CODES.BAKLAVA

    /**
     * What SwiftFloris withdraws while AAPM is on. Pure data so the decision is
     * testable without a device, and so the Settings screen and the enrolment
     * gate read the same source rather than each re-deriving it.
     */
    data class Decision(
        val advancedProtectionEnabled: Boolean,
    ) {
        /** Never learn from typed text, whatever the saved preference says. */
        val forcesIncognito: Boolean get() = advancedProtectionEnabled

        /** Keep no clipboard history on disk. */
        val suspendsClipboardHistoryPersistence: Boolean get() = advancedProtectionEnabled

        /**
         * Refuse to enrol a package that is not already trusted. An addon or
         * MCP daemon receives selected text, so adding one is exactly the kind
         * of trust decision AAPM exists to slow down. Already-enrolled packages
         * keep working; this blocks new grants, not existing ones.
         */
        val blocksNewEnrolment: Boolean get() = advancedProtectionEnabled

        companion object {
            val Unrestricted = Decision(advancedProtectionEnabled = false)
        }
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.BAKLAVA)
    fun isSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= MinimumApiLevel

    /**
     * Read the live AAPM state. Returns [Decision.Unrestricted] on older
     * platforms, and also when the read fails — a keyboard that cannot answer
     * the question must not lock the user out of their own settings, and the
     * unrestricted answer is the one that matches every release before API 36.
     */
    fun decide(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): Decision {
        if (!isSupported(sdkInt)) return Decision.Unrestricted
        val manager = runCatching {
            context.getSystemService(AdvancedProtectionManager::class.java)
        }.getOrNull() ?: return Decision.Unrestricted
        val enabled = runCatching { manager.isAdvancedProtectionEnabled }.getOrDefault(false)
        return Decision(advancedProtectionEnabled = enabled)
    }
}
