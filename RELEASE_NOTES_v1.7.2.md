# SwiftFloris v1.7.2 — More NOW-tier closures (CI + haptics + popup polish)

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
