# SwiftFloris Architecture

Last updated: 2026-05-18, against the v1.8.164 codebase.

This file is the fast architectural map for contributors. It is intentionally
shorter than `ROADMAP.md` and more code-oriented than `PROJECT_CONTEXT.md`.

## Product Boundary

SwiftFloris is an Android input method editor (IME) forked from FlorisBoard and
extended toward SwiftKey-class offline typing. The main app has three
load-bearing constraints:

- No `INTERNET` or equivalent network permission in `:app`.
- Apache-2.0-compatible code and assets in `:app`.
- No closed-source native blobs.

Features that need network access, incompatible licenses, or large optional
native runtimes belong in isolated addon APKs, not in the base app.

## Module Layout

The active Gradle modules are declared in `settings.gradle.kts`.

| Module | Role |
|---|---|
| `:app` | IME service, Settings app, Android manifest, resources, addon facades, tests |
| `:lib:android` | Android utility extensions |
| `:lib:color` | Color utilities |
| `:lib:compose` | Shared Compose resource, layout, and UI helpers |
| `:lib:kotlin` | Pure Kotlin utility code |
| `:lib:snygg` | Snygg theme parser and runtime engine |
| `:benchmark` | AndroidX Macrobenchmark module plus adb-driven benchmark harness target |

`:benchmark` is included in settings for Macrobenchmark and adb performance
harnesses. `:lib:native` remains present on disk but inactive until a native
addon/runtime slice intentionally revives it.

## Runtime Entrypoints

| Entrypoint | File | Responsibility |
|---|---|---|
| Application | `app/src/main/kotlin/dev/patrickgold/florisboard/FlorisApplication.kt` | Installs crash handling, EmojiCompat, preference datastore, extension manager, clipboard manager, dictionary manager, adaptive touch model, and global manager singletons |
| IME service | `app/src/main/kotlin/dev/patrickgold/florisboard/FlorisImeService.kt` | Owns the keyboard lifecycle, input connection, inline suggestions, IME window, voice switching, and settings launch bridge |
| Settings activity | `app/src/main/kotlin/dev/patrickgold/florisboard/app/FlorisAppActivity.kt` | Hosts the Compose Settings app, deep links, import intents, setup flow, preview field, and Android edge-to-edge behavior |
| Spell checker service | `app/src/main/AndroidManifest.xml` | Exposes the Android text-service binding |
| Tasker receiver | `app/src/main/AndroidManifest.xml` and `ime/tasker/` | Signature-protected automation endpoint for insert text, insert clip, switch layout, and trigger voice |
| Content providers | `ime/clipboard/provider/ClipboardMediaProvider.kt`, `ime/media/sticker/StickerMediaProvider.kt` | Grant read-only rich-content URIs for clipboard media and stickers |

Most long-lived managers are lazy properties on `FlorisApplication` and are
accessed through context extension helpers such as `context.keyboardManager()`,
`context.nlpManager()`, and `context.editorInstance()`.

## App Configuration

Preferences are modeled in
`app/src/main/kotlin/dev/patrickgold/florisboard/app/AppPrefs.kt` with JetPref.
The generated datastore is initialized from `FlorisApplication.init()`.

Use the existing preference groups when adding settings. A new top-level
preference class is appropriate only when the feature has a distinct lifecycle
or owner, such as `clipboard`, `correction`, `sync`, `voice`, or `sticker`.

## Package Map

Primary app code lives under
`app/src/main/kotlin/dev/patrickgold/florisboard/`.

| Package | Main responsibility |
|---|---|
| `app/` | Settings activity, navigation, setup, preference model, import screens |
| `app/settings/` | Compose Settings screens grouped by product area, plus pure policies for settings/editor validation |
| `ime/core/` | Subtype and active-language state |
| `ime/editor/` | Editor info, current input connection, rich-content commits, field guards |
| `ime/input/` | Feedback, capitalization, haptics, input state |
| `ime/keyboard/` | Keyboard orchestration and action dispatch |
| `ime/text/` | Text keyboard layout, key data, gesture typing, popup mapping |
| `ime/nlp/` | Suggestions, ranking, language blending, priors, inline autofill |
| `ime/dictionary/` | Floris/user dictionary, encryption-adjacent storage hooks, dictionary import/export |
| `ime/bidi/`, `ime/indic/`, `ime/geez/`, `ime/cjk/` | Script shaping, normalization, transliteration, and CJK facades |
| `ime/hardware/` | Windows KLC, Keyman LDML, macOS `.keylayout`, and runtime hardware-keyboard mapping |
| `ime/media/` | Emoji, emoticons, stickers, user-imported sticker folder support |
| `ime/clipboard/` | Internal clipboard history, media sharing, content-provider bridge |
| `ime/voice/` | FUTO Voice Input handoff, Vosk fallback, model-management surfaces |
| `ime/theme/` | Theme loading, per-app accents, wallpaper receiver, Snygg integration |
| `ime/smartbar/` | Candidate strip, quick actions, smartbar layout |
| `ime/smartcompose/`, `ime/translate/`, `ime/handwriting/`, `ime/passkey/`, `ime/wordstyles/` | Offline-first facades for larger optional capabilities |
| `ime/addon/` | Addon registry, manifest contracts, package enumeration |
| `ime/mcp/` | Local MCP daemon discovery, AIDL client, service lifecycle, settings surface |
| `ime/sync/` | User-chosen local sync channels and QR/manual pairing helpers |
| `ime/window/`, `ime/landscapeinput/`, `ime/sheet/`, `ime/popup/` | IME window, extracted input, sheets, and key popups |
| `lib/` | Shared app-local utility code |

## Typing Pipeline

The high-level typing path is:

1. Android calls `FlorisImeService` with editor lifecycle events.
2. `EditorInstance` tracks the active `EditorInfo`, input connection, selected
   text, MIME capabilities, and sensitive-field state.
3. `KeyboardManager` receives key events from the Compose keyboard, hardware
   keyboard hooks, or automation entry points.
4. Text-key behavior delegates into `ime/text/`, `ime/input/`, `ime/keyboard`
   policies, and `ime/nlp/` depending on whether the action is raw text, a
   correction, a suggestion, a gesture trace, or rich content.
5. `NlpManager` gathers dictionary, priors, user learning, multilingual
   scoring, and field guards before `CandidateAutoCommitPolicy` and the rankers
   decide auto-commit, quick-prediction, and rejection behavior.
6. Commits return through `EditorInstance` into the Android `InputConnection`.

Keep deterministic rules pure where practical. The roadmap and improvement plan
prefer extracting policy from Android-bound managers into JVM-testable helpers.

## Media And Rich Content

Emoji and sticker UI is under `ime/media/`. Clipboard media and stickers use
content-provider URIs instead of broad media-library permissions.

- Clipboard media is served through `ClipboardMediaProvider`.
- Bundled and imported stickers are served through `StickerMediaProvider`.
- User-imported sticker folders use a persisted, user-selected SAF tree URI and
  remain read-only unless a later explicit write flow is implemented.
- Rich content is committed through `EditorInstance.commitRichContent(...)`
  after checking the target editor's declared MIME support.

## Addon Boundary

The addon system is the architectural answer for optional or risky capability:
large models, network-capable helpers, incompatible licenses, native runtimes,
and language/theme/dictionary packs that should not live in the base APK.

The manifest declares the signature-protected
`dev.patrickgold.florisboard.permission.REGISTER_ADDON` permission and package
visibility queries for addon registration actions. Addon specs live under
`docs/addons/`.

Main-app code may define stable contracts and no-op/local facades for optional
capabilities. The base APK must not quietly absorb an addon's risky dependency.

## Security And Privacy

Key security files:

- `docs/THREAT_MODEL.md`
- `docs/SECURITY.md`
- `docs/PRIVACY_AND_AI.md`
- `docs/REPRODUCIBLE_BUILDS.md`
- `app/build.gradle.kts`

Important implementation anchors:

- `verifyNoInternetPermission` in `app/build.gradle.kts` fails the build if any
  app manifest declares a network permission.
- Personal dictionary storage uses SQLCipher, with its passphrase protected by
  the Tink/AndroidKeystore wrapper.
- Sensitive fields are guarded before learning, clipboard writes, addon calls,
  and local AI/automation surfaces.
- Password fields get IME-side screenshot protection through `FLAG_SECURE`.
- `backup_rules.xml` excludes sensitive local state from Android cloud backup.

## Build And CI

Use the Gradle wrapper. The expected maintainer-host verification path is:

```powershell
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew.bat :app:verifyRoborazziDebug
```

The CI workflows under `.github/workflows/` cover Android build/test/lint,
network-permission verification, OSV scanning, release builds, reproducible
build checks, string validation, Crowdin upload, and maintainer-triggered
Roborazzi baseline capture.

The local VM used by some agent runs may not have Java or the Android SDK on
PATH. In that case, record the exact Gradle command attempted and leave final
verification for the maintainer build host.

## Testing

Unit tests live under `app/src/test/kotlin/` and mostly use Kotest. Screenshot
tests use Roborazzi and Robolectric. The active test strategy is:

- Pure policy classes get focused JVM tests.
- Android-facing managers get tests around extracted decisions when framework
  tests would be brittle.
- UI-affecting visual changes should add or update Roborazzi coverage once the
  baseline-capture window closes.
- Every bug fix should include a regression test unless the fix is purely
  documentation or build metadata.

## Documentation Routing

- Product state and roadmap: `PROJECT_CONTEXT.md`, `ROADMAP.md`,
  `SWIFTKEY_PARITY_ROADMAP_2026-05-17.md`
- Typing behavior contracts: `docs/AUTOCORRECT_LIFECYCLE.md`
- Research artifacts: `.ai/research/<YYYY-MM-DD>/`
- Release notes: one root `RELEASE_NOTES_vX.Y.Z.md` per versioned release
- Contributor workflow: `CONTRIBUTING.md`
- Architecture: this file
- Security/privacy/rebuild docs: `docs/`

When a change updates a load-bearing invariant, update the code, the gate, and
the corresponding documentation in the same logical commit.
