package org.ostadix.koilife;

import java.util.Random;

/**
 * Small, deterministic pool of bodies that share one body of water.
 *
 * Coordinates are centered and measured in units of the screen's shorter
 * dimension. Collision circles are gentle visual bounds, not exact outlines of
 * the changing procedural forms. All buffers are retained; advance performs no
 * allocations.
 *
 * When merging is enabled, bodies that stay in contact form a bond. A growing
 * bond pulls their centers into one and aligns their motion until they move as
 * a single merged body. Later the bond weakens again: one body buds off along a
 * fresh axis and the two drift apart. A divided pair rests before it can fuse
 * again, and no merged body holds more than {@link #MAX_CLUSTER} members. With
 * merging disabled, the pond behaves exactly as before: soft repulsion only.
 */
public final class PondDynamics {
    public static final int MAX_CREATURES = 9;
    /** Largest number of bodies a single merged body may contain. */
    public static final int MAX_CLUSTER = 4;
    /** Event types returned by {@link #readEvent}. */
    public static final int EVENT_MERGED = 1, EVENT_DIVIDING = 2, EVENT_DIVIDED = 3;
    /** Pair states. */
    public static final int FREE = 0, FUSING = 1, MERGED = 2, DIVIDING = 3;
    private static final int PAIRS = MAX_CREATURES * (MAX_CREATURES - 1) / 2;
    private static final int EVENT_CAPACITY = 16;
    private static final double STEP = 1.0 / 120.0;
    private static final double FADE_SECONDS = 1.5;
    private static final double MAX_SPEED = 0.23;
    /** Weight of the shared Life current relative to each body's own swimming. */
    private static final double CURRENT_GAIN = 0.055;
    /** Sustained touch, in pond seconds, before two bodies begin to fuse. */
    private static final double CONTACT_SECONDS = 0.45;
    private static final double FUSE_SECONDS = 2.6;
    private static final double DIVIDE_SECONDS = 2.2;
    private static final double MIN_MERGED_SECONDS = 5.0;
    private static final double MEAN_MERGED_SECONDS = 16.0;
    private static final double REFRACTORY_SECONDS = 9.0;
    private static final double DIVIDE_SPEED = 0.085;
    /**
     * Fixed visual depth for the nine pooled bodies. 1 is nearest.
     * Swimming, collisions, and the shared radius do not read this.
     */
    private static final double[] DEPTH = {
        1.00, 0.46, 0.78, 0.58, 0.40, 0.88, 0.66, 0.94, 0.52
    };
    private static final int[][] PAIR_INDEX = new int[MAX_CREATURES][MAX_CREATURES];
    private static final int[] PAIR_A = new int[PAIRS], PAIR_B = new int[PAIRS];
    static {
        int p = 0;
        for (int i = 0; i < MAX_CREATURES; i++) {
            PAIR_INDEX[i][i] = -1;
            for (int j = i + 1; j < MAX_CREATURES; j++) {
                PAIR_INDEX[i][j] = p; PAIR_INDEX[j][i] = p;
                PAIR_A[p] = i; PAIR_B[p] = j; p++;
            }
        }
    }
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
    private final double[] currentX = new double[MAX_CREATURES];
    private final double[] currentY = new double[MAX_CREATURES];
    private final double[] turbulence = new double[MAX_CREATURES];
    private final double[] bond = new double[PAIRS];
    private final int[] pairState = new int[PAIRS];
    private final double[] pairClock = new double[PAIRS];
    private final double[] refractory = new double[PAIRS];
    private final double[] pairPhase = new double[PAIRS];
    private final double[] axisX = new double[PAIRS], axisY = new double[PAIRS];
    private final int[] root = new int[MAX_CREATURES];
    private final int[] clusterSize = new int[MAX_CREATURES];
    private final int[] eventType = new int[EVENT_CAPACITY];
    private final double[] eventX = new double[EVENT_CAPACITY], eventY = new double[EVENT_CAPACITY];
    private final int[] eventA = new int[EVENT_CAPACITY], eventB = new int[EVENT_CAPACITY];
    private long eventSerial;
    private int mergeCount, divideCount;
    private final Random random;
    private final double structureStrength;
    private double structurePhase;
    private double streamMix, pairMix;
    private int maxCount = 3;
    private int targetCount = 3;
    private boolean varying;
    private boolean collisions;
    private boolean merging;
    private double width = 1.0;
    private double height = 2.0;
    private double radius = 0.24;
    private double targetRadius = 0.24;
    private double accumulator;
    private double untilCountChange;
    // A person's touch and return: zero strength leaves the pond untouched.
    private double attentionX, attentionY, attentionStrength;
    private double gatherX, gatherY, gatherStrength;

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
            root[i] = i;
            clusterSize[i] = 1;
        }
        untilCountChange = nextInterval();
        // A separate generator keeps the original layout and count schedule
        // identical to earlier releases for the same seed.
        Random affinity = new Random(seed ^ 0x4b494e5348495053L);
        for (int p = 0; p < PAIRS; p++) pairPhase[p] = affinity.nextDouble() * Math.PI * 2.0;
    }

    /** Idempotent: repeated settings reads never restart a fade or count timer. */
    public void configure(int requestedMax, boolean changeCount, boolean collide) {
        configure(requestedMax, changeCount, collide, merging);
    }

    /**
     * As {@link #configure(int, boolean, boolean)}, also choosing whether bodies
     * can merge. Turning merging off lets existing bonds divide gradually.
     */
    public void configure(int requestedMax, boolean changeCount, boolean collide, boolean merge) {
        int nextMax = Math.max(1, Math.min(MAX_CREATURES, requestedMax));
        if (merge != merging) {
            merging = merge;
            if (!merging) {
                for (int p = 0; p < PAIRS; p++) {
                    if (pairState[p] == FUSING || pairState[p] == MERGED) beginDividing(p, false);
                }
            }
        }
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
     * fades, bonds, or the population timer. Long pauses have bounded catch-up
     * work. drive[i][0..3] accepts normalized chaotic values; drive[i][4..5] the
     * shared Life current at the body; drive[i][6] local Life turbulence in
     * [0, 1]. Absent values mean zero.
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

    /**
     * A point of interest in centered units. Positive strength draws nearby
     * bodies in to circle just outside it; negative strength sends them away.
     * Strength is clamped to [-1, 1]; zero has no effect.
     */
    public void setAttention(double px, double py, double strength) {
        if (!finite(px) || !finite(py) || !finite(strength)) { attentionStrength = 0; return; }
        attentionX = px; attentionY = py; attentionStrength = clamp(strength, -1, 1);
    }

    /** Gathers every visible body toward a point; strength in [0, 1]. */
    public void setGathering(double px, double py, double strength) {
        if (!finite(px) || !finite(py) || !finite(strength)) { gatherStrength = 0; return; }
        gatherX = px; gatherY = py; gatherStrength = clamp(strength, 0, 1);
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
    public boolean isMerging() { return merging; }
    public int getVisibleCount() {
        int count = 0;
        for (int i = 0; i < MAX_CREATURES; i++) if (opacity[i] > 0.001) count++;
        return count;
    }
    /** Bond strength in [0, 1] between two different bodies; zero for a body and itself. */
    public double getBond(int a, int b) {
        int p = PAIR_INDEX[a][b];
        return p < 0 ? 0.0 : bond[p];
    }
    public int getPairState(int a, int b) {
        int p = PAIR_INDEX[a][b];
        return p < 0 ? FREE : pairState[p];
    }
    /** Lowest-index member of the body's current merged group (itself when alone). */
    public int getClusterRoot(int creature) { return root[creature]; }
    public int getClusterSize(int creature) { return clusterSize[root[creature]]; }
    /** Sum of bond strength times partner visibility: 0 alone, about 1 per merged partner. */
    public double getBondMass(int creature) {
        double mass = 0;
        for (int j = 0; j < MAX_CREATURES; j++) {
            if (j != creature) mass += getBond(creature, j) * opacity[j];
        }
        return mass;
    }
    /** Number of completed fusions and divisions since construction. */
    public int getMergeCount() { return mergeCount; }
    public int getDivideCount() { return divideCount; }
    /** Serial of the most recent event; zero before the first. */
    public long getEventSerial() { return eventSerial; }
    /**
     * Copies event {@code serial}: out[0..1] its centered position, out[2..3]
     * the two bodies. Returns its type, or zero when it was never written or
     * has been overwritten. Allocation-free.
     */
    public int readEvent(long serial, double[] out) {
        if (serial <= 0 || serial > eventSerial || eventSerial - serial >= EVENT_CAPACITY
                || out == null || out.length < 4) return 0;
        int slot = (int) (serial % EVENT_CAPACITY);
        out[0] = eventX[slot]; out[1] = eventY[slot];
        out[2] = eventA[slot]; out[3] = eventB[slot];
        return eventType[slot];
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
            currentX[i] += (driveValue(drive, i, 4) - currentX[i]) * (STEP / 0.8);
            currentY[i] += (driveValue(drive, i, 5) - currentY[i]) * (STEP / 0.8);
            turbulence[i] += (clamp(driveValue(drive, i, 6), 0, 1) - turbulence[i]) * (STEP / 1.5);
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
            double axisXScale = edgeX * 0.72;
            double axisYScale = edgeY * 0.72;
            double orbitX = steeringX[i] * edgeX * 0.12;
            double orbitY = steeringY[i] * edgeY * 0.12;
            double nx = (x[i] - orbitX) / axisXScale;
            double ny = (y[i] - orbitY) / axisYScale;
            double ring = 0.83 + 0.08 * driftY[i] / 0.045;
            double radial = clamp(0.70 * (ring * ring - nx * nx - ny * ny), -0.55, 0.35);
            double angular = 0.16 + 0.035 * steeringZ[i] + 0.018 * driftX[i] / 0.045;
            double direction = i % 3 == 1 ? -1.0 : 1.0;
            double desiredX = axisXScale * (radial * nx - direction * angular * ny);
            double desiredY = axisYScale * (radial * ny + direction * angular * nx);
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
            // targets. Bodies that may fuse prefer touching to keeping a gap.
            double neighborX = 0, neighborY = 0, neighborMass = 0;
            if (opacity[i] > 0.001) {
                for (int j = 0; j < MAX_CREATURES; j++) {
                    if (j == i || opacity[j] <= 0.001) continue;
                    double dx = x[j] - x[i], dy = y[j] - y[i];
                    double square = dx * dx + dy * dy;
                    if (square < 1e-12) continue;
                    double distance = Math.sqrt(square);
                    double weight = opacity[j] / (1 + square / (9 * radius * radius));
                    int p = PAIR_INDEX[i][j];
                    double attraction;
                    if (merging && pairState[p] == FREE && canFuse(p)) {
                        // Affinity rises and falls slowly, so not every passing
                        // neighbor is drawn in at once.
                        double affinity = 0.5 + 0.5 * Math.sin(structurePhase * 0.37 + pairPhase[p]);
                        attraction = 0.034 * affinity * clamp(distance / (2 * radius), 0, 1)
                                + 0.025 * (1 - affinity) * clamp((distance - 2.4 * radius) / (2 * radius), -1, 1);
                    } else if (pairState[p] != FREE) {
                        attraction = 0;  // the bond itself governs this pair
                    } else {
                        attraction = 0.025 * clamp((distance - 2.4 * radius) / (2 * radius), -1, 1);
                    }
                    double curl = 0.028;
                    neighborX += weight * (attraction * dx - curl * dy) / distance;
                    neighborY += weight * (attraction * dy + curl * dx) / distance;
                    neighborMass += weight;
                }
            }
            desiredX += pairMix * neighborX / (1 + neighborMass);
            desiredY += pairMix * neighborY / (1 + neighborMass);
            // Everyone swims in the same Life current, so neighbors drift together.
            desiredX += CURRENT_GAIN * currentX[i];
            desiredY += CURRENT_GAIN * currentY[i];
            if (attentionStrength != 0.0) {
                double dx = attentionX - x[i], dy = attentionY - y[i];
                double distance = Math.sqrt(dx * dx + dy * dy) + 1e-9;
                double reach = distance / 0.85, fall = 1.0 / (1.0 + reach * reach);
                if (attentionStrength > 0.0) {
                    // Approach, then circle just outside the point of interest.
                    double approach = clamp((distance - radius * 0.9) / radius, -0.6, 1.0);
                    desiredX += attentionStrength * fall * (0.55 * approach * dx - 0.12 * dy) / distance;
                    desiredY += attentionStrength * fall * (0.55 * approach * dy + 0.12 * dx) / distance;
                } else {
                    desiredX += attentionStrength * fall * 0.40 * dx / distance;
                    desiredY += attentionStrength * fall * 0.40 * dy / distance;
                }
            }
            if (gatherStrength > 0.0) {
                desiredX += gatherStrength * 0.14 * (gatherX - x[i]);
                desiredY += gatherStrength * 0.14 * (gatherY - y[i]);
            }
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
        applyBondForces();
        for (int i = 0; i < MAX_CREATURES; i++) {
            x[i] += vx[i] * STEP;
            y[i] += vy[i] * STEP;
            keepInside(i);
        }
        settleMergedBodies();
        if (collisions) {
            // Nine bodies have at most 36 pairs. Fading bodies contribute in
            // proportion to visibility, avoiding a sudden force on appearance.
            for (int i = 0; i < MAX_CREATURES; i++) {
                if (opacity[i] < 0.02) continue;
                for (int j = i + 1; j < MAX_CREATURES; j++) {
                    if (opacity[j] < 0.02) continue;
                    int p = PAIR_INDEX[i][j];
                    // Bonded bodies overlap on purpose; a pair that may fuse
                    // touches without bouncing.
                    if (bond[p] > 0.0 || pairState[p] != FREE) continue;
                    if (merging && canFuse(p)) continue;
                    collide(i, j);
                }
            }
            for (int i = 0; i < MAX_CREATURES; i++) {
                limitSpeed(i);
                keepInside(i);
            }
        }
        if (merging || anyBond()) updateBonds();
        updateEncounterHues();
    }

    /** True while any pair is bonded or still dividing. */
    public boolean hasBonds() { return anyBond(); }

    private boolean anyBond() {
        for (int p = 0; p < PAIRS; p++) if (bond[p] > 0.0 || pairState[p] != FREE) return true;
        return false;
    }

    /** True when the pair is free, rested, visible, and would not overfill a merged body. */
    private boolean canFuse(int p) {
        int a = PAIR_A[p], b = PAIR_B[p];
        if (pairState[p] != FREE || refractory[p] > 0.0) return false;
        if (opacity[a] < 0.6 || opacity[b] < 0.6) return false;
        int ra = root[a], rb = root[b];
        return ra == rb || clusterSize[ra] + clusterSize[rb] <= MAX_CLUSTER;
    }

    private double contactDistance(int a, int b) {
        return radius * (1.3 + 0.35 * (opacity[a] + opacity[b]));
    }

    /**
     * A bond is a spring whose rest length shrinks to zero while fusing and
     * grows again while dividing, plus damping of the pair's relative motion.
     */
    private void applyBondForces() {
        for (int p = 0; p < PAIRS; p++) {
            double b = bond[p];
            if (b <= 0.0) continue;
            int i = PAIR_A[p], j = PAIR_B[p];
            double dx = x[j] - x[i], dy = y[j] - y[i];
            double distance = Math.sqrt(dx * dx + dy * dy);
            double nx, ny;
            if (distance > 1e-9) { nx = dx / distance; ny = dy / distance; }
            else { nx = axisX[p]; ny = axisY[p]; }
            double contact = contactDistance(i, j);
            double rest, stiffness;
            if (pairState[p] == DIVIDING) {
                rest = contact * 1.5 * (1.0 - b);
                stiffness = 2.2 * Math.max(b, 0.25);
            } else {
                double s = b * b * (3.0 - 2.0 * b);
                rest = contact * (1.0 - s);
                stiffness = 2.2 * b;
            }
            double pull = stiffness * (distance - rest) * STEP;
            vx[i] += nx * pull; vy[i] += ny * pull;
            vx[j] -= nx * pull; vy[j] -= ny * pull;
            double align = Math.min(0.5, 1.5 * b * STEP);
            double rx = (vx[j] - vx[i]) * align, ry = (vy[j] - vy[i]) * align;
            vx[i] += rx; vy[i] += ry;
            vx[j] -= rx; vy[j] -= ry;
            limitSpeed(i);
            limitSpeed(j);
        }
    }

    /** Fully merged members share one center and one velocity. */
    private void settleMergedBodies() {
        for (int p = 0; p < PAIRS; p++) {
            if (pairState[p] != MERGED) continue;
            int i = PAIR_A[p], j = PAIR_B[p];
            double mx = (x[i] + x[j]) * 0.5, my = (y[i] + y[j]) * 0.5;
            x[i] += (mx - x[i]) * 0.08; y[i] += (my - y[i]) * 0.08;
            x[j] += (mx - x[j]) * 0.08; y[j] += (my - y[j]) * 0.08;
            double mvx = (vx[i] + vx[j]) * 0.5, mvy = (vy[i] + vy[j]) * 0.5;
            vx[i] += (mvx - vx[i]) * 0.08; vy[i] += (mvy - vy[i]) * 0.08;
            vx[j] += (mvx - vx[j]) * 0.08; vy[j] += (mvy - vy[j]) * 0.08;
        }
    }

    private void updateBonds() {
        for (int p = 0; p < PAIRS; p++) {
            int i = PAIR_A[p], j = PAIR_B[p];
            double dx = x[j] - x[i], dy = y[j] - y[i];
            double distance = Math.sqrt(dx * dx + dy * dy);
            switch (pairState[p]) {
                case FREE:
                    if (refractory[p] > 0.0) refractory[p] = Math.max(0.0, refractory[p] - STEP);
                    if (merging && canFuse(p) && distance < contactDistance(i, j) * 1.12) {
                        pairClock[p] += STEP;
                        if (pairClock[p] >= CONTACT_SECONDS) {
                            pairState[p] = FUSING;
                            pairClock[p] = 0.0;
                            // Commit the membership now so that simultaneous
                            // contacts cannot overfill one merged body.
                            computeClusters();
                        }
                    } else {
                        pairClock[p] = Math.max(0.0, pairClock[p] - 2.0 * STEP);
                    }
                    break;
                case FUSING:
                    if (opacity[i] < 0.3 || opacity[j] < 0.3 || distance > radius * 4.0) {
                        beginDividing(p, false);
                        break;
                    }
                    bond[p] = Math.min(1.0, bond[p] + STEP / FUSE_SECONDS);
                    if (bond[p] >= 1.0) {
                        pairState[p] = MERGED;
                        pairClock[p] = 0.0;
                        mergeCount++;
                        record(EVENT_MERGED, (x[i] + x[j]) * 0.5, (y[i] + y[j]) * 0.5, i, j);
                    }
                    break;
                case MERGED:
                    if (opacity[i] < 0.05 || opacity[j] < 0.05) {
                        // A fading member is quietly absorbed; nothing visible remains to divide.
                        bond[p] = 0.0; pairState[p] = FREE; refractory[p] = REFRACTORY_SECONDS;
                        break;
                    }
                    pairClock[p] += STEP;
                    if (pairClock[p] >= MIN_MERGED_SECONDS) {
                        double unrest = 0.55 + 1.8 * 0.5 * (turbulence[i] + turbulence[j]);
                        double crowding = clusterSize[root[i]] >= 3 ? 1.6 : 1.0;
                        double hazard = unrest * crowding / MEAN_MERGED_SECONDS;
                        if (random.nextDouble() < hazard * STEP) divideFrom(i, j);
                    }
                    break;
                case DIVIDING:
                    bond[p] = Math.max(0.0, bond[p] - STEP / DIVIDE_SECONDS);
                    if (bond[p] <= 0.0) {
                        pairState[p] = FREE;
                        pairClock[p] = 0.0;
                        refractory[p] = REFRACTORY_SECONDS;
                        divideCount++;
                        record(EVENT_DIVIDED, (x[i] + x[j]) * 0.5, (y[i] + y[j]) * 0.5, i, j);
                    }
                    break;
                default:
                    pairState[p] = FREE;
            }
        }
        computeClusters();
    }

    /**
     * The higher-index body buds off its merged group along a fresh axis; the
     * rest of the group stays together.
     */
    private void divideFrom(int a, int b) {
        int bud = Math.max(a, b), group = root[bud];
        double angle = random.nextDouble() * Math.PI * 2.0;
        double ux = Math.cos(angle), uy = Math.sin(angle);
        int remaining = 0;
        for (int m = 0; m < MAX_CREATURES; m++) {
            if (m == bud || root[m] != group) continue;
            int p = PAIR_INDEX[m][bud];
            if (bond[p] > 0.0 || pairState[p] != FREE) {
                // axis points from the lower-index body toward the higher one.
                axisX[p] = m < bud ? ux : -ux;
                axisY[p] = m < bud ? uy : -uy;
                pairState[p] = DIVIDING;
                pairClock[p] = 0.0;
            }
            remaining++;
        }
        if (remaining == 0) return;
        vx[bud] += ux * DIVIDE_SPEED; vy[bud] += uy * DIVIDE_SPEED;
        for (int m = 0; m < MAX_CREATURES; m++) {
            if (m == bud || root[m] != group) continue;
            vx[m] -= ux * DIVIDE_SPEED / remaining;
            vy[m] -= uy * DIVIDE_SPEED / remaining;
        }
        record(EVENT_DIVIDING, x[bud], y[bud], Math.min(a, b), bud);
    }

    private void beginDividing(int p, boolean announce) {
        if (bond[p] <= 0.0) {
            bond[p] = 0.0; pairState[p] = FREE; pairClock[p] = 0.0;
            refractory[p] = REFRACTORY_SECONDS;
            return;
        }
        int i = PAIR_A[p], j = PAIR_B[p];
        double dx = x[j] - x[i], dy = y[j] - y[i], d = Math.sqrt(dx * dx + dy * dy);
        if (d > 1e-9) { axisX[p] = dx / d; axisY[p] = dy / d; }
        else { axisX[p] = 1.0; axisY[p] = 0.0; }
        pairState[p] = DIVIDING;
        pairClock[p] = 0.0;
        if (announce) record(EVENT_DIVIDING, x[j], y[j], i, j);
    }

    /** Union-find over bonded pairs; roots are the lowest member index. */
    private void computeClusters() {
        for (int i = 0; i < MAX_CREATURES; i++) { root[i] = i; clusterSize[i] = 0; }
        for (int p = 0; p < PAIRS; p++) {
            if (pairState[p] == FREE) continue;
            if (pairState[p] == DIVIDING && bond[p] < 0.5) continue;
            int ra = find(PAIR_A[p]), rb = find(PAIR_B[p]);
            if (ra == rb) continue;
            if (ra < rb) root[rb] = ra; else root[ra] = rb;
        }
        for (int i = 0; i < MAX_CREATURES; i++) {
            root[i] = find(i);
            clusterSize[root[i]]++;
        }
    }

    private int find(int i) {
        while (root[i] != i) i = root[i];
        return i;
    }

    private void record(int type, double px, double py, int a, int b) {
        eventSerial++;
        int slot = (int) (eventSerial % EVENT_CAPACITY);
        eventType[slot] = type; eventX[slot] = px; eventY[slot] = py;
        eventA[slot] = a; eventB[slot] = b;
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
        double contact = contactDistance(a, b);
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
