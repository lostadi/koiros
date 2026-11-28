/*
 * Koiros: the phone's theme follows the wallpaper's colours. GPL-3.0-or-later; see LICENSE.
 */
package org.ostadix.koilife;

/**
 * Finds the colours the pond is showing and decides when the phone's theme
 * should follow them.
 *
 * <p>Hues are gathered into a circular histogram weighted by how much of each
 * colour is drawn. The strongest peak becomes the theme's seed; the next two
 * peaks at least {@link #SEPARATION} degrees away become its companions (for
 * the terminal). Each peak's hue is the weighted circular mean of the hues
 * near it, so the seed moves smoothly as the pond's colours drift.
 *
 * <p>Every theme change restyles every open app, so a new theme is due only
 * when the seed has moved at least {@link #MIN_STEP} degrees and at least
 * {@link #MIN_INTERVAL_MS} has passed since the last one. No Android types: the
 * same code runs in the tests.
 */
final class ThemeFollower {
    static final int BUCKETS = 36;
    static final double SEPARATION = 50, MIN_STEP = 14;
    static final long MIN_INTERVAL_MS = 45000;
    private static final double SPREAD = 25;

    private final double[] weight = new double[BUCKETS], sin = new double[BUCKETS], cos = new double[BUCKETS];
    private double total;
    private double lastHue = Double.NaN;
    private long lastAt;

    void reset() {
        java.util.Arrays.fill(weight, 0); java.util.Arrays.fill(sin, 0); java.util.Arrays.fill(cos, 0);
        total = 0;
    }

    /** One drawn colour: an on-screen hue in degrees and how much of it shows. */
    void add(double hue, double amount) {
        if (!(amount > 0) || Double.isNaN(hue) || Double.isInfinite(hue)) return;
        double h = wrap(hue), r = Math.toRadians(h);
        int b = (int) (h * BUCKETS / 360) % BUCKETS;
        weight[b] += amount; sin[b] += amount * Math.sin(r); cos[b] += amount * Math.cos(r);
        total += amount;
    }

    /**
     * Writes up to three hues, strongest first, to out and returns how many were
     * found. Each is the circular mean of the colours within 25 degrees of a peak.
     */
    int peaks(double[] out) {
        if (total <= 0) return 0;
        int found = 0;
        boolean[] used = new boolean[BUCKETS];
        while (found < Math.min(3, out.length)) {
            int best = -1; double bestWeight = 0;
            for (int b = 0; b < BUCKETS; b++) {
                if (used[b]) continue;
                // A peak is judged with its neighbours, so a colour split across a
                // bucket edge is not undercounted.
                double w = weight[b] + .5 * (weight[(b + 1) % BUCKETS] + weight[(b + BUCKETS - 1) % BUCKETS]);
                if (w <= bestWeight) continue;
                double centre = (b + .5) * 360 / BUCKETS;
                boolean apart = true;
                for (int k = 0; k < found; k++) apart &= Math.abs(arc(centre - out[k])) >= SEPARATION;
                if (apart) { best = b; bestWeight = w; }
            }
            if (best < 0 || bestWeight < total * .02) break;
            double centre = (best + .5) * 360 / BUCKETS, s = 0, c = 0;
            for (int b = 0; b < BUCKETS; b++) {
                if (Math.abs(arc((b + .5) * 360 / BUCKETS - centre)) <= SPREAD) { s += sin[b]; c += cos[b]; used[b] = true; }
            }
            out[found++] = wrap(Math.toDegrees(Math.atan2(s, c)));
        }
        return found;
    }

    /** True when the theme should move to this seed hue now; remembers it if so. */
    boolean due(double hue, long nowMs) {
        if (Double.isNaN(hue)) return false;
        boolean first = Double.isNaN(lastHue);
        if (!first && (nowMs - lastAt < MIN_INTERVAL_MS || Math.abs(arc(hue - lastHue)) < MIN_STEP)) return false;
        lastHue = hue; lastAt = nowMs;
        return true;
    }

    /** Forget the last theme, so the next offer applies at once. */
    void forget() { lastHue = Double.NaN; }

    /** The lattice's own neon for a hue: full value, saturation eased only near red. */
    static int neon(double hue) {
        double h = wrap(hue);
        double nearRed = Math.max(0, Math.cos(Math.toRadians(h)));
        return hsv(h, 1 - .3 * Math.pow(nearRed, 8), 1);
    }

    static int hsv(double h, double s, double v) {
        h = wrap(h) / 60; int i = (int) Math.floor(h) % 6; double f = h - Math.floor(h);
        double p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
        double r, g, b;
        switch (i) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q;
        }
        return 0xFF000000 | channel(r) << 16 | channel(g) << 8 | channel(b);
    }

    static String hex(int rgb) { return String.format(java.util.Locale.ROOT, "%06X", rgb & 0xFFFFFF); }

    /**
     * Android's theme setting for a seed colour: Material You's vibrant style
     * keeps the most of the pond's neon, for both accents and backgrounds.
     */
    static String themeJson(int seed, long timestampMs) {
        String h = hex(seed);
        return "{\"_applied_timestamp\":" + timestampMs
            + ",\"android.theme.customization.color_both\":\"1\""
            + ",\"android.theme.customization.system_palette\":\"" + h + "\""
            + ",\"android.theme.customization.color_source\":\"preset\""
            + ",\"android.theme.customization.theme_style\":\"VIBRANT\""
            + ",\"android.theme.customization.accent_color\":\"" + h + "\"}";
    }

    private static int channel(double x) { return (int) Math.round(Math.max(0, Math.min(1, x)) * 255); }
    static double wrap(double d) { d %= 360; return d < 0 ? d + 360 : d; }
    static double arc(double d) { d = wrap(d); return d > 180 ? d - 360 : d; }
}
