package org.ostadix.koilife;

import java.util.Random;

/**
 * Small, deterministic pool of drifting bodies. Coordinates are centered and
 * measured in units of the screen's shorter dimension. Collision circles are
 * gentle visual bounds, not exact outlines of the changing procedural forms.
 * All buffers are retained; advance performs no allocations.
 */
public final class PondDynamics {
    public static final int MAX_CREATURES = 9;
    private static final double STEP = 1.0 / 120.0;
    private static final double FADE_SECONDS = 1.5;
    private static final double MAX_SPEED = 0.23;
    /**
     * Fixed visual depth for the nine pooled bodies. 1 is nearest.
     * Swimming, collisions, and the shared radius do not read this.
     */
    private static final double[] DEPTH = {
        1.00, 0.46, 0.78, 0.58, 0.40, 0.88, 0.66, 0.94, 0.52
    };
    private final double[] x = new double[MAX_CREATURES];
    private final double[] y = new double[MAX_CREATURES];
    private final double[] vx = new double[MAX_CREATURES];
    private final double[] vy = new double[MAX_CREATURES];
    private final double[] driftX = new double[MAX_CREATURES];
    private final double[] driftY = new double[MAX_CREATURES];
    private final double[] opacity = new double[MAX_CREATURES];
    private final double[] encounterHue = new double[MAX_CREATURES];
    private final double[] steeringX = new double[MAX_CREATURES];
    private final double[] steeringY = new double[MAX_CREATURES];
    private final double[] steeringZ = new double[MAX_CREATURES];
    private final Random random;
    private final double structureStrength;
    private double structurePhase;
    private double streamMix, pairMix;
    private int maxCount = 3;
    private int targetCount = 3;
    private boolean varying;
    private boolean collisions;
    private double width = 1.0;
    private double height = 2.0;
    private double radius = 0.24;
    private double targetRadius = 0.24;
    private double accumulator;
    private double untilCountChange;

    public PondDynamics(long seed) {
        this(seed, 0.50);
    }

    /** The zero-strength variant supports numerical comparison of the shared current. */
    PondDynamics(long seed, double collectiveStrength) {
        if (!finite(collectiveStrength) || collectiveStrength < 0 || collectiveStrength > 1)
            throw new IllegalArgumentException("Collective strength must be from zero through one");
        structureStrength = collectiveStrength;
        structurePhase = ((seed * 0x9e3779b97f4a7c15L) >>> 11) * 0x1.0p-53 * Math.PI * 2;
        random = new Random(seed ^ 0x504f4e44424f4459L);
        // First three occupy different rows; the remaining pooled bodies fill
        // the gaps when revealed. The seed only introduces a small offset.
        int[] cellOrder = {1, 6, 5, 0, 8, 3, 2, 7, 4};
        for (int i = 0; i < MAX_CREATURES; i++) {
            int cell = cellOrder[i];
            x[i] = (cell % 3 - 1) * 0.25 + (random.nextDouble() - 0.5) * 0.035;
            y[i] = (cell / 3 - 1) * 0.53 + (random.nextDouble() - 0.5) * 0.05;
            double angle = random.nextDouble() * Math.PI * 2.0;
            driftX[i] = Math.cos(angle) * 0.045;
            driftY[i] = Math.sin(angle) * 0.045;
            vx[i] = driftX[i];
            vy[i] = driftY[i];
            opacity[i] = i < 3 ? 1.0 : 0.0;
            keepInside(i);
        }
        untilCountChange = nextInterval();
    }

    /** Idempotent: repeated settings reads never restart a fade or count timer. */
    public void configure(int requestedMax, boolean changeCount, boolean collide) {
        int nextMax = Math.max(1, Math.min(MAX_CREATURES, requestedMax));
        if (nextMax == maxCount && changeCount == varying && collide == collisions) return;
        boolean scheduleChanged = nextMax != maxCount || changeCount != varying;
        boolean wasVarying = varying;
        maxCount = nextMax;
        varying = changeCount;
        collisions = collide;
        targetRadius = clamp(0.24 * Math.sqrt(3.0 / maxCount), 0.12, 0.34);
        if (!varying) targetCount = maxCount;
        else if (!wasVarying) targetCount = Math.min(3, maxCount);
        else targetCount = Math.max(1, Math.min(targetCount, maxCount));
        if (scheduleChanged) untilCountChange = nextInterval();
    }

    /**
     * A paused render can update screen geometry, but does not advance motion,
     * fades, or the population timer. Long pauses have bounded catch-up work.
     * drive[i][0..3] accepts normalized chaotic values; absent values mean zero.
     */
    public void advance(double seconds, double widthUnits, double heightUnits, double[][] drive) {
        resize(widthUnits, heightUnits);
        if (!(seconds > 0.0) || !finite(seconds)) return;
        accumulator += Math.min(seconds, 0.5);
        while (accumulator + 1e-12 >= STEP) {
            step(drive);
            accumulator -= STEP;
            if (accumulator < 0.0) accumulator = 0.0;
        }
    }

    public double getX(int creature) { return x[creature]; }
    public double getY(int creature) { return y[creature]; }
    public double getRadius(int creature) { return radius; }
    /** Stable visual depth in [0.40, 1]. Motion does not change it. */
    public double getDepth(int creature) { return DEPTH[creature]; }
    public double getOpacity(int creature) { return opacity[creature]; }
    /** Continuous encounter tint in degrees, bounded to [-24,24]. */
    public double getEncounterHueOffset(int creature) { return encounterHue[creature]; }
    public int getTargetCount() { return targetCount; }
    public int getMaxCount() { return maxCount; }
    public int getVisibleCount() {
        int count = 0;
        for (int i = 0; i < MAX_CREATURES; i++) if (opacity[i] > 0.001) count++;
        return count;
    }

    private void step(double[][] drive) {
        if (varying && maxCount > 1) {
            untilCountChange -= STEP;
            if (untilCountChange <= 0.0) {
                // Select a different target, including both range endpoints.
                int next = 1 + random.nextInt(maxCount - 1);
                if (next >= targetCount) next++;
                targetCount = next;
                untilCountChange += nextInterval();
            }
        }
        radius += (targetRadius - radius) * (STEP / 0.7);
        double collectiveZ = 0, collectiveMass = 0;
        for (int i = 0; i < MAX_CREATURES; i++) {
            opacity[i] = clamp(opacity[i] + (i < targetCount ? STEP : -STEP) / FADE_SECONDS, 0.0, 1.0);
            // Slowly filtered chaos steers the current; every body observes the
            // same old position snapshot before any positions are advanced.
            steeringX[i] += (driveValue(drive, i, 0) - steeringX[i]) * (STEP / 3.0);
            steeringY[i] += (driveValue(drive, i, 1) - steeringY[i]) * (STEP / 3.0);
            steeringZ[i] += (driveValue(drive, i, 2) - steeringZ[i]) * (STEP / 3.0);
            collectiveZ += steeringZ[i] * opacity[i];
            collectiveMass += opacity[i];
        }
        collectiveZ /= Math.max(1e-9, collectiveMass);
        // No discrete modes: two broad tendencies pass smoothly through one
        // another. At 0.18x, a substantial change takes tens of visible seconds.
        structurePhase += STEP * (0.32 + 0.04 * collectiveZ);
        streamMix = structureStrength * (0.18 + 0.30 * (0.5 + 0.5 * Math.sin(structurePhase)));
        pairMix = structureStrength * (0.35 + 0.65 * (0.5 + 0.5 * Math.sin(structurePhase * 0.73 + 1.1)));
        for (int i = 0; i < MAX_CREATURES; i++) {
            double edgeX = width * 0.5 - radius;
            double edgeY = height * 0.5 - radius;
            double axisX = edgeX * 0.72;
            double axisY = edgeY * 0.72;
            double orbitX = steeringX[i] * edgeX * 0.12;
            double orbitY = steeringY[i] * edgeY * 0.12;
            double nx = (x[i] - orbitX) / axisX;
            double ny = (y[i] - orbitY) / axisY;
            double ring = 0.83 + 0.08 * driftY[i] / 0.045;
            double radial = clamp(0.70 * (ring * ring - nx * nx - ny * ny), -0.55, 0.35);
            double angular = 0.16 + 0.035 * steeringZ[i] + 0.018 * driftX[i] / 0.045;
            double direction = i % 3 == 1 ? -1.0 : 1.0;
            double desiredX = axisX * (radial * nx - direction * angular * ny);
            double desiredY = axisY * (radial * ny + direction * angular * nx);
            // A shared, gently curving stream opens the circulating arrangement.
            // It remains subordinate to the orbit, keeping room for free motion.
            double streamAngle = 0.9 * y[i] / edgeY + structurePhase * 0.65;
            double streamCenter = edgeX * 0.45 * Math.sin(streamAngle);
            double streamY = 0.08 * Math.cos(structurePhase * 0.48);
            double streamX = 0.20 * (streamCenter - x[i])
                    + edgeX * 0.45 * 0.9 / edgeY * Math.cos(streamAngle) * streamY;
            desiredX += (streamX - desiredX) * streamMix;
            desiredY += (streamY - desiredY) * streamMix;
            // Weak distance-weighted neighbor attraction and circulation create
            // temporary pairs and arcs. There are no assigned slots or snap-to
            // targets; hard contact remains governed by the collision switch.
            double neighborX = 0, neighborY = 0, neighborMass = 0;
            if (opacity[i] > 0.001) {
                for (int j = 0; j < MAX_CREATURES; j++) {
                    if (j == i || opacity[j] <= 0.001) continue;
                    double dx = x[j] - x[i], dy = y[j] - y[i];
                    double square = dx * dx + dy * dy;
                    if (square < 1e-12) continue;
                    double distance = Math.sqrt(square);
                    double weight = opacity[j] / (1 + square / (9 * radius * radius));
                    double attraction = 0.025 * clamp((distance - 2.4 * radius) / (2 * radius), -1, 1);
                    double curl = 0.028;
                    neighborX += weight * (attraction * dx - curl * dy) / distance;
                    neighborY += weight * (attraction * dy + curl * dx) / distance;
                    neighborMass += weight;
                }
            }
            desiredX += pairMix * neighborX / (1 + neighborMass);
            desiredY += pairMix * neighborY / (1 + neighborMass);
            // Begin turning well before contact. The final wall bounce remains a
            // safety constraint; no persistent outward drift pushes into it.
            double marginX = Math.min(0.15, edgeX * 0.50);
            double marginY = Math.min(0.15, edgeY * 0.50);
            double nearWallX = clamp((Math.abs(x[i]) - (edgeX - marginX)) / marginX, 0.0, 1.0);
            double nearWallY = clamp((Math.abs(y[i]) - (edgeY - marginY)) / marginY, 0.0, 1.0);
            desiredX -= Math.signum(x[i]) * 0.12 * nearWallX * nearWallX;
            desiredY -= Math.signum(y[i]) * 0.12 * nearWallY * nearWallY;
            vx[i] += (desiredX - vx[i]) * STEP * 0.65;
            vy[i] += (desiredY - vy[i]) * STEP * 0.65;
            limitSpeed(i);
        }
        for (int i = 0; i < MAX_CREATURES; i++) {
            x[i] += vx[i] * STEP;
            y[i] += vy[i] * STEP;
            keepInside(i);
        }
        if (collisions) {
            // Nine bodies have at most 36 pairs. Fading bodies contribute in
            // proportion to visibility, avoiding a sudden force on appearance.
            for (int i = 0; i < MAX_CREATURES; i++) {
                if (opacity[i] < 0.02) continue;
                for (int j = i + 1; j < MAX_CREATURES; j++) {
                    if (opacity[j] >= 0.02) collide(i, j);
                }
            }
            for (int i = 0; i < MAX_CREATURES; i++) {
                limitSpeed(i);
                keepInside(i);
            }
        }
        updateEncounterHues();
    }

    /**
     * Nearby, visible bodies gently exchange hue direction along the shortest
     * color-wheel arc. This state never feeds back into motion or collisions.
     * It advances only on the existing fixed motion clock.
     */
    private void updateEncounterHues() {
        double outer = radius * 3.2;
        double inner = radius * 2.0;
        for (int i = 0; i < MAX_CREATURES; i++) {
            double differenceSum = 0, weightSum = 0;
            if (opacity[i] > 0.001) {
                for (int j = 0; j < MAX_CREATURES; j++) {
                    if (j == i || opacity[j] <= 0.001) continue;
                    double dx = x[j] - x[i], dy = y[j] - y[i];
                    double squaredDistance = dx * dx + dy * dy;
                    if (squaredDistance >= outer * outer) continue;
                    double proximity = clamp((outer - Math.sqrt(squaredDistance)) / (outer - inner), 0, 1);
                    proximity *= proximity * (3 - 2 * proximity);
                    double weight = proximity * opacity[j];
                    // Intrinsic hues are c*32 degrees, matching the renderer.
                    // This signed shortest arc also handles 0/360 crossings.
                    int hueDifference = ((j - i) * 32 + 540) % 360 - 180;
                    differenceSum += weight * hueDifference;
                    weightSum += weight;
                }
            }
            double target = clamp(0.45 * differenceSum / Math.max(1, weightSum), -24, 24) * opacity[i];
            double responseSeconds = weightSum > 0.001 ? 2.2 : 4.0;
            double change = (target - encounterHue[i]) * STEP / responseSeconds;
            encounterHue[i] = clamp(encounterHue[i] + clamp(change, -8 * STEP, 8 * STEP), -24, 24);
        }
    }

    private void collide(int a, int b) {
        double dx = x[b] - x[a];
        double dy = y[b] - y[a];
        double contact = radius * (1.3 + 0.35 * (opacity[a] + opacity[b]));
        double distanceSquared = dx * dx + dy * dy;
        double approach = contact * 1.12;
        if (distanceSquared >= approach * approach) return;
        double distance = Math.sqrt(distanceSquared);
        double nx, ny;
        if (distance > 1e-9) {
            nx = dx / distance;
            ny = dy / distance;
        } else {
            nx = ((a + b) & 1) == 0 ? 1.0 : 0.0;
            ny = 1.0 - nx;
        }
        double strength = Math.min(opacity[a], opacity[b]);
        double repel = (approach - distance) * 0.7 * STEP * strength;
        vx[a] -= nx * repel;
        vy[a] -= ny * repel;
        vx[b] += nx * repel;
        vy[b] += ny * repel;
        if (distance < contact) {
            double correction = (contact - distance) * 0.42 * strength;
            x[a] -= nx * correction;
            y[a] -= ny * correction;
            x[b] += nx * correction;
            y[b] += ny * correction;
            double closingSpeed = (vx[b] - vx[a]) * nx + (vy[b] - vy[a]) * ny;
            if (closingSpeed < 0.0) {
                double impulse = -closingSpeed * 0.90 * strength;
                vx[a] -= nx * impulse;
                vy[a] -= ny * impulse;
                vx[b] += nx * impulse;
                vy[b] += ny * impulse;
            }
        }
    }

    private void resize(double requestedWidth, double requestedHeight) {
        double nextWidth = finite(requestedWidth) && requestedWidth > 0.0
                ? clamp(requestedWidth, 1.0, 20.0) : width;
        double nextHeight = finite(requestedHeight) && requestedHeight > 0.0
                ? clamp(requestedHeight, 1.0, 20.0) : height;
        if (nextWidth == width && nextHeight == height) return;
        double ratioX = (nextWidth * 0.5 - radius) / (width * 0.5 - radius);
        double ratioY = (nextHeight * 0.5 - radius) / (height * 0.5 - radius);
        width = nextWidth;
        height = nextHeight;
        for (int i = 0; i < MAX_CREATURES; i++) {
            x[i] *= ratioX;
            y[i] *= ratioY;
            keepInside(i);
        }
    }

    private void keepInside(int i) {
        if (!finite(x[i]) || !finite(y[i]) || !finite(vx[i]) || !finite(vy[i])) {
            x[i] = 0.0;
            y[i] = (i - 4) * 0.03;
            vx[i] = driftX[i];
            vy[i] = driftY[i];
        }
        double edgeX = width * 0.5 - radius;
        double edgeY = height * 0.5 - radius;
        if (x[i] < -edgeX) { x[i] = -edgeX; vx[i] = Math.abs(vx[i]) * 0.92; }
        else if (x[i] > edgeX) { x[i] = edgeX; vx[i] = -Math.abs(vx[i]) * 0.92; }
        if (y[i] < -edgeY) { y[i] = -edgeY; vy[i] = Math.abs(vy[i]) * 0.92; }
        else if (y[i] > edgeY) { y[i] = edgeY; vy[i] = -Math.abs(vy[i]) * 0.92; }
    }

    private void limitSpeed(int i) {
        double square = vx[i] * vx[i] + vy[i] * vy[i];
        if (square > MAX_SPEED * MAX_SPEED) {
            double scale = MAX_SPEED / Math.sqrt(square);
            vx[i] *= scale;
            vy[i] *= scale;
        }
    }

    private double nextInterval() { return 8.0 + 6.0 * random.nextDouble(); }
    private static double driveValue(double[][] values, int body, int axis) {
        if (values == null || body >= values.length || values[body] == null || axis >= values[body].length) return 0.0;
        double value = values[body][axis];
        return finite(value) ? clamp(value, -1.0, 1.0) : 0.0;
    }
    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }
    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }

    // Package-private fixture access keeps collision tests independent of the
    // seeded initial layout and avoids reflection or an Android dependency.
    void placeBodyForTest(int body, double px, double py, double dx, double dy) {
        x[body] = px; y[body] = py; vx[body] = dx; vy[body] = dy;
    }
    double structurePhaseForTest() { return structurePhase; }
    double streamMixForTest() { return streamMix; }
    double pairMixForTest() { return pairMix; }
    double velocityXForTest(int body) { return vx[body]; }
    double velocityYForTest(int body) { return vy[body]; }
}
