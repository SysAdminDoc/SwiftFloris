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

package dev.patrickgold.florisboard.app.preview

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.AppTheme
import dev.patrickgold.florisboard.app.apptheme.FlorisAppTheme
import org.florisboard.lib.compose.ProvideLocalizedResources

@Composable
fun SwiftFlorisPreviewFrame(content: @Composable () -> Unit) {
    ProvideLocalizedResources(
        resourcesContext = LocalContext.current,
        appName = R.string.app_name,
    ) {
        CompositionLocalProvider(
            // Preview snapshots run at the generated xxhdpi test density.
            LocalDensity provides Density(density = 3f, fontScale = 1f),
        ) {
            FlorisAppTheme(theme = AppTheme.LIGHT) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    content()
                }
            }
        }
    }
}
