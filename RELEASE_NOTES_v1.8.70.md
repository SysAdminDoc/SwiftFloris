# SwiftFloris v1.8.70

**Release date:** 2026-05-17
**Type:** Documentation / migration-window messaging

## What changed

- Refreshed the README front door for the SwiftKey migration window.
- Added a Samsung / Grammarly users callout:
  - Galaxy users on One UI 7+ can keep SwiftFloris as their default keyboard
    and invoke Galaxy AI Writing Assist through Samsung's selected-text UI when
    they intentionally want that separate Samsung layer.
  - Grammarly's Android support docs say the old Grammarly Keyboard for
    Android is being discontinued and replaced by Grammarly for Android, which
    integrates with any keyboard.
- Bumped README badges, Highlights caption, Recent releases, and footer status
  to v1.8.70.

## Sources checked

- Samsung support: `https://www.samsung.com/us/support/answer/ANS10000943/`
- SamMobile: `https://www.sammobile.com/news/one-ui-7-0-galaxy-ai-writing-tools-any-keyboard/`
- 9to5Google: `https://9to5google.com/2025/01/31/one-ui-7-galaxy-ai-writing-features-without-samsung-keyboard/`
- Grammarly support: `https://support.grammarly.com/hc/en-us/articles/25028519116429-Error-Grammarly-Assistant-is-not-enabled-right-now`

## Files touched

- `README.md`
- `gradle.properties`
- `ROADMAP.md`
- `ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md`
- `PROJECT_CONTEXT.md`
- `AGENTS.md`
- `.ai/research/2026-05-17/*` release/context artifacts

## Verification

- Documentation-only release; no app code, permissions, dependencies, or
  runtime behavior changed.
- `git diff --check`
- README/source-link inspection
- Attempted `.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`;
  blocked by the known VM issue: `JAVA_HOME` is not set and no `java` command
  is on PATH.
