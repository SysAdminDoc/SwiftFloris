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

package dev.patrickgold.florisboard.ime.text.gestures

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * SwiftFloris builds a 120,000-word glide vocabulary plus a pruner and gesture caches for every
 * eligible subtype, on every device. These cases pin the gate that keeps that off low-RAM devices
 * and the session-scoped recovery after an allocation failure.
 */
class GlideTypingCapabilityTest : FunSpec({

    beforeTest { GlideTypingCapability.resetForTesting() }
    afterTest { GlideTypingCapability.resetForTesting() }

    test("a normal device keeps glide available") {
        val state = GlideTypingCapabilityState(isLowRamDevice = false, allocationFailed = false)

        state.isAvailable shouldBe true
        state.unavailableReason shouldBe null
    }

    test("a low-RAM device explains why glide is off") {
        val state = GlideTypingCapabilityState(isLowRamDevice = true)

        state.isAvailable shouldBe false
        state.unavailableReason shouldBe GlideTypingUnavailableReason.LowRamDevice
    }

    test("an allocation failure disables glide with its own reason") {
        val state = GlideTypingCapabilityState(allocationFailed = true)

        state.isAvailable shouldBe false
        state.unavailableReason shouldBe GlideTypingUnavailableReason.AllocationFailed
    }

    test("the low-RAM verdict outranks a later allocation failure") {
        val state = GlideTypingCapabilityState(isLowRamDevice = true, allocationFailed = true)

        state.unavailableReason shouldBe GlideTypingUnavailableReason.LowRamDevice
    }

    test("an allocation failure survives only the current IME session") {
        GlideTypingCapability.setLowRamDevice(false)
        GlideTypingCapability.disableAfterAllocationFailure()

        GlideTypingCapability.isAvailable shouldBe false
        GlideTypingCapability.unavailableReason shouldBe GlideTypingUnavailableReason.AllocationFailed

        GlideTypingCapability.resetForNewSession()

        GlideTypingCapability.isAvailable shouldBe true
        GlideTypingCapability.unavailableReason shouldBe null
    }

    test("a new session does not re-enable glide on a low-RAM device") {
        GlideTypingCapability.setLowRamDevice(true)
        GlideTypingCapability.disableAfterAllocationFailure()
        GlideTypingCapability.resetForNewSession()

        GlideTypingCapability.isAvailable shouldBe false
        GlideTypingCapability.unavailableReason shouldBe GlideTypingUnavailableReason.LowRamDevice
    }

    test("the observable state publishes every capability change") {
        val observed = mutableListOf<GlideTypingUnavailableReason?>()
        observed += GlideTypingCapability.state.value.unavailableReason

        GlideTypingCapability.setLowRamDevice(false)
        GlideTypingCapability.disableAfterAllocationFailure()
        observed += GlideTypingCapability.state.value.unavailableReason

        GlideTypingCapability.resetForNewSession()
        observed += GlideTypingCapability.state.value.unavailableReason

        observed shouldBe listOf(null, GlideTypingUnavailableReason.AllocationFailed, null)
    }

    test("setting the low-RAM flag is idempotent") {
        GlideTypingCapability.setLowRamDevice(true)
        GlideTypingCapability.setLowRamDevice(true)

        GlideTypingCapability.state.value shouldBe GlideTypingCapabilityState(isLowRamDevice = true)
    }
})
