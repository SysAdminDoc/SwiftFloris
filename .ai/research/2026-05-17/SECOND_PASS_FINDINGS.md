# Second-Pass Findings — 2026-05-17

Companion to the first-pass artifacts in this directory. This file
captures **deeper, source-verified evidence** for items the first pass
marked as thin, unverified, or "say so explicitly." Where the first pass
left a `⚠️` marker or a "could not verify" line, this pass closes it.

The second pass made **zero code changes**. New findings update the
recommendation set in
[../../../ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md](../../../ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md)
and the priority list in [PRIORITIZATION_MATRIX.md](PRIORITIZATION_MATRIX.md).

---

## 1. Source-code verification of the L1 / L2 / L7 facade contracts

The first pass left ROADMAP §8 L7 (MCP) as `⚠️ unverified` in
[MEMORY_CONSOLIDATION.md §2.6](MEMORY_CONSOLIDATION.md). Second pass:
opened the actual files and counted tests.

| Subsystem | Files in `app/src/main/kotlin/.../ime/<pkg>/` | Tests in `app/src/test/kotlin/.../ime/<pkg>/` | Verdict |
|---|---|---|---|
| **`mcp/`** (L7) | 13 (`AndroidMcpClient`, `DisabledDaemonSet`, `DisabledToolSet`, `McpAndroidDiscoverer`, `McpBridgeContract`, `McpClient`, `McpDaemonDiscoverer`, `McpDaemonRegistry`, `McpDispatchRouter`, `McpServiceConnectionManager`, `McpServiceLifecycle`, `McpTimeoutClient`, `McpToolCallEnvelope`) | 13 matching test files | ✅ ROADMAP claim "MCP advanced fastest: AIDL binder, Android client, discovery, service lifecycle, settings listing, and per-daemon enable/disable all shipped through v1.8.40" is fully accurate. `ACTION_BIND_MCP_DAEMON` constant verified at `McpBridgeContract.kt:49`. |
| **`smartcompose/`** (L1) | 13 (`AddonAuditExport`, `AddonConsentState`, `AddonInvocationAudit`, `NlpAddonHub`, `OptInAddonDispatcher`, `RewriteProvider`, `RewriteRouter`, `SensitiveFieldGuard`, `SmartComposeCache`, `SmartComposeContextWindow`, `SmartComposeProvider`, `SmartComposeResultFilter`, `SmartComposeRouter`) | 10 tests | ✅ Facade + cache + router stack shipped; `SmartComposeProvider.Default` returns `NoSuggestion` until an addon binds; gated on L1.1a LiteRT-LM addon. |
| **`translate/`** (L2) | 6 (`InlineTranslator`, `LanguageDetector`, `SentenceTokenizer`, `TranslationCache`, `TranslationLanguagePackManager`, `TranslationRouter`) | — (tests exist under cousin packages) | ✅ Facade + cache + router + language-pack manager shipped; `InlineTranslator.Default.translate(...)` returns `Unavailable` until Bergamot addon binds. |
| **`voice/`** (Next-2) | 11 (`StreamingVoiceTranscriptBuffer`, `VoiceCommandCustomization`, `VoiceCommandExecutor`, `VoiceCommandFallbackHandler`, `VoiceCommandParser`, `VoiceInputManager`, `VoiceInputSetupActivity`, `VoiceModelCatalog`, `VoiceModelInstallStore`, `VoiceModelSelection`, `VoiceRecognitionEngineSelection`) | 9 tests | ✅ FUTO handoff + Vosk streaming fallback + RAM-aware model selector all shipped per Next-2.1/2.2/2.3/2.4. |

The MEMORY_CONSOLIDATION marker has been updated to ✅ in this pass.

## 2. Tink migration recipe (closes ROADMAP_ADDENDUM §A.2)

The first pass recommended replacing `androidx-security-crypto:1.1.0-alpha06`
with Google Tink + AndroidKeystoreV1. Second pass verified the **exact
API, artifact, and code pattern**.

**Artifact:** `com.google.crypto.tink:tink-android:1.19.0` (latest on
Maven Central). Single artifact — the `integration.android` package
containing `AndroidKeysetManager` and `AndroidKeystoreKmsClient` is
bundled in `tink-android`; there is no separate `tink-android-cryptography`
artifact at the consumption layer.

**Canonical wrap pattern (KINTO Tech Blog-validated):**

```kotlin
class TinkClient(context: Context) {
    init { AeadConfig.register() }

    val aead: Aead = AndroidKeysetManager.Builder()
        .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
        .withSharedPref(context,
            "${context.packageName}.key_set",
            "${context.packageName}.pref_file")
        .withMasterKeyUri("android-keystore://tink_master_key")
        .build()
        .keysetHandle
        .getPrimitive(RegistryConfiguration.get(), Aead::class.java)

    fun encrypt(data: ByteArray) = aead.encrypt(data, null)
    fun decrypt(blob: ByteArray) = aead.decrypt(blob, null)
}
```

**Key facts from `AndroidKeystoreKmsClient.java`:**
- URI scheme: `"android-keystore://"` + opaque key-id.
- `AndroidKeystoreKmsClient.getOrGenerateNewAeadKey(uri)` returns an `Aead`
  directly — useful when wrapping a raw 64-byte SQLCipher passphrase
  without persisting a full Tink keyset.
- `AndroidKeysetManager.withSharedPref(...)` stores the Keystore-wrapped
  Tink keyset in plain `SharedPreferences`. Plain prefs is safe by design
  because the keyset is encrypted by the master key in the Keystore.
- Requires Android M (API 23+) for the Keystore-backed path. SwiftFloris
  is minSdk 26, so this is satisfied.

**Migration recipe (atomic, idempotent):**

```kotlin
fun migrateIfNeeded(context: Context) {
    val newPrefs = context.getSharedPreferences("secure_v2", MODE_PRIVATE)
    if (newPrefs.contains("sqlcipher_pp")) return            // already migrated

    val legacy = try {
        EncryptedSharedPreferences.create(...)               // old AndroidX path
    } catch (e: Throwable) { return }                        // nothing to migrate

    val raw = legacy.getString("sqlcipher_pp_legacy", null) ?: return
    val wrapped = Base64.encodeToString(
        tinkClient.encrypt(Base64.decode(raw, NO_WRAP)), NO_WRAP)

    newPrefs.edit()
        .putString("sqlcipher_pp", wrapped)
        .putInt("schema_version", 2)
        .commit()                                            // synchronous, atomic
    legacy.edit().remove("sqlcipher_pp_legacy").commit()
}
```

Idempotency gated on `newPrefs.contains(...)`. Atomicity via single
`commit()`.

**Test recipe:**
- Unit (JVM/Robolectric): register `FakeKmsClient` via `KmsClients.add(FakeKmsClient().withDefaultCredentials())` using URI prefix `"fake-kms://"`.
- Instrumentation: real `android-keystore://test_${UUID}` URI; teardown deletes via `AndroidKeystoreKmsClient().deleteKey(uri)`.

**Caveats:**
- Some OEM keystores (Samsung sub-API-28, Huawei) can lose keys on factory reset of secure hardware — keep an opt-in "re-derive from user PIN" fallback for users who hit this.
- `AndroidKeysetManager` is not thread-safe during `build()` — wrap construction in `synchronized` or eager-init in `Application.onCreate`.

**Sources:**
- https://github.com/tink-crypto/tink/blob/master/java_src/src/main/java/com/google/crypto/tink/integration/android/AndroidKeystoreKmsClient.java
- https://mvnrepository.com/artifact/com.google.crypto.tink/tink-android/1.19.0
- https://blog.kinto-technologies.com/posts/2025-06-16-encrypted-shared-preferences-migration-en/

## 3. FunctionGemma 270M concrete details (closes ROADMAP_ADDENDUM §A.7)

The first pass named FunctionGemma as the relevant model. Second pass:
fetched the canonical model cards.

| Property | Value |
|---|---|
| Canonical mobile card | `litert-community/functiongemma-270m-ft-mobile-actions` |
| Canonical base card | `google/functiongemma-270m-it` |
| Mobile artifact | `.litertlm` |
| Mobile quantization | **dynamic INT8 only** (FP16 / Q4 not shipped officially) |
| Mobile disk size | **288–289 MB** (revises the ROADMAP's "~135 MB" estimate from Gemma 3 270M Q4) |
| License | Gemma Terms of Use; commercial use permitted with attribution; redistribution permitted; HF login + click-through to download |
| Context length (mobile bundle) | **1,024 tokens** (capped vs. base's 32 K) |
| S25 Ultra benchmark | prefill 2,238 tok/s; decode 154.2 tok/s; TTFT 0.24 s; peak RAM 510 MB |
| Pixel 7 / Tensor G3 benchmark | Not published — could not verify |
| Sample Kotlin loading API | **Not yet published** — the mobile card says "No code snippets available yet"; Google directs to AI Edge Gallery for testing |

Function-calling JSON-schema shape (from base card):

```python
{ "type": "function",
  "function": {
    "name": "get_current_temperature",
    "description": "Gets the current temperature for a given location.",
    "parameters": {
      "type": "object",
      "properties": { "location": {"type":"string","description":"City"} },
      "required": ["location"] } } }
```

Output token sequence: `<start_function_call>call:name{p1:v1}<end_function_call>`.

**Implication for ROADMAP §8 L1:**
- The disk-size estimate of "~135 MB" in §8 L1 was for Gemma 3 270M Q4 INT4.
  FunctionGemma's official mobile bundle is **INT8 at 289 MB** — roughly
  2× the budget.
- Until Google publishes a Kotlin loading snippet (the `.litertlm` API
  is not yet stable in docs), the L1.1a addon must build against the
  AI Edge Gallery sample app as the reference implementation.
- Source: https://huggingface.co/litert-community/functiongemma-270m-ft-mobile-actions

**Sources:**
- https://huggingface.co/litert-community/functiongemma-270m-ft-mobile-actions
- https://huggingface.co/google/functiongemma-270m-it
- https://ai.google.dev/gemma/terms

## 4. ML Kit Digital Ink — root-cause F-Droid friction

The first-pass [SECURITY_AND_DEPENDENCY_REVIEW.md §4](SECURITY_AND_DEPENDENCY_REVIEW.md)
table marked ML Kit Digital Ink as "F-Droid distribution dependent." Second
pass: identified the **layered root cause**.

| Layer | Issue | F-Droid policy violated |
|---|---|---|
| 1 | `com.google.mlkit:digital-ink-recognition` is a **proprietary closed-source library** distributed under Google's ML Kit Terms | "FLOSS dependencies" rule on the artifact itself |
| 2 | Unbundled models stored in and fetched via **Google Play Services** | "Google Play Services and Firebase strictly forbidden in all applications" |
| 3 | `RemoteModelManager.download()` is a runtime network fetch | "applications must not download additional executable binary files without explicit user consent" |

The gating violation is **(1)+(2)** — even bundling the 20 MB models
locally wouldn't help because the recognizer library itself is proprietary.

**F-Droid-distributable alternatives:**
- **OSS CRNN handwriting models** (TF 2.x / TFLite / LiteRT) — runnable in
  an OSS app; accuracy below ML Kit for cursive but acceptable for printed
  script. ~10 MB packaged.
- **InkSight (Google Research, 2024)** — VLM for offline-to-online conversion;
  weights on HF but no Android-ready runtime yet.
- **`handwriting.cpp`** — could not verify as a maintained Android project.
- **Detexify** — LaTeX-symbol-specific only.

**Realistic 2026 path for Next-4.2a addon:** ship a small TFLite CRNN from
the `handwritten-text-recognition` GitHub topic, package ~10 MB in the
addon, run via LiteRT. Accept reduced accuracy as the cost of F-Droid
eligibility.

**Implication for ROADMAP §7 Next-4.2a:**
- The current ROADMAP frames Next-4.2a as "ML Kit Digital Ink in `addons/handwriting-mlkit/`."
- For F-Droid distribution this still fails. The addon must be **two SKUs** — a
  Play-Store-only `handwriting-mlkit` and an F-Droid-friendly `handwriting-tflite`
  using an OSS recogniser.
- Or the addon must be Play-Store-only and explicitly out-of-F-Droid (consistent
  with the `:app` Apache-2.0 ceiling not applying to addons).

**Sources:**
- https://f-droid.org/en/docs/Inclusion_Policy/
- https://developers.google.com/ml-kit/vision/digital-ink-recognition
- https://github.com/topics/handwriting-recognition

## 5. Bergamot — the real Android distributable is `DavidVentura/firefox-translator`

The first pass treated `browsermt/bergamot-translator` as the upstream
target. Second pass: **upstream has no Android assets** and never has.

| Project | Status | Android consumability |
|---|---|---|
| `browsermt/bergamot-translator` | Latest tag **v0.4.5 (June 2022)**; C++/WASM oriented; npm `@browsermt/bergamot-translator` last 0.4.9 ~3 years ago; project alive but no first-party Android product | **None** — no AAR, no JNI bindings, no Android build instructions |
| `DavidVentura/firefox-translator` | The **real Android distributable**. JNI (`bergamot.cpp` C++ adapter + `NativeLib.kt` Kotlin wrapper). F-Droid + GitHub. **20 bidirectional + 7 one-way language pairs** from `mozilla/firefox-translations-models` (now retired in favor of `mozilla/translations`). ~40 MB per model, gzipped. ~15 MB APK (12 MB shared libs); ~100 ms first-load, 5–80 ms per translation | **No AAR published** — SwiftFloris must fork the JNI layer |

**Implication for ROADMAP §8 L2.1a:**
- The §8 L2 description "Bergamot WASM runtime addon" is misleading — the
  Bergamot Android path is **not WASM**, it's a **C++ JNI** wrapper around
  `marian-decoder`. Update the L2 prose.
- `DavidVentura/firefox-translator` should be the **explicit upstream
  reference** the L2.1a addon forks from, not `browsermt/bergamot-translator`.
- ML Kit-equivalent F-Droid friction does NOT apply to Bergamot — it's
  fully OSS (MPL-2.0) and reproducible.

**Quality benchmark:** Mozilla's own evaluations put Bergamot within
~3–5 BLEU of Google Translate for EN↔ES/FR/DE on general text;
degrades on idiomatic and low-resource languages. No fresh 2026
independent head-to-head benchmark located.

**Sources:**
- https://github.com/browsermt/bergamot-translator/releases
- https://blog.davidv.dev/posts/mobile-translator/
- https://github.com/DavidVentura/firefox-translator
- https://github.com/mozilla/firefox-translations-models

## 6. Android 17 IME visibility — concrete migration

The first pass confirmed Android 17 changes IME visibility on config change.
Second pass: captured the **exact behavior** and the IME-side response.

**Verbatim from `behavior-changes-all`:**

> Beginning with Android 17, when the device's configuration changes (for
> example, through rotation), and this is not handled by the app itself,
> the previous IME visibility is not restored.

**Migration paths for HOST apps (not SwiftFloris):**

1. `android:windowSoftInputMode="stateAlwaysVisible"` on the activity.
2. Call `InputMethodManager.showSoftInput(...)` in `Activity.onCreate()`.
3. Handle `onConfigurationChanged()` explicitly.

**Implication for SwiftFloris** (the IME itself):

- `InputMethodService.onConfigurationChanged()` does **not auto-restart**
  the keyboard view across rotation when the host doesn't handle the config
  change. v1.8.45 already shipped the Android 17 IME-visibility-restore
  fix per release notes — second-pass spot-check did not contradict this.
- Best practice for 2026 IMEs: in `onStartInputView`, idempotently call
  `requestShowSelf(...)` when the connection indicates the prior state was
  visible. Rely on `EditorInfo.initialSelStart/End` rather than persisted
  state across config changes.
- **Related Android 17 change** (running-activity behavior): system no
  longer restarts activities for `CONFIG_KEYBOARD`, `CONFIG_KEYBOARD_HIDDEN`,
  `CONFIG_NAVIGATION`, `CONFIG_UI_MODE` changes — these now flow via
  `onConfigurationChanged`. IMEs should be ready for more
  `onConfigurationChanged` traffic without `onCreate` cycling.
- **Accessibility pane changes:** Android 17 enhances pane-change reporting
  via `setAccessibilityPaneTitle` — for an IME this means the candidates
  strip, the toolbar pane, and the glide-typing overlay should each carry
  a pane title so TalkBack announces pane transitions.

No new IME-specific `IME_FLAG_*` constants in Android 17 were verifiable
from official docs in this pass.

**Sources:**
- https://developer.android.com/about/versions/17/behavior-changes-all
- https://developer.android.com/about/versions/17/behavior-changes-17

## 7. F-Droid Reproducible Builds — exact 2026 process

The first pass recommended a CI "build twice, compare" job. Second pass:
captured the **exact verification surface and YAML directives**.

| Item | Value |
|---|---|
| Rebuilder service | **`verification.f-droid.org`** |
| Per-app status URL | `https://verification.f-droid.org/packages/<appId>/` |
| Per-APK JSON | `<applicationId>_<versionCode>.apk.json` |
| Badge ✔️ | Rebuilder reproduced upstream APK (signature transplanted) |
| Badge 💔 | Rebuild did not match; `diffoscope` output published alongside |
| Required YAML directives in `metadata/<appId>.yml` | `Binaries:` (URL template), `AllowedAPKSigningKeys:` (signing-cert SHA-256), and standard `Builds:` stanza |

Toolchain pinning:

- AGP / Gradle pinned via `gradle.properties` + `gradle-wrapper.properties` (already in tree).
- NDK pinned via `ndkVersion` in `build.gradle` (already pinned to `29.0.14206865`).
- JDK pinned via `compileOptions` / `kotlin.jvmToolchain(17)` (effectively in tree via the existing JDK 17 pin).

Common 2026 failure modes:

- ZIP entry timestamps (resolved by AGP ≥ 7.2.2 — SwiftFloris is on 9.0.0; fine).
- Embedded baseline profile timestamps.
- VCS info embedding (AGP ≥ 8.3 — must disable via
  `androidComponents { onVariants { ... vcsInfo.enabled = false } }`).
  **Recommended check for SwiftFloris** — this is potentially flipping the
  rebuilder badge to 💔 because the project's `BUILD_COMMIT_HASH`
  buildConfigField is non-deterministic across runs.
- R8 nondeterminism on different CPU counts.
- Non-deterministic PNG crushing — disable `cruncherEnabled`.
- Kotlin compiler version skew between developer and rebuilder.

**Implication for ROADMAP §6 N12.5 (Reproducible-build self-verification CI):**

- Add an explicit `vcsInfo.enabled = false` check or
  `androidComponents { onVariants { ... vcsInfo.enabled = false } }` to
  `app/build.gradle.kts`.
- The current `buildConfigField("String", "BUILD_COMMIT_HASH", ...)` is
  **non-deterministic** across runs (different worktrees = different
  commits) — this is acceptable because the commit hash is the canonical
  binding between the APK and the source tag. F-Droid's rebuilder runs
  against the same commit so this should reproduce. Re-verify on the
  first rebuild.

**Sources:**
- https://f-droid.org/en/docs/Reproducible_Builds/
- https://verification.f-droid.org

## 8. SCOWL → ESDB transition + wordfreq sunset (closes the SCOWL line in NOTICE)

The first pass took the project's bundled SCOWL 2020.12.07 reference
at face value. Second pass: the SCOWL distribution itself has **moved
forward** since.

| Asset | First-pass assumption | Second-pass truth |
|---|---|---|
| **SCOWL** | "BSD-like, current" | **SCOWL 2026.02.25** is current; SCOWL format is being **replaced by ESDB (English Speller Database)** — a SQLite database (`scowl.db`) plus `scowl.txt`. Old SCOWL artifacts/scripts will stop being maintained. NOTICE references 2020.12.07 — **5+ years stale** |
| **wordfreq** (rspeer) | Apache-2.0 + CC-BY-SA, viable | **In sunset.** Latest v3.0.2 (2022). `SUNSET.md` notes the project is "unlikely to be updated again." Data snapshot ~2021. Still usable as a stable historical corpus |
| **HermitDave/FrequencyWords** | MIT + CC-BY-SA, 61 langs | Last major content drops 2016 / 2018. No 2024-2026 refresh. CC-BY-SA share-alike applies to bundled data file (not to SwiftFloris's code) |
| **Leipzig Corpora Collection** | n/a | CC-BY-NC-SA — kills commercial keyboard distribution |
| **CC-100 / OSCAR** | n/a | License fragmented per language; **best pure-permissive option for non-English Zipf overlays** |
| **OPUS / OpenSubtitles raw** | n/a | Varies per sub-corpus; usable to regenerate frequencies with attention to subtitle copyright |

**Recommendations:**

- **NEW ROADMAP item — refresh SCOWL bundle.** Either update to
  SCOWL 2026.02.25 (keep current format) or migrate to ESDB
  (SQLite `scowl.db`). 5-year freshness gain. New `NOTICE` line for
  ESDB attribution.
- **NEW ROADMAP item — non-English Zipf overlays.** ROADMAP §6 B1
  plans cs/de/es/fr/it/pt overlays "from a curated CC-licensed phrase
  corpus." Concrete source: **CC-100** for permissive multilingual
  word-frequency derivation; HermitDave/FrequencyWords as quick
  bootstrap if CC-BY-SA share-alike on the data file is acceptable.

**Sources:**
- https://wordlist.aspell.net/news/
- https://github.com/en-wl/wordlist
- https://github.com/rspeer/wordfreq/blob/master/SUNSET.md
- https://github.com/hermitdave/FrequencyWords

## 9. New ROADMAP items surfaced by the second pass

These promote into [ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md](../../../ROADMAP_RESEARCH_ADDENDUM_2026-05-17.md)
on the next refresh. Capturing here so they're not lost:

| # | Item | Tier | Source |
|---|---|---|---|
| 1 | **Refresh bundled SCOWL** 2020.12.07 → 2026.02.25 (or migrate to ESDB) | NOW (§6 N7-class — data hygiene) | §8 above |
| 2 | **Bergamot upstream reference correction** — name `DavidVentura/firefox-translator` as the L2.1a fork target, not `browsermt/bergamot-translator` | NEXT (§8 L2 correction) | §5 above |
| 3 | **ML Kit Digital Ink two-SKU plan** — keep `addons/handwriting-mlkit/` for Play Store; add `addons/handwriting-tflite/` for F-Droid using an OSS CRNN | NEXT (§7 Next-4.2a expansion) | §4 above |
| 4 | **`vcsInfo.enabled = false` in `app/build.gradle.kts`** for F-Droid reproducibility | NOW (§6 N6 follow-up) | §7 above |
| 5 | **Update §8 L1 disk-size estimate** — FunctionGemma INT8 is 289 MB, not 135 MB | §8 L1 correction | §3 above |
| 6 | **CC-100 as the non-English Zipf-overlay source** (replaces "curated CC-licensed phrase corpus" vague language in §6 B1) | NOW (§6 B1 concretization) | §8 above |
| 7 | **Tink artifact pin to `1.19.0`** alongside the §A.2 migration | NOW (§6 N7.6 from the addendum, concretized) | §2 above |

---

## 10. What this second pass did NOT cover

Still on the future-pass backlog:

- Open `app/src/main/assets/ime/dict/data.json` (SCOWL bundle) for a
  fresh size + content audit. First and second pass both deferred this.
- Run `:app:dependencies` and diff against the version-catalog pins —
  transitive surface not surveyed.
- Verify the LiteRT-LM 0.11.0 GA claim — only 0.10.x confirmed
  externally; 0.11.0 was claimed in the ROADMAP appendix but not
  confirmed.
- Inspect `.github/workflows/*.yml` actions versions against latest
  stable (e.g. `actions/checkout@v4` is current; the project pins
  `gradle/actions/setup-gradle@v4` which is current).
- Open `app-release-v1.5.2.apk` (9.7 MB) and run `apkanalyzer` /
  `apksigner verify --print-certs` to confirm the signing-cert
  fingerprint published in the README — first pass took it on faith.
- Survey the 32 `RELEASE_NOTES_v1.8.*` files for any feature that
  shipped without a corresponding ROADMAP commit (cross-validate that
  ROADMAP §3 "Recently Shipped" actually covers the v1.8.x stream).
