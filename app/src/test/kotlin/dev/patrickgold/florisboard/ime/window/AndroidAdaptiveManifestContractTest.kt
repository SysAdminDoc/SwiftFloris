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

package dev.patrickgold.florisboard.ime.window

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File

class AndroidAdaptiveManifestContractTest : FunSpec({
    test("main manifest does not opt out of Android 16/17 large-screen adaptive behavior") {
        val manifest = locateProjectFile(
            "app/src/main/AndroidManifest.xml",
            "src/main/AndroidManifest.xml",
        ).readText()

        manifest shouldContain "android:windowSoftInputMode=\"adjustResize\""
        manifest shouldNotContain "android:screenOrientation"
        manifest shouldNotContain "android:resizeableActivity"
        manifest shouldNotContain "android:minAspectRatio"
        manifest shouldNotContain "android:maxAspectRatio"
        manifest shouldNotContain "android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY"
        manifest shouldNotContain "android:appCategory=\"game\""
    }
})

private fun locateProjectFile(vararg paths: String): File {
    return paths.asSequence()
        .map { File(it) }
        .firstOrNull { it.exists() && it.canRead() }
        ?: error("None of these files are reachable from ${File(".").absolutePath}: ${paths.joinToString()}")
}
