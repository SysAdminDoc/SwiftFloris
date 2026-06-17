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

package dev.patrickgold.florisboard.app.settings

import io.kotest.matchers.string.shouldContain
import org.junit.Test
import java.io.File

class HomeScreenPremiumPolishContractTest {
    @Test
    fun homeScreenKeepsPolishedOverviewAndPrimaryActions() {
        val source = locateHomeScreenSource().readText()

        source shouldContain "SettingsHomeOverviewCard("
        source shouldContain "SettingsHomeStatusRow("
        source shouldContain "SettingsHomeQuickActions("
        source shouldContain "SettingsHomeTrustChecks()"
        source shouldContain "settings__home__overview_status_a11y"
        source shouldContain "settings__home__quick_action_search"
        source shouldContain "settings__home__quick_action_import"
        source shouldContain "settings__home__quick_action_privacy"
        source shouldContain "shape = MaterialTheme.shapes.small"
    }

    @Test
    fun homeScreenTrustCopyStaysAvailable() {
        val strings = locateStringsSource().readText()

        strings shouldContain "settings__home__trust_no_network_title"
        strings shouldContain "settings__home__trust_local_import_title"
        strings shouldContain "settings__home__trust_verification_title"
    }
}

private fun locateHomeScreenSource(): File {
    val candidates = listOf(
        "app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/HomeScreen.kt",
        "src/main/kotlin/dev/patrickgold/florisboard/app/settings/HomeScreen.kt",
    )
    return candidates.map(::File).firstOrNull { it.exists() && it.canRead() }
        ?: error("HomeScreen.kt not reachable from working directory ${File(".").absolutePath}")
}

private fun locateStringsSource(): File {
    val candidates = listOf(
        "app/src/main/res/values/strings.xml",
        "src/main/res/values/strings.xml",
    )
    return candidates.map(::File).firstOrNull { it.exists() && it.canRead() }
        ?: error("strings.xml not reachable from working directory ${File(".").absolutePath}")
}
