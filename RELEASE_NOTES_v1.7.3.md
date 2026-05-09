# SwiftFloris v1.7.3 — Release pipeline + a11y audit

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
  RELEASE_NOTES_v1.7.3.md, ROADMAP.md).
