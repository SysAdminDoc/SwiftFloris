package dev.patrickgold.florisboard.config

import io.kotest.matchers.string.shouldContain
import java.io.File
import kotlin.test.Test

class ReleaseEvidenceContractTest {
    @Test
    fun `release evidence derives high risk trust claims from live sources`() {
        val script = locateProjectFile("scripts/release-evidence.ps1").readText()
        val checker = locateProjectFile("scripts/check-trust-capabilities.py").readText()
        val registry = locateProjectFile(
            "app/src/main/config/trust-capabilities.json",
        ).readText()
        val readme = locateProjectFile("README.md").readText()

        script shouldContain "Invoke-EvidenceCommand \"trust-capability-gate\""
        script shouldContain "scripts/check-trust-capabilities.py"
        checker shouldContain "app/src/main/AndroidManifest.xml"
        checker shouldContain "IMcpDaemon.aidl"
        checker shouldContain "gradle/libs.versions.toml"
        checker shouldContain "derive_optional_capabilities"
        checker shouldContain "validate_public_copy"
        registry shouldContain "\"clipboardHistory\": \"encrypted_or_unknown\""
        registry shouldContain "\"localVoiceRecognizer\": \"preview_only\""
        checker shouldContain "NoNetworkPermissionPolicy.kt"
        registry shouldContain "\"daemonNetworkPermissionsRejected\": true"
        readme shouldContain "app/src/main/config/trust-capabilities.json"
    }

    @Test
    fun `release evidence includes runBlocking drift gate`() {
        val script = locateProjectFile("scripts/release-evidence.ps1").readText()
        val readme = locateProjectFile("README.md").readText()

        script shouldContain "Invoke-EvidenceCommand \"runblocking-allowlist\""
        script shouldContain "scripts/check-runblocking-allowlist.py"
        readme shouldContain "production `runBlocking` allowlist"
    }

    @Test
    fun `every release gate is wired or has an explicit manual reason`() {
        val runner = locateProjectFile("scripts/release-evidence.ps1").readText()
        val normalizedRunner = runner.replace('\\', '/')
        val scriptsDirectory = locateProjectDirectory("scripts")
        val manualManifest = locateProjectFile("scripts/release-evidence-manual-gates.tsv")
        val manualEntries = parseManualGateManifest(manualManifest)
        val manualGateNames = manualEntries.keys.map { it.substringAfterLast('/') }.toSet()
        val gateScripts = scriptsDirectory.listFiles()
            ?.filter { file ->
                file.isFile && (file.name.startsWith("check-") || file.name.startsWith("verify-"))
            }
            ?.associateBy { it.name }
            .orEmpty()

        val unknownManualEntries = manualGateNames - gateScripts.keys
        if (unknownManualEntries.isNotEmpty()) {
            throw AssertionError("Manual release gates do not exist on disk: $unknownManualEntries")
        }

        val uncovered = gateScripts.keys.filter { name ->
            !normalizedRunner.contains("scripts/$name") && !manualGateNames.contains(name)
        }
        if (uncovered.isNotEmpty()) {
            throw AssertionError("Release gate scripts are neither wired nor manual: $uncovered")
        }

        runner shouldContain "-Filter \"test-*.py\""
        runner shouldContain "release-evidence-manual-gates.tsv"
    }

    @Test
    fun `release evidence discovers every script self-test`() {
        val runner = locateProjectFile("scripts/release-evidence.ps1").readText()
        val scriptsDirectory = locateProjectDirectory("scripts")
        val selfTests = scriptsDirectory.listFiles()
            ?.filter { file -> file.isFile && file.name.startsWith("test-") && file.name.endsWith(".py") }
            .orEmpty()

        runner shouldContain "Get-ChildItem"
        runner shouldContain "selfTestScripts"
        if (selfTests.isEmpty()) {
            throw AssertionError("The scripts directory must contain at least one self-test")
        }
    }

    @Test
    fun `strict release evidence requires signing material`() {
        val script = locateProjectFile("scripts/release-evidence.ps1").readText()
        val buildScript = locateProjectFile("app/build.gradle.kts").readText()

        script shouldContain "Assert-StrictReleaseSigning"
        script shouldContain "KEYSTORE_PATH"
        script shouldContain "SIGNING_KEYSTORE_PASSWORD"
        script shouldContain "SIGNING_KEY_ALIAS"
        script shouldContain "SIGNING_KEY_PASSWORD"
        script shouldContain "StrictRelease requires release signing environment variables"
        buildScript shouldContain "maintainer's local release environment"
    }

    @Test
    fun `release comments do not reference removed remote build workflow`() {
        val buildScript = locateProjectFile("app/build.gradle.kts").readText()

        if (buildScript.contains("GitHub release workflow")) {
            throw AssertionError("release signing must stay documented as a local-maintainer flow")
        }
        if (buildScript.contains("CI pull request")) {
            throw AssertionError("visual checks must stay documented as local release gates")
        }
    }
}

private fun locateProjectFile(path: String): File {
    return sequenceOf(File(path), File("../$path"))
        .firstOrNull { it.isFile }
        ?: error("File is not reachable from ${File(".").absolutePath}: $path")
}

private fun locateProjectDirectory(path: String): File {
    return sequenceOf(File(path), File("../$path"))
        .firstOrNull { it.isDirectory }
        ?: error("Directory is not reachable from ${File(".").absolutePath}: $path")
}

private fun parseManualGateManifest(path: File): Map<String, String> {
    val entries = linkedMapOf<String, String>()
    path.readLines().forEachIndexed { index, raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
        val fields = raw.split('\t', limit = 2)
        if (fields.size != 2 || fields[0].isBlank() || fields[1].isBlank()) {
            throw AssertionError("Malformed manual gate entry at ${path.path}:${index + 1}")
        }
        if (entries.put(fields[0].trim(), fields[1].trim()) != null) {
            throw AssertionError("Duplicate manual gate entry at ${path.path}:${index + 1}")
        }
    }
    return entries
}
