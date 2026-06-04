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

package dev.patrickgold.florisboard.ime.theme

enum class PerAppAccentDiscoveryHintState {
    COLLECTING,
    READY,
    DISMISSED,
}

/**
 * Tracks the F6 discovery threshold without persisting package names.
 *
 * The distinct package set is intentionally process-local. Persisted state is
 * limited to the terminal hint state, so SwiftFloris does not add a stored
 * history of apps where the IME was used just to show an onboarding prompt.
 */
class PerAppAccentDiscoveryHintTracker(
    private val appPackageName: String,
    private val requiredDistinctPackages: Int = RequiredDistinctPackages,
) {
    private val observedPackages = linkedSetOf<String>()

    fun observe(
        packageName: String?,
        state: PerAppAccentDiscoveryHintState,
        perAppAccentEnabled: Boolean,
    ): PerAppAccentDiscoveryHintState {
        if (perAppAccentEnabled) return PerAppAccentDiscoveryHintState.DISMISSED
        if (state != PerAppAccentDiscoveryHintState.COLLECTING) return state

        val normalizedPackageName = packageName
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it != appPackageName }
            ?: return state

        observedPackages.add(normalizedPackageName)
        return if (observedPackages.size >= requiredDistinctPackages) {
            PerAppAccentDiscoveryHintState.READY
        } else {
            state
        }
    }

    companion object {
        const val RequiredDistinctPackages = 3
    }
}
