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

package dev.patrickgold.florisboard.ime.tasker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.lib.devtools.flogWarning

class TaskerActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs by FlorisPreferenceStore
        if (!prefs.privacy.externalAutomationEnabled.get()) {
            flogWarning {
                "TaskerActionReceiver rejected '${intent?.action.orEmpty()}': external automation disabled"
            }
            return
        }
        TaskerActionDispatcher.dispatch(
            context = context,
            action = intent?.action,
            extras = intent.toTaskerExtrasMap(),
        )
    }
}

@Suppress("DEPRECATION")
private fun Intent?.toTaskerExtrasMap(): Map<String, Any?> {
    val intent = this ?: return emptyMap()
    val bundle: Bundle = intent.extras ?: return emptyMap()
    val allowedKeys = when (intent.action) {
        TaskerIntentContract.InsertText.ACTION -> setOf(
            TaskerIntentContract.InsertText.EXTRA_TEXT,
            TaskerIntentContract.InsertText.EXTRA_APPEND_SPACE,
        )
        TaskerIntentContract.InsertClipboard.ACTION -> emptySet()
        TaskerIntentContract.SwitchLayout.ACTION -> setOf(
            TaskerIntentContract.SwitchLayout.EXTRA_LAYOUT_ID,
        )
        TaskerIntentContract.TriggerVoice.ACTION -> setOf(
            TaskerIntentContract.TriggerVoice.EXTRA_MODE,
        )
        else -> return emptyMap()
    }
    val values = linkedMapOf<String, Any?>()
    for (key in bundle.keySet()) {
        values[key] = if (key in allowedKeys) {
            runCatching { bundle.get(key) }.getOrDefault(UnreadableTaskerExtra)
        } else {
            UnexpectedTaskerExtra
        }
    }
    return values
}

private data object UnreadableTaskerExtra

private data object UnexpectedTaskerExtra
