#!/data/data/com.termux/files/usr/bin/python3
# Koiros: the system's secondary and tertiary colours from the pond's. GPL-3.0-or-later; see LICENSE.
#
#   koiros-palette.py RRGGBB RRGGBB > overlay.xml
#
# Android's Material You engine grows a whole theme from one seed, so on its
# own every colour is a shade of the same hue. This writes a fabricated-overlay
# file that gives the secondary roles the pond's second colour and the
# tertiary roles its third, each as a tonal palette: the same hue at every
# lightness from white to black, as vivid as the screen can show at that
# lightness. Tones follow CIELAB lightness, as Material's do.
import math
import sys

TONES = {0: 100, 10: 99, 50: 95, 100: 90, 200: 80, 300: 70, 400: 60, 500: 50,
         600: 40, 700: 30, 800: 20, 900: 10, 1000: 0}
# Material roles: (light tone, dark tone). Fixed roles are the same in both.
ROLES = {"": (40, 80), "on_": (100, 20), "_container": (90, 30), "on__container": (10, 90)}
FIXED = {"{r}_fixed": 90, "{r}_fixed_dim": 80, "on_{r}_fixed": 10, "on_{r}_fixed_variant": 30}


def linear(c):
    c /= 255
    return c / 12.92 if c <= .04045 else ((c + .055) / 1.055) ** 2.4


def gamma(c):
    c = c * 12.92 if c <= .0031308 else 1.055 * c ** (1 / 2.4) - .055
    return c * 255


WHITE = (.95047, 1.0, 1.08883)


def to_lab(rgb):
    r, g, b = (linear(v) for v in rgb)
    xyz = (.4124 * r + .3576 * g + .1805 * b, .2126 * r + .7152 * g + .0722 * b, .0193 * r + .1192 * g + .9505 * b)
    f = [v / w for v, w in zip(xyz, WHITE)]
    f = [t ** (1 / 3) if t > 216 / 24389 else (24389 / 27 * t + 16) / 116 for t in f]
    return 116 * f[1] - 16, 500 * (f[0] - f[1]), 200 * (f[1] - f[2])


def from_lch(lightness, chroma, hue):
    a, b = chroma * math.cos(hue), chroma * math.sin(hue)
    fy = (lightness + 16) / 116
    fx, fz = fy + a / 500, fy - b / 200
    inv = lambda t: t ** 3 if t ** 3 > 216 / 24389 else (116 * t - 16) / (24389 / 27)
    x, y, z = (inv(t) * w for t, w in zip((fx, fy, fz), WHITE))
    rgb = (3.2406 * x - 1.5372 * y - .4986 * z, -.9689 * x + 1.8758 * y + .0415 * z, .0557 * x - .2040 * y + 1.0570 * z)
    return tuple(gamma(max(0.0, v)) for v in rgb), all(-1e-4 <= v <= 1 + 1e-4 for v in rgb)


def tone(source, t):
    """The source's hue at CIELAB lightness t, with as much of its chroma as fits on screen."""
    if t >= 100:
        return 0xFFFFFFFF
    if t <= 0:
        return 0xFF000000
    _, a, b = to_lab(source)
    hue, wanted = math.atan2(b, a), math.hypot(a, b)
    low, high = 0.0, wanted
    for _ in range(30):
        mid = (low + high) / 2
        if from_lch(t, mid, hue)[1]:
            low = mid
        else:
            high = mid
    rgb, _ = from_lch(t, low, hue)
    r, g, b = (max(0, min(255, round(v))) for v in rgb)
    return 0xFF000000 | r << 16 | g << 8 | b


def names(role, source):
    """Every resource name for one role (secondary or tertiary) and its colour."""
    out = {}
    accent = {"secondary": 2, "tertiary": 3}[role]
    for step, t in TONES.items():
        out[f"system_accent{accent}_{step}"] = tone(source, t)
    for pattern, (light, dark) in ROLES.items():
        name = pattern.replace("on_", "on_" + role) if pattern.startswith("on_") else role + pattern
        if pattern == "on__container":
            name = f"on_{role}_container"
        for mode, t in (("light", light), ("dark", dark)):
            out[f"system_{name}_{mode}"] = tone(source, t)
            for prefix in ("gm3_sys_color", "m3_sys_color"):
                out[f"{prefix}_{mode}_{name}"] = tone(source, t)
                out[f"{prefix}_dynamic_{mode}_{name}"] = tone(source, t)
    for pattern, t in FIXED.items():
        out["system_" + pattern.format(r=role)] = tone(source, t)
    key = 0xFF000000 | source[0] << 16 | source[1] << 8 | source[2]
    for mode in ("light", "dark"):
        out[f"system_palette_key_color_{role}_{mode}"] = key
        for prefix in ("gm3_sys_color", "m3_sys_color"):
            out[f"{prefix}_{mode}_palette_key_color_{role}"] = key
            out[f"{prefix}_dynamic_{mode}_palette_key_color_{role}"] = key
    return out


def parse(text):
    if len(text) != 6 or any(c not in "0123456789abcdefABCDEF" for c in text):
        raise SystemExit("not a colour: " + text)
    return tuple(int(text[i:i + 2], 16) for i in (0, 2, 4))


def main(argv):
    if len(argv) != 3:
        raise SystemExit("usage: koiros-palette.py RRGGBB RRGGBB")
    colours = {**names("secondary", parse(argv[1])), **names("tertiary", parse(argv[2]))}
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<overlay>"]
    lines += [f'  <item target="color/{name}" value="0x{value:08x}" />' for name, value in sorted(colours.items())]
    lines.append("</overlay>")
    print("\n".join(lines))


if __name__ == "__main__":
    main(sys.argv)
