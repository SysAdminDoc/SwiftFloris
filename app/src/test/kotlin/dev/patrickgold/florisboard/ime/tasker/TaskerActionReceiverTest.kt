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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputType
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.security.SecureRandom

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TaskerActionReceiverTest {
    private lateinit var context: Context
    private lateinit var sink: RecordingTaskerActionSink
    private lateinit var persistence: MemorySecretPersistence
    private lateinit var random: FillSecureRandom
    private lateinit var secret: ByteArray
    private val receiver = TaskerActionReceiver()
    private val prefs by FlorisPreferenceStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sink = RecordingTaskerActionSink()
        TaskerActionDispatcher.sinkFactory = { sink }
        secret = ByteArray(TaskerIntentContract.AUTH_SECRET_BYTES) { index -> (index + 3).toByte() }
        persistence = MemorySecretPersistence(secret)
        random = FillSecureRandom(0x55)
        val store = TaskerAuthenticationStore(persistence, random)
        TaskerAuthentication.storeFactory = { store }
        setExternalAutomationEnabled(true)
    }

    @After
    fun tearDown() {
        setExternalAutomationEnabled(false)
        TaskerActionDispatcher.resetSinkFactoryForTests()
        TaskerAuthentication.resetStoreFactoryForTests()
    }

    @Test
    fun receiverDropsAuthenticatedActionsWhenExternalAutomationIsDisabled() {
        setExternalAutomationEnabled(false)

        receiver.onReceive(
            context,
            pluginIntent(
                TaskerIntentContract.InsertText.ACTION,
                mapOf(TaskerIntentContract.InsertText.EXTRA_TEXT to "Hello"),
            ),
        )

        sink.calls shouldBe emptyList()
    }

    @Test
    fun receiverDispatchesAllAuthenticatedActions() {
        receiver.onReceive(
            context,
            pluginIntent(
                TaskerIntentContract.InsertText.ACTION,
                mapOf(
                    TaskerIntentContract.InsertText.EXTRA_TEXT to "Hello",
                    TaskerIntentContract.InsertText.EXTRA_APPEND_SPACE to true,
                ),
            ),
        )
        receiver.onReceive(
            context,
            pluginIntent(TaskerIntentContract.InsertClipboard.ACTION, emptyMap()),
        )
        receiver.onReceive(
            context,
            pluginIntent(
                TaskerIntentContract.SwitchLayout.ACTION,
                mapOf(TaskerIntentContract.SwitchLayout.EXTRA_LAYOUT_ID to "dvorak"),
            ),
        )
        receiver.onReceive(
            context,
            pluginIntent(
                TaskerIntentContract.TriggerVoice.ACTION,
                mapOf(TaskerIntentContract.TriggerVoice.EXTRA_MODE to "command"),
            ),
        )

        sink.calls.shouldContainExactly(
            "insert:Hello ",
            "paste",
            "layout:dvorak",
            "voice:command",
        )
    }

    @Test
    fun receiverRejectsRawAndUnauthenticatedBroadcasts() {
        receiver.onReceive(
            context,
            Intent(TaskerIntentContract.InsertText.ACTION)
                .putExtra(TaskerIntentContract.InsertText.EXTRA_TEXT, "forged"),
        )
        receiver.onReceive(
            context,
            pluginIntent(
                action = TaskerIntentContract.InsertText.ACTION,
                extras = mapOf(TaskerIntentContract.InsertText.EXTRA_TEXT to "forged"),
                signingSecret = ByteArray(TaskerIntentContract.AUTH_SECRET_BYTES) { 0x77 },
            ),
        )

        sink.calls shouldBe emptyList()
    }

    @Test
    fun receiverRejectsConfigurationsAfterSecretRotation() {
        val oldIntent = pluginIntent(
            TaskerIntentContract.SwitchLayout.ACTION,
            mapOf(TaskerIntentContract.SwitchLayout.EXTRA_LAYOUT_ID to "dvorak"),
        )

        TaskerAuthentication.rotate(context) shouldBe true
        receiver.onReceive(context, oldIntent)

        sink.calls shouldBe emptyList()
    }

    @Test
    fun receiverRejectsMalformedOrUnexpectedPluginBundles() {
        receiver.onReceive(
            context,
            Intent(TaskerIntentContract.Plugin.ACTION_FIRE_SETTING),
        )
        receiver.onReceive(
            context,
            Intent(TaskerIntentContract.Plugin.ACTION_FIRE_SETTING)
                .putExtra(TaskerIntentContract.Plugin.EXTRA_BUNDLE, "not a bundle"),
        )
        receiver.onReceive(
            context,
            Intent(TaskerIntentContract.Plugin.ACTION_FIRE_SETTING)
                .putExtra(
                    TaskerIntentContract.Plugin.EXTRA_BUNDLE,
                    Bundle().apply {
                        putString(TaskerIntentContract.Plugin.EXTRA_STRING_JSON, "{}")
                        putString("shadow", "payload")
                    },
                ),
        )

        sink.calls shouldBe emptyList()
    }

    @Test
    fun receiverAllowsHostExtensionExtrasOutsideThePluginBundle() {
        val intent = pluginIntent(TaskerIntentContract.InsertClipboard.ACTION, emptyMap())
            .putExtra("net.dinglisch.android.tasker.extras.HOST_CAPABILITY", true)

        receiver.onReceive(context, intent)

        sink.calls.shouldContainExactly("paste")
    }

    @Test
    fun receiverSuppressesTextAndClipboardActionsInSensitiveFields() {
        sink.attributes = EditorAttributes(
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            imeOptions = 0,
        )

        receiver.onReceive(
            context,
            pluginIntent(
                TaskerIntentContract.InsertText.ACTION,
                mapOf(TaskerIntentContract.InsertText.EXTRA_TEXT to "secret"),
            ),
        )
        receiver.onReceive(
            context,
            pluginIntent(TaskerIntentContract.InsertClipboard.ACTION, emptyMap()),
        )

        sink.calls shouldBe emptyList()
    }

    @Test
    fun manifestExposesOnlyTheLocalePluginEntrypointsWithoutSignaturePermission() {
        @Suppress("DEPRECATION")
        val receiverInfo = context.packageManager.getReceiverInfo(
            ComponentName(context, TaskerActionReceiver::class.java),
            PackageManager.GET_META_DATA,
        )
        @Suppress("DEPRECATION")
        val activityInfo = context.packageManager.getActivityInfo(
            ComponentName(context, TaskerConfigActivity::class.java),
            PackageManager.GET_META_DATA,
        )

        receiverInfo.exported shouldBe true
        receiverInfo.permission shouldBe null
        activityInfo.exported shouldBe true
        activityInfo.permission shouldBe null

        @Suppress("DEPRECATION")
        val fireReceivers = context.packageManager.queryBroadcastReceivers(
            Intent(TaskerIntentContract.Plugin.ACTION_FIRE_SETTING)
                .setPackage(context.packageName),
            PackageManager.MATCH_ALL,
        )
        fireReceivers.map { it.activityInfo.name }
            .shouldContainExactly(TaskerActionReceiver::class.java.name)

        @Suppress("DEPRECATION")
        val editActivities = context.packageManager.queryIntentActivities(
            Intent(TaskerIntentContract.Plugin.ACTION_EDIT_SETTING)
                .setPackage(context.packageName)
                .addCategory(Intent.CATEGORY_DEFAULT),
            PackageManager.MATCH_ALL,
        )
        editActivities.map { it.activityInfo.name }
            .shouldContainExactly(TaskerConfigActivity::class.java.name)

        listOf(
            TaskerIntentContract.InsertText.ACTION,
            TaskerIntentContract.InsertClipboard.ACTION,
            TaskerIntentContract.SwitchLayout.ACTION,
            TaskerIntentContract.TriggerVoice.ACTION,
        ).forEach { rawAction ->
            @Suppress("DEPRECATION")
            context.packageManager.queryBroadcastReceivers(
                Intent(rawAction).setPackage(context.packageName),
                PackageManager.MATCH_ALL,
            ) shouldBe emptyList()
        }
    }

    private fun pluginIntent(
        action: String,
        extras: Map<String, Any?>,
        signingSecret: ByteArray = secret,
    ): Intent {
        val json = TaskerIntentContract.createAuthenticatedJson(signingSecret, action, extras)
        return Intent(TaskerIntentContract.Plugin.ACTION_FIRE_SETTING)
            .putExtra(
                TaskerIntentContract.Plugin.EXTRA_BUNDLE,
                Bundle().apply {
                    putString(TaskerIntentContract.Plugin.EXTRA_STRING_JSON, json)
                },
            )
    }

    private class RecordingTaskerActionSink : TaskerActionSink {
        val calls = mutableListOf<String>()
        var attributes: EditorAttributes? = null

        override fun insertText(text: String): Boolean {
            calls += "insert:$text"
            return true
        }

        override fun pasteClipboard(): Boolean {
            calls += "paste"
            return true
        }

        override fun switchLayout(layoutId: String): Boolean {
            calls += "layout:$layoutId"
            return true
        }

        override fun triggerVoice(mode: String?): Boolean {
            calls += "voice:${mode ?: "dictation"}"
            return true
        }

        override fun currentEditorAttributes(): EditorAttributes? = attributes
    }

    private class MemorySecretPersistence(initial: ByteArray?) : TaskerSecretPersistence {
        var value = initial?.copyOf()

        override fun read(): ByteArray? = value?.copyOf()

        override fun write(secret: ByteArray): Boolean {
            value = secret.copyOf()
            return true
        }
    }

    private class FillSecureRandom(var fillByte: Byte) : SecureRandom() {
        constructor(fillByte: Int) : this(fillByte.toByte())

        override fun nextBytes(bytes: ByteArray) {
            bytes.fill(fillByte)
        }
    }

    private fun setExternalAutomationEnabled(enabled: Boolean) {
        runBlocking {
            prefs.privacy.externalAutomationEnabled.set(enabled).getOrThrow()
        }
    }
}
