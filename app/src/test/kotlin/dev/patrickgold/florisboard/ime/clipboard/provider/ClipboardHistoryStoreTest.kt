/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.clipboard.provider

import android.database.sqlite.SQLiteDatabaseCorruptException
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.IOException

class ClipboardHistoryStoreTest : FunSpec({
    test("only the platform corruption exception authorizes quarantine") {
        ClipboardHistoryStore.isDefinitiveCorruption(
            SQLiteDatabaseCorruptException("malformed database"),
        ) shouldBe true
        ClipboardHistoryStore.isDefinitiveCorruption(
            IOException("temporary read failure"),
        ) shouldBe false
        ClipboardHistoryStore.isDefinitiveCorruption(
            IllegalStateException("database read failed", IOException("disk unavailable")),
        ) shouldBe false
    }
})
