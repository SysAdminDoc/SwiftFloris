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

package dev.patrickgold.florisboard.ime.editor

import android.view.inputmethod.InputConnection
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Replays the host-desync defect family three other Android keyboards report
 * against rich-text and web editors:
 *
 *  - FlorisBoard #3310 — text spam and failed deletion in the Google Keep web editor
 *  - HeliBoard #2702 — text duplicated when deleting on certain websites
 *  - AnySoftKeyboard #4812 / #4856 — duplicated words, cursor jumps mid-text
 *
 * The shared cause is a host that does not honour `setComposingRegion`. It
 * returns `true` and then ignores the call, so the IME believes a span is
 * marked for replacement while the host has nothing marked. The next
 * `setComposingText` therefore *inserts* at the cursor instead of replacing,
 * and the user sees their word twice.
 *
 * [FakeHostEditor] is a real text buffer, not a call recorder: it applies each
 * `InputConnection` operation the way `BaseInputConnection` would, so these
 * cases assert the text the user ends up with rather than the call sequence
 * the IME intended. `HostileEditorCandidateReplayTest` covers the call
 * sequences; this file covers the resulting document.
 */
class HostDesyncReplayTest : FunSpec({

    test("cooperative host: replacing a typed word leaves exactly one copy") {
        val host = FakeHostEditor(text = "hello wor", selectionStart = 9)

        EditorInputConnectionBatch.replacePreviousWithComposingRegion(
            ic = host.connection,
            replaceStart = 6,
            replaceEnd = 9,
            text = "world",
            composing = EditorRange.Unspecified,
        )

        host.text shouldBe "hello world"
        host.selectionStart shouldBe 11
    }

    test("the old composing-region strategy is what duplicated the word") {
        // Regression witness for why replacePreviousWithComposingRegion no
        // longer marks a composing region. This is the exact call sequence it
        // used to issue; against a host that ignores setComposingRegion it
        // inserts instead of replacing, which is the reported defect.
        val host = FakeHostEditor(
            text = "hello wor",
            selectionStart = 9,
            ignoresComposingRegion = true,
        )

        EditorInputConnectionBatch.runWithBatchEdit(host.connection) {
            setComposingRegion(6, 9)
            setComposingText("world", 1)
        }

        host.text shouldBe "hello worworld"
    }

    test("the shipped path survives the same host") {
        val host = FakeHostEditor(
            text = "hello wor",
            selectionStart = 9,
            ignoresComposingRegion = true,
        )

        EditorInputConnectionBatch.replacePreviousWithComposingRegion(
            ic = host.connection,
            replaceStart = 6,
            replaceEnd = 9,
            text = "world",
            composing = EditorRange.Unspecified,
        )

        host.text shouldBe "hello world"
    }

    test("selection-based replacement is duplication-free on a host that ignores composing regions") {
        val host = FakeHostEditor(
            text = "hello wor",
            selectionStart = 9,
            ignoresComposingRegion = true,
        )

        EditorInputConnectionBatch.replaceRangeBySelection(
            ic = host.connection,
            replaceStart = 6,
            replaceEnd = 9,
            text = "world",
            composing = EditorRange.Unspecified,
        )

        host.text shouldBe "hello world"
        host.selectionStart shouldBe 11
    }

    test("selection-based replacement matches the composing path on a cooperative host") {
        val host = FakeHostEditor(text = "hello wor", selectionStart = 9)

        EditorInputConnectionBatch.replaceRangeBySelection(
            ic = host.connection,
            replaceStart = 6,
            replaceEnd = 9,
            text = "world",
            composing = EditorRange.Unspecified,
        )

        host.text shouldBe "hello world"
        host.selectionStart shouldBe 11
    }

    test("mid-text replacement keeps the trailing text intact") {
        // AnySoftKeyboard #4856: editing an existing word mid-document moved
        // the cursor and mangled what followed.
        val host = FakeHostEditor(
            text = "the qick brown fox",
            selectionStart = 8,
            ignoresComposingRegion = true,
        )

        EditorInputConnectionBatch.replaceRangeBySelection(
            ic = host.connection,
            replaceStart = 4,
            replaceEnd = 8,
            text = "quick",
            composing = EditorRange.Unspecified,
        )

        host.text shouldBe "the quick brown fox"
        host.selectionStart shouldBe 9
    }

    test("replacing the whole buffer neither duplicates nor drops characters") {
        val host = FakeHostEditor(
            text = "teh",
            selectionStart = 3,
            ignoresComposingRegion = true,
        )

        EditorInputConnectionBatch.replaceRangeBySelection(
            ic = host.connection,
            replaceStart = 0,
            replaceEnd = 3,
            text = "the",
            composing = EditorRange.Unspecified,
        )

        host.text shouldBe "the"
        host.selectionStart shouldBe 3
    }

    test("an out-of-range span is refused rather than corrupting the document") {
        // A host that has already dropped text can report a stale range back.
        // Clamping silently would delete the wrong span; refusing leaves the
        // document exactly as the user last saw it.
        val host = FakeHostEditor(text = "hi", selectionStart = 2)

        EditorInputConnectionBatch.replaceRangeBySelection(
            ic = host.connection,
            replaceStart = 4,
            replaceEnd = 9,
            text = "world",
            composing = EditorRange.Unspecified,
        )

        host.text shouldBe "hi"
    }

    test("batch depth is balanced even when the host refuses every operation") {
        val host = FakeHostEditor(
            text = "hello wor",
            selectionStart = 9,
            ignoresComposingRegion = true,
            refusesEdits = true,
        )

        EditorInputConnectionBatch.replaceRangeBySelection(
            ic = host.connection,
            replaceStart = 6,
            replaceEnd = 9,
            text = "world",
            composing = EditorRange.Unspecified,
        )

        host.batchDepth shouldBe 0
        host.text shouldBe "hello wor"
    }
})

/**
 * A minimal but *stateful* `InputConnection`: it keeps a real buffer and
 * applies edits the way `BaseInputConnection` does, so a test can assert the
 * document the user would see.
 *
 * @param ignoresComposingRegion models the reported hosts — `setComposingRegion`
 *   reports success and does nothing, so the IME's idea of the marked span and
 *   the host's disagree.
 * @param refusesEdits models a host that has torn down its editor: every
 *   mutation returns false.
 */
private class FakeHostEditor(
    text: String,
    selectionStart: Int,
    selectionEnd: Int = selectionStart,
    private val ignoresComposingRegion: Boolean = false,
    private val refusesEdits: Boolean = false,
) : InvocationHandler {
    private val buffer = StringBuilder(text)
    var selectionStart: Int = selectionStart
        private set
    var selectionEnd: Int = selectionEnd
        private set
    private var composingStart = -1
    private var composingEnd = -1
    var batchDepth = 0
        private set

    val text: String get() = buffer.toString()

    val connection: InputConnection = Proxy.newProxyInstance(
        InputConnection::class.java.classLoader,
        arrayOf(InputConnection::class.java),
        this,
    ) as InputConnection

    override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
        return when (method.name) {
            "beginBatchEdit" -> { batchDepth += 1; true }
            "endBatchEdit" -> { batchDepth -= 1; batchDepth > 0 }
            "setSelection" -> setSelection(args.int(0), args.int(1))
            "setComposingRegion" -> setComposingRegion(args.int(0), args.int(1))
            "setComposingText" -> setComposingText(args.text(0))
            "finishComposingText" -> { composingStart = -1; composingEnd = -1; true }
            "commitText" -> commitText(args.text(0))
            "getTextBeforeCursor" -> buffer.substring(
                (selectionStart - args.int(0)).coerceAtLeast(0),
                selectionStart,
            )
            "getTextAfterCursor" -> buffer.substring(
                selectionEnd,
                (selectionEnd + args.int(0)).coerceAtMost(buffer.length),
            )
            "getSelectedText" ->
                if (selectionStart == selectionEnd) null else buffer.substring(selectionStart, selectionEnd)
            "equals" -> proxy === args?.get(0)
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "FakeHostEditor(\"$buffer\", sel=$selectionStart..$selectionEnd)"
            else -> if (method.returnType == java.lang.Boolean.TYPE) false else null
        }
    }

    private fun setSelection(start: Int, end: Int): Boolean {
        if (refusesEdits) return false
        if (start !in 0..buffer.length || end !in 0..buffer.length || start > end) return false
        selectionStart = start
        selectionEnd = end
        return true
    }

    private fun setComposingRegion(start: Int, end: Int): Boolean {
        if (refusesEdits) return false
        // The defect: report success, change nothing.
        if (ignoresComposingRegion) return true
        if (start !in 0..buffer.length || end !in 0..buffer.length || start > end) return false
        composingStart = start
        composingEnd = end
        return true
    }

    private fun setComposingText(text: String): Boolean {
        if (refusesEdits) return false
        val start = replaceTargetStart()
        val end = replaceTargetEnd()
        buffer.replace(start, end, text)
        composingStart = start
        composingEnd = start + text.length
        selectionStart = composingEnd
        selectionEnd = composingEnd
        return true
    }

    private fun commitText(text: String): Boolean {
        if (refusesEdits) return false
        val start = replaceTargetStart()
        val end = replaceTargetEnd()
        buffer.replace(start, end, text)
        composingStart = -1
        composingEnd = -1
        selectionStart = start + text.length
        selectionEnd = selectionStart
        return true
    }

    /** Composing region wins when set, else the selection — as in `BaseInputConnection`. */
    private fun replaceTargetStart(): Int =
        if (composingStart >= 0) composingStart else selectionStart

    private fun replaceTargetEnd(): Int =
        if (composingEnd >= 0) composingEnd else selectionEnd

    private fun Array<out Any?>?.int(index: Int): Int = (this?.get(index) as? Int) ?: 0

    private fun Array<out Any?>?.text(index: Int): String = (this?.get(index) as? CharSequence)?.toString().orEmpty()
}
