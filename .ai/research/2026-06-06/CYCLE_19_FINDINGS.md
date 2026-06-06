# Cycle 19 Findings - 2026-06-06

## Cycle

`sync-pairing-accessibility-recheck-2026-06-06`

## Scope

Rechecked the local sync pairing surface after Cycle 18 added product/trust
rows. This pass focused only on whether the existing Sync UI gives users a
non-visual, no-camera way to move the pairing payload between devices.

## Files and sources reviewed

- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/sync/SyncSettingsScreen.kt`
- `app/src/main/res/values/strings.xml`
- `docs/ACCESSIBILITY.md`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/sync/SyncPairingUiModelTest.kt`
- Android Compose semantics docs:
  https://developer.android.com/develop/ui/compose/accessibility/semantics

## Findings

- `SyncSettingsScreen.kt:229-233` generates a serialized `PairingPayload`, but
  `SyncSettingsScreen.kt:278-279` passes it only into `SyncQrPayloadCard`.
- `SyncSettingsScreen.kt:358-383` renders status text, a custom QR `Canvas`,
  and summary copy. There is no copy/share action for the raw payload and no
  explicit semantic description on the custom canvas.
- `SyncSettingsScreen.kt:207-214` and `SyncSettingsScreen.kt:318-327` expose
  the paste dialog only as a fallback when scanner launch fails on the receiving
  device. A user who cannot use visual QR scanning still needs an explicit
  source-side copy/share route.
- `docs/ACCESSIBILITY.md` already requires semantics for dynamic Settings
  surfaces, and Android's Compose guidance treats semantics as the mechanism
  for giving custom components enough meaning for accessibility services.

## Roadmap effect

Added R19-1 to `ROADMAP.md`: add accessible copy/share fallback to the Sync QR
flow. This is not a new network/sync transport; it reuses the existing local
pairing payload and keeps transport user-chosen.

## Acceptance shape

- Generated pairing state offers localized "Copy payload" and "Share payload"
  actions.
- The QR card has a useful TalkBack announcement without reading raw JSON by
  default.
- Copy/share copy states that the payload contains no private key.
- Receiving paste is directly available, not only after scanner failure.
- Accessibility docs include a no-camera/TalkBack pairing check.

## Duplicate avoidance

- Did not reopen R3-3 sealed-box vectors; pairing payload movement is separate
  from sync envelope cryptography.
- Did not reopen the persisted URI grant fixes already present in
  `SyncSettingsScreen`.
- Did not duplicate R18-4 privacy evidence dashboard; this is a specific Sync
  pairing accessibility gap.

