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

# Art is drawn on a grid of PIXEL x PIXEL saved pixels and saved at the 32-px-per-tile size the game expects.
# PIXEL = 4 gives 8 art pixels per tile, so a 2x2 block is 16x16 art pixels. Items and ore are 16x16 (ITEM_PIXEL = 2),
# since they are shown alone at one tile's size. All coordinates below are in saved (32 px per tile) units.
PIXEL = 4
ITEM_PIXEL = 2

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
    def __init__(self, w, h=None, pixel=None):
        self.size = (w, h or w)
        self.p = pixel or PIXEL
        self.w, self.h = w // self.p, (h or w) // self.p
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
        self.image().resize(self.size, Image.NEAREST).save(path)
        return path


def empty(c):
    return np.zeros((c.h, c.w), bool)


def g(c, v):
    """Saved-pixel coordinate to grid cell."""
    return int(math.floor(v / c.p))


def grid(c):
    """Saved-pixel coordinates of each grid cell's centre."""
    ys, xs = np.mgrid[0:c.h, 0:c.w]
    return (ys + 0.5) * c.p, (xs + 0.5) * c.p


def rect(c, x0, y0, x1, y1):
    """Inclusive pixel rectangle."""
    x0, y0, x1, y1 = g(c, x0), g(c, y0), g(c, x1), g(c, y1)
    m = empty(c)
    m[max(y0, 0):y1 + 1, max(x0, 0):x1 + 1] = True
    return m


def octo(c, x0, y0, x1, y1, k):
    """Rectangle with its corners cut at 45 degrees by k pixels."""
    m = rect(c, x0, y0, x1, y1)
    ys, xs = grid(c)
    for cx, cy, sx, sy in ((x0, y0, 1, 1), (x1 + 1, y0, -1, 1), (x0, y1 + 1, 1, -1), (x1 + 1, y1 + 1, -1, -1)):
        m &= ((xs - cx) * sx + (ys - cy) * sy) >= k
    return m


def disk(c, cx, cy, r):
    ys, xs = grid(c)
    return (xs - cx) ** 2 + (ys - cy) ** 2 <= r * r


def ellipse(c, cx, cy, rx, ry):
    ys, xs = grid(c)
    return ((xs - cx) / rx) ** 2 + ((ys - cy) / ry) ** 2 <= 1


def poly(c, pts):
    im = Image.new("L", (c.w, c.h))
    ImageDraw.Draw(im).polygon([(x / c.p, y / c.p) for x, y in pts], fill=255)
    return np.array(im) > 0


def line(c, pts, width=1):
    im = Image.new("L", (c.w, c.h))
    ImageDraw.Draw(im).line([(x / c.p, y / c.p) for x, y in pts], fill=255, width=max(1, round(width / c.p)))
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
    """Fills a shape and shades its rim: lit from the top right when raised, from the bottom left when sunk.
    width is in grid cells."""
    width = 1
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
    ys, xs = grid(c)
    dx, dy = (xs - cx) / r, (ys - cy) / r
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


def art(c, rows, pal):
    """Paints a hand-drawn grid: one character per art pixel, '.' left empty."""
    assert len(rows) == c.h and all(len(r) == c.w for r in rows), (len(rows), [len(r) for r in rows])
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                c.a[y, x] = pal[ch]


# shared legend for hand-drawn blocks
PAL = {
    "L": LIGHT, "M": MID, "D": DARK, "S": SHADOW, "H": HOLE, "P": PORT_M,
    "C": COPPER_M, "c": COPPER_D, "Y": YELLOW, "y": YELLOW_D, "W": WHITE,
}


def framed(n, inner, ports=True):
    """Wraps an (n-2)-wide interior in the vanilla crafter frame: light top and right, dark left and bottom, ports
    in the middle of each side."""
    a, b = n // 2 - 2, n // 2 + 1
    rows = []
    for y in range(n):
        row = []
        for x in range(n):
            port = ports and ((y in (0, n - 1) and a <= x <= b) or (x in (0, n - 1) and a <= y <= b))
            if port:
                row.append("P")
            elif y == 0:
                row.append("D" if x == 0 else "L")
            elif y == n - 1:
                row.append("L" if x == n - 1 else "D")
            elif x == 0:
                row.append("D")
            elif x == n - 1:
                row.append("L")
            else:
                row.append(inner[y - 1][x - 1])
        rows.append("".join(row))
    return rows


# ---------------------------------------------------------------- liquid: booster pump

def booster_pump():
    """1x1, drawn facing right (rotation 0). Liquid shows through the windows either side of the housing."""
    bottom = Canvas(32)
    art(bottom, [
        "LLLLLLLL",
        "DDDDDDDD",
        "SSSSSSSS",
        "SSSSSSSS",
        "SSSSSSSS",
        "SSSSSSSS",
        "DDDDDDDD",
        "LLLLLLLL",
    ], PAL)
    # white mask the game tints with the liquid's colour
    liquid = Canvas(32)
    art(liquid, [
        "........",
        "........",
        "WWWWWWWW",
        "WWWWWWWW",
        "WWWWWWWW",
        "WWWWWWWW",
        "........",
        "........",
    ], PAL)
    top = Canvas(32)
    art(top, [
        "LLLLLLLL",
        "DDDYLLDD",
        "..DMML..",
        "..DSSL..",
        "..DSSL..",
        "..DMML..",
        "LLDYMLLL",
        "DDDDDDDD",
    ], PAL)
    icon = Canvas(32)
    icon.a = np.array(Image.alpha_composite(bottom.image(), top.image()))
    bottom.save("blocks/booster-pump-bottom.png")
    liquid.save("blocks/booster-pump-liquid.png")
    top.save("blocks/booster-pump-top.png")
    icon.save("blocks/booster-pump.png")


# ---------------------------------------------------------------- steelmaking blocks

def magnetic_separator():
    """2x2. A magnetised drum (red and blue poles) lifts iron out of a sand feed, copper coils at both ends."""
    c = Canvas(64)
    art(c, framed(16, [
        "MMMMMMMMMMMMMM",
        "MssssssssssssM",
        "CCSSSSSSSSSSCC",
        "CcLRRLLLLBBLcC",
        "CcLRRLLLLBBLcC",
        "CcLRRLLLLBBLcC",
        "CcMRRMMMMBBMcC",
        "CcDrrDDDDbbDcC",
        "CcDrrDDDDbbDcC",
        "CcDrrDDDDbbDcC",
        "CCSSSSSSSSSSCC",
        "MssssssssssssM",
        "MssssssssssssM",
        "MMMMMMMMMMMMMM",
    ]), {**PAL, "s": hexc("d3ae8d"), "R": hexc("d0574d"), "r": hexc("a33c3a"), "B": hexc("6a85d8"), "b": hexc("4b5cb0")})
    c.save("blocks/magnetic-separator.png")


def ore_concentrator():
    """2x2. A water-filled washing tank with a rake arm."""
    c = Canvas(64)
    art(c, framed(16, [
        "MMMMMMMMMMMMMM",
        "MMMDDDDDDDDMMM",
        "MMDWWWWWWWWDMM",
        "MLWWWWWWWWWWDM",
        "MLWWWWWWWWWWDM",
        "MLWWWWWWWWWWDM",
        "MLLLLLSSLLLLDM",
        "MLwwwwSSwwwwDM",
        "MLwwwwwwwwwwDM",
        "MLwwwwwwwwwwDM",
        "MLwwwwwwwwwwDM",
        "MMLwwwwwwwwDMM",
        "MMMLLLLLLLLMMM",
        "MMMMMMMMMMMMMM",
    ]), {**PAL, "W": hexc("8aa6ec"), "w": hexc("596ac0")})
    c.save("blocks/ore-concentrator.png")


def coke_oven():
    """2x2. A brick oven battery: four charging doors and a central firebox."""
    pal = {**PAL, "k": hexc("a35a45"), "K": hexc("8a4a3a")}
    c = Canvas(64)
    art(c, framed(16, [
        "LLLLLLLLLLLLLL",
        "DDDDDDDDDDDDDD",
        "kkkKkkkkkkKkkk",
        "kSSkkkKkkkkSSk",
        "kSSkkkkkkKkSSk",
        "kkKkDLLLLLkkKk",
        "kkkkDHHHHLkkkk",
        "KkkkDHHHHLkkkK",
        "kkkkDDDDDLkkkk",
        "kSSkkKkkkkkSSk",
        "kSSkkkkkKkkSSk",
        "kkkKkkkkkkKkkk",
        "LLLLLLLLLLLLLL",
        "DDDDDDDDDDDDDD",
    ]), pal)
    c.save("blocks/coke-oven.png")
    top = Canvas(64)
    top.paint(rect(top, 24, 28, 39, 35), GLOW)
    top.save("blocks/coke-oven-top.png")


def sinter_plant():
    """2x2. A travelling grate carries the mix under an ignition hood."""
    c = Canvas(64)
    art(c, framed(16, [
        "LLLLLLLLLLLLLL",
        "DDDDDDDDDDDDDD",
        "gSgSLLLLLLgSgS",
        "gSgSDMMMMLgSgS",
        "gSgSDMHHMLgSgS",
        "gSgSDMHHMLgSgS",
        "gSgSDMHHMLgSgS",
        "gSgSDMHHMLgSgS",
        "gSgSDMHHMLgSgS",
        "gSgSDMHHMLgSgS",
        "gSgSDMMMMLgSgS",
        "gSgSDDDDDLgSgS",
        "LLLLLLLLLLLLLL",
        "DDDDDDDDDDDDDD",
    ]), {**PAL, "g": hexc("8a5446")})
    c.save("blocks/sinter-plant.png")
    top = Canvas(64)
    top.paint(rect(top, 28, 20, 35, 43), GLOW)
    top.save("blocks/sinter-plant-top.png")


def blast_furnace():
    """3x3. Seen from above: a round shaft in a steel jacket, ringed by tuyeres and the hot-blast bustle pipe."""
    c = Canvas(96)
    frame(c, 96)
    cx = cy = 48
    # shell: brick-red refractory jacket in steel bands
    shell = disk(c, cx, cy, 33)
    shade_sphere(c, shell, cx, cy, 33, [hexc("8a4a3a"), hexc("b5684f")])
    for r in (33,):
        band = disk(c, cx, cy, r) & ~disk(c, cx, cy, r - 3)
        shade_sphere(c, band, cx, cy, r, [DARK, MID, LIGHT])
    # throat
    throat = disk(c, cx, cy, 17)
    bevel(c, throat, LIGHT, DARK, SHADOW, 2, raised=False)
    c.paint(disk(c, cx, cy, 12), HOLE)
    # charging bell
    bell = disk(c, cx, cy, 6)
    bevel(c, bell, LIGHT, MID, DARK, 1)
    c.save("blocks/blast-furnace.png")

    top = Canvas(96)
    top.paint(disk(top, cx, cy, 13) & ~disk(top, cx, cy, 6), GLOW_SOFT)
    top.paint(disk(top, cx, cy, 12) & ~disk(top, cx, cy, 8), GLOW)
    top.save("blocks/blast-furnace-top.png")


def arc_furnace():
    """3x3. A round vessel; three graphite electrodes in copper clamps drop through the roof in a triangle."""
    c = Canvas(96)
    frame(c, 96)
    cx = cy = 48
    vessel = disk(c, cx, cy, 40)
    c.paint(vessel, DARK)
    roof = disk(c, cx, cy, 34)
    shade_sphere(c, roof, cx, cy, 34, [MID, LIGHT])
    ex = [(36, 48), (56, 36), (56, 60)]
    for x, y in ex:
        c.paint(rect(c, x - 6, y - 6, x + 5, y + 5), COPPER_D)
        c.paint(rect(c, x - 6, y - 6, x + 1, y + 1), COPPER_M)
        c.paint(rect(c, x - 2, y - 2, x + 1, y + 1), hexc("45464e"))
    c.save("blocks/arc-furnace.png")

    top = Canvas(96)
    for x, y in ex:
        top.paint(rect(top, x - 2, y - 2, x + 1, y + 1), WHITE)
    top.paint(rect(top, 44, 44, 51, 51), hexc("d6e8ff"))
    top.save("blocks/arc-furnace-top.png")


def ore_iron():
    """Three ore overlays, transparent around the rocks like vanilla ores."""
    tones = [hexc("cf9a7a"), hexc("a8705a"), hexc("7c4d3e")]
    layouts = [
        [(8, 8, 5), (22, 10, 4), (14, 21, 6), (25, 24, 3)],
        [(9, 20, 5), (21, 8, 5), (23, 22, 4), (7, 7, 3)],
        [(16, 15, 6), (6, 26, 3), (26, 8, 3), (26, 25, 4), (7, 10, 3)],
    ]
    for i, lay in enumerate(layouts):
        c = Canvas(32, pixel=ITEM_PIXEL)
        rnd = random.Random(10 + i)
        for x, y, r in lay:
            pts = []
            for k in range(7):
                a = k / 7 * 2 * math.pi + rnd.uniform(-0.3, 0.3)
                rr = r * rnd.uniform(0.75, 1.1)
                pts.append((x + math.cos(a) * rr, y + math.sin(a) * rr))
            m = poly(c, pts)
            shade_sphere(c, m, x, y, r, [tones[1], tones[0]])
        c.save(f"blocks/ore-iron{i + 1}.png")


def steel_walls():
    light, hi, mid, dark, deep = STEEL
    pal = {"L": light, "h": hi, "M": mid, "D": dark, "d": deep}
    small = Canvas(32)
    art(small, [
        "DLLLLLLL",
        "DMMMMMML",
        "DMddddML",
        "DMdMMhML",
        "DMdMMhML",
        "DMdhhhML",
        "DMMMMMML",
        "DDDDDDDL",
    ], pal)
    small.save("blocks/steel/steel-wall.png")
    large = Canvas(64)
    art(large, [
        "DLLLLLLLLLLLLLLL",
        "DMMMMMMMMMMMMMML",
        "DMddddddddddddML",
        "DMdMMMMMMMMMMhML",
        "DMdMMMMMMMMMMhML",
        "DMdMMMMMMMMMMhML",
        "DMdMMMMMMMMMMhML",
        "DMdMMMMMMMMMMhML",
        "DMdMMMMMMMMMMhML",
        "DMdMMMMMMMMMMhML",
        "DMdMMMMMMMMMMhML",
        "DMdMMMMMMMMMMhML",
        "DMdMMMMMMMMMMhML",
        "DMdhhhhhhhhhhhML",
        "DMMMMMMMMMMMMMML",
        "DDDDDDDDDDDDDDDL",
    ], pal)
    large.save("blocks/steel/steel-wall-large.png")


def transmission_tower():
    """2x2. A lattice pylon seen from above: four legs, X bracing and an insulated crossarm."""
    inner = []
    for y in range(14):
        row = ""
        for x in range(14):
            if (x < 2 or x > 11) and (y < 2 or y > 11):
                row += "L"
            elif y == 6:
                row += "Y" if x in (0, 6, 7, 13) else "L"
            elif y == 7:
                row += "y" if x in (0, 6, 7, 13) else "D"
            elif x in (0, 13) or y in (0, 13) or x == y or x == 13 - y:
                row += "M"
            else:
                row += "S"
        inner.append(row)
    c = Canvas(64)
    art(c, framed(16, inner, ports=False), PAL)
    c.save("blocks/transmission-tower.png")


# ---------------------------------------------------------------- items

def item_iron_ore():
    c = Canvas(32, pixel=ITEM_PIXEL)
    m = poly(c, [(6, 12), (12, 5), (22, 4), (28, 10), (28, 20), (23, 27), (12, 28), (5, 22)])
    shade_sphere(c, m, 17, 16, 13, [hexc("8c5a48"), hexc("a8705a"), hexc("cf9a7a")])
    c.save("items/iron-ore.png")


def item_iron_concentrate():
    c = Canvas(32, pixel=ITEM_PIXEL)
    pile = poly(c, [(2, 28), (6, 19), (12, 9), (17, 5), (22, 10), (27, 18), (30, 28)])
    shade_sphere(c, pile, 17, 20, 14, [hexc("3c3c47"), hexc("4c4c58"), hexc("686877")])
    c.save("items/iron-concentrate.png")


def item_coke():
    c = Canvas(32, pixel=ITEM_PIXEL)
    m = poly(c, [(5, 14), (10, 6), (20, 4), (27, 9), (29, 19), (24, 27), (13, 29), (5, 23)])
    shade_sphere(c, m, 17, 16, 13, [hexc("4a4a50"), hexc("5f5f66"), hexc("8a8a92")])
    # porous: a few pits
    for x, y in ((12, 11), (20, 17), (11, 21)):
        c.paint(rect(c, x, y, x + 2, y + 2) & m, hexc("2e2e33"))
    c.save("items/coke.png")


def item_sinter():
    c = Canvas(32, pixel=ITEM_PIXEL)
    m = poly(c, [(4, 17), (8, 8), (17, 5), (26, 7), (29, 16), (25, 26), (14, 28), (6, 25)])
    shade_sphere(c, m, 17, 16, 13, [hexc("74443a"), hexc("8a5446"), hexc("b27460")])
    for x, y in ((12, 11), (20, 17), (11, 21)):
        c.paint(rect(c, x, y, x + 2, y + 2) & m, hexc("4a2a24"))
    c.save("items/sinter.png")


def ingot(tones, name, notch=False):
    """A cast ingot in the vanilla three-quarter view (like titanium and metaglass)."""
    c = Canvas(32, pixel=ITEM_PIXEL)
    light, top, side, dark = tones
    # a bar seen from the front and above: top face over a darker front face
    body = octo(c, 2, 8, 29, 25, 4)
    face = octo(c, 2, 8, 29, 19, 4)
    c.paint(body, dark)
    c.paint(body & rect(c, 22, 0, 31, 31), side)
    c.paint(face, top)
    c.paint(rim(face) & rect(c, 0, 0, 31, 9), light)
    c.paint(rim(face) & rect(c, 28, 0, 31, 31), light)
    if notch:
        # pig iron is cast in sand moulds with a notch across the middle
        c.paint(rect(c, 14, 10, 17, 17) & face, side)
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
    ys, xs = grid(c)
    nx, ny = (xs - cx) / R, (ys - cy) / R
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
    h = noise(3, 1) * 0.7 + noise(5, 2) * 0.3
    lat = np.abs(ny)
    light = np.clip(nx * 0.55 - ny * 0.45 + nz * 0.7, 0, 1)
    band = np.digitize(light, [0.4, 0.8]) + 1  # 1 dark .. 3 bright

    pal = {
        "ocean": [hexc("1f3a66"), hexc("2a4f86"), hexc("3767a6"), hexc("4a7fb8")],
        "shallow": [hexc("2a5a7a"), hexc("357599"), hexc("4891b4"), hexc("5ea9c8")],
        "green": [hexc("2f4f2c"), hexc("436b3a"), hexc("5a8a48"), hexc("74a85a")],
        "iron": [hexc("5a2c22"), hexc("7a3d2c"), hexc("a3553a"), hexc("c0643a")],
        "ice": [hexc("8fa2b4"), hexc("b4c4d2"), hexc("d6e0ea"), hexc("f0f5fa")],
    }
    kind = np.full((c.h, c.w), "ocean", object)
    kind[h > 0.52] = "green"
    kind[(h > 0.63)] = "iron"
    kind[lat + (h - 0.5) * 0.3 > 0.82] = "ice"
    for k, tones in pal.items():
        for b in range(1, 4):
            c.paint(inside & (kind == k) & (band == b), tones[b])
    # thin cloud streaks
    # atmosphere rim
    ring = (nx * nx + ny * ny <= (1 + c.p / R) ** 2) & ~inside
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
