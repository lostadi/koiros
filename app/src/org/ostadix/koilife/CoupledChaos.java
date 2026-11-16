package org.ostadix.koilife;

import java.util.Arrays;
import java.util.Random;

/**
 * Three seeded Lorenz systems with weak, directional ring coupling.
 * Base equations: E. N. Lorenz, Deterministic Nonperiodic Flow (1963),
 * https://wind.mit.edu/~emanuel/Lorenz/EdLorenz/Deterministic_63.pdf .
 * The ring coupling and gentle Life modulation are this application's additions.
 * This is an operational animation drive, not a guarantee of perpetual chaos.
 * A deterministic watchdog recovers invalid or persistently stalled trajectories.
 * No Android dependency or per-step/per-sample allocation is required.
 */
public final class CoupledChaos {
    public static final int SYSTEM_COUNT = 3;
    public static final double STEP_SECONDS = 1.0 / 120.0;
    private static final int DIMENSIONS = SYSTEM_COUNT * 3;
    private static final double SIGMA = 10.0;
    private static final double BETA = 8.0 / 3.0;
    private static final double MAX_ADVANCE_SECONDS = 1.0;
    private static final double STALL_SECONDS = 15.0;
    private static final double MAX_STATE_MAGNITUDE = 1000.0;

    private final double coupling;
    private final double[] state = new double[DIMENSIONS];
    private final double[] stage = new double[DIMENSIONS];
    private final double[] k1 = new double[DIMENSIONS];
    private final double[] k2 = new double[DIMENSIONS];
    private final double[] k3 = new double[DIMENSIONS];
    private final double[] k4 = new double[DIMENSIONS];
    private final double[] quietSeconds = new double[SYSTEM_COUNT];
    private Random random;
    private double accumulator;
    private double lifeDrive;
    private long stepCount;
    private int reseedCount;

    public CoupledChaos(long seed) {
        this(seed, 0.12);
    }

    /** A zero coupling value is useful for measuring the contribution of the ring. */
    public CoupledChaos(long seed, double coupling) {
        if (!finite(coupling) || coupling < 0.0 || coupling > 0.25) {
            throw new IllegalArgumentException("Coupling must be finite and from 0 through 0.25");
        }
        this.coupling = coupling;
        initialize(seed, true);
    }

    /**
     * Advances only visible elapsed time. Life inputs are mean activity and alive
     * density in [0,1]; a constant or extinct Life field does not stop this drive.
     * Invalid/nonpositive dt does nothing, including leaving sample values intact.
     * A long interruption contributes at most one second rather than a work burst.
     */
    public void advance(double visibleSeconds, double lifeActivity, double lifeDensity) {
        if (!(visibleSeconds > 0.0) || !finite(visibleSeconds)) return;
        lifeDrive = 2.0 * unit(lifeActivity) + 1.5 * unit(lifeDensity);
        accumulator += Math.min(visibleSeconds, MAX_ADVANCE_SECONDS);
        while (accumulator + 1e-12 >= STEP_SECONDS) {
            integrateStep();
            accumulator -= STEP_SECONDS;
            if (accumulator < 0.0) accumulator = 0.0;
            stepCount++;
        }
    }

    /**
     * Returns signed, tanh-normalized x/y/z in out[0..2] and speed in out[3].
     * Coordinates lie in [-1,1]; speed lies in [0,1]. Sampling does not advance time.
     */
    public void sample(int oscillator, double[] out) {
        if (oscillator < 0 || oscillator >= SYSTEM_COUNT) {
            throw new IllegalArgumentException("Oscillator must be from 0 through 2");
        }
        if (out == null || out.length < 4) {
            throw new IllegalArgumentException("Chaos output needs at least four elements");
        }
        int i = oscillator * 3;
        int previous = ((oscillator + SYSTEM_COUNT - 1) % SYSTEM_COUNT) * 3;
        double x = state[i];
        double y = state[i + 1];
        double z = state[i + 2];
        double dx = SIGMA * (y - x) + coupling * (state[previous] - x);
        double dy = x * (rho(oscillator) - z) - y;
        double dz = x * y - BETA * z;
        out[0] = Math.tanh(x / 18.0);
        out[1] = Math.tanh(y / 23.0);
        out[2] = Math.tanh((z - 27.0) / 18.0);
        out[3] = Math.tanh(Math.sqrt(dx * dx + dy * dy + dz * dz) / 90.0);
    }

    /** Explicit new seed starts a new visible-time run and increments the reseed count. */
    public void reseed(long seed) {
        initialize(seed, true);
        countReseed();
    }

    public long getStepCount() {
        return stepCount;
    }

    /** Simulated time, excluding a partial step held in the accumulator. */
    public double getTimeSeconds() {
        return stepCount * STEP_SECONDS;
    }

    public int getReseedCount() {
        return reseedCount;
    }

    public int getSystemCount() {
        return SYSTEM_COUNT;
    }

    private void initialize(long seed, boolean resetClock) {
        random = new Random(seed);
        for (int oscillator = 0; oscillator < SYSTEM_COUNT; oscillator++) {
            int i = oscillator * 3;
            state[i] = -10.0 + 20.0 * random.nextDouble() + oscillator * 0.13;
            state[i + 1] = -10.0 + 20.0 * random.nextDouble() - oscillator * 0.17;
            state[i + 2] = 15.0 + 20.0 * random.nextDouble();
        }
        Arrays.fill(quietSeconds, 0.0);
        if (resetClock) {
            accumulator = 0.0;
            stepCount = 0L;
            lifeDrive = 0.0;
        }
    }

    /** Every derivative uses a complete nine-dimensional stage snapshot. */
    private void integrateStep() {
        derivatives(state, k1);
        for (int i = 0; i < DIMENSIONS; i++) stage[i] = state[i] + STEP_SECONDS * 0.5 * k1[i];
        derivatives(stage, k2);
        for (int i = 0; i < DIMENSIONS; i++) stage[i] = state[i] + STEP_SECONDS * 0.5 * k2[i];
        derivatives(stage, k3);
        for (int i = 0; i < DIMENSIONS; i++) stage[i] = state[i] + STEP_SECONDS * k3[i];
        derivatives(stage, k4);
        boolean invalid = false;
        for (int i = 0; i < DIMENSIONS; i++) {
            state[i] += STEP_SECONDS / 6.0 * (k1[i] + 2.0 * k2[i] + 2.0 * k3[i] + k4[i]);
            if (!finite(state[i]) || Math.abs(state[i]) > MAX_STATE_MAGNITUDE) invalid = true;
        }
        if (invalid) {
            recover();
            return;
        }
        derivatives(state, k1);
        for (int oscillator = 0; oscillator < SYSTEM_COUNT; oscillator++) {
            int i = oscillator * 3;
            double squaredSpeed = k1[i] * k1[i] + k1[i + 1] * k1[i + 1] + k1[i + 2] * k1[i + 2];
            quietSeconds[oscillator] = squaredSpeed < 1e-8
                    ? quietSeconds[oscillator] + STEP_SECONDS : 0.0;
            if (quietSeconds[oscillator] >= STALL_SECONDS) {
                recover();
                return;
            }
        }
    }

    private void derivatives(double[] values, double[] output) {
        for (int oscillator = 0; oscillator < SYSTEM_COUNT; oscillator++) {
            int i = oscillator * 3;
            int previous = ((oscillator + SYSTEM_COUNT - 1) % SYSTEM_COUNT) * 3;
            double x = values[i];
            double y = values[i + 1];
            double z = values[i + 2];
            output[i] = SIGMA * (y - x) + coupling * (values[previous] - x);
            output[i + 1] = x * (rho(oscillator) - z) - y;
            output[i + 2] = x * y - BETA * z;
        }
    }

    private double rho(int oscillator) {
        return 28.0 + lifeDrive + oscillator * 0.35;
    }

    private void recover() {
        initialize(random.nextLong(), false);
        countReseed();
    }

    private void countReseed() {
        if (reseedCount != Integer.MAX_VALUE) reseedCount++;
    }

    private static double unit(double value) {
        return finite(value) ? Math.max(0.0, Math.min(1.0, value)) : 0.0;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
