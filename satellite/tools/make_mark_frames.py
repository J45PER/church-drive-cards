#!/usr/bin/env python3
"""Renders the frames of Clive's mark for the satellite's dock.

The mark is two overlapping rounded squares with the overlap cut out (even-odd fill), see
companion/android/core/.../AssistantMark.kt. LVGL has no even-odd path, so each pose is rendered here into a short
loop of white-on-transparent PNGs, which clive-round.yaml plays with an animimg. The maths is a port of the app's
(and of the mockup's stepMark) at the steady state of each pose.

Frame = 112 x 112 px: the app draws the 200-unit mark space at 0.88 px per unit in the dock, and the art stays inside
+-60 units of the centre.
Usage: python3 make_mark_frames.py [outdir]
"""
import math
import sys
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw

SIZE = 112          # output px
SS = 4              # supersampling
UNIT = 0.88         # px per mark unit
CENTRE = SIZE / 2
SH = 11.3


def wave(p):
    return (math.cos(p * 2 * math.pi) + 1) / 2


def rr_mask(x0, y0, x1, y1, r):
    """Anti-aliased rounded rectangle in mark units (200-unit space, centre 100,100) as an L image."""
    im = Image.new("L", (SIZE * SS, SIZE * SS), 0)
    d = ImageDraw.Draw(im)
    f = lambda v: (CENTRE + (v - 100) * UNIT) * SS
    r = max(0.0, min(r, (x1 - x0) / 2, (y1 - y0) / 2))
    d.rounded_rectangle([f(x0), f(y0), f(x1), f(y1)], radius=r * UNIT * SS, fill=255)
    return im


def shape(s, sx, sy, together):
    x, y, w, h, r, dx, dy = s
    cx = 100 + dx * together + (x + w / 2 - 100)
    cy = 100 + dy * together + (y + h / 2 - 100)
    return rr_mask(cx - w * sx / 2, cy - h * sy / 2, cx + w * sx / 2, cy + h * sy / 2, r * min(sx, sy))


REST_A = (62, 62, 76, 76, 30, -SH, SH)
REST_B = (62, 62, 76, 76, 30, SH, -SH)


def xor(a, b):
    m = ImageChops.multiply(a, b)
    return ImageChops.subtract(ImageChops.add(a, b), ImageChops.add(m, m))


def render(a, b=None):
    mask = a if b is None else xor(a, b)
    mask = mask.resize((SIZE, SIZE), Image.LANCZOS)
    im = Image.new("LA", (SIZE, SIZE), (255, 0))
    im.putalpha(mask)
    return im.convert("RGBA")


def frames(pose, n):
    out = []
    for i in range(n):
        p = i / n
        if pose == "idle":
            t = 1 - 0.55 * (1 - wave(p))
            out.append(render(shape(REST_A, 1, 1, t), shape(REST_B, 1, 1, t)))
        elif pose == "think":
            tr = math.cos(p * 2 * math.pi)
            sa, sb = 1 + 0.025 + 0.175 * tr, 1 + 0.025 - 0.175 * tr
            out.append(render(shape(REST_A, sa, sa, 1), shape(REST_B, sb, sb, 1)))
        elif pose == "listen":
            stop = 1 + 0.07 * (1 - wave(p)) + 0.16 * 0.45   # 0.45: a typical voice level
            out.append(render(shape((70, 70, 60, 60, 18, 0, 0), stop, stop, 0)))
        elif pose == "speak":
            bars = math.cos(p * 2 * math.pi)
            ba, bb = 1 - 0.25 + 0.25 * bars, 1 - 0.25 - 0.25 * bars
            out.append(render(shape((64, 52, 30, 96, 15, 0, 0), 1, ba, 0), shape((106, 52, 30, 96, 15, 0, 0), 1, bb, 0)))
    return out


# pose: (frames in the loop, loop length in ms): the app's cycles are 4 s, 1.4 s, 1.2 s and 0.7 s
POSES = {"idle": (24, 4000), "listen": (12, 1400), "think": (12, 1200), "speak": (10, 700)}

if __name__ == "__main__":
    out = Path(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent / "assets" / "mark")
    out.mkdir(parents=True, exist_ok=True)
    for pose, (n, ms) in POSES.items():
        for i, im in enumerate(frames(pose, n)):
            im.save(out / f"{pose}_{i:02d}.png", optimize=True)
    print({k: v for k, v in POSES.items()})
