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

package dev.patrickgold.florisboard.ime.smartcompose

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private class StubProvider(
    private val ready: Boolean = true,
    private val result: RewriteResult = RewriteResult.Rewritten("stub-rewrite", RewriteTone.FORMAL),
) : RewriteProvider {
    var calls: Int = 0
        private set

    override fun isReady(tone: RewriteTone, sourceLanguageTag: String): Boolean = ready

    override fun rewrite(request: RewriteRequest): RewriteResult {
        calls++
        return result
    }
}

class RewriteRouterTest : FunSpec({

    test("happy path returns Rewritten with the provider output (fromCache = false on first call)") {
        val provider = StubProvider()
        val router = RewriteRouter(provider)

        val resp = router.rewrite(RewriteRequest(
            sourceText = "Hey, just checking in",
            tone = RewriteTone.FORMAL,
        ))

        val rewritten = resp as RewriteRouter.Response.Rewritten
        rewritten.rewrittenText shouldBe "stub-rewrite"
        rewritten.tone shouldBe RewriteTone.FORMAL
        rewritten.fromCache shouldBe false
        provider.calls shouldBe 1
    }

    test("second identical call returns the cached rewrite (fromCache = true)") {
        val provider = StubProvider()
        val router = RewriteRouter(provider)

        val request = RewriteRequest(
            sourceText = "Hey, just checking in",
            tone = RewriteTone.FORMAL,
        )
        router.rewrite(request)
        val second = router.rewrite(request)

        val rewritten = second as RewriteRouter.Response.Rewritten
        rewritten.fromCache shouldBe true
        provider.calls shouldBe 1
    }

    test("different tone for the same source text misses the cache") {
        val provider = StubProvider()
        val router = RewriteRouter(provider)

        router.rewrite(RewriteRequest("text", tone = RewriteTone.FORMAL))
        router.rewrite(RewriteRequest("text", tone = RewriteTone.SHORTER))

        provider.calls shouldBe 2
    }

    test("bypassCache = true skips the cache layer entirely") {
        val provider = StubProvider()
        val router = RewriteRouter(provider, bypassCache = true)

        val request = RewriteRequest("text", tone = RewriteTone.FORMAL)
        router.rewrite(request)
        router.rewrite(request)

        provider.calls shouldBe 2
    }

    test("missing consent short-circuits before any provider call") {
        val provider = StubProvider()
        val router = RewriteRouter(provider, isConsentGranted = { false })

        val resp = router.rewrite(RewriteRequest("text", tone = RewriteTone.FORMAL))

        (resp as RewriteRouter.Response.Suppressed).reason shouldBe "consent required"
        provider.calls shouldBe 0
    }

    test("password field short-circuits with the sensitive-field reason") {
        val provider = StubProvider()
        val router = RewriteRouter(provider)

        val resp = router.rewrite(RewriteRequest(
            sourceText = "secret",
            tone = RewriteTone.SHORTER,
            inputType = 0x81,
        ))

        (resp as RewriteRouter.Response.Suppressed).reason shouldBe "sensitive field"
        provider.calls shouldBe 0
    }

    test("blank input short-circuits with the blank-input reason") {
        val provider = StubProvider()
        val router = RewriteRouter(provider)

        val resp = router.rewrite(RewriteRequest(sourceText = "   ", tone = RewriteTone.LONGER))

        (resp as RewriteRouter.Response.Suppressed).reason shouldBe "blank input"
    }

    test("provider not ready for the requested tone surfaces the reason in Suppressed") {
        val provider = StubProvider(ready = false)
        val router = RewriteRouter(provider)

        val resp = router.rewrite(RewriteRequest("hi", tone = RewriteTone.CASUAL, sourceLanguageTag = "ja"))

        val sup = resp as RewriteRouter.Response.Suppressed
        sup.reason shouldBe "provider not ready for CASUAL/ja"
    }

    test("provider Unavailable result maps to Response.Suppressed with the reason preserved") {
        val provider = StubProvider(result = RewriteResult.Unavailable("no model"))
        val router = RewriteRouter(provider)

        val resp = router.rewrite(RewriteRequest("text", tone = RewriteTone.SUMMARIZE))

        (resp as RewriteRouter.Response.Suppressed).reason shouldBe "no model"
    }

    test("provider Failed result maps to Response.Failed with the reason preserved") {
        val provider = StubProvider(result = RewriteResult.Failed("OOM during decode"))
        val router = RewriteRouter(provider)

        val resp = router.rewrite(RewriteRequest("text", tone = RewriteTone.SHORTER))

        (resp as RewriteRouter.Response.Failed).reason shouldBe "OOM during decode"
    }

    test("clearCache drops cached entries") {
        val provider = StubProvider()
        val router = RewriteRouter(provider)
        val request = RewriteRequest("text", tone = RewriteTone.FORMAL)

        router.rewrite(request)
        router.clearCache()
        router.rewrite(request)

        provider.calls shouldBe 2
    }

    test("NoOpRewriteProvider always returns Unavailable") {
        NoOpRewriteProvider.isReady(RewriteTone.FORMAL, "en") shouldBe false
        val result = NoOpRewriteProvider.rewrite(RewriteRequest("text", tone = RewriteTone.FORMAL))
        (result as RewriteResult.Unavailable).reason shouldBe "no rewrite provider installed"
    }

    test("RewriteProviderRegistry round-trips setActive / reset") {
        try {
            val custom = StubProvider()
            RewriteProviderRegistry.active shouldBe NoOpRewriteProvider
            RewriteProviderRegistry.setActive(custom)
            RewriteProviderRegistry.active shouldBe custom
            RewriteProviderRegistry.reset()
            RewriteProviderRegistry.active shouldBe NoOpRewriteProvider
        } finally {
            RewriteProviderRegistry.reset()
        }
    }
})
