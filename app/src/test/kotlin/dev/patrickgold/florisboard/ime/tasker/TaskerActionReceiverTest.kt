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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TaskerActionReceiverTest {
    private lateinit var context: Context
    private lateinit var sink: RecordingTaskerActionSink
    private val receiver = TaskerActionReceiver()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sink = RecordingTaskerActionSink()
        TaskerActionDispatcher.sinkFactory = { sink }
    }

    @After
    fun tearDown() {
        TaskerActionDispatcher.resetSinkFactoryForTests()
    }

    @Test
    fun receiverDispatchesInsertText() {
        receiver.onReceive(
            context,
            Intent(TaskerIntentContract.InsertText.ACTION)
                .putExtra(TaskerIntentContract.InsertText.EXTRA_TEXT, "Hello")
                .putExtra(TaskerIntentContract.InsertText.EXTRA_APPEND_SPACE, true),
        )

        sink.calls.shouldContainExactly("insert:Hello ")
    }

    @Test
    fun receiverDispatchesInsertClipboard() {
        receiver.onReceive(context, Intent(TaskerIntentContract.InsertClipboard.ACTION))

        sink.calls.shouldContainExactly("paste")
    }

    @Test
    fun receiverDispatchesSwitchLayout() {
        receiver.onReceive(
            context,
            Intent(TaskerIntentContract.SwitchLayout.ACTION)
                .putExtra(TaskerIntentContract.SwitchLayout.EXTRA_LAYOUT_ID, "dvorak"),
        )

        sink.calls.shouldContainExactly("layout:dvorak")
    }

    @Test
    fun receiverDispatchesTriggerVoice() {
        receiver.onReceive(
            context,
            Intent(TaskerIntentContract.TriggerVoice.ACTION)
                .putExtra(TaskerIntentContract.TriggerVoice.EXTRA_MODE, "command"),
        )

        sink.calls.shouldContainExactly("voice:command")
    }

    @Test
    fun receiverRejectsInvalidIntentBeforeDispatch() {
        receiver.onReceive(context, Intent(TaskerIntentContract.InsertText.ACTION))

        sink.calls shouldBe emptyList()
    }

    @Test
    fun receiverIsExportedAndSignatureProtectedInManifest() {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getReceiverInfo(
            ComponentName(context, TaskerActionReceiver::class.java),
            PackageManager.GET_META_DATA,
        )

        info.exported shouldBe true
        info.permission shouldBe TaskerIntentContract.PERMISSION_TRIGGER
    }

    private class RecordingTaskerActionSink : TaskerActionSink {
        val calls = mutableListOf<String>()

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
    }
}
