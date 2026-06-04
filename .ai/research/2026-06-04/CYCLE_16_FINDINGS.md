# Cycle 16 Findings - 2026-06-04

## Scope

- Repository: `SwiftFloris`
- Baseline: clean worktree at pushed `master` `caf6bea`
  (`docs: refresh cycle 15 research queue`), described as
  `v1.8.246-4-gcaf6bea`.
- Sync: `git pull --rebase` fast-forwarded the local checkout to the pushed
  Cycle 15 docs state before this cycle.
- Constraint: research/docs only. No feature source, tests, build files,
  manifests, assets, or generated outputs were edited.

## Anti-Duplicate Checks

- Did not duplicate R12-1, R13-1, or R14-1. Those rows already cover personal
  n-gram durability, stats/reset serialization, and TSV token safety.
- Did not duplicate R15-1. The Honeycomb parser diagnostic row remains the
  focused parser/degradation handoff.
- Did not reopen `switchToNextSubtype` / `switchToPrevSubtype`. The 2026-06-02
  audit records those fallback paths as fixed; this cycle isolates the still-open
  `switchToSubtypeById` double-read path.
- Did not add new platform/API rows for Android 17 subtype limits, Play target
  SDK policy, or 16 KB page-size enforcement. Existing roadmap/dependency rows
  already track API 37, native dependency, release, and device-gated work.
- Did not propose networked, closed-source, or incompatible-licensed keyboard
  features. Those remain outside `:app` invariants or existing addon/sibling-repo
  decision rows.

## Local Evidence

- `SubtypeManager.kt:276-278` snapshots `subtypes` inside `getSubtypeById(id)`.
- `SubtypeManager.kt:402-404` first checks `subtypes.any { it.id == id }`, then
  calls `getSubtypeById(id)!!`, which reads the subtype list again and asserts
  non-null.
- `SelectSubtypePanel.kt:83` calls `subtypeManager.switchToSubtypeById(it.id)`
  from the subtype chooser UI.
- `docs/AUDIT_2026-05-28.md:61-63` records the deferred TOCTOU/NPE finding and
  recommends reading the subtype once.
- `docs/AUDIT_2026-06-02.md:37` records the closed next/previous subtype fallback
  fix, not a closure for `switchToSubtypeById`.
- `app/src/test/.../ime/core` has focused subtype preset/classifier/per-app
  memory tests, but no visible switch-by-id regression test.

## External Landscape Sources Reviewed

1. Android `InputMethodSubtype` API: https://developer.android.com/reference/android/view/inputmethod/InputMethodSubtype
2. Android `InputMethodManager` API: https://developer.android.com/reference/android/view/inputmethod/InputMethodManager
3. Android `InputMethod` API: https://developer.android.com/reference/android/view/inputmethod/InputMethod
4. Android `InputConnection` API: https://developer.android.com/reference/android/view/inputmethod/InputConnection
5. Android `EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING`: https://developer.android.com/reference/android/view/inputmethod/EditorInfo#IME_FLAG_NO_PERSONALIZED_LEARNING
6. Android 17 behavior changes for all apps: https://developer.android.com/about/versions/17/behavior-changes-all
7. Google Play target API policy: https://developer.android.com/google/play/requirements/target-sdk
8. Android 16 KB page-size support: https://developer.android.com/guide/practices/page-sizes
9. Android security bulletin overview: https://source.android.com/docs/security/bulletin/asb-overview
10. Kotlin null safety and `!!`: https://kotlinlang.org/docs/null-safety.html
11. Kotlin `StateFlow`: https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-state-flow/
12. Kotlin `MutableStateFlow`: https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-mutable-state-flow/
13. Android Compose accessibility semantics: https://developer.android.com/develop/ui/compose/accessibility/semantics
14. Android package visibility: https://developer.android.com/training/package-visibility
15. Android custom permission element: https://developer.android.com/guide/topics/manifest/permission-element
16. Android `SigningInfo`: https://developer.android.com/reference/android/content/pm/SigningInfo
17. Android `Locale`: https://developer.android.com/reference/java/util/Locale
18. IANA Language Subtag Registry: https://www.iana.org/assignments/language-subtag-registry/language-subtag-registry
19. Unicode Emoji technical report: https://unicode.org/reports/tr51/
20. Unicode latest version page: https://www.unicode.org/versions/latest/
21. CLDR downloads: https://cldr.unicode.org/index/downloads
22. Unicode LDML keyboards: https://www.unicode.org/reports/tr35/tr35-keyboards.html
23. Keyman Android engine: https://help.keyman.com/developer/engine/android/
24. Keyman source repository: https://github.com/keymanapp/keyman
25. FlorisBoard v0.6.0-alpha02 release: https://github.com/florisboard/florisboard/releases/tag/v0.6.0-alpha02
26. HeliBoard releases: https://github.com/HeliBorg/HeliBoard/releases
27. AnySoftKeyboard releases: https://github.com/AnySoftKeyboard/AnySoftKeyboard/releases
28. FUTO Keyboard v0.1.29 release: https://github.com/futo-org/android-keyboard/releases/tag/0.1.29
29. FUTO Swipe dataset: https://huggingface.co/datasets/futo-org/swipe.futo.org
30. OpenBoard repository: https://github.com/openboard-team/openboard
31. AOSP LatinIME source: https://android.googlesource.com/platform/packages/inputmethods/LatinIME/
32. Rime input method engine: https://github.com/rime/librime
33. Mozc source repository: https://github.com/google/mozc
34. Mozc for Android F-Droid package: https://f-droid.org/en/packages/org.mozc.android.inputmethod.japanese/
35. F-Droid reproducible builds: https://f-droid.org/docs/Reproducible_Builds/
36. SQLCipher 4.16.0 release: https://www.zetetic.net/blog/2026/05/12/sqlcipher-4.16.0-release/
37. SQLCipher Android repository: https://github.com/sqlcipher/sqlcipher-android
38. AndroidX `MimeTypeFilter`: https://developer.android.com/reference/androidx/core/content/MimeTypeFilter

## Roadmap Changes Fed

- R16-1: Collapse subtype switch-by-id to a single nullable lookup. The
  implementation should avoid proving existence against one subtype-list
  snapshot and then force-unwrapping a second lookup from a later snapshot.
  Missing or concurrently removed subtype ids should no-op, while valid ids keep
  the current manual activation path.

## Rejected / Deferred Ideas

- Android 17 subtype-count validation: relevant, but no current live code path
  showed SwiftFloris approaching the 1500-subtype platform cap. Keep this under
  the existing API 37/Android 17 compatibility follow-up instead of adding a
  duplicate.
- HeliBoard/FUTO glide and gesture ideas: already represented by F9/F21 and
  device/ML-gated rows; no new in-tree feature should bypass the Apache-2.0,
  no-network, and no-closed-blob constraints.
- Keyman/Rime/Mozc engine integration: useful landscape context, but adding
  engines would introduce native/runtime scope or license/product decisions
  larger than a focused SwiftFloris build-machine item. Keep such work in
  external addon/sibling-repo decision lanes if it ever becomes active.

## Non-Adds

- No source fix was made in this cycle.
- No new permission, network, telemetry, export/import, or storage behavior was
  proposed.
- No product decision is required for R16-1; it is a local crash-hardening and
  test-coverage item.
