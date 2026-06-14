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

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Bundle
import dev.patrickgold.florisboard.app.settings.about.SigningFingerprint

/**
 * ROADMAP §10.5 L7.5 — Android wrapper that converts
 * `PackageManager.queryIntentServices` results into
 * [DiscoveryCandidate]s for [McpDaemonDiscoverer]. Sits between the
 * platform API and the platform-independent core.
 *
 * Lookup pipeline:
 *  1. Query services matching the [McpBridgeContract.ACTION_BIND_MCP_DAEMON]
 *     intent + `GET_META_DATA` flag.
 *  2. For each [ResolveInfo], extract the package/class names,
 *     protocol version meta-data, tool-catalog resource pointer.
 *  3. Read the daemon package's signing-certificate fingerprint.
 *  4. Resolve the tool-catalog resource through the daemon's own
 *     `Resources` (via `Context.createPackageContext`) so we can read
 *     the JSON without needing a content URI handshake.
 *  5. Confirm the daemon advertises the signature-protected
 *     [McpBridgeContract.PERMISSION_BIND_MCP] on its `<service>`.
 *  6. Hand the [DiscoveryCandidate] list to [McpDaemonDiscoverer]
 *     for validation + parse-and-build into a [DaemonEntry] map.
 *
 * **Pure-JVM testability:** the Android-bound pipeline is decomposed
 * into [resolveInfoToCandidate] (visible-for-test) which consumes a
 * pre-built [ResolveInfo] + a `catalogLookup` callback (so we don't
 * have to mock `Context.createPackageContext` / `Resources.openRawResource`
 * to exercise the candidate-shaping logic). The full runDiscovery
 * glue uses real Android objects and is thin enough that visual
 * inspection covers it.
 */
object McpAndroidDiscoverer {

    /**
     * Drive a full discovery pass against [PackageManager]. Returns
     * the map [McpDaemonRegistry.setActive] consumes. Safe to call
     * from a background thread; the [PackageManager] APIs we use
     * (`queryIntentServices`, `createPackageContext`,
     * `Resources.openRawResource`) are thread-safe and quick.
     */
    fun runDiscovery(
        context: Context,
        persistedSigningPinsRaw: String = "",
        trustedRootSigningCertSha256: String? = SigningFingerprint.sha256(context),
    ): Map<DaemonKey, DaemonEntry> =
        runDiscoverySnapshot(
            context = context,
            persistedSigningPinsRaw = persistedSigningPinsRaw,
            trustedRootSigningCertSha256 = trustedRootSigningCertSha256,
        ).accepted

    /**
     * Drive discovery and return both accepted daemons and rejected trust
     * candidates so Settings can offer explicit pinning without binding an
     * untrusted package first.
     */
    fun runDiscoverySnapshot(
        context: Context,
        persistedSigningPinsRaw: String,
        trustedRootSigningCertSha256: String?,
    ): McpDiscoverySnapshot {
        val pm = context.packageManager
        val resolveInfos = queryServices(pm)
        val candidates = resolveInfos.mapNotNull { info ->
            val attrs = serviceAttrsFrom(info) ?: return@mapNotNull null
            val attrsWithSigning = attrs.copy(
                signingCertSha256 = readSigningFingerprint(context, attrs.packageName),
            )
            shapeCandidate(attrsWithSigning) { packageName, resourceId ->
                readCatalogFromPackage(context, packageName, resourceId)
            }
        }
        val pinSet = McpSigningPinSet.parse(persistedSigningPinsRaw)
        return McpDaemonDiscoverer.discoverSnapshot(
            candidates = candidates,
            trustPolicy = McpDaemonTrustPolicy(
                pinnedSigningCertificates = pinSet.asMap(),
                trustedRootSigningCertSha256 = trustedRootSigningCertSha256,
            ),
        )
    }

    /**
     * Visible-for-test: shape a [ServiceAttrs] into a
     * [DiscoveryCandidate] via the [catalogLookup] callback. Returns
     * null when the attributes fail any validation check:
     *  - blank package / class name,
     *  - protocol version < 1 (default sentinel `-1`),
     *  - catalog resource id == 0,
     *  - catalog lookup that returns null/blank.
     */
    internal fun shapeCandidate(
        attrs: ServiceAttrs,
        catalogLookup: (packageName: String, catalogResourceId: Int) -> String?,
    ): DiscoveryCandidate? {
        if (attrs.packageName.isBlank()) return null
        if (attrs.className.isBlank()) return null
        if (attrs.protocolVersion < 1) return null
        if (attrs.catalogResourceId == 0) return null
        val catalogJson = catalogLookup(attrs.packageName, attrs.catalogResourceId) ?: return null
        if (catalogJson.isBlank()) return null
        return DiscoveryCandidate(
            packageName = attrs.packageName,
            daemonClassName = attrs.className,
            protocolVersion = attrs.protocolVersion,
            hasBindPermission = attrs.permission == McpBridgeContract.PERMISSION_BIND_MCP,
            signingCertSha256 = attrs.signingCertSha256,
            toolCatalogJson = catalogJson,
        )
    }

    /**
     * Lift the platform-bound bits out of [ResolveInfo] into a flat
     * data class. Returns null when the ResolveInfo isn't a Service
     * (the intent filter targets services only) or when meta-data
     * is entirely missing.
     */
    internal fun serviceAttrsFrom(info: ResolveInfo): ServiceAttrs? {
        val service = info.serviceInfo ?: return null
        val metaData: Bundle = service.metaData ?: return null
        return ServiceAttrs(
            packageName = service.packageName.orEmpty(),
            className = service.name.orEmpty(),
            permission = service.permission,
            protocolVersion = metaData.getInt(
                McpBridgeContract.METADATA_PROTOCOL_VERSION,
                /* defaultValue = */ -1,
            ),
            catalogResourceId = metaData.getInt(
                McpBridgeContract.METADATA_TOOL_CATALOG,
                /* defaultValue = */ 0,
            ),
            signingCertSha256 = null,
        )
    }

    /** Flat record of the `<service>` + `<meta-data>` shape we care about. */
    internal data class ServiceAttrs(
        val packageName: String,
        val className: String,
        val permission: String?,
        val protocolVersion: Int,
        val catalogResourceId: Int,
        val signingCertSha256: String?,
    )

    private fun queryServices(pm: PackageManager): List<ResolveInfo> {
        val intent = Intent(McpBridgeContract.ACTION_BIND_MCP_DAEMON)
        return runCatching {
            @Suppress("DEPRECATION") // The PackageManager.ResolveInfoFlags API
            // returns the same data on API 33+; the legacy overload keeps
            // us compatible across the full minSdk 26 target range.
            pm.queryIntentServices(intent, PackageManager.GET_META_DATA)
        }.getOrDefault(emptyList())
    }

    /**
     * Read the tool-catalog JSON from the daemon package's raw
     * resources. Catalog must live under `res/raw/<name>` (referenced
     * by `R.raw.<name>` from the daemon's manifest meta-data).
     * Returns null on any IO / resource-not-found failure — the
     * candidate is then dropped at the discoverer level.
     */
    private fun readCatalogFromPackage(
        context: Context,
        packageName: String,
        resourceId: Int,
    ): String? {
        return runCatching {
            val pkgContext = context.createPackageContext(packageName, /* flags = */ 0)
            pkgContext.resources.openRawResource(resourceId).use { stream ->
                readBounded(stream, McpBridgeContract.MAX_PAYLOAD_BYTES)
            }
        }.getOrNull()
    }

    /**
     * Reads at most [limitBytes] from [stream], returning null if the source
     * exceeds the cap. Discovery runs for any sibling package that merely
     * declares the bind service — trust and the [McpBridgeContract.MAX_PAYLOAD_BYTES]
     * catalog-size check are only applied later, so the raw read must be bounded
     * here to stop an untrusted package from OOM-ing the IME with a giant
     * `res/raw` blob before it is ever rejected.
     */
    private fun readBounded(stream: java.io.InputStream, limitBytes: Long): String? {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
            if (total > limitBytes) return null
            out.write(buffer, 0, read)
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    private fun readSigningFingerprint(context: Context, packageName: String): String? =
        runCatching { SigningFingerprint.sha256OfPackage(context, packageName) }.getOrNull()
}
