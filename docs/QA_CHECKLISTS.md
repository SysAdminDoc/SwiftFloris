# SwiftFloris QA Checklists

Last updated: 2026-06-04 for v1.8.244.

Use these checklists when a release touches UI, IME behavior, setup, backup /
restore, import/export, resources, accessibility, release evidence, or anything
that users must verify on a device. Record skipped rows with the reason, not as
pass.

## Visual QA Matrix

Run the affected flow across the smallest matrix that covers the changed
surface. For keyboard or Settings layout changes, use the full matrix.

| Area | Required checks | Evidence |
|---|---|---|
| Phone portrait | Settings entry screen, affected nested screen, active IME in a normal text field | Screenshot or screen recording |
| Phone landscape | Active IME, Settings route if layout changed, candidate row, toolbar/smartbar overflow | Screenshot or screen recording |
| Compact keyboard | Key rows, candidate row, smartbar actions, long labels, backspace/enter/space state | Screenshot or note |
| Floating keyboard | Drag handle, resize handle, key hit targets, candidate row, clipboard/media panel if touched | Screenshot or note |
| Split/tablet layout | Split key rows, thumb reach zones, smartbar/candidate row, Settings pane width if touched | Screenshot or note |
| Dark theme | Affected screen/dialog/card plus keyboard surface if touched | Screenshot |
| SwiftKey High Contrast | Same affected screen/flow; verify non-color state indicators still read clearly | Screenshot |
| High font scale | Settings rows, dialogs, cards, buttons, extension/theme metadata, keyboard preview labels | Screenshot |
| Reduced animation | Animated glide trails, loading/progress states, dialogs that transition between states | Note or screen recording |
| RTL / long labels | Arabic/Hebrew or long localized labels when the change touches layout or string length | Screenshot or note |

Pass criteria:

- No text clipping, overlapping controls, truncated destructive copy, or hidden
  primary actions at the tested size.
- Buttons and chips keep stable dimensions; hover/pressed/disabled states do not
  shift surrounding layout.
- Icon-only controls remain understandable through content descriptions or
  visible context.
- Color is never the only signal for warning, error, success, progress,
  disabled, selected, or skipped states.

## Manual QA Flow

Run this flow for releases that touch behavior. For docs-only or pure utility
changes, record why the flow was not applicable.

1. Fresh launch Settings.
   - Open the app.
   - Confirm Settings reaches content without a crash or infinite splash.
   - Check the top-level route affected by the change.
2. Keyboard activation.
   - Enable the debug IME if needed.
   - Switch to SwiftFloris in a normal text field.
   - Type, backspace, enter, switch symbols, and switch subtype if relevant.
3. Field types.
   - Normal text field.
   - Password field.
   - Multiline field.
   - Field with suggestions/no-personalized-learning disabled.
   - Rich-content-capable app if clipboard, emoji, stickers, or media changed.
4. Trust-sensitive flows.
   - Backup create/cancel/failure if backup copy changed.
   - Restore merge/erase/cancel/failure if restore copy changed.
   - Dictionary import/export/save/delete if dictionary copy or behavior changed.
   - Extension import/export/edit/delete if extension copy or file behavior changed.
   - Language-pack delete/import if language-pack copy or state changed.
5. Accessibility.
   - Enable TalkBack for the affected screen or keyboard surface.
   - Verify traversal order, labels, result/status announcements, and custom
     actions.
   - Check high font scale for the affected screen.
6. Regression smoke.
   - Confirm no `FATAL EXCEPTION` / `AndroidRuntime` appears in recent logcat.
   - Confirm no new network permission is declared in the merged manifest.
   - Confirm generated files, crash logs, APKs, and reports are not staged.

## Release Evidence Checklist

Attach or record this evidence before publishing a release tag or PR.

- Exact commands run, including flags such as `--no-daemon`, `--max-workers`,
  selected tests, and heap limits.
- Result of `git diff --check`.
- Result of `bash scripts/check-fastlane-metadata.sh` when versionCode changes.
- Result of `bash scripts/check-repo-hygiene.sh` before staging release files.
- Focused unit/Robolectric/Kotest command for the changed behavior.
- Full local gate status or explicit reason it was skipped:
  `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`,
  `:app:verifyRoborazziDebug`.
- Manual QA rows completed from this document, with device/emulator model,
  Android version, orientation, theme, font scale, and input app.
- Screenshots or recordings for visual changes.
- Logcat excerpt or note that no crash signature was present.
- Version bump evidence: `gradle.properties`, `CHANGELOG.md`,
  `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`, `ROADMAP.md`,
  `COMPLETED.md`, and tag name.
- Remaining risk: device-gated rows, external-account work, skipped APK
  assembly, missing screenshot baselines, or maintainer-only checks.

Recommended evidence line format:

```text
Device: <model>, Android <version>, <orientation>, <theme>, font scale <value>
Flow: <screen/IME/app>
Result: PASS/FAIL/SKIPPED - <short reason>
Artifact: <screenshot path, recording path, logcat command, or N/A>
```
