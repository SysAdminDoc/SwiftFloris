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

package dev.patrickgold.florisboard.app

import android.content.Intent
import android.os.Bundle
import androidx.core.os.BundleCompat
import java.util.UUID

/**
 * Lets the activity handle each external request once, across rotation and process recreation.
 *
 * Every intent the activity gets is tagged with a token in an extra, and the tokens of handled
 * requests go into the saved state. A rotation recreates the activity with the same intent
 * object, token included, so a handled one is skipped. After process death the system hands back
 * the launch intent without the token, so the launch intent's token is saved too and stands in
 * for it. The request still waiting to be handled is saved as well, because one delivered to the
 * running activity doesn't come back on its own after process death.
 */
internal class ExternalIntentLedger {
    private var launchToken: String? = null
    private val handled = LinkedHashSet<String>()

    /** The request waiting to be handled, if any. */
    var pending: Intent? = null
        private set

    fun restore(state: Bundle?) {
        if (state == null) return
        launchToken = state.getString(KEY_LAUNCH_TOKEN)
        state.getStringArrayList(KEY_HANDLED)?.let { handled += it }
        pending = BundleCompat.getParcelable(state, KEY_PENDING, Intent::class.java)
    }

    fun save(out: Bundle) {
        out.putString(KEY_LAUNCH_TOKEN, launchToken)
        out.putStringArrayList(KEY_HANDLED, ArrayList(handled))
        out.putParcelable(KEY_PENDING, pending)
    }

    /**
     * Tags [intent] with its token. [isLaunch] is true for the intent the activity was created
     * with, and false for one delivered later, which is always new, whatever extras its sender
     * put on it. A request ([isRequest]) that wasn't handled yet becomes [pending], and a
     * different one still waiting counts as handled, since the newer one replaces it. Anything
     * else, like a tap on the launcher icon, leaves [pending] alone.
     */
    fun offer(intent: Intent, isLaunch: Boolean, isRequest: Boolean) {
        val token = if (isLaunch) {
            intent.token ?: launchToken ?: newToken()
        } else {
            newToken()
        }
        intent.putExtra(EXTRA_TOKEN, token)
        if (isLaunch && launchToken == null) launchToken = token
        if (!isRequest || token in handled) return
        val waiting = pending
        // The saved copy of the request this activity was recreated with.
        if (waiting?.token == token) return
        waiting?.let(::markHandled)
        pending = intent
    }

    fun markHandled(intent: Intent) {
        val token = intent.token ?: return
        handled += token
        // Only the launch intent and the activity's current one can come back.
        handled.retainAll(setOfNotNull(launchToken, token))
        if (pending?.token == token) pending = null
    }

    private val Intent.token: String?
        get() = getStringExtra(EXTRA_TOKEN)

    private fun newToken() = UUID.randomUUID().toString()

    private companion object {
        const val EXTRA_TOKEN = "swiftfloris.app.external_intent_token"
        const val KEY_LAUNCH_TOKEN = "swiftfloris.app.launch_intent_token"
        const val KEY_HANDLED = "swiftfloris.app.handled_intent_tokens"
        const val KEY_PENDING = "swiftfloris.app.pending_intent"
    }
}
