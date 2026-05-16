# SwiftFloris v1.8.36 — 2026-05-15

L7.4b — `McpServiceConnectionManager`: per-daemon `bindService`
lifecycle owner that provides the production `binderLookup` lambda
[AndroidMcpClient][v1.8.35] consumes. **963 unit tests** at HEAD,
0 failures.

[v1.8.35]: RELEASE_NOTES_v1.8.35.md

## What changed (user-visible)

Nothing yet. The L7.5 NlpManager wire-up (which actually registers
this manager via `McpClientRegistry.setActive(...)` at IME startup
and consults the smart-compose path through MCP tools) rides next.

## What changed (internal)

### L7.4b — `McpServiceConnectionManager`

New `ime/mcp/McpServiceConnectionManager`:

- `bind(daemonKey)` — issues `Context.bindService` with
  `BIND_AUTO_CREATE` and the daemon's
  `ACTION_BIND_MCP_DAEMON` intent. No-ops when already bound.
- `unbind(daemonKey)` — calls `Context.unbindService` and drops
  the entry. Safe to call when not bound.
- `shutdown()` — unbinds every live binding; called from
  `FlorisImeService.onDestroy`.
- `binderFor(daemonKey): IBinder?` — pass `::binderFor` straight
  to `AndroidMcpClient`'s constructor.

The `ServiceConnection` callbacks drive an in-memory state machine:

- `onServiceConnected` → store the live `IBinder`
- `onServiceDisconnected` → clear the binder (keep pending row)
- `onBindingDied` → clear + unbind + rebind (Android contract)
- `onNullBinding` → hard refusal; clear the binder

### State separation — `BindingTable`

State is split into a pure-Kotlin `BindingTable` nested class so the
state-machine transitions are pure-JVM testable. Production-side
`bind` / `unbind` (which touch `Context`) sit one layer up and are
the only Android-bound surface.

### Tests — `McpServiceConnectionManagerTest`

Nine new Kotest tests covering `BindingTable` transitions:

1. `binderFor` returns null when no binding registered.
2. `registerPending` records the connection but leaves binder null.
3. `onConnected` stores the live binder under the daemon key.
4. `onConnected` no-ops when the key has no pending binding.
5. `onDisconnected` clears the binder, keeps the row.
6. `removeBinding` returns the original `ServiceConnection`.
7. `removeBinding` returns null on unknown keys.
8. `activeKeys` reflects the full registered set.
9. Per-key isolation — `onDisconnected(keyA)` does not affect `keyB`.

## Versioning

- `gradle.properties`: `projectVersionCode=1836`,
  `projectVersionName=1.8.36`.
- README badge bumped to `v1.8.36`.

## What's next

- **L7.5** — register `AndroidMcpClient(manager::binderFor)`
  via `McpClientRegistry.setActive(...)` at IME startup
  (`FlorisImeService.onCreate`); call `manager.bind(...)` on
  every daemon the `McpDaemonRegistry` knows about; consult the
  result from `NlpManager` smart-compose.
- **L7.6 Settings UI** — a Settings → Privacy → MCP screen that
  lists every bound daemon, surfaces its advertised tools, and
  lets the user enable / disable each daemon individually.
