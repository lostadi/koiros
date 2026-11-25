/*
 * Koiros companion rhythm. GPL-3.0-or-later; see LICENSE.
 */
package org.ostadix.koilife;

/**
 * How the pond relates to its person, from events only: how long they were
 * away, the time of day, the battery, and remembered affection. Nothing here
 * polls, senses, or runs in the background. No allocations.
 */
public final class Companion {
    /** Shortest absence that earns a welcome. */
    public static final long WELCOME_AFTER_MS = 20_000L;
    /** Affection fades with this time constant when nobody plays with the pond. */
    public static final double AFFECTION_FADE_MS = 5.0 * 24 * 3600 * 1000;

    private Companion() { }

    /**
     * Welcome strength in [0, 1] after an absence: zero below twenty seconds,
     * a quarter at twenty seconds, rising logarithmically to one at four hours.
     */
    public static double welcome(long awayMs) {
        if (awayMs < WELCOME_AFTER_MS) return 0.0;
        double minutes = awayMs / 60000.0;
        double scaled = Math.log1p(minutes) / Math.log1p(240.0);
        return clamp(0.25 + 0.75 * scaled, 0.25, 1.0);
    }

    /** 0 by day; 1 from 23:00 to 05:00; linear ramps 21-23 and 05-07. */
    public static double night(double hourOfDay) {
        double h = ((hourOfDay % 24) + 24) % 24;
        if (h >= 23 || h < 5) return 1.0;
        if (h >= 21) return (h - 21) / 2.0;
        if (h < 7) return 1.0 - (h - 5) / 2.0;
        return 0.0;
    }

    /**
     * Liveliness in [0.35, 1.2]: calmer at night, sleepy on a low battery that
     * is not charging, a little brighter while charging.
     */
    public static double liveliness(double night, int batteryPercent, boolean charging) {
        double low = !charging && batteryPercent >= 0 && batteryPercent < 20
            ? (20 - batteryPercent) / 20.0 * 0.6 + 0.4 : 0.0;
        double value = 1.0 - 0.45 * clamp(night, 0, 1) - 0.40 * low + (charging ? 0.15 : 0.0);
        return clamp(value, 0.35, 1.2);
    }

    /** True when the pond should save its own power: a low battery that is not charging. */
    public static boolean sleepy(int batteryPercent, boolean charging) {
        return !charging && batteryPercent >= 0 && batteryPercent < 20;
    }

    /** Affection after {@code idleMs} without play, fading exponentially toward zero. */
    public static double fade(double affection, long idleMs) {
        if (!(affection > 0) || idleMs <= 0) return clamp(affection, 0, 1);
        return clamp(affection * Math.exp(-idleMs / AFFECTION_FADE_MS), 0, 1);
    }

    /** Affection after one tap: quick at first, slower as it approaches one. */
    public static double pet(double affection) {
        double a = clamp(affection, 0, 1);
        return clamp(a + 0.06 * (1.0 - a), 0, 1);
    }

    private static double clamp(double value, double low, double high) {
        if (Double.isNaN(value)) return low;
        return Math.max(low, Math.min(high, value));
    }
}
