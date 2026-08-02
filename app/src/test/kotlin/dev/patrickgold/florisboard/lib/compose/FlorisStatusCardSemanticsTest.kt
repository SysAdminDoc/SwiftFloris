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

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.screenshot.RoborazziHostActivity
import org.florisboard.lib.compose.FlorisErrorCard
import org.florisboard.lib.compose.FlorisInfoCard
import org.florisboard.lib.compose.FlorisNeutralCard
import org.florisboard.lib.compose.FlorisProgressCard
import org.florisboard.lib.compose.FlorisSimpleCard
import org.florisboard.lib.compose.FlorisStatusSemantics
import org.florisboard.lib.compose.FlorisStatusSeverity
import org.florisboard.lib.compose.FlorisSuccessCard
import org.florisboard.lib.compose.FlorisWarningCard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Backup, restore, sync, dictionary and hardware-keyboard screens report asynchronous progress
 * through the shared status cards. These assertions pin the live-region semantics so a state
 * change that is visible is also announced — and so static copy never becomes a live region.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi", sdk = [35])
class FlorisStatusCardSemanticsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<RoborazziHostActivity>()

    @Test
    fun severityMappingOptsInOnlyTransitionalStates() {
        assertEquals(LiveRegionMode.Polite, FlorisStatusSemantics.liveRegionFor(FlorisStatusSeverity.Progress))
        assertEquals(LiveRegionMode.Polite, FlorisStatusSemantics.liveRegionFor(FlorisStatusSeverity.Success))
        assertEquals(LiveRegionMode.Polite, FlorisStatusSemantics.liveRegionFor(FlorisStatusSeverity.Warning))
        assertEquals(LiveRegionMode.Assertive, FlorisStatusSemantics.liveRegionFor(FlorisStatusSeverity.Error))
        assertNull(FlorisStatusSemantics.liveRegionFor(FlorisStatusSeverity.Info))
        assertNull(FlorisStatusSemantics.liveRegionFor(FlorisStatusSeverity.Neutral))
    }

    @Test
    fun progressSuccessAndWarningCardsAnnouncePolitely() {
        composeRule.setContent {
            MaterialTheme {
                Surface {
                    Column {
                        FlorisProgressCard(modifier = Modifier.testTag(TAG_PROGRESS), text = "Backing up")
                        FlorisSuccessCard(modifier = Modifier.testTag(TAG_SUCCESS), text = "Backup complete")
                        FlorisWarningCard(modifier = Modifier.testTag(TAG_WARNING), text = "Partially restored")
                    }
                }
            }
        }

        composeRule.onNodeWithTag(TAG_PROGRESS).assertLiveRegion(LiveRegionMode.Polite)
        composeRule.onNodeWithTag(TAG_SUCCESS).assertLiveRegion(LiveRegionMode.Polite)
        composeRule.onNodeWithTag(TAG_WARNING).assertLiveRegion(LiveRegionMode.Polite)
    }

    @Test
    fun errorCardInterruptsSoAFailedOperationIsNeverSilent() {
        composeRule.setContent {
            MaterialTheme {
                Surface {
                    FlorisErrorCard(modifier = Modifier.testTag(TAG_ERROR), text = "Restore failed")
                }
            }
        }

        composeRule.onNodeWithTag(TAG_ERROR).assertLiveRegion(LiveRegionMode.Assertive)
    }

    @Test
    fun staticCopyIsNotALiveRegion() {
        composeRule.setContent {
            MaterialTheme {
                Surface {
                    Column {
                        FlorisInfoCard(modifier = Modifier.testTag(TAG_INFO), text = "Backups stay on this device")
                        FlorisNeutralCard(modifier = Modifier.testTag(TAG_NEUTRAL), text = "Sync is off")
                        FlorisSimpleCard(modifier = Modifier.testTag(TAG_SIMPLE), text = "Learned entries")
                    }
                }
            }
        }

        composeRule.onNodeWithTag(TAG_INFO).assertNoLiveRegion()
        composeRule.onNodeWithTag(TAG_NEUTRAL).assertNoLiveRegion()
        composeRule.onNodeWithTag(TAG_SIMPLE).assertNoLiveRegion()
    }

    @Test
    fun perKeystrokeSurfacesCanOptOutExplicitly() {
        composeRule.setContent {
            MaterialTheme {
                Surface {
                    FlorisProgressCard(
                        modifier = Modifier.testTag(TAG_PROGRESS),
                        text = "Loading candidates",
                        liveRegion = null,
                    )
                }
            }
        }

        composeRule.onNodeWithTag(TAG_PROGRESS).assertNoLiveRegion()
    }

    @Test
    fun progressToSuccessTransitionLeavesExactlyOneAnnouncingNode() {
        var finished by mutableStateOf(false)
        composeRule.setContent {
            MaterialTheme {
                Surface {
                    if (finished) {
                        FlorisSuccessCard(modifier = Modifier.testTag(TAG_SUCCESS), text = "Backup complete")
                    } else {
                        FlorisProgressCard(modifier = Modifier.testTag(TAG_PROGRESS), text = "Backing up")
                    }
                }
            }
        }

        assertEquals(1, composeRule.liveRegionNodeCount())

        finished = true
        composeRule.waitForIdle()

        assertEquals(1, composeRule.liveRegionNodeCount())
        composeRule.onNodeWithTag(TAG_SUCCESS).assertLiveRegion(LiveRegionMode.Polite)
    }

    @Test
    fun recompositionWithUnchangedStatusDoesNotAddAnnouncingNodes() {
        var unrelated by mutableStateOf(0)
        composeRule.setContent {
            MaterialTheme {
                Surface {
                    FlorisProgressCard(
                        modifier = Modifier.testTag(TAG_PROGRESS),
                        text = "Backing up",
                        secondaryText = "step $unrelated of 1",
                    )
                }
            }
        }

        assertEquals(1, composeRule.liveRegionNodeCount())

        repeat(3) {
            unrelated = 0
            composeRule.waitForIdle()
        }

        assertEquals(1, composeRule.liveRegionNodeCount())
    }

    private companion object {
        const val TAG_PROGRESS = "status-progress"
        const val TAG_SUCCESS = "status-success"
        const val TAG_WARNING = "status-warning"
        const val TAG_ERROR = "status-error"
        const val TAG_INFO = "status-info"
        const val TAG_NEUTRAL = "status-neutral"
        const val TAG_SIMPLE = "status-simple"
    }
}

private fun SemanticsNodeInteraction.assertLiveRegion(mode: LiveRegionMode) {
    assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, mode))
}

private fun SemanticsNodeInteraction.assertNoLiveRegion() {
    assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
}

private fun ComposeTestRule.liveRegionNodeCount(): Int {
    return onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
        .fetchSemanticsNodes()
        .size
}
