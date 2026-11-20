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

import android.content.Context
import android.inputmethodservice.InputMethodService
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.input.InputFeedbackController
import dev.patrickgold.florisboard.ime.input.LocalInputFeedbackController
import dev.patrickgold.florisboard.ime.keyboard.ComputingEvaluator
import dev.patrickgold.florisboard.ime.keyboard.DefaultComputingEvaluator
import dev.patrickgold.florisboard.ime.keyboard.KeyData
import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.key.KeyType
import dev.patrickgold.florisboard.ime.text.key.UtilityKeyAction
import dev.patrickgold.florisboard.ime.window.ImeInsets
import dev.patrickgold.florisboard.ime.window.ImeWindowConfig
import dev.patrickgold.florisboard.ime.window.ImeWindowController
import dev.patrickgold.florisboard.ime.window.ImeWindowMode
import dev.patrickgold.florisboard.ime.window.ImeWindowSpec
import dev.patrickgold.florisboard.ime.window.LocalWindowController
import dev.patrickgold.florisboard.screenshot.RoborazziHostActivity
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.florisboard.lib.snygg.SnyggStylesheet
import org.florisboard.lib.snygg.ui.ProvideSnyggTheme
import org.florisboard.lib.snygg.ui.rememberSnyggTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/**
 * Turning the utility key from Switch language back to Switch to emojis left the bottom row as 123, comma, an
 * empty gap, Space until the app was restarted. The cached keyboard was computed and laid out again, but the
 * comma only moved (its size stayed the same), and its position was read through a lambda that never changed, so
 * it stayed drawn where the emoji key belongs. Like KeyboardManager, the test recomputes one keyboard in place
 * and hands the layout a new evaluator; every key must then sit where the layout put it.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi", sdk = [35])
class UtilityKeyRelayoutTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<RoborazziHostActivity>()

    private val windowScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var feedback: InputFeedbackController
    private lateinit var windowController: ImeWindowController

    @Before
    fun setUp() {
        feedback = InputFeedbackController.new(Robolectric.buildService(InputMethodService::class.java).create().get())
        val prefs by FlorisPreferenceStore
        windowController = ImeWindowController(prefs, windowScope)
        val density = Density(composeRule.activity)
        with(density) { windowController.updateRootInsets(ImeInsets.Root.of(IntRect(0, 0, 1080, 1920))) }
        val config = ImeWindowConfig(ImeWindowMode.FIXED, fixedMode = ImeWindowMode.Fixed.NORMAL)
        windowController.updateWindowConfig { config }
        runBlocking { windowController.activeWindowSpec.first { it !== ImeWindowSpec.Fallback } }
    }

    @After
    fun tearDown() {
        feedback.dispose()
        windowScope.cancel()
    }

    @Test
    fun theKeysBesideTheEmojiKeyMoveOverAsSoonAsItComesBack() {
        val keyboard = keyboardWithTheDefaultBottomRow()
        var evaluator by mutableStateOf(recompute(keyboard, UtilityKeyAction.SWITCH_LANGUAGE, version = 1))
        composeRule.setContent {
            Frame {
                Box(Modifier.width(360.dp).height(120.dp)) {
                    TextKeyboardLayout(evaluator = evaluator)
                }
            }
        }
        composeRule.waitForIdle()
        check(shows(keyboard, KeyCode.LANGUAGE_SWITCH) && !shows(keyboard, KeyCode.IME_UI_MODE_MEDIA))
        assertKeysWhereLaidOut(keyboard)

        composeRule.runOnIdle {
            evaluator = recompute(keyboard, UtilityKeyAction.SWITCH_TO_EMOJIS, version = 2)
        }
        composeRule.waitForIdle()
        check(shows(keyboard, KeyCode.IME_UI_MODE_MEDIA) && !shows(keyboard, KeyCode.LANGUAGE_SWITCH))

        val emoji = shownBounds(keyboard, KeyCode.IME_UI_MODE_MEDIA)
        check(emoji.width > 0f) { "The emoji key came back with no width: $emoji" }
        val comma = shownBounds(keyboard, ','.code)
        check(comma.left >= emoji.right - 1f) { "The comma at $comma still covers the emoji key at $emoji" }
        assertKeysWhereLaidOut(keyboard)
    }

    /** Computes every key against a new evaluator, the way KeyboardManager.updateActiveEvaluators does. */
    private fun recompute(keyboard: TextKeyboard, action: UtilityKeyAction, version: Int): ComputingEvaluator {
        val evaluator = UtilityKeyEvaluator(version, keyboard, action, composeRule.activity.applicationContext)
        for (key in keyboard.keys()) {
            key.compute(evaluator)
            key.computeLabelsAndDrawables(evaluator)
        }
        return evaluator
    }

    /** Every visible bottom-row key is shown at the spot the last layout pass gave it, measured from the 123 key. */
    private fun assertKeysWhereLaidOut(keyboard: TextKeyboard) {
        val anchorKey = key(keyboard, KeyCode.VIEW_SYMBOLS)
        val anchorShown = shownBounds(keyboard, KeyCode.VIEW_SYMBOLS)
        val bottomRow = keyboard.arrangement.last().filter { it.isVisible }
        check(bottomRow.size == 6) { "Expected six visible bottom-row keys, found ${bottomRow.map { it.computedData.code }}" }
        for (textKey in bottomRow) {
            val shown = shownBounds(keyboard, textKey.computedData.code)
            val expectedOffset = textKey.touchBounds.left - anchorKey.touchBounds.left
            val shownOffset = shown.left - anchorShown.left
            check(abs(shownOffset - expectedOffset) <= 1.5f) {
                "Key ${textKey.computedData.code} is shown ${shownOffset}px from 123, the layout put it ${expectedOffset}px"
            }
        }
        assertKeysDrawnWhereLaidOut(keyboard, anchorKey, anchorShown)
    }

    /**
     * The key people see is a separate box from the accessibility one, and it clears its semantics, so it's a node
     * with no properties at all. Each visible bottom-row key needs one of those at its laid-out visible bounds.
     */
    private fun assertKeysDrawnWhereLaidOut(keyboard: TextKeyboard, anchorKey: TextKey, anchorShown: Rect) {
        val drawn = composeRule.onAllNodes(SemanticsMatcher("has no semantics") { it.config.none() }, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot }
        for (textKey in keyboard.arrangement.last().filter { it.isVisible }) {
            val left = anchorShown.left + textKey.visibleBounds.left - anchorKey.touchBounds.left
            val top = anchorShown.top + textKey.visibleBounds.top - anchorKey.touchBounds.top
            val width = textKey.visibleBounds.width
            check(drawn.any { abs(it.left - left) <= 1.5f && abs(it.top - top) <= 1.5f && abs(it.width - width) <= 1.5f }) {
                "Key ${textKey.computedData.code} isn't drawn at ($left, $top), ${width}px wide, where the layout put it. " +
                    "Drawn: $drawn"
            }
        }
    }

    private fun shows(keyboard: TextKeyboard, code: Int): Boolean =
        keyboard.keys().asSequence().any { it.computedData.code == code && it.isVisible }

    private fun key(keyboard: TextKeyboard, code: Int): TextKey =
        keyboard.keys().asSequence().first { it.computedData.code == code }

    /** Where the key with [code] is on screen, read from the accessibility box drawn for it. */
    private fun shownBounds(keyboard: TextKeyboard, code: Int): Rect {
        val textKey = key(keyboard, code)
        val description = keyContentDescription(composeRule.activity, code, textKey.label, textKey.hintedLabel)
        val matcher = SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(description))
        val nodes = composeRule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes()
            .filter { it.boundsInRoot.width > 0f }
        check(nodes.size == 1) { "Expected one shown key described \"$description\", found ${nodes.size}" }
        return nodes.single().boundsInRoot
    }

    /** A letter row and charactersMod/default.json's bottom row, the one the utility key action changes. */
    private fun keyboardWithTheDefaultBottomRow(): TextKeyboard {
        fun keys(vararg data: TextKeyData) = data.map(::TextKey).toTypedArray()
        val letters = keys(*"qwertyuiop".map { TextKeyData(code = it.code, label = it.toString()) }.toTypedArray())
        val bottomRow = keys(
            TextKeyData(KeyType.SYSTEM_GUI, KeyCode.VIEW_SYMBOLS, "view_symbols"),
            TextKeyData(KeyType.SYSTEM_GUI, KeyCode.IME_UI_MODE_MEDIA, "ime_ui_mode_media"),
            TextKeyData(code = ','.code, label = ",", groupId = KeyData.GROUP_LEFT),
            TextKeyData(KeyType.SYSTEM_GUI, KeyCode.LANGUAGE_SWITCH, "language_switch"),
            TextKeyData(code = KeyCode.SPACE, label = "space"),
            TextKeyData(code = '.'.code, label = ".", groupId = KeyData.GROUP_RIGHT),
            TextKeyData(KeyType.ENTER_EDITING, KeyCode.ENTER, "enter", KeyData.GROUP_ENTER),
        )
        return TextKeyboard(
            arrangement = arrayOf(letters, bottomRow),
            mode = KeyboardMode.CHARACTERS,
            extendedPopupMapping = null,
            extendedPopupMappingDefault = null,
        )
    }

    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        val stylesheet = remember { loadBundledStylesheet("floris_night.json") }
        val theme = rememberSnyggTheme(stylesheet)
        ProvideLocalizedResources(resourcesContext = LocalContext.current, appName = R.string.app_name) {
            MaterialTheme {
                ProvideSnyggTheme(theme) {
                    CompositionLocalProvider(
                        LocalInputFeedbackController provides feedback,
                        LocalWindowController provides windowController,
                    ) {
                        content()
                    }
                }
            }
        }
    }

    private fun loadBundledStylesheet(fileName: String): SnyggStylesheet {
        val file = listOf(
            File("src/main/assets/ime/theme/org.florisboard.themes/stylesheets/$fileName"),
            File("app/src/main/assets/ime/theme/org.florisboard.themes/stylesheets/$fileName"),
        ).firstOrNull { it.exists() } ?: error("$fileName not reachable from ${File(".").absolutePath}")
        return SnyggStylesheet.fromJson(file.readText()).getOrThrow()
    }
}

/** Shows the emoji key or the language key for [action], as KeyboardManager's evaluator does. */
private class UtilityKeyEvaluator(
    override val version: Int,
    override val keyboard: TextKeyboard,
    private val action: UtilityKeyAction,
    private val context: Context,
) : ComputingEvaluator by DefaultComputingEvaluator {
    override fun context(): Context = context

    override fun evaluateVisible(data: KeyData): Boolean = when (data.code) {
        KeyCode.IME_UI_MODE_MEDIA -> action == UtilityKeyAction.SWITCH_TO_EMOJIS
        KeyCode.LANGUAGE_SWITCH -> action == UtilityKeyAction.SWITCH_LANGUAGE
        else -> true
    }
}
