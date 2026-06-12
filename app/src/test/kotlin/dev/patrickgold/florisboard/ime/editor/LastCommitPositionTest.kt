/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.editor

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LastCommitPositionTest : FunSpec({
    test("selection staying at last commit position keeps adjacency") {
        val position = AbstractEditorInstance.LastCommitPosition()

        position.handleCommit(EditorRange.cursor(5))
        position.handleUpdateSelection(EditorRange.cursor(5))

        position.pos shouldBe 5
    }

    test("selection jumping before or after the last commit breaks adjacency") {
        val before = AbstractEditorInstance.LastCommitPosition()
        val after = AbstractEditorInstance.LastCommitPosition()

        before.handleCommit(EditorRange.cursor(5))
        after.handleCommit(EditorRange.cursor(5))
        before.handleUpdateSelection(EditorRange.cursor(2))
        after.handleUpdateSelection(EditorRange.cursor(8))

        before.pos shouldBe -1
        after.pos shouldBe -1
    }
})
