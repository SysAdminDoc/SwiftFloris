# SwiftFloris v1.8.64 — 2026-05-17

Phase D1 — calendar quick-insert.

## Why ship this now

`SWIFTKEY_PARITY_ROADMAP_2026-05-17.md` tracks SwiftKey's calendar toolbar
tile as a productivity surface that can be implemented fully on-device. This
slice adds the Android-side hook without adding network access, accounts,
telemetry, or remote services.

The only new permission is `READ_CALENDAR`, and it is requested only after the
user explicitly taps the calendar quick action.

## What changed

### Calendar quick action

Added `QuickAction.InsertCalendarEvent` (`@SerialName("insert_calendar_event")`)
and registered it with `QuickActionJsonConfig`. New installs see the action in
the hidden quick-action editor pool, and the smartbar arrangement migration adds
it to existing users' hidden pool without forcing it into the visible row.

The action checks the local calendar permission. If already granted, it opens an
IME-local picker; if not, it launches `CalendarPermissionActivity`, requests
`READ_CALENDAR`, then opens the picker after the grant.

### Local agenda reader and picker

Added `CalendarAgendaReader`, backed by `CalendarContract.Instances`, to read
events from today through the next seven days. The reader runs off the main
thread through `CalendarQuickInsertManager`, filters completed / blank-title
rows, and caps the picker to 24 upcoming entries.

`CalendarAgendaPickerPanel` renders inside the keyboard window, so choosing an
agenda item can still commit through the active input connection. Selecting an
event inserts:

```text
<event title> - <localized date/time range>
```

All-day events are formatted from UTC dates to avoid shifting a midnight event
to the previous local day.

### Quick-action reachability

The hidden editor pool now includes both `InsertTask` and
`InsertCalendarEvent`. `QuickActionButton` also renders compact text labels for
task and calendar actions so optional smartbar slots are not blank.

## Tests

Added / updated unit coverage for:

- calendar window bounds for today + next seven days;
- same-day timed event formatting;
- cross-day timed event formatting;
- all-day and multi-day all-day UTC date handling;
- blank-title fallback labels;
- default quick-action availability and calendar-action JSON round-trip.

## Versioning

- `gradle.properties`: `projectVersionCode=1864`,
  `projectVersionName=1.8.64`.

## Verification

Local non-Java checks:

```powershell
git diff --check
rg -n "android.permission.INTERNET|ACCESS_NETWORK_STATE|ACCESS_WIFI_STATE|CHANGE_NETWORK_STATE|CHANGE_WIFI_STATE" app/src/main/AndroidManifest.xml app/src -g AndroidManifest.xml
```

The no-network permission scan returned no matches. This VM still has no JDK /
Android SDK on the path; a focused Gradle run failed with `JAVA_HOME is not set
and no 'java' command could be found in your PATH`. Run before merge on the main
Android build host:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Focused test targets once Java is available:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.calendar.CalendarAgendaFormatterTest
.\gradlew.bat :app:testDebugUnitTest --tests dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickActionArrangementTest
```

Manual device follow-up: verify the picker against at least one AOSP calendar
app and Google Calendar, with both permission-denied and permission-granted
paths.

## What's next

The remaining local-code SwiftKey-parity work is gated: B5 needs
human-captured trace fixtures, and Phase E needs optional addon runtimes for
Gemma / Bergamot / Rime. A3 still has Settings UI wiring for encrypted
dictionary export after the codec primitive shipped.
