# SwiftFloris Roadmap

Actionable work only. Historical and completed roadmap material is archived in CHANGELOG.md; blocked work is kept in Roadmap_Blocked.md.

## Research-Driven Additions (2026-08-10)

### P1

- [ ] P1 — Replace the addon/MCP network denylist with a permitted-permission allowlist
  Why: the enrolment gate screens only five network permissions, so a trusted addon or MCP daemon can hold `SEND_SMS`, `BLUETOOTH_CONNECT`, `NEARBY_WIFI_DEVICES`, `NFC`, `MANAGE_EXTERNAL_STORAGE` or Android 17's `ACCESS_LOCAL_NETWORK` and still receive the selected text the bridge hands it.
  Evidence: `ime/security/NoNetworkPermissionPolicy.kt:28-34`; consumers `ime/addon/AddonEnumerator.kt:189-192`, `ime/mcp/McpAndroidDiscoverer.kt:222`, `ime/mcp/McpDaemonDiscoverer.kt:108`; data-flow claim `docs/PRIVACY_AND_AI.md:216-219`; `ACCESS_LOCAL_NETWORK` per https://developer.android.com/about/versions/17/behavior-changes-17
  Touches: `ime/security/NoNetworkPermissionPolicy.kt`, `ime/addon/AddonEnumerator.kt`, `ime/mcp/McpAndroidDiscoverer.kt`, `ime/mcp/McpDaemonDiscoverer.kt`, `app/src/test/.../McpAndroidDiscovererTest.kt`, `McpDaemonDiscovererTest.kt`, `docs/PRIVACY_AND_AI.md`, `app/src/main/config/trust-capabilities.json`
  Acceptance: enrolment accepts a package only when every requested permission is in an explicit allowlist; a fixture manifest declaring `android.permission.SEND_SMS` is rejected with a named reason; at least one test uses hard-coded permission literals rather than deriving its inputs from the policy under test.
  Complexity: M

- [ ] P1 — Restore the four correctness lint checks disabled across `:lib:android`
  Why: a build workaround for an unrelated `:lib:snygg:generateJsonSchema` failure disables `Recycle`, `CommitPrefEdits`, `ApplySharedPref` and `CommitTransaction` for the whole Android compat layer, which includes the clipboard manager; only `UElementAsPsi` plausibly relates to the lint-API conflict, and no `lint-baseline.xml` exists to catch what they miss.
  Evidence: `lib/android/build.gradle.kts:68-79` (the repo's only `FIXME`)
  Touches: `lib/android/build.gradle.kts`, `lib/snygg/build.gradle.kts`, any `lib/android` sources the restored checks flag
  Acceptance: the `disable` list contains at most `UElementAsPsi`; `:lib:android:lintDebug` and `:lib:snygg:generateJsonSchema` both succeed; any violations the restored checks find are fixed rather than re-suppressed.
  Complexity: M

- [ ] P1 — Move the startup cache wipe off the main thread
  Why: `cacheDir?.deleteContentsRecursively()` runs synchronously in `Application.onCreate` and again in `init()`, so every cold IME start blocks the main thread on a recursive delete whose cost scales with leftover cache; upstream reports the same defect class.
  Evidence: `FlorisApplication.kt:139`, `FlorisApplication.kt:153`; `ExtensionManager.init()` at `lib/ext/ExtensionManager.kt:113-119` already shows the `ioScope` pattern; https://github.com/florisboard/florisboard/issues/3300
  Touches: `FlorisApplication.kt`
  Acceptance: no filesystem traversal happens on the main thread during `onCreate`; a test asserts the wipe is dispatched off-main and that a subsequent cache read tolerates the wipe still being in flight.
  Complexity: S

- [ ] P1 — Correct the emoji data version header and regenerate the assets
  Why: the bundled assets are generated from CLDR v48 — the Unicode 17 update — and already contain Emoji 17.0 characters, yet declare `EMOJI-VERSION: 16.0`, so every downstream claim about emoji coverage is wrong in both directions.
  Evidence: `app/src/main/assets/ime/media/emoji/en.txt` lines 1-4 (`# CLDR-VERSION: 48`, `# EMOJI-VERSION: 16.0`) with matches for `distorted face`, `fight cloud` and `hairy creature` — all listed as V17.0 at https://www.unicode.org/emoji/charts/emoji-versions.html — plus `orca`, `trombone`, `treasure chest`, `landslide`; CLDR 48 release note (2025-10-29) "Updated for Unicode 17, including new names and search terms for new emoji, new sort-order" — https://cldr.unicode.org/downloads/cldr-48 ; FUTO Keyboard shipped Unicode 17 emoji in v0.1.29 (2026-06-01)
  Touches: `app/src/main/assets/ime/media/emoji/*.txt`, `ime/media/emoji/EmojiData.kt`, `app/src/test/.../EmojiDataVersionTest.kt`, `Roadmap_Blocked.md` (retire the CLDR v49 blocker)
  Acceptance: every asset header declares the emoji version that matches its contents; a test asserts the declared version against a probe set of Emoji 17.0 code points present in the data; the `Roadmap_Blocked.md` Emoji 17 entry is removed with its premise corrected rather than left as a stale blocker.
  Complexity: S

- [ ] P1 — Repair or retire the Crowdin pipeline and reconcile its locale mapping
  Why: `crowdin.yml` is orphaned — the workflow that consumed it no longer exists anywhere in the tree — and its mapping omits 8 shipped locales including `zh-rCN` (999 strings, the largest translation) and `ur-rPK`, so those translations cannot round-trip; README nevertheless advertises a Crowdin pipeline.
  Evidence: `crowdin.yml` (34 mapped locales) vs 42 `app/src/main/res/values-*` locale directories — missing `et-rEE`, `ko-rKR`, `lv-rLV`, `nds-rDE`, `pt-rBR`, `sq-rAL`, `ur-rPK`, `zh-rCN`; `.github/` contains no `workflows/` directory; `README.md:187`
  Touches: `crowdin.yml`, `scripts/` (a local `crowdin` CLI wrapper if kept), `CONTRIBUTING.md`, `README.md`, `docs/`
  Acceptance: either the mapping covers every shipped locale and a documented local command performs upload/download, or `crowdin.yml` is deleted and `CONTRIBUTING.md` documents the PR-based translation path; `README.md` matches whichever is true; a gate fails when a `values-*` directory exists with no corresponding translation route.
  Complexity: M

### P2

- [ ] P2 — Pin the host-desync reconciliation with regression fixtures
  Why: text duplication and cursor jumps in rich-text and web hosts are reported independently against three other keyboards. SwiftFloris already has the mechanism — `finishComposingText()` inside the delete batch, a range-guarded `setComposingRegion`, and a re-read fallback when the host's reported state contradicts the prediction — but nothing replays those sequences, so a regression in that path would ship silently.
  Evidence: https://github.com/florisboard/florisboard/issues/3310 ; https://github.com/HeliBorg/HeliBoard/issues/2702 ; https://github.com/AnySoftKeyboard/AnySoftKeyboard/issues/4812 ; https://github.com/AnySoftKeyboard/AnySoftKeyboard/issues/4856 ; mechanism at `ime/editor/AbstractEditorInstance.kt:185-211`, `:362-368`, `:595-600`, `:614-619`; anti-append guard scoped to password fields at `ime/editor/EditorInstance.kt:294-300`
  Touches: `app/src/test/.../ime/editor/`, `ime/editor/AbstractEditorInstance.kt`, `ime/editor/EditorInstance.kt`
  Acceptance: fake-`InputConnection` fixtures replay the three reported sequences (delete-then-duplicate, mid-text cursor jump, web-editor spam) against a host that reports a selection contradicting the batch, and assert no duplicated or lost characters; the `commitCompletion` anti-append guard is evaluated for non-password editors and either widened or documented as deliberately narrow.
  Complexity: M

- [ ] P2 — Harden defaults while Android Advanced Protection Mode is enabled
  Why: AAPM is the platform's declared signal that the user is at elevated risk, and a no-network keyboard is the natural consumer of it; no competing keyboard reacts to it today.
  Evidence: https://developer.android.com/privacy-and-security/advanced-protection-mode ; `AdvancedProtectionManager` has zero references in the tree; existing gates `app/build.gradle.kts` `verifyNoInternetPermission`, `app/src/main/config/trust-capabilities.json`
  Touches: `app/src/main/AndroidManifest.xml` (add `QUERY_ADVANCED_PROTECTION_MODE`), a new `ime/security/AdvancedProtectionPolicy.kt`, `app/settings/privacy/PrivacyPostureScreen.kt`, `app/src/main/config/trust-capabilities.json`, `docs/PRIVACY_AND_AI.md`
  Acceptance: when `isAdvancedProtectionEnabled` is true the IME forces incognito, suspends clipboard-history persistence and refuses new addon/MCP enrolment, states this on the privacy-posture screen, and reacts live via `registerAdvancedProtectionCallback`; behaviour is unchanged and no permission is required on API < 36; the added permission is recorded in the trust-capability manifest with a rationale.
  Complexity: M

- [ ] P2 — Bump AGP to 9.3.0
  Why: AGP 9.3.0 (July 2026) ships the updated optimization DSL and `src/<variant>/keepRules/` source sets, which removes hand-maintained keep-rule wiring from the release lane; the repo is two minors behind and the AGP 10 preparation item downstream is easier from 9.3.
  Evidence: https://developer.android.com/build/releases/agp-9-3-0-release-notes ; `gradle/libs.versions.toml:3` (`android-gradle-plugin = "9.2.1"`); blocked AGP 10 item in `Roadmap_Blocked.md`
  Touches: `gradle/libs.versions.toml`, `app/build.gradle.kts`, `lib/*/build.gradle.kts`, `benchmark/build.gradle.kts`, `README.md` Architecture & Stack
  Acceptance: `scripts/release-evidence.ps1` passes end to end on 9.3.0, including the reproducible-APK and Roborazzi gates; README version pins match the catalog.
  Complexity: M

- [ ] P2 — Add a VNI composer for Vietnamese
  Why: `vi-VN` ships with only one of Vietnamese's two standard input methods. Telex is already a data-driven rule table, so VNI is a second table in the same extension rather than engine work — the cheapest remaining coverage gain for a major shipped locale.
  Evidence: `app/src/main/assets/ime/keyboard/org.florisboard.composers/extension.json:14-17` declares `telex` as the only `with-rules` composer; the matcher is locale-agnostic (`ime/text/composing/Composer.kt:63-89`); subtype binding at `org.florisboard.localization/extension.json:660-663`
  Touches: `app/src/main/assets/ime/keyboard/org.florisboard.composers/extension.json`, `app/src/main/assets/ime/keyboard/org.florisboard.localization/extension.json`, `app/src/main/res/values/strings.xml`, `app/src/test/.../ime/text/composing/`
  Acceptance: a `vni` composer is selectable for the Vietnamese subtype; digit-tone sequences (`a1` → `á`, `a6` → `â`, `a8` → `ă`, `d9` → `đ`) and their undo rules produce the same output as the equivalent Telex sequences; a table test covers every tone/diacritic pair in both composers.
  Complexity: M

### P3

- [ ] P3 — Repair the stale pointers in the local agent docs
  Why: the reading order still routes a fresh session to two files the same document records as deleted, and a blocked item cites a workflow that no longer exists — stale pointers are how the Emoji 17 blocker's false premise survived.
  Evidence: `CLAUDE.md:11-18` vs `CLAUDE.md:38-39`; `Roadmap_Blocked.md:174` cites `.github/workflows/emulator-smoke.yml`; `.github/` contains no `workflows/` directory; `ime/addon/AddonEnumerator.kt:36-38` still calls the addon-rejection UI "future" although it shipped at `app/settings/addons/AddonsSettingsScreen.kt:274-282`
  Touches: `CLAUDE.md`, `Roadmap_Blocked.md`, `ime/addon/AddonEnumerator.kt`
  Acceptance: every file path referenced in `CLAUDE.md` and `Roadmap_Blocked.md` resolves in the working tree; the emulator-tier item is re-scoped to `app/src/androidTest/` and a local `adb` run since the project deliberately ships no GitHub Actions; the `AddonEnumerator` KDoc names the shipped screen instead of a hypothetical one.
  Complexity: S

- [ ] P3 — Bump Kotlin to the current stable patch (2.4.10)
  Why: the catalog pins 2.4.0 while 2.4.10 (2026-07-14) is the current stable patch in the 2.4 line. This is routine maintenance and explicitly **not** the CVE-2026-53914 fix — that requires 2.4.20 stable, which is still at Beta2 with a September 2026 target and remains tracked in `Roadmap_Blocked.md`.
  Evidence: https://kotlinlang.org/docs/releases.html ; `gradle/libs.versions.toml:20`; `scripts/check-kotlin-build-cache-cve-guard.py` (`MIN_FIXED_KOTLIN = (2, 4, 20)`)
  Touches: `gradle/libs.versions.toml`, `README.md`, `app/build.gradle.kts:58-62` (re-evaluate the KSP `moduleName` workaround)
  Acceptance: full unit suite plus lint and the release-evidence lane pass on 2.4.10; the build-cache CVE guard still reports the vulnerable-but-mitigated state (it must not silently start passing); the KSP colon workaround is either still required with a refreshed comment or removed.
  Complexity: S

- [ ] P3 — Justify or remove the native toolchain build pins
  Why: `ndkVersion` forces an NDK 29 install for every build while the tree has no native sources at all, and the CMake/Rust pins are read only by the reproducible-build script with no recorded rationale.
  Evidence: `app/build.gradle.kts:75`; `gradle/tools.versions.toml` (`ndk`, `cmake`, `rustToolchain`); no `CMakeLists.txt`, `Cargo.toml`, `.so`, `jniLibs` or `externalNativeBuild` anywhere in the tree; `utils/repr_build/run.sh:30-34` is the only other consumer
  Touches: `app/build.gradle.kts`, `gradle/tools.versions.toml`, `utils/repr_build/run.sh`, `docs/` build prerequisites, `README.md` Prerequisites
  Acceptance: either a comment records why the pins are load-bearing for reproducible builds and the prerequisites document them, or `ndkVersion` is dropped and `:app:assembleRelease` plus `verify-reproducible-apk.sh` succeed on a machine with no NDK installed.
  Complexity: S

- [ ] P3 — Clamp the floating IME window so its own handles stay reachable
  Why: three August 2026 HeliBoard reports describe a movable/resizable keyboard reaching a state the user cannot undo from inside the keyboard; SwiftFloris has the reset actions but an unresolved rounding-error TODO in the drag math and a documented untested offset case.
  Evidence: https://github.com/HeliBorg/HeliBoard/issues/2725 ; https://github.com/HeliBorg/HeliBoard/issues/2709 ; https://github.com/HeliBorg/HeliBoard/issues/2708 ; `ime/window/ImeWindowEditorHandles.kt:433`; `app/src/test/.../ime/window/ImeWindowControllerActionsTest.kt:227`
  Touches: `ime/window/ImeWindowEditorHandles.kt`, `ime/window/ImeWindowController.kt`, `ime/window/SplitKeyboardLayoutCalculator.kt`, `app/src/test/.../ime/window/`
  Acceptance: a property test over arbitrary drag/resize sequences asserts the window spec always keeps the move handle and resize corner inside the display bounds and above the minimum interactive size; the drag-accumulation TODO is resolved or replaced with a measured rationale.
  Complexity: M

- [ ] P3 — Make invalid key code points fail visibly
  Why: `getCodeInfoAsTextKeyData` swallows the failure from `appendCodePoint` and produces a key with an empty label, so a malformed user-imported Keyboard3/KLC/Keyman layout yields silently blank keys instead of a diagnostic.
  Evidence: `ime/text/keyboard/TextKeyData.kt:82-88`
  Touches: `ime/text/keyboard/TextKeyData.kt`, the Keyboard3 / hardware-layout import diagnostics under `ime/keyboard3/` and `ime/hardware/`, `app/src/test/.../ime/text/keyboard/`
  Acceptance: an invalid code point yields either a rejected key with a named import diagnostic or a visible placeholder glyph, never a blank label; a test covers a surrogate-range and an out-of-range code point.
  Complexity: S

- [ ] P3 — Expose line and page cursor jumps in the quick-actions panel
  Why: `KeyboardMode.EDITING` was removed and its replacement panel carries undo/redo, arrows and clipboard actions but no line- or page-level movement, so `MOVE_START_OF_LINE` / `MOVE_END_OF_LINE` / `MOVE_START_OF_PAGE` and the Page Up/Down keys added in v1.9.58 are reachable only through swipe bindings a user has to discover.
  Evidence: `ime/keyboard/KeyboardMode.kt:31-33` (EDITING removed as a dead mode); default arrangement `ime/smartbar/quickaction/QuickActionArrangement.kt:74-100`; key codes `ime/keyboard/KeyCode.kt:57-60`; swipe-only routing `ime/keyboard/KeyboardManager.kt:406-408`; https://github.com/HeliBorg/HeliBoard/issues/2706 ; https://github.com/AnySoftKeyboard/AnySoftKeyboard/issues/4805
  Touches: `ime/smartbar/quickaction/QuickActionArrangement.kt`, `ime/smartbar/quickaction/QuickAction.kt`, `app/src/main/res/values/strings.xml`, `app/src/test/.../ime/smartbar/`
  Acceptance: line-start, line-end and page-up/page-down actions are available from the quick-actions overflow panel with localized labels and TalkBack descriptions, and existing user arrangements migrate without losing customization.
  Complexity: S

- [ ] P3 — Add a reveal affordance and manual marking for sensitive clipboard entries
  Why: sensitive clips render as a fixed asterisk string with no way to confirm what was captured, and classification is fully automatic (two regexes plus the API-33 flag) with no way for a user to mark or unmark an entry — so a false positive is unrecoverable and a false negative is unfixable.
  Evidence: `ime/clipboard/provider/ClipboardDatabase.kt:233-238`; `app/src/main/res/values/strings_dont_translate.xml:35`; classifier `ime/clipboard/ClipboardSensitiveTextClassifier.kt:19-30`; `ime/clipboard/ClipboardManager.kt:274-301`; no reveal/mask pref in `app/prefs/ClipboardPrefs.kt`; https://github.com/florisboard/florisboard/issues/3323
  Touches: `ime/clipboard/ClipboardInputLayout.kt`, `ime/clipboard/provider/ClipboardDatabase.kt`, `app/prefs/ClipboardPrefs.kt`, `app/src/main/res/values/strings.xml`, `docs/PRIVACY_AND_AI.md`
  Acceptance: a long-press action toggles an entry's sensitive flag, and a per-entry reveal shows the content transiently without persisting the unmasked form or changing the a11y label default; revealing is disabled while the incognito or lock-screen gates are active.
  Complexity: M

- [ ] P3 — Offer emoji suggestions alongside the word instead of replacing it
  Why: an emoji candidate commits as a full replacement of the composing region, so accepting a suggestion destroys the word the user typed; the requested behaviour is to append.
  Evidence: `ime/nlp/SuggestionCandidate.kt:251-262` (`EmojiSuggestionCandidate.text = emoji.value`); `ime/editor/EditorInstance.kt:292-327` finalizes the composing region with `candidate.text`; no shape option in `app/prefs/EmojiPrefs.kt:108-128`; https://github.com/HeliBorg/HeliBoard/issues/2704
  Touches: `ime/nlp/SuggestionCandidate.kt`, `ime/editor/EditorInstance.kt`, `app/prefs/EmojiPrefs.kt`, `app/src/main/res/values/strings.xml`, `app/src/test/.../ime/nlp/`
  Acceptance: a preference selects replace-word (current default) or append-after-word; in append mode committing an emoji suggestion for `INLINE_TEXT` leaves the typed word intact followed by the emoji, and `LEADING_COLON` triggers keep replacing the `:shortcode:`.
  Complexity: S
