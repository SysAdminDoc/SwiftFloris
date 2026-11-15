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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Constraints
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
import dev.patrickgold.florisboard.app.settings.SettingsHomeDashboardTag
import dev.patrickgold.florisboard.app.settings.search.SettingsSearchScreen
import dev.patrickgold.jetpref.datastore.ui.ProvideDefaultDialogPrefStrings
import org.florisboard.lib.compose.FlorisEmptyState
import org.florisboard.lib.compose.FlorisErrorCard
import org.florisboard.lib.compose.FlorisProgressCard
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.florisboard.lib.compose.stringRes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.jupiter.api.DisplayName
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
@DisplayName("Roborazzi capture: settings regression matrix")
class RoborazziSettingsRegressionMatrixCaptureTest {
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
    fun homeScreenAutoAmoledUnderSystemLight() {
        // Robolectric runs with the system in light mode, so this must look like home_compact_light.
        capture(
            fileName = "home_auto_amoled_system_light.png",
            theme = AppTheme.AUTO_AMOLED,
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
@DisplayName("Roborazzi capture: wide landscape settings")
class RoborazziWideLandscapeSettingsCaptureTest {
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

/**
 * The settings home dashboard at 200 percent text on narrow phones. The window is tall so the
 * whole dashboard is on screen for the capture.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h2800dp-hdpi", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@DisplayName("Roborazzi capture: settings home at 200 percent text")
class RoborazziSettingsHomeLargeTextCaptureTest {
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
    fun width320LightLtr() = checkHome(320.dp, AppTheme.LIGHT, LayoutDirection.Ltr)

    @Test
    fun width320LightRtl() = checkHome(320.dp, AppTheme.LIGHT, LayoutDirection.Rtl)

    @Test
    fun width320DarkLtr() = checkHome(320.dp, AppTheme.DARK, LayoutDirection.Ltr)

    @Test
    fun width320DarkRtl() = checkHome(320.dp, AppTheme.DARK, LayoutDirection.Rtl)

    @Test
    fun width360LightLtr() = checkHome(360.dp, AppTheme.LIGHT, LayoutDirection.Ltr)

    @Test
    fun width360LightRtl() = checkHome(360.dp, AppTheme.LIGHT, LayoutDirection.Rtl)

    @Test
    fun width360DarkLtr() = checkHome(360.dp, AppTheme.DARK, LayoutDirection.Ltr)

    @Test
    fun width360DarkRtl() = checkHome(360.dp, AppTheme.DARK, LayoutDirection.Rtl)

    private fun checkHome(width: Dp, theme: AppTheme, layoutDirection: LayoutDirection) {
        composeRule.setContent {
            SettingsRegressionFrame(
                theme = theme,
                width = width,
                height = 2800.dp,
                layoutDirection = layoutDirection,
                fontScale = 2f,
            ) {
                HomeScreen()
            }
        }
        composeRule.waitForIdle()

        val dashboard = composeRule.onNodeWithTag(SettingsHomeDashboardTag).fetchSemanticsNode()
        val window = composeRule.onRoot().fetchSemanticsNode()
        assertTrue(
            "the dashboard runs past the window, so the capture would cut it off",
            dashboard.boundsInRoot.bottom <= window.boundsInRoot.bottom,
        )

        val texts = composeRule.onAllNodes(
            hasAnyAncestor(hasTestTag(SettingsHomeDashboardTag)) and
                SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        assertTrue("no text found in the dashboard", texts.size > 10)
        texts.forEach { node ->
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            val layout = layouts.single()
            val input = layout.layoutInput
            val label = input.text.text
            // Semantics lays a plain-string Text out again at the full width it was offered,
            // so neither its overflow flag nor its line widths say what fit in the box the
            // label got. Laying it out in that box, with its own line limit and overflow,
            // does what the screen did.
            val inBox = TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection, cacheSize = 0)
                .measure(
                    text = input.text,
                    style = input.style,
                    overflow = input.overflow,
                    softWrap = input.softWrap,
                    maxLines = input.maxLines,
                    constraints = Constraints(maxWidth = layout.size.width),
                )
            assertFalse(
                "\"$label\" is cut off: box ${layout.size}, needs ${inBox.multiParagraph.width} x " +
                    "${inBox.multiParagraph.height} in ${inBox.lineCount} lines",
                inBox.hasVisualOverflow || inBox.size.height > layout.size.height,
            )
            assertFalse(
                "\"$label\" ends in an ellipsis",
                (0 until inBox.lineCount).any { inBox.isLineEllipsized(it) },
            )
        }

        listOf(
            R.string.settings__home__open_keyboard_settings,
            R.string.settings__search__placeholder,
            R.string.settings__home__section_typing,
            R.string.settings__home__section_personalization,
            R.string.settings__home__section_privacy_data,
            R.string.settings__home__section_advanced,
            R.string.settings__home__section_about,
            R.string.settings__home__quick_action_import,
            R.string.settings__home__quick_action_privacy,
            R.string.settings__home__quick_action_about,
        ).forEach { label ->
            val text = composeRule.activity.getString(label)
            composeRule.onAllNodes(
                hasAnyAncestor(hasTestTag(SettingsHomeDashboardTag)) and hasText(text) and hasClickAction(),
            ).onFirst().assertExists()
        }

        composeRule.onNodeWithTag(SettingsHomeDashboardTag).captureRoboImage(
            filePath = "src/test/snapshots/settings_regression_matrix/" +
                "home_font_scale_200_w${width.value.toInt()}_${theme.id}_${layoutDirection.name.lowercase()}.png",
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
