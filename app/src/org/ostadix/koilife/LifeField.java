package org.ostadix.koilife;

import java.util.Arrays;
import java.util.Random;

/**
 * A toroidal Conway field and its low spatial-frequency projection.
 *
 * The wrapped-neighbour behavior follows GolController.kt in the MIT-licensed
 * Game of Life Wallpaper project by Harold Martin. This Java implementation
 * adds an elapsed-time clock, a spatial Fourier transform, and a smooth flow.
 * No Android objects or allocations are needed during advance() or sample().
 */
public final class LifeField {
    private static final double TAU = 2.0 * Math.PI;
    private static final double INITIAL_DENSITY = 0.32;
    private static final double SMOOTH_SECONDS = 0.45;
    private static final double MAX_ADVANCE_SECONDS = 1.0;
    private static final double DEAD_HOLD_SECONDS = 6.0;
    private static final double CYCLIC_HOLD_SECONDS = 24.0;
    private static final double MAX_RUN_SECONDS = 12.0 * 60.0;
    private static final double CLOCK_EPSILON = 1.0e-10;
    private static final int[] MODE_X = {1, 0, 1, 2, 1, 2, 3, 1};
    private static final int[] MODE_Y = {0, 1, 1, 1, 2, 2, 1, 3};
    private static final int MODE_COUNT = MODE_X.length;

    private final int columns;
    private final int rows;
    private final int cellCount;
    private final int[] left;
    private final int[] right;
    private final int[] above;
    private final int[] below;
    private boolean[] cells;
    private boolean[] buffer;
    private final boolean[] previous;
    private boolean hasPrevious;
    private final float[][] cosine;
    private final float[][] sine;
    private final double[] inverseFrequency = new double[MODE_COUNT];
    private final double[] real = new double[MODE_COUNT];
    private final double[] imaginary = new double[MODE_COUNT];
    private final double[] smoothReal = new double[MODE_COUNT];
    private final double[] smoothImaginary = new double[MODE_COUNT];
    private boolean sampleBoundsDirty = true;
    private double sampleFlowNormalization = 0.08;
    private double sampleProjectionNormalization = 0.08;
    private int sampleGridColumns;
    private int sampleGridRows;
    private double[] sampleGridCosine;
    private double[] sampleGridSine;

    private Random random;
    private int rate = 1;
    private boolean autoReseed = true;
    private double untilTick = 1.0;
    private double runSeconds;
    private double deadSeconds;
    private double cyclicSeconds;
    private double activity;
    private int population;
    private long generation;
    private int reseedCount;

    public LifeField(int columns, int rows, long seed) {
        if (columns <= 0 || rows <= 0 || (long) columns * rows > 262144L) {
            throw new IllegalArgumentException("Grid dimensions must be positive and total at most 262144 cells");
        }
        this.columns = columns;
        this.rows = rows;
        this.cellCount = columns * rows;
        cells = new boolean[cellCount];
        buffer = new boolean[cellCount];
        previous = new boolean[cellCount];
        left = new int[columns];
        right = new int[columns];
        above = new int[rows];
        below = new int[rows];
        for (int x = 0; x < columns; x++) {
            left[x] = x == 0 ? columns - 1 : x - 1;
            right[x] = x + 1 == columns ? 0 : x + 1;
        }
        for (int y = 0; y < rows; y++) {
            above[y] = (y == 0 ? rows - 1 : y - 1) * columns;
            below[y] = (y + 1 == rows ? 0 : y + 1) * columns;
        }
        cosine = new float[MODE_COUNT][cellCount];
        sine = new float[MODE_COUNT][cellCount];
        for (int mode = 0; mode < MODE_COUNT; mode++) {
            inverseFrequency[mode] = 1.0 / Math.hypot(MODE_X[mode], MODE_Y[mode]);
            for (int y = 0; y < rows; y++) {
                for (int x = 0; x < columns; x++) {
                    double angle = TAU * (MODE_X[mode] * (double) x / columns
                            + MODE_Y[mode] * (double) y / rows);
                    int index = y * columns + x;
                    cosine[mode][index] = (float) Math.cos(angle);
                    sine[mode][index] = (float) Math.sin(angle);
                }
            }
        }
        initialize(seed);
        snapSpectrum();
    }

    /** Changes the generation rate while preserving progress through the current tick. */
    public void setRate(int rate) {
        if (rate != 1 && rate != 4) {
            throw new IllegalArgumentException("Generation rate must be 1 or 4 updates per second");
        }
        untilTick *= (double) this.rate / rate;
        this.rate = rate;
    }

    public void setAutoReseed(boolean enabled) {
        autoReseed = enabled;
    }

    /**
     * Advances by visible elapsed seconds, independently of the rendering rate.
     * Invalid or nonpositive durations do nothing. Long interruptions contribute
     * at most one second, so returning from a stall cannot trigger a catch-up burst.
     * The owner should not call this while its wallpaper is hidden.
     */
    public void advance(double seconds) {
        if (!(seconds > 0.0) || Double.isInfinite(seconds)) {
            return;
        }
        double remaining = Math.min(seconds, MAX_ADVANCE_SECONDS);
        while (remaining > 0.0) {
            double slice = Math.min(remaining, untilTick);
            smoothSpectrum(slice);
            runSeconds += slice;
            remaining -= slice;
            untilTick -= slice;
            if (untilTick <= CLOCK_EPSILON) {
                step();
                untilTick = 1.0 / rate;
            }
        }
    }

    /** Starts a reproducible new run; its Fourier projection blends into the old one. */
    public void reseed(long seed) {
        initialize(seed);
        if (reseedCount != Integer.MAX_VALUE) {
            reseedCount++;
        }
    }

    /**
     * Samples a periodic, divergence-free drift and a scalar spatial projection.
     * out[0], out[1], and out[2] are finite and in [-1, 1]. Coordinates wrap at 1;
     * nonfinite coordinates are treated as zero. No per-sample allocation occurs.
     */
    public void sample(double u, double v, double[] out) {
        if (out == null || out.length < 3) {
            throw new IllegalArgumentException("Sample output needs at least three elements");
        }
        updateSampleBounds();
        u = wrap(u);
        v = wrap(v);
        double driftX = 0.0;
        double driftY = 0.0;
        double projection = 0.0;
        for (int mode = 0; mode < MODE_COUNT; mode++) {
            double re = smoothReal[mode];
            double im = smoothImaginary[mode];
            double angle = TAU * (MODE_X[mode] * u + MODE_Y[mode] * v);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double inverse = inverseFrequency[mode];
            // Curl of sum((re*cos + im*sin)/|k|), with a common scale.
            double derivative = (-re * sin + im * cos) * inverse;
            driftX += MODE_Y[mode] * derivative;
            driftY -= MODE_X[mode] * derivative;
            projection += (re * cos + im * sin) * inverse;
        }
        // Global (not spatially varying) normalization preserves zero divergence.
        out[0] = driftX / sampleFlowNormalization;
        out[1] = driftY / sampleFlowNormalization;
        out[2] = projection / sampleProjectionNormalization;
    }

    /**
     * Samples a row-major grid at x/(gridColumns-1), y/(gridRows-1).
     * The last row and column wrap to zero, exactly as in sample(); a singleton
     * dimension also samples zero. Outputs must be separate arrays with room for
     * gridColumns*gridRows values. A fixed grid size allocates and computes its
     * trigonometric basis once; subsequent calls allocate nothing and use no trig.
     */
    public void sampleGrid(int gridColumns, int gridRows,
            double[] outX, double[] outY, double[] outProjection) {
        long size = (long) gridColumns * gridRows;
        if (gridColumns <= 0 || gridRows <= 0 || size > 262144L) {
            throw new IllegalArgumentException("Sample grid dimensions must be positive and total at most 262144 points");
        }
        int count = (int) size;
        if (outX == null || outY == null || outProjection == null
                || outX.length < count || outY.length < count || outProjection.length < count
                || outX == outY || outX == outProjection || outY == outProjection) {
            throw new IllegalArgumentException("Sample grid needs three separate output arrays with room for every point");
        }
        if (gridColumns != sampleGridColumns || gridRows != sampleGridRows) {
            cacheSampleGrid(gridColumns, gridRows, count);
        }
        updateSampleBounds();
        for (int index = 0; index < count; index++) {
            double driftX = 0.0;
            double driftY = 0.0;
            double projection = 0.0;
            int basis = index * MODE_COUNT;
            for (int mode = 0; mode < MODE_COUNT; mode++) {
                double re = smoothReal[mode];
                double im = smoothImaginary[mode];
                double cos = sampleGridCosine[basis + mode];
                double sin = sampleGridSine[basis + mode];
                double inverse = inverseFrequency[mode];
                double derivative = (-re * sin + im * cos) * inverse;
                driftX += MODE_Y[mode] * derivative;
                driftY -= MODE_X[mode] * derivative;
                projection += (re * cos + im * sin) * inverse;
            }
            outX[index] = driftX / sampleFlowNormalization;
            outY[index] = driftY / sampleFlowNormalization;
            outProjection[index] = projection / sampleProjectionNormalization;
        }
    }

    private void cacheSampleGrid(int gridColumns, int gridRows, int count) {
        double[] cos = new double[count * MODE_COUNT];
        double[] sin = new double[count * MODE_COUNT];
        for (int y = 0; y < gridRows; y++) {
            double v = wrap(y / (double) (gridRows - 1));
            for (int x = 0; x < gridColumns; x++) {
                double u = wrap(x / (double) (gridColumns - 1));
                int basis = (y * gridColumns + x) * MODE_COUNT;
                for (int mode = 0; mode < MODE_COUNT; mode++) {
                    double angle = TAU * (MODE_X[mode] * u + MODE_Y[mode] * v);
                    cos[basis + mode] = Math.cos(angle);
                    sin[basis + mode] = Math.sin(angle);
                }
            }
        }
        sampleGridCosine = cos;
        sampleGridSine = sin;
        sampleGridColumns = gridColumns;
        sampleGridRows = gridRows;
    }

    private void updateSampleBounds() {
        if (!sampleBoundsDirty) return;
        double flowBound = 0.0;
        double projectionBound = 0.0;
        for (int mode = 0; mode < MODE_COUNT; mode++) {
            double amplitude = Math.hypot(smoothReal[mode], smoothImaginary[mode]);
            flowBound += amplitude;
            projectionBound += amplitude * inverseFrequency[mode];
        }
        sampleFlowNormalization = Math.max(0.08, flowBound);
        sampleProjectionNormalization = Math.max(0.08, projectionBound);
        sampleBoundsDirty = false;
    }

    /** Fraction of cells that changed in the latest generation. */
    public double getActivity() {
        return activity;
    }

    /**
     * Normalized Fourier amplitude, 2*|DFT|/cellCount, of the current alive grid.
     * Bands 0..7: (1,0), (0,1), (1,1), (2,1), (1,2), (2,2), (3,1), (1,3).
     */
    public double getBandEnergy(int band) {
        if (band < 0 || band >= MODE_COUNT) {
            throw new IllegalArgumentException("Frequency band must be from 0 through 7");
        }
        return Math.min(1.0, Math.hypot(real[band], imaginary[band]));
    }

    /** Generation number within the current seeded run. */
    public long getGeneration() {
        return generation;
    }

    public int getPopulation() {
        return population;
    }

    /** Number of user-requested or automatic reseeds, excluding construction. */
    public int getReseedCount() {
        return reseedCount;
    }

    public int getColumns() {
        return columns;
    }

    public int getRows() {
        return rows;
    }

    public boolean isAlive(int x, int y) {
        int wrappedX = x % columns;
        int wrappedY = y % rows;
        if (wrappedX < 0) wrappedX += columns;
        if (wrappedY < 0) wrappedY += rows;
        return cells[wrappedY * columns + wrappedX];
    }

    private void initialize(long seed) {
        random = new Random(seed);
        population = 0;
        for (int i = 0; i < cellCount; i++) {
            cells[i] = random.nextDouble() < INITIAL_DENSITY;
            if (cells[i]) population++;
        }
        resetClock();
        transform();
    }

    private void resetClock() {
        hasPrevious = false;
        generation = 0L;
        activity = 0.0;
        runSeconds = 0.0;
        deadSeconds = 0.0;
        cyclicSeconds = 0.0;
        untilTick = 1.0 / rate;
    }

    private void step() {
        int nextPopulation = 0;
        int changed = 0;
        boolean sameAsPrevious = hasPrevious;
        for (int y = 0; y < rows; y++) {
            int row = y * columns;
            int top = above[y];
            int bottom = below[y];
            for (int x = 0; x < columns; x++) {
                int l = left[x];
                int r = right[x];
                int neighbors = (cells[top + l] ? 1 : 0)
                        + (cells[top + x] ? 1 : 0)
                        + (cells[top + r] ? 1 : 0)
                        + (cells[row + l] ? 1 : 0)
                        + (cells[row + r] ? 1 : 0)
                        + (cells[bottom + l] ? 1 : 0)
                        + (cells[bottom + x] ? 1 : 0)
                        + (cells[bottom + r] ? 1 : 0);
                int index = row + x;
                boolean next = neighbors == 3 || (cells[index] && neighbors == 2);
                buffer[index] = next;
                if (next) nextPopulation++;
                if (next != cells[index]) changed++;
                if (next != previous[index]) sameAsPrevious = false;
            }
        }
        System.arraycopy(cells, 0, previous, 0, cellCount);
        hasPrevious = true;
        boolean[] old = cells;
        cells = buffer;
        buffer = old;
        population = nextPopulation;
        activity = changed / (double) cellCount;
        generation++;
        double interval = 1.0 / rate;
        deadSeconds = population == 0 ? deadSeconds + interval : 0.0;
        cyclicSeconds = changed == 0 || sameAsPrevious ? cyclicSeconds + interval : 0.0;
        if (autoReseed && (deadSeconds >= DEAD_HOLD_SECONDS
                || cyclicSeconds >= CYCLIC_HOLD_SECONDS
                || runSeconds >= MAX_RUN_SECONDS)) {
            reseed(random.nextLong());
        } else {
            transform();
        }
    }

    private void transform() {
        double mean = population / (double) cellCount;
        double scale = 2.0 / cellCount;
        for (int mode = 0; mode < MODE_COUNT; mode++) {
            double re = 0.0;
            double im = 0.0;
            float[] cos = cosine[mode];
            float[] sin = sine[mode];
            for (int index = 0; index < cellCount; index++) {
                // Subtracting DC also keeps aliased modes of tiny grids well behaved.
                double value = (cells[index] ? 1.0 : 0.0) - mean;
                re += value * cos[index];
                im += value * sin[index];
            }
            real[mode] = re * scale;
            imaginary[mode] = im * scale;
        }
    }

    private void smoothSpectrum(double seconds) {
        sampleBoundsDirty = true;
        double blend = -Math.expm1(-seconds / SMOOTH_SECONDS);
        for (int mode = 0; mode < MODE_COUNT; mode++) {
            smoothReal[mode] += (real[mode] - smoothReal[mode]) * blend;
            smoothImaginary[mode] += (imaginary[mode] - smoothImaginary[mode]) * blend;
        }
    }

    private void snapSpectrum() {
        sampleBoundsDirty = true;
        System.arraycopy(real, 0, smoothReal, 0, MODE_COUNT);
        System.arraycopy(imaginary, 0, smoothImaginary, 0, MODE_COUNT);
    }

    private static double wrap(double coordinate) {
        if (Double.isNaN(coordinate) || Double.isInfinite(coordinate)) return 0.0;
        return coordinate - Math.floor(coordinate);
    }

    // Package-private fixture/pattern entry point; callers cannot retain the mutable grid.
    void setCells(boolean[] replacement) {
        if (replacement == null || replacement.length != cellCount) {
            throw new IllegalArgumentException("Pattern dimensions must match the grid");
        }
        System.arraycopy(replacement, 0, cells, 0, cellCount);
        Arrays.fill(buffer, false);
        population = 0;
        for (boolean cell : cells) if (cell) population++;
        resetClock();
        transform();
        snapSpectrum();
    }
}
