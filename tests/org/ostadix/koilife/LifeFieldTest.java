package org.ostadix.koilife;

/** Run with java org.ostadix.koilife.LifeFieldTest; no Android or test library needed. */
public final class LifeFieldTest {
    private static int checks;

    public static void main(String[] args) {
        blinkerAndWrappedEdges();
        gliderMovesAfterFourGenerations();
        clocksAreIndependentOfFrameRate();
        seededRunsAreReproducible();
        fourierMatchesAnalyticStripe();
        samplesArePeriodicBoundedAndDivergenceFree();
        sampledGridsMatchPointSampling();
        normalizationTracksSpectrumChanges();
        reseedsWaitForStagnation();
        argumentsAndDefensiveClock();
        System.out.println("LifeFieldTest passed: " + checks + " checks");
    }

    private static void blinkerAndWrappedEdges() {
        LifeField field = pattern(7, 7, new int[][] {{2, 3}, {3, 3}, {4, 3}});
        field.advance(1.0);
        expectPattern(field, new int[][] {{3, 2}, {3, 3}, {3, 4}});
        field.advance(1.0);
        expectPattern(field, new int[][] {{2, 3}, {3, 3}, {4, 3}});
        LifeField wrapped = pattern(7, 7, new int[][] {{6, 3}, {0, 3}, {1, 3}});
        wrapped.advance(1.0);
        expectPattern(wrapped, new int[][] {{0, 2}, {0, 3}, {0, 4}});
        check(wrapped.isAlive(-7, 3), "negative coordinates wrap");
        check(wrapped.isAlive(7, 3), "positive coordinates wrap");
        close(field.getActivity(), 4.0 / 49.0, 1e-12, "activity counts changed cells");
    }

    private static void gliderMovesAfterFourGenerations() {
        LifeField field = pattern(12, 12,
                new int[][] {{3, 2}, {4, 3}, {2, 4}, {3, 4}, {4, 4}});
        field.setRate(4);
        field.advance(1.0);
        check(field.getGeneration() == 4, "one second gives four generations");
        expectPattern(field, new int[][] {{4, 3}, {5, 4}, {3, 5}, {4, 5}, {5, 5}});
    }

    private static void clocksAreIndependentOfFrameRate() {
        for (int rate : new int[] {1, 4}) {
            LifeField coarse = new LifeField(32, 24, 4123L);
            LifeField fine = new LifeField(32, 24, 4123L);
            coarse.setAutoReseed(false);
            fine.setAutoReseed(false);
            coarse.setRate(rate);
            fine.setRate(rate);
            for (int second = 0; second < 10; second++) coarse.advance(1.0);
            for (int frame = 0; frame < 600; frame++) fine.advance(1.0 / 60.0);
            check(coarse.getGeneration() == rate * 10, "exact coarse generation count");
            check(fine.getGeneration() == rate * 10, "exact 60 fps generation count");
            sameGrid(coarse, fine);
            double[] a = new double[3];
            double[] b = new double[3];
            coarse.sample(0.231, 0.734, a);
            fine.sample(0.231, 0.734, b);
            for (int channel = 0; channel < 3; channel++) {
                close(a[channel], b[channel], 1e-9, "smoothing is elapsed-time invariant");
            }
        }
        LifeField switchRate = new LifeField(16, 16, 1);
        switchRate.setAutoReseed(false);
        switchRate.advance(0.5);
        switchRate.setRate(4);
        switchRate.advance(0.124);
        check(switchRate.getGeneration() == 0, "rate change preserves partial tick");
        switchRate.advance(0.001);
        check(switchRate.getGeneration() == 1, "rate change completes partial tick");
    }

    private static void seededRunsAreReproducible() {
        LifeField a = new LifeField(48, 32, 91234L);
        LifeField b = new LifeField(48, 32, 91234L);
        a.setAutoReseed(false);
        b.setAutoReseed(false);
        sameGrid(a, b);
        double density = a.getPopulation() / (48.0 * 32.0);
        check(density > 0.25 && density < 0.39, "random initialization is near 32 percent");
        for (int i = 0; i < 30; i++) {
            a.advance(1.0);
            b.advance(1.0);
        }
        sameGrid(a, b);
        a.reseed(91234L);
        b.reseed(91234L);
        sameGrid(a, b);
        check(a.getGeneration() == 0 && a.getReseedCount() == 1, "reseed resets run clock and counts once");
    }

    private static void fourierMatchesAnalyticStripe() {
        LifeField field = new LifeField(32, 24, 5L);
        boolean[] stripe = new boolean[32 * 24];
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 16; x++) stripe[y * 32 + x] = true;
        }
        field.setCells(stripe);
        // Closed-form finite geometric series for a half-width binary stripe.
        double fundamental = 2.0 / (32.0 * Math.sin(Math.PI / 32.0));
        close(field.getBandEnergy(0), fundamental, 1e-7, "spatial DFT agrees with analytic stripe");
        for (int band = 1; band < 8; band++) {
            close(field.getBandEnergy(band), 0.0, 1e-7, "stripe has no mixed or vertical modes");
        }
        field.setCells(new boolean[32 * 24]);
        double[] sample = new double[3];
        field.sample(0.2, 0.7, sample);
        for (int band = 0; band < 8; band++) close(field.getBandEnergy(band), 0, 0, "empty spectrum");
        for (double value : sample) close(value, 0.0, 0.0, "empty field has zero flow");
        for (int i = 0; i < stripe.length; i++) stripe[i] = true;
        field.setCells(stripe);
        for (int band = 0; band < 8; band++) close(field.getBandEnergy(band), 0, 0, "uniform DC has no selected modes");
    }

    private static void samplesArePeriodicBoundedAndDivergenceFree() {
        LifeField field = new LifeField(40, 28, 8754L);
        field.setAutoReseed(false);
        field.setRate(4);
        double[] point = new double[3];
        double[] wrapped = new double[3];
        for (int frame = 0; frame < 300; frame++) {
            field.advance(1.0 / 30.0);
            double u = frame * 0.173 - 20.0;
            double v = frame * 0.371 - 30.0;
            field.sample(u, v, point);
            field.sample(u + 3.0, v - 2.0, wrapped);
            for (int channel = 0; channel < 3; channel++) {
                check(Double.isFinite(point[channel]) && Math.abs(point[channel]) <= 1.0 + 1e-12,
                        "field channel is finite and bounded");
                close(point[channel], wrapped[channel], 1e-10, "sampling is periodic");
            }
            check(field.getActivity() >= 0.0 && field.getActivity() <= 1.0, "activity bounded");
            for (int band = 0; band < 8; band++) {
                double energy = field.getBandEnergy(band);
                check(Double.isFinite(energy) && energy >= 0.0 && energy <= 1.0, "energy bounded");
            }
        }
        field.sample(Double.NaN, Double.POSITIVE_INFINITY, point);
        for (double value : point) check(Double.isFinite(value), "nonfinite coordinates handled");
        double h = 1e-5;
        double[] xp = new double[3];
        double[] xm = new double[3];
        double[] yp = new double[3];
        double[] ym = new double[3];
        field.sample(0.31 + h, 0.47, xp);
        field.sample(0.31 - h, 0.47, xm);
        field.sample(0.31, 0.47 + h, yp);
        field.sample(0.31, 0.47 - h, ym);
        double divergence = (xp[0] - xm[0] + yp[1] - ym[1]) / (2.0 * h);
        close(divergence, 0.0, 1e-6, "flow is divergence free");
    }

    private static void sampledGridsMatchPointSampling() {
        for (int rate : new int[] {1, 4}) {
            LifeField field = new LifeField(48, 64, 73119L);
            field.setRate(rate);
            field.setAutoReseed(false);
            assertSampleGrid(field, 13, 19);
            for (int frame = 0; frame < 90; frame++) {
                field.advance(1.0 / 30.0);
                if (frame % 11 == 0) assertSampleGrid(field, 13, 19);
            }
            field.reseed(123789L);
            assertSampleGrid(field, 13, 19);
            field.advance(0.127);
            assertSampleGrid(field, 13, 19);
            field.advance(0.873);
            assertSampleGrid(field, 13, 19);
            // Changing shape must rebuild the basis even if the point count matches.
            assertSampleGrid(field, 19, 13);
            assertSampleGrid(field, 13, 19);
            assertSampleGrid(field, 2, 2);
            assertSampleGrid(field, 1, 7);
            assertSampleGrid(field, 9, 1);
            assertSampleGrid(field, 1, 1);
        }
        LifeField dead = pattern(16, 16, new int[0][]);
        dead.setAutoReseed(true);
        assertSampleGrid(dead, 13, 19);
        for (int second = 0; second < 6; second++) dead.advance(1.0);
        check(dead.getReseedCount() == 1, "sample grid covers automatic renewal");
        dead.advance(0.25);
        assertSampleGrid(dead, 13, 19);
    }

    private static void assertSampleGrid(LifeField field, int columns, int rows) {
        int size = columns * rows;
        double[] xs = new double[size + 1];
        double[] ys = new double[size + 1];
        double[] ps = new double[size + 1];
        xs[size] = ys[size] = ps[size] = 123.0;
        field.sampleGrid(columns, rows, xs, ys, ps);
        double[] point = new double[3];
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < columns; x++) {
                int index = y * columns + x;
                field.sample(x / (double) (columns - 1), y / (double) (rows - 1), point);
                close(xs[index], point[0], 0.0, "grid flow X matches point sample exactly");
                close(ys[index], point[1], 0.0, "grid flow Y matches point sample exactly");
                close(ps[index], point[2], 0.0, "grid projection matches point sample exactly");
                if (x == columns - 1) {
                    close(xs[index], xs[y * columns], 0.0, "grid X endpoint wraps");
                    close(ys[index], ys[y * columns], 0.0, "grid Y endpoint wraps");
                    close(ps[index], ps[y * columns], 0.0, "grid projection endpoint wraps");
                }
                if (y == rows - 1) {
                    close(xs[index], xs[x], 0.0, "grid X bottom wraps");
                    close(ys[index], ys[x], 0.0, "grid Y bottom wraps");
                    close(ps[index], ps[x], 0.0, "grid projection bottom wraps");
                }
            }
        }
        close(xs[size], 123.0, 0.0, "grid leaves extra output capacity untouched");
        close(ys[size], 123.0, 0.0, "grid leaves extra output capacity untouched");
        close(ps[size], 123.0, 0.0, "grid leaves extra output capacity untouched");
    }

    private static void normalizationTracksSpectrumChanges() {
        LifeField field = new LifeField(32, 24, 5L);
        boolean[] cells = new boolean[32 * 24];
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 16; x++) cells[y * 32 + x] = true;
        }
        field.setCells(cells);
        double[] point = new double[3];
        field.sample(0.321, 0.456, point);
        double re = 2.0 / 32.0;
        double im = 2.0 / (32.0 * Math.tan(Math.PI / 32.0));
        double phase = 2.0 * Math.PI * 0.321;
        double amplitude = Math.hypot(re, im);
        close(point[0], 0.0, 1e-7, "stripe has no horizontal drift");
        close(point[1], (re * Math.sin(phase) - im * Math.cos(phase)) / amplitude,
                1e-7, "stripe uses its amplitude above the normalization floor");
        close(point[2], (re * Math.cos(phase) + im * Math.sin(phase)) / amplitude,
                1e-7, "stripe projection uses its amplitude above the floor");
        cells = new boolean[32 * 24];
        cells[0] = true;
        field.setCells(cells);
        field.sample(0.0, 0.0, point);
        double inverseSum = 2.0 + 1.0 / Math.sqrt(2.0) + 2.0 / Math.sqrt(5.0)
                + 1.0 / Math.sqrt(8.0) + 2.0 / Math.sqrt(10.0);
        close(point[2], (2.0 / (32.0 * 24.0)) * inverseSum / 0.08, 1e-7,
                "single cell uses the normalization floor after a strong spectrum");
        assertSampleGrid(field, 13, 19);
        field.setCells(new boolean[32 * 24]);
        assertSampleGrid(field, 13, 19);
        field.sample(0.321, 0.456, point);
        for (double value : point) close(value, 0.0, 0.0, "empty reset clears cached normalization");
    }

    private static void reseedsWaitForStagnation() {
        LifeField dead = pattern(20, 20, new int[0][]);
        dead.setAutoReseed(true);
        for (int i = 0; i < 5; i++) dead.advance(1.0);
        check(dead.getReseedCount() == 0, "dead field is held rather than reset every tick");
        dead.advance(1.0);
        check(dead.getReseedCount() == 1 && dead.getPopulation() > 0, "dead field eventually reseeds");
        LifeField blinker = pattern(20, 20, new int[][] {{8, 9}, {9, 9}, {10, 9}});
        blinker.setAutoReseed(true);
        for (int i = 0; i < 24; i++) blinker.advance(1.0);
        check(blinker.getReseedCount() == 0, "period-two pattern gets a long observation window");
        blinker.advance(1.0);
        check(blinker.getReseedCount() == 1, "period-two pattern eventually reseeds");
        LifeField still = pattern(20, 20, new int[][] {{8, 8}, {8, 9}, {9, 8}, {9, 9}});
        for (int i = 0; i < 40; i++) still.advance(1.0);
        check(still.getReseedCount() == 0, "disabled auto reseed preserves still life");
        expectPattern(still, new int[][] {{8, 8}, {8, 9}, {9, 8}, {9, 9}});
    }

    private static void argumentsAndDefensiveClock() {
        LifeField field = new LifeField(16, 16, 1L);
        field.setAutoReseed(false);
        expectIllegal(() -> field.setRate(2));
        expectIllegal(() -> field.getBandEnergy(-1));
        expectIllegal(() -> field.getBandEnergy(8));
        expectIllegal(() -> field.sample(0, 0, new double[2]));
        expectIllegal(() -> field.sampleGrid(0, 2, new double[4], new double[4], new double[4]));
        expectIllegal(() -> field.sampleGrid(2, 2, null, new double[4], new double[4]));
        expectIllegal(() -> field.sampleGrid(2, 2, new double[3], new double[4], new double[4]));
        expectIllegal(() -> field.sampleGrid(262144, 2, new double[4], new double[4], new double[4]));
        double[] shared = new double[4];
        expectIllegal(() -> field.sampleGrid(2, 2, shared, shared, new double[4]));
        expectIllegal(() -> new LifeField(0, 10, 1));
        field.advance(Double.NaN);
        field.advance(Double.POSITIVE_INFINITY);
        field.advance(-1.0);
        field.advance(0.0);
        check(field.getGeneration() == 0, "invalid elapsed times do not move time");
        field.setRate(4);
        field.advance(100000.0);
        check(field.getGeneration() == 4, "large stalls cannot create catch-up bursts");
    }

    private static LifeField pattern(int columns, int rows, int[][] live) {
        LifeField field = new LifeField(columns, rows, 1L);
        boolean[] grid = new boolean[columns * rows];
        for (int[] position : live) grid[position[1] * columns + position[0]] = true;
        field.setCells(grid);
        field.setAutoReseed(false);
        return field;
    }

    private static void expectPattern(LifeField field, int[][] live) {
        boolean[] expected = new boolean[field.getColumns() * field.getRows()];
        for (int[] position : live) expected[position[1] * field.getColumns() + position[0]] = true;
        check(field.getPopulation() == live.length, "expected population");
        for (int y = 0; y < field.getRows(); y++) {
            for (int x = 0; x < field.getColumns(); x++) {
                check(field.isAlive(x, y) == expected[y * field.getColumns() + x], "expected Conway pattern");
            }
        }
    }

    private static void sameGrid(LifeField a, LifeField b) {
        check(a.getPopulation() == b.getPopulation(), "matching populations");
        check(a.getGeneration() == b.getGeneration(), "matching generations");
        for (int y = 0; y < a.getRows(); y++) {
            for (int x = 0; x < a.getColumns(); x++) {
                check(a.isAlive(x, y) == b.isAlive(x, y), "matching seeded cells");
            }
        }
    }

    private static void expectIllegal(Runnable runnable) {
        try {
            runnable.run();
        } catch (IllegalArgumentException expected) {
            checks++;
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException");
    }

    private static void close(double actual, double expected, double tolerance, String message) {
        check(Double.isFinite(actual) && Math.abs(actual - expected) <= tolerance,
                message + ": expected " + expected + ", actual " + actual);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
