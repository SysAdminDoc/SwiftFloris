# Contributing To SwiftFloris

Thanks for improving SwiftFloris. This project is an Android keyboard, so small
changes can affect private user input. Keep changes narrow, testable, and
consistent with the offline-first architecture.

## Start Here

Read these first:

1. `README.md` for the current product invariants, architecture, and release state.
2. `ROADMAP.md` for priority and historical context.
3. `docs/SECURITY.md` and `docs/THREAT_MODEL.md` for privacy and permission rules.

## Project Rules

The base app must remain:

- No network permission.
- No telemetry, ads, account binding, or cloud processing.
- Apache-2.0-compatible in `:app`.
- Free of closed-source native blobs.
- Safe around password fields and `IME_FLAG_NO_PERSONALIZED_LEARNING`.

If a feature conflicts with those rules, design it as an isolated optional
addon instead of weakening the base app.

## Development Setup

Required:

- JDK 21.
- Android SDK with compile SDK 36 and Build Tools 36.0.0.
- The bundled Gradle wrapper.
- An Android device or emulator for manual IME smoke tests.

Common commands:

```powershell
.\gradlew.bat :app:verifyNoInternetPermission :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
.\gradlew.bat :app:verifyRoborazziDebug
.\gradlew.bat :app:installDebug
```

Linux/macOS shells can use `./gradlew` with the same tasks. The complete local
gate, lint baseline-drift wrapper, adb smoke, and benchmark commands are in
[`docs/LOCAL_VERIFICATION.md`](docs/LOCAL_VERIFICATION.md).
Repo hygiene rules for generated outputs and deleted docs are in
[`docs/REPO_HYGIENE.md`](docs/REPO_HYGIENE.md).

## Picking Work

Prefer the smallest complete slice:

- One roadmap item, one bug, or one coherent refactor per pull request.
- Start with `ROADMAP.md` and `RESEARCH.md` if no issue is assigned.
- Do not mix product behavior, dependency bumps, and broad cleanup in one
  change unless the roadmap explicitly groups them.
- Leave unrelated dirty files alone.

## Code Guidelines

- Match nearby Kotlin, Compose, and JetPref patterns.
- Extract deterministic rules into pure Kotlin helpers when it makes tests
  easier.
- Keep Android framework wiring thin in managers and activities.
- Prefer existing project helpers over new utility layers.
- Add comments only where the code path is non-obvious.
- Keep user-facing strings in resources; do not hard-code UI copy in Compose.
- Avoid adding dependencies unless they are necessary, actively maintained,
  license-compatible, and documented.

## Privacy And Permission Changes

Before adding a permission, provider, exported component, model, dataset, or
new file-sharing path:

1. Explain why the existing local-only route is insufficient.
2. Check `docs/THREAT_MODEL.md` and `docs/SECURITY.md`.
3. Update relevant tests or verification gates.
4. Update documentation and release notes.

Never add `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`,
`CHANGE_NETWORK_STATE`, or `CHANGE_WIFI_STATE` to the app manifest.

## Tests And Verification

At minimum, code changes should run:

```powershell
.\gradlew.bat :app:verifyNoInternetPermission :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Also run when relevant:

```powershell
.\gradlew.bat :app:verifyRoborazziDebug
.\gradlew.bat :app:installDebug
```

Manual QA is required for IME behavior. Use
[`docs/QA_CHECKLISTS.md`](docs/QA_CHECKLISTS.md) for the visual matrix,
manual-flow rows, and release-evidence format. For keyboard changes, test at
least:

- A normal text field.
- A password field.
- A multiline field.
- A field with suggestions disabled.
- A rich-content-capable app if the change touches clipboard, emoji, stickers,
  or media.

For autocorrect, spacebar, punctuation, backspace, glide-delete, or hardware
keyboard changes, also follow the contract and scenario matrix in
[`docs/AUTOCORRECT_LIFECYCLE.md`](docs/AUTOCORRECT_LIFECYCLE.md).

Accessibility notes for manual QA:

- With TalkBack enabled, Settings screens should announce one pane title on
  entry, then traverse app bar controls, content, persistent actions, and
  floating actions in that order.
- Keyboard keys should announce specific labels for modifiers, clipboard,
  voice, mode, layout, input-method, and smartbar controls instead of generic
  "button" or "key" text.
- Candidate-row items should announce suggestion type, position, and candidate
  text, and eligible suggestions should expose the remove-from-predictions
  accessibility action.
- At high font scale, settings metadata, links, dialogs, extension components,
  and theme key previews should wrap or grow without clipping text.
- Warning, error, progress, success, cancellation, and ready/skipped states
  should remain understandable by icon shape and copy when color perception is
  unavailable.
- Re-check dark theme, SwiftKey High Contrast, compact/floating/split layouts,
  and at least one landscape viewport when the change touches keyboard or
  settings layout.

Record skipped visual or manual rows with a reason. Do not mark a
device-gated row complete from source inspection alone.

If your machine cannot run Gradle, say exactly which command was attempted and
why it failed. Do not mark a code change fully verified from source inspection
alone.

## Documentation And Release Notes

Versioned releases use:

- `gradle.properties` version code/name bump.
- A new `## vX.Y.Z` section in root `CHANGELOG.md` with an
  `<a id="vX.Y.Z"></a>` anchor.
- A matching `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`
  file.
- Updates to `README.md` and `ROADMAP.md` when user-facing state changes.
- A local annotated tag for the release commit.

Draft the Fastlane changelog as the short store-facing summary: keep it at or
below 480 characters for headroom, summarize the verified outcome, and avoid
test commands, file paths, internal-only IDs, or claims not backed by the full
`CHANGELOG.md` section. The detailed rule lives in
[`docs/REPO_HYGIENE.md`](docs/REPO_HYGIENE.md).

Docs-only housekeeping commits do not need a version bump unless they are being
published as a release.

## Pull Requests

Every pull request should include:

- What changed.
- Why it changed.
- Tests run, with exact commands.
- Manual QA performed, if applicable.
- Any remaining risk or maintainer-host verification needed.

Keep PRs reviewable. If the change is growing beyond one logical topic, split
it.

## AI-Assisted Contributions

AI tools are allowed, but the contributor remains responsible for the result.

- Verify generated code against source, tests, and project invariants.
- Do not paste private user data, crash dumps with secrets, or proprietary model
  assets into external services.
- Do not accept generated code with incompatible licensing.

## Licensing

SwiftFloris is Apache-2.0 in the main app. New code should follow the existing
file-header style when adding Kotlin source. New third-party assets or
dependencies require license review and, when applicable, `NOTICE` / `LICENSES/`
updates.
