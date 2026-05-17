#!/usr/bin/env python3
"""
ROADMAP §7 Next-11.1 + SwiftKey parity C3 — bundled theme regen.

Takes the well-tested swift_slate.json baseline (~500 lines, full Snygg
selector coverage) and re-skins it with palettes for Nord, Tokyo Night,
Dracula, Catppuccin Mocha, "SwiftKey Pure (M3E)", SwiftKey High Contrast,
and Aurora Animated. Most themes only change the @defines block; high
contrast also overrides a few key / popup rules so alt-glyphs and key
boundaries keep visible outlines.

Idempotent: re-running overwrites the generated files. Theme entries in
extension.json are managed manually (see surrounding doc comment).
"""

from __future__ import annotations

import json
import pathlib
from typing import Dict


REPO_ROOT = pathlib.Path(__file__).resolve().parents[1]
THEME_ROOT = REPO_ROOT / "app/src/main/assets/ime/theme/org.florisboard.themes/stylesheets"
BASELINE = THEME_ROOT / "swift_slate.json"

# Bundled token palettes. We keep the original variable names so the 400+
# downstream selectors don't need touching; the M3E layer is reflected in the
# values (expanded role hues, container tones) and in the shape overrides at
# the end of each palette.
PALETTES: Dict[str, Dict[str, str]] = {
    # Nord — frost + polar night, light variant.
    "m3e_nord_light": {
        "--primary": "#5e81ac",
        "--primary-variant": "#4c6f96",
        "--secondary": "#b48ead",
        "--secondary-variant": "#8e6f8a",
        "--background": "#eceff4",
        "--background-variant": "#e5e9f0",
        "--surface": "#d8dee9",
        "--surface-variant": "#c8d0dc",
        "--popup-surface": "#e5e9f0",
        "--focused-popup-surface": "#d0d8e6",
        "--drag-marker": "#bf616a",
        "--spacer-color": "rgba(46, 52, 64, 0.18)",
        "--one-hand-background": "#dfe5ee",
        "--one-hand-foreground": "#2e3440",
        "--incognito-icon-color": "#2e344012",
        "--on-primary": "#eceff4",
        "--on-background": "#2e3440",
        "--on-background-disabled": "#2e344050",
        "--on-surface": "#2e3440",
        "--on-surface-variant": "#4c566a",
    },
    "m3e_nord_dark": {
        "--primary": "#88c0d0",
        "--primary-variant": "#5e81ac",
        "--secondary": "#b48ead",
        "--secondary-variant": "#8e6f8a",
        "--background": "#2e3440",
        "--background-variant": "#343a48",
        "--surface": "#3b4252",
        "--surface-variant": "#434c5e",
        "--popup-surface": "#434c5e",
        "--focused-popup-surface": "#4c566a",
        "--drag-marker": "#bf616a",
        "--spacer-color": "rgba(216, 222, 233, 0.22)",
        "--one-hand-background": "#353b48",
        "--one-hand-foreground": "#eceff4",
        "--incognito-icon-color": "#ffffff12",
        "--on-primary": "#2e3440",
        "--on-background": "#eceff4",
        "--on-background-disabled": "#eceff450",
        "--on-surface": "#e5e9f0",
        "--on-surface-variant": "#d8dee9",
    },
    # Tokyo Night — enkia/tokyo-night palette, night variant.
    "m3e_tokyo_night": {
        "--primary": "#7aa2f7",
        "--primary-variant": "#5d7fcc",
        "--secondary": "#bb9af7",
        "--secondary-variant": "#9d76d9",
        "--background": "#1a1b26",
        "--background-variant": "#1f2030",
        "--surface": "#24283b",
        "--surface-variant": "#2c3148",
        "--popup-surface": "#2c3148",
        "--focused-popup-surface": "#363b54",
        "--drag-marker": "#f7768e",
        "--spacer-color": "rgba(192, 202, 245, 0.22)",
        "--one-hand-background": "#1f2030",
        "--one-hand-foreground": "#c0caf5",
        "--incognito-icon-color": "#ffffff12",
        "--on-primary": "#1a1b26",
        "--on-background": "#c0caf5",
        "--on-background-disabled": "#c0caf550",
        "--on-surface": "#c0caf5",
        "--on-surface-variant": "#a9b1d6",
    },
    # Dracula — dracula/dracula palette.
    "m3e_dracula": {
        "--primary": "#bd93f9",
        "--primary-variant": "#9b75dc",
        "--secondary": "#ff79c6",
        "--secondary-variant": "#cf60a0",
        "--background": "#282a36",
        "--background-variant": "#2e3142",
        "--surface": "#343746",
        "--surface-variant": "#44475a",
        "--popup-surface": "#3a3d4c",
        "--focused-popup-surface": "#44475a",
        "--drag-marker": "#ff5555",
        "--spacer-color": "rgba(248, 248, 242, 0.22)",
        "--one-hand-background": "#2e3142",
        "--one-hand-foreground": "#f8f8f2",
        "--incognito-icon-color": "#ffffff12",
        "--on-primary": "#282a36",
        "--on-background": "#f8f8f2",
        "--on-background-disabled": "#f8f8f250",
        "--on-surface": "#f8f8f2",
        "--on-surface-variant": "#bfbfbf",
    },
    # Catppuccin Mocha — flavours/mocha palette.
    "m3e_catppuccin_mocha": {
        "--primary": "#cba6f7",
        "--primary-variant": "#a987d3",
        "--secondary": "#f5c2e7",
        "--secondary-variant": "#cf9bc3",
        "--background": "#1e1e2e",
        "--background-variant": "#181825",
        "--surface": "#313244",
        "--surface-variant": "#45475a",
        "--popup-surface": "#3a3c4d",
        "--focused-popup-surface": "#45475a",
        "--drag-marker": "#f38ba8",
        "--spacer-color": "rgba(205, 214, 244, 0.22)",
        "--one-hand-background": "#252535",
        "--one-hand-foreground": "#cdd6f4",
        "--incognito-icon-color": "#ffffff12",
        "--on-primary": "#1e1e2e",
        "--on-background": "#cdd6f4",
        "--on-background-disabled": "#cdd6f450",
        "--on-surface": "#cdd6f4",
        "--on-surface-variant": "#a6adc8",
    },
    # SwiftKey Pure (M3E) — neutral surface stack tuned for the M3 Expressive
    # surface-container hierarchy. Light + Dark variants.
    "m3e_swiftkey_pure_light": {
        "--primary": "#1976d2",
        "--primary-variant": "#1565c0",
        "--secondary": "#5e6b78",
        "--secondary-variant": "#4a555f",
        "--background": "#f5f6f8",
        "--background-variant": "#eceef2",
        "--surface": "#ffffff",
        "--surface-variant": "#e6e9ee",
        "--popup-surface": "#ffffff",
        "--focused-popup-surface": "#e6e9ee",
        "--drag-marker": "#d32f2f",
        "--spacer-color": "rgba(30, 35, 40, 0.20)",
        "--one-hand-background": "#eceef2",
        "--one-hand-foreground": "#1e2328",
        "--incognito-icon-color": "#1e232812",
        "--on-primary": "#ffffff",
        "--on-background": "#1e2328",
        "--on-background-disabled": "#1e232850",
        "--on-surface": "#1e2328",
        "--on-surface-variant": "#4a555f",
    },
    "m3e_swiftkey_pure_dark": {
        "--primary": "#82b1ff",
        "--primary-variant": "#4f86d3",
        "--secondary": "#b0bec5",
        "--secondary-variant": "#90a4ae",
        "--background": "#101216",
        "--background-variant": "#16191f",
        "--surface": "#1c1f25",
        "--surface-variant": "#262a31",
        "--popup-surface": "#1c1f25",
        "--focused-popup-surface": "#262a31",
        "--drag-marker": "#ef9a9a",
        "--spacer-color": "rgba(225, 230, 240, 0.22)",
        "--one-hand-background": "#16191f",
        "--one-hand-foreground": "#e1e6f0",
        "--incognito-icon-color": "#ffffff12",
        "--on-primary": "#0a1322",
        "--on-background": "#e1e6f0",
        "--on-background-disabled": "#e1e6f050",
        "--on-surface": "#e1e6f0",
        "--on-surface-variant": "#9aa6b3",
    },
    # SwiftKey parity C3/P15 — explicit AAA-targeted high-contrast palette.
    # Every text/background pair used by the keyboard is intentionally either
    # white-on-near-black or black-on-bright-yellow, so ThemeContrastTest can
    # pin WCAG AAA instead of the existing AA floor.
    "swiftkey_high_contrast": {
        "--primary": "#ffd400",
        "--primary-variant": "#e6bf00",
        "--secondary": "#00e5ff",
        "--secondary-variant": "#00b8d4",
        "--background": "#000000",
        "--background-variant": "#080808",
        "--surface": "#101010",
        "--surface-variant": "#1a1a1a",
        "--popup-surface": "#000000",
        "--focused-popup-surface": "#1a1a1a",
        "--drag-marker": "#ff3b30",
        "--spacer-color": "rgba(255, 255, 255, 0.42)",
        "--one-hand-background": "#000000",
        "--one-hand-foreground": "#ffffff",
        "--incognito-icon-color": "#ffffff24",
        "--on-primary": "#000000",
        "--on-background": "#ffffff",
        "--on-background-disabled": "#ffffff80",
        "--on-surface": "#ffffff",
        "--on-surface-variant": "#ffffff",
    },
    # SwiftKey parity C3/P14 — static Snygg palette paired with the runtime
    # GenericShape morph in AuroraAnimatedThemeBackground.
    "aurora_animated": {
        "--primary": "#7dd3fc",
        "--primary-variant": "#38bdf8",
        "--secondary": "#c084fc",
        "--secondary-variant": "#a855f7",
        "--background": "#07111f",
        "--background-variant": "#0b1729",
        "--surface": "#172033",
        "--surface-variant": "#22324f",
        "--popup-surface": "#111c32",
        "--focused-popup-surface": "#21304d",
        "--drag-marker": "#f472b6",
        "--spacer-color": "rgba(226, 232, 240, 0.26)",
        "--one-hand-background": "#0b1729",
        "--one-hand-foreground": "#eff6ff",
        "--incognito-icon-color": "#ffffff14",
        "--on-primary": "#06121f",
        "--on-background": "#eff6ff",
        "--on-background-disabled": "#eff6ff55",
        "--on-surface": "#f8fafc",
        "--on-surface-variant": "#cbd5e1",
    },
}

# Shape tokens common to generated bundled themes. The chip shape moves off
# the pill/stadium (50%) to a 12dp corner per the project's no-pill rule.
SHAPE_TOKENS = {
    "--shape": "rounded-corner(12dp, 12dp, 12dp, 12dp)",
    "--shape-variant": "rounded-corner(16dp, 16dp, 16dp, 16dp)",
    "--shape-chip": "rounded-corner(12dp, 12dp, 12dp, 12dp)",
}

RULE_OVERRIDES: Dict[str, Dict[str, Dict[str, str]]] = {
    "swiftkey_high_contrast": {
        "key": {
            "border-color": "var(--on-surface)",
            "border-width": "1dp",
            "shadow-elevation": "0dp",
        },
        "key:pressed": {
            "border-color": "var(--primary)",
            "border-width": "2dp",
        },
        "key[code=10]": {
            "border-color": "var(--on-primary)",
            "border-width": "1dp",
        },
        "key[code=10]:pressed": {
            "border-color": "var(--on-primary)",
            "border-width": "2dp",
        },
        "key-hint": {
            "foreground": "var(--on-surface)",
        },
        "key-popup-box": {
            "border-color": "var(--on-surface)",
            "border-width": "1dp",
            "shadow-elevation": "0dp",
        },
        "key-popup-element:focus": {
            "border-color": "var(--primary)",
            "border-width": "2dp",
        },
        "inline-autofill-chip": {
            "border-color": "var(--on-surface)",
            "border-width": "1dp",
        },
    },
}


def build_defines(palette: Dict[str, str]) -> Dict[str, str]:
    """Return a fully-populated @defines block by overlaying [palette] on shapes."""
    combined: Dict[str, str] = {}
    combined.update(palette)
    combined.update(SHAPE_TOKENS)
    return combined


def main() -> None:
    if not BASELINE.is_file():
        raise SystemExit(f"baseline missing: {BASELINE}")

    baseline = json.loads(BASELINE.read_text(encoding="utf-8"))
    written = 0
    for theme_id, palette in PALETTES.items():
        sheet = json.loads(json.dumps(baseline))  # deep copy
        sheet["@defines"] = build_defines(palette)
        for rule, properties in RULE_OVERRIDES.get(theme_id, {}).items():
            sheet.setdefault(rule, {}).update(properties)
        out = THEME_ROOT / f"{theme_id}.json"
        # Stable formatting so subsequent runs are clean no-ops on git.
        out.write_text(
            json.dumps(sheet, indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )
        written += 1
    print(f"Wrote {written} bundled theme stylesheets to {THEME_ROOT}")


if __name__ == "__main__":
    main()
