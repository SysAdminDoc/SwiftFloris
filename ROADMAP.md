# SwiftFloris Roadmap

Actionable work only. Historical and completed roadmap material is archived in CHANGELOG.md; blocked work is kept in Roadmap_Blocked.md.

## Research-Driven Additions

### P1

- [ ] P1: Triage the unverified reliability suspicions from the 2026-08-25 sweep
  Why: A delegated sweep over `ime/`, `backup/` and `lib/` raised 25 candidate defects. Six were traced and fixed this pass (curly-template self-substitution, the unpruned glide fade buffer, the two missing `SupervisorJob`s, the crash-dialog report loss, the Gboard slash regex). The rest were never traced to a caller, so they are suspicions, not findings, and are recorded here so they are not silently dropped. Each needs reachability confirmed before any code moves.
  Where, highest-value first: `PersonalBigramStore.kt:207` / `PersonalTrigramStore.kt:214` / `CorrectionOutcomePriors.kt:124` (`learn()`'s `ioScope.launch` body has no try/catch while `ensureLoadedLocked` rethrows, and the loaded flag is not set on failure, so a malformed TSV may repeat per keystroke); `SnippetManager.kt:86,168` (`sanitizeFileName` never forces a `.yml`/`.yaml` extension while `loadAll` reads only those, and `SnippetSettingsScreen.kt:105` passes a SAF document id, so an import may write a file that is invisible and undeletable); `LatinLanguageProvider.kt:590,635` (`decodeFrequencies` routes any non-`.fldic` path to `decodeFromString` unguarded and `DictionaryPackDescriptor` never validates the extension); `LayoutManager.kt:89,91` (unbounded `layoutCache`/`popupMappingCache` with no eviction, which may also cache a transient read failure permanently); `CacheManager.kt:306` (`runBlocking` reached from `remember` on the main thread in `RestoreScreen.kt:166` and `BackupScreen.kt:343`); `QuickActionsEditorPanel.kt:237` (`runBlocking` around a suspend preference write inside `onDispose`); `KeyboardManager.kt:542-544` and `KeyboardState.kt:238` (non-volatile cross-thread state where the surrounding class uses `@Volatile` elsewhere); `GlideAlternativeSession.kt:95`, `AddonRegistryStartup.kt:79-104`, `LocalStickerPackRepository.kt:84,297` (unsynchronised shared mutation); `PersonalDictionaryImportBatch.kt:109,120` (insert uses a raw locale string while the lookup binds `FlorisLocale.localeTag()`); `StartupCachePurge.kt:49`, `FsFile.kt:48`, `VoiceModelInstallStore.kt:64`, `StickerPaletteView.kt:132`, `InputFeedbackController.kt:67`, `HanShapeBasedLanguageProvider.kt:243`, `QuickAction.kt:127`.
  Acceptance: Every entry above is either reproduced and fixed with a regression test, or closed with a one-line note saying which existing guard makes it unreachable. Entries that survive triage but are too large get their own roadmap item.
  Complexity: L

- [ ] P1: Apply installed locale hints with explicit user choices first
  Why: Host apps can provide a conversation locale and Android 13 per-app locale, but SwiftFloris ignores both outside debug output.
  Evidence: https://developer.android.com/reference/android/view/inputmethod/EditorInfo; https://developer.android.com/reference/android/app/LocaleManager; https://github.com/futo-org/android-keyboard/pull/1892; app/src/main/kotlin/dev/patrickgold/florisboard/lib/util/DebugSummarizeUtils.kt:47-49; app/src/main/kotlin/dev/patrickgold/florisboard/ime/core/SubtypeManager.kt:191-238.
  Touches: EditorInfo snapshot, SubtypeManager.kt, PerAppSubtypeMemory, localization preferences and UI, locale-resolution tests.
  Acceptance: A current manual choice and saved per-app subtype take precedence; otherwise one unambiguous installed match from hintLocales, then LocaleManager application locales on API 33 and newer, may select a subtype; ambiguous or unavailable hints do nothing; no asset is downloaded; users can disable automatic hint matching; precedence and fallback tests pass.
  Complexity: M

- [ ] P1: Bring adaptive-touch state under the persisted-data contract
  Why: AdaptiveTouchModel writes `adaptive_touch_model.xml`, but the canonical inventory, backup omission UI, parity tests, and public privacy description do not classify or accurately describe that store.
  Evidence: app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/AdaptiveTouchModel.kt:36-47 and :83-92; app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/advanced/BackupDataInventory.kt:89-101; docs/PRIVACY_AND_AI.md:97-106.
  Touches: AdaptiveTouchModel.kt, BackupDataInventory.kt, Android backup rules, manual archive sections or omission UI, TypingStatsScreen.kt, privacy documentation, persisted-store discovery and parity tests.
  Acceptance: The inventory classifies `adaptive_touch_model.xml` as portable or sensitive-excluded; Android and manual backup behavior matches that decision; an excluded store is named in omission UI, while an included store has an archive version, migration, and reset path; restored state cannot contain samples from password, incognito, or host-declared no-learning sessions; public text states that only ordinary learning sessions update the model; a fixture introducing an unregistered SharedPreferences file fails the persisted-store gate.
  Complexity: M

- [ ] P1: Verify manual backup publication before reporting success
  Why: The interactive path treats a completed SAF write as a valid backup, while the scheduled path reads the destination back and compares its digest before publication.
  Evidence: app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/advanced/BackupScreen.kt:392-405; app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/advanced/ScheduledBackupSaf.kt:74-80 and :164-213; https://bitwarden.com/help/encrypted-export/; https://www.reddit.com/r/Swiftkey/comments/1vp13la/restore_keyboard_layout_languages_settings_and/; https://www.reddit.com/r/Swiftkey/comments/1tylh00/migration_to_onedrive_failed_lost_predictions/.
  Touches: BackupScreen.kt, BackupArchiveBuilder.kt, ScheduledBackupSaf.kt, PortableBackupEnvelope.kt, restore inspection, backup receipt UI and strings, SAF provider fixtures.
  Acceptance: One shared verifier reads the destination document back, enforces size limits, compares SHA-256, authenticates and decrypts encrypted envelopes, and validates metadata plus selected archive sections through the same parser limits used by restore; interactive success appears only after verification; truncation, corruption, wrong passphrases, and provider readback failures produce actionable errors without discarding the source workspace or a prior verified archive; the receipt shows format version, encryption state, selected sections, named omissions, byte size, and checksum; fake-provider tests cover short writes and altered reads.
  Complexity: M

### P2

- [ ] P2: Wire the rewrite surface to production, or say it is a contract
  Why: `RewriteRouter` has no production instantiation, `RewriteProviderRegistry.setActive` has no caller, and every `RewriteRequest` is built in a test. The Android 16 writing-tools guard added on 2026-08-25 is correct and covered, but it sits on a path a shipped build never enters, so `FlorisEditorInfo.isWritingToolsEnabled` currently has no production reader either.
  Where: app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartcompose/RewriteRouter.kt:40; RewriteProvider.kt:121; app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/FlorisEditorInfo.kt:114.
  Acceptance: Either a production caller builds a `RewriteRequest` from the live `FlorisEditorInfo` (carrying `isWritingToolsEnabled`) and dispatches through `RewriteRouter`, with a test proving an editor that forbids rewriting is suppressed end to end; or the package is documented as a contract awaiting a provider, and the KDoc says so rather than describing a pipeline that never runs.
  Complexity: M

- [ ] P2: Make the keyboard layout controller reachable from unit tests
  Why: `TextKeyboardLayoutController` is private to `TextKeyboardLayout.kt`, so the glide buffer lifecycle, pointer bookkeeping, and touch routing have no direct coverage. The trail-retention rule had to be extracted to `GlideTrailRetention` to be testable at all, and the per-pointer trace handling around it is still only covered indirectly.
  Where: app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/TextKeyboardLayout.kt:741 and :1580-1670; app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/gestures/GlideTrailRetention.kt.
  Acceptance: The controller's non-Compose state transitions are reachable from a JVM or Robolectric test without making the whole composable public; tests cover a single-pointer glide, two pointers finishing in either order, and a cancel mid-gesture; the fade buffer is asserted bounded after a long replay.
  Complexity: M

- [ ] P2: Give the settings home screen the spacing rhythm every other screen uses
  Why: HomeScreen is the only settings file inventing 3, 5, 6, 7, 10, 14, 17, 44 and 86 dp values. Every other screen sticks to the 4/8/12/16/24/32 steps that `defaultFlorisOutlinedBox` (8/16) and `FlorisCardDefaults` establish, so cards visibly fail to align when you move between home and any screen it links to.
  Where: app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/HomeScreen.kt:361, 464, 469, 681, 690, 691, 701, 745, 790, 795, 828, 869.
  Acceptance: A named spacing scale exists alongside `FlorisSurfaceTokens`; HomeScreen uses it; the Roborazzi home captures are re-recorded deliberately with the alignment change reviewed rather than tolerated.
  Complexity: S

- [ ] P2: Replace the remaining dark-only Snygg colour fallbacks
  Why: `snyggErrorForegroundFor` fixed the emoji sheet error tone, but three surfaces still pass a fixed dark literal as the last-resort value for an element that no bundled stylesheet defines, so a light or custom theme that misses the rule gets a dark panel or a black strip. `window-resize-overlay-fixed` is defined in 0 of 21 stylesheets and always falls back to an untinted 50% gray scrim.
  Where: app/src/main/kotlin/dev/patrickgold/florisboard/ime/media/emoji/EmojiTagSheet.kt:77 and PinToGroupSheet.kt:86 (`Color(0xFF171923)`); app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartbar/CandidatesRow.kt:335 (same literal); app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartbar/Smartbar.kt:374 (`Color.Black`); app/src/main/kotlin/dev/patrickgold/florisboard/ime/window/ImeWindowEditorHandles.kt:213 (`Color.Gray.copy(alpha = 0.5f)`).
  Acceptance: Every last-resort colour is derived from a resolved sibling or from the theme's light/dark posture rather than a literal; a test enumerates the elements referenced from Kotlin that no bundled stylesheet defines and asserts each one's fallback is derived, so a new undefined element cannot silently ship a dark-only default.
  Complexity: M

- [ ] P2: Decide what the pending honeycomb keyboard widgets are for
  Why: `HoneycombHexButton` and `HoneycombKeyboardRow` are reachable only from a Roborazzi capture. Their own KDoc says production shipped in v1.8.79 through the normal Snygg `TextKeyButton` path with `HoneycombHexShape`, which is the code that is actually live. They still carry hardcoded dark-navy colours that ignore Snygg entirely, and `HoneycombKeyboardRow` computes `rowStrideDp` only to discard it behind `@Suppress("UNUSED_VARIABLE")`.
  Where: app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/HoneycombHexButton.kt:75-77; HoneycombKeyboardRow.kt:73-88; app/src/test/kotlin/dev/patrickgold/florisboard/screenshot/PendingKeyboardSurfacesScreenshotTest.kt:142.
  Acceptance: Either the widgets are wired to Snygg and given a stated production role, or they and their capture are removed; `HoneycombTessellation` and `HoneycombHexShape` stay either way because `TextKeyboard.kt` and `TextKeyboardLayout.kt` use them.
  Complexity: S

- [ ] P2: Preserve reproducible-build output when the container build fails
  Why: The current script copies output only after success and removes the failed container before its evidence is collected.
  Evidence: utils/repr_build/run.sh:90-108.
  Touches: utils/repr_build/run.sh, reproducible-build shell tests, output manifest and log handling.
  Acceptance: Success and failure both copy available logs, manifests, and partial outputs before container removal; the script returns the original assembly exit code; cleanup runs once; a fake failing container test proves evidence survives.
  Complexity: S

- [ ] P2: Balance the candidate-row benchmark trace on every exit
  Why: The current comment accepts an open Perfetto section if composition throws.
  Evidence: app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartbar/CandidatesRow.kt:97-109 and :217-222.
  Touches: CandidatesRow.kt, trace helper or trace sink, candidate-row tests, benchmark parser if marker shape changes.
  Acceptance: No hand-started candidate-row section remains open after normal composition, recomposition cancellation, or a thrown test composable; the benchmark still records a bounded candidate-row duration; fake-sink tests assert begin and end counts.
  Complexity: S

- [ ] P2: Enforce coverage floors for trust-critical packages
  Why: Kover is enabled without verification rules, so a green suite does not guarantee execution of privacy, backup, migration, addon, editor, or dictionary branches.
  Evidence: app/build.gradle.kts:331-333; https://kotlin.github.io/kotlinx-kover/gradle-plugin/; recurring migration, privacy, and editor fixes in CHANGELOG.md.
  Touches: app/build.gradle.kts, Kover filters and verification rules, release-evidence.ps1, coverage documentation.
  Acceptance: Package or class-level line and branch floors cover privacy policy, backup and restore, migrations, addon enrollment, editor transactions, and dictionary persistence; generated and UI-only code is excluded with written reasons; a self-test removes coverage from a fixture and proves verification fails.
  Complexity: M

- [ ] P2: Centralize and localize input-provider metadata
  Why: The subtype editor owns a two-entry English map and cannot describe future providers consistently.
  Evidence: app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/localization/SubtypeEditorScreen.kt:403-415.
  Touches: NLP provider registry or extension metadata, SubtypeEditorScreen.kt, strings.xml and locale resources, provider registry tests.
  Acceptance: Every registered provider supplies a stable ID, localized label, capability summary, and availability state from one registry; the subtype editor contains no provider-name literals; unknown persisted IDs show an explicit unavailable state; an exhaustive test fails when a provider lacks metadata.
  Complexity: S

- [ ] P2: Let users choose the subtype cycle order and subset
  Why: FlorisBoard and HeliBoard users independently request quick switching that skips rarely used languages, while SwiftFloris cycles the full subtype list.
  Evidence: https://github.com/florisboard/florisboard/issues/3328; https://github.com/HeliBorg/HeliBoard/issues/2744; app/src/main/kotlin/dev/patrickgold/florisboard/ime/core/SubtypeManager.kt:386-413.
  Touches: localization preferences and UI, SubtypeManager.kt, backup schema, preference migration, switching tests.
  Acceptance: Users can reorder subtypes and exclude entries from previous or next cycling without deleting them; the subtype picker still shows all configured entries; deleting or disabling the active entry selects a deterministic fallback; backup, restore, and migration retain the configured cycle.
  Complexity: M

- [ ] P2: Add an explicit clean-link clipboard action
  Why: Tracking-parameter removal fits the offline privacy model, but silent clipboard rewriting would be unsafe for signed or authenticated URLs.
  Evidence: https://github.com/futo-org/android-keyboard/pull/1833; https://support.brave.com/hc/en-us/articles/9982188779405-What-does-Copy-clean-link-mean; app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard.
  Touches: clipboard text actions, URL sanitizer and pinned rules asset, preview and undo UI, strings, sanitizer and Roborazzi tests.
  Acceptance: A user-invoked action previews original and cleaned URLs before copy or paste; path, fragment, parameter order, and repeated non-tracking parameters are preserved; signed, expiring, authentication, and ambiguous URLs remain unchanged; no automatic rewrite occurs; undo restores the original.
  Complexity: M

- [ ] P2: Guard public toolchain and accessibility facts against drift
  Why: Build docs disagree on JDK and Build Tools versions, and accessibility docs attribute Android's 48 dp guidance to a WCAG criterion that specifies 44 CSS pixels.
  Evidence: gradle/tools.versions.toml; README.md:268-305; CONTRIBUTING.md; docs/REPRODUCIBLE_BUILDS.md; docs/ACCESSIBILITY.md; https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum.html.
  Touches: CONTRIBUTING.md, docs/REPRODUCIBLE_BUILDS.md, docs/ACCESSIBILITY.md, scripts/check-public-doc-version-pins.py, checker self-tests.
  Acceptance: Docs distinguish host JDK 21 from reproducible and F-Droid JDK 17, derive or verify Build Tools 37.0.0 from the machine-readable pin, cite Android 48 dp and WCAG 44 CSS pixels separately, and fail a fixture containing each stale value or attribution.
  Research note (2026-08-23): The same gate must derive SQLCipher version and native-library claims from the dependency catalog and built APK. docs/THREAT_MODEL.md still says 4.17.0 and zero native code while SQLCipher 4.18.0 contributes native libraries.
  Complexity: M

### P3

- [ ] P3: Rename the misspelled `hint_component_label_to_long` resource key
  Why: The key should be `too_long`. It is referenced by roughly 30 translated `values-*` files, so renaming it out of band would drop every existing translation.
  Where: app/src/main/res/values/strings.xml; app/src/main/kotlin/dev/patrickgold/florisboard/lib/ext/ExtensionValidation.kt:106; all `values-*/strings.xml`.
  Acceptance: The rename is coordinated with Crowdin so translations follow the key; the locale-coverage gate stays green.
  Complexity: S

- [ ] P3: Remove the dead `else` branch in the popup hint priority resolution
  Why: The compiler reports the enclosing `when` as exhaustive, so the branch is unreachable. It reads as defensive but hides the compile error that would otherwise flag a new `KeyHintMode` value.
  Where: app/src/main/kotlin/dev/patrickgold/florisboard/ime/popup/PopupSet.kt:99.
  Acceptance: The branch is gone and adding a hint mode fails the build rather than silently taking a fallback path; popup priority tests still pass for every existing mode pair.
  Complexity: S

- [ ] P3: Re-evaluate reduced motion without waiting for a configuration change
  Why: `rememberReducedMotion` keys its lookup on `LocalConfiguration`, but the animator duration scale is a `Settings.Global` value that does not produce a configuration change, so turning "Remove animations" on while the app is open has no effect until something else recomposes it.
  Where: lib/compose/src/main/kotlin/org/florisboard/lib/compose/ReducedMotion.kt:30-44.
  Acceptance: The value is observed through a settings observer like the other `Settings.Global` reads in `State.kt`, and toggling the system setting updates the running UI.
  Complexity: S
