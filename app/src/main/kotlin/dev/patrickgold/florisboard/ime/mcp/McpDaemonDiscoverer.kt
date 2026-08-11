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

package dev.patrickgold.florisboard.ime.mcp

import dev.patrickgold.florisboard.ime.security.NoNetworkPermissionPolicy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * ROADMAP §10.5 L7.2 — Pure-Kotlin MCP daemon discovery pipeline.
 *
 * Takes a list of [DiscoveryCandidate]s — produced in production by
 * the `IntentResolver` wrapper that calls
 * `PackageManager.queryIntentServices(...)` for
 * [McpBridgeContract.ACTION_BIND_MCP_DAEMON] — and produces the
 * `Map<DaemonKey, DaemonEntry>` that feeds [McpDaemonRegistry.setActive].
 *
 * Validation rules (silently drop the candidate when violated; not
 * fatal — a single malformed daemon shouldn't break the registry):
 *
 *  1. Candidate package + class must be non-blank.
 *  2. Candidate must declare the
 *     [McpBridgeContract.PERMISSION_BIND_MCP] permission. This is
 *     enforced by PackageManager before reaching the discoverer; the
 *     discoverer re-checks defensively in case the caller hand-fed
 *     a fixture list.
 *  3. Every permission the candidate package requests must be in
 *     [NoNetworkPermissionPolicy.AllowedPermissions] or SwiftFloris's own
 *     signature-permission namespace. Anything else is a hard reject.
 *  4. Candidate must expose a signing-certificate fingerprint that is
 *     either co-signed with the IME or explicitly pinned by the user.
 *  5. Protocol version metadata must be in `1..SUPPORTED_PROTOCOL_VERSION`.
 *  6. Tool catalog JSON must parse + contain at least one entry with
 *     a non-blank `name`. Tools missing `description` or
 *     `parameterSchema` default to safe placeholders so a partial
 *     catalog still lights up something.
 *  7. Catalog payload must not exceed
 *     [McpBridgeContract.MAX_PAYLOAD_BYTES] — runaway-tool guard.
 *
 * The full Android-side wrapper that converts `ResolveInfo` →
 * [DiscoveryCandidate] is intentionally a thin shim and lives in the
 * adjacent `McpAndroidDiscoverer` (TBD); this object is the
 * platform-independent core that's directly testable.
 */
object McpDaemonDiscoverer {

    /**
     * Drive a discovery pass.  Returns the active registry map the
     * caller should hand to [McpDaemonRegistry.setActive].
     * [candidates] doesn't need to be sorted; the output preserves
     * insertion order.
     */
    fun discover(
        candidates: List<DiscoveryCandidate>,
        trustPolicy: McpDaemonTrustPolicy,
    ): Map<DaemonKey, DaemonEntry> =
        discoverSnapshot(candidates, trustPolicy).accepted

    /**
     * Full discovery result, including rejected daemons Settings can surface
     * for explicit certificate trust. Rejections happen before catalog parsing
     * whenever the daemon is not already trusted.
     */
    fun discoverSnapshot(
        candidates: List<DiscoveryCandidate>,
        trustPolicy: McpDaemonTrustPolicy,
    ): McpDiscoverySnapshot {
        val out = LinkedHashMap<DaemonKey, DaemonEntry>(candidates.size)
        val rejected = mutableListOf<RejectedMcpDaemon>()
        for (cand in candidates) {
            val rejection = validateCandidateBeforeCatalog(cand, trustPolicy)
            if (rejection != null) {
                rejected += rejection
                continue
            }
            val entry = buildEntry(cand) ?: continue
            out[entry.key] = entry
        }
        return McpDiscoverySnapshot(
            accepted = out,
            rejected = rejected.sortedWith(RejectedMcpDaemonDisplayOrder),
        )
    }

    private fun validateCandidateBeforeCatalog(
        cand: DiscoveryCandidate,
        trustPolicy: McpDaemonTrustPolicy,
    ): RejectedMcpDaemon? {
        if (cand.packageName.isBlank() || cand.daemonClassName.isBlank()) return null
        NoNetworkPermissionPolicy.firstDisallowed(cand.requestedPermissions)?.let { permission ->
            return cand.rejected(NoNetworkPermissionPolicy.rejectionReason(permission))
        }
        if (!cand.hasBindPermission) return null
        val signingCert = cand.signingCertSha256
        if (signingCert.isNullOrBlank()) {
            return cand.rejected(McpDaemonTrustPolicy.ReasonMissingSigningCertificate)
        }
        return when (trustPolicy.verdict(cand.packageName, signingCert)) {
            McpDaemonTrustVerdict.Accepted -> null
            McpDaemonTrustVerdict.ExplicitTrustRequired ->
                cand.rejected(McpDaemonTrustPolicy.ReasonExplicitTrustRequired)
            McpDaemonTrustVerdict.SigningCertificateChanged ->
                cand.rejected(McpDaemonTrustPolicy.ReasonSigningCertificateChanged)
        }
    }

    private fun buildEntry(cand: DiscoveryCandidate): DaemonEntry? {
        if (cand.packageName.isBlank() || cand.daemonClassName.isBlank()) return null
        if (!cand.hasBindPermission) return null
        val protocolVersion = cand.protocolVersion
        if (protocolVersion < 1) return null
        if (protocolVersion > McpBridgeContract.SUPPORTED_PROTOCOL_VERSION) return null
        if (cand.toolCatalogJson.isBlank()) return null
        if (cand.toolCatalogJson.length.toLong() > McpBridgeContract.MAX_PAYLOAD_BYTES) return null
        val tools = parseToolCatalog(cand.toolCatalogJson) ?: return null
        if (tools.isEmpty()) return null
        val key = DaemonKey(cand.packageName, cand.daemonClassName)
        return runCatching {
            DaemonEntry(key = key, protocolVersion = protocolVersion, tools = tools)
        }.getOrNull()
    }

    private fun parseToolCatalog(json: String): List<McpToolDescriptor>? {
        return runCatching {
            val root = JSON.parseToJsonElement(json).jsonObject
            val toolsArr: JsonArray = root["tools"]?.jsonArray ?: return null
            toolsArr.mapNotNull { entry ->
                val obj = (entry as? JsonObject) ?: return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (name.isEmpty()) return@mapNotNull null
                val description = obj["description"]?.jsonPrimitive?.contentOrNull
                    ?.takeIf { it.isNotBlank() } ?: "(no description provided)"
                val schema = obj["parameterSchema"]?.jsonPrimitive?.contentOrNull
                    ?.takeIf { it.isNotBlank() } ?: """{"type":"object"}"""
                McpToolDescriptor(
                    name = name,
                    description = description,
                    parameterSchemaJson = schema,
                )
            }
        }.getOrNull()
    }

    private val JSON = Json { ignoreUnknownKeys = true; isLenient = true }
}

/**
 * One candidate daemon as surfaced by the PackageManager scan.
 * Production callers build this from `ResolveInfo + ServiceInfo +
 * PackageManager.getServiceInfo` — see `McpAndroidDiscoverer`. Tests
 * build it directly.
 *
 *  - [protocolVersion] — value read from the
 *    [McpBridgeContract.METADATA_PROTOCOL_VERSION] meta-data attribute.
 *  - [hasBindPermission] — true when the service declares
 *    [McpBridgeContract.PERMISSION_BIND_MCP]; PackageManager normally
 *    enforces this for us, but the discoverer re-checks defensively.
 *  - [requestedPermissions] — every permission requested by the daemon
 *    package, regardless of current grant state.
 *  - [toolCatalogJson] — raw JSON pulled from the resource pointed to
 *    by [McpBridgeContract.METADATA_TOOL_CATALOG]. Expected shape:
 *    `{"tools": [{"name": "...", "description": "...", "parameterSchema": "..."}, ...]}`.
 */
data class DiscoveryCandidate(
    val packageName: String,
    val daemonClassName: String,
    val protocolVersion: Int,
    val hasBindPermission: Boolean,
    val signingCertSha256: String?,
    val toolCatalogJson: String,
    val requestedPermissions: Set<String> = emptySet(),
)

data class McpDiscoverySnapshot(
    val accepted: Map<DaemonKey, DaemonEntry>,
    val rejected: List<RejectedMcpDaemon>,
) {
    companion object {
        val Empty = McpDiscoverySnapshot(
            accepted = emptyMap(),
            rejected = emptyList(),
        )
    }
}

data class RejectedMcpDaemon(
    val packageName: String,
    val daemonClassName: String,
    val signingCertSha256: String?,
    val reason: String,
)

internal val RejectedMcpDaemonDisplayOrder: Comparator<RejectedMcpDaemon> =
    compareBy<RejectedMcpDaemon> { it.packageName }
        .thenBy { it.daemonClassName }
        .thenBy { it.reason }

data class McpDaemonTrustPolicy(
    val pinnedSigningCertificates: Map<String, String> = emptyMap(),
    val trustedRootSigningCertSha256: String? = null,
) {
    private val normalizedPinnedSigningCertificates = pinnedSigningCertificates
        .mapValues { (_, fingerprint) -> fingerprint.trim().uppercase() }
    private val normalizedTrustedRootSigningCertSha256 = trustedRootSigningCertSha256
        ?.trim()
        ?.uppercase()

    fun verdict(packageName: String, signingCertSha256: String): McpDaemonTrustVerdict {
        val fingerprint = signingCertSha256.trim().uppercase()
        val pinnedFingerprint = normalizedPinnedSigningCertificates[packageName]
        return when {
            pinnedFingerprint == null && fingerprint == normalizedTrustedRootSigningCertSha256 ->
                McpDaemonTrustVerdict.Accepted
            pinnedFingerprint == fingerprint ->
                McpDaemonTrustVerdict.Accepted
            pinnedFingerprint == null ->
                McpDaemonTrustVerdict.ExplicitTrustRequired
            else ->
                McpDaemonTrustVerdict.SigningCertificateChanged
        }
    }

    companion object {
        const val ReasonMissingSigningCertificate = "cannot read signing certificate"
        const val ReasonExplicitTrustRequired = "explicit trust required"
        const val ReasonSigningCertificateChanged = "signing certificate changed"
    }
}

enum class McpDaemonTrustVerdict {
    Accepted,
    ExplicitTrustRequired,
    SigningCertificateChanged,
}

private fun DiscoveryCandidate.rejected(reason: String): RejectedMcpDaemon =
    RejectedMcpDaemon(
        packageName = packageName,
        daemonClassName = daemonClassName,
        signingCertSha256 = signingCertSha256,
        reason = reason,
    )
