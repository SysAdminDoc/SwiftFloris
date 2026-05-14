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

package dev.patrickgold.florisboard.ime.theme

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe

class PerAppAccentResolverTest : FunSpec({

    test("pure red sits at hue 0") {
        PerAppAccentResolver.hueOf(255, 0, 0) shouldBe 0f
    }

    test("pure green sits at hue 120") {
        PerAppAccentResolver.hueOf(0, 255, 0) shouldBe 120f
    }

    test("pure blue sits at hue 240") {
        PerAppAccentResolver.hueOf(0, 0, 255) shouldBe 240f
    }

    test("low-saturation grey is classified as grey") {
        // ROADMAP §7 Next-11.3 — Slack-white / Outlook-blue+white-ish icons
        // must not contaminate the per-app accent. Anything below the
        // saturation floor maps to "grey" and is skipped by the extractor.
        PerAppAccentResolver.classify(220, 220, 220) shouldBe "grey"
        PerAppAccentResolver.classify(80, 80, 90) shouldBe "grey"
    }

    test("near-white saturated pixel is classified as tooLight") {
        // 250/240/240 is a barely-pink that's effectively white in a
        // launcher icon. Reject it so the resolver picks a real accent
        // pixel instead.
        PerAppAccentResolver.classify(250, 240, 240) shouldBe "tooLight"
    }

    test("near-black saturated pixel is classified as tooDark") {
        PerAppAccentResolver.classify(20, 5, 5) shouldBe "tooDark"
    }

    test("saturated mid-value pixel is classified as accent") {
        PerAppAccentResolver.classify(40, 100, 220) shouldBe "accent"
        PerAppAccentResolver.classify(220, 100, 40) shouldBe "accent"
        PerAppAccentResolver.classify(20, 180, 80) shouldBe "accent"
    }

    test("more-saturated of two reds wins the tiebreak") {
        // Higher-saturation accent must win over washed-out accent of the
        // same hue — pinning the "highest saturation wins" rule baked
        // into the resolver's pixel-pick loop.
        val highSat = PerAppAccentResolver.saturationOf(255, 0, 0)
        val lowSat = PerAppAccentResolver.saturationOf(200, 80, 80)
        (lowSat < highSat) shouldBe true
    }

    test("hue distance wraps around 360") {
        // hue 350 vs hue 10 should be 20 degrees apart, not 340.
        PerAppAccentResolver.hueDistance(350f, 10f) shouldBeLessThan 30f
    }
})
