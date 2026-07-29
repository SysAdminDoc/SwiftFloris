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
            flogWarning { "TaskerActionReceiver rejected action: external automation disabled" }
            return
        }
        if (intent?.action != TaskerIntentContract.Plugin.ACTION_FIRE_SETTING) {
            flogWarning { "TaskerActionReceiver rejected action: invalid plug-in action" }
            return
        }

        val rawJson = intent.readTaskerPluginJson()
        if (rawJson == null) {
            flogWarning { "TaskerActionReceiver rejected action: malformed plug-in bundle" }
            return
        }
        when (val result = TaskerAuthentication.authenticate(context, rawJson)) {
            is PluginAuthenticationResult.Accept -> {
                TaskerActionDispatcher.dispatch(
                    context = context,
                    action = result.action.action,
                    extras = result.action.extras,
                )
            }
            is PluginAuthenticationResult.Reject -> {
                flogWarning { "TaskerActionReceiver rejected action: ${result.reason}" }
            }
        }
    }
}

@Suppress("DEPRECATION")
internal fun Intent.readTaskerPluginJson(): String? {
    return runCatching {
        val outerExtras = extras ?: return@runCatching null
        val pluginBundle = outerExtras.get(TaskerIntentContract.Plugin.EXTRA_BUNDLE) as? Bundle
            ?: return@runCatching null
        if (pluginBundle.keySet() != setOf(TaskerIntentContract.Plugin.EXTRA_STRING_JSON)) {
            return@runCatching null
        }
        pluginBundle.get(TaskerIntentContract.Plugin.EXTRA_STRING_JSON) as? String
    }.getOrNull()
}
