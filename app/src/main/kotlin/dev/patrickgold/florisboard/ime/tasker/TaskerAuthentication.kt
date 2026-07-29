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

import android.content.Context
import dev.patrickgold.florisboard.ime.security.TinkStringPreferenceCrypto
import java.security.SecureRandom

internal const val TASKER_AUTH_PREFS_FILE = "swiftfloris_tasker_auth"
internal const val TASKER_AUTH_PREFS_XML = "$TASKER_AUTH_PREFS_FILE.xml"
private const val TASKER_AUTH_PREF_KEY = "installation_secret_v1"
private const val TASKER_AUTH_KEYSTORE_ALIAS = "swiftfloris_tasker_authentication_secret_v1"

internal interface TaskerSecretPersistence {
    fun read(): ByteArray?
    fun write(secret: ByteArray): Boolean
}

/**
 * Owns creation and rotation of the per-install Tasker signing key. Persistence
 * is injected so key lifecycle behavior can be tested without an Android
 * Keystore; production uses [TinkStringPreferenceCrypto].
 */
internal class TaskerAuthenticationStore(
    private val persistence: TaskerSecretPersistence,
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    @Synchronized
    fun getOrCreateSecret(): ByteArray? {
        val stored = runCatching { persistence.read() }.getOrNull()
        if (stored?.size == TaskerIntentContract.AUTH_SECRET_BYTES) {
            return stored.copyOf()
        }
        return generateAndPersist()
    }

    @Synchronized
    fun rotateSecret(): Boolean {
        return generateAndPersist() != null
    }

    private fun generateAndPersist(): ByteArray? {
        val generated = ByteArray(TaskerIntentContract.AUTH_SECRET_BYTES)
        secureRandom.nextBytes(generated)
        val stored = runCatching { persistence.write(generated) }.getOrDefault(false)
        return generated.copyOf().takeIf { stored }
    }
}

private class AndroidTaskerSecretPersistence(context: Context) : TaskerSecretPersistence {
    private val prefs = TinkStringPreferenceCrypto.sharedPreferences(
        context.applicationContext ?: context,
        TASKER_AUTH_PREFS_FILE,
    )

    override fun read(): ByteArray? {
        return TinkStringPreferenceCrypto.readBytes(
            prefs = prefs,
            prefsFileName = TASKER_AUTH_PREFS_FILE,
            key = TASKER_AUTH_PREF_KEY,
            keystoreAlias = TASKER_AUTH_KEYSTORE_ALIAS,
        )
    }

    override fun write(secret: ByteArray): Boolean {
        return TinkStringPreferenceCrypto.writeBytes(
            prefs = prefs,
            prefsFileName = TASKER_AUTH_PREFS_FILE,
            key = TASKER_AUTH_PREF_KEY,
            keystoreAlias = TASKER_AUTH_KEYSTORE_ALIAS,
            value = secret,
        )
    }
}

/**
 * Small process-wide facade shared by the edit activity, receiver, and privacy
 * settings. The lock prevents two concurrent first-use calls from minting
 * different keys before either one is persisted.
 */
internal object TaskerAuthentication {
    private val lock = Any()

    @Volatile
    internal var storeFactory: (Context) -> TaskerAuthenticationStore = { context ->
        TaskerAuthenticationStore(AndroidTaskerSecretPersistence(context))
    }

    fun createAuthenticatedJson(
        context: Context,
        action: String,
        extras: Map<String, Any?>,
    ): String? = synchronized(lock) {
        val secret = storeFactory(context).getOrCreateSecret() ?: return@synchronized null
        runCatching {
            TaskerIntentContract.createAuthenticatedJson(secret, action, extras)
        }.getOrNull()
    }

    fun authenticate(context: Context, rawJson: String): PluginAuthenticationResult = synchronized(lock) {
        val secret = storeFactory(context).getOrCreateSecret()
            ?: return@synchronized PluginAuthenticationResult.Reject(
                "authentication key unavailable",
            )
        TaskerIntentContract.authenticateJson(secret, rawJson)
    }

    fun rotate(context: Context): Boolean = synchronized(lock) {
        storeFactory(context).rotateSecret()
    }

    internal fun resetStoreFactoryForTests() {
        storeFactory = { context ->
            TaskerAuthenticationStore(AndroidTaskerSecretPersistence(context))
        }
    }
}
