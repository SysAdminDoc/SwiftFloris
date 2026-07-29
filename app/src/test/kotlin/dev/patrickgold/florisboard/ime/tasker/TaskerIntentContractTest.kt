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
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.Base64

class TaskerIntentContractTest : FunSpec({
    val secret = ByteArray(TaskerIntentContract.AUTH_SECRET_BYTES) { index -> (index + 1).toByte() }

    test("constants implement the Locale setting plug-in protocol") {
        TaskerIntentContract.Plugin.ACTION_EDIT_SETTING shouldBe
            "com.twofortyfouram.locale.intent.action.EDIT_SETTING"
        TaskerIntentContract.Plugin.ACTION_FIRE_SETTING shouldBe
            "com.twofortyfouram.locale.intent.action.FIRE_SETTING"
        TaskerIntentContract.Plugin.EXTRA_BUNDLE shouldBe
            "com.twofortyfouram.locale.intent.extra.BUNDLE"
        TaskerIntentContract.Plugin.EXTRA_STRING_BLURB shouldBe
            "com.twofortyfouram.locale.intent.extra.BLURB"
        TaskerIntentContract.Plugin.EXTRA_STRING_JSON shouldBe
            "com.twofortyfouram.locale.intent.extra.STRING_JSON"
    }

    test("authenticated JSON round-trips each supported action") {
        val configurations = listOf(
            TaskerPluginAction(
                TaskerIntentContract.InsertText.ACTION,
                mapOf(
                    TaskerIntentContract.InsertText.EXTRA_TEXT to "hello",
                    TaskerIntentContract.InsertText.EXTRA_APPEND_SPACE to true,
                ),
            ),
            TaskerPluginAction(TaskerIntentContract.InsertClipboard.ACTION, emptyMap()),
            TaskerPluginAction(
                TaskerIntentContract.SwitchLayout.ACTION,
                mapOf(TaskerIntentContract.SwitchLayout.EXTRA_LAYOUT_ID to "colemak_dh"),
            ),
            TaskerPluginAction(
                TaskerIntentContract.TriggerVoice.ACTION,
                mapOf(TaskerIntentContract.TriggerVoice.EXTRA_MODE to "command"),
            ),
        )

        configurations.forEach { expected ->
            val json = TaskerIntentContract.createAuthenticatedJson(
                secret,
                expected.action,
                expected.extras,
            )
            TaskerIntentContract.authenticateJson(secret, json) shouldBe
                PluginAuthenticationResult.Accept(expected)
        }
    }

    test("configuration contains a tag but never embeds the signing secret") {
        val json = TaskerIntentContract.createAuthenticatedJson(
            secret,
            TaskerIntentContract.InsertClipboard.ACTION,
            emptyMap(),
        )

        json shouldContain "\"authTag\""
        json shouldNotContain Base64.getEncoder().encodeToString(secret)
        json shouldNotContain Base64.getUrlEncoder().withoutPadding().encodeToString(secret)
    }

    test("wrong key and tampered payload are rejected") {
        val json = TaskerIntentContract.createAuthenticatedJson(
            secret,
            TaskerIntentContract.SwitchLayout.ACTION,
            mapOf(TaskerIntentContract.SwitchLayout.EXTRA_LAYOUT_ID to "dvorak"),
        )
        val wrongSecret = ByteArray(TaskerIntentContract.AUTH_SECRET_BYTES) { 0x5A }
        TaskerIntentContract.authenticateJson(wrongSecret, json)
            .shouldBeInstanceOf<PluginAuthenticationResult.Reject>()

        val tampered = json.replace("\"dvorak\"", "\"colemak\"")
        TaskerIntentContract.authenticateJson(secret, tampered)
            .shouldBeInstanceOf<PluginAuthenticationResult.Reject>()
    }

    test("malformed, oversized, unknown-field, and wrong-type JSON are rejected") {
        TaskerIntentContract.authenticateJson(secret, "{")
            .shouldBeInstanceOf<PluginAuthenticationResult.Reject>()
        TaskerIntentContract.authenticateJson(
            secret,
            "x".repeat(TaskerIntentContract.Plugin.MAX_JSON_LENGTH + 1),
        ).shouldBeInstanceOf<PluginAuthenticationResult.Reject>()

        val valid = TaskerIntentContract.createAuthenticatedJson(
            secret,
            TaskerIntentContract.InsertClipboard.ACTION,
            emptyMap(),
        )
        TaskerIntentContract.authenticateJson(
            secret,
            valid.dropLast(1) + ",\"unexpected\":true}",
        ).shouldBeInstanceOf<PluginAuthenticationResult.Reject>()
        TaskerIntentContract.authenticateJson(
            secret,
            valid.replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\""),
        ).shouldBeInstanceOf<PluginAuthenticationResult.Reject>()
        TaskerIntentContract.authenticateJson(
            secret,
            valid.replace("\"schemaVersion\":1", "\"schemaVersion\":{}"),
        ).shouldBeInstanceOf<PluginAuthenticationResult.Reject>()
    }

    test("rotated configuration can be read for editing but not executed") {
        val json = TaskerIntentContract.createAuthenticatedJson(
            secret,
            TaskerIntentContract.TriggerVoice.ACTION,
            mapOf(TaskerIntentContract.TriggerVoice.EXTRA_MODE to "dictation"),
        )
        val rotatedSecret = ByteArray(TaskerIntentContract.AUTH_SECRET_BYTES) { 0x33 }

        TaskerIntentContract.authenticateJson(rotatedSecret, json)
            .shouldBeInstanceOf<PluginAuthenticationResult.Reject>()
        TaskerIntentContract.decodeForEditing(json) shouldBe TaskerPluginAction(
            TaskerIntentContract.TriggerVoice.ACTION,
            mapOf(TaskerIntentContract.TriggerVoice.EXTRA_MODE to "dictation"),
        )
    }

    test("InsertText validates required non-empty bounded text") {
        TaskerIntentContract.validate(
            TaskerIntentContract.InsertText.ACTION,
            mapOf(TaskerIntentContract.InsertText.EXTRA_TEXT to "hello"),
        ) shouldBe ValidationResult.Accept

        TaskerIntentContract.validate(
            TaskerIntentContract.InsertText.ACTION,
            emptyMap(),
        ).shouldBeInstanceOf<ValidationResult.Reject>().reason shouldContain "EXTRA_TEXT"
        TaskerIntentContract.validate(
            TaskerIntentContract.InsertText.ACTION,
            mapOf(TaskerIntentContract.InsertText.EXTRA_TEXT to ""),
        ).shouldBeInstanceOf<ValidationResult.Reject>().reason shouldContain "not be empty"
        TaskerIntentContract.validate(
            TaskerIntentContract.InsertText.ACTION,
            mapOf(
                TaskerIntentContract.InsertText.EXTRA_TEXT to
                    "x".repeat(TaskerIntentContract.MAX_INSERT_LENGTH + 1),
            ),
        ).shouldBeInstanceOf<ValidationResult.Reject>().reason shouldContain "exceeds"
        TaskerIntentContract.validate(
            TaskerIntentContract.InsertText.ACTION,
            mapOf(
                TaskerIntentContract.InsertText.EXTRA_TEXT to "hello",
                TaskerIntentContract.InsertText.EXTRA_APPEND_SPACE to "yes",
            ),
        ).shouldBeInstanceOf<ValidationResult.Reject>().reason shouldContain "EXTRA_APPEND_SPACE"
        TaskerIntentContract.validate(
            TaskerIntentContract.InsertText.ACTION,
            mapOf(TaskerIntentContract.InsertText.EXTRA_TEXT to "hello", "shadow" to true),
        ).shouldBeInstanceOf<ValidationResult.Reject>().reason shouldContain "unexpected"
    }

    test("InsertClipboard requires no action fields") {
        TaskerIntentContract.validate(
            TaskerIntentContract.InsertClipboard.ACTION,
            emptyMap(),
        ) shouldBe ValidationResult.Accept
        TaskerIntentContract.validate(
            TaskerIntentContract.InsertClipboard.ACTION,
            mapOf("ignored" to "payload"),
        ).shouldBeInstanceOf<ValidationResult.Reject>()
    }

    test("SwitchLayout enforces lowercase underscore digit layout IDs") {
        listOf("dvorak", "colemak_dh", "layout_2").forEach { layoutId ->
            TaskerIntentContract.validate(
                TaskerIntentContract.SwitchLayout.ACTION,
                mapOf(TaskerIntentContract.SwitchLayout.EXTRA_LAYOUT_ID to layoutId),
            ) shouldBe ValidationResult.Accept
        }
        listOf("Dvorak", "dvorak v2", "../layout").forEach { layoutId ->
            TaskerIntentContract.validate(
                TaskerIntentContract.SwitchLayout.ACTION,
                mapOf(TaskerIntentContract.SwitchLayout.EXTRA_LAYOUT_ID to layoutId),
            ).shouldBeInstanceOf<ValidationResult.Reject>()
        }
    }

    test("TriggerVoice allows dictation or command only") {
        TaskerIntentContract.validate(
            TaskerIntentContract.TriggerVoice.ACTION,
            emptyMap(),
        ) shouldBe ValidationResult.Accept
        TaskerIntentContract.validate(
            TaskerIntentContract.TriggerVoice.ACTION,
            mapOf(TaskerIntentContract.TriggerVoice.EXTRA_MODE to "command"),
        ) shouldBe ValidationResult.Accept
        TaskerIntentContract.validate(
            TaskerIntentContract.TriggerVoice.ACTION,
            mapOf(TaskerIntentContract.TriggerVoice.EXTRA_MODE to "humming"),
        ).shouldBeInstanceOf<ValidationResult.Reject>()
    }

    test("unknown actions are rejected without reflecting attacker input") {
        val result = TaskerIntentContract.validate(
            "com.evil.action.SEND_ALL_TYPING",
            emptyMap(),
        ).shouldBeInstanceOf<ValidationResult.Reject>()
        result.reason shouldBe "unknown SwiftFloris Tasker action"
    }
})
