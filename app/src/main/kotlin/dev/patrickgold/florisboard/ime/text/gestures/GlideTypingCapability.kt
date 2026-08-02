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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Why glide typing is not running, when it is not. */
enum class GlideTypingUnavailableReason {
    /**
     * `ActivityManager.isLowRamDevice`. The glide vocabulary plus its pruner and gesture caches
     * are built for every eligible subtype; on a low-RAM device that allocation competes with the
     * foreground app the keyboard is typing into.
     */
    LowRamDevice,

    /** A dictionary, pruner or cache allocation failed. Glide stays off for this IME session. */
    AllocationFailed,
}

/**
 * Session-scoped glide-typing availability.
 *
 * Two facts drive it: whether the device is flagged low-RAM (stable for the process) and whether
 * an allocation already failed (reset at the start of each IME session, so a one-off memory spike
 * does not permanently disable a feature the user paid for).
 *
 * Nothing here records what was typed — only that a build failed.
 */
object GlideTypingCapability {
    private val _state = MutableStateFlow(GlideTypingCapabilityState())

    val state: StateFlow<GlideTypingCapabilityState> = _state.asStateFlow()

    val unavailableReason: GlideTypingUnavailableReason?
        get() = _state.value.unavailableReason

    val isAvailable: Boolean
        get() = _state.value.unavailableReason == null

    /** Records the device's low-RAM flag. Idempotent; safe to call on every session start. */
    fun setLowRamDevice(isLowRamDevice: Boolean) {
        _state.value = _state.value.copy(isLowRamDevice = isLowRamDevice)
    }

    /**
     * Disables glide for the remainder of this IME session after a failed allocation. The caller
     * must have already released the partial dictionaries, pruners and gesture caches.
     */
    fun disableAfterAllocationFailure() {
        _state.value = _state.value.copy(allocationFailed = true)
    }

    /** Clears an allocation failure at the start of a new IME session. Low-RAM status persists. */
    fun resetForNewSession() {
        _state.value = _state.value.copy(allocationFailed = false)
    }

    /** Test seam: restores the pristine state. */
    fun resetForTesting() {
        _state.value = GlideTypingCapabilityState()
    }
}

/**
 * Immutable capability snapshot. Low RAM outranks an allocation failure because it is the more
 * specific and more durable explanation.
 */
data class GlideTypingCapabilityState(
    val isLowRamDevice: Boolean = false,
    val allocationFailed: Boolean = false,
) {
    val unavailableReason: GlideTypingUnavailableReason?
        get() = when {
            isLowRamDevice -> GlideTypingUnavailableReason.LowRamDevice
            allocationFailed -> GlideTypingUnavailableReason.AllocationFailed
            else -> null
        }

    val isAvailable: Boolean
        get() = unavailableReason == null
}
