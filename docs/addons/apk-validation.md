# SwiftFloris Addon APK Validation Contract

**Addon validation contract.** This document defines the universal validation contract every
SwiftFloris addon APK must pass before it can be enrolled at runtime by the IME's `AddonEnumerator`. It applies to
every addon type (dictionary-pack, theme-pack, layout-pack, popup-mapping-pack, language-pack) and to any future
native-bearing addon (whisper.cpp voice runtime, ONNX glide model, LiteRT-LM smart-compose, Bergamot translator,
librime CJK engine, MCP daemon).

The contract is intentionally enforceable at addon-release time using only the standard Android SDK (`zipalign`,
`aapt2`) plus Bash. Addon repos can copy [`scripts/verify-addon-apk.sh`](../../scripts/verify-addon-apk.sh) directly
into their own local release gate.

## Why a separate validation contract

SwiftFloris's base APK is already gated locally by the no-network check and the 16 KB native-library alignment guard.
But addons ship as **separate APKs from
separate repositories**: typically maintained by third parties such as language-pack authors, theme designers, or
runtime providers (whisper.cpp wrapper, Bergamot wrapper, etc.). When a user installs such an addon, they are
extending the keyboard's effective trust boundary without re-running the base APK's local gates.

The validation contract makes the requirements load-bearing on the addon side too:

- **16 KB native-library alignment.** Per Google Play's Nov 1 2025 cutoff for `targetSdk >= 35` apps shipping
  native libraries, every `.so` in an addon APK must be 16 KB ELF-segment aligned (page-size aligned for 16 KiB
  page Android devices). Misaligned native libs crash at load time on 16 KB Android. The base IME's check catches
  the case where SwiftFloris itself accidentally regresses; the addon-side check catches the case where an addon
  ships a 4 KB-aligned native lib that crashes only when SwiftFloris tries to load it via
  `System.load(/data/app/.../lib/arm64-v8a/libfoo.so)`.
- **Permission allowlist.** An addon must request only the platform permissions in
  `enrollment.allowedPermissions` in [`trust-capabilities.json`](../../app/src/main/config/trust-capabilities.json),
  or a SwiftFloris signature permission under the configured prefix. The IME's `AddonEnumerator`, the merged
  manifest gate, and this APK validator all consume that same policy. Network, SMS, Bluetooth, storage, and future
  unknown permissions therefore fail closed before publication.
  The merged base-APK gate also permits only its explicitly listed generated base-manifest permissions and prefixes;
  those base-only exceptions are never accepted for addon APKs.
- **REGISTER_ADDON receiver.** The addon must expose a broadcast receiver with the appropriate
  `REGISTER_*` intent action and the required meta-data keys.
- **Bundle size cap.** `AddonContract.ADDON_MAX_BUNDLE_BYTES = 64 MiB`. The enumerator rejects oversized addons;
  the release-time check fails the build instead of pushing a doomed APK to users.

## Validation checklist

Every addon APK is required to pass:

| Check | What | Rationale |
|---|---|---|
| Native-lib 16 KB alignment | `zipalign -c -P 16 -v 4 path/to/addon.apk` exits 0 | Crash-on-load avoidance on Android 15+ 16 KiB-page devices |
| Permission allowlist | `aapt2 dump permissions` contains only the permissions in `enrollment.allowedPermissions` or the configured SwiftFloris signature namespace | Runtime enrolment and release gates must agree |
| `REGISTER_ADDON` receiver present | `aapt2 dump xmltree` over `AndroidManifest.xml` shows at least one `<receiver>` carrying a `REGISTER_*` intent action from `AddonContract.Action` | Enrolment will silently no-op otherwise |
| Required meta-data keys | `addon.type`, `addon.version`, `addon.license`, `addon.descriptor` are all present on the receiver | `AddonEnumerator.evaluate` rejection cascade |
| Bundle size ≤ 64 MiB | `stat` reports `≤ 67_108_864` bytes | `AddonContract.ADDON_MAX_BUNDLE_BYTES` |
| Signing-cert SHA-256 captured | `apksigner verify --print-certs path/to/addon.apk` reports the SHA-256 fingerprint | Pinned by `AddonManifest.signingCertSha256` at first enrolment |

## Reproducing the validation locally

```bash
# Standard layout: addon repo's release APK at ./build/outputs/apk/release/addon-release.apk
./scripts/verify-addon-apk.sh path/to/your-addon.apk

# In-tree reference fixture:
./gradlew :addons:dictionary-pack-sample:assembleRelease
./scripts/verify-addon-apk.sh addons/dictionary-pack-sample/build/outputs/apk/release/dictionary-pack-sample-release.apk
```

The script exits non-zero on any failed check, with a human-readable line indicating which check failed and how to
remediate. It produces no output beyond the per-check status, so it composes cleanly in local release scripts:

```bash
./scripts/verify-addon-apk.sh ./build/outputs/apk/release/addon-release.apk
```

The script requires Android SDK build-tools `r33+` (for the `zipalign -P 16` flag), `aapt2`, `apksigner`,
and `unzip`. If the local script is copied into another addon repository, copy the trust-capabilities registry too
or set `SWIFTFLORIS_TRUST_CAPABILITIES` to the SwiftFloris registry path. A legacy zipalign fallback is accepted
only for APKs with no native libraries; native-bearing APKs fail closed when `-P 16` is unavailable.

## What this contract does not cover

- **Code quality / linting.** Not covered by this packaging contract; addon authors choose their own lint stack.
- **Functional correctness.** The contract validates the packaging, not the runtime behavior. A theme addon that
  passes every validation step can still ship a broken `Snygg` stylesheet that crashes at apply time. Functional
  tests are the addon author's responsibility.
- **License compatibility with the user's deployment.** A dictionary pack may be CC-BY-SA-licensed dataset wrapped
  in a GPL-3.0 APK; a user installing it onto an F-Droid SwiftFloris build is fine, but a user with a more
  restrictive license posture should read `AddonProvenanceReport.datasetLicenseSpdxId` /
  `AddonProvenanceReport.apkLicenseSpdxId` and decide for themselves.

See [`docs/addons/dictionary-pack-spec.md`](dictionary-pack-spec.md) for the dictionary-pack-specific descriptor +
asset layout that sits on top of this contract.
