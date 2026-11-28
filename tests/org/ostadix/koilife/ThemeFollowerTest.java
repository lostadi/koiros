package org.ostadix.koilife;

/** The phone's theme follows the pond's colours, smoothly and rarely. */
public final class ThemeFollowerTest {
    private static int checks;

    public static void main(String[] args) {
        strongestColourLeads();
        peaksAreApartAndWrapAroundRed();
        themeMovesOnlyWhenColoursMove();
        seedGlidesThroughTheHuesBetween();
        neonMatchesTheLattice();
        jsonIsTheSystemsOwnFormat();
        System.out.println("ThemeFollowerTest passed: " + checks + " checks");
    }

    private static void strongestColourLeads() {
        ThemeFollower f = new ThemeFollower();
        for (int i = 0; i < 300; i++) f.add(190 + (i % 7) - 3, 1);   // cyan, most
        for (int i = 0; i < 120; i++) f.add(320 + (i % 5) - 2, 1);   // pink
        for (int i = 0; i < 60; i++) f.add(95, 1);                   // green, least
        double[] h = new double[3];
        check(f.peaks(h) == 3, "Three colours were not found");
        near(h[0], 190, 1, "Seed is not the strongest colour");
        near(h[1], 320, 1, "Second is not the next colour");
        near(h[2], 95, 1, "Third is not the last colour");
        f.reset();
        check(f.peaks(h) == 0, "An empty pond gave colours");
        f.add(Double.NaN, 1); f.add(10, -1); f.add(10, 0);
        check(f.peaks(h) == 0, "Invalid colours were counted");
    }

    private static void peaksAreApartAndWrapAroundRed() {
        ThemeFollower f = new ThemeFollower();
        for (int i = 0; i < 200; i++) f.add(i % 2 == 0 ? 356 : 4, 1);   // red split across 0
        for (int i = 0; i < 150; i++) f.add(20, 1);                     // too close to red to count apart
        double[] h = new double[3];
        int n = f.peaks(h);
        check(Math.abs(ThemeFollower.arc(h[0] - 6)) < 8, "Red across 0 degrees was not one colour: " + h[0]);
        for (int k = 1; k < n; k++) check(Math.abs(ThemeFollower.arc(h[k] - h[0])) >= ThemeFollower.SEPARATION, "Peaks too close");
    }

    private static double[] h(double a, double b, double c) { return new double[] {a, b, c}; }

    private static void themeMovesOnlyWhenColoursMove() {
        ThemeFollower f = new ThemeFollower();
        long gap = ThemeFollower.MIN_INTERVAL_MS;
        double[] out = new double[3];
        check(f.next(h(200, 300, 60), 0, out) && out[0] == 200 && out[1] == 300, "The first theme was not applied");
        check(!f.next(h(240, 300, 60), 1000, out), "Theme changed again within the interval");
        check(!f.next(h(203, 302, 61), gap + 1, out), "A tiny drift changed the theme");
        check(f.next(h(240, 300, 62), gap + 2, out), "A big change was not taken");
        near(out[0], 200 + ThemeFollower.MAX_STEP, 1e-9, "A big change was not one small step");
        near(out[2], 62, 1e-9, "A small change elsewhere was not carried along");
        check(f.next(h(200, 360, 62), 2 * gap + 3, out), "The second colour alone did not move the theme");
        near(out[1], 300 + ThemeFollower.MAX_STEP, 1e-9, "The second colour's step was not small");
        ThemeFollower g = new ThemeFollower();
        g.next(h(355, 100, 220), 0, out);
        g.next(h(15, 100, 220), gap, out);
        near(out[0], 355 + ThemeFollower.MAX_STEP - 360, 1e-9, "Step across red went the long way");
        f.forget();
        check(f.next(h(5, 6, 7), 3 * gap, out) && out[0] == 5, "Turning on again did not apply at once");
        check(!new ThemeFollower().next(h(Double.NaN, 1, 2), 0, out), "No colour changed the theme");
    }

    private static void seedGlidesThroughTheHuesBetween() {
        ThemeFollower f = new ThemeFollower();
        double[] out = new double[3];
        f.glide(h(100, 0, 0), 1, 0, out);
        near(out[0], 100, 1e-9, "The glide did not start at the first colour");
        near(out[1], 220, 1e-9, "A one-colour pond did not lend a second, different colour");
        near(out[2], 340, 1e-9, "A one-colour pond did not lend a third, different colour");
        f.glide(h(160, 220, 340), 3, 5000, out);
        check(out[0] > 100 && out[0] < 104, "A new colour was not eased toward: " + out[0]);
        double previous = out[0];
        for (long t = 10000; t <= 900000; t += 5000) {
            f.glide(h(160, 220, 340), 3, t, out);
            check(out[0] >= previous - 1e-9 && out[0] <= 160 + 1e-9, "Glide overshot or went back");
            previous = out[0];
        }
        near(out[0], 160, 1, "The glide never arrived");
        // Peaks that swap order are still followed by the hue nearest them.
        f.glide(h(340, 160, 220), 3, 905000, out);
        near(out[1], 220, 1, "A hue jumped to another colour when the order changed");
        // The theme seen through glide and steps: never more than a small step at once.
        ThemeFollower t = new ThemeFollower();
        double[] g = new double[3], w = new double[3], last = null;
        double largest = 0;
        for (long now = 0; now < 1800000; now += 5000) {
            double[] pond = now < 300000 ? h(30, 110, 190) : h(80, 160, 250);
            t.glide(pond, 3, now, g);
            if (!t.next(g, now, w)) continue;
            if (last != null) for (int k = 0; k < 3; k++) largest = Math.max(largest, Math.abs(ThemeFollower.arc(w[k] - last[k])));
            last = w.clone();
        }
        check(largest <= ThemeFollower.MAX_STEP + 1e-9, "The theme leapt " + largest + " degrees");
        near(last[0], 80, ThemeFollower.MIN_STEP + 1, "The theme did not reach the new colour");
        near(last[2], 250, ThemeFollower.MIN_STEP + 1, "The third colour did not follow");
    }

    private static void neonMatchesTheLattice() {
        check(ThemeFollower.neon(120) == 0xFF00FF00, "Green is not full neon");
        check(ThemeFollower.neon(240) == 0xFF0000FF, "Blue is not full neon");
        check(ThemeFollower.neon(0) == 0xFFFF4D4D, "Red was not eased as the lattice eases it: " + Integer.toHexString(ThemeFollower.neon(0)));
        check(ThemeFollower.hex(0xFF00E5FF).equals("00E5FF"), "Hex");
    }

    private static void jsonIsTheSystemsOwnFormat() {
        String json = ThemeFollower.themeJson(0xFF00E5FF, 123);
        check(json.startsWith("{\"_applied_timestamp\":123,"), "Timestamp missing");
        check(json.contains("\"android.theme.customization.system_palette\":\"00E5FF\""), "Seed missing");
        check(json.contains("\"android.theme.customization.accent_color\":\"00E5FF\""), "Accent missing");
        check(json.contains("\"android.theme.customization.color_source\":\"preset\""), "Source missing");
        check(json.contains("\"android.theme.customization.theme_style\":\"VIBRANT\""), "Style missing");
    }

    private static void near(double actual, double expected, double tolerance, String message) {
        checks++;
        if (!(Math.abs(actual - expected) <= tolerance)) throw new AssertionError(message + ": " + actual + " vs " + expected);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
