# SwiftFloris — Project Context

**Maintained at root for fast onboarding.**
**Last consolidated:** 2026-05-18 (from the autonomous research run at
[`.ai/research/2026-05-17/`](.ai/research/2026-05-17/) plus the first
three seventh-pass follow-up slices).

This file is the single fastest read for an AI session, new contributor, or
maintainer-context refresh. It does **not** replace [ROADMAP.md](ROADMAP.md),
[SWIFTKEY_PARITY_ROADMAP_2026-05-17.md](SWIFTKEY_PARITY_ROADMAP_2026-05-17.md),
[IMPROVEMENT_PLAN.md](IMPROVEMENT_PLAN.md), or any per-release notes — it
distills their durable content into one page so those longer documents
don't have to be re-read every time.

---

## 1. What SwiftFloris is

A privacy-first **Android keyboard (IME)** forked from
[FlorisBoard](https://github.com/florisboard/florisboard) and pushed toward
SwiftKey-class multilingual typing **without the cloud**. Apache-2.0 main
app, no `INTERNET` permission, no vendor account.

**Wedge:** *"every paywalled cloud feature, fully on-device, fully
auditable, with zero account requirement."* The whole product exists in
the gap between (a) Apple/Samsung's on-device-AI-locked-to-vendor-silicon
and (b) Microsoft/Google/Grammarly's cloud-and-account-bound keyboards.

## 2. Load-bearing invariants

These are pinned by build gates, not just by marketing. Touching any of
them requires changing both the relevant code *and* the gate.

| Invariant | Pinned by |
|---|---|
| No `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_NETWORK_STATE`, `CHANGE_WIFI_STATE` permission | [Gradle task `verifyNoInternetPermission`](app/build.gradle.kts) runs as part of `preBuild` on every variant; CI re-runs it; `:app:assembleDebug` fails if violated |
| Apache-2.0 main app | `LICENSE`, `NOTICE`, `LICENSES/`; addons under their own license live in a separate APK (signature-protected `permission.REGISTER_ADDON`) |
| No closed-source `.so` blobs | Reproducible-build pin matrix + build-twice APK self-check in [docs/REPRODUCIBLE_BUILDS.md](docs/REPRODUCIBLE_BUILDS.md); F-Droid verified-tier target |
| No vendor account, no telemetry | [docs/THREAT_MODEL.md](docs/THREAT_MODEL.md) |
| Personal dictionary encrypted at rest | SQLCipher 4.16.0 + Tink / AndroidKeystore passphrase wrapper + `PersonalDictionaryEncryptionTest` |
| Personal dictionary excluded from cloud-backup | `backup_rules.xml` (read by `<application android:dataExtractionRules android:fullBackupContent>`) |
| `FLAG_SECURE` on password fields | `FlorisImeService.applyFlagSecureForCurrentField` (N7.2) |
| 16 KB native page alignment | CI `zipalign -c -P 16 -v 4` step (Next-12.4); AGP 9 + NDK 29 produce aligned `.so` by default |

If a proposed change conflicts with any of these, the answer is "move that
feature into an addon" — never "loosen the invariant."

## 3. Stack at HEAD (v1.8.113)

```
Kotlin 2.3.21 · Compose BOM 2026.05.00 · Material 3 + material-kolor 4.1.1
AGP 9.2.1 · Gradle 9.4.1 · JDK 17 · KSP 2.3.8
minSdk 26 (Android 8.0) · targetSdk/compileSdk 36 (Android 16; API 37 gates wired)
Room 2.8.4 · SQLCipher 4.16.0 · Tink Android 1.21.0 · Coroutines 1.11.0 · Coil 3.4.0 · ZXing 3.5.4
Kotest 6.1.11 · Roborazzi 1.60.0 (plugin active) · Robolectric 4.16.1
NDK 29.0.14206865 · Build Tools 36.0.0
Crowdin localization · No INTERNET permission · 1000+ unit tests
```

The [.ai/research/2026-05-17/SECURITY_AND_DEPENDENCY_REVIEW.md](.ai/research/2026-05-17/SECURITY_AND_DEPENDENCY_REVIEW.md)
plus the fifth-pass correction in
[.ai/research/2026-05-17/FIFTH_PASS_FINDINGS.md](.ai/research/2026-05-17/FIFTH_PASS_FINDINGS.md)
flag several pins as materially behind. Bump-batch A shipped in v1.8.69:
coroutines 1.10.2 → 1.11.0, KSP 2.3.5 → 2.3.8, ZXing 3.5.3 → 3.5.4,
and AboutLibraries 14.0.1 → 14.2.0. Bump-batch C shipped in v1.8.74:
AGP 9.0.0 → 9.2.1 and Compose BOM 2026.03.01 → 2026.05.00. The
`androidx-security-crypto:1.1.0-alpha06` migration shipped in v1.8.68:
local encrypted preference payloads now use Tink Android + direct
AndroidKeystore wrapping, with one-shot AndroidX encrypted-preference
migration. `androidx-activity 1.13.0` is stable; do **not** downgrade it.
Bump-batch B shipped in v1.8.71: Roborazzi 1.55.0 → 1.60.0 and
Robolectric 4.14.1 → 4.16.1.

v1.8.70 was a docs-only migration-window follow-up: the README now explains
how Samsung One UI 7+ Galaxy AI Writing Assist and Grammarly for Android can
coexist with SwiftFloris as the no-network default keyboard. No app code or
permission surface changed.

v1.8.72 was a docs-only glide-strategy correction: HeliBoard / NLnet
open-glide remains an additive future integration path, while
`swiftfloris-statistical` is the production glide engine until a permissive
open library and dataset land and beat the N1.4 replay benchmark.

v1.8.73 was a repo-hygiene guardrail: ignored local JVM crash/replay logs were
moved out of the repo root, and CI now runs
`scripts/check-no-root-crash-logs.sh` to reject committed root
`hs_err_pid*.log` / `replay_pid*.log` files.

v1.8.74 was Bump-batch C: Android Gradle Plugin 9.2.1 and Compose BOM
2026.05.00. `compileSdk` / `targetSdk` intentionally remain 36 until the
Android 17 behavior-gate checklist is closed.

v1.8.75 shipped the Next-6.4a macOS `.keylayout` parser. The hardware-layout
import stack now has Windows KLC, Keyman LDML, and macOS XML parsers feeding the
same `HardwareKeyboardLayout` target. v1.8.76 shipped the adjacent
`HardwareKeyboardRuntimeMapper`: parsed layouts can bind to Android hardware
keyboard `deviceId` values, prune detached devices via `InputManager`, and map
`KeyEvent` scan/key-code input through KLC/macOS/source-name fallbacks before
`KeyboardManager` commits printable characters.

v1.8.77 shipped the Next-9.5 user-imported sticker folder. Settings → Emoji &
stickers stores a read-only SAF folder URI in `prefs.sticker.userFolderUri`,
`UserStickerRepository` enumerates local PNG / WebP / JPEG / GIF documents into
an Imported pack, `StickerPaletteView` decodes local previews, and
`StickerMediaProvider` proxies imported stickers through the existing
rich-content `commitContent` path. No network, account, or broad media-library
permission was added.

v1.8.78 ships the Tier-3 #34 Keyman `.kmp` package import foundation.
`KeymanPackageParser` opens ZIP-compatible `.kmp` packages, normalizes
`kmp.json` metadata, extracts any LDML XML layouts through `KeymanLdmlParser`,
skips unsafe package paths, and classifies lexical-model-only, mixed,
metadata-only, invalid, LDML-ready, and compiled-engine-required packages.
Compiled `.kmx` / `.js` keyboard execution remains future addon/runtime work,
not an in-`:app` dependency.

v1.8.79 ships the Tier-3 #35 honeycomb hex layout production wire-up. The
bundled `honeycomb` character layout is registered in `extension.json`,
`LayoutManager` marks it as `TextKeyboardLayoutStyle.Honeycomb`,
`TextKeyboard.layoutHoneycomb(...)` positions real `TextKey` instances in the
hex tessellation, `TextKeyboardLayout` clips production Snygg key surfaces to
`HoneycombHexShape`, and hit testing now uses the actual hex shape instead of
rectangular bounding boxes.

v1.8.80 ships the Tier-3 #36 SQLCipher provider migration plan. The runtime
dependency stays on the stock `sqlcipher-android` Community AAR, but
`docs/SQLCIPHER_PROVIDER_MIGRATION.md` now records the upstream LibTomCrypt /
OpenSSL provider state, migration triggers, OpenSSL proof-of-concept steps,
16 KB page-size gates, verification requirements, and rollback rules.

v1.8.81 ships the Next-10.3a addon catalog foundation. `AddonRegistry`
reconciles `AddonEnumerator` snapshots into process-live addon state, preserves
first-seen signing-certificate pins, rejects package-name hijacks with changed
certificates, and exposes deterministic addon lookups. `DictionaryPackCatalog`
validates enrolled dictionary-pack descriptor JSON, rejects missing/malformed/
future-schema descriptors, exposes per-language catalog entries, and produces
`AddonProvenanceReport`s for the future Settings → Addons UI and dictionary
asset-mounting slice.

v1.8.82 ships the Next-10.3b persisted signing-pin foundation.
`AddonSigningPinSet` parses/encodes the newline-string `packageName=SHA-256`
pin format with malformed-line tolerance and first-seen preservation,
`prefs.addon.signingCertPins` is the durable JetPref key, and `AddonRegistry`
can round-trip through the codec without depending on JetPref directly.

v1.8.83 ships the Next-10.3c addon registry startup wiring.
`FlorisImeService` scans installed addon manifests on startup through
`AddonEnumerator`, `AddonRegistryStartup` reconciles that snapshot against the
persisted signing-pin string, `AddonRegistryStore` publishes the active
process-wide registry, and canonical pins are written back only when first-seen
addons or malformed stored lines change the trust set.

v1.8.84 ships the Next-10.3d Settings -> Addons read-only status surface.
`AddonsSettingsScreen` lets users inspect accepted/rejected addon APKs, manually
rescan installed addon packages through the same startup reconciliation path,
and review package, type, version, license, size, and signing-certificate
details before destructive revoke/reset controls or dictionary asset mounting
land.

**v1.8.85 – v1.8.103** ships a 19-release session covering the sixth research-
pass cross-subsystem hardening + the F1 – F12 follow-up roster + outreach
drafts + this release-hygiene catch-up. Full breakdown in
[`.ai/research/2026-05-17/SIXTH_PASS_FINDINGS.md`](.ai/research/2026-05-17/SIXTH_PASS_FINDINGS.md)
and in [`ROADMAP.md` §0 v5.5 + v5.4](ROADMAP.md). Net deltas to invariants:

- **§2 No-`INTERNET` gate** now scans merged manifests + honours
  `tools:node="remove"` (v1.8.85).
- **§2 Personal-dictionary excluded from cloud-backup AND D2D transfer.**
  New `app/src/main/res/xml/data_extraction_rules.xml` ships the correct
  Android-12+ schema with explicit excludes (v1.8.85).
- **§2 FLAG_SECURE coverage** extended via `keyVariation == PASSWORD`
  propagation for `TYPE_NUMBER_VARIATION_PASSWORD` and a Compose
  `DisposableEffect` on the encrypted-dictionary passphrase dialog
  (v1.8.86, v1.8.87).
- **New build gate `verifyDataExtractionRules`** pins the load-bearing
  excludes against accidental rewrite (v1.8.95).
- **`ZipUtils.unzip` atomic-abort semantics** — security violations throw,
  benign anomalies continue with warning (v1.8.85 + v1.8.89).
- **Hardware-keyboard runtime mapper** now thread-safe and AltGr-aware
  (v1.8.85). LDML parser honours `shift=` over `longPress=` and stores
  alternates in a `longPressAlternates` field (v1.8.92 + v1.8.102).
- **CI workflow `permissions: { contents: read }`** at file scope on
  `android.yml`, `crowdin-upload.yml`, `reproducible-build.yml`;
  `pull_request_target` env-var hardening pattern documented;
  third-party action floating tags pinned to verified SHAs (v1.8.85 +
  v1.8.96).
- **Outreach drafts** for the SwiftKey-refugee discovery gap shipped
  at `docs/outreach/2026-05-17-swiftkey-migration/` covering
  AlternativeTo, BGR, Android Authority, and r/Swiftkey.

**v1.8.104 – v1.8.113** ships the seventh research-pass privacy,
voice, and clipboard hardening layer. v1.8.104 – v1.8.110 closed the
app-declared privacy-flag and voice/clipboard data-leak findings
documented in `ROADMAP.md`; v1.8.111 closes follow-up **G2** and
**G12** by bounding provider-backed clipboard media clones and rejecting
oversized preview-image dimensions before decode; v1.8.112 closes
follow-up **G6** by closing provider-backed media before automatic
history rotation / expiry deletes the Room rows; v1.8.113 closes
follow-up **G7** by pinning `VoiceInputSetupActivity` as non-exported
and validating the setup-intent extras before the dialog renders.

The only sixth-pass F-roster item still open is **F11** (Roborazzi visual
baselines for the new themes + Addons surface) — needs Android SDK +
on-device baseline record from the maintainer build host.

## 4. Module layout

```
:app                    — IME + Settings UI + addon facades
lib/android             — Android utility extensions
lib/color               — color math
lib/compose             — Compose helpers
lib/kotlin              — pure-Kotlin utilities
lib/snygg               — Snygg theme engine
:benchmark              — Macrobenchmark (present on disk, NOT in active settings)
:lib:native             — placeholder for future native add-ons (commented out)
```

`app/src/main/kotlin/dev/patrickgold/florisboard/` is the work tree. Full
subsystem map in
[.ai/research/2026-05-17/STATE_OF_REPO.md §5](.ai/research/2026-05-17/STATE_OF_REPO.md).

## 5. Distribution

- **Primary:** [GitHub Releases](https://github.com/SysAdminDoc/SwiftFloris/releases)
- **Recommended for auto-updates:** [Obtainium](https://github.com/ImranR98/Obtainium)
  via the one-tap `obtainium://` URL in [README.md](README.md)
- **Target:** F-Droid (verified-reproducible badge; metadata submission outstanding)
- **Not on Google Play** by design (Play forces target-SDK churn and Integrity-API
  tradeoffs that conflict with the no-telemetry posture)
- **Tag cadence recovered locally** — release tags `v1.8.41` through
  `v1.8.69` were backfilled on 2026-05-17 from their matching
  `gradle.properties` version-bump commits, and `v1.8.70` through `v1.8.84`
  are tagged with their release commits. The tags still need to be
  pushed from the maintainer host because this VM cannot push to
  `SysAdminDoc/SwiftFloris`.

## 6. Roadmap structure (where to put what)

| If your change is… | Lives in… |
|---|---|
| One feature slice, one release | A new `RELEASE_NOTES_vX.Y.Z.md` at repo root + a `gradle.properties` bump |
| A SwiftKey-parity slice (Phase A/B/C/D/E) | The "Phased plan" in [SWIFTKEY_PARITY_ROADMAP_2026-05-17.md](SWIFTKEY_PARITY_ROADMAP_2026-05-17.md) |
| A roadmap-tier change (NOW / NEXT / LATER / UNDER CONSIDERATION) | [ROADMAP.md](ROADMAP.md) §6/§7/§8/§9 |
| A quality / a11y / perf / test / CI / release-hygiene workstream | [IMPROVEMENT_PLAN.md](IMPROVEMENT_PLAN.md) |
| A research finding that updates a prior roadmap claim | [ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md](ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md) (this run's addendum) |
| A security / dependency / crypto migration | [docs/SECURITY.md](docs/SECURITY.md) + [docs/THREAT_MODEL.md](docs/THREAT_MODEL.md) + [docs/REPRODUCIBLE_BUILDS.md](docs/REPRODUCIBLE_BUILDS.md) |
| Architecture or contributor workflow | [ARCHITECTURE.md](ARCHITECTURE.md) + [CONTRIBUTING.md](CONTRIBUTING.md) |
| A UX / migration walkthrough | `docs/MIGRATE_FROM_SWIFTKEY.md`, `docs/INLINE_AUTOFILL.md`, `docs/TASKER_INTEGRATION.md` |
| An external-research prompt for a fresh AI session | [docs/AI_PROMPTS_EXTERNAL_WORK.md](docs/AI_PROMPTS_EXTERNAL_WORK.md) |
| Addon spec (theme / dict / language / layout / popup-mapping pack) | [docs/addons/](docs/addons/) |

The roadmap files are intentionally append-mostly. `ROADMAP.md` is now
~340 KB and tracks the full history; the parity roadmap is the current
sprint plan.

## 7. Current sprint anchor

**SwiftKey account retirement: 2026-05-31** — Microsoft is deleting
standalone SwiftKey accounts on that date. Non-MS-account data is gone
after the cutoff. The migration funnel is the highest-priority external
clock the project has.

Phase A of `SWIFTKEY_PARITY_ROADMAP_2026-05-17.md` is sized for this:

- ✅ A1 — README outreach pivot (shipped v1.8.52)
- ✅ A2 — Post-import confirmation + rollback (shipped v1.8.53)
- ✅ A3 — Encrypted-blob export/import round-trip (codec shipped v1.8.54; Settings wiring shipped v1.8.65)
- ⏳ Marketing-side completion (Reddit thread + 2026-05-30 pinned release)

Roadmap addendum N8.7 also shipped in v1.8.66: setup now starts with a
first-run AI/ML transparency disclosure before IME enablement, and
Settings → About → **AI features in this keyboard** reopens the same
local-only/no-account/no-telemetry explanation with links to
`docs/PRIVACY_AND_AI.md`, `docs/THREAT_MODEL.md`, and this context file.

Roadmap addendum N12.5 shipped in v1.8.67: `.github/workflows/reproducible-build.yml`
now runs `scripts/verify-reproducible-apk.sh`, which builds release APKs
from two clean worktrees at the same commit and fails on byte drift before
F-Droid's rebuilder becomes the first detector.

Roadmap addendum N7.6 shipped in v1.8.68: AndroidX Security Crypto was
removed from `:app`. `TinkStringPreferenceCrypto` now wraps local encrypted
preference payloads with Tink `Aead` + AndroidKeystore-held AES-256-GCM keys,
and migrates legacy AndroidX encrypted-preference strings for the SQLCipher
personal-dictionary passphrase and legacy clipboard-history store.

Bump-batch A shipped in v1.8.69: coroutines `1.11.0`, KSP `2.3.8`,
ZXing `3.5.4`, and AboutLibraries `14.2.0`. Maven metadata showed
AboutLibraries `15.0.0-b01` as the latest artifact, but it is beta and was
intentionally skipped for the stable line.

Phase B (touch & decoder calibration):

- ✅ B1 — Sentence-position priors expansion seed (shipped v1.8.60) —
  `ColdStartNextWordPriors` now covers EN/CS/DE/ES/FR/IT/PT and
  `assets/freq/` includes top-1,000 Zipf seed overlays for CS/DE/ES/FR/IT/PT
- ✅ B2 — Quick-prediction-insert tuning (shipped v1.8.61) —
  blank-current-word spacebar insertion now requires cold-start, sentence
  boundary, or newline context plus a configurable weighted-confidence floor;
  plain-space suppression uses the same ranker decision
- ✅ B3 — Shared-spelling bilingual handling (shipped v1.8.55)
- ✅ B4 — Same-sentence language switch hardening (shipped v1.8.56)
- B5 — Decoder field calibration with real traces (planned)

Phase C/D opened in the same release window:

- ✅ C2 — Arrow-keys bottom-row preset (shipped v1.8.57) — SwiftKey
  "Modes → Arrow keys" parity via new `BottomRowPreset.Navigation` +
  `BottomRowKey.ARROW_*`
- ✅ D2 — Generic task-creation quick action (shipped v1.8.58) —
  `QuickAction.InsertTask` via `Intent.ACTION_SEND` chooser; works with
  Tasks.org / OpenTasks / Google Tasks / Joplin / Notion / Markor
- ✅ D3 — Typing-stats accuracy-delta (shipped v1.8.59) —
  `CorrectionOutcomePriors.accuracyDelta()` backs the Settings → Typing
  stats row for current-week accepted corrections versus last week
- ✅ C1 — Split-keyboard renderer wire-up inside `TextKeyboardLayout`
  (shipped v1.8.62) — split mode now pre-shrinks the base layout by
  the active gutter, post-shifts right-half key bounds back into the final
  container, rejects non-viable narrow roots, and refuses nearest-key rescue
  inside the gutter
- ✅ C3 — High-Contrast AAA theme + animated theme (shipped v1.8.63) —
  `swiftkey_high_contrast` registers an AAA-tested Snygg stylesheet with
  explicit key / popup / inline-chip borders; `aurora_animated` registers a
  Snygg palette plus reduced-motion-aware Compose `GenericShape` background
  bands gated by `LocalActiveThemeName`
- ✅ D1 — Calendar quick-insert (shipped v1.8.64) —
  `QuickAction.InsertCalendarEvent` reads local `CalendarContract.Instances`
  for today + next 7 days, requests `READ_CALENDAR` only after explicit tap,
  and shows an IME-local picker that inserts selected event title + date/time

## 8. AI / model surfaces — current state

| Surface | Status |
|---|---|
| **Heuristic decoder** (SymSpell d1+d2 + bigram+trigram + adaptive touch + multilingual ranker) | ✅ shipped; production path; calibrated through HEAD |
| **NeuralCandidateReranker boundary** | ✅ facade shipped; no model bound yet (gated on L1) |
| **Smart Compose ghost-text** | ✅ candidate type + UI surface shipped v1.8.3; provider is no-op until L1 lands |
| **Tone / Rewrite router** | ✅ scaffolded; gated on L1 |
| **Inline translation** | ✅ facade + cache + router + language-pack manager shipped; Bergamot WASM addon (L2.1a) outstanding |
| **Voice** | ✅ FUTO Voice Input handoff (preferred) + Vosk streaming fallback + RAM-aware model selector + local Whisper/Vosk model manager |
| **Stylus handwriting** | ✅ facade + toggle shipped (Next-4.1/4.2/4.3); recogniser ships in `addons/handwriting-mlkit/` (Next-4.2a, external) |
| **AI transparency surface** | ✅ first-run setup explainer + Settings → About screen shipped v1.8.66; `docs/PRIVACY_AND_AI.md` is the canonical long-form disclosure |
| **MCP daemon bridge** | ✅ shipped end-to-end (AIDL + Android client + discovery + service lifecycle + Settings + per-daemon enable/disable + per-tool switches) |
| **WebAuthn passkey injection** | ✅ detector + adapter contract shipped (L10); ceremony in addon (external) |
| **CJK Pinyin / Jyutping / Zhuyin / Mozc** | ⚠️ facade only; gated on librime JNI addon (L3) |
| **Bergamot offline NMT** | ⚠️ facade only; addon outstanding (L2.1a) |
| **Gemma 3 270M / FunctionGemma / LiteRT-LM** | ⚠️ facade + provider registry shipped; runtime + model in addon (L1.1a) |

**Important:** the AI / model surfaces all run on-device in their final
form. The addon delivery vehicle exists because runtimes (LiteRT-LM,
Bergamot WASM, librime, ML Kit Digital Ink) are multi-MB and not all
users want them — they ship as opt-in companion APKs through F-Droid /
GitHub Releases, signature-checked, with their own licenses, never linked
into `:app`.

## 9. The trap that this project explicitly avoids

These are rejected with reasoning. Restating so they don't get
re-litigated:

- Cloud sync of personal LM via vendor servers
- Microsoft / Google account requirement
- GPL / AGPL / FUTO Source-First code linked into `:app`
- Closed-source `libjni_latinimegoogle.so` blob
- Federated learning to vendor cloud
- In-keyboard ads
- Cloud-bound Bing / Copilot / Gemini API
- Default-on T9 layout (OK as alt; not default)
- In-keyboard search (Maps/YouTube/web)
- Tenor / Giphy GIF keyboard
- Google Play Store as primary distribution
- Self-update (in-app APK download + install)
- Mandatory analytics opt-out toggle that defaults on

Full reasoning in [ROADMAP.md](ROADMAP.md) §10.

## 10. Verification before any release

Per [ROADMAP.md](ROADMAP.md) §15 Definition of Done:

1. `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug` all green locally.
2. Manual QA pass on a real device.
3. Per-release `RELEASE_NOTES_v*.md` file written.
4. `gradle.properties` versionCode + versionName bumped.
5. New dep / asset → `NOTICE` / `LICENSES/` updated.
6. APK signed and installable; SHA-256 published in release notes.
7. CI passes: `verifyNoInternetPermission`, OSV scan, 16 KB alignment check.
8. (Workstream coverage) Tests added for new behavior; visual via Roborazzi
   if UI-touching; macro via Benchmark if perf-sensitive.

## 11. Memory rules captured by prior research

- **Per-release file pattern:** every release ships its own
  `RELEASE_NOTES_v<MAJOR>.<MINOR>.<PATCH>.md`. There is no rolled-up
  `CHANGELOG.md` and there should not be.
- **Build gate:** every code change must keep `:app:verifyNoInternetPermission`
  passing. The build fails otherwise.
- **License ceiling:** `:app` is Apache-2.0. GPL / AGPL / LGPL /
  Source-First belongs in an addon under its own license. Adapter
  contracts in `:app` are fine; runtimes / models that bring those
  licenses are not.
- **Push limitation (this maintainer's VM):** `git push` to
  `SysAdminDoc/SwiftFloris` fails with 403 from this VM. Commits land
  locally; the user pushes from a separate host. Don't try to push from
  the dev VM.
- **Repos master location:** `Z:\repos\` is the master directory on this
  VM; all SwiftFloris work happens at `Z:\repos\SwiftFloris\`.

## 12. Where the autonomous research run lives

[.ai/research/2026-05-17/](.ai/research/2026-05-17/) contains, in this run:

- [STATE_OF_REPO.md](.ai/research/2026-05-17/STATE_OF_REPO.md) — local reconnaissance
- [MEMORY_CONSOLIDATION.md](.ai/research/2026-05-17/MEMORY_CONSOLIDATION.md) — file inventory + conflict resolution
- [SOURCE_REGISTER.md](.ai/research/2026-05-17/SOURCE_REGISTER.md) — every source cited this run
- [RESEARCH_LOG.md](.ai/research/2026-05-17/RESEARCH_LOG.md) — search strategies + saturation
- [COMPETITOR_MATRIX.md](.ai/research/2026-05-17/COMPETITOR_MATRIX.md) — 14 OSS + 8 commercial keyboards, May 2026
- [SECURITY_AND_DEPENDENCY_REVIEW.md](.ai/research/2026-05-17/SECURITY_AND_DEPENDENCY_REVIEW.md) — every pin checked
- [DATASET_MODEL_INTEGRATION_REVIEW.md](.ai/research/2026-05-17/DATASET_MODEL_INTEGRATION_REVIEW.md) — datasets, models, integrations
- [FEATURE_BACKLOG.md](.ai/research/2026-05-17/FEATURE_BACKLOG.md) — raw harvested ideas
- [PRIORITIZATION_MATRIX.md](.ai/research/2026-05-17/PRIORITIZATION_MATRIX.md) — scored + tiered
- [CHANGESET_SUMMARY.md](.ai/research/2026-05-17/CHANGESET_SUMMARY.md) — what this research run created or changed
- (At repo root) [ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md](ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md) — the actionable additions

Future research runs should follow the same `.ai/research/<YYYY-MM-DD>/`
convention so prior runs remain auditable.

## 13. Quick navigation

| You want… | Read… |
|---|---|
| Project pitch + setup | [README.md](README.md) |
| The big roadmap with full history | [ROADMAP.md](ROADMAP.md) |
| The current sprint plan | [SWIFTKEY_PARITY_ROADMAP_2026-05-17.md](SWIFTKEY_PARITY_ROADMAP_2026-05-17.md) |
| The latest research run's recommendations | [ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md](ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md) |
| Quality / a11y / perf / test plan | [IMPROVEMENT_PLAN.md](IMPROVEMENT_PLAN.md) |
| Threat model + security posture | [docs/THREAT_MODEL.md](docs/THREAT_MODEL.md) + [docs/SECURITY.md](docs/SECURITY.md) |
| What ships in the next release | The latest `RELEASE_NOTES_v*.md` |
| What I'm allowed to put in `:app` | §2 above + [ROADMAP.md](ROADMAP.md) §1, §10 |
