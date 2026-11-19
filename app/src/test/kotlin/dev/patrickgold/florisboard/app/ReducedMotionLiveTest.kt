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

package dev.patrickgold.florisboard.app

import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.apptheme.FlorisAppTheme
import dev.patrickgold.florisboard.ime.window.AuroraAnimatedThemeBackground
import dev.patrickgold.florisboard.ime.window.AuroraAnimatedThemeComponentId
import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName
import dev.patrickgold.florisboard.screenshot.RoborazziHostActivity
import dev.patrickgold.jetpref.datastore.ui.ProvideDefaultDialogPrefStrings
import io.kotest.assertions.withClue
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.florisboard.lib.compose.stringRes
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Android's Remove animations lives in Settings.Global, and changing it is no configuration change, so motion
 * keyed on the configuration kept going until the screen was recreated. These flip the setting while the
 * settings screens and the Aurora background stay up, and check motion stops at once and comes back, all in the
 * same activity. The settings motion also has to mirror in a right-to-left language, where it used to enter from
 * the right and shrink toward the right edge just like left-to-right.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi", sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReducedMotionLiveTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<RoborazziHostActivity>()

    @After
    fun tearDown() {
        removeAnimations(false)
    }

    @Test
    fun settingsNavigationStopsMovingAsSoonAsAnimationsAreRemoved() {
        val activity = composeRule.activity
        val nav = showSettings()

        withClue("with animations on, the next screen slides in from the right") {
            enteringShiftMidway(nav) shouldBeGreaterThan 1f
        }

        removeAnimations(true)
        withClue("right after Remove animations is turned on, the next screen is in place at once") {
            enteringShiftMidway(nav) shouldBe 0f
        }

        removeAnimations(false)
        withClue("turning it back off brings the slide back") {
            enteringShiftMidway(nav) shouldBeGreaterThan 1f
        }
        composeRule.activity shouldBeSameInstanceAs activity
    }

    @Test
    fun forwardNavigationEntersFromTheLeftInRightToLeft() {
        val nav = showSettings(LayoutDirection.Rtl)

        withClue("in a right-to-left language the next screen slides in from the left") {
            enteringShiftMidway(nav) shouldBeLessThan -1f
        }
    }

    @Test
    fun goingBackShrinksTowardTheRightInLeftToRight() {
        val nav = showSettings(LayoutDirection.Ltr)

        withClue("the screen being left shrinks toward its right edge") {
            exitingCenterShiftMidway(nav) shouldBeGreaterThan 1f
        }
    }

    @Test
    fun goingBackShrinksTowardTheLeftInRightToLeft() {
        val nav = showSettings(LayoutDirection.Rtl)

        withClue("in a right-to-left language the screen being left shrinks toward its left edge") {
            exitingCenterShiftMidway(nav) shouldBeLessThan -1f
        }
    }

    @Test
    fun theAuroraBackgroundStopsAndStartsWithTheSetting() {
        val activity = composeRule.activity
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            Box(modifier = Modifier.size(160.dp)) {
                AuroraAnimatedThemeBackground(
                    activeThemeName = ExtensionComponentName("org.florisboard.themes", AuroraAnimatedThemeComponentId),
                )
            }
        }
        composeRule.mainClock.advanceTimeByFrame()

        withClue("with animations on, the bands move") { movesOverASecond() shouldBe true }
        removeAnimations(true)
        withClue("with Remove animations on, the bands hold still") { movesOverASecond() shouldBe false }
        removeAnimations(false)
        withClue("turning it back off starts them again") { movesOverASecond() shouldBe true }
        composeRule.activity shouldBeSameInstanceAs activity
    }

    /**
     * Opens the language list from the privacy audit screen and reads how far its search field still is from
     * where it comes to rest, 48 ms in. Positive is to the right of that spot.
     */
    private fun enteringShiftMidway(nav: NavHostController): Float {
        composeRule.runOnIdle { nav.navigate(Routes.Settings.SelectLocale) }
        composeRule.mainClock.advanceTimeBy(48)
        val midway = field().left
        composeRule.mainClock.advanceTimeBy(3_000)
        val settled = field().left
        composeRule.runOnIdle { nav.popBackStack() }
        composeRule.mainClock.advanceTimeBy(3_000)
        return midway - settled
    }

    /** Opens the language list, goes back, and reads how far its search field's center moved 64 ms in. */
    private fun exitingCenterShiftMidway(nav: NavHostController): Float {
        composeRule.runOnIdle { nav.navigate(Routes.Settings.SelectLocale) }
        composeRule.mainClock.advanceTimeBy(3_000)
        val settled = field().center.x
        composeRule.runOnIdle { nav.popBackStack() }
        composeRule.mainClock.advanceTimeBy(64)
        val midway = field().center.x
        composeRule.mainClock.advanceTimeBy(3_000)
        return midway - settled
    }

    private fun field(): Rect = composeRule.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot

    private fun movesOverASecond(): Boolean {
        val before = composeRule.onRoot().captureToImage().toPixelMap().buffer.copyOf()
        composeRule.mainClock.advanceTimeBy(1_000)
        val after = composeRule.onRoot().captureToImage().toPixelMap().buffer
        return !before.contentEquals(after)
    }

    /**
     * Sets the animator duration scale the way the platform stores it, as a string, and sends the change
     * notification the platform sends. Robolectric keeps a putFloat value typed, so getString reads it back as
     * null, and it keeps observers per resolver, where ours sits on the application context's.
     */
    private fun removeAnimations(on: Boolean) {
        val resolver = composeRule.activity.applicationContext.contentResolver
        Settings.Global.putString(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, if (on) "0" else "1")
        resolver.notifyChange(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), null)
        shadowOf(Looper.getMainLooper()).idle()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun showSettings(layoutDirection: LayoutDirection = LayoutDirection.Ltr): NavHostController {
        lateinit var nav: NavHostController
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            SettingsFrame(layoutDirection) {
                nav = rememberNavController()
                CompositionLocalProvider(LocalNavController provides nav) {
                    Routes.AppNavHost(
                        modifier = Modifier.fillMaxSize(),
                        navController = nav,
                        startDestination = Routes.Settings.PrivacyAuditLog::class,
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000)
        return nav
    }
}

/** The settings frame the app wraps every screen in, which also sets the layout direction the screens see. */
@Composable
private fun SettingsFrame(layoutDirection: LayoutDirection = LayoutDirection.Ltr, content: @Composable () -> Unit) {
    ProvideLocalizedResources(
        resourcesContext = LocalContext.current,
        appName = R.string.app_name,
        forceLayoutDirection = layoutDirection,
    ) {
        FlorisAppTheme(theme = AppTheme.LIGHT) {
            ProvideDefaultDialogPrefStrings(
                confirmLabel = stringRes(R.string.action__ok),
                dismissLabel = stringRes(R.string.action__cancel),
                neutralLabel = stringRes(R.string.action__default),
            ) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    content()
                }
            }
        }
    }
}
