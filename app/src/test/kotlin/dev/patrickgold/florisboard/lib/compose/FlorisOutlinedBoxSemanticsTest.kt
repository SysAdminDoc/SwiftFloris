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

package dev.patrickgold.florisboard.lib.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.screenshot.RoborazziHostActivity
import org.florisboard.lib.compose.FlorisOutlinedBox
import org.florisboard.lib.compose.FlorisTouchTarget
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [FlorisOutlinedBox] backs 90+ settings surfaces. Its title and subtitle are
 * optionally clickable, so the semantics have to say which of the two a given
 * box is: a decorative caption must not reach TalkBack as a control at all,
 * and a navigating caption must announce as a button the user can reach.
 *
 * Both directions are pinned here because the failure modes are silent — a
 * decorative title that emits a disabled click action is announced as an
 * unusable button, and a real navigation target with no role is announced as
 * plain text.
 */
@RunWith(AndroidJUnit4::class)
class FlorisOutlinedBoxSemanticsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<RoborazziHostActivity>()

    @Test
    fun decorativeTitleAndSubtitleAreNotAnnouncedAsControls() {
        composeRule.setContent {
            MaterialTheme {
                Surface {
                    FlorisOutlinedBox(
                        title = DECORATIVE_TITLE,
                        subtitle = DECORATIVE_SUBTITLE,
                    ) {
                        Text(text = BODY, modifier = Modifier.testTag(TAG_BODY))
                    }
                }
            }
        }

        composeRule.onNodeWithText(DECORATIVE_TITLE).assertHasNoClickAction()
        composeRule.onNodeWithText(DECORATIVE_SUBTITLE).assertHasNoClickAction()
    }

    @Test
    fun clickableTitleAnnouncesAsAButton() {
        composeRule.setContent {
            MaterialTheme {
                Surface {
                    FlorisOutlinedBox(
                        title = CLICKABLE_TITLE,
                        onTitleClick = {},
                    ) {
                        Text(text = BODY, modifier = Modifier.testTag(TAG_BODY))
                    }
                }
            }
        }

        composeRule.onNodeWithText(CLICKABLE_TITLE)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun clickableSubtitleAnnouncesAsAButton() {
        composeRule.setContent {
            MaterialTheme {
                Surface {
                    FlorisOutlinedBox(
                        title = CLICKABLE_TITLE,
                        subtitle = CLICKABLE_SUBTITLE,
                        onSubtitleClick = {},
                    ) {
                        Text(text = BODY, modifier = Modifier.testTag(TAG_BODY))
                    }
                }
            }
        }

        composeRule.onNodeWithText(CLICKABLE_SUBTITLE)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun clickableCaptionsMeetTheSharedTouchTargetFloor() {
        composeRule.setContent {
            MaterialTheme {
                Surface {
                    FlorisOutlinedBox(
                        title = CLICKABLE_TITLE,
                        onTitleClick = {},
                        subtitle = CLICKABLE_SUBTITLE,
                        onSubtitleClick = {},
                    ) {
                        Text(text = BODY, modifier = Modifier.testTag(TAG_BODY))
                    }
                }
            }
        }

        composeRule.onNodeWithText(CLICKABLE_TITLE)
            .assertHeightIsAtLeast(FlorisTouchTarget.MinSize)
        composeRule.onNodeWithText(CLICKABLE_SUBTITLE)
            .assertHeightIsAtLeast(FlorisTouchTarget.MinSize)
    }

    private companion object {
        const val DECORATIVE_TITLE = "Decorative caption"
        const val DECORATIVE_SUBTITLE = "decorative.subtitle"
        const val CLICKABLE_TITLE = "Navigating caption"
        const val CLICKABLE_SUBTITLE = "navigating.subtitle"
        const val BODY = "Box body"
        const val TAG_BODY = "outlined-box-body"
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertHasNoClickAction() {
    assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
}
