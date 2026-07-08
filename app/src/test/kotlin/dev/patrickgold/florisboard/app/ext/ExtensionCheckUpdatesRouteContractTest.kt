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

package dev.patrickgold.florisboard.app.ext

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.string.shouldNotContain
import java.io.File

class ExtensionCheckUpdatesRouteContractTest : FunSpec({
    test("orphan check-updates route and title resource stay removed") {
        val routesSource = locateProjectFile(
            "app/src/main/kotlin/dev/patrickgold/florisboard/app/Routes.kt",
        ).readText()
        routesSource shouldNotContain "CheckUpdates"
        routesSource shouldNotContain "ext/check-updates"

        val screenReferences = locateProjectDir(
            "app/src/main/kotlin/dev/patrickgold/florisboard/app/ext",
        ).walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("CheckUpdatesScreen") }
            .map { it.invariantSeparatorsPath }
            .toList()
        screenReferences.shouldBeEmpty()

        val titleResources = locateProjectDir("app/src/main/res").walkTopDown()
            .filter { it.isFile && it.name == "strings.xml" }
            .filter { it.readText().contains("ext__check_updates__title") }
            .map { it.invariantSeparatorsPath }
            .toList()
        titleResources.shouldBeEmpty()
    }
})

private fun locateProjectFile(path: String): File {
    return sequenceOf(File(path), File("../$path"))
        .firstOrNull { it.exists() && it.canRead() }
        ?: error("File is not reachable from ${File(".").absolutePath}: $path")
}

private fun locateProjectDir(path: String): File {
    return sequenceOf(File(path), File("../$path"))
        .firstOrNull { it.exists() && it.isDirectory }
        ?: error("Directory is not reachable from ${File(".").absolutePath}: $path")
}
