"""Rasterise every generated symbol vector and measure where its ink actually lands.

The icon buttons are the smallest thing on screen and the easiest to get subtly wrong, so this
checks the shipped `.xml` files the way the platform will draw them: parse the path data, flatten
it to polygons, fill them with the even-odd rule, and measure the resulting ink box.

Two properties per glyph, both in viewport units (a 24dp icon):

* the ink's centre is the viewport centre, so the glyph sits centred in its button;
* the ink stays inside the 2dp padding ring, so nothing is ever clipped.

Exit code is non-zero when either fails.
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
DRAWABLE = str(REPO / "app" / "src" / "main" / "res" / "drawable")
VIEWPORT = 24.0
PADDING = 2.0
RES = 240          # pixels per 24dp viewport -> 0.1dp per pixel
STEPS = 32         # subdivisions when flattening quadratic curves
TOLERANCE_DP = 0.15

NUMBER = re.compile(r"-?\d*\.?\d+(?:[eE][-+]?\d+)?")
TOKEN = re.compile(r"[MLQVHZ]|-?\d*\.?\d+(?:[eE][-+]?\d+)?")


def flatten(data, scale):
    """Flatten an absolute M/L/Q/H/V/Z path (the form SVGPathPen emits) into polygons."""
    tokens = TOKEN.findall(data)
    polygons, current = [], []
    pos = start = (0.0, 0.0)
    cmd = None
    i = 0

    def point(x, y):
        return (x * scale, y * scale)

    while i < len(tokens):
        token = tokens[i]
        if token in "MLQVHZ" and not NUMBER.fullmatch(token):
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


def ink_box(path):
    """Rasterise one vector and return its ink box in viewport units, or None when empty."""
    xml = open(path, encoding="utf-8").read()
    data = re.search(r'android:pathData="([^"]+)"', xml).group(1)
    polygons = flatten(data, RES / VIEWPORT)

    accumulator = Image.new("1", (RES, RES), 0)
    for polygon in polygons:
        layer = Image.new("1", (RES, RES), 0)
        ImageDraw.Draw(layer).polygon(polygon, fill=1)
        accumulator = ImageChops.logical_xor(accumulator, layer)

    box = accumulator.getbbox()
    if box is None:
        return None
    unit = VIEWPORT / RES
    return (box[0] * unit, box[1] * unit, (box[2] - 1) * unit, (box[3] - 1) * unit)


def main():
    files = sorted(glob.glob(os.path.join(DRAWABLE, "sym_*.xml")))
    rows, failures = [], []
    for path in files:
        name = os.path.basename(path)[4:-4]
        box = ink_box(path)
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
        if x0 < PADDING - 0.05 or y0 < PADDING - 0.05 or \
                x1 > VIEWPORT - PADDING + 0.05 or y1 > VIEWPORT - PADDING + 0.05:
            failures.append(
                "%s: ink x[%.2f..%.2f] y[%.2f..%.2f] leaves the %.0fdp padding ring"
                % (name, x0, x1, y0, y1, PADDING)
            )

    rows.sort(reverse=True)
    print("symbol vectors rasterised: %d" % len(rows))
    print("worst centring offsets:")
    for offset, name, cx, cy, w, h in rows[:6]:
        print("   %-24s (%+.2f, %+.2f) dp   ink %.2f x %.2f" % (name, cx - 12, cy - 12, w, h))
    mean = sum(r[0] for r in rows) / len(rows) / 2
    print("mean |offset|: %.3f dp" % mean)

    if failures:
        print()
        print("FAILURES")
        for line in failures:
            print("  -", line)
        return 1
    print()
    print("icon geometry: every glyph is centred and inside its viewport")
    return 0


if __name__ == "__main__":
    sys.exit(main())
