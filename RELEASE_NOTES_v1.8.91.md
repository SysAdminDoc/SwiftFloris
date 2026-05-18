# Release v1.8.91 — Addon spec docs: mandate REGISTER receiver

Date: 2026-05-17

Follow-up #6 from the v1.8.85 audit pass. Docs-only.

## What changed

[app/src/main/kotlin/dev/patrickgold/florisboard/ime/addon/AddonContract.kt](app/src/main/kotlin/dev/patrickgold/florisboard/ime/addon/AddonContract.kt#L28-L60)
KDoc previously said:

> 2. An addon APK *may* declare a broadcast `<receiver>` that responds to
>    [AddonContract.Action.REGISTER] …

This contradicted the actual visibility mechanism. The IME's
[AndroidManifest.xml `<queries>`](app/src/main/AndroidManifest.xml#L29-L58)
block declares intent-filter queries for the six `REGISTER_*` actions.
On Android 11+, package visibility under `<queries>` based on `<intent>`
only includes packages whose manifest components carry a matching
`<intent-filter>`. An addon declaring only `<meta-data>` on
`<application>` is therefore *invisible* to
`PackageManager.getInstalledPackages()` regardless of how cleanly it
otherwise conforms to the spec.

The script-side enforcement ([scripts/verify-addon-apk.sh](scripts/verify-addon-apk.sh#L134-L146))
and the public docs ([docs/addons/apk-validation.md](docs/addons/apk-validation.md))
already mandate a REGISTER receiver — only the KDoc in `AddonContract.kt`
was out of sync. Updated to:

> 2. An addon APK **MUST** declare a broadcast `<receiver>` whose
>    `<intent-filter>` matches one of the [AddonContract.Action] register
>    actions … This is a *visibility* requirement, not a feature
>    requirement … the receiver can be a no-op (it does not need to handle
>    the broadcast); the intent-filter alone satisfies the visibility query.

The receiver still *should* respond to the broadcast (so the addon can
self-announce changes without forcing the IME to poll), but the bare
minimum is just the intent-filter for visibility.

## Why this matters

Spec-compliant addons that followed only the previous (incorrect) "may
declare a receiver" wording would be silently invisible to the
enumerator on Android 11+ devices. No runtime symptom in `:app` —
they'd just never appear in the addon catalog. This release closes
the spec/implementation drift.

## Files touched

- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/addon/AddonContract.kt`
- `gradle.properties` — versionCode 1891 / versionName 1.8.91

## Verification

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
```

No behaviour change to `:app`. The verifyNoInternetPermissionMerged
checks (shipped in v1.8.85) and the addon enumerator still behave as
before. Existing addons that already declared a REGISTER receiver
(the canonical examples in `docs/addons/apk-validation.md` do so) are
unaffected.
