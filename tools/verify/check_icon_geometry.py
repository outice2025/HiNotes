"""Rasterise the generated vectors and measure where their ink actually lands.

The icon buttons are the smallest thing on screen and the easiest thing to get subtly wrong, so this
checks the shipped `.xml` files the way the platform will draw them: parse the path data, flatten
it to polygons, fill them with the even-odd rule, and measure the resulting ink box.

Two properties per 24dp symbol in `drawable/sym_*.xml`:

* the ink's centre is the viewport centre, so the glyph sits centred in its button;
* the ink stays inside the 2dp padding ring, so nothing is ever clipped.

The launcher icon's foreground is drawn by the system rather than by a composable, so it is measured
against its own rules - the same drawing, at the adaptive icon's 108dp viewport:

* the ink is centred, so the mark sits in the middle of the tile;
* the ink stays inside the 66dp safe zone, the part an OEM's icon mask never clips.

Exit code is non-zero when any of it fails.
"""
import glob
import os
import re
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

# This file lives in <repo>/tools/verify, so the repository is two directories up. Deriving it
# keeps the check runnable from a clone anywhere.
REPO = Path(__file__).resolve().parents[2]
RES = REPO / "app" / "src" / "main" / "res"
DRAWABLE = RES / "drawable"

SYMBOL_VIEWPORT = 24.0
SYMBOL_PADDING = 2.0
SYMBOL_RES = 240           # pixels per 24dp viewport -> 0.1dp per pixel
ADAPTIVE_VIEWPORT = 108.0
ADAPTIVE_SAFE_ZONE = 66.0  # the square in the middle an icon mask never clips
ADAPTIVE_RES = 864         # pixels per 108dp viewport -> 0.125dp per pixel
STEPS = 32                 # subdivisions when flattening quadratic curves
TOLERANCE_DP = 0.15


def flatten(data, scale):
    """Flatten an absolute M/L/Q/H/V/Z path (the form SVGPathPen emits) into polygons."""
    tokens = re.findall(r"[MLQVHZ]|-?\d*\.?\d+(?:[eE][-+]?\d+)?", data)
    polygons, current = [], []
    pos = start = (0.0, 0.0)
    cmd = None
    i = 0

    def point(x, y):
        return (x * scale, y * scale)

    while i < len(tokens):
        token = tokens[i]
        if token in "MLQVHZ" and not re.fullmatch(r"-?\d*\.?\d+(?:[eE][-+]?\d+)?", token):
            cmd = token
            i += 1
            if cmd == "Z":
                if len(current) >= 3:
                    polygons.append(current)
                current = []
                pos = start
            continue
        if cmd is None:
            i += 1
            continue
        if cmd == "M":
            x, y = float(tokens[i]), float(tokens[i + 1])
            if len(current) >= 3:
                polygons.append(current)
            current = [point(x, y)]
            pos = start = (x, y)
            i += 2
            cmd = "L"
        elif cmd == "L":
            x, y = float(tokens[i]), float(tokens[i + 1])
            current.append(point(x, y))
            pos = (x, y)
            i += 2
        elif cmd == "H":
            x = float(tokens[i])
            current.append(point(x, pos[1]))
            pos = (x, pos[1])
            i += 1
        elif cmd == "V":
            y = float(tokens[i])
            current.append(point(pos[0], y))
            pos = (pos[0], y)
            i += 1
        elif cmd == "Q":
            cx, cy, x, y = (float(v) for v in tokens[i:i + 4])
            p0 = pos
            for s in range(1, STEPS + 1):
                u = s / STEPS
                mu = 1 - u
                current.append(point(
                    mu * mu * p0[0] + 2 * mu * u * cx + u * u * x,
                    mu * mu * p0[1] + 2 * mu * u * cy + u * u * y,
                ))
            pos = (x, y)
            i += 4
        else:
            i += 1

    if len(current) >= 3:
        polygons.append(current)
    return polygons


def ink_box(path, viewport, resolution):
    """Rasterise one vector and return its ink box in viewport units, or None when empty."""
    xml = path.read_text(encoding="utf-8")
    match = re.search(r'android:pathData="([^"]+)"', xml)
    if match is None:
        return None
    polygons = flatten(match.group(1), resolution / viewport)

    accumulator = Image.new("1", (resolution, resolution), 0)
    for polygon in polygons:
        layer = Image.new("1", (resolution, resolution), 0)
        ImageDraw.Draw(layer).polygon(polygon, fill=1)
        accumulator = ImageChops.logical_xor(accumulator, layer)

    box = accumulator.getbbox()
    if box is None:
        return None
    unit = viewport / resolution
    return (box[0] * unit, box[1] * unit, (box[2] - 1) * unit, (box[3] - 1) * unit)


def symbols(failures):
    files = sorted(glob.glob(os.path.join(DRAWABLE, "sym_*.xml")))
    rows = []
    for path in files:
        path = Path(path)
        name = path.stem[4:]
        box = ink_box(path, SYMBOL_VIEWPORT, SYMBOL_RES)
        if box is None:
            failures.append(f"{name}: nothing was drawn")
            continue
        x0, y0, x1, y1 = box
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        rows.append((abs(cx - 12) + abs(cy - 12), name, cx, cy, x1 - x0, y1 - y0))

        if abs(cx - 12) > TOLERANCE_DP or abs(cy - 12) > TOLERANCE_DP:
            failures.append(
                "%s: ink centre (%.2f, %.2f) is %.2fdp off the viewport centre"
                % (name, cx, cy, max(abs(cx - 12), abs(cy - 12)))
            )
        if x0 < SYMBOL_PADDING - 0.05 or y0 < SYMBOL_PADDING - 0.05 or \
                x1 > SYMBOL_VIEWPORT - SYMBOL_PADDING + 0.05 or \
                y1 > SYMBOL_VIEWPORT - SYMBOL_PADDING + 0.05:
            failures.append(
                "%s: ink x[%.2f..%.2f] y[%.2f..%.2f] leaves the %.0fdp padding ring"
                % (name, x0, x1, y0, y1, SYMBOL_PADDING)
            )

    rows.sort(reverse=True)
    print("symbol vectors rasterised: %d" % len(rows))
    print("worst centring offsets:")
    for offset, name, cx, cy, w, h in rows[:6]:
        print("   %-24s (%+.2f, %+.2f) dp   ink %.2f x %.2f" % (name, cx - 12, cy - 12, w, h))
    mean = sum(r[0] for r in rows) / len(rows) / 2
    print("mean |offset|: %.3f dp" % mean)


def launcher_layers(failures):
    """Measure the vectors the launcher icon is built from, at the adaptive icon's 108dp viewport.

    The foreground has a light and a dark form, so both are measured: the system draws whichever one
    the device's theme asks for in the middle of the tile, and an OEM's mask must never clip it.
    """
    middle = ADAPTIVE_VIEWPORT / 2
    outer = middle - ADAPTIVE_SAFE_ZONE / 2
    inner = middle + ADAPTIVE_SAFE_ZONE / 2
    layers = [
        (DRAWABLE / "ic_launcher_foreground.xml", "ic_launcher_foreground"),
        (RES / "drawable-night-v31" / "ic_launcher_foreground.xml", "ic_launcher_foreground night"),
    ]
    print()
    print("launcher icon layers at the %.0fdp viewport:" % ADAPTIVE_VIEWPORT)
    for path, name in layers:
        box = ink_box(path, ADAPTIVE_VIEWPORT, ADAPTIVE_RES)
        if box is None:
            failures.append(
                "%s: nothing is drawn, so that layer of the launcher icon would be blank" % name
            )
            print("   %-28s nothing drawn" % name)
            continue
        x0, y0, x1, y1 = box
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        print("   %-28s (%+.2f, %+.2f) dp off centre   ink %.2f x %.2f dp"
              % (name, cx - middle, cy - middle, x1 - x0, y1 - y0))
        if x1 - x0 < 1.0 or y1 - y0 < 1.0:
            # A path that rasterises to a pixel or two is not a layer anyone can see.
            failures.append("%s: draws less than 1dp of ink, so that layer is effectively blank"
                            % name)
        if abs(cx - middle) > TOLERANCE_DP or abs(cy - middle) > TOLERANCE_DP:
            failures.append(
                "%s: ink centre (%.2f, %.2f) is %.2fdp off the canvas centre"
                % (name, cx, cy, max(abs(cx - middle), abs(cy - middle)))
            )
        if x0 < outer - 0.05 or y0 < outer - 0.05 or x1 > inner + 0.05 or y1 > inner + 0.05:
            failures.append(
                "%s: ink x[%.2f..%.2f] y[%.2f..%.2f] leaves the %.0fdp safe zone"
                % (name, x0, x1, y0, y1, ADAPTIVE_SAFE_ZONE)
            )


def main():
    failures = []
    symbols(failures)
    launcher_layers(failures)

    if failures:
        print()
        print("FAILURES")
        for line in failures:
            print("  -", line)
        return 1
    print()
    print("icon geometry: every symbol is centred inside its viewport")
    print("launcher icons: both foreground layers are centred inside the safe zone")
    return 0


if __name__ == "__main__":
    sys.exit(main())
