# SwiftFloris v1.8.169

Released: 2026-05-18

## Intent

Close the Workstream 10 empty-state polish item for settings and keyboard-adjacent surfaces that could still render blank, overly generic, or non-actionable states.

## What Changed

- Personal dictionary language detail views now show a specific empty state when the selected locale has no saved words, with the add-word action still available when editing is enabled.
- Extension category and language-pack manager empty states now use clearer import-focused copy instead of generic management labels.
- Theme manager now shows a recovery empty state if no theme components are available.
- Clipboard filtered history now explains when active filters match no clips, while the unfiltered empty clipboard copy mentions text, images, videos, and sensitive-field exclusions.

## Files Touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/dictionary/UserDictionaryScreen.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/localization/LanguagePackManagerScreen.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/theme/ThemeManagerScreen.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardInputLayout.kt`
- `app/src/main/res/values/strings.xml`
- `gradle.properties`
- `README.md`
- `ROADMAP.md`
- `IMPROVEMENT_PLAN.md`
- `PROJECT_CONTEXT.md`
- `ARCHITECTURE.md`
- `AGENTS.md`

## Verification

- `./gradlew.bat :app:compileDebugKotlin`
- `git diff --check`
- `./gradlew.bat :app:verifyNoInternetPermission :app:testDebugUnitTest :app:verifyRoborazziDebug :app:lintDebug :app:assembleDebug`
- `bash scripts/check-repo-hygiene.sh`

## Notes

No permissions, network surface, dependencies, or persisted data formats changed.
