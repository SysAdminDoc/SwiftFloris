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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

class TaskerIntentContractTest : FunSpec({
    test("constants follow the swiftfloris.action.* namespace") {
        TaskerIntentContract.InsertText.ACTION shouldBe "swiftfloris.action.INSERT_TEXT"
        TaskerIntentContract.InsertClipboard.ACTION shouldBe "swiftfloris.action.INSERT_CLIP"
        TaskerIntentContract.SwitchLayout.ACTION shouldBe "swiftfloris.action.SWITCH_LAYOUT"
        TaskerIntentContract.TriggerVoice.ACTION shouldBe "swiftfloris.action.TRIGGER_VOICE"
    }

    test("InsertText validates required + non-empty + bounded EXTRA_TEXT") {
        TaskerIntentContract.validate(
            TaskerIntentContract.InsertText.ACTION,
            mapOf(TaskerIntentContract.InsertText.EXTRA_TEXT to "hello"),
        ) shouldBe ValidationResult.Accept

        val missing = TaskerIntentContract.validate(
            TaskerIntentContract.InsertText.ACTION,
            emptyMap(),
        ).shouldBeInstanceOf<ValidationResult.Reject>()
        missing.reason shouldContain "EXTRA_TEXT"

        val empty = TaskerIntentContract.validate(
            TaskerIntentContract.InsertText.ACTION,
            mapOf(TaskerIntentContract.InsertText.EXTRA_TEXT to ""),
        ).shouldBeInstanceOf<ValidationResult.Reject>()
        empty.reason shouldContain "not be empty"

        val tooLong = TaskerIntentContract.validate(
            TaskerIntentContract.InsertText.ACTION,
            mapOf(
                TaskerIntentContract.InsertText.EXTRA_TEXT to "x".repeat(
                    TaskerIntentContract.MAX_INSERT_LENGTH + 1,
                ),
            ),
        ).shouldBeInstanceOf<ValidationResult.Reject>()
        tooLong.reason shouldContain "exceeds"

        val wrongAppendType = TaskerIntentContract.validate(
            TaskerIntentContract.InsertText.ACTION,
            mapOf(
                TaskerIntentContract.InsertText.EXTRA_TEXT to "hello",
                TaskerIntentContract.InsertText.EXTRA_APPEND_SPACE to "yes",
            ),
        ).shouldBeInstanceOf<ValidationResult.Reject>()
        wrongAppendType.reason shouldContain "EXTRA_APPEND_SPACE"

        val unexpected = TaskerIntentContract.validate(
            TaskerIntentContract.InsertText.ACTION,
            mapOf(
                TaskerIntentContract.InsertText.EXTRA_TEXT to "hello",
                "shadow" to true,
            ),
        ).shouldBeInstanceOf<ValidationResult.Reject>()
        unexpected.reason shouldContain "unexpected"
    }

    test("InsertClipboard requires no extras") {
        TaskerIntentContract.validate(
            TaskerIntentContract.InsertClipboard.ACTION,
            emptyMap(),
        ) shouldBe ValidationResult.Accept

        TaskerIntentContract.validate(
            TaskerIntentContract.InsertClipboard.ACTION,
            mapOf("ignored" to "payload"),
        ).shouldBeInstanceOf<ValidationResult.Reject>()
    }

    test("SwitchLayout enforces lowercase + underscore + digit layout-id format") {
        TaskerIntentContract.validate(
            TaskerIntentContract.SwitchLayout.ACTION,
            mapOf(TaskerIntentContract.SwitchLayout.EXTRA_LAYOUT_ID to "dvorak"),
        ) shouldBe ValidationResult.Accept

        TaskerIntentContract.validate(
            TaskerIntentContract.SwitchLayout.ACTION,
            mapOf(TaskerIntentContract.SwitchLayout.EXTRA_LAYOUT_ID to "colemak_dh"),
        ) shouldBe ValidationResult.Accept

        // Uppercase, spaces, special chars all rejected.
        TaskerIntentContract.validate(
            TaskerIntentContract.SwitchLayout.ACTION,
            mapOf(TaskerIntentContract.SwitchLayout.EXTRA_LAYOUT_ID to "Dvorak"),
        ).shouldBeInstanceOf<ValidationResult.Reject>()
        TaskerIntentContract.validate(
            TaskerIntentContract.SwitchLayout.ACTION,
            mapOf(TaskerIntentContract.SwitchLayout.EXTRA_LAYOUT_ID to "dvorak v2"),
        ).shouldBeInstanceOf<ValidationResult.Reject>()
        // Missing EXTRA_LAYOUT_ID.
        TaskerIntentContract.validate(
            TaskerIntentContract.SwitchLayout.ACTION,
            emptyMap(),
        ).shouldBeInstanceOf<ValidationResult.Reject>()
        TaskerIntentContract.validate(
            TaskerIntentContract.SwitchLayout.ACTION,
            mapOf(
                TaskerIntentContract.SwitchLayout.EXTRA_LAYOUT_ID to "dvorak",
                "unused" to true,
            ),
        ).shouldBeInstanceOf<ValidationResult.Reject>()
    }

    test("TriggerVoice allows missing mode or 'dictation'/'command'") {
        TaskerIntentContract.validate(
            TaskerIntentContract.TriggerVoice.ACTION, emptyMap(),
        ) shouldBe ValidationResult.Accept
        TaskerIntentContract.validate(
            TaskerIntentContract.TriggerVoice.ACTION,
            mapOf(TaskerIntentContract.TriggerVoice.EXTRA_MODE to "command"),
        ) shouldBe ValidationResult.Accept
        TaskerIntentContract.validate(
            TaskerIntentContract.TriggerVoice.ACTION,
            mapOf(TaskerIntentContract.TriggerVoice.EXTRA_MODE to 1),
        ).shouldBeInstanceOf<ValidationResult.Reject>()
        TaskerIntentContract.validate(
            TaskerIntentContract.TriggerVoice.ACTION,
            mapOf(TaskerIntentContract.TriggerVoice.EXTRA_MODE to "humming"),
        ).shouldBeInstanceOf<ValidationResult.Reject>()
        TaskerIntentContract.validate(
            TaskerIntentContract.TriggerVoice.ACTION,
            mapOf("mode" to "command", "extra" to "ignored"),
        ).shouldBeInstanceOf<ValidationResult.Reject>()
    }

    test("Unknown actions are rejected with a clear reason") {
        val result = TaskerIntentContract.validate("com.evil.action.SEND_ALL_TYPING", emptyMap())
            .shouldBeInstanceOf<ValidationResult.Reject>()
        result.reason shouldContain "unknown SwiftFloris Tasker action"
    }
})
