# Release v1.8.98 — generate_icon.py portability

Date: 2026-05-17

Follow-up F1 from the [v1.8.85 audit roster](RELEASE_NOTES_v1.8.85.md#follow-up-work-next-per-feature-releases).

## What changed

[`generate_icon.py`](generate_icon.py) — the output path was a
hard-coded Windows absolute path:

```python
output_path = r'C:\Users\--\repos\SwiftFloris\SwiftFloris_icon_new.png'
```

with the original maintainer's home directory redacted. The script
worked only on a single host and silently failed (or, worse, wrote to
some unrelated `C:\Users\--\` if the directory happened to exist) on
every other machine.

Replaced with:

```python
output_path = Path(__file__).resolve().parent / "SwiftFloris_icon_new.png"
```

so the icon now lands next to the script regardless of caller CWD or
host OS. Added a module docstring explaining the script's purpose.

## Files touched

- `generate_icon.py`
- `gradle.properties` — versionCode 1898 / versionName 1.8.98

## Verification

```bash
python3 generate_icon.py
# Expect: "✓ Icon generated: <repo>/SwiftFloris_icon_new.png"
```

The icon is not consumed by the build or by CI; it's a maintainer
artifact for one-off branding work, so no further verification is
needed.
