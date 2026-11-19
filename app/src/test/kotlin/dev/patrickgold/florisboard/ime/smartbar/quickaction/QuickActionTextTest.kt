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

package dev.patrickgold.florisboard.ime.smartbar.quickaction

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.inputmethodservice.InputMethodService
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.input.InputFeedbackController
import dev.patrickgold.florisboard.ime.input.LocalInputFeedbackController
import dev.patrickgold.florisboard.ime.keyboard.ComputingEvaluator
import dev.patrickgold.florisboard.ime.keyboard.DefaultComputingEvaluator
import dev.patrickgold.florisboard.ime.keyboard.KeyData
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.keyboard.TextKeyData
import dev.patrickgold.florisboard.screenshot.RoborazziHostActivity
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File
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
 * Translate, Add task, Insert event and the typed-text action had English names, tooltips and key labels
 * written into the code, so every language saw and heard them in English. Every string here is rendered
 * through pseudo-localized resources, which wrap whatever comes from a resource in [START] and [END]: text
 * that arrives without the marks was written into the code and would stay English in every language.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi", sdk = [35])
class QuickActionTextTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<RoborazziHostActivity>()

    private lateinit var feedback: InputFeedbackController

    @Before
    fun setUp() {
        feedback = InputFeedbackController.new(Robolectric.buildService(InputMethodService::class.java).create().get())
    }

    @After
    fun tearDown() {
        feedback.dispose()
    }

    private fun res(id: Int): String = pseudo(composeRule.activity.getString(id))

    /** The spelled-out text each action that isn't a key must show and say, taken from the string resources. */
    private val expected: Map<QuickAction, Expected> by lazy {
        mapOf(
            QuickAction.TranslateSelection to Expected(
                name = res(R.string.quick_action__translate_selection),
                compact = null,
                tooltip = res(R.string.quick_action__translate_selection__tooltip),
            ),
            QuickAction.InsertTask to Expected(
                name = res(R.string.quick_action__insert_task),
                compact = res(R.string.quick_action__insert_task__compact),
                tooltip = res(R.string.quick_action__insert_task__tooltip),
            ),
            QuickAction.InsertCalendarEvent to Expected(
                name = res(R.string.quick_action__insert_calendar_event),
                compact = res(R.string.quick_action__insert_calendar_event__compact),
                tooltip = res(R.string.quick_action__insert_calendar_event__tooltip),
            ),
            // What the user typed stays as they typed it. Only the words around it are translated.
            TypedText to Expected(
                name = TypedText.data,
                compact = null,
                tooltip = res(R.string.quick_action__insert_text__tooltip).replace("{text}", TypedText.data),
            ),
        )
    }

    /** Every action the quick-action editor offers, its empty-slot placeholder, and one of each other kind. */
    private fun allActions(): List<QuickAction> {
        val catalog = QuickActionArrangement.Default
        return (listOfNotNull(catalog.stickyAction) + catalog.dynamicActions + catalog.hiddenActions +
            QuickAction.InsertKey(TextKeyData(code = KeyCode.NOOP)) + expected.keys).distinct()
    }

    @Test
    fun everyKindOfActionIsChecked() {
        val checked = allActions().map { it::class }.toSet()
        withClue("a new kind of quick action needs its strings and an entry in this test") {
            QuickAction::class.sealedSubclasses.filterNot { it in checked }.shouldBeEmpty()
        }
    }

    @Test
    fun everyTileShowsAndSaysItsTranslatedName() {
        val actions = allActions()
        val shown = mutableMapOf<QuickAction, Pair<String, String>>()
        composeRule.setContent {
            Frame {
                Column {
                    for (action in actions) {
                        shown[action] = action.computeDisplayName(DefaultComputingEvaluator) to
                            action.computeTooltip(DefaultComputingEvaluator)
                        QuickActionButton(
                            action = action,
                            evaluator = DefaultComputingEvaluator,
                            modifier = Modifier.size(64.dp),
                            type = QuickActionBarType.EDITOR_TILE,
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val missing = res(R.string.general__invalid_fatal)
        for (action in actions) {
            val (name, tooltip) = shown.getValue(action)
            withClue("$action") {
                val spec = expected[action]
                if (spec != null) {
                    name shouldBe spec.name
                    tooltip shouldBe spec.tooltip
                } else {
                    isPseudo(name) shouldBe true
                    isPseudo(tooltip) shouldBe true
                    name shouldNotBe missing
                    tooltip shouldNotBe missing
                    name shouldNotBe pseudo("")
                }
                // The tile says the full name to TalkBack and prints it under the key.
                var tile = hasContentDescription(name) and hasAnyDescendant(hasText(name))
                spec?.compact?.let { tile = tile and hasAnyDescendant(hasText(it)) }
                composeRule.onAllNodes(tile, useUnmergedTree = true).fetchSemanticsNodes().size shouldNotBe 0
            }
        }
    }

    @Test
    fun aLongPressShowsTheTranslatedTooltip() {
        val disabled = object : ComputingEvaluator by DefaultComputingEvaluator {
            override fun evaluateEnabled(data: KeyData): Boolean = false
        }
        val spec = expected.getValue(QuickAction.InsertTask)
        composeRule.setContent {
            Frame {
                QuickActionButton(
                    action = QuickAction.InsertTask,
                    evaluator = disabled,
                    modifier = Modifier.size(48.dp),
                    type = QuickActionBarType.INTERACTIVE_BUTTON,
                )
            }
        }

        composeRule.onNode(hasContentDescription(spec.name), useUnmergedTree = true).performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onAllNodes(hasText(spec.tooltip), useUnmergedTree = true).fetchSemanticsNodes().size shouldNotBe 0
    }

    @Test
    fun screenReadersHearTheFullNameOnceNotTheShortLabel() {
        val keys = listOf(
            QuickAction.InsertTask,
            QuickAction.InsertCalendarEvent,
            QuickAction.TranslateSelection,
            QuickAction.InsertText(data = "hi"),
        )
        val names = mutableMapOf<QuickAction, String>()
        composeRule.setContent {
            Frame {
                Column {
                    for (action in keys) {
                        names[action] = action.computeDisplayName(DefaultComputingEvaluator)
                        QuickActionButton(
                            action = action,
                            evaluator = DefaultComputingEvaluator,
                            modifier = Modifier.size(48.dp),
                            type = QuickActionBarType.INTERACTIVE_BUTTON,
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val shortLabels = listOf(
            expected.getValue(QuickAction.InsertTask).compact!!,
            expected.getValue(QuickAction.InsertCalendarEvent).compact!!,
            String(Character.toChars(0x1F310)),
            "h",
        )
        for (label in shortLabels) {
            withClue("the short label $label is drawn but never read out on its own") {
                val drawn = composeRule.onAllNodes(hasText(label), useUnmergedTree = true).fetchSemanticsNodes()
                drawn.size shouldNotBe 0
                drawn.all { SemanticsProperties.HideFromAccessibility in it.config } shouldBe true
            }
        }
        for (action in keys) {
            withClue("$action") {
                composeRule.onAllNodes(hasContentDescription(names.getValue(action)))
                    .fetchSemanticsNodes().size shouldBe 1
            }
        }
    }

    @Test
    fun aTypedTextKeyShowsItsFirstCharacter() {
        val grinning = String(Character.toChars(0x1F600))
        insertTextKeyLabel("hi") shouldBe "h"
        insertTextKeyLabel(grinning + "x") shouldBe grinning
        insertTextKeyLabel("") shouldBe "?"
        insertTextKeyLabel(" hi") shouldBe "?"
    }

    @Test
    fun aTypedTextKeyKeepsAnEmojiMadeOfSeveralCodePointsWhole() {
        fun chars(vararg codePoints: Int) = codePoints.joinToString("") { String(Character.toChars(it)) }
        val flagPoland = chars(0x1F1F5, 0x1F1F1)
        val womanTechnologist = chars(0x1F469, 0x200D, 0x1F4BB)
        val thumbsUpMediumDark = chars(0x1F44D, 0x1F3FE)
        val keycapHash = chars(0x23, 0xFE0F, 0x20E3)
        val redHeart = chars(0x2764, 0xFE0F)
        for (emoji in listOf(flagPoland, womanTechnologist, thumbsUpMediumDark, keycapHash, redHeart)) {
            withClue(emoji.codePoints().toArray().joinToString { Integer.toHexString(it) }) {
                insertTextKeyLabel("$emoji rest") shouldBe emoji
            }
        }
    }

    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        val base = LocalContext.current
        val pseudoContext = remember(base) { PseudoLocalizedContext(base) }
        val stylesheet = remember { loadBundledStylesheet("floris_night.json") }
        val theme = rememberSnyggTheme(stylesheet)
        ProvideLocalizedResources(resourcesContext = pseudoContext, appName = R.string.app_name) {
            MaterialTheme {
                ProvideSnyggTheme(theme) {
                    CompositionLocalProvider(LocalInputFeedbackController provides feedback) {
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

    private class Expected(val name: String, val compact: String?, val tooltip: String)

    private companion object {
        const val START = "[«"
        const val END = "»]"
        val TypedText = QuickAction.InsertText("hi")

        fun pseudo(text: String) = START + text + END

        fun isPseudo(text: String) = text.startsWith(START) && text.endsWith(END)
    }
}

/** Hands out resources whose strings come back wrapped, the way a pseudo-locale marks translated text. */
private class PseudoLocalizedContext(base: Context) : ContextWrapper(base) {
    private val pseudoResources = PseudoLocalizedResources(base.resources)

    override fun getResources(): Resources = pseudoResources
}

@Suppress("DEPRECATION")
private class PseudoLocalizedResources(private val base: Resources) :
    Resources(base.assets, base.displayMetrics, base.configuration) {
    override fun getString(id: Int): String = "[«" + base.getString(id) + "»]"
}
