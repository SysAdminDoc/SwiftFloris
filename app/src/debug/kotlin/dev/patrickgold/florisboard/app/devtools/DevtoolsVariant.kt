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

package dev.patrickgold.florisboard.app.devtools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adb
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavController
import androidx.navigation.toRoute
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.Deeplink
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.composableWithDeepLink
import dev.patrickgold.florisboard.app.settings.search.Preference
import dev.patrickgold.florisboard.app.settings.search.SettingsSearchDestination
import dev.patrickgold.florisboard.app.settings.search.SettingsSearchEntry
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.serialization.Serializable
import org.florisboard.lib.compose.stringRes

internal object DebugDevtoolsRoutes {
    @Serializable
    @Deeplink("devtools")
    object Home

    @Serializable
    @Deeplink("devtools/android/locales")
    object AndroidLocales

    @Serializable
    @Deeplink("devtools/android/settings")
    data class AndroidSettings(val name: String)

    @Serializable
    @Deeplink("export-debug-log")
    object ExportDebugLog
}

@Composable
private fun DevtoolsRouteGate(content: @Composable () -> Unit) {
    val prefs by FlorisPreferenceStore
    val devtoolsEnabled by prefs.devtools.enabled.collectAsState()
    if (devtoolsEnabled) {
        content()
    } else {
        DevtoolsScreen()
    }
}

internal fun NavGraphBuilder.registerDevtoolsRoutes() {
    composableWithDeepLink(DebugDevtoolsRoutes.Home::class) { DevtoolsScreen() }
    composableWithDeepLink(DebugDevtoolsRoutes.AndroidLocales::class) {
        DevtoolsRouteGate { AndroidLocalesScreen() }
    }
    composableWithDeepLink(DebugDevtoolsRoutes.AndroidSettings::class) { navBackStack ->
        DevtoolsRouteGate {
            val payload = navBackStack.toRoute<DebugDevtoolsRoutes.AndroidSettings>()
            AndroidSettingsScreen(payload.name)
        }
    }
    composableWithDeepLink(DebugDevtoolsRoutes.ExportDebugLog::class) {
        DevtoolsRouteGate { ExportDebugLogScreen() }
    }
}

@Composable
internal fun DevtoolsPreference(navController: NavController) {
    Preference(
        icon = Icons.Default.Adb,
        title = stringRes(R.string.devtools__title),
        summary = stringRes(R.string.settings__other__devtools_summary),
        onClick = { navController.navigate(DebugDevtoolsRoutes.Home) },
    )
}

internal fun devtoolsSettingsSearchEntries(): List<SettingsSearchEntry> = listOf(
    SettingsSearchEntry(
        id = "devtools",
        screenTitleResId = R.string.devtools__title,
        titleResId = R.string.devtools__title,
        summaryResId = R.string.settings__other__devtools_summary,
        destination = SettingsSearchDestination.DEVTOOLS,
        keywords = listOf("debug", "logs", "android", "settings"),
    ),
)

internal fun devtoolsSearchRoute(): Any = DebugDevtoolsRoutes.Home
