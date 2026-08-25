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
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow

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

    /**
     * The current decision, as a stream.
     *
     * [decide] stays the authoritative read and every enforcement point still
     * calls it, so a callback that never arrives cannot leave a stale answer
     * enforcing anything. What this adds is a change *signal*, for the two
     * places a pull cannot serve: a Settings screen that is already composed
     * when the user toggles AAPM elsewhere, and a keyboard session already in
     * progress, which would otherwise keep learning until the next field focus.
     */
    val decisions: StateFlow<Decision>
        get() = decisionState

    private val decisionState = MutableStateFlow(Decision.Unrestricted)
    private val registrationLock = Any()
    private var activeHost: CallbackHost? = null

    /**
     * The platform plumbing, behind a seam.
     *
     * Robolectric has no `AdvancedProtectionManager` even at SDK 36, so the
     * real host returns null under test and every lifecycle assertion would
     * pass without a callback ever existing. Tests install a fake instead, and
     * assert against something that actually registered.
     */
    internal interface CallbackHost {
        fun register(onChanged: (Boolean) -> Unit): Boolean
        fun unregister()
    }

    /** Test seam. Null means "use the real `AdvancedProtectionManager`". */
    internal var callbackHostFactory: ((Context) -> CallbackHost?)? = null

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun platformHost(context: Context): CallbackHost? {
        val manager = runCatching {
            context.getSystemService(AdvancedProtectionManager::class.java)
        }.getOrNull() ?: return null
        return object : CallbackHost {
            private var callback: AdvancedProtectionManager.Callback? = null

            override fun register(onChanged: (Boolean) -> Unit): Boolean {
                val created = AdvancedProtectionManager.Callback { enabled -> onChanged(enabled) }
                val ok = runCatching {
                    manager.registerAdvancedProtectionCallback(Runnable::run, created)
                }.isSuccess
                if (ok) callback = created
                return ok
            }

            override fun unregister() {
                val held = callback ?: return
                callback = null
                runCatching { manager.unregisterAdvancedProtectionCallback(held) }
            }
        }
    }

    /**
     * Registers the platform callback if it is not already registered, and
     * seeds [decisions] from a live read.
     *
     * Idempotent: a second call while a callback is live re-seeds the state and
     * returns without registering again, because the platform would then hold
     * two callbacks and only one could ever be handed back to unregister.
     */
    fun startObserving(context: Context, sdkInt: Int = Build.VERSION.SDK_INT) {
        val seeded = decide(context, sdkInt)
        synchronized(registrationLock) {
            decisionState.value = seeded
            if (!isSupported(sdkInt) || activeHost != null) return
            // The explicit SDK_INT comparison, rather than isSupported(sdkInt),
            // is what lets lint see that platformHost cannot run on a platform
            // that lacks AdvancedProtectionManager. sdkInt stays a parameter so
            // a test can simulate an older release.
            val host = callbackHostFactory?.invoke(context)
                ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                    platformHost(context)
                } else {
                    null
                }
                ?: return
            val registered = host.register { enabled ->
                decisionState.value = Decision(advancedProtectionEnabled = enabled)
            }
            if (registered) activeHost = host
        }
    }

    /**
     * Unregisters the callback registered by [startObserving], if any, and
     * drops the observed state back to unrestricted.
     */
    fun stopObserving(context: Context, sdkInt: Int = Build.VERSION.SDK_INT) {
        synchronized(registrationLock) {
            val host = activeHost ?: return
            activeHost = null
            decisionState.value = Decision.Unrestricted
            host.unregister()
        }
    }

    /** Visible for tests: whether a platform callback is currently held. */
    internal fun isObserving(): Boolean = synchronized(registrationLock) { activeHost != null }
}
