/*
 * Copyright (C) 2026 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.app.settings.about

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.util.launchUrl
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import org.florisboard.lib.compose.FlorisInfoCard
import org.florisboard.lib.compose.stringRes

@Composable
fun AiFeaturesScreen() = FlorisScreen {
    title = stringRes(R.string.about__ai_features__title)

    val context = LocalContext.current

    content {
        FlorisInfoCard(
            modifier = Modifier.padding(8.dp),
            text = stringRes(R.string.about__ai_features__headline),
            secondaryText = stringRes(R.string.about__ai_features__headline_summary),
        )
        PreferenceGroup(title = stringRes(R.string.about__ai_features__processing_group)) {
            Preference(
                icon = Icons.Outlined.Info,
                title = stringRes(R.string.about__ai_features__local_processing_title),
                summary = stringRes(R.string.about__ai_features__local_processing_summary),
            )
            Preference(
                icon = Icons.Outlined.Info,
                title = stringRes(R.string.about__ai_features__no_accounts_title),
                summary = stringRes(R.string.about__ai_features__no_accounts_summary),
            )
        }
        PreferenceGroup(title = stringRes(R.string.about__ai_features__surfaces_group)) {
            AiFeatureDisclosureCatalog.rows.forEach { row ->
                Preference(
                    title = stringRes(row.titleRes),
                    summary = stringRes(row.summaryRes),
                )
            }
        }
        PreferenceGroup(title = stringRes(R.string.about__ai_features__docs_group)) {
            Preference(
                icon = Icons.Outlined.Description,
                title = stringRes(R.string.about__ai_features__privacy_doc_title),
                summary = stringRes(R.string.about__ai_features__privacy_doc_summary),
                onClick = { context.launchUrl(R.string.florisboard__privacy_and_ai_url) },
            )
            Preference(
                icon = Icons.Outlined.Description,
                title = stringRes(R.string.about__ai_features__threat_model_title),
                summary = stringRes(R.string.about__ai_features__threat_model_summary),
                onClick = { context.launchUrl(R.string.florisboard__threat_model_url) },
            )
            Preference(
                icon = Icons.Outlined.Description,
                title = stringRes(R.string.about__ai_features__project_context_title),
                summary = stringRes(R.string.about__ai_features__project_context_summary),
                onClick = { context.launchUrl(R.string.florisboard__project_context_url) },
            )
        }
    }
}
