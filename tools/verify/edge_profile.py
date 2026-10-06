"""Print the edge-inset profile of a rounded rectangle so its corner radius can be read off.

For a rounded rectangle of radius r, the left edge of row y is inset by
r - sqrt(r^2 - (top + r - y)^2) until y reaches top + r, after which the inset is zero.
The first row whose inset is zero is therefore exactly r below the top edge.

This is how a design's corner radii are taken from a screenshot rather than guessed: crop tightly
around the shape and the printed radius is the number the design used. A screenshot from a
1080px-wide phone at density 2.625 gives 0.38dp per pixel.

Usage:  python edge_profile.py <image> <y0> <y1> <x0> <x1>
"""

from __future__ import annotations

import sys

import numpy as np
from PIL import Image

DENSITY = 2.625


def main() -> None:
    path = sys.argv[1]
    y0, y1, x0, x1 = (int(v) for v in sys.argv[2:6])
    img = np.asarray(Image.open(path).convert("RGB")).astype(np.float64)
    crop = img[y0:y1, x0:x1, :]

    corners = np.concatenate([crop[:4].reshape(-1, 3), crop[-4:].reshape(-1, 3)])
    page = np.median(corners, axis=0)
    # The fill is whatever colour the crop contains most of.
    colours, counts = np.unique(crop.reshape(-1, 3).astype(np.int16), axis=0, return_counts=True)
    fill = colours[counts.argmax()].astype(np.float64)
    print(f"crop page {page.astype(int)}  fill {fill.astype(int)}")

    denom = ((page - fill) ** 2).sum()
    if denom < 1:
        print("page and fill are the same colour - widen the crop")
        return
    coverage = ((page - crop) * (page - fill)).sum(axis=2) / denom
    solid = coverage >= 0.5

    rows = np.where(solid.any(axis=1))[0]
    cols = np.where(solid.any(axis=0))[0]
    top, bottom, left, right = rows[0], rows[-1], cols[0], cols[-1]
    height = bottom - top + 1
    width = right - left + 1
    print(
        f"shape y {top}..{bottom} ({height}px = {height / DENSITY:.2f}dp) "
        f"x {left}..{right} ({width}px = {width / DENSITY:.2f}dp)"
    )

    radius = None
    for y in range(top, min(bottom + 1, top + height // 2 + 2)):
        xs = np.where(solid[y])[0]
        inset = int(xs[0]) - left
        if radius is None and inset == 0:
            radius = y - top
        if y - top < 24 or (radius and y - top < radius + 3):
            print(f"  y+{y - top:3d}  left inset {inset:3d}px  right inset {right - int(xs[-1]):3d}px")
    if radius is not None:
        print(
            f"corner radius = {radius}px = {radius / DENSITY:.2f}dp "
            f"(height/2 = {height / 2 / DENSITY:.2f}dp)"
        )


if __name__ == "__main__":
    main()
