#!/usr/bin/env python
"""Generate HiNotes launcher icons and the in-app mark from the Material Symbols 'edit' glyph.

The launcher icon is a tile carrying a pencil. From Android 12 the tile and the pencil follow the
system's light and dark setting - a pale blue tile with a deep blue pencil in the light theme, a deep
tile with the same pale pencil in the dark one - and below Android 12 the icon keeps the one fixed
pair of colours, which is the oldest platform's answer to the same question.

Outputs:
  * res/drawable/ic_logo_note.xml                  - in-app About/lock mark (24dp vector)
  * res/drawable/ic_launcher_foreground.xml        - adaptive-icon foreground (108dp vector)
  * res/drawable-night-v31/ic_launcher_foreground.xml - the same, for the dark theme
  * res/mipmap-anydpi-v26/ic_launcher{,_round}.xml
  * res/mipmap-*/ic_launcher{,_round}.png           - legacy icons for API < 26
  * res/values/colors.xml, res/values-night-v31/colors.xml - the tile colour of each theme

Both the vector and the bitmap are placed by the glyph's *ink* box rather than by its em box.
The em box is 960 units but this glyph's artwork only fills 120..840 of it, so centring on the em
left the pencil visibly off-centre inside the icon; centring on the ink puts it dead centre.
"""
import os
import re
from pathlib import Path

from fontTools.misc.transform import Transform
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont
from PIL import Image, ImageDraw, ImageFont

# This file lives in <repo>/tools, so the repository is one directory up. The Material Symbols
# font is not redistributed with the app, so its two paths are read from the environment.
REPO = Path(__file__).resolve().parents[1]
FONT = os.environ.get("HINOTES_SYMBOLS_FONT", "")
CODEPOINTS = os.environ.get("HINOTES_SYMBOLS_CODEPOINTS", "")
if not FONT or not CODEPOINTS:
    raise SystemExit(
        "Set HINOTES_SYMBOLS_FONT to the Material Symbols Rounded variable font and "
        "HINOTES_SYMBOLS_CODEPOINTS to its codepoints table (both are downloads from "
        "Google Fonts)."
    )
RES = str(REPO / "app" / "src" / "main" / "res")

GLYPH = "edit"
EM_UNITS = 960.0

ADAPTIVE_VIEWPORT = 108.0
ADAPTIVE_ARTWORK = 34.0          # artwork size inside the 108dp canvas
LOGO_VIEWPORT = 24.0
LOGO_ARTWORK = 24.0              # the in-app mark fills its box

DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

# The icon's colours. The light theme is a pale blue tile with a deep blue pencil on it; the dark
# theme keeps the pencil pale and makes the tile deep, so the mark reads on a home screen of either
# kind. Below Android 12 the light pair is the one fixed set of colours. The vector drawables are
# written from the same values, so repainting the icon here repaints every form of it at once.
BG = (195, 215, 250, 255)    # #C3D7FA
FG = (56, 81, 121, 255)      # #385179
FG_FILL = "#%02X%02X%02X%02X" % (FG[3], FG[0], FG[1], FG[2])    # Android writes AARRGGBB

BG_NIGHT = (42, 53, 80, 255)     # #2A3550
FG_NIGHT = BG                    # the pale tile colour, now the pencil's
FG_NIGHT_FILL = "#%02X%02X%02X%02X" % (FG_NIGHT[3], FG_NIGHT[0], FG_NIGHT[1], FG_NIGHT[2])

PNG_ARTWORK = 0.50          # fraction of the legacy icon the glyph occupies


def codepoint(name):
    with open(CODEPOINTS, encoding="utf-8") as fh:
        for line in fh:
            parts = line.split()
            if len(parts) == 2 and parts[0] == name:
                return int(parts[1], 16)
    raise SystemExit("codepoint not found: " + name)


def ink_bounds(name):
    font = TTFont(FONT, lazy=True)
    glyf = font["glyf"]
    glyph = glyf[name]
    glyph.recalcBounds(glyf)
    return glyph.xMin, glyph.yMin, glyph.xMax, glyph.yMax


def round_numbers(d, places=3):
    def fix(m):
        v = round(float(m.group(0)), places)
        t = ("%f" % v).rstrip("0").rstrip(".")
        return t if t not in ("", "-0") else "0"

    return re.sub(r"-?\d+\.\d+", fix, d)


def glyph_path(viewport, artwork):
    """Glyph outline centred on its ink box inside `viewport`, sizing the ink to `artwork`."""
    x0, y0, x1, y1 = ink_bounds(GLYPH)
    span = max(x1 - x0, y1 - y0)
    scale = artwork / span
    cx = (x0 + x1) / 2.0
    cy = (y0 + y1) / 2.0
    # Font space is Y-up, vector space is Y-down.
    transform = Transform(
        scale, 0, 0, -scale,
        viewport / 2.0 - cx * scale,
        viewport / 2.0 + cy * scale,
    )
    font = TTFont(FONT, lazy=True)
    pen = SVGPathPen(font.getGlyphSet())
    font.getGlyphSet()[GLYPH].draw(TransformPen(pen, transform))
    return round_numbers(pen.getCommands())


def write_drawable(name, path_data, viewport, size_dp, fill="#FFFFFFFF", folder="drawable", notes=()):
    """Writes one vector drawable, with the header every generated file carries.

    `notes` are extra lines of explanation between the glyph's own line and the "do not edit"
    warning: what the file is for, when it is one of the launcher icon's several forms.
    """
    lines = ['Material Symbols Rounded "%s", centred on its ink box.' % GLYPH]
    lines += list(notes)
    lines.append("Generated by tools/gen_icons.py; do not edit by hand.")
    comment = "\n".join("  " + line for line in lines)
    body = f'''<?xml version="1.0" encoding="utf-8"?>
<!--
{comment}
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{size_dp:g}dp"
    android:height="{size_dp:g}dp"
    android:viewportWidth="{viewport:g}"
    android:viewportHeight="{viewport:g}">
    <path
        android:fillColor="{fill}"
        android:pathData="{path_data}" />
</vector>
'''
    out = os.path.join(RES, folder, name)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(body)
    print("  %-18s %-28s %4d chars" % (folder + "/", name, len(path_data)))


def glyph_layer(size, cp):
    """The glyph on transparent, its ink centred and occupying PNG_ARTWORK of `size`.

    Drawn in white whichever colour the icon uses, and tinted by `compose` on the way onto the
    tile, so the glyph's artwork is written once.

    The ink's position is *measured* rather than derived: the glyph is first drawn onto a scratch
    canvas at a known origin, the resulting ink box is read back, and the placement is solved from
    it. `ImageFont.getbbox` reports a box based on the font's layout extent rather than the
    artwork, which left the pencil about 6% high.
    """
    x0, y0, x1, y1 = ink_bounds(GLYPH)
    ink_units = max(x1 - x0, y1 - y0)
    target_ink = size * PNG_ARTWORK
    # FreeType sizes by em, so ask for an em whose ink lands on the target.
    font_px = max(8, int(round(target_ink * EM_UNITS / ink_units)))
    font = ImageFont.truetype(FONT, font_px)
    text = chr(cp)

    pad = font_px * 2
    scratch = Image.new("L", (pad + font_px * 2, pad + font_px * 2), 0)
    ImageDraw.Draw(scratch).text((pad, pad), text, font=font, fill=255)
    box = scratch.getbbox()
    if box is None:
        return Image.new("RGBA", (size, size), (0, 0, 0, 0))

    ink_w, ink_h = box[2] - box[0], box[3] - box[1]
    # Where the ink started relative to the draw origin.
    dx = box[0] - pad
    dy = box[1] - pad

    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ImageDraw.Draw(layer).text(
        ((size - ink_w) / 2 - dx, (size - ink_h) / 2 - dy),
        text,
        font=font,
        fill=(255, 255, 255, 255),
    )
    return layer


def compose(glyph, size, circular):
    ss = 4
    canvas = Image.new("RGBA", (size * ss, size * ss), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    if circular:
        draw.ellipse([0, 0, size * ss - 1, size * ss - 1], fill=BG)
    else:
        draw.rounded_rectangle(
            [0, 0, size * ss - 1, size * ss - 1], radius=size * ss * 0.22, fill=BG
        )
    big = glyph.resize((size * ss, size * ss), Image.LANCZOS)
    tinted = Image.new("RGBA", big.size, FG)
    tinted.putalpha(big.getchannel("A"))
    canvas.alpha_composite(tinted)
    return canvas.resize((size, size), Image.LANCZOS)


def main():
    cp = codepoint(GLYPH)
    x0, y0, x1, y1 = ink_bounds(GLYPH)
    print("glyph %s U+%04X  ink x[%d..%d] y[%d..%d]  em %.0f"
          % (GLYPH, cp, x0, x1, y0, y1, EM_UNITS))
    print("ink centre (%.1f, %.1f) vs em centre (%.0f, %.0f)"
          % ((x0 + x1) / 2, (y0 + y1) / 2, EM_UNITS / 2, EM_UNITS / 2))
    print()

    write_drawable("ic_logo_note.xml", glyph_path(LOGO_VIEWPORT, LOGO_ARTWORK), LOGO_VIEWPORT, 24.0)
    adaptive = glyph_path(ADAPTIVE_VIEWPORT, ADAPTIVE_ARTWORK)
    write_drawable(
        "ic_launcher_foreground.xml",
        adaptive,
        ADAPTIVE_VIEWPORT,
        ADAPTIVE_VIEWPORT,
        fill=FG_FILL,
    )
    # The same foreground for the dark theme, where the tile is deep and the pencil pale. It is a
    # -v31 resource because following the system is Android 12's answer; older versions keep the
    # one fixed pair of colours above, which is the "fixed colours below Android 12" the icon was
    # asked for.
    write_drawable(
        "ic_launcher_foreground.xml",
        adaptive,
        ADAPTIVE_VIEWPORT,
        ADAPTIVE_VIEWPORT,
        fill=FG_NIGHT_FILL,
        folder="drawable-night-v31",
        notes=[
            "The launcher icon's foreground for the dark theme: the tile behind it is deep, so the",
            "pencil is the pale colour of the light theme's tile.",
        ],
    )

    anydpi = os.path.join(RES, "mipmap-anydpi-v26")
    os.makedirs(anydpi, exist_ok=True)
    adaptive_xml = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
'''
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        with open(os.path.join(anydpi, name), "w", encoding="utf-8", newline="\n") as fh:
            fh.write(adaptive_xml)
    print("  mipmap-anydpi-v26/ic_launcher{,_round}.xml")

    # The dark theme's tile colour. The light one lives in values/colors.xml beside the window
    # background, which is not this script's to rewrite.
    night_values = os.path.join(RES, "values-night-v31")
    os.makedirs(night_values, exist_ok=True)
    night_colors = '''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!--
      The launcher icon's tile in the dark theme.

      The icon follows the system's light and dark setting from Android 12 on: the pale tile with a
      deep pencil is the light side, and this deep tile with the same pale pencil is the dark one, so
      the app's mark is legible on a home screen of either kind. Below Android 12 the icon keeps the
      one fixed pair of colours - see values/colors.xml - which is what "a fixed colour scheme on
      older versions" means. Every value here is generated by tools/gen_icons.py.
    -->
    <color name="ic_launcher_background">#%02X%02X%02X</color>
</resources>
''' % (BG_NIGHT[0], BG_NIGHT[1], BG_NIGHT[2])
    with open(os.path.join(night_values, "colors.xml"), "w", encoding="utf-8", newline="\n") as fh:
        fh.write(night_colors)
    print("  values-night-v31/colors.xml")

    glyph = glyph_layer(max(DENSITIES.values()) * 2, cp)
    for bucket, px in DENSITIES.items():
        out = os.path.join(RES, f"mipmap-{bucket}")
        os.makedirs(out, exist_ok=True)
        compose(glyph, px, False).save(os.path.join(out, "ic_launcher.png"))
        compose(glyph, px, True).save(os.path.join(out, "ic_launcher_round.png"))
    print("  mipmap-*/ic_launcher{,_round}.png at %d densities" % len(DENSITIES))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
