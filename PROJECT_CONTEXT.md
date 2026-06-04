# SwiftFloris — Project Context

**Maintained at root for fast onboarding.**
**Last consolidated:** 2026-06-04 (from the autonomous research run at
[`.ai/research/2026-05-17/`](.ai/research/2026-05-17/) plus follow-up slices
through v1.8.227).

This file is the single fastest read for an AI session, new contributor, or
maintainer-context refresh. It does **not** replace [ROADMAP.md](ROADMAP.md),
[COMPLETED.md](COMPLETED.md), [CHANGELOG.md](CHANGELOG.md), or archived
planning snapshots such as
[docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17.md](docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17.md)
and [docs/archive/IMPROVEMENT_PLAN_2026-05-18.md](docs/archive/IMPROVEMENT_PLAN_2026-05-18.md).
It distills their durable content into one page so those longer documents
do not have to be re-read every time.

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

## 3. Stack at HEAD (v1.8.227)

```
Kotlin 2.3.21 · Compose BOM 2026.05.01 · Material 3 + material-kolor 4.1.1
AGP 9.2.1 · Gradle 9.5.1 · JDK 21 · KSP 2.3.9
minSdk 26 (Android 8.0) · targetSdk/compileSdk 36 (Android 16; API 37 gates wired)
Room 2.8.4 · SQLCipher 4.16.0 · Tink Android 1.21.0 · Coroutines 1.11.0 · Coil 3.4.0 · ZXing 3.5.4
Kotest 6.1.11 · Roborazzi 1.63.0 (plugin active) · Robolectric 4.16.1
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
Bump-batch B shipped in v1.8.71: Roborazzi 1.55.0 -> 1.60.0 and
Robolectric 4.14.1 -> 4.16.1. The compatible dependency freshness pass in
v1.8.216 moved Compose BOM to 2026.05.01, KSP to 2.3.9, and Roborazzi to
1.63.0. Kotlin 2.4.0 remains deferred until a matching KSP plugin exists, and
AndroidX Core 1.19.0 remains deferred until the API 37 compileSdk behavior gate.

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
details. v1.8.124 adds the destructive trust-management controls: reset every
saved addon signing-certificate pin without silently re-enrolling installed
APKs, or trust a changed certificate from the rejected list after confirmation
and rescan. v1.8.125 adds the runtime asset-mounting path: enrolled
dictionary-pack APK `assets/` are read through `PackageManager`, merged ahead
of bundled Latin dictionary baselines, and invalidated when addon registry
generation changes. v1.8.126 closes the Settings catalog/install-hint polish
with a dictionary-pack catalog group, descriptor rejection rows, and updated
install guidance.

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

**v1.8.104 - v1.8.227** ships the seventh research-pass privacy,
voice, clipboard, NLP, visual-regression, Addons trust/asset/catalog layer, and
input-behavior testability plus conservative lint/dependency cleanup and
performance-baseline / CI-quality / repo-hygiene / destructive-confirmation
polish / addon scan-progress / empty-state UX / preview-field polish layers,
followed by migration/import surfaces, settings search, hardware-keyboard
imports, release gates, dependency freshness, startup crash recovery, restore
diagnostic hardening, root-doc source-of-truth cleanup, the settings-search
catalog drift guard, and no-results fallback navigation.
v1.8.104 - v1.8.110 closed the
app-declared privacy-flag and voice/clipboard data-leak findings
documented in `ROADMAP.md`; v1.8.111 closes follow-up **G2** and
**G12** by bounding provider-backed clipboard media clones and rejecting
oversized preview-image dimensions before decode; v1.8.112 closes
follow-up **G6** by closing provider-backed media before automatic
history rotation / expiry deletes the Room rows; v1.8.113 closes
follow-up **G7** by pinning `VoiceInputSetupActivity` as non-exported
and validating the setup-intent extras before the dialog renders; v1.8.114
closes follow-up **G8** by requiring `RECORD_AUDIO` permission for every
enabled external voice IME package before external handoff is considered ready;
v1.8.115 closes follow-up **G10** by skipping clipboard description
classification for sensitive clips before raw text is read; v1.8.116
closes follow-up **G3** by reconciling clipboard-history rows,
`ClipboardFileInfo` rows, and stored provider files at startup; v1.8.117
closes follow-up **G4** by recreating `ClipboardFileInfo` rows during
clipboard media restore; v1.8.118 closes clipboard agent finding **#2**
by rejecting failed foreign `content://` media clones before phantom
provider rows enter history; v1.8.119 closes follow-up **G5** by
collecting Room history off Main, sorting on `Dispatchers.Default`, and
serializing history-size / timed-expiry eviction behind one maintenance
mutex; v1.8.120 closes follow-up **G1** by making the missing local
Whisper/Vosk recognizer runtime a first-class route availability gate and
marking the local model catalog preview-only until that runtime exists;
v1.8.121 closes follow-up **G9** by deleting the unused parallel
`ClipboardHistoryManager` Tink store and its unused panel so the Room-backed
`ClipboardManager` remains the sole live clipboard-history storage path.
v1.8.122 closes the carried-forward **G11** local audit with a concrete
KenLM mmap-reader hardening: `KenLmTrieReader.readBytesAt(...)` now rejects
header/pre-body absolute offsets instead of aliasing them to trie-body bytes,
and its header probe avoids large-file `toInt()` overflow.
v1.8.123 closes the carried-forward sixth-pass **F11** visual-regression item:
Roborazzi baseline PNGs are committed for the maintainer chip, SwiftKey High
Contrast, Aurora Animated, and Settings -> Addons surfaces, and CI now treats
`:app:verifyRoborazziDebug` as a hard gate.
v1.8.124 returns to the Addons roadmap and closes the signing-pin
revoke/reset UX slice with confirmed Settings actions plus targeted pin removal
in `AddonSigningPinSet`.
v1.8.125 closes the next local Addons loader slice by mounting enrolled
dictionary-pack APK assets into `LatinDictionaryStore` without extraction and
by tying dictionary cache invalidation to `AddonRegistryStore.generation()`.
v1.8.126 completes the current local Next-10.4 Addons polish by sharing the
descriptor reader between runtime and Settings, listing dictionary-pack
descriptor details, and surfacing descriptor rejection reasons.
v1.8.127 closes the Next-9.4a emoji pinned-group sheet: long-pressing emoji
can pin them to existing or newly named groups, and pinned-group chips commit
the saved emoji sequence from the palette.

v1.8.128 closes the L4.2 Nastaliq font item: the official OFL-1.1 Noto
Nastaliq Urdu hinted TTF is committed under `app/src/main/assets/fonts/`,
`NastaliqFontProvider` exposes a Compose `FontFamily`, and Urdu subtype key
labels/hints route Arabic-script text through the bundled font while Latin and
non-Urdu text keep the active Snygg font.

v1.8.129 closes the first archived improvement-plan editor/input behavior test
item by extracting `EditorInputBehaviorPolicy` and routing `EditorInstance` /
`KeyboardManager` through it. Focused JVM tests now pin accepted autocorrect
spacebar commits, rejected-correction protection, suppressed plain-space
predictions, punctuation auto-spacing, phantom spacing, double-space period,
and sentence-capitalization gates.

v1.8.130 closes the adjacent hardware-keyboard test item by extracting
`HardwareKeyboardInputPolicy`. The hardware keydown / keyup router is now
covered for space, enter, delete pass-through, shift, mapped letters, mapped
punctuation, mapped-key priority, and punctuation-triggered pending-autocorrect
flush behavior.

v1.8.131 closes the phantom-space/autospace lifecycle test item by adding
`EditorSpacingLifecycleStateTest`. The pure state holders are now covered for
one-update grace behavior, immediate update expiry, composing-region visibility,
and candidate-for-revert cleanup after explicit or editor-update deactivation.

v1.8.132 closes the glide typing delete interaction test item by extracting
`EditorInputBehaviorPolicy.shouldEscalateGlideBackspaceToWordDelete`. The
policy pins the `immediateBackspaceDeletesWord` path so an active committed
glide word escalates one character backspace to word deletion while disabled,
inactive, invalid-word, and explicit word-delete paths do not re-escalate.

v1.8.133 closes the incognito suggestion behavior test item by extracting
`SuggestionPrivacyPolicy`. App-declared no-personalized-learning now has a
focused JVM contract that overrides user force-off, dynamic incognito toggles
stay unavailable in app-private fields, and committed-word learning plus
touch-decoder evidence are suppressed in incognito or password contexts.

v1.8.134 closes the backup/restore test item and related validation-policy
extraction by adding `BackupRestorePolicy`. Backup completion, cancellation,
restore archive validation, no-content archive rejection, restore action
enablement, and partial-failure classification are now covered in JVM tests
instead of being implicit inside Compose screen state.

v1.8.135 closes the language-pack import/update test item by adding
`ExtensionImportPolicy`. Specific extension import screens now classify
readiness by manifest serial type before enabling import, so the language-pack
screen rejects keyboard/theme packs instead of presenting them as ready, while
new language-pack installs, user-installed updates, bundled-core rejection,
corrupted metadata, unsupported files, and import button enablement have focused
JVM coverage.

v1.8.136 closes the subtype editor validation test item by adding
`SubtypeEditorValidationPolicy`. The editor save-state now serializes a draft
model and delegates required-field checks to the policy, with focused JVM
coverage for default add-state missing fields, complete draft-to-subtype
building, select-placeholder rejection, and edit-state preservation.

v1.8.137 closes the theme editor validation test item by adding
`ThemeComponentMetaValidationPolicy`. Theme component metadata confirmation now
uses a pure policy for field validity, duplicate-ID detection, and normalized
apply data, with JVM coverage for valid apply normalization, invalid fields,
duplicate IDs, and blank stylesheet fallback.

v1.8.138 closes the first conservative `UnusedResources` review by deleting
obsolete launcher/branding resources and legacy color tokens after checking
manifest, code, asset, test, and dynamic lookup references. Lint now reports
245 warnings / 1 hint, down from 289 warnings / 1 hint; remaining unused
resource warnings are string, theme-palette, or spec-dimension buckets that
need semantic review before deletion.

v1.8.139 closes the dependency-version lint warning review as its own
dependency slice. Gradle moves from 9.4.1 to checksum-pinned 9.5.1, Navigation
Compose moves from 2.9.7 to 2.9.8, and JUnit Vintage moves from 5.13.1 to
cataloged 6.0.3 for the remaining JUnit-4-style Robolectric test bridge. Lint
now reports 241 warnings / 1 hint.

v1.8.140 closes the candidate auto-commit policy extraction item from
archived improvement-plan Workstream 3. `CandidateAutoCommitPolicy` owns
deterministic ordering and rejection decisions for user-dictionary shortcuts,
phrase repairs, active-strip autocorrects, immediate fallbacks, quick-prediction
spacebar insertion, and plain-space prediction suppression. `NlpManager` stays
responsible for Android-bound state collection and provider lookups, while
`CandidateAutoCommitPolicyTest` pins the pure JVM contract.

v1.8.141 closes the punctuation-triggered commit-rule extraction item from
archived improvement-plan Workstream 3. `KeyboardAutoCommitFlushPolicy` now owns
whether software text commits flush a pending autocorrect candidate first:
media-mode text commits flush, character-mode non-letter commits flush, and
numeric / phone layout punctuation does not flush. `KeyboardManager` now only
executes the selected behavior.

v1.8.142 closes the theme rule editing extraction item from
archived improvement-plan Workstream 3. `ThemeRuleEditPolicy` now owns add-rule
selection validation, selector toggling, and key-code attribute parsing /
replacement decisions. `EditRuleDialog` remains responsible for dialog state
and user feedback, while focused JVM coverage pins invalid, duplicate,
unchanged, add, and replace decisions.

v1.8.143 closes archived improvement-plan Workstream 4. The autocorrect lifecycle
contract now lives in `docs/AUTOCORRECT_LIFECYCLE.md`, covering spacebar,
punctuation, hardware, delete, glide-delete, provider-notification, manual QA,
and regression coverage. `CandidateCommitSideEffectPolicy` pins provider
acceptance notifications and personal-dictionary learning to successful editor
commits, and `KeyboardManager.commitCandidate(...)` now follows that contract.

v1.8.144 starts the Workstream 5 trust-state audit by hardening the backup
flow. `BackupFlowNotice` defines progress, cancellation, failure, share-sheet
handoff, success, and sensitive-clipboard warning states, and the backup screen
now renders explicit cards for the states that remain visible on-screen.

v1.8.145 continues Workstream 5 by hardening the restore flow. `RestoreFlowNotice`
defines archive loading, restore progress, cancellation, failure, partial
failure, success, and erase-mode recovery-copy guidance states. Restore execution
now records section-level summaries, confirms destructive erase restores, keeps
duplicate restore/cancel actions disabled while busy, and avoids erasing local
data for selected archive sections that are missing from the backup.

v1.8.146 hardens the language pack trust states. Extension import now reports
file-reading, importing, cancellation, failure, and action-count summaries for
new installs, updates, and skipped files. Language pack management now reports
delete progress, success, and failure states and blocks duplicate import/delete
actions while deletion is running.

v1.8.147 hardens the theme extension trust states. Theme extension editing now
reports save progress and save failures, blocks duplicate save/cancel/component
actions while saving, confirms theme-component removal, and keeps a visible
"removed from draft" state until the user saves or exits. Installed extension
deletion now reports progress/failure states and blocks duplicate delete/export
actions while removal is running.

v1.8.148 hardens extension archive file trust states. The generic extension
archive file manager now reports import/rename/delete progress and terminal
success/failure states, moves selected-file copy/rename/delete work off the main
thread, and blocks duplicate file actions while archive file work is running.

v1.8.149 hardens manual dictionary entry trust states. User-dictionary
add/update/delete actions now report progress and terminal success/failure
states, run DAO writes off the main thread, refresh affected suggestion overlays
after successful mutations, and block duplicate entry actions while a mutation is
running.

v1.8.150 closes the first broad Workstream 5 recovery-copy pass. Backup,
restore, extension import/edit/delete, archive-file management, language-pack
delete, and manual dictionary entry failure cards now state what stayed
unchanged and include a retry/recovery path beside the technical detail.

v1.8.151 closes the broad Workstream 5 busy-state pass by covering the remaining
dictionary transfer gap. User dictionary import/export now has explicit transfer
state, visible import/export progress cards, off-main-thread transfer work, and
disabled navigation/menu/entry actions while a transfer is running.

v1.8.152 begins the Accessibility Pass with settings focus order. The shared
settings scaffold now pins traversal to app bar, content, persistent bottom
actions, and floating action order, and `FlorisScreenFocusOrderTest` keeps the
contract from drifting.

v1.8.153 continues the Accessibility Pass with candidate row and smartbar
TalkBack labels. `SmartbarAccessibilityLabels` centralizes prediction-strip and
quick-action labels, candidates now announce suggestion type, position, and
text, clipboard/autocorrect suggestions are identified explicitly, and focused
JVM coverage pins label fallback behavior.

v1.8.154 closes the keyboard key semantics and touch-target audit item. Text-key
semantics now use the real `touchBounds` hitbox instead of the smaller visual
key surface, expose an accessibility click action through the normal key event
dispatcher, and label common clipboard, voice, mode, layout, and smartbar
control keys explicitly with JVM coverage.

v1.8.155 closes the settings/dialog dynamic-font scaling audit item.
`DynamicFontScale` centralizes the high-font-scale expansion threshold, compact
extension metadata rows and hyperlinks gain extra wrapping room, and the
theme-rule key preview grows from 36 dp to 48 dp with label wrapping at high
font scale.

v1.8.156 closes the theme-contrast audit item. `ThemeContrastTest` now checks
bundled keyboard, candidate-row, and clipboard-dialog Snygg selectors across
every bundled stylesheet, plus settings warning/error/dialog Material color
schemes across every predefined accent. Low-contrast enter-key theme variants
were fixed and shared card secondary text now uses stronger opacity.

v1.8.157 closes the non-color state-indicator audit item. Shared success,
progress, and neutral card wrappers now carry distinct icons, high-frequency
import/export/delete/backup/restore/readiness notices use the state-specific
wrappers, and extension import file rows add ready/skipped icons beside the
status text.

v1.8.158 closes the accessibility manual-QA documentation item.
`CONTRIBUTING.md` and `docs/ACCESSIBILITY.md` now list TalkBack traversal,
keyboard label, candidate-row announcement, high-font-scale, non-color-state,
and theme/layout checks for changes that touch settings or keyboard surfaces.

v1.8.159 starts Performance Instrumentation Workstream 7. `:benchmark` is
active again on AGP 9, the benchmark variant has a local-only input activity,
and `tools/benchmark-ime-first-render.ps1` records adb-driven cold IME
first-render baselines. The first committed SM-S938B / Android 16 run records
five iterations with `am start -W` medians of `TotalTime` 31.0 ms and
`WaitTime` 34.0 ms, plus benchmark-only `swiftfloris.ime.firstRenderMs`
median 18.335469 ms.

v1.8.160 adds the first suggestion latency baseline. A benchmark-only
`BenchmarkSuggestionActivity` invokes `LatinLanguageProvider.suggest` against
a real `EditorContent` snapshot for `teh`, logs
`swiftfloris.nlp.firstSuggestionMs`, and `tools/benchmark-ime-suggestion-latency.ps1`
records repeatable adb JSON. The first SM-S938B / Android 16 cold
provider-direct run records median 1878.616249 ms with eight candidates.

v1.8.161 adds the dictionary load/preload baseline. `BenchmarkDictionaryActivity`
preloads `Subtype.DEFAULT`, probes invalid token `zzzxqq` to force lazy
SymSpell d1/d2 index construction, and `tools/benchmark-ime-dictionary-load.ps1`
records repeatable adb JSON. The first SM-S938B / Android 16 run records median
`swiftfloris.dict.loadMs` 757.353333 ms, median `swiftfloris.dict.preloadMs`
772.080625 ms, median SymSpell d1 build 500.230156 ms over 94,934 correction
words, median SymSpell d2 build 532.298281 ms over 10,534 correction words, and
median post-preload spell path 1030.179896 ms.

v1.8.162 adds the candidate-row recomposition baseline. `CandidatesRow` now
emits benchmark-build-only `swiftfloris.smartbar.candidates.recomposeMs` log
markers, and `tools/benchmark-ime-candidate-row.ps1` focuses the benchmark
input field, clears startup log noise, types `hello world this is a test`, and
restores the previous IME. The first SM-S938B / Android 16 run records median
nine recompositions per run, median recomposition body 0.326563 ms, median max
0.770365 ms, median total 4.069529 ms, and paired median
`swiftfloris.nlp.suggestMs` 0.339896 ms.

v1.8.163 adds the theme-switch baseline. `ThemeManager` now emits
benchmark-build-only direct `swiftfloris.theme.switchMs` markers, and
`tools/benchmark-ime-theme-switch.ps1` opens a focused benchmark input field,
switches across SwiftKey Pure Light, M3E Nord Dark, and M3E SwiftKey Pure Dark
while the IME is visible, then restores the previous IME. The first SM-S938B /
Android 16 run records median five direct switches per run, median switch body
18.541197 ms, median max 19.587708 ms, median total 57.505571 ms, median
cold-step 19.221354 ms, median warm cached-step 0.2808075 ms, and zero load
failures.

v1.8.164 adds the backup/restore duration baseline. `BenchmarkBackupRestoreActivity`
seeds a representative default backup profile inside the isolated benchmark app
data (preferences plus keyboard/theme extension files), measures archive
creation, then measures unzip/validation plus merge restore. The first SM-S938B
/ Android 16 run records median backup create 12.653698 ms, median archive size
22,034 bytes, median restore prepare 4.062604 ms, median restore apply
5.727604 ms, median restore total 9.874167 ms, and 3/3 selected sections
restored with zero missing or failed sections.

v1.8.165 closes archived improvement-plan Workstream 8. Android CI already ran
unit tests and `assembleDebug` on pull requests; this slice fixes the lint
configuration so `app/lint.xml` is treated as lint config rather than a stale
baseline, routes CI lint through `scripts/run-lint-debug-with-baseline-check.sh`,
adds weekly Dependabot version-review coverage for Gradle and GitHub Actions,
adds the manual `Android Emulator Smoke` settings-launch workflow, and documents
the maintainer/local verification commands in `docs/LOCAL_VERIFICATION.md`.

v1.8.166 closes archived improvement-plan Workstream 9. `docs/REPO_HYGIENE.md`
records the intentional legacy root-markdown deletions, commit-scope rules,
generated-output rules, and final-handoff verification rule. CI now runs
`scripts/check-repo-hygiene.sh` after the root crash-log guard so tracked build
or report outputs fail before slower Gradle work.

v1.8.167 continues archived improvement-plan Workstream 10. Extension draft archive
file deletion, theme editor rule deletion, and theme property deletion now show
draft-aware confirmation copy before mutation and clarify that the installed
extension or theme is unchanged until the draft is saved.

v1.8.168 continues archived improvement-plan Workstream 10. Addons Settings now
uses the shared progress-card treatment while installed addon APKs are rescanned
and dictionary-pack metadata is refreshed. The touched signing-pin preference
observation also moved from deprecated `observeAsState` to `collectAsState`.

v1.8.169 continues archived improvement-plan Workstream 10. Selected
user-dictionary languages, extension categories, language-pack management,
filtered clipboard history, and the theme manager now show specific empty-state
copy with add/import/filter-clear or recovery guidance instead of leaving blank
screens or generic labels.

v1.8.170 continues archived improvement-plan Workstream 10. The shared settings
keyboard preview field now renders as a distinct bottom surface, exposes
ready/active focus-state feedback, preserves bottom-bar traversal ordering, and
uses coroutine-safe feedback when Android cannot open the keyboard picker.

v1.8.171 through v1.8.227 refresh the release front door after the 2026-05-31
SwiftKey account-export cutoff, consolidate planning into `ROADMAP.md`,
backfill audit docs, ship settings search and search polish, add hardware
keyboard import and per-app language/accent discovery, harden release gates
with reproducible-build / Roborazzi / benchmark checks, refresh compatible
dependency pins, surface staged startup crashes, harden restore/crash
diagnostics, refresh this root onboarding set to the current
roadmap/completed/changelog source-of-truth contract, add a real-resource /
typed-route drift guard for settings search, give zero-result searches a
one-tap path back to Settings Home, pin high-traffic search synonyms for
theme, haptic feedback, trace gestures, punctuation spacing, and privacy audit,
and reset populated search results to the top when the query changes.
v1.8.226 restores the release ledger after the pushed post-v1.8.225 fix
commits, covering n-gram/data-loss hardening, thread-safety cleanup,
SealedBoxCrypto KDF/scrubbing, private-session trace suppression, Arabic
combining-mark shaping, Snygg selector/contentScale recovery, and the
clipboard missing-media fallback compile repair.
v1.8.227 closes the Japanese locale capability-gate typo by treating `ja` as
the no-capitalization and no-auto-space language in `FlorisLocale`, with focused
JVM coverage for the adjacent script-sensitive language table.

## 4. Module layout

```
:app                    — IME + Settings UI + addon facades
lib/android             — Android utility extensions
lib/color               — color math
lib/compose             — Compose helpers
lib/kotlin              — pure-Kotlin utilities
lib/snygg               — Snygg theme engine
:benchmark              — Macrobenchmark + adb benchmark harness (active in settings)
```

Native runtimes for optional capabilities (LiteRT-LM, Bergamot, librime, ML
Kit Digital Ink, Vosk) ship as out-of-tree signed addon APKs via the addon
enrolment contract, not as a `:lib:native` module in the base APK. The
prior `:lib:native` placeholder and `libnative/` Rust scaffold were dropped
in v1.8.185 (`docs/archive/research/RESEARCH_FEATURE_PLAN_2026-05-25.md` EI11) since no addon was ever going
to consume them and they confused contributors about whether native code
was permitted in `:app`.

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
- **Tag cadence recovered** — release tags `v1.8.41` through
  `v1.8.69` were backfilled on 2026-05-17 from their matching
  `gradle.properties` version-bump commits, and `v1.8.70` through `v1.8.84`
  are tagged with their release commits. Current maintainer hosts can push
  commits and tags directly to `SysAdminDoc/SwiftFloris`.

## 6. Roadmap structure (where to put what)

| If your change is… | Lives in… |
|---|---|
| One feature slice, one release | A new `## vX.Y.Z` section appended to [CHANGELOG.md](CHANGELOG.md) + a `gradle.properties` bump + matching fastlane changelog |
| A SwiftKey-parity slice (Phase A/B/C/D/E) | The "Phased plan" in [docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17.md](docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17.md) (snapshot; migrate active items into `ROADMAP.md`) |
| A roadmap or active work change | [ROADMAP.md](ROADMAP.md) |
| A shipped-state summary | [COMPLETED.md](COMPLETED.md) |
| A quality / a11y / perf / test / CI / release-hygiene workstream | [ROADMAP.md](ROADMAP.md); historical context is archived at [docs/archive/IMPROVEMENT_PLAN_2026-05-18.md](docs/archive/IMPROVEMENT_PLAN_2026-05-18.md) |
| A research finding that updates a prior roadmap claim | [docs/archive/ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md](docs/archive/ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md) (snapshot; fold corrections into `ROADMAP.md`) |
| A security / dependency / crypto migration | [docs/SECURITY.md](docs/SECURITY.md) + [docs/THREAT_MODEL.md](docs/THREAT_MODEL.md) + [docs/REPRODUCIBLE_BUILDS.md](docs/REPRODUCIBLE_BUILDS.md) |
| Architecture or contributor workflow | [ARCHITECTURE.md](ARCHITECTURE.md) + [CONTRIBUTING.md](CONTRIBUTING.md) |
| A UX / migration walkthrough | `docs/MIGRATE_FROM_SWIFTKEY.md`, `docs/INLINE_AUTOFILL.md`, `docs/TASKER_INTEGRATION.md` |
| An external-research prompt for a fresh AI session | [docs/AI_PROMPTS_EXTERNAL_WORK.md](docs/AI_PROMPTS_EXTERNAL_WORK.md) |
| Addon spec (theme / dict / language / layout / popup-mapping pack) | [docs/addons/](docs/addons/) |

`ROADMAP.md` is the active open-work source of truth and intentionally retains
historical context. `COMPLETED.md` summarizes shipped roadmap state, and
`CHANGELOG.md` is the only release-note stream. The parity roadmap and
improvement plan are archived snapshots; do not route new work there.

## 7. Current sprint anchor

**SwiftKey account retirement: 2026-05-31** — Microsoft is deleting
standalone SwiftKey accounts on that date. Non-MS-account data is gone
after the cutoff. The migration funnel is the highest-priority external
clock the project has.

Phase A of `docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17.md` is sized for this:

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
3. New `## vX.Y.Z` section appended to root `CHANGELOG.md`.
4. `gradle.properties` versionCode + versionName bumped.
5. New dep / asset → `NOTICE` / `LICENSES/` updated.
6. APK signed and installable; SHA-256 published in release notes.
7. CI passes: `verifyNoInternetPermission`, OSV scan, 16 KB alignment check.
8. (Workstream coverage) Tests added for new behavior; visual via Roborazzi
   if UI-touching; macro via Benchmark if perf-sensitive.
9. Roborazzi baselines stay committed under `app/src/test/snapshots/`; do not
   soften the visual-regression CI gate to absorb drift.

## 11. Memory rules captured by prior research

- **Consolidated changelog pattern:** every release appends a new
  `## v<MAJOR>.<MINOR>.<PATCH>` section to root `CHANGELOG.md`, preceded
  by an `<a id="vX.Y.Z"></a>` anchor. The release workflow extracts the
  section between anchors for the GitHub release body. The legacy
  per-file `RELEASE_NOTES_v*.md` pattern was retired 2026-05-18.
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
- (At repo root) [docs/archive/ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md](docs/archive/ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md) — the actionable additions

Future research runs should follow the same `.ai/research/<YYYY-MM-DD>/`
convention so prior runs remain auditable.

## 13. Quick navigation

| You want… | Read… |
|---|---|
| Project pitch + setup | [README.md](README.md) |
| The big roadmap with full history | [ROADMAP.md](ROADMAP.md) |
| The current sprint plan | [docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17.md](docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17.md) |
| The latest research run's recommendations | [docs/archive/ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md](docs/archive/ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md) (snapshot) |
| Quality / a11y / perf / test plan | [ROADMAP.md](ROADMAP.md); archived context in [docs/archive/IMPROVEMENT_PLAN_2026-05-18.md](docs/archive/IMPROVEMENT_PLAN_2026-05-18.md) |
| Threat model + security posture | [docs/THREAT_MODEL.md](docs/THREAT_MODEL.md) + [docs/SECURITY.md](docs/SECURITY.md) |
| What ships in the next release | Top section of [CHANGELOG.md](CHANGELOG.md) |
| What I'm allowed to put in `:app` | §2 above + [ROADMAP.md](ROADMAP.md) §1, §10 |
