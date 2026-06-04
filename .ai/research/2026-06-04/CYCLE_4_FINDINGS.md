# SwiftFloris Cycle 4 Findings - 2026-06-04

## Scope

Cycle 4 began after `git pull --rebase` reported `master` up to date. The
worktree carried only owned roadmap/research-doc edits during this pass. Current
pushed history before this cycle's commit was clean at `dc72e32`
(`v1.8.223-6-gdc72e32`) with no tag pointing at HEAD. This pass did not edit
feature code, tests, build files, or assets, and did not run Gradle because the
research lane is docs-only.

## Anti-Duplication Check

- R3-1 through R3-4 remain open and were not duplicated.
- RA-4 and RA-9 remain the existing settings-search accessibility/highlight
  follow-ups.
- The sync channel selector accessibility item from `docs/AUDIT_2026-05-29.md`
  is already fixed in code through radio-button roles, so no roadmap item was
  added.
- The deferred `StickerMediaProvider.openFile` SAF validation from
  `docs/AUDIT_2026-06-02.md` was merged into existing WS13 rather than added as
  a separate row.

## Local Evidence

- `FlorisLocale.kt:219-231` disables auto-space for `"jp"` but not `"ja"`;
  `LayoutScriptClassifier.kt:139` already classifies `"ja"` as Japanese, and
  `EditorInstance.kt:701` plus `KeyboardManager.kt:678,728` consume
  `primaryLocale.supportsAutoSpace`.
- `ClipboardInputLayout.kt:282-357` renders image/video clipboard thumbnails
  and the video overlay icon with `contentDescription = null`;
  `docs/AUDIT_2026-05-29.md:163-164` already records the missing clipboard
  media descriptions.
- `MimeTypeFilter.kt:31-127` documents wildcard-at-any-position behavior, prints
  compiled filters in the constructor, and leaves `matchesAll`, `matchesAny`,
  and `matchesOne` as documentation/test TODOs. `MimeTypeFilterTest.kt:23-124`
  currently covers only single-MIME `matches`.
- `Native.kt:39-46` decodes `buffer.array()` for heap-backed buffers, ignoring
  `position()`, `limit()`, and `arrayOffset()`, while the direct-buffer branch
  copies only `remaining()` bytes. `docs/AUDIT_2026-05-29.md:165-166` records
  the latent slice bug.

## External Evidence

- IANA Language Subtag Registry lists `Subtag: ja` / `Description: Japanese`
  and region `Subtag: JP` / `Description: Japan`, with no `Subtag: jp`:
  https://www.iana.org/assignments/language-subtag-registry/language-subtag-registry
- Android `Locale` docs recommend BCP 47 language tags for conforming locale
  strings: https://developer.android.com/reference/java/util/Locale
- Android Compose semantics docs state that semantic properties provide context
  to accessibility services and that `contentDescription` conveys icon/image
  meaning: https://developer.android.com/develop/ui/compose/accessibility/semantics
- AndroidX `MimeTypeFilter` documents platform-style whole-type/subtype
  wildcards, and Android `ClipDescription.compareMimeTypes` documents framework
  MIME pattern matching:
  https://developer.android.com/reference/androidx/core/content/MimeTypeFilter
  https://developer.android.com/reference/android/content/ClipDescription#compareMimeTypes(java.lang.String,java.lang.String)
- Android `ByteBuffer` docs describe `array()` / `arrayOffset()` and
  position-to-limit remaining content semantics:
  https://developer.android.com/reference/java/nio/ByteBuffer

## Roadmap Changes Fed

1. R4-1, P1: correct Japanese locale capability gates and pin them with tests.
2. R4-2, P3: add TalkBack descriptions for clipboard image/video history tiles.
3. R4-3, P3: pin `MimeTypeFilter` aggregate semantics and remove constructor
   stdout.
4. R4-4, P3: make `NativeStr.toJavaString()` honor ByteBuffer
   position/limit/arrayOffset.
5. WS13 sharpened with imported-sticker provider SAF allow-list validation.

## Non-Adds

- No new sync settings accessibility item was added; the radio-button semantics
  issue is already addressed in code.
- No separate sticker-provider row was added; the provider validation belongs
  with WS13's existing backup/restore/import path-safety device pass.
- No dependency-refresh or Android API row was added; those remain covered by
  the existing API 37 / Kotlin 2.4 follow-up.
- No source-code fix was attempted in this cycle; all changes are roadmap and
  research documentation only.
