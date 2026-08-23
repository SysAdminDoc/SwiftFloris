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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavController
import dev.patrickgold.florisboard.app.settings.search.SettingsSearchEntry

@Composable
fun DevtoolsOverlay(modifier: Modifier = Modifier) = Unit

internal fun NavGraphBuilder.registerDevtoolsRoutes() = Unit

@Composable
internal fun DevtoolsPreference(navController: NavController) = Unit

internal fun devtoolsSettingsSearchEntries(): List<SettingsSearchEntry> = emptyList()

internal fun devtoolsSearchRoute(): Any? = null
