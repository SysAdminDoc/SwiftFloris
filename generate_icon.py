#!/usr/bin/env python3
"""Regenerate the SwiftFloris app-icon PNG next to this script.

Maintainer-only helper. Output lands at `SwiftFloris_icon_new.png` in
the same directory as this script regardless of the caller's CWD, so
running `python3 generate_icon.py` from anywhere produces a writable,
predictable artifact path. Replaces the previous hard-coded Windows
path that worked only on a single maintainer's host.
"""

from pathlib import Path
from PIL import Image, ImageDraw

# Create a 512x512 image with transparency
img = Image.new('RGBA', (512, 512), (0, 0, 0, 0))
draw = ImageDraw.Draw(img)

# Define gradient colors (teal to emerald)
teal = (20, 184, 166)      # #14B8A6
emerald = (16, 185, 129)   # #10B981

# Create gradient background circle
center_x, center_y = 256, 256
radius = 200

# Draw radial gradient (circle)
for r in range(radius, 0, -1):
    ratio = r / radius
    r_val = int(teal[0] * ratio + emerald[0] * (1 - ratio))
    g_val = int(teal[1] * ratio + emerald[1] * (1 - ratio))
    b_val = int(teal[2] * ratio + emerald[2] * (1 - ratio))
    
    draw.ellipse(
        [center_x - r, center_y - r, center_x + r, center_y + r],
        fill=(r_val, g_val, b_val, 255)
    )

# Draw keyboard key shape (modern, minimal)
key_x, key_y = 256, 240
key_width, key_height = 140, 100
corner_radius = 16

# Draw modern keyboard key with soft corners
draw.rounded_rectangle(
    [key_x - key_width//2, key_y - key_height//2, 
     key_x + key_width//2, key_y + key_height//2],
    radius=corner_radius,
    fill=(255, 255, 255, 240),
    outline=(200, 200, 200, 200),
    width=2
)

# Add subtle depth with inner highlight
inner_offset = 6
draw.rounded_rectangle(
    [key_x - key_width//2 + inner_offset, key_y - key_height//2 + inner_offset,
     key_x + key_width//2 - inner_offset, key_y - key_height//2 + inner_offset + 8],
    radius=corner_radius - 2,
    fill=(255, 255, 255, 100)
)

# Draw motion line (swoosh) - abstract speed element
motion_x_start = key_x + key_width//2 + 20
motion_y_start = key_y - 30
motion_x_end = key_x + key_width//2 + 80
motion_y_end = key_y - 80

# Draw swoosh line with thickness
line_width = 6
for offset in range(-line_width//2, line_width//2 + 1):
    draw.line(
        [motion_x_start, motion_y_start + offset, motion_x_end, motion_y_end + offset],
        fill=(255, 255, 255, 200),
        width=1
    )

# Draw decorative keyboard key detail (small)
detail_x, detail_y = key_x - 50, key_y + 70
detail_size = 30
draw.rounded_rectangle(
    [detail_x - detail_size//2, detail_y - detail_size//2,
     detail_x + detail_size//2, detail_y + detail_size//2],
    radius=6,
    fill=(255, 255, 255, 180),
    outline=(200, 200, 200, 150),
    width=1
)

# Save the icon next to this script so the output path is portable
# across maintainer hosts and CWDs.
output_path = Path(__file__).resolve().parent / "SwiftFloris_icon_new.png"
img.save(output_path, "PNG")
print(f"✓ Icon generated: {output_path}")
print(f"  Size: {img.size}, Format: PNG with transparency (RGBA)")
