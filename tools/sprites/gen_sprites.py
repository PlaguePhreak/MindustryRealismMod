#!/usr/bin/env python3
"""Draws every Grounded Industry sprite as pixel art in the vanilla Serpulo style.

Run from the repository root:  python3 tools/sprites/gen_sprites.py
Writes into assets/sprites/. Needs Pillow and numpy.

Conventions copied from vanilla Serpulo blocks:
  * 32 px per tile; 1x1 = 32, 2x2 = 64, 3x3 = 96.
  * Metal palette b0bac0 / 989aa4 / 6e7080, shadows 4a4b53 / 3c3837.
  * Light falls from the top right: raised parts are light on top and right, dark on bottom and left.
    Recessed parts are the reverse.
  * Item ports are darker notches in the middle of each side.
  * DrawFlame draws <name>-top over the block and a glowing circle at its centre, so furnaces get a -top sprite.
"""
import math
import os
import random

import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "assets", "sprites")


def hexc(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


# vanilla Serpulo metal
LIGHT, MID, DARK = hexc("b0bac0"), hexc("989aa4"), hexc("6e7080")
SHADOW, DEEP, HOLE = hexc("4a4b53"), hexc("3c3837"), hexc("2f2e33")
PORT_L, PORT_M, PORT_D = hexc("646567"), hexc("515151"), hexc("3c3837")
GLOW, GLOW_SOFT, WHITE = hexc("ffef99"), hexc("ffc999", 136), hexc("ffffff")
YELLOW, YELLOW_D = hexc("fbd367"), hexc("d4a43a")
COPPER_L, COPPER_M, COPPER_D = hexc("e5b18a"), hexc("d39169"), hexc("a1674a")

# steel: a cool, pale blue-grey, lighter and less saturated than titanium
STEEL = [hexc("eef3f8"), hexc("ccd5df"), hexc("a8b3c1"), hexc("808b9b"), hexc("5d6675")]


# ---------------------------------------------------------------- canvas and shapes

class Canvas:
    def __init__(self, w, h=None):
        self.w, self.h = w, h or w
        self.a = np.zeros((self.h, self.w, 4), np.uint8)

    def paint(self, mask, color):
        if color[3] == 255:
            self.a[mask] = color
        else:
            # alpha-over, so soft glows can sit on transparency or on paint
            src = np.array(color, float)
            dst = self.a[mask].astype(float)
            sa = src[3] / 255
            da = dst[:, 3] / 255
            oa = sa + da * (1 - sa)
            rgb = (src[:3] * sa + dst[:, :3] * (da * (1 - sa))[:, None]) / np.maximum(oa, 1e-6)[:, None]
            out = np.concatenate([rgb, (oa * 255)[:, None]], 1)
            self.a[mask] = np.round(out).astype(np.uint8)

    def mask(self):
        return self.a[:, :, 3] > 0

    def image(self):
        return Image.fromarray(self.a, "RGBA")

    def save(self, rel):
        path = os.path.join(OUT, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        self.image().save(path)
        return path


def empty(c):
    return np.zeros((c.h, c.w), bool)


def rect(c, x0, y0, x1, y1):
    """Inclusive pixel rectangle."""
    m = empty(c)
    m[max(y0, 0):y1 + 1, max(x0, 0):x1 + 1] = True
    return m


def octo(c, x0, y0, x1, y1, k):
    """Rectangle with its corners cut at 45 degrees by k pixels."""
    m = rect(c, x0, y0, x1, y1)
    ys, xs = np.mgrid[0:c.h, 0:c.w]
    for cx, cy, sx, sy in ((x0, y0, 1, 1), (x1, y0, -1, 1), (x0, y1, 1, -1), (x1, y1, -1, -1)):
        m &= ((xs - cx) * sx + (ys - cy) * sy) >= k
    return m


def disk(c, cx, cy, r):
    ys, xs = np.mgrid[0:c.h, 0:c.w]
    return (xs + 0.5 - cx) ** 2 + (ys + 0.5 - cy) ** 2 <= r * r


def ellipse(c, cx, cy, rx, ry):
    ys, xs = np.mgrid[0:c.h, 0:c.w]
    return ((xs + 0.5 - cx) / rx) ** 2 + ((ys + 0.5 - cy) / ry) ** 2 <= 1


def poly(c, pts):
    im = Image.new("L", (c.w, c.h))
    ImageDraw.Draw(im).polygon(pts, fill=255)
    return np.array(im) > 0


def line(c, pts, width=1):
    im = Image.new("L", (c.w, c.h))
    ImageDraw.Draw(im).line(pts, fill=255, width=width)
    return np.array(im) > 0


def runs(m):
    """Distance from each pixel to the edge of the mask in each direction (1 = on the edge)."""
    h, w = m.shape
    up, down, left, right = (np.zeros((h, w), int) for _ in range(4))
    for y in range(h):
        up[y] = np.where(m[y], (up[y - 1] if y else 0) + 1, 0)
    for y in range(h - 1, -1, -1):
        down[y] = np.where(m[y], (down[y + 1] if y < h - 1 else 0) + 1, 0)
    for x in range(w):
        left[:, x] = np.where(m[:, x], (left[:, x - 1] if x else 0) + 1, 0)
    for x in range(w - 1, -1, -1):
        right[:, x] = np.where(m[:, x], (right[:, x + 1] if x < w - 1 else 0) + 1, 0)
    return up, right, down, left


def bevel(c, m, light, mid, dark, width=2, raised=True):
    """Fills a shape and shades its rim: lit from the top right when raised, from the bottom left when sunk."""
    c.paint(m, mid)
    up, right, down, left = runs(m)
    near = np.stack([up, right, down, left])
    side = np.argmin(near, 0)
    edge = m & (near.min(0) <= width)
    lit = np.isin(side, (0, 1)) if raised else np.isin(side, (2, 3))
    c.paint(edge & lit, light)
    c.paint(edge & ~lit, dark)


def rim(m):
    """Pixels of the mask that touch the outside."""
    up, right, down, left = runs(m)
    return m & (np.stack([up, right, down, left]).min(0) == 1)


def shade_sphere(c, m, cx, cy, r, tones, lx=0.55, ly=-0.6):
    """Bands of tones across a round shape, brightest towards the light (top right)."""
    ys, xs = np.mgrid[0:c.h, 0:c.w]
    dx, dy = (xs + 0.5 - cx) / r, (ys + 0.5 - cy) / r
    lit = dx * lx + dy * ly + 0.15 * np.sqrt(np.clip(1 - dx * dx - dy * dy, 0, 1))
    n = len(tones)
    edges = np.linspace(-0.75, 0.8, n + 1)[1:-1]
    idx = np.digitize(lit, edges)  # 0 = darkest
    for i, t in enumerate(tones):
        c.paint(m & (idx == i), t)


def frame(c, size, light=LIGHT, mid=MID, dark=DARK, width=None, ports=True):
    """The outer body of a vanilla crafter: bevelled square with item-port notches on each side."""
    s = size
    width = width or (2 if s <= 32 else 3)
    bevel(c, rect(c, 0, 0, s - 1, s - 1), light, mid, dark, width)
    if ports:
        pw = s // 4
        a, b = s // 2 - pw // 2, s // 2 + pw // 2 - 1
        d = max(3, s // 12)
        for m in (rect(c, a, 0, b, d), rect(c, a, s - 1 - d, b, s - 1), rect(c, 0, a, d, b), rect(c, s - 1 - d, a, s - 1, b)):
            bevel(c, m, PORT_L, PORT_M, PORT_D, 1, raised=False)


def bolts(c, pts, light=LIGHT, dark=SHADOW):
    for x, y in pts:
        c.paint(rect(c, x, y, x + 1, y + 1), dark)
        c.paint(rect(c, x + 1, y, x + 1, y), light)


# ---------------------------------------------------------------- liquid: booster pump

def booster_pump():
    """32x32, drawn facing right (rotation 0). Liquid shows through the windows on either side of the housing."""
    bottom = Canvas(32)
    bottom.paint(rect(bottom, 0, 0, 31, 3), LIGHT)
    bottom.paint(rect(bottom, 0, 4, 31, 7), DARK)
    bottom.paint(rect(bottom, 0, 8, 31, 23), SHADOW)
    bottom.paint(rect(bottom, 0, 24, 31, 27), DARK)
    bottom.paint(rect(bottom, 0, 28, 31, 31), LIGHT)

    # white mask the game tints with the liquid's colour
    liquid = Canvas(32)
    liquid.paint(rect(liquid, 0, 8, 31, 23), WHITE)

    top = Canvas(32)
    # pipe walls, as on a conduit
    for y0 in (0, 24):
        bevel(top, rect(top, 0, y0, 31, y0 + 7), LIGHT, MID, DARK, 1)
    # flange collars where the pipe meets the housing
    for x0 in (6, 23):
        bevel(top, rect(top, x0, 2, x0 + 2, 29), LIGHT, MID, DARK, 1)
    # pump housing
    bevel(top, octo(top, 9, 3, 22, 28, 3), LIGHT, MID, DARK, 1)
    # recessed rotor well with an impeller
    well = disk(top, 16, 16, 6)
    bevel(top, well, LIGHT, SHADOW, DARK, 1, raised=False)
    for ang in (20, 110, 200, 290):
        a = math.radians(ang)
        top.paint(line(top, [(16 + math.cos(a) * 1.5, 16 + math.sin(a) * 1.5), (16 + math.cos(a + 0.5) * 4.6, 16 + math.sin(a + 0.5) * 4.6)]), MID)
    top.paint(disk(top, 16, 16, 1.6), LIGHT)
    # flow arrow on the housing, pointing downstream
    for y in (6, 25):
        for k in range(4):
            top.paint(rect(top, 16 + k, y - 3 + k, 16 + k, y + 3 - k), YELLOW)
        top.paint(rect(top, 16, y + 3, 18, y + 3) & ~rect(top, 17, y + 3, 18, y + 3), YELLOW_D)
    # arrows in the windows, like a conduit
    for x in (2, 26):
        top.paint(line(top, [(x, 11), (x + 2, 15), (x + 2, 16), (x, 20)]), hexc("ffffff", 110))

    icon = Canvas(32)
    icon.a = np.array(Image.alpha_composite(bottom.image(), top.image()))

    bottom.save("blocks/booster-pump-bottom.png")
    liquid.save("blocks/booster-pump-liquid.png")
    top.save("blocks/booster-pump-top.png")
    icon.save("blocks/booster-pump.png")


# ---------------------------------------------------------------- steelmaking blocks

def magnetic_separator():
    """2x2. A magnetised drum lifts iron out of a sand feed; copper coils at both ends."""
    c = Canvas(64)
    frame(c, 64)
    # sand hopper under the drum
    bevel(c, octo(c, 8, 8, 55, 55, 6), LIGHT, DARK, SHADOW, 2, raised=False)
    sand = [hexc("f7cba4"), hexc("d3ae8d"), hexc("b28768")]
    rnd = random.Random(3)
    for y in range(11, 53):
        for x in range(11, 53):
            if octo(c, 10, 10, 53, 53, 6)[y, x]:
                c.a[y, x] = sand[1] if rnd.random() < 0.7 else rnd.choice(sand)
    # drum: a horizontal cylinder, banded light at the top
    drum = octo(c, 12, 20, 51, 43, 3)
    ys = np.mgrid[0:64, 0:64][0]
    for (a, b), t in (((20, 23), STEEL[1]), ((24, 29), MID), ((30, 37), hexc("878a96")), ((38, 43), DARK)):
        c.paint(drum & (ys >= a) & (ys <= b), t)
    # magnet poles: alternating red and blue bands across the drum
    for i, x in enumerate(range(17, 47, 6)):
        col = [hexc("d0574d"), hexc("a33c3a")] if i % 2 == 0 else [hexc("6a85d8"), hexc("4b5cb0")]
        c.paint(drum & rect(c, x, 20, x + 2, 32), col[0])
        c.paint(drum & rect(c, x, 33, x + 2, 43), col[1])
    c.paint(rim(drum), SHADOW)
    # coil housings
    for x0 in (6, 50):
        coil = octo(c, x0, 17, x0 + 7, 46, 2)
        bevel(c, coil, COPPER_L, COPPER_M, COPPER_D, 1)
        for y in range(20, 44, 3):
            c.paint(rect(c, x0 + 1, y, x0 + 6, y) & coil, COPPER_D)
    bolts(c, [(5, 5), (57, 5), (5, 57), (57, 57)])
    c.save("blocks/magnetic-separator.png")


def ore_concentrator():
    """2x2. A water-filled washing cyclone with a spiral classifier."""
    c = Canvas(64)
    frame(c, 64)
    bevel(c, octo(c, 7, 7, 56, 56, 9), LIGHT, MID, DARK, 2)
    tank = octo(c, 12, 12, 51, 51, 9)
    bevel(c, tank, LIGHT, SHADOW, DARK, 2, raised=False)
    water = octo(c, 15, 15, 48, 48, 8)
    shade_sphere(c, water, 32, 32, 22, [hexc("4a5fa8"), hexc("596ac0"), hexc("6f88d9"), hexc("8aa6ec")])
    # spiral classifier arm
    pts = []
    for i in range(70):
        t = i / 69 * 2.4 * math.pi
        r = 2 + t * 2.0
        pts.append((32 + math.cos(t) * r, 32 + math.sin(t) * r))
    c.paint(line(c, pts, 2), LIGHT)
    c.paint(line(c, [(p[0] - 1, p[1] + 1) for p in pts], 1) & ~line(c, pts, 2), DARK)
    hub = disk(c, 32, 32, 4)
    bevel(c, hub, LIGHT, MID, DARK, 1)
    c.paint(disk(c, 32, 32, 1.5), SHADOW)
    # foam highlights
    for x, y in ((22, 20), (40, 41), (19, 38), (43, 22)):
        c.paint(rect(c, x, y, x + 1, y), hexc("d6e0ff"))
    bolts(c, [(5, 5), (57, 5), (5, 57), (57, 57)])
    c.save("blocks/ore-concentrator.png")


def bricks(c, m, tones, bw=6, bh=3, seed=1):
    """Running-bond brickwork inside a mask: tones = (light, mid, dark, mortar)."""
    rnd = random.Random(seed)
    ys, xs = np.mgrid[0:c.h, 0:c.w]
    c.paint(m, tones[3])
    for row in range(0, c.h // bh + 1):
        off = (bw // 2) * (row % 2)
        for col in range(-1, c.w // bw + 1):
            x0, y0 = col * bw + off, row * bh
            b = rect(c, x0, y0, x0 + bw - 2, y0 + bh - 2) & m
            if not b.any():
                continue
            c.paint(b, rnd.choice((tones[1], tones[1], tones[2])))
            c.paint(b & (ys == y0), tones[0])


def coke_oven():
    """2x2. A brick oven battery: four charging doors and a central firebox."""
    c = Canvas(64)
    frame(c, 64, ports=True)
    body = octo(c, 5, 5, 58, 58, 4)
    bricks(c, body, (hexc("c47a5e"), hexc("a35a45"), hexc("8a4a3a"), hexc("5c3a33")), seed=2)
    c.paint(rim(body), hexc("4a2f2a"))
    # iron bands across the battery
    for y0 in (8, 52):
        bevel(c, rect(c, 5, y0, 58, y0 + 3), LIGHT, MID, DARK, 1)
    # oven doors along the top and bottom
    for x0 in (11, 23, 35, 47):
        for y0 in (13, 42):
            door = rect(c, x0, y0, x0 + 5, y0 + 8)
            bevel(c, door, LIGHT, DARK, SHADOW, 1)
            c.paint(rect(c, x0 + 2, y0 + 2, x0 + 3, y0 + 6), DEEP)
    # firebox
    fire = octo(c, 21, 23, 42, 40, 5)
    bevel(c, fire, LIGHT, MID, DARK, 2)
    hole = octo(c, 25, 26, 38, 37, 4)
    bevel(c, hole, DARK, HOLE, SHADOW, 1, raised=False)
    c.save("blocks/coke-oven.png")

    top = Canvas(64)
    top.paint(octo(top, 24, 25, 39, 38, 4), GLOW_SOFT)
    top.paint(octo(top, 26, 27, 37, 36, 3), GLOW)
    for x0 in (11, 23, 35, 47):
        for y0 in (13, 42):
            top.paint(rect(top, x0 + 2, y0 + 2, x0 + 3, y0 + 6), GLOW)
    top.save("blocks/coke-oven-top.png")


def sinter_plant():
    """2x2. A travelling grate carries the mix under an ignition hood."""
    c = Canvas(64)
    frame(c, 64)
    # grate bed, running left to right
    bed = rect(c, 5, 14, 58, 49)
    bevel(c, bed, LIGHT, SHADOW, DARK, 2, raised=False)
    for x in range(8, 57, 4):
        c.paint(rect(c, x, 17, x + 2, 46), hexc("7a4c40"))
        c.paint(rect(c, x, 17, x + 2, 18), hexc("a8644f"))
        c.paint(rect(c, x + 2, 19, x + 2, 46), hexc("5a3830"))
    # side rails
    for y0 in (10, 50):
        bevel(c, rect(c, 3, y0, 60, y0 + 3), LIGHT, MID, DARK, 1)
    # ignition hood
    hood = octo(c, 18, 7, 45, 56, 5)
    bevel(c, hood, LIGHT, MID, DARK, 2)
    bevel(c, octo(c, 22, 11, 41, 52, 4), LIGHT, MID, DARK, 1, raised=False)
    # burner slot
    slot = octo(c, 26, 22, 37, 41, 3)
    bevel(c, slot, DARK, HOLE, SHADOW, 1, raised=False)
    bolts(c, [(20, 9), (42, 9), (20, 53), (42, 53)])
    c.save("blocks/sinter-plant.png")

    top = Canvas(64)
    top.paint(octo(top, 25, 21, 38, 42, 3), GLOW_SOFT)
    top.paint(octo(top, 27, 23, 36, 40, 2), GLOW)
    top.save("blocks/sinter-plant-top.png")


def blast_furnace():
    """3x3. Seen from above: a round shaft in a steel jacket, ringed by tuyeres and the hot-blast bustle pipe."""
    c = Canvas(96)
    frame(c, 96)
    cx = cy = 48
    # bustle pipe
    pipe = disk(c, cx, cy, 40) & ~disk(c, cx, cy, 35)
    shade_sphere(c, pipe, cx, cy, 40, [DARK, MID, LIGHT])
    c.paint(rim(pipe), SHADOW)
    # four support columns in the corners
    for x0, y0 in ((4, 4), (80, 4), (4, 80), (80, 80)):
        bevel(c, octo(c, x0, y0, x0 + 11, y0 + 11, 3), LIGHT, MID, DARK, 2)
        c.paint(rect(c, x0 + 4, y0 + 4, x0 + 7, y0 + 7), SHADOW)
    # shell: brick-red refractory jacket in steel bands
    shell = disk(c, cx, cy, 33)
    shade_sphere(c, shell, cx, cy, 33, [hexc("6b3a30"), hexc("8a4a3a"), hexc("a35a45"), hexc("c47a5e")])
    for r in (33, 26):
        band = disk(c, cx, cy, r) & ~disk(c, cx, cy, r - 3)
        shade_sphere(c, band, cx, cy, r, [DARK, MID, LIGHT])
    # tuyeres: eight blast nozzles from the bustle pipe into the shell
    for i in range(8):
        a = i * math.pi / 4
        p0 = (cx + math.cos(a) * 36, cy + math.sin(a) * 36)
        p1 = (cx + math.cos(a) * 25, cy + math.sin(a) * 25)
        c.paint(line(c, [p0, p1], 4), MID)
        c.paint(line(c, [p0, p1], 2), LIGHT)
    # throat
    throat = disk(c, cx, cy, 17)
    bevel(c, throat, LIGHT, DARK, SHADOW, 2, raised=False)
    c.paint(disk(c, cx, cy, 12), HOLE)
    # charging bell
    bell = disk(c, cx, cy, 6)
    bevel(c, bell, LIGHT, MID, DARK, 1)
    c.save("blocks/blast-furnace.png")

    top = Canvas(96)
    ring = disk(top, cx, cy, 12) & ~disk(top, cx, cy, 6)
    top.paint(disk(top, cx, cy, 13) & ~disk(top, cx, cy, 6), GLOW_SOFT)
    top.paint(ring & ~disk(top, cx, cy, 8), GLOW)
    for i in range(8):
        a = i * math.pi / 4 + math.pi / 8
        top.paint(disk(top, cx + math.cos(a) * 10, cy + math.sin(a) * 10, 1.5), WHITE)
    top.save("blocks/blast-furnace-top.png")


def arc_furnace():
    """3x3. A round vessel with a domed roof; three graphite electrodes drop through it in a triangle."""
    c = Canvas(96)
    frame(c, 96)
    cx = cy = 48
    # transformer cabinet and bus bars on the left
    cab = octo(c, 5, 30, 18, 65, 3)
    bevel(c, cab, LIGHT, MID, DARK, 2)
    for y in (36, 44, 52, 60):
        c.paint(rect(c, 8, y, 15, y + 1), SHADOW)
    # vessel
    vessel = disk(c, cx + 4, cy, 38)
    shade_sphere(c, vessel, cx + 4, cy, 38, [SHADOW, DARK, MID, LIGHT])
    c.paint(rim(vessel), SHADOW)
    # water-cooled roof panels
    roof = disk(c, cx + 4, cy, 30)
    shade_sphere(c, roof, cx + 4, cy, 30, [DARK, MID, hexc("a4a8b2"), LIGHT])
    ys, xs = np.mgrid[0:96, 0:96]
    ang = np.arctan2(ys + 0.5 - cy, xs + 0.5 - cx - 4)
    for k in range(12):
        a = -math.pi + k * math.pi / 6
        c.paint(roof & (np.abs(np.angle(np.exp(1j * (ang - a)))) < 0.05), DARK)
    c.paint(rim(roof), SHADOW)
    # delta: the electrode port ring
    delta = disk(c, cx + 4, cy, 16)
    bevel(c, delta, LIGHT, MID, DARK, 2)
    # electrodes with their copper clamps and arms back to the cabinet
    ex = [(cx + 4 + math.cos(a) * 9, cy + math.sin(a) * 9) for a in (math.pi, math.pi / 3, -math.pi / 3)]
    for x, y in ex:
        c.paint(line(c, [(16, y), (x, y)], 4), COPPER_D)
        c.paint(line(c, [(16, y - 1), (x, y - 1)], 2), COPPER_M)
    for x, y in ex:
        clamp = disk(c, x, y, 5.5)
        bevel(c, clamp, COPPER_L, COPPER_M, COPPER_D, 1)
        el = disk(c, x, y, 3.5)
        shade_sphere(c, el, x, y, 3.5, [hexc("3a3a40"), hexc("55565e"), hexc("7a7c86")])
    c.save("blocks/arc-furnace.png")

    top = Canvas(96)
    cx2 = cx + 4
    top.paint(disk(top, cx2, cy, 8), hexc("c8e0ff", 120))
    for x, y in ex:
        top.paint(line(top, [(x, y), (cx2, cy)], 2), hexc("d6e8ff"))
        top.paint(disk(top, x, y, 2), WHITE)
    top.paint(disk(top, cx2, cy, 3), WHITE)
    top.save("blocks/arc-furnace-top.png")


def ore_iron():
    """Three ore overlays, transparent around the rocks like vanilla ores."""
    tones = [hexc("cf9a7a"), hexc("a8705a"), hexc("7c4d3e")]
    rust = hexc("d87a4a")
    layouts = [
        [(8, 8, 5), (22, 10, 4), (14, 21, 6), (25, 24, 3)],
        [(9, 20, 5), (21, 8, 5), (23, 22, 4), (7, 7, 3)],
        [(16, 15, 6), (6, 26, 3), (26, 8, 3), (26, 25, 4), (7, 10, 3)],
    ]
    for i, lay in enumerate(layouts):
        c = Canvas(32)
        rnd = random.Random(10 + i)
        for x, y, r in lay:
            pts = []
            for k in range(7):
                a = k / 7 * 2 * math.pi + rnd.uniform(-0.3, 0.3)
                rr = r * rnd.uniform(0.75, 1.1)
                pts.append((x + math.cos(a) * rr, y + math.sin(a) * rr))
            m = poly(c, pts)
            shade_sphere(c, m, x, y, r, [tones[2], tones[1], tones[0]])
            if r >= 4:
                c.paint(rect(c, x - 1, y, x, y) & m, rust)
        c.save(f"blocks/ore-iron{i + 1}.png")


def steel_walls():
    def wall(s, name):
        c = Canvas(s)
        light, hi, mid, dark, deep = STEEL
        bevel(c, rect(c, 0, 0, s - 1, s - 1), light, mid, dark, 2 if s == 32 else 3)
        inset = 5 if s == 32 else 8
        cells = 1 if s == 32 else 2
        step = (s - 2 * inset) // cells
        for i in range(cells):
            for j in range(cells):
                x0, y0 = inset + i * step, inset + j * step
                plate = octo(c, x0, y0, x0 + step - 2, y0 + step - 2, 3)
                bevel(c, plate, hi, mid, dark, 1, raised=False)
                # brushed highlight across each plate
                c.paint(line(c, [(x0 + 3, y0 + step - 6), (x0 + step - 6, y0 + 3)]) & plate, hi)
        rv = [(2, 2), (s - 4, 2), (2, s - 4), (s - 4, s - 4)]
        if s > 32:
            rv += [(s // 2 - 1, 2), (s // 2 - 1, s - 4), (2, s // 2 - 1), (s - 4, s // 2 - 1)]
        bolts(c, rv, light, deep)
        c.save(name)
    wall(32, "blocks/steel/steel-wall.png")
    wall(64, "blocks/steel/steel-wall-large.png")


def transmission_tower():
    """2x2. A lattice pylon seen from above: four legs, cross bracing, and an insulated crossarm."""
    c = Canvas(64)
    light, hi, mid, dark, deep = STEEL
    # footing pad, sunk into the ground
    bevel(c, rect(c, 0, 0, 63, 63), LIGHT, MID, DARK, 3)
    bevel(c, octo(c, 4, 4, 59, 59, 6), LIGHT, SHADOW, DARK, 2, raised=False)
    # legs
    for x0, y0 in ((6, 6), (50, 6), (6, 50), (50, 50)):
        bevel(c, rect(c, x0, y0, x0 + 7, y0 + 7), light, mid, dark, 1)
    # lattice: outer square, X-bracing and inner square
    for pts in ([(9, 9), (54, 9), (54, 54), (9, 54), (9, 9)], [(9, 9), (54, 54)], [(54, 9), (9, 54)],
                [(20, 20), (43, 20), (43, 43), (20, 43), (20, 20)]):
        c.paint(line(c, pts, 2), dark)
        c.paint(line(c, [(x + 1, y - 1) for x, y in pts], 1), hi)
    # crossarm
    arm = rect(c, 4, 28, 59, 35)
    bevel(c, arm, light, mid, dark, 1)
    # insulator strings: stacked yellow-glazed discs at each end and the peak
    for x in (9, 32, 54):
        for k, y in enumerate((26, 29, 32, 35)):
            d = disk(c, x, y + 1.5, 3.2 - 0.3 * k)
            c.paint(d, YELLOW_D)
            c.paint(d & ~disk(c, x - 0.8, y + 2.3, 3.0 - 0.3 * k), YELLOW)
        c.paint(disk(c, x, 32, 1.4), deep)
    c.save("blocks/transmission-tower.png")


# ---------------------------------------------------------------- items

def item_iron_ore():
    c = Canvas(32)
    m = poly(c, [(6, 12), (12, 5), (22, 4), (28, 10), (28, 20), (23, 27), (12, 28), (5, 22)])
    shade_sphere(c, m, 17, 16, 13, [hexc("6b4036"), hexc("8c5a48"), hexc("a8705a"), hexc("cf9a7a")])
    # rust and dark flecks
    for x, y in ((11, 11), (19, 9), (14, 19), (21, 18), (9, 20)):
        c.paint(rect(c, x, y, x + 1, y + 1) & m, hexc("d87a4a"))
    for x, y in ((17, 13), (12, 24), (24, 13)):
        c.paint(rect(c, x, y, x + 1, y) & m, hexc("4e2f28"))
    c.save("items/iron-ore.png")


def item_iron_concentrate():
    c = Canvas(32)
    pile = poly(c, [(2, 28), (6, 19), (12, 9), (17, 5), (22, 10), (27, 18), (30, 28)])
    shade_sphere(c, pile, 17, 20, 14, [hexc("2d2d36"), hexc("3c3c47"), hexc("4c4c58"), hexc("686877")])
    rnd = random.Random(5)
    for _ in range(40):
        x, y = rnd.randint(4, 28), rnd.randint(8, 27)
        if pile[y, x]:
            c.a[y, x] = rnd.choice([hexc("7c7d8e"), hexc("2a2a33"), hexc("5b5b6a")])
    c.save("items/iron-concentrate.png")


def item_coke():
    c = Canvas(32)
    m = poly(c, [(5, 14), (10, 6), (20, 4), (27, 9), (29, 19), (24, 27), (13, 29), (5, 23)])
    shade_sphere(c, m, 17, 16, 13, [hexc("38383d"), hexc("4a4a50"), hexc("5f5f66"), hexc("8a8a92")])
    # porous: pits with a lit lower lip
    for x, y in ((11, 11), (19, 9), (15, 17), (23, 16), (10, 22), (19, 23)):
        c.paint(rect(c, x, y, x + 2, y + 1) & m, hexc("232327"))
        c.paint(rect(c, x, y + 2, x + 2, y + 2) & m, hexc("8a8a92"))
    c.save("items/coke.png")


def item_sinter():
    c = Canvas(32)
    m = poly(c, [(4, 17), (8, 8), (17, 5), (26, 7), (29, 16), (25, 26), (14, 28), (6, 25)])
    shade_sphere(c, m, 17, 16, 13, [hexc("5a3029"), hexc("74443a"), hexc("8a5446"), hexc("b27460")])
    for x, y in ((10, 12), (18, 9), (14, 18), (22, 15), (9, 21), (19, 23), (24, 21)):
        c.paint(rect(c, x, y, x + 1, y + 1) & m, hexc("3e2420"))
        c.paint(rect(c, x + 2, y, x + 2, y) & m, hexc("c98c72"))
    c.save("items/sinter.png")


def ingot(tones, name, notch=False):
    """A cast ingot in the vanilla three-quarter view (like titanium and metaglass)."""
    c = Canvas(32)
    light, top, side, dark = tones
    # top face (parallelogram), front face and end face
    face = poly(c, [(2, 14), (16, 3), (30, 12), (16, 23)])
    front = poly(c, [(2, 14), (16, 23), (16, 29), (2, 20)])
    end = poly(c, [(16, 23), (30, 12), (30, 18), (16, 29)])
    c.paint(face, top)
    c.paint(front, dark)
    c.paint(end, side)
    c.paint(line(c, [(4, 14), (16, 5)]) & face, light)
    c.paint(line(c, [(16, 5), (27, 12)]) & face, light)
    if notch:
        # pig iron is cast in sand moulds: notched across, with a rough top
        for x0, y0 in ((8, 16), (13, 19)):
            c.paint(line(c, [(x0, y0), (x0 + 11, y0 - 9)]) & face, dark)
            c.paint(line(c, [(x0 + 1, y0), (x0 + 12, y0 - 9)]) & face, light)
    else:
        # steel: a bright polished stripe
        c.paint(line(c, [(9, 16), (20, 8)], 2) & face, light)
    c.save(name)


def items():
    item_iron_ore()
    item_iron_concentrate()
    item_coke()
    item_sinter()
    ingot((hexc("a6a6b2"), hexc("7c7c88"), hexc("5f5f6a"), hexc("484852")), "items/pig-iron.png", notch=True)
    ingot((STEEL[0], STEEL[1], STEEL[2], STEEL[3]), "items/steel.png")


# ---------------------------------------------------------------- planet

def halden():
    """64x64 planet icon: temperate oceans, rust-red iron hills, green lowlands, ice caps."""
    c = Canvas(64)
    cx = cy = 32
    R = 29
    ys, xs = np.mgrid[0:64, 0:64]
    nx, ny = (xs + 0.5 - cx) / R, (ys + 0.5 - cy) / R
    inside = nx * nx + ny * ny <= 1
    nz = np.sqrt(np.clip(1 - nx * nx - ny * ny, 0, 1))
    # value noise on the sphere surface
    rng = np.random.default_rng(7)
    def noise(freq, seed):
        g = np.random.default_rng(seed).random((freq + 2, freq + 2, freq + 2))
        u = (nx + 1) / 2 * freq; v = (ny + 1) / 2 * freq; w = (nz + 1) / 2 * freq
        i, j, k = u.astype(int), v.astype(int), w.astype(int)
        fu, fv, fw = u - i, v - j, w - k
        s = lambda t: t * t * (3 - 2 * t)
        fu, fv, fw = s(fu), s(fv), s(fw)
        out = 0
        for di in (0, 1):
            for dj in (0, 1):
                for dk in (0, 1):
                    wgt = (fu if di else 1 - fu) * (fv if dj else 1 - fv) * (fw if dk else 1 - fw)
                    out = out + wgt * g[i + di, j + dj, k + dk]
        return out
    h = noise(3, 1) * 0.6 + noise(6, 2) * 0.3 + noise(12, 3) * 0.1
    lat = np.abs(ny)
    light = np.clip(nx * 0.55 - ny * 0.45 + nz * 0.7, 0, 1)
    band = np.digitize(light, [0.35, 0.65, 0.9])  # 0 dark .. 3 bright

    pal = {
        "ocean": [hexc("1f3a66"), hexc("2a4f86"), hexc("3767a6"), hexc("4a7fb8")],
        "shallow": [hexc("2a5a7a"), hexc("357599"), hexc("4891b4"), hexc("5ea9c8")],
        "green": [hexc("2f4f2c"), hexc("436b3a"), hexc("5a8a48"), hexc("74a85a")],
        "iron": [hexc("5a2c22"), hexc("7a3d2c"), hexc("a3553a"), hexc("c0643a")],
        "ice": [hexc("8fa2b4"), hexc("b4c4d2"), hexc("d6e0ea"), hexc("f0f5fa")],
    }
    kind = np.full((64, 64), "ocean", object)
    kind[h > 0.47] = "shallow"
    kind[h > 0.52] = "green"
    kind[(h > 0.63)] = "iron"
    kind[lat + (h - 0.5) * 0.3 > 0.82] = "ice"
    for k, tones in pal.items():
        for b in range(4):
            c.paint(inside & (kind == k) & (band == b), tones[b])
    # thin cloud streaks
    cl = noise(8, 9)
    c.paint(inside & (cl > 0.68) & (band >= 1), hexc("e8eef2", 170))
    # atmosphere rim
    ring = (nx * nx + ny * ny <= (1 + 2 / R) ** 2) & ~inside
    c.paint(ring, hexc("4a7fb8", 150))
    c.save("planets/halden.png")


def main():
    booster_pump()
    magnetic_separator()
    ore_concentrator()
    coke_oven()
    sinter_plant()
    blast_furnace()
    arc_furnace()
    ore_iron()
    steel_walls()
    transmission_tower()
    items()
    halden()


if __name__ == "__main__":
    main()
