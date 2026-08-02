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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziRule
import com.github.takahirom.roborazzi.captureRoboImage
import dev.patrickgold.florisboard.FlorisApplication
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.AppTheme
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.apptheme.FlorisAppTheme
import dev.patrickgold.florisboard.app.settings.HomeScreen
import dev.patrickgold.florisboard.app.settings.search.SettingsSearchScreen
import dev.patrickgold.jetpref.datastore.ui.ProvideDefaultDialogPrefStrings
import org.florisboard.lib.compose.FlorisEmptyState
import org.florisboard.lib.compose.FlorisErrorCard
import org.florisboard.lib.compose.FlorisProgressCard
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.florisboard.lib.compose.stringRes
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Headless layout matrix for settings surfaces and shared asynchronous-state controls.
 *
 * These cases intentionally use the same frame and theme wrapper as the settings screenshot
 * suite, while varying dimensions, layout direction, and font scale so regressions are visible
 * in review instead of only on the default 360dp portrait fixture.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsRegressionMatrixScreenshotTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<RoborazziHostActivity>()

    @get:Rule
    val roborazziRule = RoborazziRule(
        options = RoborazziRule.Options(
            outputDirectoryPath = "build/outputs/roborazzi",
        ),
    )

    @Before
    fun setUp() {
        waitForPreferenceStore()
    }

    @Test
    fun homeScreenCompactLight() {
        capture("home_compact_light.png") {
            HomeScreen()
        }
    }

    @Test
    fun homeScreenRtlDark() {
        capture(
            fileName = "home_rtl_dark.png",
            theme = AppTheme.DARK,
            layoutDirection = LayoutDirection.Rtl,
        ) {
            HomeScreen()
        }
    }

    @Test
    fun settingsSearchAtTwoHundredPercentFontScale() {
        capture(
            fileName = "settings_search_font_scale_200.png",
            fontScale = 2f,
        ) {
            SettingsSearchScreen()
        }
    }

    @Test
    fun loadingErrorAndEmptyStatesExposeSharedSemantics() {
        composeRule.setContent {
            SettingsRegressionFrame {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FlorisProgressCard(
                        modifier = Modifier.testTag(TAG_LOADING),
                        text = "Loading backups",
                        secondaryText = "Reading local archive",
                    )
                    FlorisErrorCard(
                        modifier = Modifier.testTag(TAG_ERROR),
                        text = "Restore failed",
                        secondaryText = "The archive was not changed",
                        actionLabel = "Try again",
                        onClick = {},
                    )
                    FlorisEmptyState(
                        icon = Icons.Default.Inbox,
                        title = "No backups",
                        message = "Create an encrypted local backup to see it here.",
                        actionLabel = "Create backup",
                        onAction = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_LOADING).assertLiveRegion(LiveRegionMode.Polite)
        composeRule.onNodeWithTag(TAG_ERROR).assertLiveRegion(LiveRegionMode.Assertive)
        composeRule.onNodeWithTag(TAG_ERROR).assertHasClickAction()
        composeRule.onNodeWithText("No backups")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        composeRule.onNodeWithText("Create backup").assertHasClickAction()
        composeRule.onRoot().captureRoboImage(
            filePath = "$BASELINE_DIR/status_cards_loading_error_empty.png",
            roborazziOptions = ROBORAZZI_OPTIONS,
        )
    }

    private fun capture(
        fileName: String,
        theme: AppTheme = AppTheme.LIGHT,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        fontScale: Float = 1f,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            SettingsRegressionFrame(
                theme = theme,
                layoutDirection = layoutDirection,
                fontScale = fontScale,
                content = content,
            )
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            filePath = "$BASELINE_DIR/$fileName",
            roborazziOptions = ROBORAZZI_OPTIONS,
        )
    }

    private fun waitForPreferenceStore() {
        val app = composeRule.activity.application as FlorisApplication
        composeRule.waitUntil(timeoutMillis = 10_000) {
            app.preferenceStoreLoaded.value
        }
    }

    private companion object {
        const val BASELINE_DIR = "src/test/snapshots/settings_regression_matrix"
        const val TAG_LOADING = "regression-loading"
        const val TAG_ERROR = "regression-error"

        val ROBORAZZI_OPTIONS = RoborazziOptions(
            compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
        )
    }
}

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w640dp-h360dp-xxhdpi", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WideLandscapeSettingsScreenshotTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<RoborazziHostActivity>()

    @get:Rule
    val roborazziRule = RoborazziRule(
        options = RoborazziRule.Options(
            outputDirectoryPath = "build/outputs/roborazzi",
        ),
    )

    @Before
    fun setUp() {
        val app = composeRule.activity.application as FlorisApplication
        composeRule.waitUntil(timeoutMillis = 10_000) {
            app.preferenceStoreLoaded.value
        }
    }

    @Test
    fun homeScreenWideLandscape() {
        composeRule.setContent {
            SettingsRegressionFrame(
                width = 640.dp,
                height = 360.dp,
            ) {
                HomeScreen()
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            filePath = "src/test/snapshots/settings_regression_matrix/home_wide_landscape.png",
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
            ),
        )
    }
}

@Composable
private fun SettingsRegressionFrame(
    theme: AppTheme = AppTheme.LIGHT,
    width: Dp = 360.dp,
    height: Dp = 640.dp,
    layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    ProvideLocalizedResources(
        resourcesContext = LocalContext.current,
        appName = R.string.app_name,
    ) {
        FlorisAppTheme(theme = theme) {
            val density = LocalDensity.current
            val navController = rememberNavController()
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = fontScale),
                LocalLayoutDirection provides layoutDirection,
                LocalNavController provides navController,
            ) {
                ProvideDefaultDialogPrefStrings(
                    confirmLabel = stringRes(R.string.action__ok),
                    dismissLabel = stringRes(R.string.action__cancel),
                    neutralLabel = stringRes(R.string.action__default),
                ) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Box(modifier = Modifier.size(width = width, height = height)) {
                            content()
                        }
                    }
                }
            }
        }
    }
}

private fun SemanticsNodeInteraction.assertLiveRegion(mode: LiveRegionMode) {
    assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, mode))
}
