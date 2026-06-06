# Cycle 22 Findings - 2026-06-06

## Cycle

`sync-pairing-trust-confirmation-recheck-2026-06-06`

## Scope

Resumed from the roadmap continuation state after Cycle 21. This pass focused
on Settings -> Sync pairing receive/generate behavior, paired-device persistence,
the payload/key model, sync tests, local threat-model notes, and official
public-key/device-ID pairing guidance.

## Files and sources reviewed

- `app/src/main/kotlin/dev/patrickgold/florisboard/app/settings/sync/SyncSettingsScreen.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/sync/PairingPayload.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/sync/PairingPayloadGenerator.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/sync/PairedSyncDevice.kt`
- `app/src/main/kotlin/dev/patrickgold/florisboard/ime/sync/SealedBoxCrypto.kt`
- `app/src/test/kotlin/dev/patrickgold/florisboard/ime/sync/SyncPairingUiModelTest.kt`
- `docs/THREAT_MODEL.md`
- Libsodium sealed boxes:
  https://doc.libsodium.org/public-key_cryptography/sealed_boxes
- Syncthing device IDs:
  https://docs.syncthing.net/v1.23.1/dev/device-ids.html

## Findings

- `SyncSettingsScreen.kt:120-132` saves a parsed pairing payload immediately:
  it builds `PairedSyncDevice.fromPayload(...)`, upserts `pairedDevicesJson`,
  and switches `channelId` before any user confirmation of the remote device,
  channel, or public key.
- `PairingPayload.kt:52-59` makes the payload-controlled public key, display
  name, and sync channel part of the QR/paste contract. `PairedSyncDevice.kt`
  copies those values directly into persisted paired-device state.
- `SyncSettingsScreen.kt:408-432` displays paired devices as name + channel
  only. There is no public-key fingerprint after saving, and no replacement
  warning if a payload reuses an existing device id with a different key or
  channel.
- `SyncPairingUiModelTest.kt:55-84` covers deterministic upsert/replacement and
  corrupt JSON tolerance, but there is no pending-confirmation, cancel-no-op, or
  duplicate-replacement contract test.
- `docs/THREAT_MODEL.md:179-188` already treats the X25519 public key in the
  pairing payload as load-bearing future transport state. The base APK has no
  network permission today, so this is a pre-transport trust-binding gap, not a
  claim of current network exposure.

## External-source effect

- Libsodium sealed boxes encrypt to a recipient public key and do not identify
  the sender without additional protocol data. That makes the user-visible
  pairing decision the point where SwiftFloris binds a human device to a public
  key.
- Syncthing's official device-ID docs describe device identity as derived from
  the public key and discuss confirmation UX for shortened identifiers. That is
  a close local-sync analogue for showing a short SwiftFloris fingerprint while
  retaining full-key verification in persisted state.

## Roadmap effect

Added R22-1 to `ROADMAP.md`: confirm scanned/pasted Sync pairing payloads before
saving remote devices.

## Acceptance shape

- Scanned and pasted payloads open a confirmation dialog/sheet before changing
  preferences.
- Confirmation shows display name, channel, device id, and a short grouped
  fingerprint derived from `pubkeyHex`.
- Cancellation leaves `pairedDevicesJson` and `channelId` unchanged.
- Confirming persists the device and labels same-device-id replacements with
  old vs new fingerprint/channel.
- Paired-device rows expose the fingerprint after saving.
- Focused tests cover valid confirm, cancel no-op, malformed no-op, and
  duplicate replacement behavior.

## Duplicate avoidance

- R19-1 remains the QR accessibility/copy/share fallback.
- R22-1 is narrower: it is about trust confirmation and public-key identity
  before the Sync control plane persists a remote recipient.
