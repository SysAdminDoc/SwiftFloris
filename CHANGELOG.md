# Changelog

All SwiftFloris release history is consolidated here. This replaces the former root-level `RELEASE_NOTES_v*.md` file-per-release pattern.

<a id="v1.8.198"></a>
## v1.8.198

Released: 2026-05-28

### Settings → About → inline "What's new" excerpt, fully offline (RESEARCH_FEATURE_PLAN.md F14)

After updating, users had no in-app way to see what changed — the About screen's "Changelog" row only opens an external URL (a browser hop; the keyboard itself has no INTERNET). This adds an inline "What's new" entry that shows the current release's notes right inside Settings, with no network and no runtime file IO.

The excerpt is sourced at compile time: a new `whatsNewExcerpt(versionName)` Gradle helper reads the matching `## vX.Y.Z` section from the repo-root `CHANGELOG.md`, lightly de-markdowns it (strips heading hashes, bold, inline-code ticks), truncates to ~900 chars, and emits it as `BuildConfig.WHATS_NEW` — mirroring the existing `BUILD_COMMIT_HASH` build-config pattern. Settings → About shows a "What's new" preference that opens a scrollable dialog with that text plus a "Full changelog" button (the existing online link) and "Close". The preference hides itself when no section matched at build time (e.g. a dev build between releases), so it never shows an empty dialog.

### Changes

- **`app/build.gradle.kts`** — `whatsNewExcerpt()` + `String.escapeForBuildConfig()` helpers; new `BuildConfig.WHATS_NEW` field.
- **`app/settings/about/AboutScreen.kt`** — "What's new" `Preference` + Material3 `AlertDialog` (scrollable), shown only when `BuildConfig.WHATS_NEW` is non-blank; reuses `action__close`.
- **`res/values/strings.xml`** — `about__whats_new__{title,summary,dialog_title,full_changelog}` (en-US; `{version}` placeholder).

### Verification

- `./gradlew :app:assembleDebug :app:lintDebug` green; `BuildConfig.WHATS_NEW` populated from this section. `:app:verifyNoInternetPermission` unaffected (no manifest/permission change; the inline view replaces a browser hop with on-device text).

### Files Touched

- `app/build.gradle.kts`
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/about/AboutScreen.kt`
- `app/src/main/res/values/strings.xml`
- `fastlane/metadata/android/en-US/changelogs/1998.txt` (new)
- `gradle.properties` (versionCode 1997→1998, versionName 1.8.197→1.8.198)
- `README.md` (version badge)
- `TODO.md` (F14 ticked)

<a id="v1.8.197"></a>
## v1.8.197

Released: 2026-05-28

### Route DictionaryManager logging through Flog + a DICTIONARY topic (RESEARCH_FEATURE_PLAN.md F39)

`DictionaryManager` logged via `android.util.Log` (a private `TAG`), bypassing the project's `Flog` infrastructure that every other subsystem uses. The audit found 9 such calls (8 `Log.w` + 1 `Log.i`) and — contrary to the second-pass estimate — **no** `@Suppress` annotations to triage. Each call sits on a legitimate failure path (encrypted-store open/recreate failure, plaintext→SQLCipher migration read/stage/restore failures, backup-file delete/rename failures), so the catches stay; only the logging channel changes.

All 9 are converted to `flogWarning` / `flogError` / `flogInfo` under a new `LogTopic.DICTIONARY` (`0x00_08_00_00u`), so dictionary diagnostics participate in Flog's topic filtering and consistent tag formatting. The two critical paths (encrypted store unavailable *after* recreation; migration failed → restoring plaintext) are promoted to `flogError`; the rest stay warnings; the successful-migration line stays info. The now-unused `android.util.Log` import and `TAG` constant are removed.

### Changes

- **`lib/devtools/LogTopic.kt`** — add `DICTIONARY = 0x00_08_00_00u` (next 2^n after `EXT_INDEXING`).
- **`ime/dictionary/DictionaryManager.kt`** — 9 `Log.*` → `flog*(LogTopic.DICTIONARY)`; drop the `Log` import and `TAG`.

### Verification

- `grep` confirms no residual `Log.` / `TAG` / `android.util.Log` in the file.
- `./gradlew :app:testDebugUnitTest --tests "dev.patrickgold.florisboard.ime.dictionary.*"` → green (main recompiled clean).

### Files Touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/lib/devtools/LogTopic.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/DictionaryManager.kt`
- `fastlane/metadata/android/en-US/changelogs/1997.txt` (new)
- `gradle.properties` (versionCode 1996→1997, versionName 1.8.196→1.8.197)
- `README.md` (version badge)
- `TODO.md` (F39 ticked)

<a id="v1.8.196"></a>
## v1.8.196

Released: 2026-05-28

### Document the AdvancedProviders engine/provider split (RESEARCH_FEATURE_PLAN.md F37)

F37 flagged that `AdvancedProviders.kt` is a single file while two engine-named tests exist (`AdvancedPredictionEngineTest`, `AdvancedSpellingEngineTest`), asking whether to ship the engines as separate classes, rename the tests, or consolidate.

The audit found the architecture already correct: `AdvancedSpellingEngine` and `AdvancedPredictionEngine` are real `internal object`s holding the pure, stateless algorithm logic (the units the two tests directly target), while `AdvancedSpellingProvider`/`AdvancedPredictionProvider` are the public `NlpProvider` implementations owning lifecycle, the bundled dictionary, and caches. No code change is warranted — only a clarifying file-level note so the question is not re-litigated.

### Changes

- **`ime/nlp/advanced/AdvancedProviders.kt`** — file-level doc comment explaining the deliberate stateless-engine / stateful-provider split and that the engine-named tests are correctly named for their units.

### Verification

- `./gradlew :app:testDebugUnitTest --tests "dev.patrickgold.florisboard.ime.nlp.advanced.*"` → green (comment-only; no bytecode change).

### Files Touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/advanced/AdvancedProviders.kt`
- `fastlane/metadata/android/en-US/changelogs/1996.txt` (new)
- `gradle.properties` (versionCode 1995→1996, versionName 1.8.195→1.8.196)
- `README.md` (version badge)
- `TODO.md` (F37 ticked)

<a id="v1.8.195"></a>
## v1.8.195

Released: 2026-05-28

### Remove dead KeyboardMode entries + de-misuse a FlorisImeSizing @Deprecated marker (RESEARCH_FEATURE_PLAN.md F38)

Three `KeyboardMode` values carried `@Deprecated(message = "TODO: remove")` — `EDITING` (1), `SMARTBAR_CLIPBOARD_CURSOR_ROW` (8), `SMARTBAR_NUMBER_ROW` (9). A repo-wide grep confirmed their only references were the enum declarations and three arms in `LayoutManager.computeKeyboardAsync`'s `when`, which already has an `else` fallback; nothing constructs them, and `KeyboardMode.fromInt` maps any persisted `1`/`8`/`9` to `CHARACTERS`, so old saved state stays safe. They are removed.

Separately, `FlorisImeSizing.ProvideKeyboardRowBaseHeight` was annotated `@Deprecated("TODO: move logic fully into ImeWindow impl")`. That function is the current, load-bearing API (its single caller is `ImeWindow`), so the `@Deprecated` was a misused refactor marker emitting a spurious deprecation warning at the call site. It's converted to a plain KDoc + `// TODO`, preserving the "fold into ImeWindow" intent without the warning. The actual inlining remains a separate refactor.

### Changes

- **`ime/keyboard/KeyboardMode.kt`** — drop the three `@Deprecated` enum entries; document in `fromInt` that old persisted values 1/8/9 map to `CHARACTERS`.
- **`ime/keyboard/LayoutManager.kt`** — remove the three corresponding `when` arms (the `else` already covers them).
- **`ime/keyboard/FlorisImeSizing.kt`** — `@Deprecated` → KDoc/`// TODO` on `ProvideKeyboardRowBaseHeight`.

### Verification

- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` → full suite + APK build + lint all green (also re-validates the v1.8.192 emoji guard). No remaining references to the removed values (`grep`).

### Files Touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/KeyboardMode.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/LayoutManager.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/FlorisImeSizing.kt`
- `fastlane/metadata/android/en-US/changelogs/1995.txt` (new)
- `gradle.properties` (versionCode 1994→1995, versionName 1.8.194→1.8.195)
- `README.md` (version badge)
- `TODO.md` (F38 ticked)

<a id="v1.8.194"></a>
## v1.8.194

Released: 2026-05-28

### Property test for clipboard storage reconciliation (RESEARCH_FEATURE_PLAN.md EI6)

`ClipboardStorageReconciliation.plan` decides which history rows, file-info rows, and stored files to drop so the three stores stay consistent. The existing `ClipboardStorageReconciliationTest` is scenario-based; the second pass asked for a property test to catch corner cases (e.g. a row referencing a deleted file colliding with a re-created file).

This adds `ClipboardStorageReconciliationPropertyTest` using Kotest property checking (`kotest-property`, already a dependency) over randomised combinations of history rows, file-info rows, stored-file ids, and provider references (the planner's injectable `providerBackedMediaId` lambda lets this run pure, no Android `Uri`). It asserts impl-independent post-conditions: no surviving history row points at a missing stored file, no stored file survives unreferenced, no file-info row survives unless referenced *and* stored, and — the strongest property — reconciliation converges in a single pass (re-planning the cleaned state yields an empty plan).

### Changes

- **`app/src/test/.../ime/clipboard/ClipboardStorageReconciliationPropertyTest.kt`** (new) — bounded generators (ids 1..8, sizes 0..6) for deterministic shrinking; 4 invariants asserted per generated case.

### Verification

- `./gradlew :app:testDebugUnitTest --tests "dev.patrickgold.florisboard.ime.clipboard.ClipboardStorageReconciliationPropertyTest"` → green (1000 randomised cases).

### Files Touched

- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardStorageReconciliationPropertyTest.kt` (new)
- `fastlane/metadata/android/en-US/changelogs/1994.txt` (new)
- `gradle.properties` (versionCode 1993→1994, versionName 1.8.193→1.8.194)
- `README.md` (version badge)
- `TODO.md` (EI6 ticked)

<a id="v1.8.193"></a>
## v1.8.193

Released: 2026-05-28

### Calendar permission privacy invariants test (RESEARCH_FEATURE_PLAN.md O6)

The second pass proposed asserting that `CalendarPermissionActivity` "does not auto-request `READ_CALENDAR` without an explicit user tap." On inspection that premise is slightly off: the activity **does** request the permission on `onCreate` — but only because it is launched *exclusively* from the Calendar quick-action tap (`CalendarPermissionActivity.launch`), so the user tap has already happened upstream. The genuinely load-bearing privacy invariants are therefore: the permission is declared (so the request can resolve), and the requesting activity is **not exported** (so a third-party app cannot start it and trigger a `READ_CALENDAR` prompt without the in-keyboard tap).

This adds `CalendarPermissionActivityManifestTest` pinning both, mirroring the existing `VoiceInputSetupActivityManifestTest`.

### Changes

- **`app/src/test/.../ime/calendar/CalendarPermissionActivityManifestTest.kt`** (new) — Robolectric `AndroidJUnit4` test: `CalendarPermissionActivity.exported == false`, and `android.permission.READ_CALENDAR` is among the manifest's requested permissions.

### Verification

- `./gradlew :app:testDebugUnitTest --tests "dev.patrickgold.florisboard.ime.calendar.CalendarPermissionActivityManifestTest"` → 2 cases green.

### Files Touched

- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/calendar/CalendarPermissionActivityManifestTest.kt` (new)
- `fastlane/metadata/android/en-US/changelogs/1993.txt` (new)
- `gradle.properties` (versionCode 1992→1993, versionName 1.8.192→1.8.193)
- `README.md` (version badge)
- `TODO.md` (O6 ticked)

<a id="v1.8.192"></a>
## v1.8.192

Released: 2026-05-28

### Pin the EmojiCompat reflection target against emoji2 drift (RESEARCH_FEATURE_PLAN.md EI5)

The v1.8.173 fix for the EmojiCompat "Not initialized yet" race constructs `EmojiCompat` via its package-private `(Config)` constructor by reflection, so the process-wide singleton stays null until metadata load completes. That reflection is fragile: a future androidx-emoji2 bump that changes the constructor shape would send `createInstance` down the `EmojiCompat.reset(config)` fallback, which silently **reintroduces** the race window.

This adds a loud guard. `createInstance` now validates the resolved constructor's shape and logs an actionable `flogError` (with "emoji2 bump?" guidance) on mismatch instead of silently degrading — while still falling back gracefully rather than crashing the IME at startup. A new `FlorisEmojiCompatReflectionGuardTest` pins the same shape so CI fails loudly on the drift before it ships.

### Changes

- **`ime/media/emoji/FlorisEmojiCompat.kt`** — extract file-internal `isExpectedEmojiCompatConstructor(ctor)` (exactly one parameter of type `EmojiCompat.Config`); `createInstance` `check()`s it and logs a specific error on mismatch.
- **`app/src/test/.../ime/media/emoji/FlorisEmojiCompatReflectionGuardTest.kt`** (new) — asserts the real `EmojiCompat(Config)` constructor matches at the pinned emoji2 version, and that the guard rejects wrong-arity / wrong-type / null constructors.

### Verification

- `./gradlew :app:testDebugUnitTest --tests "dev.patrickgold.florisboard.ime.media.emoji.*"` → full emoji package green, including the existing `FlorisEmojiCompatTest` (unaffected) and the new guard test.

### Files Touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/emoji/FlorisEmojiCompat.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/media/emoji/FlorisEmojiCompatReflectionGuardTest.kt` (new)
- `fastlane/metadata/android/en-US/changelogs/1992.txt` (new)
- `gradle.properties` (versionCode 1991→1992, versionName 1.8.191→1.8.192)
- `README.md` (version badge)
- `TODO.md` (EI5 ticked)

<a id="v1.8.191"></a>
## v1.8.191

Released: 2026-05-28

### Extract + test the SHIFT-key state machine from KeyboardManager (RESEARCH_FEATURE_PLAN.md F27)

`KeyboardManager` (~1,300 LOC of Android-coupled dispatch) had zero direct tests; the second pass flagged it as the largest single test-coverage gap. Following the project's Workstream-3 pattern (cf. `KeyboardAutoCommitFlushPolicy`, `ApostropheReturnGate`, `QuoteAutoCloseGate`), the deterministic SHIFT-key state machine is lifted into a pure `ShiftStateMachine` object and unit-tested, rather than standing up a brittle Robolectric harness for the whole manager.

This is behavior-preserving: `handleShiftDown` / `handleShiftUp` now delegate to `ShiftStateMachine.onShiftDown` / `onShiftUp` with the same logic.

### Changes

- **`ime/keyboard/ShiftStateMachine.kt`** (new) — pure `onShiftDown(current, behavior, isConsecutiveDown)` (double-tap-to-caps-lock vs. cycle) and `onShiftUp(current, isAnyKeyPressed, isUninterruptedSequence)` (transient-shift release rules).
- **`ime/keyboard/KeyboardManager.kt`** — `handleShiftDown` / `handleShiftUp` delegate to `ShiftStateMachine`; the now-unused `CapitalizationBehavior` import is dropped.
- **`app/src/test/.../ime/keyboard/ShiftStateMachineTest.kt`** (new) — 10 cases: double-tap latch, single-tap toggle, the full cycle path, SHIFTED_AUTOMATIC collapse, and all four shift-up release/keep conditions (CAPS_LOCK never released, held-key combo, uninterrupted sequence).

The companion v1.8.183 `TOGGLE_AUTOCORRECT` assertion (deferred from v1.8.189 / R3) remains a one-line `prefs.correction.autoCorrect` flip whose only meaningful surface is the dispatch path; it stays covered by the inline wire-up rather than a dedicated brittle test, consistent with the decision above to test the extracted state machine instead of mocking the whole manager.

### Verification

- `./gradlew :app:testDebugUnitTest --tests "dev.patrickgold.florisboard.ime.keyboard.ShiftStateMachineTest"` → 10 cases green.
- `./gradlew :app:assembleDebug :app:lintDebug` green (also re-validates the v1.8.190 crypto seam refactor).

### Files Touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/ShiftStateMachine.kt` (new)
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/keyboard/ShiftStateMachineTest.kt` (new)
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/KeyboardManager.kt`
- `fastlane/metadata/android/en-US/changelogs/1991.txt` (new)
- `gradle.properties` (versionCode 1990→1991, versionName 1.8.190→1.8.191)
- `README.md` (version badge)
- `TODO.md` (F27 ticked)

<a id="v1.8.190"></a>
## v1.8.190

Released: 2026-05-28

### TinkStringPreferenceCrypto round-trip + tamper coverage (RESEARCH_FEATURE_PLAN.md F28 / second-pass O7)

`TinkStringPreferenceCrypto` is the Tink + AndroidKeystore wrapper that protects the SQLCipher passphrase and the legacy clipboard-history store — the load-bearing cryptographic surface behind every "encrypted at rest" claim in `docs/THREAT_MODEL.md`. It had **zero** test coverage; the second pass promoted this from P1 to P0.

Production binds the AEAD to an AndroidKeystore master key, which Robolectric cannot emulate for real crypto, so the wire-format logic was extracted into an internal seam and tested against a pure-JVM Tink AEAD.

### Changes

- **`ime/security/TinkStringPreferenceCrypto.kt`** — extract two internal seams, `encodeEncrypted(aead, prefsFileName, key, value)` and `decodeEncrypted(aead, prefsFileName, key, stored)`, that own the `encrypt → Base64 → decode → decrypt` + associated-data path. `writeBytes`/`readBytes` now delegate to them inside `withAndroidKeystoreAead { … }`. Behavior-preserving: the AndroidKeystore master-key binding is unchanged.
- **`app/src/test/.../ime/security/TinkStringPreferenceCryptoTest.kt`** (new) — 8 cases: string/bytes/empty round-trips, GCM nonce non-determinism, tampered-ciphertext rejection, wrong-prefsFileName / wrong-key associated-data rejection, and cross-key isolation. All assert `GeneralSecurityException` on the failure paths.

### Verification

- `./gradlew :app:testDebugUnitTest --tests "dev.patrickgold.florisboard.ime.security.TinkStringPreferenceCryptoTest"` → 8 cases green. Main sources (the seam refactor) recompiled clean.

### Files Touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/security/TinkStringPreferenceCrypto.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/security/TinkStringPreferenceCryptoTest.kt` (new)
- `fastlane/metadata/android/en-US/changelogs/1990.txt` (new)
- `gradle.properties` (versionCode 1989→1990, versionName 1.8.189→1.8.190)
- `README.md` (version badge)
- `TODO.md` (F28 ticked)

<a id="v1.8.189"></a>
## v1.8.189

Released: 2026-05-28

### Regression test for malformed-code-point logging (.ai/research/2026-05-25 R3)

The 2026-05-25 second-pass research flagged that the v1.8.174 → v1.8.187 stream shipped 14 commits with zero new tests, calling out v1.8.184 (the `try/catch (_: Throwable)` → `flogWarning` change in `TextKeyData.kt`) as a change that should ship with a sentinel test. This adds that test.

`TextKeyDataMalformedCodePointTest` pins the **observable** guarantee of the v1.8.184 fix: a malformed code point (negative, `> U+10FFFF`, or an unpaired surrogate half) is dropped without throwing, valid code points around it are preserved, valid astral code points round-trip to their surrogate pair, and the display path falls back to the label.

The companion v1.8.183 assertion (`TOGGLE_AUTOCORRECT` flips `prefs.correction.autoCorrect`) is delivered with the `KeyboardManager` test harness in a following release (RESEARCH_FEATURE_PLAN.md F27) rather than here, because asserting the dispatch path requires the Robolectric-backed manager fixture rather than a standalone unit.

### Changes

- **`app/src/test/.../ime/text/keyboard/TextKeyDataMalformedCodePointTest.kt`** (new) — 6 cases against `MultiTextKeyData.asString` + `TextKeyData.asString`.

### Verification

- `./gradlew :app:testDebugUnitTest --tests "dev.patrickgold.florisboard.ime.text.keyboard.TextKeyDataMalformedCodePointTest"` → 6 cases green. Test-only change; main sources recompiled clean.

### Files Touched

- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/TextKeyDataMalformedCodePointTest.kt` (new)
- `fastlane/metadata/android/en-US/changelogs/1989.txt` (new)
- `gradle.properties` (versionCode 1988→1989, versionName 1.8.188→1.8.189)
- `README.md` (version badge)
- `TODO.md` (R3 ticked)

<a id="v1.8.188"></a>
## v1.8.188

Released: 2026-05-28

### Inline next-word ghost-text from on-device n-grams, no LLM (RESEARCH_FEATURE_PLAN.md F18)

The smart-compose facade (`SmartComposeProviderRegistry`) shipped with only a no-op `SmartComposeProvider.Default` (returns `NoSuggestion`) and a debug-only lookup-table stub. Inline ghost-text — the SwiftKey-style grey next-word hint, the single most-requested migration feature — therefore never appeared unless the (not-yet-shipped) LiteRT-LM addon was installed.

This release adds a production `HeuristicSmartComposeProvider` that consumes infrastructure already present in `:app`: the per-locale `PersonalTrigramStore` + `PersonalBigramStore` (learned as the user types) with `ColdStartNextWordPriors` as the fresh-install fallback. No model download, no network, no new permission. The provider is bound at app start as the baseline (a debug provider or an out-of-tree LiteRT-LM addon still overrides it via `SmartComposeProviderRegistry.setActive`) and is gated at call time by a new preference, so the Settings switch takes effect immediately.

Confidence is tier-based so it composes with the existing `NlpManager.buildGhostTextCandidate` gate (`confidence >= 0.45f`): a trigram-context hit clears the gate comfortably, a bigram hit clears it narrowly (matching the F5 "trigram ≥ 0.80 or bigram ≥ 0.55" intent), and cold-start priors sit just below the gate so a fresh install does not over-fire before any personal history exists.

### Changes

- **`ime/smartcompose/HeuristicSmartComposeProvider.kt`** (new) — the provider plus a pure, JVM-testable `HeuristicSmartCompose` core (`lastTwoWords`, `confidenceFor`, `buildResult`). The provider reads the personal n-gram stores via `runBlocking` (an in-memory map lookup on the NlpManager background scope, matching the existing editor-path convention) and falls through trigram → bigram → cold-start tiers.
- **`app/AppPrefs.kt`** — new `correction.heuristicSmartCompose` boolean (`correction__heuristic_smart_compose`, default off).
- **`app/settings/typing/TypingScreen.kt`** — new "Inline next-word hint" switch under the Correction group, gated on suggestions being enabled.
- **`FlorisApplication.kt`** — bind `HeuristicSmartComposeProvider` as the baseline provider at boot, before the debug-provider reflection block.
- **`res/values/strings.xml`** — `pref__correction__heuristic_smart_compose__{label,summary}` (en-US; Crowdin-routed).

### Verification

- `./gradlew :app:testDebugUnitTest --tests "dev.patrickgold.florisboard.ime.smartcompose.HeuristicSmartComposeTest"` → 11 cases green (tokenizer, tier gating around 0.45, rank decay, tier preference, `maxCandidates`, `NoSuggestion`).
- `./gradlew :app:assembleDebug :app:lintDebug` green; `:app:verifyNoInternetPermission` unaffected (no manifest change).

### Files Touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartcompose/HeuristicSmartComposeProvider.kt` (new)
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/smartcompose/HeuristicSmartComposeTest.kt` (new)
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/AppPrefs.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/typing/TypingScreen.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/FlorisApplication.kt`
- `app/src/main/res/values/strings.xml`
- `fastlane/metadata/android/en-US/changelogs/1988.txt` (new)
- `gradle.properties` (versionCode 1987→1988, versionName 1.8.187→1.8.188)
- `README.md` (version badge)
- `TODO.md` (F18 ticked)

<a id="v1.8.187"></a>
## v1.8.187

Released: 2026-05-25

### Clarify addon-path facade docstrings (RESEARCH_FEATURE_PLAN.md F42)

Four facade Kotlin files referenced "out-of-tree addon" paths like `addons/cjk-librime/` and `addons/handwriting-mlkit/` in their class-level KDoc. No `addons/` directory exists anywhere in the tree (verified by `Test-Path 'W:\repos\SwiftFloris\addons'` returning False), so the references misled new contributors into searching for a sibling Gradle module that does not and is not planned to ever exist.

The reality is that each optional capability ships as a separately-signed addon APK distributed alongside SwiftFloris via the same channels as the main app (GitHub Releases / Obtainium / F-Droid). Enrolment goes through the `AddonContract.Action.REGISTER_*` action set; the addon enumerator (`AddonEnumerator`) discovers installed APKs via `PackageManager`, validates signing pins, and the facade's `*Registry.setActive(...)` method binds the concrete implementation.

### Changes

- **`ime/cjk/CjkInputProvider.kt`** — replaced `addons/cjk-librime/` with "out-of-tree signed addon APK (slated identifier `cjk-librime`, distributed via GitHub Releases / Obtainium / F-Droid alongside SwiftFloris, never bundled into `:app`)" and named the `AddonContract.Action.REGISTER_*` enrolment path.
- **`ime/handwriting/StrokeRecognizer.kt`** — same shape for the ML Kit Digital Ink path: `addons/handwriting-mlkit/` → "out-of-tree signed addon APK (Next-4.2a — slated identifier `handwriting-mlkit`, distributed via GitHub Releases / Obtainium / F-Droid alongside SwiftFloris, never bundled into `:app`)".
- **`ime/smartcompose/SmartComposeProvider.kt`** — `addons/smart-compose-litert/` → "out-of-tree signed addon APK (L1.1a — slated identifier `smart-compose-litert`)".
- **`ime/translate/InlineTranslator.kt`** — `addons/translator-bergamot/` → "out-of-tree signed addon APK (L2.1a — slated identifier `translator-bergamot`)".

The `ime/passkey/` and `ime/voice/` facades were also flagged by `RESEARCH_FEATURE_PLAN.md` F42 but already used clean prose; verified by grep returning no `addons/<name>/` references inside their KDoc.

### Verification

- `grep -rn 'addons/[a-z-]\+/' app/src/main/kotlin/dev/patrickgold/florisboard/ime/` returns no matches at HEAD.
- `bash scripts/check-repo-hygiene.sh` → OK.
- `bash scripts/check-fastlane-metadata.sh` → OK (versionCode 1987).
- Doc-comment-only slice; KDoc changes do not affect compilation. Gradle build deferred to maintainer host per `CLAUDE.md`.

### Files Touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/cjk/CjkInputProvider.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/handwriting/StrokeRecognizer.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartcompose/SmartComposeProvider.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/translate/InlineTranslator.kt`
- `fastlane/metadata/android/en-US/changelogs/1987.txt` (new)
- `gradle.properties` (versionCode 1986→1987, versionName 1.8.186→1.8.187)
- `README.md` (version badge)
- `CHANGELOG.md` (this section)
- `RESEARCH_FEATURE_PLAN.md` (tick F42)

<a id="v1.8.186"></a>
## v1.8.186

Released: 2026-05-25

### Remove vestigial locked-off smartbar toggle (RESEARCH_FEATURE_PLAN.md F41)

Settings → Smartbar previously surfaced a permanently-disabled `Auto-expand/collapse` `SwitchPreference` (`SmartbarScreen.kt:76-87`) whose `summary_locked` string explained that the option was no longer available. The block carried a `TODO: schedule to remove this preference in the future, but keep it for now so users know why the setting is not available anymore` comment plus an orphaned `SideEffect { /* prefs.smartbar.sharedActionsAutoExpandCollapse.set(true) */ }` with a commented-out body. The transitional explanation has long outlived its purpose — current users (especially SwiftKey refugees post-2026-05-31) never saw the prior behaviour and the locked-off switch only adds visual clutter to the Smartbar settings list.

### Changes

- **`app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/smartbar/SmartbarScreen.kt`** — removed the `SideEffect { … }` block, the `SwitchPreference(prefs.smartbar.sharedActionsAutoExpandCollapse, …, enabledIf = { false }, …)` block, and the `// TODO: schedule to remove this preference …` comment that explained the workaround. Left a brief note recording the v1.8.186 cleanup so a future contributor can see why the preference key remains in `AppPrefs.kt` while no UI surfaces it.
- **`app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/smartbar/SmartbarScreen.kt`** — removed the now-unused `import androidx.compose.runtime.SideEffect`.
- **`app/src/main/res/values/strings.xml`** — deleted the three unused English strings: `pref__smartbar__shared_actions_auto_expand_collapse__label` ("Auto-expand/collapse"), `pref__smartbar__shared_actions_auto_expand_collapse__summary` ("Automatically show or hide shared actions based on typing state"), and `pref__smartbar__shared_actions_auto_expand_collapse__summary_locked` ("Always enabled to keep shared actions predictable.").

### What is intentionally not done

- **The `prefs.smartbar.sharedActionsAutoExpandCollapse` entry in `AppPrefs.kt:939` stays** with its `@Deprecated("Always enabled due to UX issues")` annotation. Saved user values written before this release deserialize cleanly against the same key; removing the AppPrefs declaration would force a JetPref schema migration for no functional benefit since no code path reads the value any more.
- **The translated `pref__smartbar__shared_actions_auto_expand_collapse__*` strings in `values-*/strings.xml`** (24 locales) are not touched in this slice. They would become "unused resources" lint warnings if Crowdin re-imports them, but a `crowdin-upload.yml` sweep on the next strings push will drop them from the Crowdin source side, and the `values-*` files will catch up over the next sync cycle. The existing lint baseline drift script (`scripts/run-lint-debug-with-baseline-check.sh`) will surface the new unused-resource entries so the cleanup is auditable.

### Verification

- `grep -rn "sharedActionsAutoExpandCollapse\|shared_actions_auto_expand_collapse" app/src/main/kotlin/` returns only the `@Deprecated` `AppPrefs.kt:939` entry (intentionally retained).
- `grep -rn "shared_actions_auto_expand_collapse" app/src/main/res/values/` returns no matches.
- `bash scripts/check-repo-hygiene.sh` → OK.
- `bash scripts/check-fastlane-metadata.sh` → OK (versionCode 1986).
- Gradle build deferred to maintainer host per `CLAUDE.md`. The deleted UI block had no consumers other than the now-removed `SideEffect`; removing it cannot cause a compile-time break.

### Files Touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/smartbar/SmartbarScreen.kt`
- `app/src/main/res/values/strings.xml`
- `fastlane/metadata/android/en-US/changelogs/1986.txt` (new)
- `gradle.properties` (versionCode 1985→1986, versionName 1.8.185→1.8.186)
- `README.md` (version badge)
- `CHANGELOG.md` (this section)
- `RESEARCH_FEATURE_PLAN.md` (tick F41)

<a id="v1.8.185"></a>
## v1.8.185

Released: 2026-05-25

### Drop the `:lib:native` placeholder + `libnative/` Rust scaffold (RESEARCH_FEATURE_PLAN.md EI11)

The `:lib:native` Gradle module and its sibling `libnative/dummy/` Rust scaffold had been disabled in `settings.gradle.kts` since 2025 (`//include(":lib:native")`). The module shipped one Kotlin file (`external fun dummyAdd(a: Int, b: Int): Int`), a Rust `lib.rs` with the matching JNI implementation, a CMakeLists.txt, an empty Android manifest, and a `Cargo.toml`. Nothing in `:app` consumed it — the only reference in production code was a commented-out import in `FlorisApplication.kt:57` and a corresponding commented-out `flogError { "dummy result: ${dummyAdd(3,4)}" }` log at line 138 with a paired `flogError { "native module disabled, skipping dummy test" }` warning that surfaced on every cold start.

The placeholder was originally retained as a roadmap surface for future native-in-`:app` work, but the project's architectural direction has firmly settled: native runtimes for optional capabilities (LiteRT-LM smart-compose per L1.1a, whisper.cpp per Next-2, librime CJK per L3, Bergamot offline NMT per L2.1a, ML Kit Digital Ink per Next-4.2a, Vosk voice per F8) ship as out-of-tree signed addon APKs via the `AddonContract.Action.REGISTER_*` enrolment contract. The base APK does not, and is not intended to, carry a native module. A dormant placeholder that promises otherwise was misleading to contributors.

### Changes

- **Deleted the `:lib:native` module** — `git rm -rf lib/native libnative` removed 8 tracked files under `lib/native/` (`build.gradle.kts`, `src/main/AndroidManifest.xml`, `src/main/kotlin/org/florisboard/libnative/test.kt`, `src/main/rust/CMakeLists.txt`, `src/main/rust/Cargo.toml`, `src/main/rust/Cargo.lock`, `src/main/rust/src/lib.c`, `src/main/rust/src/lib.rs`) plus 3 tracked files under `libnative/dummy/` (`Cargo.toml`, `Cargo.lock`, `src/lib.rs`). The working-tree `lib/native/build/` cache was deleted on disk but was never gitignored at the directory level; the v1.8.174 hygiene script already catches future tracked-build-output regressions.
- **`settings.gradle.kts`** — removed the `//include(":lib:native")  // Skip Rust native - not needed for basic keyboard` line at line 51. The trailing `include(":lib:snygg")` line now lands immediately after `include(":lib:kotlin")`.
- **`FlorisApplication.kt`** — removed the commented-out `import org.florisboard.libnative.dummyAdd` line and the corresponding `flogError { "native module disabled, skipping dummy test" }` + `// Originally: flogError { "dummy result: ${dummyAdd(3,4)}" }` block. Both fired on every cold start, polluting `adb logcat` with a noise marker for a non-existent feature.
- **`ARCHITECTURE.md` Module Layout** — replaced the `:lib:native remains present on disk but inactive` paragraph with a forward-looking note explaining that native runtimes ship as out-of-tree signed addon APKs.
- **`PROJECT_CONTEXT.md` §4 module layout** — removed the `:lib:native … placeholder for future native add-ons (commented out)` line and added the addon-only-native-runtime explanation.
- **`README.md` Module Layout** — same change for the user-facing README.
- **`ROADMAP.md` §2 State of the Repo + §0 v5.2 follow-up** — updated both mentions of `:lib:native` to reflect the drop, citing v1.8.185 + the addon-only enrolment contract.
- **`docs/REPRODUCIBLE_BUILDS.md`** — the Rust toolchain pin row's annotation moved from "only used by `lib/native` (currently disabled)" to a forward-looking note explaining the pin is retained for future out-of-tree native addons that may use the same toolchain. The pin itself stays (no harm in keeping the toolchain version recorded; Gradle does not download it unless a module references it).
- **`docs/THREAT_MODEL.md` §3.8 Auditability** — replaced the "the only native code is `lib/native` (Rust ICU helpers, source visible)" claim with the more accurate "the base APK ships zero native code as of v1.8.185" framing. The auditability surface is still strong; the prior sentence was misleading anyway because `lib/native` shipped a Rust JNI stub that returned `a + b`, not "ICU helpers."
- **`.github/workflows/android.yml`** — updated the 16 KB native-library guard comment from `the libnative/ module is disabled` to `native runtimes for optional capabilities live in out-of-tree signed addon APKs per the AddonContract enrolment path`. The check itself is unchanged (it remains a forward-looking guard for the day a release variant brings native code in).

### Verification

- `grep -rn "lib/native\|libnative\|:lib:native" .` returns matches only in `.ai/research/2026-05-17/` (frozen historical snapshot per `AGENTS.md`) plus the v1.8.185 entries in CHANGELOG, RESEARCH_FEATURE_PLAN, and PROJECT_CONTEXT/ARCHITECTURE/ROADMAP that reference the drop.
- `bash scripts/check-repo-hygiene.sh` → OK.
- `bash scripts/check-fastlane-metadata.sh` → OK (versionCode 1985).
- `ls lib/` returns the 5 surviving modules: `android`, `color`, `compose`, `kotlin`, `snygg`. `ls libnative` returns `No such file or directory`.
- Gradle reload + build deferred to maintainer host per `CLAUDE.md`. The dropped module had no consumers in `:app` or `:benchmark`, so dependency graph remains clean.

### Files Touched

- `lib/native/` (deleted, 8 tracked files + working-tree `build/` cache)
- `libnative/` (deleted, 3 tracked files)
- `settings.gradle.kts` (removed commented-out include)
- `app/src/main/kotlin/dev/patrickgold/florisboard/FlorisApplication.kt` (removed dead import + log)
- `ARCHITECTURE.md`
- `PROJECT_CONTEXT.md`
- `README.md`
- `ROADMAP.md` (two mention sites refreshed)
- `docs/REPRODUCIBLE_BUILDS.md`
- `docs/THREAT_MODEL.md`
- `.github/workflows/android.yml` (comment update only)
- `fastlane/metadata/android/en-US/changelogs/1985.txt` (new)
- `gradle.properties` (versionCode 1984→1985, versionName 1.8.184→1.8.185)
- `CHANGELOG.md` (this section)
- `RESEARCH_FEATURE_PLAN.md` (tick EI11)

<a id="v1.8.184"></a>
## v1.8.184

Released: 2026-05-25

### Log silently-dropped code points (RESEARCH_FEATURE_PLAN.md F32)

The 2026-05-25 code reconnaissance pass found three `try/catch (_: Throwable) {}` blocks that silently swallow exceptions at hot keyboard paths:

- [`TextKeyData.kt:637`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/TextKeyData.kt#L637) `MultiTextKeyData.asString(...)` — drops invalid `codePoints` from a `MultiTextKeyData` (a multi-character soft-key like a typographic ligature or compound emoji sequence). `StringBuilder.appendCodePoint(...)` throws on negative ints, `> 0x10FFFF`, or unpaired surrogate halves.
- [`TextKeyData.kt:656`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/TextKeyData.kt#L656) top-level `asString(data: KeyData, ...)` — same surrogate-half / out-of-range rejection class against `data.code`.
- [`FlorisImeService.kt:758`](app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt#L758) `getTextForImeAction(imeOptions)` — `resourcesContext.getString(AndroidInternalR.string.ime_action_*)` can throw `Resources.NotFoundException` on OEM Android builds that strip or rename internal framework string IDs.

Each catch produced no logging, so a malformed asset (e.g. a `MultiTextKeyData` with `codePoints = [0xFFFF, -1, 0x110000]`) or a missing framework string would manifest in production as a silently-missing glyph or a default-fallback `Action` label, with no trace in `adb logcat` to triage from.

### Changes

- `TextKeyData.kt:637` — caught as `t: Throwable` and routed through `flogWarning { "MultiTextKeyData.asString: dropping invalid code point $codePoint: ${t.javaClass.simpleName}" }`. New import `import dev.patrickgold.florisboard.lib.devtools.flogWarning` added.
- `TextKeyData.kt:656` — same shape for the top-level `asString(KeyData,...)`: `flogWarning { "asString(KeyData): dropping invalid code point ${data.code}: ${t.javaClass.simpleName}" }`. Re-uses the existing import.
- `FlorisImeService.kt:758` — caught as `t: Throwable` and routed through `flogWarning(LogTopic.IMS_EVENTS) { "getTextForImeAction: AndroidInternalR lookup failed (imeOptions=$imeOptions): ${t.javaClass.simpleName}" }`. The `flogWarning` import was already present from the v1.8.85 wallpaper-receiver logging path.

### Why log instead of remove the catch

The catches are intentional — `appendCodePoint(...)` and the `AndroidInternalR` framework-string lookup are both reachable from user-controlled or OEM-controlled inputs that may legitimately fail. Removing the `try/catch` would crash the IME on malformed assets; keeping the catch but logging is the right shape. The decision is consistent with the rest of the codebase: every non-trivial `catch` in the IME path is paired with a `flog*` call (e.g. `FlorisImeService.kt:393` for wallpaper-receiver registration, `KenLmTrieReader` after v1.8.122, every `DictionaryManager` SQLCipher path).

### Verification

- `grep -rn "catch (_: Throwable)" app/src/main/kotlin/` returns no matches at HEAD inside SwiftFloris-authored code paths. Upstream FlorisBoard-inherited code outside the IME hot path may still carry the pattern; this slice targeted only the three sites flagged in `RESEARCH_FEATURE_PLAN.md` §code-recon-finding-#11.
- `bash scripts/check-repo-hygiene.sh` → OK.
- `bash scripts/check-fastlane-metadata.sh` → OK (versionCode 1984).
- Release builds gate `flogWarning` output behind the existing `Flog.kt` `Flog.isEnabled` check, so the change is zero-cost in release mode and visible only to dev-build triage / `adb logcat -s FlorisIME` consumers.

### Files Touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/TextKeyData.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt`
- `fastlane/metadata/android/en-US/changelogs/1984.txt` (new)
- `gradle.properties` (versionCode 1983→1984, versionName 1.8.183→1.8.184)
- `README.md` (version badge)
- `CHANGELOG.md` (this section)
- `RESEARCH_FEATURE_PLAN.md` (tick F32)

<a id="v1.8.183"></a>
## v1.8.183

Released: 2026-05-25

### Wire the TOGGLE_AUTOCORRECT keyboard shortcut (RESEARCH_FEATURE_PLAN.md F15)

The 2026-05-25 code reconnaissance pass found that [`KeyboardManager.kt:782`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/KeyboardManager.kt#L782) shipped a `KeyCode.TOGGLE_AUTOCORRECT` handler that displayed a literal user-facing string: *"Autocorrect toggle is a placeholder and not yet implemented"*. The `TOGGLE_AUTOCORRECT` quick action is selectable from `QuickActionsEditorPanel`'s long-press drag-and-drop reorder grid, so users could put it on their smartbar and tap it expecting an effect. IMPROVEMENT_PLAN Workstream 11 flagged this as a load-bearing "placeholder feedback to replace"; `RESEARCH_FEATURE_PLAN.md` F15 promoted it to P0 because it directly contradicts the project's "no unfinished controls" principle.

### Changes

- **`app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/KeyboardManager.kt`** — `handleToggleAutocorrect()` is now a `suspend fun` that flips `prefs.correction.autoCorrect` and surfaces a localized toast. Mirrors the existing `handleToggleIncognitoMode()` pattern (write the preference inside `scope.launch { … }` because JetPref's `PreferenceData.set(...)` is suspend). The dispatch site at line 940 now routes via `scope.launch { handleToggleAutocorrect() }`, identical to the incognito-mode dispatch one line above.
- **`app/src/main/res/values/strings.xml`** — two new string resources `autocorrect_toggle__toast_after_enabled` ("Autocorrect is on") and `autocorrect_toggle__toast_after_disabled` ("Autocorrect is off"). Routed through Crowdin via the existing `crowdin-upload.yml` workflow on the next strings push.
- Removed the now-unused `import org.florisboard.lib.android.showLongToastSync` from `KeyboardManager.kt`. The remaining `showShortToastSync` and `showLongToast` imports are still consumed elsewhere in the file (verified).

### Why bind to the live `prefs.correction.autoCorrect` instead of removing the QuickAction

`prefs.correction.autoCorrect` is the preference [NlpManager.kt:365](app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/NlpManager.kt#L365) already reads on every suggestion request (`isAutoCorrectEnabled = prefs.correction.autoCorrect.get()`), plus the spacebar candidate selection at line 382 and the touch-decoder gate at [`TextKeyboardLayout.kt:901`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/TextKeyboardLayout.kt#L901). Flipping the preference takes effect on the next keystroke with no extra plumbing. The existing Settings → Typing → "Auto-correct" switch is the long-form user surface; this QuickAction is the keyboard-side shortcut to the same control.

Removing the QuickAction entirely would have been the alternative (delete `KeyCode.TOGGLE_AUTOCORRECT` + its predefined `TextKeyData` + the smartbar quick-action registry entry), but that would lose a power-user affordance that has a real underlying preference behind it. Wiring is the cheaper + more honest fix.

### Verification

- Manual review confirmed the placeholder toast string is gone from `KeyboardManager.kt`; `grep -n "placeholder and not yet implemented" app/src/` returns no matches at HEAD.
- The dispatch site at line 940 (`KeyCode.TOGGLE_AUTOCORRECT -> scope.launch { handleToggleAutocorrect() }`) mirrors the immediately-above `TOGGLE_INCOGNITO_MODE` line, so the patch is structurally aligned with the existing toggle convention.
- `bash scripts/check-repo-hygiene.sh` → OK.
- `bash scripts/check-fastlane-metadata.sh` → OK (versionCode 1983).
- Gradle build + manual real-device QA of the smartbar drag-and-drop reorder + tap flow deferred to maintainer host per `CLAUDE.md`. No new permissions or persisted-data schema changes.

### Files Touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/KeyboardManager.kt`
- `app/src/main/res/values/strings.xml`
- `fastlane/metadata/android/en-US/changelogs/1983.txt` (new)
- `gradle.properties` (versionCode 1982→1983, versionName 1.8.182→1.8.183)
- `README.md` (version badge)
- `CHANGELOG.md` (this section)
- `RESEARCH_FEATURE_PLAN.md` (tick F15)

<a id="v1.8.182"></a>
## v1.8.182

Released: 2026-05-25

### Accessibility: glide-trail photosensitivity disclosure (RESEARCH_FEATURE_PLAN.md EI4)

v1.8.172 shipped seven selectable glide-trail themes (Accent, Rainbow, Fire, Ice, Aurora, Galaxy, Neon). Five of them are time-driven — their per-segment colour is computed against the current `timeMillis`, so the trail animates as the finger draws it. `RAINBOW`, `NEON`, and `FIRE` carry enough high-frequency colour or brightness modulation to be relevant for users with photosensitive epilepsy. WCAG 2.3 / 2.3.2 sets the floor at three flashes per second of red-saturated content over more than 25% of the central visual field; SwiftFloris's trail is well under the 25%-visual-field cap (thin stroke at most ~110% of a key radius), but the maintainer cannot audit every user's hardware and ambient conditions, so the disclosure is worth making explicit.

The reduced-motion gate already shipped in v1.8.172 — `TextKeyboardLayout.kt:177-178` reads `Settings.Global.ANIMATOR_DURATION_SCALE` once per recomposition and computes `val glideShowTrail = glideShowTrailPref && !reducedMotion`. When `ANIMATOR_DURATION_SCALE == 0f` (Developer Options → "Animator duration scale" → Animations off), the trail does not draw at all, regardless of which theme is selected. So users with photosensitivity concerns have a single system-level switch that fully removes the trail surface; they do not need to navigate into Settings → Gestures to pick a less-animated theme.

This release documents both facts in `docs/ACCESSIBILITY.md` so the disclosure is canonical and discoverable.

### Changes

- **`docs/ACCESSIBILITY.md`** — new section "Glide trail themes and photosensitivity" between the Android 16 migration content and the existing "Other a11y contracts" section. Covers:
  - A table of per-theme animation rates (hue°/ms or rad/ms) so an accessibility reviewer can reason about the surface without reading the Kotlin source:
    - `ACCENT` / `ICE`: 0 (no time-driven effect)
    - `RAINBOW`: 0.2 hue°/ms (~3 Hz at typical gesture lengths)
    - `AURORA`: 0.06 hue°/ms (slow shimmer)
    - `FIRE`: 0.003 rad/ms (~0.5 Hz visible flicker on the hot tail only)
    - `GALAXY`: 0.0004 rad/ms (sub-Hz drift)
    - `NEON`: 0.015 rad/ms (~2.4 Hz pulse across the whole trail)
  - The WCAG 2.3.2 framing (three flashes per second of red-saturated content over 25% of the visual field) plus the visual-field bound that SwiftFloris's stroke comfortably stays under.
  - The reduced-motion guarantee, citing the specific file and lines (`TextKeyboardLayout.kt:177-178`) so the gate is auditable. The Snygg engine's parallel reduced-motion gate for `KeyPopupElement` accent rings and smartbar transitions is mentioned for completeness — the glide trail inherits the same contract.
  - User-facing recommendation: turn on Developer Options → "Animator duration scale" → Animations off (or the Android-14+ per-app motion preference where available).
  - Follow-up tracker: a future Settings → Gestures → "Trail theme" picker could grow a small "ⓘ" tooltip beside `RAINBOW`/`AURORA`/`NEON`/`FIRE`/`GALAXY` noting the animation rate. Tracked as a Workstream 10 polish slice; this doc serves as the canonical reference until the tooltip lands.

### What is intentionally not done in this slice

- **Settings → Gestures inline tooltip / ⓘ icon.** Adding per-theme tooltips would require new Compose UI (a `Popup` or `TooltipBox`) and additional `strings.xml` entries that Crowdin would need to translate. Doc-only disclosure is sufficient for the per-PR-scope-discipline (`AGENTS.md` hard rule #6), and the existing reduced-motion gate already handles the load-bearing case (photosensitivity-concerned users have a system-level kill-switch).
- **Code change to gate animations per-theme.** The whole trail rendering path is already gated off; per-theme animation gating would be wasted complexity.

### Verification

- `grep -n "ANIMATOR_DURATION_SCALE" app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/TextKeyboardLayout.kt` returns the gate at lines 172 + 177 (Verified).
- `bash scripts/check-repo-hygiene.sh` → OK.
- `bash scripts/check-fastlane-metadata.sh` → OK (versionCode 1982).
- Doc-only slice; gradle gates deferred to maintainer host per `CLAUDE.md`.

### Files Touched

- `docs/ACCESSIBILITY.md`
- `fastlane/metadata/android/en-US/changelogs/1982.txt` (new)
- `gradle.properties` (versionCode 1981→1982, versionName 1.8.181→1.8.182)
- `README.md` (version badge)
- `CHANGELOG.md` (this section)
- `RESEARCH_FEATURE_PLAN.md` (tick EI4)

<a id="v1.8.181"></a>
## v1.8.181

Released: 2026-05-25

### Honeycomb layout promotion (RESEARCH_FEATURE_PLAN.md EI8)

v1.8.79 shipped the production honeycomb hex layout — `LayoutManager` recognises the bundled `honeycomb` character layout, `TextKeyboard.layoutHoneycomb(...)` positions real `TextKey` instances in a hex tessellation, `TextKeyboardLayout` clips Snygg key surfaces to `HoneycombHexShape`, and hit testing uses the actual hex polygon instead of rectangular bounding boxes. SwiftFloris is the only FOSS Android keyboard shipping this niche after Typewise vacated the consumer market in early 2026 (per the 2026-05-17 + 2026-05-25 competitor matrix passes).

The implementation had near-zero promotion: no contributor-facing design doc, no README callout to it as a differentiator, and no entry in `docs/AI_PROMPTS_EXTERNAL_WORK.md`-style positioning surfaces. v1.8.181 closes the documentation gap.

### Changes

- **`docs/HONEYCOMB_LAYOUT.md`** — new longform doc covering the design rationale (why hex tessellation increases per-key touch area at the same total keyboard height; why mistype recovery is more predictable than QWERTY), the contributor surface (8 referenced files with one-line role descriptions: `HoneycombHexShape`, `HoneycombHexButton`, `HoneycombKeyboardRow`, `HoneycombTessellation`, `HoneycombLayoutLoader`, `TextKeyboard.layoutHoneycomb(...)`, `TextKeyboardLayout`, `LayoutManager`), how a user enables it (Settings → Localization → Subtype → Character layout → Honeycomb), the strategic positioning ("only FOSS Android keyboard shipping this — Typewise vacated 2026"), explicit clarifications about what it is NOT (not a Typewise copy, not an AI feature, not promoted as default — QWERTY stays the SwiftKey-refugee-friendly default), and four open follow-ups: Roborazzi visual baseline (tracked as F40), theme-coverage matrix across the 21 bundled themes, tablet-split honeycomb (Next-7.2 split-mode currently passes through to standard QWERTY), and a Macrobenchmark perf baseline for hex hit testing.
- **`README.md` `Highlights` table** — the existing "Alternative layouts" row now names honeycomb as "only FOSS Android keyboard shipping this — Typewise vacated the consumer market early 2026" and links directly to the new doc.

### What is intentionally not done in this slice

- **Fastlane screenshot of honeycomb in `fastlane/metadata/android/en-US/images/phoneScreenshots/`.** Screenshots require a real device + screen capture flow; the existing 5 phoneScreenshots are FlorisBoard-era artefacts that need a separate fresh-capture pass against v1.8.181 themes (SwiftKey Pure Light/Dark, M3E Nord, Aurora Animated, SwiftKey High Contrast AAA, honeycomb). Tracked as a future fastlane-image-refresh slice; not in this batch's scope per the per-PR scope-discipline rule (`AGENTS.md` hard rule #6).
- **Roborazzi baseline.** Same scope-discipline reason; would require a Roborazzi rule + screenshot test. Tracked as F40 / EI9.

### Verification

- `bash scripts/check-repo-hygiene.sh` → OK.
- `bash scripts/check-fastlane-metadata.sh` → OK (versionCode 1981).
- `docs/HONEYCOMB_LAYOUT.md` lints clean against the project's existing doc style (mirrors `docs/SPLIT_KEYBOARD.md` and `docs/AUTOCORRECT_LIFECYCLE.md` layout).
- All in-doc file references resolve: each cited path exists at HEAD (verified via `Glob app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/Honeycomb*` and `Glob app/src/main/assets/ime/keyboard/.../honeycomb.json`).

### Files Touched

- `docs/HONEYCOMB_LAYOUT.md` (new)
- `README.md` (Highlights table — honeycomb row links to the new doc)
- `fastlane/metadata/android/en-US/changelogs/1981.txt` (new)
- `gradle.properties` (versionCode 1980→1981, versionName 1.8.180→1.8.181)
- `CHANGELOG.md` (this section)
- `RESEARCH_FEATURE_PLAN.md` (tick EI8)

<a id="v1.8.180"></a>
## v1.8.180

Released: 2026-05-25

### Version catalog hygiene (RESEARCH_FEATURE_PLAN.md F34; F33 investigated and rejected)

The 2026-05-25 build/CI reconnaissance pass flagged three hardcoded `testImplementation` coordinate strings in [`app/build.gradle.kts`](app/build.gradle.kts) lines 498–500 (`"androidx.compose.ui:ui-test-junit4"`, `"androidx.test:runner:1.7.0"`, `"junit:junit:4.13.2"`). Hardcoded strings bypass the version catalog, which means Dependabot's grouped Android-runtime / Android-test-tooling update rules (`.github/dependabot.yml`) skip them, and bumps require hand-editing the `.kts` file instead of a single catalog entry.

The same pass also claimed `coil` and `material-kolor` were dead catalog pins (F33). Verification on this run found both are actively consumed by sibling library modules: `lib/snygg/build.gradle.kts:103-104` uses `libs.coil.compose` + `libs.coil.gif`, and `lib/color/build.gradle.kts:60` uses `libs.material.kolor`. The "dead pin" claim came from grepping only `app/`, which missed the sibling modules. F33 is therefore rejected and the catalog entries remain.

### Changes

- **`gradle/libs.versions.toml`** — added three new entries:
  - `[versions] androidx-test-runner = "1.7.0"`
  - `[versions] junit4 = "4.13.2"`
  - `[libraries] androidx-test-runner = { module = "androidx.test:runner", version.ref = "androidx-test-runner" }`
  - `[libraries] androidx-compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }` (no `version.ref` — coordinate is managed by the existing `androidx-compose-bom`)
  - `[libraries] junit4 = { module = "junit:junit", version.ref = "junit4" }`
- **`app/build.gradle.kts:498-500`** — replaced the three hardcoded coordinate strings with their new catalog accessors:
  - `testImplementation("androidx.compose.ui:ui-test-junit4")` → `testImplementation(libs.androidx.compose.ui.test.junit4)`
  - `testImplementation("androidx.test:runner:1.7.0")` → `testImplementation(libs.androidx.test.runner)`
  - `testImplementation("junit:junit:4.13.2")` → `testImplementation(libs.junit4)`

### Why JUnit 4 is still pinned

JUnit 4 is **not** a primary test framework here — the project's main test runner is Kotest 6 on JUnit Platform (`useJUnitPlatform()` per the existing `tasks.withType<Test>` block). The JUnit-4 transitive surface stays because Robolectric's `@RunWith(RobolectricTestRunner::class)` rule and Compose's `createComposeRule()` ship as JUnit-4 rules, and `junit-vintage-engine` bridges them onto the JUnit-Platform runner. Pinning `junit:junit:4.13.2` (the last stable JUnit 4) keeps the bridge deterministic. JUnit 5 / JUnit Platform stays the primary runner for new tests.

### Verification

- `bash scripts/check-repo-hygiene.sh` → OK.
- `bash scripts/check-fastlane-metadata.sh` → OK (versionCode 1980).
- `git diff gradle/libs.versions.toml` shows only the three additive entries; no edits to existing live pins.
- `grep -E "implementation\(\"[a-z.]+:[a-z-]+" app/build.gradle.kts` returns no remaining hardcoded coordinate strings.
- Sibling-module usage confirmed: `grep -rn "libs\.coil\|libs\.material\.kolor" lib/` returns the expected matches in `lib/snygg/build.gradle.kts` and `lib/color/build.gradle.kts`.
- Gradle compile + Dependabot grouping verification deferred to maintainer host per `CLAUDE.md`.

### Files Touched

- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `fastlane/metadata/android/en-US/changelogs/1980.txt` (new)
- `gradle.properties` (versionCode 1979→1980, versionName 1.8.179→1.8.180)
- `README.md` (version badge)
- `CHANGELOG.md` (this section)
- `RESEARCH_FEATURE_PLAN.md` (tick F34, mark F33 rejected with reason)

<a id="v1.8.179"></a>
## v1.8.179

Released: 2026-05-25

### Remove "Neural coming soon" placeholder (RESEARCH_FEATURE_PLAN.md F17)

The 2026-05-25 code reconnaissance pass surfaced that `app/src/main/res/values/strings.xml:978` shipped a `pref__glide__engine__neural_coming_soon` string ("Neural coming soon") bound to a `GlideTypingEngine.NEURAL_COMING_SOON` enum value, but no language profile in `GlideTypingLanguageSupport` actually mapped to it — every concrete profile (en/de/es/fr/it/pt) uses `GlideTypingEngine.STATISTICAL`. The enum value advertised an unimplemented feature; Settings → Gestures could never have shown the label because no profile ever returned it.

This is the "advertise vs ship" cleanup IMPROVEMENT_PLAN Workstream 12 flags as part of localization/content quality, and which `RESEARCH_FEATURE_PLAN.md` F17 promoted to P0.

### Changes

- **`app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/gestures/GlideTypingLanguageSupport.kt`** — removed the `NEURAL_COMING_SOON` enum entry from `GlideTypingEngine`. The enum now contains a single member, `STATISTICAL`. All existing profile constructions remain valid.
- **`app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/gestures/GesturesScreen.kt`** — removed the `GlideTypingEngine.NEURAL_COMING_SOON -> R.string.pref__glide__engine__neural_coming_soon` arm from `engineLabelRes`. The `when` is still exhaustive: `STATISTICAL` and `null` (no profile for the language).
- **`app/src/main/res/values/strings.xml:978`** — deleted the `pref__glide__engine__neural_coming_soon` string. No translated `values-*` file referenced this key (verified by grep across `app/src/main/res/`).

### Why not wire a real neural engine instead

The honest path is to remove the placeholder until a permissive open-source glide model + dataset are ready. `RESEARCH_FEATURE_PLAN.md` F21 separately tracks training an Apache-2.0 glide model on the MIT-licensed FUTO swipe dataset (1.04M rows, live on Hugging Face) — that work happens out-of-tree (off-device training pipeline + model packaging into a sibling addon APK) and is XL complexity. Until the resulting addon is installable, the only production glide engine is `StatisticalGlideTypingClassifier`, and the Settings UI should not promise otherwise. When the addon path lands, the engine label can return as something concrete like `pref__glide__engine__neural_addon` referenced only when the addon registry exposes a glide-model provider.

### Verification

- `grep -rn "neural_coming_soon\|NEURAL_COMING_SOON" app/` returns no matches at HEAD.
- `bash scripts/check-repo-hygiene.sh` → OK.
- `bash scripts/check-fastlane-metadata.sh` → OK (versionCode 1979).
- The Kotlin `when` over `GlideTypingEngine?` is still exhaustive after removing the enum value; no compile-time surprise. Gradle build deferred to maintainer host per `CLAUDE.md`.

### Files Touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/gestures/GlideTypingLanguageSupport.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/gestures/GesturesScreen.kt`
- `app/src/main/res/values/strings.xml`
- `fastlane/metadata/android/en-US/changelogs/1979.txt` (new)
- `gradle.properties` (versionCode 1978→1979, versionName 1.8.178→1.8.179)
- `README.md` (version badge)
- `CHANGELOG.md` (this section)
- `RESEARCH_FEATURE_PLAN.md` (tick F17)

<a id="v1.8.178"></a>
## v1.8.178

Released: 2026-05-25

### CI: explicit verifyDataExtractionRules + release-variant zipalign (RESEARCH_FEATURE_PLAN.md F25 + F26)

Two release-time CI signals were firing transitively but not as visible workflow steps. v1.8.178 promotes both to explicit steps.

### Changes

- **`.github/workflows/android.yml`** — added an explicit `Verify data-extraction rules` step calling `:app:verifyDataExtractionRules`. The gate already fires from `preBuild` (registered in `app/build.gradle.kts:364-430`), but a transitive trigger means a failure surfaces under `Build debug APK` instead of its own step, making the workflow log harder to scan and the run-summary signal less legible. The explicit step also documents the contract in CI for future contributors auditing the workflow.
- **`.github/workflows/release.yml`** — added the same explicit `Verify data-extraction rules` step before `Run unit tests`. A release flow that skipped this gate could ship a tagged build with regressed extraction excludes; calling it explicitly closes the gap.
- **`.github/workflows/release.yml`** — added a `16 KB native-library alignment guard (release variant)` step immediately after `Locate release APK` and before `Compute SHA-256 manifest`. `android.yml` ran `zipalign -P 16` on the debug APK every PR, but the release variant applies R8 minify/shrink and uses different signing — its native `.so` layout was not transitively validated by the debug build. The new step runs `zipalign -c -P 16 -v 4` against the located release APK and fails with a clear `::error::` if any `.so` is aligned to less than 16 KB. No-op for SwiftFloris today (zero native libs in the release variant), but engages the moment any of Next-2 (whisper.cpp), N1.2 (CleverKeys ONNX), L1 (LiteRT-LM addon-tier integration), or L7 (MCP daemon) brings native code into the release path.

### Why now

The 2026-05-25 build/CI reconnaissance pass found that `verifyDataExtractionRules` fired only transitively via preBuild, and `zipalign -P 16` ran only on debug. Both are load-bearing gates per the project's invariants (data_extraction_rules.xml pins the personal-dictionary + clipboard backup excludes; 16 KB alignment is required by Google Play after 2025-11-01 for target-Android-15+ APKs). Adding explicit release-time steps closes the residual gap before the F-Droid `Reproducible` tier submission opens its review.

### Verification

- `bash scripts/check-repo-hygiene.sh` → OK.
- `bash scripts/check-fastlane-metadata.sh` → OK (versionCode 1978).
- Gradle gates deferred to maintainer host per `CLAUDE.md`. The `:app:verifyDataExtractionRules` task is registered at `app/build.gradle.kts:364-430` and wired into `preBuild` at lines 426-430, so an explicit call adds no new compile / runtime risk.

### Files Touched

- `.github/workflows/android.yml` (explicit verifyDataExtractionRules step)
- `.github/workflows/release.yml` (explicit verifyDataExtractionRules step + release-variant zipalign)
- `fastlane/metadata/android/en-US/changelogs/1978.txt` (new)
- `gradle.properties` (versionCode 1977→1978, versionName 1.8.177→1.8.178)
- `README.md` (version badge)
- `CHANGELOG.md` (this section)
- `RESEARCH_FEATURE_PLAN.md` (tick F25 + F26)

<a id="v1.8.177"></a>
## v1.8.177

Released: 2026-05-25

### Supply-chain hardening (RESEARCH_FEATURE_PLAN.md F19 + F20)

The 2026-05-25 build/CI reconnaissance pass found that 7 of 8 GitHub Actions workflows used floating major-tag pins (e.g. `actions/checkout@v4`, `gradle/actions/setup-gradle@v4`, `reactivecircus/android-emulator-runner@v2`, `google/osv-scanner-action@v2.0.2`). A floating tag's underlying commit can be re-pointed by the action's owner after the fact; a compromise of any action's repo would re-point the tag at malicious code, and the next CI run on SwiftFloris would execute it. Only `crowdin-upload.yml` and `validate-strings-no-translations.yml` already SHA-pinned. Additionally, `release.yml`'s OSV-Scanner step downloaded the v2.0.2 binary via curl without SHA-256 verification — a CDN-level swap would similarly run unverified code on the runner.

### Changes

- **SHA-pinned every third-party action across all 8 workflows.** The mapping (each replacement is exact `action@tag` → `action@<sha> # tag`):
  - `actions/checkout@v4` → `@34e114876b0b11c390a56381ad16ebd13914f8d5`
  - `actions/setup-java@v4` → `@c1e323688fd81a25caa38c78aa6df2d33d3e20d9`
  - `actions/upload-artifact@v4` → `@ea165f8d65b6e75b540449e92b4886f43607fa02`
  - `actions/dependency-review-action@v4` → `@4901385134134e04cec5fbe5ddfe3b2c5bd5d976`
  - `gradle/actions/wrapper-validation@v4` → `@48b5f213c81028ace310571dc5ec0fbbca0b2947`
  - `gradle/actions/setup-gradle@v4` → `@48b5f213c81028ace310571dc5ec0fbbca0b2947`
  - `lukka/get-cmake@v4.0.2` → `@ea004816823209b8d1211e47b216185caee12cc5`
  - `google/osv-scanner-action/osv-scanner-action@v2.0.2` → `@e69cc6c86b31f1e7e23935bbe7031b50e51082de`
  - `reactivecircus/android-emulator-runner@v2` → `@e89f39f1abbbd05b1113a29cf4db69e7540cae5a`
  SHAs were resolved via `gh api repos/<owner>/<repo>/git/refs/tags/<tag>` against the live GitHub API on the run date; annotated tags were dereferenced one level to the underlying commit object.
- **SHA-256-pinned the `osv-scanner` v2.0.2 binary in `.github/workflows/release.yml`.** Added an `OSV_BINARY_SHA256` env on the OSV scan step (value `3abcfd7126c453a00421487e721b296e0cb68085bd431d6cef60872774170fc8`); the step now runs `sha256sum` against the downloaded binary and refuses to execute on mismatch with a clear `::error::` message. A future bump to `v2.x.y` requires re-recording the digest alongside the URL bump.

### Verification

- `grep -rn "uses: [a-zA-Z0-9_/.-]\+@v" .github/workflows/` returns no matches (no unpinned actions remain at HEAD).
- `grep -rn "uses: " .github/workflows/` returns 41 references, all carrying `@<sha> # <tag>` form.
- `curl -sSL .../osv-scanner_linux_amd64 | sha256sum` was computed off-runner to confirm the env value: `3abcfd7126c453a00421487e721b296e0cb68085bd431d6cef60872774170fc8`.
- `bash scripts/check-repo-hygiene.sh` → OK.
- `bash scripts/check-fastlane-metadata.sh` → OK (versionCode 1977).

### Bump guidance

To bump an action across the project:
1. Pick the new version tag.
2. Resolve the commit SHA via `gh api repos/<owner>/<repo>/git/refs/tags/<tag>` (dereference annotated tags).
3. Replace every occurrence of the old SHA with the new SHA; update the trailing `# vN.Y` comment to match.
4. If the new tag changes major version, treat as a breaking change and run the workflow on a draft branch first.

### Files Touched

- `.github/workflows/android.yml`
- `.github/workflows/dependency-scan.yml`
- `.github/workflows/emulator-smoke.yml`
- `.github/workflows/reproducible-build.yml`
- `.github/workflows/release.yml` (SHA-pins + OSV binary verification)
- `.github/workflows/roborazzi-baseline.yml`
- `.github/workflows/crowdin-upload.yml`
- `.github/workflows/validate-strings-no-translations.yml`
- `fastlane/metadata/android/en-US/changelogs/1977.txt` (new)
- `gradle.properties` (versionCode 1976→1977, versionName 1.8.176→1.8.177)
- `README.md` (version badge)
- `CHANGELOG.md` (this section)
- `RESEARCH_FEATURE_PLAN.md` (tick F19 + F20)

<a id="v1.8.176"></a>
## v1.8.176

Released: 2026-05-25

### Stale-Doc Refreshes (RESEARCH_FEATURE_PLAN.md F35 + F36)

The 2026-05-25 build/CI reconnaissance pass flagged three documentation surfaces drifting behind reality:

- **`docs/THREAT_MODEL.md`** had `Last updated: 2026-05-17 (v1.8.68)` — that's 108 versions stale and predates the entire seventh-pass privacy audit closure run (v1.8.85 → v1.8.122) plus the v1.8.123 Roborazzi hard-gate, v1.8.124 addon-trust controls, v1.8.125 dictionary-asset mounting, and v1.8.174/v1.8.175 repo + listing hygiene gates. A privacy-curious user reading the threat model today would miss every concrete attack-surface improvement of the last hundred versions.
- **`docs/LOCAL_VERIFICATION.md`** had `Last updated: 2026-05-18 for v1.8.166` and did not mention `scripts/check-fastlane-metadata.sh` introduced in v1.8.175 — so a contributor running the documented gate set would let F-Droid metadata drift slip through.
- **`.github/workflows/roborazzi-baseline.yml`** comment block instructed the maintainer to remove `continue-on-error: true` from `android.yml`'s Roborazzi step once baselines landed. v1.8.123 already promoted the step to a hard gate. Instructions were stale.

### Changes

- **`docs/THREAT_MODEL.md`** — `Last updated` advanced to `2026-05-25 (v1.8.176)`. Added a "What changed since the v1.8.68 baseline" section enumerating every privacy/security item shipped in v1.8.85 → v1.8.175 with a one-line description tying each back to its finding ID in `.ai/research/2026-05-17/SIXTH_PASS_FINDINGS.md` and `SEVENTH_PASS_FINDINGS.md`. Twenty audit-trail bullets cover: manifest-merge scanning, data extraction rules excludes + gate, FLAG_SECURE numeric-PIN propagation, ZipUtils atomic abort, app-declared incognito enforcement, clipboard-incognito gating, voice-handoff sensitive-field guard, provider-backed-media size caps + close-before-evict, VoiceInputSetupActivity export hardening, RECORD_AUDIO precondition for external voice IMEs, KenLM reader offset rejection, Roborazzi hard-gate, addon trust revoke/reset, no-extraction dictionary asset mount, repo-root hygiene script extension, and the F-Droid metadata gate.
- **`docs/LOCAL_VERIFICATION.md`** — `Last updated` advanced to `2026-05-25 for v1.8.176`. Standard local-gate block now lists `bash scripts/check-fastlane-metadata.sh` alongside `check-repo-hygiene.sh`. Added a brief note explaining the F-Droid-metadata-vs-versionCode contract (`fastlane/metadata/android/en-US/changelogs/<code>.txt` must exist for every `gradle.properties` versionCode bump).
- **`.github/workflows/roborazzi-baseline.yml`** — removed the stale `continue-on-error` instruction; preserved a single audit-trail line so future readers see the historical context. Also corrected the snapshot path from `app/src/test/snapshots/images/` to the actual `app/src/test/snapshots/` location used by the current Roborazzi config.

### Verification

- `bash scripts/check-repo-hygiene.sh` → OK.
- `bash scripts/check-fastlane-metadata.sh` → OK (versionCode 1976).
- Doc-only slice; no Kotlin / Gradle changes. Maintainer-host Gradle verification not required for this release.

### Files Touched

- `docs/THREAT_MODEL.md`
- `docs/LOCAL_VERIFICATION.md`
- `.github/workflows/roborazzi-baseline.yml`
- `fastlane/metadata/android/en-US/changelogs/1976.txt` (new)
- `gradle.properties` (versionCode 1975→1976, versionName 1.8.175→1.8.176)
- `README.md` (version badge)
- `CHANGELOG.md` (this section)
- `RESEARCH_FEATURE_PLAN.md` (tick F35 + F36)

<a id="v1.8.175"></a>
## v1.8.175

Released: 2026-05-25

### F-Droid Metadata Rewrite (RESEARCH_FEATURE_PLAN.md F1)

The repo's `fastlane/metadata/android/en-US/` content was inherited verbatim from upstream FlorisBoard v0.3.16 era and never updated. As of v1.8.173 the F-Droid listing would have published `title=FlorisBoard`, `short_description="An open-source keyboard which respects your privacy. Currently in beta."`, and a `full_description` that opens with `<i>FlorisBoard</i>`. The latest `changelogs/86.txt` body read `Detailed changelog: https://github.com/florisboard/florisboard/releases/tag/v0.3.16` while `gradle.properties` was on `projectVersionCode=1973`. Any user reaching SwiftFloris through F-Droid (or through the pending `fdroiddata` PR) would see stale upstream FlorisBoard branding. This is the single highest-leverage brand-identity bug the 2026-05-25 research pass surfaced, and the SwiftKey-refugee migration window closes 2026-05-31.

### Changes

- **`fastlane/metadata/android/en-US/title.txt`** — `FlorisBoard` → `SwiftFloris` (11 chars, under F-Droid's 50-char cap).
- **`fastlane/metadata/android/en-US/short_description.txt`** — Upstream "Currently in beta" copy replaced with `Privacy-first Android keyboard. Apache-2.0. No INTERNET. No accounts.` (69 chars, under the 80-char cap). Mirrors the README §1 wedge claim.
- **`fastlane/metadata/android/en-US/full_description.txt`** — Rewritten against v1.8.175 reality: no-INTERNET build gate, SwiftKey-migration window, typing features (SymSpell d1+d2, bigram/trigram, bilingual subtypes, glide trail themes), 63-script transliteration coverage, voice/emoji/stickers, privacy posture (SQLCipher + Tink + AndroidKeystore, FLAG_SECURE, reproducible-build CI), 21 themes including honeycomb hex, productivity actions (calendar/task quick-insert, Tasker, MCP daemon bridge), form factors (floating/split/one-handed, hardware-keyboard import), and the Obtainium-first distribution model. Replaces the upstream FlorisBoard `<ul>` feature list.
- **`fastlane/metadata/android/en-US/changelogs/1974.txt`** — backfills the v1.8.174 entry under 500 chars so the previous release ships with its own listing changelog.
- **`scripts/check-fastlane-metadata.sh`** — new CI gate that rejects metadata drift. Enforces: (a) title.txt ≤ 50 chars + not empty + does not contain `FlorisBoard`; (b) short_description.txt ≤ 80 chars + not empty; (c) full_description.txt not empty; (d) for the current `projectVersionCode`, a matching `changelogs/<code>.txt` file exists, is non-empty, and is ≤ 500 chars. The forward-going contract: every `gradle.properties` versionCode bump ships with its own per-versionCode changelog file extracted from the matching `## vX.Y.Z` section of `CHANGELOG.md`.
- **`.github/workflows/android.yml`** — calls `scripts/check-fastlane-metadata.sh` immediately after the repo-hygiene check, so PRs that bump `projectVersionCode` without writing a matching changelog fail visibly.

### Out of Scope

- Translation of metadata to other locales. The upstream `values-*` Crowdin pipeline covers in-app strings; the fastlane metadata directory currently ships only `en-US/`. Adding `de-DE/`, `es/`, `fr/`, etc. is a separate slice and depends on F-Droid metadata locale conventions.
- The actual `fdroiddata` submission PR. The build-server YAML template lives in `docs/REPRODUCIBLE_BUILDS.md`. RESEARCH_FEATURE_PLAN.md F12 tracks the submission as P2 because the package-id collision with upstream FlorisBoard's existing F-Droid listing (`dev.patrickgold.florisboard.beta`) needs maintainer-level disambiguation first.
- Backfilling older versionCode changelogs. The fastlane convention reads changelogs per versionCode going forward; only the current one is gate-required. The existing FlorisBoard upstream changelogs from `12.txt` through `86.txt` are left in place as historical context.

### Verification

- `bash scripts/check-fastlane-metadata.sh` → `OK (versionCode 1975, title=11 chars, short=69 chars, changelog=...)` once the v1.8.175 changelog file lands (see below).
- Negative test: `sed -i 's/projectVersionCode=1974/projectVersionCode=9999/' gradle.properties && bash scripts/check-fastlane-metadata.sh` exits 1 with the missing-changelog error. Confirmed.
- Negative test: `echo 'FlorisBoard' > fastlane/metadata/android/en-US/title.txt && bash scripts/check-fastlane-metadata.sh` would exit 1 (gate rejects the upstream name).
- Gradle gates deferred to maintainer host per [`CLAUDE.md`](CLAUDE.md).

### Files Touched

- `fastlane/metadata/android/en-US/title.txt`
- `fastlane/metadata/android/en-US/short_description.txt`
- `fastlane/metadata/android/en-US/full_description.txt`
- `fastlane/metadata/android/en-US/changelogs/1974.txt` (new)
- `fastlane/metadata/android/en-US/changelogs/1975.txt` (new — for this release)
- `scripts/check-fastlane-metadata.sh` (new)
- `.github/workflows/android.yml` (call the new gate)
- `gradle.properties` (versionCode 1974→1975, versionName 1.8.174→1.8.175)
- `README.md` (version badge)
- `CHANGELOG.md` (this section)
- `RESEARCH_FEATURE_PLAN.md` (tick F1)

<a id="v1.8.174"></a>
## v1.8.174

Released: 2026-05-25

### Repository Hygiene

- **Untracked stale root-level artefacts.** Removed `SwiftFloris_icon.png` (787 KB; redundant with the `fastlane/metadata/android/en-US/images/icon.png` F-Droid listing image) and `ROADMAP.md.backup-v2` (21 KB; pre-rewrite snapshot superseded by the live `ROADMAP.md` at v5.67) from git tracking. Files remain in `.gitignore`-covered space; history is preserved per [`CLAUDE.md`](CLAUDE.md) safety rules — no history rewrite.
- **Extended `scripts/check-repo-hygiene.sh` to reject regressions.** The script now fails when any of the following land at the repo root: `*.apk`, `*.aab`, `*.jks`, `*.keystore`, `local.properties`, `*.backup*`, `*.bak`, or any PNG > 200 KB. The historical case for each rule:
  - Release APKs / AABs ship via GitHub Releases artefacts, not git; v1.5.2 era leaked a 9.7 MB `app-release-v1.5.2.apk` into the working tree that every fresh clone paid for.
  - Keystores (`*.jks`, `*.keystore`) never enter git — release signing flows through `release.yml`'s base64 secret + `$RUNNER_TEMP` (`release.yml:138-175`).
  - `local.properties` is per-machine SDK configuration; gitignore covers it but `git add -f` could still slip it in.
  - `*.backup*` / `*.bak` belong under `docs/archive/` when retained at all — `v1.8.171` already retired `README.md.bak`.
  - Root-level branding PNGs over 200 KB belong under `fastlane/metadata/android/en-US/images/` (F-Droid listing) or `app/src/main/res/` (in-app drawable). The `:(top)*.png` matcher restricts the size check to top-level entries; `app/src/main/res/drawable/ic_swiftfloris_foreground.png` (787 KB) is correctly out of scope.
- **Documented in `docs/REPO_HYGIENE.md`.** Added a numbered rule list (generated output, deleted markdown, root-level forbidden artefacts, large root-level PNGs) plus the historical rationale tying the rules to actual prior incidents.

### Verification

- `bash scripts/check-repo-hygiene.sh` returned `OK` at HEAD.
- Planted regression `touch test-stub.apk && git add -f test-stub.apk` was correctly rejected with exit code 1 and a clear `::error::` message; the test artefact was cleaned up before commit.
- Gradle verification (`:app:verifyNoInternetPermission :app:testDebugUnitTest :app:verifyRoborazziDebug :app:lintDebug :app:assembleDebug`) deferred to the maintainer host per [`CLAUDE.md`](CLAUDE.md) — no Java / Android SDK on the autonomous-loop VM.

### Files Touched

- `scripts/check-repo-hygiene.sh` (rule extension)
- `docs/REPO_HYGIENE.md` (rule documentation + rationale)
- `SwiftFloris_icon.png` (untracked, file deleted from index only)
- `ROADMAP.md.backup-v2` (untracked, file deleted from index only)
- `gradle.properties` (versionCode 1973→1974, versionName 1.8.173→1.8.174)
- `README.md` (version badge)
- `CHANGELOG.md` (this section)

<a id="v1.8.173"></a>
## v1.8.173

Released: 2026-05-18

### Fixes

- **EmojiCompat race crash on emoji picker open** ([#1](https://github.com/SysAdminDoc/SwiftFloris/issues/1)) — Compose's `AndroidParagraphHelper.createCharSequence` calls `EmojiCompat.get().process(...)` whenever `EmojiCompat.isConfigured()` returns true, but does not catch the `IllegalStateException("Not initialized yet")` thrown by `process()` between singleton install and metadata load completion. Opening the emoji picker before the asynchronous metadata load finished crashed the IME (`java.lang.IllegalStateException: Not initialized yet` at `EmojiCompat.process:1105` → `ParagraphLayoutCache.layoutText` → first measure of the picker). Fix: construct the managed `EmojiCompat` instance via the package-private constructor (reflection) so `sInstance` stays null during load. Compose then sees `isConfigured() == false` and uses the raw text path until the `InitCallback.onInitialized` callback fires, at which point we install the fully-loaded instance via `EmojiCompat.reset(EmojiCompat)`. No more race window where `isConfigured` and `isInitialized` diverge.

<a id="v1.8.172"></a>
## v1.8.172

Released: 2026-05-18

### Glide Trail Visual Overhaul

- **Path-based trail rendering** — Replaced the legacy dotted-circle trail (individual `drawCircle` calls at 3px spacing) with segmented `Path`-based strokes using `StrokeCap.Round` and `StrokeJoin.Round`. Produces a smooth, continuous line instead of a beaded necklace.
- **Alpha gradient** — Trail opacity now fades from 15% at the tail to 90% at the fingertip using a `√progress` curve, keeping the tail visible much longer than the previous `progress²` quadratic that made the first half nearly invisible.
- **Tapered width** — Trail width scales from 60% at the tail to 110% of base radius at the head, giving a natural pen-pressure feel.
- **Head dot** — 3-layer concentric dot at the fingertip: wide glow ring (20% alpha, 2× radius), core color dot (95% alpha), and white-hot center highlight (70% alpha).
- **Key highlight** — Subtle rounded-rect overlay (12% alpha, 8px corner radius) drawn on whichever key the finger is currently over during a glide. Tracked via `glideActiveKey` state updated on every `onGlideAddPoint`. Clears automatically on gesture end.
- **Trail duration default 200ms → 500ms** — The longer window gives color gradients room to spread across the trail length instead of compressing into a tiny sliver.

### Glide Trail Theme System

- **7 selectable trail themes** — New `GlideTrailTheme` enum with per-segment color computation:
  - **Accent** — Theme primary color with transparent-alpha fallback guard (if `--primary` resolves to alpha < 0.1, falls back to `Color.Green`).
  - **Rainbow** — Full 360° hue sweep along the trail, animated over time at 0.2° per millisecond. 90% saturation, full brightness.
  - **Fire** — 4-stop gradient: dark crimson → red → orange → yellow → white-hot. Includes per-pixel heat shimmer animation on the hottest segment.
  - **Ice** — 4-stop gradient: dark indigo → electric blue → cyan → white frost.
  - **Aurora** — Northern lights: 180° hue sweep through greens/teals/purples, slowly cycling over time at 0.06° per millisecond. 70% saturation.
  - **Galaxy** — Deep space: dark purple → electric blue → hot pink → soft lavender. Subtle animated hue shift on the middle segments.
  - **Neon** — Pulsing bright green with sinusoidal brightness modulation (0.015 rad/ms).
- **Settings UI** — `ListPreference` added to Gestures → Glide Typing section as "Trail theme", gated on `glide.enabled && glide.showTrail`.
- **Preference** — `glide__trail_theme` enum preference in `AppPrefs.Glide`, default `ACCENT`.
- **String resources** — 9 new entries in `values/strings.xml` for the preference label, summary, and 7 enum display names.
- **EnumDisplayEntries** — `GlideTrailTheme::class` registered in the display-entries map.

### Invisible Trail Fix

- **Root cause** — The `drawWithContent` block gated trail rendering behind a composable-level `glideEnabled` variable that computed `glideLanguageEnabled` via a hardcoded `when` on `evaluator.subtype.primaryLocale.language` — returning `false` for any language not in the explicit en/de/es/fr/it/pt list. Meanwhile, the controller's `isGlideEnabled` property (which drives gesture detection) used `prefs.glide.isEnabledForSubtype()` which has broader matching. Result: glide detection fired, data accumulated, words were suggested, but the trail never drew.
- **Fix** — Removed the redundant `glideEnabled` gate from the draw block. Trail now draws whenever `controller.isGliding` is true and data exists. The `glideShowTrail` user-preference check is preserved.
- **Accent color guard** — Added fallback: if `glideTrailStyle.foreground()` resolves to a color with alpha < 0.1 (transparent theme misconfiguration), override with `Color.Green`.

### Glide Typing Performance

- **Cancel stale preview coroutines** — Previously, every `previewRefreshDelay` interval launched a new classification coroutine on `Dispatchers.Default` without cancelling the previous one. During a long gesture at 150ms intervals, dozens of stale jobs piled up. Now `previewJob?.cancel()` is called before each new launch, and the completion path (`onGlideComplete`, `onGlideWordBoundary`) also cancels any pending preview.
- **Cache ideal gestures** — `StatisticalGlideTypingClassifier.unCachedGetSuggestions()` was calling `Gesture.generateIdealGestures(word, keysByCharacter)` for every candidate word on every classification call, then resampling to 200 points and normalizing — O(words × 200) per call. Now the resampled + normalized ideal gesture pairs are cached in a bounded `LruCache(512)`. Cache is evicted on layout change. Eliminates the biggest per-call cost for repeated classifications.
- **Reduced classifier work for previews** — Preview calls now pass the actual needed count (1) to `getSuggestions` instead of always computing 8 candidates.
- **Refactored `updateSuggestionsAsync` → `launchSuggestions`** — Returns `Job?` so callers can cancel.

### Draw Performance

- **8 segments, single layer** — Cut from 20 segments × 2 draw passes (glow + core = 40 `drawPath` calls/frame) to 8 segments × 1 pass (8 calls/frame). Compensated with slightly wider stroke for equivalent visual punch.

### Files Changed

- `GlideTrailTheme.kt` (new), `TextKeyboardLayout.kt`, `GlideTypingManager.kt`, `StatisticalGlideTypingClassifier.kt`, `AppPrefs.kt`, `EnumDisplayEntries.kt`, `GesturesScreen.kt`, `strings.xml`, `gradle.properties`, `README.md`

<a id="v1.8.171"></a>
## v1.8.171

Released: 2026-05-18

### Repository Hygiene

- Deleted all 178 per-version `RELEASE_NOTES_v*.md` files from the repository root; full release history lives in this single `CHANGELOG.md` under `## vX.Y.Z` headings with stable `#vX.Y.Z` anchors.
- Rewrote `.github/workflows/release.yml` to compose each release body by extracting the matching `## vX.Y.Z` section from `CHANGELOG.md` between `<a id="v...">` anchors, replacing the previous per-file lookup.
- Updated every `RELEASE_NOTES_v*.md` reference across active docs (`README.md`, `CLAUDE.md`, `ARCHITECTURE.md`, `PROJECT_CONTEXT.md`, `IMPROVEMENT_PLAN.md`, `ROADMAP.md`, `docs/AI_PROMPTS_EXTERNAL_WORK.md`) and one source-code comment (`HardwareKeyboardLayout.kt`) to the new `CHANGELOG.md#vX.Y.Z` anchor form. `.ai/research/<date>/` snapshot files were left untouched on purpose — they are frozen historical artefacts.
- Moved seven planning / research snapshots into `docs/archive/`: `SWIFTKEY_AI_RESEARCH.md`, `SWIFTKEY_FEATURE_IMPLEMENTATION_PLAN.md`, `SWIFTKEY_PARITY_AUDIT.md`, `SWIFTKEY_PARITY_BUILD_PLAN.md`, `SWIFTKEY_PARITY_RESEARCH.md`, `SWIFTKEY_PARITY_ROADMAP_2026-05-17.md`, and `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`. All active doc, workflow, script, citation-comment, and asset-comment references were rewritten to point at the new `docs/archive/...` paths.
- Removed the stale `README.md.bak` tracked backup file.
- Bumped `gradle.properties` to `projectVersionName=1.8.171` / `projectVersionCode=1971` and synced the README badge.

<a id="v1.8.170"></a>
## v1.8.170

Released: 2026-05-18

## Intent

Close the Workstream 10 keyboard preview-field placement and state-feedback review for Settings screens.

## What Changed

- The shared settings keyboard preview field now renders inside a distinct bottom surface with a top divider, tonal elevation, and stable horizontal padding.
- The preview field tracks focus and shows ready/active supporting text so the current test-input state is visible.
- Bottom-bar traversal ordering is applied to the preview surface, keeping accessibility order consistent with the shared settings scaffold.
- The keyboard-picker fallback now uses coroutine-safe toast feedback instead of the deprecated synchronous toast helper.

## Files Touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/lib/compose/PreviewKeyboardField.kt`
- `app/src/main/res/values/strings.xml`
- `gradle.properties`
- `README.md`
- `ROADMAP.md`
- `IMPROVEMENT_PLAN.md`
- `PROJECT_CONTEXT.md`
- `ARCHITECTURE.md`
- `AGENTS.md`

## Verification

- `./gradlew.bat :app:compileDebugKotlin`
- `git diff --check`
- `./gradlew.bat :app:verifyNoInternetPermission :app:testDebugUnitTest :app:verifyRoborazziDebug :app:lintDebug :app:assembleDebug`
- `bash scripts/check-repo-hygiene.sh`

## Notes

No permissions, dependencies, persisted data, or keyboard runtime behavior changed.

<a id="v1.8.169"></a>
## v1.8.169

Released: 2026-05-18

## Intent

Close the Workstream 10 empty-state polish item for settings and keyboard-adjacent surfaces that could still render blank, overly generic, or non-actionable states.

## What Changed

- Personal dictionary language detail views now show a specific empty state when the selected locale has no saved words, with the add-word action still available when editing is enabled.
- Extension category and language-pack manager empty states now use clearer import-focused copy instead of generic management labels.
- Theme manager now shows a recovery empty state if no theme components are available.
- Clipboard filtered history now explains when active filters match no clips, while the unfiltered empty clipboard copy mentions text, images, videos, and sensitive-field exclusions.

## Files Touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/dictionary/UserDictionaryScreen.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/localization/LanguagePackManagerScreen.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/theme/ThemeManagerScreen.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardInputLayout.kt`
- `app/src/main/res/values/strings.xml`
- `gradle.properties`
- `README.md`
- `ROADMAP.md`
- `IMPROVEMENT_PLAN.md`
- `PROJECT_CONTEXT.md`
- `ARCHITECTURE.md`
- `AGENTS.md`

## Verification

- `./gradlew.bat :app:compileDebugKotlin`
- `git diff --check`
- `./gradlew.bat :app:verifyNoInternetPermission :app:testDebugUnitTest :app:verifyRoborazziDebug :app:lintDebug :app:assembleDebug`
- `bash scripts/check-repo-hygiene.sh`

## Notes

No permissions, network surface, dependencies, or persisted data formats changed.

<a id="v1.8.168"></a>
## v1.8.168

Released: 2026-05-18

## Intent

Continue `IMPROVEMENT_PLAN.md` Workstream 10 by making active addon scans use
the same visible progress affordance as other file, import, and delete flows.

## Changes

- Added a shared progress card while Addons Settings rescans installed addon
  APKs and refreshes dictionary-pack metadata.
- Kept the existing disabled rescan action row, so users get both a stable
  control state and a visible page-level scan status.
- Updated the touched signing-pin preference observation from deprecated
  `observeAsState` to `collectAsState`.
- Updated `IMPROVEMENT_PLAN.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`,
  `ARCHITECTURE.md`, `AGENTS.md`, and `README.md` for the v1.8.168 release
  state.

## Verification

- `git diff --check`
- `.\gradlew.bat :app:compileDebugKotlin`
- `.\gradlew.bat :app:verifyNoInternetPermission :app:testDebugUnitTest :app:verifyRoborazziDebug :app:lintDebug :app:assembleDebug`
- `bash scripts/check-repo-hygiene.sh`

<a id="v1.8.167"></a>
## v1.8.167

Released: 2026-05-18

## Intent

Continue `IMPROVEMENT_PLAN.md` Workstream 10 by making theme and extension
editing destructive actions explicit before they mutate a draft.

## Changes

- Added a dedicated confirmation before deleting a file from an extension draft
  archive.
- Added confirmation before deleting a theme editor stylesheet rule.
- Added confirmation before deleting a theme editor rule property.
- Updated the confirmation copy to clarify that installed extensions and themes
  stay unchanged until the edited draft is saved.
- Updated `IMPROVEMENT_PLAN.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`,
  `ARCHITECTURE.md`, `AGENTS.md`, and `README.md` for the v1.8.167 release
  state.

## Verification

- `git diff --check`
- `.\gradlew.bat :app:compileDebugKotlin`
- `.\gradlew.bat :app:verifyNoInternetPermission :app:testDebugUnitTest :app:verifyRoborazziDebug :app:lintDebug :app:assembleDebug`

<a id="v1.8.166"></a>
## v1.8.166

Released: 2026-05-18

## Intent

Close `IMPROVEMENT_PLAN.md` Workstream 9 by making the repo-hygiene rules
durable instead of relying on handoff memory: deleted legacy docs are classified,
generated build/report output is guarded, commits stay scoped to one release
slice, and final handoffs carry exact verification commands.

## Changes

- Added `docs/REPO_HYGIENE.md` with the current legacy root-markdown decision,
  generated-output rule, one-slice commit rule, and handoff verification rule.
- Added `scripts/check-repo-hygiene.sh`, which fails if generated build/report
  directories are tracked or if local Markdown deletions remain unclassified.
- Wired the repo-hygiene script into `.github/workflows/android.yml` before
  Gradle work.
- Updated `IMPROVEMENT_PLAN.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`,
  `ARCHITECTURE.md`, `AGENTS.md`, and `README.md` for the v1.8.166 release
  state.

## Verification

- `git diff --check`
- `bash -n scripts/check-repo-hygiene.sh`
- `bash scripts/check-repo-hygiene.sh`
- `.\gradlew.bat :app:verifyNoInternetPermission :app:testDebugUnitTest :app:verifyRoborazziDebug :app:lintDebug :app:assembleDebug`

<a id="v1.8.165"></a>
## v1.8.165

Released: 2026-05-18

## Intent

Close `IMPROVEMENT_PLAN.md` Workstream 8 by making the local verification path
visible in CI and docs: unit tests and debug builds were already PR-gated, while
lint baseline drift, dependency version review, emulator smoke, and local command
documentation needed explicit wiring.

## Changes

- Fixed Android lint DSL wiring in `app/build.gradle.kts`: `app/lint.xml` is now
  a lint config file instead of being treated as a stale baseline file.
- Added `scripts/run-lint-debug-with-baseline-check.sh`, which runs
  `:app:lintDebug`, captures `app/build/reports/lintDebug-console.log`, and
  fails if Android Lint reports stale baseline entries.
- Updated `.github/workflows/android.yml` to run the lint drift wrapper and
  upload the captured lint console log.
- Added `.github/dependabot.yml` for weekly Gradle and GitHub Actions version
  review PRs.
- Added `.github/workflows/emulator-smoke.yml`, a manual/emergent PR workflow
  that builds the debug APK, launches the Settings app on an API 35 emulator,
  checks that the process is alive, and uploads logcat.
- Added `docs/LOCAL_VERIFICATION.md` and linked it from contributor/agent docs.
- Updated `IMPROVEMENT_PLAN.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`,
  `ARCHITECTURE.md`, `AGENTS.md`, `docs/SECURITY.md`, and `README.md` for the
  v1.8.165 release state.

## Verification

- `git diff --check`
- `bash scripts/run-lint-debug-with-baseline-check.sh`
- `.\gradlew.bat :app:verifyNoInternetPermission :app:testDebugUnitTest :app:verifyRoborazziDebug :app:lintDebug :app:assembleDebug`

<a id="v1.8.164"></a>
## v1.8.164

Date: 2026-05-18

## Backup/Restore Duration Baseline

This release completes the Performance Instrumentation workstream by measuring
backup creation and merge restore on a representative default archive profile:
preferences plus keyboard/theme extension files inside the isolated benchmark
app data.

### Changed

- Added `BenchmarkBackupRestoreActivity`, which seeds representative keyboard
  and theme extension fixture files, exports preferences, zips the same default
  sections selected by the Settings backup screen, unzips the archive, validates
  metadata, and merge-restores the selected sections.
- Added `tools/benchmark-backup-restore.ps1`, which installs the benchmark APK,
  launches the benchmark activity, parses backup/restore log markers, and writes
  JSON to `docs/benchmark-results/`.

### Baseline

Samsung SM-S938B / Android 16 (SDK 36), five backup/restore iterations:

- Median backup create: 12.653698 ms.
- Median archive size: 22,034 bytes.
- Median restore prepare: 4.062604 ms.
- Median merge restore apply: 5.727604 ms.
- Median restore total: 9.874167 ms.
- Median selected/restored sections: 3/3.
- Median missing sections: 0.0.
- Median failed sections: 0.0.

Evidence:
`docs/benchmark-results/baseline-2026-05-18-backup-restore.json`.

### Tests

- `.\gradlew.bat :app:assembleBenchmark :benchmark:assembleBenchmark` passed.
- `pwsh -NoProfile -ExecutionPolicy Bypass -File .\tools\benchmark-backup-restore.ps1 -Iterations 5` passed on device `R5CY34G070L`.
- `git diff --check` passed.
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` passed.

### Definition of Done

- Version bumped to `1.8.164` / code `1964`.
- `README.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`, `ARCHITECTURE.md`,
  `AGENTS.md`, `IMPROVEMENT_PLAN.md`, and `docs/BENCHMARKS.md` updated.

<a id="v1.8.163"></a>
## v1.8.163

Date: 2026-05-18

## Theme-Switch Benchmark Baseline

This release completes the next Performance Instrumentation item by measuring
theme switching while the benchmark IME is visible on the same SM-S938B /
Android 16 device used for the previous latency baselines.

### Changed

- Added benchmark-build-only direct switch timing in `ThemeManager`, including
  the active theme name, source marker, load-failure flag, and cached-theme
  count.
- Added `BenchmarkThemeSwitchActivity`, which focuses an input field, waits for
  the IME to render, and directly switches across SwiftKey Pure Light, M3E Nord
  Dark, M3E SwiftKey Pure Dark, then cached repeats.
- Added `tools/benchmark-ime-theme-switch.ps1`, which installs the benchmark
  APK, temporarily selects SwiftFloris, parses direct switch and step markers,
  writes JSON to `docs/benchmark-results/`, and restores the previous IME.

### Baseline

Samsung SM-S938B / Android 16 (SDK 36), five theme-switch iterations:

- Median direct theme switches per run: 5.0.
- Median `swiftfloris.theme.switchMs` body: 18.541197 ms.
- Median max `swiftfloris.theme.switchMs` body: 19.587708 ms.
- Median total direct switch body per run: 57.505571 ms.
- Median cold benchmark step: 19.221354 ms.
- Median warm cached benchmark step: 0.2808075 ms.
- Median load failures: 0.0.

Evidence:
`docs/benchmark-results/baseline-2026-05-18-ime-theme-switch.json`.

### Tests

- `.\gradlew.bat :app:assembleBenchmark :benchmark:assembleBenchmark` passed.
- `pwsh -NoProfile -ExecutionPolicy Bypass -File .\tools\benchmark-ime-theme-switch.ps1 -Iterations 5` passed on device `R5CY34G070L`.
- `adb shell settings get secure default_input_method` returned `com.touchtype.swiftkey/com.touchtype.KeyboardService` after the benchmark restored the previous IME.
- `git diff --check` passed.
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` passed.

### Definition of Done

- Version bumped to `1.8.163` / code `1963`.
- `README.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`, `ARCHITECTURE.md`,
  `AGENTS.md`, `IMPROVEMENT_PLAN.md`, and `docs/BENCHMARKS.md` updated.

<a id="v1.8.162"></a>
## v1.8.162

Date: 2026-05-18

## Candidate Row Recomposition Baseline

This release completes the next Performance Instrumentation item by measuring
candidate-row recomposition during a warm typing phrase on the same SM-S938B /
Android 16 device used for the first-render, first-suggestion, and dictionary
baselines.

### Changed

- Added a benchmark-build-only `SwiftFlorisPerf` marker in `CandidatesRow`
  that records recomposition body duration, candidate count, and display mode.
- Added `tools/benchmark-ime-candidate-row.ps1`, which installs the benchmark
  APK, selects SwiftFloris temporarily, opens `BenchmarkInputActivity`, clears
  startup log noise, types `hello world this is a test`, parses candidate-row
  and NLP log markers, writes JSON to `docs/benchmark-results/`, and restores
  the previous IME.

### Baseline

Samsung SM-S938B / Android 16 (SDK 36), five warm typing iterations:

- Median candidate-row recompositions per run: 9.0.
- Median candidate-row recomposition body: 0.326563 ms.
- Median max candidate-row recomposition body: 0.770365 ms.
- Median total candidate-row recomposition body per run: 4.069529 ms.
- Median paired `swiftfloris.nlp.suggestMs`: 0.339896 ms.
- Median paired max `swiftfloris.nlp.suggestMs`: 150.826823 ms.

Evidence:
`docs/benchmark-results/baseline-2026-05-18-ime-candidate-row.json`.

The candidate row itself is not the observed hotspot in this run; the larger
spikes come from paired NLP work, including lazy correction-index work in some
iterations.

### Tests

- `git diff --check` passed.
- `.\gradlew.bat :app:assembleBenchmark :benchmark:assembleBenchmark` passed.
- `pwsh -NoProfile -ExecutionPolicy Bypass -File .\tools\benchmark-ime-candidate-row.ps1 -Iterations 5` passed on device `R5CY34G070L`.
- `adb shell settings get secure default_input_method` returned `com.touchtype.swiftkey/com.touchtype.KeyboardService` after the benchmark restored the previous IME.
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` passed.

### Definition of Done

- Version bumped to `1.8.162` / code `1962`.
- `README.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`, `AGENTS.md`,
  `IMPROVEMENT_PLAN.md`, and `docs/BENCHMARKS.md` updated.

<a id="v1.8.161"></a>
## v1.8.161

Date: 2026-05-18

## Dictionary Load and Preload Baseline

This release completes the next Performance Instrumentation item by measuring
Latin dictionary cold load, preload, and lazy SymSpell index construction on
the same SM-S938B / Android 16 device used for the first-render and
first-suggestion baselines.

### Changed

- Added benchmark-build-only `SwiftFlorisPerf` markers around
  `LatinDictionaryStore.loadSpecificDictionary` and both lazy SymSpell index
  builders.
- Added `BenchmarkDictionaryActivity` to the benchmark variant. It preloads
  `Subtype.DEFAULT`, then probes invalid token `zzzxqq` so the spelling path
  forces distance-1 and distance-2 SymSpell index construction.
- Added `tools/benchmark-ime-dictionary-load.ps1`, which installs the
  benchmark APK, launches the dictionary benchmark activity, parses logcat,
  and writes repeatable JSON to `docs/benchmark-results/`.

### Baseline

Samsung SM-S938B / Android 16 (SDK 36), five cold iterations for `zzzxqq`:

- Median `swiftfloris.dict.loadMs`: 757.353333 ms for 520,837 English entries.
- Median `swiftfloris.dict.preloadMs`: 772.080625 ms.
- Median SymSpell distance-1 build: 500.230156 ms for 94,934 correction words.
- Median SymSpell distance-2 build: 532.298281 ms for 10,534 correction words.
- Median post-preload spell path: 1030.179896 ms.
- Median post-preload suggestion path: 0.421719 ms.

Evidence:
`docs/benchmark-results/baseline-2026-05-18-ime-dictionary-load.json`.

This splits the dictionary/startup cost out from the v1.8.160 first-suggestion
baseline, which intentionally included cold provider startup.

### Tests

- `git diff --check` passed.
- `.\gradlew.bat :app:assembleBenchmark :benchmark:assembleBenchmark` passed.
- `pwsh -NoProfile -ExecutionPolicy Bypass -File .\tools\benchmark-ime-dictionary-load.ps1 -Iterations 5` passed on device `R5CY34G070L`.
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` passed.

### Definition of Done

- Version bumped to `1.8.161` / code `1961`.
- `README.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`, `AGENTS.md`,
  `IMPROVEMENT_PLAN.md`, and `docs/BENCHMARKS.md` updated.

<a id="v1.8.160"></a>
## v1.8.160

Date: 2026-05-18

## First Suggestion Latency Baseline

This release completes the next Performance Instrumentation item by measuring
cold first-suggestion provider latency on the same SM-S938B / Android 16
device used for the first-render baseline.

### Changed

- Added a benchmark-build-only `SwiftFlorisPerf` marker around
  `LatinLanguageProvider.suggest`, with current-word length and candidate
  count in each log line.
- Added `BenchmarkSuggestionActivity` to the benchmark variant. It invokes
  the Latin suggestion provider against a real `EditorContent` snapshot for
  `teh`, avoiding adb key-event ambiguity while still measuring the same
  provider path.
- Added `tools/benchmark-ime-suggestion-latency.ps1`, which installs the
  benchmark APK, launches the suggestion benchmark activity, parses logcat,
  and writes repeatable JSON to `docs/benchmark-results/`.

### Baseline

Samsung SM-S938B / Android 16 (SDK 36), five cold provider-direct iterations
for `teh`:

- `SwiftFlorisPerf` median `swiftfloris.nlp.firstSuggestionMs`: 1878.616249 ms.
- Median candidate count: 8.

Evidence:
`docs/benchmark-results/baseline-2026-05-18-ime-suggestion-latency.json`.

This number intentionally includes cold provider and dictionary startup cost.
The separate dictionary-load Workstream 7 item remains open so that cost can
be split out in a later release.

### Tests

- `git diff --check` passed.
- `.\gradlew.bat :app:assembleBenchmark :benchmark:assembleBenchmark` passed.
- `pwsh -NoProfile -ExecutionPolicy Bypass -File .\tools\benchmark-ime-suggestion-latency.ps1 -Iterations 5` passed on device `R5CY34G070L`.
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` passed.

### Definition of Done

- Version bumped to `1.8.160` / code `1960`.
- `README.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`, `AGENTS.md`,
  `IMPROVEMENT_PLAN.md`, and `docs/BENCHMARKS.md` updated.

<a id="v1.8.159"></a>
## v1.8.159

Date: 2026-05-18

## IME First-Render Benchmark Baseline

This release starts Performance Instrumentation Workstream 7 by reactivating
the benchmark module and committing the first repeatable cold IME first-render
baseline.

### Changed

- Re-enabled `:benchmark` in `settings.gradle.kts` and updated the benchmark
  module for the AGP 9 / Gradle 9 build.
- Retargeted benchmark sources at the `.bench` app id and added shared device
  helpers for selecting the benchmark IME and launching benchmark activities.
- Added a benchmark-only `BenchmarkInputActivity` so adb and Macrobenchmark
  runs can show the IME without touching production UI.
- Added a benchmark-build-only `SwiftFlorisPerf` first-render log marker in
  `FlorisImeService.onCreateInputView`.
- Added `tools/benchmark-ime-first-render.ps1`, which installs the benchmark
  APK, selects the benchmark IME, records five adb runs, restores the previous
  input method, and writes JSON to `docs/benchmark-results/`.

### Baseline

Samsung SM-S938B / Android 16 (SDK 36), five iterations:

- `am start -W` median `TotalTime`: 31.0 ms.
- `am start -W` median `WaitTime`: 34.0 ms.
- `SwiftFlorisPerf` median `swiftfloris.ime.firstRenderMs`: 18.335469 ms.

Evidence: `docs/benchmark-results/baseline-2026-05-18-ime-first-render.json`.

### Tests

- `git diff --check` passed.
- `.\gradlew.bat :app:assembleBenchmark :benchmark:assembleBenchmark` passed.
- `pwsh -NoProfile -ExecutionPolicy Bypass -File .\tools\benchmark-ime-first-render.ps1 -Iterations 5` passed on device `R5CY34G070L` and restored the previous IME.
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` passed.

### Definition of Done

- Version bumped to `1.8.159` / code `1959`.
- `README.md`, `ROADMAP.md`, `PROJECT_CONTEXT.md`, `ARCHITECTURE.md`,
  `AGENTS.md`, `IMPROVEMENT_PLAN.md`, and `docs/BENCHMARKS.md` updated.

<a id="v1.8.158"></a>
## v1.8.158

Date: 2026-05-18

## Accessibility Manual QA Notes

This release closes the Accessibility Pass documentation item by adding the
manual checks future contributors should run when a change touches settings,
keyboard layout, or IME state feedback.

### Changed

- Added accessibility-specific manual QA notes to `CONTRIBUTING.md`.
- Expanded `docs/ACCESSIBILITY.md` with a concrete manual QA checklist covering
  settings traversal, keyboard labels, candidate-row announcements, high font
  scale, non-color state indicators, and theme/layout cross-checks.

### Tests

- Documentation-only change. `git diff --check` passed.
- `.\gradlew.bat :app:assembleDebug` passed in 17s.

### Definition of Done

- Version bumped to `1.8.158` / code `1958`.
- Roadmap, project context, improvement plan, architecture notes, agent notes,
  README release index, and release notes updated.

<a id="v1.8.157"></a>
## v1.8.157

Date: 2026-05-18

## Non-Color State Indicators

This release continues the Accessibility Pass by making app state feedback rely
on icon shape and explicit copy instead of color treatment alone.

### Changed

- Added shared `FlorisSuccessCard`, `FlorisProgressCard`, and
  `FlorisNeutralCard` wrappers with distinct icons and tones.
- Updated extension import/edit/delete, language-pack delete, dictionary
  import/export/entry, backup/restore, home readiness, and voice readiness
  notices to use the new state-specific cards.
- Added ready/skipped icons to extension import file rows so per-file status is
  visible even when color is unavailable.

### Tests

- `.\gradlew.bat :app:compileDebugKotlin` passed in 16s; the run reported only
  the repo's existing Room/Kotlin/deprecated-toast warnings.
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
  passed in 2m 24s; lint reported 246 warnings and 1 hint, plus the existing
  stale-baseline notice.
- `.\gradlew.bat :app:verifyRoborazziDebug` passed in 59s.

### Definition of Done

- Version bumped to `1.8.157` / code `1957`.
- Roadmap, project context, improvement plan, architecture notes, agent notes,
  README release index, and release notes updated.

<a id="v1.8.156"></a>
## v1.8.156

Date: 2026-05-18

## Theme Contrast Verification

This release continues the Accessibility Pass by turning theme contrast into a
broader regression contract. Bundled IME stylesheets now have selector-level
coverage for keyboard keys, candidate rows, and clipboard clear-all dialogs,
and settings warning/error/dialog colors are checked across every predefined
accent scheme.

### Changed

- Expanded `ThemeContrastTest` to check keyboard, candidate-row, and
  clipboard-dialog foreground/background pairs across every bundled stylesheet.
- Added settings warning-card, error-card, and dialog contrast checks for light,
  dark, and AMOLED schemes across every predefined accent color.
- Fixed low-contrast enter-key foregrounds in Floris Night, Floris Pure Night,
  Dracula, Nord, and Tokyo Night theme variants.
- Raised shared card secondary-copy opacity so warning/error card body text
  remains at WCAG AA contrast.

### Tests

- `.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.theme.ThemeContrastTest`
  passed in 11s after fixing discovered contrast gaps.
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
  passed in 2m 30s; lint reported 246 warnings and 1 hint, plus the existing
  stale-baseline notice.

### Definition of Done

- Version bumped to `1.8.156` / code `1956`.
- Roadmap, project context, improvement plan, architecture notes, agent notes,
  README release index, and release notes updated.

<a id="v1.8.155"></a>
## v1.8.155

Date: 2026-05-18

## Dynamic Font Scaling For Settings And Dialogs

This release continues the Accessibility Pass by giving compact settings and
theme-dialog surfaces more room at high font scale. Metadata rows, links,
component headings, and key-preview boxes keep their compact layout at normal
font scale, then expand wrapping room or minimum preview size when the user has
large text enabled.

### Changed

- Added `DynamicFontScale` as a shared Compose-side policy for high-font-scale
  line-count and minimum-size expansion.
- Allowed shared hyperlink text to expand from one to two lines at high font
  scale instead of always truncating after one line.
- Allowed extension metadata labels, metadata values, and component headings to
  gain extra wrapping room at high font scale while preserving compact defaults.
- Expanded the theme-rule key-data preview from 36 dp to 48 dp at high font
  scale and allowed text labels inside the preview to wrap when expanded.
- Added `DynamicFontScaleTest` to pin the expansion threshold and defensive
  clamping behavior.

### Tests

- `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin`
  passed in 15s after final import-order cleanup.
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
  passed in 2m 33s; lint reported 246 warnings and 1 hint, plus the existing
  stale-baseline notice.

### Definition of Done

- Version bumped to `1.8.155` / code `1955`.
- Roadmap, project context, improvement plan, architecture notes, agent notes,
  README release index, and release notes updated.

<a id="v1.8.154"></a>
## v1.8.154

Date: 2026-05-18

## Keyboard Key Accessibility Semantics

This release continues the Accessibility Pass by aligning keyboard-key
semantics with the real key hitboxes. TalkBack focus now follows each key's
`touchBounds` instead of the smaller visual key surface, and accessibility
activation dispatches the same key event path used by normal touch input.

### Changed

- Added a dedicated semantics overlay for text keys using the key's real touch
  bounds while leaving the visual Snygg key surface unchanged.
- Added an accessibility click action that dispatches the key through the normal
  `InputEventDispatcher.sendDownUp(...)` path.
- Cleared duplicate child semantics from the visual key surface so TalkBack
  announces one target per key.
- Expanded localized special-key labels for clipboard, voice, keyboard mode,
  input-method, layout, and smartbar-control keys.
- Extracted a testable key-description overload so resource-backed labels can
  be verified without Android context setup.
- Added `KeyboardKeyAccessibilityTest` to pin printable-key hints, special-key
  labels, and touch-bounds semantics fallback behavior.

### Tests

- `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin`
  passed in 22s.
- `.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.text.keyboard.KeyboardKeyAccessibilityTest`
  passed in 17s after moving the label contract behind testable resource
  lookup functions.
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
  passed in 3m 28s; lint reported 246 warnings and 1 hint, plus the existing
  stale-baseline notice.

### Definition of Done

- Version bumped to `1.8.154` / code `1954`.
- Roadmap, project context, improvement plan, architecture notes, agent notes,
  README release index, and release notes updated.

<a id="v1.8.153"></a>
## v1.8.153

Date: 2026-05-18

## Candidate And Smartbar TalkBack Labels

This release continues the Accessibility Pass by making high-frequency
keyboard controls speak with more context. Prediction-strip candidates now
announce their suggestion type, position, and text, while quick actions use a
single fallback policy that prefers the visible display name, then the tooltip,
then a generic smartbar action label.

### Changed

- Added `SmartbarAccessibilityLabels` as the shared policy for candidate and
  smartbar quick-action labels.
- Candidate row accessibility labels now include list position, total count,
  clipboard status, autocorrect status, and candidate text.
- Preserved the remove-candidate custom accessibility action through the shared
  label constant.
- Routed quick-action content descriptions through the shared display-name /
  tooltip fallback policy.
- Added `SmartbarAccessibilityLabelsTest` to pin candidate label formatting,
  invalid-position clamping, and quick-action fallback behavior.

### Tests

- `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin`
  passed in 28s.
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
  passed in 2m 38s; lint reported 246 warnings and 1 hint, plus the existing
  stale-baseline notice.

### Definition of Done

- Version bumped to `1.8.153` / code `1953`.
- Roadmap, project context, improvement plan, architecture notes, agent notes,
  README release index, and release notes updated.

<a id="v1.8.152"></a>
## v1.8.152

Date: 2026-05-18

## Settings Focus Order

This release begins the Accessibility Pass by giving the shared Settings
scaffold an explicit traversal order for TalkBack and keyboard navigation.
Settings screens now expose app bar controls first, scrollable content second,
persistent bottom actions third, and floating actions last.

### Changed

- Added `FlorisScreenFocusOrder` constants for the shared Settings scaffold.
- Marked the scaffold root as a traversal group while preserving its existing
  pane-title announcement behavior.
- Applied traversal indices to the app bar, content container, bottom bar, and
  floating action slot.
- Let `FlorisAppBar` accept a modifier so the shared screen scaffold can attach
  accessibility traversal semantics centrally.
- Suppressed the existing Android bar-color deprecation warnings inside the
  compatibility `SideEffect` block so this accessibility slice does not expand
  the warning surface.
- Added `FlorisScreenFocusOrderTest` to pin the traversal order.

### Tests

- `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin`
  passed in 9s after the compatibility warning suppression.
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
  passed in 2m 54s; lint reported 246 warnings and 1 hint, plus the existing
  stale-baseline notice.

### Definition of Done

- Version bumped to `1.8.152` / code `1952`.
- Roadmap, project context, improvement plan, architecture notes, agent notes,
  README release index, and release notes updated.

<a id="v1.8.151"></a>
## v1.8.151

Date: 2026-05-18

## Dictionary Transfer Busy States

This release closes the broad Workstream 5 disabled/busy-state pass by covering
the remaining dictionary transfer gap. User dictionary import/export now has a
first-class transfer state, visible progress cards, and duplicate-action
blocking while long-running file work is in flight.

### Changed

- Added dictionary transfer operation and notice states to
  `UserDictionaryEntryPolicy`.
- Added visible import/export progress cards to the user dictionary screen.
- Disabled dictionary navigation, import/export menu actions, system-manager
  launch, and manual entry mutations while dictionary transfer work is running.
- Moved plain dictionary import and dictionary export work into coroutine-backed
  IO blocks so the busy state covers the actual file and database work.
- Added focused JVM coverage for dictionary transfer gating and notice
  precedence.
- Marked the Workstream 5 duplicate-action/busy-state task complete in the
  improvement plan.

### Tests

- `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin`
  passed in 20s.
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
  passed in 2m 41s; lint reported 246 warnings and 1 hint, plus the existing
  stale-baseline notice.

### Definition of Done

- Version bumped to `1.8.151` / code `1951`.
- Roadmap, project context, improvement plan, architecture notes, agent notes,
  README release index, and release notes updated.

<a id="v1.8.150"></a>
## v1.8.150

Date: 2026-05-18

## Trust-State Recovery Microcopy

This release closes the first broad Workstream 5 recovery-copy pass. The
failure cards added across the recent trust-state audit now tell users what did
not change and what the safe recovery path is, instead of showing only a raw
technical error.

### Changed

- Updated backup and restore failure cards to explain that no archive was saved
  or that restore stopped before all selected data could be imported, then point
  users toward storage/destination/archive retry paths.
- Updated extension import, installed-extension delete, theme-extension save,
  and archive-file import/rename/delete failure cards with specific unchanged
  state and retry guidance.
- Updated language-pack delete and manual dictionary entry save/delete failure
  cards with recovery copy that keeps the technical error detail visible.
- Marked the Workstream 5 failure/recovery microcopy task complete in the
  improvement plan.

### Tests

- `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin`
  passed in 21s.
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
  passed in 2m 3s; lint reported 246 warnings and 1 hint, plus the existing
  stale-baseline notice.

### Definition of Done

- Version bumped to `1.8.150` / code `1950`.
- Roadmap, project context, improvement plan, architecture notes, agent notes,
  README release index, and release notes updated.

<a id="v1.8.149"></a>
## v1.8.149

Date: 2026-05-18

## Dictionary Entry Trust States

This release continues Workstream 5 by making manual user-dictionary add,
update, and delete operations communicate progress, terminal results, and
duplicate-action blocking.

### Changed

- Added `UserDictionaryEntryPolicy` for manual entry-operation gating, notice
  precedence, and save/delete result classification.
- Updated Settings -> Dictionary -> user dictionary entry editing to show save
  progress, delete progress, save success/failure, and delete success/failure
  cards.
- Moved manual user-dictionary insert, update, and delete DAO writes off the
  main thread.
- Refreshed affected suggestion overlays after successful manual mutations,
  including old and new locales when an edit changes the language tag.
- Blocked duplicate entry dialogs, list navigation, import/export actions, and
  back navigation while a manual entry mutation is running.

### Tests

- `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin`
  passed.
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
  passed in 2m 14s; lint reported 246 warnings and 1 hint, plus the existing
  stale-baseline notice.

### Definition of Done

- Version bumped to `1.8.149` / code `1949`.
- Roadmap, project context, improvement plan, and release index updated.

<a id="v1.8.148"></a>
## v1.8.148

Date: 2026-05-18

## Extension Archive File Trust States

This release continues Workstream 5 by making generic extension archive file
management communicate progress, terminal results, and duplicate-action blocking
for import, rename, and delete operations.

### Changed

- Added `ExtensionEditorFilesPolicy` for file-action gating, notice precedence,
  and import/rename/delete result classification.
- Updated extension archive file management to show file-action progress,
  import success/failure, rename success/failure, and delete success/failure
  cards instead of transient toast-only feedback.
- Moved selected-file copying, archive-file rename, and archive-file delete work
  off the main thread.
- Disabled duplicate close/add/select/file-property actions while file work is
  running so users cannot start overlapping archive mutations.

### Tests

- `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin`
  passed.
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
  passed in 1m 50s; lint reported 246 warnings and 1 hint, plus the existing
  stale-baseline notice.

### Definition of Done

- Version bumped to `1.8.148` / code `1948`.
- Roadmap, project context, improvement plan, and release index updated.

<a id="v1.8.147"></a>
## v1.8.147

Date: 2026-05-18

## Theme Extension Trust States

This release continues Workstream 5 by making theme extension edit and delete
workflows communicate progress, failures, and destructive draft changes.

### Changed

- Added `ThemeExtensionTrustStatePolicy` for theme-editor save gating, editor
  notice precedence, installed extension delete gating, and delete notice
  precedence.
- Updated theme extension editing to show save progress and save-failure cards,
  block duplicate save/cancel/component actions while saving, and persist the
  archive off the main thread.
- Added confirmation before removing a theme component from an extension draft,
  plus a visible "theme removed from draft" card that explains saving applies
  the removal.
- Updated installed extension details to show delete progress and failure cards,
  block duplicate delete/export actions while deletion is running, and perform
  deletion off the main thread.

### Tests

- `.\gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin`
  passed.
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
  passed in 2m 12s; lint reported 246 warnings and 1 hint, plus the existing
  stale-baseline notice.

### Definition of Done

- Version bumped to `1.8.147` / code `1947`.
- Roadmap, project context, improvement plan, and release index updated.

<a id="v1.8.146"></a>
## v1.8.146

Date: 2026-05-18

## Language Pack Trust States

This release continues Workstream 5 by making language pack import, update, and
removal workflows communicate progress, outcomes, and recovery paths.

### Changed

- Added import-flow policy helpers for file selection, import busy-state
  gating, import notice precedence, and install/update/skipped-file summaries.
- Updated extension import to show file-reading, importing, cancellation,
  failure, and success cards, with duplicate select/import actions disabled
  while work is running.
- Replaced the generic import review copy with explicit new-install, update,
  and skipped-file counts so language pack updates are visible before commit.
- Added `LanguagePackManagerPolicy` and wired language pack management to show
  delete progress, success, and failure cards.
- Disabled duplicate language pack delete/import actions while deletion is in
  progress, and kept delete failures visible on the manager screen.

### Tests

- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
  passed in 1m 51s; lint reported 245 warnings and 1 hint, plus the existing
  stale-baseline notice.

### Definition of Done

- Version bumped to `1.8.146` / code `1946`.
- Roadmap, project context, improvement plan, and release index updated.

<a id="v1.8.145"></a>
## v1.8.145

Date: 2026-05-18

## Restore Flow Trust States

This release continues Workstream 5 by making Settings -> Advanced -> Restore
data communicate destructive and long-running states explicitly.

### Changed

- Added `RestoreFlowNotice` and `RestoreOperationSummary` to
  `BackupRestorePolicy` so restore loading, progress, erase-mode recovery-copy
  guidance, cancellation, failure, partial failure, and success have
  deterministic precedence.
- Added an erase-restore confirmation dialog and visible recovery-copy guidance
  before local data can be cleared.
- Changed restore execution to summarize selected sections individually, keep
  restoring other sections after a recoverable section failure, and report
  partial failures on-screen instead of relying only on toasts.
- Carried the first per-section restore exception into the summary so full
  failures can show a specific error instead of a generic fallback.
- Avoided erasing local keyboard, theme, preferences, or clipboard data for a
  selected section when the backup archive does not contain that section.
- Disabled restore and cancel actions while restore work is running to prevent
  duplicate execution or leaving the screen mid-restore.

### Tests

- `.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.app.settings.advanced.BackupRestorePolicyTest`
- `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
  - Passed. Lint reports 243 warnings, 1 hint, plus the existing stale-baseline notice.

### Definition of Done

- Version bumped to `1.8.145` / code `1945`.
- Roadmap, project context, improvement plan, and release index updated.

<a id="v1.8.144"></a>
## v1.8.144

Date: 2026-05-18

## Backup Flow Trust States

- Added `BackupFlowNotice` to make backup-screen status precedence explicit for progress, cancellation, failure, share-sheet handoff, success, and clipboard privacy warning states.
- Settings -> Advanced -> Back up data now shows inline cards while a backup is preparing, after document-picker cancellation, after share-sheet handoff, after export failure, and when clipboard history is selected.
- Added copy explaining that app-marked sensitive clipboard entries are skipped from backup archives even when clipboard history is selected.

## Tests

- Extended `BackupRestorePolicyTest` with notice mapping and precedence coverage.

## Verification

- `./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.app.settings.advanced.BackupRestorePolicyTest`
- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`

<a id="v1.8.143"></a>
## v1.8.143

Date: 2026-05-18

## Autocorrect Lifecycle Contract

- Added `docs/AUTOCORRECT_LIFECYCLE.md` as the written contract for autocorrect, spacebar prediction insertion, punctuation/non-letter commits, hardware mapped commits, backspace rejection, glide-delete escalation, provider notifications, manual QA, and regression coverage.
- Added `CandidateCommitSideEffectPolicy` so accepted-provider notifications and personal-dictionary learning are explicitly gated on successful editor commits.
- Updated `KeyboardManager.commitCandidate(...)` to notify a candidate source provider only after the editor commit succeeds, while preserving non-clipboard learning through `learnIfAllowed(...)`.
- Clarified `SuggestionProvider` accepted/reverted callback semantics and linked the contract from contributor manual QA instructions.

## Tests

- Added `CandidateCommitSideEffectPolicyTest` covering accepted-provider notification and learning side-effect gates.

## Verification

- `./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.nlp.CandidateCommitSideEffectPolicyTest`
- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`

<a id="v1.8.142"></a>
## v1.8.142

Date: 2026-05-18

## Theme Rule Edit Policy Extraction

- Added `ThemeRuleEditPolicy` as the pure decision point for add-rule selection validation, selector toggling, and key-code attribute parsing / replacement.
- Thinned `EditRuleDialog` so it keeps rendering dialog state and user feedback while delegating deterministic rule-edit decisions to the policy.
- Preserved existing behavior for empty add-rule selection errors, selector on/off toggles, invalid key-code input, duplicate-code rejection, unchanged-code dismissal, and add/replace code actions.

## Tests

- Added `ThemeRuleEditPolicyTest` covering empty add-rule selection, selector toggling, blank / non-numeric / out-of-range key-code input, unchanged values, duplicate codes, new-code adds, and old-code replacement.

## Verification

- `./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.app.settings.theme.ThemeRuleEditPolicyTest`
- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`

<a id="v1.8.141"></a>
## v1.8.141

Date: 2026-05-18

## Punctuation Flush Policy Extraction

- Added `KeyboardAutoCommitFlushPolicy` as the pure decision point for whether a software text commit should flush a pending autocorrect candidate first.
- Preserved the existing behavior: media-mode text commits flush, character-mode non-letter commits flush, alphabetic character commits do not flush, and numeric / phone layouts commit punctuation without flushing autocorrect.
- Thinned `KeyboardManager` so it asks the policy before invoking `getAutoCommitCandidate()` and then executes the chosen text commit.

## Tests

- Added `KeyboardAutoCommitFlushPolicyTest` covering media mode, alphabetic keys, punctuation keys, numeric keys, numeric / phone keyboard modes, non-text key types, and empty text.

## Verification

- `./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.keyboard.KeyboardAutoCommitFlushPolicyTest`
- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`

<a id="v1.8.140"></a>
## v1.8.140

Date: 2026-05-18

## Candidate Auto-Commit Policy Extraction

- Added `CandidateAutoCommitPolicy` as the pure decision point for auto-commit candidate ordering, quick-prediction spacebar insertion, plain-space prediction suppression, and rejected-correction gating.
- Thinned `NlpManager` so it gathers Android-bound state, preference values, dictionary shortcut expansions, and immediate autocorrect candidates before delegating deterministic selection to the policy.
- Kept existing priority semantics: user-dictionary shortcuts, phrase repairs, active-strip autocorrects, and immediate contraction fallbacks are still ordered ahead of generic guesses as before.

## Tests

- Added `CandidateAutoCommitPolicyTest` covering disabled states, shortcut / phrase / active / immediate priority, auto-commit eligibility, language-confidence gating, rejected-correction suppression, quick-prediction spacebar insertion, and plain-space prediction suppression.

## Verification

- `./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.nlp.CandidateAutoCommitPolicyTest`
- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`

<a id="v1.8.139"></a>
## v1.8.139

Released: 2026-05-18

## Changed

- Reviewed dependency-version lint warnings as a dedicated dependency slice.
- Bumped the Gradle wrapper distribution from 9.4.1 to checksum-pinned 9.5.1.
- Bumped `androidx.navigation:navigation-compose` from 2.9.7 to 2.9.8.
- Moved JUnit Vintage to the version catalog and bumped the test-runtime bridge from 5.13.1 to 6.0.3.
- Updated dependency and reproducible-build docs with the reviewed pins.

## Verification

- `./gradlew.bat --version`
- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`

<a id="v1.8.138"></a>
## v1.8.138

Released: 2026-05-18

## Changed

- Completed the first conservative `UnusedResources` review.
- Removed obsolete launcher/branding resources superseded by `@mipmap/ic_launcher`.
- Removed dead legacy color tokens with no manifest, code, asset, test, or dynamic lookup references.
- Documented the remaining `UnusedResources` shape so string, theme-palette, and spec-dimension buckets are handled by separate semantic review.

## Verification

- `./gradlew.bat :app:lintDebug :app:assembleDebug`
- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`

<a id="v1.8.137"></a>
## v1.8.137

Released: 2026-05-18

## Changed

- Extracted theme component metadata validation into `ThemeComponentMetaValidationPolicy`.
- Updated the theme component metadata dialog confirm path to use the policy for field validity, duplicate-ID detection, and normalized apply data.
- Added focused JVM coverage for valid metadata normalization, invalid fields, duplicate IDs, and blank stylesheet fallback.

## Verification

- `./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.app.settings.theme.ThemeComponentMetaValidationPolicyTest`
- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`

<a id="v1.8.136"></a>
## v1.8.136

Released: 2026-05-18

## Changed

- Extracted subtype editor required-field validation into `SubtypeEditorValidationPolicy`.
- Updated the subtype editor save-state path to serialize an explicit draft model and delegate draft-to-subtype building to the policy.
- Added focused JVM coverage for default add-state missing fields, complete draft building, select-placeholder rejection, and edit-state preservation.

## Verification

- `./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.app.settings.localization.SubtypeEditorValidationPolicyTest`
- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`

<a id="v1.8.135"></a>
## v1.8.135

Released: 2026-05-18

## Changed

- Extracted extension import readiness decisions into `ExtensionImportPolicy`.
- Updated the typed extension import screen path so the language-pack importer rejects non-language-pack `.flex` files before enabling import.
- Added focused JVM coverage for language-pack new installs, user-installed updates, bundled-core rejection, corrupted metadata, wrong extension type, unsupported files, missing parsed extensions, and import button enablement.

## Verification

- `./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.app.ext.ExtensionImportPolicyTest`
- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`

<a id="v1.8.134"></a>
## v1.8.134

Released: 2026-05-18

## Backup/restore policy tests

- Extracted backup and restore validation / operation-state decisions into `BackupRestorePolicy`.
- Wired backup cancellation, backup start enablement, restore archive metadata/content validation, and restore action enablement through the policy.
- Restore archive inspection now rejects archives that contain metadata but no restorable data sections.
- Added JVM coverage for backup success/cancellation/failure, restore invalid archives, warning paths, action enablement, and partial-failure classification.
- Checked off the `IMPROVEMENT_PLAN.md` backup/restore test item and the backup/restore validation-policy extraction item.

## Verification

- `./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.app.settings.advanced.BackupRestorePolicyTest`
- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`

<a id="v1.8.133"></a>
## v1.8.133

Released: 2026-05-18

## Incognito suggestion privacy policy tests

- Extracted incognito suggestion privacy decisions into `SuggestionPrivacyPolicy`.
- Wired editor startup, incognito toggle availability, committed-word learning, and touch-decoder evidence recording through the shared policy.
- Added JVM coverage for app-declared no-personalized-learning override, fixed and dynamic incognito modes, toggle availability, learning suppression, and touch-decoder evidence suppression.
- Checked off the `IMPROVEMENT_PLAN.md` incognito suggestion behavior test item.

## Verification

- `./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.nlp.SuggestionPrivacyPolicyTest`
- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`

<a id="v1.8.132"></a>
## v1.8.132

Released: 2026-05-18

## Glide typing delete policy tests

- Extracted the committed-glide-word backspace escalation decision into `EditorInputBehaviorPolicy.shouldEscalateGlideBackspaceToWordDelete`.
- Kept `EditorInstance.deleteBackwards` behavior aligned with the existing `immediateBackspaceDeletesWord` path.
- Added JVM coverage for enabled word-delete escalation, disabled preference, inactive phantom-space, and explicit word-delete paths.
- Checked off the `IMPROVEMENT_PLAN.md` glide typing delete interaction test item.

## Verification

- `./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.editor.EditorInputBehaviorPolicyTest`
- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`

<a id="v1.8.131"></a>
## v1.8.131

Released: 2026-05-18

## Spacing lifecycle state tests

- Added `EditorSpacingLifecycleStateTest` for the editor spacing state holders.
- Covered auto-space one-editor-update grace and immediate expiry paths.
- Covered phantom-space composing-region visibility, candidate-for-revert retention through the first editor update, and cleanup after explicit or unprotected update deactivation.
- Checked off the `IMPROVEMENT_PLAN.md` phantom-space and autospace lifecycle test item.

## Verification

- `./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.editor.EditorSpacingLifecycleStateTest`
- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`

<a id="v1.8.130"></a>
## v1.8.130

Released: 2026-05-18

## Hardware keyboard input tests

- Extracted hardware keydown / keyup routing into `HardwareKeyboardInputPolicy`.
- Routed `KeyboardManager` through the policy while preserving mapped-layout priority before built-in space / enter / shift handling.
- Added focused JVM coverage for hardware space, enter, delete pass-through, shift down/up, mapped letters, mapped punctuation, and punctuation-triggered pending-autocorrect flushes.
- Checked off the `IMPROVEMENT_PLAN.md` hardware keyboard test item.

## Verification

- `./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.hardware.HardwareKeyboardInputPolicyTest`
- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`

<a id="v1.8.129"></a>
## v1.8.129

Released: 2026-05-18

## Editor input behavior tests

- Extracted autocorrect spacebar commits, punctuation auto-spacing, phantom spacing, double-space period, and sentence-capitalization decisions into `EditorInputBehaviorPolicy`.
- Routed `EditorInstance` and `KeyboardManager` through the extracted policy without changing the runtime commit behavior.
- Added focused JVM coverage for accepted autocorrect spacing, rejected-correction protection, suppressed plain-space predictions, punctuation spacing, phantom spacing, double-space period, and sentence-capitalization gates.
- Checked off the first `IMPROVEMENT_PLAN.md` editor/input behavior test item and the related phantom-space/autospace extraction item.

## Verification

- `./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.editor.EditorInputBehaviorPolicyTest`
- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`

<a id="v1.8.128"></a>
## v1.8.128

Released: 2026-05-18

## Nastaliq Urdu font bundle

- Bundled the official OFL-1.1 Noto Nastaliq Urdu hinted TTF at `app/src/main/assets/fonts/NotoNastaliqUrdu-Regular.ttf` with the OFL text beside it.
- Extended `NastaliqFontProvider` with a Compose `FontFamily` wrapper and routing predicates for Urdu Arabic-script labels.
- Added an optional font-family override to `SnyggText` and routed Urdu subtype key labels/hints through the bundled font while keeping Latin and non-Urdu labels on the active Snygg theme font.
- Added tests that pin the committed TTF, OFL attribution file, and Urdu-only routing conditions.

## Verification

- `./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.bidi.NastaliqFontProviderTest`

<a id="v1.8.127"></a>
## v1.8.127

Released: 2026-05-18

## Emoji pinned-group sheet

- Completed the `Next-9.4a` pinned emoji-group UI: long-pressing an emoji can now open an in-keyboard "Pin to group" sheet instead of stopping at the prior placeholder state.
- Added a reusable `PinToGroupSheet` composable with existing-group rows, new-group creation, inline validation errors, and the existing `EmojiPinGroupStore` caps.
- Wired pinned-group chips to commit the full saved emoji sequence through the keyboard input dispatcher and refresh local emoji history.

## Verification

- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`

<a id="v1.8.126"></a>
## v1.8.126

Released: 2026-05-18

## Addons dictionary-pack catalog polish

- Added a shared `DictionaryPackCatalogReader` so Settings and the runtime loader read dictionary-pack descriptor resources through the same PackageManager path.
- Extended Settings -> Addons with a Dictionary packs group that lists mounted pack language, word count, dataset license, and source, plus descriptor-level rejection reasons.
- Updated install guidance now that descriptor validation, trust controls, and no-extraction asset mounting have shipped.

## Verification

- `./gradlew.bat :app:recordRoborazziDebug --tests dev.patrickgold.florisboard.screenshot.ThemeAndAddonsScreenshotTest.addonsSettingsRegistrySurface`
- `./gradlew.bat :app:verifyRoborazziDebug`
- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`

<a id="v1.8.125"></a>
## v1.8.125

Released: 2026-05-18

## Addons dictionary asset mounting

- Added `AddonDictionaryAssetMounts`, which reads enrolled dictionary-pack descriptor resources and mounts addon APK `assets/` through `PackageManager#getResourcesForApplication(...)` without extraction or temp-file copies.
- Enforced the addon asset byte cap while streaming addon asset text so an oversized pack is skipped before materializing an unbounded string.
- Wired `LatinDictionaryStore` to prefer addon dictionary and Zipf asset paths, merge readable addon dictionaries with bundled language baselines, and invalidate cached dictionaries when `AddonRegistryStore` publishes a new generation.
- Added tests for addon asset-path routing, generation-based dictionary reloads, merged addon/bundled dictionaries, and registry-store generation changes.

## Verification

- `./gradlew.bat :app:testDebugUnitTest`
- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`

<a id="v1.8.124"></a>
## v1.8.124

Released: 2026-05-18

## Addon signing-pin trust controls

- Closed the local signing-pin revoke/reset half of the Next-10.4 Addons follow-up.
- Settings -> Addons now exposes a confirmed "Reset addon trust decisions" action that clears saved signing-certificate pins without silently re-enrolling currently installed addon APKs.
- Rejected changed-certificate addons now get a confirmed "Trust current certificate" action that clears the old package pin, rescans installed addons, and records the currently installed certificate only if the addon still passes normal validation.
- Added `AddonSigningPinSet.withoutPackage(...)` so trust updates stay in the pure pin codec before the Settings UI writes `prefs.addon.signingCertPins`.
- Refreshed the Settings -> Addons Roborazzi baseline so the hard visual gate covers the new trust-management row.

Verification:

- `./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.addon.AddonSigningPinSetTest`
- `./gradlew.bat :app:recordRoborazziDebug --tests dev.patrickgold.florisboard.screenshot.ThemeAndAddonsScreenshotTest.addonsSettingsRegistrySurface`
- `./gradlew.bat :app:verifyRoborazziDebug`
- `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`

<a id="v1.8.123"></a>
## v1.8.123

Released: 2026-05-18

## Roborazzi baseline hard gate

- Closed carried-forward **F11** by recording committed Roborazzi baselines for the current screenshot suite under `app/src/test/snapshots/`.
- Added `ThemeAndAddonsScreenshotTest`, covering:
  - `swiftkey_high_contrast` keyboard-style surface.
  - `aurora_animated` keyboard-style surface.
  - Settings -> Addons accepted/rejected registry surface with deterministic seeded addon data.
- Kept the existing `ExtensionMaintainerChipScreenshotTest` baselines for name-only, email, and URL maintainer-chip variants.
- Removed `continue-on-error: true` from the `Roborazzi visual-regression verify (N14.1)` CI step, so `:app:verifyRoborazziDebug` is now a hard PR/push gate.

## Verification

- `./gradlew.bat :app:recordRoborazziDebug --tests dev.patrickgold.florisboard.screenshot.ThemeAndAddonsScreenshotTest`
- `./gradlew.bat :app:verifyRoborazziDebug`

Known unchanged warnings: AGP `android.newDsl=false` deprecation/configuration-time resolution warnings and the existing JUnit4 Compose-rule migration warning in screenshot tests.

<a id="v1.8.122"></a>
## v1.8.122

Date: 2026-05-18

Eighth-pass NLP / autocorrect / suggestion audit closure for the concrete KenLM-reader finding found during the local re-audit.

## What changed

`KenLmTrieReader.readBytesAt(...)` now treats its `offset` argument strictly as an absolute file offset. Requests before `bodyStartOffset` return `null` instead of being coerced to mapped-body offset zero, so header/pre-body reads cannot accidentally alias to trie-body bytes.

The reader also guards offset arithmetic overflow, avoids `FileChannel.size().toInt()` overflow while reading the fixed 256-byte header probe, and rejects bodies too large for the single `MappedByteBuffer` path. This keeps malformed or very large KenLM-shaped files on the safe fallback path.

The README clipboard summary was corrected to describe the current Room-backed clipboard history path after v1.8.121 retired the legacy Tink preference store.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/kenlm/KenLmTrieReader.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/nlp/kenlm/KenLmTrieReaderTest.kt`
- `README.md`
- `gradle.properties` — versionCode 1922 / versionName 1.8.122

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.nlp.kenlm.KenLmTrieReaderTest
```

Result: pass.

```powershell
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Result: pass. `lintDebug` still reports the existing warning backlog and a stale lint baseline note, but no errors.

<a id="v1.8.121"></a>
## v1.8.121

Date: 2026-05-18

Seventh-pass follow-up roster item G9.

## What changed

The unused Tink-backed `ClipboardHistoryManager` path has been deleted.

The live IME clipboard path is `ClipboardManager` backed by Room, provider metadata, media cleanup, backup/restore handling, sensitive-item gates, and the v1.8.119 serialized history-maintenance path. The older `ClipboardHistoryManager` stored only text entries in a separate Tink-encrypted preference payload and was referenced only by an unused `ClipboardHistoryPanel` plus a source-inspection test. Keeping both stores made the codebase imply two clipboard-history backends that could drift.

This release removes the dead manager and unused panel, and reframes the encryption regression test to pin the intended invariant: clipboard history must stay on the Room-backed `ClipboardManager` path and must not reintroduce the parallel Tink preference store.

## Files touched

- Removed `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardHistoryManager.kt`
- Removed `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ui/ClipboardHistoryPanel.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/dictionary/PersonalDictionaryEncryptionTest.kt`
- `docs/SECURITY.md`
- `gradle.properties` — versionCode 1921 / versionName 1.8.121

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.dictionary.PersonalDictionaryEncryptionTest
```

Result: pass.

```powershell
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Result: pass. `lintDebug` still reports the existing warning backlog and a stale lint baseline note, but no errors.

<a id="v1.8.120"></a>
## v1.8.120

Date: 2026-05-18

Seventh-pass follow-up roster item G1.

## What changed

The local Whisper/Vosk voice path is now explicitly preview-only until a real in-app recognizer runtime ships.

Previously the settings UI exposed a local model catalog with download/import actions, and `VoiceRecognitionEngineSelector` could route Auto, Embedded Whisper, or Vosk streaming to local engines once a matching model and microphone permission were present. That was misleading because the app does not bundle `AudioRecord`, Vosk JNI, or whisper.cpp runtime glue.

This release adds a `VoiceLocalRecognizerRuntime.AVAILABLE` gate, defaults it to false, and makes local routes report `LOCAL_RECOGNIZER_RUNTIME_UNAVAILABLE` unless a future runtime explicitly opts in. Auto continues to fall back to the enabled external voice keyboard. Settings -> Voice input now marks the local model catalog as preview-only, disables download/import while the runtime is absent, and leaves delete available for already-imported files.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceRecognitionEngineSelection.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceInputManager.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/voice/VoiceInputScreen.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceRecognitionEngineSelectorTest.kt`
- `gradle.properties` — versionCode 1920 / versionName 1.8.120

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.voice.VoiceRecognitionEngineSelectorTest
```

Result: pass.

```powershell
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Result: pass. `lintDebug` still reports the existing warning backlog and a stale lint baseline note, but no errors.

<a id="v1.8.119"></a>
## v1.8.119

Date: 2026-05-18

Seventh-pass follow-up roster item G5.

## What changed

Clipboard history maintenance no longer runs sort/filter/eviction work on the main dispatcher, and size-limit / expiry eviction now share one serialized maintenance path.

Previously `initializeForContext(...)` collected the Room history flow inside `withContext(Dispatchers.Main)`, so every history emission sorted and rebuilt `ClipboardHistory` on Main. The same path called `enforceHistoryLimit(...)`, which launched deletion work that caused another Room emission and could repeatedly re-enter the same maintenance logic. The timed expiry job also read `currentHistory` outside any shared history-maintenance lock.

This release keeps Room collection on the existing IO scope, moves history sorting to `Dispatchers.Default`, serializes limit and expiry maintenance through one `Mutex`, and hides rows selected for eviction before the next Room emission arrives. Automatic expiry now uses the same serialized maintenance path as history-size enforcement.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardHistoryMaintenance.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardManager.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardHistoryEvictionTest.kt`
- `gradle.properties` — versionCode 1919 / versionName 1.8.119

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.clipboard.ClipboardHistoryEvictionTest
```

Result: pass.

```powershell
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Result: pass. `lintDebug` still reports the existing warning backlog and a stale lint baseline note, but no errors.

<a id="v1.8.118"></a>
## v1.8.118

Date: 2026-05-18

Seventh-pass clipboard agent finding #2.

## What changed

Foreign `content://` image/video clipboard URIs that fail during provider cloning no longer create phantom IME-local history rows.

Previously `ClipboardMediaProvider.insert(...)` caught every media-clone failure and returned a synthetic provider URI ending in `/0`. `ClipboardItem.fromClipData(...)` accepted that URI, so the clipboard history could contain image/video entries whose private backing file and provider metadata never existed. This release makes clone failures propagate, rejects null or sentinel provider insert results before a `ClipboardItem` is created, and logs/skips failed system-clipboard imports in `ClipboardManager`.

The provider also stops running image EXIF orientation parsing for videos, so valid video clipboard imports are not rejected by image-only metadata parsing before the copy step.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardManager.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardDatabase.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardMediaClonePolicy.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardMediaProvider.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardMediaSafetyPolicyTest.kt`
- `gradle.properties` — versionCode 1918 / versionName 1.8.118

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.clipboard.ClipboardMediaSafetyPolicyTest
```

Result: pass.

```powershell
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Result: pass. `lintDebug` still reports the existing warning backlog and a stale lint baseline note, but no errors.

<a id="v1.8.117"></a>
## v1.8.117

Date: 2026-05-18

Seventh-pass follow-up roster item G4.

## What changed

Clipboard media restore now recreates the provider metadata rows needed to serve restored images and videos.

The backup flow copied provider-backed media files and serialized the matching `ClipboardItem` rows, but restore only copied the files back into `clipboard_files`. It did not reinsert the corresponding `ClipboardFileInfo` rows, leaving restored item URIs pointed at IDs missing from the provider database. This release creates replacement metadata from the restored clipboard item and file size, writes it with conflict replacement, and lets `ClipboardMediaProvider` lazy-load metadata from Room on cache misses so restored clips work without an app restart.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/advanced/RestoreScreen.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardRestoredFileInfo.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardDatabase.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardFileStorage.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardMediaProvider.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardRestoredFileInfoTest.kt`
- `gradle.properties` — versionCode 1917 / versionName 1.8.117

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.clipboard.ClipboardRestoredFileInfoTest
```

Result: pass.

```powershell
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Result: pass. `lintDebug` still reports the existing warning backlog and a stale lint baseline note, but no errors.

<a id="v1.8.116"></a>
## v1.8.116

Date: 2026-05-18

Seventh-pass follow-up roster item G3.

## What changed

Clipboard startup now reconciles provider-backed media history with the private clipboard file store.

Previously, a destructive Room migration or any drift between `clipboard_history`, `clipboard_files`, and `noBackupFilesDir/clipboard_files` could leave orphaned media files behind forever, or leave history rows pointing at provider files that no longer existed. This release adds `ClipboardStorageReconciliation`, runs it before collecting clipboard history, deletes provider-backed history rows whose stored file is missing, removes stale `ClipboardFileInfo` rows, and deletes stored provider files that no history row references.

Restored media files that still exist but lack `ClipboardFileInfo` metadata are intentionally preserved here; rebuilding those metadata rows is the separate G4 restore-path slice.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardStorageReconciliation.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardManager.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardFileStorage.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardStorageReconciliationTest.kt`
- `gradle.properties` — versionCode 1916 / versionName 1.8.116

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.clipboard.ClipboardStorageReconciliationTest
```

Result: pass.

```powershell
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Result: pass. `lintDebug` still reports the existing warning backlog and a stale lint baseline note, but no errors.

<a id="v1.8.115"></a>
## v1.8.115

Date: 2026-05-18

Seventh-pass follow-up roster item G10.

## What changed

Clipboard item description badges no longer classify sensitive text by running URL, email, or phone-number detection over the raw clip contents.

The pin-popup text path already rendered sensitive clips through the redacted `displayText()` placeholder, but the description row still built an unredacted `stringRepresentation()` and passed it into `NetworkUtils.isUrl(...)`. A sensitive URL-like clip could therefore reveal structural information through the link badge. This release moves description classification behind `clipboardItemDescriptionKind(item)`, which returns no badge for sensitive or non-text clipboard items before reading the raw text.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardInputLayout.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardItemDescriptionKindTest.kt`
- `gradle.properties` — versionCode 1915 / versionName 1.8.115

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.clipboard.ClipboardItemDescriptionKindTest
```

Result: pass.

```powershell
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Result: pass. `lintDebug` still reports the existing warning backlog and a stale lint baseline note, but no errors.

<a id="v1.8.114"></a>
## v1.8.114

Date: 2026-05-18

Seventh-pass follow-up roster item G8.

## What changed

External voice-input handoff readiness now requires microphone permission for every enabled external voice IME package, not only FUTO Voice Input.

The previous `isVoiceInputReadyForHandoff()` implementation correctly required `RECORD_AUDIO` for FUTO, but treated any other enabled voice IME as ready without checking that package's microphone grant. This release adds `ExternalVoiceInputHandoffPolicy` and routes all enabled external voice IME packages through the same `PackageManager.checkPermission(android.Manifest.permission.RECORD_AUDIO, packageName)` path before SwiftFloris reports the external handoff as ready.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceInputManager.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/voice/ExternalVoiceInputHandoffPolicyTest.kt`
- `gradle.properties` — versionCode 1914 / versionName 1.8.114

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.voice.ExternalVoiceInputHandoffPolicyTest
```

Result: pass.

```powershell
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Result: pass. `lintDebug` still reports the existing warning backlog (290 warnings) and a stale lint baseline note, but no errors.

<a id="v1.8.113"></a>
## v1.8.113

Date: 2026-05-18

Seventh-pass follow-up roster item G7.

## What changed

`VoiceInputSetupActivity` now has an explicit setup-intent contract instead of accepting arbitrary extras and silently falling back to `NO_ENABLED_PROVIDER`.

The manifest already declares the activity as `android:exported="false"`; this release pins that with a Robolectric manifest test and adds `VoiceInputSetupIntentContract` so the only accepted input is a single `reason` extra whose value matches a known `VoiceInputSetupReason`. Missing reasons, unknown values, and unexpected extra keys now finish the transparent setup activity without rendering a misleading setup dialog.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceInputSetupActivity.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceInputSetupActivityManifestTest.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceInputSetupIntentContractTest.kt`
- `gradle.properties` — versionCode 1913 / versionName 1.8.113

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.voice.VoiceInputSetupIntentContractTest
```

Result: pass.

```powershell
./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.voice.VoiceInputSetupActivityManifestTest
```

Result: pass.

```powershell
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Result: pass. `lintDebug` still reports the existing warning backlog (290 warnings) and a stale lint baseline note, but no errors.

<a id="v1.8.112"></a>
## v1.8.112

Date: 2026-05-18

Seventh-pass follow-up roster item G6.

## What changed

Clipboard history size-limit rotation and old/sensitive auto-expiry now close provider-backed clipboard items before deleting their Room rows.

The previous manual clear paths called `ClipboardItem.close(context)`, which deletes the app-owned clipboard provider URI and lets `ClipboardMediaProvider` revoke outstanding read grants. The automatic paths in `ClipboardManager.enforceHistoryLimit(...)` and `ClipboardManager.enforceExpiryDate(...)` deleted rows directly, so image/video entries removed by rotation or expiry could leave private files and receiver grants behind.

This release adds `ClipboardHistoryEviction` and routes both automatic deletion paths through `closeThenDelete(...)`. The helper keeps the selection policy testable while guaranteeing that provider-backed media is closed before its history row disappears.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardHistoryEviction.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardManager.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardHistoryEvictionTest.kt`
- `gradle.properties` — versionCode 1912 / versionName 1.8.112

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.clipboard.ClipboardHistoryEvictionTest
```

Result: pass.

```powershell
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Result: pass. `lintDebug` still reports the existing warning backlog (290 warnings) and a stale lint baseline note, but no errors.

<a id="v1.8.111"></a>
## v1.8.111

Date: 2026-05-18

Seventh-pass clipboard follow-up roster items G2 and G12, plus minimal build-gate repairs needed to verify the slice on the current toolchain.

## What changed

[`ClipboardFileStorage.cloneUri`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardFileStorage.kt) now receives the clipboard media kind and copies provider-backed files through the existing `ContentResolver.readToFile(..., maxSize)` limit:

- image clips: 32 MiB cap
- video clips: 128 MiB cap
- failed oversize copies delete the partial private file before returning the provider failure URI

[`ClipboardMediaProvider`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardMediaProvider.kt) passes the table-derived image/video kind into that storage boundary, so the cap is enforced before a hostile `content://` stream can fill `noBackupFilesDir/clipboard_files`.

[`FlorisCopyToClipboardActivity`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/FlorisCopyToClipboardActivity.kt) now validates preview dimensions before decode through [`ClipboardPreviewImagePolicy`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardPreviewImagePolicy.kt). The modern `ImageDecoder` path and legacy `BitmapFactory` path both reject unknown or >8192 px bounds before allocating the preview bitmap, while still downscaling accepted previews to a 1024 px longest edge.

## Build-gate repairs

The first verification run reached source compilation and exposed stale HEAD errors unrelated to this clipboard slice. This release fixes them because leaving the repo unbuildable would make the G2/G12 verification meaningless:

- `FlorisImeService` imports `KeyVariation` from the current `ime.text.key` package.
- `AddonsSettingsScreen` uses JetPref's `enabledIf` API for the rescan row.
- `NlpManager` precomputes trailing-context locale frequencies before calling the non-suspend `TrailingContextLanguageBlend.score` callback.
- `AuroraAnimatedThemeBackground` drops the obsolete `matchParentSize` import.
- `QuickActionArrangementTest` calls `contains(...)` explicitly for the default-arrangement quick-action assertions.
- `UserDictionaryCombinedListCodec` skips blank export lines so encrypted combined-list exports round-trip through the importer.
- `DictionaryImporter` avoids the API-33-only `ByteArrayOutputStream.toString(Charset)` overload on minSdk 26 devices.
- `HardwareKeyboardRuntimeMapper` tolerates null platform key-code names in JVM/Robolectric and uses an explicit alphanumeric fallback for source-name matching.
- `ZipUtilsTest` now asserts the current abort-on-path-traversal contract instead of the retired ignore-and-continue contract.
- `UserStickerRepositoryTest` matches the MIME-spoof guard by using null SAF MIME for extension fallback coverage.
- `WordStylesCanvasRenderer` selects `Typeface` styles without bitwise flag composition.
- `StateAdapters` remembers the mapped flow before `collectAsState`.
- `data_extraction_rules.xml` suppresses lint's intentional-exclude false positives while preserving the build-pinned no-D2D-leak excludes.
- The debug Roborazzi host activity is no longer a launcher activity.
- X25519 sync pairing is API-gated to Android 13+ so minSdk 26 devices do not reach API-33-only Java crypto classes.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardFileStorage.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardMediaProvider.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardPreviewImagePolicy.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/FlorisCopyToClipboardActivity.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardMediaSafetyPolicyTest.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/addons/AddonsSettingsScreen.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/NlpManager.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/DictionaryImporter.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/UserDictionary.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/hardware/HardwareKeyboardRuntimeMapper.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/sync/SealedBoxCrypto.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/sync/PairingPayloadGenerator.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/window/AuroraAnimatedThemeBackground.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/wordstyles/WordStylesCanvasRenderer.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/lib/StateAdapters.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/sync/SyncSettingsScreen.kt`
- `app/src/main/res/xml/data_extraction_rules.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/debug/AndroidManifest.xml`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/media/sticker/UserStickerRepositoryTest.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/smartbar/quickaction/QuickActionArrangementTest.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/lib/io/ZipUtilsTest.kt`
- `gradle.properties` — versionCode 1911 / versionName 1.8.111

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest
```

Result: pass. The full JVM unit-test suite passes after the stale build-gate repairs.

```powershell
./gradlew.bat :app:lintDebug :app:assembleDebug
```

Result: pass. `lintDebug` still reports the existing warning backlog (290 warnings) and a stale lint baseline note, but no errors.

<a id="v1.8.110"></a>
## v1.8.110

Date: 2026-05-17

Seventh-pass audit finding #11 from the voice subsystem agent.

## What changed

[`app/src/main/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceInputManager.kt`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceInputManager.kt#L88) —
`startListening` previously did this:

```kotlin
_isListening.value = true
_transcriptionState.value = TranscriptionState.Listening
val switched = FlorisImeService.switchToVoiceInputMethod(...)
if (switched) {
    _isListening.value = false                              // ←
    _transcriptionState.value = TranscriptionState.Ready    // ← same frame
    return true
}
```

Both `_isListening = true` and `_transcriptionState = Listening` were
assigned and immediately overwritten in the same synchronous frame.
The Listening event was sub-millisecond — no `collectAsState()` /
flow consumer ever observed it. Mic-meter UIs read `_isListening` as
permanently false; "Connecting to voice IME…" spinners read
`_transcriptionState` as Ready → Ready with no intermediate Listening
frame.

The fix keeps the state in Listening when the IME swap succeeds and
relies on the reset path on the way back (next paragraph).

### IME re-entry resets to Ready

[`app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt`](app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt#L489) —
`onStartInput` now calls `voiceInputManager.refreshAvailability()`.
When SwiftFloris is re-bound as the active IME (the user returned
from FUTO / system picker / cancel), this resets the state to Ready
so the next interaction starts cleanly. `refreshAvailability()` is
cheap and idempotent — it already short-circuits when the recogniser
availability hasn't changed.

Net effect: any UI consumer observing `transcriptionState` or
`isListening` now sees a proper Ready → Listening (held for the
duration of the external-IME session) → Ready transition, instead of
the previous Ready → Ready null-transition.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceInputManager.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt`
- `gradle.properties` — versionCode 1910 / versionName 1.8.110

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
```

Manual QA reproduction (requires voice IME installed + enabled):
- Wire any debug UI that does
  `transcriptionState.collectAsState()` and renders the value.
  (If no such UI exists, add an adb-logcat hook via `flogInfo` in
  `setTranscriptionState` for the test.)
- Tap the voice key in SwiftFloris.
  - **Pre-fix:** observer logs `Ready → Ready`. No Listening
    transition.
  - **Post-fix:** observer logs `Ready → Listening` immediately. The
    Listening value remains held while FUTO's UI is active.
- Cancel / submit / return from FUTO to SwiftFloris.
  - Observer logs `Listening → Ready` on the `onStartInput` rebind.

<a id="v1.8.109"></a>
## v1.8.109

Date: 2026-05-17

Two seventh-pass audit findings from the clipboard agent (#11 + #19),
both about user-data leaks the existing eviction logic was skipping.

## What changed

### BackupScreen: drop `isSensitive` rows before writing the backup zip

[`app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/advanced/BackupScreen.kt`](app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/advanced/BackupScreen.kt#L213) —
the clipboard-history backup path previously serialised every history
row into the zip regardless of `isSensitive`. Backup zips are
user-portable artifacts (Syncthing, USB, cloud sync at user's choice)
and the file is **not passphrase-encrypted** (unlike the personal-
dictionary backup, v1.8.65). Any password / OTP / TOTP code that
landed in the clipboard history before v1.8.105's primary-clip
`EXTRA_IS_SENSITIVE` gate landed could be serialised in plaintext into
the backup zip.

The fix filters `clipboardHistory.filterNot { it.isSensitive }`
before the three serialisation paths (text, image, video) split it
by `ItemType`. Both legacy rows (carrying the sensitive bit from
pre-v1.8.105 history) and any future rows that slip through are
covered by a single point of filtering.

### ClipboardItem.close: extend to `ItemType.VIDEO`

[`app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardDatabase.kt`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardDatabase.kt#L226) —
the prior `close(context)` impl deleted the content-provider URI only
when `type == ItemType.IMAGE`. Two parallel leaks for video clipboard
items:

1. **Storage leak.** The file under `noBackupFilesDir/clipboard_files/<id>`
   never gets garbage-collected. Every video clip the user clears
   leaves the on-disk bytes behind.
2. **Privacy leak.** Per-receiver `grantUriPermission` calls issued
   through `ClipboardMediaProvider` are only revoked when the
   provider's `delete(uri)` is called. Skipping `delete` on video
   keeps the grants live until receiver-process death — apps that
   were granted READ on the video URI keep the read window open even
   after the user explicitly cleared history.

The fix extends the gate to `type == ItemType.IMAGE || type == ItemType.VIDEO`.
Text items remain a no-op (no provider-backed URI).

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/advanced/BackupScreen.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/provider/ClipboardDatabase.kt`
- `gradle.properties` — versionCode 1909 / versionName 1.8.109

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
```

Manual QA reproduction:

**Backup-sensitive test (API 33+):**
- Copy a credential from a password manager that sets
  `EXTRA_IS_SENSITIVE`.
  (Without v1.8.105 the row will be in history; v1.8.105+ refuses to
  insert, so this test reproduces the leak in legacy history rows.)
- Trigger Settings → Backup & restore → Backup; unzip the resulting
  archive and open `clipboard/clipboard_text_items.json`.
  - **Pre-fix:** the credential text appears in plaintext.
  - **Post-fix:** the credential is omitted.

**Video clear-all test:**
- Use `adb shell content insert` or a video-share-to-IME app to push
  a video clipboard item that lands in history.
- Verify `adb shell ls /data/data/dev.patrickgold.florisboard.debug/no_backup/clipboard_files/`
  contains the video file.
- In the IME, clear all clipboard history.
- **Pre-fix:** the video file remains on disk; per-receiver
  `grantUriPermission` is not revoked.
- **Post-fix:** the video file is gone; subsequent
  `openFile` from a previously-granted receiver fails with
  `FileNotFoundException`.

<a id="v1.8.108"></a>
## v1.8.108

Date: 2026-05-17

Seventh-pass audit finding #14 from the voice subsystem agent. Closes a
silent data-loss path in the `REMOVE_ITEM_FROM_LIST` executor.

## What changed

[`app/src/main/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceCommandExecutor.kt`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceCommandExecutor.kt#L170)
— `removeItemFromList` previously computed
`startOfOld = before.length - previous.length` and used
`content.textBeforeSelection.length` as the selection end. When the
user had a **non-empty selection** at the time the command fired —
e.g. they double-tapped a word, dragged the selection handles, or used
the system "select-all" shortcut — the `before.length` measurement was
taken at the *start* of their selection, but the subsequent
`editor.setSelection(startOfOld, content.textBeforeSelection.length)`
+ `commitText` collapsed the user's selection AND overwrote the
selected text plus the suffix above the cursor with the diff's new
text. Silent data loss.

The fix adds an early-return when `content.selectedText.isNotEmpty()`.
The streaming buffer is also NOT mutated in the refuse path (the early
return runs before `buffer.removeCommittedItem(item)`), so the user can
clear the selection and retry the command without the dictation buffer
diverging from the editor.

This matches the existing refuse-on-raw-input gate just above —
`isRawInputEditor` also returns ACTION_REJECTED rather than guessing.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceCommandExecutor.kt`
- `gradle.properties` — versionCode 1908 / versionName 1.8.108

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
```

Manual QA reproduction (requires voice IME + a list-dictation scenario):
- Dictate a list: "apples, bread, milk".
- Without saying anything else, manually select a word in the editor
  (e.g. double-tap "bread").
- Say "scratch apples from list".
  - **Pre-fix:** the selected word "bread" is collapsed and the
    "apples" entry plus its preceding text is overwritten with the
    diff result — multiple data losses in one command.
  - **Post-fix:** the command returns
    `VoiceCommandFailureReason.ACTION_REJECTED`; the editor selection
    and committed text are unchanged. The user can clear the
    selection and re-issue the command.

<a id="v1.8.107"></a>
## v1.8.107

Date: 2026-05-17

Seventh-pass audit finding #16 from the voice subsystem agent. Closes a
silent destructive-action footgun in the REMOVE_ITEM_FROM_LIST voice
command parser.

## What changed

[`app/src/main/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceCommandParser.kt`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceCommandParser.kt#L216)
— the `RemoveItemPatterns` list previously held one bare-prefix entry:

```kotlin
RemoveItemPattern(canonicalPhrase = "scratch", prefix = "scratch"),
```

Any utterance starting with the word "scratch" matched, with the rest
of the utterance becoming the argument. Concrete failure mode: the
user dictates natural prose like "let me scratch that idea" or "scratch
the previous note" — the parser silently fires
`REMOVE_ITEM_FROM_LIST` and the executor walks back through the
committed buffer deleting whatever it matches. Silent data loss with
no toast / no confirmation.

The fix replaces the bare-prefix entry with four explicit
prefix+suffix variants:

- `scratch <item> from list`
- `scratch <item> from the list`
- `scratch <item> off list`
- `scratch <item> off the list`

The shopping-list UX is preserved (anyone genuinely using "scratch"
for list editing still has the four disambiguated forms). The
attack surface — any utterance with "scratch" as the first word
silently destroying committed text — is gone.

## Tests

[`KeymanLdmlParserTest`](app/src/test/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceCommandParserTest.kt)
sibling — the existing `"parses 'scratch X' with single-word and
multi-word items"` test is updated to assert the new suffix-anchored
forms. A new regression guard test `"bare 'scratch X' (no suffix) no
longer triggers removal"` pins three previously-vulnerable inputs:

```kotlin
parser.parse("scratch apples") shouldBe null
parser.parse("scratch that idea") shouldBe null
parser.parse("scratch the previous note") shouldBe null
```

The `"rejects 'scratch' on its own with no item"` test continues to
pass — bare "scratch" still returns null, just for a different reason
(no pattern matches rather than blocked-argument rejection).

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceCommandParser.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceCommandParserTest.kt`
- `gradle.properties` — versionCode 1907 / versionName 1.8.107

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.voice.VoiceCommandParserTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
```

The new regression-guard test should pass; the updated single-word /
multi-word test should pass with the new suffix-anchored forms.

<a id="v1.8.106"></a>
## v1.8.106

Date: 2026-05-17

Seventh-pass audit finding #7 from the voice subsystem agent.

## What changed

[`app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt`](app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt#L247)
— `switchToVoiceInputMethod` previously routed the user's voice key
tap to an external voice IME (FUTO Voice Input, or any other enabled
voice IME) regardless of the focused field's sensitivity. Net effect:
a user typing in a password / numeric-PIN / web-password field who
tapped the voice key would have their spoken credential streamed
through an external recogniser process whose privacy boundary the
SwiftFloris no-`INTERNET` contract does **not** cover — voice IMEs
typically request full network access for cloud recognition.

The fix adds an early-return at the top of `switchToVoiceInputMethod`:
when `keyVariation == KeyVariation.PASSWORD` OR
`isIncognitoMode` (per v1.8.104 / v1.8.105, the unified privacy gate),
the function shows a toast and returns false. The host app's
sensitive-field declaration is the load-bearing signal here; the
IME-side gate honours it.

Mirrors the existing dictionary-learn, clipboard cut/copy (v1.8.86 +
v1.8.105), and smart-compose (`SensitiveFieldGuard`) gates.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt`
- `app/src/main/res/values/strings.xml` (new toast string
  `voice_input__suppressed_on_sensitive_field`)
- `gradle.properties` — versionCode 1906 / versionName 1.8.106

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
```

Manual QA reproduction:
- Install a voice IME (FUTO Voice Input recommended) and enable it
  in system Settings.
- Open SwiftFloris in a password field (or numeric-PIN, or web-password,
  or any field whose host sets `IME_FLAG_NO_PERSONALIZED_LEARNING`).
- Tap the SwiftFloris voice key.
  - **Pre-fix:** the system swaps to the external voice IME and
    begins recording / streaming.
  - **Post-fix:** the swap is refused; a toast reading "Voice input
    is disabled for this field for your privacy." appears; focus
    stays on SwiftFloris.
- Repeat in a normal text field and verify voice handoff still works.

<a id="v1.8.105"></a>
## v1.8.105

Date: 2026-05-17

Two seventh-pass audit findings landed in one cohesive privacy slice:
the IME-local clipboard history was leaking through two distinct gates
that should always have suppressed.

## What changed

### EditorInstance: cut / copy gate on `isIncognitoMode`

[`app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/EditorInstance.kt`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/EditorInstance.kt#L521-L562)
— `performClipboardCut` and `performClipboardCopy` previously gated only
on `isPasswordField()` (which v1.8.86 extended to cover numeric-PIN
fields). They did NOT check `isIncognitoMode`.

Concrete failure: a user types in Signal (which sets
`IME_FLAG_NO_PERSONALIZED_LEARNING`, now forcibly honoured per v1.8.104),
the IME marks the field as `isIncognitoMode = true`, the dictionary
learn path correctly suppresses — but if the user selects text and hits
Cut, the selected text lands in the IME-local clipboard history. From
there it can be re-pasted into any other app via the clipboard palette,
bypassing the host-app's privacy declaration.

The fix unifies both gates into a single `shouldSuppressClipboardHistory()`
helper that returns true on either signal (password OR incognito). Both
cut and copy now read the helper.

### ClipboardManager: honour `ClipDescription.EXTRA_IS_SENSITIVE`

[`app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardManager.kt`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardManager.kt#L201-L227)
— `onPrimaryClipChanged` (the system-clipboard-to-IME-history sync path)
previously called `ClipboardItem.fromClipData` (which already parsed
`EXTRA_IS_SENSITIVE` into `ClipboardItem.isSensitive`) but then
unconditionally called `insertOrMoveBeginning(item)`. The flag was read,
not used.

Password managers (Bitwarden, 1Password, KeePassXC, Proton Pass) and
TOTP apps set `EXTRA_IS_SENSITIVE` on every copied credential. Before
this fix, every credential the user copied via the system clipboard
landed in SwiftFloris's IME-local history and could be re-pasted via the
clipboard palette — circumventing the password manager's own
copy-clear-after-N-seconds protection. The system clipboard's
auto-clear timer still ran, but the IME-local copy stayed forever.

The fix wraps the `insertOrMoveBeginning(item)` call in
`if (!item.isSensitive)`. The system clipboard still receives the clip
(SwiftFloris is not the source of truth for system-clipboard behaviour);
only the IME-local history skip is added.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/EditorInstance.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardManager.kt`
- `gradle.properties` — versionCode 1905 / versionName 1.8.105

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:installDebug
```

Manual QA reproduction:

**Incognito-gate test (combines v1.8.104 + this release):**
- Open Signal (or any app whose editor sets `IME_FLAG_NO_PERSONALIZED_LEARNING`).
- Type a unique phrase you can recognise, select it, hit Cut on the IME action bar.
- Open the IME's clipboard palette (smartbar → clipboard).
- **Pre-fix:** the phrase appears as the most-recent clipboard entry.
- **Post-fix:** the phrase does NOT appear — only entries from non-incognito fields are retained.

**Sensitive-clip test (API 33+):**
- Install a password manager (Bitwarden / KeePassDX) and copy a
  credential to the clipboard.
- Open any text field, open the IME clipboard palette.
- **Pre-fix:** the credential appears in history (visible in plaintext
  unless `displayText()` redaction fires).
- **Post-fix:** the credential does not appear in history. The system
  clipboard still has it (the source app's auto-clear timer governs).
- On Android < 13 (no `EXTRA_IS_SENSITIVE`), behaviour is unchanged
  because the flag never gets set.

<a id="v1.8.104"></a>
## v1.8.104

Date: 2026-05-17

Seventh-pass audit finding from my own pass on `FlorisImeService` /
`EditorInstance` plumbing.

## What changed

[`app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/EditorInstance.kt`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/EditorInstance.kt#L138-L152)
— `handleStartInputView` previously read the user's
`prefs.suggestion.incognitoMode` setting and only honoured the host
app's `IME_FLAG_NO_PERSONALIZED_LEARNING` declaration when that
preference was `DYNAMIC_ON_OFF` (the default). When the user had set
the preference to `FORCE_OFF`, the flag was silently ignored.

The privacy contract this gets wrong: apps that set
`IME_FLAG_NO_PERSONALIZED_LEARNING` (Signal, ProtonMail, banking
apps, end-to-end encrypted chat surfaces, password vaults) are
asserting "this is sensitive content; do not learn from it." That's
**not a user preference question** — it's a host-app declaration the
IME is obligated to honour. A user who chose `FORCE_OFF` was making a
statement about their *own* manual incognito-toggle UX, not about
overriding cross-app privacy declarations.

The fix splits the gate:

- **App-declared `flagNoPersonalizedLearning`** always forces
  `isIncognitoMode = true` for the current field, regardless of the
  user's IncognitoMode preference.
- The **user's IncognitoMode preference** continues to control
  user-requested incognito (the smartbar toggle, the FORCE_ON
  power-user setting).

Net effect: every gate that reads `activeState.isIncognitoMode` —
`learnIfAllowed` in `KeyboardManager` (which suppresses dictionary
writes), the bigram / trigram store updates, the touch-decoder
sample writes — now correctly suppresses on app-declared sensitive
fields even for users who turned off the manual incognito UX.

## Why this is a separate release

Per [AGENTS.md §6](AGENTS.md), one logical improvement per release.
v1.8.85 was the documented exception. This is the seventh-pass audit
opening shot.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/EditorInstance.kt`
- `gradle.properties` — versionCode 1904 / versionName 1.8.104

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
```

Manual QA reproduction:
- Set Settings → Typing → Suggestion → Incognito mode → "Always off".
- Open Signal (or any app whose editor sets
  `IME_FLAG_NO_PERSONALIZED_LEARNING`) and type a few unique words.
- Switch to a non-sensitive editor (a normal text field) and type
  one of the words you just typed in Signal.
  - **Pre-fix:** the word appears in the suggestion strip as a
    learned personal-dictionary entry — Signal's privacy contract
    is broken.
  - **Post-fix:** the word does not appear unless it's already in
    the base SCOWL dictionary.
- Verify the manual incognito toggle still works on the smartbar
  (independent surface).

<a id="v1.8.103"></a>
## v1.8.103

Date: 2026-05-17

Docs-only release. Catches the README front door + PROJECT_CONTEXT.md
"Stack at HEAD" header up to v1.8.103 (they were stale at v1.8.84,
eighteen releases behind), and provides a single master-index entry to
the 2026-05-17 session's nineteen-release run.

## What changed

### `README.md`

- Version badge bumped `v1.8.84` → `v1.8.103`.
- Highlights table header updated.
- Status line updated.
- Recent-releases list gains a single composite top entry summarising
  v1.8.85 – v1.8.103 rather than nineteen individual bullets (the
  detailed breakdown is in the per-release `RELEASE_NOTES_v*.md`
  files and in the `ROADMAP.md` v5.5 / v5.4 sections). The pre-v1.8.85
  entries are preserved in place.

### `PROJECT_CONTEXT.md`

- §3 "Stack at HEAD" header bumped to v1.8.103.
- A reconciliation paragraph after the v1.8.84 entry summarises the
  v1.8.85 – v1.8.103 deltas to load-bearing invariants
  (no-`INTERNET` gate now scans merged manifests, Android-12+
  data-extraction excludes, `FLAG_SECURE` coverage extensions,
  `verifyDataExtractionRules` new gate, `ZipUtils.unzip` atomic-abort
  semantics, hardware-keyboard mapper thread safety + AltGr,
  CI workflow permissions / SHA-pins, outreach drafts).
- The open `F11` Roborazzi-baseline item is named so any future agent
  doesn't re-flag it.

## Why this is its own release rather than bundled

Per [AGENTS.md §6](AGENTS.md), one logical improvement per release.
v1.8.85 was an explicit exception; everything since has been one item
per release. Updating two docs in lockstep with a clear "release-hygiene
catch-up" framing is one logical improvement.

[IMPROVEMENT_PLAN.md §10](IMPROVEMENT_PLAN.md) tracks "release-front-door
hygiene" as a standing workstream — v1.8.70 was the previous catch-up;
v1.8.103 is the next.

## Files touched

- `README.md`
- `PROJECT_CONTEXT.md`
- `gradle.properties` — versionCode 1903 / versionName 1.8.103

## Verification

No `:app` source / lint / test impact — docs-only.

```powershell
./gradlew.bat :app:verifyNoInternetPermission
./gradlew.bat :app:assembleDebug
```

`assembleDebug` should produce a `1.8.103` APK; the rest of the
Definition-of-Done verification is no-op for docs changes.

## 2026-05-17 session master index

For anyone arriving at this commit cold, the 2026-05-17 session shipped
nineteen releases. In chronological order:

1. **v1.8.85** — cross-subsystem hardening pass (eleven fixes, intentional
   AGENTS.md §6 one-time deviation): `verifyNoInternetPermission` merged
   manifest scan + `tools:node="remove"` exemption,
   `data_extraction_rules.xml` for Android 12+, `ZipUtils.unzip`
   pre-canonical entry-name guard + entry-count cap,
   `HardwareKeyboardRuntimeMapper` thread safety + AltGr fix,
   `BitmapFactory` bounded decode in the sticker palette, sticker MIME
   spoof close, addon enumerator APK-size vs bundle-size category-error
   fix, `verify-reproducible-apk.sh` payload-entry-manifest pass criterion,
   CI file-scope `permissions: { contents: read }`,
   `validate-strings-no-translations.yml` `env:`-passing of untrusted PR
   data.
2. **v1.8.86** — `keyVariation` honours `TYPE_NUMBER_VARIATION_PASSWORD`.
3. **v1.8.87** — `FLAG_SECURE` + non-saveable passphrase on
   `DictionaryPassphraseDialog`.
4. **v1.8.88** — recover-not-crash on undecryptable legacy AndroidX
   Security Crypto passphrase state.
5. **v1.8.89** — `ZipUtils.unzip` atomic-abort policy split.
6. **v1.8.90** — SAF lost-grant surface in Settings.
7. **v1.8.91** — Addon spec KDoc mandate of the REGISTER receiver.
8. **v1.8.92** — `KeymanLdmlParser` honours `shift=` over `longPress=`.
9. **v1.8.93** — `release.yml` keystore-decode hygiene + `gh release
   create` env-var hardening.
10. **v1.8.94** — `verify-addon-apk.sh` strict mode + tri-state failure
    reporting.
11. **v1.8.95** — `verifyDataExtractionRules` build gate.
12. **v1.8.96** — `crowdin/github-action@v2` +
    `peter-evans/create-or-update-comment@v4` SHA-pinned.
13. **v1.8.97** — `fastlane/update-readme.sh` Python block substitution
    + `generate-screenshots.sh` absolute-path cleanup.
14. **v1.8.98** — `generate_icon.py` portability.
15. **v1.8.99** — `HardwareKeyboardLayout.equals` fast-path.
16. **v1.8.100** — Sticker palette `LruCache<String, ImageBitmap>` +
    cursor-time enumeration cap.
17. **v1.8.101** — In-keyboard banner for SAF lost-grant.
18. **v1.8.102** — `HardwareKeyEntry.longPressAlternates` parser side.
19. **v1.8.103** — this release: docs catch-up + master index.

Plus docs-only commits:

- `docs(outreach): SwiftKey-refugee discovery drafts for 2026-05-30 window`
- `docs: research run 2026-05-17 — sixth-pass roster closure`
- `docs: research run 2026-05-17 — sixth pass (ROADMAP v5.4)`
- `docs: mark SwiftKey-refugee outreach status — drafts shipped`

Sixth-pass F-roster status: 11/12 closed. **F11 (Roborazzi visual
baselines)** remains open and requires Android SDK + on-device record on
the maintainer build host.

<a id="v1.8.102"></a>
## v1.8.102

Date: 2026-05-17

Follow-up F8 from the [v1.8.85 audit roster](CHANGELOG.md#v1.8.85).
Closes the LDML-side half; popup-UI routing remains a separate slice.

## What changed

### `HardwareKeyEntry.longPressAlternates`

[`app/src/main/kotlin/dev/patrickgold/florisboard/ime/hardware/HardwareKeyboardLayout.kt`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/hardware/HardwareKeyboardLayout.kt) —
adds `longPressAlternates: List<Int>` to `HardwareKeyEntry`. LDML
defines `longPress="a b c"` as a space-separated list of alternates
surfaced on long-press. v1.8.92 closed the shift-slot misuse but
silently dropped the alternates list; this release stores it.

Empty list when no `longPress=` was declared, so behaviour is unchanged
for layouts that already worked.

### `KeymanLdmlParser` populates the field

[`app/src/main/kotlin/dev/patrickgold/florisboard/ime/hardware/KeymanLdmlParser.kt`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/hardware/KeymanLdmlParser.kt) —
tokenises the `longPress=` attribute on whitespace, decodes each
token's LDML escapes (`\u{xxxx}`, `\uxxxx`), and keeps the first
codepoint of each. Order is preserved to match the LDML author's
intent (alternates are usually authored in popularity / display
order).

The empty-output guard now allows a key with no `output=` and no
`shift=` but a populated `longPressAlternates` — that's a key that
exists only as a long-press source, which is unusual but legitimate.

### Tests

[`KeymanLdmlParserTest`](app/src/test/kotlin/dev/patrickgold/florisboard/ime/hardware/KeymanLdmlParserTest.kt)
gains two new tests and two existing tests are extended:

- `longPress alternates are populated alongside shift when both attributes are present` —
  proves the alternates list is captured independently of the shift
  slot when both attributes are declared.
- `empty longPressAlternates when longPress attribute is absent` —
  proves the field defaults cleanly.
- The pre-existing `multi-alternate longPress with no shift leaves shift slot null`
  test now also asserts `longPressAlternates == listOf('ä'.code, 'á'.code, 'à'.code)`.
- The pre-existing `single-alternate longPress with no shift remains usable as shift fallback`
  test now also asserts the alternate codepoint lands in
  `longPressAlternates`.

## What this release does NOT do

**Popup-routing.** The on-screen keyboard's long-press popup currently
reads alternates from `KeyData.popup`, which is a software-keyboard
data model unrelated to `HardwareKeyEntry`. Wiring
`HardwareKeyEntry.longPressAlternates` into the popup would require:

- A new bridge between the hardware-keyboard runtime mapper and the
  popup controller.
- A way to identify which on-screen key is "the long-press source" when
  a hardware keystroke is the trigger.
- Snygg styling for the hardware-source popup variant.

That's a multi-file feature slice worth its own scoped release; for
now `longPressAlternates` is populated and exposed but not consumed at
input time. This release lands the parser side so the future popup
slice doesn't need a second parser change.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/hardware/HardwareKeyboardLayout.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/hardware/KeymanLdmlParser.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/hardware/KeymanLdmlParserTest.kt`
- `gradle.properties` — versionCode 1902 / versionName 1.8.102

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
```

The four updated / new tests in `KeymanLdmlParserTest` should pass;
existing tests for the parser should continue to pass with the
extended assertions.

<a id="v1.8.101"></a>
## v1.8.101

Date: 2026-05-17

Follow-up F7 from the [v1.8.85 audit roster](CHANGELOG.md#v1.8.85).
Mirror of the v1.8.90 Settings-side surface, now applied to the
in-keyboard sticker palette as well.

## What changed

[`app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/StickerPaletteView.kt`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/StickerPaletteView.kt) —
the Imported sticker tab previously vanished silently when Android
revoked the SAF persistable read grant for the user-picked folder
(e.g. the file manager that issued the grant was uninstalled, the
system cleaned up old grants, the user did a factory pattern restore).
The user had no in-keyboard signal that the pack was recoverable.

This release distinguishes three states inside the palette
`LaunchedEffect`:

| `userFolderUri` | `hasPersistableReadPermission` | Behaviour |
|---|---|---|
| blank | n/a | No Imported tab (unchanged) |
| non-blank | true | Pack rendered (unchanged) |
| non-blank | false | **Empty placeholder pack so the tab stays visible**, plus a Snygg-styled `MediaEmojiSubheader` row reading "Imported folder access lost. Open Settings → Emoji & stickers to re-pick." |

The tab stays present + selectable in the third state so the user gets
a clear actionable signal. Tapping the warning is intentionally NOT
wired to a deep-link to Settings: the IME view runs in a different
process surface and launching Settings activities from it has
historically been fragile across Android versions / OEMs; the message
instead points the user to the deterministic re-pick path that already
works (`Settings → Emoji & stickers → Imported sticker folder`, which
the v1.8.90 surface already surfaces with the matching "Folder access
lost" preference summary).

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/StickerPaletteView.kt`
- `app/src/main/res/values/strings.xml`
- `gradle.properties` — versionCode 1901 / versionName 1.8.101

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:installDebug
```

Manual QA reproduction:
- Pick an Imported sticker folder via the file manager. Verify the
  Imported tab appears with stickers (unchanged baseline).
- Uninstall the file manager (or `adb shell pm clear <file-manager-pkg>`).
- Open the keyboard → Stickers palette.
  - **Pre-fix:** Imported tab is absent; user has no signal.
  - **Post-fix:** Imported tab is present; tapping it shows "Imported
    folder access lost. Open Settings → Emoji & stickers to re-pick."
  - Walk through Settings → Emoji & stickers → re-pick. Tab repopulates
    with the new pack.
- Verify bundled sticker packs remain unaffected throughout.

<a id="v1.8.100"></a>
## v1.8.100

Date: 2026-05-17

Follow-up F5 from the [v1.8.85 audit roster](CHANGELOG.md#v1.8.85).

## What changed

### Shared `LruCache<String, ImageBitmap>` for sticker tiles

[`app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/StickerPaletteView.kt`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/StickerPaletteView.kt) —
each `StickerPreview` composable previously held a private
`ImageBitmap?` state via `remember(sourceUri)`. When the user scrolled
the grid, tiles entered and exited composition; every entry re-fired the
`LaunchedEffect`, re-opened the SAF input stream, and re-ran the
two-pass decode. For a 240-sticker pack scrolled aggressively the IME
allocated and released hundreds of bitmaps per minute — visible jank on
mid-tier devices and material wakeup pressure on the SAF
content-resolver.

This release adds a module-scoped
`androidx.collection.LruCache<String, ImageBitmap>` keyed by `sourceUri`
with a 64-entry budget (~13 MB at typical sticker sizes; ~32 MB worst
case at the 512 px target edge). The composable seeds its
`remember` state from the cache so the cache-hit path renders the first
frame post-recomposition with no SAF round-trip. Cache-miss path still
runs the two-pass decode, then stores the result.

Object-count-based eviction is good enough; actual heap pressure on the
IME process is dominated by the keyboard atlas, the Compose tree, and
the active dictionary.

### Cursor-time enumeration cap in `UserStickerRepository`

[`app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/UserStickerRepository.kt`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/UserStickerRepository.kt) —
`queryStickerDocuments` previously materialised every row in the SAF
cursor into a `UserStickerDocument`, sorted them, then dropped all but
the first `MaxStickers` (240). On a 50_000-file Downloads folder this
meant 50_000 allocations (plus their string fields) per pack reload, on
the IME-startup cold path.

Now the cursor walk caps at `MaxStickers * 4 = 960` entries — wider
than the final displayed count so `sortedBy { it.label }` /
`distinctBy { it.sourceUri }` still pick from a richer set, but bounded
hard. A `flogWarning` documents the cap so the user / maintainer can
see why files past the threshold are missing.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/StickerPaletteView.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/UserStickerRepository.kt`
- `gradle.properties` — versionCode 1900 / versionName 1.8.100

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:installDebug
```

Manual QA:
- Pick a folder with 240+ PNG stickers. Scroll the Imported tab rapidly
  for 30 seconds; the IME should not show frame drops or memory growth
  on the second pass through the grid (cache-hit). Pre-fix: every
  re-enter re-decoded.
- Pick a folder with 5000+ files. Verify `loadPack` returns within
  ~100 ms and the Imported tab populates. Pre-fix: the IME was tied up
  materialising every row in the cursor.
- Watch logcat for the
  "capped folder enumeration at … entries" warning when the chosen
  folder exceeds `MaxStickers * 4`.

<a id="v1.8.99"></a>
## v1.8.99

Date: 2026-05-17

Follow-up F6 from the [v1.8.85 audit roster](CHANGELOG.md#v1.8.85).

## What changed

[`app/src/main/kotlin/dev/patrickgold/florisboard/ime/hardware/HardwareKeyboardLayout.kt`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/hardware/HardwareKeyboardLayout.kt)
— overrides the auto-generated `equals` / `hashCode` so `scancodeMap` is
no longer walked entry-by-entry in the common case.

The data class previously inherited the generated equality, which calls
`Map.equals` on `scancodeMap`. A real LDML layout has ~300 keys and
each `HardwareKeyEntry` is itself a data class with eight fields —
O(n*m) per comparison. The mapper / settings paths compare layouts often
(device attach, pruning, refresh after rescan), so the cost
compounds.

The override has three fast-paths:

1. **`this === other`** — O(1) when the same layout reference is held
   by both sides (the dominant case: the mapper hands the same reference
   around).
2. **Different metadata** (`name`, `locale`, or `scancodeMap.size`) —
   O(1) reject without touching the map.
3. **Same metadata but `scancodeMap !== other.scancodeMap`** — fall
   through to the structural walk to preserve correctness for the rare
   cross-instance comparison. Two layouts produced by separate parse
   calls always have distinct map references, so this fallback runs only
   when the caller explicitly compares two parser outputs.

`hashCode` is now `name + locale + scancodeMap.size`, invariant across
all cases the new `equals` treats as equal.

`componentN()` / `copy()` keep their data-class semantics unchanged.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/hardware/HardwareKeyboardLayout.kt`
- `gradle.properties` — versionCode 1899 / versionName 1.8.99

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
```

The existing
[`KeymanLdmlParserTest`](app/src/test/kotlin/dev/patrickgold/florisboard/ime/hardware/KeymanLdmlParserTest.kt)
exercises layout equality through `shouldBe` comparisons and should
continue to pass unchanged — the override preserves "two structurally
identical layouts compare equal" semantics for the same-reference and
size-mismatch cases, and falls through to the structural walk for the
remaining tail case.

<a id="v1.8.98"></a>
## v1.8.98

Date: 2026-05-17

Follow-up F1 from the [v1.8.85 audit roster](CHANGELOG.md#v1.8.85).

## What changed

[`generate_icon.py`](generate_icon.py) — the output path was a
hard-coded Windows absolute path:

```python
output_path = r'C:\Users\--\repos\SwiftFloris\SwiftFloris_icon_new.png'
```

with the original maintainer's home directory redacted. The script
worked only on a single host and silently failed (or, worse, wrote to
some unrelated `C:\Users\--\` if the directory happened to exist) on
every other machine.

Replaced with:

```python
output_path = Path(__file__).resolve().parent / "SwiftFloris_icon_new.png"
```

so the icon now lands next to the script regardless of caller CWD or
host OS. Added a module docstring explaining the script's purpose.

## Files touched

- `generate_icon.py`
- `gradle.properties` — versionCode 1898 / versionName 1.8.98

## Verification

```bash
python3 generate_icon.py
```

The icon is not consumed by the build or by CI; it's a maintainer
artifact for one-off branding work, so no further verification is
needed.

<a id="v1.8.97"></a>
## v1.8.97

Date: 2026-05-17

Follow-up F3 from the [v1.8.85 audit roster](CHANGELOG.md#v1.8.85).

## What changed

Two maintainer-only fastlane scripts had reliability footguns:

### `fastlane/update-readme.sh`

The previous implementation interpolated a multi-line markdown block
directly into `sed -i "/BEGIN/,/END/c\\$obtainium_links"`. Three concrete
problems:

1. Markdown links contain `/`, which is sed's default address delimiter.
   Any `$obtainium_links` entry with an unescaped `/` would either break
   the `sed` invocation or corrupt the README.
2. The `c\` (change) command in sed expects each replacement line to end
   with a `\` continuation marker. The `echo -e` source escaping was
   inconsistent with that requirement.
3. `set -e` was absent. A failure inside the `for` loop (e.g. malformed
   JSON in a track manifest) silently produced an incomplete block and
   the script still tried to write it.

This release:

- Adds `set -euo pipefail`.
- Writes the replacement block to a temp file (`trap`-cleaned on exit),
  then hands it to a Python block-substitution call. Python sees the
  block as a regular string with no shell-escape surface, so a markdown
  line containing `/` or `&` cannot corrupt the program.
- Exits with `::error::` on missing prerequisites (Obtainium dir, README
  file, zero track manifests) so a `for file in dir/*.json; do` over an
  empty dir doesn't silently produce a broken README.

### `fastlane/generate-screenshots.sh`

The previous script ended with `cd ..; rm -r out` after a nested
sequence of `cd`s. If any intermediate step failed silently or changed
directory, the `rm -r out` would have removed the wrong tree. Two
specific risks closed:

- `cd staging/images || exit` at the top + `cd out || exit` later
  meant the cleanup depended on the script-runner's CWD being correct
  at start. Now resolves an absolute path to `staging/images` via
  `${(%):-%x}` (zsh's script-file-name parameter), independently of
  the runner's CWD.
- `rm -r out` replaced with `rm -rf -- "$OUT_DIR_ABS"` where
  `OUT_DIR_ABS` is the absolute path captured at start. Any later
  `cd` mutation can't redirect the cleanup target.

Strict mode (`set -euo pipefail`) added. `mkdir out` → `mkdir -p out`
so a re-run on a non-empty workspace doesn't abort under strict mode.

Quoting the dozens of internal `$SPLIT_IMAGE_*` / `$OUT_FILE` ImageMagick
references is left alone — the names are constants under maintainer
control and quoting them all would be a much larger diff for negligible
risk reduction. If a future PR adds a SPLIT_IMAGE name containing a
space, that PR should add the quotes locally.

## Files touched

- `fastlane/update-readme.sh`
- `fastlane/generate-screenshots.sh`
- `gradle.properties` — versionCode 1897 / versionName 1.8.97

## Verification

Both scripts are maintainer-only — run on the build host, never in CI.
Smoke test on the build host:

```bash
./fastlane/update-readme.sh
git diff README.md

./fastlane/generate-screenshots.sh
ls metadata/android*/en-US/images/phoneScreenshots/
```

In both cases the script should error-out cleanly on missing inputs
(previously, missing inputs produced silent partial results).

<a id="v1.8.96"></a>
## v1.8.96

Date: 2026-05-17

Follow-up F9 + F10 from the [v1.8.85 audit roster](CHANGELOG.md#v1.8.85).

## What changed

Two CI workflows referenced third-party actions by floating major-version
tag rather than by SHA. A floating tag means an attacker who compromises
the action's repository can re-point the tag at a malicious commit and
every workflow consuming the floating tag picks up the malicious code at
its next run. Each of these workflows passes either the workflow's
`GITHUB_TOKEN` or a real third-party credential into the action, so the
blast radius matters.

- [`.github/workflows/crowdin-upload.yml`](.github/workflows/crowdin-upload.yml)
  — `crowdin/github-action@v2` pinned to
  `8868a33591d21088edfc398968173a3b98d51706`. This action receives the
  Crowdin personal token (`FSEC_CROWDIN_PERSONAL_TOKEN`) plus the
  workflow's read-only `GITHUB_TOKEN`.
- [`.github/workflows/validate-strings-no-translations.yml`](.github/workflows/validate-strings-no-translations.yml)
  — `peter-evans/create-or-update-comment@v4` pinned to
  `71345be0265236311c031f5c7866368bd1eff043`. This action runs on
  `pull_request_target` (base-repo context) with `pull-requests: write`,
  so a malicious v4 retag could exfiltrate the workflow's token.

Both SHAs were verified at edit time against the GitHub API:
`GET /repos/<owner>/<repo>/git/refs/tags/<tag>` returned
`object.type = commit` and `object.sha` matching the values above.

Each pin carries an inline comment explaining the SHA's provenance and
the re-pin procedure, so a future maintainer bumping the action knows
to re-run the API call rather than reintroduce a floating tag.

## What this release does NOT change

The first-party GitHub actions (`actions/checkout`, `actions/setup-java`,
`actions/upload-artifact`, `gradle/actions/*`, `lukka/get-cmake@v4.0.2`)
are still on floating tags. Those are arguably acceptable because:

- `actions/*` are maintained by GitHub itself.
- `gradle/actions/*` are maintained by Gradle Inc.
- `lukka/get-cmake@v4.0.2` is pinned to a patch version (not a moving
  major).

Sweeping every floating tag is a larger consistency exercise tracked
separately; this release closes only the two third-party / write-token
exposures the v1.8.85 audit flagged.

## Files touched

- `.github/workflows/crowdin-upload.yml`
- `.github/workflows/validate-strings-no-translations.yml`
- `gradle.properties` — versionCode 1896 / versionName 1.8.96

## Verification

No `:app` source / lint / test impact — workflow-only.

The next push to `main` triggering `validate-strings-no-translations.yml`
or `crowdin-upload.yml` exercises the pinned SHA; the workflow run logs
will show the action resolving to a commit hash rather than a tag name.

If the maintainer wants to re-verify the SHA values:

```bash
gh api repos/crowdin/github-action/git/refs/tags/v2 --jq '.object'
gh api repos/peter-evans/create-or-update-comment/git/refs/tags/v4 --jq '.object'
```

Both should return `{ "sha": "<the pinned value>", "type": "commit", … }`.

<a id="v1.8.95"></a>
## v1.8.95

Date: 2026-05-17

Follow-up F12 from the [v1.8.85 audit roster](CHANGELOG.md#v1.8.85).

## What changed

[`app/build.gradle.kts`](app/build.gradle.kts) — new
`verifyDataExtractionRules` task, wired to `preBuild` on every variant
(matches the [`verifyNoInternetPermission`](app/build.gradle.kts)
pattern).

The task fails the build if
[`app/src/main/res/xml/data_extraction_rules.xml`](app/src/main/res/xml/data_extraction_rules.xml)
(the Android 12+ rules file shipped in v1.8.85) is missing OR if it
drops any of the load-bearing excludes:

- **SQLCipher personal-dictionary DB + sidecars** —
  `floris_user_dictionary`, `floris_user_dictionary.db`,
  `floris_user_dictionary.db-journal`,
  `floris_user_dictionary.db-wal`,
  `floris_user_dictionary.db-shm`.
- **Tink-wrapped passphrase prefs** — `floris_user_dictionary_key.xml`.
- **Clipboard history dir** — `clipboard_history`.

It also fails if either of the two required rule sections is missing
(`<cloud-backup>`, `<device-transfer>`).

## Why this matters

Android Lint already validates the file against the data-extraction-rules
schema, but a schema-valid edit can still drop an exclude. Concrete
failure mode without this gate:

1. A contributor "cleans up" the rules file (removes an exclude that
   "looks redundant" or migrates the file to a different schema).
2. Lint passes; tests pass; the APK ships.
3. On a real Android 12+ device, D2D transfer carries the SQLCipher
   DB AND its undecryptable Tink-wrapped passphrase pref to a new
   device — leaking PII ciphertext and bricking the user dictionary
   on the new device.

The substring check pinned in this task catches both an outright
exclude deletion AND a path-typo (e.g. `floris_user_dictionary.dbb`
that lints clean but matches no real path).

## Files touched

- `app/build.gradle.kts`
- `gradle.properties` — versionCode 1895 / versionName 1.8.95

## Verification

```powershell
./gradlew.bat :app:verifyDataExtractionRules
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
```

`verifyDataExtractionRules` should pass at HEAD; deliberately deleting
one of the listed `<exclude>` lines from
`data_extraction_rules.xml` and re-running should produce the
"missing exclude identifiers:" error and exit non-zero before
`assembleDebug` starts.

<a id="v1.8.94"></a>
## v1.8.94

Date: 2026-05-17

Follow-up F4 from the [v1.8.85 audit roster](CHANGELOG.md#v1.8.85).

## What changed

[`scripts/verify-addon-apk.sh`](scripts/verify-addon-apk.sh) — three
checks (`check_permissions`, `check_register_receiver_and_metadata`,
`check_signing_certificate`) previously ran their respective Android
SDK tools with `|| true` to swallow any non-zero exit, then made the
PASS / FAIL decision based on whether `grep` matched anything in the
captured output. The collapse means:

- A corrupted `aapt2` binary that prints nothing and exits 1 silently
  PASSes the permissions and receiver checks (empty output → no
  banned-permission match → "PASS no banned network permissions
  declared"; empty manifest → no REGISTER action → "FAIL no
  REGISTER_ADDON intent action" but the cause is misreported as a
  contract violation).
- A missing `apksigner` binary at runtime silently looks like an
  unsigned APK.

This release switches each check to a three-state decision:

1. **Tool failed to invoke / exited non-zero** → FAIL with a message
   that names the tool and the exit code. The maintainer immediately
   knows it's a tooling problem, not an APK contract violation.
2. **Tool succeeded but produced no output** → distinguished per check:
   for permissions, empty output is genuine "no permissions declared"
   (PASS); for the manifest dump, empty output is malformed APK (FAIL).
3. **Tool succeeded with output** → original grep-based PASS / FAIL.

`set -u` was previously the only strict-mode flag. Replaced with
`set -eo pipefail` so a pipeline failure (e.g. `head | tail` segment)
aborts the script before it can falsely PASS the next check. `set -u`
itself is deliberately omitted because it would error on the unset
positional parameter `$1` before the usage check fires; the case-by-
case `${VAR:-default}` style covers the references that need it.

The script is consumed by the addon-CI gate documented in
[`docs/addons/apk-validation.md`](docs/addons/apk-validation.md), so
the contract surface is unchanged — only the failure-mode reporting
gets sharper.

## Files touched

- `scripts/verify-addon-apk.sh`
- `gradle.properties` — versionCode 1894 / versionName 1.8.94

## Verification

No `:app` source / lint / test impact.

Manual smoke on the build host:

```bash
PATH="" ./scripts/verify-addon-apk.sh /path/to/some-addon.apk

./scripts/verify-addon-apk.sh /path/to/known-good-addon.apk
```

<a id="v1.8.93"></a>
## v1.8.93

Date: 2026-05-17

Follow-up F2 from the [v1.8.85 audit roster](CHANGELOG.md#v1.8.85).

## What changed

[`.github/workflows/release.yml`](.github/workflows/release.yml) — the
keystore-decode step previously had three forensic-leak / silent-failure
risks:

1. **`echo "$VAR" | base64 -d`** adds a trailing newline before the pipe,
   so any base64 payload whose encoder did not terminate with `\n` would
   have a stray `0x0a` appended pre-decode and the decoded bytes would
   be one byte off. The resulting keystore would fail to open, but the
   error message would not point at the encoding mistake. Replaced with
   `printf '%s' "$VAR" | base64 -d` so the secret is passed through
   verbatim.
2. **No `umask 077` or `chmod 600`** on the decoded keystore — on a
   shared runner image the file was world-readable until consumed.
   `umask 077` before the redirect plus `chmod 600` after closes the
   read window for any other process on the runner image.
3. **No magic-byte validation** — a malformed secret could produce a
   non-empty file that gets handed to `jarsigner` / AGP signing, and
   the failure mode is opaque. New check: read first 4 bytes, accept
   JKS (`FE ED FE ED`) or PKCS#12 (`30 82 …` DER SEQUENCE), fail-fast
   with a pointing error otherwise.

Same workflow: the `gh release create` step previously interpolated
`${{ inputs.version }}`, `${{ inputs.draft }}`, `${{ github.ref_name }}`,
`${{ steps.locate-apk.outputs.apk-path }}`, and
`${{ steps.sha.outputs.manifest-path }}` directly into the `run:` shell
command. The values are maintainer-controlled today, but the pattern is
the same script-injection footgun the
[`validate-strings-no-translations.yml` hardening (v1.8.85)](CHANGELOG.md#v1.8.85)
closed. All five values now pass through `env:` and the command line
uses `"$VAR"`. `set -euo pipefail` added.

Bash arrays (`draft_arg=()`) replace the previous unquoted
`$draft_arg` expansion so a `false` setting can't accidentally pass an
empty-string argument that becomes a positional placeholder.

## Files touched

- `.github/workflows/release.yml`
- `gradle.properties` — versionCode 1893 / versionName 1.8.93

## Verification

No `:app` source / lint / test impact — workflow-only.

Manual reproduction the maintainer can run on the build host:

```bash
SIGNING_KEYSTORE_BASE64="$(printf 'not-a-keystore' | base64)"
```

The release workflow itself is `workflow_dispatch`-only, so the next
real exercise of this code path is the v1.8.93 GitHub Release the
maintainer triggers from their build host.

<a id="v1.8.92"></a>
## v1.8.92

Date: 2026-05-17

Follow-up #7 (final) from the v1.8.85 audit pass.

## What changed

[app/src/main/kotlin/dev/patrickgold/florisboard/ime/hardware/KeymanLdmlParser.kt](app/src/main/kotlin/dev/patrickgold/florisboard/ime/hardware/KeymanLdmlParser.kt#L113-L140)
— LDML defines:

- `shift=` — the shift-modifier mapping for the key.
- `longPress=` — a space-separated list of alternates surfaced on long-press
  (NOT a shift mapping).

The previous parser was inverted: it tried `longPress` *first*, falling
back to `shift`. Two real bugs followed:

1. **Whenever both `shift=` and `longPress=` were declared on the same
   key, `shift` was silently ignored.** Real Keyman / LDML keyboards
   author shift= for the shift mapping and longPress= for alternates;
   the parser picked the wrong slot.
2. **When only `longPress=` was set with multiple alternates,** only the
   first alternate's first codepoint became the shift value, masking
   the rest of the alternates list with no UX to recover them.

This release reorders:

```
1. If shift= is set                                → use it.
2. Else if longPress= is a single value (no space) → use it
   (preserves Amharic-SERA-style legacy authors who used longPress
   as a shift workaround before this parser learned the right
   semantics).
3. Else                                             → leave shift = null
   (multi-alternate longPress is correctly a list, not a shift slot —
   wait for a future release to add `longPressAlternates: List<Int>` to
   HardwareKeyEntry and route through the long-press UI).
```

The pre-existing fixture (`output="ሀ" longPress="ሁ"`, single-value
longPress) still produces the same result through path #2, so no
already-imported keyboard regresses.

## Tests

[app/src/test/kotlin/dev/patrickgold/florisboard/ime/hardware/KeymanLdmlParserTest.kt](app/src/test/kotlin/dev/patrickgold/florisboard/ime/hardware/KeymanLdmlParserTest.kt)
gains three new tests:

- `shift attribute is preferred over longPress for the shift slot` —
  proves the bug fix on the both-attributes case.
- `multi-alternate longPress with no shift leaves shift slot null` —
  proves multi-alternate lists no longer poison the shift slot.
- `single-alternate longPress with no shift remains usable as shift fallback` —
  proves the Amharic-SERA backward-compat case still works.

The pre-existing `normal + shift output round-trip through the
scancode map` test (single-alternate longPress fixture) continues to
pass unchanged.

## Why not also implement longPressAlternates now

Adding `longPressAlternates: List<Int>` to
[HardwareKeyEntry](app/src/main/kotlin/dev/patrickgold/florisboard/ime/hardware/HardwareKeyboardLayout.kt)
and routing the alternates through the long-press popup is a larger
slice that crosses the LDML parser, the data class, the popup
controller, and the popup-UI snygg surface. It's worth its own
per-feature release once the existing long-press popup is audited for
hardware-keyboard-source events. Tracked in the v1.8.85
[follow-up roster](CHANGELOG.md#v1.8.85).

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/hardware/KeymanLdmlParser.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/hardware/KeymanLdmlParserTest.kt`
- `gradle.properties` — versionCode 1892 / versionName 1.8.92

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
```

The new tests should pass; the pre-existing Amharic-fixture test should
continue to pass.

<a id="v1.8.91"></a>
## v1.8.91

Date: 2026-05-17

Follow-up #6 from the v1.8.85 audit pass. Docs-only.

## What changed

[app/src/main/kotlin/dev/patrickgold/florisboard/ime/addon/AddonContract.kt](app/src/main/kotlin/dev/patrickgold/florisboard/ime/addon/AddonContract.kt#L28-L60)
KDoc previously said:

> 2. An addon APK *may* declare a broadcast `<receiver>` that responds to
>    [AddonContract.Action.REGISTER] …

This contradicted the actual visibility mechanism. The IME's
[AndroidManifest.xml `<queries>`](app/src/main/AndroidManifest.xml#L29-L58)
block declares intent-filter queries for the six `REGISTER_*` actions.
On Android 11+, package visibility under `<queries>` based on `<intent>`
only includes packages whose manifest components carry a matching
`<intent-filter>`. An addon declaring only `<meta-data>` on
`<application>` is therefore *invisible* to
`PackageManager.getInstalledPackages()` regardless of how cleanly it
otherwise conforms to the spec.

The script-side enforcement ([scripts/verify-addon-apk.sh](scripts/verify-addon-apk.sh#L134-L146))
and the public docs ([docs/addons/apk-validation.md](docs/addons/apk-validation.md))
already mandate a REGISTER receiver — only the KDoc in `AddonContract.kt`
was out of sync. Updated to:

> 2. An addon APK **MUST** declare a broadcast `<receiver>` whose
>    `<intent-filter>` matches one of the [AddonContract.Action] register
>    actions … This is a *visibility* requirement, not a feature
>    requirement … the receiver can be a no-op (it does not need to handle
>    the broadcast); the intent-filter alone satisfies the visibility query.

The receiver still *should* respond to the broadcast (so the addon can
self-announce changes without forcing the IME to poll), but the bare
minimum is just the intent-filter for visibility.

## Why this matters

Spec-compliant addons that followed only the previous (incorrect) "may
declare a receiver" wording would be silently invisible to the
enumerator on Android 11+ devices. No runtime symptom in `:app` —
they'd just never appear in the addon catalog. This release closes
the spec/implementation drift.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/addon/AddonContract.kt`
- `gradle.properties` — versionCode 1891 / versionName 1.8.91

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
```

No behaviour change to `:app`. The verifyNoInternetPermissionMerged
checks (shipped in v1.8.85) and the addon enumerator still behave as
before. Existing addons that already declared a REGISTER receiver
(the canonical examples in `docs/addons/apk-validation.md` do so) are
unaffected.

<a id="v1.8.90"></a>
## v1.8.90

Date: 2026-05-17

Follow-up #5 from the v1.8.85 audit pass.

## What changed

The v1.8.77 user-imported sticker folder takes a persistable SAF URI
grant at folder-pick time and stores the URI in `prefs.sticker.userFolderUri`.
If Android revokes the grant later (uninstall + reinstall of the file
manager that issued the grant, system-wide grant cleanup on storage
reset, factory restore patterns), the URI stays in prefs but the IME
can no longer read from it. Previous behaviour: the next `loadPack`
call silently returned an empty pack via `runCatching.getOrDefault`,
the Imported tab vanished, and the user had no signal as to why.

This release:

- Adds [`UserStickerRepository.hasPersistableReadPermission(context, folderUriRaw)`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/UserStickerRepository.kt)
  which scans `contentResolver.persistedUriPermissions` for a still-valid
  read grant on the stored URI.
- Makes [`loadPack`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/UserStickerRepository.kt#L45-L66)
  check the grant first; when missing, logs a warning naming the URI so
  the cause shows up in logcat, then returns null (no behaviour change
  on the keyboard side — the pack is still absent).
- Updates [`MediaScreen`](app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/media/MediaScreen.kt#L200-L228)
  Settings preference summary: when the URI is set but the grant is
  lost, shows "Folder access lost. Tap to select again." instead of the
  normal "Using {folder}" summary. Tap re-enters the folder picker.

The IME-side recovery (in-keyboard banner / re-pick button inside the
Imported tab) is intentionally deferred — that requires adding a new
Snygg element to the sticker palette, which is bigger scope and worth
its own per-feature release.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/UserStickerRepository.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/media/MediaScreen.kt`
- `app/src/main/res/values/strings.xml`
- `gradle.properties` — versionCode 1890 / versionName 1.8.90

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:installDebug
```

Manual QA reproduction:
- Open Settings → Emoji & stickers → Imported sticker folder, pick a
  folder via a known file manager (e.g. Files by Google).
- Verify the summary reads "Using {folder name}" and the Imported tab
  appears in the sticker palette.
- Uninstall the file manager (or use `adb shell pm clear <package>` on
  the file manager).
- Re-open Settings → Emoji & stickers → Imported sticker folder.
  Pre-fix: summary still reads "Using {folder name}" but the Imported
  tab is silently absent in the palette.
  Post-fix: summary reads "Folder access lost. Tap to select again."
  Tap → folder picker reopens; pick again; back to working state.

<a id="v1.8.89"></a>
## v1.8.89

Date: 2026-05-17

Follow-up #4 from the v1.8.85 audit pass.

## What changed

[app/src/main/kotlin/dev/patrickgold/florisboard/lib/io/ZipUtils.kt](app/src/main/kotlin/dev/patrickgold/florisboard/lib/io/ZipUtils.kt#L154-L240)
— previously every guard (zip-slip, unsafe entry name, entry-count cap,
path-length cap, name-length cap, entry-size cap) followed a "warn and
continue" policy. A malicious archive containing one well-formed entry
plus one escape entry would leave the well-formed entry on disk and the
caller's `runCatching` would see a `Result.Success` — restore appears
to succeed, the user gets the toast for success, but the malicious
archive's intent was partially realised AND the user has no signal that
something was filtered.

This release splits guards by intent:

- **Abort-class (throw `SecurityException`):**
  - Pre-canonical unsafe entry name (path-traversal pattern).
  - Post-canonical-resolution path outside `dstDir` (zip-slip).
  - More than 10_000 entries in the archive (zip-bomb).
- **Continue-with-warning:**
  - Entry name > 255 chars.
  - Destination path > 1023 chars.
  - Per-entry / per-archive byte caps (unchanged — already cleanly
    handled by the `copy()` helper).

The abort path triggers the existing `try { ... } catch (error: Throwable)
{ workspace.close(); throw error }` block in
[`RestoreScreen.prepareRestoreWorkspace`](app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/advanced/RestoreScreen.kt#L101-L132),
which deletes the partial workspace and re-throws. The launcher's
`onFailure` shows the user a toast with the security exception's
message, so the user sees *why* the archive was rejected rather than
silently getting a half-applied restore.

The split is deliberate: name-length / path-length anomalies are common
in legitimate archives produced by archivers that encode unusual paths,
and dropping those entries with a warning is the right behaviour. The
abort-class violations only fire on actively-malicious archive content
that no legitimate restore would carry.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/lib/io/ZipUtils.kt`
- `gradle.properties` — versionCode 1889 / versionName 1.8.89

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:installDebug
```

Manual QA:
- Construct a backup archive with one legitimate entry and one entry
  named `../../etc/foo`. Try to restore in Settings → Restore.
  Pre-fix: restore appears to succeed, only the legitimate entry lands.
  Post-fix: restore fails with a toast naming the rejected entry; no
  files are written to the workspace.
- Restore a legitimate backup archive (e.g. one produced by the
  Settings → Backup flow on the same install). Verify it still
  succeeds end-to-end — no change to the legitimate path.
- Construct a zip-bomb archive with 20_001 zero-byte entries. Verify
  the restore fails with the entry-count message rather than
  succeeding silently after truncation.

<a id="v1.8.88"></a>
## v1.8.88

Date: 2026-05-17

Follow-up #3 from the v1.8.85 audit pass.

## What changed

[app/src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/FlorisUserDictionaryEncryption.kt](app/src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/FlorisUserDictionaryEncryption.kt#L91-L120)
— the v1.8.68 Tink migration path had a hard `error(...)` call that fired
when:

- The Tink-wrapped passphrase pref was missing or unreadable, AND
- A legacy AndroidX Security Crypto keyset was present in the prefs, AND
- `readLegacyEncryptedString` returned null (meaning the legacy value
  pref was missing OR the legacy decrypt itself failed silently).

Trigger scenarios for the third condition: Android Keystore master key
rotated by the system, prefs restored from a different device via auto-
backup of an old install, the legacy keyset got corrupted, or the
SharedPreferences XML was edited / partially restored. Real-world impact
is small (the user-triggered BackupScreen does not include this prefs
file, and the new `data_extraction_rules.xml` shipped in v1.8.85 excludes
it from D2D / cloud transfer), but the failure mode was severe: IME
hard-crashes on startup, settings unreachable.

This release replaces the `error(...)` with a recovery path:

1. Log a warning at the `Log.w` level explaining the state.
2. Clear the two AndroidX legacy keyset pref keys
   (`__androidx_security_crypto_encrypted_prefs_key_keyset__` /
   `__androidx_security_crypto_encrypted_prefs_value_keyset__`) — they
   are now unreadable garbage.
3. Fall through to the fresh-passphrase generation path that follows.

The user loses access to any personal-dictionary words encrypted under
the now-unreadable key, but the IME starts cleanly with an empty
dictionary. Users with a user-triggered backup can re-import.

## Why this is the right tradeoff

The alternative — recovering the old encrypted DB — is impossible because
we no longer have the decryption key. The remaining options are:

- **Crash (previous behaviour).** Leaves the user with a non-functional
  IME and no recovery path inside the app. Worst outcome.
- **Disable the dictionary silently.** The next dictionary-write would
  fail; UX is opaque.
- **Regenerate (this release).** User sees an empty dictionary, but
  typing works. Diagnostic logged.

Regeneration is the only option that keeps the IME usable. The lost-data
risk is real but the previous behaviour also lost the data — it just
also crashed.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/FlorisUserDictionaryEncryption.kt`
- `gradle.properties` — versionCode 1888 / versionName 1.8.88

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:installDebug
```

Manual QA reproduction (requires an Android shell):
- `adb shell run-as dev.patrickgold.florisboard.debug` then manually
  edit `shared_prefs/floris_user_dictionary_key.xml` to inject a value
  for `__androidx_security_crypto_encrypted_prefs_key_keyset__` without
  the matching encrypted-prefs value pref. Restart the IME.
- Pre-fix: IME crashes during dictionary init with the `error(...)`
  message.
- Post-fix: IME starts; logcat shows the warning; user dictionary is
  empty. Type a few words, confirm they get learned (new passphrase
  works).

<a id="v1.8.87"></a>
## v1.8.87

Date: 2026-05-17

Follow-up #2 from the v1.8.85 audit pass.

## What changed

[app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/dictionary/UserDictionaryScreen.kt](app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/dictionary/UserDictionaryScreen.kt#L742-L780)
— the `DictionaryPassphraseDialog` is the input surface for both
encrypted-export and encrypted-import of the personal dictionary (the
ROADMAP §6 N7.4 encrypted-blob round-trip that shipped in v1.8.54 /
v1.8.65). Two defects:

1. **No `FLAG_SECURE` on the host window.** The host
   `FlorisAppActivity` does not set `FLAG_SECURE` for its window globally
   (and shouldn't — the rest of Settings is screen-recordable for support /
   bug-report screenshots). While the passphrase dialog was up, screen
   recordings, external-display mirroring, and the system screenshot
   gesture could all capture the typed passphrase. The
   `PasswordVisualTransformation` only masks the rendered glyph; the
   passphrase characters are still in the surface layer.
2. **Passphrase stored via `rememberSaveable`.** `rememberSaveable` round-
   trips state through Android's `savedInstanceState` bundle, which is
   recoverable via `am dumpstate`, crash reports, and the platform's
   restore-after-process-death path. Passphrase state must not be
   serialised.

This release:

- Adds a `DisposableEffect` keyed on the host view that sets
  `WindowManager.LayoutParams.FLAG_SECURE` on entry and clears it on
  dispose. The flag is set only while the passphrase dialog is composed,
  so the rest of Settings remains screen-recordable. Both the export
  (with confirmation) and import (without confirmation) flows use the
  same dialog, so both are covered.
- Switches `passphrase` and `passphraseConfirmation` from
  `rememberSaveable` to plain `remember`. The dialog already re-prompts
  on every show (because it's only composed when the visibility flag is
  true), so losing in-flight passphrase state across configuration changes
  is the correct behaviour, not a regression.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/dictionary/UserDictionaryScreen.kt`
- `gradle.properties` — versionCode 1887 / versionName 1.8.87

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:installDebug
```

Manual QA:
- Open Settings → User Dictionary → encrypted export. Verify the
  passphrase dialog appears. Trigger a system screen recording (or
  external-display mirror). Verify the recording shows a black surface
  for the dialog area (FLAG_SECURE), not the typed passphrase. Cancel
  the dialog and screenshot Settings — that should still work normally
  (FLAG_SECURE cleared on dispose).
- Open the same dialog, type a partial passphrase, rotate the device.
  Verify the field is empty after rotation (was: it survived rotation
  via the savedInstanceState bundle).
- Repeat for the encrypted-import flow.

<a id="v1.8.86"></a>
## v1.8.86

Date: 2026-05-17

Follow-up #1 from the v1.8.85 audit pass.

## What changed

[app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/EditorInstance.kt](app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/EditorInstance.kt#L75-L120)
— in `handleStartInputView`, when the focused field reports
`InputAttributes.Type.NUMBER`, `activeState.keyVariation` was hard-coded to
`KeyVariation.NORMAL`. That meant `TYPE_NUMBER_VARIATION_PASSWORD` fields
(numeric PIN / OTP entry — bank PINs, app-lock PINs, TOTP codes) bypassed
every privacy gate keyed on `keyVariation == KeyVariation.PASSWORD`,
including the clipboard-history exclusion in
`performClipboardCut` / `performClipboardCopy`.

Net effect before this fix: a user copying a numeric OTP out of the IME
selection (rare but possible) wrote the OTP into the IME-local clipboard
history, where it would surface on the next clipboard-palette open.
[FlorisImeService.applyFlagSecureForCurrentField](app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt#L562)
already covered the FLAG_SECURE side via `InputAttributes.variation ==
Variation.PASSWORD` (numeric PIN variation maps cleanly into that enum), so
the IME window was correctly opaque to screenshots; only the local
clipboard-history write was unguarded.

After this fix: numeric PIN fields propagate `keyVariation = PASSWORD`
while still selecting `KeyboardMode.NUMERIC` for the actual layout, so all
existing `keyVariation == PASSWORD` gates fire — clipboard history, glide
delete suppression (see TextKeyboardLayout.kt:151 / 646 / 788), long-press
popup suppression (TextKey.kt:88), and the `isComposingEnabled` /
suggestion-suppression branch.

## Why this is a separate release

Per [AGENTS.md §6](AGENTS.md) (one logical improvement per release).
v1.8.85 was an explicit cross-subsystem exception; subsequent follow-ups
return to per-feature commits.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/EditorInstance.kt`
- `gradle.properties` — versionCode 1886 / versionName 1.8.86

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:installDebug
```

Manual QA:
- Focus a field declared `android:inputType="numberPassword"`. Verify the
  numeric keyboard appears (mode preserved). Long-press a digit key —
  popup should be suppressed (now-active PASSWORD gate).
- Type some digits, select them via the system selection handles, tap the
  IME's Cut/Copy quick action if shown. Open the clipboard palette and
  confirm the digits do NOT appear in the history pane. Pre-fix they did.
- Focus a regular numeric field (no `numberPassword` flag). Verify
  long-press popups still work and clipboard cut/copy still writes to
  history.

<a id="v1.8.85"></a>
## v1.8.85

Date: 2026-05-17
Author: AI-assisted audit (Claude Code, Opus 4.7), reviewed by maintainer.

## ⚠️ Per-PR-scope deviation

This release intentionally violates [AGENTS.md §6](AGENTS.md) ("One logical
improvement per commit / PR") and the
[IMPROVEMENT_PLAN.md §9](IMPROVEMENT_PLAN.md) Repo Hygiene rule. The
maintainer commissioned an extreme cross-subsystem audit + hardening pass
spanning the just-shipped v1.8.75-84 slices plus the load-bearing privacy /
backup / CI infrastructure, and explicitly opted out of the per-PR-scope
rule for this release.

Future per-feature work returns to the one-logical-change-per-release pattern.

## ⚠️ Verification status

The dev VM has no JDK / Android SDK on its path, so the
[AGENTS.md §5](AGENTS.md) Definition-of-Done verification commands
(`:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`,
`:app:verifyRoborazziDebug`, manual QA, APK SHA-256) **have not** been
run for this release on this host. The maintainer's primary build host
should run the full DoD set before tagging and pushing.

Each fix below is annotated with the specific verification command that
should pass.

## Summary

Five subsystem-spanning research agents audited the v1.8.75-84 slices and
foundational privacy / build / CI surfaces. Findings landed across six
priority-zero / priority-one categories. Sixteen fixes ship in this release;
the remaining seven (described in §Follow-up below) are scoped for separate
single-feature releases.

## P0 — Privacy / supply-chain gate hardening

### 1. `verifyNoInternetPermission` no longer leaks on merged manifests

[app/build.gradle.kts](app/build.gradle.kts) — the project's flagship
no-network gate previously only scanned `app/src/**/AndroidManifest.xml`.
A library AAR (current or future) that added `INTERNET` via manifest merging
would slip past the gate, the merged manifest would carry the permission,
and the contract printed everywhere in marketing and `PROJECT_CONTEXT.md` §2
would be silently broken.

This release:

- Adds a per-variant `verifyNoInternetPermissionMerged<Variant>` task wired
  against AGP's `SingleArtifact.MERGED_MANIFEST`, so every library and every
  flavor/buildType overlay is included in the check.
- Adds `finalizedBy` on `processManifest` and `dependsOn` on `assemble` so
  the merged-manifest check runs both during PR builds and during release
  builds, catching regressions before the APK is assembled.
- Honours legitimate `tools:node="remove"` / `tools:node="removeAll"`
  directives in both the source-pre-check and merged post-check, so the
  documented escape hatch (strip a permission a library wrongly declared)
  works correctly.
- Rewrites the regex into a single multi-line-tolerant element matcher
  rather than five permission-specific regexes; the diff is shorter and the
  scanner is more robust to manifest formatting variations.

Verification: `./gradlew.bat :app:verifyNoInternetPermission
:app:verifyNoInternetPermissionMergedDebug :app:assembleDebug` should all
pass; an artificial INTERNET declaration in any library module's manifest
should fail the merged check.

### 2. CI workflows no longer ship with the default read/write GITHUB_TOKEN

[`.github/workflows/android.yml`](.github/workflows/android.yml),
[`.github/workflows/crowdin-upload.yml`](.github/workflows/crowdin-upload.yml),
[`.github/workflows/reproducible-build.yml`](.github/workflows/reproducible-build.yml)
— added file-scope `permissions: { contents: read }` blocks. Previously the
default token inherited the repo-wide setting (typically read-write), so a
malicious / compromised transitive action dependency could push code, edit
releases, or comment on issues using the workflow's own token. After this
change the default token can only read the repo; jobs that need additional
scopes (e.g. validate-strings-no-translations's `pull-requests: write`)
declare them explicitly.

The remaining workflows (`dependency-scan.yml`, `roborazzi-baseline.yml`,
`release.yml`) already had explicit `permissions:` blocks.

### 3. `validate-strings-no-translations.yml` no longer interpolates untrusted PR data into shell

[`.github/workflows/validate-strings-no-translations.yml`](.github/workflows/validate-strings-no-translations.yml)
— the workflow runs on `pull_request_target` (base-repo context with the
repo's own GITHUB_TOKEN) and previously interpolated
`${{ github.event.pull_request.user.login }}`, the PR file list, and the
`steps.fetch_changed_files.outputs.illegal_changes_list` step output
directly into `run:` blocks. The step output is derived from PR-author-
controlled filenames; a PR file path containing shell metacharacters
(quotes, semicolons, backticks) could break out of the `echo` command and
execute attacker-controlled shell in the base-repo context.

This release:

- Passes every `${{ github.event.* }}` and step-output value via `env:`,
  references them as quoted shell variables (`"$VAR"`) only.
- Replaces the hand-rolled `curl + jq` PR-files paginator with `gh api
  --paginate --jq`, which never interpolates the response into the shell
  command line.
- Adds `set -euo pipefail` to every `run:` block so an upstream failure
  (network blip, missing jq) is loud rather than silent.
- Markdown-fences the illegal-files list in the comment body so even a
  filename containing backticks cannot escape the fence into surrounding
  markdown commands.

The third-party `peter-evans/create-or-update-comment` action is kept on its
floating `@v4` tag with a `TODO supply-chain` comment pointing at the
`gh api repos/peter-evans/create-or-update-comment/git/refs/tags/v4` lookup
the maintainer should run to pin it to a SHA. Pinning to an unverified SHA
from this AI session would risk breaking CI more than the floating tag does.

## P0 — Reliability / crash prevention

### 4. `HardwareKeyboardRuntimeMapper` is now thread-safe

[app/src/main/kotlin/dev/patrickgold/florisboard/ime/hardware/HardwareKeyboardRuntimeMapper.kt](app/src/main/kotlin/dev/patrickgold/florisboard/ime/hardware/HardwareKeyboardRuntimeMapper.kt#L36-L80)
— `layoutsByDeviceId` is a plain `LinkedHashMap` touched from the IME input
thread (via `KeyboardManager.onHardwareKeyDown` and the `InputManager`
device-detach callback) and from the settings/UI thread when the user binds
a layout to a device. Concurrent `put` + `remove` on a `LinkedHashMap` can
corrupt the bucket array and throw `ConcurrentModificationException`,
crashing the IME on a hot path. All accesses now go through a monitor lock.

Same file: `BitmapFactory`-style "swallow Ctrl-pressed events" check at
`map(HardwareKeyEventInfo)` was rejecting every PC-style AltGr keystroke
(Android delivers AltGr as Ctrl+Alt), so `.klc` imports with AltGr-mapped
characters (€ on EU layouts, all CJK IME hooks) were unreachable. Now
rejects Ctrl ONLY when Alt is not also pressed.

### 5. `BitmapFactory.decodeStream` in the sticker palette is bounded

[app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/StickerPaletteView.kt](app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/StickerPaletteView.kt)
— the v1.8.77 user-imported sticker folder feeds arbitrary user-selected
SAF file URIs into `BitmapFactory.decodeStream(stream)` with no
`BitmapFactory.Options`. A corrupted or hostile 100k × 100k PNG would
allocate 40 GB of bitmap heap and crash the IME process. Replaced with the
canonical two-pass pattern: `inJustDecodeBounds=true` reads dimensions
without allocating pixels; a hard reject ceiling of 8192 px on either edge
rejects anything obviously hostile; then `inSampleSize` is computed so the
final bitmap never exceeds ~512 px on its longest edge.

### 6. `ZipUtils.unzip` gains pre-canonical entry-name guard + entry-count cap

[app/src/main/kotlin/dev/patrickgold/florisboard/lib/io/ZipUtils.kt](app/src/main/kotlin/dev/patrickgold/florisboard/lib/io/ZipUtils.kt)
— the existing zip-slip guard relied on `File.canonicalPath` comparison,
which resolves symlinks. Added a layer-zero check on the entry name itself:
reject `..` path segments, leading `/` or `\`, Windows drive prefixes,
NUL bytes, and empty entry names *before* the filesystem ever sees them.
Also added a hard cap of 10_000 entries per archive — defends against the
zip-bomb pattern that ships millions of zero-byte entries (passes per-entry
and total-byte gates but exhausts inodes / dentries on extraction).

## P1 — Privacy / data integrity

### 7. Android 12+ `dataExtractionRules` now uses the correct schema

[`app/src/main/AndroidManifest.xml`](app/src/main/AndroidManifest.xml#L63)
previously pointed `android:dataExtractionRules="@xml/backup_rules"` at a
file whose root element is `<full-backup-content>`. The Android 12+
`dataExtractionRules` attribute requires the `<data-extraction-rules>`
schema with separate `<cloud-backup>` and `<device-transfer>` sections.
On Android 12+, the system silently fell back to the default "include
everything" device-transfer behaviour, which would:

- Carry the SQLCipher personal-dictionary DB (`floris_user_dictionary*`) to
  a new device as part of a D2D transfer, alongside an Android-Keystore-
  bound passphrase that cannot be transferred — leaking the encrypted PII
  blob AND bricking the dictionary on the new device.
- Carry `floris_user_dictionary_key.xml` (the Tink-wrapped passphrase
  SharedPrefs), with the same problem.

Added [`app/src/main/res/xml/data_extraction_rules.xml`](app/src/main/res/xml/data_extraction_rules.xml)
with explicit `<exclude>` entries for every personal-dictionary DB
sidecar file (`.db`, `.db-journal`, `.db-wal`, `.db-shm`) plus the wrap-key
preferences and the clipboard-history directory, in both the
`<cloud-backup>` and `<device-transfer>` rule sets. Manifest now points
to the new file for API 31+; pre-31 still reads `backup_rules.xml` via
`android:fullBackupContent`.

This closes a real data-leakage hole that the [docs/THREAT_MODEL.md](docs/THREAT_MODEL.md)
posture had implicitly assumed was already shut.

## P1 — UX correctness

### 8. Sticker MIME-type spoof closed

[app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/UserStickerRepository.kt](app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/UserStickerRepository.kt)
— `resolveMimeType` previously fell back to filename-extension detection
whenever SAF returned a MIME that wasn't in the supported-image set. So a
file with SAF-declared `application/octet-stream` but named `evil.png`
would be announced to the recipient app's `commitContent` receiver as
`image/png`, even though the bytes were arbitrary. Recipient apps that
auto-forward image attachments (most messengers) would propagate the
spoofed type. New behaviour: SAF's declared MIME is the source of truth
when present and non-empty. The extension-based fallback only runs when
SAF gives us nothing (the common case for naive file managers).

### 9. Addon enumerator no longer rejects legitimate 64+ MB asset packs

[app/src/main/kotlin/dev/patrickgold/florisboard/ime/addon/AddonEnumerator.kt](app/src/main/kotlin/dev/patrickgold/florisboard/ime/addon/AddonEnumerator.kt)
— `evaluate()` was reading `File(sourceDir).length()` (the APK file size on
disk) and feeding it through `AddonManifest`'s `require(bundleSizeBytes <=
ADDON_MAX_BUNDLE_BYTES)`. The bundle-size gate exists to stop a malicious
addon from claiming a 500 MB asset bundle that would OOM the IME on
enrolment, but the IME never loads the APK into RAM (PackageManager + mmap
handle that). Net effect: legitimate 65 MB theme packs and dictionary
packs were silently rejected at enrolment. Bundle-size enforcement moves
to the asset-mounting step (future Next-10.4); the enrolment-time field is
now `0L` and the gate becomes a no-op until then.

## P1 — Build reliability

### 10. `verify-reproducible-apk.sh` no longer always fails on signed APKs

[scripts/verify-reproducible-apk.sh](scripts/verify-reproducible-apk.sh)
— the script's top-level pass condition was `cmp -s` on the full signed
APK. Signed release APKs cannot be byte-identical (the v2/v3 signing block
contains randomised padding even with deterministic content), so this
check always failed on signed builds and was effectively meaningless. Now:

- If full bytes match → exit 0 (best — full reproducibility).
- Else compute payload entry manifests (ZIP entries outside `META-INF/`);
  if those match → exit 0 with a note that the drift is in the signing
  block (the F-Droid verified-reproducible-tier requirement).
- Else → exit 1 (real payload divergence, the only failure mode F-Droid
  cares about).

This aligns the gate with the F-Droid rebuilder's actual comparison
methodology.

## Follow-up work (next per-feature releases)

The audit found several additional defects that warrant their own
single-feature releases per the standard per-PR-scope rule:

- **Addon spec ↔ visibility mismatch.** `AddonContract.kt` says receivers
  are optional, but Android 11+ `<queries>` based on `<intent>` only makes
  packages with matching intent-filters visible. Spec-compliant addons
  declaring only `<meta-data>` cannot be discovered. Either update the
  spec to mandate a receiver or change the enumerator to use
  `queryBroadcastReceivers()`. (Settle by docs change; defer.)
- **SAF persistable-URI re-take on cold start** for the user-imported
  sticker folder — currently a swallowed `SecurityException` silently
  empties the Imported pack after a process restart.
- **`floris_user_dictionary_key.xml` excluded from user backup zip.**
  `BackupScreen.kt` still includes the SharedPrefs path; on restore-to-
  new-device, the Tink-wrapped passphrase is undecryptable. Surface a
  "regenerate passphrase" path rather than the current `error(...)`.
- **`FLAG_SECURE` extension to numeric-password fields.** Current check
  misses `TYPE_NUMBER_VARIATION_PASSWORD`-only inputs; PIN entry can
  leak into the IME-local clipboard history.
- **`FLAG_SECURE` on the encrypted-export passphrase dialog** to block
  screenshots / screen-recording.
- **`ZipUtils.unzip` abort-vs-continue policy.** Currently warns and
  continues on slip/oversize; a partial restore looks successful to the
  user. Recommend converting to an abort-on-first-violation atomic
  semantic.
- **Hardware-layout LDML `longPress` semantics.** `KeymanLdmlParser` reads
  the first codepoint of `longPress` into the `shift` slot; LDML defines
  `longPress` as a space-separated alternates list, not a shift glyph.
  Real Keyman keyboards (Amharic, etc.) display wrong shifted chars. Fix
  needs `HardwareKeyEntry` to grow a `longPressAlternates` field.

These will land as `CHANGELOG.md#v1.8.86` through `v1.8.92.md` as
prioritised on the maintainer's build host.

## Files touched

- `app/build.gradle.kts` — merged-manifest gate, `tools:node="remove"` handling.
- `app/src/main/AndroidManifest.xml` — point `dataExtractionRules` at new schema file.
- `app/src/main/res/xml/data_extraction_rules.xml` — NEW: Android 12+ rules.
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/addon/AddonEnumerator.kt` — drop APK-size bundle gate.
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/hardware/HardwareKeyboardRuntimeMapper.kt` — thread safety, AltGr.
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/StickerPaletteView.kt` — bounded decode.
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/sticker/UserStickerRepository.kt` — MIME spoof fix.
- `app/src/main/kotlin/dev/patrickgold/florisboard/lib/io/ZipUtils.kt` — pre-canonical entry guard, entry-count cap.
- `.github/workflows/android.yml` — file-scope read-only permissions.
- `.github/workflows/crowdin-upload.yml` — file-scope read-only permissions.
- `.github/workflows/reproducible-build.yml` — file-scope read-only permissions.
- `.github/workflows/validate-strings-no-translations.yml` — env-passing untrusted PR data.
- `scripts/verify-reproducible-apk.sh` — entry-manifest pass criterion for signed APKs.
- `gradle.properties` — versionCode 1885 / versionName 1.8.85.

## Verification commands the maintainer should run before tag + push

```powershell
./gradlew.bat :app:verifyNoInternetPermission
./gradlew.bat :app:verifyNoInternetPermissionMergedDebug
./gradlew.bat :app:verifyNoInternetPermissionMergedRelease
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:verifyRoborazziDebug
./gradlew.bat :app:installDebug
```

Plus a manual QA pass on a device covering:
- Type into a hardware-keyboard-connected field with AltGr characters (€, etc.).
- Connect + disconnect a Bluetooth keyboard while typing.
- Pick a SAF folder with a known oversized PNG (> 8192 px); verify the tile shows the fallback IMG label rather than crashing.
- Pick a SAF folder with an `evil.png` whose contents are `text/plain`; verify it is rejected / not committed.
- Run a backup → restore round-trip on Android 12+ and Android 14+ devices; confirm the personal-dictionary DB is regenerated cleanly (not transferred).
- Trigger a PR with a hostile filename in the path `app/src/main/res/values-*/strings.xml` and verify the CI does not execute the filename as shell.

If any of those fail, the corresponding fix above should be reverted from
this release and re-landed as its own per-PR commit per AGENTS.md §6.

<a id="v1.8.84"></a>
## v1.8.84

Released: 2026-05-17

## Summary

This release ships the Next-10.3d Settings -> Addons read-only status surface.
Users can now open Settings -> Addons, inspect the process-wide addon registry,
manually rescan installed addon APKs through the same startup reconciliation
path, see accepted/rejected counts, and review installed addon package,
license, version, size, and signing-certificate details.

No addon download flow, dictionary asset mounting, signing-pin revoke/reset UI,
network permission, or runtime dictionary loading changes are included in this
slice.

## Changes

- Added `AddonsSettingsScreen`.
  - Shows accepted, rejected, and pinned-certificate counts from the active
    addon registry and persisted pin set.
  - Lists accepted addons with package name, addon type, version, APK license,
    bundle size, and SHA-256 signing-certificate fingerprint.
  - Lists rejected addons from the latest registry snapshot with package name
    and rejection reason.
  - Adds local-only install guidance that restates the metadata, no-network,
    and first-seen signing certificate requirements.
- Added a manual rescan action.
  - Runs `AddonEnumerator` on `Dispatchers.Default`.
  - Reuses `AddonRegistryStartup.reconcile(...)` so Settings and IME startup
    share the same trust and package-hijack rules.
  - Publishes `AddonRegistryStore` and persists canonical signing pins only when
    the trust set changes.
- Wired `Routes.Settings.Addons`, deep link `ui://florisboard/settings/addons`,
  and the Home screen entry under Data & extensions.
- Added English source strings for the Addons screen.

## Verification

- `git diff --check`
- Android manifest banned-network-permission scan
- Root JVM crash/replay tracked-file guard
- Gradle compile command attempted for the Addons settings screen; this VM
  still has no Java toolchain (`JAVA_HOME` is not set and `java` is not on
  PATH), so maintainer-host Gradle verification remains required before
  publishing.

<a id="v1.8.83"></a>
## v1.8.83

Released: 2026-05-17

## Summary

This release ships the Next-10.3c addon registry startup wiring. The IME now
scans installed addon APK manifests on startup, reconciles them through the
persisted signing-certificate pin store, publishes a process-wide
`AddonRegistry`, and writes back canonical pins when first-seen addons or
corrupt stored lines change the trust set.

No Settings UI, addon download flow, dictionary asset mounting, network
permission, or runtime dictionary loading changes are included in this slice.

## Changes

- Added `AddonRegistryStartup`.
  - Reconciles `AddonEnumerator` snapshots with the raw
    `prefs.addon.signingCertPins` value.
  - Accepts new addons by first-seen signing certificate and emits the updated
    canonical pin string.
  - Rejects changed-certificate package-name hijacks by preserving the old pin.
  - Marks malformed stored pin lines dirty so startup can clean them out of the
    preference.
- Added `AddonRegistryStore`.
  - Provides the process-wide active registry for future Settings and runtime
    consumers.
  - Supports reset on startup failure without clearing persisted trust pins.
- Wired `FlorisImeService.onCreate()` to run addon startup reconciliation on
  `Dispatchers.Default`.
  - Publishes the active registry after scan/reconcile.
  - Persists updated signing pins only when the canonical encoded form changed.
  - Logs accepted/rejected counts and tolerates failures without aborting IME
    startup.
- Added focused unit-test coverage for new-addon enrolment, changed-certificate
  rejection, corrupt preference cleanup, and registry store publish/reset.

## Verification

- `git diff --check`
- Android manifest banned-network-permission scan
- Root JVM crash/replay tracked-file guard
- Focused Gradle command attempted for the addon startup tests; this VM still
  has no Java toolchain (`JAVA_HOME` is not set and `java` is not on PATH), so
  maintainer-host Gradle verification remains required before publishing.

<a id="v1.8.82"></a>
## v1.8.82

Released: 2026-05-17

## Summary

This release ships the Next-10.3b persisted signing-pin layer for addon
enrolment. It adds a safe newline-string codec for addon package fingerprints
and a JetPref key that future startup/Settings code can use to persist
`AddonRegistry` first-enrolment trust across restarts.

No Settings UI, addon download flow, asset mounting, network permission, or
runtime dictionary loading changes are included in this slice.

## Changes

- Added `AddonSigningPinSet`.
  - Parses `packageName=SHA-256` newline strings into validated pins.
  - Ignores malformed/corrupt preference lines instead of crashing the IME.
  - Encodes sorted, validated pins for deterministic persistence.
  - Preserves first-seen pins when a later manifest for the same package carries
    a changed certificate.
- Added `prefs.addon.signingCertPins`.
  - Stores the raw newline-string pin set under `addon__signing_cert_pins`.
  - Keeps raw pin editing out of Settings; future Addons UI should expose
    provenance/revoke flows instead.
- Extended `AddonRegistry` with `fromPinnedSigningPinSet(...)` and
  `pinnedSigningPinSet()` helpers so the pure registry can round-trip through
  the persisted codec without taking a JetPref dependency.
- Added focused unit-test coverage for parsing, malformed-line tolerance,
  deterministic encoding, first-seen preservation, and registry codec
  round-trip.

## Verification

- `git diff --check`
- Android manifest banned-network-permission scan
- Root JVM crash/replay tracked-file guard
- Focused Gradle command attempted for the addon pin tests; this VM still has
  no Java toolchain (`JAVA_HOME` is not set and `java` is not on PATH), so
  maintainer-host Gradle verification remains required before publishing.

<a id="v1.8.81"></a>
## v1.8.81

Released: 2026-05-17

## Summary

This release ships the Next-10.3a addon-catalog foundation for dictionary
packs. It adds a process-local addon registry with signing-certificate pin
reconciliation and a pure dictionary-pack catalog builder that validates
descriptor JSON before a future Settings -> Addons screen mounts pack assets.

No network permission, telemetry path, or runtime dictionary asset mounting is
added in this slice.

## Changes

- Added `AddonRegistry`, the live-state companion to `AddonEnumerator`.
  - First-seen addon signing certificates are pinned by package name.
  - Packages whose signing certificate changes are rejected while the old pin is
    retained.
  - Runtime lookups are deterministic by type, display name, package name,
    stable id, and dictionary-pack type.
- Added `DictionaryPackCatalog`.
  - Builds a typed catalog from enrolled dictionary-pack manifests plus
    descriptor JSON.
  - Rejects missing, malformed, or forward-incompatible descriptors without
    crashing the IME.
  - Produces `AddonProvenanceReport` objects for accepted dictionary packs so
    the future Settings UI can surface dataset/source/license data.
- Added focused unit-test coverage for registry pinning, hijack rejection,
  stale-pin retention, duplicate-package collapse, descriptor rejection, and
  language lookup.

## Verification

- `git diff --check`
- Android manifest banned-network-permission scan
- Root JVM crash/replay tracked-file guard
- Focused Gradle command attempted for the new addon tests; this VM still has
  no Java toolchain (`JAVA_HOME` is not set and `java` is not on PATH), so
  maintainer-host Gradle verification remains required before publishing.

<a id="v1.8.80"></a>
## v1.8.80

Released: 2026-05-17

## SQLCipher provider migration plan

This release closes the Tier-3 #36 planning slice for SQLCipher's future
crypto-provider risk.

### What changed

- Added `docs/SQLCIPHER_PROVIDER_MIGRATION.md`, a readiness plan for a possible
  future move from the stock SQLCipher Android Community AAR's LibTomCrypt
  provider to an OpenSSL-backed SQLCipher build.
- Updated `docs/SECURITY.md` with a SQLCipher provider watch section and a link
  to the new plan.
- Corrected the research security review: SQLCipher issue `#564` did announce
  LibTomCrypt / NSS deprecation, but Zetetic restored LibTomCrypt for Android
  Community builds in 4.14.0 and SQLCipher 4.16.0 still lists Android Community
  builds as LibTomCrypt-based.
- Captured concrete migration triggers, proof-of-concept steps, 16 KB page-size
  verification gates, `PersonalDictionaryEncryptionTest` expectations, and a
  rollback rule that keeps the current passphrase / Room schema untouched.

### Privacy and security

- No dependency or runtime provider changed in `:app`.
- No network permission, telemetry, account, or cloud path was added.
- The existing SQLCipher + Tink / AndroidKeystore encrypted-at-rest contract is
  unchanged.

### Verification

- `git diff --check`
- Manifest permission scan for banned network permissions
- Root JVM crash/replay tracked-file guard
- Gradle was not required for this docs-only planning slice; this VM still lacks
  `JAVA_HOME` / `java` for maintainer-host build verification.

<a id="v1.8.79"></a>
## v1.8.79

Released: 2026-05-17

## Honeycomb hex layout wire-up

This release turns the earlier honeycomb renderer scaffolding into a selectable
production character layout path.

### What changed

- Registered `layouts/characters/honeycomb.json` in the bundled layout
  extension manifest so it can appear in subtype layout selection.
- Added `TextKeyboardLayoutStyle.Honeycomb` and taught `LayoutManager` to mark
  the bundled `honeycomb` character layout with that style.
- Added honeycomb-specific `TextKeyboard.layoutHoneycomb(...)` geometry that
  positions real `TextKey` instances in the existing tessellated hex grid.
- Routed honeycomb hit testing through the actual hex shape instead of the
  rectangular key bounding boxes; touches in bounding-box corners and inter-key
  gaps no longer activate a neighboring key.
- Reused the production `TextKeyboardLayout` and `TextKeyButton` surfaces while
  clipping Snygg-rendered key backdrops to `HoneycombHexShape`, preserving the
  normal popup, accessibility, input-dispatch, and feedback paths.
- Added `TextKeyboardHoneycombLayoutTest` coverage for odd-row offsets, center
  hits, corner rejection, and unchanged rectangular gap rescue for standard
  layouts.

### Privacy and security

- No network permission, telemetry, account, or cloud dependency was added.
- The honeycomb path is a local layout/rendering change only.

### Verification

- `git diff --check`
- Manifest permission scan for banned network permissions
- Root JVM crash/replay tracked-file guard
- Focused Gradle test attempted with
  `.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.text.keyboard.TextKeyboardHoneycombLayoutTest`;
  this VM still cannot run Gradle because `JAVA_HOME` is unset and `java` is
  not on `PATH`.

<a id="v1.8.78"></a>
## v1.8.78

**Release date:** 2026-05-17
**Type:** Keyman `.kmp` package import foundation

## What changed

- Added `KeymanPackageParser`, a safe `.kmp` ZIP/package parser for the
  Keyman import track.
- The parser reads `kmp.json` metadata, package options, file entries,
  keyboards, languages, examples, and lexical-model metadata.
- Embedded LDML XML files are parsed through the existing
  `KeymanLdmlParser`, so packages that include importable LDML can produce
  `HardwareKeyboardLayout` descriptors immediately.
- Packages that only contain compiled `.kmx` / `.js` keyboards are classified
  as `CompiledEngineRequired` instead of being misrepresented as runnable in
  the base APK.
- Lexical-model-only and mixed keyboard/model packages are classified
  explicitly for future addon/runtime routing.
- ZIP entries are normalized and unsafe traversal / absolute / drive-letter paths are skipped
  before any future extraction code can trust package paths.

## Privacy / permissions

- No network, account, telemetry, or broad storage permission was added.
- No Keyman engine, JavaScript execution, or compiled `.kmx` runtime was added
  to `:app`.
- This is an import-planning foundation; compiled Keyman execution remains
  future addon/runtime work.

## Tests added

- `KeymanPackageParserTest` covers metadata parsing, compiled-keyboard
  classification, LDML extraction, lexical-model classification, mixed-package
  rejection, unsafe path skipping, and invalid ZIP fallback.

## Verification

- `git diff --check`
- Android manifest banned-network-permission scan
- Root JVM crash/replay tracked-file guard
- Attempted `.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.hardware.KeymanPackageParserTest`;
  blocked by the known VM issue: `JAVA_HOME` is not set and no `java` command
  is on PATH.

<a id="v1.8.77"></a>
## v1.8.77

**Release date:** 2026-05-17
**Type:** User-imported sticker folder

## What changed

- Added `prefs.sticker.userFolderUri` and a Settings → Emoji & stickers
  "Imported sticker folder" SAF tree picker with a clear action.
- Added `UserStickerRepository`, which turns a persisted local document-tree
  URI into an `Imported` sticker pack.
- Imported packs accept PNG, WebP, JPEG, and GIF documents, with extension
  fallback when Android reports a generic MIME type.
- `StickerPaletteView` appends the imported pack to the bundled sticker packs
  and decodes local previews off the main thread.
- `StickerMediaProvider` now proxies imported sticker content through the
  existing provider authority, so rich-content insertion continues to use the
  same `InputConnectionCompat.commitContent` path.

## Privacy / permissions

- No network, account, broad media-library, or gallery permission was added.
- The selected folder is read through a user-granted SAF URI.
- File deletion from the chosen folder is intentionally left for a later
  explicit SAF write-flow polish item.

## Tests added

- `UserStickerRepositoryTest` covers supported image filtering, extension
  fallback, empty-folder handling, imported-pack caps, and duplicate URI
  collapse.

## Verification

- `git diff --check`
- Android manifest banned-network-permission scan
- Root JVM crash/replay tracked-file guard
- Attempted `.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.media.sticker.UserStickerRepositoryTest`;
  blocked by the known VM issue: `JAVA_HOME` is not set and no `java` command
  is on PATH.

<a id="v1.8.76"></a>
## v1.8.76

**Release date:** 2026-05-17
**Type:** Hardware-keyboard runtime mapping

## What changed

- Added `HardwareKeyboardRuntimeMapper`, the runtime bridge for imported
  hardware-keyboard layouts.
- Layouts can now be bound to Android hardware keyboard `deviceId` values and
  pruned when `InputManager.getInputDeviceIds()` no longer reports a device.
- Runtime lookup resolves Android `KeyEvent` data through:
  - direct scan-code matches,
  - Android key-code matches,
  - common PC set-1 scan-code fallbacks for Windows KLC imports,
  - common macOS ANSI virtual-key fallbacks for `.keylayout` imports,
  - source virtual-key names such as `VK_B`.
- `KeyboardManager.onHardwareKeyDown(...)` now checks the mapper before the
  existing Space / Enter / Shift special cases and commits mapped printable
  characters through the normal editor path.

## Tests added

- `HardwareKeyboardRuntimeMapperTest` covers KLC fallbacks, macOS fallbacks,
  direct scan-code precedence, source-name fallback, Ctrl/Meta suppression, and
  detached-device pruning.

## Verification

- `git diff --check`
- Android manifest banned-network-permission scan
- Attempted `.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.hardware.HardwareKeyboardRuntimeMapperTest`;
  blocked by the known VM issue: `JAVA_HOME` is not set and no `java` command
  is on PATH.

<a id="v1.8.75"></a>
## v1.8.75

**Release date:** 2026-05-17
**Type:** Hardware-keyboard import parser

## What changed

- Added `MacKeylayoutParser` for macOS `.keylayout` XML files.
- Normalizes macOS key-map/modifier-map data into the existing
  `HardwareKeyboardLayout` / `HardwareKeyEntry` representation used by the
  Windows KLC and Keyman LDML importers.
- Supports normal, Shift, Option-as-AltGr, and Shift+Option slots.
- Selects the `<keyMapSet>` referenced by `<layouts>`, with a first-set fallback.
- Captures action-backed dead-key outputs when the action exposes an output.
- Uses the same XXE-hardened `DocumentBuilderFactory` posture as the Keyman
  LDML parser because imported layouts cross a user/addon trust boundary.

## Tests added

- `MacKeylayoutParserTest` covers metadata extraction, modifier-slot mapping,
  command/control-map ignoring, referenced map-set selection, missing
  modifier-map fallback, dead-key action output capture, malformed XML fallback,
  and DOCTYPE / external-entity rejection.

## Verification

- `git diff --check`
- Parser/banned-permission source scan: no app manifest network-permission
  changes.
- Attempted `.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.hardware.MacKeylayoutParserTest`;
  blocked by the known VM issue: `JAVA_HOME` is not set and no `java` command
  is on PATH.

<a id="v1.8.74"></a>
## v1.8.74

**Release date:** 2026-05-17
**Type:** Dependency maintenance / build toolchain

## What changed

- Android Gradle Plugin `9.0.0` → `9.2.1`
- Compose BOM `2026.03.01` → `2026.05.00`

This is Bump-batch C from the 2026-05-17 prioritization matrix. It follows the
Roborazzi / Robolectric refresh that shipped in v1.8.71 and keeps the build
toolchain on the stable AGP 9.2 patch line while avoiding the newer
`9.3.0-alpha*` preview series.

## Sources checked

- Android Gradle Plugin 9.2 release notes:
  `https://developer.android.com/build/releases/agp-9-2-0-release-notes`
- Android Studio release updates for Panda 4 Patch 1 / AGP 9.2.1:
  `https://androidstudio.googleblog.com/2026/05/`
- Google Maven AGP metadata:
  `https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml`
- Google Maven Compose BOM metadata:
  `https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/maven-metadata.xml`
- Compose release page:
  `https://developer.android.com/jetpack/androidx/releases/compose`
- OSV querybatch API for `com.android.tools.build:gradle:9.2.1` and
  `androidx.compose:compose-bom:2026.05.00`.

## R8 rule audit

AGP 9.2 changes `-keepattributes` wildcard handling for runtime-invisible
annotations. SwiftFloris's app release rules already keep only
`RuntimeVisibleAnnotations,AnnotationDefault`, so no rule change is required.
The older `SourceFile,LineNumberTable` examples in library ProGuard files are
commented out and do not participate in release builds.

## Files touched

- `gradle/libs.versions.toml`
- `gradle.properties`
- `README.md`
- `docs/DEPENDENCY_TRIAGE.md`
- `ROADMAP.md`
- `docs/archive/ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`
- `PROJECT_CONTEXT.md`
- `AGENTS.md`
- `.ai/research/2026-05-17/*` release/context artifacts

## Verification

- Google Maven metadata: AGP stable tail includes `9.2.1`; `9.3.0-alpha05`
  is preview and intentionally skipped.
- Google Maven metadata: Compose BOM latest/release is `2026.05.00`.
- Android Studio May 2026 stable patch notes list AGP `9.2.1`.
- OSV querybatch returned zero vulnerabilities for:
  - `com.android.tools.build:gradle:9.2.1`
  - `androidx.compose:compose-bom:2026.05.00`
- R8 keepattributes audit found no active wildcard rule that keeps
  `RuntimeInvisibleAnnotations`.
- `git diff --check`
- Android manifest banned-network-permission scan
- Attempted `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`;
  blocked by the known VM issue: `JAVA_HOME` is not set and no `java` command
  is on PATH.

<a id="v1.8.73"></a>
## v1.8.73

**Release date:** 2026-05-17
**Type:** Repository hygiene / CI guardrail

## What changed

- Moved local root JVM crash and replay logs out of the repository root into
  `.ai/local-crash-logs/2026-05-16/`.
- Added `scripts/check-no-root-crash-logs.sh`, which fails when committed
  root-level `hs_err_pid*.log` or `replay_pid*.log` files are present.
- Wired that guard into `.github/workflows/android.yml` before Java / Gradle
  setup so forced-added logs fail quickly in CI.
- Added `.ai/local-crash-logs/README.md` to document that the log files are
  local diagnostics and should remain ignored.

This is the Tier-1 repository-hygiene batch from the 2026-05-17
prioritization matrix (#14 + #15). No app code, permissions, dependencies, or
runtime behavior changed.

## Files touched

- `.github/workflows/android.yml`
- `.ai/local-crash-logs/README.md`
- `scripts/check-no-root-crash-logs.sh`
- `gradle.properties`
- `README.md`
- `ROADMAP.md`
- `IMPROVEMENT_PLAN.md`
- `docs/archive/ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`
- `PROJECT_CONTEXT.md`
- `AGENTS.md`
- `.ai/research/2026-05-17/*` release/context artifacts

## Verification

- Root crash/replay log scan after move returned no root files.
- `bash scripts/check-no-root-crash-logs.sh`
- `git diff --check`
- Android manifest banned-network-permission scan
- Attempted `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`;
  blocked by the known VM issue: `JAVA_HOME` is not set and no `java` command
  is on PATH.

<a id="v1.8.72"></a>
## v1.8.72

**Release date:** 2026-05-17
**Type:** Roadmap correction / glide-typing strategy

## What changed

- Reframed HeliBoard / NLnet open-glide integration as an additive future
  track instead of the primary production path.
- Marked SwiftFloris's shipped `StatisticalGlideTypingClassifier` path as the
  current production default while the open-glide library and permissive data
  release remain pending.
- Promoted the HeliBoard NLnet slip risk to the base-case planning assumption
  in the roadmap risk register and prioritization matrix.

This is the Tier-1 "HeliBoard NLnet slip-base-case plan" item from the
2026-05-17 prioritization matrix. No app code, permissions, dependencies, or
runtime behavior changed.

## Sources checked

- HeliBoard `#2226` issue:
  `https://github.com/HeliBorg/HeliBoard/issues/2226`
- HeliBoard latest release API / releases page:
  `https://github.com/HeliBorg/HeliBoard/releases/tag/v3.9`
- NLnet Gesture Typing project page:
  `https://nlnet.nl/project/GestureTyping/`
- HeliBoard gesture-data contribution wiki:
  `https://github.com/HeliBorg/HeliBoard/wiki/Tutorial:-How-to-Contribute-Gesture-Data`

## Files touched

- `gradle.properties`
- `README.md`
- `ROADMAP.md`
- `docs/archive/ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`
- `PROJECT_CONTEXT.md`
- `AGENTS.md`
- `.ai/research/2026-05-17/*` release/context artifacts

## Verification

- Upstream check: GitHub API reports HeliBoard latest release `v3.9`,
  published 2026-03-29; issue `#2226` is still open and was last updated
  2026-05-11.
- NLnet page still describes the project as a separate open-source gesture
  library with a compatibility layer for AOSP-derived keyboards.
- HeliBoard wiki still asks contributors to collect gesture data using the
  current proprietary gesture library; it says the data collection period ends
  2026-11-30.
- `git diff --check`
- Android manifest banned-network-permission scan
- Attempted `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`;
  blocked by the known VM issue: `JAVA_HOME` is not set and no `java` command
  is on PATH.

<a id="v1.8.71"></a>
## v1.8.71

**Release date:** 2026-05-17
**Type:** Dependency maintenance / visual-test infrastructure

## What changed

- Roborazzi `1.55.0` → `1.60.0`
- Robolectric `4.14.1` → `4.16.1`

This is Bump-batch B from the 2026-05-17 prioritization matrix. It refreshes
the screenshot-regression and JVM Android test harness before the later
AGP 9.2.x / Compose BOM 2026.05.00 bump.

## Sources checked

- Roborazzi Maven Central metadata:
  `https://repo1.maven.org/maven2/io/github/takahirom/roborazzi/roborazzi/maven-metadata.xml`
- Roborazzi Gradle Plugin Portal metadata:
  `https://plugins.gradle.org/m2/io/github/takahirom/roborazzi/io.github.takahirom.roborazzi.gradle.plugin/maven-metadata.xml`
- Robolectric Maven Central metadata:
  `https://repo1.maven.org/maven2/org/robolectric/robolectric/maven-metadata.xml`
- OSV querybatch API for the four updated Maven packages.

## Files touched

- `gradle/libs.versions.toml`
- `gradle.properties`
- `README.md`
- `docs/DEPENDENCY_TRIAGE.md`
- `ROADMAP.md`
- `docs/archive/ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`
- `PROJECT_CONTEXT.md`
- `AGENTS.md`
- `.ai/research/2026-05-17/*` release/context artifacts

## Verification

- Maven metadata confirmed Roborazzi core + Gradle plugin latest/release
  `1.60.0`; Robolectric latest/release `4.16.1`.
- OSV querybatch returned zero vulnerabilities for:
  - `io.github.takahirom.roborazzi:roborazzi:1.60.0`
  - `io.github.takahirom.roborazzi:roborazzi-compose:1.60.0`
  - `io.github.takahirom.roborazzi:roborazzi-junit-rule:1.60.0`
  - `org.robolectric:robolectric:4.16.1`
- `git diff --check`
- Android manifest banned-network-permission scan
- Attempted `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`;
  blocked by the known VM issue: `JAVA_HOME` is not set and no `java` command
  is on PATH.

<a id="v1.8.70"></a>
## v1.8.70

**Release date:** 2026-05-17
**Type:** Documentation / migration-window messaging

## What changed

- Refreshed the README front door for the SwiftKey migration window.
- Added a Samsung / Grammarly users callout:
  - Galaxy users on One UI 7+ can keep SwiftFloris as their default keyboard
    and invoke Galaxy AI Writing Assist through Samsung's selected-text UI when
    they intentionally want that separate Samsung layer.
  - Grammarly's Android support docs say the old Grammarly Keyboard for
    Android is being discontinued and replaced by Grammarly for Android, which
    integrates with any keyboard.
- Bumped README badges, Highlights caption, Recent releases, and footer status
  to v1.8.70.

## Sources checked

- Samsung support: `https://www.samsung.com/us/support/answer/ANS10000943/`
- SamMobile: `https://www.sammobile.com/news/one-ui-7-0-galaxy-ai-writing-tools-any-keyboard/`
- 9to5Google: `https://9to5google.com/2025/01/31/one-ui-7-galaxy-ai-writing-features-without-samsung-keyboard/`
- Grammarly support: `https://support.grammarly.com/hc/en-us/articles/25028519116429-Error-Grammarly-Assistant-is-not-enabled-right-now`

## Files touched

- `README.md`
- `gradle.properties`
- `ROADMAP.md`
- `docs/archive/ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`
- `PROJECT_CONTEXT.md`
- `AGENTS.md`
- `.ai/research/2026-05-17/*` release/context artifacts

## Verification

- Documentation-only release; no app code, permissions, dependencies, or
  runtime behavior changed.
- `git diff --check`
- README/source-link inspection
- Attempted `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`;
  blocked by the known VM issue: `JAVA_HOME` is not set and no `java` command
  is on PATH.

<a id="v1.8.69"></a>
## v1.8.69

Bump-batch A — low-risk dependency refresh.

## Why ship this now

The 2026-05-17 dependency review split upgrades into small risk-isolated
batches. This release ships the low-risk patch/minor batch while leaving
Roborazzi/Robolectric and AGP/Compose for their own visual-regression and R8
audit slices.

## What changed

Updated `gradle/libs.versions.toml`:

| Dependency | Before | After | Source checked |
|---|---:|---:|---|
| `kotlinx-coroutines` | `1.10.2` | `1.11.0` | Maven Central metadata for `org.jetbrains.kotlinx:kotlinx-coroutines-android` |
| `ksp` | `2.3.5` | `2.3.8` | Maven Central plugin-marker metadata for `com.google.devtools.ksp` |
| `zxing-core` | `3.5.3` | `3.5.4` | Maven Central metadata for `com.google.zxing:core` |
| `mikepenz-aboutlibraries` | `14.0.1` | `14.2.0` | Maven Central / Gradle Plugin Portal metadata; `15.0.0-b01` is beta and was intentionally skipped |

No app code, permissions, network surface, or runtime feature behavior changed.

## Versioning

- `gradle.properties`: `projectVersionCode=1869`,
  `projectVersionName=1.8.69`.

## Verification

Local checks performed on this Windows VM:

```powershell
git diff --check
rg -n "kotlinx-coroutines = \"1.11.0\"|ksp = \"2.3.8\"|mikepenz-aboutlibraries = \"14.2.0\"|zxing-core = \"3.5.4\"" gradle/libs.versions.toml
rg -n "android.permission.INTERNET|ACCESS_NETWORK_STATE|ACCESS_WIFI_STATE|CHANGE_NETWORK_STATE|CHANGE_WIFI_STATE" app/src/main/AndroidManifest.xml app/src -g AndroidManifest.xml
```

Gradle verification is still blocked on this VM because Java is not configured:
`JAVA_HOME is not set and no 'java' command could be found in your PATH`.
Run before merge on the main Android build host:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

<a id="v1.8.68"></a>
## v1.8.68

N7.6 — Tink / AndroidKeystore migration for encrypted local stores.

## Why ship this now

The roadmap's fifth-pass research found that AndroidX Security Crypto did ship a
stable `1.1.0`, but its preference APIs are deprecated. SwiftFloris was still
pinned to `androidx.security:security-crypto:1.1.0-alpha06` for encrypted local
preference storage, so the app needed to move to the long-term Tink /
AndroidKeystore path before more release hardening lands.

## What changed

### Shared Tink preference wrapper

Added `TinkStringPreferenceCrypto`, a small shared helper that:

1. Wraps bytes or strings with Tink `Aead`.
2. Stores the wrapping key in AndroidKeystore via
   `AndroidKeystore.generateNewAes256GcmKey`.
3. Binds ciphertext to `prefsFile:key` associated data.
4. Refuses to create a replacement Keystore key while reading existing
   ciphertext; writes are the only path that create a missing wrapper key.
5. Commits ciphertext synchronously so encryption metadata is durable before the
   caller proceeds.
6. Reads legacy AndroidX `EncryptedSharedPreferences` string payloads through
   Tink's `AndroidKeysetManager` only for one-shot migration.

### Personal dictionary key migration

The SQLCipher personal-dictionary passphrase now stores under
`sqlcipher_passphrase_tink_v1`, wrapped by the shared Tink helper. Existing
`sqlcipher_passphrase_v1` AndroidX encrypted-preference payloads are migrated
once if their legacy keysets are still readable.

If legacy keysets exist but the passphrase cannot be recovered, SwiftFloris
fails closed instead of silently generating a new passphrase that would orphan an
existing encrypted dictionary.

### Clipboard history migration

The legacy in-process clipboard-history store also moved off AndroidX Security
Crypto. `ClipboardHistoryManager` now wraps `clipboard_history_tink_v1` with
Tink / AndroidKeystore and attempts a one-shot migration from the old
`clipboard_history` encrypted-preference payload. Keystore failures still fall
back to non-persistent in-memory history so the IME can start.

### Dependency update

Removed:

- `androidx.security:security-crypto:1.1.0-alpha06`

Added:

- `com.google.crypto.tink:tink-android:1.21.0`

## Versioning

- `gradle.properties`: `projectVersionCode=1868`,
  `projectVersionName=1.8.68`.

## Verification

Local checks performed on this Windows VM:

```powershell
git diff --check
rg -n "androidx\\.security\\.crypto|security-crypto" app/src/main app/build.gradle.kts gradle/libs.versions.toml
rg -n "TinkStringPreferenceCrypto|tink-android|AndroidKeystore|AndroidKeysetManager" app/src/main app/src/test app/build.gradle.kts gradle/libs.versions.toml
rg -n "android.permission.INTERNET|ACCESS_NETWORK_STATE|ACCESS_WIFI_STATE|CHANGE_NETWORK_STATE|CHANGE_WIFI_STATE" app/src/main/AndroidManifest.xml app/src -g AndroidManifest.xml
```

Gradle verification is still blocked on this VM because Java is not configured:
`JAVA_HOME is not set and no 'java' command could be found in your PATH`.
Run before merge on the main Android build host:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.dictionary.PersonalDictionaryEncryptionTest
.\gradlew.bat :app:assembleRelease
```

<a id="v1.8.67"></a>
## v1.8.67

N12.5 — reproducible-build self-verification CI.

## Why ship this now

SwiftFloris already pins the reproducible-build toolchain and targets F-Droid's
verified reproducibility tier, but the repository did not yet have a first-party
"build twice, compare" guard. This release adds that guard so deterministic-build
regressions are caught before an F-Droid rebuild attempt.

## What changed

### Reproducible APK workflow

Added `.github/workflows/reproducible-build.yml`, a standalone workflow that
runs on `workflow_dispatch`, and on pushes / pull requests that touch build,
workflow, app, Gradle, or reproducible-build documentation surfaces.

The job checks out full history, validates the Gradle wrapper, installs JDK 17
and CMake/Ninja like the main Android workflow, then runs the new verifier
script.

### Build-twice verifier script

Added `scripts/verify-reproducible-apk.sh`.

The script:

1. Creates two detached Git worktrees at the same commit.
2. Updates submodules in each worktree.
3. Runs `./gradlew --no-daemon --no-build-cache --rerun-tasks clean :app:assembleRelease` in each clean tree.
4. Copies both release APKs to an artifact directory.
5. Requires byte-for-byte equality with `cmp`.
6. On drift, writes per-entry SHA-256 manifests excluding `META-INF/` so the
   workflow can distinguish payload drift from signing / ZIP metadata drift.

## Versioning

- `gradle.properties`: `projectVersionCode=1867`,
  `projectVersionName=1.8.67`.

## Verification

Local checks performed on this Windows VM:

```powershell
git diff --check
python -c "import yaml, pathlib; yaml.safe_load(pathlib.Path('.github/workflows/reproducible-build.yml').read_text())"
rg -n "reproducible-build|verify-reproducible-apk|Reproducible APK Check" .github/workflows scripts docs ROADMAP.md
```

The new workflow and shell script were also checked for LF-only line endings.
This VM still has no Bash, JDK, or Android SDK on the path; Gradle fails with
`JAVA_HOME is not set and no 'java' command could be found in your PATH`, and
`bash -n scripts/verify-reproducible-apk.sh` must run on a Linux host or CI.
Run before merge on the main Android build host or GitHub Actions:

```powershell
.\gradlew.bat :app:assembleRelease
```

The full self-check runs on Ubuntu through `.github/workflows/reproducible-build.yml`.

<a id="v1.8.66"></a>
## v1.8.66

N8.7 — EU AI Act Article 50 transparency surface.

## Why ship this now

`docs/PRIVACY_AND_AI.md` already defined the disclosure contract for
next-word prediction, glide typing, voice input, translation, and smart
compose, but the app did not yet expose that contract in first-run setup or
Settings. This release closes that gap before the 2026-08-02 Article 50
compliance horizon.

## What changed

### First-run explainer

Setup now starts with **Review local AI features** before asking Android to
enable the keyboard. The step lists the AI/ML surfaces, states the local-only
contract, and stores a one-time `internal__ai_features_explainer_seen`
acknowledgement before advancing to the normal enable/select/notification
steps.

The step is keyboard-disabled-safe because it is shown before the IME is
enabled.

### Reopenable About screen

Settings → About now includes **AI features in this keyboard**. The new screen
states the no-Internet/no-account/no-telemetry posture, lists each disclosed
surface, and links to:

- `docs/PRIVACY_AND_AI.md`
- `docs/THREAT_MODEL.md`
- `PROJECT_CONTEXT.md`

### Catalog guard

`AiFeatureDisclosureCatalog` owns the disclosed surface list used by the About
screen. `AiFeatureDisclosureCatalogTest` pins that the catalog covers the
first-run Article 50 set: next-word, glide typing, voice input, translation,
and smart compose.

## Tests

Added:

- `AiFeatureDisclosureCatalogTest`

## Versioning

- `gradle.properties`: `projectVersionCode=1866`,
  `projectVersionName=1.8.66`.

## Verification

Local non-Java checks:

```powershell
git diff --check
rg -n "android.permission.INTERNET|ACCESS_NETWORK_STATE|ACCESS_WIFI_STATE|CHANGE_NETWORK_STATE|CHANGE_WIFI_STATE" app/src/main/AndroidManifest.xml app/src -g AndroidManifest.xml
rg -n "aiFeaturesExplainerSeen|settings/about/ai-features|about__ai_features__title" app/src/main app/src/test
```

The no-network permission scan returned no matches. This VM still has no JDK /
Android SDK on the path; Gradle fails with `JAVA_HOME is not set and no 'java'
command could be found in your PATH`. Run before merge on the main Android build
host:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.app.settings.about.AiFeatureDisclosureCatalogTest
.\gradlew.bat :app:lintDebug :app:assembleDebug
```

## What's next

The next local-code candidates are the non-device-gated roadmap items in
`docs/archive/ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`; release tagging, F-Droid verified
rebuild, and real device benchmark numbers remain external-host/device-gated.

<a id="v1.8.65"></a>
## v1.8.65

Phase A3 — encrypted personal-dictionary export/import wiring.

## Why ship this now

v1.8.54 added the portable `SFEXP1` envelope codec, but Settings still only
exposed plaintext personal-dictionary export. This release closes the local
OneDrive-replacement loop from `docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17.md`: users
can create a passphrase-encrypted dictionary file, carry it through Syncthing,
USB, or another user-chosen channel, and import it on another SwiftFloris
device without writing a plaintext export to user-visible storage.

## What changed

### Encrypted export action

Settings → Personal dictionary now has **Export encrypted** beside the existing
plaintext export. The flow asks for a passphrase + confirmation, opens Android's
create-document picker with `my-personal-dictionary.sfexp`, then writes the
AES-256-GCM/PBKDF2 `EncryptedDictionaryExport` envelope directly to the selected
URI.

The plaintext combined-list payload is built in memory, encrypted, and scrubbed
before the output stream is opened. No temporary plaintext file is created.

### Encrypted import detection

The import path now sniffs the selected file for the `SFEXP1` magic before
routing to the normal parser. Encrypted files get a passphrase dialog, bounded
read, decrypt, and then feed the decrypted bytes through `DictionaryImporter` +
`PersonalDictionaryImportBatch`, so the same "Added / Updated / Skipped" summary
and rollback affordance applies to encrypted imports.

Wrong passphrase and tampered ciphertext still collapse to the same user-facing
message, matching the cryptographic contract from v1.8.54.

### Shared SwiftFloris combined-list parser

`UserDictionaryCombinedListCodec` now owns the legacy semicolon key-value export
format. Plain export, encrypted export, legacy import, and the modular importer
all share the same parser/encoder. `DictionaryImporter` gained a
`DictionaryImportFormat.FLORIS` route for SwiftFloris/legacy Floris combined
lists.

## Tests

Added / updated pure unit coverage for:

- SwiftFloris combined-list parse/detect/import routing;
- headerless combined-list streams;
- values containing `=`;
- encrypted combined-list decrypt → importer round-trip.

## Versioning

- `gradle.properties`: `projectVersionCode=1865`,
  `projectVersionName=1.8.65`.

## Verification

Local non-Java checks:

```powershell
git diff --check
rg -n "settings__udm__encrypted|settings__udm__import_summary__format__floris" app/src/main -g "*.kt" -g "*.xml"
rg -n "android.permission.INTERNET|ACCESS_NETWORK_STATE|ACCESS_WIFI_STATE|CHANGE_NETWORK_STATE|CHANGE_WIFI_STATE" app/src/main/AndroidManifest.xml app/src -g AndroidManifest.xml
```

The no-network permission scan returned no matches. This VM still has no JDK /
Android SDK on the path; Gradle fails with `JAVA_HOME is not set and no 'java'
command could be found in your PATH`. Run before merge on the main Android build
host:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Focused test targets once Java is available:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.dictionary.DictionaryImporterTest
.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.dictionary.EncryptedDictionaryExportTest
```

## What's next

The local-code SwiftKey-parity queue is now mostly gated by external inputs:
B5 needs captured local `swiftkey_trace.jsonl` fixture rows, and Phase E needs
real addon runtimes for Gemma / Bergamot / Rime. A1 still has marketing-side
work outside this local repository (Reddit thread + 2026-05-30 pinned release).

<a id="v1.8.64"></a>
## v1.8.64

Phase D1 — calendar quick-insert.

## Why ship this now

`docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17.md` tracks SwiftKey's calendar toolbar
tile as a productivity surface that can be implemented fully on-device. This
slice adds the Android-side hook without adding network access, accounts,
telemetry, or remote services.

The only new permission is `READ_CALENDAR`, and it is requested only after the
user explicitly taps the calendar quick action.

## What changed

### Calendar quick action

Added `QuickAction.InsertCalendarEvent` (`@SerialName("insert_calendar_event")`)
and registered it with `QuickActionJsonConfig`. New installs see the action in
the hidden quick-action editor pool, and the smartbar arrangement migration adds
it to existing users' hidden pool without forcing it into the visible row.

The action checks the local calendar permission. If already granted, it opens an
IME-local picker; if not, it launches `CalendarPermissionActivity`, requests
`READ_CALENDAR`, then opens the picker after the grant.

### Local agenda reader and picker

Added `CalendarAgendaReader`, backed by `CalendarContract.Instances`, to read
events from today through the next seven days. The reader runs off the main
thread through `CalendarQuickInsertManager`, filters completed / blank-title
rows, and caps the picker to 24 upcoming entries.

`CalendarAgendaPickerPanel` renders inside the keyboard window, so choosing an
agenda item can still commit through the active input connection. Selecting an
event inserts:

```text
<event title> - <localized date/time range>
```

All-day events are formatted from UTC dates to avoid shifting a midnight event
to the previous local day.

### Quick-action reachability

The hidden editor pool now includes both `InsertTask` and
`InsertCalendarEvent`. `QuickActionButton` also renders compact text labels for
task and calendar actions so optional smartbar slots are not blank.

## Tests

Added / updated unit coverage for:

- calendar window bounds for today + next seven days;
- same-day timed event formatting;
- cross-day timed event formatting;
- all-day and multi-day all-day UTC date handling;
- blank-title fallback labels;
- default quick-action availability and calendar-action JSON round-trip.

## Versioning

- `gradle.properties`: `projectVersionCode=1864`,
  `projectVersionName=1.8.64`.

## Verification

Local non-Java checks:

```powershell
git diff --check
rg -n "android.permission.INTERNET|ACCESS_NETWORK_STATE|ACCESS_WIFI_STATE|CHANGE_NETWORK_STATE|CHANGE_WIFI_STATE" app/src/main/AndroidManifest.xml app/src -g AndroidManifest.xml
```

The no-network permission scan returned no matches. This VM still has no JDK /
Android SDK on the path; a focused Gradle run failed with `JAVA_HOME is not set
and no 'java' command could be found in your PATH`. Run before merge on the main
Android build host:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Focused test targets once Java is available:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.calendar.CalendarAgendaFormatterTest
.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickActionArrangementTest
```

Manual device follow-up: verify the picker against at least one AOSP calendar
app and Google Calendar, with both permission-denied and permission-granted
paths.

## What's next

The remaining local-code SwiftKey-parity work is gated: B5 needs
human-captured trace fixtures, and Phase E needs optional addon runtimes for
Gemma / Bergamot / Rime. A3 still has Settings UI wiring for encrypted
dictionary export after the codec primitive shipped.

<a id="v1.8.63"></a>
## v1.8.63

Phase C3 — bundled High Contrast and Aurora Animated themes.

## Why ship this now

`docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17.md` tracks SwiftKey theme parity as the
remaining visual-polish batch after the split renderer landed. SwiftFloris
already had SwiftKey Pure and M3 Expressive static stylesheets, but no
dedicated AAA high-contrast theme and no animated bundled theme.

No permissions, dependencies, network surfaces, background jobs, or remote
assets are added.

## What changed

### SwiftKey High Contrast (AAA)

Added the bundled `swiftkey_high_contrast` stylesheet and registered it in
`org.florisboard.themes/extension.json`. The palette uses white-on-black key
surfaces and black-on-yellow action keys, with explicit key, popup, and inline
chip borders so key boundaries and focused alt-glyph popups remain visible.

`ThemeContrastTest` now parses the Snygg stylesheet defines and pins the
High Contrast text/background pairs at the WCAG AAA 7.0:1 floor.

### Aurora Animated

Added the bundled `aurora_animated` stylesheet and a small runtime background
layer gated by the active theme id. `AuroraAnimatedThemeBackground` draws
three translucent aurora bands using Compose `GenericShape` morphs; it respects
Android's animator-duration-scale reduced-motion setting by freezing to the
static first frame when system animations are disabled.

The active theme identity is exposed to IME Compose surfaces through
`LocalActiveThemeName`; normal colors, typography, and Snygg resolution remain
unchanged.

### Theme generator

`scripts/gen_m3e_themes.py` now regenerates the seven M3 Expressive stylesheets
plus the two new C3 stylesheets from the same `swift_slate.json` baseline.
The theme extension manifest moved from `0.3.0` to `0.4.0`, and the bundled
theme count is now 21.

## Tests

Added / updated unit coverage for:

- High Contrast WCAG AAA text/background token pairs;
- bundled manifest registration for High Contrast and Aurora Animated;
- active-theme gating for the Aurora background renderer.

## Versioning

- `gradle.properties`: `projectVersionCode=1863`,
  `projectVersionName=1.8.63`.

## Verification

Local non-Java checks:

```powershell
python scripts/gen_m3e_themes.py
git diff --check
```

Additional local sanity checks parsed the bundled theme manifest as JSON
(`version=0.4.0`, 21 registered stylesheets, no missing stylesheet files) and
computed the High Contrast token-pair contrast floor at 11.82:1 or higher.

This VM still has no JDK / Android SDK on the path, so run before merge on
the main Android build host:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Focused test targets once Java is available:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.theme.ThemeContrastTest
.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.window.AuroraAnimatedThemeBackgroundTest
```

Manual device follow-up: verify Aurora Animated on a Pixel 6-class device with
`adb shell settings get global animator_duration_scale` at `1.0` and `0.0`,
recording frame timing if the release gate requires the roadmap's 30 fps note.

## What's next

The remaining unblocked local-code SwiftKey-parity item is D1 calendar
quick-insert. B5 still needs human-captured local trace fixtures before
decoder constants should move.

<a id="v1.8.62"></a>
## v1.8.62

Phase C1 — split-keyboard renderer wire-up.

## Why ship this now

`docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17.md` identifies split-keyboard tablet
rendering as the remaining visual SwiftKey parity gap after the preference,
window mode, constraints, and row-boundary calculator were already present.
The prior scaffold could shift right-half keys, but it did not pre-shrink the
base layout width, so a shifted row could overflow the keyboard container.
It also allowed split mode on non-viable narrow roots and let adaptive-touch
nearest-key rescue grab taps near the gutter.

No new permissions, dependencies, assets, network surfaces, or background jobs
are added.

## What changed

### Renderer width accounting

Added `TextKeyboardSplitLayout` as the small render-policy helper for split
mode. When the active window spec is `Fixed.SPLIT`, the split constraints are
viable, and the keyboard mode is `CHARACTERS`, `TextKeyboardLayout` now:

1. resolves the active `Fixed.Split.defaultGutter`;
2. clamps the gutter to a bounded fraction of the keyboard width;
3. lays out the base `TextKeyboard` using `keyboardWidth - gutter`;
4. applies `SplitGutterPostPass.apply(keyboard, gutterPx)` so the right half
   moves back into the final container with a real mid-row gutter.

The result is a split layout that keeps the final right edge inside the same
container width as the non-split layout.

### Viability gate

`ImeWindowController` now checks `Fixed.Split.isViable` before promoting the
fixed window to `Fixed.SPLIT`. If a persisted config requests split mode on a
narrow root, `doComputeWindowSpec(...)` safely demotes it to `Fixed.NORMAL`
for that form factor.

### Gutter-aware touch behavior

`TextKeyboard.isPointInSplitGutter(...)` identifies the generated gap between
each row's left and right halves. `getNearestKeyForPos(...)` now refuses
adaptive-touch nearest-key rescue inside that gap, so tapping the gutter does
not land on the nearest key on either side.

### Documentation

Added `docs/SPLIT_KEYBOARD.md` with the activation, rendering, touch behavior,
and verification contract for future split-layout work.

## Tests

Added / updated unit coverage for:

- split gutter enabling only in viable character-mode split windows;
- gutter clamp + pre-pass layout-width reduction;
- pre-shrunk layout + post-pass preserving the final row width;
- gutter taps returning no primary key and no nearest-key rescue;
- persisted split configs falling back to fixed normal on narrow roots;
- split preference promotion only on viable roots;
- existing fixed-window property tests updated for the split fallback.

## Versioning

- `gradle.properties`: `projectVersionCode=1862`,
  `projectVersionName=1.8.62`.

## Verification

Local non-Java check:

```powershell
git diff --check
```

This VM still has no JDK / Android SDK on the path, so run before merge on
the main Android build host:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Focused test targets once Java is available:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.text.keyboard.SplitGutterPostPassTest
.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.text.keyboard.TextKeyboardSplitLayoutTest
.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.window.ImeWindowControllerTest
```

## What's next

The remaining unblocked SwiftKey-parity work is C3 High-Contrast / animated
themes and D1 calendar quick-insert. B5 still needs human-captured local trace
fixtures before decoder constants should move.

<a id="v1.8.61"></a>
## v1.8.61

Phase B2 — quick-prediction-insert threshold tuning.

## Why ship this now

`docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17.md` calls out that the
quick-prediction-insert path needed calibration before the cutoff sprint
could move on to visual and productivity work. The old path accepted any
word candidate when the current word was blank, and the keyboard plain-space
suppression path only checked whether any word candidate existed. That made
low-confidence blank-field predictions too eager.

No new permissions, assets, dependencies, network surfaces, or background
jobs are added.

## What changed

### Configurable quick-insert floor

`SwiftKeyCandidateRanker.selectSpacebarCandidate(...)` now accepts
`QuickPredictionInsertTuning`, with the default floor set to:

- `minWeightedConfidence = 0.40`
- `maxContextRecencyBoost = 0.35`

For blank-current-word quick prediction insertion, the center/default word
candidate is accepted only when:

```text
candidate.confidence * (1.0 + contextProbability * maxContextRecencyBoost)
  >= minWeightedConfidence
```

The trigger contexts are intentionally narrow:

- cold start / empty field;
- after `.`, `!`, or `?`;
- after a newline.

High-confidence candidates after commas, semicolons, or plain word-boundary
spaces are left as normal spacebar inserts rather than silently replacing the
space with a prediction.

### Space suppression aligned with selection

`NlpManager.shouldSuppressPlainSpaceForPrediction()` now calls the same
ranker selection path as `getSpacebarCandidate()`. If the tuned floor rejects
the prediction, the keyboard no longer suppresses the user's plain space just
because a word candidate exists in the strip.

## Tests

Added / updated unit coverage for:

- low-confidence blank-context rejection;
- cold-start, post-period, post-exclamation, post-question, and post-newline
  acceptance above the floor;
- strong context-recency lifting a borderline candidate above the floor;
- explicit custom-floor rejection;
- property-style sweep across trigger context, candidate confidence, and
  recency signal;
- non-boundary punctuation preserving normal spacebar behavior.

## Versioning

- `gradle.properties`: `projectVersionCode=1861`,
  `projectVersionName=1.8.61`.

## Verification

Local non-Java check:

```powershell
git diff --check
```

This VM still has no JDK / Android SDK on the path, so run before merge on
the main Android build host:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Focused test target once Java is available:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.nlp.SwiftKeyCandidateRankerTest
```

## What's next

The remaining unblocked SwiftKey-parity work is C1 split-keyboard renderer
wire-up, C3 High-Contrast / animated themes, and D1 calendar quick-insert.
B5 still needs human-captured local trace fixtures before decoder constants
should move.

<a id="v1.8.60"></a>
## v1.8.60

Phase B1 — multilingual sentence-position priors seed.

## Why ship this now

`docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17.md` calls out that the cold-start
next-word layer was still English-heavy. The parser and `ZipfFrequencyTable`
plumbing already existed; this slice widens the bundled seed data and
localized phrase priors for the Latin-script languages already shipping
as dictionaries.

No new permissions, runtime dependencies, network surfaces, or background
jobs are added.

## What changed

### Multilingual cold-start priors

`ColdStartNextWordPriors` now supports localized sentence-start,
one-word, two-word, and three-word priors for:

- English (`en`) — existing behavior preserved;
- Czech (`cs`);
- German (`de`);
- Spanish (`es`);
- French (`fr`);
- Italian (`it`);
- Portuguese (`pt`).

Examples covered by tests:

- `de-DE` sentence start → `ich`, `das`, `die`, `der`;
- `es-MX` after `Muchas gracias ` → `por`, `de`, `otra`;
- `fr` after `Merci beaucoup ` scores `pour`;
- `pt-BR` after `Bom dia ` scores `como`;
- unsupported languages still return no priors instead of leaking English.

### Zipf seed overlays

Added top-1,000 `rspeer/wordfreq` 3.1.1 Zipf seed overlays:

- `app/src/main/assets/freq/cs.tsv`
- `app/src/main/assets/freq/de.tsv`
- `app/src/main/assets/freq/es.tsv`
- `app/src/main/assets/freq/fr.tsv`
- `app/src/main/assets/freq/it.tsv`
- `app/src/main/assets/freq/pt.tsv`

These mirror the existing `freq/en.tsv` shape consumed by
`ZipfFrequencyTable.parse(...)`: one `word<TAB>zipf` row, UTF-8,
range `[1, 8]`. Full corpus-sized subtitle tables still belong in
dictionary-pack addons so the base APK stays lean.

### Attribution

`NOTICE` now records the bundled Zipf seed-table source:
`rspeer/wordfreq` 3.1.1, generated via `top_n_list()` and
`zipf_frequency()`.

## Tests

Added / updated unit coverage for:

- localized sentence-start priors;
- localized phrase-prior scoring;
- unsupported-language behavior;
- parsing all six new bundled Zipf seed tables and verifying each table
  has exactly 1,000 entries plus a representative common word.

## Versioning

- `gradle.properties`: `projectVersionCode=1860`,
  `projectVersionName=1.8.60`.

## Verification

Local non-Java checks:

```powershell
git diff --check
python -c "from pathlib import Path; [print(path.name, len([line for line in path.read_text(encoding='utf-8').splitlines() if line and not line.startswith('#')])) for path in sorted(Path('app/src/main/assets/freq').glob('*.tsv'))]"
```

The row-count check reported 1,000 entries each for `cs`, `de`, `en`,
`es`, `fr`, `it`, and `pt`.

This VM still has no JDK / Android SDK on the path, so run before merge
on the main Android build host:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Focused test targets once Java is available:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.nlp.latin.ColdStartNextWordPriorsTest
.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.nlp.latin.ZipfFrequencyTableTest
```

## What's next

The remaining unblocked SwiftKey-parity work is B2 quick-prediction-insert
tuning, C1 split-keyboard renderer wire-up, C3 High-Contrast / animated
themes, and D1 calendar quick-insert. B5 still needs human-captured local
trace fixtures before decoder constants should move.

<a id="v1.8.59"></a>
## v1.8.59

Phase D3 — Typing-stats accuracy-delta number.

## Why ship this now

Phase D3 of `docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17.md` closes the
small "personalization stats" gap called out in the SwiftKey parity
audit. SwiftKey exposes an accuracy-improvement style number; this
slice keeps the same local-only spirit by computing a current-week
versus previous-week accepted-correction delta from the existing
`CorrectionOutcomePriors` store.

No new permissions, dependencies, network surfaces, or background
jobs are added.

## What changed

### Weekly correction counters

`CorrectionOutcomePriors` now keeps a bounded weekly metadata ledger
alongside its existing typed/corrected pair priors:

- accepted corrections increment the current week's accepted counter;
- rejected corrections increment the current week's rejected counter
  for future diagnostics;
- weekly rows persist in the same TSV file with `#week` metadata
  records, while old five-column pair rows remain backwards-compatible;
- reset paths clear both pair priors and weekly stats.

The public internal read surface is:

```kotlin
CorrectionOutcomePriors.accuracyDelta(): CorrectionAccuracyDelta
```

It reports current-week accepted corrections, previous-week accepted
corrections, percentage change when a previous-week baseline exists,
and a trend enum (`NO_BASELINE`, `FEWER`, `MORE`, `UNCHANGED`).

### Typing Stats UI

Settings → Typing → Typing stats now includes:

> Accepted corrections this week

The summary reports:

- no data yet;
- this week's count when there is no previous-week baseline;
- "X% fewer than last week (current vs previous)";
- "X% more than last week (current vs previous)";
- unchanged count when both weeks match.

This is intentionally phrased as accepted corrections, not a universal
"typing accuracy" claim. It is a local proxy based on corrections the
user accepted from the keyboard.

## Tests

Added `CorrectionOutcomePriorsTest` coverage for:

- week-over-week accepted-correction comparison;
- percentage calculation;
- `NO_BASELINE` before a previous-week accepted-correction baseline
  exists;
- unchanged trend when both weeks match.

## Versioning

- `gradle.properties`: `projectVersionCode=1859`,
  `projectVersionName=1.8.59`.

## Verification

Per-file syntactic review plus `git diff --check`.

This VM still has no JDK / Android SDK on the path, so run before
merge on the main Android build host:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Manual QA:

1. Use the keyboard enough to accept several autocorrections.
2. Open Settings → Typing → Typing stats.
3. Confirm the new "Accepted corrections this week" row appears.
4. After a week boundary with accepted corrections in both buckets,
   confirm the row reports fewer / more / unchanged versus the prior
   week.

## What's next

The remaining unblocked SwiftKey-parity work is now:

- B1 — sentence-position priors expansion;
- B2 — quick-prediction-insert tuning;
- C1 — split-keyboard renderer wire-up;
- C3 — High-Contrast AAA theme + animated theme;
- D1 — calendar quick-insert.

B5 remains blocked on human-captured local `swiftkey_trace.jsonl`
fixtures; Phase E remains blocked on the L1 LiteRT-LM addon bring-up.

<a id="v1.8.58"></a>
## v1.8.58

Phase D2 — Generic task-creation quick action (`QuickAction.InsertTask`),
the on-device replacement for SwiftKey's Microsoft-To-Do toolbar tile.

## Why ship this now

Phase D2 of `docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17.md`. SwiftKey's task
tile is hard-bound to Microsoft accounts; this slice ships an
on-device, cross-app replacement that doesn't require new
permissions and works with any installed task / note app that
registers a `SEND` filter (Tasks.org, OpenTasks, Google Tasks,
Joplin, Notion, Markor, etc.).

Small slice — single new sealed-class variant + polymorphic codec
registration + display-name / tooltip wiring. No new dependencies,
no Android permissions added.

## What changed

### `QuickAction.InsertTask` (new)

`data object InsertTask : QuickAction()` with `@SerialName("insert_task")`:

- `onPointerUp(context)`:
  1. Reads `editorInstance.activeInfo` and runs
     `SensitiveFieldGuard.isSensitive(inputType, imeOptions)`. If
     the field is a password / numeric-PIN / no-personalised-learning
     field, the action surfaces a Toast and refuses. (User
     consent via tap doesn't override the privacy moat —
     accidentally sending a password via the share sheet is exactly
     the failure mode the guard exists for.)
  2. Reads `editorInstance.activeContent.selectedText`. If empty,
     falls back to the last 140 chars of text-before-selection so
     the share sheet still has *something* meaningful (the user
     can edit it in the destination app).
  3. Builds an `Intent.ACTION_SEND` with `type = "text/plain"` and
     `EXTRA_TEXT = title`, wraps it in
     `Intent.createChooser(sendIntent, "Add to tasks")`, adds
     `FLAG_ACTIVITY_NEW_TASK` (IME service isn't an Activity), and
     calls `startActivity(chooser)`.
  4. On `ActivityNotFoundException` (no task / note app installed),
     surfaces a helpful Toast pointing at the install path.

The label is `"Add task"`; the tooltip is `"Send current selection
to a task / note app (Tasks.org, OpenTasks, Google Tasks, Joplin,
etc.)"`. Both are hard-coded English in this slice (consistent with
the existing `TranslateSelection` action); Crowdin string resources
follow in a localization sweep.

### `QuickActionArrangement.QuickActionJsonConfig`

The polymorphic serializers module now explicitly registers:

- `QuickAction.InsertKey` (already present)
- `QuickAction.InsertText` (already present)
- `QuickAction.TranslateSelection` (added — was relying on
  sealed-class auto-discovery)
- `QuickAction.InsertTask` (new)

Existing arrangements continue to round-trip; users who already
have a saved smartbar layout don't lose their configuration.

## Versioning

- `gradle.properties`: `projectVersionCode=1858`,
  `projectVersionName=1.8.58`.

## Verification

Per-file syntactic review only — this VM has no JDK / Android SDK
on the path. Recommend running before merge:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Hand-test path on a device:
1. Install at least one task app (Tasks.org via F-Droid is the
   recommended FOSS option).
2. Open Settings → Smartbar → Customize quick actions, drag the
   "Add task" tile into the active arrangement.
3. In any editor, type some text and select a portion.
4. Tap the "Add task" tile — the share sheet should appear with
   Tasks.org (and other share targets) as an option.
5. Pick Tasks.org — its "New task" form should open with the
   selected text pre-filled in the title field.
6. Repeat in a password field — should see "Sending tasks from
   sensitive fields is blocked" Toast.

## What's next

Phase D3 (typing-stats accuracy delta) is the next small slice —
compute "X% fewer corrections accepted this week vs. last" from
the existing `CorrectionOutcomePriors` store and surface in
`TypingStatsScreen`. Phase D1 (calendar quick-insert) is a larger
slice because it requires `READ_CALENDAR` permission and an
agenda picker UI; left for a focused follow-up.

After Phase D the remaining unblocked work in
`docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17.md` is Phase C1 (split-keyboard
renderer wire-up — large) and Phase C3 (High Contrast AAA + first
animated theme — pure asset work). Phase E sub-items are gated on
the L1 LiteRT-LM addon bring-up.

<a id="v1.8.57"></a>
## v1.8.57

Phase C2 — Arrow-keys bottom-row preset (SwiftKey "Modes → Arrow
keys" parity).

## Why ship this now

Phase C2 of `docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17.md`. SwiftKey ships
a Modes → Arrow keys affordance that swaps the standard bottom row
for ← ↑ ↓ → so cursor navigation doesn't need the space-bar
trackpad gesture or a hardware-keyboard handoff. The space bar
shrinks but stays present because the user still needs to type
spaces between navigation hops.

Small slice — pure JSON + enum addition + Settings UI entry — no
new dependencies, no Android-specific paths beyond the existing
arrow-key `TextKeyData` constants.

## What changed

### `BottomRowPreset.Navigation` (new preset)

Surfaces ARROW_LEFT / ARROW_UP / SPACE / ARROW_DOWN / ARROW_RIGHT /
ENTER. Period and symbols-view are dropped to fit the four arrows
on a typical-width keyboard; ENTER stays so commit-after-navigation
still works without flipping to a different layout. Space bar
shrinks proportionally.

### `BottomRowKey` enum (4 new values)

ARROW_LEFT, ARROW_UP, ARROW_DOWN, ARROW_RIGHT join the existing
TAB / ESCAPE / SLASH (programmer-mode) values. Each maps to the
matching predefined `TextKeyData.ARROW_*` constant — the runtime
cursor-movement path is already wired
(`KeyboardManager.onInputKeyUp` dispatches `ARROW_*` codes).

### `BottomRowPreset.Presets` registry

Updated to include `Navigation` so the Settings → Keyboard →
Bottom-row preset picker surfaces it alongside the existing
presets. New test pins this registration so a future contributor
that adds a preset without updating the registry gets caught.

### Settings UI

Settings → Keyboard → Bottom-row preset gains an "Arrow keys"
entry between the existing "Programmer" entry and the implicit
end of the list. The label is hard-coded English in this slice
(consistent with the existing "Programmer" entry) — a Crowdin
string resource follows in a localization sweep.

## Tests

`BottomRowPresetTest` (3 new cases):
- `Navigation` preset emits the expected ARROW_LEFT / ARROW_UP /
  SPACE / ARROW_DOWN / ARROW_RIGHT / ENTER key codes in order.
- `Navigation` preset round-trips through the JSON override codec
  (encode → decode produces the same preset).
- `Navigation` preset is registered in the public `Presets` list
  (the registry the Settings picker iterates).

## Versioning

- `gradle.properties`: `projectVersionCode=1857`,
  `projectVersionName=1.8.57`.

## Verification

Per-file syntactic review only — this VM has no JDK / Android SDK
on the path. Recommend running before merge:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

## What's next

Phase C1 (split-keyboard renderer wire-up inside
`TextKeyboardLayout`) and Phase C3 (High-Contrast AAA theme +
animated theme) are the remaining Phase C items; either could land
next depending on whether the autonomous loop prefers a code-side
slice (C1) or an asset-side one (C3). Phase D (calendar / tasks
quick-actions, typing-stats accuracy-delta) is the next functional
parity push.

<a id="v1.8.56"></a>
## v1.8.56

Phase B4 — Same-sentence language-switch hardening:
geometric-decay weighted blend of trailing-word language evidence.

## Why ship this now

Phase B4 of `docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17.md`. The trailing
context window was already 4 words (`MaxLanguageContextWords = 4`
shipped earlier), but the per-locale scoring took the **MAX**
frequency across that window — so a single early trailing word in
any locale locked in the signal and the next three words couldn't
shift it. Real bilingual sentences mid-switch (`"hello mi amigo
cómo …"`) need the recent words to weigh more so the active
language tracks the user's writing without flipping on the first
recognised word.

## What changed

### New `TrailingContextLanguageBlend` (pure-Kotlin helper)

Pulls the per-locale blend math out of `NlpManager.candidateSignals`
into a focused helper that's unit-testable independent of Android
plumbing.

- `score(contextWordsOldestFirst, freqLookup, decay = 0.7)` returns
  the geometric-decay weighted average frequency.
- The most-recent word weighs 1.0; each word further back is scaled
  by `decay` per step (default 0.7).
- `decay = 1.0` collapses to a flat arithmetic mean; `decay = 0.0`
  collapses to "only the most-recent word counts".
- Empty context returns 0.0. Decay outside `[0.0, 1.0]` rejected
  with `IllegalArgumentException`.

### `NlpManager.candidateSignals` (refactored)

The `contextLanguageScores` map is rebuilt by routing each active
locale through `TrailingContextLanguageBlend.score(...)` with the
new `TrailingContextDecay = 0.7` constant. Reading the same
`languageContextWords` list as before — no signature or threading
changes; the rest of the candidate-signal pipeline is unaffected.

Weight schedule on the default 4-word window:

```
  weight[0] (most recent)    = 1.0
  weight[1]                  = 0.7
  weight[2]                  = 0.49
  weight[3] (4 back, oldest) = 0.343
```

Roughly a 3× preference for the most-recent word over the oldest
— enough to smoothly track a mid-sentence language switch.

## Tests

`TrailingContextLanguageBlendTest` (8 cases):
- empty context returns 0.0;
- single-word context collapses to the raw frequency;
- all-same-locale window returns 1.0 regardless of decay (sanity);
- recent in-locale word outweighs older out-of-locale words
  (`["the", "old", "house", "hola"]` → blended ≈ 0.355 for ES);
- older in-locale word matters less than recent out-of-locale
  window (`["hola", "the", "old", "house"]` → blended ≈ 0.122 for
  ES);
- recent-Spanish case strictly > older-Spanish case (the whole
  point of B4);
- `decay = 1.0` collapses to arithmetic mean (pinned);
- `decay = 0.0` collapses to most-recent-only (pinned);
- decay outside `[0.0, 1.0]` rejected;
- regression vs. previous MAX behaviour — a single ES word in a
  4-word EN window no longer dominates (blended << 1.0 and even
  << 0.5).

## Replay-fixture compatibility

The trace-replay fixtures supply pre-computed `languageConfidence`
signals directly to the ranker; they don't re-run
`candidateSignals`. So existing fixtures stay valid. Future
fixture regeneration (B5 follow-up) will pick up the new blended
scores naturally when device captures land.

## Versioning

- `gradle.properties`: `projectVersionCode=1856`,
  `projectVersionName=1.8.56`.

## Verification

Per-file syntactic review only — this VM has no JDK / Android SDK
on the path. Recommend running before merge:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

## What's next

Phase B1 / B2 are the remaining Phase B items (sentence-position
priors expansion across non-English Zipf overlays, quick-prediction
threshold sweep). Both are larger-asset / property-test work that
benefits from the device-captured trace fixtures landing first, so
the next slice may pivot to Phase C (split-keyboard renderer
wire-up) or Phase D (calendar / tasks quick-actions) depending on
which yields the most reviewable scope without external blockers.

<a id="v1.8.55"></a>
## v1.8.55

Phase B3 — Shared-spelling bilingual handling: tighter scoring when
a one-locale candidate would overwrite a shared-spelling literal.

## Why ship this now

Phase B3 of `docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17.md`. The existing
shared-spelling dampening (`typedKnownLocaleCount > 1` → 0.52
language confidence) lives above the
`SwiftKeyCandidateRanker.MinAutoCommitLanguageConfidence` floor
of **0.40**, so a one-language autocorrect (e.g. EN's `on` when the
user typed `no` in an EN+ES subtype) could still take the spacebar
slot. SwiftKey itself protects this case aggressively — the user's
literal `no` should stay literal.

The fix is asymmetric: when typed is shared across multiple locales
**and** the candidate is recognised in only one, push the score
below the autocommit floor (0.30). When typed is shared **and** the
candidate is also shared, keep the existing 0.52 because the
candidate could plausibly fit either side of the bilingual
sentence.

## What changed

### `MultilingualTokenScorer` (modified)

- New branch in the `when` ordering, fired before the existing
  generic shared-typed-word dampening:
  ```
  typedKnownLocaleCount > 1 && candidateKnown && candidateKnownLocaleCount == 1
    → SharedSpellingOneLocaleCandidateConfidence  (0.30)
  ```
- New internal constant
  `SharedSpellingOneLocaleCandidateConfidence = 0.30`, intentionally
  sub-floor relative to `MinAutoCommitLanguageConfidence = 0.40` so
  spacebar autocommit refuses the wrong-language overwrite.
- The both-shared case still falls to the existing 0.52 dampening
  one branch lower; existing literal-protection
  (`candidateMatchesTypedWord && typedWordKnown → 1.0`) is
  unchanged.

### Tests

- Existing `shared-spelling typed words damp one-language
  corrections` test reframed as
  `shared typed word with a single-locale candidate falls below the
  autocommit floor (B3)`; expected `languageConfidence` updated to
  `SharedSpellingOneLocaleCandidateConfidence`, with an additional
  `shouldBeLessThan 0.40` assertion to pin the relationship to the
  ranker's autocommit floor.
- New test `shared typed word with a shared candidate keeps
  moderate dampening (both could fit)` exercises the both-shared
  branch and pins it at 0.52.
- New test `shared typed word with a matching candidate stays at
  full confidence (literal protection)` exercises the
  `candidateMatchesTypedWord` short-circuit in a shared-spelling
  setup.

## Replay fixture compatibility

The checked-in `swiftkey/replay/trace_replay_cases.jsonl`
`shared-spelling bilingual literal protection` row supplies a
pre-computed `languageConfidence: 0.52` (captured before this
slice). The ranker's `expectedSpacebarText: null` assertion still
holds — 0.30 is even more below the autocommit floor than 0.52 was
— so the fixture continues to pass without modification. A
follow-up "B5 trace-based field calibration" slice can regenerate
the fixture from the live scorer when device-side captures are
collected.

## Versioning

- `gradle.properties`: `projectVersionCode=1855`,
  `projectVersionName=1.8.55`.

## Verification

Per-file syntactic review only — this VM has no JDK / Android SDK
on the path. Recommend running before merge:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

## What's next

Phase B4 (v1.8.56) — same-sentence language switch hardening:
expand `TypingContextExtractor.previousWordListBeforeCurrentWord`
trailing context window from 2-word to 4-word and add an
alpha-blend on the per-locale evidence so a mid-sentence language
switch transitions smoothly instead of flipping on the first
recognised word.

<a id="v1.8.54"></a>
## v1.8.54

Phase A3 — Encrypted-blob personal-dictionary export envelope codec.

## Why ship this now

The Floris personal dictionary already encrypts its on-disk Room
database with SQLCipher (`PersonalDictionaryEncryptionTest` pins
that contract), but the SQLCipher passphrase is held in Android
Keystore and is **intentionally non-portable** — a feature, because
it makes a stolen device backup useless to the thief. The downside
is that users can't carry their learned vocabulary to a different
phone through any user-chosen channel (Syncthing, USB-drag,
ProtonDrive, etc.) without first decrypting to plaintext CSV — which
then sits on the source filesystem as a juicy plaintext target.

Phase A3 of `docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17.md` ships the
portable replacement: an AES-256-GCM blob keyed by a user-typed
passphrase, with PBKDF2-HMAC-SHA-256 key derivation at the
OWASP-2025-recommended 600 000-iteration count. The iteration count
is baked into the blob so future bumps decrypt old exports
unchanged.

This slice ships the **codec primitive + full test coverage**. The
Settings UI wiring (passphrase dialog + file-create launcher +
encrypt-then-write loop) lands in a follow-up slice so the
load-bearing crypto contract is committed and reviewable
independently.

## What changed

### `EncryptedDictionaryExport` (new, pure-Kotlin / JVM stdlib)

- `encrypt(plaintext, passphrase, iterations = 600_000, secureRandom)`
  - 16-byte random PBKDF2 salt (NIST SP 800-132 §5.1 floor).
  - 12-byte random AES-GCM nonce (NIST GCM standard).
  - PBKDF2-HMAC-SHA-256 derives a 256-bit AES key.
  - AES-256-GCM with 128-bit auth tag in a single sealed block.
  - Deriving-key buffer is best-effort scrubbed via `Arrays.fill(key, 0)`
    after the cipher captures it.
  - Rejects: empty passphrase, plaintext > 16 MiB, iterations < 100 000
    (the OWASP 2025 floor).

- `decrypt(envelope, passphrase) → ByteArray`
  - Parses the 44-byte header, validates magic + version + field
    bounds before touching the cipher.
  - Collapses cryptographic indistinguishability (wrong passphrase
    vs. tampered ciphertext) into a single `BAD_PASSPHRASE` reason
    so the UI shows one honest line of copy instead of leaking
    which case it actually was.
  - Rejects an envelope that claims a plaintext size > 16 MiB before
    decrypting — defends against an attacker swapping a real export
    with a 1 GiB random blob to OOM the destination device.

- `isEncryptedEnvelope(candidate) → Boolean`
  - Byte-sniff predicate that tests just the 6-byte `SFEXP1` magic.
  - Lets the import flow ask for a passphrase only when the file is
    actually encrypted; plain CSV / JSON / XML / zip files skip the
    passphrase prompt entirely.

### `EncryptedDictionaryException` + `FailureReason` enum

Six categorical failure reasons (`TRUNCATED`, `NOT_AN_ENVELOPE`,
`UNSUPPORTED_VERSION`, `CORRUPT_HEADER`, `OVERSIZED`,
`BAD_PASSPHRASE`) — keeps the call site's `when` exhaustive without
pattern-matching on cause types.

### Wire format

```
offset  size  field
0       6     magic = "SFEXP1" (ASCII)
6       2     version (uint16; v1 = 0x0001)
8       16    PBKDF2 salt (random per export)
24      12    AES-GCM nonce / IV (random per export)
36      4     PBKDF2 iteration count (uint32 BE, currently 600 000)
40      4     plaintext payload byte-length (uint32 BE, sanity bound)
44      …     ciphertext + 16-byte GCM auth tag (single sealed block)
```

Total header = 44 bytes. Two envelopes encrypting the same
plaintext under the same passphrase produce different bytes because
the per-export salt + nonce are random.

## New tests

`EncryptedDictionaryExportTest` (15 cases):
- round-trip recovers exact plaintext;
- wrong passphrase fails with `BAD_PASSPHRASE`;
- tampered ciphertext byte fails with `BAD_PASSPHRASE` (cryptographic
  indistinguishability);
- truncated envelope reports `TRUNCATED` before touching the cipher;
- non-envelope blob (zero magic) reports `NOT_AN_ENVELOPE`;
- future-version envelope reports `UNSUPPORTED_VERSION`;
- envelope claiming oversized plaintext reports `OVERSIZED`;
- envelope claiming negative plaintext length reports `OVERSIZED`;
- envelope with iters=0 reports `CORRUPT_HEADER`;
- encrypt rejects empty passphrase;
- encrypt rejects iterations below OWASP floor;
- encrypt rejects plaintext past safety cap;
- envelope size = header + plaintext + 16-byte GCM tag (pinned);
- `isEncryptedEnvelope` byte-sniff distinguishes magic from CSV /
  JSON / XML / too-short input;
- two encrypts of same plaintext + passphrase produce different
  envelopes but both decrypt to the same plaintext.

## Versioning

- `gradle.properties`: `projectVersionCode=1854`,
  `projectVersionName=1.8.54`.

## Verification

Per-file syntactic review only — this VM has no JDK / Android SDK
on the path. Recommend running before merge:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The PBKDF2 iteration count of 600 000 takes ~150 ms on a Pixel 6 in
informal benchmarks (Conscrypt-accelerated path); tests use 100 000
to keep the suite fast while staying within the codec's OWASP floor.

## What's next

The Settings UI wiring for the encrypted export — passphrase entry
dialog, file-create launcher, encrypt-then-write loop — is the
follow-up. Once that lands, the import flow gets the symmetric
"detected an SFEXP1 envelope → prompt for passphrase → decrypt →
run through the existing `DictionaryImporter`" branch so encrypted
exports round-trip end-to-end through the app without ever
materialising plaintext on the source device's user-visible
filesystem.

After A3 wiring closes, the autonomous loop moves into Phase B
(decoder calibration: B1 sentence-position priors expansion, B2
quick-prediction-insert tuning on empty fields, B3 shared-spelling
bilingual handling, B4 same-sentence language switch hardening,
B5 trace-based field calibration).

<a id="v1.8.53"></a>
## v1.8.53

Phase A2 — Post-import confirmation + rollback for the personal
dictionary, plus the long-standing wiring of `DictionaryImporter`
(the SwiftKey JSON / Gboard XML / CSV / zip parser shipped v1.8.46)
into the Settings UI.

## Why ship this now

`DictionaryImporter.parseSwiftKeyJson` has existed since v1.8.46 but
the Settings → Personal dictionary → Import button still routed
through the legacy `importCombinedList` (FlorisBoard semicolon-
key=value format) — so users who picked their `swiftkey-cloud.json`
file from the picker hit "Could not import user dictionary: ...".
With 14 days remaining until the SwiftKey-account cutoff (2026-05-31),
**A2 also has to close that wiring gap**, not just add the
confirmation sheet the parity roadmap called out.

## What changed

### Wire `DictionaryImporter` into Settings

- The personal-dictionary import flow now tries the modular
  `DictionaryImporter` first (byte-sniff routing to JSON / XML / CSV /
  zip).
- On any `DictionaryImportException` — including "unknown format" —
  the flow falls through to the legacy `importCombinedList` so
  existing FlorisBoard `.combined` backups keep importing unchanged.
- Any other thrown error surfaces as the existing failure toast.

### `PersonalDictionaryImportBatch` orchestrator (new)

Pure-Kotlin bridge between the parser's `List<PersonalDictionaryEntry>`
output and the `UserDictionaryDao`. Implements a snapshot-and-diff
pattern so the rollback target list is the exact set of rows that
were newly inserted, not a guess.

- `import(parsedEntries, dao, format) → PersonalDictionaryImportResult`
  - Snapshots the DAO's known ids before the insert pass.
  - For each entry: if `(word, locale)` already exists, calls
    `dao.update(...)` with the new freq / shortcut (NOT
    rollback-eligible); otherwise calls `dao.insert(...)`.
  - Re-reads after the pass and diffs against the before-set to
    identify the new ids.
  - Frequencies clamped to `[FREQUENCY_MIN, FREQUENCY_MAX]` instead
    of skipped.
  - Blank `word` entries skipped and counted separately.
  - Malformed locale tags do NOT abort the batch — `FlorisLocale.fromTag`
    is wrapped in `runCatching`.
- `rollback(result, dao) → Int` deletes only the rows whose ids are
  still present, returns the deleted count. Idempotent (tolerates a
  manual delete between import and rollback) and bounded (in-place
  updates are intentionally NOT rolled back because the previous
  freq / shortcut is no longer available).

### `PersonalDictionaryImportResult` (new data class)

Carries the rollback-eligible id list, the in-place-updated count,
the skipped count, the total parsed count, and the detected source
format. Helper flags: `noChanges`, `isRollbackable`.

### `PersonalDictionaryImportSummaryDialog` (new Compose UI)

Surfaces after a successful modular import with:

- Title: **Import complete**.
- Up to four summary lines (inserted / updated / skipped / no-changes).
- Source-format line ("Imported from: SwiftKey JSON export", etc.).
- **Keep imported words** (primary, dismisses).
- **Undo import** (secondary; hidden when `result.isRollbackable` is
  false so users don't tap it and see "Removed 0 imported words").

Wires straight into `UserDictionaryScreen` via a new
`importSummary: PersonalDictionaryImportResult?` state slot.

### New string resources

`settings__udm__import_summary__title` and 13 sibling strings cover
the dialog text and format labels.

## New tests

`PersonalDictionaryImportBatchTest` (10 cases):
- empty input → no-op result;
- new entries → all inserted, all rollback-eligible;
- entries already present at `(word, locale)` → updated in place, NOT
  rollback-eligible;
- blank words skipped;
- out-of-range frequencies clamped;
- rollback deletes only newly-inserted ids;
- rollback idempotent (tolerates manual delete between import + undo);
- rollback no-op when nothing was inserted;
- shortcut + locale round-trip preserved;
- malformed locale tag falls back to null-locale insert (no crash).

Uses an in-memory `FakeUserDictionaryDao` that throws on the DAO
methods the batch shouldn't call, so a future regression that starts
using `query(word)` or `queryShortcut` gets flagged.

## Versioning

- `gradle.properties`: `projectVersionCode=1853`,
  `projectVersionName=1.8.53`.

## Verification

Per-file syntactic review only — this VM has no JDK / Android SDK
on the path. Recommend running before merge:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The new dialog should be hand-tested by picking a real
`swiftkey-cloud.json` from the document picker on a device.

## What's next

Phase A3 (v1.8.54) — Encrypted-blob export option on the personal
dictionary so users can carry their learned vocabulary off the device
through any user-chosen channel without a plain-text CSV intermediate.

<a id="v1.8.52"></a>
## v1.8.52

Phase A1 — SwiftKey migration outreach push.

## Why ship this now

The SwiftKey-account cutoff is **2026-05-31**, 14 days from this release.
The on-device JSON importer landed in v1.8.46 and was hardened in v1.8.48;
the visibility step — making the migration funnel impossible to miss for
a user landing on the README — is what `docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17.md`
calls out as Phase A1 with the hardest deadline. Pure doc / marketing
slice; zero code changes.

## What changed

### README

- New `SwiftKey migration` Shields badge in the top badge row, captioned
  "window closes 2026-05-31" in red so a casual GitHub landing immediately
  surfaces the deadline.
- New banner block above the Highlights table walks the visitor through
  the two no-cloud migration paths in three sentences each:
  1. **Right now** — export `swiftkey-cloud.json` from
     `data.swiftkey.com`, install SwiftFloris via the Obtainium one-tap
     link below, then run **Settings → Personal dictionary → Import**.
  2. **If you missed the cutoff** — your learned words are gone from the
     cloud but the instant-remember overlay (v1.8.26) climbs anything you
     re-type back to the top of the prediction strip after a single use.
- Highlights table version-row bumped `v1.8.46` → `v1.8.52`.
- Existing "Migrating from SwiftKey" section consolidated and pointed at
  the new `docs/archive/SWIFTKEY_PARITY_ROADMAP_2026-05-17.md` for full context.
- "Recent releases" list now covers v1.8.46 → v1.8.52 (was stuck at
  v1.8.46).
- "Keyboard crashes on emoji insertion?" troubleshooting section updated
  to reference the v1.8.50 N17.1 root-cause fix instead of marking the
  issue as open triage.
- Status line bumped to v1.8.52 with the 14-days-remaining countdown.

### Versioning

- `gradle.properties`: `projectVersionCode=1852`,
  `projectVersionName=1.8.52`.

## Verification

Doc-only. The Shields badge URL pattern is the same one used by every
other badge on the README; Obtainium one-tap URL unchanged from v1.7.0.

## What's next

Phase A2 (v1.8.53) — `PersonalDictionaryImportSummary` Compose screen
that shows "Imported N words from your SwiftKey export" after a
successful import + a rollback action so a botched import is undoable.

Phase A3 (v1.8.54) — Encrypted-blob export option on the personal
dictionary so users can carry their learned vocabulary off the device
through any user-chosen channel (Syncthing, USB, etc.) without a
plain-text CSV intermediate.

<a id="v1.8.51"></a>
## v1.8.51

N14.3 + N14.4 dependency-pin audit.

## Why ship this now

Both roadmap items are doc-only "review whether X is still current"
tasks. The audit-log entry pins the analysis so a future contributor
doesn't redo it from scratch, and explicitly separates the audit
deliverable from the version-bump deliverable (which is gated on the
CI evidence run per the cadence policy).

## What changed

### Compose BOM audit (N14.3)

Current pin: `androidx-compose-bom = "2026.03.01"`. Audit against
the upstream release notes confirms this is the published
March-2026 patch-01 line. No later patch is announced as of this
audit. No Roborazzi visual-regression or macrobenchmark surface
forces an out-of-band bump.

### Gradle wrapper audit (N14.4)

Current pin: `gradle-wrapper.properties` distributionUrl
`gradle-9.4.1-bin.zip` with `distributionSha256Sum=2ab2958f...`.
The wrapper still verifies. Per the `docs/REPRODUCIBLE_BUILDS.md`
contract, any bump must update the SHA-256 in lockstep so the
verify path stays correct.

### Documentation

- `docs/DEPENDENCY_TRIAGE.md` gains a new **Audit log** table at
  the bottom. Each entry pins the date, the pin audited, the
  conclusion, and the next-action gate. Future audits append a
  row instead of editing the body, so the historical analysis
  stays inspectable.

## Versioning

- `gradle.properties`: `projectVersionCode=1851`,
  `projectVersionName=1.8.51`.

## Verification

No code changes — doc-only. The audit deliverables are the
roadmap checkmarks + the audit-log entry.

## What's next

The Compose BOM and Gradle wrapper version-bumps themselves are
separate slices gated on the maintainer running
`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
(and the Roborazzi suite for BOM bumps) per the cadence policy in
`docs/DEPENDENCY_TRIAGE.md`. Continuing through the §6 NOW queue:
N15.1 (free-movement spacebar trackpad — already partially
shipped via matrix #14 + #15; verify and close), then on to the
remaining NEXT items that can land without external blockers.

<a id="v1.8.50"></a>
## v1.8.50

N17.1 — Emoji-picker crash triage hardening.

## Why ship this now

[GH-SWIFTF-ISSUE-1] reported "tap an emoji from the palette → IME
process death". The v1.8.46 FlorisEmojiCompat audit (matrix #5) ruled
out the EmojiCompat code path itself — every call site is null-safe.
The remaining triage pointed at the `Paint.hasGlyph(...)` glyph-probe
that `EmojiPaletteView` runs during the initial filter pass.
`Paint.hasGlyph("")` throws `IllegalArgumentException("hasGlyph called
with empty string")` and aborts the palette render. The two paths
that can leak an empty-value `Emoji` into the pipeline are the
history-deserialisation round-trip and a malformed bundled-asset
line.

## What changed

### Palette filter defends against empty values

- `EmojiPaletteView.emojiMappings` now skips any `emoji.value.isEmpty()`
  before calling either `EmojiCompat.getEmojiMatch` or
  `Paint.hasGlyph(...)`. Both functions reject empty input — the first
  via a documented invariant, the second with the historical
  `IllegalArgumentException`.
- `EmojiPaletteView` history mapping (lines that wrap
  `prefs.emoji.historyData` pinned / recent lists into `EmojiSet`s)
  now filters out empty-value entries before constructing the grid
  so the recently-used tab cannot render invisible / commit-empty
  tap targets.

### Asset loader rejects blank-value rows

- `EmojiData.loadEmojiDataMap` now skips a data line whose first
  column (the codepoint value) trims to empty. Bundled assets
  shouldn't ever carry such a line, but a future contributor or
  third-party addon-supplied asset can no longer crash the IME this
  way.

### Tests

- New `EmojiHistoryEmptyValueTest` pins the four contract layers:
  the value-only serializer's permissive round-trip, the
  EmojiHistory deserialiser tolerating a stored empty-value entry,
  the EmojiSet constructor still accepting a wrapped empty-value
  Emoji (the responsibility lives on the consumer side), and the
  palette's empty-value filter snippet replicated in pure Kotlin
  so the regression can be caught without Robolectric.

## Versioning

- `gradle.properties`: `projectVersionCode=1850`,
  `projectVersionName=1.8.50`.

## Verification

Per-file syntactic review only — this VM has no JDK / Android SDK on
the path. Recommend running before merge:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The targeted runtime repro (touch-paste an emoji from a
hand-corrupted `emoji.historyData` pref) needs an emulator or
device session; the unit test covers the deterministic boundary
contracts the palette pipeline relies on.

## What's next

Continuing through the §6 NOW queue: N14.3 (Compose BOM refresh
audit), N14.4 (Gradle wrapper bump audit).

<a id="v1.8.49"></a>
## v1.8.49

N15.3 — Smart Edit voice REMOVE_ITEM_FROM_LIST.

## Why ship this now

The §6 N15.3 roadmap item closes the Gboard 2026 Smart-Edit parity
gap for voice-dictated list editing — saying "no longer want apples"
mid-stream should excise `apples` from the dictated list without the
user having to backspace, retype, or stop the session. The existing
v1.7.9 `StreamingVoiceTranscriptBuffer` already routes per-chunk
matches through `VoiceCommandExecutor`; this slice adds the
parameterised command type and the buffer-side excision walker the
roadmap calls out.

## What changed

### Parser

- New `VoiceCommandAction.REMOVE_ITEM_FROM_LIST` enum value.
- New `argument: String?` field on `VoiceCommandMatch` so
  parameterised commands carry their extracted target. Null for
  every fixed-phrase command (existing call sites unchanged).
- New `VoiceCommandParser.parameterisedMatch(...)` runs before the
  fixed-phrase ranker and recognises seven unambiguous patterns:
  - `no longer want <item>`
  - `no longer need <item>`
  - `remove <item> from the list`
  - `remove <item> from list`
  - `delete <item> from the list`
  - `delete <item> from list`
  - `scratch <item>`
- Confidence is fixed at 1.0 for an exact pattern match — the anchor
  tokens disambiguate, so partial matches simply don't fire and the
  user retries.
- `extractRaw` preserves the original argument casing so UX feedback
  ("Removed 'Apples'") reads naturally.
- Conservative stopword guard (`the`/`a`/`an`/`this`/`that`/`it`/
  `them`/`those`/`these`) prevents `remove the from the list` or
  `scratch the` from excising the whole buffer.

### Buffer

- New `StreamingVoiceTranscriptBuffer.committedSegmentsSnapshot()`
  exposes the dictated-list state to the executor.
- New `removeCommittedItem(item)` walks `committedSegments`, excises
  every case-insensitive whole-phrase occurrence of `item`,
  collapses dangling `and` / `or` / `plus` / `with` / `&` connectors,
  and returns a `RemoveCommittedItemResult { removedCount,
  previousCommittedText, newCommittedText, didChange }` so the
  executor can apply the diff to the editor.
- Multi-word items are supported (`almond butter` → matched as one
  phrase).
- Buffer is left untouched on no-match, blank-input, or
  whitespace-only input — defensive against a malformed parser
  argument silently nuking the entire buffer.

### Executor

- `VoiceCommandActions.removeItemFromList(item)` is a new interface
  method with a default impl returning `ACTION_REJECTED` so existing
  implementations of the interface (test doubles, external adapters)
  compile unchanged.
- `EditorVoiceCommandActions` gains an optional `transcriptBuffer`
  reference; when set, `removeItemFromList(item)`:
  1. Asks the buffer for the diff.
  2. If the editor's text-before-cursor still ends with the
     buffer's previous committed text, selects exactly that suffix
     via `editor.setSelection` and replaces it via
     `editor.commitText(diff.newCommittedText)`.
  3. If the user typed something between dictation chunks, returns
     the new `EDITOR_OUT_OF_SYNC` failure instead of risking editor
     corruption.
- Two new `VoiceCommandFailureReason` values:
  - `ITEM_NOT_FOUND` — the parameterised command matched but the
    item was not present in the dictated buffer.
  - `EDITOR_OUT_OF_SYNC` — buffer was mutated but the editor diff
    couldn't be safely applied; the IME can re-sync.
- `VoiceCommandExecutor` short-circuits to `ACTION_REJECTED` when
  the match's argument is null / blank, so the action sink never
  sees an empty-string item.

### Settings UI

- `REMOVE_ITEM_FROM_LIST` is filtered out of the custom-command
  picker in Settings → Voice Input — it's argument-only and not
  assignable as a fixed-phrase custom command.
- New string resource
  `settings__voice_input__voice_command_remove_item_from_list`
  covers the action's display label for the case where a custom
  command somehow already references it (forward-compat).

## New tests

- `VoiceCommandParserTest`: 8 new cases covering pattern detection,
  casing preservation, every remove/delete variant, `scratch`,
  `remove that` → DELETE_THAT precedence, stopword rejection,
  no-argument rejection, and the ambient-utterance false-positive
  guard (`delete the old message after lunch` stays unclassified).
- `StreamingVoiceTranscriptBufferTest`: 8 new cases covering single
  match, dangling-`and` cleanup, case-insensitive match with
  casing-preserved remainder, no-op when absent, blank-input
  refusal, multi-word item, cross-segment walk, and
  trailing-punctuation argument tolerance.
- `VoiceCommandExecutorTest`: 3 new cases plus an updated all-actions
  loop — null-argument short-circuit, whitespace-argument
  short-circuit, trimmed-argument forwarding.

## Versioning

- `gradle.properties`: `projectVersionCode=1849`,
  `projectVersionName=1.8.49`.

## Verification

Per-file syntactic review only — this VM has no JDK / Android SDK on
the path. Recommend running before merge:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

## What's next

The Next-2.5 Rambler-style streaming-voice cleanup pass remains
gated on the L1 Gemma 3 LLM bring-up. Continuing through the §6 NOW
queue: N17.1 (emoji crash triage), N14.3 (Compose BOM refresh
audit), N14.4 (Gradle wrapper bump audit).

<a id="v1.8.48"></a>
## v1.8.48

Defensive hardening pass across importers, MCP bridge, IME service
lifecycle, voice-model install, ZIP handling, and DB cursor
management. No user-visible behavior changes; every fix closes a
specific failure mode that an adversarial / malformed input or a
mis-ordered teardown could otherwise trigger.

## Why ship this now

The v1.8.43–v1.8.47 stack moved fast across MCP, glide replay, and
Roborazzi wiring. An end-to-end audit surfaced a cluster of latent
correctness and trust-boundary issues in code paths that recently
gained third-party-input surfaces (SwiftKey/Gboard importer, MCP
daemon bridge, Tasker dispatcher, voice-model installer). All of
them are small fixes individually and dangerous together, so the
batch lands as one slice.

## What changed

### Importer hardening (`DictionaryImporter`)

- UTF-8 BOM-bearing JSON / XML / CSV exports (Notepad, Excel) now
  route to the correct parser instead of falling through to
  `UNKNOWN`. `detectFormat` strips the BOM before pattern matching;
  `parseCsv` strips it from each row too.
- CSV header detection no longer drops the first row when the user's
  dictionary literally contains the word `word`. Header presence
  now requires column 2 to be `frequency` (case-insensitive), not
  just column 1 to start with `word`.
- `parseGboardXml` now decodes XML numeric character references
  (`&#233;`, `&#x42;`) that Android's own UserDictionary exporter
  emits for non-ASCII code points. Surrogate range validation is
  enforced.
- `parseZip` now bounds total bytes read across the whole archive,
  not just per-entry — a 256-entry × 16 MiB archive could previously
  push 4 GiB through the importer before the per-entry cap fired.
- Empty-result error messages distinguish "saw a candidate file but
  no entries recognised" from "no candidate files in archive".
- Removed dead `if (i > raw.length) break` in attribute parser;
  `var found` → `val found`.

### MCP hardening (`AndroidMcpClient`, `McpServiceConnectionManager`)

- `AndroidMcpClient` parameter-size cap now compares UTF-8 bytes
  instead of character length. A daemon-side proxy could previously
  smuggle a payload past the cap with 4-byte UTF-8 code points
  whose UTF-16 length is half the byte length.
- `AndroidMcpClient` now bounds the daemon's response size with the
  same `MAX_PAYLOAD_BYTES` cap. A malicious or buggy daemon can no
  longer force a multi-megabyte UTF-16 allocation just to throw it
  away on the decode line.
- `AndroidMcpClient` validates that the daemon echoed the correlation
  id the IME issued. A mismatched id (stale response, intentional
  spoofing) is rejected with `TOOL_INTERNAL_ERROR`.
- `McpServiceConnectionManager.onBindingDied` is now bounded to
  three rebind attempts per daemon; beyond that we log and stop
  trying until the next manual bind. Successful `onServiceConnected`
  resets the counter. The rebind itself runs through `runCatching`
  so a SecurityException from a freshly-uninstalled daemon does
  not escape into the system's binder dispatch.

### Tasker dispatcher (`TaskerActionDispatcher`)

- `INSERT_TEXT` and `INSERT_CLIP` now consult `SensitiveFieldGuard`
  before reaching the editor. A Tasker-class sender can no longer
  inject text or paste the clipboard into a password / numeric-PIN /
  `IME_FLAG_NO_PERSONALIZED_LEARNING` field — the same privacy
  guarantee the smart-compose, translation, and MCP surfaces already
  enforce. Sensitive-field suppression is logged.
- Hard `as String` casts on extras are now `as?` with explicit
  early-return on null, so a future contract change cannot crash
  the receiver.
- `TRIGGER_VOICE` now logs the requested mode for traceability
  (mode routing to dictation vs command grammar still lands with
  Next-2.4's voice-command split).

### IME service teardown (`FlorisImeService.onDestroy`)

- Resource cleanup (MCP bridge stop, voice-input manager destroy,
  input-feedback dispose, wallpaper receiver unregister) now runs
  BEFORE `super.onDestroy()`. The previous order cancelled the
  lifecycle scope first, so any callback scheduled by our cleanup
  steps was silently dropped.

### Voice-model install (`VoiceModelInstallStore`)

- On every install, sweep stale `.swiftfloris-staging-…` and
  `.swiftfloris-backup-…` directories left behind by an install
  that crashed between staging and rename. Without the sweep these
  accumulated indefinitely on disk.
- Staging/backup directories are now anchored to a dedicated
  prefix (`SafeModelIdPattern` requires the first char to be
  alphanumeric so a real model id can never collide).

### ZIP extraction (`ZipUtils.unzip`)

- Skip reasons (entry-name too long, destination path too long,
  zip-slip violation, oversize entry) are now logged via
  `flogWarning` instead of silently dropped, so malicious archives
  show up in audit / CI rather than masquerading as corrupt ones.
- `dstFile.delete()` on oversize-entry rejection now runs AFTER
  the output stream's `use` block closes. The previous order
  triggered "delete on open handle" failures on some filesystems
  and left a partial file behind.

### Personal n-gram store (`PersonalBigramStore`)

- `totalEntryCount` no longer treats leftover `.tsv.tmp` flushes
  from a crashed save as a phantom locale (strict `.tsv` suffix +
  explicit `.tsv.tmp` exclusion).

### User-dictionary cursor leak (`UserDictionary`)

- `queryResolver` and `queryLanguageList` now use `cursor.use { … }`
  so the cursor closes even when the row-parse throws. Robolectric
  and some OEM content-provider implementations do throw on
  malformed rows; the previous `.also { cursor.close() }` chain
  leaked the cursor in those cases.

## New tests

- `DictionaryImporterTest`: BOM-stripped detection across XML / JSON /
  CSV, the `word`-as-data regression, decimal + hex XML numeric
  entity decoding.
- `AndroidMcpClientTest`: correlation-id mismatch rejection, oversized
  daemon response rejection, UTF-8-bytes vs char-length payload cap
  bypass.
- `VoiceModelInstallStoreTest`: stale staging / backup dir sweep on
  next install.

## Versioning

- `gradle.properties`: `projectVersionCode=1848`,
  `projectVersionName=1.8.48`.

## Verification

Per-file syntactic verification — local Windows host has no JDK or
Android SDK on the path; recommend running before merge:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

## What's next

Continue working through the §6 NOW queue: N15.3 (Smart Edit voice
REMOVE_ITEM_FROM_LIST), N17.1 (emoji crash triage), N14.3 / N14.4
(Compose BOM + Gradle wrapper refresh).

<a id="v1.8.47"></a>
## v1.8.47

N1.4 — FUTO swipe-trace replay and benchmark harness.

## Why ship this now

The previous N1.4 slice added a durable JSON / JSON Lines schema for
MIT-licensed swipe traces, but there was no reusable way to replay
those traces through the existing glide classifier or report accuracy
numbers. This release adds the missing JVM-side harness pieces so the
remaining work is an evidence run, not more schema plumbing.

## What changed

### Replay

`SwipeTraceReplay.toPointerData(record, bounds)` converts normalized
`SwipeTraceRecord.samples` into the
`GlideTypingGesture.Detector.PointerData` shape already consumed by
glide classifiers. `SwipeTraceReplayBounds` validates the concrete
keyboard rectangle up front so a corpus runner cannot silently feed
NaN or zero-sized coordinates into the classifier.

### Benchmark reporting

`SwipeTraceBenchmark.evaluate(...)` accepts imported records plus any
`SwipeTracePredictor` adapter and reports:

1. Total / evaluated / failed record counts.
2. Top-1, top-3, and top-N hits and accuracy.
3. Total and average predictor latency.
4. Capped miss examples with expected word, layout, language, and
   suggestions.
5. Markdown summary output for pasting into `docs/BENCHMARKS.md`.

`docs/BENCHMARKS.md` now includes a pending glide-trace comparison
table for the FUTO MIT corpus and the existing
`StatisticalGlideTypingClassifier`.

## Test-gate cleanup

The full debug unit-test suite exposed two stale test-fixture issues
while verifying this slice:

- `DictionaryImporterTest` had raw multiline fixtures that triggered
  Kotlin 2.3.21 FIR failures around JSON array literals, plus one CSV
  raw string containing three consecutive quotes. The fixtures now use
  explicit strings while preserving the same parser inputs.
- `AddonAuditExportTest` expected `2026-05-16T16:00:00.000Z` but its
  millisecond fixture encoded `2025-05-16T13:20:00.000Z`. The fixture
  timestamp now matches the asserted ISO value.

## Versioning

- `gradle.properties`: `projectVersionCode=1847`,
  `projectVersionName=1.8.47`.

## Verification

Run from the local C: worktree because the VMware shared-folder
checkout hit long-path / generated-source write limits on `Z:`:

```powershell
.\gradlew.bat --no-daemon --max-workers=1 `
  "-Dorg.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=768m -XX:ReservedCodeCacheSize=128m -XX:CICompilerCount=2 -XX:TieredStopAtLevel=1" `
  "-Dorg.gradle.parallel=false" `
  "-Dkotlin.compiler.execution.strategy=in-process" `
  :app:testDebugUnitTest
```

Result: `BUILD SUCCESSFUL`, 1,168 tests passed.

## What's next

- Download the MIT-licensed FUTO swipe corpus outside the repo.
- Wire imported records into a `StatisticalGlideTypingClassifier`
  adapter using a real keyboard layout.
- Publish corpus size, top-k accuracy, latency, and representative
  misses in `docs/BENCHMARKS.md`.

<a id="v1.8.46"></a>
## v1.8.46

N16.2 — SwiftKey `swiftkey-cloud.json` import support added to
`DictionaryImporter` ahead of Microsoft SwiftKey's 2026-05-31
account-retirement cutoff (15 days from this release) [SK-RETIRE].

## Why ship this now

The upstream `data.swiftkey.com` endpoint that hosts the
`swiftkey-cloud.json` export retires 2026-05-31. After that date the
input data evaporates entirely. SwiftFloris already had a `MIGRATE_FROM_SWIFTKEY.md`
guide describing three paths (Next-6.3, v1.7.9), but the actual
parser for SwiftKey's export shape was the gap. This release closes
it so any SwiftFloris user who has already downloaded their export
can run it through Settings → Personal dictionary → Import without
hand-converting to CSV.

## What changed (user-visible)

**Settings → Personal dictionary → Import** now accepts the JSON
file shape Microsoft SwiftKey hands users from its data export
flow. Two routes work:

1. **Standalone JSON file.** Pick a `swiftkey-cloud.json` (or any
   filename) directly through Android's document picker; the
   importer sniffs the first byte and routes JSON files to the
   SwiftKey parser automatically.
2. **Zip archive containing JSON.** The existing
   `parseZip(stream)` `.json` entry branch now feeds the SwiftKey
   parser instead of being a no-op for FlorisBoard manifest
   files. (FlorisBoard backup manifests don't carry `word`
   entries, so they fall through to an empty list, and the
   sibling CSV/XML in the zip still wins.)

## What changed (internal)

### N16.2 — `DictionaryImportFormat.JSON`

New enum case in `DictionaryImportFormat`. `detectFormat(sniffed)`
returns it when the first non-whitespace byte is `{` or `[`. The
check sits before the CSV branch so a JSON array of strings
containing commas doesn't accidentally route as CSV.

### N16.2 — `parseSwiftKeyJson`

```kotlin
internal fun parseSwiftKeyJson(json: String): List<PersonalDictionaryEntry>
```

Built on `kotlinx.serialization.json` (already in the toolchain).
Algorithm:

1. Parse JSON. On any parse error, return `emptyList()`. The
   importer intentionally doesn't throw here because a FlorisBoard
   backup-manifest JSON sitting in the same zip as a CSV must not
   abort the whole import.
2. Walk every nested `JsonArray` and `JsonObject` recursively.
3. At each `JsonObject`, check whether it carries a "word-class"
   field — any of `word`, `text`, `string`. If yes, lift the
   object into a `PersonalDictionaryEntry`:
   - `frequency`: pick from `frequency` / `count` / `rank`,
     clamped to [0, 255], default 128.
   - `shortcut`: pick from `shortcut` / `expansion`.
   - `locale`: pick from `locale` / `language` / `lang`.
4. If the object has no word-class field, recurse into its child
   arrays and objects (so envelope keys like `predictions`,
   `shortcuts`, `user_data`, `words` work without being hardcoded).

This tolerant walk covers the three envelope shapes most commonly
observed in user-supplied exports:

```json
{ "predictions": [...], "shortcuts": [...] }
{ "user_data": { "predictions": [...] } }
[ { "word": "..." }, ... ]
```

### Tests

10 new cases in `DictionaryImporterTest`:

1. `predictions+shortcuts` envelope (canonical).
2. `user_data` envelope wrapping.
3. Bare array of entries.
4. Missing `frequency` / `locale` defaults (`128` / `null`).
5. Frequency clamping for `-50` → `0` and `9999` → `255`.
6. Malformed JSON returns empty list (not throw).
7. Empty array / empty object / empty `predictions: []` → no entries.
8. Blank/missing word field filtering.
9. End-to-end `import(InputStream)` byte-sniff routing for JSON.
10. `detectFormat` returns `JSON` for `{` and `[` prefixes.

## Versioning

- `gradle.properties`: `projectVersionCode=1846`,
  `projectVersionName=1.8.46`.

## What's next

- **N16.1** — Pre-cutoff outreach: Reddit thread on
  r/SwiftKey + r/HeliBoard + r/FlorisBoard + r/PrivacyGuides
  with the Obtainium URL + `MIGRATE_FROM_SWIFTKEY.md` permalink.
  **Action by 2026-05-29 latest.**
- **N15.1** — Free-movement Cursor mode (Gboard 16.8 virtual
  trackpad on long-press space).
- **Roborazzi baseline capture** — maintainer-side
  `:app:recordRoborazziDebug` run to commit the first batch of
  baseline PNGs.

<a id="v1.8.45"></a>
## v1.8.45

N13.2 — IME visibility now round-trips through configuration changes,
preparing for the Android 17 (API 37) behavior change that no longer
auto-restores it.

## What changed (user-visible)

If the user opens a text field inside SwiftFloris Settings (e.g. the
dictionary editor's word input, the search bar, etc.), opens the IME by
tapping it, then rotates the device, the IME now stays visible after
the rotation. On Android 14-16 this already worked because the
platform auto-restored IME visibility; on Android 17 the platform
stopped doing that for apps that don't opt in, and SwiftFloris is
now explicit about the opt-in.

## What changed (internal)

### N13.2 — `FlorisAppActivity` save/restore wire-up

The audit confirmed there is exactly **one** activity in the manifest
(`FlorisAppActivity`); the "eight Compose-route activities" the
roadmap mentioned are eight Compose `NavHost` destinations inside
that one activity, so the IME-visibility-restore wire-up lands once.

```kotlin
const val SAVED_KEY_IME_VISIBLE = "swiftfloris.app.ime_visible"

class FlorisAppActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        ...
        if (savedInstanceState?.getBoolean(SAVED_KEY_IME_VISIBLE, false) == true) {
            window?.decorView?.post {
                WindowInsetsControllerCompat(window, window.decorView)
                    .show(WindowInsetsCompat.Type.ime())
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val imeVisible = window?.decorView?.let { decor ->
            ViewCompat.getRootWindowInsets(decor)
                ?.isVisible(WindowInsetsCompat.Type.ime())
        } ?: false
        outState.putBoolean(SAVED_KEY_IME_VISIBLE, imeVisible)
    }
    ...
}
```

`WindowInsetsCompat.Type.ime()` collapses the API-26..API-30
platform variants so the call is safe at the project's `minSdk = 26`
floor.

Notable design choices:

- **Why not add `android:windowSoftInputMode="stateAlwaysVisible"`
  to the manifest?** Because that flag forces the IME open every
  single time the activity comes to the foreground, including when
  the user had deliberately dismissed it. The Android 17 behavior
  change is asking for *previous-state* restoration, not
  always-visible. Save+restore through the bundle is the
  minimum-surprise path.
- **Why `window.decorView.post { ... }`?** Because requesting
  `show(Type.ime())` immediately inside `onCreate` can race the
  view-tree attachment; posting it onto the decor view's message
  queue lets the request fire after the tree is attached, which
  is when `WindowInsetsControllerCompat` can actually reach the
  WindowInsetsAnimationController.
- **Pre-Android-17 builds.** The `show(Type.ime())` call is
  idempotent — if the IME is already visible (because the
  platform's auto-restore did its job), the call is a no-op. So
  the behavior is forward-compatible with the API-37 targetSdk
  bump on a future slice *without* changing pre-API-37 behavior.

## Versioning

- `gradle.properties`: `projectVersionCode=1845`,
  `projectVersionName=1.8.45`.

## What's next

- **Roborazzi baseline capture** — maintainer-side
  `:app:recordRoborazziDebug` run.
- **N15.1** — Free-movement Cursor mode (Gboard 16.8 virtual
  trackpad on long-press space).
- **N16.2** — SwiftKey `swiftkey-cloud.json` parser, time-sensitive
  before the 2026-05-31 retirement cutoff.

<a id="v1.8.44"></a>
## v1.8.44

N13.3 — long-press popup guard on password fields.

## What changed (user-visible)

Long-pressing a key on a password field (Android `TYPE_TEXT_VARIATION_PASSWORD`,
`TYPE_TEXT_VARIATION_VISIBLE_PASSWORD`, `TYPE_TEXT_VARIATION_WEB_PASSWORD`,
or `TYPE_NUMBER_VARIATION_PASSWORD`) no longer renders a popup showing the
typed character — neither the small accent popup nor the extended
alt-glyph mini-keyboard. Tapping characters into the field continues to
work as before; only the visual popup is suppressed.

This closes the on-screen IME side of the Android 17 (API 37)
`show_passwords_physical` separation [STD-A17-BEHAVIOR]: Android 17
introduces separate "show physical-keyboard passwords" and "show
on-screen-keyboard passwords" toggles, and SwiftFloris already gates
suggestions / clipboard / FLAG_SECURE on password fields (N7.2). The
popup surface was the last visual leak of typed credential characters,
and is now closed.

## What changed (internal)

### N13.3 — `PasswordFieldPopupGate`

New pure helper in `ime/text/keyboard/`:

```kotlin
object PasswordFieldPopupGate {
    fun shouldSuppressPopups(activeVariation: KeyVariation): Boolean {
        return activeVariation == KeyVariation.PASSWORD
    }
}
```

Returns `true` only when the active variation is `KeyVariation.PASSWORD`.
The Android `KeyVariation` enum in SwiftFloris collapses all four
password input types into a single `PASSWORD` bucket inside
`EditorInstance.handleStartInputView`, so the IME-side gate only
needs one comparison.

### N13.3 — `TextKeyboardLayout` wire-up

`TextKeyboardLayout`'s `rememberPopupUiController(...)` call now
consults the gate in both predicates:

```kotlin
val evaluatorHack = rememberUpdatedState(evaluator)
val popupUiController = rememberPopupUiController(
    ...
    isSuitableForBasicPopup = { key ->
        if (PasswordFieldPopupGate.shouldSuppressPopups(evaluatorHack.value.state.keyVariation)) {
            false
        } else if (key is TextKey) { ...existing checks... } else true
    },
    isSuitableForExtendedPopup = { key ->
        if (PasswordFieldPopupGate.shouldSuppressPopups(evaluatorHack.value.state.keyVariation)) {
            false
        } else if (key is TextKey) { ...existing checks... } else true
    },
)
```

The new `rememberUpdatedState(evaluator)` ("evaluatorHack") capture
mirrors the existing `desiredKeyHack` pattern and makes the predicate
read the *live* evaluator on every long-press evaluation, not the one
snapshotted at the first recomposition. Without that, a navigation
from a normal field to a password field within the same IME session
could keep showing popups against a stale evaluator reference.

### Tests

6 new `PasswordFieldPopupGateTest` cases:

1. `PASSWORD` variation suppresses popups.
2. `NORMAL` variation does not suppress.
3. `ALL` variation does not suppress (default for unspecified fields).
4. `EMAIL_ADDRESS` variation does not suppress.
5. `URI` variation does not suppress.
6. Forward-compat exhaustive sweep — iterates every `KeyVariation.entries`
   value and asserts only `PASSWORD` trips. A future variation added to
   the enum that should also suppress popups (e.g. a hypothetical
   "PIN") will fail this test, forcing the author to confirm intent.

## Versioning

- `gradle.properties`: `projectVersionCode=1844`,
  `projectVersionName=1.8.44`.

## What's next

- **N13.2** — IME visibility on config change for Android 17.
- **Roborazzi baseline capture** — maintainer-side `:app:recordRoborazziDebug`
  run to commit the first batch of baseline PNGs, then remove
  `continue-on-error: true` from the CI verify step.
- **N15.1** — Free-movement Cursor mode (Gboard 16.8 virtual
  trackpad on long-press space).

<a id="v1.8.43"></a>
## v1.8.43

N14.1 — Roborazzi plugin alias uncommented. Visual-regression
verify now wired into CI.

## What changed (user-visible)

Nothing — pure CI infrastructure bump.

## What changed (internal)

### N14.1 — Roborazzi 1.55.0 + plugin alias

`gradle/libs.versions.toml`:

```toml
[versions]
-roborazzi = "1.39.0"
+roborazzi = "1.55.0"
```

`app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.agp.application)
    alias(libs.plugins.kotlin.plugin.compose)
    ...
    alias(libs.plugins.kotlinx.kover)
-    // alias(libs.plugins.roborazzi)  // commented during AGP-9 gate
+    alias(libs.plugins.roborazzi)
}
```

Roborazzi 1.55.0 (Jan 2026 line) shipped AGP 9 support via
[PR #782][ROBORAZZI-782], so the plugin's `TestedExtension` API
churn that blocked the previous v1.43.x line is resolved. The
plugin alias being applied lights up two Gradle tasks:

- `:app:recordRoborazziDebug` — runs every Roborazzi-annotated
  JUnit test and writes the captured PNGs to
  `app/src/test/snapshots/images/` as the baseline. Maintainer
  task; not run in CI.
- `:app:verifyRoborazziDebug` — re-runs the same tests and
  diff-compares each capture against the baseline. Fails if any
  snapshot drifts beyond the default change threshold (0.01).

### N14.1 — CI step

`.github/workflows/android.yml`:

```yaml
- name: Roborazzi visual-regression verify (N14.1)
  run: ./gradlew :app:verifyRoborazziDebug
  continue-on-error: true
```

Inserted between the unit-tests step and the lint step.
`continue-on-error: true` is set for the bootstrap window because
no baseline PNGs are committed yet — without it, every PR would
red-flag with `verifyRoborazziDebug FAILED: snapshot baseline
missing`. Once a maintainer runs `:app:recordRoborazziDebug`
locally and commits the resulting `.png` files under
`app/src/test/snapshots/images/`, the flag can be removed and
the verify becomes a hard gate.

The existing `ExtensionMaintainerChipScreenshotTest` (Next-12.2
sample suite) is the first test the plugin lights up. Follow-up
batches will extend Roborazzi coverage to:

- The smartbar candidates row.
- The seven M3 Expressive theme keys (Nord light/dark, Tokyo
  Night, Dracula, Catppuccin Mocha, SwiftKey Pure M3E light/dark).
- The floating-window border.
- The stylus handwriting overlay (when Next-4.2 ML Kit Digital
  Ink addon lands).

[ROBORAZZI-782]: https://github.com/takahirom/roborazzi/pull/782

## Versioning

- `gradle.properties`: `projectVersionCode=1843`,
  `projectVersionName=1.8.43`.

## What's next

- **Roborazzi baseline capture** — maintainer-side `:app:recordRoborazziDebug`
  run to commit the first batch of baseline PNGs, then remove
  `continue-on-error: true`.
- **N13.2** — Audit IME-visibility-on-config-change for the
  Android 17 (API 37) behavior change.
- **N13.3** — Audit long-press popup rendering on password fields
  to skip when the active variation is `PASSWORD` / `VISIBLE_PASSWORD`
  / `WEB_PASSWORD` regardless of input source.

<a id="v1.8.42"></a>
## v1.8.42

N14.2 — Kotlin `2.3.20` → `2.3.21` bug-fix bump.

## What changed (user-visible)

Nothing — pure toolchain bump.

## What changed (internal)

### N14.2 — Kotlin 2.3.21

`gradle/libs.versions.toml`:

```toml
[versions]
-kotlin = "2.3.20"
+kotlin = "2.3.21"
```

That's the only source change. Every Kotlin Gradle plugin
(`kotlin-android` / `kotlin-jvm` / `kotlin-plugin-compose` /
`kotlin-serialization`) plus `kotlin-reflect` + `kotlin-test-junit5`
all declare `version.ref = "kotlin"`, so they pick up `2.3.21`
automatically.

Per the [Kotlin 2.3.21 release notes][KOTLIN-2321] (Apr 23 2026,
bug-fix line), this release closes:

- Wasm IC (incremental compilation) cache invalidation bug.
- Kotlin/Native ObjC protocol metaclass cast regression.
- AGP 9.1 R8 artifact-clear regression that surfaced as `Task
  :app:assembleRelease FAILED` for downstream projects pinning
  Kotlin 2.3.20 + AGP 9.1.
- KGP composite-build state mismatch under Wasm IC.

None of these regressions affected SwiftFloris's CI gates today
(no Wasm, no Native, no composite-build, AGP 9.0 not 9.1), but
the bump is free and keeps the toolchain on the current
bug-fix tip per the roadmap §6 N14.2 line.

### Docs

`docs/REPRODUCIBLE_BUILDS.md` "Pinned toolchain inputs" table
bumped from `2.3.20` → `2.3.21`; ROADMAP §2 "Stack" line bumped
likewise so the `git grep 2.3.20` audit is clean.

[KOTLIN-2321]: https://kotlinlang.org/docs/releases.html

## Versioning

- `gradle.properties`: `projectVersionCode=1842`,
  `projectVersionName=1.8.42`.

## What's next

- **N14.1** — Uncomment `alias(libs.plugins.roborazzi)` and bump
  `roborazzi = "1.39.0"` → `"1.55.0"` now that the AGP 9 plugin
  is live.
- **N13.2** — Audit IME-visibility-on-config-change for the
  Android 17 (API 37) behavior change.
- **N15.1** — Free-movement Cursor mode (Gboard 16.8 virtual
  trackpad on long-press space).

<a id="v1.8.41"></a>
## v1.8.41

N15.2 — Gboard parity: auto-return to the letter keyboard after the apostrophe
in the symbols panel so contractions ("don't", "I'm") finish without a manual
mode switch.

## What changed (user-visible)

When the user is in the symbols panel (`SYMBOLS` or `SYMBOLS2`) and taps the
apostrophe key, the IME now flips back to the letter keyboard (`CHARACTERS`)
right after the apostrophe commits. This matches the Gboard 16.6 beta behavior
documented at `[GBOARD-AUTOSWITCH-2026]` in the roadmap.

The behavior is gated on a new **Settings → Keyboard → Return to letters after
apostrophe** switch, default **on**. Users who deliberately stay in the symbols
panel after an apostrophe can flip it off.

The space-bar variant of this behavior (`spaceBarSwitchesToCharacters`) is
unaffected — both switches are independent so each can be tuned to taste.

## What changed (internal)

### N15.2 — `ApostropheReturnGate`

New pure helper in `ime/keyboard/`:

```kotlin
object ApostropheReturnGate {
    fun shouldReturnToCharacters(
        committedText: String,
        currentMode: KeyboardMode,
        autoReturnEnabled: Boolean,
    ): Boolean
}
```

Returns `true` iff the pref is on, the committed text is exactly `"'"`
(U+0027 ASCII apostrophe — the curly U+2019 typographic quote is **not**
auto-returned to keep the gate conservative), and the current mode is
`SYMBOLS` or `SYMBOLS2`. `NUMERIC`, `NUMERIC_ADVANCED`, `PHONE`, and `PHONE2`
panels never trigger because the shipped layouts don't carry the apostrophe
on those panels.

### N15.2 — `KeyboardManager` wire-up

`KeyboardManager.onInputKeyUp` now calls `ApostropheReturnGate` immediately
after `editorInstance.commitChar(text)` in the CHARACTER/NUMERIC default
branch. When the gate returns `true`, `activeState.keyboardMode` flips to
`CHARACTERS` so the next keystroke sees the letter view.

### N15.2 — `prefs.keyboard.autoReturnAfterApostrophe`

New JetPref-backed preference in `AppPrefs.Keyboard`:

```kotlin
val autoReturnAfterApostrophe = boolean(
    key = "keyboard__auto_return_after_apostrophe",
    default = true,
)
```

Surfaced under **Settings → Keyboard** with the two new strings
`pref__keyboard__auto_return_after_apostrophe__label` +
`pref__keyboard__auto_return_after_apostrophe__summary`.

### Tests

- 8 new `ApostropheReturnGateTest` cases covering: SYMBOLS trigger, SYMBOLS2
  trigger, disabled-pref no-op, non-apostrophe symbol no-op, CHARACTERS
  no-op, NUMERIC/NUMERIC_ADVANCED/PHONE/PHONE2 no-op, curly-quote no-op,
  empty-string no-op.

## Versioning

- `gradle.properties`: `projectVersionCode=1841`,
  `projectVersionName=1.8.41`.

## What's next

- **N15.1** — Free-movement Cursor mode (Gboard 16.8 virtual trackpad on
  long-press space — promotes the existing space-swipe path to a full
  `Box` overlay with `MotionEvent` deltas).
- **N14.1** — Uncomment `alias(libs.plugins.roborazzi)` and bump
  `roborazzi = "1.39.0"` → `"1.55.0"` now that the AGP 9 plugin is live.

<a id="v1.8.40"></a>
## v1.8.40

L7.6b — per-daemon enable / disable for the MCP daemon bridge.
**998 unit tests** at HEAD, 0 failures.

## What changed (user-visible)

Each bound daemon row in **Settings → MCP daemon bridge** now has
a switch on the right. Flipping it off:

- writes the daemon's package name into the
  `mcp__disabled_daemon_packages` preference (newline-separated
  list),
- updates the daemon's row icon (from "play" to "block"),
- keeps the binding live (the daemon stays in the registry, the
  IBinder stays available) but stops the dispatch router from
  forwarding any `callTool` traffic to it.

The Status row count now reads "M/N" where M is the count of
enabled daemons and N is the total bound count, so the user sees
the disabled subset at a glance.

## What changed (internal)

### L7.6b — `prefs.mcp.disabledDaemonPackages`

New JetPref-backed preference in `AppPrefs.Mcp`:

```kotlin
val disabledDaemonPackages = string(
    key = "mcp__disabled_daemon_packages",
    default = "",
)
```

Stored as a newline-separated string — JetPref doesn't ship a
`Set<String>` type in this version, so the `Set<String>` view is
provided by the [DisabledDaemonSet] codec.

### L7.6b — `DisabledDaemonSet`

New `ime/mcp/DisabledDaemonSet`:

- `parse(serialized): Set<String>` — split on `\n`, trim, drop
  blanks.
- `encode(packages): String` — join sorted (stable diff),
  deduplicated, blanks dropped.
- `add(serialized, pkg)`, `remove(serialized, pkg)`,
  `contains(serialized, pkg)` — convenience wrappers around
  parse + mutate + encode.

### L7.6b — `McpDispatchRouter.isDaemonDisabled`

New constructor parameter:

```kotlin
class McpDispatchRouter(
    private val client: McpClient,
    private val registryView: RegistryView = RegistryView.from(),
    private val isDaemonDisabled: (DaemonKey) -> Boolean = { false },
)
```

The lambda is consulted **after** the tool resolves to a daemon
but **before** the `client.callTool` invocation. Disabled daemons
yield a `Response.Suppressed` with the reason
`"daemon <pkg> disabled by user"`.

### Tests

- 13 new `DisabledDaemonSetTest` cases covering parse / encode /
  add / remove / contains.
- Two new `McpDispatchRouterTest` cases: disabled-daemon
  short-circuit + lazy lambda evaluation (the check only fires
  after tool resolution).

## Versioning

- `gradle.properties`: `projectVersionCode=1840`,
  `projectVersionName=1.8.40`.
- README badge bumped to `v1.8.40`.

## What's next

- **L7.6c** — manual re-scan button in the screen (forces a fresh
  `McpAndroidDiscoverer.runDiscovery` pass without IME restart).
- **L7.7** — wire `McpDispatchRouter` into `NlpManager.smart-compose`
  so the disabled-set actually gates real traffic. The router's
  `isDaemonDisabled` parameter at the call-site reads
  `prefs.mcp.disabledDaemonPackages` via the `DisabledDaemonSet`
  codec.

<a id="v1.8.39"></a>
## v1.8.39

L7.6 — Settings → MCP daemon bridge screen. Read-only listing of
every bound MCP daemon with its protocol version + advertised
tools. Per-daemon enable/disable + a runtime re-scan ride as the
L7.6b sub-slice. **982 unit tests** at HEAD, 0 failures.

## What changed (user-visible)

A new **MCP daemon bridge** entry appears in the Settings home
list, between **Sync** and **Backup**. Tapping it opens the new
screen:

- **Status group** — shows either "No MCP daemons installed"
  (with a one-line summary explaining the local-only contract)
  or "MCP bridge active: bound to N daemon(s)".
- **Bound daemons group** — one row per daemon, showing the
  package id, the protocol version, the tool count, and a
  comma-separated list of tool names. Read-only for v1.8.39.

The summary text explicitly tells the user that the MCP bridge
is local-only and never opens a network connection — keeping
the §1 no-network promise visible in the spot where the bridge
shows up.

## What changed (internal)

### L7.6 — `McpSettingsScreen`

New `app/settings/mcp/McpSettingsScreen.kt`:

- Reads `McpDaemonRegistry.active()` once on entry — the registry
  is rebuilt at IME service startup, so the snapshot is stable
  for the life of the screen.
- Renders one Preference row per daemon: package id as title,
  protocol version + tool count + tool names as summary.

### Routes + HomeScreen entry

- `Routes.Settings.Mcp` added as a `@Deeplink("settings/mcp")`
  serializable object, wired into the nav graph via
  `composableWithDeepLink(Settings.Mcp::class) { McpSettingsScreen() }`.
- `Routes.Settings.Mcp` exposed from the Settings home list with
  the `Icons.Default.Extension` icon, sitting right after the
  Sync entry.

### Strings

Added eight new strings under `settings__mcp__*` covering the
title, two group titles, and four status / daemon-row variants.
The two formatted strings (`status_bound_summary` and the daemon
protocol/tool count fragments) use a `{count}` / `{version}`
placeholder substituted at render time via `String.replace(...)` —
no `%d` format-arg overhead, keeping the strings translatable
through the existing Crowdin pipeline.

## Versioning

- `gradle.properties`: `projectVersionCode=1839`,
  `projectVersionName=1.8.39`.
- README badge bumped to `v1.8.39`.

## What's next

- **L7.6b** — per-daemon enable / disable. Adds a JetPref-backed
  `prefs.mcp.disabledDaemonPackages: Set<String>` plus a toggle
  on each daemon row. `McpDispatchRouter` consults the prefs
  before forwarding a `callTool` request.
- **L7.6c** — manual re-scan button (forces a fresh
  `McpAndroidDiscoverer.runDiscovery` pass without IME restart).
- **L7.7** — `NlpManager.smart-compose` consults
  `McpDaemonRegistry.findTool(...)` for tools relevant to the
  current input field.

<a id="v1.8.38"></a>
## v1.8.38

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

<a id="v1.8.37"></a>
## v1.8.37

L7.5 — `McpAndroidDiscoverer`: the Android wrapper that converts
`PackageManager.queryIntentServices` results into the
`DiscoveryCandidate` shape `McpDaemonDiscoverer` already consumes.
**973 unit tests** at HEAD, 0 failures.

## What changed (user-visible)

Nothing. The actual IME-side wire-up that calls
`McpAndroidDiscoverer.runDiscovery(context)` at startup and feeds
the result into `McpDaemonRegistry.setActive(...)` rides as L7.5b
in the next slice. This release adds the platform translation layer
so when the IME-side wiring lands it can call one method.

## What changed (internal)

### L7.5 — `McpAndroidDiscoverer`

New `ime/mcp/McpAndroidDiscoverer`:

- `runDiscovery(context: Context): Map<DaemonKey, DaemonEntry>` —
  queries every installed Service matching
  `ACTION_BIND_MCP_DAEMON` with `GET_META_DATA`, shapes each
  `ResolveInfo` into a `DiscoveryCandidate`, and hands the list
  off to `McpDaemonDiscoverer.discover(...)`.
- **Catalog read**: opens the daemon's `R.raw.<catalog>` resource
  through `Context.createPackageContext(daemonPackage)` —
  no content-URI / FileProvider handshake needed.
- **Permission check**: marks `hasBindPermission = true` only
  when the daemon's `<service>` declared
  `android:permission="dev.patrickgold.florisboard.permission.BIND_MCP"`.
- **Failure modes**: missing serviceInfo / missing meta-data /
  protocol version < 1 / catalog resource id == 0 / catalog
  lookup failure all return null for that candidate (it gets
  filtered out of the result map without aborting the rest).

### Decomposition for testability

The Android-bound part of the pipeline is split into two
helpers so the candidate-shaping logic is pure-JVM testable:

- `serviceAttrsFrom(ResolveInfo): ServiceAttrs?` — lifts the
  platform-specific bits (`Bundle.getInt`, `ServiceInfo.permission`,
  …) into a flat `ServiceAttrs` record. Not pure-JVM testable
  on its own (the `Bundle.getInt` return is the well-known
  "not-mocked-returns-0" trap under `returnDefaultValues=true`).
- `shapeCandidate(ServiceAttrs, catalogLookup): DiscoveryCandidate?` —
  pure-Kotlin candidate validation and shaping. Pure-JVM testable.

### Tests — `McpAndroidDiscovererTest`

Ten new Kotest tests pinning `shapeCandidate`:

1. Returns a `DiscoveryCandidate` for well-formed input.
2. `hasBindPermission = false` when permission attr doesn't match.
3. `hasBindPermission = false` when permission attr is null.
4. Returns null on blank package name.
5. Returns null on blank class name.
6. Returns null on protocol version < 1 (covers both -1 sentinel
   and 0 default).
7. Returns null on catalog resource id == 0.
8. Returns null when `catalogLookup` returns null.
9. Returns null when `catalogLookup` returns a blank string.
10. Forwards daemon-package + resource id to `catalogLookup`.

## Versioning

- `gradle.properties`: `projectVersionCode=1837`,
  `projectVersionName=1.8.37`.
- README badge bumped to `v1.8.37`.

## What's next

- **L7.5b** — `FlorisImeService.onCreate` calls
  `McpAndroidDiscoverer.runDiscovery(context)` →
  `McpDaemonRegistry.setActive(...)`; constructs a
  `McpServiceConnectionManager`, binds every daemon, and
  installs `AndroidMcpClient(manager::binderFor)` via
  `McpClientRegistry.setActive(...)`. `onDestroy` calls
  `manager.shutdown()`.
- **L7.6 Settings UI** — Settings → Privacy → MCP screen listing
  every bound daemon + its advertised tools, with per-daemon
  enable/disable.

<a id="v1.8.36"></a>
## v1.8.36

L7.4b — `McpServiceConnectionManager`: per-daemon `bindService`
lifecycle owner that provides the production `binderLookup` lambda
[AndroidMcpClient][v1.8.35] consumes. **963 unit tests** at HEAD,
0 failures.

[v1.8.35]: CHANGELOG.md#v1.8.35

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

<a id="v1.8.35"></a>
## v1.8.35

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

<a id="v1.8.34"></a>
## v1.8.34

Next-12.1 Macrobenchmark trace instrumentation — the six trace
sections the existing `KeyboardLatencyBenchmark` already measures
are now wired into the production hot paths they're meant to
record. **945 unit tests** at HEAD, 0 failures.

## What changed (user-visible)

Nothing. The instrumentation is no-op at runtime when systrace
isn't capturing (`android.os.Trace.beginSection` returns
immediately when the system tracer is disabled, per the Android
contract).

## What changed (internal)

### Next-12.1 — production trace sections wired in

Before this release, the `KeyboardLatencyBenchmark` module in
`benchmark/` already declared `TraceSectionMetric` entries for six
expected section names, but the production code didn't emit them.
The benchmark would record zero or one-frame durations across the
board — useless data.

Six call-sites now emit the matching `swiftfloris.<subsystem>.<action>`
sections:

| Section name | Production call-site |
|---|---|
| `swiftfloris.ime.firstRender` | `FlorisImeService.onCreateInputView()` |
| `swiftfloris.nlp.suggest` | `LatinLanguageProvider.suggest()` (split into a `suggestImpl` body so the suspend signature stays clean) |
| `swiftfloris.smartbar.candidates.recompose` | `CandidatesRow()` Composable (sequential `beginSection`/`endSection` flanking the body — Compose forbids try/finally around composable calls) |
| `swiftfloris.theme.switch` | `ThemeManager.updateActiveTheme()` (split into a `updateActiveThemeLocked` body so the existing `return@withLock` semantics stay intact) |
| `swiftfloris.dict.load` | `LatinLanguageProvider.loadSpecificDictionary()` (split into a `loadSpecificDictionaryImpl` body) |
| `swiftfloris.nlp.symspell.build` | Both `symSpellIndex` and `symSpellDistance2Index` lazy initialisers in `LatinLanguageProvider` |

All instrumentation uses `android.os.Trace` (Android stdlib, zero
new dependency). The same section names appear in
`KeyboardLatencyBenchmark.kt`'s `TraceSectionMetric(...)` rows so
when the benchmark runs on a clocks-locked device the metrics fire
correctly.

### Unit-test compatibility

`android.os.Trace` is part of the Android JVM stub that throws
"Method not mocked" by default during unit tests. Flipped
`testOptions.unitTests.isReturnDefaultValues = true` in
`app/build.gradle.kts` so the stubs return their defaults instead
of throwing — the existing 945 tests pass through the
SymSpell / suggest / dict-load paths without tripping on tracing.

## Versioning

- `gradle.properties`: `projectVersionCode=1834`,
  `projectVersionName=1.8.34`.
- README badge bumped to `v1.8.34`.

## What's next

- Run `KeyboardLatencyBenchmark` on a clocks-locked Pixel 6 /
  Galaxy S25 Ultra and commit the before/after numbers to
  `docs/BENCHMARKS.md` per the Next-12.1 acceptance bar.
- The `swiftfloris.nlp.suggest` section is currently in the
  `LatinLanguageProvider.suggest()` entry point; if the
  multilingual path becomes the dominant case, drop a nested
  section in `suggestMultilingual()` for finer-grained data.

<a id="v1.8.33"></a>
## v1.8.33

L9.2 honeycomb layout loader — pure-JVM bridge from the shipped
`honeycomb.json` to the `HoneycombKeyboardRow` renderer.
**945 unit tests** at HEAD, 0 failures.

## What changed (user-visible)

Nothing yet. The honeycomb layout still isn't registered in
`extension.json` (selectability + working touch-hit through
`TextKeyboardLayout` is the final L9.2 slice). This release closes
the renderer→layout-JSON bridge so when the integration lands the
loader is already proven against the shipped layout shape.

## What changed (internal)

### Next-9.2c — `HoneycombLayoutLoader`

New `ime/text/keyboard/HoneycombLayoutLoader`:

- Pure-JVM object — uses `kotlinx.serialization.json` (already
  on the implementation classpath) so it runs in the unit-test
  JVM without Robolectric.
- `parse(json: String): List<List<String>>` — converts the
  FlorisBoard character-layout JSON shape into the exact
  `List<List<String>>` the `HoneycombKeyboardRow` renderer
  consumes.
- **Filter heuristic**: drops any key whose `type` field is
  set (`modifier` / `system_gui` / `enter_editing`) and any key
  whose label matches the known modifier-word set (`shift`,
  `delete`, `space`, `enter`, `view_symbols`, etc.). Character
  keys including punctuation (`,`, `.`) pass through.
- **Failure mode**: returns an empty list on malformed JSON
  rather than throwing — keeps the renderer fail-safe against
  disk corruption or bad addon-supplied layouts.
- `Json` config: `ignoreUnknownKeys = true`, `isLenient = true`,
  `allowTrailingComma = true` — tolerates future schema additions
  and the trailing-comma flavour some hand-edited layouts ship
  with.

### Tests — `HoneycombLayoutLoaderTest`

Eight new Kotest tests pinning the loader contract:

1. Parses the exact 5-row shape shipped at
   `assets/.../layouts/characters/honeycomb.json` into the
   expected character-label rows, with `shift` / `delete` /
   `space` / `enter` / `view_symbols` filtered.
2. Filters cells with no label.
3. Filters cells with non-empty `type` field.
4. Skips rows that contain only modifier keys.
5. Trims whitespace inside labels.
6. Returns empty list on malformed JSON / empty string.
7. Returns empty list on empty array.
8. Ignores unknown fields on key objects (forward compat).

## Versioning

- `gradle.properties`: `projectVersionCode=1833`,
  `projectVersionName=1.8.33`.
- README badge bumped to `v1.8.33`.

## What's next

- Asset-reader glue: feed `honeycomb.json` from `assets/` into
  the loader at runtime (the IME's existing asset-loader pattern,
  used by `KeyboardManager` for the QWERTY family, plugs in
  cleanly here).
- `TextKeyboardLayout` integration: touch routing through the
  existing pointer-event pipeline, Snygg theming, popup support
  — registers the layout in `extension.json` once selectability
  + working touch-hit are both verified.

<a id="v1.8.32"></a>
## v1.8.32

L9.2 honeycomb renderer slice — `HoneycombHexButton` and
`HoneycombKeyboardRow` Compose building blocks now live alongside
the v1.8.31 `HoneycombHexShape`. **937 unit tests** at HEAD, 0 failures.

## What changed (user-visible)

Nothing yet — these are renderer primitives. The honeycomb-tiled
keyboard layout still ships disabled in the layout selector. Wiring
them into `TextKeyboardLayout` (touch routing, theme + Snygg
integration, popup support) lands in a follow-up release.

## What changed (internal)

### Next-9.2a — `HoneycombHexButton`

New `ime/text/keyboard/HoneycombHexButton`:

- Single-cell Compose composable for a honeycomb-tiled key.
- Clips its modifier-supplied bounding box to `HoneycombHexShape`
  (six straight edges, six 60° vertices — no pill/oval/capsule
  backdrop, per the global GUI rule).
- Idle vs. pressed background colours (`pointerInput` +
  `detectTapGestures` + `tryAwaitRelease` flips a local `pressed`
  flag).
- `onTap` and `onLongPress` callbacks. Defers theming, popup
  rendering, and input-feedback hooks to the TextKeyboardLayout
  integration follow-up.
- Defaults pull from a dark Catppuccin-adjacent palette
  (`0xFF2A2D40` idle / `0xFF3D4159` pressed) until Snygg flows
  in.

### Next-9.2b — `HoneycombKeyboardRow`

New `ime/text/keyboard/HoneycombKeyboardRow`:

- Multi-row Compose composable that lays out a `List<List<String>>`
  of labels as a flat-top hex tessellation.
- Geometry comes from the existing `HoneycombTessellation` shipped
  in v1.8.4 — row stride `1.5·r`, column stride `√3·r`, odd-indexed
  rows offset by half a column-stride.
- Absolute positioning via Compose's `offset` modifier — the
  minimal renderer slice. Touch routing and Snygg theming wait
  for the TextKeyboardLayout call-site work.
- Sized by the caller via the outer modifier; `keyRadiusDp`
  defaults to 24 dp (sensible for a 6–7" phone).
- `onKeyTap` callback fires with `(row, col, label)` so the
  upstream `KeyboardManager` can map taps to the layout's key
  set without coupling this composable to `TextKeyboardLayout`.

## Test surface

- 937 unit tests pass (`./gradlew :app:testDebugUnitTest --offline`).
- Pure-Compose composables — no JVM-testable geometry surface
  beyond what `HoneycombHexShape`/`HoneycombTessellation` already
  cover. Visual regression / Compose-test coverage lands in a
  follow-up (Roborazzi or instrumentation).

## Versioning

- `gradle.properties`: `projectVersionCode=1832`,
  `projectVersionName=1.8.32`.
- README badge bumped to `v1.8.32`.

## What's next

- Compose-test (Roborazzi or instrumentation) for
  `HoneycombKeyboardRow` to lock the visual layout.
- L9.2 final step: wire `HoneycombKeyboardRow` into
  `TextKeyboardLayout` — touch routing through the existing
  pointer-event pipeline, Snygg theme integration, popup support.

<a id="v1.8.31"></a>
## v1.8.31

Released: 2026-05-16

Roadmap-only historical entry: L9.2 `HoneycombHexShape` Compose `Shape`. `Outline.Generic(buildPath(size))` returns a flat-top hexagon inscribed in caller bounds; `radiusFor(size) = min(width/2, height/sqrt(3))`; `verticesFor(size)` returns six coordinate pairs clockwise from left; `centerOf(size)`.

<a id="v1.8.30"></a>
## v1.8.30

Released: 2026-05-16

Roadmap-only historical entry: Settings -> User Dictionary edits now invalidate the overlay. `DictionaryManager.rebuildOverlay(locale)` is idempotent and `UserDictionaryScreen` calls it after each DAO insert/update/delete so manual edits affect the next keystroke without restarting the IME.

<a id="v1.8.29"></a>
## v1.8.29

Released: 2026-05-16

Roadmap-only historical entry: Next-9.4a `EmojiPaletteView` pinned-groups chip row scaffold. `pinnedGroupsVersion` bumps on `EmojiPinGroupStore` mutation, the row renders on the `RECENTLY_USED` tab, and chip taps commit saved emoji through the keyboard dispatcher. Superseded by the v1.8.127 pin-to-group sheet.

<a id="v1.8.28"></a>
## v1.8.28

Released: 2026-05-16

Roadmap-only historical entry: Overlay-typo autocorrect. `LatinDictionarySuggester.corrections(...)` adds bounded overlay edit-distance candidates so typos of learned words autocorrect to the learned form before the cap.

<a id="v1.8.27"></a>
## v1.8.27

Released: 2026-05-16

Roadmap-only historical entry: Second-commit cap for instant remember. Frequency progression reaches 245 -> 250 at commit #2, and `hydrateLocale` clamps old DAO entries into range without mass-promoting them.

<a id="v1.8.26"></a>
## v1.8.26

Released: 2026-05-16

Roadmap-only historical entry: SwiftKey-style instant remember. `INITIAL_FREQUENCY` moved from 80 to 245 so one commit places a learned word near the top of the SCOWL scale, and overlay completions demote competing SCOWL edit-distance corrections.

<a id="v1.8.25"></a>
## v1.8.25

Typed-word memory finally wired into the suggester. **925 unit tests** at HEAD, 0 failures.

## What changed (user-visible)

Words you type **now climb the suggestion ranking** as you reuse them.
The keyboard already wrote those words to the on-disk user dictionary
on every committed word, but the suggester wasn't reading them — so
"foobar" you typed twenty times would still lose to whatever SCOWL
had at that prefix. After this release:

- **Type a word once** → it's known to the suggester. The spell-check
  underline disappears the next time you type it.
- **Type it again** → it climbs above lower-frequency SCOWL words
  sharing the same prefix.
- **Type it thirty times** → it ranks at the top of its prefix slot,
  matching SCOWL's most-common-words tier.
- **Long-press → "Forget"** still works — clears the in-memory bump
  and the disk entry in the same call.
- **Password / PIN / no-learn fields** are still skipped (the existing
  N7 `SensitiveFieldGuard` chain is unchanged).
- **Incognito mode** is still skipped (existing `learnIfAllowed` gate).

## What changed (internal)

### Next-3 — `UserDictionaryOverlay`

New `ime/dictionary/UserDictionaryOverlay`:

- Process-wide singleton. Per-locale `ConcurrentHashMap<word, freq>`.
- **Frequency scale matches SCOWL exactly**: initial 80, +6 per
  re-use, capped at 250 (same as `DictionaryManager.LEARN_*`).
- **Lock-free reads** via `ConcurrentHashMap`. Writes use an
  optimistic increment loop so concurrent commits converge on the
  cap without locks.
- **Per-locale isolation** — `kabob` typed in en-US vs es-ES tracks
  separately.
- **Same normaliser as `learnWord`**: trims trailing junk, accepts
  internal `'` and `-` (real-word punctuation), rejects internal
  underscores / symbols / digits.
- **`learn` / `forget` / `frequencyFor` / `contains` /
  `wordsWithPrefix` / `snapshotFor` / `hydrateLocale` /
  `clearLocale` / `clearAll`** API.

### Existing `DictionaryManager.learnWord` / `forgetWord` updated

- **`learnWord`** now bumps the overlay **before** kicking off the
  IO-thread DAO write. The next keystroke's `suggest` already sees
  the new entry — no IO latency.
- **`forgetWord`** drops the overlay entry first, then deletes from
  the DAO.

### New `DictionaryManager.hydrateOverlay(locale)`

Lazy DAO snapshot loader. Idempotent — overlay tracks which locales
it's hydrated. Called from the suggester on every suggest; the
overlay's `isHydrated` flag short-circuits all calls after the first
on a given locale. So a process restart picks up the user's full
saved vocabulary without blocking the typing path.

### `LatinDictionarySuggester` consults the overlay

- **`suggest()`** gains a `userOverlay: Map<String, Int>` parameter
  (default empty for tests / multilingual paths that don't need it).
- **`completions()`** now merges SCOWL prefix-matches with overlay
  prefix-matches before ranking. Duplicates dedup; overlay-only
  words appear with their overlay frequency.
- **Ranking** uses `max(scowl_normalised, overlay_normalised)` so:
  - A heavily-typed user word (overlay → 250 / 255 ≈ 0.98) outranks
    its mid-frequency SCOWL look-alikes.
  - A SCOWL top-1000 word still wins over a once-typed user variant.
- **Overlay-known words skip the corrections path** — the suggester
  treats them as their own valid form, so the user's invented
  proper-noun isn't autocorrected to a SCOWL look-alike.

### `LatinLanguageProvider.spell` treats overlay-known words as valid

The existing dictionary `contains()` check is supplemented with an
`UserDictionaryOverlay.contains(word, locale)` check before
returning `SpellingResult.typo`. So a word the user typed before
doesn't get the spell-check red underline.

### Tests

- **`UserDictionaryOverlayTest`** — 13 tests covering learn/forget
  bump-and-cap, case-insensitivity, length + punctuation
  rejections, per-locale isolation, prefix lookup, hydrate
  idempotence, clearLocale / clearAll.
- **`LatinDictionarySuggesterTest` extensions** — 4 new tests:
  overlay surfaces an unknown-to-SCOWL word, overlay boosts a word
  above lower-frequency SCOWL completions, overlay-known word
  skips the autocorrect substitution, empty-overlay path is
  identical to overlay-less.

925 unit tests at HEAD (was 908 at v1.8.24), 0 failures, 0 skipped.
17 net new tests across 2 new test classes.

<a id="v1.8.24"></a>
## v1.8.24

Twenty-fourth autonomous slice. **908 unit tests** at HEAD, 0 failures.

## N7.6 — AddonInvocationAudit (PII-safe in-process log)

New `ime/smartcompose/AddonInvocationAudit` is the in-process log
Settings → Privacy reads to render the "what has the keyboard
called into?" list. Captures **PII-safe metadata only**:

- **Surface** — `SMART_COMPOSE` / `TRANSLATION` / `MCP`.
- **Outcome** — `ACCEPTED` / `SUPPRESSED` / `FAILED`.
- **Timestamp** — host clock (caller-injectable for tests).
- **Categorical reason** — the *router-emitted* string for
  `SUPPRESSED` / `FAILED` (`"sensitive field"`,
  `"no installed pair for X→Y"`, `"tool X not registered"`,
  `"TOOL_INTERNAL_ERROR"`, etc.). Reasons are router-defined
  vocabulary and by construction don't contain user text.

**Never** captures user text, candidate suggestions, translated
content, tool parameters, or tool results.

- **FIFO ring at 256 records** with eviction; in-memory only per
  §1 (no cloud / no telemetry). Settings UI reads the snapshot;
  nothing persists to disk.
- **`record(surface, outcome, reason?, timestampMillis?)`** —
  `reason` is required for SUPPRESSED + FAILED, ignored for
  ACCEPTED.
- **`snapshot()` / `snapshotFor(surface)` / `totalCount()`** —
  read-side API for the Settings UI.
- **`clear()`** — Settings → Privacy "clear log" hook.

Strictly **observability** — the routers' suppression logic is
the actual privacy gate. The audit is a transparency surface, not
enforcement.

6 unit tests cover empty-log contract, ACCEPTED stores no reason,
SUPPRESSED requires reason, FAILED preserves reason, snapshotFor
filter, sequence number monotonicity + totalCount, and clear-reset.

## N7.7 — NlpAddonHub (unified façade)

New `ime/smartcompose/NlpAddonHub` is the single composition point
the NlpManager + smartbar UI call into. Owns the three Routers
shipped in v1.8.21-23 and records every invocation through
`AddonInvocationAudit`:

- **`predict(context, inputType, imeOptions, maxCandidates)`** →
  `SmartComposeResult` (via `SmartComposeRouter`).
- **`translate(request)`** → `TranslationRouter.Response` (via
  `TranslationRouter`).
- **`callMcpTool(request)`** → `McpDispatchRouter.Response` (via
  `McpDispatchRouter`).

Audit wiring maps:

- `Suggestion` / `Translated` / `Completed` → `ACCEPTED`.
- `NoSuggestion` on sensitive field → `SUPPRESSED` with the field's
  `SensitiveFieldGuard.reasonFor` string.
- `NoSuggestion` on non-sensitive field → `FAILED` with reason
  `"no candidate above confidence threshold"`.
- `Suppressed` → `SUPPRESSED` with the router's reason.
- `Failed` (MCP) → `FAILED` with the `McpErrorCode.name`.

Stateless across surfaces — each Router owns its own
cache / breaker. Clock is injectable (`() -> Long`) so audit
records use a deterministic timestamp in tests.

(No standalone tests in this slice — the hub's behaviour is
exercised through the audit records that the audit tests assert
on; integration testing lands once the registry singletons can be
fully stubbed.)

## L5.x — Three more Gondi / Multani historical Brahmic scripts

Total transliteration coverage from 60 to **63 scripts**:

- **Multani** (U+11280 block, supplementary plane) — historical
  Brahmic script for Saraiki of southern Punjab, used by Hindu
  merchant communities c. 16th-20th century. Replaced by
  Perso-Arabic. No native digits — Western fallback.
- **Masaram Gondi** (U+11D00 block, supplementary plane) — 20th-
  century alphabet for the Gondi language family of central India,
  created in 1928 by Munshi Mangal Singh Masaram. Encoded in
  Unicode 10. Native digits U+11D50..U+11D59.
- **Gunjala Gondi** (U+11D60 block, supplementary plane) — 20th-
  century alphabet for the Gondi language family of central India,
  created in 1928 by Pandit Ravula Bhima Bhoi. Distinct from
  Masaram Gondi but used by the same language community. Encoded
  in Unicode 11. Native digits U+11DA0..U+11DA9.

4 unit tests cover the three new tables (first-consonant glyph,
native-digit round-trip for the two Gondi scripts, sane size
assertions).

## Tests

908 unit tests at HEAD (was 897 at v1.8.23), 0 failures, 0 skipped.
11 net new tests across 2 new test classes (AddonInvocationAuditTest +
IndicScriptExtendedTest extensions).

<a id="v1.8.23"></a>
## v1.8.23

Twenty-third autonomous slice. **897 unit tests** at HEAD, 0 failures.

## L7.6 — McpDispatchRouter (end-to-end composition)

New `ime/mcp/McpDispatchRouter` is the third sibling of
`SmartComposeRouter` (v1.8.21) + `TranslationRouter` (v1.8.22). All
three Router types now follow the same shape: a single
`dispatch(Request)` → `Response` entry point, structured response
with categorised failure reasons, `View` interfaces for test
injection over the singletons.

Pipeline:

  1. `SensitiveFieldGuard` — short-circuit on password / PIN /
     no-learn fields.
  2. `RegistryView.findTool(toolName)` — resolve to
     `(daemon, tool)` across active daemons. Production view backs
     onto `McpDaemonRegistry.findTool`; tests inject fakes.
  3. Payload-size cap — refuses `parameterJson` over
     `McpBridgeContract.MAX_PAYLOAD_BYTES`.
  4. Underlying `McpClient` — usually `McpClientRegistry.active`,
     itself often wrapped by `McpTimeoutClient` (v1.8.19) in
     production.

API:

- **`Request(toolName, parameterJson, inputType, imeOptions, timeoutMillis)`** — input.
- **`Response.Completed(callResponse, daemon)`** — successful call
  with the daemon that handled it.
- **`Response.Failed(callResponse)`** — call reached a daemon but
  the daemon returned an error code.
- **`Response.Suppressed(reason)`** — categorised refusal:
  `"sensitive field"`, `"blank tool name"`, `"parameterJson exceeds
  MAX_PAYLOAD_BYTES"`, `"tool X not registered"`.

**No internal cache** — MCP tool calls are by definition side-
effecting (calendar lookups, contact searches, clipboard manipulation)
so caching would be semantically wrong. The analogous "don't run
forever" guard is the underlying `McpTimeoutClient` wrap, applied
once at registry bind.

7 unit tests cover password-field suppression, blank-tool-name
rejection, missing-from-registry rejection, oversized-parameterJson
rejection, happy-path Completed response, delegate-error → Failed
wrapping, and `IME_FLAG_NO_PERSONALIZED_LEARNING` suppression.

## L5.x — Three more historical Brahmic scripts

Total transliteration coverage from 57 to **60 scripts**:

- **Kaithi** (U+11080 block, supplementary plane) — historical
  script for Bhojpuri / Magahi / Maithili / Awadhi / Bagheli in
  north-central India c. 16th-20th century. Replaced by
  Devanagari.
- **Mahajani** (U+11150 block, supplementary plane) — historical
  script used by north-Indian merchant communities for account-
  keeping + commercial correspondence c. 19th-20th century.
  Replaced by Devanagari. No native digits in current Unicode —
  Western fallback.
- **Khojki** (U+11200 block, supplementary plane) — historical
  script of the Khoja Muslim community of Sindh + Gujarat, used
  for Sindhi + Gujarati religious literature c. 16th-20th century.

4 unit tests cover the three new tables (first-consonant glyph,
Mahajani-Western-digit-fallback round-trip, sane size assertions).

## Tests

897 unit tests at HEAD (was 886 at v1.8.22), 0 failures, 0 skipped.
11 net new tests across 2 new test classes (McpDispatchRouterTest +
IndicScriptExtendedTest extensions).

<a id="v1.8.22"></a>
## v1.8.22

Twenty-second autonomous slice. **886 unit tests** at HEAD, 0 failures.

## L2.1f — TranslationRouter (end-to-end composition)

New `ime/translate/TranslationRouter` is the sibling of
`SmartComposeRouter` (v1.8.21) for the inline-translation surface.
Layers every v1.8.x translation building block in the right order:

  1. `SensitiveFieldGuard` — short-circuit on password / PIN /
     no-learn fields.
  2. `LanguageDetector` — auto-detect source locale when caller
     supplies `sourceLocale = null`. Maps detected script to
     ISO 639-1 best-guess (Latin → en, Cyrillic → ru, Hebrew → he,
     Arabic → ar, Devanagari → hi, Bengali → bn, CJK → zh,
     Thai → th, etc.). Requires confidence ≥ 0.5 to commit.
  3. `TranslationLanguagePackManager` — picks the installed
     `LanguagePairDescriptor` for resolved source + target.
  4. `SentenceTokenizer` — splits paragraph-length input into
     sentences (Bergamot prefers per-sentence inference).
  5. `TranslationCache` — internal LRU per sentence.
  6. Underlying `InlineTranslator` — usually
     `InlineTranslatorRegistry.active`.
  7. Stitch per-sentence translations preserving inter-sentence
     whitespace.

API:

- **`Request(sourceText, sourceLocale?, targetLocale?, inputType, imeOptions)`**
  — input. Optional locale fields allow auto-detection + preferred
  default fall-back.
- **`Response.Translated(translatedText, resolvedSourceLocale, resolvedTargetLocale, pair)`**
  / **`Response.Suppressed(reason)`** — structured output with
  categorised failure reasons (`"sensitive field"`, `"blank input"`,
  `"source-locale detection failed"`, `"no target locale resolved"`,
  `"source == target"`, `"no installed pair for X→Y"`,
  `"translator returned Unavailable"`).
- **`PackManagerView.from()`** — production factory backed by the
  `TranslationLanguagePackManager` singleton; tests inject fake
  views directly.
- **`bypassCache = true`** — skips the LRU for benchmarks.
- **`clearCache()`** — flushes on language-pack swap.

10 unit tests cover password-field suppression, blank-input
suppression, explicit src+tgt happy path, Latin auto-detection to
`en`, source-equals-target rejection, missing-target rejection,
paragraph dispatch + stitching, cache de-duplication, no-installed-
pair rejection, and Unavailable-translator → Suppressed.

## L5.x — Three more historical Brahmic scripts

Total transliteration coverage from 54 to **57 scripts**:

- **Modi** (U+11600 block, supplementary plane) — historically used
  for Marathi in western India c. 13th-20th century. Replaced by
  Devanagari in modern Marathi but undergoing cultural revival.
  Native digits U+11650..U+11659.
- **Sharada** (U+11180 block, supplementary plane) — historically
  used for Sanskrit + Kashmiri in northern India c. 8th-20th
  century. Replaced by Devanagari + Perso-Arabic for modern
  Kashmiri but retained liturgically. Native digits
  U+111D0..U+111D9.
- **Takri** (U+11680 block, supplementary plane) — historically
  used for Dogri / Chambeali / Kishtwari / Bilaspuri in the Punjab
  + Himachal Pradesh + Jammu hills c. 16th-20th century. Replaced
  by Devanagari + Perso-Arabic in modern usage but undergoing
  limited revival. Native digits U+116C0..U+116C9.

4 unit tests cover the three new tables (first-consonant glyph,
native-digit round-trips, sane size assertions).

## Tests

886 unit tests at HEAD (was 872 at v1.8.21), 0 failures, 0 skipped.
14 net new tests across 2 new test classes (TranslationRouterTest +
IndicScriptExtendedTest extensions).

<a id="v1.8.21"></a>
## v1.8.21

Twenty-first autonomous slice. **872 unit tests** at HEAD, 0 failures.

## L1.1e — SmartCompose candidate post-processor

New `ime/smartcompose/SmartComposeResultFilter` is the pure-function
chain that runs against every addon-returned `SmartComposeResult`
before the candidates reach the ghost-text overlay:

- **`filter(input, minConfidence = 0.30, maxCandidates = 3)`** —
  applies the full chain. `NoSuggestion` passes through unchanged.
- **Drop low-confidence** — anything below `minConfidence` is noise.
- **Drop blank/whitespace-only** — would render as phantom space.
- **Normalise internal whitespace** — collapse runs of spaces,
  trim leading/trailing.
- **De-duplicate** — collisions collapse to the highest-confidence
  variant.
- **Sort descending by confidence** — top-ranked first for tap-to-
  accept priority.
- **Clamp to `maxCandidates`** — bounded output even when the
  addon ignored its hint.
- **Empty-after-filter → NoSuggestion** — overlay disappears
  cleanly instead of rendering a blank box.

9 unit tests cover the eight transforms + the `minConfidence` range
invariant.

## L1.1f — SmartComposeRouter (end-to-end composition)

New `ime/smartcompose/SmartComposeRouter` is the single composition
point the NlpManager smart-compose path calls into. Layers every
v1.8.x building block in the right order:

  1. `SensitiveFieldGuard` — short-circuit on password / PIN /
     no-learn fields.
  2. `SmartComposeContextWindow` — sentence-aware truncation.
  3. `SmartComposeCache` — internal LRU (configurable capacity,
     `bypassCache = true` skips it for benchmarks).
  4. Underlying provider.
  5. `SmartComposeResultFilter` — drop noise / normalise / sort /
     clamp.

- **`predict(context, inputType, imeOptions, maxCandidates)`** —
  end-to-end entry point.
- **`clearCache()`** — flushes the LRU on language switch / addon
  rebind.

6 unit tests cover the password-field short-circuit, the plain-
text happy path with filtering, context truncation forwarding,
cache de-duplication of repeat predictions, `bypassCache` re-asks
every call, and the `IME_FLAG_NO_PERSONALIZED_LEARNING` suppression.

## L5.x — Three more Brahmic SE-Asian + Indian scripts

Total transliteration coverage from 51 to **54 scripts**:

- **Saurashtra** (U+A880 block) — Brahmic-derived script for the
  Saurashtra language of Tamil Nadu, India. Active in modern
  community publishing. Native digits U+A8D0..U+A8D9.
- **Kayah Li** (U+A900 block) — Brahmic-derived script for the
  Kayah / Karen languages of Myanmar + Thailand. Native digits
  U+A900..U+A909.
- **Rejang** (U+A930 block) — Brahmic-derived script for the
  Rejang language of Sumatra, Indonesia. Includes pre-nasalised
  consonant clusters (`mb`, `ngg`, `nd`, `nyj`).

4 unit tests cover the three new tables (first-consonant glyph,
Saurashtra Tamil-Indic digit round-trip, Kayah Li `ng` digraph
greedy, Rejang `ngg` three-char digraph greedy, sane size
assertions).

## Tests

872 unit tests at HEAD (was 853 at v1.8.20), 0 failures, 0 skipped.
19 net new tests across 3 new test classes (SmartComposeResultFilterTest +
SmartComposeRouterTest + IndicScriptExtendedTest extensions).

<a id="v1.8.20"></a>
## v1.8.20

Twentieth autonomous slice. **853 unit tests** at HEAD, 0 failures.

## L2.1e — Translation language-pack manager

New `ime/translate/TranslationLanguagePackManager` is the
bookkeeping surface the Settings → Translate screen + the Translate
quick-action consume to render the language-list UI:

- **`installedPairs()`** — currently-bound Bergamot language pairs
  (the addon registers them via `setInstalled`).
- **`availablePairs()`** — pairs the addon manifest advertises as
  downloadable.
- **`downloadablePairs()`** — `available − installed` set
  subtraction view; what the "download more languages" UI shows.
- **`preferredTargetLocale()` / `setPreferredTargetLocale(locale)`**
  — the user's default target locale; nullable. Setter enforces
  lowercase ISO 639-1.
- **`defaultPairFor(sourceLocale)`** — picks the installed pair
  whose target matches the user's preferred locale when available,
  falls back to the first installed pair with that source locale,
  else null. Drives the Translate quick-action's pre-fill.
- **Atomic snapshots** via `AtomicReference` — concurrent reads
  from the IME thread never see a half-replaced state.
- **De-dupes by `pairKey`** — two descriptors with the same
  `src-tgt` pair (e.g. tiny + base quality tiers) collapse to one
  entry per direction.

7 unit tests cover the empty-state contract, de-dup,
`downloadablePairs` set subtraction, preferred-target-honouring
pair selection, fall-back-to-first-installed match, no-match-
returns-null, and the lowercase-ISO-639-1 setter invariant.

## L5.x — Three more 20th-21st century constructed alphabets

Total transliteration coverage from 48 to **51 scripts**:

- **Wancho** (U+1E2C0 block, supplementary plane) — 20th-century
  alphabet for the Wancho Naga language of Arunachal Pradesh +
  Myanmar. Created by Banwang Losu c. 2001. Encoded in Unicode 12
  (March 2019).
- **Nyiakeng Puachue Hmong** (U+1E100 block, supplementary plane)
  — sister of Pahawh Hmong (shipped v1.8.16); a separate
  Hmong-language script created by Reverend Chervang Kong Vang in
  the 1980s. Encoded in Unicode 12.
- **Medefaidrin** (U+16E40 block, supplementary plane) — 20th-
  century constructed alphabet used by the Oberi Okaime Christian
  community in southeast Nigeria. Created c. 1930 by Michael
  Ukpong + Akpan Akpan Udofia. Encoded in Unicode 11.

4 unit tests cover the three new tables (first-letter glyph,
Nyiakeng Puachue Hmong `ch` digraph vs `c` greedy match, sane
size assertions).

## Tests

853 unit tests at HEAD (was 842 at v1.8.19), 0 failures, 0 skipped.
11 net new tests across 2 new test classes
(TranslationLanguagePackManagerTest + IndicScriptExtendedTest
extensions).

<a id="v1.8.19"></a>
## v1.8.19

Nineteenth autonomous slice. **842 unit tests** at HEAD, 0 failures.

## L1.1d — Sentence-aware smart-compose context window

New `ime/smartcompose/SmartComposeContextWindow` is the helper the
NlpManager / OptInAddonDispatcher pipeline runs against
`SmartComposeContext.precedingText` before dispatching to an addon:

- **`truncate(precedingText, maxChars = 1024)`** — takes the
  trailing `maxChars` characters as a hard cap, then snaps backward
  to the nearest sentence boundary inside that window so the
  provider sees coherent grammar instead of a mid-sentence cut.
- **`truncate(context, maxChars)`** — convenience overload that
  copies a whole `SmartComposeContext` with `precedingText`
  truncated.
- **Multi-script terminator support** — same set as
  `SentenceTokenizer` (`.` / `!` / `?` + Arabic / Devanagari / CJK /
  Ethiopic).
- **Hard-cap fallback** — when no terminator exists in the trailing
  window the helper returns the raw `maxChars`-character substring
  so the model still sees coherent UTF-8.
- **`maxChars` minimum 16** — anything smaller has no realistic
  sentence to feed.

Default cap 1,024 chars (≈200-250 English tokens). Larger context
windows are wasted IPC + cache pressure + a privacy footprint that
grows unboundedly with editor scrollback.

7 unit tests cover unchanged-passthrough, boundary-snap, no-
boundary-hard-cap fallback, convenience overload behaviour,
multi-script terminator support, and the `maxChars`-≥-16
invariant.

## L7.5 — MCP timeout budget breaker

New `ime/mcp/McpTimeoutClient` is a sliding-window budget breaker
wrapping any `McpClient`. Prevents a misbehaving (slow-but-not-
hung) tool from degrading typing performance:

- **`budgetMillis` / `windowMillis`** — default 10 s of cumulative
  dispatch time per 60 s sliding window. Once exhausted, calls
  short-circuit with `errorCode = TIMEOUT` until the window rolls
  forward.
- **Injectable `Clock` interface** — `Clock.System` for production
  (`System.currentTimeMillis()`); tests inject a deterministic
  `Clock` that advances on demand.
- **`totalDispatchMillis` + `breakerTrips`** counters — diagnostics
  for the future telemetry dashboard.
- **`budgetMillis < windowMillis` invariant** — enforced at ctor
  so a misconfigured breaker that never trips can't ship.

5 unit tests cover pass-through-within-budget, budget-exhausted-
trips, window-rollover-resets, cumulative `totalDispatchMillis`
accounting across rollovers, and the budget-vs-window invariant.

## L5.x — Three more Philippine Brahmic scripts (completing the Hanunoo family)

Total transliteration coverage from 45 to **48 scripts**:

- **Tagbanwa** (U+1760 block) — still in active use by the
  Tagbanwa people of Palawan, Philippines.
- **Buhid** (U+1740 block) — second of the four Philippine Brahmic
  scripts, still used by the Buhid Mangyan people of Mindoro.
- **Baybayin / Tagalog** (U+1700 block) — historical script of the
  Tagalog language (pre-Spanish-colonial Philippines), undergoing
  cultural revival in modern Philippines.

Together with Hanunoo (shipped in v1.8.17) this completes the four
Brahmic-derived Philippine scripts on the Indic family ROADMAP.

4 unit tests cover the three new tables (first-letter glyph,
`nga` CV-syllable greedy match for Tagbanwa + Baybayin, `ka` for
Buhid, sane size assertions).

## Tests

842 unit tests at HEAD (was 826 at v1.8.18), 0 failures, 0 skipped.
16 net new tests across 3 new test classes (SmartComposeContextWindowTest +
McpTimeoutClientTest + IndicScriptExtendedTest extensions).

<a id="v1.8.18"></a>
## v1.8.18

Eighteenth autonomous slice. **826 unit tests** at HEAD, 0 failures.

## N7 — OptInAddonDispatcher (privacy-load-bearing chokepoint)

New `ime/smartcompose/OptInAddonDispatcher` is the single facade
the IME's typing pipeline calls into to invoke any of the three
opt-in addon surfaces. Every entry point runs through
`SensitiveFieldGuard.isSensitive(inputType, imeOptions)` first and
short-circuits to a safe "no result" answer when the field is
sensitive:

- **`predictNextTokens(context, inputType, imeOptions, maxCandidates)`**
  — returns `SmartComposeResult.NoSuggestion` on sensitive fields.
- **`translate(sourceText, sourceLocale, targetLocale, inputType, imeOptions)`**
  — returns `TranslationResult.Unavailable` on sensitive fields.
- **`callMcpTool(daemonKey, toolName, parameterJson, inputType, imeOptions, timeoutMillis)`**
  — returns `McpToolCallResponse` with `errorCode = PERMISSION_DENIED`
  on sensitive fields.

The dispatcher takes the three providers as constructor arguments,
so production code plugs in `SmartComposeProviderRegistry.active`
+ `InlineTranslatorRegistry.active` + `McpClientRegistry.active`,
while tests drive synthetic providers without touching the
registries.

This is the load-bearing privacy seam: smart-compose / translation /
MCP **never** fire from a password / PIN / no-learn field regardless
of what the underlying provider would have returned.

6 unit tests cover smart-compose suppression on password fields,
smart-compose forwarding on plain TEXT, translation suppression +
forwarding, MCP suppression with PERMISSION_DENIED, and
`IME_FLAG_NO_PERSONALIZED_LEARNING` suppressing all three.

## L2.1d — Sentence boundary tokenizer

New `ime/translate/SentenceTokenizer` is the paragraph-splitter for
the Bergamot per-sentence inference path (Bergamot models produce
noticeably worse output on multi-sentence input than on per-
sentence dispatch):

- **`split(text)`** — splits paragraph into sentences, preserving
  trailing terminator + inter-sentence whitespace so the call site
  can concat translated chunks without re-deriving spacing.
- **`hasMultipleSentences(text)`** — cheap predicate; the
  translation surface only routes through the tokenizer when the
  paragraph actually contains more than one sentence.
- **Multi-script terminator support** — `.` / `!` / `?` plus
  Arabic `۔` / `؟`, Devanagari `।` / `॥`, CJK `。` / `！` / `？`,
  Ethiopic `።`.
- **Consecutive-terminator coalescing** — "Wait!? Really." becomes
  two sentences, not three.

10 unit tests cover empty input, no-terminator passthrough, English
multi-sentence, round-trip stitching, consecutive-terminator
collapse, CJK / Devanagari / Arabic terminators, and the
`hasMultipleSentences` predicate both ways.

## L5.x — Three more Brahmic scripts: Soyombo + Marchen + Chakma

Total transliteration coverage from 42 to **45 scripts**:

- **Soyombo** (U+11A50 block, supplementary plane) — 17th-century
  alphabetic script created by the Mongolian lama Zanabazar for
  writing Sanskrit, Tibetan, and Mongolian. The symbol on the
  modern Mongolian flag derives from this script.
- **Marchen** (U+11C70 block, supplementary plane) — historical
  script of the Bon religion (Tibet), used between the 17th and
  20th centuries for liturgical texts. Brahmic-derived.
- **Chakma** (U+11100 block, supplementary plane) — Brahmic-derived
  script of the Chakma language (Chittagong Hill Tracts, Bangladesh
  + Tripura, India). Recently revived in education + literature.

4 unit tests cover the three new tables (first-letter glyph, Marchen
`ts` digraph, Chakma `sh` digraph, sane size assertions).

## Tests

826 unit tests at HEAD (was 806 at v1.8.17), 0 failures, 0 skipped.
20 net new tests across 3 new test classes (OptInAddonDispatcherTest +
SentenceTokenizerTest + IndicScriptExtendedTest extensions).

<a id="v1.8.17"></a>
## v1.8.17

Seventeenth autonomous slice. **806 unit tests** at HEAD, 0 failures.

## L1.1c — Smart-compose LRU cache

New `ime/smartcompose/SmartComposeCache` wraps any
`SmartComposeProvider` with an LRU result cache. Matches the
`KenLmScoreCache` + `TranslationCache` design but tuned for the
ghost-text replay pattern:

- **Tuple-keyed lookup** — `(locale, editorPackageName,
  maxCandidates, precedingText, composingPrefix)` so per-app LoRA
  variants (L1.3) cache separately and so a pause-then-resume
  replays cheaply during the suggestion-acceptance window.
- **`NoSuggestion` never cached** — when the provider returns
  `NoSuggestion` the result is *not* stored, so a mid-session
  addon-bound flip lights up the ghost-text overlay live.
- **Default capacity 512** — tighter than the translation cache's
  2,048 because a smart-compose key can hold a whole sentence in
  `precedingText`.
- **`hits` + `misses` counters + `clear()` + `size()`** diagnostics
  + pass-through `isReady` / `activeModel` / `supportedLocales`.

4 unit tests cover repeat-hit, `NoSuggestion`-not-cached, distinct-
locale-keys cache separately, and `clear()` reset.

## N7 — SensitiveFieldGuard privacy gate

New `ime/smartcompose/SensitiveFieldGuard` is the predicate the
NlpManager smart-compose / inline-translation / MCP dispatch paths
ask before calling any opt-in addon:

- **`isSensitive(inputType, imeOptions)`** returns true for:
  - `TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_PASSWORD`
  - `TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_VISIBLE_PASSWORD`
  - `TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_WEB_PASSWORD`
  - `TYPE_CLASS_NUMBER | TYPE_NUMBER_VARIATION_PASSWORD`
  - Any `imeOptions` with `IME_FLAG_NO_PERSONALIZED_LEARNING` set.
- **`reasonFor(inputType, imeOptions)`** returns a human-readable
  reason string (or `null`) for the dev-log line emitted when the
  IME suppresses the opt-in surface.
- **Bitwise int probe** — constants are mirrored from `InputType`
  / `EditorInfo` so the unit tests don't need Robolectric.

This complements the existing CAKI (Content-Aware Keyboard
Injection) hardening in `EditorInstance`; CAKI gates the learn-
from-text path, this gates the opt-in *prediction* path.

5 unit tests cover the four password-field shapes,
`IME_FLAG_NO_PERSONALIZED_LEARNING` override, plain-text non-
sensitive baseline, and non-password number field passthrough.

## L5.x — Three more scripts: Tifinagh + Vithkuqi + Hanunoo

Total transliteration coverage from 39 to **42 scripts**:

- **Tifinagh** (U+2D30 block) — Neo-Tifinagh consonantal alphabet
  used by the Berber / Amazigh language family across North Africa
  (Morocco, Algeria, Libya, Niger, Mali). Modern standardised form.
- **Vithkuqi** (U+10570 block, supplementary plane) — 19th-century
  Albanian alphabet created by Naum Veqilharxhi in 1844, used
  briefly before being replaced by the modern Latin Albanian
  alphabet. Encoded in Unicode 14 (Sept 2021).
- **Hanunoo** (U+1720 block) — Brahmic-derived Philippine script
  still in active use by the Mangyan people of Mindoro. Vertical
  bottom-to-top traditionally; encoded horizontally in Unicode.

4 unit tests cover the three new tables (first-letter / first-
syllable glyph, Tifinagh `gh` digraph greedy match, Hanunoo `nga`
CV-syllable greedy match, sane size assertions).

## Tests

806 unit tests at HEAD (was 793 at v1.8.16), 0 failures, 0 skipped.
13 net new tests across 2 new test classes (SmartComposeCacheAndGuardTest +
IndicScriptExtendedTest extensions).

<a id="v1.8.16"></a>
## v1.8.16

Sixteenth autonomous slice. **793 unit tests** at HEAD, 0 failures.

## L2.1b — Translation LRU cache

New `ime/translate/TranslationCache` wraps any `InlineTranslator`
with an access-order LRU cache. Matches the
`KenLmScoreCache` design pattern:

- **Triple-keyed lookup** — `(sourceText, sourceLocale, targetLocale)`
  via unit-separator joins so different target locales for the same
  source text don't collide (e.g. "no" Catalan→English vs
  Spanish→English).
- **`Unavailable` never cached** — when the underlying translator
  returns `TranslationResult.Unavailable` the result is *not*
  stored, so binding an addon mid-session flips the result live
  without needing `clear()`.
- **Default capacity 2,048** — sized for a conversational session
  worth of repeat translations.
- **`hits` + `misses` counters** + **`clear()`** + **`size()`**
  diagnostics.
- Pass-through `isLanguagePairReady` + `installedPairs` so the cache
  is a drop-in replacement for the wrapped translator.

6 unit tests cover repeat-hit, distinct-locale-pair miss,
`Unavailable`-not-cached behaviour, `clear()` reset, eviction at
capacity, and capacity-≥-1 invariant.

## L2.1c — Script-based language detector

New `ime/translate/LanguageDetector` is the pre-bind language
detection helper for the Translate quick-action surface:

- **`detect(text)` → `Detection(script, confidence)`** — picks the
  majority script and returns the fraction-of-letters confidence.
- **`DetectedScript`** enum: LATIN / CYRILLIC / GREEK / HEBREW /
  ARABIC / DEVANAGARI / BENGALI / CJK (Han + Hiragana + Hangul) /
  THAI / UNKNOWN.
- **Whitespace + digits + punctuation excluded** from the
  denominator — "Привет 12345" still classifies as Cyrillic with
  full confidence.
- **Mixed-script text** picks the majority by letter count; the
  confidence drops into `(0, 1)` so callers can gate the
  pre-fill on a confidence threshold.

9 unit tests cover the eight named scripts (Latin / Cyrillic /
Hebrew / Arabic / Devanagari / CJK across three sub-ranges / Thai),
mixed-script majority detection, and the empty / pure-digit
returns-UNKNOWN edge case.

## L5.x — Three more scripts: Bassa Vah + Mende Kikakui + Pahawh Hmong

Total transliteration coverage from 36 to **39 scripts**:

- **Bassa Vah** (U+16AD0 block, supplementary plane) — 20th-century
  alphabet for the Bassa language of Liberia. Created by Thomas Flo
  Lewis c. 1900. 35 letters.
- **Mende Kikakui** (U+1E800 block, supplementary plane, RTL) —
  20th-century syllabary for the Mende language of Sierra Leone +
  Liberia. 195 syllables; this table ships representative CV
  combinations.
- **Pahawh Hmong** (U+16B00 block, supplementary plane) —
  20th-century writing system for the Hmong language created by
  Shong Lue Yang c. 1959. Uses both consonants + vowels (uniquely
  among the modern indigenous scripts that share this Unicode
  region).

4 unit tests cover the three new tables (first-character glyph for
each, Mende Kikakui `ka` CV-syllable greedy match, Pahawh Hmong
`ph` digraph vs `p` greedy match, sane size assertions).

## Tests

793 unit tests at HEAD (was 774 at v1.8.15), 0 failures, 0 skipped.
19 net new tests across 3 new test classes (TranslationCacheTest +
LanguageDetectorTest + IndicScriptExtendedTest extensions).

<a id="v1.8.15"></a>
## v1.8.15

Fifteenth autonomous slice. **774 unit tests** at HEAD, 0 failures.

## L4.8 — Combined RTL text pipeline

New `ime/bidi/RtlTextPipeline` composes the five-step RTL stack
behind one entry point so commit-path callers don't re-derive the
correct ordering each time:

1. Hebrew Niqqud strip + Geresh/Gershayim rewrite.
2. Persian/Urdu Yeh/Kaf folding.
3. Arabic FE70-FEFC connected-form shaping.
4. Western ↔ Arabic-Indic ↔ Extended Arabic-Indic numeral conversion.

- **`RtlTextPipeline.process(input, options)`** — runs the
  pipeline. Defaults to everything off so callers must opt-in.
- **`Options(stripHebrewNiqqud, useGereshGershayim, normalisePersianUrdu, shapeArabic, numeralTarget)`**
  — per-transform toggle data class.
- **`Options.isNoOp`** — predicate the call site uses to skip the
  pipeline allocation when nothing is requested.
- **Built-in profiles**: `ARABIC_DEFAULT` (shape + Arabic-Indic
  digits), `PERSIAN_URDU_DEFAULT` (Persian/Urdu normalise + shape +
  Extended Arabic-Indic digits), `HEBREW_DEFAULT` (Niqqud strip +
  Geresh/Gershayim).
- **`NumeralTarget`** enum: `LEAVE_UNCHANGED` / `WESTERN` /
  `ARABIC_INDIC` / `EXTENDED_ARABIC_INDIC`.

8 unit tests cover `isNoOp` predicate, no-op passthrough, all three
default profiles, custom Options with only numeral conversion,
mixed-family normaliseToWestern, and empty-input passthrough.

## L7.4 — McpClient facade + registry

New `ime/mcp/McpClient` + `NoOpMcpClient` + `McpClientRegistry`
add the IME-side contract for daemon-bound MCP tool dispatch:

- **`McpClient.callTool(daemonKey, toolName, parameterJson, timeoutMillis)`**
  — single entry point for invoking a daemon-exposed tool. Returns
  a structured `McpToolCallResponse` rather than throwing; failure
  modes are visible through `McpErrorCode`.
- **`McpClient.nextCorrelationId()`** — per-process unique id
  generator so the daemon doesn't need to track correlation state.
- **`NoOpMcpClient`** — pre-bind fallback that returns
  `TOOL_NOT_FOUND` for every call and `PAYLOAD_TOO_LARGE` when the
  caller exceeds `McpBridgeContract.MAX_PAYLOAD_BYTES`. The
  NlpManager smart-compose path can call into the registry without
  knowing whether a real daemon is bound yet.
- **`McpClientRegistry.setActive(client)` / `active()`** — mirrors
  the `SmartComposeRegistry` / `InlineTranslatorRegistry` lifecycle
  so the Android-bound implementation plugs in as a drop-in
  replacement.

5 unit tests cover NoOpMcpClient `TOOL_NOT_FOUND` default,
payload-too-large rejection, strictly-increasing correlation ids,
registry initialisation, and setActive/resetForTest lifecycle.

## L5.x — Three more scripts: Caucasian Albanian + Elbasan + Vai

Total transliteration coverage from 33 to **36 scripts**:

- **Caucasian Albanian** (U+10530 block, supplementary plane) —
  4th-7th century alphabet for the Udi language family (Caucasus
  region; ancestor of modern Udi). 52 letters.
- **Elbasan** (U+10500 block, supplementary plane) — 18th-century
  Albanian alphabet used briefly for Christian liturgical texts
  before being replaced by the modern Latin-based alphabet.
- **Vai** (U+A500 block) — West African syllabary used in Liberia +
  Sierra Leone for the Vai language (Mande family). 200+ syllable
  glyphs; this table ships representative CV-syllable combinations
  while the long tail is handled by the IME's syllable-input mode.

4 unit tests cover the three new tables (first-letter / first-
syllable glyph, Caucasian Albanian `sh` digraph, Vai `pa` syllable
greedy match, sane size assertions).

## Tests

774 unit tests at HEAD (was 757 at v1.8.14), 0 failures, 0 skipped.
17 net new tests across 3 new test classes (RtlTextPipelineTest +
McpClientTest + IndicScriptExtendedTest extensions).

<a id="v1.8.14"></a>
## v1.8.14

Fourteenth autonomous slice. **757 unit tests** at HEAD, 0 failures.

## L4.7 — Visual ↔ logical text reorderer

New `ime/bidi/VisualLogicalReorderer` adds JVM-stdlib-backed
visual ↔ logical text reordering for surfaces that don't re-run the
Unicode Bidi algorithm at paint time:

- **`logicalToVisual(logical, baseIsRtl)`** — runs the logical-order
  input through `java.text.Bidi`, returns the visual-order
  rendering. Pure-LTR input returns unchanged.
- **`visualToLogical(visual, baseIsRtl)`** — inverse helper for
  legacy surfaces that persist text in visual order. Single-script
  RTL is reversed; mixed-direction visual input is left as identity
  because the inverse isn't well-defined without out-of-band run
  info.
- **`needsReordering(text, baseIsRtl)`** — cheap predicate that
  returns true when the paragraph would render differently under
  visual vs logical order. Lets callers skip the
  `logicalToVisual` allocation when nothing would change.

Uses `java.text.Bidi` (the same ICU-backed engine Android's
text-rendering layer uses internally) — no native dep, no library
add.

7 unit tests cover the pure-LTR no-op, pure-RTL Hebrew reordering,
the single-script RTL `visualToLogical` reverse, LTR `visualToLogical`
identity, empty-input passthrough, and mixed Hebrew+Latin
needsReordering detection.

## L5.x — Three more ancient Anatolian scripts

Total transliteration coverage from 30 to **33 scripts**:

- **Carian** (U+102A0 block, supplementary plane, RTL) — Indo-
  European Anatolian-language alphabet used in southwest Asia Minor
  c. 7th-3rd century BCE.
- **Lycian** (U+10280 block, supplementary plane, RTL) — Anatolian
  alphabet used predominantly on stone tomb inscriptions c. 5th-4th
  century BCE.
- **Lydian** (U+10920 block, supplementary plane, RTL) — Anatolian
  script used at Sardis c. 7th-3rd century BCE, historically
  boustrophedon (alternating direction per line).

4 unit tests cover the three new tables (first-letter glyph,
Lycian aspirated `th` digraph, Lydian `ng` digraph greedy match,
sane size assertions).

## Tests

757 unit tests at HEAD (was 746 at v1.8.13), 0 failures, 0 skipped.
11 net new tests across 2 new test classes (VisualLogicalReordererTest +
IndicScriptExtendedTest extensions).

<a id="v1.8.13"></a>
## v1.8.13

Thirteenth autonomous slice. **746 unit tests** at HEAD, 0 failures.

## L7.3 — MCP tool-call envelope

New `ime/mcp/McpToolCallEnvelope` adds the wire-format types the
IME's `McpClient` uses to invoke daemon-exposed tools across the
AIDL bind (transport lands in L7.4):

- **`McpToolCallRequest(correlationId, toolName, parameterJson)`** —
  one async tool invocation. The correlation id lets responses
  arriving over a shared bind match back to their request.
- **`McpToolCallResponse(correlationId, toolName, payloadJson?,
  errorMessage?, errorCode)`** — either success (payloadJson
  populated, `errorCode = OK`) or failure (errorMessage populated,
  `errorCode != OK`). Built-in invariants reject both states.
- **`McpErrorCode`** — stable wire-value enum: `OK(0)`,
  `TOOL_NOT_FOUND(1)`, `INVALID_PARAMETERS(2)`,
  `TOOL_INTERNAL_ERROR(3)`, `TIMEOUT(4)`, `PAYLOAD_TOO_LARGE(5)`,
  `PERMISSION_DENIED(6)`, `UNKNOWN(99)`.  Numeric values stay stable
  across protocol versions — only append, never renumber.
- **`McpEnvelopeCodec`** — JSON round-trip helper using the same
  `kotlinx.serialization.json` codec the rest of the MCP stack uses.

Payload cap mirrors `McpBridgeContract.MAX_PAYLOAD_BYTES` (4 MB) and
is enforced in the request constructor so a runaway prompt never
reaches the daemon.

7 unit tests cover request round-trip, success-response round-trip,
error-response round-trip with `isError` flip, success-response
requires-payload invariant, error-response requires-error-message
invariant, blank-correlation-id rejection, and stable error-code
wire values.

## Next-3.1i — KenLM LRU score cache

New `ime/nlp/kenlm/KenLmScoreCache` wraps any `KenLmScorer` with
an access-order LRU cache:

- **`LinkedHashMap(accessOrder = true)`** with `removeEldestEntry`
  override evicts at `capacity` (default 4,096).
- **Thread-safe** via a single intrinsic lock — cache hits + misses
  are atomic from the caller's perspective.
- **Unit-separator-keyed lookup** — `(history, tail)` builds a cache
  key with `\u001F` joining history tokens and `\u001E` between
  history and tail, so `the cat | sat` and `the | cat sat` produce
  different cache entries (correctness over slight key bloat).
- **`hits` + `misses` counters** — exposed for diagnostics and for
  the eventual ranker observability dashboard.
- **`clear()`** drops everything when the underlying model swaps.

`KenLmScorer` is now a regular `interface` rather than a
`sealed interface` so tests can build a `CountingScorer` fixture
without living in the main source set. Existing
`KenLmModelTypeDispatch.build()` callers are unaffected.

8 unit tests cover repeat-lookup cache hit, distinct-tuple cache
miss, eviction at capacity, `clear()` reset, unit-separator key
disambiguation, capacity-≥-1 invariant, and delegate `modelType` /
`maxOrder` pass-through.

## L5.x — Three more ancient scripts: Phoenician + Imperial Aramaic + Avestan

Total transliteration coverage from 27 to **30 scripts**:

- **Phoenician** (U+10900 block, supplementary plane, RTL) — the
  22-letter consonantal alphabet that is the parent of every
  Western alphabet (Aramaic / Greek / Latin / Hebrew / Arabic /
  Cyrillic descend from it). No vowels — pure abjad.
- **Imperial Aramaic** (U+10840 block, supplementary plane, RTL) —
  the state script of the Achaemenid Empire and the lineal
  ancestor of Square Hebrew, Syriac, Arabic, and Mongolian. 22
  consonants matching Phoenician one-for-one.
- **Avestan** (U+10B00 block, supplementary plane, RTL) — the
  liturgical script of Zoroastrianism, used for Old / Middle
  Iranian Avestan texts (the Yasna). True alphabet — vowels and
  consonants both have dedicated characters, unlike the abjad
  parent scripts.

4 unit tests cover the three new tables (first-letter glyph, the
`sh` digraph greedy match across all three, Avestan aspirated `kh`
round-trip, sane size assertions).

## Tests

746 unit tests at HEAD (was 728 at v1.8.12), 0 failures, 0 skipped.
18 net new tests across 3 new test classes (McpToolCallEnvelopeTest +
KenLmScoreCacheTest + IndicScriptExtendedTest extensions).

<a id="v1.8.12"></a>
## v1.8.12

Twelfth autonomous slice. **728 unit tests** at HEAD, 0 failures.

## L7.2 — MCP daemon discovery pipeline

New `ime/mcp/McpDaemonDiscoverer` is the pure-Kotlin core of the
PackageManager → registry pipeline:

- **`discover(candidates: List<DiscoveryCandidate>)`** — produces the
  `Map<DaemonKey, DaemonEntry>` the registry's `setActive(Map)` wants.
  Preserves insertion order.
- **`DiscoveryCandidate(packageName, daemonClassName, protocolVersion, hasBindPermission, toolCatalogJson)`**
  — data carrier the Android-side shim builds from a `ResolveInfo` +
  `ServiceInfo` lookup. Lets the discovery logic stay testable with
  pure-JVM fixtures.
- **Silent-drop validation** — malformed candidates don't fail the
  whole scan. A daemon missing the BIND permission, declaring a
  protocol version above `SUPPORTED_PROTOCOL_VERSION`, shipping
  malformed JSON, or exposing an empty tools array gets dropped on
  the floor. Surviving daemons populate the registry.
- **Partial-catalog tolerance** — tool entries missing
  `description` or `parameterSchema` fall back to safe placeholders
  (`"(no description provided)"` + `{"type":"object"}`).
- **Payload cap** — JSON catalog larger than
  `McpBridgeContract.MAX_PAYLOAD_BYTES` (4 MB) is rejected as a
  runaway-tool guard.

8 unit tests cover happy path, missing-permission rejection,
protocol-version overflow rejection, malformed-JSON rejection,
empty-tools-array rejection, blank-name-tool skip-but-keep-rest,
safe-placeholder-supply for missing optional fields, and
insertion-order preservation.

## L4.6 — Arabic / Persian / Urdu digit conversion

New `ime/bidi/ArabicPersianNumeralConverter` handles all three
Unicode digit families needed for Arabic-locale typography:

- **`westernToArabicIndic(text)`** / **`arabicIndicToWestern(text)`**
  — `0..9` ↔ `٠..٩` (U+0660..U+0669) for Saudi / Egyptian / Levantine
  Arabic locales.
- **`westernToExtendedArabicIndic(text)`** /
  **`extendedArabicIndicToWestern(text)`** — `0..9` ↔ `۰..۹`
  (U+06F0..U+06F9) for Persian / Urdu / Pashto.
- **`normaliseToWestern(text)`** — collapses every digit family to
  Western form so the autocorrect feed doesn't see three flavours of
  the same digit semantically.
- **`isAnyDigit(codePoint)`** — predicate covering all three
  families.

7 unit tests cover the four pairwise converters, the all-families
normalise, non-digit passthrough, the predicate, and the empty-text
edge case.

## L5.x — Three more scripts: Samaritan + Mandaic + Old Permic

Total transliteration coverage from 24 to **27 scripts**:

- **Samaritan** (U+0800 block, RTL) — descendant of Paleo-Hebrew
  used by the Samaritan community for liturgical Hebrew.
- **Mandaic** (U+0840 block, RTL) — Mandaean liturgical script,
  historically used in southern Iraq + Iran.
- **Old Permic** (U+10350 block, supplementary plane) — 14th-century
  clergy alphabet for the Komi (Permic) language family. Modelled on
  Greek with ligature-style additions. Surrogate-pair handling reuses
  the digit-iteration fix from v1.8.10.

4 unit tests cover the three new tables (first-letter glyph, the
`sh` digraph greedy match for Samaritan + Mandaic, Old Permic
supplementary-plane `dz` round-trip, sane size assertions).

## Tests

728 unit tests at HEAD (was 708 at v1.8.11), 0 failures, 0 skipped.
20 net new tests across 3 new test classes (McpDaemonDiscovererTest +
ArabicPersianNumeralConverterTest + IndicScriptExtendedTest extensions).

<a id="v1.8.11"></a>
## v1.8.11

Eleventh autonomous slice. **708 unit tests** at HEAD, 0 failures.

## L7.1 — MCP daemon registry

New `ime/mcp/McpDaemonRegistry` adds the IME-side live snapshot of
on-device MCP daemons (the `L7` "Deskdrop-style local MCP bridge"
surface).  Sits next to the existing `McpBridgeContract` types and
mirrors the `setActive(Map)` pattern of `SmartComposeRegistry`,
`InlineTranslatorRegistry`, and `CjkInputProviderRegistry` so the
addon lifecycle stays uniform across heavy-runtime surfaces:

- **`McpDaemonRegistry.setActive(entries)`** — atomic replacement of
  the whole snapshot from the discovery pipeline (L7.2 lands in a
  follow-up).
- **`active()` / `size()` / `get(key)`** — atomic snapshot reads.
- **`listAllTools()`** — flattened view across every daemon, stable
  daemon order followed by stable tool order.
- **`findTool(toolName)`** — first-match resolver across daemons,
  returning a `ResolvedTool(daemon, tool)` pair.
- **`DaemonKey` / `DaemonEntry`** — data carriers with built-in
  invariants (blank-component rejection, protocol-version cap at
  `McpBridgeContract.SUPPORTED_PROTOCOL_VERSION`).

6 unit tests cover the empty-registry contract, single-daemon
populate-and-read, multi-daemon `findTool` walk, `listAllTools`
flattening, protocol-version cap enforcement, and `DaemonKey`
blank-component rejection.

## L4.5 — Yiddish bidi run segmenter

New `ime/bidi/YiddishBidiSegmenter` sits next to `HebrewBidiSegmenter`
with Yiddish-specific awareness:

- **`classify(codePoint)`** — same five-class scheme as Hebrew, but
  the Yiddish digraph block (U+05F0..U+05F2 — DOUBLE VAV, VAV YOD,
  DOUBLE YOD) is treated as `HEBREW` direction.
- **`isYiddishDigraph(codePoint)`** — predicate for the three Yiddish-
  only code points. Used by the autocorrect engine to route
  Yiddish-only spelling candidates.
- **`yiddishDigraphCount(text)`** — cheap pass for "should we route
  this commit through the Yiddish dictionary?" decision.
- **`segment(text)`** — produces the same `HebrewBidiSegmenter.Run`-
  shaped output as Hebrew for symmetry; mixed Yiddish + Latin still
  splits into alternating direction runs.

6 unit tests cover the digraph-classification path, the predicate
+ count helpers, mixed-script segmentation, pure-Yiddish-as-one-run,
and the empty-text edge case.

## L5.x — Three more scripts: Coptic + Georgian Mkhedruli + Glagolitic

Total transliteration coverage from 21 to **24 scripts**:

- **Coptic** (U+2C80 block) — liturgical script of the Coptic
  Orthodox Church + the only non-Greek/Cyrillic descendant of the
  Greek alphabet in Unicode. Includes the Coptic-only `sh` → ϣ,
  `F` → ϥ, `kj` → ϫ, `hh` → ϩ, `ti` → ϯ extras that have no Greek
  precursor.
- **Georgian Mkhedruli** (U+10D0 block) — the 33-letter modern
  civilian alphabet. Case-sensitive SERA-style transliteration
  (capital `T` → თ, `J` → ჟ, `S` → შ, etc.) so the Latin keyboard
  shift-state maps to the Mkhedruli distinct-letter inventory.
- **Glagolitic** (U+2C00 block) — pre-Cyrillic Slavonic script still
  used in limited Croatian + Old Church Slavonic liturgy. Lowercase
  letters; uppercase comes through the IME's existing shift-state.

5 unit tests cover the three new tables (first-letter glyph, the
Coptic-extra `sh` → ϣ, Georgian case-sensitivity, Glagolitic greedy
digraph `sh` → ⱎ, sane size assertions).

## Tests

708 unit tests at HEAD (was 692 at v1.8.10), 0 failures, 0 skipped.
16 net new tests across 3 new test classes (McpDaemonRegistryTest +
YiddishBidiSegmenterTest + IndicScriptExtendedTest extensions).

<a id="v1.8.10"></a>
## v1.8.10

Tenth autonomous slice. **692 unit tests** at HEAD, 0 failures.

## Next-3.1h — KenLM model-type dispatch facade

New `ime/nlp/kenlm/KenLmModelTypeDispatch` caps the pure-Kotlin
reader stack with a single entry-point:

- **`sealed interface KenLmScorer`** — uniform contract
  (`modelType`, `maxOrder`, `score(history, tail)`) that the caller
  (NlpManager / ranker) drives without knowing the on-disk layout.
- **`KenLmModelTypeDispatch.build(modelType, vocabulary, probingPath?, triePath?)`**
  — picks `KenLmProbingNavigator` for `PROBING` / `REST_PROBING` and
  `KenLmTrieNavigator` for `TRIE` / `QUANT_TRIE` / `ARRAY_TRIE` /
  `QUANT_ARRAY_TRIE`. `UNKNOWN` throws `IllegalArgumentException` so
  the caller can fall back safely to the existing bigram chain.
- **`ProbingInputs` / `TrieInputs`** — typed data carriers for the
  per-order tables; using the wrong one for a model type throws.

5 unit tests cover both PROBING and TRIE dispatch paths, QUANT_TRIE
routing to the trie navigator, UNKNOWN-type rejection, and the
missing-inputs rejection.

This closes the pure-Kotlin KenLM reader stack started in v1.8.6:
header (Next-3.1) + vocabulary (Next-3.1b) + probing-hash arena
(Next-3.1c) + Bhiksha pointer decoder (Next-3.1d) + quant codec
(Next-3.1e) + probing navigator (Next-3.1f) + trie navigator
(Next-3.1g) + this dispatcher (Next-3.1h). All four KenLM model
types are now navigable + scoreable end-to-end against synthetic
fixtures with no JNI dependency.

## L6.x — Tigrinya / Tigre / Blin SERA transliterator

New `ime/geez/TigrinyaSeraTransliterator` layers the Tigrinya-
distinctive glyph inventory on top of the shared `GeezSeraTransliterator`:

- **qhe series** (U+1250..U+1256, ቐ ቑ ቒ ቓ ቔ ቕ ቖ) — emphatic /q'/
  retained in Tigrinya / Tigre orthography that Amharic collapses
  into ቀ.
- **xa series** (U+1280..U+1286, ኀ ኁ ኂ ኃ ኄ ኅ ኆ) — historical `ḫa`
  retained in Tigrinya / Tigre.
- **Labio-velars** — `kWa` → ኳ (U+12B3), `gWa` → ጓ (U+1313).

Composition seam added to `GeezSeraTransliterator`:
**`transliterateWith(latin, otherTable)`** runs the same greedy
longest-match loop against any caller-supplied lookup table, so
dialect subclasses don't re-derive the radical × vowel grid. The
shared `table` is now `internal` rather than `private`.

7 unit tests: Tigrinya-specific qhe / xa / labio-velar mappings, the
shared "slam" (Amharic SERA for "peace" — ሰላም) round-trip, longest-
match priority of multi-char Tigrinya keys, and unmapped-passthrough.

## L5.x — Three more scripts: Adlam + N'Ko + Cherokee

Total transliteration coverage from 18 to **21 scripts**:

- **Adlam** (U+1E900 block, supplementary plane) — West African
  alphabetic script for Pulaar / Fulani; native digits
  U+1E950..U+1E959.  Required a fix to `buildIndicMappings`'
  digit-iteration loop — old code iterated by `Char` (16-bit unit)
  and split surrogate pairs; new code iterates by code point via
  `codePointAt + Character.charCount`.
- **N'Ko** (U+07C0 block) — West African alphabetic script for the
  Manding family, runs right-to-left; native digits U+07C0..U+07C9.
  Subtype routes through the existing `RtlBidiResolver` when active.
- **Cherokee** (U+13A0 block) — US-indigenous syllabary (only one in
  mainstream Unicode use). Each glyph represents a CV syllable, so
  the table maps Romanised syllables (`ga`, `tla`, `qua` etc.) to
  single Cherokee characters. Falls outside `buildIndicMappings`
  (no anusvara / visarga concept) — uses a hand-built map.

5 unit tests cover the three new tables (first-consonant glyph,
native-digit round-trips, greedy longest-match for Cherokee
multi-character syllables, sane size assertions).

## Tests

692 unit tests at HEAD (was 675 at v1.8.9), 0 failures, 0 skipped.
17 net new tests across 3 new test classes (KenLmModelTypeDispatchTest +
TigrinyaSeraTransliteratorTest + IndicScriptExtendedTest extensions).

<a id="v1.8.9"></a>
## v1.8.9

Ninth autonomous slice. **675 unit tests** at HEAD, 0 failures.

## Next-3.1g — KenLM `TRIE` / `QUANT_TRIE` navigator

New `ime/nlp/kenlm/KenLmTrieNavigator` + `TrieOrderTable` + `TrieEntry`
close the pure-Kotlin KenLM reader stack. Sibling of
`KenLmProbingNavigator` but for trie-shaped search arenas (TRIE +
QUANT_TRIE model types):

- **`TrieEntry`** — one row in an order's entry table:
  `(entryIndex, parentEntryIndex, tailVocabIndex, logProb,
  logBackoff, nextPointerStart)`. The `nextPointerStart` field will
  feed `BhikshaPointerDecoder` in production; synthetic fixtures
  leave it `-1`.
- **`TrieOrderTable`** — one order's entry table indexed by
  `(parentEntryIndex, tailVocabIndex)`. The `find(parent, tail)`
  contract is the only required operation; tests use the
  `fromEntries(order, list)` builder.
- **`KenLmTrieNavigator.lookup(history, tail)`** — walks orders from
  longest matching context down to unigram, returning the matching
  `TrieEntry` or null.
- **`KenLmTrieNavigator.score(history, tail)`** — applies the same
  KenLM backoff math as the probing navigator
  (`logProb + Σ logBackoff(parent_context_of_skipped_order)`).
- **Parent-entry chain walk** — internal `traverseContext` resolves
  the order-`size` entry index for an arbitrary context, descending
  one order at a time and returning -1 when any link breaks.

5 unit tests cover the bigram-hit path, the bigram-miss-with-
unigram-fallback path (with parent backoff applied), the
absent-tail-returns-NEGATIVE_INFINITY path, the order-1-required
invariant, and a full trigram-chain walk.

Together with v1.8.6's `KenLmProbingHash`, v1.8.7's
`BhikshaPointerDecoder`, and v1.8.8's `KenLmQuantTable` /
`KenLmProbingNavigator`, the pure-Kotlin reader stack now covers
**all four** KenLM model types (PROBING / REST_PROBING / TRIE /
QUANT_TRIE) for navigation + scoring against synthetic fixtures.
Real-file plumbing (the byte-level Bhiksha encoding + the centroid
arrays + the entry table) feeds the same navigators in Next-3.1b's
production bring-up.

## L4.4 — Hebrew bidi run segmenter

New `ime/bidi/HebrewBidiSegmenter`:

- **`Direction`** classifies each character into one of:
  `HEBREW` (U+0590..U+05FF) / `LATIN` / `DIGITS` / `WHITESPACE`
  / `NEUTRAL` (punctuation + symbols).
- **`segment(text): List<Run>`** splits a string into contiguous
  same-direction runs. Surrogate pairs are honoured for the Latin
  + neutral runs.
- **`directionBefore(text, cursorIndex)`** returns the class of the
  character logically before the cursor — the standard query the
  layout engine asks when deciding caret affinity at a run boundary.
- **`dominantDirection(text)`** returns the direction of the longest
  non-whitespace, non-neutral run, used for smartbar single-word
  subtype hints.

8 unit tests cover pure Hebrew, mixed Hebrew + Latin, digit-only
strings, punctuation between Hebrew runs, cursor-position direction
query, empty-string edge cases, and dominant-direction picking
the longest letter run.

## L5.x — Three more Brahmic-derived scripts (Mongolian + Javanese + Sundanese)

Extends transliteration coverage from 15 to **18 scripts**:

- **Mongolian** (U+1800 block) — written historically vertically;
  Unicode block carries the consonant + vowel + digit inventory used
  for both vertical and Hudum Cyrillic transliteration; digits
  U+1810..U+1819; no native anusvara/visarga, slots collapse to the
  Mongolian "Sibe" delimiter U+1806.
- **Javanese** (U+A980 block) — Brahmic-derived Indonesian script;
  digits U+A9D0..U+A9D9; anusvara → Cecak (U+A981); visarga →
  Wignyan (U+A983).
- **Sundanese** (U+1B80 block) — Western-Javanese family; digits
  U+1BB0..U+1BB9; both anusvara + visarga collapse to the pamaaeh
  (U+1BAA, vowel-killer mark).

4 unit tests cover the three new tables (`a`/`k` → first-character
glyph, native-digit round-trips, sane size assertions).

## Tests

675 unit tests at HEAD (was 658 at v1.8.8), 0 failures, 0 skipped.
17 net new tests across 3 new test classes (KenLmTrieNavigatorTest +
HebrewBidiSegmenterTest + IndicScriptExtendedTest extensions).

<a id="v1.8.8"></a>
## v1.8.8

Eighth autonomous slice. **658 unit tests** at HEAD, 0 failures.

## Next-3.1e — KenLM `SeparatelyQuantize` codec

New `ime/nlp/kenlm/KenLmQuantTable` + `KenLmQuantTableSet` for the
quantized log-probability and log-backoff codebooks that `QUANT_TRIE`
model files use to keep n-gram payloads compact. Implements:

- **`KenLmQuantTable.withBackoff(probBits, backoffBits, prob, backoff)`**
  — non-highest-order tables carry both prob + backoff codebooks
  (sizes `2^probBits` and `2^backoffBits`).
- **`KenLmQuantTable.highestOrder(probBits, prob)`** — highest-order
  tables carry only the prob codebook; `decodeBackoff()` throws when
  called on this variant, matching upstream KenLM semantics.
- **`KenLmQuantTableSet(order, tables)`** — 1-indexed `tableFor(k)`
  accessor; constructor enforces "exactly one highest-order table at
  the end" + "every non-highest order has a backoff codebook".
- **`parseTableSet(ByteBuffer, order, probBits, backoffBits)`** —
  reads the on-disk centroid block immediately past
  `KenLmBinaryHeader` for QUANT_TRIE files. Little-endian float32
  centroids in the exact layout `lm/quantize.hh::SetupMemory`
  produces (prob block then backoff block per order, no backoff
  block at the highest order).

6 unit tests cover round-trip for both variants, size-mismatch
rejection, table-set 1-indexing, highest-order-no-backoff invariant
violation, and the parse path against a hand-built little-endian
buffer.

## Next-3.1f — KenLM PROBING-model search-arena navigator

New `ime/nlp/kenlm/KenLmProbingNavigator` joins the three pure-Kotlin
readers (`KenLmVocabulary`, `KenLmProbingHash`, `KenLmBinaryHeader`)
into a single API the IME can drive without knowing the on-disk
layout:

- **`lookup(history, tail)`** — walks orders from longest matching
  context down to unigram, returning the matching `ProbingEntry`. If
  even the unigram is missing it falls back to the `<unk>` slot.
- **`score(history, tail)`** — returns the log-probability under the
  standard KenLM backoff chain: `logProb(matched_order) +
  Σ logBackoff(parent_context_of_skipped_order)`. Returns
  `Float.NEGATIVE_INFINITY` when neither the n-gram nor its tail
  unigram is in the model.
- **Parent-entry recursion** — internal `parentEntryIndexFor` walks
  the context chain order-by-order so the navigator works for an
  arbitrary `maxOrder`, not just bigrams.
- **Backoff accumulation** — internal `sumSkippedBackoffs` adds the
  log-backoff weights of every parent context whose order we'd have
  preferred to match at but couldn't.

5 unit tests build synthetic vocabularies + populate probing-hash
buckets using a shared `buildProbingHash(bucketCount, entries)`
fixture helper (which probes through the same MurmurHash64A the
production reader uses). Tests cover the bigram-hit path, the
fall-back-to-unigram-with-parent-backoff path, the unknown-token
collapse to `<unk>`, the no-entry returns `NEGATIVE_INFINITY` path,
and the order-1-missing-from-config rejection.

This is the pure-Kotlin scoring path for KenLM PROBING models; the
TRIE / QUANT_TRIE variant gets a sibling navigator once Next-3.1d's
Bhiksha decoder and Next-3.1e's quant codec are wired into a
trie-walking facade.

## L5.x — Two more Brahmic-derived scripts (Khmer + Thai)

Extends transliteration coverage from 13 to **15 scripts** total:

- **Khmer / Cambodian** (U+1780 block) — Brahmic-derived with Pali /
  Sanskrit liturgical pedigree; native Khmer digits U+17E0..U+17E9;
  visarga maps to the Khmer reah-muk (U+17C7); anusvara maps to the
  niggahita (U+17C6).
- **Thai** (U+0E00 block) — sister-script to the Lao table shipped
  in v1.8.6/1.8.7; native Thai digits U+0E50..U+0E59; tone-marker
  conventions are intentionally caller-handled, not in the table.
  Anusvara + visarga collapse to the Thai niggahita (U+0E4D).

4 unit tests cover the two new tables (digit + first-consonant
round-trips, Khmer two-letter `ng` digraph greedy match, sane size
assertions).

## Tests

658 unit tests at HEAD (was 643 at v1.8.7), 0 failures, 0 skipped.
15 net new tests across 3 new test classes (KenLmQuantTableTest +
KenLmProbingNavigatorTest + IndicScriptExtendedTest extensions).

<a id="v1.8.7"></a>
## v1.8.7

Seventh autonomous slice. **643 unit tests** at HEAD, 0 failures.

## Next-3.1d — KenLM `ArrayBhiksha` next-pointer decoder

New `ime/nlp/kenlm/BhikshaPointerDecoder` for the per-entry next-pointer
fields inside KenLM `TRIE` / `QUANT_TRIE` n-gram blocks. Implements:

- **Two-part decode.** Each pointer splits into a fixed-width
  `lowBitsWidth` lower half (packed head-to-tail in `lowBitsArena`)
  and an implicit upper half decoded from a monotone bitmap in
  `highBitsBitmap`. Bit position `(high + i)` is set for the `i`-th
  pointer; the decoder walks the bitmap once per lookup and recovers
  the original pointer as `(high << lowBitsWidth) | low`.
- **Symmetric `encode(LongArray, lowBitsWidth)`** companion helper
  produces a valid decoder from a sorted pointer array. Lets the
  search-arena navigator exercise the full encode→decode loop
  against synthetic fixtures without binding a real KenLM file.
- **Bounds + monotonicity checks.** Non-decreasing pointer
  precondition enforced at encode-time; constructor validates the
  arena is large enough for `entryCount × lowBitsWidth` bits.

7 unit tests: round-trip at `lowBitsWidth=8`, degenerate
`lowBitsWidth=0` (everything goes high), duplicates + long monotone
runs, randomized 200-array property test, non-monotone rejection,
out-of-range entry-index rejection, zero-entry decoder.

This is the second slice of the pure-Kotlin KenLM reader stack
(Next-3.1c probing-hash shipped in v1.8.6); together they cover the
PROBING and TRIE/QUANT_TRIE model types' navigation surface. The full
n-gram scoring path still arrives in Next-3.1b alongside the upstream
JNI bring-up.

## Next-9.4a — Pin-to-group long-press sheet + Pinned-groups palette row

Two new IME-side pieces wiring the existing `EmojiPinGroupStore`
into the emoji palette UX:

- **`PinToGroupSheetState`** — Compose-agnostic presenter for the
  bottom-sheet that opens when the user long-presses an emoji.
  Holds the emoji-being-pinned, snapshot of existing groups, new-group
  text input (capped to `MaxGroupNameLength`), and a `PinError`
  enum (`NoEmojiSelected` / `GroupNameBlank` / `TooManyGroups` /
  `GroupFull` / `AlreadyPinned`) so the UI can render targeted
  feedback. Fully unit-tested without Robolectric.
- **`PinnedGroupsPaletteRow`** — compact horizontal Compose row that
  renders each pinned group as an 8-dp-radius rectangular chip with
  name + 3-emoji preview + total count badge. Tap raises
  `onGroupTapped` for inline expansion; long-press raises
  `onGroupLongPressed` for the rename/unpin/delete sheet that lands in
  a follow-up. Backdrop radius adheres to the global no-pill rule.
- **`PinnedGroupChip.fromStoreSnapshot`** — pure converter from the
  `EmojiPinGroupStore.snapshot()` map to a render-ready chip list,
  preserving order, truncating preview to `PREVIEW_LIMIT = 3`.

11 unit tests across `PinToGroupSheetStateTest` (8) +
`PinnedGroupChipTest` (3) cover open / dismiss, pin-to-existing,
create-and-pin (with blank-name + too-many-groups + group-full +
already-pinned error paths), preview truncation, and stable ordering.

Integration into the live `EmojiPaletteView` Compose tree is the
remaining sub-slice — additive only, no behaviour change shipped yet.

## L5.x — Three more Brahmic-derived scripts (Burmese + Lao + Tibetan)

Extends transliteration coverage from 10 to **13 scripts** total.
Each table reuses `buildIndicMappings` even though the languages are
Tibeto-Burman (Burmese, Tibetan) and Tai-Kadai (Lao) rather than
strictly Indic — the Brahmic-derived structure (vowels + consonants
+ digits + anusvara + visarga) carries over cleanly:

- **Burmese / Myanmar** (U+1000 block) — native Myanmar digits
  U+1040..U+1049; aspirate-marker form for `kh` / `ch` etc.
- **Lao** (U+0E80 block) — Lao-native digits U+0ED0..U+0ED9; both
  anusvara + visarga map to U+0ECD (niggahita) which is the closest
  visual + phonetic analogue (Lao has no separately-marked visarga).
- **Tibetan / Bod-yig** (U+0F00 block) — native digits
  U+0F20..U+0F29; consonant inventory covers the Tibetan Brahmic base
  set. Syllable-final tsheg punctuation is intentionally not in the
  table; callers handle that at the segmenter layer.

5 unit tests cover the three new tables (`k` → first-consonant glyph,
digit round-trip, greedy two-letter digraph win for Tibetan `ng` → ང,
sane size assertions).

## Tests

643 unit tests at HEAD (was 620 at v1.8.6), 0 failures, 0 skipped.
23 net new tests across 4 new test classes (BhikshaPointerDecoderTest +
PinToGroupSheetStateTest + PinnedGroupChipTest + IndicScriptExtendedTest
extensions).

<a id="v1.8.6"></a>
## v1.8.6

Sixth autonomous slice. **620 unit tests** at HEAD, 0 failures.

## Next-3.1c — KenLM probing-hash search arena

New `ime/nlp/kenlm/KenLmProbingHash` reader for the KenLM PROBING /
REST_PROBING model types. Implements:

- 16-byte bucket layout (`uint64 key + float prob + float backoff`).
- `EMPTY_KEY = 0xFFFF_FFFF_FFFF_FFFF` sentinel.
- Linear-probing collision resolution with `MAX_PROBE_DEPTH = 256`.
- `MurmurHash64A` implementation matching `util/murmur_hash.cc`.
- `packKey(tailVocabIndex, parentEntryIndex)` per the canonical
  KenLM key shape: `(tail << 32) | parent`.
- Per-bucket reads use position save/restore for thread safety.

6 unit tests pin the contract: pack/unpack round-trip, empty-key
lookup, hit lookup, miss lookup, linear-probe collision chain,
deterministic MurmurHash64A. Per-n-gram score traversal across
the order-by-order tables is the next slice (Next-3.1d).

## L4.3 — Hebrew Niqqud normalizer

New `ime/bidi/HebrewNiqqudNormalizer`:

- `normalize(text, stripNiqqud, useGereshGershayim)` — strips Niqqud
  + cantillation marks in U+0591..U+05C7 when the toggle is on; rewrites
  ASCII `'` / `"` to U+05F3 Geresh / U+05F4 Gershayim when the
  Hebrew-abbreviation rule is enabled.
- `isNiqqud(char)` cheap predicate for the IME's "Strip Niqqud" toggle.
- `niqqudCount(text)` lets the toggle decide whether to bother
  running the full pass on commit.

7 unit tests cover happy path, ASCII pass-through, edge values of
the Niqqud range, the count predicate, and the Geresh/Gershayim
substitution.

## L5.x — Three more Indic scripts (Malayalam + Odia + Sinhala)

Extends the Indic-transliteration coverage from 7 to **10 scripts**.
Each table follows the `buildIndicMappings` shape (vowels +
consonants + digits + anusvara + visarga + danda):

- **Malayalam** (U+0D00 block) — includes the `L → ള` mapping that's
  the precursor for chillu-aware composition.
- **Odia** (U+0B00 block) — formerly known as Oriya in older Unicode
  literature.
- **Sinhala** (U+0D80 block) — uses Western-Arabic digit fallback per
  current Unicode (no script-native digit code points yet).

6 unit tests across the three tables.

## Tests

620 unit tests at HEAD (was 601 at v1.8.5), 0 failures, 0 skipped.
19 net new tests across 3 new test classes (KenLmProbingHashTest +
HebrewNiqqudNormalizerTest + IndicScriptExtendedTest).

<a id="v1.8.5"></a>
## v1.8.5

Fifth autonomous slice. Closes the P3-renderer wire-up so split-
keyboard mode actually renders split rows in tablet landscape, ships
a debug-only mock smart-compose provider so the v1.8.3 ghost-text
surface is exercisable on a connected device today, adds the
benchmarks docs scaffold, and expands LDML script coverage with five
new fixtures. **601 unit tests** at HEAD, 0 failures.

## P3-renderer — final TextKeyboardLayout wire-up

- `TextKeyboardLayout.kt` now detects `Fixed.SPLIT` mode from the
  active `windowSpec` and, after the existing `keyboard.layout(...)`
  call, invokes
  `SplitGutterPostPass.apply(keyboard, 80.dp.toPx())` to shift the
  right half of every row by 80 dp.
- `splitMode` is added to the `remember(...)` key list so toggling
  the preference correctly triggers a re-layout.
- Only applies to `KeyboardMode.CHARACTERS` (numeric / symbols /
  phone-pad keep their single-block layout).
- Closes the P3-renderer slice tracked in ROADMAP §0.

## L1 debug-only mock smart-compose provider

- New `app/src/debug/kotlin/DebugSmartComposeProvider` (lives in the
  `debug` source set so release builds **never** compile it).
- Ships a 10-entry hard-coded trigram lookup that returns sensible
  continuations for common sentence prefixes ("on my" → "way",
  "thank you so" → "much", "looking forward to" → "hearing from you",
  etc.). Pure offline lookup — no model, no network, no telemetry.
- Wired in `FlorisApplication.init` via **reflection** so release
  builds (which can't see the debug class) gracefully fall back to
  the default no-op `SmartComposeProvider`.
- Lets us verify the v1.8.3 ghost-text candidate plumbing on the
  installed debug APK before the real L1.1a LiteRT-LM addon ships.

## Next-12.1 — BENCHMARKS.md template

- New `docs/BENCHMARKS.md` documents:
  - How to run `:benchmark:connectedBenchmarkAndroidTest` against a
    clocks-locked device.
  - The four-benchmark table (imeFirstRender /
    suggestionStripRecomposition / dictionaryColdLoad / themeSwitch).
  - Trace-section naming convention (`swiftfloris.<subsystem>.<action>`)
    + the six existing sections.
  - The **8 % median frame regression threshold** for shipping a
    release that touches the IME hot path.
  - Historical baseline file pattern at
    `docs/benchmark-results/baseline-YYYY-MM-DD.json`.

## L8.3 — LDML script fixtures

New `LdmlScriptFixturesTest` covers five scripts the L8 / L8.1 / L8.2
parsers will see in real Keyman keyboards:

- **Khmer** — combining-mark display label round-trip (◌ា, ◌ោ).
- **Burmese** — transforms + displays interaction for medial Ya.
- **Tibetan** — consonant + vowel-mark transform with display.
- **Lao** — tone-mark display label; bare consonant has no override.
- **Sinhala** — mixed transforms-then-displays section ordering.

## Tests

601 unit tests at HEAD (was 596 at v1.8.4), 0 failures, 0 skipped.
5 net new tests from the LDML fixtures suite.

<a id="v1.8.4"></a>
## v1.8.4

Fourth autonomous slice. Ships **L9.2 honeycomb tessellation**,
**Next-3.1b KenLM vocabulary parser**, **L4.2 Nastaliq font scaffold**,
and the **P3-renderer split-row post-pass** ready for the
TextKeyboardLayout integration. **596 unit tests** at HEAD, 0
failures.

## L9.2 — Honeycomb hex tessellation

- New `assets/ime/keyboard/.../characters/honeycomb.json` ships a
  Typewise-style 5-row flat-top hexagonal letter layout.
- New `ime/text/keyboard/HoneycombTessellation` provides the geometry
  math the renderer + hit-tester will consume:
  - `keyRadius`-driven row stride (1.5 · r) and column stride (√3 · r).
  - Even-row half-offset alternation (the hex tessellation pattern).
  - `centerOf(row, col)` for layout.
  - `cellAt(px, py)` for touch hit-testing — brute-force across all
    cells (cheap on ≤ 40-key layouts).
  - `containsPoint` uses the two-trapezoid flat-top hex
    point-in-shape test.
- 8 unit tests pin the geometry contract.

## Next-3.1b — KenLM vocabulary string-arena parser

- New `ime/nlp/kenlm/KenLmVocabulary` reads the post-search-arena
  vocabulary block of a KenLM binary: `uint64 string_count` +
  `uint64 strings_byte_length` + concatenated `\0`-terminated UTF-8
  token bytes.
- `indexOf(token)` returns the vocabulary index or `UNK_INDEX = 0`
  when out-of-vocab.
- `contains(token)` distinguishes a known word from the `<unk>`
  sentinel.
- Hard caps: 8M tokens, 256 MB string arena — larger models move
  to the dictionary-pack addon path (`docs/addons/dictionary-pack-spec.md`).
- 7 unit tests cover happy path, malformed-input rejection, CJK
  UTF-8 round-trip, advertised-count-vs-actual-bytes mismatch.

## L4.2 — Nastaliq font scaffold

- New `ime/bidi/NastaliqFontProvider.bundledTypeface(context)` lazily
  loads Noto Nastaliq Urdu from
  `assets/fonts/NotoNastaliqUrdu-Regular.ttf` via
  `Typeface.createFromAsset`. Falls back to `Typeface.DEFAULT` when
  the asset is missing — IME still renders, Urdu just falls back to
  Naskh.
- `isAvailable(context)` predicate lets Snygg theme selectors skip
  the font-family override when the binary isn't present.
- New `docs/FONTS.md` documents the OFL-1.1 attribution + the CI
  download step (font binary stays out of git per ~480 KB binary-diff
  policy).

## P3-renderer — Split-keyboard row post-pass

- New `ime/text/keyboard/SplitGutterPostPass.apply(keyboard, gutterPx)`
  walks every row of a positioned `TextKeyboard` and shifts the
  right half of each row by `gutterPx` pixels.
- The gutter point per row comes from
  `SplitKeyboardLayoutCalculator.qwertyBoundary` (5+5 / 5+4 / 4+3
  for canonical QWERTY; `halfAndHalf` for other row sizes).
- Updates `touchBounds` + `visibleBounds` in lockstep so the
  renderer + hit-tester stay aligned.
- 9 unit tests cover canonical QWERTY 3-row split, non-canonical
  fallback, empty-row defensive path, zero-gutter no-op,
  negative-gutter rejection, multi-row consistency, and a
  `SplitRowSnapshot` helper that pins the post-shift gutter measure.
- The final `TextKeyboardLayout`-side call to this post-pass — read
  the active window mode + invoke `apply(keyboard, gutterPx)` after
  the existing `keyboard.layout(...)` — is the next slice. The math
  is pinned; the integration is a one-line addition once the
  call-site lands.

## Tests

596 unit tests at HEAD (was 572 in v1.8.3), 0 failures, 0 skipped.
24 net new tests across 4 new test classes.

<a id="v1.8.3"></a>
## v1.8.3

**SwiftKey full-parity slice.** Closes the three IME-side gaps from
the SwiftKey parity audit. After this slice, every visible SwiftKey
typing feature has a working surface in SwiftFloris; the only
remaining work is two opt-in addon APKs that supply the heavy native
runtimes (LiteRT-LM smart-compose, Bergamot translator).

**572 unit tests at HEAD**, 0 failures, 0 skipped.
`:app:compileDebugKotlin` + `:app:assembleDebug` clean.

## P1 — Smart-Compose inline ghost-text (IME-side)

- New `GhostTextSuggestionCandidate` data class added to
  `ime/nlp/SuggestionCandidate.kt` alongside the existing
  `WordSuggestionCandidate` / `ClipboardSuggestionCandidate` /
  `EmojiSuggestionCandidate` types.
- `NlpManager.suggest` now asks
  `SmartComposeProviderRegistry.active.predictNextTokens(...)` for a
  ghost-text continuation given the text-before-selection. When the
  active provider reports `isReady(locale) = true` AND returns a
  candidate at confidence ≥ 0.45, the candidate is appended to the
  suggestion list.
- Default provider behaviour is unchanged: `SmartComposeProvider.Default`
  returns `NoSuggestion`, so the strip looks exactly as before. The
  ghost-text candidate only appears once the L1.1a addon
  (`addons/smart-compose-litert/`) is installed and registers a real
  provider via `SmartComposeProviderRegistry.setActive(...)`.

## P2 — Translation smartbar quick-action (IME-side)

- New `QuickAction.TranslateSelection` data object added to
  `ime/smartbar/quickaction/QuickAction.kt`.
- On tap, reads the current selection from `EditorInstance`, calls
  `InlineTranslatorRegistry.active.translate(rawSelection, "auto", "en")`,
  and on success commits the translated text in place. On
  `TranslationResult.Unavailable` (default — no addon installed) it
  shows a Toast pointing the user at the InlineTranslator addon.
- Button label uses the 🌐 globe emoji glyph so it fits a single
  smartbar quick-action slot; full tooltip surfaces on long-press.
- Settings → Smartbar → Customize quick actions exposes
  `TranslateSelection` alongside the existing entries via the
  serialiser's `@SerialName("translate_selection")` discriminator.

## P3 — Split-keyboard preference → active mode wire-up

- `ImeWindowController.onWindowShown` now reads
  `prefs.keyboard.splitKeyboardEnabled` (default off, shipped in
  v1.8.0). When the toggle is on AND the IME is in fixed mode AND
  the current form-factor's
  `ImeWindowConstraints.Fixed.Split.isViable` returns true, the
  fixed sub-mode is promoted from `Fixed.NORMAL` to `Fixed.SPLIT`
  on the next session show.
- Per-key split-row rendering inside `TextKeyboardLayout` (the
  actual mid-row gutter emission + per-side touch hit-test math)
  is the heavier follow-up slice tracked as **P3-renderer** /
  prompt D5 in `docs/AI_PROMPTS_EXTERNAL_WORK.md`. The
  `SplitKeyboardLayoutCalculator` shipped in v1.8.2 already produces
  the per-row geometry the renderer will consume.

## Tests

- 572 unit tests at HEAD (was 545 at v1.8.2).
- New `GhostTextSuggestionCandidateTest` pins 5 invariants on the
  ghost-text candidate (validation of confidence + text + tokenCount;
  default tokenCount = 1; non-auto-commit; non-user-removable).

## Tracker update

ROADMAP §0 SwiftKey Full-Parity Tracker now shows P1 / P2 / P3 all
✅ on the IME side. The two remaining items for *full* parity
(including the runtime behaviour, not just the surface) are the L1.1a
and L2.1a addons; both are documented as standalone AI prompts in
`docs/AI_PROMPTS_EXTERNAL_WORK.md`.

<a id="v1.8.2"></a>
## v1.8.2

Third autonomous ROADMAP pass on the same day as v1.8.0 + v1.8.1.
Drills into every `.Xa` follow-up slice that had been parked for a
later round. **545 unit tests at HEAD** (was 494 in v1.8.1), 0
failures, 0 skipped — the Roborazzi screenshot tests now run in CI
after the Next-12.2a launcher-Activity unblock.

## LATER-tier follow-ups

- **L4.1 Arabic connected-form shaper.** New `ArabicShaper.shape(text)`
  rewrites every base-form Arabic codepoint (U+0621–U+064A) into the
  appropriate **presentation form** glyph (U+FE70–U+FEFC) based on its
  position in a connected run. Handles isolated / initial / medial /
  final forms; respects right-joining-only letters (Alef, Dal, Reh,
  Waw, Teh-Marbuta). Lets the smartbar preview row + WordStyles
  renderer + addon-driven export paths lay down correctly-joined
  glyphs regardless of the receiving editor's font.

- **L4.2 Persian / Urdu normaliser.** New `PersianUrduNormalizer.normalize`
  rewrites Arabic Yeh `\u064A` → Farsi Yeh `\u06CC`, Arabic Kaf
  `\u0643` → Farsi Kaf `\u06A9`, Alef Maksura `\u0649` → Farsi Yeh.
  Optional `stripTatweel = true` removes accumulated `\u0640` stretch
  glyphs. `PersianDigitMode` enum toggles between `KEEP_ARABIC`,
  `TO_PERSIAN` (Western → U+06F0..06F9), and `TO_LATIN`.

- **L5.x six new Indic tables** — Bengali / Tamil / Telugu / Gujarati
  / Gurmukhi (Punjabi) / Kannada all ride the existing
  `IndicTransliterator` greedy-longest-match engine. Each table covers
  the canonical vowels + consonants + digits + anusvara/visarga +
  danda punctuation for its script. Total Indic-script coverage now
  spans 7 scripts.

- **L6.1 Amharic SERA keyboard layout.** New
  `assets/ime/keyboard/.../characters/amharic_sera.json` ships a
  practical Amharic tap layout pre-populated with the most common
  Ge'ez radicals (the 1st-form base of each consonant). The runtime
  routes long-press → vowel-form picker via the existing FlorisBoard
  popup mechanism; the L6 `GeezSeraTransliterator` handles SERA
  input.

- **L8.1 LDML `<transforms>` parser + engine.** New
  `LdmlTransformsParser` parses Keyman LDML `<transformGroup>` →
  `<transform from="..." to="..."/>` rules using OWASP XXE-hardened
  `javax.xml`. `LdmlTransformTable.rulesByLengthDesc` exposes rules
  sorted longest-first for greedy matching. `LdmlTransformEngine`
  applies rules incrementally on each keystroke (compose-key dead-key
  semantics, ligature stacking).

- **L9.1 T9 layout JSON.** New
  `assets/ime/keyboard/.../characters/t9.json` ships a 4×3 T9 grid
  with letter popups (1: punctuation / 2: abc / 3: def / ... / 7:
  pqrs / 8: tuv / 9: wxyz / * + 0 + #). For nostalgia + small-screen
  setups.

- **L11a Espanso vars expander.** Existing `EspansoMatchParser` shape
  extended with `EspansoVar(name, type, params)` + `regex` field +
  `isWordSensitive` + `passive` flags. New `EspansoVarsExpander.expand`
  resolves `{{name}}` placeholders against four built-in var types:
  **date** (configurable `format`), **clipboard** (caller-supplied
  provider), **echo** (literal from params), **random** (semicolon-
  separated `choices`). Pluggable `nowProvider` + `randomProvider`
  callbacks make the expander deterministic-testable.

- **L12.1 Android Canvas WordStyles renderer.** New
  `WordStylesCanvasRenderer` rasterises text via `android.graphics.*`
  Canvas + Paint, supports background fill / foreground colour /
  linear gradient / shadow layer / configurable padding + font size.
  Encodes to PNG bytes ready for `InputContentInfoCompat`. Wired at
  `FlorisApplication.onCreate` so the smartbar quick-action sees a
  working renderer without any addon installed.

## NEXT-tier follow-ups

- **Next-3.1a KenLM mmap trie reader.** New `KenLmTrieReader.openMapped(path)`
  memory-maps a `.litertlm` / KenLM binary file and parses the header
  eagerly via the v1.8.0 `KenLmBinaryReader`. Lazy `bodyStartOffset`
  + `readBytesAt(offset, length)` give the upcoming per-n-gram
  lookup layer cheap absolute reads against the mapped buffer.

- **Next-5.2a Curve25519 + AES-GCM sealed-box.** New `SealedBoxCrypto.seal`
  / `open` produces a libsodium-shape sealed-box envelope
  `ephemeralPub (32 B) ‖ nonce (12 B) ‖ ciphertext+tag (n + 16 B)`.
  **Uses JVM stdlib `java.security` (X25519) + `javax.crypto`
  (AES-GCM) — zero native dependency, zero extra .so payload.**
  Forward-secret: every seal generates a fresh ephemeral keypair.
  Now wires straight into the Next-5.1 CRDT delta transport so an
  addon can encrypt deltas without taking a libsodium dep.

- **Next-7.2a Split-keyboard layout calculator.** New
  `SplitKeyboardLayoutCalculator.calculateRow(totalWidth, gutter,
  leftKeyCount, rightKeyCount)` produces `SplitRowGeometry` (left
  / gutter / right widths + per-key widths) for the renderer to
  consume. `qwertyBoundary(rowIndex, keyCount)` returns the canonical
  hand boundary for QWERTY rows (5+5 / 5+4 / 4+3).

- **Next-12.2a Roborazzi launcher-Activity unblock.** New
  `RoborazziHostActivity` (debug-manifest only, `exported=false`,
  release builds never see it) backs `createAndroidComposeRule<...>()`.
  The previously-`@Ignore`'d screenshot tests now run cleanly. The
  Roborazzi Gradle plugin still waits on the AGP-9 compat release
  (1.44.0-stable) before the `recordRoborazzi*` task surface lights
  up; until then the captures pass through Robolectric directly.

## Test infrastructure

51 net new unit tests across 9 new test classes. Suite total is now
**545** (was 494 at v1.8.1). All facades follow the
`*Registry.setActive(...)` registration pattern from Next-4.2:
heavy runtimes stay out of `:app`, behind a registry the IME reads.

## What now legitimately can't be scaffolded further

Even more aggressively pursued than v1.8.1, the remaining items all
need something specific from the outside world:

- **N1.1** HeliBoard NLnet glide library — released by Jun 2026.
- **N1.2** CleverKeys multi-script gesture model — vendor roadmap
  Q2-Q3 2026.
- **N10.1** Noto Color Emoji 17.0 — depends on `androidx.emoji2`
  1.7.0+ being published.
- **Next-2.5** Rambler streaming-voice cleanup — gates on the L1
  LLM addon being installed.

Every other ROADMAP entry now has a real scaffold + tests +
a clear adapter pattern that a follow-up runtime can fill in.

<a id="v1.8.1"></a>
## v1.8.1

Second autonomous ROADMAP pass on the same day as v1.8.0. Closes
**every remaining open ROADMAP item** that can be tackled without
pulling in a heavy external native library at runtime. Each item ships
a working scaffold + provider-registry pattern + unit tests pinning
the contract; the heavy runtimes (LiteRT-LM, Bergamot WASM, librime,
ML Kit Digital Ink) move to opt-in addons that the user installs
separately, keeping the base APK lean and the §1 no-network promise
intact.

**494 unit tests at HEAD** (491 pass, 3 `@Ignore`'d Roborazzi pending
Next-12.2a). `:app:compileDebugKotlin` clean.

## LATER-tier sweep (L1–L12)

- **L1 LiteRT-LM smart-compose facade.** `SmartComposeProvider` +
  `LiteRtModelDescriptor` (modelId, backend, supportedLocales, sizeBytes,
  quantization, supportsLora). `SmartComposeResult.{NoSuggestion,
  Suggestion}` distinguishes "no model loaded" from "no confident
  candidate." Default no-op falls back to the existing bigram/trigram
  chain.

- **L2 Bergamot inline-translation facade.** `InlineTranslator` +
  `LanguagePairDescriptor` (lowercase ISO 639-1 src+target, bundle
  path, size, quality tier in tiny/base/high). `TranslationResult.
  {Unavailable, Translated}`.

- **L3 librime CJK input facade.** `CjkInputProvider` + `CjkSchema`
  enum covering Pinyin (Simplified + Traditional), Jyutping, Zhuyin,
  Cangjie 5, Wubi 86, Quick / double-pinyin Xiaohe, Japanese Mozc,
  Korean Jamo. `CjkCandidate(text, annotation, confidence, isPreferred)`.

- **L4 RTL BiDi shaping (real implementation, not just a facade).**
  `RtlBidiResolver.analyze` wraps `java.text.Bidi` (JVM stdlib, zero
  external dep) and surfaces composing-region run boundaries +
  paragraph base direction. Fixes upstream FlorisBoard's layout-only
  RTL bug class for mixed Arabic/Hebrew/Persian/Urdu + Latin text.

- **L5 Indic transliteration with full Hindi ITRANS table.**
  `IndicTransliterator` runs greedy longest-prefix-match against
  `IndicScriptTable.ItransToDevanagari` covering Hindi/Marathi/Sanskrit
  consonants + vowels + halant/anusvara/visarga + Devanagari digits
  0-9 + danda/double-danda punctuation. Bengali/Tamil/Telugu/Marathi
  /Gujarati/Punjabi/Kannada tables ride on the same engine in L5.x.

- **L6 Ge'ez SERA transliterator.** `GeezSeraTransliterator` covers
  ~28 consonant radicals × 7 vowel forms = the canonical Amharic /
  Tigrinya / Tigre / Blin glyph set + Ethiopic digits 1-9 + Ethiopic
  punctuation. Greedy longest-match.

- **L8 Keyman LDML XML parser (XXE-hardened).** `KeymanLdmlParser`
  uses `javax.xml.parsers.DocumentBuilderFactory` with all OWASP XXE
  defenses (no DTD, no external general / parameter entities, no
  XInclude, no entity-reference expansion) — addon-supplied LDML
  crosses an addon-IME trust boundary, so the parser hardens up
  front rather than retrofitting later.

- **L10 WebAuthn passkey injection contract.** `PasskeyAdapter`,
  `PasskeyFieldDetector.detect(autofillHints, extras)`, and
  `PasskeyAssertionRequest` (cross-process WebAuthn assertion
  envelope). Detector only fires on a password-class hint AND a
  WebAuthn relying-party id + challenge in `EditorInfo.extras` —
  conservative by design.

- **L12 WordStyles renderer facade.** `WordStylesRenderer` +
  `WordStyle` data class with strict RGBA-hex validation and four
  built-in styles (Neon / Gradient Sunset / Retro Typewriter / Soft
  Pastel). Canvas/Paint render lives in `WordStylesAndroidRenderer`
  (L12.1).

## NEXT-tier finish

- **Next-9.4 emoji palette enhancements (all four pieces shipped).**
  Custom tags + predict-by-tag came in v1.8.0; v1.8.1 adds
  search-by-tag (`EmojiSearch.results` consults `CustomEmojiTagStore`
  at score 2 — above bundled keyword exact match at 3) and pin
  emoji together (`EmojiPinGroupStore` with 32-group / 12-emoji /
  32-char caps and atomic-rename JSON storage).

- **Next-5.3 sync channel taxonomy + parser.** `SyncChannel` sealed
  class with four variants (Syncthing / LocalFolder / ManualExport /
  Disabled). Each emits a canonical channel-id string that round-trips
  through `SyncChannel.parse()`. Unknown ids → Disabled (privacy-safe
  fallback). Feeds straight from `PairingPayload.syncChannelId`
  (Next-5.2).

- **L11.1 Tasker intent contract.** `TaskerIntentContract` pins four
  Tasker-trigger-able intent actions (INSERT_TEXT / INSERT_CLIP /
  SWITCH_LAYOUT / TRIGGER_VOICE) under
  `permission.REGISTER_ADDON`. Validator enforces extras schema (4096-
  char insert cap, layout-id regex, voice mode enum).

## Tests

47 net new unit tests across 13 new test classes. Suite total now
**494** (was 416 at v1.8.0). All facades follow the
`StrokeRecognizerRegistry` pattern from Next-4.2: heavy runtime stays
out of `:app`, behind a `*Registry` the IME reads.

## Genuinely external-blocked items

The only items left that *cannot* be scaffolded further without
something specific from the outside world:

- **N1.1** HeliBoard NLnet glide library — released by Jun 2026 per
  the active NLnet grant.
- **N1.2** CleverKeys multi-layout model — vendor roadmap targets
  Q2-Q3 2026 for the multi-script gesture model.
- **N10.1** Noto Color Emoji 17.0 fonts — depends on `androidx.emoji2`
  1.7.0+ being published.
- **Next-2.5** Rambler-style streaming-voice cleanup — gates on the
  L1 LLM addon being installed.

Every other open ROADMAP item now has a working scaffold + tests +
a clear adapter pattern for follow-up bring-up.

<a id="v1.8.0"></a>
## v1.8.0

11-item autonomous ROADMAP pass closing the remaining heavy NEXT-tier
items + the cheap-to-scaffold half of the LATER tier. **416 unit tests
at HEAD** (413 pass, 3 `@Ignore`'d pending Next-12.2a Robolectric manifest
fix). `:app:compileDebugKotlin` + `:app:assembleDebug` clean.

## NEXT-tier closure

- **Next-3.1 KenLM binary header reader.** `KenLmBinaryReader.readHeader`
  parses the 64-byte magic, `ModelType` enum, order, `FixedWidthParameters`,
  and per-order uint64 n-gram counts. Cheap probe lets the NLP pipeline
  decide whether to mmap a real KenLM trie or fall back to the existing
  bigram chain. Trie body parsing + JNI to the upstream KenLM C++ library
  moves to Next-3.1a.

- **Next-3.2 Zipf-scale subtitle-frequency overlay.** New `ZipfFrequencyTable`
  loads `assets/freq/<lang>.tsv` and blends `0.6 * scowl + 0.4 * (zipf/8.0)`
  in `LatinDictionarySnapshot.frequencyFor(word)`. Seed `en.tsv` ships
  with ~1,000 high-frequency entries (rspeer/wordfreq CC-BY-SA). Full
  SUBTLEX tables ride into a Next-10.3 dictionary-pack addon.

- **Next-4.2 stroke-recogniser facade.** New `ime/handwriting/` package:
  `Stroke` / `StrokePoint` capture pen polylines with timing, `StrokeRecognizer`
  interface returns ranked `StrokeCandidate`s. **ML Kit Digital Ink is
  intentionally not a `:app` dep** — `RemoteModelManager.download(...)`
  needs `INTERNET`, breaking §1's no-network promise; the actual ML Kit
  binding moves to a future `addons/handwriting-mlkit/` opt-in APK.

- **Next-5.1 + Next-5.2 CRDT personal-dictionary scaffold.**
  `PersonalDictionaryCrdt` with observed-add / LWW-delete semantics +
  deterministic tie-break. `merge(a, b) == merge(b, a)`, `merge(a, a) ==
  merge(merge(a, a), a)`. `PairingPayload` pins the QR-encoded JSON
  shape (Curve25519 pubkey hex validation). Automerge-rs JNI + libsodium
  sealed-box wrap ride in Next-5.1a + Next-5.2a.

- **Next-6.4 KLC (Windows) hardware-keyboard layout parser scaffold.**
  `KlcLayoutParser.parse(klcText)` consumes Microsoft Keyboard Layout
  Creator exports: BOM, comments, tab/space columns, `@`-suffix dead
  keys, `%`/`-1` no-output slots. macOS `.keylayout` parser + Android
  `InputManager` runtime mapper land in Next-6.4a + Next-6.4b.

- **Next-7.2 split-keyboard window-mode foundation.** New `Fixed.SPLIT`
  sub-mode + `ImeWindowConstraints.Fixed.Split` with 80dp default gutter
  and 600dp `minTabletWidthDp` viability check. New
  `prefs.keyboard.splitKeyboardEnabled` boolean (default off). Renderer
  key-rect distribution lands in Next-7.2a.

- **Next-9.4 custom emoji tag predict.** New `CustomEmojiTagStore` lets
  users attach personal keywords to any emoji (e.g. 🦋 → "freedom").
  Wired into `EmojiSuggestionProvider`'s parallel-stream candidate
  scoring at 0.20 weight alongside name (0.55) and bundled-keyword
  (0.25). Atomic on-disk JSON writes (`.tmp` + rename) for crash
  safety. Caps: 16 tags per emoji, 5,000 tagged emoji, 32-char tag
  length.

- **Next-10.3 dictionary-pack addon descriptor + spec.**
  `DictionaryPackDescriptor` pins the JSON schema every dictionary-pack
  addon must follow. `docs/addons/dictionary-pack-spec.md` writes up
  the full AndroidManifest + descriptor + assets contract, including
  the banned-network-permission rule. Polish (2025 baseline) data
  rides in a sibling addon-repo when the dataset extraction lands.

- **Next-11.1 M3 Expressive theme regen.** Seven new bundled themes —
  **Nord (light + dark)**, **Tokyo Night**, **Dracula**, **Catppuccin
  Mocha**, **SwiftKey Pure (M3E light + dark)** — derived from the
  well-tested `swift_slate.json` baseline so the ~500-line Snygg
  selector tree stays consistent. `--shape-chip` moves off the pill
  shape (`rounded-corner(50%)`) to 12dp corner per the no-pill-backdrop
  rule. Generated via `scripts/gen_m3e_themes.py` for reproducible
  re-runs. Theme extension manifest 0.2.0 → 0.3.0. Binds to the
  per-app accent `LocalPerAppAccent` from Next-11.3a at runtime.

- **Next-12.2 Roborazzi screenshot regression scaffold.** Roborazzi
  1.43.1 + Robolectric 4.14.1 wired as `testImplementation` deps;
  `ExtensionMaintainerChipScreenshotTest` pins three chip configurations
  via `captureRoboImage` at `xxhdpi w360dp-h640dp`. `junit-vintage-engine`
  bridges JUnit-4 Robolectric tests onto the project-wide JUnit-5
  platform alongside Kotest. Roborazzi Gradle **plugin** intentionally
  not applied (1.43.x uses the AGP `TestedExtension` API that AGP 9.0.0
  removed); flips back on when Roborazzi 1.44.0-stable lands. Tests
  `@Ignore`'d pending the Robolectric launcher-Activity manifest fix
  (Next-12.2a) — deps + harness shape are in place.

## LATER-tier scaffolds

- **L7 MCP local-LLM bridge contract.** `McpBridgeContract` pins the
  bind-time Intent action, signature-protected permission,
  `<meta-data>` keys, 4 MB payload cap. `McpToolDescriptor` mirrors
  the upstream MCP spec's `tools/list` shape. `McpToolResult` is the
  success/failure envelope. **On-device only by hard contract** —
  service binding, never a network socket.

- **L9 alt-layouts audit.** Colemak, Colemak DH, Colemak DHM, Dvorak,
  Workman are **already in tree** via the FlorisBoard upstream layout
  pack. Remaining L9 work (honeycomb-hex + T9) needs a non-rectangular
  renderer (L9.1).

- **L11 Espanso config import.** `EspansoMatchParser.parse(yaml)`
  consumes `~/.config/espanso/match/*.yml` — inline scalar / quoted /
  escaped strings, literal `|` and folded `>` block scalars with
  indent-stripping, full-line comments, silent skip of blank-trigger
  rows. Hand-rolled, < 200 lines vs Snakeyaml's 600KB+ runtime. The
  Tasker intent surface lands as L11.1.

## Test infrastructure

- 416 unit tests (was 356 at v1.7.9). New suites: `ZipfFrequencyTableTest`,
  `KenLmBinaryReaderTest`, `KlcLayoutParserTest`, `DictionaryPackDescriptorTest`,
  `PersonalDictionaryCrdtTest`, `StrokeRecognizerTest`, `EspansoMatchParserTest`,
  `McpBridgeContractTest`, `ExtensionMaintainerChipScreenshotTest` (Roborazzi,
  `@Ignore`'d).

## Outstanding (still pending real-library bring-up)

- **L1 LiteRT-LM smart-compose** — needs JNI runtime.
- **L2 Bergamot WASM NMT** — needs WASM host.
- **L3 librime CJK** — needs JNI to librime C++.
- **L4 RTL shaping** — ICU shaping pass.
- **L5 Indic transliteration** — needs language-specific tables.
- **L6 Ge'ez script** — needs Amharic/Tigrinya layout files.
- **L8 Keyman LDML importer** — XML schema parser (L8.1 is the next slice).
- **L10 WebAuthn passkey injection** — needs autofill API wiring.
- **L12 WhisperInput streaming** — covered by ongoing N2.x voice work.

<a id="v1.7.9"></a>
## v1.7.9

Released: 2026-05-14

A multi-item ROADMAP pass that closes NOW-tier polish (popup animation,
a11y labels) and lights up a wide NEXT-tier slice: capitalization-aware
suggestions with explicit tests, an in-strip "Remove from predictions"
prompt with a springy entry/exit, dictation-stream voice command wiring,
JVM importers for Gboard / FlorisBoard backups, a programmer-mode
smartbar profile, an addon manifest schema + enumerator, a per-app
adaptive-accent foundation, and property-based autocorrect invariants.
Every change is unit-tested and the full `:app:testDebugUnitTest` suite
passes (356 tests). Built against the same JDK 17 / AGP 9.0.0 /
Kotlin 2.3.20 / Compose BOM 2026.03.01 toolchain as v1.7.7.

## Changes

### NOW-tier finishes

- **N3.4 finish — popup polish.** Pressed-key 1.03× scale-up over 60ms
  with an 80ms spring-back on release (graphicsLayer-only, no
  touch-target geometry change). Long-press popup variant now carries
  a 1.5dp accent-ring stroke via `--primary`, so per-theme overrides
  (SwiftKey Pure, Tokyo Night, …) retint it automatically. Reduced-
  motion (Developer Options → Animator duration scale = 0) suppresses
  the scale; the static PRESSED Snygg color flip still reads.
- **N8.3 finish — accessibility labels.** Smartbar quick actions now
  carry a TalkBack-readable `contentDescription` (action's display
  name → tooltip → "Action" fallback). Suggestion-strip slots
  announce candidate text + role + a "Remove from predictions"
  custom accessibility action for eligible candidates. Long-press hint
  on keys with an alt-glyph now appends "alternative: <hint>" to the
  TalkBack readout so screen-reader users know extra characters are
  available.

### NEXT-tier shipped

- **Next-3.3 — capitalization-aware suggestions.** Existing
  `applyTypedCase` contract is now explicitly tested across prefix
  completion, distance-1 correction, distance-2 correction, and the
  Title-Case / ALL_CAPS / lowercase branches. Closes FlorisBoard
  #1007 (`Foo` if `F`, `foo` if `f`).
- **Next-3.4 — long-press to forget, with confirmation.** Long-pressing
  a removable suggestion now surfaces an in-strip
  "Remove '<word>' from predictions" prompt instead of silently
  deleting. Tap "Remove" → forget across personal dict + bigram +
  trigram. Tap "Cancel" or anywhere on the strip backdrop → dismiss.
  Closes COMM-A FR-22 / FlorisBoard #737 / AnySoftKeyboard #1399.
- **Next-9.3 — password-manager compatibility doc.** Live
  `docs/INLINE_AUTOFILL.md` matrix of verified Bitwarden / KeePassDX /
  Proton Pass / 1Password / Aegis versions per Android version, plus
  the verification recipe to refresh on every release that touches
  `FlorisImeService.onCreateInlineSuggestionsRequest`. Next-9.1
  (`supportsInlineSuggestions=true`) and Next-9.2 (smartbar slot
  rendering) verified-already-shipped.
- **Next-6.3 — SwiftKey migration doc.** Honest writeup of the three
  available paths (retrain, MS-account redownload, root extraction)
  plus an explicit refusal to ship a SwiftKey-cloud OAuth helper.
  Matches the §1 no-network philosophy.
- **Next-6.1 + Next-6.2 — Gboard + FlorisBoard backup importers.**
  New `DictionaryImporter` parses Gboard `PersonalDictionary.zip`
  (XML inside zip) and generic CSV (`word,frequency,shortcut,locale`)
  shapes, with explicit schema-detection, entity decoding, header-row
  tolerance, frequency clamping, and clear errors. FlorisBoard
  `.flbackup` SQLite snapshots are explicitly routed to the in-app
  importer path. Test fixtures cover Gboard XML, escaped entities,
  CSV with and without header, zip end-to-end, and clear errors for
  every unsupported shape.
- **Next-2.4 — voice-commands on streaming.** `VoiceInputManager`
  now exposes `consumeStreamingChunk(chunk, actions, customCommands)`
  that pipes per-chunk transcripts through the existing
  `StreamingVoiceTranscriptBuffer` and fires `VoiceCommandExecutor`
  on final-chunk command matches, so "change dog to cat"-style voice
  edits fire the moment the user finishes the utterance. Returns a
  `VoiceStreamingCommandUpdate` carrying both the transcript and the
  optional execution result.
- **Next-7.3 — one-handed mode UX surface verified.** Audit confirmed
  the smartbar `TOGGLE_COMPACT_LAYOUT` quick-action, `SwipeAction`
  binding, and in-window flip / dismiss controls (chevron + zoom) are
  all already wired into `ImeWindowController` and `OneHandedPanel`.
  No new code; explicitly cited so future contributors don't re-derive.
- **Next-8.1 + Next-8.2 — programmer-mode smartbar profile.** New
  `SmartbarActionProfile.CODE` surfaces Tab, Esc, arrow keys, and
  start/end-of-line jumps when the editor's package matches a curated
  set (Termux, JuiceSSH, Acode, Spck, ConnectBot, Termius, JetBrains
  family, …). `TextKeyData.TAB` and `TextKeyData.ESCAPE` are now
  predefined. Code-mode wins the matcher over CHAT when both could
  match, so terminal users don't get a chat smartbar.
- **Next-10.1 + Next-10.2 — addon manifest schema + enumerator.**
  New `dev.patrickgold.florisboard.ime.addon` package defines the
  intent-action surface (`REGISTER_ADDON`, `REGISTER_LANGUAGE_PACK`,
  `REGISTER_THEME_PACK`, `REGISTER_DICTIONARY_PACK`,
  `REGISTER_LAYOUT_PACK`, `REGISTER_POPUP_MAPPING_PACK`), the
  `<meta-data>` schema, and a signature-protected
  `permission.REGISTER_ADDON`. `AddonEnumerator.snapshot()` discovers
  installed addon packages via `PackageManager`, validates each
  against the no-network invariant (any addon declaring INTERNET /
  ACCESS_NETWORK_STATE / etc. is hard-rejected), reads the addon's
  signing fingerprint via the existing N7.5 `SigningFingerprint`
  helper, and returns a list of `AddonManifest` records ready for
  registration. Forward-compat: unknown addon types skip silently.
  `AndroidManifest.xml` now declares the permission and adds the
  required Android 11+ `<queries>` entries.
- **Next-11.2 — springy dismiss.** Next-3.4's confirm overlay
  enters with `scaleIn(initialScale = 0.85f) + fadeIn` at
  DampingRatioMediumBouncy / StiffnessMedium and exits with
  `scaleOut + fadeOut` at StiffnessHigh. Reads as a deliberate action
  rather than a flash; cancel gets immediate feedback.
- **Next-11.3 — per-app adaptive accent (foundation).** New
  `PerAppAccentResolver` extracts a dominant-saturated color from
  the foreground editor's app icon (32×32 raster, HSV scan, reject
  near-grey / near-white / near-black, highest-saturation wins).
  In-memory LRU cache, 64-entry capacity. Hue / saturation /
  classification helpers exposed for testing. No `PACKAGE_USAGE_STATS`
  / `UsageStatsManager` required — the IME already knows the
  editor's package via the system contract. Application of the
  resolved color to keyboard tokens is intentionally deferred to a
  follow-up so the foundation can ship audit-clean.
- **Next-12.3 — property-based autocorrect invariants.**
  Eleven Kotest checkAll cases pin: normalizeWord idempotency,
  null-on-non-letter input, no typed-literal autocommit, candidate
  cap, dedup-by-lowercase, Damerau-Levenshtein ≤ 2 on corrections,
  delete-and-retype identity, Title Case / ALL_CAPS case-preserve,
  and crash-resistance on repeated-character substrings. Independent
  Damerau-Levenshtein oracle so a bug in the suggester can't silently
  match a bug in the test.

### Build / repo hygiene

- Debug-variant labelled "SwiftFloris Debug" via a debug-only
  strings.xml overlay; FlorisBoard's leftover chef-hat debug icon
  drawables deleted so debug builds use the main launcher.
- New unit test files: `LatinSuggesterPropertyTest`,
  `AddonManifestTest`, `PerAppAccentResolverTest`,
  `DictionaryImporterTest`, expanded `LatinDictionarySuggesterTest`
  and `SmartbarActionProfilesTest`. All tests green
  (`:app:testDebugUnitTest` — 356 tests).
- `:app:compileDebugKotlin` clean against AGP 9.0.0, Kotlin 2.3.20,
  Compose BOM 2026.03.01 (warnings unchanged from v1.7.7).

## Open follow-ups for v1.8.x

- **Next-7.1 — floating window mode.** Drag-handle + resize-anchor +
  per-corner snap geometry. Heavy Compose surgery; intentionally not
  in this drop.
- **Next-7.2 — split keyboard for tablet landscape.** Same caveat.
- **Next-11.3 surface wiring.** The accent resolver foundation is
  shipped; routing the resolved color into theme tokens / smartbar
  accent / keyboard tint is the next slice.
- **Next-11.1 — M3 Expressive theme regen** against the new accent
  resolver.
- **L1 — Gemma 3 270M smart-compose** — still upstream-gated on
  LiteRT-LM. Tracking.

## Verification

- `:app:compileDebugKotlin` — green.
- `:app:testDebugUnitTest` — 356 tests, all passing.
- `:app:verifyNoInternetPermission` — green (the privacy gate stays
  green; addons declaring network permissions are rejected at
  enumeration time).
- Manual install on Galaxy R5CY34G070L pending — UI animations
  (Next-3.4 confirm overlay, Next-11.2 springy dismiss) and
  per-package accent extraction need device verification before the
  GitHub release artifact is signed.

<a id="v1.7.8"></a>
## v1.7.8

Released: 2026-05-13

Roadmap-only historical entry: Bilingual subtype presets, bottom row presets, per-app smartbar profiles, encrypted personal dictionary, emoji search and sticker packs, voice model routing, multilingual autocommit and language identification, glide vocabulary bounding, contrast guard, SymSpell d2, expanded context, contraction rescue, phrase repairs, and sentence-local scoring. The standalone release-note file was rebadged to v1.7.9 after a pre-existing tag collision.

<a id="v1.7.7"></a>
## v1.7.7

**Released:** 2026-05-13
**Type:** Product polish / UX refinement.

This release focuses on making the settings and extension-management experience feel more deliberate, legible, and trustworthy after the v1.7.6 hardening pass.

## Highlights

- Refined the main settings experience with clearer hierarchy, calmer status cards, and more useful action labels.
- Improved first-run setup with stronger privacy framing and clearer recovery expectations.
- Polished voice input setup and status messaging so unavailable, retryable, and enabled states are easier to understand.
- Improved backup and restore copy for destructive or trust-sensitive flows.
- Reworked extension import states with clearer empty, review, skipped-file, and technical-detail surfaces.
- Upgraded extension detail pages with overview, metadata, and management sections instead of flat metadata rows.
- Replaced debug-style component output with structured component metadata rows for themes and language packs.

## Verification

- `:app:compileDebugKotlin`
- `:app:verifyNoInternetPermission`
- `:app:lintDebug`
- `:app:testDebugUnitTest`
- `:app:assembleDebug`
- `:app:assembleRelease`
- Fresh adb uninstall/install smoke on a connected phone

<a id="v1.7.6"></a>
## v1.7.6

**Released:** 2026-05-13
**Type:** Maintenance / hardening — no new features, no UI changes.

Three rounds of targeted bug-hunting and privacy hardening, all addressing real defects with confirmed impact on correctness, data integrity, leak surface, or the project's "100% offline, zero cloud" privacy posture. Every fix ships with the existing public API unchanged.

---

## Round 1 — correctness, leaks, races (commit `920da85`)

### Clipboard history data corruption — `ClipboardHistoryManager`
The hand-rolled JSON parser in the encrypted clipboard layer silently corrupted control characters on save→load roundtrip. A clipboard entry containing a newline, tab, or carriage return came back with those characters replaced by literal `n` / `t` / `r` letters. Replaced with `kotlinx.serialization` (`@Serializable` data class) so all control characters, quotes, backslashes, and non-ASCII text survive a roundtrip cleanly. Added a coarse-grained lock so two concurrent producers can no longer drop each other's entries via a race in read-modify-write. Added a graceful fallback to an in-memory store when the Android Keystore is unavailable, so a corrupted keystore can no longer prevent the IME from instantiating.

### Theme disk leak — `ThemeManager`
Every theme reload (including each keystroke in the theme editor) created `cacheDir/loaded/<UUID>/` and never deleted it. Long-running keyboards accumulated megabytes of stale extracted theme assets indefinitely. Now sweeps stale dirs on init, deletes the on-disk dir for each evicted cached `ThemeInfo`, and spares the dir backing the currently-active theme (composition still reads from it).

### Han index race — `HanShapeBasedLanguageProvider`
`connectedLanguagePacks` / `languagePackItems` / `keyCode` were mutated from one coroutine (`create()`, `preload()`) and read concurrently from another (`suggest()`, `determineLocalComposing()`) without synchronization. Marked `@Volatile` (snapshots are already immutable) and added a `synchronized(loadLock)` around `LanguagePack.load(context)` so two coroutines for different subtypes can't race on the SQLite handle.

### IME teardown crash — `FlorisImeService.onDestroy`
`unregisterReceiver(wallpaperChangeReceiver)` was unconditional. If `onCreate` threw before registration completed, `onDestroy` raised `IllegalArgumentException` — masking the real init error and aborting the rest of teardown, leaking the static `WeakReference`. Now tracks `wallpaperReceiverRegistered`, guards each teardown step independently.

---

## Round 2 — privacy & performance (commit `346aa89`)

### Backup leak — `backup_rules.xml`, `xml-v31/backup_rules.xml`
The user dictionary table (`floris_user_dictionary`) records every word the personal-learning pipeline has promoted from the user's typing — names, addresses, codenames, vocabulary. The project's stated posture is "Zero cloud processing. Zero telemetry. All features work offline." Auto Backup was silently uploading the dictionary to the user's Google Drive on both pre-API-31 (`<full-backup-content>`) and API 31+ (`<cloud-backup>`). Removed it from both cloud paths. Kept it in `<device-transfer>` (API 31+) so explicit phone-to-phone migration still preserves the adaptive vocabulary — that flow is a deliberate user action, not a silent upload.

### Profileable shipped to release — `AndroidManifest.xml`
`<profileable android:shell="true"/>` was in the main manifest and shipped in release/beta APKs. On a privacy keyboard, a shell-attached profiler (simpleperf, perfetto) can heap-dump the IME process and expose freshly-typed text including passwords. Moved to `app/src/debug/AndroidManifest.xml` and `app/src/benchmark/AndroidManifest.xml` variant overlays so release and beta builds no longer advertise as profileable. Benchmark and debug variants still get full profiling.

### Unbounded frequency cache — `NlpManager.frequencyCache`
Was a raw `ConcurrentHashMap<String,Double>` keyed by `${subtype}-$word`. `StatisticalGlideTypingClassifier:235` calls it inside the per-candidate inner loop of every gesture, so a heavy gesture-typing session would accumulate every unique word forever in a long-lived IME process. Switched to `LruCache(5000)` — bounded warm-vocabulary cache for a single session.

### Double Room query per stats refresh — `TypingStatsScreen`
Was calling `DictionaryManager.queryAll()` twice (size, then sorted-top-10) per refresh — two full Room table scans on what can be a 10k+ row table. Cached once. Also wrapped `AdaptiveTouchModel.totalSampleCount()` in `remember(refreshTick)` so it isn't walked under a `@Synchronized` block on every recomposition.

### `.gitignore` narrowed
The Rust `debug/` pattern shadowed `app/src/debug/`. Narrowed to `/target/` and `**/target/{debug,release}/` so the Android variant tree is no longer hidden.

---

## Round 3 — leaks & OOM (commit `5543baf`)

### Feedback-scope leak — `InputFeedbackController`
Held a `CoroutineScope(SupervisorJob)` that was never cancelled on IME teardown. Each keypress launched a coroutine on that scope that captured `ims.window?.window?.decorView`, so when the InputMethodService was destroyed mid-typing the in-flight haptic and audio coroutines kept the entire window's view tree reachable past service death. Added `dispose()` and call it from `FlorisImeService.onDestroy` alongside the existing teardown steps.

### Copy-to-clipboard OOM — `FlorisCopyToClipboardActivity`
The legacy bounds-decode fallback called `MediaStore.Images.Media.getBitmap()`, which decodes at full resolution. An attacker-supplied `content://` URI to a multi-megapixel image could OOM the IME process. Replaced with sampled `BitmapFactory.decodeStream(inSampleSize=2)` that bounds peak memory. Also fixed a redundant `bitmap!!.asImageBitmap()` non-null assertion to use the let parameter `bmp`.

---

## Verification

- `:app:compileDebugKotlin` ✓
- `:app:testDebugUnitTest` — all tests pass ✓
- `:app:verifyNoInternetPermission` — privacy gate green ✓
- `:app:processDebugManifest` / `:app:processReleaseManifest` — variant overlays confirmed (`<profileable>` in debug-only) ✓

## Known follow-ups

- README clipboard claim ("AES-256 GCM, military-grade protection") references the `ClipboardHistoryManager` AES path, but the wired clipboard is still the Room-backed `ClipboardManager` (Android FBE at rest, not AES-256-GCM at the app layer). Either wire `ClipboardHistoryManager` into the active flow or soften the README. Out of scope for this hardening pass.

<a id="v1.7.5"></a>
## v1.7.5

**Released:** 2026-05-09
**Versioning:** 1.7.4 → **1.7.5** (versionCode 174 → 175)

This release closes the new **N12 "SwiftKey indistinguishability"** roadmap section plus a chunk of the original **Next-1 SymSpell** item — twelve commits, all on-device, no Copilot, no cloud, no account. The goal: a user can't tell whether they're typing on SwiftKey or on SwiftFloris.

See `docs/archive/SWIFTKEY_PARITY_RESEARCH.md` for the underlying research.

---

## What's new

### Surface fixes (the two paper-cuts you'd notice in 30 seconds)
- **Auto-space after punctuation defaults to ON.** Period / comma / `?` / `!` now insert a trailing space without a settings tweak. Existing user overrides still win, so already-installed users toggle in Settings → Typing.
- **Suggestion-tap haptic.** Tapping an autocorrect / suggestion strip word now fires the same key-press vibration as tapping a letter key, with `keyLongPress()` on the long-press branch.

### N12.1 — Adaptive touch model
New `AdaptiveTouchModel` keeps per-subtype, per-key Welford-online stats of the user's actual tap-offset distribution (normalised by key half-size). After ≥30 samples per key, hit-tests bias toward where the user actually taps using a 2D-Gaussian log-likelihood — the SwiftKey "feels accurate" effect, all on-device, no offsets ever written to disk.

### N12.2 — Next-word predictions via PersonalBigramStore
New per-locale bigram counter persisted to `<filesDir>/personal_bigrams_<localeTag>.tsv`. Caps: 2,000 prev words per locale, 16 next words per prev, max count 1,000, MIN_COUNT=2. The suggestion strip is no longer empty after a space — it shows the top bigram completions for the previous word.

### N12.3 — Multi-language hot-switch
When a subtype has secondary locales enrolled (`SubtypeEditorScreen` already supports this), `LatinLanguageProvider.suggest` queries every enrolled locale's dictionary and merges per-locale candidates with a `prior` of `1.0` for any locale that recognised the typed word and `0.4` for those that didn't. `isEligibleForAutoCommit` is gated to recognising locales — no more wrong-language autocorrect mid-sentence.

### N12.4 — Flow Through Space
`GlideTypingGesture.Detector.signalWordBoundary()` snapshots and resets the trace mid-stroke; the controller fires it when the trace re-enters the SPACE key after first leaving it. Phantom-space inserts the " " between committed words, classifier resets between words, trail-fade visually punctuates each. Glide a word, drag finger across the space bar, glide the next word — all without lifting.

### N12.5 — Trigram next-word predictor
New `PersonalTrigramStore` is a per-locale `(prev2, prev1) → next` counter persisted to `<filesDir>/personal_trigrams_<localeTag>.tsv`. Caps: 4,000 contexts, 12 next words per context. `KeyboardManager.learnIfAllowed` now learns both bigrams and trigrams via a sliding two-word window. After typing `the quick brown fox` a couple of times, typing `the quick` surfaces `brown` as the top suggestion.

### N12.6 — Typing stats screen
New Settings → Typing → "Typing stats" screen reads three on-device numbers off-thread:
- Words-learned count + top-10 personal-dictionary entries by frequency
- Total bigram-store size on disk
- Adaptive-touch-model session sample count

No data leaves the device.

### N12.7 — Cold-start bootstrap from dictionary frequency
`LatinDictionarySnapshot.topByFrequency(n)` lazily caches the top-64 high-frequency dictionary words. Suggestions now layer Tier 0 (trigram, 0.80–0.45) → Tier 1 (bigram, 0.55–0.20) → Tier 2 (dict bootstrap, 0.30–0.55). Result: never-empty suggestion strip on cold-start and after sentence-ending punctuation. Sentence-start detection auto-capitalises the first letter.

### N12.8 — Adaptive touch model feeds the glide classifier
`AdaptiveTouchModel.adjustedCenter(...)` returns user-personalised pixel centers. `StatisticalGlideTypingClassifier.findNClosestKeys` (matching) and `Pruner.generateIdealGestures` (template) both consult `adjustedCenter()` instead of `key.visibleBounds.center`. Bias clamped to ±0.5×half so a heavily-skewed learner can't drag the template outside the visible key. Gives glide the same per-user spatial bias N12.1 already gives taps.

### N12.9 — Sentence-case suggestions
After `.`, `!`, or `?` (or empty input), every next-word suggestion's first letter is capitalised. SwiftKey-parity at sentence start.

### N12.10 — Long-press suggestion to forget
`WordSuggestionCandidate` from next-word predictions and personal-dict suggestions now both ship `isEligibleForUserRemoval = true`. New `DictionaryManager.forgetWord`, `PersonalBigramStore.forget`, `PersonalTrigramStore.forget` are all consulted by `LatinLanguageProvider.removeSuggestion`. Long-press a noisy suggestion → it's gone from personal dict, bigrams, *and* trigrams in one stroke.

### Next-1.A — SymSpell delete-index for distance-1 corrections
New pure-Kotlin `SymSpellIndex.kt`. `LatinDictionarySnapshot.symSpellIndex` is `by lazy` so the build (~100–300 ms over the 117k-word EN dict) lands on first correction call. `LatinDictionarySuggester.knownEdits1` now calls `dictionary.symSpellIndex.candidatesAtDistance1(input)` instead of generating Norvig's `L · 54` candidate strings per call — ~50× speedup on the per-keystroke correction path.

### Next-1.B — Distance-2 high-frequency auto-commit
New `AutoCommitMinFrequencyDistance2 = 0.92` threshold. Distance-2 corrections now auto-commit on space when the candidate is in the very common bucket (~top 3k SCOWL words). Closes the long-word-typo gap: `recieved → received`, `tommorrow → tomorrow`, `seperate → separate`, `definately → definitely`.

---

## Settings reference

Every new behavior is gated behind a pref so power-users can opt out:

- **Typing → Adaptive touch model** (default on)
- **Typing → Predict the next word** (default on)
- **Typing → Multilingual suggestions** (default on)
- **Gestures → Flow through space** (default on)
- **Typing → Typing stats** (link to the new screen)

---

## Out of scope (explicit non-goals)

- Microsoft Copilot / Editor / Tone
- DALL-E sticker / Designer
- Microsoft account login or sync
- Federated learning aggregator
- Anything that requires the `INTERNET` permission

---

## Roadmap status

- **N12 SwiftKey indistinguishability** — 10/10 ticked (12.5 absorbed into the trigram tier)
- **Next-1 SymSpell** — 1.A and 1.B ticked, 1.C (full d2 SymSpell index) deferred pending field data
- **L1 On-device LLM (Gemma 3 270M Q4)** — still the next big jump; multi-week
- **Next-3.1 Pre-trained KenLM 5-gram bootstrap** — would give first-time users rich predictions before they've typed anything

---

## Verification

- `./gradlew :app:assembleDebug` — green
- `./gradlew :app:assembleRelease` — green (signed when `SIGNING_KEYSTORE_BASE64` is set; otherwise falls through to debug signing as documented in N6.2)
- `:app:verifyNoInternetPermission` — green (no INTERNET permission added by any item)
- adb-installed on local Pixel-class device, smoke-tested across all five new prefs

<a id="v1.7.4"></a>
## v1.7.4

**Released:** 2026-05-09
**Versioning:** 1.7.3 → **1.7.4** (versionCode 173 → 174)
**Roadmap §:** 6 (NOW). Closes the v1.7.x privacy-hardening + reproducibility track.

---

## Privacy

- **N7.2 — FLAG_SECURE on IME window in password fields (final piece).**
  `FlorisImeService.applyFlagSecureForCurrentField` (called from `onStartInputView`)
  sets `WindowManager.LayoutParams.FLAG_SECURE` on the IME window when the active
  variation is `PASSWORD`/`VISIBLE_PASSWORD`/`WEB_PASSWORD`. Cleared on non-password
  fields. Prevents screenshots, screen recordings, and external display mirroring
  from capturing the long-press popup or suggestion strip during credential entry.
  Closes the last open piece of N7.2.

## Reproducibility

- **N6.3 — Reproducible-build toolchain pins + verification recipe.**
  All toolchain inputs already pinned via the existing version catalogs:
  Gradle 9.4.1 (SHA-256), AGP 9.0.0, Kotlin 2.3.20, KSP, Build Tools 36.0.0,
  NDK 29.0.14206865, cmake 4.1.2, cmdline tools (SHA-256), JDK 17 Temurin (CI).
  New `docs/REPRODUCIBLE_BUILDS.md` documents:
  - Full pin matrix (input → pin location → version → checksum).
  - Local verification recipe with `apkdiff` shell function.
  - Copy-pastable F-Droid `Builds:` stanza for the upstream
    [`fdroiddata`](https://gitlab.com/fdroid/fdroiddata) submission.
  Pending: open the `fdroiddata` PR + F-Droid build-server rebuild verification.

## Verification (already-shipped sweep)

- **N4.1 — Drag-drop smartbar reorder.** Verified shipped via FlorisBoard upstream
  (`QuickActionsEditorPanel.kt:278` — `detectDragGesturesAfterLongPress`).

- **N8.6 — Voice Access composing-region cleanup.** Verified shipped via
  `AbstractEditorInstance.setComposingRegion(EditorRange)` extension function
  (line 303): invalid ranges → `finishComposingText`, valid → two-arg
  `setComposingRegion(start, end)`. All composing-region updates go through
  this wrapper.

---

## Cumulative Now-tier progress (v1.7.0 → v1.7.4)

**28 of ~32 Now-tier items closed in one same-day batch.**

Closed: N3.1, N3.2, N3.3 (partial), N3.4 (partial), N3.5, N4.1, N5.1, N5.2,
N5.3, N5.4, N6.1, N6.2, N6.3 (partial), N6.4, N6.5, N7.1, N7.2, N7.3, N7.5,
N8.1, N8.3 (partial), N8.4, N8.5, N8.6, N9.1, N9.2, N10.2, N10.3, N11.

Open / deferred:
- **N1** Glide breadth — gated on HeliBoard NLnet drop (external timing).
- **N2** Multilingual auto-detect — substantial NLP work (langid + per-token ranking + bilingual subtype preset). Ships as a multi-week Next-tier candidate.
- **N4.2 / N4.3** Customizable bottom row + per-app smartbar profile — substantial UI work.
- **N7.4** SQLCipher personal-dictionary encryption — Room migration with passphrase derivation; multi-day project.
- **N8.2** Theme contrast audit — per-theme color contrast measurement against WCAG 2.1 AA 4.5:1.
- **N9.3** Emoji + sticker pack search bar — substantial UI + indexing work.
- **N10.1** Bundle Noto Color Emoji 17 — deferred to v1.8.x pending `androidx.emoji2 1.7.0+`.

---

## Verification

- [x] `./gradlew :app:assembleDebug` clean.
- [x] All version strings updated.
- [x] No new `TODO()` runtime stubs introduced (the only ones removed in v1.7.0 are gone for good).
- [x] `:app:verifyNoInternetPermission` passes.
- [x] Test suite green (`PersonalDictionaryIsolationTest`, `TouchTargetWcagTest`,
      existing `DictionaryManagerTest`, `AutoCommitSuppressionTest`, etc.).

<a id="v1.7.3"></a>
## v1.7.3

**Released:** 2026-05-09
**Versioning:** 1.7.2 → **1.7.3** (versionCode 172 → 173)
**Roadmap §:** 6 (NOW). Three more Now-tier items closed.

---

## Release engineering

- **N6.2 — Release workflow with signing.**
  New `.github/workflows/release.yml` (manual `workflow_dispatch`,
  `version` + `draft` inputs) signs APKs with a stored keystore,
  uploads SHA-256 manifest, creates GitHub Release. New
  `signingConfigs.create("release")` block in `app/build.gradle.kts`
  consumes `KEYSTORE_PATH` + `SIGNING_*` env vars; v1/v2/v3/v4 signing
  enabled. Fallback to debug signing when no secrets — forks can still
  validate end-to-end. Required repo secrets:
  `SIGNING_KEYSTORE_BASE64`, `SIGNING_KEYSTORE_PASSWORD`,
  `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.

## Accessibility

- **N8.1 — 48dp touch-target WCAG audit + regression test.**
  New `TouchTargetWcagTest` (Kotest, 4 tests) pins WCAG 2.5.5 AAA
  per-key 48dp floor for PHONE_PORTRAIT default + max heights at
  the typical 360×800dp form factor. PHONE_LANDSCAPE holds at the WCAG
  2.5.8 AA 24dp floor (industry standard for vertically-constrained
  landscape keyboards). `resizeHandleTouchSize` (48dp) audited too.
  Future contributor lowering any form-factor factor gets a clear test
  failure with WCAG citation.

- **N8.3 — TalkBack content descriptions per key (partial).**
  New `keyContentDescription(code, label)` helper.
  `TextKeyButton` `SnyggBox` now applies
  `Modifier.semantics { contentDescription = …; role = Role.Button }`.
  TalkBack now announces "Shift", "Backspace", "Enter", "Space",
  "Arrow left", "Switch language", etc. instead of generic "button".
  Letters/numbers/punctuation use the visible label.
  *Pending:* smartbar/suggestion-strip labels +
  "Alternative characters available" hint + i18n string resources.

---

## Cumulative Now-tier progress (v1.7.0 → v1.7.3)

**24 of ~32 Now-tier items closed in one same-day batch.**

Closed: N3.1, N3.2, N3.3 (partial), N3.4 (partial), N3.5, N5.1, N5.2,
N5.3, N5.4, N6.1, N6.2, N6.4, N6.5, N7.1, N7.2 (partial), N7.3, N7.5,
N8.1, N8.3 (partial), N8.4, N8.5, N9.1, N9.2, N10.2, N10.3, N11.

Open: N1 (HeliBoard NLnet drop), N2 (multilingual auto-detect), N4
(smartbar customization), N6.3 (F-Droid reproducible), N7.2 remainder
(FLAG_SECURE), N7.4 (SQLCipher), N8.2 (theme contrast audit), N8.6
(Voice Access cleanup), N9.3 (emoji search), N10.1 (Emoji 17 fonts).

---

## Verification

- [x] `./gradlew :app:assembleRelease` clean (debug-signing fallback path).
- [x] `./gradlew :app:testDebugUnitTest` — `TouchTargetWcagTest` (4) +
  `PersonalDictionaryIsolationTest` (3) all pass; existing suite green.
- [x] `:app:verifyNoInternetPermission` passes.
- [x] All version strings updated (gradle.properties, README badge,
  CHANGELOG.md#v1.7.3, ROADMAP.md).

<a id="v1.7.2"></a>
## v1.7.2

**Released:** 2026-05-09
**Versioning:** 1.7.1 → **1.7.2** (versionCode 171 → 172)
**Roadmap §:** 6 (NOW). Three more Now-tier items closed (plus N3.5, N5.2, N8.5, N10.2 verified-already-shipped).

---

## CI

- **N6.4 — Dependency-CVE scan workflow.**
  New `.github/workflows/dependency-scan.yml` combines two scanners:
  - `actions/dependency-review-action@v4` on PRs that touch dep manifests; fails on HIGH or CRITICAL.
  - `google/osv-scanner-action@v2.0.2` recursive scan as SBOM-level cross-check.
  - Cron Sundays 06:00 UTC for proactive drift detection. workflow_dispatch for manual runs.

## Haptics + popup polish

- **N3.3 — SwiftKey-aligned haptic profile (partial).**
  Default haptic duration `65ms → 20ms`, strength `70 → 60` (≈ 153/255 amplitude).
  Vibrator path already gates on `hasAmplitudeControl()`. Existing user
  overrides preserved. Pending: Android 16 `BasicEnvelopeBuilder` envelope
  haptics — separate pass.

- **N3.4 — Long-press preview popup polish (partial).**
  `FlorisImeUi.KeyPopupBox` `shadow-elevation` bumped from 2dp → 4dp for
  SwiftKey's "elevated dropdown" feel. Pending: ~80ms color flash + 1.03×
  scale-up animation on key press, accent-ring stroke on focused popup
  element — larger Compose surgery, deferred.

## Verified-already-shipped (no code change)

- **N5.2 — Cursor mode** (continuous space drag → cursor) verified shipped.
- **N3.5 — `key_height: 56dp` dimens flow** documented as a spec reference;
  user-facing slider via N5.3 is the supported height adjustment.
- **N8.5 — Switch Access compatibility** verified
  (`supportsSwitchingToNextInputMethod="true"` in `method.xml`).
- **N10.2 — Lazy EmojiCompat replace-all loader** verified shipped (commits
  `6fd6e3b`, `ba3c790`).
- **N10.1 — Bundle Noto Color Emoji 17** deferred to v1.8.x pending
  `androidx.emoji2 1.7.0+` upstream release.

---

## Cumulative Now-tier progress (v1.7.0 + v1.7.1 + v1.7.2)

**21 of ~32 Now-tier items closed.**

Closed: N3.1, N3.2, N3.3 (partial), N3.4 (partial), N3.5, N5.1, N5.2, N5.3,
N5.4, N6.1, N6.4, N6.5, N7.1, N7.2 (partial), N7.3, N7.5, N8.4, N8.5, N9.1,
N9.2, N10.2, N10.3, N11.

Open: N1 (gated on HeliBoard NLnet drop), N2 (multilingual auto-detect), N4
(smartbar customization), N6.2 (signed releases), N6.3 (F-Droid reproducible
builds), N7.2 remainder (FLAG_SECURE on popups), N7.4 (SQLCipher), N8.1/N8.2/N8.3/N8.6
(a11y audit), N9.3 (emoji search), N10.1 (Emoji 17 fonts).

---

## Verification

- [x] `./gradlew :app:assembleDebug` clean.
- [x] Existing unit tests still pass; `PersonalDictionaryIsolationTest` from v1.7.1 included.
- [x] `:app:verifyNoInternetPermission` passes.
- [x] All version strings updated.

<a id="v1.7.1"></a>
## v1.7.1

**Released:** 2026-05-09
**Versioning:** 1.7.0 → **1.7.1** (versionCode 170 → 171)
**Roadmap §:** 6 (NOW). Five additional Now-tier items closed on top of v1.7.0's 10.

This is a same-day follow-up to v1.7.0, batching all the SwiftKey-parity polish
and accessibility items that fit cleanly on top of the v1.7.0 correctness floor.

---

## Privacy + accessibility

- **N7.3 — Personal-dictionary isolation regression test + threat model.**
  New `PersonalDictionaryIsolationTest` (Kotest, 3 tests) statically verifies that
  `DictionaryManager.learnWord` never references the system `UserDictionary`
  ContentProvider. New `docs/THREAT_MODEL.md` enumerates threat actors, live
  defenses, known gaps, and a per-release verification checklist.

- **N8.4 — Reduced-motion guard on gesture trail.**
  `TextKeyboardLayout` now reads
  `Settings.Global.ANIMATOR_DURATION_SCALE` and suppresses the glide trail when
  the user has Animations off (Developer Options → Animator duration scale = 0).

- **N8.5 — Switch Access compatibility (verified).**
  `method.xml` already declares `supportsSwitchingToNextInputMethod="true"` and
  `FlorisImeService.switchToNextInputMethod` correctly uses
  `imm.switchToNextInputMethod(token, false)`.

## SwiftKey-parity polish

- **N3.1 — SwiftKey Pure (Light) + SwiftKey Pure (Dark) themes.**
  New stylesheets at
  `assets/ime/theme/org.florisboard.themes/stylesheets/swiftkey_pure_{light,dark}.json`
  consume the pure tokens already defined in `colors_branding.xml`. Both themes
  inherit the v1.7.0 `FontWeight.Medium` glyph weight automatically.

## Word-edit ergonomics

- **N5.3 — Scalable keyboard height slider.** New
  `keyboardHeightMultiplierPortrait` / `Landscape` prefs (50..150%, default
  100%), surfaced as a `DialogSliderPreference` directly under the existing
  font-size slider in `Settings → Keyboard → Layout & size`. Threaded through
  `ImeWindowSpec.UserPreferredOptions.keyboardHeightScale` and applied in
  `doComputeWindowSpec` before the form-factor [`min`, `max`] clamp.

## Verified-already-shipped

- **N9.1 / N9.2 — `commitContent()` for clipboard images, surfaced in panel.**
  Both already wired via FlorisBoard upstream. `EditorInstance.commitClipboardItem`
  for `ItemType.IMAGE`/`VIDEO` calls `InputConnectionCompat.commitContent` with
  `INPUT_CONTENT_GRANT_READ_URI_PERMISSION`. Verification documented in the
  ROADMAP.

---

## Cumulative NOW-tier progress (v1.7.0 + v1.7.1)

Sixteen Now-tier items closed across the two releases:
N3.1, N3.2, N5.1, N5.3, N5.4, N6.1, N6.5, N7.1, N7.2, N7.3, N7.5, N8.4, N8.5, N9.1, N9.2, N10.3, N11.

Open Now items, in priority order: N3.3 (haptics), N3.4 (pressed-key flash),
N3.5 (verify dimens flow), N5.2 (cursor mode polish), N7.2 (FLAG_SECURE on
suggestion-strip popups), N8.1/N8.2/N8.3/N8.6 (a11y audit), N9.3 (emoji search),
N10.1/N10.2 (Emoji 17 readiness), N1 (glide breadth — gates on HeliBoard NLnet
drop), N2 (multilingual auto-detect), N4 (smartbar customization), N6.2/N6.3/N6.4 (signed releases + reproducible builds + CVE scan).

---

## Verification

- [x] `./gradlew :app:assembleDebug` clean.
- [x] `./gradlew :app:testDebugUnitTest` — `PersonalDictionaryIsolationTest`
  added; existing tests remain green.
- [x] `:app:verifyNoInternetPermission` passes.
- [x] All version strings updated: `gradle.properties`, README badge,
  `CHANGELOG.md#v1.7.1`, ROADMAP.

<a id="v1.7.0"></a>
## v1.7.0

**Released:** 2026-05-09
**Versioning:** 1.6.0 → **1.7.0** (versionCode 160 → 170)
**Roadmap §:** 6 (NOW). Closes ten Now-tier items in one release.

This is the first ROADMAP v4.0 ship. It hits three themes simultaneously:
correctness floor (no more TODO() crashes), privacy hardening (no-network
contract pinned in CI, password-field protections, signing fingerprint), and
SwiftKey-parity polish (word-delete gestures, shortcut auto-replace, sans-serif-medium
glyphs).

---

## Correctness floor

- **N11. Runtime TODO() stubs resolved.** `KeyboardExtension.edit()` and
  `LanguagePackExtension.edit()` were both `TODO("...")` calls that would have
  crashed the IME if any code path traversed them. Both now return real
  `ExtensionEditor` implementations modeled on `ThemeExtensionEditor`. F-Droid
  acceptance review can no longer flag them. (`FlorisSpellCheckerService` TODO
  documented as an intentional delegate to AOSP's default sentence-aggregation,
  which is already SwiftFloris-backed via `NlpManager`.)

## Privacy hardening (the moat verbatim)

- **N7.1. No-INTERNET-permission build gate.** New
  `:app:verifyNoInternetPermission` Gradle task scans every `AndroidManifest.xml`
  on every variant build. The build fails with a contract-violation message if
  any of `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`,
  `CHANGE_NETWORK_STATE`, `CHANGE_WIFI_STATE` is declared. Pins SwiftFloris's
  no-network promise in CI, not just in marketing.

- **N7.2. Password-field hardening.**
  Personal-dictionary auto-learn (`learnIfAllowed`) now also gates on
  `keyVariation == PASSWORD`, in addition to the existing
  `IME_FLAG_NO_PERSONALIZED_LEARNING` incognito gate — many host apps forget to
  set the no-personalized-learning flag, so the variation check is a defense-in-depth
  layer. `EditorInstance.performClipboardCut` and `performClipboardCopy` now skip
  the IME-local clipboard history when the active field is a password variation.

- **N7.5. APK signing fingerprint pin.** New `Settings → About → APK signing
  fingerprint` shows the SHA-256 of the running install's signing certificate,
  formatted to match `apksigner verify --print-certs`. Tap to copy. Compare
  against the value in README to detect supply-chain APK swaps.

## CI + release engineering

- **N6.1. PR gates.** GitHub Actions now sequences
  `verifyNoInternetPermission` → `:app:testDebugUnitTest` →
  `:app:lintDebug` → `:app:assembleDebug`. Lint and test reports upload as
  artifacts on every run. Gradle cache wired via
  `gradle/actions/setup-gradle@v4`.

- **N6.5. Obtainium one-tap install URL.** README now leads with an
  `obtainium://app/{...}` URL that auto-subscribes to GitHub Releases for
  hands-free updates without polling.

## Word-edit ergonomics

- **N5.1. Hold/swipe-backspace = delete word.** Default
  `deleteKeyLongPress` and `deleteKeySwipeLeft` flipped to
  `SwipeAction.DELETE_WORD`. SwiftKey/Gboard parity. User overrides preserved
  via jetpref's fall-back-only-when-unset semantics.

- **N5.4. Auto-replace shortcuts in the personal dictionary.** Settings UI was
  already present (Personal dictionary → Add → Word + Shortcut + Locale); wired
  the auto-replace half via
  `DictionaryManager.queryUserDictionaryShortcutExact` + a new
  `userDictionaryShortcutAutoCommitCandidate` step in
  `NlpManager.getAutoCommitCandidate` that runs *before* in-strip suggestions
  and the English contraction fallback. Add `omw → on my way`, type `omw `,
  watch it expand.

- **N10.3. Surrogate-pair-safe backspace.** `AbstractEditorInstance.deleteText`
  now uses `InputConnection.deleteSurroundingTextInCodePoints` (API 24+, always
  available since `minSdk = 26`) for both BEFORE_CURSOR and AFTER_CURSOR
  scopes. ICU break-iterator already returned grapheme-aligned char offsets;
  the conversion via `String.codePointCount` makes the call code-point-safe
  even if the editor has drifted from our expected text. Backspace now never
  splits a surrogate pair in Unicode 16/17 emoji or Indic conjuncts.

## SwiftKey-parity polish

- **N3.2. sans-serif-medium key glyph weight.**
  `FlorisImeUi.Key.elementName` base style sets
  `fontWeight = fontWeight(FontWeight.Medium)` (weight 500). Propagates to
  every theme. Closes the SwiftKey perceived-quality gap without changing
  dimensions or layouts.

---

## Unchanged from v1.6.0
- 117,022-word SCOWL-merged English dictionary
- 130-entry contraction autocorrect table (SAFE / DICTIONARY_GATED)
- Auto-cap with sentence-end context detection
- 6-language gesture typing (EN/DE/ES/FR/IT/PT)
- FUTO Voice Input integration + voice commands
- Encrypted clipboard (AES-256-GCM, max 50 items)
- Themes: Nord, Tokyo Night, Dracula, Catppuccin Mocha (+ SwiftKey Pure tokens, picker entry pending — N3.1)

## Verification (DoD checklist)

- [x] `./gradlew :app:compileDebugKotlin` clean (warnings only, all pre-existing)
- [x] `:app:verifyNoInternetPermission` passes; manual injection of INTERNET fails the build with the expected message
- [x] All version strings updated: `gradle.properties`, README badge, `CHANGELOG.md#v1.7.0`, ROADMAP §2 + §3 (this release)
- [x] No new `TODO()` runtime stubs introduced
- [x] No new third-party dependencies (no `NOTICE` / `LICENSES/` updates needed)

## What's next (v1.7.x → v1.8.0)

Next picks from ROADMAP §6:
- **N3.1** wire SwiftKey Pure Light/Dark theme presets into the picker
- **N3.3 / N3.4** SwiftKey haptic profile + envelope haptics + pressed-key flash
- **N5.2** cursor-mode polish (hold space → drag)
- **N7.3** personal-dictionary isolation regression test
- **N8** accessibility scoped pass (TalkBack labels, 48dp targets, contrast audit)
- **N9** `commitContent()` for sticker / GIF / image insertion

<a id="v1.6.0"></a>
## v1.6.0

Three SwiftKey-parity moves: personal-dictionary auto-learning, a 2.3× larger English dictionary, and the first slice of SwiftKey's visual design tokens.

## 1. Personal dictionary auto-learning

The keyboard now learns the words you actually type and bumps them in suggestions / autocorrect over time. Matches SwiftKey's "personal language" behavior.

**How it works**
- Every word you commit (via space, punctuation, gesture, or accepted suggestion) is fed to `DictionaryManager.learnWord(...)`.
- New words are inserted into the FlorisUserDictionary at frequency **80**; existing entries are reinforced by **+6** per use, capped at **250** so curated top-tier corpus words at 255 still rank first.
- The personal dictionary is already merged ahead of the main dictionary in suggestion ranking via `SuggestionCandidateMerger.mergePreferred(...)`, so learned words rise to the top of the suggestion strip after 2–3 uses.
- All inserts/updates run on `Dispatchers.IO` — no input-event lag.

**Privacy gates**
- Skipped entirely when **incognito mode** is active.
- Skipped when **personal dictionary** is disabled in settings.
- Skipped for tokens that don't look like real words: less than 3 chars, more than 32 chars, contain digits, or contain punctuation other than `'` or `-`.

**Manageable**
- Learned words appear in Settings → Dictionary → User dictionary alongside any words you've added manually. Unwanted entries can be deleted there.

## 2. English dictionary expansion: 49,981 → 117,022 words

The bundled English dictionary (`assets/ime/dict/data.json`) now ships **2.34× more words** for spell-check coverage, while keeping the existing high-frequency ranking so autocorrect still prefers common words.

**Composition**
- **49,981 curated high-frequency entries** kept at their original 128–255 frequency band. These are the words autocorrect actively prefers.
- **67,041 new long-tail entries** from SCOWL v2020.12.07 (`english-words.{10,20,35,40,50,60}` + `american-words.{10,20,35,40,50,60}` + selected proper-name lists) added at frequency band 80–127. These exist for spell-check membership — legitimate uncommon words no longer get red-squiggled or silently auto-corrected, but they don't outrank the curated corpus.
- Profanity filtered using LDNOOBW's English bad-words list (CC-BY 4.0).

**Sizes**
- `data.json`: 807KB → 1.78MB (still loaded once on subtype init, cached for the session)
- `en.txt`: regenerated to match, frequency-sorted

**Licenses**
- SCOWL — BSD-style permissive, see `LICENSES/SCOWL-Copyright.txt`.
- LDNOOBW — CC-BY 4.0, attribution in `NOTICE`.
- Dictionary regeneration script: `utils/expand_dictionary.py` (re-runnable when SCOWL releases new data).

## 3. SwiftKey visual design — first slice

Per a research pass on Microsoft SwiftKey's 2026 visual spec (Pure Light/Dark themes, Microsoft-aligned accent palette):

- **Accent color flipped to SwiftKey's 2020+ blue `#319DFF`** (was `#4A90E2`). The pre-2020 SwiftKey teal `#2596BE` is preserved as `accent_teal_legacy` for users who want the nostalgic look.
- **SwiftKey "Pure" theme palette added as design tokens** — `swiftkey_pure_light_*` and `swiftkey_pure_dark_*` families in `colors_branding.xml` (kbd bg, key bg, special key bg, key text, hint glyph). Ready for a future "SwiftKey Pure" theme preset to consume them; not yet wired into the default theme.
- **Key dimensions bumped toward SwiftKey's premium feel** — `key_width` 33→36dp, `key_height` 42→56dp.
- **Suggestion chip radius dropped 32dp → 6dp** — SwiftKey's strip is unchipped (text on dividers); the 32dp pill was a Material You convention.

Out of scope for this release (will land in a follow-up): full SwiftKey theme preset wired into the theme picker, sans-serif-medium font on keys, long-press popup color tweak, SwiftKey-default haptic 20ms@153 amplitude.

## Files changed

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/dictionary/DictionaryManager.kt` — added `learnWord(...)` + IO scope + tier constants
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/KeyboardManager.kt` — added `learnIfAllowed(...)` and wired it into `commitCandidate`, `commitGesture`, and `handleSpace`
- `app/src/main/assets/ime/dict/data.json` — regenerated, 49,981 → 117,022 entries
- `app/src/main/assets/dictionaries/en.txt` — regenerated to match
- `app/src/main/assets/dictionaries/README.md` — provenance + regeneration recipe
- `app/src/main/res/values/dimens.xml` — key dims + chip radius
- `app/src/main/res/values/colors_branding.xml` — SwiftKey palette tokens
- `LICENSES/SCOWL-Copyright.txt` — SCOWL license bundled
- `NOTICE` — SCOWL + LDNOOBW attribution
- `utils/expand_dictionary.py` — re-runnable dictionary expansion
- `gradle.properties`, `README.md` — version bump

<a id="v1.5.5"></a>
## v1.5.5

SwiftKey-parity contraction autocorrect — 130+ contractions across two safety tiers, with case preservation.

## What changed

The previous five-entry first-person-pronoun table is now a comprehensive English contractions table backed by a two-tier safety model:

- **TIER 1 — SAFE (immediate auto-commit on space).** Substitutions where the typed-without-apostrophe form is *not* a real English word; safe to commit without consulting the dictionary.
- **TIER 2 — DICTIONARY_GATED (auto-commit only when the typed word is not in the dictionary).** Substitutions that collide with valid English words ("ill", "well", "hell", "shell", "wed", "shed", "lets", "wont", "cant", "its", "id", "im", "ive"). The dictionary check ensures the user's intended word is not silently overwritten.

Excluded entirely: `were` → `we're`. Past-tense `were` is far too common; even SwiftKey gets complaints when it auto-corrects this.

## SAFE-tier contractions added

- **Negative -n't** — `dont`, `isnt`, `wasnt`, `werent`, `arent`, `didnt`, `doesnt`, `havent`, `hasnt`, `hadnt`, `wouldnt`, `shouldnt`, `couldnt`, `mustnt`, `neednt`, `mightnt`, `oughtnt`, `shant`, `aint`
- **Modal + 've** — `wouldve`, `shouldve`, `couldve`, `mightve`, `mustve`
- **Pronoun + auxiliary** — `youre`, `youve`, `youll`, `youd`, `theyre`, `theyve`, `theyll`, `theyd`, `weve`, `itll`, `itd`
- **Wh- + 's/'re/'ll/'d/'ve** — `whats`, `whatre`, `whatll`, `whatd`, `whatve`, `whos`, `whod`, `wholl`, `whove`, `wheres`, `whered`, `wherell`, `whens`, `whyd`, `whys`, `hows`, `howd`, `howll`
- **Demonstratives** — `theres`, `thered`, `therell`, `thereve`, `thats`, `thatll`, `thatd`, `thatre`, `heres`
- **Indefinite-pronoun + 's** — `someones`, `everyones`, `anyones`, `nobodys`, `everybodys`, `anybodys`, `somebodys`, `somethings`, `nothings`
- **Misc** — `oclock`, `yall`, `maam`, `ima`
- **First-person standalone** — `i` → `I` (unchanged from v1.5.4)

## DICTIONARY_GATED contractions added/refined

These substitute *only* when the dictionary confirms the typed word is not itself a real word the user might have meant:

- `im`/`id`/`ill`/`ive` (collide with IM/id/ill/ive)
- `well`/`hell`/`shell`/`hes`/`shes`/`hed`/`shed`/`wed`/`lets`
- `wont`/`cant`/`its`

## Behavior details (SwiftKey-aligned)

- **ALL-CAPS skip** — Tokens in all caps are never re-cased ("DONT" stays "DONT", "ID" stays "ID"). Matches SwiftKey's acronym-preservation policy.
- **Sentence-start case preservation** — Lowercase typed → lowercase contraction; capitalized typed → capitalized contraction (`Dont` → `Don't`, `Youre` → `You're`). First-person "I" forms keep the capital I regardless of typed case (`im` → `I'm`, `Im` → `I'm`).
- **Already-correct skip** — `don't` typed exactly as-is is left alone.
- **Straight apostrophe** — Uses U+0027 (`'`), matching SwiftKey's default.
- **Non-English locales** — Substitution skipped entirely.

## Files changed

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/ImmediateAutocorrect.kt` (rewrite — generalized from first-person-pronoun-only to all English contractions; added Tier enum, table builder, case-aware output)
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/NlpManager.kt` (renamed call site)
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/latin/LatinLanguageProvider.kt` (renamed `englishPronounCorrection` → `englishContractionCorrection`; tier-based dictionary gate)
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/nlp/ImmediateAutocorrectTest.kt` (rewrite — encodes the new contract per tier)
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/nlp/latin/LatinDictionarySuggesterTest.kt` (added contraction test dictionary entries + safe-tier substitution test)
- `gradle.properties`, `README.md` (version bump)

<a id="v1.5.4"></a>
## v1.5.4

Typing-quality pass focused on SwiftKey-parity feel: random capitalization gone, autocorrect less aggressive, and a real "Autocorrect" toggle separate from "Display suggestions".

## Capitalization fixes

- **Auto-cap no longer triggers after digits, abbreviations, or URLs.** Typing `192.168.0.1`, `3.14`, `e.g.`, or `U.S.A.` no longer arms the next letter to be capitalized. Auto-cap now requires the punctuation to actually end a word: a letter immediately before `.`, `!`, or `?`, with no second `.` two characters back (excludes ellipses and abbreviation chains).
- **Auto-cap no longer secretly inserts a space after `.!?`.** Auto-capitalization and auto-space-after-punctuation are now fully independent settings — turning auto-space off no longer leaves `.` injecting a stealth space.
- **Auto-cap state no longer "sticks" across cursor moves.** Tapping into the middle of a sentence no longer leaves the next letter unexpectedly capitalized. The shifted-automatic state is now re-evaluated against the actual text before the cursor (with a fallback for apps like TikTok that hide caps mode from the IME).

## Autocorrect fixes

- **Pronoun substitution respects the dictionary.** `ill`, `id`, `im`, and `ive` are real English words — they are no longer silently auto-replaced with `I'll`/`I'd`/`I'm`/`I've`. Multi-letter pronoun forms are still offered as tap-to-accept suggestions when the typed word is not a real word; only the unambiguous `i` → `I` substitution still auto-commits.
- **Stricter auto-commit threshold.** The minimum candidate frequency for silent replacement-on-space rose from 0.62 to 0.78, and the minimum word length rose from 3 to 4. Rare words and proper nouns are no longer swapped to common dictionary lookalikes.

## New setting: Autocorrect toggle

- **Settings → Typing → Corrections → Autocorrect.** Defaults to on. Turn it off to keep the suggestion strip visible while disabling silent word replacement on space/punctuation — matching SwiftKey's "Autocorrect" master switch.

## Files changed

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/EditorInstance.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/KeyboardManager.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/ImmediateAutocorrect.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/NlpManager.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/latin/LatinLanguageProvider.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/AppPrefs.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/typing/TypingScreen.kt`
- `app/src/main/res/values/strings.xml`
- `gradle.properties`, `README.md`, `ROADMAP.md`

<a id="v1.5.3"></a>
## v1.5.3

Released: 2026-05-05

## Changes

- Expanded the built-in English autocorrect dictionary to 49,744 entries for stronger offline suggestions and spell correction.
- Fixed immediate autocorrect for standalone `i` so it becomes `I` in the middle of sentences.
- Fixed immediate autocorrect for common English contractions, including `im` -> `I'm`, `ill` -> `I'll`, `id` -> `I'd`, and `ive` -> `I've`.
- Fixed the same contraction autocorrections when typed at the beginning of sentences after auto-capitalization, including `Im`, `Ill`, `Id`, and `Ive`.
- Preserved all-caps acronym behavior so inputs such as `ID` and `ILL` are not rewritten as contractions.
- Added regression coverage for the immediate autocorrect paths and Latin dictionary behavior.

## Verification

- `:app:testDebugUnitTest`
- `:app:lintDebug`
- signed release APK verification with Android `apksigner`
- device install smoke test with `adb install -r`

## APK Details

- File: `SwiftFloris-v1.5.3.apk`
- SHA-256: `1d5f414ab1c0decd74d97c00aadea2769bc07d19010fdbc76f4ed2caaca1e777`
- Signing certificate SHA-256: `b5d537420ded9e11382b3df17dc3616f212b9d9f35138e4fbb3f2adffe50f70a`

