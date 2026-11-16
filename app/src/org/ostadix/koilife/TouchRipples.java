/*
 * Koi Life touch response. GPL-3.0-or-later; see LICENSE.
 */
package org.ostadix.koilife;

/** Three pooled, short-lived radial waves in screen-normalized coordinates. */
public final class TouchRipples {
    public static final int MAX_RIPPLES = 3;
    public static final double LIFETIME_SECONDS = 7;
    public static final double MAX_FORCE = .18;
    private final double[] u = new double[MAX_RIPPLES], v = new double[MAX_RIPPLES];
    private final double[] age = new double[MAX_RIPPLES];
    private final boolean[] active = new boolean[MAX_RIPPLES];

    /** A new tap replaces only the oldest wave when all three slots are occupied. */
    public void touch(double normalizedX, double normalizedY) {
        if (!finite(normalizedX) || !finite(normalizedY)) return;
        int slot = -1;
        for (int i = 0; i < MAX_RIPPLES; i++) {
            if (!active[i]) { slot = i; break; }
            if (slot < 0 || age[i] > age[slot]) slot = i;
        }
        u[slot] = clamp(normalizedX, 0, 1);
        v[slot] = clamp(normalizedY, 0, 1);
        age[slot] = 0;
        active[slot] = true;
    }

    public void clear() {
        for (int i = 0; i < MAX_RIPPLES; i++) { active[i] = false; age[i] = 0; }
    }

    /** Uses visible wall time; a paused frame neither ages nor expires a wave. */
    public void advance(double seconds) {
        if (!finite(seconds) || !(seconds > 0)) return;
        for (int i = 0; i < MAX_RIPPLES; i++) {
            if (!active[i]) continue;
            age[i] = Math.min(LIFETIME_SECONDS, age[i] + seconds);
            if (age[i] >= LIFETIME_SECONDS) active[i] = false;
        }
    }

    /**
     * Samples one grid node. Aspect dimensions are measured in short-screen-side
     * units, keeping the wave circular in portrait and landscape. The compact
     * polynomial envelope needs no trig or exponentials and contributes nothing
     * outside its broad ring. The three outputs are force X, force Y, projection.
     */
    public void sample(double x, double y, double widthUnits, double heightUnits, double[] out) {
        if (out == null || out.length < 3) throw new IllegalArgumentException("Three outputs required");
        out[0] = out[1] = out[2] = 0;
        if (!finite(x) || !finite(y) || !finite(widthUnits) || !finite(heightUnits)
                || !(widthUnits > 0) || !(heightUnits > 0)) return;
        double forceX = 0, forceY = 0, projection = 0;
        for (int i = 0; i < MAX_RIPPLES; i++) {
            if (!active[i]) continue;
            double opacity = getOpacity(i);
            if (!(opacity > 0)) continue;
            double dx = (x - u[i]) * widthUnits, dy = (y - v[i]) * heightUnits;
            double distance = Math.hypot(dx, dy);
            double q = (distance - getRadius(i)) / (.09 + .012 * age[i]);
            if (Math.abs(q) >= 1) continue;
            double envelope = 1 - q * q;
            envelope *= envelope * opacity;
            double push = .14 * envelope;
            if (distance > .000001) {
                forceX += push * dx / distance;
                forceY += push * dy / distance;
            }
            projection += .06 * envelope;
        }
        double magnitude = Math.hypot(forceX, forceY);
        if (magnitude > MAX_FORCE) {
            double scale = MAX_FORCE / magnitude;
            forceX *= scale; forceY *= scale;
        }
        out[0] = forceX; out[1] = forceY; out[2] = Math.min(.10, projection);
    }

    public int getActiveCount() {
        int count = 0;
        for (int i = 0; i < MAX_RIPPLES; i++) if (active[i]) count++;
        return count;
    }
    public boolean isActive(int slot) { return active[slot]; }
    public double getU(int slot) { return u[slot]; }
    public double getV(int slot) { return v[slot]; }
    public double getAge(int slot) { return age[slot]; }
    public double getRadius(int slot) { return active[slot] ? .025 + .070 * age[slot] : 0; }
    public double getOpacity(int slot) {
        if (!active[slot]) return 0;
        double rise = Math.min(1, age[slot] / .65);
        rise = rise * rise * (3 - 2 * rise);
        double fade = 1 - age[slot] / LIFETIME_SECONDS;
        return rise * fade * fade;
    }
    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }
    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}

