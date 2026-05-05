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

package dev.patrickgold.florisboard.ime.voice

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class VoiceCommandCustomizationTest : FunSpec({
    test("serializes and deserializes custom command list") {
        val commands = VoiceCommandCustomCommands(
            listOf(
                VoiceCommandCustomCommand(
                    id = "delete-custom",
                    phrase = "scratch that",
                    action = VoiceCommandAction.DELETE_THAT,
                ),
            ),
        )

        val encoded = VoiceCommandCustomCommands.Serializer.serialize(commands)

        VoiceCommandCustomCommands.Serializer.deserialize(encoded) shouldBe commands
    }

    test("invalid custom command JSON falls back to empty list") {
        VoiceCommandCustomCommands.Serializer.deserialize("{broken") shouldBe VoiceCommandCustomCommands.Empty
    }

    test("custom commands can be added replaced and removed by id") {
        val initial = VoiceCommandCustomCommands.Empty.upsert(
            VoiceCommandCustomCommand(
                id = "line",
                phrase = "break here",
                action = VoiceCommandAction.NEW_LINE,
            ),
        )
        val replaced = initial.upsert(
            VoiceCommandCustomCommand(
                id = "line",
                phrase = "line here",
                action = VoiceCommandAction.NEW_LINE,
            ),
        )

        replaced.commands shouldBe listOf(
            VoiceCommandCustomCommand(
                id = "line",
                phrase = "line here",
                action = VoiceCommandAction.NEW_LINE,
            ),
        )
        replaced.remove("line") shouldBe VoiceCommandCustomCommands.Empty
    }

    test("parser uses enabled custom commands") {
        val customCommands = VoiceCommandCustomCommands(
            listOf(
                VoiceCommandCustomCommand(
                    id = "delete-custom",
                    phrase = "scratch that",
                    action = VoiceCommandAction.DELETE_THAT,
                ),
            ),
        )

        VoiceCommandParser().parse("scratch that", customCommands)?.action shouldBe VoiceCommandAction.DELETE_THAT
    }

    test("parser ignores disabled or blank custom commands") {
        val customCommands = VoiceCommandCustomCommands(
            listOf(
                VoiceCommandCustomCommand(
                    id = "disabled",
                    phrase = "scratch that",
                    action = VoiceCommandAction.DELETE_THAT,
                    enabled = false,
                ),
                VoiceCommandCustomCommand(
                    id = "blank",
                    phrase = " ",
                    action = VoiceCommandAction.NEW_LINE,
                ),
            ),
        )

        VoiceCommandParser().parse("scratch that", customCommands) shouldBe null
    }
})
