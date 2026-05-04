# Suggestions Not Working - Troubleshooting Handoff

**Date**: 2026-05-04 10:35 UTC  
**Status**: 🔴 UNRESOLVED - Escalating to different model  
**Device APK**: v1.5.1 fresh installed (confirmed via adb)  
**Last Build**: 2026-05-04 10:20 (verified on device)

---

## Problem Statement

User reports that **spell suggestions and autocorrect completely broken**. Typing misspelled words shows NO suggestions despite:
- Settings all enabled (suggestion.enabled = true, emoji.suggestionEnabled = true)
- All code fixes in place (ANR fixes, async updates, caching)
- v1.5.1 fresh APK installed on device
- No compilation errors

User tested twice after install: "Tested, still no suggestions"

---

## What Has Been Done

### Investigation Steps Taken

1. ✅ **Version verification** - Confirmed v1.5.1 APK on device (timestamp 10:20)
2. ✅ **Code review** - All NlpManager fixes present:
   - runBlocking removed (converted to async scope.launch)
   - Word list caching implemented
   - Mutex-protected state updates
3. ✅ **Build verification** - Clean compilation, zero errors
4. ✅ **APK reinstall** - Complete uninstall/reinstall cycle
5. ✅ **Preference defaults** - suggestion.enabled defaults to true
6. 🔍 **Logic audit** - Fixed assembleCandidates() logic (clipboard was replacing spell)

### Fixes Applied

**Fix #1: assembleCandidates() logic (Commit d67fa68)**
- Problem: `.ifEmpty()` was hiding spell suggestions when clipboard had content
- Solution: Changed to `buildList { addAll(clipboard) + addAll(spell) }`
- Status: Deployed in v1.5.1

### What Didn't Work

- Rebuilding with correct version (1.3.0 → 1.5.1)
- Uninstall/reinstall (may have failed initially, now confirmed fresh)
- Logic fix in assembleCandidates()
- **Issue persists despite all fixes**

---

## Code Context

### Suggestion Pipeline (Current Understanding)

```
User types "teh"
    ↓
KeyboardManager.handleTextInput()
    ↓
resetSuggestions(content) [KeyboardManager.kt line XXX]
    ↓
nlpManager.suggest(subtype, content) [NlpManager.kt:204]
    ↓
getSuggestionProvider(subtype).suggest(...) [NlpManager.kt:219]
    ↓
internalSuggestions = emoji + spell [NlpManager.kt:228-231]
    ↓
Observable triggers assembleCandidates() [NlpManager.kt:84]
    ↓
activeCandidates emitted to UI [NlpManager.kt:316]
```

### Key Files & Sections

**NlpManager.kt**
- Line 204-235: `suggest()` method - puts emoji + spell into internalSuggestions
- Line 248-257: `clearSuggestions()` - async, uses Mutex
- Line 296-319: `assembleCandidates()` - combines all suggestions (JUST FIXED)
- Line 82-85: Observable on internalSuggestions triggers assembly
- Line 199-202: `isSuggestionOn()` returns if suggestions enabled

**KeyboardManager.kt**
- Line XXX: `resetSuggestions(content)` - calls nlpManager.suggest()
- Line XXX: Called from activeContentFlow collector

**AppPrefs.kt**
- `suggestion.enabled` - default = true
- `emoji.suggestionEnabled` - default = true

### Current Code (After d67fa68)

```kotlin
private fun assembleCandidates() {
    scope.launch {
        val candidates = when {
            isSuggestionOn() -> {
                buildList {
                    // Clipboard suggestions first
                    addAll(
                        clipboardSuggestionProvider.suggest(
                            subtype = Subtype.DEFAULT,
                            content = editorInstance.activeContent,
                            maxCandidateCount = 8,
                            allowPossiblyOffensive = !prefs.suggestion.blockPossiblyOffensive.get(),
                            isPrivateSession = keyboardManager.activeState.isIncognitoMode,
                        )
                    )
                    // Then add spell + emoji suggestions from internalSuggestions
                    internalSuggestionsGuard.withLock {
                        addAll(internalSuggestions.second)
                    }
                }
            }
            else -> emptyList()
        }
        activeCandidates = candidates
        autoExpandCollapseSmartbarActions(candidates, NlpInlineAutofill.suggestions.value)
    }
}
```

---

## Theories (Not Yet Validated)

### Theory A: Preferences Corrupted or Reset
- Fresh install should have defaults, but maybe SharedPreferences has stale data?
- Check if suggestion.enabled is actually TRUE at runtime

### Theory B: getSuggestionProvider() Returns Null/Empty Provider
- Maybe the spelling provider isn't initialized?
- `getSuggestionProvider(subtype)` might return a provider that has empty dictionaries

### Theory C: activeContentFlow Not Firing
- Maybe resetSuggestions() isn't being called at all?
- No keystroke detection?

### Theory D: Spell Suggestion Provider Issue
- LatinLanguageProvider.suggest() might be returning empty
- Dictionary not loaded
- Spell checker not functioning

### Theory E: Settings UI Shows Wrong State
- Preferences say "enabled" but something else is overriding it
- Provider forcing suggestions OFF

---

## Debugging Needs

### What Would Help

1. **Runtime logging** - Add Log.d() in:
   - `resetSuggestions()` - does it get called?
   - `suggest()` - what's returned from provider?
   - `assembleCandidates()` - what candidates are assembled?
   - `getSuggestionProvider()` - what provider is returned?

2. **Preference inspection** - Check at runtime:
   - What is prefs.suggestion.enabled.get() returning?
   - What is prefs.emoji.suggestionEnabled.get() returning?
   - What does isSuggestionOn() return?

3. **Provider inspection** - Check:
   - Is LatinLanguageProvider initialized?
   - Does its suggest() method return non-empty list?
   - Is the word "the" in its dictionary?

4. **UI inspection** - Check:
   - Is activeCandidatesFlow emitting updates?
   - Is CandidatesRow composable receiving candidates?
   - Is the bar rendering at all?

5. **Logcat output** - Full logcat during typing to see any exceptions

---

## Files to Review

```
app/src/main/kotlin/dev/patrickgold/florisboard/ime/nlp/
├── NlpManager.kt          ← Main suggestion engine
├── LatinLanguageProvider.kt    ← Spell checker
├── SuggestionProvider.kt   ← Interface
└── SuggestionCandidate.kt  ← Data class

app/src/main/kotlin/dev/patrickgold/florisboard/ime/keyboard/
├── KeyboardManager.kt      ← resetSuggestions() calls
├── EditorInstance.kt       ← activeContent tracking
└── SmartbarManager.kt      ← ?

app/src/main/kotlin/dev/patrickgold/florisboard/ime/smartbar/
├── CandidatesRow.kt        ← UI that displays suggestions
└── SmartbarUI.kt           ← Parent composable
```

---

## Device State

- **Device**: Android (debloated ROM, user has root)
- **FUTO Voice Input**: Installed (user integrated it)
- **APK version**: v1.5.1 (confirmed)
- **Build date**: 2026-05-04 10:20 UTC (confirmed)
- **App package**: dev.patrickgold.florisboard.debug

---

## Commands Available

```bash
# Check app version
adb shell dumpsys package dev.patrickgold.florisboard | grep -i version

# Check logcat
adb logcat -s FlorisBoard | grep -i suggest

# Dump preferences (if accessible)
adb shell sqlite3 /data/data/dev.patrickgold.florisboard/databases/florisboard.db \
  "SELECT * FROM preferences WHERE key LIKE '%suggestion%';"
```

---

## Next Steps For Different Model

1. Add detailed runtime logging to trace the exact execution path
2. Check what provider.suggest() is actually returning
3. Verify preferences are TRUE at runtime
4. Check if there's a quiet exception being caught somewhere
5. Consider if this is a timing issue (async vs UI updates)
6. Check if composable UI is even looking at activeCandidatesFlow

---

## Commits For Reference

- `d67fa68` - CRITICAL FIX: Combine clipboard and spell suggestions
- `9b79b3a` - Bump version to v1.5.1 with audit fixes
- `94ab37c` - Document version mismatch diagnosis and resolution
- `835d601` - Engineering audit pass: Fix critical ANR bugs

---

**Escalating to different reasoning model for fresh perspective.**
