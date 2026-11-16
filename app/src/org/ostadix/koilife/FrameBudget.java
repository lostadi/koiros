package org.ostadix.koilife;

/**
 * Adjusts drawing density to measured render cost without changing simulation time.
 * The owner supplies CPU/render duration in milliseconds and visible elapsed
 * seconds, and calls resetTiming() after a pause. No allocations occur per sample.
 */
public final class FrameBudget {
    public static final int MIN_POINTS = 3000;
    // Equal-sized creature groups: the largest multiple of three below 14,000.
    public static final int MAX_POINTS = 13998;
    public static final int INITIAL_POINTS = 9000;
    public static final double TARGET_DRAW_MS = 12.0;

    private static final double FILTER_SECONDS = 0.35;
    private static final double WARMUP_SECONDS = 0.50;
    private static final double DECREASE_INTERVAL = 0.60;
    private static final double INCREASE_INTERVAL = 2.0;
    private static final double OVERLOAD_HOLD_SECONDS = 0.20;
    private static final double HEADROOM_HOLD_SECONDS = 2.0;
    private int pointCount = INITIAL_POINTS;
    private boolean hasSample;
    private double averageMs;
    private double activeSeconds;
    private double sinceChange;
    private double overloadSeconds;
    private double headroomSeconds;

    public int getPointCount() { return pointCount; }
    public double getSmoothedCostMs() { return averageMs; }

    /** Returns true only when this sample changes the drawing point budget. */
    public boolean observeFrame(double costMs, double elapsedSeconds) {
        if (!positiveFinite(costMs) || !positiveFinite(elapsedSeconds)) return false;
        // A single stall or missed lifecycle notification cannot stand in for
        // seconds of sustained load or ample headroom.
        double dt = Math.min(elapsedSeconds, 0.25);
        double sample = Math.min(costMs, 1000.0);
        if (!hasSample) {
            averageMs = sample;
            hasSample = true;
        } else {
            averageMs += (sample - averageMs) * (1.0 - Math.exp(-dt / FILTER_SECONDS));
        }
        activeSeconds += dt;
        sinceChange += dt;
        if (activeSeconds < WARMUP_SECONDS) return false;

        boolean overloaded = averageMs > TARGET_DRAW_MS * 1.12
                && sample > TARGET_DRAW_MS;
        boolean ampleHeadroom = averageMs < TARGET_DRAW_MS * 0.65
                && sample < TARGET_DRAW_MS * 0.80;
        overloadSeconds = overloaded ? overloadSeconds + dt : 0.0;
        headroomSeconds = ampleHeadroom ? headroomSeconds + dt : 0.0;

        if (pointCount > MIN_POINTS && sinceChange >= DECREASE_INTERVAL
                && overloadSeconds >= OVERLOAD_HOLD_SECONDS) {
            double factor = Math.max(0.75, Math.min(0.90,
                    TARGET_DRAW_MS / averageMs * 0.95));
            // Round upward so a decrease never exceeds one quarter.
            int next = Math.max(MIN_POINTS, (int) Math.ceil(pointCount * factor / 3.0) * 3);
            return changeTo(next);
        }
        if (pointCount < MAX_POINTS && sinceChange >= INCREASE_INTERVAL
                && headroomSeconds >= HEADROOM_HOLD_SECONDS) {
            // Round downward so recovery adds no more than ten percent.
            int next = Math.min(MAX_POINTS, (int) Math.floor(pointCount * 1.10 / 3.0) * 3);
            return changeTo(next);
        }
        return false;
    }

    /** Drops stale timing after a pause while preserving the learned density. */
    public void resetTiming() {
        hasSample = false;
        averageMs = 0.0;
        activeSeconds = 0.0;
        sinceChange = 0.0;
        overloadSeconds = 0.0;
        headroomSeconds = 0.0;
    }

    private boolean changeTo(int next) {
        if (next == pointCount) return false;
        pointCount = next;
        sinceChange = 0.0;
        overloadSeconds = 0.0;
        headroomSeconds = 0.0;
        return true;
    }

    private static boolean positiveFinite(double value) {
        return value > 0.0 && !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
