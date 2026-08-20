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

package dev.patrickgold.florisboard.app.settings.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.screenshot.RoborazziHostActivity
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsSearchRowScrollTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<RoborazziHostActivity>()

    @Before
    fun setUp() {
        SettingsSearchHighlightStore.clear()
    }

    @After
    fun tearDown() {
        SettingsSearchHighlightStore.clear()
    }

    @Test
    fun matchingRowRequestsScrollAndClearsPendingCardTarget() {
        SettingsSearchHighlightStore.mark(
            entry = SettingsSearchEntry(
                id = "keyboard.row",
                screenTitleResId = R.string.settings__title,
                titleResId = R.string.settings__keyboard__title,
                destination = SettingsSearchDestination.KEYBOARD,
            ),
            query = "keyboard",
            resolveString = ::resolve,
        )

        var scrollValue = 0
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalSettingsSearchScreenTitle provides "Settings") {
                    SearchScrollFixture { value -> scrollValue = value }
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            SettingsSearchHighlightStore.activeTarget == null
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings-search-target").assertIsDisplayed()
        assertTrue(scrollValue > 0, "Expected the target row to move the scroll state, got $scrollValue")
    }
}

@Composable
private fun SearchScrollFixture(onScroll: (Int) -> Unit) {
    val scrollState = rememberScrollState()
    val currentScrollValue = scrollState.value
    SideEffect { onScroll(currentScrollValue) }
    CompositionLocalProvider(LocalSettingsSearchScrollState provides scrollState) {
        Column(
            modifier = Modifier
                .height(200.dp)
                .verticalScroll(scrollState),
        ) {
            Spacer(Modifier.height(640.dp))
            Box(
                modifier = Modifier
                    .settingsSearchRow("Keyboard")
                    .testTag("settings-search-target")
                    .fillMaxWidth()
                    .height(56.dp),
            )
        }
    }
}

private fun resolve(resId: Int): String = when (resId) {
    R.string.settings__title -> "Settings"
    R.string.settings__keyboard__title -> "Keyboard"
    else -> "test-$resId"
}
