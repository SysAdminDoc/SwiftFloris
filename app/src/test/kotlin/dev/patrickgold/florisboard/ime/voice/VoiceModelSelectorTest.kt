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

class VoiceModelSelectorTest : FunSpec({
    test("low-ram devices always use tiny English") {
        VoiceModelSelector.recommend(
            VoiceDeviceRamProfile(totalRamMb = 12_288, isLowRamDevice = true),
        ) shouldBe VoiceModelTier.TINY_EN
    }

    test("auto recommends tiny English below the base tier RAM floor") {
        VoiceModelSelector.recommend(
            VoiceDeviceRamProfile(totalRamMb = 3_072, isLowRamDevice = false),
        ) shouldBe VoiceModelTier.TINY_EN
    }

    test("auto recommends base English for mid-tier devices and unknown non-low-ram devices") {
        VoiceModelSelector.recommend(
            VoiceDeviceRamProfile(totalRamMb = 6_144, isLowRamDevice = false),
        ) shouldBe VoiceModelTier.BASE_EN
        VoiceModelSelector.recommend(
            VoiceDeviceRamProfile(totalRamMb = null, isLowRamDevice = false),
        ) shouldBe VoiceModelTier.BASE_EN
    }

    test("auto recommends large v3 turbo int8 for flagship RAM") {
        VoiceModelSelector.recommend(
            VoiceDeviceRamProfile(totalRamMb = 8_192, isLowRamDevice = false),
        ) shouldBe VoiceModelTier.LARGE_V3_TURBO_INT8
    }

    test("manual preferences override auto RAM selection") {
        val lowRamProfile = VoiceDeviceRamProfile(totalRamMb = 2_048, isLowRamDevice = true)

        VoiceModelPreference.TINY_EN.resolve(lowRamProfile) shouldBe VoiceModelTier.TINY_EN
        VoiceModelPreference.BASE_EN.resolve(lowRamProfile) shouldBe VoiceModelTier.BASE_EN
        VoiceModelPreference.LARGE_V3_TURBO_INT8.resolve(lowRamProfile) shouldBe
            VoiceModelTier.LARGE_V3_TURBO_INT8
        VoiceModelPreference.AUTO.resolve(lowRamProfile) shouldBe VoiceModelTier.TINY_EN
    }

    test("tier metadata preserves the roadmap size and RAM ordering") {
        VoiceModelTier.TINY_EN.approximateSizeMb shouldBe 75
        VoiceModelTier.BASE_EN.approximateSizeMb shouldBe 140
        VoiceModelTier.LARGE_V3_TURBO_INT8.approximateSizeMb shouldBe 800

        (VoiceModelTier.TINY_EN.minimumRecommendedRamMb <
            VoiceModelTier.BASE_EN.minimumRecommendedRamMb) shouldBe true
        (VoiceModelTier.BASE_EN.minimumRecommendedRamMb <
            VoiceModelTier.LARGE_V3_TURBO_INT8.minimumRecommendedRamMb) shouldBe true
    }
})
