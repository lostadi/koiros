/*
 * Koi Life page drift and depth scale. GPL-3.0-or-later; see LICENSE.
 */
package org.ostadix.koilife;

/**
 * Home-page parallax and the visual size of a creature's fixed depth.
 * Motion, collisions, and Life clocks do not read these values.
 * No allocations.
 */
public final class PageDrift {
    private PageDrift() { }

    /**
     * Centers the launcher offset on zero. A reported page step wins.
     * Otherwise a pixel offset is used, and only up to half a screen.
     * One page, or an unknown offset, stays still.
     */
    public static double centeredOffset(double xOffset, double xOffsetStep,
                                        double xPixelOffset, double surfaceWidth) {
        if (finite(xOffsetStep) && xOffsetStep > 0.0 && finite(xOffset)) {
            return clamp(xOffset, 0.0, 1.0) - 0.5;
        }
        if (finite(xPixelOffset) && finite(surfaceWidth) && surfaceWidth > 1.0 && xPixelOffset != 0.0) {
            return clamp(xPixelOffset / surfaceWidth, -0.5, 0.5);
        }
        return 0.0;
    }

    /**
     * Pixels to add to a body center. Moving toward a later page shifts the
     * scene left. Nearer bodies move farther. The extreme nearest shift is
     * 7 percent of the short screen side.
     */
    public static double pixelShift(double centered, double depth, double extent) {
        if (!finite(centered) || !finite(depth) || !finite(extent) || !(extent > 0.0)) return 0.0;
        double span = clamp(centered, -0.5, 0.5);
        double nearness = clamp(depth, 0.0, 1.0);
        return -span * extent * 0.14 * (0.35 + 0.65 * nearness);
    }

    /** 1 is the nearest body. The farthest readable body stays about two thirds as large. */
    public static double visualScale(double depth) {
        if (!finite(depth)) depth = 0.0;
        return 0.55 + 0.70 * clamp(depth, 0.0, 1.0);
    }

    /** Radians per visible second. Slow saved motion still leaves a visible wash. */
    public static double waterRadiansPerSecond(double motionSpeed) {
        if (!finite(motionSpeed)) motionSpeed = 0.02;
        return 0.10 + 0.18 * clamp(motionSpeed, 0.02, 2.0);
    }

    public static double positiveMod(double value, double period) {
        if (!finite(value) || !finite(period) || !(period > 0.0)) return 0.0;
        double mod = value % period;
        return mod < 0.0 ? mod + period : mod;
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
