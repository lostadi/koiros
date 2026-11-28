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
 * <p>Every theme change restyles every open app, so the seed glides toward
 * the pond's colour and the theme follows in small, infrequent steps. No Android types: the
 * same code runs in the tests.
 */
final class ThemeFollower {
    static final int BUCKETS = 36;
    static final double SEPARATION = 50, MIN_STEP = 8, MAX_STEP = 14, GLIDE_SECONDS = 180;
    // Every change restyles every open app, the costliest part for the battery:
    // at most one a minute, and only while the wallpaper is showing.
    static final long MIN_INTERVAL_MS = 60000;
    private static final double SPREAD = 25;

    private final double[] weight = new double[BUCKETS], sin = new double[BUCKETS], cos = new double[BUCKETS];
    private double total;
    private final double[] last = {Double.NaN, Double.NaN, Double.NaN};
    private final double[] glided = {Double.NaN, Double.NaN, Double.NaN};
    private long lastAt, glideAt;

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

    /**
     * Three hues glide toward the pond's three strongest colours rather than
     * jumping to them: each follows the nearest peak (within 90 degrees) with a
     * two-minute time constant, so a new colour arrives as a slow gradient
     * through the hues between. A pond showing fewer than three colours lends
     * its missing ones from a triad around the strongest, so the theme is never
     * a single hue. Writes the gliding hues to out.
     */
    void glide(double[] peaks, int found, long nowMs, double[] out) {
        if (Double.isNaN(glided[0])) {
            if (found == 0) { System.arraycopy(glided, 0, out, 0, 3); return; }
            for (int k = 0; k < 3; k++) glided[k] = wrap(k < found ? peaks[k] : peaks[0] + (k == 1 ? 120 : -120));
            glideAt = nowMs;
        } else {
            double ease = -Math.expm1(-Math.max(0, (nowMs - glideAt) / 1000.0) / GLIDE_SECONDS);
            glideAt = nowMs;
            boolean[] taken = new boolean[3];
            for (int k = 0; k < 3; k++) {
                int nearest = -1; double distance = 90;
                for (int p = 0; p < found; p++) {
                    double d = Math.abs(arc(peaks[p] - glided[k]));
                    if (!taken[p] && d <= distance) { nearest = p; distance = d; }
                }
                if (nearest < 0) continue;
                taken[nearest] = true;
                glided[k] = wrap(glided[k] + arc(peaks[nearest] - glided[k]) * ease);
            }
        }
        System.arraycopy(glided, 0, out, 0, 3);
    }

    /**
     * The next theme hues: true, with out set, when one is due. Themes move in
     * small steps: each hue at most {@link #MAX_STEP} degrees, only once some
     * hue has moved {@link #MIN_STEP}, and at most every {@link #MIN_INTERVAL_MS}.
     * Each change is slight, and colours shift through every hue between.
     */
    boolean next(double[] hues, long nowMs, double[] out) {
        if (Double.isNaN(hues[0])) return false;
        if (Double.isNaN(last[0])) {
            for (int k = 0; k < 3; k++) last[k] = wrap(hues[k]);
        } else {
            if (nowMs - lastAt < MIN_INTERVAL_MS) return false;
            double largest = 0;
            for (int k = 0; k < 3; k++) largest = Math.max(largest, Math.abs(arc(hues[k] - last[k])));
            if (largest < MIN_STEP) return false;
            for (int k = 0; k < 3; k++) {
                last[k] = wrap(last[k] + Math.max(-MAX_STEP, Math.min(MAX_STEP, arc(hues[k] - last[k]))));
            }
        }
        lastAt = nowMs;
        System.arraycopy(last, 0, out, 0, 3);
        return true;
    }

    /** Forget the last theme, so the next offer applies at once. */
    void forget() {
        java.util.Arrays.fill(last, Double.NaN);
        java.util.Arrays.fill(glided, Double.NaN);
    }

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
