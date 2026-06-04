# Cycle 17 Findings - 2026-06-04

## Scope

- Repository: `SwiftFloris`
- Baseline: clean detached worktree at pushed `master` `2076f49`
  (`docs: refresh cycle 16 research queue`), described as
  `v1.8.246-5-g2076f49`.
- Sync: `git pull --rebase origin master` reported up to date before this
  cycle.
- Constraint: research/docs only. No feature source, tests, build files, or
  assets were edited.

## Anti-Duplicate Checks

- Did not duplicate R16-1. R16-1 is subtype switching; this cycle is MCP daemon
  tool identity.
- Did not reopen Tasker extras or Keyman package caps. Current research notes
  and the June 2 audit already treat those as handled.
- Did not change the MCP payload-size, consent, sensitive-field, or signature
  permission gates. The row targets name shape and cross-daemon dispatch identity
  only.
- Did not propose networked MCP behavior; this remains the existing local daemon
  bridge.

## Local Evidence

- `McpDaemonDiscoverer.kt:91-109` parses tool catalog entries and accepts every
  trimmed nonblank `name`.
- `McpBridgeContract.kt:84-94` requires only nonblank `McpToolDescriptor.name`.
- `McpDaemonRegistry.kt:85-93` implements `findTool(toolName)` as a first-match
  scan across active daemons.
- `McpDispatchRouter.kt:53-83` accepts a request with only `toolName`, resolves
  it through `RegistryView.findTool`, and then dispatches to the resolved
  daemon.
- `McpDaemonDiscovererTest.kt:85-100` covers skipping blank names, but not
  malformed tool-name shape.
- `McpDaemonRegistryTest.kt:53-70` covers multi-daemon lookup with distinct names
  and does not pin duplicate-name behavior.
- `docs/AUDIT_2026-05-28.md:80-82` records the tool-name shape and
  collision/shadowing risk.

## Roadmap Changes Fed

- R17-1: Scope MCP daemon tool dispatch by daemon and constrain tool names. The
  implementation should reject malformed names, replace global first-match
  lookup with a daemon-scoped identity before dispatch, keep Settings summaries
  and per-tool disable keys on that same identity, and add tests for malformed
  names plus duplicate names across daemons.

## Non-Adds

- No source fix was made in this cycle.
- No new permission, export, storage, or network behavior was proposed.
- No broad MCP transport redesign proposed. The target is the advertised
  tool-name contract and dispatch identity boundary.
