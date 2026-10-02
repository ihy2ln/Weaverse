"""
Renders Weaverse's ambient "focus" backdrops: calm, seamless video loops (every motion is
periodic in the loop length, so the last frame flows into the first).

usage: python tools/render_ambient_videos.py <scene> <out.mp4> [--preview N]
Output goes in app/src/main/assets/videos/ambient/ (plus a 360x640 poster .jpg).
Set FFMPEG to the ffmpeg executable if it is not on PATH.
scenes: rain ocean forest space snow aurora
"""
import math
import os
import subprocess
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

W, H = 720, 1280
FPS = 24
SECONDS = 20
N = FPS * SECONDS
TAU = 2 * math.pi
FFMPEG = os.environ.get("FFMPEG", "ffmpeg")


# ---------------------------------------------------------------- helpers

def hexc(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], np.float32) / 255.0


def vgrad(stops, h=H, w=W):
    """Vertical gradient from [(pos, '#rrggbb'), ...]."""
    ys = np.linspace(0, 1, h, dtype=np.float32)
    out = np.zeros((h, 3), np.float32)
    for c in range(3):
        out[:, c] = np.interp(ys, [p for p, _ in stops], [hexc(col)[c] for _, col in stops])
    return np.repeat(out[:, None, :], w, axis=1)


def periodic_noise(h, w, beta, seed):
    """Tileable fractal noise in [0,1] (FFT-filtered white noise wraps at its edges)."""
    rng = np.random.default_rng(seed)
    fy = np.fft.fftfreq(h)[:, None]
    fx = np.fft.rfftfreq(w)[None, :]
    f = np.sqrt(fx * fx + fy * fy)
    f[0, 0] = 1.0
    spec = (rng.normal(size=f.shape) + 1j * rng.normal(size=f.shape)) / f ** beta
    spec[0, 0] = 0
    n = np.fft.irfft2(spec, s=(h, w)).astype(np.float32)
    n -= n.min()
    n /= n.max()
    return n


def glow_sprite(r, falloff=2.2):
    y, x = np.mgrid[-r:r + 1, -r:r + 1].astype(np.float32)
    d = np.sqrt(x * x + y * y) / r
    return np.clip(1 - d, 0, 1) ** falloff


def add_sprite(img, sprite, cx, cy, color, strength=1.0):
    """Additive paste of a glow sprite, clipped at the frame edges."""
    r = sprite.shape[0] // 2
    x0, y0 = int(cx) - r, int(cy) - r
    x1, y1 = x0 + sprite.shape[1], y0 + sprite.shape[0]
    sx0, sy0 = max(0, -x0), max(0, -y0)
    sx1 = sprite.shape[1] - max(0, x1 - img.shape[1])
    sy1 = sprite.shape[0] - max(0, y1 - img.shape[0])
    if sx0 >= sx1 or sy0 >= sy1:
        return
    region = img[y0 + sy0:y0 + sy1, x0 + sx0:x0 + sx1]
    region += sprite[sy0:sy1, sx0:sx1, None] * color[None, None, :] * strength


def to_u8(a):
    return (np.clip(a, 0, 1) * 255 + 0.5).astype(np.uint8)


def pil(a):
    return Image.fromarray(to_u8(a))


def from_pil(im):
    return np.asarray(im, dtype=np.float32) / 255.0


def blur2d(a, r):
    """Gaussian blur of a 2D float field in [0,1]."""
    return np.asarray(Image.fromarray(to_u8(a)).filter(ImageFilter.GaussianBlur(r)), np.float32) / 255.0


def pine(d, x, base, h, color, off=lambda f: 0.0, snow=None, tiers=7):
    """A layered pine: overlapping tier triangles, wide at the bottom, swaying by height."""
    d.rectangle([x + off(0) - h * 0.018, base - h * 0.12, x + off(0) + h * 0.018, base], fill=color)
    for j in range(tiers):
        f0 = 0.1 + 0.82 * j / tiers
        f1 = min(1.0, f0 + 0.26)
        y0 = base - f0 * h
        y1 = base - f1 * h
        w = h * 0.27 * (1 - (j / tiers) * 0.82)
        xb, xt = x + off(f0), x + off(f1)
        d.polygon([(xb - w, y0), (xb + w, y0), (xb + w * 0.15, y0 - 0.06 * h), (xt, y1), (xb - w * 0.15, y0 - 0.06 * h)], fill=color)
        if snow is not None:
            d.polygon([(xt, y1), (xt + w * 0.38, y1 + (y0 - y1) * 0.42), (xt, y1 + (y0 - y1) * 0.3), (xt - w * 0.38, y1 + (y0 - y1) * 0.42)], fill=snow)


def vignette(strength=0.45):
    y, x = np.mgrid[0:H, 0:W].astype(np.float32)
    d = np.sqrt(((x - W / 2) / (W * 0.75)) ** 2 + ((y - H / 2) / (H * 0.7)) ** 2)
    return (1 - strength * np.clip(d, 0, 1.4) ** 2)[:, :, None]


def film_grain(seed):
    rng = np.random.default_rng(seed)
    return [rng.normal(0, 0.012, (H, W, 1)).astype(np.float32) for _ in range(6)]


# ---------------------------------------------------------------- rain

def scene_rain():
    rng = np.random.default_rng(11)
    base = vgrad([(0, "#0a0f1c"), (0.55, "#141c2e"), (1, "#1d2638")])
    # Out-of-focus city lights behind the glass, in three groups that breathe out of step.
    groups = []
    palette = [hexc(c) for c in ("#ffb46b", "#ff9a6b", "#8fb7ff", "#ffd98e", "#ffc48a", "#9fd3ff")]
    for g in range(3):
        layer = Image.new("RGB", (W, H))
        d = ImageDraw.Draw(layer)
        for _ in range(11):
            x, y = rng.uniform(-40, W + 40), rng.uniform(H * 0.25, H * 1.05)
            r = rng.uniform(18, 64)
            col = tuple(int(v * 255 * rng.uniform(0.25, 0.5)) for v in palette[rng.integers(len(palette))])
            d.ellipse([x - r, y - r, x + r, y + r], fill=col)
        groups.append(from_pil(layer.filter(ImageFilter.GaussianBlur(26))))
    # Beads of water on the glass, lit from below.
    drops = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    dd = ImageDraw.Draw(drops)
    for _ in range(110):
        x, y, r = rng.uniform(0, W), rng.uniform(0, H), rng.uniform(2, 7)
        dd.ellipse([x - r, y - r, x + r, y + r], fill=(150, 170, 205, 45))
        dd.ellipse([x - r * 0.4, y - r * 0.6, x + r * 0.1, y - r * 0.1], fill=(235, 240, 255, 110))
    drops = drops.filter(ImageFilter.GaussianBlur(0.6))
    # Streaks: each travels a whole number of frame heights per loop, so it wraps cleanly.
    def streaks(count, speeds, lengths, alpha, width):
        return [dict(x=rng.uniform(-60, W + 60), y=rng.uniform(0, H), n=rng.integers(*speeds),
                     l=rng.uniform(*lengths), a=rng.integers(*alpha), w=width) for _ in range(count)]
    far = streaks(520, (5, 9), (18, 42), (35, 80), 1)
    near = streaks(90, (8, 12), (60, 120), (60, 120), 2)
    slant = 0.12
    vig = vignette(0.5)
    grain = film_grain(5)

    def frame(i):
        t = i / N
        img = base.copy()
        for g, layer in enumerate(groups):
            img += layer * (0.75 + 0.25 * math.sin(TAU * (t + g / 3)))
        rain = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        d = ImageDraw.Draw(rain)
        for s in far:
            y = (s["y"] + s["n"] * H * t) % (H + s["l"]) - s["l"]
            x = s["x"] + y * slant
            d.line([x, y, x + s["l"] * slant, y + s["l"]], fill=(190, 205, 235, s["a"]), width=1)
        nl = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        dn = ImageDraw.Draw(nl)
        for s in near:
            y = (s["y"] + s["n"] * H * t) % (H + s["l"]) - s["l"]
            x = s["x"] + y * slant
            dn.line([x, y, x + s["l"] * slant, y + s["l"]], fill=(210, 225, 250, s["a"]), width=2)
        nl = nl.filter(ImageFilter.GaussianBlur(1.2))
        out = pil(img).convert("RGBA")
        out = Image.alpha_composite(out, rain)
        out = Image.alpha_composite(out, nl)
        out = Image.alpha_composite(out, drops)
        return np.clip(from_pil(out.convert("RGB")) * vig + grain[i % 6], 0, 1)
    return frame


# ---------------------------------------------------------------- ocean

def scene_ocean():
    rng = np.random.default_rng(23)
    horizon = int(H * 0.52)
    sky = vgrad([(0, "#0b1030"), (0.35, "#2a2160"), (0.62, "#7a3f7a"), (0.85, "#e07a5f"), (1, "#f6b26b")], h=horizon)
    stars = np.zeros((horizon, W), np.float32)
    for _ in range(220):
        stars[rng.integers(0, int(horizon * 0.7)), rng.integers(0, W)] = rng.uniform(0.3, 1)
    stars = blur2d(stars, 0.7) * 2.2
    star_phase = periodic_noise(horizon, W, 0.2, 3) * TAU
    moon_x, moon_y, moon_r = W * 0.68, horizon * 0.38, 46
    moon = glow_sprite(int(moon_r * 4), 2.8)
    disc = (glow_sprite(int(moon_r), 0.08) > 0.02).astype(np.float32)
    # Sea: wave sums whose angular speeds are whole multiples of the loop.
    ys = np.arange(H - horizon, dtype=np.float32)[:, None]
    depth = (ys + 6) / (H - horizon)          # 0 at horizon → 1 at bottom
    xs = np.arange(W, dtype=np.float32)[None, :]
    persp = 1.0 / (0.04 + depth)              # far rows compress
    z = 1.0 / (depth + 0.035)                 # distance: large near the horizon
    waves = [(2.1, 0.0040, 1, 0.0, 1.0), (3.3, -0.0065, 2, 1.3, 0.6), (5.2, 0.0090, 3, 2.1, 0.35), (1.3, 0.0025, 1, 0.7, 0.8), (7.9, -0.012, 4, 0.4, 0.2)]
    sea_dark = hexc("#070f26")
    sea_light = hexc("#56609e")
    # Sparkle field rolled toward the viewer a whole number of times per loop.
    spark = periodic_noise(H - horizon, W, 0.35, 8) ** 6
    glitter_col = hexc("#ffd8a8")
    vig = vignette(0.35)
    grain = film_grain(9)

    def frame(i):
        t = i / N
        img = np.zeros((H, W, 3), np.float32)
        tw = 0.65 + 0.35 * np.sin(star_phase + TAU * 2 * t)
        img[:horizon] = sky + (stars * tw)[:, :, None]
        add_sprite(img, moon, moon_x, moon_y, hexc("#ffcf9a"), 0.55)
        r = disc.shape[0] // 2
        img[int(moon_y) - r:int(moon_y) + r + 1, int(moon_x) - r:int(moon_x) + r + 1] = np.maximum(
            img[int(moon_y) - r:int(moon_y) + r + 1, int(moon_x) - r:int(moon_x) + r + 1], disc[:, :, None] * hexc("#fff1dc"))
        slope = np.zeros((H - horizon, W), np.float32)
        for kz, kx, n, ph, a in waves:
            arg = kz * z + kx * (xs - W / 2) * (0.25 + depth) - TAU * n * t + ph
            slope += a * np.cos(arg)
        slope /= 3.0
        shade = 0.5 + 0.5 * slope
        sea = sea_dark + (sea_light - sea_dark) * (shade[:, :, None] * (0.35 + 0.65 * depth[:, :, None]))
        # Moon glitter: a column under the moon that widens toward the viewer.
        width = 18 + 210 * depth
        band = np.exp(-((xs - moon_x) / width) ** 2)
        rolled = np.roll(spark, int(round(t * 2 * (H - horizon))), axis=0)
        sparkle = np.clip((slope + 0.2) * 1.4, 0, 1) * rolled * 9
        sea += glitter_col * (band * sparkle * (0.5 + 0.9 * depth))[:, :, None]
        # Horizon haze.
        sea += hexc("#e9a07a") * (np.exp(-ys / 30) * 0.35)[:, :, None]
        img[horizon:] = sea
        return np.clip(img * vig + grain[i % 6], 0, 1)
    return frame


# ---------------------------------------------------------------- forest

def scene_forest():
    rng = np.random.default_rng(37)
    sky = vgrad([(0, "#1d2a48"), (0.4, "#3c4a7a"), (0.62, "#c97b6b"), (0.75, "#f2b880"), (1, "#f8d8a8")])
    sun = glow_sprite(260, 1.8)
    base = sky.copy()
    add_sprite(base, sun, W * 0.42, H * 0.6, hexc("#ffd2a0"), 0.65)
    hills = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    hd = ImageDraw.Draw(hills)
    for k, (col, y0, amp) in enumerate([((92, 84, 120, 255), 0.58, 40), ((60, 56, 92, 255), 0.64, 55)]):
        pts = [(x, H * y0 + amp * math.sin(x / (90 + 40 * k) + k) + 0.5 * amp * math.sin(x / 37 + 2 * k)) for x in range(-10, W + 20, 10)]
        hd.polygon(pts + [(W + 20, H), (-10, H)], fill=col)
    base_img = Image.alpha_composite(pil(base).convert("RGBA"), hills.filter(ImageFilter.GaussianBlur(1.5)))
    layers = []
    for depth, (count, color, ymin, ymax, hmin, hmax, sway) in enumerate([
        (14, (44, 40, 70), 0.70, 0.74, 160, 260, 4),
        (10, (26, 24, 44), 0.78, 0.84, 280, 420, 7),
        (6, (10, 10, 20), 0.92, 1.0, 520, 760, 11),
    ]):
        trees = []
        for _ in range(count):
            trees.append(dict(x=rng.uniform(-60, W + 60), base=H * rng.uniform(ymin, ymax), h=rng.uniform(hmin, hmax),
                              tiers=int(rng.integers(5, 9)), ph=rng.uniform(0, TAU), n=int(rng.integers(1, 3))))
        layers.append((trees, color, sway, depth))
    flies = [dict(cx=rng.uniform(0, W), cy=rng.uniform(H * 0.62, H * 0.95), rx=rng.uniform(20, 70), ry=rng.uniform(10, 40),
                  a=int(rng.integers(1, 3)), b=int(rng.integers(1, 4)), p=rng.uniform(0, TAU), q=rng.uniform(0, TAU),
                  blink=int(rng.integers(2, 5))) for _ in range(46)]
    fly = glow_sprite(14, 1.6)
    grass = [dict(x=x, h=rng.uniform(30, 90), ph=rng.uniform(0, TAU)) for x in np.arange(-10, W + 10, 7)]
    vig = vignette(0.45)
    grain = film_grain(13)

    def frame(i):
        t = i / N
        im = base_img.copy()
        for trees, color, sway, depth in layers:
            layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
            d = ImageDraw.Draw(layer)
            for tr in trees:
                amp = sway * (tr["h"] / 300) * math.sin(TAU * tr["n"] * t + tr["ph"])
                pine(d, tr["x"], tr["base"], tr["h"], color + (255,), off=lambda f, a=amp: a * f * f, tiers=tr["tiers"])
            if depth == 0:
                layer = layer.filter(ImageFilter.GaussianBlur(1.2))
            im = Image.alpha_composite(im, layer)
        g = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        gd = ImageDraw.Draw(g)
        for b in grass:
            sw = 9 * math.sin(TAU * t + b["ph"] + b["x"] / 120)
            gd.line([b["x"], H, b["x"] + sw, H - b["h"]], fill=(6, 6, 12, 255), width=3)
        im = Image.alpha_composite(im, g)
        img = from_pil(im.convert("RGB"))
        for f in flies:
            x = f["cx"] + f["rx"] * math.sin(TAU * f["a"] * t + f["p"])
            y = f["cy"] + f["ry"] * math.sin(TAU * f["b"] * t + f["q"])
            s = 0.35 + 0.65 * max(0.0, math.sin(TAU * f["blink"] * t + f["p"]))
            add_sprite(img, fly, x, y, hexc("#ffe58a"), 0.9 * s)
        return np.clip(img * vig + grain[i % 6], 0, 1)
    return frame


# ---------------------------------------------------------------- space

def scene_space():
    rng = np.random.default_rng(51)
    pad = 60
    hh, ww = H + 2 * pad, W + 2 * pad
    n1 = periodic_noise(hh, ww, 1.25, 1)
    n2 = periodic_noise(hh, ww, 1.25, 2)
    n3 = periodic_noise(hh, ww, 1.6, 3)
    deep = vgrad([(0, "#05040f"), (0.5, "#0b0820"), (1, "#05060f")], h=hh, w=ww)
    c1, c2, c3 = hexc("#7b2ff7"), hexc("#1fb6ff"), hexc("#ff4f9a")
    starfields = []
    for layer, (count, size, bright) in enumerate([(1500, 0.6, 0.8), (420, 1.0, 1.0), (110, 1.6, 1.0)]):
        im = np.zeros((hh, ww), np.float32)
        for _ in range(count):
            im[rng.integers(0, hh), rng.integers(0, ww)] = rng.uniform(0.4, 1.0) * bright
        im = blur2d(im, size) * (3 + layer * 2)
        starfields.append((im, rng.uniform(0, TAU, size=(hh, ww)).astype(np.float32) if layer == 2 else None))
    big = [dict(x=rng.uniform(0, W), y=rng.uniform(0, H), r=int(rng.integers(10, 22)), ph=rng.uniform(0, TAU),
                col=hexc(["#cfe3ff", "#ffe0c2", "#d9c8ff"][int(rng.integers(3))])) for _ in range(14)]
    sprites = {r: glow_sprite(r, 3.0) for r in range(10, 23)}
    vig = vignette(0.55)
    grain = film_grain(17)
    # A gas giant low in the frame: lit from the upper right, bands that turn once per loop.
    pr, pcx, pcy = 520, W * 0.1, H * 1.12
    py, px = np.mgrid[0:H, 0:W].astype(np.float32)
    pd = np.sqrt((px - pcx) ** 2 + (py - pcy) ** 2) / pr
    pmask = np.clip((1 - pd) * 60, 0, 1)
    nz = np.sqrt(np.clip(1 - pd ** 2, 0, 1))
    light = np.clip(((px - pcx) * 0.55 + (pcy - py) * 0.8) / pr + nz * 0.5, 0, 1)
    lat = (py - pcy) / pr
    rim = np.clip(1 - np.abs(pd - 1) * 25, 0, 1) * np.clip(((px - pcx) * 0.5 + (pcy - py)) / pr, 0, 1)
    pcol_a, pcol_b = hexc("#3a2a6a"), hexc("#d07a8a")

    def frame(i):
        t = i / N
        # The nebula breathes between two shapes and drifts in a slow ellipse, all periodic.
        m = 0.5 - 0.5 * math.cos(TAU * t)
        neb = n1 * (1 - m) + n2 * m
        dx = int(round(pad * 0.8 * math.sin(TAU * t)))
        dy = int(round(pad * 0.5 * math.cos(TAU * t)))
        img = deep.copy()
        mix = np.clip((neb - 0.36) * 2.0, 0, 1) ** 1.4
        tint = c1 * (1 - n3[:, :, None]) + c2 * n3[:, :, None]
        img += tint * mix[:, :, None] * 0.95
        img += c3 * (np.clip((n3 - 0.55) * 3, 0, 1) ** 2 * mix)[:, :, None] * 0.7
        for layer, (field, ph) in enumerate(starfields):
            s = layer + 1
            ox = int(round(pad * 0.3 * s * math.sin(TAU * t)))
            oy = int(round(pad * 0.2 * s * math.cos(TAU * t)))
            f = np.roll(field, (oy, ox), axis=(0, 1))
            if ph is not None:
                f = f * (0.6 + 0.4 * np.sin(np.roll(ph, (oy, ox), axis=(0, 1)) + TAU * 3 * t))
            img += f[:, :, None] * hexc("#e6ecff")[None, None, :]
        img = img[pad + dy:pad + dy + H, pad + dx:pad + dx + W]
        img = np.ascontiguousarray(img)
        bands = 0.5 + 0.5 * np.sin(lat * 26 + 0.8 * np.sin(lat * 7 + TAU * t) + 0.6 * np.sin((px - pcx) / pr * 3 - TAU * t))
        planet = (pcol_a * (1 - bands[:, :, None]) + pcol_b * bands[:, :, None]) * (0.12 + 0.9 * light[:, :, None] ** 1.3)
        img = img * (1 - pmask[:, :, None]) + planet * pmask[:, :, None] + hexc("#ffb3c8") * (rim * 0.9)[:, :, None]
        for b in big:
            tw = 0.55 + 0.45 * math.sin(TAU * 2 * t + b["ph"])
            add_sprite(img, sprites[b["r"]], b["x"], b["y"], b["col"], 0.8 * tw)
        # One slow shooting star per loop, gone well before the loop point.
        if 0.3 < t < 0.42:
            p = (t - 0.3) / 0.12
            sx, sy = W * (0.15 + 0.6 * p), H * (0.12 + 0.18 * p)
            for k in range(26):
                add_sprite(img, sprites[10], sx - k * 9, sy - k * 2.7, hexc("#ffffff"), 0.25 * (1 - k / 26) * math.sin(math.pi * p))
        return np.clip(img * vig + grain[i % 6], 0, 1)
    return frame


# ---------------------------------------------------------------- snow

def scene_snow():
    rng = np.random.default_rng(61)
    base = vgrad([(0, "#0d1528"), (0.55, "#22335a"), (0.8, "#4b6390"), (1, "#a8bbd8")])
    moon = glow_sprite(300, 2.2)
    add_sprite(base, moon, W * 0.25, H * 0.2, hexc("#9fb4e0"), 0.35)
    scene = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(scene)
    for k, (col, ymin, hmin, hmax, count) in enumerate([((58, 76, 110, 255), 0.66, 120, 220, 18), ((30, 40, 66, 255), 0.74, 220, 360, 12)]):
        for _ in range(count):
            x, b, h = rng.uniform(-40, W + 40), H * rng.uniform(ymin, ymin + 0.05), rng.uniform(hmin, hmax)
            pine(d, x, b, h, col, snow=(205, 220, 245, 230), tiers=int(rng.integers(6, 9)))
    ground = [(x, H * 0.86 + 18 * math.sin(x / 140) + 8 * math.sin(x / 53)) for x in range(-10, W + 20, 10)]
    d.polygon(ground + [(W + 20, H), (-10, H)], fill=(196, 210, 236, 255))
    back = Image.alpha_composite(pil(base).convert("RGBA"), scene.filter(ImageFilter.GaussianBlur(0.8)))
    back_arr = from_pil(back.convert("RGB"))
    flakes = []
    for layer, (count, r, n, sway, a) in enumerate([(380, 1.2, 1, 10, 0.55), (180, 2.2, 1, 18, 0.8), (50, 4.5, 2, 30, 0.9)]):
        for _ in range(count):
            flakes.append(dict(x=rng.uniform(0, W), y=rng.uniform(0, H), r=r * rng.uniform(0.7, 1.3), n=n,
                               sw=sway, ph=rng.uniform(0, TAU), a=a, layer=layer, k=int(rng.integers(1, 3))))
    sprites = [glow_sprite(3, 1.4), glow_sprite(5, 1.4), glow_sprite(9, 1.2)]
    vig = vignette(0.4)
    grain = film_grain(19)

    def frame(i):
        t = i / N
        img = back_arr.copy()
        for f in flakes:
            y = (f["y"] + f["n"] * H * t) % (H + 20) - 10
            x = (f["x"] + f["sw"] * math.sin(TAU * f["k"] * t + f["ph"])) % W
            add_sprite(img, sprites[f["layer"]], x, y, hexc("#f2f6ff"), f["a"])
        return np.clip(img * vig + grain[i % 6], 0, 1)
    return frame


# ---------------------------------------------------------------- aurora

def scene_aurora():
    rng = np.random.default_rng(71)
    lake = int(H * 0.76)
    sky = vgrad([(0, "#02040c"), (0.5, "#06122a"), (1, "#0c2440")], h=lake)
    stars = np.zeros((lake, W), np.float32)
    for _ in range(500):
        stars[rng.integers(0, lake), rng.integers(0, W)] = rng.uniform(0.3, 1)
    stars = blur2d(stars, 0.6) * 2.4
    star_ph = rng.uniform(0, TAU, size=(lake, W)).astype(np.float32)
    streak = periodic_noise(64, W, 1.1, 5)[0]          # vertical rays across x (tileable)
    xs = np.arange(W, dtype=np.float32)
    ys = np.arange(lake, dtype=np.float32)[:, None]
    green, teal, violet = hexc("#3dffa0"), hexc("#23d5e6"), hexc("#a26bff")
    mountains = Image.new("L", (W, lake), 0)
    md = ImageDraw.Draw(mountains)
    ridge_y, pts, yv = [], [], 0.0
    for x in range(-10, W + 20, 6):
        yv = 0.85 * yv + rng.normal(0, 9)
        pts.append((x, lake - 90 - 70 * abs(math.sin(x / 210 + 0.6)) - 45 * max(0, math.sin(x / 95 + 2)) + yv))
    md.polygon(pts + [(W + 20, lake), (-10, lake)], fill=255)
    mask = np.asarray(mountains, np.float32)[:, :, None] / 255.0
    ridge = hexc("#050a14")
    vig = vignette(0.45)
    grain = film_grain(23)

    def curtain(t, y_center, amp, k1, k2, n1, n2, ph, height, strength, cols):
        yc = y_center + amp * np.sin(k1 * xs + TAU * n1 * t + ph) + amp * 0.5 * np.sin(k2 * xs - TAU * n2 * t + 2 * ph)
        shift = int(round(40 * math.sin(TAU * t + ph)))
        rays = 0.45 + 0.75 * np.roll(streak, shift) ** 1.5
        dy = ys - yc[None, :]
        # Bright lower edge, long soft fade upward.
        prof = np.where(dy > 0, np.exp(-(dy / 34) ** 2), np.exp(-(dy / height) ** 2 * 1.0))
        inten = prof * rays[None, :] * strength
        hue = np.clip(-dy / (height * 1.4), 0, 1)
        col = cols[0] * (1 - hue[:, :, None]) + cols[1] * hue[:, :, None]
        return col * inten[:, :, None]

    def frame(i):
        t = i / N
        img = sky + (stars * (0.6 + 0.4 * np.sin(star_ph + TAU * 2 * t)))[:, :, None]
        img = img + curtain(t, lake * 0.72, 60, 0.006, 0.017, 1, 1, 0.0, 380, 0.85, (green, violet))
        img = img + curtain(t, lake * 0.58, 45, 0.009, 0.013, 1, 2, 2.0, 300, 0.55, (teal, violet))
        img = img * (1 - mask) + ridge * mask
        out = np.zeros((H, W, 3), np.float32)
        out[:lake] = img
        refl = img[::-1][: H - lake]
        refl = from_pil(pil(refl).filter(ImageFilter.GaussianBlur(3)))
        rip = 0.75 + 0.1 * np.sin(np.arange(H - lake, dtype=np.float32)[:, None] * 0.35 - TAU * 3 * t)
        out[lake:] = refl * 0.85 * rip[:, :, None] + hexc("#02060e") * 0.3
        return np.clip(out * vig + grain[i % 6], 0, 1)
    return frame


SCENES = dict(rain=scene_rain, ocean=scene_ocean, forest=scene_forest, space=scene_space, snow=scene_snow, aurora=scene_aurora)


def main():
    name, out = sys.argv[1], sys.argv[2]
    frame = SCENES[name]()
    if "--preview" in sys.argv:
        k = int(sys.argv[sys.argv.index("--preview") + 1])
        for j in range(k):
            Image.fromarray(to_u8(frame(j * N // k))).save(out.replace(".mp4", f"_{j}.jpg"), quality=88)
        return
    proc = subprocess.Popen([
        FFMPEG, "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS),
        "-i", "-", "-c:v", "libx264", "-preset", "slow", "-crf", "25", "-pix_fmt", "yuv420p",
        "-movflags", "+faststart", "-an", out,
    ], stdin=subprocess.PIPE)
    for i in range(N):
        a = to_u8(frame(i))
        if i == 0:
            Image.fromarray(a).save(out.replace(".mp4", ".jpg"), quality=86)
        proc.stdin.write(a.tobytes())
    proc.stdin.close()
    proc.wait()


if __name__ == "__main__":
    main()
