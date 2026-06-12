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
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class ExternalVoiceInputProvidersTest : FunSpec({
    test("supported offline provider catalog includes FUTO, WhisperInput, and Whisper") {
        val providers = ExternalVoiceInputProviders.SupportedOfflineImeProviders

        providers shouldHaveSize 3
        providers.map { it.packageName } shouldContain "org.futo.voiceinput"
        providers.map { it.packageName } shouldContain "com.alexvt.whisperinput"
        providers.map { it.packageName } shouldContain "org.woheller69.whisper"
    }

    test("provider lookup maps known package names to display labels") {
        ExternalVoiceInputProviders.byPackageName("com.alexvt.whisperinput")?.label shouldBe "WhisperInput"
        ExternalVoiceInputProviders.byPackageName("org.unknown.voice") shouldBe null
    }

    test("provider statuses classify ready, permission, installed, and missing states") {
        val statuses = ExternalVoiceInputProviders.statuses(
            installedPackageNames = setOf(
                "org.futo.voiceinput",
                "com.alexvt.whisperinput",
            ),
            enabledVoiceInputMethodPackages = setOf(
                "org.futo.voiceinput",
                "com.alexvt.whisperinput",
            ),
            hasMicrophonePermission = { packageName -> packageName == "org.futo.voiceinput" },
        )

        statuses.first { it.provider.packageName == "org.futo.voiceinput" }.state shouldBe
            ExternalVoiceInputProviderState.Ready
        statuses.first { it.provider.packageName == "com.alexvt.whisperinput" }.state shouldBe
            ExternalVoiceInputProviderState.EnabledNeedsMicrophone
        statuses.first { it.provider.packageName == "org.woheller69.whisper" }.state shouldBe
            ExternalVoiceInputProviderState.NotInstalled
    }

    test("installed providers that are not enabled are visible as setup candidates") {
        val whisper = ExternalVoiceInputProviders.statuses(
            installedPackageNames = setOf("org.woheller69.whisper"),
            enabledVoiceInputMethodPackages = emptySet(),
            hasMicrophonePermission = { false },
        ).first { it.provider.packageName == "org.woheller69.whisper" }

        whisper.state shouldBe ExternalVoiceInputProviderState.InstalledNotEnabled
        whisper.isReady shouldBe false
    }
})
