#!/data/data/com.termux/files/usr/bin/python3
# Koiros: a keyboard theme that is one gradient of the pond's colour. GPL-3.0-or-later; see LICENSE.
#
#   koiros-keyboard.py RRGGBB OUT.zip
#
# Writes a Gboard image-theme package whose background is a smooth gradient
# through the hues around the pond's main colour: one colour flowing into its
# neighbours across the keys, not separate colours. Each time the pond's colour
# glides, the gradient is drawn again around the new hue, so it shifts slowly.
# The theme's style sheet and metadata are the ones Gboard itself writes for an
# image theme (tools/termux/gboard/); only the pictures are Koiros's.
import colorsys
import io
import math
import pathlib
import sys
import zipfile

from PIL import Image

HERE = pathlib.Path(__file__).resolve().parent
TEMPLATE = HERE / "gboard"
SPAN = 70  # degrees of hue across the keyboard, centred on the pond's colour


def neon(hue, value):
    """The lattice's neon at this hue: full saturation, eased only near red."""
    near_red = max(0.0, math.cos(math.radians(hue)))
    saturation = 1 - .3 * near_red ** 8
    r, g, b = colorsys.hsv_to_rgb((hue % 360) / 360, saturation, value)
    return int(r * 255 + .5), int(g * 255 + .5), int(b * 255 + .5)


def gradient(width, height, hue):
    """Hue flows left to right and a little downward; it dims toward the bottom so key labels stay clear."""
    small_w, small_h = 96, 64
    image = Image.new("RGB", (small_w, small_h))
    pixels = image.load()
    for y in range(small_h):
        v = y / (small_h - 1)
        for x in range(small_w):
            u = x / (small_w - 1)
            h = hue + SPAN * (u - .5) + .35 * SPAN * (v - .5)
            pixels[x, y] = neon(h, .62 - .2 * v)
    # Drawn small and enlarged smoothly: no banding, and fast on a phone.
    return image.resize((width, height), Image.BICUBIC)


def jpeg(image):
    out = io.BytesIO()
    image.save(out, "JPEG", quality=92)
    return out.getvalue()


def main(argv):
    if len(argv) != 3 or len(argv[1]) != 6:
        raise SystemExit("usage: koiros-keyboard.py RRGGBB OUT.zip")
    r, g, b = (int(argv[1][i:i + 2], 16) / 255 for i in (0, 2, 4))
    hue = colorsys.rgb_to_hsv(r, g, b)[0] * 360
    out = pathlib.Path(argv[2])
    tmp = out.with_suffix(".tmp")
    with zipfile.ZipFile(tmp, "w", zipfile.ZIP_STORED) as package:
        package.writestr("background", jpeg(gradient(728, 476, hue)))
        package.writestr("original_cropping", jpeg(gradient(910, 512, hue)))
        package.writestr("thumbnail", jpeg(gradient(384, 233, hue)))
        package.write(TEMPLATE / "metadata.binarypb", "metadata.binarypb")
        package.write(TEMPLATE / "style_sheet.binarypb", "style_sheet.binarypb")
    tmp.replace(out)


if __name__ == "__main__":
    main(sys.argv)
