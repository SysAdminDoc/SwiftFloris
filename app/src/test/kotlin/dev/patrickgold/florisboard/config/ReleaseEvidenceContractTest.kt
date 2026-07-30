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

        script shouldContain "Invoke-EvidenceCommand \"trust-capability-gate-self-test\""
        script shouldContain "scripts/test-check-trust-capabilities.py"
        script shouldContain "Invoke-EvidenceCommand \"trust-capability-gate\""
        script shouldContain "scripts/check-trust-capabilities.py"
        checker shouldContain "app/src/main/AndroidManifest.xml"
        checker shouldContain "IMcpDaemon.aidl"
        checker shouldContain "gradle/libs.versions.toml"
        checker shouldContain "derive_optional_capabilities"
        checker shouldContain "validate_public_copy"
        registry shouldContain "\"clipboardHistory\": \"plaintext_room\""
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
