# SwiftFloris Dictionary-Pack Addon Specification

**ROADMAP §7 Next-10.3.** This document defines the exact APK + manifest +
descriptor shape an external dictionary-pack addon must take to be enrolled
by the SwiftFloris IME at runtime. It is the foundation that the bundled
Polish (2025 baseline) addon — and every future language pack — will follow.

The contract is intentionally minimal: a dictionary pack is just an APK
whose `assets/` directory contains a `.fldic` word list (and optionally a
Zipf TSV overlay) and whose `AndroidManifest.xml` advertises a single
broadcast receiver carrying the IME's REGISTER_DICTIONARY_PACK intent
filter + a descriptor JSON resource.

## 1. AndroidManifest.xml

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.swiftfloris.dictpack.pl">

    <uses-permission android:name="dev.patrickgold.florisboard.permission.REGISTER_ADDON" />

    <application
        android:hasCode="false"
        android:label="Polish Dictionary for SwiftFloris">

        <!-- Static enrolment receiver: the IME's AddonEnumerator scans for
             this intent filter via PackageManager#queryBroadcastReceivers
             and reads the meta-data values below to decide whether to mount
             this addon. The receiver is exported because the IME (a
             different package) must be able to see it; the
             signature-protected permission keeps random apps from sending
             unsolicited REGISTER_* broadcasts. -->
        <receiver
            android:name=".DictionaryPackReceiver"
            android:enabled="true"
            android:exported="true"
            android:permission="dev.patrickgold.florisboard.permission.REGISTER_ADDON">
            <intent-filter>
                <action android:name="dev.patrickgold.florisboard.action.REGISTER_DICTIONARY_PACK" />
            </intent-filter>

            <!-- Required meta-data, all read by AddonEnumerator.snapshot(). -->
            <meta-data
                android:name="dev.patrickgold.florisboard.addon.type"
                android:value="dictionary-pack" />
            <meta-data
                android:name="dev.patrickgold.florisboard.addon.version"
                android:value="1" />
            <meta-data
                android:name="dev.patrickgold.florisboard.addon.license"
                android:value="Apache-2.0" />
            <!-- @raw/dict_descriptor must point to the JSON spec below. -->
            <meta-data
                android:name="dev.patrickgold.florisboard.addon.descriptor"
                android:resource="@raw/dict_descriptor" />
        </receiver>
    </application>
</manifest>
```

**Banned permissions.** A dictionary-pack APK MUST NOT declare any of
`INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`,
`CHANGE_NETWORK_STATE`, or `CHANGE_WIFI_STATE`. The
`AddonEnumerator.snapshot()` rejection path silently skips any pack that
declares one of these (logged via `flogInfo` so a future Settings →
Addons → "Why was X rejected?" surface can replay the decision). This
keeps the §1 no-network philosophy intact even when the user installs
third-party addons.

**Universal addon APK validation contract.** Beyond the banned-permission
list, every addon APK must also satisfy the 16 KB native-library
alignment requirement and the bundle-size / signing-certificate / receiver
checks documented in [`apk-validation.md`](apk-validation.md). Addon repos
should adopt [`scripts/verify-addon-apk.sh`](../../scripts/verify-addon-apk.sh)
as a CI gate so failures surface before publication rather than at user
enrolment time.

## 2. Descriptor JSON (`res/raw/dict_descriptor.json`)

```json
{
  "schema": 1,
  "language": "pl",
  "displayName": "Polish (2025 baseline)",
  "wordCount": 320000,
  "fldicAssetPath": "ime/dict/pl.fldic",
  "zipfAssetPath": "freq/pl.tsv",
  "source": "OpenSubtitles 2024 + Wiktionary",
  "license": "CC-BY-SA-4.0",
  "minSchemaCompat": 1
}
```

| Field | Required | Notes |
|---|---|---|
| `schema` | yes | Currently `1`. Bump only on load-bearing layout changes. |
| `language` | yes | Lowercased ISO 639-1 code (e.g. `pl`, `de`, `fr`, `pt`). |
| `displayName` | yes | Shown in Settings → Addons → Installed packs. |
| `wordCount` | yes | Total word count (display only). |
| `fldicAssetPath` | yes | Relative path inside the addon APK's `assets/` to the `.fldic`. |
| `zipfAssetPath` | no | Relative path to the Zipf TSV overlay; omit when none exists. |
| `source` | yes | One-line provenance string. |
| `license` | yes | SPDX license id of the *dataset* (may differ from APK code license). |
| `minSchemaCompat` | no | Defaults to `1`. Older IMEs must support a schema `<= minSchemaCompat` to enrol. |

## 3. assets/

The `.fldic` file follows the existing FlorisBoard frequency-dict format:

```
#~schema: https://schemas.florisboard.org/nlp/v0~draft1/fldic.txt
#~encoding: utf-8

[words]
ten	1000
nie	987
to	980
... etc

[ngrams]
1,2	500
... optional
```

When a `zipfAssetPath` is declared, the `.tsv` must contain one
`word\tzipf` line per entry (per the rspeer/wordfreq Zipf scale, range
[1, 8]). The IME's `ZipfFrequencyTable.parse(...)` (ROADMAP §7 Next-3.2)
handles this format directly.

## 4. Routing

At runtime, when the user has a Polish subtype active and a compatible
addon pack is enrolled, the IME's `LatinDictionaryStore.dictionaryForLanguage("pl")`
call **prefers the addon-supplied dictionary** over any bundled
`assets/ime/dict/pl.fldic` shipped inside the base APK. Both files
together would be merged at the snapshot level so the addon's word list
augments rather than blanks out the bundled baseline.

The enrolment pipeline is read-only: the IME never writes back into the
addon's APK. As of v1.8.83, IME startup uses `AddonEnumerator` plus
`AddonRegistryStartup` to reconcile the PackageManager snapshot into
process-live addon state, keep first-seen signing certificate pins, reject
changed-certificate package hijacks, publish `AddonRegistryStore`, and clean
malformed stored pin lines; `DictionaryPackCatalog` then validates the
descriptor JSON and produces provenance rows for Settings.
The next loader slice mounts the addon's `assets/` via the standard
[`PackageManager#getResourcesForApplication`](https://developer.android.com/reference/android/content/pm/PackageManager#getResourcesForApplication(java.lang.String))
+ `AssetManager` flow — no extraction, no temp-file copy, no permission
escalation.

## 5. Signing certificate pinning

`AddonManifest.signingCertSha256` is captured at first enrolment and
pinned. If the addon's signing certificate changes between IME launches
(package-name hijack attempt), the addon is silently skipped on
subsequent enumerations. To rotate signing keys an addon author should
publish a new package-name rather than re-signing under the same name.

As of v1.8.82, the persisted pin format is implemented by
`AddonSigningPinSet` and stored at `prefs.addon.signingCertPins` as one
`packageName=SHA-256` entry per line. The raw preference is not meant to be
user-edited; Settings should expose provenance plus revoke/reset actions once
the Addons screen lands. As of v1.8.83, startup writes back the canonical pin
string only when first-seen addons or malformed stored lines change the trust
set.

## 6. Reference implementation

A minimal reference dictionary-pack project will live at
`addons/dictionary-pack-polish/` in a sibling repo once the Polish
dataset extraction lands. Until then, the descriptor + manifest layout
documented here is fully sufficient to build a working pack against the
current IME (`v1.8.83+`). Validation can be exercised in unit tests via
`DictionaryPackDescriptor.parse(rawJson)` and `DictionaryPackCatalog.build(...)`
— see `DictionaryPackDescriptorTest` and `DictionaryPackCatalogTest`.
