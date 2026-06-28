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
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.florisboard.lib.snygg.value.SnyggAssetResolveException
import org.florisboard.lib.snygg.value.SnyggDefaultAssetResolver

class SnyggDefaultAssetResolverTest : FunSpec({

    test("default resolver returns typed failure for any URI") {
        val result = SnyggDefaultAssetResolver.resolveAbsolutePath("flex:/some/theme/asset.png")
        result.shouldBeFailure()
        result.exceptionOrNull().shouldBeInstanceOf<SnyggAssetResolveException>()
    }

    test("failure message includes the requested URI") {
        val uri = "flex:/custom/font.ttf"
        val result = SnyggDefaultAssetResolver.resolveAbsolutePath(uri)
        result.exceptionOrNull()!!.message.shouldContain(uri)
    }

    test("getOrNull returns null for default resolver") {
        SnyggDefaultAssetResolver.resolveAbsolutePath("flex:/anything").getOrNull().shouldBeNull()
    }
})
