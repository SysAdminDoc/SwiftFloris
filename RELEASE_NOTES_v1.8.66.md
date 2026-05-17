# SwiftFloris v1.8.66 — 2026-05-17

N8.7 — EU AI Act Article 50 transparency surface.

## Why ship this now

`docs/PRIVACY_AND_AI.md` already defined the disclosure contract for
next-word prediction, glide typing, voice input, translation, and smart
compose, but the app did not yet expose that contract in first-run setup or
Settings. This release closes that gap before the 2026-08-02 Article 50
compliance horizon.

## What changed

### First-run explainer

Setup now starts with **Review local AI features** before asking Android to
enable the keyboard. The step lists the AI/ML surfaces, states the local-only
contract, and stores a one-time `internal__ai_features_explainer_seen`
acknowledgement before advancing to the normal enable/select/notification
steps.

The step is keyboard-disabled-safe because it is shown before the IME is
enabled.

### Reopenable About screen

Settings → About now includes **AI features in this keyboard**. The new screen
states the no-Internet/no-account/no-telemetry posture, lists each disclosed
surface, and links to:

- `docs/PRIVACY_AND_AI.md`
- `docs/THREAT_MODEL.md`
- `PROJECT_CONTEXT.md`

### Catalog guard

`AiFeatureDisclosureCatalog` owns the disclosed surface list used by the About
screen. `AiFeatureDisclosureCatalogTest` pins that the catalog covers the
first-run Article 50 set: next-word, glide typing, voice input, translation,
and smart compose.

## Tests

Added:

- `AiFeatureDisclosureCatalogTest`

## Versioning

- `gradle.properties`: `projectVersionCode=1866`,
  `projectVersionName=1.8.66`.

## Verification

Local non-Java checks:

```powershell
git diff --check
rg -n "android.permission.INTERNET|ACCESS_NETWORK_STATE|ACCESS_WIFI_STATE|CHANGE_NETWORK_STATE|CHANGE_WIFI_STATE" app/src/main/AndroidManifest.xml app/src -g AndroidManifest.xml
rg -n "aiFeaturesExplainerSeen|settings/about/ai-features|about__ai_features__title" app/src/main app/src/test
```

The no-network permission scan returned no matches. This VM still has no JDK /
Android SDK on the path; Gradle fails with `JAVA_HOME is not set and no 'java'
command could be found in your PATH`. Run before merge on the main Android build
host:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.app.settings.about.AiFeatureDisclosureCatalogTest
.\gradlew.bat :app:lintDebug :app:assembleDebug
```

## What's next

The next local-code candidates are the non-device-gated roadmap items in
`ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`; release tagging, F-Droid verified
rebuild, and real device benchmark numbers remain external-host/device-gated.
