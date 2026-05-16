# SwiftFloris v1.8.38 — 2026-05-15

L7.5b — the end-to-end MCP daemon bridge is now active inside the
IME. `FlorisImeService.onCreate` runs daemon discovery, binds every
installed daemon, and installs `AndroidMcpClient` as the active MCP
client; `onDestroy` tears the bridge down cleanly.
**982 unit tests** at HEAD, 0 failures.

## What changed (user-visible)

Still nothing — `McpDaemonRegistry` is empty on every install
because there are no published SwiftFloris MCP daemon APKs yet. As
soon as a sibling app declares the `ACTION_BIND_MCP_DAEMON` intent
filter + the matching `BIND_MCP` permission + a tool catalog in
`R.raw.<name>`, the IME automatically picks it up on next start.

The L7.6 Settings → Privacy → MCP screen (per-daemon enable /
disable + tool listing) rides next.

## What changed (internal)

### L7.5b — `McpServiceLifecycle`

New `ime/mcp/McpServiceLifecycle`:

- Top-level orchestration that owns the lifecycle for the MCP
  bridge as seen by `FlorisImeService`.
- `start(appContext)` — production factory. Constructs
  `McpServiceConnectionManager`, runs
  `McpAndroidDiscoverer.runDiscovery(context)` (tolerates failure
  via `runCatching`), and calls `startWithDaemons(...)`.
- `startWithDaemons(daemons)` — publishes daemons into
  `McpDaemonRegistry`, binds each via the injected `bindCallback`,
  and installs `AndroidMcpClient(binderLookup)` into
  `McpClientRegistry`. Single-shot — throws on second call.
- `stop()` — unbinds every active daemon, calls `shutdownCallback()`,
  empties `McpDaemonRegistry`, and restores `NoOpMcpClient` into
  `McpClientRegistry`. Idempotent.

### L7.5b — `FlorisImeService` wire-up

- New `mcpLifecycle: McpServiceLifecycle?` field.
- `onCreate` ends with `McpServiceLifecycle.start(applicationContext)`
  wrapped in a `try/catch` so a discovery failure doesn't abort
  IME startup.
- `onDestroy` calls `mcpLifecycle?.stop()` (with the same per-step
  exception-guard pattern as the other teardown steps).

### Tests — `McpServiceLifecycleTest`

Nine new Kotest tests covering the lifecycle around injected
bind/unbind/shutdown lambdas:

1. `startWithDaemons` publishes the daemon map into
   `McpDaemonRegistry`.
2. `startWithDaemons` invokes the bind callback once per daemon.
3. `startWithDaemons` installs an `AndroidMcpClient` into
   `McpClientRegistry`.
4. `startWithDaemons` throws on second call.
5. `stop` unbinds every daemon and calls `shutdown`.
6. `stop` empties the registry and restores `NoOpMcpClient`.
7. `stop` is idempotent.
8. `isStarted` reflects the transitions.
9. Empty-daemon start publishes an empty registry but still
   installs the client.

Tests reset both registries in `afterEach` to keep them
test-order-independent.

## Versioning

- `gradle.properties`: `projectVersionCode=1838`,
  `projectVersionName=1.8.38`.
- README badge bumped to `v1.8.38`.

## What's next

- **L7.6 Settings UI** — Settings → Privacy → MCP screen listing
  every bound daemon, its advertised tools, and a per-daemon
  enable/disable toggle (writes back to `McpDaemonRegistry`).
- **L7.7** — `NlpManager.smart-compose` consults
  `McpDaemonRegistry.findTool(...)` for tools relevant to the
  current input field (calendar / contacts / clipboard / SMS).
