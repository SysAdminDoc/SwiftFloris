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

package dev.patrickgold.florisboard.screenshot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureRoboImage
import dev.patrickgold.florisboard.ime.text.gestures.GlideTrailTheme
import dev.patrickgold.florisboard.ime.text.keyboard.HoneycombKeyboardRow
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ROADMAP TODO F40 test-class phase.
 *
 * Disabled until the Tier B capture phase records baseline PNGs. The active
 * compile target protects the future visual surfaces from API drift while
 * keeping `verifyRoborazziDebug` green until those baselines are committed.
 */
@Ignore("F40 baseline capture is tracked separately in TODO Tier B.")
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PendingKeyboardSurfacesScreenshotTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<RoborazziHostActivity>()

    @get:Rule
    val roborazziRule = RoborazziRule(
        options = RoborazziRule.Options(
            outputDirectoryPath = "build/outputs/roborazzi",
        ),
    )

    @Test
    fun honeycombKeyboardSurface() {
        captureKeyboardSurface("honeycomb_keyboard_surface.png") {
            HoneycombKeyboardPreview()
        }
    }

    @Test
    fun glideTrailThemesSurface() {
        captureKeyboardSurface("glide_trail_themes_surface.png") {
            GlideTrailThemesPreview()
        }
    }

    private fun captureKeyboardSurface(fileName: String, content: @Composable () -> Unit) {
        composeRule.setContent {
            KeyboardScreenshotFrame(content = content)
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            filePath = "$BASELINE_DIR/$fileName",
            roborazziOptions = ROBORAZZI_OPTIONS,
        )
    }

    private companion object {
        const val BASELINE_DIR = "src/test/snapshots/pending_keyboard_surfaces"

        val ROBORAZZI_OPTIONS = RoborazziOptions(
            compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
        )
    }
}

@Composable
private fun KeyboardScreenshotFrame(content: @Composable () -> Unit) {
    MaterialTheme {
        Surface(color = Color(0xFF101318), contentColor = Color(0xFFECEFF4)) {
            Box(
                modifier = Modifier
                    .size(width = 360.dp, height = 360.dp)
                    .padding(12.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun HoneycombKeyboardPreview() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Honeycomb keyboard surface",
            style = MaterialTheme.typography.titleMedium,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(252.dp)
                .background(Color(0xFF161A22)),
            contentAlignment = Alignment.TopCenter,
        ) {
            HoneycombKeyboardRow(
                rowLabels = listOf(
                    listOf("Q", "W", "E", "R", "T", "Y"),
                    listOf("A", "S", "D", "F", "G"),
                    listOf("Z", "X", "C", "V", "B", "N"),
                    listOf("123", "space", ".", "go"),
                ),
                onKeyTap = { _, _, _ -> },
                modifier = Modifier
                    .padding(top = 18.dp)
                    .size(width = 320.dp, height = 218.dp),
                keyRadiusDp = 24.dp,
            )
        }
    }
}

@Composable
private fun GlideTrailThemesPreview() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Glide trail theme ramps",
            style = MaterialTheme.typography.titleMedium,
        )
        for (theme in GlideTrailTheme.entries) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = theme.name.lowercase().replaceFirstChar { it.uppercase() },
                    modifier = Modifier.width(74.dp),
                    color = Color(0xFFECEFF4),
                    fontSize = 11.sp,
                )
                GlideTrailRamp(theme = theme)
            }
        }
    }
}

@Composable
private fun GlideTrailRamp(theme: GlideTrailTheme) {
    val accentColor = Color(0xFF88C0D0)
    val timeMillis = 12_345L
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp),
    ) {
        val left = 4.dp.toPx()
        val right = size.width - 4.dp.toPx()
        val centerY = size.height / 2f
        val steps = 14
        for (step in 0 until steps) {
            val startProgress = step / steps.toFloat()
            val endProgress = (step + 1) / steps.toFloat()
            val start = Offset(
                x = left + (right - left) * startProgress,
                y = centerY + if (step % 2 == 0) -2.dp.toPx() else 2.dp.toPx(),
            )
            val end = Offset(
                x = left + (right - left) * endProgress,
                y = centerY + if (step % 2 == 0) 2.dp.toPx() else -2.dp.toPx(),
            )
            drawLine(
                color = theme.colorAt(endProgress, timeMillis, accentColor),
                start = start,
                end = end,
                strokeWidth = 7.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}
