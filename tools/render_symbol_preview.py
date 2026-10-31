#!/usr/bin/env python
"""Render candidate Material Symbols glyphs to PNG for visual comparison.

Used while choosing the artwork for an icon: the font is not redistributed with the app, so this
reads it from the environment like the vector generator does, and writes one small PNG per glyph
plus a combined contact sheet.

    HINOTES_SYMBOLS_FONT=... HINOTES_SYMBOLS_CODEPOINTS=... python tools/render_symbol_preview.py
"""
import os
from pathlib import Path

from fontTools.ttLib import TTFont
from fontTools.varLib import instancer
from PIL import Image, ImageDraw, ImageFont

REPO = Path(__file__).resolve().parents[1]
FONT = os.environ.get("HINOTES_SYMBOLS_FONT", "")
CODEPOINTS = os.environ.get("HINOTES_SYMBOLS_CODEPOINTS", "")
OUT = REPO / "build" / "icon-preview"

# Glyphs worth looking at for a solid pencil, in outline and filled.
CANDIDATES = ["edit", "edit_square", "ink_pen", "stylus", "draw", "brush", "create"]

CELL = 96


def table():
    codes = {}
    with open(CODEPOINTS, encoding="utf-8") as fh:
        for line in fh:
            parts = line.split()
            if len(parts) == 2:
                codes[parts[0]] = int(parts[1], 16)
    return codes


def render(font_path, codes, names, label):
    font = ImageFont.truetype(str(font_path), 72)
    sheet = Image.new("RGB", (CELL * len(names), CELL + 18), (70, 90, 140))
    draw = ImageDraw.Draw(sheet)
    for index, name in enumerate(names):
        if name not in codes:
            draw.text((index * CELL + 8, CELL // 2), "n/a", font=ImageFont.load_default(), fill=(255, 255, 255))
            continue
        draw.text((index * CELL + 12, 8), chr(codes[name]), font=font, fill=(255, 255, 255))
        draw.text((index * CELL + 4, CELL + 2), name[:13], font=ImageFont.load_default(), fill=(255, 255, 255))
    out = OUT / f"{label}.png"
    sheet.save(out)
    print("wrote", out)


def main():
    if not FONT or not CODEPOINTS:
        raise SystemExit("set HINOTES_SYMBOLS_FONT and HINOTES_SYMBOLS_CODEPOINTS")
    OUT.mkdir(parents=True, exist_ok=True)
    codes = table()
    render(FONT, codes, CANDIDATES, "outlined")
    filled = OUT / "filled-instance.ttf"
    if not filled.exists():
        instancer.instantiateVariableFont(TTFont(FONT), {"FILL": 1.0}).save(str(filled))
    render(filled, codes, CANDIDATES, "filled")


if __name__ == "__main__":
    main()
