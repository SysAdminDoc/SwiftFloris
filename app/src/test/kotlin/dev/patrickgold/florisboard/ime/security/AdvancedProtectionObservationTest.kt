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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The observation side of Advanced Protection Mode.
 *
 * Every enforcement point calls [AdvancedProtectionPolicy.decide] live, so a
 * callback that never fires cannot leave a stale answer withdrawing anything.
 * What the registration buys is a change signal for the two surfaces a pull
 * cannot reach: a Settings screen that is already composed, and a keyboard
 * session already in progress.
 *
 * These run against the injected [AdvancedProtectionPolicy.CallbackHost] rather
 * than the platform one. Robolectric hands back no `AdvancedProtectionManager`
 * even at SDK 36, so a test that used the real host would register nothing and
 * every "registered exactly once" assertion would hold trivially.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class AdvancedProtectionObservationTest {

    private class FakeHost(private val registerSucceeds: Boolean = true) :
        AdvancedProtectionPolicy.CallbackHost {

        var registrations: Int = 0
            private set
        var unregistrations: Int = 0
            private set
        private var onChanged: ((Boolean) -> Unit)? = null

        override fun register(onChanged: (Boolean) -> Unit): Boolean {
            if (!registerSucceeds) return false
            registrations++
            this.onChanged = onChanged
            return true
        }

        override fun unregister() {
            unregistrations++
            onChanged = null
        }

        fun emit(enabled: Boolean) {
            val sink = requireNonNull()
            sink(enabled)
        }

        fun isLive(): Boolean = onChanged != null

        private fun requireNonNull(): (Boolean) -> Unit =
            onChanged ?: error("nothing registered, so the platform could not have called back")
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var host: FakeHost

    @Before
    fun installFakeHost() {
        host = FakeHost()
        AdvancedProtectionPolicy.callbackHostFactory = { host }
    }

    @After
    fun tearDown() {
        AdvancedProtectionPolicy.stopObserving(context)
        AdvancedProtectionPolicy.callbackHostFactory = null
    }

    @Test
    fun startingRegistersExactlyOnce() {
        AdvancedProtectionPolicy.startObserving(context)

        assertEquals(1, host.registrations)
        assertTrue(AdvancedProtectionPolicy.isObserving())
    }

    @Test
    fun repeatStartsDoNotRegisterAgain() {
        // A second registration would leave a callback the process can never
        // hand back, because only the most recent one is remembered.
        repeat(5) { AdvancedProtectionPolicy.startObserving(context) }

        assertEquals(1, host.registrations)
    }

    @Test
    fun stoppingUnregistersOnceAndReleasesTheHost() {
        AdvancedProtectionPolicy.startObserving(context)
        AdvancedProtectionPolicy.stopObserving(context)

        assertEquals(1, host.unregistrations)
        assertFalse(host.isLive())
        assertFalse(AdvancedProtectionPolicy.isObserving())
    }

    @Test
    fun stoppingWithoutAStartDoesNothing() {
        AdvancedProtectionPolicy.stopObserving(context)

        assertEquals(0, host.unregistrations)
        assertFalse(AdvancedProtectionPolicy.isObserving())
    }

    @Test
    fun startingAgainAfterStoppingRegistersAfresh() {
        AdvancedProtectionPolicy.startObserving(context)
        AdvancedProtectionPolicy.stopObserving(context)
        AdvancedProtectionPolicy.startObserving(context)

        assertEquals(2, host.registrations)
        assertEquals(1, host.unregistrations)
    }

    @Test
    fun enablingProtectionWithdrawsLearningClipboardAndEnrolmentAtOnce() {
        AdvancedProtectionPolicy.startObserving(context)

        host.emit(enabled = true)

        val decision = AdvancedProtectionPolicy.decisions.value
        assertTrue(decision.forcesIncognito)
        assertTrue(decision.suspendsClipboardHistoryPersistence)
        assertTrue(decision.blocksNewEnrolment)
    }

    @Test
    fun disablingProtectionHandsTheDecisionBackToSavedPreferences() {
        AdvancedProtectionPolicy.startObserving(context)
        host.emit(enabled = true)

        host.emit(enabled = false)

        assertEquals(
            AdvancedProtectionPolicy.Decision.Unrestricted,
            AdvancedProtectionPolicy.decisions.value,
        )
    }

    @Test
    fun aHostThatRefusesToRegisterIsNotRetained() {
        // Better to fall back to the live read at each decision than to believe
        // a callback is coming when the platform declined to give one.
        val refusing = FakeHost(registerSucceeds = false)
        AdvancedProtectionPolicy.callbackHostFactory = { refusing }

        AdvancedProtectionPolicy.startObserving(context)

        assertFalse(AdvancedProtectionPolicy.isObserving())
        assertEquals(0, refusing.unregistrations)
    }

    @Test
    @Config(sdk = [35])
    fun nothingIsRegisteredBelowApi36() {
        AdvancedProtectionPolicy.startObserving(context)

        assertEquals(0, host.registrations)
        assertFalse(AdvancedProtectionPolicy.isObserving())
        assertEquals(
            AdvancedProtectionPolicy.Decision.Unrestricted,
            AdvancedProtectionPolicy.decisions.value,
        )
    }

    @Test
    fun theObservedStateStartsFromALiveRead() {
        AdvancedProtectionPolicy.startObserving(context)

        assertEquals(
            AdvancedProtectionPolicy.decide(context),
            AdvancedProtectionPolicy.decisions.value,
            "the seed must agree with what the enforcement points would read",
        )
    }

    @Test
    fun stoppingDropsBackToUnrestricted() {
        AdvancedProtectionPolicy.startObserving(context)
        host.emit(enabled = true)

        AdvancedProtectionPolicy.stopObserving(context)

        assertEquals(
            AdvancedProtectionPolicy.Decision.Unrestricted,
            AdvancedProtectionPolicy.decisions.value,
            "an unobserved process must not keep asserting a protection it stopped tracking",
        )
    }
}
