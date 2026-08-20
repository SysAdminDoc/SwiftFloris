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

package dev.patrickgold.florisboard.lib.compose

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlinx.coroutines.channels.Channel

private data class FlorisSnackbarRequest(
    val message: String,
    val actionLabel: String?,
    val onAction: () -> Unit,
)

/** Single-consumer snackbar queue shared by the Settings activity and IME window. */
object FlorisSnackbarController {
    private val requests = Channel<FlorisSnackbarRequest>(capacity = Channel.BUFFERED)

    fun show(
        message: String,
        actionLabel: String? = null,
        onAction: () -> Unit = {},
    ) {
        requests.trySend(FlorisSnackbarRequest(message, actionLabel, onAction))
    }

    @Composable
    fun Host(modifier: Modifier = Modifier) {
        val hostState = remember { SnackbarHostState() }
        LaunchedEffect(hostState) {
            for (request in requests) {
                val result = hostState.showSnackbar(
                    message = request.message,
                    actionLabel = request.actionLabel,
                    duration = if (request.actionLabel == null) {
                        SnackbarDuration.Short
                    } else {
                        SnackbarDuration.Long
                    },
                )
                if (result == SnackbarResult.ActionPerformed) {
                    request.onAction()
                }
            }
        }
        SnackbarHost(hostState = hostState, modifier = modifier)
    }
}
