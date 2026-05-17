# SwiftFloris v1.8.72

**Release date:** 2026-05-17
**Type:** Roadmap correction / glide-typing strategy

## What changed

- Reframed HeliBoard / NLnet open-glide integration as an additive future
  track instead of the primary production path.
- Marked SwiftFloris's shipped `StatisticalGlideTypingClassifier` path as the
  current production default while the open-glide library and permissive data
  release remain pending.
- Promoted the HeliBoard NLnet slip risk to the base-case planning assumption
  in the roadmap risk register and prioritization matrix.

This is the Tier-1 "HeliBoard NLnet slip-base-case plan" item from the
2026-05-17 prioritization matrix. No app code, permissions, dependencies, or
runtime behavior changed.

## Sources checked

- HeliBoard `#2226` issue:
  `https://github.com/HeliBorg/HeliBoard/issues/2226`
- HeliBoard latest release API / releases page:
  `https://github.com/HeliBorg/HeliBoard/releases/tag/v3.9`
- NLnet Gesture Typing project page:
  `https://nlnet.nl/project/GestureTyping/`
- HeliBoard gesture-data contribution wiki:
  `https://github.com/HeliBorg/HeliBoard/wiki/Tutorial:-How-to-Contribute-Gesture-Data`

## Files touched

- `gradle.properties`
- `README.md`
- `ROADMAP.md`
- `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`
- `PROJECT_CONTEXT.md`
- `AGENTS.md`
- `.ai/research/2026-05-17/*` release/context artifacts

## Verification

- Upstream check: GitHub API reports HeliBoard latest release `v3.9`,
  published 2026-03-29; issue `#2226` is still open and was last updated
  2026-05-11.
- NLnet page still describes the project as a separate open-source gesture
  library with a compatibility layer for AOSP-derived keyboards.
- HeliBoard wiki still asks contributors to collect gesture data using the
  current proprietary gesture library; it says the data collection period ends
  2026-11-30.
- `git diff --check`
- Android manifest banned-network-permission scan
- Attempted `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`;
  blocked by the known VM issue: `JAVA_HOME` is not set and no `java` command
  is on PATH.
