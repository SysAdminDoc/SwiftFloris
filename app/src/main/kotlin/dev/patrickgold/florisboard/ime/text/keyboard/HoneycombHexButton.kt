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

package dev.patrickgold.florisboard.ime.text.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.sp

/**
 * ROADMAP §8 L9.2 — single-cell composable for honeycomb-tiled
 * keyboards. Sibling of the existing `TextKeyButton` but with a
 * hex-shaped backdrop (via [HoneycombHexShape]) instead of a rounded
 * rectangle.
 *
 * Intentionally minimal — defers theming (Snygg integration), input-
 * feedback hooks, popup rendering, and long-press preview-bubble work
 * to the future TextKeyboardLayout call-site wire-up. This composable
 * is the standalone widget the renderer can drop into a
 * `HoneycombTessellation` grid for the initial implementation.
 *
 * Adheres to the global "no pill / oval / fully-rounded backdrop"
 * rule: the backdrop is an actual hexagon (six straight edges, six
 * 60° vertices), not a circle or capsule.
 *
 * @param label what to render at the centre of the hex.
 * @param onTap fires on a single-tap release.
 * @param onLongPress fires when the pointer is held past the system
 *        long-press threshold.
 * @param modifier outer modifier — the caller supplies width/height
 *        + position; the hex inscribes into whatever box it gets.
 * @param backgroundColor backdrop fill when idle.
 * @param pressedBackgroundColor backdrop fill while the pointer is
 *        down. Snygg-themed values flow through this once the
 *        TextKeyboardLayout integration lands.
 * @param textColor label foreground.
 */
@Composable
fun HoneycombHexButton(
    label: String,
    onTap: () -> Unit,
    onLongPress: () -> Unit = {},
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF2A2D40),
    pressedBackgroundColor: Color = Color(0xFF3D4159),
    textColor: Color = Color(0xFFE6E6F0),
) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(HoneycombHexShape)
            .background(if (pressed) pressedBackgroundColor else backgroundColor)
            .pointerInput(label) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        try {
                            tryAwaitRelease()
                        } finally {
                            pressed = false
                        }
                    },
                    onTap = { onTap() },
                    onLongPress = { onLongPress() },
                )
            }
            .fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontSize = 16.sp, color = textColor)
    }
}
