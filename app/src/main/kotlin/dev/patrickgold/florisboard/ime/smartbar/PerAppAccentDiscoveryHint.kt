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

package dev.patrickgold.florisboard.ime.smartbar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.ime.theme.PerAppAccentDiscoveryHintState
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggText

@Composable
fun PerAppAccentDiscoveryHint(modifier: Modifier = Modifier) {
    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()

    fun dismiss() {
        scope.launch {
            prefs.theme.perAppAccentDiscoveryHintState.set(PerAppAccentDiscoveryHintState.DISMISSED)
        }
    }

    SnyggRow(
        elementName = FlorisImeUi.SmartbarCandidatesRow.elementName,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SnyggText(
            elementName = FlorisImeUi.SmartbarCandidateWordText.elementName,
            modifier = Modifier.weight(1f),
            text = stringRes(R.string.ime__per_app_accent_hint__message),
        )
        SnyggText(
            elementName = FlorisImeUi.SmartbarCandidateWordText.elementName,
            modifier = Modifier
                .clickable(role = Role.Button) {
                    scope.launch {
                        prefs.theme.perAppAccentEnabled.set(true)
                        prefs.theme.perAppAccentDiscoveryHintState.set(PerAppAccentDiscoveryHintState.DISMISSED)
                    }
                }
                .padding(8.dp),
            text = stringRes(R.string.ime__per_app_accent_hint__enable),
        )
        SnyggText(
            elementName = FlorisImeUi.SmartbarCandidateWordSecondaryText.elementName,
            modifier = Modifier
                .clickable(role = Role.Button, onClick = ::dismiss)
                .padding(8.dp),
            text = stringRes(R.string.ime__per_app_accent_hint__dismiss),
        )
    }
}
