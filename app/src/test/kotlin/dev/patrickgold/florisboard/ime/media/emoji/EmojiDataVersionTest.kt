/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.media.emoji

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EmojiDataVersionTest : FunSpec({

    test("default version is unknown") {
        val version = EmojiDataVersion()
        version.cldr shouldBe "unknown"
        version.emoji shouldBe "unknown"
    }

    test("version can be constructed with explicit values") {
        val version = EmojiDataVersion(cldr = "48", emoji = "16.0")
        version.cldr shouldBe "48"
        version.emoji shouldBe "16.0"
    }

    test("empty EmojiData has unknown version") {
        val data = EmojiData.empty()
        data.version.cldr shouldBe "unknown"
        data.version.emoji shouldBe "unknown"
    }
})
