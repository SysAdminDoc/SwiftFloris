/*
 * Copyright (C) 2026 SwiftFloris Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.theme

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class ThemeStylesheetLoadPolicyTest : FunSpec({
    test("resolveStylesheetFile resolves nested stylesheets inside the loaded theme directory") {
        val root = Files.createTempDirectory("theme-root").toFile()
        try {
            val stylesheet = root.resolve("stylesheets/aurora.json")
            requireNotNull(stylesheet.parentFile).mkdirs()
            stylesheet.writeText("{}")

            ThemeStylesheetLoadPolicy.resolveStylesheetFile(
                loadedDir = root,
                stylesheetPath = "stylesheets/aurora.json",
            ) shouldBe stylesheet.canonicalFile
        } finally {
            root.deleteRecursively()
        }
    }

    test("resolveStylesheetFile rejects parent traversal into a sibling directory") {
        val parent = Files.createTempDirectory("theme-parent").toFile()
        try {
            val root = parent.resolve("theme")
            val sibling = parent.resolve("theme-sibling")
            root.mkdirs()
            sibling.mkdirs()
            sibling.resolve("stolen.json").writeText("{}")

            shouldThrow<IllegalStateException> {
                ThemeStylesheetLoadPolicy.resolveStylesheetFile(
                    loadedDir = root,
                    stylesheetPath = "../theme-sibling/stolen.json",
                )
            }
        } finally {
            parent.deleteRecursively()
        }
    }

    test("resolveStylesheetFile rejects absolute stylesheet paths") {
        val root = Files.createTempDirectory("theme-root").toFile()
        val outside = Files.createTempFile("theme-stolen", ".json").toFile()
        try {
            outside.writeText("{}")

            shouldThrow<IllegalStateException> {
                ThemeStylesheetLoadPolicy.resolveStylesheetFile(
                    loadedDir = root,
                    stylesheetPath = outside.absolutePath,
                )
            }
        } finally {
            root.deleteRecursively()
            outside.delete()
        }
    }

    test("resolveStylesheetFile rejects missing paths inside the loaded theme directory") {
        val root = Files.createTempDirectory("theme-root").toFile()
        try {
            shouldThrow<IllegalStateException> {
                ThemeStylesheetLoadPolicy.resolveStylesheetFile(
                    loadedDir = root,
                    stylesheetPath = "stylesheets/missing.json",
                )
            }
        } finally {
            root.deleteRecursively()
        }
    }

    test("cleanupLoadedDir removes failed unzip directories and children") {
        val parent = Files.createTempDirectory("theme-parent").toFile()
        try {
            val loadedDir = parent.resolve("loaded")
            val nested = loadedDir.resolve("stylesheets")
            nested.mkdirs()
            nested.resolve("bad.json").writeText("{")

            ThemeStylesheetLoadPolicy.cleanupLoadedDir(loadedDir)

            loadedDir.exists() shouldBe false
        } finally {
            parent.deleteRecursively()
        }
    }
})
