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

package dev.patrickgold.florisboard.ime.text.gestures

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import java.io.File

class GlidePreviewCancellationContractTest : FunSpec({
    test("cancelled glides cancel the pending preview job") {
        val source = locateGestureSource("GlideTypingManager.kt").readText()
        val normalized = source.replace(Regex("\\s+"), " ")

        normalized shouldContain "override fun onGlideCancelled() { " +
            "cancelPreviewJob() glideTypingClassifier.clear() pendingContextRescore = null }"
        normalized shouldContain "private fun cancelPreviewJob() { previewJob?.cancel() previewJob = null }"
    }

    test("empty glide snapshots return before cache or classification work") {
        val source = locateGestureSource("StatisticalGlideTypingClassifier.kt").readText()
        val guardIndex = source.indexOf("if (snapshot.isEmpty) return emptyList()")
        val subtypeIndex = source.indexOf("val subtype = currentSubtype ?: return emptyList()")

        guardIndex shouldNotBe -1
        subtypeIndex shouldNotBe -1
        (guardIndex < subtypeIndex) shouldBe true
    }
})

private fun locateGestureSource(fileName: String): File {
    val candidates = listOf(
        "app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/gestures/$fileName",
        "src/main/kotlin/dev/patrickgold/florisboard/ime/text/gestures/$fileName",
    )
    return candidates.map(::File).firstOrNull { it.exists() && it.canRead() }
        ?: error("$fileName not reachable from working directory ${File(".").absolutePath}")
}
