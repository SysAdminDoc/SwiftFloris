# Fifth-Pass Findings — 2026-05-17

**Scope:** same-day re-run of the autonomous research prompt after the
fourth pass. This pass did not touch app source, build files, release
notes, assets, or workflow YAML. It corrects stale dependency guidance,
adds one newly surfaced competitor, and promotes the root roadmap to a
small `v5.3` delta without mechanically rewriting the historical body.

**Local state at start:** clean worktree, `master...origin/master [ahead 47]`,
HEAD `e62ba34` (`docs: research run 2026-05-17 — fourth pass: README
catch-up + PRIVACY_AND_AI.md + subsystem inspection`). Latest local tag
still `v1.8.40`; HEAD release metadata is `v1.8.58`. `java` is not on
PATH in this VM, so Gradle verification still belongs on the maintainer's
build host.

---

## 1. Corrections to earlier 2026-05-17 research

### 1.1 `androidx-activity 1.13.0` is stable

The prior `SECURITY_AND_DEPENDENCY_REVIEW.md`, `PROJECT_CONTEXT.md`, and
`ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md` treated `androidx-activity
1.13.0` as an RC and recommended downgrading to `1.12.4`. That is now
wrong.

AndroidX Activity release notes show `1.13.0` was released on
2026-03-11, and Maven metadata reports `release=1.13.0`.

**Roadmap effect:** remove the downgrade from Bump-batch A. Keep the
current pin unless tests reveal a repo-specific regression.

Sources:
- https://developer.android.com/jetpack/androidx/releases/activity
- https://dl.google.com/dl/android/maven2/androidx/activity/activity-compose/maven-metadata.xml

### 1.2 `androidx-security-crypto 1.1.0` exists, but the API is still deprecated

The prior wording said "no stable 1.1.0 will ever ship." That is wrong:
`androidx.security:security-crypto:1.1.0` shipped on 2025-07-30.

The material finding still holds. AndroidX release notes say all APIs
were deprecated in favor of platform APIs and direct Android Keystore use
starting in the 1.1.0 alpha/beta line, and SwiftFloris is still pinned to
older `1.1.0-alpha06` in `app/build.gradle.kts`.

**Roadmap effect:** keep the Tink / Android Keystore migration, but state
the reason precisely: not "no stable exists," but "the stable exists and
the API is deprecated."

Sources:
- https://developer.android.com/jetpack/androidx/releases/security
- https://dl.google.com/dl/android/maven2/androidx/security/security-crypto/maven-metadata.xml
- https://repo.maven.apache.org/maven2/com/google/crypto/tink/tink-android/maven-metadata.xml

### 1.3 Dependency latest-stable refresh

Live Maven metadata on 2026-05-17 changed several earlier version targets:

| Pin | Repo pin | Fifth-pass latest stable / release | Roadmap action |
|---|---:|---:|---|
| AGP | 9.0.0 | 9.2.1 by Google Maven metadata; official 9.2 notes page documents the 9.2 baseline | Bump as a later toolchain slice after Roborazzi/Robolectric and R8 rules audit |
| Compose BOM | 2026.03.01 | 2026.05.00 | Bump with toolchain slice |
| Activity | 1.13.0 | 1.13.0 | Keep |
| KSP | 2.3.5 | 2.3.8 | Bump in low-risk dep batch |
| Coroutines | 1.10.2 | 1.11.0 | Bump in low-risk dep batch |
| AboutLibraries | 14.0.1 | 14.2.0 stable; 15.0.0-a02 preview | Bump to 14.2.0, not alpha |
| ZXing Core | 3.5.3 | 3.5.4 | Bump |
| Roborazzi | 1.55.0 | 1.60.0 | Bump before removing CI `continue-on-error` |
| Robolectric | 4.14.1 | 4.16.1 | Bump with Roborazzi |
| SQLCipher Android | 4.16.0 | 4.16.0 | Keep |
| Tink Android | not direct | 1.21.0 | Use as target for security migration |

Sources:
- https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml
- https://developer.android.com/build/releases/gradle-plugin
- https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/maven-metadata.xml
- https://repo.maven.apache.org/maven2/com/google/devtools/ksp/com.google.devtools.ksp.gradle.plugin/maven-metadata.xml
- https://repo.maven.apache.org/maven2/io/github/takahirom/roborazzi/roborazzi/maven-metadata.xml
- https://repo.maven.apache.org/maven2/org/robolectric/robolectric/maven-metadata.xml

## 2. New competitor surfaced: LeanType

LeanType (`LeanBitLab/LeanType`) is an active HeliBoard fork positioned
as an AI-enhanced keyboard. GitHub shows `v3.7.9` published on
2026-05-17. Its README advertises three APK lines:

- Standard: cloud AI providers and `INTERNET`.
- Offline: no `INTERNET`, offline ONNX proofreading/translation, manual
  gesture/AI model setup.
- Offline Lite: no `INTERNET`, no AI features.

This is strategically important even though LeanType is GPL-3.0 and
cannot be copied into `:app`: it validates the market split SwiftFloris
already chose architecturally, but SwiftFloris's stronger version is the
base APK with no `INTERNET` plus opt-in signed addon APKs instead of
separate app flavors.

Roadmap implication: add LeanType to the competitor matrix and track its
offline SKU as a reference for onboarding copy, model-loading UX, and the
claim that "offline AI keyboard" users exist.

Sources:
- https://github.com/LeanBitLab/LeanType
- https://github.com/LeanBitLab/LeanType/releases/tag/v3.7.9

## 3. External facts re-confirmed

- Microsoft Support still states SwiftKey Accounts retire on
  2026-05-31, with backup/sync moving to Microsoft Account + OneDrive.
- FlorisBoard upstream remains on latest stable `v0.5.2` from
  2025-11-28.
- HeliBoard remains latest `v3.9` from 2026-03-29.
- FUTO Keyboard latest is `0.1.28` from 2026-05-04; release notes include
  clipboard-image history, 16 KB page-size support, CJK/Vietnamese work,
  and removal of transformer finetuning because it was not stabilized.
- Android's 16 KB page-size requirement remains a live Play requirement
  for updates targeting Android 15+ with native code.
- MediaPipe LLM Inference on Android/iOS is deprecated; LiteRT-LM remains
  the correct mobile target.
- Bergamot and librime remain viable addon targets by license and shape:
  Bergamot is MPL-2.0, librime core is BSD-3-Clause and has Android
  frontends such as Trime/fcitx5-android.

Sources:
- https://support.microsoft.com/en-us/topic/account-a3c38581-903f-4d22-a388-cc13c7debf0e
- https://github.com/florisboard/florisboard
- https://github.com/HeliBorg/HeliBoard
- https://github.com/futo-org/android-keyboard/releases/tag/0.1.28
- https://developer.android.com/guide/practices/page-sizes
- https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference
- https://github.com/browsermt/bergamot-translator
- https://github.com/rime/librime

## 4. Updated dependency-roadmap batches

Recommended batches after this fifth pass:

1. **Bump-batch A — low risk:** coroutines `1.10.2 -> 1.11.0`, KSP
   `2.3.5 -> 2.3.8`, ZXing `3.5.3 -> 3.5.4`, AboutLibraries
   `14.0.1 -> 14.2.0`. No Activity downgrade.
2. **Bump-batch B — visual/test infrastructure:** Roborazzi
   `1.55.0 -> 1.60.0`, Robolectric `4.14.1 -> 4.16.1`, then capture
   baselines and remove `continue-on-error` from the CI Roborazzi step.
3. **Bump-batch C — Android toolchain:** AGP `9.0.0 -> 9.2.x`, Compose
   BOM `2026.03.01 -> 2026.05.00`; inspect AGP 9.2 R8
   `-keepattributes` behavior and runtime-invisible annotation stripping
   before merging.
4. **Security migration:** replace `androidx.security:security-crypto`
   usage with direct Tink / Android Keystore wrapper for the SQLCipher
   passphrase. Target Tink Android `1.21.0` unless a later stable exists
   at implementation time.

## 5. Root roadmap v5.3 delta

The root `ROADMAP.md` was patched as `v5.3` with a short fifth-pass
refresh section. It preserves the existing v5.2 historical body and
records only the material deltas above: Activity downgrade retired,
Security Crypto wording corrected, dependency targets refreshed, LeanType
added, and the toolchain-batch order updated.

## 6. Self-audit

| Completion criterion | Fifth-pass status |
|---|---|
| Required artifact set still present | Pass: all required files remain present under `.ai/research/2026-05-17/`; `CONTINUE_FROM_HERE.md` remains unnecessary because no hard limit stopped the run |
| Local repo reconnaissance | Pass: clean worktree, branch-ahead count, tag lag, Java absence, workflow state, and dependency pins rechecked |
| Memory consolidation | Pass: no new AI-instruction file conflicts found; this file adds corrections to the same canonical run |
| External research multiple passes | Pass: live source refresh across AndroidX, Google Maven, Maven Central, competitor repos, platform docs, SwiftKey, FUTO, LiteRT, Bergamot, librime |
| Source saturation tested | Pass: new findings are corrections/new competitor only; no additional category revealed a new roadmap pillar |
| Roadmap updated | Pass: `ROADMAP.md` now has a v5.3 fifth-pass section; `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md` has a fifth-pass override |
| Verification | Partial: markdown/source checks only; Gradle not run because `java` is not on PATH in this VM |

