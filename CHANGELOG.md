# Changelog

## v1.9.64 (2026-08-25)

- Answer Android 16 when it asks where the keyboard's language settings are. The system keyboard settings offer a link to an IME's language setup, and with nothing declaring that entry point the link had nowhere to go. It now opens the subtype list directly, which is where a language actually gets added. Together with the reworded language-pack copy and the renamed Add keyboard language button, that closes the path Discussion #21 got lost on.
- Check the app's own native libraries for 16 KB page compatibility. Only addon APKs were verified before, while the app ships SQLCipher across four ABIs; a library linked for 4 KB pages does not load on an Android 15 or newer 16 KB device, it crashes at first use. The new gate reads every `lib/**/*.so` out of the built APK and requires it to be stored, to start on a 16384-byte boundary, and to declare `p_align` of at least 16384 on every `PT_LOAD` segment. All eight libraries pass.
- Emit a genuinely unsigned release APK when no release keystore is configured, so the artifact F-Droid builds from matches the filename its recipe declares and can start a reproducible comparison. Also removes an `AntiFeatures: KnownVuln` entry whose description read "None known", which F-Droid applies on the presence of the key rather than the prose under it.
- Apply Advanced Protection changes without restarting the keyboard. Clipboard retention and addon enrolment already re-read the live state at every decision, but private typing was decided when a field was focused, so a session already in progress kept learning until the user moved on, and the privacy posture screen kept showing whatever it was composed with. One platform callback per process now carries the change to both. The live read stays authoritative, so a callback that never arrives cannot leave a stale answer enforcing anything.
- Respect an editor that forbids generative text replacement. Android 16 lets an editor set `isWritingToolsEnabled` to false, which is a stronger statement than the keyboard's own consent preference. The rewrite router now suppresses ahead of the cache, so a result cached for a field that allowed rewriting cannot be served into one that forbids it. Editors below API 36 cannot express the objection and are unchanged.
- Bound the error text that reaches toasts, notice cards and dialogs. All 24 paths that show a caught throwable to a person now route through one helper that collapses line breaks and control characters to spaces, drops the bidi overrides that reverse how a line reads, and cuts the result at 200 characters without splitting a surrogate pair. `ZipUtils` quotes a rejected archive entry name straight into its `SecurityException`, so a crafted archive previously chose up to 255 characters of what the user saw. Joiners that carry meaning in Arabic, Persian and Indic scripts and in emoji sequences are kept. The full cause goes to the log at every one of those sites, several of which logged nothing before.
- Stop curly placeholder substitution from rescanning its own output. A substituted value that carried the same placeholder was substituted into itself until the heap ran out, and ZipUtils echoes a rejected archive entry name into the message RestoreScreen interpolates, so a crafted backup archive took Settings down through the guard that caught it. Both overloads now scan the template once and never re-examine what they inserted.
- Stop settings captions announcing as unusable buttons. `FlorisOutlinedBox` attached a clickable modifier unconditionally, so the 92 boxes with a decorative title reached TalkBack as disabled buttons, while the one that navigates carried no button role and only a 23 dp target. List rows that navigate or apply a preset gained a button role, the extension add-file icon gained a label, and the subtype panel header lost a `clickable(false)` no-op.
- Make emoji sheet validation errors readable on light themes. `media-emoji-pin-sheet-error` is defined in none of the 21 bundled stylesheets, so the hardcoded Material 3 dark-scheme tone was what every user saw, at roughly 2:1 on the light themes. The tone is now chosen against the resolved sheet background.
- Keep a single failure from disabling a subsystem for the session. `SubtypeManager` and the Latin pre-warm scope were the only long-lived scopes in `ime/` without a `SupervisorJob`, so one throw left language switching or index pre-warming silently dead until the process restarted.
- Stop the crash dialog losing the report it exists to send. Reading the stacktraces deletes them, and the activity handles only rotation, so a theme, font-scale or locale change rebuilt an empty report. The report now survives recreation.
- Stop glide trails growing for the whole keyboard session. The shared fade buffer was never pruned, so retained memory and the per-frame scan grew with every word typed, and each in-flight trace was rebuilt per motion event rather than appended to.
- Stop the setup screen relaunching itself twice a second. Its captured state stays frozen for one effect run, so the relaunch condition stayed true until the settings observer caught up; the poll now stops once it has acted and never starts when it cannot.
- Say what the language pack screen is for. Discussion #21 is a user hunting for Portuguese there while `pt.fldic` was already bundled, guided by copy that read like the place to add a language and a button labelled "Add subtype".
- Count things correctly. Six strings ran every quantity through one English form, so a single item read "1 triggers", "1 apps" and "Keep 1 verified archives".
- Give every neutral hairline one opacity. Dividers and container borders drew `outlineVariant` at five different alphas across thirteen call sites.
- Stop silently dropping Gboard dictionary entries that contain a slash. The entry matcher excluded `/` from the attribute run, so "24/7", "km/h" and "n/a" never matched and were discarded. The import only reports failure when zero entries parse, so a file with other valid words imported "successfully" while losing them.
- Stop tracking generated Python bytecode, and reject it in the hygiene gate.
- Fix nine awkward or ungrammatical user-facing strings, an incognito toast that claimed a manual toggle changed a default, nine em dashes against the project style, four spellings of "spacebar", and British spellings in the en-US default.
- Cut Kotlin compiler warnings from 30 to 22, and match `LayoutTypeSerializer`'s visibility to the type it serializes so it cannot fail to resolve at a use site.

## v1.9.63 (2026-08-22)

- Fail closed across Android backup transports. Android 8 exports nothing, encrypted cloud backup and Android 12 device transfer use the portable inventory, and Android 16 QPR2 cross-platform transfer exports nothing. Persisted-store discovery, selected-resource parsing, and transport-policy tests guard the boundary.
- Keep raw-content developer tools out of production APKs. Debug overlays now clear and hide clipboard, editor, spelling, and inline-autofill content in password, incognito, and no-learning sessions. Release APK and build-variant gates enforce both boundaries.
- Repair the backup and restore benchmark harness for the current archive workspace and persisted-content inventory APIs.
- Keep Android's tracked `src/release` code distinct from generated release output in the repository-hygiene gate. Its fixture preserves both decisions, and the root-crash-log self-test now finds Git Bash directly on Windows instead of falling into an unavailable WSL relay.
- Stop adaptive-touch refinement and persistence in password, incognito, and app-declared no-personalized-learning sessions. Normal text fields still learn, and focused privacy tests cover all four paths.
- Restore the canonical release trust registry to Build Tools 37.0.0 and SQLCipher 4.18.0. Exact drift fixtures now fail when either live owner changes without a matching registry update.

## v1.9.62 (2026-08-21)

- Evaluate offline rule-based proofreading without adding a production engine. LanguageTool's licence, Java surface, and per-language data cost rule it out for the base APK. Harper is the cleaner future addon candidate, but it has no official Android library and supports English only.
- Add a debug-only English agreement rule and device test. The test reaches `FlorisSpellCheckerService` through Android's `TextServicesManager` and proves that the existing sentence path returns `RESULT_ATTR_LOOKS_LIKE_GRAMMAR_ERROR` on a physical Android 16 device.

## v1.9.61: 2026-08-20

- Add one snackbar surface to Settings and the keyboard panels. Clipboard text deletion and per-app profile deletion can now be undone, media deletion asks for confirmation, and Delete no longer sits in the profile dialog's dismiss controls.
- Settings search now scrolls to the matching preference row and briefly highlights it. The shared preference wrappers keep the destination screens unchanged while the row reports its position, and the search card disappears after the row is reached.
- Replace security-path source checks with behavior tests for encrypted stores, dictionary learning, clipboard policy, backup, sync, MCP consent, privacy gates, and sticker manifest replacement. Roborazzi capture classes are explicitly named in the plain unit-test report, with fixed preview density and font scale for repeatable captures.
- Share the long-lived `NlpAddonHub` from `NlpManager` with the translation quick action, so future per-instance addon state cannot diverge between typing and smartbar calls.
- Raise material-kolor to 5.0.0 now that the Compose BOM is enforced. The full unit suite and Roborazzi theme captures remain green.
- Decode alternating two-pointer glides as separate traces, committing each word in finger-release order. The existing single-pointer detector behavior remains covered, and the active trail now renders both traces.
- Add a fork-provenance section to README with the install package identity, merged release permission surface, certificate capture commands, and reproducible-build review checklist. The official release fingerprint remains pending until a signed release APK is published.
- Add clipboard privacy controls: long-press can mark or unmark a text entry as sensitive, and a deliberate reveal shows it for four seconds only while the device is unlocked and the keyboard is not incognito. The default accessibility label stays masked.
- Add an opt-in scrambled keypad for numeric password fields. The permutation is generated per field focus, and the key label, TalkBack text, and committed digit stay aligned. Normal numeric and phone fields are unchanged.
- Add optional local keypress sounds for standard, delete, return, and spacebar keys. Files are imported through the system picker, loaded into a pooled player, and carried by the existing backup flow. System effects remain the default until a class is selected.
- Add a spacebar touchpad mode that tracks horizontal and vertical drags, preserves fractional movement at a configurable ratio, and clamps horizontal targets to the editor's safe bounds. Existing discrete spacebar actions remain available when the mode is off.
- Align split-keyboard gutters with vertical hinge bounds reported by AndroidX Window Manager. Each row scales its halves to the hinge, while non-folding windows keep the centered layout.
- Add a runtime warning when bundled emoji assets declare a different CLDR or Emoji version than the checked-in expectation.
- Split offensive suggestion filtering into the existing all-offensive switch and a slurs-only option, with exact-token filtering shared by word suggestions and spell-check corrections.
- Add a 50% to 100% autocorrect confidence slider. The local typing-quality scorecard records every threshold and selects the measured 50% default from the checked-in replay corpus.
- Stop shipping a Material 3 version no file in the repository names. Four
  Compose Multiplatform dependencies each constrain `androidx.compose.material3`
  to a pre-release alpha, and Gradle picked that over the Compose BOM's stable
  pin, so the reproducible-build doc's claim that every Compose version is pinned
  here was not true. The BOM is now applied as an `enforcedPlatform`, and a gate
  fails the build if any Compose artifact resolves to a pre-release again.
- Add "Report a problem" to Settings → About. Reaching the issue tracker from
  inside the app previously required an actual crash: the version, build type,
  commit hash, install source, device and Android version the issue templates ask
  for were assembled in the crash dialog and nowhere else, so anyone reporting a
  bug that did not crash had to find all of it by hand. The row copies the same
  block, with the redaction reminder, and opens the bug report form.
- Let the crash report and the debug log be shared to another app. Both were
  copy-to-clipboard only, which meant pasting by hand on the device that is
  misbehaving. The redaction reminder travels with the shared text.
- Fix a crash reporter that could crash. It read every field on
  `Build.VERSION_CODES` as an integer, which holds for the constants a stock
  platform declares but throws the moment a non-integer field exists: so the
  failure would land while reporting a failure. It now reads only static integer
  fields and falls back to naming the SDK level.
- Stop clipboard search from copying the whole history on every keystroke. Each
  typed character lowercased every stored clip into a fresh string before
  searching it, so with a history of near-limit clips a single keystroke
  allocated megabytes on the thread drawing the panel. Matching now compares in
  place. A replay covering a full history of 64 KiB clips measures the
  allocation, because a wall-clock budget loose enough not to flake also passes
  with the defect present.
- Make settings search find every preference. It covered 103 of the 276 rows the
  settings screens declare, so Input Feedback matched none of its 16 preferences,
  Addons none of 8, MCP none of 9 and Gestures 7 of 28: searching "vibration
  strength" or "utility key action" returned nothing. The 173 missing rows are
  indexed, and a new gate fails the build when a screen declares a preference
  that search cannot reach: the old integrity test iterated the index itself, so
  it could only check what was already there and never saw an omission.
- Say on the MCP screen that no daemon runs in this build. The screen offered
  discovery review, a bridge switch and per-daemon toggles while nothing could
  be bound or dispatched, so turning a daemon on looked like it started
  something. Trust decisions and toggles are still saved and apply once binding
  returns. One flag now decides this for both the keyboard service and Settings,
  so the two cannot drift apart.
- Let glide typing work with a second finger on the keyboard. A finger already
  resting on the keys claimed the gesture detector and never let go, because a
  motionless finger is never classified as a glide, so the other hand could not
  swipe at all. Sliding movement was also matched against the wrong pointer,
  which dropped every move once the gliding finger was not the first one down.
  A glide already under way now keeps the trace when another finger lands, and
  the key a glide starts on is resolved from the gliding pointer rather than
  assumed to be the first: which is what suppresses glides that begin on
  delete, shift or space.
- Put SwiftFloris under Android Settings → Apps → Language on Android 13 and
  newer. The app declared no `android:localeConfig`, so the system's per-app
  language picker never listed it and the 43 shipped translations could only be
  reached from inside the app.
- Offer every shipped translation in the in-app language picker. Its list was
  written by hand and had fallen four behind, so the Asturian, Estonian,
  Albanian and Urdu translations shipped in the APK with no way to select them.
  The list is now generated from the same resource directories the build reads.
- Keep the in-app language choice and Android's per-app language in step: the
  picker writes through to the system setting, and a language chosen in system
  Settings wins at startup rather than being overwritten by the stored one.
- Bring the build and library pins current: Gradle 9.7.1, AGP 9.3.1, Compose BOM
  2026.08.00, AndroidX Core 1.19.0, AndroidX SQLite 2.7.0, SQLCipher 4.18.0, Coil
  3.5.0, KSP 2.3.11, build tools 37.0.0 to match `compileSdk 37`, AboutLibraries
  15.1.0, Kotest 6.2.4, Roborazzi 1.72.0, Kover 0.9.9, JUnit Vintage 6.1.3 and
  ComposablePreviewScanner 0.9.3. Kotlin stays on 2.4.10 and Robolectric on
  4.16.1, both for reasons recorded in `docs/DEPENDENCY_TRIAGE.md`.
- Stop two tests from pinning dependency versions as literal source text. The
  SQLCipher, SQLite and Tink version literals had a second owner in a test that
  had to be edited on every bump, so they now live only in the freshness gate
  that already enforces them; the AboutLibraries assertion reads its expected
  version from the version catalog, so a pin bump that leaves the generated
  licence metadata stale still fails.

## v1.9.60: 2026-08-20

- Recognise Transcribro as an offline voice input provider, alongside FUTO Voice
  Input, WhisperInput and Whisper.
- Name every store a backup archive leaves behind, rendered from the backup
  inventory instead of a hand-written sentence that listed four of thirteen and
  never mentioned the personal dictionary. A new excluded store without a label
  now fails a test rather than going unlisted.
- Say plainly that importing over an existing word replaces its frequency and
  shortcut, and that undo does not bring the old values back.
- Say that the privacy audit log covers the current keyboard session, so an
  empty log after a restart no longer reads as "no AI call ever happened".
- Keep the personal dictionary on the language you are editing when you delete
  its last word. It previously bounced back to the language list with no
  explanation, which also made the per-language empty state unreachable; that
  state now renders and names the language.
- Give the backup exclude list one owner. The `verifyDataExtractionRules` build
  task pinned a hand-written 13 of the 22 paths in `data_extraction_rules.xml`,
  so removing the Tasker HMAC secret, the clipboard history and its keys, or the
  scheduled-backup preferences left it green; `BackupDataInventoryTest` already
  matched both rule sets exactly and now owns the check alone.
- Give every security-relevant dependency a freshness floor. The gate checked a
  single dependency and printed `OK (1 checked dependency floor(s))`, which read
  like a pass while Tink, Room, androidx-sqlite, Kotlin, KSP and AGP had no floor
  at all; it now fails when a security-relevant catalog pin has no reviewed
  entry. The override matcher was inverted and could let one coordinate's
  override suppress another's floor, so overrides now match on both fields and
  are rejected at load time when either is missing.
- Record the Tink CVE-2026-15432 triage: the reported timing side channel is in
  `ChunkedMacVerification`, which this app never calls, so the pin stays at
  1.23.0 with a floor that surfaces the patched release when it ships.
- Repair two dead ends in the migration flow: the "SwiftFloris encrypted backup
  (.sfexp)" tile now opens the personal dictionary import picker, which detects
  the encrypted envelope and prompts for the passphrase, instead of the archive
  Restore screen that cannot read it; and an archive storing its dictionary as a
  SQLite snapshot now explains how to re-export it rather than pointing at an
  import path that does not exist.
- Correct four public docs that described protections the code does not have:
  the personal dictionary passphrase is Keystore-wrapped rather than
  Keystore-held, the Tasker receiver is gated by a default-off preference and a
  per-install HMAC signature rather than a signature permission (its `adb`
  examples were rejected at runtime and are replaced with the real setup flow),
  the reduced-motion guard reads `ANIMATOR_DURATION_SCALE` through
  `rememberReducedMotion()` rather than a Compose API that does not exist, and
  the release checklist now names the merged-manifest gate that actually
  enforces the permission guarantee.
- Raise the shared Settings widgets to the 48 dp WCAG 2.5.5 touch-target floor
  through a single named constant, including the custom layout editor's key
  buttons, and cover the floor with a test that measures the rendered target
  rather than matching source text.
- Fix a context-chain walk that could not terminate: configuring the IME system
  bars from a wrapped context (a Compose dialog or themed wrapper) spun on the
  main thread instead of resolving, and a missing window now skips the system
  bar setup rather than crashing.
- Derive theme contrast coverage from every foreground-bearing selector in each
  bundled stylesheet instead of a ten-selector list, report all violations at
  once, and name each WCAG exemption (inactive components, decorative
  separators, and the 3:1 non-text floor for glyph elements) in the gate.
- Raise contrast in the bundled themes the widened gate exposed: the extracted
  landscape action and the borderless focused key popup no longer draw light
  text on a light surface, and the glide trail, focused emoji tab, and floating
  resize handle use accent-variant tints that clear the non-text floor in the
  Floris Day, SwiftKey Pure, and M3E Nord themes.
- Warn inline in the theme editor when an edited foreground/background pair
  falls below the 4.5:1 WCAG AA text floor.
- Make the OSV release gate classify numeric CVSS scores and supported vectors
  correctly, fail closed on unknown severity, and run a regression self-test
  beside the release gate.
- Keep smart-compose ghost text off during incognito sessions, including in
  ordinary non-sensitive editors, and retain request-scoped privacy gating.
- Make release evidence exhaustive: discover every gate and Python self-test,
  record the two connected-device gates as explicit operator-run checks, and
  repair the repo-hygiene allowlist drift.
- Keep Android system dictionary access read-only in SwiftFloris: system entries
  remain browsable and exportable, while add, edit, delete, and import actions
  stay in the internal dictionary and Android system dictionary settings.
- Unify addon/MCP enrollment, merged-manifest, and addon-APK permission gates
  around the same fail-closed allowlist, including a SEND_SMS fixture regression
  check and the shared trust-capabilities registry.
- Expand live-document integrity to scan planning and untracked Markdown, reject
  dead roadmap/workflow references, and validate the blocked-roadmap structure.
- Require the F-Droid recipe's `commit:` ref to resolve on both local and origin
  tag sets, with a fixture proving an unresolvable ref fails the release gate.
- Route live smart-compose and translation calls through the audited addon hub,
  require explicit MCP consent/disable gates, remove the dead direct dispatcher,
  and pause MCP daemon binding until a real audited keyboard action exists.
- Preserve unreadable sticker, correction-prior, and personal n-gram stores
  instead of treating them as empty, and only quarantine clipboard history on
  explicit SQLite corruption evidence while keeping transient read failures
  retryable.
- Make MCP lifecycle startup single-shot even when consent is disabled and
  serialize start, rescan, retry, and teardown so a Settings rescan cannot
  repopulate registries during IME shutdown.
- Remove the maintainer device serial from benchmark baselines and emit a
  stable SHA-256 device key derived from manufacturer, model, and SDK, with
  repository hygiene rejecting serial fields in tracked documentation.
- Reconcile stale device-tier blockers with the attached API-36 emulator:
  preserve the real app/password-manager/API-37 gaps, close the already-shipped
  instrumented smoke coverage, and keep the missing MCP addon sample blocked.
- Declare API-33 stylus-handwriting and TalkBack touch-exploration inline-
  autofill capabilities in the IME manifest, with a contract test and updated
  accessibility/autofill verification guidance.
- Keep theme, extension, and addon settings loading states separate from their
  empty states, automatically start the first addon scan, and add a Roborazzi
  baseline for the theme loading surface.

## v1.9.59: 2026-08-11

- Screen addon and MCP daemon enrolment against a permission allowlist so
  transports that need no `INTERNET` permission: SMS, Bluetooth,
  nearby-devices, shared storage: are rejected alongside the network ones.
- Clear leftover cache off the main thread at startup, so a cold keyboard
  start no longer blocks on a recursive delete.
- Replace a typed word by selecting it and committing over the selection
  instead of marking a composing region, so rich-text and web editors that
  ignore composing regions no longer duplicate the word.
- Move the build to Android Gradle Plugin 9.3.0.
- React to Android Advanced Protection Mode (Android 16+): while it is on,
  learning from typed text, clipboard history persistence and new add-on
  enrolment are all held off, and the privacy posture screen says so.
- Reject unrenderable key code points during layout import instead of
  producing a blank, unlabelled key.
- Move the build to Kotlin 2.4.10.
- Offer line-start, line-end, text-start, text-end and Page Up/Down as
  quick actions, so cursor jumps no longer require knowing a swipe binding.
- Add an opt-in emoji suggestion mode that keeps the typed word and puts the
  emoji after it instead of replacing the word.
- Route all 42 shipped locales through the translation pipeline; eight,
  including Simplified Chinese and Urdu, previously had no mapping and could
  never round-trip. A gate now fails when a locale has no route.
- Declare the bundled emoji data as Unicode Emoji 17.0, which is what CLDR 48
  generated; the assets already carried every Emoji 17.0 character.

## v1.9.58: 2026-08-02

- Keep trust-capability evidence aligned with SQLCipher clipboard fallback and
  manifest permission-removal directives, and make the release-front-door
  locale gate portable across `python` and `python3` environments.
- Retain bounded, privacy-gated glide alternatives for unchanged committed words and restore them when the cursor returns to the word.
- Search emoji across the active and enrolled subtype locales with ordered fallback matching and bounded deduplication.
- Expand headless settings screenshot coverage across compact, wide-landscape, RTL, and 200% font-scale states, with shared loading/error/empty semantics and production color contrast checks.
- Refresh Tink to 1.23.0, Roborazzi to 1.70.0, and Kotest to 6.2.3 with the existing verification gates retained.
- Localize privacy-audit record labels, plural summaries, and timestamps while keeping the JSON export schema locale-independent.
- Centralize editor input-class/variation/flag compatibility, clear stale candidates for host-owned completion fields, and add headless restart and hardware-key contract coverage.
- Add a deterministic trust-critical locale coverage gate with translated-resource ratchets, explicit reviewed UI locale policy, typing-language separation, `en-XA`/`ar-XB` pseudolocale contracts, and hard-coded critical-copy detection.
- Centralize bounded keyboard-mode/context transitions so clipboard and media panels restore the prior symbols/numeric mode and clear stale history across editor, privacy, and window boundaries.
- Wire custom emoji tags through long-press palette actions, palette search, emoji suggestions, persisted settings management, locale-root normalization, and atomic file replacement.
- Preserve existing emoji pin-group files when an atomic replacement fails, including on Windows hosts that reject rename-over-existing.
- Surface snippet load and delete failures, move snippet file work to Dispatchers.IO, and expose per-file trigger counts from state.
- Route emoji and sticker empty-state copy through the keyboard theme text pipeline, including expanded-font-scale Roborazzi coverage.
- Resolve custom-layout labels and generated extension metadata through localized resources while preserving stable on-disk slugs.
- Add programmable Page Up/Page Down key codes, Android page-key dispatch, custom-layout editor support, localized labels, and Terminal/Navigation preset coverage.
- Import Unicode Keyboard3 XML as hardened local keyboard extensions with bounded XML parsing, versioned bundled-CLDR allowlists, deterministic compilation, source provenance, and security/conformance diagnostics.

## Roadmap archive: 2026-08-10: ROADMAP.md

<details>
<summary>Original roadmap snapshot</summary>

```markdown
# SwiftFloris Roadmap

This file contains only actionable, unblocked work. Completed items are
deleted (they live in git history and the fastlane changelogs). Items
gated on external deliverables or hardware testing live in
`Roadmap_Blocked.md`.

---

## Research-Driven Additions

### P3
## Research-Driven Additions (2026-06-29)

### P1

### P3

## Research-Driven Additions

### P1

### P2

## Research-Driven Additions (2026-06-29 refresh)

### P1

### P2

### P3

## Research-Driven Additions

### P1

### P2

### P3

## Research-Driven Additions

### P1

### P2

### P3

## Research-Driven Additions

### P0

### P1

### P2

### P3

## Research-Driven Additions

### P2

### P3

## Audit Findings: 2026-08-02

Baseline for this pass: `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug`
→ **BUILD SUCCESSFUL in 4m 34s**, no failing tests, no lint failures. Nothing
below is a pre-existing baseline failure; every item is a defect found by
reading and tracing the code against that green baseline.

### P2

### P3
```

</details>
