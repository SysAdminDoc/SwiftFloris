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

package dev.patrickgold.florisboard.ime.window

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.screenshot.RoborazziHostActivity
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import org.florisboard.lib.compose.FlorisChip
import org.florisboard.lib.compose.FlorisIconButton
import org.florisboard.lib.compose.FlorisTextButton
import org.florisboard.lib.compose.FlorisTouchTarget
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.annotation.Config

/**
 * WCAG 2.5.5 and the Android guidance both put the interactive floor at 48 dp.
 * [TouchTargetWcagTest] covers the keyboard rows, which are laid out from the
 * window spec; the shared Settings widgets are laid out by Compose and are
 * measured here.
 *
 * Note what each half of this class is sensitive to, because they are not the
 * same thing. The rendered-height cases guard the geometry a user actually
 * touches, but Material 3 raises its own components to 48 dp through
 * `LocalMinimumInteractiveComponentEnforcement` regardless of what
 * [FlorisTouchTarget.MinSize] says, so they keep passing if the constant
 * regresses and only fail if a widget stops being a Material component or the
 * enforcement is switched off. The constant case is the one that fails when
 * the declared floor is lowered. Both are needed; neither replaces the other.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SharedWidgetTouchTargetTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<RoborazziHostActivity>()

    private fun assertMeetsFloor(content: @Composable () -> Unit) {
        composeRule.setContent {
            MaterialTheme {
                Surface {
                    content()
                }
            }
        }

        val height = with(composeRule.density) {
            composeRule.onNodeWithTag(TargetTag).fetchSemanticsNode().size.height.toDp()
        }
        height shouldBeGreaterThanOrEqualTo FlorisTouchTarget.MinSize
    }

    @Test
    fun `text button meets the interactive floor`() {
        assertMeetsFloor {
            FlorisTextButton(
                modifier = Modifier.testTag(TargetTag),
                onClick = {},
                text = "A",
            )
        }
    }

    @Test
    fun `chip meets the interactive floor`() {
        assertMeetsFloor {
            FlorisChip(
                modifier = Modifier.testTag(TargetTag),
                onClick = {},
                text = "A",
            )
        }
    }

    @Test
    fun `icon button meets the interactive floor`() {
        assertMeetsFloor {
            FlorisIconButton(
                modifier = Modifier.testTag(TargetTag),
                onClick = {},
                content = {},
            )
        }
    }

    @Test
    fun `the shared floor is the android minimum rather than the ios one`() {
        FlorisTouchTarget.MinSize shouldBeGreaterThanOrEqualTo 48.dp
    }
}

private const val TargetTag = "touch-target-under-test"
