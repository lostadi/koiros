package org.ostadix.koilife;

/** The one shared Life world: seeding, local sensing, and the colour it carries. */
public final class SharedWorldTest {
    private static int checks;

    public static void main(String[] args) {
        seedingFillsOnlyTheDisc();
        seedingWrapsAndIsDeterministic();
        localSamplesMatchCounts();
        tintPaintsFallsOffAndSaturates();
        tintMixesHueOnTheCircle();
        tintTravelsOnlyAlongLife();
        tintFades();
        System.out.println("SharedWorldTest passed: " + checks + " checks");
    }

    private static LifeField empty(int columns, int rows, long seed) {
        LifeField field = new LifeField(columns, rows, seed);
        field.setCells(new boolean[columns * rows]);
        return field;
    }

    private static void seedingFillsOnlyTheDisc() {
        LifeField field = empty(40, 30, 3L);
        int born = field.seedDisc((20 + .5) / 40.0, (15 + .5) / 30.0, 3.0, 1.0);
        int inside = 0;
        for (int y = 0; y < 30; y++) for (int x = 0; x < 40; x++) {
            int dx = x - 20, dy = y - 15;
            boolean disc = dx * dx + dy * dy <= 9;
            if (disc) inside++;
            require(field.isAlive(x, y) == disc, "Cell " + x + "," + y + " disc=" + disc);
        }
        require(born == inside && field.getPopulation() == inside, "Born count " + born + " vs " + inside);
        require(field.seedDisc(.5, .5, 3, 1.0) == 0, "Living cells cannot be born again");
        LifeField untouched = empty(40, 30, 3L);
        require(untouched.seedDisc(.5, .5, 3, 0) == 0 && untouched.seedDisc(.5, .5, 0, 1) == 0
            && untouched.seedDisc(.5, .5, Double.NaN, 1) == 0 && untouched.seedDisc(.5, .5, 3, Double.NaN) == 0
            && untouched.getPopulation() == 0, "Invalid seeding changed the field");
        LifeField partial = empty(64, 64, 9L);
        int some = partial.seedDisc(.5, .5, 12, 0.3);
        require(some > 60 && some < 220, "Probability 0.3 over ~450 cells gave " + some);
    }

    private static void seedingWrapsAndIsDeterministic() {
        LifeField field = empty(20, 20, 5L);
        field.seedDisc(0.0, 0.0, 1.0, 1.0);
        require(field.isAlive(0, 0) && field.isAlive(19, 0) && field.isAlive(0, 19) && field.isAlive(1, 0),
            "Seeding must wrap across edges");
        LifeField a = empty(50, 50, 77L), b = empty(50, 50, 77L);
        for (int i = 0; i < 20; i++) {
            double u = (i * 0.137) % 1.0, v = (i * 0.311) % 1.0;
            require(a.seedDisc(u, v, 4, 0.4) == b.seedDisc(u, v, 4, 0.4), "Nondeterministic seeding");
            a.advance(0.25); b.advance(0.25);
        }
        for (int y = 0; y < 50; y++) for (int x = 0; x < 50; x++)
            require(a.isAlive(x, y) == b.isAlive(x, y), "Seeded worlds diverged");
    }

    private static void localSamplesMatchCounts() {
        LifeField field = empty(30, 30, 1L);
        double[] out = new double[2];
        field.sampleLocal(.5, .5, 4, out);
        near(out[0], 0, 0, "Empty density"); near(out[1], 0, 0, "Empty activity");
        boolean[] cells = new boolean[900];
        for (int x = 10; x < 20; x++) cells[15 * 30 + x] = true;  // one horizontal line
        field.setCells(cells);
        field.sampleLocal((15 + .5) / 30, (15 + .5) / 30, 2, out);
        // Radius-2 disc has 13 cells; the line covers 5 of them.
        near(out[0], 5 / 13.0, 1e-12, "Local density");
        near(out[1], 0, 0, "No activity before a generation");
        field.setRate(1);
        field.advance(1.0);
        boolean[] copy = new boolean[900];
        field.copyCells(copy);
        int changed = 0, total = 0, alive = 0;
        for (int dy = -2; dy <= 2; dy++) for (int dx = -2; dx <= 2; dx++) {
            if (dx * dx + dy * dy > 4) continue;
            int index = (15 + dy) * 30 + 15 + dx;
            total++;
            if (copy[index]) alive++;
            if (copy[index] != cells[index]) changed++;
        }
        field.sampleLocal((15 + .5) / 30, (15 + .5) / 30, 2, out);
        near(out[0], alive / (double) total, 1e-12, "Density after a generation");
        near(out[1], changed / (double) total, 1e-12, "Activity after a generation");
        for (int y = 0; y < 30; y++) for (int x = 0; x < 30; x++)
            require(copy[y * 30 + x] == field.isAlive(x, y), "copyCells mismatch");
        boolean threw = false;
        try { field.copyCells(new boolean[10]); } catch (IllegalArgumentException expected) { threw = true; }
        require(threw, "Short copy buffer accepted");
    }

    private static void tintPaintsFallsOffAndSaturates() {
        WorldTint tint = new WorldTint(30, 30);
        tint.paint((15 + .5) / 30, (15 + .5) / 30, 4, 120, 0.8);
        double center = tint.strength(15 * 30 + 15), side = tint.strength(15 * 30 + 18), outside = tint.strength(15 * 30 + 20);
        require(center > side && side > 0, "Paint should fall off: " + center + " " + side);
        near(outside, 0, 0, "Paint beyond radius");
        near(tint.hue(15 * 30 + 15), 120, 1e-4, "Painted hue");
        for (int i = 0; i < 20; i++) tint.paint((15 + .5) / 30, (15 + .5) / 30, 4, 120, 1.0);
        for (int i = 0; i < 900; i++) require(tint.strength(i) <= 1.0 + 1e-6, "Strength above one");
        double[] out = new double[2];
        tint.sample((15 + .5) / 30, (15 + .5) / 30, out);
        near(out[0], 120, 1e-4, "Sampled hue"); near(out[1], 1.0, 1e-6, "Saturated strength");
        tint.clear();
        near(tint.strength(15 * 30 + 15), 0, 0, "Cleared tint");
    }

    private static void tintMixesHueOnTheCircle() {
        WorldTint tint = new WorldTint(10, 10);
        tint.paint(.55, .55, 1, 350, 0.5);
        tint.paint(.55, .55, 1, 10, 0.5);
        double hue = tint.hue(5 * 10 + 5);
        require(hue < 1e-3 || hue > 360 - 1e-3, "350 and 10 degrees must mix to 0, got " + hue);
    }

    private static void tintTravelsOnlyAlongLife() {
        LifeField field = empty(20, 20, 2L);
        boolean[] cells = new boolean[400];
        cells[10 * 20 + 11] = true;  // one living neighbour east of the painted cell
        field.setCells(cells);
        WorldTint tint = new WorldTint(20, 20);
        tint.paint((10 + .5) / 20, (10 + .5) / 20, 0.5, 200, 1.0);
        require(tint.strength(10 * 20 + 10) > 0.9 && tint.strength(10 * 20 + 11) == 0, "Setup paint");
        tint.spread(field, 0.25, 30);
        require(tint.strength(10 * 20 + 10) > 0.5, "Painted cell keeps most colour");
        near(tint.strength(10 * 20 + 11), 0, 1e-9, "A living cell only gathers from living neighbours");
        near(tint.strength(10 * 20 + 9), 0, 1e-9, "Dead neighbours do not spread colour");
        // Colour flows into cells that have a living, coloured neighbour.
        WorldTint flowing = new WorldTint(20, 20);
        flowing.paint((10 + .5) / 20, (11 + .5) / 20, 0.5, 200, 1.0);
        cells = new boolean[400];
        cells[11 * 20 + 10] = true;
        field.setCells(cells);
        flowing.spread(field, 0.25, 30);
        require(flowing.strength(10 * 20 + 10) > 0.05, "Colour should move from a living cell to its neighbour");
        near(flowing.hue(10 * 20 + 10), 200, 1e-3, "Spread keeps hue");
        boolean threw = false;
        try { flowing.spread(new LifeField(21, 20, 1L), 0.25, 30); } catch (IllegalArgumentException expected) { threw = true; }
        require(threw, "Mismatched dimensions accepted");
    }

    private static void tintFades() {
        LifeField field = empty(12, 12, 4L);
        WorldTint tint = new WorldTint(12, 12);
        for (int y = 0; y < 12; y++) for (int x = 0; x < 12; x++) tint.paint((x + .5) / 12, (y + .5) / 12, 0.5, 40, 1.0);
        double before = tint.strength(0);
        tint.spread(field, 3.0, 30.0);
        near(tint.strength(0), before * Math.exp(-0.1), 1e-5, "Empty water fade");
        boolean[] cells = new boolean[144];
        java.util.Arrays.fill(cells, true);
        field.setCells(cells);
        before = tint.strength(0);
        tint.spread(field, 3.0, 30.0);
        near(tint.strength(0), before * Math.exp(-3.0 / 90.0), 1e-5, "Living water fades three times more slowly");
    }

    private static void near(double actual, double expected, double tolerance, String message) {
        checks++;
        if (!(Math.abs(actual - expected) <= tolerance)) {
            throw new AssertionError(message + ": expected " + expected + " got " + actual);
        }
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
