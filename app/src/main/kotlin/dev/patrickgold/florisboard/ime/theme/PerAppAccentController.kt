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

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.profile.PerAppKeyboardProfiles
import dev.patrickgold.florisboard.ime.profile.PerAppThemeOverride
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ROADMAP §7 Next-11.3a — runtime controller that owns the active per-app
 * accent color [StateFlow] for the IME process. Compose code consumes the
 * flow via [LocalPerAppAccent]; non-Compose call-sites can read
 * [activeAccent] directly.
 *
 * Pipeline:
 *  1. [FlorisImeService.onStartInputView] (or any other editor-focus surface)
 *     calls [setActiveEditorPackage(pkg)].
 *  2. The controller checks the user's `prefs.theme.perAppAccentEnabled`
 *     toggle. If disabled, [activeAccent] is forced to `null` regardless of
 *     the package — surface consumers fall back to the active Snygg theme's
 *     `--primary` token.
 *  3. If enabled, the controller calls [PerAppAccentResolver.accentFor(pkg)]
 *     to extract a Color (cached per-package, LRU(64)) and publishes the
 *     result to [activeAccent].
 *
 * Consumers (any Compose surface that wants the per-app tint):
 *  - `CompositionLocalProvider(LocalPerAppAccent provides ...)` is supplied
 *    by `ImeRootView` so the entire IME compose tree can opt-in.
 *  - A nullable Color value of `null` means "no per-app accent active",
 *    which always falls through to the theme's primary token.
 */
class PerAppAccentController(context: Context) {

    private val appContext: Context = context.applicationContext
    private val resolver = PerAppAccentResolver(appContext)
    private val discoveryHintTracker = PerAppAccentDiscoveryHintTracker(appContext.packageName)
    private val prefs by FlorisPreferenceStore
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _activeAccent = MutableStateFlow<Color?>(null)
    val activeAccent: StateFlow<Color?> = _activeAccent.asStateFlow()

    @Volatile
    private var lastPackageName: String? = null

    /**
     * Update the active editor package. Synchronous + cheap: the resolver
     * caches per-package results in an LRU(64), so repeated focus-flicker on
     * the same editor is a hash lookup. The first focus per cold-package is
     * a ~1-3 ms icon raster + HSV scan — still fine for the IME hot path
     * because `onStartInputView` only fires on field-focus, not per
     * keystroke.
     */
    fun setActiveEditorPackage(packageName: String?) {
        val perAppAccentEnabled = prefs.theme.perAppAccentEnabled.get()
        updateDiscoveryHint(packageName, perAppAccentEnabled)
        val profileTheme = PerAppKeyboardProfiles.resolve(
            rawJson = prefs.privacy.perAppKeyboardProfiles.get(),
            packageName = packageName,
        )?.theme
        val accentEnabledForPackage = when (profileTheme) {
            PerAppThemeOverride.ADAPTIVE_ACCENT -> true
            PerAppThemeOverride.GLOBAL_ACCENT -> false
            else -> perAppAccentEnabled
        }

        // Skip when the user has the feature off entirely. Cheap guard
        // against doing the icon-load work for nothing.
        if (!accentEnabledForPackage) {
            if (_activeAccent.value != null) _activeAccent.value = null
            lastPackageName = packageName
            return
        }
        // Same package as last invocation — preserve current accent (cache
        // hit is cheap, but emitting on the same value is a no-op anyway).
        if (packageName == lastPackageName && _activeAccent.value != null) return
        lastPackageName = packageName
        _activeAccent.value = resolver.accentFor(packageName)
    }

    /** Drop every cached entry. Use during low-memory callbacks. */
    fun onLowMemory() {
        resolver.invalidateAll()
    }

    private fun updateDiscoveryHint(packageName: String?, perAppAccentEnabled: Boolean) {
        val currentState = prefs.theme.perAppAccentDiscoveryHintState.get()
        val nextState = discoveryHintTracker.observe(
            packageName = packageName,
            state = currentState,
            perAppAccentEnabled = perAppAccentEnabled,
        )
        if (nextState != currentState) {
            scope.launch {
                prefs.theme.perAppAccentDiscoveryHintState.set(nextState)
            }
        }
    }
}

/**
 * Compose `CompositionLocal` for the active per-app accent color. `null` =
 * fall through to the active Snygg theme's `--primary` token. Provided at
 * the root of the IME compose tree (`ImeRootView`); consumers throughout
 * the smartbar / suggestion strip / keyboard can read it freely.
 */
val LocalPerAppAccent = compositionLocalOf<Color?> { null }
