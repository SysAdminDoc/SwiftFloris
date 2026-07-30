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

package dev.patrickgold.florisboard.lib.io

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class AtomicFileWriterTest : FunSpec({
    test("successful replacement stages beside the target and publishes only validated bytes") {
        withAtomicTarget { root, target ->
            AtomicFileWriter.replace(
                targetFile = target,
                write = { staged ->
                    staged.parentFile?.canonicalFile shouldBe root.canonicalFile
                    target.readText() shouldBe "previous"
                    staged.writeText("replacement")
                },
                validate = { staged ->
                    target.readText() shouldBe "previous"
                    staged.readText() shouldBe "replacement"
                },
            )

            target.readText() shouldBe "replacement"
            stagingFiles(root, target.name).shouldBeEmpty()
        }
    }

    test("a partial write failure retains the previous target and removes staging") {
        withAtomicTarget { root, target ->
            shouldThrow<InjectedAtomicFailure> {
                AtomicFileWriter.replace(
                    targetFile = target,
                    write = { staged ->
                        staged.writeText("partial")
                        throw InjectedAtomicFailure()
                    },
                    validate = {},
                )
            }

            target.readText() shouldBe "previous"
            stagingFiles(root, target.name).shouldBeEmpty()
        }
    }

    test("a validation failure retains the previous target and removes staging") {
        withAtomicTarget { root, target ->
            shouldThrow<InjectedAtomicFailure> {
                AtomicFileWriter.replace(
                    targetFile = target,
                    write = { staged -> staged.writeText("invalid") },
                    validate = {
                        target.readText() shouldBe "previous"
                        throw InjectedAtomicFailure()
                    },
                )
            }

            target.readText() shouldBe "previous"
            stagingFiles(root, target.name).shouldBeEmpty()
        }
    }

    test("a replacement failure retains the previous target and removes staging") {
        withAtomicTarget { root, target ->
            shouldThrow<InjectedAtomicFailure> {
                AtomicFileWriter.replace(
                    targetFile = target,
                    mover = AtomicFileMover { staged, currentTarget ->
                        staged.readText() shouldBe "replacement"
                        currentTarget.readText() shouldBe "previous"
                        throw InjectedAtomicFailure()
                    },
                    write = { staged -> staged.writeText("replacement") },
                    validate = {},
                )
            }

            target.readText() shouldBe "previous"
            stagingFiles(root, target.name).shouldBeEmpty()
        }
    }
})

private class InjectedAtomicFailure : IllegalStateException("injected failure")

private inline fun withAtomicTarget(block: (root: java.io.File, target: java.io.File) -> Unit) {
    val root = Files.createTempDirectory("atomic-file-writer").toFile()
    try {
        val target = root.resolve("extension.flex")
        target.writeText("previous")
        block(root, target)
    } finally {
        root.deleteRecursively()
    }
}

private fun stagingFiles(root: java.io.File, targetName: String): List<java.io.File> {
    return root.listFiles().orEmpty()
        .filter { it.name.startsWith(".$targetName.") && it.name.endsWith(".tmp") }
}
