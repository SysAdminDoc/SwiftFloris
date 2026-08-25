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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.patrickgold.florisboard.app.apptheme.errorDark
import dev.patrickgold.florisboard.app.apptheme.errorLight

/**
 * Last-resort foreground for validation errors drawn on an IME surface whose
 * Snygg element declares no `foreground` of its own.
 *
 * A fixed literal cannot work here. None of the bundled stylesheets define
 * `media-emoji-pin-sheet-error`, and a user theme is free to skip it too, so
 * whatever sits in the `default` argument is what people actually read. A
 * dark-scheme tone hardcoded there is invisible on a light keyboard, which is
 * exactly the state the string is trying to escape.
 *
 * Choosing against the resolved [background] instead keeps the message legible
 * on bundled light themes, bundled dark themes, and custom ones nobody has
 * written yet. The two tones are the Material 3 error roles the Settings
 * palette already uses, so an error looks the same wherever it appears.
 */
fun snyggErrorForegroundFor(background: Color): Color {
    return if (background.luminance() >= LightSurfaceLuminanceThreshold) errorLight else errorDark
}

/**
 * Matches the split [dev.patrickgold.florisboard.ime.window.ImeSystemUi] uses to
 * decide light-vs-dark system bar icons, so one keyboard never disagrees with
 * itself about which kind of surface it is drawing on.
 */
private const val LightSurfaceLuminanceThreshold = 0.5f
