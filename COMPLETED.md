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
- [x] F4 (P1) — Settings search entry point, static searchable catalog for every Settings destination, result ranking, destination navigation, and global search-target highlight card. Shipped v1.8.204. — *Source: TODO_2026-06-03.md*
- [x] F29 (P1) — `PhysicalKeyboardScreen` custom hardware layout import for Windows KLC, macOS `.keylayout`, and Keyman `.kmp` LDML packages, with private persisted catalog, attached-device picker, runtime apply action, and JVM policy/store coverage. Shipped v1.8.205. — *Source: TODO_2026-06-03.md*
- [x] F31 (P1, reframed) — Permission-free per-app subtype memory with opt-in localization preference, Settings screen, persisted package-to-subtype map, manual-switch recording, focus-time restore, stale-subtype pruning, and JVM memory/golden coverage. Shipped v1.8.206. — *Source: TODO_2026-06-03.md*
- [x] EI7 (P1, reframed) — Voice input empty state now explains FUTO as a separate offline Android voice keyboard and keeps the F-Droid install action visible without migration-window framing. Shipped v1.8.207. — *Source: TODO_2026-06-03.md*
- [x] F3 (P0, reframed) — First-run "Import your dictionary" setup hint for post-cutoff local keyboard exports, routing SwiftKey JSON, Gboard XML/ZIP, FlorisBoard CSV/.flbackup/.fldic, and SwiftFloris exports into the existing personal-dictionary importer. Shipped v1.8.208. — *Source: TODO_2026-06-03.md*
- [x] EI3 (P2) — Personal dictionary bulk-import preview for modular imports, with first-50-row review, row exclusion before commit, excluded-row summary counts, and persisted skip-preview opt-out. Shipped v1.8.209. — *Source: TODO_2026-06-03.md*
- [x] F6 (P2) — Per-app accent opt-in discovery hint and Settings preview, with process-local three-app threshold tracking, persisted hint state only, Settings search coverage, and privacy docs. Shipped v1.8.210. — *Source: TODO_2026-06-03.md*
- [x] EI2 (P3, reframed) — Settings home regrouped into Typing experience, Personalization, Privacy & data, Advanced, and About buckets, with Physical keyboard surfaced directly under Advanced while preserving existing deep links. Shipped v1.8.211. — *Source: TODO_2026-06-03.md*
- [x] F23 (P1) — Release workflow now depends on the reusable reproducible-build verifier before signing or GitHub Release publication, blocking release dispatches when the build-twice APK check fails. Shipped v1.8.212. — *Source: TODO_2026-06-03.md*
- [x] F24 (P1) — Non-shipping `releaseRoborazzi` variant mirrors release build flags for Roborazzi, exposed through `:app:verifyRoborazziRelease`; the release workflow runs it before APK signing/publication. Shipped v1.8.213. — *Source: TODO_2026-06-03.md*
- [x] EI9 (P2) — Manual benchmark-regression workflow now runs the adb benchmark suite, compares candidate JSON against committed baselines with `scripts/check-benchmark-trends.py`, uploads a markdown trend report, and fails watched medians above the documented +8 % window. Shipped v1.8.214. — *Source: TODO_2026-06-03.md*
- [x] RA-5 / RA-6 / RA-7 (P3) — Settings search now folds diacritics during query normalization, opens the search field focused on first entry, shows a clear action while typing, and advertises the Search IME action. Shipped v1.8.215. — *Source: ROADMAP.md Researcher Queue Cycle 1.*
- [x] Dependency freshness compatible batch (P3) — Compose BOM `2026.05.01`, KSP `2.3.9`, and Roborazzi `1.63.0` shipped while Kotlin `2.4.0` and AndroidX Core `1.19.0` stayed deferred on KSP/compileSdk gates. Shipped v1.8.216. — *Source: ROADMAP.md Docs & hygiene.*
- [x] EI10 (P2) — Confirmed no committed `app/lint-baseline.xml`, documented the baseline-free lint state, and reran `scripts/run-lint-debug-with-baseline-check.sh` successfully after setting the verified JDK 21 path. Shipped v1.8.217. — *Source: ROADMAP.md Docs & hygiene.*
- [x] R2-1 (P1) — Staged startup exceptions now persist to the existing crash-report file store and redirect Settings to `CrashDialogActivity` before the splash screen can wait forever on unloaded preferences. Shipped v1.8.218. — *Source: ROADMAP.md Researcher Queue Cycle 2.*
- [x] R2-2 (P2) — Restore and crash diagnostic failure paths now use project logging, and restore toasts/cards use stable fallback copy when throwable messages are null or blank. Shipped v1.8.219. — *Source: ROADMAP.md Researcher Queue Cycle 2.*
- [x] R2-3 (P2) — Root onboarding docs now route open work to `ROADMAP.md`, shipped state to `COMPLETED.md`, release notes to `CHANGELOG.md` plus fastlane metadata, and archived parity/improvement plans to historical context. Shipped v1.8.220. — *Source: ROADMAP.md Researcher Queue Cycle 2.*
- [x] RA-8 (P3) — Settings search entry-point discoverability was already satisfied by the Settings Home top app-bar search action. Confirmed and documented in v1.8.220. — *Source: ROADMAP.md Researcher Queue Cycle 1.*
- [x] RA-1 (P1) — Settings search now has a JVM/Robolectric drift guard for unique entry IDs, real non-blank string resources, and exhaustive typed destination-route mapping. Shipped v1.8.221. — *Source: ROADMAP.md Researcher Queue Cycle 1.*
- [x] RA-2 (P2) — Settings search no-results states now include a one-tap Browse all settings action back to Settings Home. Shipped v1.8.222. — *Source: ROADMAP.md Researcher Queue Cycle 1.*
- [x] RA-3 (P2) — Settings search high-traffic capability synonyms now cover dark/light theme mode, haptic feedback, trace/shape-writing gestures, punctuation spacing, and privacy audit queries with JVM ranking coverage. Shipped v1.8.223. — *Source: ROADMAP.md Researcher Queue Cycle 1.*
- [x] RA-10 (P2) — Settings search now resets populated non-blank result lists to the top when the query changes, so stale scroll offsets do not hide the highest-ranked hit. Shipped v1.8.224. — *Source: ROADMAP.md Researcher Queue Cycle 1.*
- [x] R3-1 (P0) — Post-v1.8.225 pushed fix commits now have a normal release ledger: v1.8.226 / versionCode 2026 covers n-gram/data-loss hardening, thread-safety cleanup, SealedBoxCrypto KDF/scrubbing, private-session trace suppression, Arabic combining-mark shaping, Snygg selector/contentScale recovery, and the clipboard missing-media fallback compile repair. — *Source: ROADMAP.md Researcher Queue Cycle 3.*
- [x] R4-1 (P1) — Japanese locale capability gates now use `ja` for no-capitalization and no-auto-space behavior, with focused `FlorisLocaleTest` coverage for adjacent script-sensitive locales and tag serialization. Shipped v1.8.227. — *Source: ROADMAP.md Researcher Queue Cycle 4.*
- [x] R3-2 (P1) — Clipboard history search is wired into the in-keyboard clipboard palette with a default-on Settings toggle, query/type-filter composition, clear/no-results states, and focused `ClipboardHistoryFilterTest` coverage. Shipped v1.8.228. — *Source: ROADMAP.md Researcher Queue Cycle 3.*
- [x] R5-1 (P1) — Non-co-signed addon APKs now remain rejected until Settings records an explicit signing-certificate pin, while co-signed addons still enroll automatically and changed certificates require a separate trust action. Shipped v1.8.229. — *Source: ROADMAP.md Researcher Queue Cycle 5.*
- [x] R3-3 (P1) — Sealed-box sync envelopes now have explicit v1 schema constants, deterministic fixed X25519/AES-GCM vector coverage, malformed-envelope null-return coverage, and documented compatibility policy before CRDT transport persists envelopes. Shipped v1.8.230. — *Source: ROADMAP.md Researcher Queue Cycle 3.*
- [x] R7-1 (P2) — Dynamic incognito toggles now immediately re-apply the IME window `FLAG_SECURE` policy for the active field via a lifecycle-cleared keyboard-manager callback, with pure policy coverage for password/incognito/off combinations. Shipped v1.8.231. — *Source: ROADMAP.md Researcher Queue Cycle 7.*
- [x] R8-1 (P3) — Settings -> Personal dictionary now surfaces operation-specific blocked-back feedback while save, delete, import, or export work is active, without weakening the existing leave-blocking policy. Shipped v1.8.232. — *Source: ROADMAP.md Researcher Queue Cycle 8.*
- [x] R6-1 (P2) — `InputConnection` batch edits for selection, text commit, composing finalize, and composing-region replacement now exclude expected-content generation/queue work and use try/finally batch pairing with focused call-order tests. Shipped v1.8.233. — *Source: ROADMAP.md Researcher Queue Cycle 6.*
- [x] R3-4 (P2) — Post-v1.8.225 hotfix surfaces now have focused regression coverage for Arabic combining-mark shaping, Snygg unknown selectors, `contentScale` serialization/defaults, private-session trace suppression, and locale-scoped n-gram flush behavior. Shipped v1.8.234. — *Source: ROADMAP.md Researcher Queue Cycle 3.*
- [x] RA-4 (P2) — Settings search now has TalkBack-oriented field labels/state, polite result-status live regions, merged button-role result labels, focused accessibility contract coverage, and manual QA checklist coverage. Shipped v1.8.235. — *Source: ROADMAP.md Researcher Queue Cycle 1.*
- [x] R9-1 (P2) — Suggestion candidate generation now snapshots request-scoped incognito, no-personalized-learning/editor sensitivity, suggestion enabled, offensive-content, and emoji-count inputs before async provider, trace, and ghost-text work begins. Shipped v1.8.236. — *Source: ROADMAP.md Researcher Queue Cycle 9.*
- [x] RA-9 (P2) — Settings search destination highlights now consume the process-wide target once into local screen state, expose a close action, and do not reappear on later visits without a new search. Shipped v1.8.237. — *Source: ROADMAP.md Researcher Queue Cycle 1.*
- [x] R4-2 (P3) — Clipboard image/video history tiles now expose localized TalkBack labels with media type, history group, and copied-time context while keeping thumbnail/video overlay icons decorative. Shipped v1.8.238. — *Source: ROADMAP.md Researcher Queue Cycle 4.*
- [x] R10-1 (P2) — Editor start-view and selection-update content generation is cancelled or superseded across reset, finishInput, and input-connection switches so delayed jobs cannot republish stale editor state or touch an old `InputConnection`. Shipped v1.8.239. — *Source: ROADMAP.md Researcher Queue Cycle 10.*
- [x] F1, F2, F15, F16, F17, F19, F20, F25, F26, F32, F34, F35, F36, F41, F42, EI8, EI11, EI4 (doc) — Closed across v1.8.174 -> v1.8.187. — *Source: TODO_2026-06-03.md*
- [x] IMPROVEMENT_PLAN Workstreams 1, 3, 4, 5, 6 complete; Workstream 2 (lint) monotonically decreasing. — *Source: IMPROVEMENT_PLAN_2026-05-18.md*

## Stale / Obsolete Items

- [STALE] F33 — *Reason: Rejected; no longer in the active queue. Source: TODO_2026-06-03.md*
- [STALE] O5 — Settings -> Sync screen "missing" finding — *Reason: Incorrect; `SyncSettingsScreen.kt` (443 LOC) already exists, is registered in `Routes.kt` (`@Deeplink settings/sync`), and is reachable from `HomeScreen` (verified 2026-05-28). Source: TODO_2026-06-03.md*
- [STALE] EI2 (original premise) — "15 sub-screens at one level" — *Reason: Stale; Settings home was already grouped before the research refresh. The surviving low-value cosmetic re-bucket shipped in v1.8.211. Source: TODO_2026-06-03.md*
- [STALE] F31 (original spec) — per-app language via `LocaleManager.getApplicationLocales(packageName)` — *Reason: Requires the privileged `READ_APP_SPECIFIC_LOCALES` permission, not grantable to a normal IME and contrary to the clean-permission posture; reframed to a permission-free remembered-subtype feature in ROADMAP. Source: TODO_2026-06-03.md*
- [STALE] F3 / EI7 (migration-window framing) — *Reason: The 2026-05-31 SwiftKey cloud-export cutoff has passed; the import/voice-install discovery value is permanent, so both items were reframed as generic surfaces in ROADMAP (migration-window framing dropped). Source: TODO_2026-06-03.md*

## Documentation Consolidation

- Single source of truth for planned work is now `ROADMAP.md` (`## Existing Planned Work`).
- Shipped state is summarized here; completed release history remains in `CHANGELOG.md`.
- Current research synthesis is in `RESEARCH_REPORT.md`.
- The historical tiered strategy roadmap is archived at `docs/archive/ROADMAP_v5.67_2026-05-18.md`; the legacy open-work checklist at `docs/archive/TODO_2026-06-03.md`; the legacy improvement plan at `docs/archive/IMPROVEMENT_PLAN_2026-05-18.md`.
- The 2026-05-25 research plan is archived at `docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25.md`.
