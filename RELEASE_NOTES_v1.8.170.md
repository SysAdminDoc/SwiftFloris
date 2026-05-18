# SwiftFloris v1.8.170

Released: 2026-05-18

## Intent

Close the Workstream 10 keyboard preview-field placement and state-feedback review for Settings screens.

## What Changed

- The shared settings keyboard preview field now renders inside a distinct bottom surface with a top divider, tonal elevation, and stable horizontal padding.
- The preview field tracks focus and shows ready/active supporting text so the current test-input state is visible.
- Bottom-bar traversal ordering is applied to the preview surface, keeping accessibility order consistent with the shared settings scaffold.
- The keyboard-picker fallback now uses coroutine-safe toast feedback instead of the deprecated synchronous toast helper.

## Files Touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/lib/compose/PreviewKeyboardField.kt`
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

No permissions, dependencies, persisted data, or keyboard runtime behavior changed.
