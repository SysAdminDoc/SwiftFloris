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

package org.florisboard.lib.compose

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import org.florisboard.lib.android.AndroidSettings

/**
 * Returns `true` when the user has disabled system animations
 * ("Remove animations" accessibility setting / animator duration scale 0),
 * so decorative or spatial motion can be replaced with static equivalents.
 *
 * Observed rather than remembered. The animator duration scale lives in
 * `Settings.Global`, and changing it produces no configuration change, so a
 * value keyed on `LocalConfiguration` kept reporting whatever was true when the
 * screen was composed. Somebody turning "Remove animations" on while the app is
 * open is precisely the person who should see it take effect.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val scale by AndroidSettings.Global.observeAsState(
        key = Settings.Global.ANIMATOR_DURATION_SCALE,
        foregroundOnly = true,
    )
    // Absent, unparseable, or a value the platform did not set: assume motion is
    // wanted, which is what every release did before this setting existed.
    return scale?.toFloatOrNull() == 0f
}
