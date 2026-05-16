# SwiftFloris v1.8.35 — 2026-05-15

L7.4 — AIDL transport for the MCP daemon bridge. The IME can now
dispatch `McpToolCallRequest` envelopes over a cross-process Binder
to any installed daemon that exposes the matching Service.
**954 unit tests** at HEAD, 0 failures.

## What changed (user-visible)

Nothing yet. The actual `ServiceConnection` manager that holds the
per-daemon binding lifecycle (L7.4b) and the NlpManager wire-up that
asks "is there a calendar tool available" (L7.5+) ride in subsequent
slices. This release adds the **transport layer** so those slices can
land without re-deriving the wire format.

## What changed (internal)

### L7.4 — `IMcpDaemon.aidl`

New `app/src/main/aidl/dev/patrickgold/florisboard/ime/mcp/IMcpDaemon.aidl`:

- Two-method Binder surface: `String[] listToolNames()` +
  `String invoke(in String requestJson)`.
- Payload-typed as `String` (not `Bundle`/`Parcelable`) — daemons
  written in any JVM language interop without needing the
  kotlinx.serialization runtime on their side; they parse the
  JSON themselves.
- Non-`oneway` methods — the IME-side `McpClient.callTool`
  contract is synchronous (callers wrap in
  `withContext(Dispatchers.IO)` if they want suspension).
- Enabled AIDL build feature in `app/build.gradle.kts`
  (`buildFeatures.aidl = true`).

### L7.4 — `AndroidMcpClient`

New `ime/mcp/AndroidMcpClient`:

- Implements `McpClient`, dispatching across the AIDL surface.
- Constructor takes a `binderLookup: (DaemonKey) -> IBinder?`
  lambda — the binding lifecycle lives one layer up (the
  service-connection manager re-binds on rebind events without
  needing to mutate the client).
- Translates all five AIDL-layer failure modes into the existing
  `McpToolCallResponse` failure shape:
  - oversized `parameterJson` → `PAYLOAD_TOO_LARGE` (refused
    before binder lookup),
  - missing binder → `TOOL_NOT_FOUND`,
  - `DeadObjectException` → `TOOL_INTERNAL_ERROR` ("binder died"),
  - `RemoteException` → `TOOL_INTERNAL_ERROR` (RemoteException msg),
  - null / blank / non-JSON daemon response →
    `TOOL_INTERNAL_ERROR` with the specific decode failure.
- Echoes daemon-emitted error envelopes verbatim — `INVALID_PARAMETERS`,
  `TOOL_NOT_FOUND`, `PERMISSION_DENIED` all flow through unchanged.

### Tests — `AndroidMcpClientTest`

Nine new Kotest tests covering the dispatch contract:

1. `PAYLOAD_TOO_LARGE` refused before binder lookup.
2. `TOOL_NOT_FOUND` when `binderLookup` returns null.
3. OK envelope round-trip through a fake `IMcpDaemon`.
4. `DeadObjectException` → `TOOL_INTERNAL_ERROR`.
5. `RemoteException` → `TOOL_INTERNAL_ERROR`.
6. Blank daemon response → `TOOL_INTERNAL_ERROR`.
7. Non-JSON daemon response → `TOOL_INTERNAL_ERROR`.
8. Daemon-emitted error envelope propagated verbatim.
9. `nextCorrelationId` uniqueness across consecutive calls.

The test injects a fake binder whose `queryLocalInterface` returns
a hand-rolled `IMcpDaemon.Stub` subclass — no Robolectric / no real
Binder transport needed.

## Versioning

- `gradle.properties`: `projectVersionCode=1835`,
  `projectVersionName=1.8.35`.
- README badge bumped to `v1.8.35`.

## What's next

- **L7.4b** — `McpServiceConnectionManager`: per-daemon
  `ServiceConnection` + binding lifecycle + binder-lookup
  callback wiring `AndroidMcpClient` to a real daemon Service.
- **L7.5+** — register `AndroidMcpClient` via
  `McpClientRegistry.setActive(...)` at IME startup so the
  smart-compose path can opt in.
