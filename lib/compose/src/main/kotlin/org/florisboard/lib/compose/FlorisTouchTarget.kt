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

package org.florisboard.lib.compose

import androidx.compose.ui.unit.dp

/**
 * The minimum size of an interactive target in the shared widgets.
 *
 * WCAG 2.5.5 (AAA) and the Android accessibility guidance both put the floor
 * at 48 dp, which is also what Material 3's
 * [androidx.compose.material3.minimumInteractiveComponentSize] enforces. 44 dp
 * is the iOS figure and does not satisfy either; use this constant rather than
 * a literal so the floor stays in one place.
 */
object FlorisTouchTarget {
    val MinSize = 48.dp
}

/**
 * Shared surface tokens for the neutral hairlines that separate and outline
 * content.
 *
 * Every divider and outlined-container border in the product draws
 * `outlineVariant` at reduced opacity. Those reductions had drifted to five
 * different values across the shared cards, the app bar, the step layout, the
 * home screen and the extension screens, so the same visual role rendered at a
 * different weight depending on which screen you were looking at. One token
 * keeps them in step, and makes a future contrast change a single edit.
 */
object FlorisSurfaceTokens {
    /** Opacity applied to `outlineVariant` for dividers and container borders. */
    const val HairlineAlpha = 0.56f
}
