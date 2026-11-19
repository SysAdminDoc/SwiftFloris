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

package dev.patrickgold.florisboard.app.settings.localization

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.AppTheme
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.apptheme.FlorisAppTheme
import dev.patrickgold.florisboard.lib.FlorisLocale
import dev.patrickgold.florisboard.lib.util.foldForSearch
import dev.patrickgold.florisboard.screenshot.RoborazziHostActivity
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.florisboard.lib.compose.ProvideLocalizedResources
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The language list's search matched only lowercase text as typed, so "francais" missed "français", and its
 * empty state was never announced. Accented and plain spellings have to find the same languages in the same
 * order, language tags still have to work, and TalkBack hears one result line per pause in typing.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xxhdpi", sdk = [35])
class SelectLocaleSearchTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<RoborazziHostActivity>()

    private fun index() = localeSearchIndex(FlorisLocale.extendedAvailableLocales(composeRule.activity))

    private fun tags(locales: List<FlorisLocale>) = locales.map { it.languageTag() }

    @Test
    fun accentedAndPlainSpellingsFindTheSameLanguagesInTheSameOrder() {
        val index = index()

        val plainFrench = tags(index.filter("francais"))
        plainFrench.shouldNotBeEmpty()
        plainFrench shouldContain "fr"
        tags(index.filter("français")) shouldContainExactly plainFrench
        tags(index.filter("FRANÇAIS")) shouldContainExactly plainFrench

        val plainSpanish = tags(index.filter("espanol"))
        plainSpanish shouldContain "es"
        tags(index.filter("español")) shouldContainExactly plainSpanish
    }

    @Test
    fun lettersWithNothingToStripFoldToWhatPeopleType() {
        foldForSearch("Włoski") shouldBe "wloski"
        foldForSearch("FØROYSKT") shouldBe "foroyskt"
        foldForSearch("Straße") shouldBe "strasse"
        foldForSearch("İzmir") shouldBe "izmir"
        foldForSearch("Azərbaycan") shouldBe "azerbaycan"
        foldForSearch("Eʋegbe") shouldBe "evegbe"
        foldForSearch("Ɛʋɛ Ɔɖɔ ƒe") shouldBe "eve odo fe"

        val index = index()
        val faroese = tags(index.filter("foroyskt"))
        faroese shouldContain "fo"
        tags(index.filter("føroyskt")) shouldContainExactly faroese
    }

    @Test
    fun languageTagsStillMatchFromTheStart() {
        val index = index()

        tags(index.filter("pt-BR")) shouldContain "pt-BR"
        tags(index.filter("pt_br")) shouldContain "pt-BR"
        tags(index.filter("de")) shouldContain "de"
        tags(index.filter("  ")).size shouldBe index.filter("").size
    }

    @Test
    fun theResultLineIsAnnouncedOncePerPauseInTyping() {
        val count = index().filter("francais").size
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { Frame { SelectLocaleScreen() } }
        composeRule.mainClock.advanceTimeBy(LocaleSearchSettleMillis * 2)
        announced() shouldBe emptyList()

        composeRule.onNode(hasSetTextAction()).performTextInput("francais")
        composeRule.mainClock.advanceTimeBy(LocaleSearchSettleMillis / 5)
        announced() shouldBe emptyList()
        // The list itself follows the folded search right away: French is in it and German isn't.
        val french = FlorisLocale.from("fr")
        val german = FlorisLocale.from("de")
        rows(french.displayName(), french.displayName(french)) shouldNotBe 0
        rows(german.displayName(), german.displayName(german)) shouldBe 0

        composeRule.mainClock.advanceTimeBy(LocaleSearchSettleMillis)
        announced() shouldBe listOf(
            composeRule.activity.resources.getQuantityString(
                R.plurals.settings__localization__subtype_search_locale_results,
                count,
            ).replace("{count}", count.toString()),
        )

        composeRule.onNode(hasSetTextAction()).performTextReplacement("zzqx")
        composeRule.mainClock.advanceTimeBy(LocaleSearchSettleMillis / 5)
        announced().single().contains("zzqx") shouldBe false

        composeRule.mainClock.advanceTimeBy(LocaleSearchSettleMillis)
        announced() shouldBe listOf(
            composeRule.activity.getString(R.string.settings__localization__subtype_search_locale_not_found)
                .replace("{search_term}", "zzqx"),
        )
    }

    private fun rows(vararg names: String): Int = composeRule.onAllNodes(
        names.map { hasText(it) }.reduce { a, b -> a or b },
        useUnmergedTree = true,
    ).fetchSemanticsNodes().size

    private fun announced(): List<String> {
        composeRule.mainClock.advanceTimeByFrame()
        return composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text } }
    }
}

@Composable
private fun Frame(content: @Composable () -> Unit) {
    ProvideLocalizedResources(resourcesContext = LocalContext.current, appName = R.string.app_name) {
        FlorisAppTheme(theme = AppTheme.LIGHT) {
            CompositionLocalProvider(LocalNavController provides rememberNavController()) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    content()
                }
            }
        }
    }
}
