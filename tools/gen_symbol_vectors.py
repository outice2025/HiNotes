#!/usr/bin/env python
"""Generate one Android VectorDrawable per Material Symbols glyph used by HiNotes.

Text-based icon rendering depends on the platform's font layout: the glyph only fills 75% of the
em box, and this font's asymmetric vertical metrics put the baseline somewhere that could not be
reproduced reliably. Rendering the outlines as vectors removes the font engine from the equation
entirely - the artwork is placed exactly where the generator puts it, at any API level.

Output:
  app/src/main/res/drawable/sym_<name>.xml   one vector per icon
  app/src/main/java/.../ui/icons/Symbols.kt  @DrawableRes constants, in the same order

Geometry: one scale for every glyph, taken from the union of their ink boxes, so all of them carry
the same optical weight; and each glyph is then centred on **its own** ink box. Centring on the
union instead leaves every glyph wherever the font happened to draw it - the union's centre is
0.44dp below the em centre, and individual glyphs drift up to 1.5dp further - which reads as an
icon sitting off-centre in its button. The ink of a 24dp icon therefore always occupies the middle
20dp of the viewport, inside a 2dp padding ring.
"""
import os
import re

from fontTools.misc.transform import Transform
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

FONT = r"D:\tools\MaterialSymbolsRounded-var.ttf"
CODEPOINTS = r"D:\tools\MaterialSymbolsRounded.codepoints"
RES = r"D:\HiNotes\app\src\main\res"
KT = r"D:\HiNotes\app\src\main\java\com\hiapps\hinotes\ui\icons\Symbols.kt"

# Kotlin constant name -> Material Symbols icon name. Only icons the app actually uses are
# generated, so the drawable set stays exactly as large as the UI needs.
ICONS = {
    "Settings": "settings", "Search": "search", "Edit": "edit",
    "Close": "close", "Delete": "delete", "Sort": "sort",
    "GridView": "grid_view", "ViewList": "view_list", "SelectAll": "select_all",
    "ArrowBack": "arrow_back", "Check": "check", "Visibility": "visibility",
    "Info": "info", "Share": "share", "KeyboardArrowDown": "keyboard_arrow_down",
    "Undo": "undo", "Redo": "redo", "FormatBold": "format_bold",
    "FormatItalic": "format_italic", "CheckBoxOutlineBlank": "check_box_outline_blank",
    "List": "list", "Palette": "palette", "Language": "language",
    "Archive": "archive", "Password": "password", "ChevronRight": "chevron_right",
    "Colors": "colors", "DarkMode": "dark_mode", "Contrast": "contrast",
    "FormatSize": "format_size", "TextFields": "text_fields", "Sell": "sell",
    "Save": "save", "Download": "download", "Upload": "upload",
    "Face": "face", "Lock": "lock", "Update": "update",
    "Box": "box", "Bookmarks": "bookmarks", "OpenInNew": "open_in_new",
}

UPEM = 960.0
VIEWPORT = 24.0
# A 24dp viewport that draws 20dp of artwork inside a 2dp padding ring, matching the optical
# size of Google's published Material Symbols vectors.
ARTWORK = 20.0
PADDING = (VIEWPORT - ARTWORK) / 2.0

# Filled in by main() from the union of every glyph's ink box: one scale for all of them, so the
# set keeps a consistent optical size while each glyph is centred on its own ink.
SHARED_SCALE = 1.0


def codepoints():
    table = {}
    with open(CODEPOINTS, encoding="utf-8") as fh:
        for line in fh:
            parts = line.split()
            if len(parts) == 2:
                table[parts[0]] = int(parts[1], 16)
    return table


def ink_union(font, names):
    """Union of every glyph's ink box, so one scale fits all of them and they stay consistent."""
    glyf = font["glyf"]
    x0 = y0 = 1e9
    x1 = y1 = -1e9
    for name in names:
        glyph = glyf[name]
        glyph.recalcBounds(glyf)
        x0 = min(x0, glyph.xMin)
        y0 = min(y0, glyph.yMin)
        x1 = max(x1, glyph.xMax)
        y1 = max(y1, glyph.yMax)
    return x0, y0, x1, y1


def round_numbers(d, places=3):
    def fix(m):
        v = round(float(m.group(0)), places)
        t = ("%f" % v).rstrip("0").rstrip(".")
        return t if t not in ("", "-0") else "0"

    return re.sub(r"-?\d+\.\d+", fix, d)


def make_transform(bounds):
    """Centre one glyph's ink box in the viewport at the shared scale, flipping Y-up to Y-down.

    [bounds] is the glyph's own ink box, not the union: the scale stays common so every icon has
    the same optical weight, while the centring is per glyph so none of them sits off-centre.
    """
    ux0, uy0, ux1, uy1 = bounds
    scale = SHARED_SCALE
    cx = (ux0 + ux1) / 2.0
    cy = (uy0 + uy1) / 2.0
    # x' = VIEWPORT/2 + (x - cx) * scale
    # y' = VIEWPORT/2 - (y - cy) * scale
    return Transform(scale, 0, 0, -scale,
                     VIEWPORT / 2.0 - cx * scale,
                     VIEWPORT / 2.0 + cy * scale)


def glyph_bounds(font, glyph_name):
    """The glyph's own ink box, in font units."""
    glyf = font["glyf"]
    glyph = glyf[glyph_name]
    glyph.recalcBounds(glyf)
    return glyph.xMin, glyph.yMin, glyph.xMax, glyph.yMax


def glyph_path(glyph_name, transform):
    font = TTFont(FONT, lazy=True)
    glyph_set = font.getGlyphSet()
    pen = SVGPathPen(glyph_set)
    glyph_set[glyph_name].draw(TransformPen(pen, transform))
    return round_numbers(pen.getCommands())


def write_vector(res_name, path_data):
    body = f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Material Symbols Rounded "{res_name}". Generated by tools/gen_symbol_vectors.py. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/white"
        android:pathData="{path_data}" />
</vector>
'''
    out = os.path.join(RES, "drawable", f"sym_{res_name}.xml")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(body)
    return len(path_data)


def main():
    global SHARED_SCALE

    table = codepoints()
    missing = [n for n in ICONS.values() if n not in table]
    if missing:
        raise SystemExit("missing codepoints: %s" % missing)

    # One scale for every icon, taken from the union of their ink boxes, so no glyph overflows
    # the viewport and all of them share the same optical size. Centring is per glyph.
    font = TTFont(FONT, lazy=True)
    names = sorted(set(ICONS.values()))
    bounds = ink_union(font, names)
    SHARED_SCALE = ARTWORK / max(bounds[2] - bounds[0], bounds[3] - bounds[1])
    print("ink union x[%g..%g] y[%g..%g] -> scale %.5f"
          % (bounds[0], bounds[2], bounds[1], bounds[3], SHARED_SCALE))

    lines = [
        "package com.hiapps.hinotes.ui.icons",
        "",
        "import androidx.annotation.DrawableRes",
        "import com.hiapps.hinotes.R",
        "",
        "/**",
        " * Material Symbols Rounded glyphs, one vector drawable each.",
        " *",
        " * Generated by tools/gen_symbol_vectors.py from the Material Symbols Rounded font that",
        " * also produced the app icon; do not edit by hand.",
        " */",
        "object Symbols {",
    ]

    total = 0
    for ident in sorted(ICONS, key=lambda k: ICONS[k]):
        name = ICONS[ident]
        # Centred on this glyph's own ink box, at the scale shared by the whole set.
        transform = make_transform(glyph_bounds(font, name))
        size = write_vector(name, glyph_path(name, transform))
        total += size
        lines.append("    @DrawableRes val %s: Int = R.drawable.sym_%s" % (ident, name))
    lines.append("}")

    os.makedirs(os.path.dirname(KT), exist_ok=True)
    with open(KT, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(lines) + "\n")

    print("generated %d vectors (%d chars of path data)" % (len(ICONS), total))
    print("wrote", KT)


if __name__ == "__main__":
    main()
