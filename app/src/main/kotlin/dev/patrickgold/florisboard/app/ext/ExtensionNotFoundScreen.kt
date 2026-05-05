/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.app.ext

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import org.florisboard.lib.compose.FlorisEmptyState
import org.florisboard.lib.compose.stringRes

@Composable
internal fun ExtensionNotFoundScreen(id: String) = FlorisScreen {
    title = stringRes(R.string.ext__error__not_found_title)

    val navController = LocalNavController.current

    content {
        FlorisEmptyState(
            modifier = Modifier.padding(16.dp),
            icon = Icons.Default.Extension,
            title = stringRes(R.string.ext__error__not_found_title),
            message = stringRes(R.string.ext__error__not_found_description, "id" to id),
            actionLabel = stringRes(R.string.action__back),
            onAction = { navController.popBackStack() },
        )
    }
}
