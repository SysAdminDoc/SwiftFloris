/*
 * Copyright (C) 2022-2025 The FlorisBoard Contributors
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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.devtools.Devtools
import org.florisboard.lib.android.showShortToastSync
import org.florisboard.lib.compose.FlorisButtonBar
import org.florisboard.lib.compose.FlorisInfoCard
import org.florisboard.lib.compose.florisHorizontalScroll
import org.florisboard.lib.compose.florisScrollbar
import org.florisboard.lib.compose.stringRes

@Composable
fun ExportDebugLogScreen() = FlorisScreen {
    title = stringRes(R.string.devtools__debuglog__title)
    scrollable = false

    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val resources = LocalResources.current
    val clipboardManager by context.clipboardManager()

    var debugLog by remember { mutableStateOf<List<String>?>(null) }
    var formattedDebugLog by remember { mutableStateOf<List<String>?>(null) }

    LaunchedEffect(Unit) {
        debugLog = Devtools.generateDebugLog(context, prefs, includeLogcat = true).lines()
        formattedDebugLog = Devtools.generateDebugLogForGithub(context, prefs, includeLogcat = true).lines()
    }

    bottomBar {
        FlorisButtonBar {
            ButtonBarSpacer()
            ButtonBarTextButton(
                onClick = {
                    debugLog?.let { log ->
                        clipboardManager.addNewPlaintext(log.joinToString("\n"))
                        context.showShortToastSync(resources.getString(R.string.devtools__debuglog__copied_to_clipboard))
                    }
                },
                text = stringRes(R.string.devtools__debuglog__copy_log),
                enabled = debugLog != null,
            )
            ButtonBarButton(
                onClick = {
                    formattedDebugLog?.let { log ->
                        clipboardManager.addNewPlaintext(log.joinToString("\n"))
                        context.showShortToastSync(resources.getString(R.string.devtools__debuglog__copied_to_clipboard))
                    }
                },
                text = stringRes(R.string.devtools__debuglog__copy_for_github),
                enabled = formattedDebugLog != null,
            )
        }
    }

    content {
        // Forcing LTR because text displayed is a debug log
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            val lazyListState = rememberLazyListState()
            val log = debugLog
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FlorisInfoCard(
                    modifier = Modifier.fillMaxWidth(),
                    text = if (log == null) {
                        stringRes(R.string.devtools__debuglog__loading)
                    } else {
                        stringRes(R.string.devtools__debuglog__ready_title)
                    },
                    secondaryText = if (log == null) {
                        stringRes(R.string.devtools__debuglog__loading_summary)
                    } else {
                        stringRes(R.string.devtools__debuglog__ready_summary, "line_count" to log.size)
                    },
                    showIcon = false,
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.56f),
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    if (log == null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = stringRes(R.string.devtools__debuglog__loading),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .florisScrollbar(lazyListState, isVertical = true)
                                .florisHorizontalScroll()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            state = lazyListState,
                        ) {
                            items(log) { logLine ->
                                Text(
                                    text = logLine,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
