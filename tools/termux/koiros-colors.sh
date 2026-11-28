#!/data/data/com.termux/files/usr/bin/sh
# Koiros: Termux follows the wallpaper's colours. GPL-3.0-or-later; see LICENSE.
#
#   koiros-colors.sh RRGGBB RRGGBB RRGGBB   recolour Termux from the pond's three colours
#   koiros-colors.sh --restore              put back the colours Termux had before
#
# Koiros runs this through Termux's RUN_COMMAND (needs allow-external-apps = true
# in ~/.termux/termux.properties). The pond's strongest colour tints the
# background, text and cursor; the sixteen terminal colours keep their usual
# meanings (red stays red) in the lattice's own neon.
set -eu
dir="$HOME/.termux"
colors="$dir/colors.properties"
kept="$dir/colors.properties.before-koiros"
mkdir -p "$dir"

reload() { command -v termux-reload-settings >/dev/null 2>&1 && termux-reload-settings || true; }

if [ "${1:-}" = "--restore" ]; then
    if [ -f "$kept" ]; then mv "$kept" "$colors"; else rm -f "$colors"; fi
    reload
    exit 0
fi

for c in "$@"; do
    case "$c" in [0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f]) ;; *) echo "not a colour: $c" >&2; exit 2;; esac
done
[ $# -ge 1 ] || { echo "usage: $0 RRGGBB [RRGGBB RRGGBB] | --restore" >&2; exit 2; }

# Keep the person's own colours once, the first time Koiros takes over.
if [ ! -f "$kept" ]; then
    if [ -f "$colors" ] && ! grep -q '^# Koiros' "$colors"; then cp "$colors" "$kept"; else : > "$kept"; fi
fi

tmp="$colors.koiros.$$"
awk -v seed="$1" -v second="${2:-$1}" -v third="${3:-$1}" '
function hx(s, i) { return index("0123456789abcdef", tolower(substr(s, i, 1))) - 1 }
function ch(s, k) { return hx(s, 2 * k + 1) * 16 + hx(s, 2 * k + 2) }
function out(r, g, b) {
    r = r < 0 ? 0 : r > 255 ? 255 : r; g = g < 0 ? 0 : g > 255 ? 255 : g; b = b < 0 ? 0 : b > 255 ? 255 : b
    return sprintf("#%02x%02x%02x", int(r + .5), int(g + .5), int(b + .5))
}
# Mix colour s into base (r, g, b) by t.
function mixed(s, r, g, b, t) { return out(r + (ch(s, 0) - r) * t, g + (ch(s, 1) - g) * t, b + (ch(s, 2) - b) * t) }
# The lattice neon for a hue: saturation eased only near red, at brightness v.
function neon(h, v,   s, f, i, p, q, u, r, g, b, red) {
    red = cos(h * 3.14159265 / 180); red = red > 0 ? red : 0
    s = 1 - .3 * red ^ 8
    h = (h % 360) / 60; i = int(h); f = h - i
    p = v * (1 - s); q = v * (1 - s * f); u = v * (1 - s * (1 - f))
    if (i == 0) { r = v; g = u; b = p } else if (i == 1) { r = q; g = v; b = p }
    else if (i == 2) { r = p; g = v; b = u } else if (i == 3) { r = p; g = q; b = v }
    else if (i == 4) { r = u; g = p; b = v } else { r = v; g = p; b = q }
    return out(r * 255, g * 255, b * 255)
}
BEGIN {
    printf "# Koiros: these colours follow the wallpaper (seed #%s). Turn off \"Theme follows the wallpaper\" in Koiros to restore yours.\n", seed
    print "background=" mixed(seed, 3, 7, 14, .07)
    print "foreground=" mixed(seed, 240, 244, 250, .16)
    print "cursor=#" seed
    print "color0=" mixed(seed, 22, 26, 34, .10)
    print "color8=" mixed(seed, 92, 98, 112, .22)
    print "color7=" mixed(second, 205, 210, 220, .12)
    print "color15=" mixed(third, 250, 252, 255, .08)
    # red, green, yellow, blue, magenta, cyan
    split("0 125 52 206 300 185", hue, " ")
    for (k = 1; k <= 6; k++) {
        print "color" k "=" neon(hue[k], .86)
        print "color" (k + 8) "=" neon(hue[k], 1)
    }
}' > "$tmp"
mv "$tmp" "$colors"
reload
