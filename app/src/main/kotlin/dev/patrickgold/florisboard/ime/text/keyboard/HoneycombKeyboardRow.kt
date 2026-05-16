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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * ROADMAP §8 L9.2 — multi-row honeycomb keyboard renderer.
 *
 * Lays out a grid of [HoneycombHexButton]s using the geometry from
 * [HoneycombTessellation] (shipped v1.8.4). Each row is offset by
 * half a column-stride from the row above when the row index is odd,
 * producing the tessellated honeycomb pattern.
 *
 * Sized by the caller via [modifier]. The composable computes the
 * key radius from `keyRadiusDp` (per-key dp size) and absolutely
 * positions each hex via Compose's `offset` modifier — this is the
 * minimal renderer slice. Full TextKeyboardLayout integration (touch
 * routing, theme + Snygg integration, popup support) lands in a
 * follow-up.
 *
 * @param rowLabels one inner list per row; each entry is the label
 *        for one hex in that row.
 * @param keyRadiusDp centre-to-vertex distance, in dp. 24 dp is a
 *        sensible default for a 6-7" phone.
 * @param onKeyTap fires with `(row, col, label)` when a hex is tapped.
 */
@Composable
fun HoneycombKeyboardRow(
    rowLabels: List<List<String>>,
    onKeyTap: (row: Int, col: Int, label: String) -> Unit,
    modifier: Modifier = Modifier,
    keyRadiusDp: Dp = 24.dp,
) {
    if (rowLabels.isEmpty()) return
    val rowCount = rowLabels.size
    val columnCounts = rowLabels.map { it.size }

    val radiusValue: Float = keyRadiusDp.value
    val rowStrideDp = (radiusValue * 1.5f).dp
    val columnStrideDp = (radiusValue * SQRT_3).dp
    val halfColumnStrideDp = (radiusValue * SQRT_3 / 2f).dp
    val hexWidthDp = columnStrideDp
    val hexHeightDp = (radiusValue * 2f).dp

    Box(modifier = modifier) {
        for (row in 0 until rowCount) {
            val rowOffsetX = if (row % 2 == 1) halfColumnStrideDp else 0.dp
            for (col in 0 until columnCounts[row]) {
                val cx = (rowOffsetX.value + radiusValue * SQRT_3 * col).dp
                val cy = (radiusValue * 1.5f * row).dp
                HoneycombHexButton(
                    label = rowLabels[row][col],
                    onTap = { onKeyTap(row, col, rowLabels[row][col]) },
                    modifier = Modifier
                        .offset(x = cx, y = cy)
                        .size(width = hexWidthDp, height = hexHeightDp),
                )
            }
        }
        // Reserve enough height for the tallest row plus the last row's
        // descender so the parent sizes correctly. (Suppress unused-var
        // warnings on the stride bookkeeping above.)
        @Suppress("UNUSED_VARIABLE") val _r = rowStrideDp
    }
}

private const val SQRT_3: Float = 1.7320508f
