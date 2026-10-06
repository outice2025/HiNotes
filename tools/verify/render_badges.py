"""Render the settings-row badges the way the app draws them, to look at the centring.

Uses the same path data the app ships, so this is the artwork the device will draw: a 40dp
primaryContainer disc with a 24dp glyph centred inside it.
"""
import glob
import os
import re
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
sys.path.insert(0, str(HERE))
from check_icon_geometry import flatten  # noqa: E402

DRAWABLE = str(REPO / "app" / "src" / "main" / "res" / "drawable")
OUT = str(REPO / "build" / "verify" / "badges.png")

# The six settings rows, in order, and the four editor toolbar glyphs the user sees most.
NAMES = ["palette", "edit", "language", "download", "password", "info",
         "search", "undo", "redo", "format_bold", "format_italic",
         "check_box_outline_blank", "keyboard_arrow_down", "delete", "check", "arrow_back"]

BADGE_DP = 40
GLYPH_DP = 24
SCALE = 8                     # pixels per dp in the render
BADGE = BADGE_DP * SCALE
GLYPH = GLYPH_DP * SCALE
BG = (232, 223, 245)          # primaryContainer (fallback palette)
FG = (26, 22, 38)             # onPrimaryContainer

columns = 4
rows = (len(NAMES) + columns - 1) // columns
sheet = Image.new("RGB", (columns * (BADGE + 16) + 16, rows * (BADGE + 16) + 16), (249, 248, 254))
page = ImageDraw.Draw(sheet)

for index, name in enumerate(NAMES):
    path = os.path.join(DRAWABLE, f"sym_{name}.xml")
    if not os.path.exists(path):
        continue
    xml = open(path, encoding="utf-8").read()
    data = re.search(r'android:pathData="([^"]+)"', xml).group(1)

    # Glyph at 24dp, then pasted so its own box centre lands on the disc centre.
    polygons = flatten(data, GLYPH / 24.0)
    glyph = Image.new("1", (GLYPH, GLYPH), 0)
    for polygon in polygons:
        layer = Image.new("1", (GLYPH, GLYPH), 0)
        ImageDraw.Draw(layer).polygon(polygon, fill=1)
        glyph = ImageChops.logical_xor(glyph, layer)

    x = 16 + (index % columns) * (BADGE + 16)
    y = 16 + (index // columns) * (BADGE + 16)
    page.ellipse([x, y, x + BADGE - 1, y + BADGE - 1], fill=BG)

    tinted = Image.new("RGB", (GLYPH, GLYPH), FG)
    sheet.paste(tinted, (x + (BADGE - GLYPH) // 2, y + (BADGE - GLYPH) // 2), glyph)

    # Crosshair through the disc centre for the same check the user made by hand.
    cx, cy = x + BADGE // 2, y + BADGE // 2
    page.line([cx, y, cx, y + BADGE], fill=(255, 0, 0))
    page.line([x, cy, x + BADGE, cy], fill=(255, 0, 0))

os.makedirs(os.path.dirname(OUT), exist_ok=True)
sheet.save(OUT)
print("wrote", OUT, sheet.size)
