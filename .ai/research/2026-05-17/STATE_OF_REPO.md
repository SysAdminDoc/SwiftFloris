# SwiftFloris — State of the Repo

**Research date:** 2026-05-17
**Initial research HEAD:** `e62ba34` — docs: research run 2026-05-17 — fourth pass: README catch-up + PRIVACY_AND_AI.md + subsystem inspection
**Branch:** `master`, ahead of `origin/master` (push fails 403 from this VM by design — see AGENTS/CLAUDE local notes)

**Continuation note:** autonomous development after this reconnaissance moved
HEAD to v1.8.84. The notable dependency deltas are
`androidx.security:security-crypto:1.1.0-alpha06` removed and
`com.google.crypto.tink:tink-android:1.21.0` added for N7.6, plus
Bump-batch A: coroutines `1.11.0`, KSP `2.3.8`, ZXing `3.5.4`, and
AboutLibraries `14.2.0`. v1.8.70 is docs-only README migration-window
messaging; it changes no app code or permission surface. v1.8.71 updates
Roborazzi to `1.60.0` and Robolectric to `4.16.1`. v1.8.72 is docs-only
glide-strategy correction: HeliBoard / NLnet open-glide remains additive,
while `swiftfloris-statistical` is production default until an open library
and permissive dataset land. v1.8.73 moved local root JVM crash/replay logs
under `.ai/local-crash-logs/2026-05-16/` and added a CI guard against
committed root `hs_err_pid*.log` / `replay_pid*.log` files.
v1.8.74 updates AGP to `9.2.1` and Compose BOM to `2026.05.00`; compile /
target SDK remain 36. v1.8.75 adds the macOS `.keylayout` XML parser for the
hardware-keyboard import stack, v1.8.76 adds the Android runtime mapper for
imported hardware-keyboard layouts, v1.8.77 adds the user-imported sticker
folder over the existing rich-content provider path, v1.8.78 adds the
Keyman `.kmp` package intake/classifier foundation without executing compiled
`.kmx` bytecode or JavaScript in `:app`, v1.8.79 wires the bundled
honeycomb character layout into production `TextKeyboardLayout` with clipped
hex key surfaces and hex-aware hit testing, v1.8.80 documents the
SQLCipher provider migration readiness plan without changing the runtime AAR,
and v1.8.81 adds the addon catalog foundation (`AddonRegistry` live state plus
`DictionaryPackCatalog` descriptor/provenance validation). v1.8.82 adds the
persisted addon signing-pin foundation (`AddonSigningPinSet` plus
`prefs.addon.signingCertPins`), v1.8.83 wires IME startup scan/reconcile/
publish through that persisted trust store, and v1.8.84 adds the Settings →
Addons read-only status/rescan surface.

This file is a pure reconnaissance memo. It captures what was observed locally
before any external research, so future passes can tell what changed in the
repo vs. what changed in the world.

---

## 1. Identity & wedge

SwiftFloris is a privacy-first Android IME forked from **FlorisBoard**. The
fork's wedge is *"every paywalled cloud feature, fully on-device, fully
auditable, with zero account requirement"* — i.e. SwiftKey-class typing
without `INTERNET` permission, OneDrive, or a Microsoft account.

Operative invariants (load-bearing — touched by build gates):

- No `INTERNET` permission. Enforced by Gradle task
  `:app:verifyNoInternetPermission` in [app/build.gradle.kts](../../../app/build.gradle.kts#L227)
  on every variant's `preBuild`.
- Apache-2.0 ceiling on `:app`. GPL / AGPL / FUTO Source-First code may only
  ship as a clearly-isolated optional addon under its own license.
- No closed-source binary blobs. Reproducible builds (toolchain pins in
  [gradle/tools.versions.toml](../../../gradle/tools.versions.toml)).
- Distribution = GitHub Releases (canonical) → Obtainium → F-Droid (target),
  not Google Play.

## 2. Versions and toolchain (verified 2026-05-17)

| Item | Pinned value | Source |
|---|---|---|
| versionName / versionCode | 1.8.84 / 1884 | [gradle.properties](../../../gradle.properties#L18-L19) |
| AGP | 9.2.1 | [libs.versions.toml](../../../gradle/libs.versions.toml#L3) |
| Kotlin | 2.3.21 | [libs.versions.toml](../../../gradle/libs.versions.toml#L19) |
| KSP | 2.3.8 | [libs.versions.toml](../../../gradle/libs.versions.toml) (updated after initial reconnaissance in v1.8.69) |
| Compose BOM | 2026.05.00 | [libs.versions.toml](../../../gradle/libs.versions.toml#L7) |
| AndroidX Core | 1.18.0 | [libs.versions.toml](../../../gradle/libs.versions.toml#L8) |
| AndroidX Activity | 1.13.0 | [libs.versions.toml](../../../gradle/libs.versions.toml#L4) |
| AndroidX Navigation | 2.9.7 | [libs.versions.toml](../../../gradle/libs.versions.toml#L12) |
| AndroidX Room | 2.8.4 | [libs.versions.toml](../../../gradle/libs.versions.toml#L14) |
| AndroidX SQLite | 2.6.2 | [libs.versions.toml](../../../gradle/libs.versions.toml#L15) |
| AndroidX emoji2 | 1.6.0 | [libs.versions.toml](../../../gradle/libs.versions.toml#L10) |
| Tink Android | 1.21.0 | [libs.versions.toml](../../../gradle/libs.versions.toml) (added after initial reconnaissance in v1.8.68; replaced inline `androidx-security-crypto`) |
| SQLCipher Android | 4.16.0 | [libs.versions.toml](../../../gradle/libs.versions.toml#L27) |
| Coil | 3.4.0 | [libs.versions.toml](../../../gradle/libs.versions.toml#L18) |
| material-kolor | 4.1.1 | [libs.versions.toml](../../../gradle/libs.versions.toml#L23) |
| jetpref | 0.3.0 | [libs.versions.toml](../../../gradle/libs.versions.toml#L26) |
| ZXing core | 3.5.4 | [libs.versions.toml](../../../gradle/libs.versions.toml) (updated after initial reconnaissance in v1.8.69) |
| kotlinx-coroutines | 1.11.0 | [libs.versions.toml](../../../gradle/libs.versions.toml) (updated after initial reconnaissance in v1.8.69) |
| kotlinx-serialization-json | 1.11.0 | [libs.versions.toml](../../../gradle/libs.versions.toml#L21) |
| Kotest | 6.1.11 | [libs.versions.toml](../../../gradle/libs.versions.toml#L35) |
| Roborazzi | 1.60.0 (plugin **active**) | [libs.versions.toml](../../../gradle/libs.versions.toml) |
| Robolectric | 4.16.1 | [libs.versions.toml](../../../gradle/libs.versions.toml) |
| Build Tools | 36.0.0 | [tools.versions.toml](../../../gradle/tools.versions.toml#L2) |
| NDK | 29.0.14206865 | [tools.versions.toml](../../../gradle/tools.versions.toml#L5) |
| JDK | 17 | [tools.versions.toml](../../../gradle/tools.versions.toml#L4) |
| Gradle wrapper | 9.4.1 (SHA-256 pinned) | gradle/wrapper/gradle-wrapper.properties |
| minSdk | 26 (Android 8.0) | [gradle.properties](../../../gradle.properties#L14) |
| target / compile SDK | 36 (Android 16) | [gradle.properties](../../../gradle.properties#L15-L16) |

Whether any of the above is materially behind 2026-05-17 latest is in the
companion [SECURITY_AND_DEPENDENCY_REVIEW.md](SECURITY_AND_DEPENDENCY_REVIEW.md).
The same-day fifth pass in [FIFTH_PASS_FINDINGS.md](FIFTH_PASS_FINDINGS.md)
corrects the most drift-prone items: Activity 1.13.0 is stable, AGP target
is 9.2.x. KSP target 2.3.8 shipped in v1.8.69; Roborazzi 1.60.0 and
Robolectric 4.16.1 shipped in v1.8.71; AGP 9.2.1 and Compose BOM 2026.05.00
shipped in v1.8.74; the macOS `.keylayout` parser shipped in v1.8.75; the
hardware-keyboard runtime mapper shipped in v1.8.76; the user-imported sticker
folder shipped in v1.8.77; Keyman `.kmp` package metadata intake and LDML-in-package
extraction shipped in v1.8.78; the honeycomb hex layout production wire-up
shipped in v1.8.79; the SQLCipher provider migration readiness plan shipped
in v1.8.80; the addon catalog foundation shipped in v1.8.81; persisted addon
signing pins shipped in v1.8.82; startup reconciliation shipped in v1.8.83;
Settings → Addons status/rescan UI shipped in v1.8.84.

A docs-only contributor-onboarding batch followed v1.8.77: root
`ARCHITECTURE.md` now captures the module/runtime/package map and root
`CONTRIBUTING.md` captures setup, invariants, verification, release-note, PR,
AI-assisted contribution, and licensing expectations. README, AGENTS,
PROJECT_CONTEXT, ROADMAP, the PR template, and the research backlog now point at
those files.

The next docs-only repo-hygiene batch moved root multilingual / voice guides
under `docs/`: `docs/GESTURE_TYPING_MULTILINGUAL.md`,
`docs/GESTURE_TYPING_MULTILINGUAL_RESEARCH.md`,
`docs/FUTO_VOICE_INPUT_TROUBLESHOOTING.md`, and `docs/VOICE_COMMANDS.md`.

v1.8.78 then implemented the Keyman `.kmp` package import foundation:
`KeymanPackageParser` reads ZIP-compatible package containers, normalizes
`kmp.json`, records file metadata, extracts LDML XML through
`KeymanLdmlParser`, and classifies packages as LDML-ready, lexical-only,
mixed, metadata-only, invalid, or compiled-engine-required.

v1.8.79 then implemented the honeycomb hex layout production wire-up:
`extension.json` registers the bundled `honeycomb` character layout,
`LayoutManager` marks it as `TextKeyboardLayoutStyle.Honeycomb`,
`TextKeyboard.layoutHoneycomb(...)` positions real `TextKey` instances in the
hex tessellation, `TextKeyboardLayout` clips the real Snygg key surface to
`HoneycombHexShape`, and the hit tester rejects bounding-box corners and
inter-key gaps.

v1.8.80 then implemented the SQLCipher provider migration planning slice:
`docs/SQLCIPHER_PROVIDER_MIGRATION.md` records the current LibTomCrypt-based
Android Community AAR state, the OpenSSL source-build escape hatch, migration
triggers, 16 KB page-size gates, verification expectations, and rollback
rules. `docs/SECURITY.md` now links that plan.

v1.8.81 then implemented the Next-10.3a addon catalog foundation:
`AddonRegistry` reconciles `AddonEnumerator` package scans into live addon
state, preserves first-seen signing-certificate pins, rejects package-name
hijacks with changed certificates, and exposes deterministic lookups.
`DictionaryPackCatalog` validates dictionary-pack descriptor JSON, rejects
missing/malformed/future-schema descriptors, exposes language lookups, and
produces `AddonProvenanceReport`s for the future Settings/Addons UI and asset
mounting slice.

v1.8.82 then implemented the Next-10.3b persisted signing-pin foundation:
`AddonSigningPinSet` parses/encodes the `packageName=SHA-256` newline-string
format with malformed-line tolerance and first-seen preservation,
`prefs.addon.signingCertPins` is the durable JetPref key, and `AddonRegistry`
can round-trip through the codec without taking a JetPref dependency.

v1.8.83 then implemented the Next-10.3c startup reconciliation slice:
`FlorisImeService` runs `AddonEnumerator` during startup, `AddonRegistryStartup`
reconciles discovered manifests against the persisted signing pins,
`AddonRegistryStore` publishes the active process-wide registry, and startup
writes back canonical pins only when first-seen addons or malformed stored lines
change the trust set.

v1.8.84 then implemented the Next-10.3d Settings status slice:
`Routes.Settings.Addons` and the Home screen expose `AddonsSettingsScreen`,
which shows accepted/rejected/pinned counts, accepted addon package/type/version/
license/size/signing-fingerprint details, rejected addon reasons, install
guidance, and a manual rescan action that reuses `AddonRegistryStartup`.

## 3. Module layout

From [settings.gradle.kts](../../../settings.gradle.kts):

```
:app              — IME + Settings UI + addon facades
lib/android       — Android utility extensions
lib/color         — color math
lib/compose       — Compose helpers
lib/kotlin        — pure-Kotlin utilities
lib/snygg         — Snygg theme engine
:benchmark        — Macrobenchmark module — present on disk, NOT in active settings
:lib:native       — placeholder for future native add-ons — commented out
```

Both `:benchmark` and `:lib:native` are present-but-detached. Macrobenchmark
trace sections are emitted from production paths (six in [README.md](../../../README.md#L226-L233)) but
no device-number capture is wired into CI.

## 4. Source size

| Tree | .kt count |
|---|---|
| `app/src/main/kotlin` | 396 |
| `app/src/test` | 159 |
| `lib/*/src` | 97 (total across modules) |
| **Total tracked Kotlin** | ~652 |

Top-15 largest sources (LOC, [`find ... wc -l`](#)):

| LOC | Path |
|---|---|
| 1763 | `app/.../ime/indic/IndicTransliterator.kt` |
| 1387 | `app/.../ime/text/keyboard/TextKeyboardLayout.kt` |
| 1258 | `app/.../app/settings/theme/EditPropertyDialog.kt` |
| 1255 | `app/.../app/AppPrefs.kt` |
| 1239 | `app/.../ime/nlp/latin/LatinLanguageProvider.kt` |
| 1227 | `app/.../ime/keyboard/KeyboardManager.kt` |
| 1053 | `app/.../ime/nlp/NlpManager.kt` |
|  903 | `app/.../app/settings/theme/ThemeEditorScreen.kt` |
|  891 | `app/.../app/ext/ExtensionEditScreen.kt` |
|  840 | `app/.../app/settings/voice/VoiceInputScreen.kt` |
|  825 | `app/.../ime/media/emoji/EmojiPaletteView.kt` |
|  808 | `app/.../app/EnumDisplayEntries.kt` |
|  771 | `app/.../ime/editor/EditorInstance.kt` |
|  766 | `app/.../ime/clipboard/ClipboardInputLayout.kt` |
|  746 | `app/.../app/settings/theme/EditRuleDialog.kt` |

`TextKeyboardLayout.kt` (1387 LOC) is the renderer hotspot and carries the
three documented `TODO quick'n'dirty hack` lines plus the unstable
`constraints.maxWidth` FIXME that ROADMAP §2 flags as a known refactor debt.

## 5. Subsystem map (where things live)

Under `app/src/main/kotlin/dev/patrickgold/florisboard/`:

```
app/...           — Compose Settings UI (per-feature sub-packages)
  apptheme        — app-level theming
  devtools        — dev/debug screens
  ext             — extension import/edit/list
  settings/{advanced,clipboard,dictionary,gestures,keyboard,localization,
    mcp,media,smartbar,sync,theme,typing,voice} — per-area Settings screens
  setup           — first-run flow

ime/...           — IME runtime
  addon           — REGISTER_ADDON plugin contract (Next-10.1/10.2)
  bidi            — RTL / mixed-direction shaper (L4)
  cjk             — facade for librime backend (L3)
  clipboard       — encrypted history + provider + UI
  core            — service binding, lifecycle glue
  dictionary      — UserDictionary + DictionaryManager + import/export
  editor          — InputConnection wrappers
  geez            — Ge'ez/Ethiopic SERA transliterator (L6)
  handwriting     — Stylus stroke facade (Next-4.2)
  hardware        — Hardware-keyboard layout parsers (Keyman / KLC)
  indic           — Indic transliteration (1763 LOC; 8 scripts)
  input           — Touch handling / gesture detector
  keyboard        — Layout / KeyboardManager / FlorisImeSizing
  landscapeinput  — Landscape-specific input behaviors
  lifecycle       — IME lifecycle hooks
  mcp             — Model Context Protocol daemon bridge (AIDL)
  media           — Emoji palette + emoticon + sticker
  nlp             — Autocorrect / predictions / multilingual / NeuralReranker
    advanced      — Property-tested predictor + tuning
    han           — Chinese shape-based (FlorisBoard upstream)
    kenlm         — KenLM binary header reader (Next-3.1 partial)
    latin         — SCOWL + SymSpell + ZipfFrequencyTable
  passkey         — WebAuthn injection (L10) facade
  popup           — Long-press alt-glyph popups
  sheet           — Bottom-sheet panels
  smartbar        — Suggestion strip + quick-action editor
    quickaction   — QuickAction registry + Smartbar profiles
  smartcompose    — Ghost-text + rewrite/tone router (L1.1 boundary)
  snippet         — Espanso parser (L11)
  sync            — CRDT + Syncthing pairing (Next-5)
  tasker          — Tasker INSERT_TEXT/CLIP/etc. receiver
  text            — Per-key rendering, composing logic
    composing     — Composing-region helpers
    gestures      — StatisticalGlideTypingClassifier
    key / keyboard — Layout assembler
  theme           — ThemeManager, PerAppAccentResolver
  translate       — InlineTranslator + cache + router (L2.1)
  voice           — FUTO handoff + Vosk streaming + Whisper model selector
  window          — Floating / split / one-handed window controllers
  wordstyles      — WordStyles renderer (L12)

lib/...           — local utility helpers (cache, compose, devtools, ext, io, util)
screenshot/...    — Roborazzi capture rule scaffolding
```

## 6. CI / workflows

[.github/workflows/](../../../.github/workflows/):

| File | Trigger | What it does |
|---|---|---|
| `android.yml` | push / PR | wrapper-validation → root crash/replay log guard → `verifyNoInternetPermission` → `:app:testDebugUnitTest` → `:app:lintDebug` → `:app:assembleDebug` → 16KB-alignment check (zipalign -c -P 16). Reports uploaded `if: always()` |
| `release.yml` | `workflow_dispatch` | version match → NOTICE/LICENSES check → re-run gates → keystore decode → `:app:assembleRelease` → SHA-256 manifest → `gh release create` |
| `dependency-scan.yml` | PR + weekly cron + on version-file change | dependency-review-action@v4 + osv-scanner-action@v2.0.2 + dep-tree upload |
| `roborazzi-baseline.yml` | `workflow_dispatch` | `:app:recordRoborazziDebug` baseline capture |
| `crowdin-upload.yml` | PR | upload string resources to Crowdin |
| `validate-strings-no-translations.yml` | PR | refuse PRs that hand-edit translations (Crowdin-only) |

## 7. Release stream

- 100 tags in repo after the v1.8.84 release tag; local release tags now run
  through `v1.8.84`. Push remains a maintainer-host task because this VM
  cannot push to `SysAdminDoc/SwiftFloris`.
- 80+ `RELEASE_NOTES_v*.md` files in repo root — per-release file pattern enforced.
- README was caught up by the later same-day pass; keep it in lockstep
  with future release notes.
- `app-release-v1.5.2.apk` (9.7 MB) stays in repo root as a historical anchor;
  newer signed APKs live in `release/` (v1.5.3, v1.7.6, v1.7.7).
- Versioning is strict semver; minor bumps on each feature; major bumps held
  for breaking dictionary / extension format changes (per ROADMAP §11).

## 8. Permissions surface (AndroidManifest)

[app/src/main/AndroidManifest.xml](../../../app/src/main/AndroidManifest.xml):

| Permission | Purpose |
|---|---|
| `android.permission.VIBRATE` | Haptic feedback |
| `android.permission.POST_NOTIFICATIONS` | Android 13+ runtime notif perm |
| `dev.patrickgold.florisboard.permission.REGISTER_ADDON` (defined) | Signature-protected; required by `TaskerActionReceiver` + addon broadcasts |

**No INTERNET. No ACCESS_NETWORK_STATE. No WiFi. No RECORD_AUDIO.** The
voice path either hands off to FUTO Voice Input or asks the user when needed.

Notable receivers/providers:

- `FlorisImeService` — BIND_INPUT_METHOD
- `FlorisSpellCheckerService` — BIND_TEXT_SERVICE
- `TaskerActionReceiver` — Tasker `swiftfloris.action.{INSERT_TEXT, INSERT_CLIP, SWITCH_LAYOUT, TRIGGER_VOICE}`, gated on the signature permission
- `ClipboardMediaProvider`, `StickerMediaProvider`, `FileProvider`

`<queries>` block declares visibility for `org.futo.voiceinput` + the five
`REGISTER_*` action intents (Android 11+ visibility rules).

## 9. Tests

| Surface | Framework | Approx count |
|---|---|---|
| Unit (Kotest) | `:app:testDebugUnitTest` | 998+ (HEAD; growing) |
| Visual regression | Roborazzi 1.60.0 (plugin active) | scaffold + a few suites; baselines still bootstrap state per workflow `continue-on-error: true` |
| Macrobenchmark | androidx-benchmark 1.4.1 | 4 tests scaffolded in `:benchmark`, not yet in `settings.gradle.kts` |
| Replay fixtures | `swiftkey/replay/trace_replay_cases.jsonl` | pinned ranker behaviors |
| Property tests | Kotest property | LatinSuggester (11 invariants), Multilingual, GlideRescorer |

## 10. TODO / FIXME inventory (37 markers, 24 files)

After v1.8.x hardening passes, **no `TODO()` crash stubs remain** in
production paths. The 37 markers are design debt:

- `FlorisLocale.kt:217/227` — hard-coded capitalization / autospace tables;
  ROADMAP recommends ICU-driven replacement.
- `LanguagePackExtension.kt:52/77/84` — multi-type loading + DB locking notes
  (load-bearing once external dictionary-pack APKs land).
- `HanShapeBasedLanguageProvider.kt:103/107` — language-pack type assumption
  (FlorisBoard upstream debt).
- `TextKeyboardLayout.kt:258/301/314` — unstable `constraints.maxWidth` +
  rotation hack (renderer rewrite candidate; touches split-mode P3 work).
- `KeyboardMode.kt:22/30/32` — deprecated values kept for compatibility.
- `EditorInstance.kt`-adjacent — none — recent hardening passes cleared
  most of the editor TODOs.
- The rest are surface-polish notes (theme editor refactors, smartbar pref
  retirement, etc.) — none blocking.

## 11. What's intentionally not present

| Missing thing | Reason |
|---|---|
| `.cursor/rules/**`, `.windsurfrules` | Not present; `AGENTS.md`, `CLAUDE.md`, and `PROJECT_CONTEXT.md` now carry the agent context. |
| `PROJECT_CONTEXT.md` | Present at repo root as the canonical consolidated project memory written by this research run. |
| `ARCHITECTURE.md` | Flagged in ROADMAP §11 as a planned doc once N11 finishes; not yet present. |
| `CHANGELOG.md` | Per-release `RELEASE_NOTES_v*.md` files supersede this; no combined changelog by design. |
| `CONTRIBUTING.md` | Not present; contribution flow is documented in `README.md` §Contributing. |
| `.ai/` | Created by this research run. |
| Closed-source `.so` blobs | Rejected by §10 of `ROADMAP.md`. |
| Telemetry / crash auto-upload | Rejected; opt-in only. |

## 12. Active development signals

- **Tag cadence:** ~50 patch releases in May 2026 alone (v1.8.16 … v1.8.84).
  The local tag stream was recovered through `v1.8.84`; future release notes
  commits should be tagged at the same time.
- **Merge freeze pressure:** the 2026-05-31 SwiftKey account cutoff is **14
  days from HEAD**. Phase A items (migration importer + encryption envelope +
  rollback dialog) all landed before this research run.
- **Commit author:** every commit attributed `Matt <matt@mavenimaging.com>`.
- **No active PR conversation visible from local repo state** (push blocked
  to remote 403; collaborator activity won't surface locally).

## 13. Known limitations of this snapshot

- This memo was assembled by reading `README.md`, `ROADMAP.md` (sampled),
  `SWIFTKEY_PARITY_ROADMAP_2026-05-17.md`, `IMPROVEMENT_PLAN.md`, the
  manifest, the version catalogs, and the file tree. The 340-KB `ROADMAP.md`
  was not read end-to-end (exceeds the read tool's 25K-token cap); section
  headings + sampled tier ranges (§§0–4, §6, §7, §8, §11, §14, §16,
  appendix) were used to map structure. The companion
  [MEMORY_CONSOLIDATION.md](MEMORY_CONSOLIDATION.md) records what could and
  could not be reconciled.
- External claims (e.g. "Gemma 3 270M ~135MB") were not re-verified in this
  pass; see [SOURCE_REGISTER.md](SOURCE_REGISTER.md) for which need to be.
