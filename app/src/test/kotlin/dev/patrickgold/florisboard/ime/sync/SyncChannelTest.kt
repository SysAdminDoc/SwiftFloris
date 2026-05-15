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

package dev.patrickgold.florisboard.ime.sync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class SyncChannelTest : FunSpec({
    test("Syncthing channel id round-trips through parse") {
        val original = SyncChannel.Syncthing("swiftfloris-home")
        val parsed = SyncChannel.parse(original.channelId)
            .shouldBeInstanceOf<SyncChannel.Syncthing>()
        parsed.folderName shouldBe "swiftfloris-home"
        original.descriptor.kind shouldBe SyncChannelKind.SYNCTHING
    }

    test("LocalFolder channel id round-trips through parse") {
        val original = SyncChannel.LocalFolder(
            absolutePath = "/storage/emulated/0/Sync/SwiftFloris",
            displayLabel = "Nextcloud mirror",
        )
        val parsed = SyncChannel.parse(original.channelId)
            .shouldBeInstanceOf<SyncChannel.LocalFolder>()
        parsed.absolutePath shouldBe "/storage/emulated/0/Sync/SwiftFloris"
    }

    test("ManualExport and Disabled singletons parse back to themselves") {
        SyncChannel.parse("swiftfloris:manual-export") shouldBe SyncChannel.ManualExport
        SyncChannel.parse("swiftfloris:disabled") shouldBe SyncChannel.Disabled
    }

    test("unknown channel ids parse to Disabled") {
        SyncChannel.parse("") shouldBe SyncChannel.Disabled
        SyncChannel.parse("foo:bar:baz") shouldBe SyncChannel.Disabled
        SyncChannel.parse("swiftfloris:icloud:home") shouldBe SyncChannel.Disabled
        // Missing folder name → Disabled.
        SyncChannel.parse("swiftfloris:syncthing:") shouldBe SyncChannel.Disabled
        // LocalFolder without absolute path → Disabled.
        SyncChannel.parse("swiftfloris:folder:relative/path") shouldBe SyncChannel.Disabled
    }

    test("Syncthing constructor rejects a blank folder") {
        shouldThrow<IllegalArgumentException> { SyncChannel.Syncthing("") }
        shouldThrow<IllegalArgumentException> { SyncChannel.Syncthing("   ") }
    }

    test("LocalFolder constructor enforces an absolute path") {
        shouldThrow<IllegalArgumentException> {
            SyncChannel.LocalFolder("relative/path", "x")
        }
        shouldThrow<IllegalArgumentException> {
            SyncChannel.LocalFolder("", "x")
        }
    }

    test("Each channel kind has a non-blank display name + brief description") {
        val channels: List<SyncChannel> = listOf(
            SyncChannel.Syncthing("home"),
            SyncChannel.LocalFolder("/abs", "label"),
            SyncChannel.ManualExport,
            SyncChannel.Disabled,
        )
        channels.forEach {
            it.descriptor.displayName.isNotBlank() shouldBe true
            it.descriptor.briefDescription.isNotBlank() shouldBe true
        }
    }
})
