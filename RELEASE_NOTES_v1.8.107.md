# Release v1.8.107 — voice "scratch" requires explicit list-anchor suffix

Date: 2026-05-17

Seventh-pass audit finding #16 from the voice subsystem agent. Closes a
silent destructive-action footgun in the REMOVE_ITEM_FROM_LIST voice
command parser.

## What changed

[`app/src/main/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceCommandParser.kt`](app/src/main/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceCommandParser.kt#L216)
— the `RemoveItemPatterns` list previously held one bare-prefix entry:

```kotlin
RemoveItemPattern(canonicalPhrase = "scratch", prefix = "scratch"),
```

Any utterance starting with the word "scratch" matched, with the rest
of the utterance becoming the argument. Concrete failure mode: the
user dictates natural prose like "let me scratch that idea" or "scratch
the previous note" — the parser silently fires
`REMOVE_ITEM_FROM_LIST` and the executor walks back through the
committed buffer deleting whatever it matches. Silent data loss with
no toast / no confirmation.

The fix replaces the bare-prefix entry with four explicit
prefix+suffix variants:

- `scratch <item> from list`
- `scratch <item> from the list`
- `scratch <item> off list`
- `scratch <item> off the list`

The shopping-list UX is preserved (anyone genuinely using "scratch"
for list editing still has the four disambiguated forms). The
attack surface — any utterance with "scratch" as the first word
silently destroying committed text — is gone.

## Tests

[`KeymanLdmlParserTest`](app/src/test/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceCommandParserTest.kt)
sibling — the existing `"parses 'scratch X' with single-word and
multi-word items"` test is updated to assert the new suffix-anchored
forms. A new regression guard test `"bare 'scratch X' (no suffix) no
longer triggers removal"` pins three previously-vulnerable inputs:

```kotlin
parser.parse("scratch apples") shouldBe null
parser.parse("scratch that idea") shouldBe null
parser.parse("scratch the previous note") shouldBe null
```

The `"rejects 'scratch' on its own with no item"` test continues to
pass — bare "scratch" still returns null, just for a different reason
(no pattern matches rather than blocked-argument rejection).

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceCommandParser.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/voice/VoiceCommandParserTest.kt`
- `gradle.properties` — versionCode 1907 / versionName 1.8.107

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.voice.VoiceCommandParserTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
```

The new regression-guard test should pass; the updated single-word /
multi-word test should pass with the new suffix-anchored forms.
