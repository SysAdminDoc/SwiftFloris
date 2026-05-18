# Release v1.8.105 — clipboard history honours incognito + ClipDescription.EXTRA_IS_SENSITIVE

Date: 2026-05-17

Two seventh-pass audit findings landed in one cohesive privacy slice:
the IME-local clipboard history was leaking through two distinct gates
that should always have suppressed.

## What changed

### EditorInstance: cut / copy gate on `isIncognitoMode`

[`app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/EditorInstance.kt`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/EditorInstance.kt#L521-L562)
— `performClipboardCut` and `performClipboardCopy` previously gated only
on `isPasswordField()` (which v1.8.86 extended to cover numeric-PIN
fields). They did NOT check `isIncognitoMode`.

Concrete failure: a user types in Signal (which sets
`IME_FLAG_NO_PERSONALIZED_LEARNING`, now forcibly honoured per v1.8.104),
the IME marks the field as `isIncognitoMode = true`, the dictionary
learn path correctly suppresses — but if the user selects text and hits
Cut, the selected text lands in the IME-local clipboard history. From
there it can be re-pasted into any other app via the clipboard palette,
bypassing the host-app's privacy declaration.

The fix unifies both gates into a single `shouldSuppressClipboardHistory()`
helper that returns true on either signal (password OR incognito). Both
cut and copy now read the helper.

### ClipboardManager: honour `ClipDescription.EXTRA_IS_SENSITIVE`

[`app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardManager.kt`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardManager.kt#L201-L227)
— `onPrimaryClipChanged` (the system-clipboard-to-IME-history sync path)
previously called `ClipboardItem.fromClipData` (which already parsed
`EXTRA_IS_SENSITIVE` into `ClipboardItem.isSensitive`) but then
unconditionally called `insertOrMoveBeginning(item)`. The flag was read,
not used.

Password managers (Bitwarden, 1Password, KeePassXC, Proton Pass) and
TOTP apps set `EXTRA_IS_SENSITIVE` on every copied credential. Before
this fix, every credential the user copied via the system clipboard
landed in SwiftFloris's IME-local history and could be re-pasted via the
clipboard palette — circumventing the password manager's own
copy-clear-after-N-seconds protection. The system clipboard's
auto-clear timer still ran, but the IME-local copy stayed forever.

The fix wraps the `insertOrMoveBeginning(item)` call in
`if (!item.isSensitive)`. The system clipboard still receives the clip
(SwiftFloris is not the source of truth for system-clipboard behaviour);
only the IME-local history skip is added.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/editor/EditorInstance.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/clipboard/ClipboardManager.kt`
- `gradle.properties` — versionCode 1905 / versionName 1.8.105

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:installDebug
```

Manual QA reproduction:

**Incognito-gate test (combines v1.8.104 + this release):**
- Open Signal (or any app whose editor sets `IME_FLAG_NO_PERSONALIZED_LEARNING`).
- Type a unique phrase you can recognise, select it, hit Cut on the IME action bar.
- Open the IME's clipboard palette (smartbar → clipboard).
- **Pre-fix:** the phrase appears as the most-recent clipboard entry.
- **Post-fix:** the phrase does NOT appear — only entries from non-incognito fields are retained.

**Sensitive-clip test (API 33+):**
- Install a password manager (Bitwarden / KeePassDX) and copy a
  credential to the clipboard.
- Open any text field, open the IME clipboard palette.
- **Pre-fix:** the credential appears in history (visible in plaintext
  unless `displayText()` redaction fires).
- **Post-fix:** the credential does not appear in history. The system
  clipboard still has it (the source app's auto-clear timer governs).
- On Android < 13 (no `EXTRA_IS_SENSITIVE`), behaviour is unchanged
  because the flag never gets set.
