# Completed Work

Items consolidated from legacy planning documents on 2026-06-03.

This file summarizes shipped state. Release-level detail remains in `CHANGELOG.md`.

## Product Baseline

- Privacy-first Android keyboard forked from FlorisBoard.
- Base app has no `INTERNET` permission, no telemetry, no account requirement, and an Apache-2.0 ceiling.
- Optional networked/native capabilities are designed as signed addon APKs rather than linked into `:app`.
- Current release stream is v1.8.x with consolidated release notes in `CHANGELOG.md`.

## Shipped Feature Areas

- SwiftKey-style migration paths: SwiftKey JSON importer, Gboard XML import, FlorisBoard CSV import, encrypted SwiftFloris dictionary export/import, and migration documentation.
- Autocorrect and prediction: SCOWL dictionary, SymSpell, bigram/trigram scoring, phrase/candidate policies, multilingual ranking, and focused JVM policy coverage.
- Gesture typing: statistical glide classifier, adaptive touch evidence, multilingual dictionaries, and configurable glide trail themes.
- Clipboard: Room-backed history with sensitive gates, media/provider metadata, backup/restore handling, reconciliation, and bounded clone/preview paths.
- Addons: manifest/enumerator contracts, signing-pin trust store, Settings status/rescan/trust controls, dictionary-pack catalog details, and APK asset mounting.
- Voice: FUTO Voice Input handoff plus preview-only local Whisper/Vosk catalog until a recognizer runtime ships.
- Local-only productivity surfaces: calendar quick insert, task quick insert, MCP daemon bridge, Tasker integration, local sticker packs, and hardware-keyboard layout import foundations.
- Quality gates: no-network manifest verification, Roborazzi visual gate, OSV/dependency scanning, reproducible-build tooling, fastlane metadata checks, benchmark baselines, and repo hygiene scripts.

## Shipped Features

Consolidated from the archived open-work checklist (closed items). Full per-release detail is in `CHANGELOG.md`.

- [x] F18 (P1) — Heuristic `SmartComposeProvider` consuming the existing trigram/bigram/cold-start chain, with `correction.heuristicSmartCompose` pref (default off) + Settings toggle + registry wiring + JVM test. Shipped v1.8.188. — *Source: TODO_2026-06-03.md*
- [x] F28 / O7 (P0) — `TinkStringPreferenceCrypto` round-trip + tamper test (encrypt/decrypt identity, AAD binding, tampered-ciphertext + cross-key failure, empty string) via an `encodeEncrypted`/`decodeEncrypted` seam. Shipped v1.8.190. — *Source: TODO_2026-06-03.md*
- [x] F27 (P1) — `KeyboardManager` SHIFT-key state machine extracted to a pure `ShiftStateMachine` + 10 JVM tests. Shipped v1.8.191. — *Source: TODO_2026-06-03.md*
- [x] R3 (P1) — Back-fill tests for v1.8.184 (malformed code point dropped without throwing). Shipped v1.8.189. — *Source: TODO_2026-06-03.md*
- [x] EI5 (P1) — `FlorisEmojiCompat` reflection-shape guard + `FlorisEmojiCompatReflectionGuardTest` CI sentinel. Shipped v1.8.192. — *Source: TODO_2026-06-03.md*
- [x] O6 (P1) — Calendar permission privacy invariants test (`CalendarPermissionActivity` not exported + `READ_CALENDAR` declared). Shipped v1.8.193. — *Source: TODO_2026-06-03.md*
- [x] EI6 (P2) — Clipboard reconciliation property test (no dangling rows, no orphan files, single-pass convergence). Shipped v1.8.194. — *Source: TODO_2026-06-03.md*
- [x] F38 (P2) — Removed 3 dead `KeyboardMode` `@Deprecated` entries + their `LayoutManager` `when` arms; converted the misused `FlorisImeSizing` `ProvideKeyboardRowBaseHeight` deprecation to a KDoc/TODO. Shipped v1.8.195. — *Source: TODO_2026-06-03.md*
- [x] F37 (P2) — `AdvancedProviders.kt` audited (engines are pure `internal object`s; providers are stateful public classes); architecture confirmed correct + doc note added. Shipped v1.8.196. — *Source: TODO_2026-06-03.md*
- [x] F39 (P2) — `DictionaryManager.kt` logging audit: 9 `Log.*` calls routed through `flog*` under a new `LogTopic.DICTIONARY`, critical paths promoted to `flogError`. Shipped v1.8.197. — *Source: TODO_2026-06-03.md*
- [x] F14 (P2) — Settings -> About inline "What's new" excerpt (`BuildConfig.WHATS_NEW` from the matching `CHANGELOG.md` section at build time; scrollable dialog + full-changelog link). Shipped v1.8.198. — *Source: TODO_2026-06-03.md*
- [x] EI12 (P2) — Settings -> Typing stats "Erase everything, including dictionary" combined wipe of all 4 learning stores + personal dictionary, behind a confirm dialog (gentler dictionary-preserving reset retained). Shipped v1.8.199. — *Source: TODO_2026-06-03.md*
- [x] F7 (P1) — Settings -> "Local audit log" screen over the existing `AddonInvocationAudit` ring (summary + recent-activity list + copy-as-JSON + clear; display-only, PII-safe). Shipped v1.8.200. — *Source: TODO_2026-06-03.md*
- [x] F40 (test-class phase, P2) — Roborazzi screen-level test classes for the remaining Settings and keyboard/glide surfaces (compile-checked, baseline-pending). Shipped v1.8.201. Baseline PNG capture remains open (see ROADMAP). — *Source: TODO_2026-06-03.md*
- [x] EI1 (P2) — `AppPrefs.kt` partitioned into feature-area preference models under `app/prefs`, with a source-level merged `FlorisPreferenceModelImpl` and 187-row key/type/default golden test preserving datastore compatibility. Shipped v1.8.202. — *Source: TODO_2026-06-03.md*
- [x] WS14 (P2) — Deprecated warning cleanup for Compose toast call sites, the Room dictionary-language DAO query, and the stale `-Xwhen-guards` compiler flag. Shipped v1.8.203. — *Source: TODO_2026-06-03.md*
- [x] F1, F2, F15, F16, F17, F19, F20, F25, F26, F32, F34, F35, F36, F41, F42, EI8, EI11, EI4 (doc) — Closed across v1.8.174 -> v1.8.187. — *Source: TODO_2026-06-03.md*
- [x] IMPROVEMENT_PLAN Workstreams 1, 3, 4, 5, 6 complete; Workstream 2 (lint) monotonically decreasing. — *Source: IMPROVEMENT_PLAN_2026-05-18.md*

## Stale / Obsolete Items

- [STALE] F33 — *Reason: Rejected; no longer in the active queue. Source: TODO_2026-06-03.md*
- [STALE] O5 — Settings -> Sync screen "missing" finding — *Reason: Incorrect; `SyncSettingsScreen.kt` (443 LOC) already exists, is registered in `Routes.kt` (`@Deeplink settings/sync`), and is reachable from `HomeScreen` (verified 2026-05-28). Source: TODO_2026-06-03.md*
- [STALE] EI2 (original premise) — "15 sub-screens at one level" — *Reason: Stale; Settings home is already grouped into four labelled sections in `HomeScreen.kt`. Only a low-value cosmetic re-bucket survives as P3 in ROADMAP. Source: TODO_2026-06-03.md*
- [STALE] F31 (original spec) — per-app language via `LocaleManager.getApplicationLocales(packageName)` — *Reason: Requires the privileged `READ_APP_SPECIFIC_LOCALES` permission, not grantable to a normal IME and contrary to the clean-permission posture; reframed to a permission-free remembered-subtype feature in ROADMAP. Source: TODO_2026-06-03.md*
- [STALE] F3 / EI7 (migration-window framing) — *Reason: The 2026-05-31 SwiftKey cloud-export cutoff has passed; the import/voice-install discovery value is permanent, so both items were reframed as generic surfaces in ROADMAP (migration-window framing dropped). Source: TODO_2026-06-03.md*

## Documentation Consolidation

- Single source of truth for planned work is now `ROADMAP.md` (`## Existing Planned Work`).
- Shipped state is summarized here; completed release history remains in `CHANGELOG.md`.
- Current research synthesis is in `RESEARCH_REPORT.md`.
- The historical tiered strategy roadmap is archived at `docs/archive/ROADMAP_v5.67_2026-05-18.md`; the legacy open-work checklist at `docs/archive/TODO_2026-06-03.md`; the legacy improvement plan at `docs/archive/IMPROVEMENT_PLAN_2026-05-18.md`.
- The 2026-05-25 research plan is archived at `docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25.md`.
