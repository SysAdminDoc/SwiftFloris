// Copyright (C) 2026 SwiftFloris Contributors
// SPDX-License-Identifier: Apache-2.0
//
// ROADMAP §10.5 L7.4 — AIDL surface bound by the IME to a third-party
// MCP daemon. The daemon advertises an Android `Service` that returns
// this Binder from `onBind`, gated by `permission.BIND_MCP` per
// `McpBridgeContract.PERMISSION_BIND_MCP`.
//
// Wire format: both methods exchange JSON strings — the same envelope
// `McpEnvelopeCodec.encode/decode` round-trips for `McpToolCallRequest`
// and `McpToolCallResponse`. Keeping the AIDL surface payload-typed
// as `String` (not `Bundle` or `Parcelable`) means daemons written in
// any JVM language interop without needing the kotlinx.serialization
// runtime on their side — they parse the JSON themselves.
//
// Methods are non-oneway because the IME-side `McpClient.callTool`
// contract is synchronous (callers wrap in `withContext(Dispatchers.IO)`
// if they want suspension). Daemons must respond within the caller's
// `timeoutMillis` (default 30 s); the IME enforces a hard upper bound
// via `McpTimeoutClient`.

package dev.patrickgold.florisboard.ime.mcp;

interface IMcpDaemon {

    /**
     * Returns the daemon's advertised tool names. Mirrors the upstream
     * MCP spec `tools/list` shape — each name corresponds to a
     * `McpToolDescriptor` the daemon's manifest declares.
     *
     * The IME caches the result in `McpDaemonRegistry`; daemons that
     * want to expose a new tool re-publish the descriptor and the IME
     * re-fetches on the next addon-enumerator sweep.
     */
    String[] listToolNames();

    /**
     * Invoke a single tool. `requestJson` is the JSON encoding of an
     * `McpToolCallRequest`; the returned string is the JSON encoding
     * of an `McpToolCallResponse`.
     *
     * Daemons should respect `correlationId` echo, enforce the
     * `MAX_PAYLOAD_BYTES` ceiling on their side, and prefer specific
     * `McpErrorCode` values over `UNKNOWN`.
     */
    String invoke(in String requestJson);
}
