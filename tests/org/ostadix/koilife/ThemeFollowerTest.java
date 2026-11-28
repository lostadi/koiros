package org.ostadix.koilife;

/** The phone's theme follows the pond's colours, smoothly and rarely. */
public final class ThemeFollowerTest {
    private static int checks;

    public static void main(String[] args) {
        strongestColourLeads();
        peaksAreApartAndWrapAroundRed();
        themeMovesOnlyWhenColoursMove();
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

    private static void themeMovesOnlyWhenColoursMove() {
        ThemeFollower f = new ThemeFollower();
        check(f.due(200, 0), "The first theme was not applied");
        check(!f.due(240, 1000), "Theme changed again within the interval");
        check(!f.due(205, ThemeFollower.MIN_INTERVAL_MS + 1), "A small drift changed the theme");
        check(f.due(230, ThemeFollower.MIN_INTERVAL_MS + 2), "A real change after the interval was not applied");
        check(f.due(359, 2 * ThemeFollower.MIN_INTERVAL_MS + 10), "A change was not applied");
        check(!f.due(5, 3 * ThemeFollower.MIN_INTERVAL_MS + 20), "Drift across red counted as a big change");
        f.forget();
        check(f.due(5, 3 * ThemeFollower.MIN_INTERVAL_MS + 21), "Turning on again did not apply at once");
        check(!f.due(Double.NaN, 10 * ThemeFollower.MIN_INTERVAL_MS), "No colour changed the theme");
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
