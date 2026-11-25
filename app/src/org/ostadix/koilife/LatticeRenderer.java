/*
 * Koiros: dots that obey their own equations in one hidden Game of Life world.
 * GPL-3.0-or-later. See LICENSE and CREDITS.
 */
package org.ostadix.koilife;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import java.util.Arrays;

/**
 * Draws {@link DotLattice}: every dot and every bond between two dots, and
 * nothing else that is not attached to them. A bond is drawn as a thin line
 * and always ends at two drawn dots. Dots in the bright half of their rhythm
 * carry a faint glow, so groups that keep the same rhythm visibly pulse
 * together. The Life world is never drawn; it supplies the current the dots
 * swim in, the restlessness that frays bonds, and carries colour between
 * bodies. No allocations occur per frame.
 */
public final class LatticeRenderer implements Pond {
    /** Dots in each lineage's group when it first appears. */
    static final int DOTS_PER_BODY = 270;
    private static final int COLOR_BUCKETS = 48, GRID_X = 13, GRID_Y = 19, GRID_SIZE = GRID_X * GRID_Y;
    private static final int WORLD_COLUMNS = 54, WORLD_ROWS = 120, WORLD_CELLS = WORLD_COLUMNS * WORLD_ROWS;
    private static final int LOCAL_RADIUS = 5, LINEAGES = 9;
    private static final int BACKGROUND = Color.rgb(3, 7, 14);
    private static final int MAX_FLASHES = 6, MAX_PENDING_FLASHES = 5;
    private static final double FLASH_SECONDS = 3.4, TINT_FADE_SECONDS = 26, TINT_ABSORB_SECONDS = 30;
    /**
     * Physics advances in fixed steps of pond time; frames between steps draw
     * positions interpolated between the last two states. Work therefore
     * follows the Drift, not the display rate: at 0.11x about four steps a
     * second, however many frames are drawn.
     */
    private static final double STEP = .025;
    /** The lattice runs this much faster than the classic forms at the same Drift setting. */
    private static final double PACE = 1.5;
    /** Below this point budget the glows are skipped; below the next, the body auras too. */
    private static final int GLOW_BUDGET = 6000, AURA_BUDGET = 4000;
    /**
     * Dots are drawn in four classes, far to near; the bright half of a dot's
     * rhythm lifts it one class. Width and opacity per class.
     */
    private static final int TIERS = 4;
    private static final float[] TIER_WIDTH = {.72f, .95f, 1.2f, 1.5f};
    private static final int[] TIER_ALPHA = {150, 195, 235, 255};
    /** Each body is drawn as a domed disc tilted in 3D: dome height and largest tilt, in radians. */
    private static final double DOME = .55, TILT = .62;
    /** Hue spread across a body, diagonally and from core to rim, in degrees. */
    private static final double GRADIENT_ARC = 100, RADIAL_ARC = 34;

    private final LifeField world;
    private final WorldTint tint = new WorldTint(WORLD_COLUMNS, WORLD_ROWS);
    private final CoupledChaos chaos;
    private final DotLattice dots;
    private final TouchRipples ripples = new TouchRipples();
    private boolean ripplesEnabled = true;

    private final double[] fieldX = new double[GRID_SIZE], fieldY = new double[GRID_SIZE];
    private final double[] projection = new double[GRID_SIZE];
    private final double[] waterX = new double[GRID_SIZE], waterY = new double[GRID_SIZE];
    private final double[] flowX = new double[GRID_SIZE], flowY = new double[GRID_SIZE];
    private final double[] turbulence = new double[GRID_SIZE];
    private final double[] local = new double[2], rippleSample = new double[3];
    private final double[] tintSample = new double[2], chaosSample = new double[4], eventOut = new double[3];
    private boolean waterReady;

    private final float[] points = new float[DotLattice.MAX_DOTS * 2], glows = new float[DotLattice.MAX_DOTS * 2];
    private final float[] lines = new float[DotLattice.MAX_EDGES * 4], faintLines = new float[DotLattice.MAX_EDGES * 4];
    private final float[] screenX = new float[DotLattice.MAX_DOTS], screenY = new float[DotLattice.MAX_DOTS];
    private final double[] previousX = new double[DotLattice.MAX_DOTS], previousY = new double[DotLattice.MAX_DOTS];
    private final double[] previousPhase = new double[DotLattice.MAX_DOTS];
    private final double[] drawX = new double[DotLattice.MAX_DOTS], drawY = new double[DotLattice.MAX_DOTS];
    private final double[] drawPhase = new double[DotLattice.MAX_DOTS];
    private final double[] auraX = new double[DotLattice.MAX_BODIES], auraY = new double[DotLattice.MAX_BODIES];
    private final int[] auraCount = new int[DotLattice.MAX_BODIES], slotOfId = new int[256];
    private double pondAccumulator;
    // Depth: each dot is drawn about a smoothed centre of its body, so fusing
    // bodies glide into one projection instead of jumping.
    private final double[] anchorX = new double[DotLattice.MAX_DOTS], anchorY = new double[DotLattice.MAX_DOTS];
    private final double[] anchorR = new double[DotLattice.MAX_DOTS];
    private final int[] dotTier = new int[DotLattice.MAX_DOTS];
    private final int[][] tierCounts = new int[TIERS][COLOR_BUCKETS], tierOffsets = new int[TIERS][COLOR_BUCKETS];
    private final int[][] tierCursor = new int[TIERS][COLOR_BUCKETS];
    private int anchorVersion = -1, anchorCount = -1;
    private double tiltClock, axisClock;
    private int savedVersion = -1, savedCount = -1;
    private int stepsThisFrame;
    private final int[] dotBucket = new int[DotLattice.MAX_DOTS];
    private final boolean[] visibleDot = new boolean[DotLattice.MAX_DOTS], glowing = new boolean[DotLattice.MAX_DOTS];
    private final boolean[] bright = new boolean[DotLattice.MAX_DOTS];
    private final int[] glowCounts = new int[COLOR_BUCKETS], glowOffsets = new int[COLOR_BUCKETS];
    private final int[] lineCounts = new int[COLOR_BUCKETS], lineOffsets = new int[COLOR_BUCKETS];
    private final int[] faintCounts = new int[COLOR_BUCKETS], faintOffsets = new int[COLOR_BUCKETS];
    private final int[] cursorA = new int[COLOR_BUCKETS], cursorB = new int[COLOR_BUCKETS];
    private final int[] colors = new int[COLOR_BUCKETS];
    private final Shader[] auraShaders = new Shader[COLOR_BUCKETS];
    private final float[] hsv = new float[3];
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG), glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG), auraPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // The companion: a person's touch, return, day, and rhythm.
    private double engagement, attention, attentionU = .5, attentionV = .5;
    private double gathering, gatherU = .5, gatherV = .78;
    private boolean touching;
    private double affection, liveliness = 1, moodGlow = .8;
    private final double[] pendingU = new double[MAX_PENDING_FLASHES], pendingV = new double[MAX_PENDING_FLASHES];
    private final double[] pendingHue = new double[MAX_PENDING_FLASHES], pendingSize = new double[MAX_PENDING_FLASHES];
    private int pendingCount;
    private final double[] flashX = new double[MAX_FLASHES], flashY = new double[MAX_FLASHES];
    private final double[] flashAge = new double[MAX_FLASHES], flashSize = new double[MAX_FLASHES];
    private final int[] flashBucket = new int[MAX_FLASHES];
    private long lastEventSerial;

    private int rate = 4, requestedCount = 3, activeLineages, pointBudget = 9000;
    private long seed;
    private boolean seeded, varying = true;
    private double waterStrength = .85, motionSpeed = 1, formSpeed = 1, colorSpeed = 1;
    private double elapsedTime, colorPhase, climate = 1, unitsW = 1, unitsH = 2;
    private double simulationCostMs, geometryCostMs, canvasCostMs;
    private int lastLinkCount, lastPointCount;
    private volatile double pageCentered;

    public LatticeRenderer(long seed) {
        world = new LifeField(WORLD_COLUMNS, WORLD_ROWS, seed);
        world.setRate(rate);
        chaos = new CoupledChaos(seed);
        // Each pond starts from a fresh random arrangement; the Life world keeps the saved seed.
        dots = new DotLattice(seed ^ System.nanoTime());
        this.seed = seed;
        PorterDuffXfermode light = new PorterDuffXfermode(PorterDuff.Mode.ADD);
        dotPaint.setStyle(Paint.Style.STROKE); dotPaint.setStrokeCap(Paint.Cap.ROUND); dotPaint.setXfermode(light);
        glowPaint.setStyle(Paint.Style.STROKE); glowPaint.setStrokeCap(Paint.Cap.ROUND); glowPaint.setXfermode(light);
        linePaint.setStyle(Paint.Style.STROKE); linePaint.setStrokeCap(Paint.Cap.BUTT); linePaint.setXfermode(light);
        auraPaint.setXfermode(light);
        for (int b = 0; b < COLOR_BUCKETS; b++) {
            hsv[0] = b * (360f / COLOR_BUCKETS); hsv[1] = .88f; hsv[2] = 1;
            int rgb = Color.HSVToColor(hsv);
            int r = Color.red(rgb), g = Color.green(rgb), bl = Color.blue(rgb);
            auraShaders[b] = new RadialGradient(0, 0, 1,
                new int[] {Color.argb(34, r, g, bl), Color.argb(10, r, g, bl), Color.argb(0, r, g, bl)},
                new float[] {0, .42f, 1}, Shader.TileMode.CLAMP);
        }
        updatePalette();
    }

    // ------------------------------------------------------------------ settings

    @Override public void configure(int newRate, double strength, int requestedPoints,
                                    boolean visibleField, boolean autoReseed) {
        rate = newRate == 1 ? 1 : 4;
        waterStrength = bounded(strength, 0, 1);
        world.setRate(rate);
        world.setAutoReseed(autoReseed);
    }
    @Override public void setAnimationSpeeds(double motion, double form, double color) {
        motionSpeed = bounded(motion, .02, 2.0);
        formSpeed = bounded(form, .02, 2.0);
        colorSpeed = bounded(color, .02, 2.0);
        // Shape evolution sets how quickly lattices rebuild and rhythms wander.
        dots.setRewire(formSpeed / .12);
    }
    @Override public void configurePopulation(int count, boolean vary, boolean collisions, boolean merging) {
        requestedCount = Math.max(1, Math.min(LINEAGES, count));
        varying = vary;
        dots.setCollisions(collisions);
        dots.setMerging(merging);
    }
    @Override public void setTouchRipplesEnabled(boolean enabled) {
        ripplesEnabled = enabled;
        if (!enabled) ripples.clear();
    }
    @Override public void setPageOffset(double xOffset, double xOffsetStep, double xPixelOffset, double surfaceWidth) {
        pageCentered = PageDrift.centeredOffset(xOffset, xOffsetStep, xPixelOffset, surfaceWidth);
    }
    @Override public void touch(double u, double v) { if (ripplesEnabled) ripples.touch(u, v); }
    /** New pond: a new Life world and a fresh random arrangement of dots. */
    @Override public void reseed(long seed) {
        this.seed = seed;
        world.reseed(seed); chaos.reseed(seed);
        seeded = false;
    }
    @Override public void setPointBudget(int requested) { pointBudget = Math.max(1, requested); }
    @Override public int getPointCount() { return dots.getCount(); }

    // ------------------------------------------------------------------ companion

    @Override public void companionTouch(double u, double v, int kind) {
        if (!finite(u) || !finite(v)) return;
        attentionU = bounded(u, 0, 1); attentionV = bounded(v, 0, 1);
        if (kind == TOUCH_DOWN) { touching = true; attention = Math.max(attention, .45); engagement = 1; }
        else if (kind == TOUCH_MOVE) { if (touching) attention = Math.max(attention, .45); }
        else if (kind == TOUCH_TAP) { touching = false; affection = Companion.pet(affection); attention = 1; engagement = 1; }
        else touching = false;
    }
    @Override public void greet(double strength) {
        double s = bounded(strength, 0, 1);
        if (s <= 0) return;
        gathering = Math.max(gathering, s); engagement = Math.max(engagement, s);
        queueFlash(gatherU, gatherV, Double.NaN, .6 + .6 * s);
    }
    @Override public void setMood(double value) { liveliness = bounded(value, .35, 1.2); }
    @Override public double getLiveliness() { return liveliness; }
    @Override public double getAffection() { return affection; }
    @Override public void setAffection(double value) { affection = bounded(value, 0, 1); }
    public double getEngagement() { return engagement; }

    /**
     * The colours of apps a person spent time in arrive at the edges of the
     * water, and each visible body leans part of the way toward one of them;
     * the dots' own colour memory relaxes back over minutes.
     */
    @Override public void tellDay(double[] hues, double[] weights, int count) {
        if (hues == null || weights == null) return;
        int n = Math.min(count, Math.min(hues.length, Math.min(weights.length, 4)));
        for (int k = 0; k < n; k++) {
            if (!finite(hues[k])) continue;
            double weight = bounded(weights[k], .2, 1);
            double u = (k & 1) == 0 ? .05 : .95;
            double v = .16 + .66 * (wrapDegrees(hues[k] * 2.3 + k * 97) / 360);
            world.seedDisc(u, v, 3.5 + 3 * weight, .55);
            tint.paint(u, v, 7 + 6 * weight, wrapDegrees(hues[k] - 214 - colorPhase), .95 * weight);
            queueFlash(u, v, hues[k], .5 + .5 * weight);
        }
        if (n == 0) return;
        for (int slot = 0; slot < dots.bodyCount; slot++) {
            int k = slot % n;
            if (!finite(hues[k])) continue;
            double target = wrapDegrees(hues[k] - 214 - colorPhase), lean = .55 * bounded(weights[k], .3, 1);
            int id = dots.bodyId[slot];
            for (int i = 0; i < dots.count; i++) {
                if (dots.bodyOf[i] != id) continue;
                dots.hue[i] = wrapDegrees(dots.hue[i] + DotLattice.arc(target - dots.hue[i]) * lean);
            }
        }
        engagement = Math.max(engagement, .4);
    }
    private void queueFlash(double u, double v, double hue, double size) {
        if (pendingCount >= MAX_PENDING_FLASHES) return;
        pendingU[pendingCount] = u; pendingV[pendingCount] = v;
        pendingHue[pendingCount] = hue; pendingSize[pendingCount] = size;
        pendingCount++;
    }

    // ------------------------------------------------------------------ frame

    @Override public void render(Canvas canvas, double dtSeconds) {
        long started = System.nanoTime();
        simulationCostMs = geometryCostMs = canvasCostMs = 0;
        int width = canvas.getWidth(), height = canvas.getHeight();
        if (width <= 0 || height <= 0) return;
        double elapsed = bounded(dtSeconds, 0, 1);
        elapsedTime += elapsed;
        double extent = Math.min(width, height);
        unitsW = width / extent; unitsH = height / extent;
        if (!seeded) {
            dots.seed(requestedCount, DOTS_PER_BODY, unitsW, unitsH);
            activeLineages = (1 << requestedCount) - 1;
            seeded = true;
        } else {
            dots.resize(unitsW, unitsH);
        }
        reconcilePopulation();
        advanceWorld(elapsed);
        // The companion: attention follows a touch, a welcome gathers dots
        // toward the thumb, and engagement briefly quickens a slow pond.
        moodGlow = .8 * (.7 + .3 * liveliness);
        if (!touching) attention *= Math.exp(-elapsed / 4.0);
        gathering *= Math.exp(-elapsed / 8.0);
        if (!touching) engagement *= Math.exp(-elapsed / 6.0);
        dots.setAttention((attentionU - .5) * unitsW, (attentionV - .5) * unitsH,
            attention < .02 ? 0 : attention * (.55 + .45 * affection));
        dots.setGathering((gatherU - .5) * unitsW, (gatherV - .5) * unitsH, gathering < .02 ? 0 : gathering);
        dots.setLiveliness(liveliness);
        dots.setClimate(varying ? climate : 1);
        // A touch or a welcome lifts the pond's own pace a little, never past
        // 1.8x the chosen Drift.
        double pondDelta = elapsed * motionSpeed * PACE * (1 + .8 * engagement);
        pondAccumulator = Math.min(pondAccumulator + pondDelta, 40 * STEP);
        tiltClock += pondDelta * .35; axisClock += pondDelta * .18;
        stepsThisFrame = 0;
        while (pondAccumulator >= STEP) {
            saveState();
            updateWater(STEP / Math.max(1e-6, motionSpeed * PACE));
            dots.step(STEP);
            pondAccumulator -= STEP;
            stepsThisFrame++;
        }
        if (dots.structureVersion != savedVersion || dots.getCount() != savedCount) saveState();
        double blend = pondAccumulator / STEP;
        for (int i = 0, n = dots.getCount(); i < n; i++) {
            drawX[i] = previousX[i] + (dots.x[i] - previousX[i]) * blend;
            drawY[i] = previousY[i] + (dots.y[i] - previousY[i]) * blend;
            drawPhase[i] = previousPhase[i] + (dots.phase[i] - previousPhase[i]) * blend;
        }
        long geometryStarted = System.nanoTime();
        simulationCostMs = (geometryStarted - started) / 1e6;
        updatePalette();
        measureBodies();
        boolean glow = pointBudget >= GLOW_BUDGET;
        int n = dots.getCount();
        Arrays.fill(glowCounts, 0);
        for (int t = 0; t < TIERS; t++) Arrays.fill(tierCounts[t], 0);
        Arrays.fill(lineCounts, 0); Arrays.fill(faintCounts, 0);
        boolean freshAnchors = dots.structureVersion != anchorVersion || n != anchorCount;
        double follow = freshAnchors ? 1 : -Math.expm1(-elapsed / .45);
        anchorVersion = dots.structureVersion; anchorCount = n;
        int drawnDots = 0;
        for (int i = 0; i < n; i++) {
            visibleDot[i] = dots.alpha[i] > .35;
            int id = dots.bodyOf[i], slot = id >= 0 && id < slotOfId.length ? slotOfId[id] : -1;
            boolean inBody = slot >= 0 && auraCount[slot] > 0;
            double targetX = inBody ? auraX[slot] : drawX[i], targetY = inBody ? auraY[slot] : drawY[i];
            double targetR = inBody ? dots.bodyRadius[slot] * 1.7 : .03;
            anchorX[i] += (targetX - anchorX[i]) * follow;
            anchorY[i] += (targetY - anchorY[i]) * follow;
            anchorR[i] += (targetR - anchorR[i]) * follow;
            if (!visibleDot[i]) continue;
            // Three-dimensional illusion. A body is a domed disc tilted toward
            // or away from the viewer about an axis that slowly turns; nearer
            // bodies (depth, as in the Koi forms) are larger, and the near side
            // of each dome is larger and brighter.
            double depth = dots.depth[i], radius = Math.max(.03, anchorR[i]);
            double dx = drawX[i] - anchorX[i], dy = drawY[i] - anchorY[i];
            double z = radius * DOME * Math.sqrt(Math.max(0, 1 - (dx * dx + dy * dy) / (radius * radius)));
            double tilt = TILT * (.55 + .45 * Math.sin(tiltClock + 6.283 * depth));
            double axis = axisClock + 1.7 * dots.spin[i];
            double ca = Math.cos(axis), sa = Math.sin(axis), ct = Math.cos(tilt), st = Math.sin(tilt);
            double u = dx * ca + dy * sa, v = -dx * sa + dy * ca;
            double v2 = v * ct - z * st, z2 = v * st + z * ct;
            double scale = PageDrift.visualScale(depth) / (1 - .22 * z2 / radius);
            double px = (u * ca - v2 * sa) * scale, py = (u * sa + v2 * ca) * scale;
            screenX[i] = (float) (width * .5 + (anchorX[i] + px) * extent + PageDrift.pixelShift(pageCentered, depth, extent));
            screenY[i] = (float) (height * .5 + (anchorY[i] + py) * extent);
            double nearness = depth + .35 * z2 / radius + .08 * (((i * 0.7548776662466927) % 1) - .5);
            int tier = nearness < .62 ? 0 : nearness < .86 ? 1 : 2;
            double c = Math.cos(drawPhase[i]);
            // Colour runs as a gradient across each body, diagonally and from
            // core to rim, so palette drift flows through a body instead of
            // switching it all at once.
            double gradient = 0;
            if (inBody) {
                double reach = Math.max(.02, dots.bodyRadius[slot] * 1.4);
                double gx = (drawX[i] - auraX[slot]) / reach, gy = (drawY[i] - auraY[slot]) / reach;
                gradient = GRADIENT_ARC * .5 * bounded(.82 * gx + .42 * gy, -1, 1)
                    + RADIAL_ARC * (Math.min(1.3, Math.sqrt(gx * gx + gy * gy)) - .55);
            }
            // Ordered dithering: each dot crosses into the next colour step at
            // its own threshold, so changes ripple through instead of jumping.
            double steps = wrapDegrees(214 + dots.hue[i] + colorPhase + 9 * c + gradient) * COLOR_BUCKETS / 360;
            int b = (int) steps;
            if (steps - b > (i * 0.6180339887498949) % 1) b++;
            if (b >= COLOR_BUCKETS) b -= COLOR_BUCKETS;
            dotBucket[i] = b;
            // The bright half of each dot's rhythm draws one class nearer, so a
            // body in step pulses as one and waves of rhythm are visible.
            bright[i] = c > .2;
            dotTier[i] = Math.min(TIERS - 1, tier + (bright[i] ? 1 : 0));
            tierCounts[dotTier[i]][b] += 2;
            glowing[i] = glow && c > .55;
            if (glowing[i]) glowCounts[b] += 2;
            drawnDots++;
        }
        int links = 0;
        for (int e = 0; e < dots.edgeCount; e++) {
            int a = dots.edgeA[e], b = dots.edgeB[e];
            if (!visibleDot[a] || !visibleDot[b]) continue;
            double s = dots.edgeDying[e] ? .5 * dots.edgeStrength[e] : dots.edgeStrength[e];
            if (s < .08) continue;
            if (s >= .5) lineCounts[dotBucket[a]] += 4; else faintCounts[dotBucket[a]] += 4;
            links++;
        }
        int pointOffset = 0, glowOffset = 0, lineOffset = 0, faintOffset = 0;
        for (int t = 0; t < TIERS; t++) {
            for (int b = 0; b < COLOR_BUCKETS; b++) {
                tierOffsets[t][b] = pointOffset; tierCursor[t][b] = pointOffset; pointOffset += tierCounts[t][b];
            }
        }
        for (int b = 0; b < COLOR_BUCKETS; b++) {
            glowOffsets[b] = glowOffset; glowOffset += glowCounts[b];
            lineOffsets[b] = lineOffset; lineOffset += lineCounts[b];
            faintOffsets[b] = faintOffset; faintOffset += faintCounts[b];
        }
        System.arraycopy(glowOffsets, 0, cursorB, 0, COLOR_BUCKETS);
        for (int i = 0; i < n; i++) {
            if (!visibleDot[i]) continue;
            int b = dotBucket[i], t = dotTier[i], p = tierCursor[t][b];
            points[p] = screenX[i]; points[p + 1] = screenY[i]; tierCursor[t][b] += 2;
            if (glowing[i]) { int g = cursorB[b]; glows[g] = screenX[i]; glows[g + 1] = screenY[i]; cursorB[b] += 2; }
        }
        System.arraycopy(lineOffsets, 0, cursorA, 0, COLOR_BUCKETS);
        System.arraycopy(faintOffsets, 0, cursorB, 0, COLOR_BUCKETS);
        for (int e = 0; e < dots.edgeCount; e++) {
            int a = dots.edgeA[e], b = dots.edgeB[e];
            if (!visibleDot[a] || !visibleDot[b]) continue;
            double s = dots.edgeDying[e] ? .5 * dots.edgeStrength[e] : dots.edgeStrength[e];
            if (s < .08) continue;
            float[] target = s >= .5 ? lines : faintLines;
            int[] cursor = s >= .5 ? cursorA : cursorB;
            int bucket = dotBucket[a], p = cursor[bucket];
            target[p] = screenX[a]; target[p + 1] = screenY[a];
            target[p + 2] = screenX[b]; target[p + 3] = screenY[b];
            cursor[bucket] += 4;
        }
        lastPointCount = drawnDots; lastLinkCount = links;
        pollEvents(width, height, extent);
        releasePendingFlashes(width, height, extent);
        long canvasStarted = System.nanoTime();
        geometryCostMs = (canvasStarted - geometryStarted) / 1e6;

        canvas.drawColor(BACKGROUND);
        if (pointBudget >= AURA_BUDGET) drawAuras(canvas, width, height, extent);
        float stroke = (float) Math.max(1.0, extent / 480 * 1.25);
        if (glow) {
            glowPaint.setStrokeWidth(stroke * 4.6f);
            int alpha = (int) bounded(34 * moodGlow / .8, 10, 52);
            for (int b = 0; b < COLOR_BUCKETS; b++) {
                if (glowCounts[b] == 0) continue;
                glowPaint.setColor(colors[b]); glowPaint.setAlpha(alpha);
                canvas.drawPoints(glows, glowOffsets[b], glowCounts[b], glowPaint);
            }
        }
        // Bonds are hairlines, a third of a dot's width, and always end at dots.
        linePaint.setStrokeWidth(Math.max(1f, stroke * .3f));
        for (int b = 0; b < COLOR_BUCKETS; b++) {
            if (faintCounts[b] > 0) {
                linePaint.setColor(colors[b]); linePaint.setAlpha(42);
                canvas.drawLines(faintLines, faintOffsets[b], faintCounts[b], linePaint);
            }
            if (lineCounts[b] > 0) {
                linePaint.setColor(colors[b]); linePaint.setAlpha(100);
                canvas.drawLines(lines, lineOffsets[b], lineCounts[b], linePaint);
            }
        }
        // Far classes first, so nearer dots sit on top.
        for (int t = 0; t < TIERS; t++) {
            dotPaint.setStrokeWidth(stroke * TIER_WIDTH[t]);
            for (int b = 0; b < COLOR_BUCKETS; b++) {
                if (tierCounts[t][b] == 0) continue;
                dotPaint.setColor(colors[b]); dotPaint.setAlpha(TIER_ALPHA[t]);
                canvas.drawPoints(points, tierOffsets[t][b], tierCounts[t][b], dotPaint);
            }
        }
        drawFlashes(canvas, elapsed);
        canvasCostMs = (System.nanoTime() - canvasStarted) / 1e6;
    }

    /** Remembers the state before a physics step, for drawing in between. */
    private void saveState() {
        int n = dots.getCount();
        System.arraycopy(dots.x, 0, previousX, 0, n);
        System.arraycopy(dots.y, 0, previousY, 0, n);
        System.arraycopy(dots.phase, 0, previousPhase, 0, n);
        savedVersion = dots.structureVersion;
        savedCount = n;
    }

    /** Adds lineages up to the chosen count in the emptiest water, or lets the newest fade away. */
    private void reconcilePopulation() {
        int active = Integer.bitCount(activeLineages);
        while (active < requestedCount) {
            int lineage = Integer.numberOfTrailingZeros(~activeLineages);
            if (lineage >= LINEAGES) break;
            double bestX = 0, bestY = 0, best = -1;
            for (int k = 0; k < 16; k++) {
                double cx = (((k * 7 + 3) % 16) / 16.0 - .5 + .03) * unitsW * .8;
                double cy = (((k * 11 + 5) % 16) / 16.0 - .5 + .03) * unitsH * .8;
                double nearest = 1e9;
                for (int slot = 0; slot < dots.bodyCount; slot++) {
                    nearest = Math.min(nearest, Math.hypot(cx - dots.bodyX[slot], cy - dots.bodyY[slot]));
                }
                if (nearest > best) { best = nearest; bestX = cx; bestY = cy; }
            }
            dots.addBody(lineage, bestX, bestY, DOTS_PER_BODY, 0);
            activeLineages |= 1 << lineage;
            active++;
        }
        while (active > requestedCount) {
            int lineage = 31 - Integer.numberOfLeadingZeros(activeLineages);
            dots.releaseLineage(lineage);
            activeLineages &= ~(1 << lineage);
            active--;
        }
    }

    private void advanceWorld(double elapsed) {
        double chaosElapsed = elapsed * motionSpeed;
        double worldDensity = world.getPopulation() / (double) WORLD_CELLS;
        while (chaosElapsed > 0) {
            double step = Math.min(1, chaosElapsed);
            chaos.advance(step, world.getActivity(), worldDensity);
            chaosElapsed -= step;
        }
        long before = world.getGeneration();
        int reseeds = world.getReseedCount();
        world.advance(elapsed);
        long ticks = world.getReseedCount() != reseeds ? Math.max(1, world.getGeneration())
            : Math.max(0, world.getGeneration() - before);
        chaos.sample(0, chaosSample);
        // Crowding climate: with Vary count, the long-range push breathes with the chaos ring.
        climate = .75 + .35 * chaosSample[0];
        colorPhase += elapsed * colorSpeed * 10 * chaosSample[3];
        if (ticks > 0 || !waterReady) {
            for (int gy = 0; gy < GRID_Y; gy++) {
                for (int gx = 0; gx < GRID_X; gx++) {
                    world.sampleLocal(gx / (double) (GRID_X - 1), gy / (double) (GRID_Y - 1), LOCAL_RADIUS, local);
                    turbulence[gy * GRID_X + gx] = bounded(local[1] * 4.5, 0, 1);
                }
            }
        }
        if (ticks > 0) {
            double generationSeconds = ticks / (double) rate;
            double bandSum = 0;
            for (int band = 0; band < 8; band++) bandSum += world.getBandEnergy(band);
            double activity = bounded(world.getActivity() * 4.5, 0, 1), energy = bounded(bandSum * 2.8, 0, 1);
            // Life generations carry the palette onward, faster when the world is busy.
            colorPhase += generationSeconds * colorSpeed * (14 + 35 * activity + 18 * energy + 35 * worldDensity);
            tendTheWorld(generationSeconds);
        }
        colorPhase = wrapDegrees(colorPhase);
    }

    /** Once per generation: colour spreads; bodies seed life and paint their hue; dots absorb the water's colour. */
    private void tendTheWorld(double generationSeconds) {
        tint.spread(world, generationSeconds, TINT_FADE_SECONDS);
        double chance = rate == 1 ? .30 : .12;
        for (int slot = 0; slot < dots.bodyCount; slot++) {
            double u = .5 + dots.bodyX[slot] / unitsW, v = .5 + dots.bodyY[slot] / unitsH;
            double size = Math.min(2, dots.bodySize[slot] / (double) DOTS_PER_BODY);
            double radiusCells = dots.bodyRadius[slot] / unitsW * WORLD_COLUMNS;
            world.seedDisc(u, v, 1.3 + .9 * size, chance * (.6 + .4 * size));
            tint.paint(u, v, Math.max(2, radiusCells * .75), dots.bodyHue[slot], .22 * Math.min(1, generationSeconds * 2));
        }
        double absorb = -Math.expm1(-generationSeconds * motionSpeed / TINT_ABSORB_SECONDS);
        for (int i = 0; i < dots.count; i++) {
            tint.sample(.5 + dots.x[i] / unitsW, .5 + dots.y[i] / unitsH, tintSample);
            if (tintSample[1] <= .05) continue;
            dots.hue[i] = wrapDegrees(dots.hue[i] + DotLattice.arc(tintSample[0] - dots.hue[i]) * tintSample[1] * absorb);
        }
    }

    private void updateWater(double elapsed) {
        if (elapsed > 0 || !waterReady) {
            world.sampleGrid(GRID_X, GRID_Y, fieldX, fieldY, projection);
            double follow = waterReady ? -Math.expm1(-elapsed * formSpeed / .4) : 1;
            for (int i = 0; i < GRID_SIZE; i++) {
                waterX[i] += (bounded(fieldX[i], -1, 1) - waterX[i]) * follow;
                waterY[i] += (bounded(fieldY[i], -1, 1) - waterY[i]) * follow;
            }
            waterReady = true;
        }
        ripples.advance(elapsed);
        boolean rippling = ripplesEnabled && ripples.getActiveCount() > 0;
        for (int gy = 0; gy < GRID_Y; gy++) {
            for (int gx = 0; gx < GRID_X; gx++) {
                int i = gy * GRID_X + gx;
                double rx = 0, ry = 0;
                if (rippling) {
                    ripples.sample(gx / (double) (GRID_X - 1), gy / (double) (GRID_Y - 1), unitsW, unitsH, rippleSample);
                    rx = rippleSample[0] * 3; ry = rippleSample[1] * 3;
                }
                flowX[i] = bounded(waterX[i] + rx, -1.5, 1.5);
                flowY[i] = bounded(waterY[i] + ry, -1.5, 1.5);
            }
        }
        dots.setWater(flowX, flowY, turbulence, GRID_X, GRID_Y, waterStrength);
    }

    /** Each body's centre, from its drawn (interpolated) dots. */
    private void measureBodies() {
        java.util.Arrays.fill(slotOfId, -1);
        for (int slot = 0; slot < dots.bodyCount; slot++) {
            if (dots.bodyId[slot] >= 0 && dots.bodyId[slot] < slotOfId.length) slotOfId[dots.bodyId[slot]] = slot;
            auraX[slot] = auraY[slot] = 0; auraCount[slot] = 0;
        }
        for (int i = 0, n = dots.getCount(); i < n; i++) {
            int id = dots.bodyOf[i];
            if (id < 0 || id >= slotOfId.length || slotOfId[id] < 0) continue;
            int slot = slotOfId[id];
            auraX[slot] += drawX[i]; auraY[slot] += drawY[i]; auraCount[slot]++;
        }
        for (int slot = 0; slot < dots.bodyCount; slot++) {
            if (auraCount[slot] > 0) { auraX[slot] /= auraCount[slot]; auraY[slot] /= auraCount[slot]; }
        }
    }

    private void drawAuras(Canvas canvas, int width, int height, double extent) {
        for (int slot = 0; slot < dots.bodyCount; slot++) {
            double size = dots.bodySize[slot] / (double) DOTS_PER_BODY;
            double depth = dots.bodyDepth[slot];
            float radius = (float) (extent * dots.bodyRadius[slot] * 2.1 * PageDrift.visualScale(depth));
            if (radius < 2 || auraCount[slot] == 0) continue;
            float cx = (float) (width * .5 + auraX[slot] * extent
                + PageDrift.pixelShift(pageCentered, dots.bodyDepth[slot], extent));
            float cy = (float) (height * .5 + auraY[slot] * extent);
            int save = canvas.save();
            canvas.translate(cx, cy);
            canvas.scale(radius, radius);
            auraPaint.setShader(auraShaders[hueBucket(214 + dots.bodyHue[slot] + colorPhase)]);
            auraPaint.setAlpha((int) bounded(85 * moodGlow * Math.min(1, size) * (.45 + .55 * depth), 0, 130));
            canvas.drawCircle(0, 0, 1, auraPaint);
            canvas.restoreToCount(save);
        }
    }

    private void pollEvents(int width, int height, double extent) {
        long serial = dots.getEventSerial();
        for (long s = Math.max(lastEventSerial + 1, serial - 15); s <= serial; s++) {
            if (dots.readEvent(s, eventOut) == 0) continue;
            addFlash(width * .5 + eventOut[0] * extent, height * .5 + eventOut[1] * extent,
                extent * .16, hueBucket(214 + eventOut[2] + colorPhase));
        }
        lastEventSerial = serial;
    }
    private void releasePendingFlashes(int width, int height, double extent) {
        for (int k = 0; k < pendingCount; k++) {
            double hue = finite(pendingHue[k]) ? pendingHue[k]
                : dots.bodyCount > 0 ? 214 + dots.bodyHue[0] + colorPhase : 200;
            addFlash(pendingU[k] * width, pendingV[k] * height, extent * .22 * pendingSize[k], hueBucket(hue));
        }
        pendingCount = 0;
    }
    private void addFlash(double x, double y, double size, int bucket) {
        int slot = -1;
        for (int f = 0; f < MAX_FLASHES && slot < 0; f++) if (flashSize[f] <= 0 || flashAge[f] >= FLASH_SECONDS) slot = f;
        if (slot < 0) {
            slot = 0;
            for (int f = 1; f < MAX_FLASHES; f++) if (flashAge[f] > flashAge[slot]) slot = f;
        }
        flashX[slot] = x; flashY[slot] = y; flashAge[slot] = 0; flashSize[slot] = size; flashBucket[slot] = bucket;
    }
    /** A fusion, division or welcome brightens the water around it; no ring is drawn. */
    private void drawFlashes(Canvas canvas, double elapsed) {
        for (int f = 0; f < MAX_FLASHES; f++) {
            if (flashSize[f] <= 0 || flashAge[f] >= FLASH_SECONDS) continue;
            double t = flashAge[f] / FLASH_SECONDS, fade = (1 - t) * (1 - t);
            int save = canvas.save();
            canvas.translate((float) flashX[f], (float) flashY[f]);
            float glow = (float) (flashSize[f] * (1.1 + .8 * t));
            canvas.scale(glow, glow);
            auraPaint.setShader(auraShaders[flashBucket[f]]);
            auraPaint.setAlpha((int) bounded(150 * fade * moodGlow, 0, 255));
            canvas.drawCircle(0, 0, 1, auraPaint);
            canvas.restoreToCount(save);
            flashAge[f] += elapsed;
        }
    }

    private void updatePalette() {
        for (int b = 0; b < COLOR_BUCKETS; b++) {
            hsv[0] = b * (360f / COLOR_BUCKETS); hsv[1] = .86f; hsv[2] = 1;
            colors[b] = Color.HSVToColor(hsv);
        }
    }

    // ------------------------------------------------------------------ reports

    @Override public double getSimulationCostMs() { return simulationCostMs; }
    @Override public double getGeometryCostMs() { return geometryCostMs; }
    @Override public double getCanvasCostMs() { return canvasCostMs; }
    @Override public int getLastLinkCount() { return lastLinkCount; }
    @Override public int getLastPointCount() { return lastPointCount; }
    @Override public int getMergeCount() { return dots.getMergeCount(); }
    @Override public int getDivideCount() { return dots.getDivideCount(); }
    /** Bodies holding well over one lineage's worth of dots. */
    @Override public int getMergedBodyCount() {
        int merged = 0;
        for (int slot = 0; slot < dots.bodyCount; slot++) if (dots.bodySize[slot] >= 1.6 * DOTS_PER_BODY) merged++;
        return merged;
    }
    @Override public long getGeneration() { return world.getGeneration(); }
    public LifeField getLifeField() { return world; }
    DotLattice getLattice() { return dots; }
    public double getColorPhaseDegrees() { return colorPhase; }

    private static int hueBucket(double hue) {
        return Math.min(COLOR_BUCKETS - 1, (int) (wrapDegrees(hue) * COLOR_BUCKETS / 360));
    }
    private static double wrapDegrees(double degrees) {
        if (!finite(degrees)) return 0;
        double wrapped = degrees % 360;
        if (wrapped < 0) wrapped += 360;
        return wrapped >= 360 ? 0 : wrapped;
    }
    private static double bounded(double value, double low, double high) {
        return finite(value) ? Math.max(low, Math.min(high, value)) : low;
    }
    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
}
