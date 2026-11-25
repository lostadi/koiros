/*
 * Koiros: living forms in one shared Game of Life world. They fuse into a
 * single body and divide again, and the water carries their colour.
 * Original point equations: Studio Coldstream (mathewmolt), ArtificialKoi,
 * https://codeberg.org/mathewmolt/ArtificialKoi, commit
 * 8834094e2357e5482e1495b80516f12360ff72dc. Upstream credits @yuruyurau.
 * This adaptation is GPL-3.0-or-later. See LICENSE and CREDITS.
 */
package org.ostadix.koilife;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import java.util.Arrays;

/**
 * One Life world drives every body. Each body senses the world where it
 * swims, seeds new life in its wake, and paints its hue into the water.
 * Bonded bodies converge in rotation, rhythm, form, depth and colour until
 * they render as one larger body; after dividing, each keeps part of what it
 * shared. No allocations occur per frame.
 */
public final class KoiRenderer {
    private static final int MAX_CREATURES = 9, COLOR_BUCKETS = 48, MAX_POINTS = 14000;
    private static final int MAX_LINKS = 900;
    private static final int GRID_X = 13, GRID_Y = 19, GRID_SIZE = GRID_X * GRID_Y;
    private static final int HISTORY = 2, HISTORY_CAPACITY = (MAX_POINTS / 10 + COLOR_BUCKETS) * 2;
    private static final double HISTORY_INTERVAL = .12, HISTORY_LIFETIME = .26;
    private static final int BACKGROUND = Color.rgb(3, 7, 14);
    private static final int[] TOUR_STRIDES = {1, 3, 7, 9};
    /** One shared world, close to a modern phone's 9:20 portrait shape. */
    private static final int WORLD_COLUMNS = 54, WORLD_ROWS = 120, WORLD_CELLS = WORLD_COLUMNS * WORLD_ROWS;
    private static final int LOCAL_RADIUS = 5;
    public static final int TOUCH_DOWN = 0, TOUCH_MOVE = 1, TOUCH_UP = 2, TOUCH_TAP = 3;
    private static final int MAX_PENDING_FLASHES = 5;
    private static final int SPARK_STRIDE = 5;
    private static final int SPARK_CAPACITY = (MAX_POINTS / SPARK_STRIDE + COLOR_BUCKETS) * 2;
    private static final int MAX_FLASHES = 6, THREADS = 5;
    private static final double FLASH_SECONDS = 3.4, TINT_FADE_SECONDS = 26;
    private static final double HUE_RELAX_SECONDS = 45, DEPTH_RELAX_SECONDS = 20, TINT_ABSORB_SECONDS = 30;

    private final LifeField world;
    private final WorldTint tint = new WorldTint(WORLD_COLUMNS, WORLD_ROWS);
    private final CoupledChaos chaos;
    private final PondDynamics pond;
    private final TouchRipples touchRipples = new TouchRipples();
    private boolean touchRipplesEnabled = true;
    private double rippleDisplacementMagnitude;
    private final double[] encounterHue = new double[MAX_CREATURES];
    private final int[] activeCreatures = new int[MAX_CREATURES];
    private final double[] opacity = new double[MAX_CREATURES];
    private final int[] pointStarts = new int[MAX_CREATURES];
    private final double[] bodyFlowDistance = new double[MAX_CREATURES];
    private int activeCreatureCount;
    private final double[][] chaosState = new double[MAX_CREATURES][4];
    private final double[][] drive = new double[MAX_CREATURES][7];
    private final double[][] systemSample = new double[CoupledChaos.SYSTEM_COUNT][4];
    // Identity that merging shares and dividing keeps.
    private final double[] hueIdentity = new double[MAX_CREATURES];
    private final double[] groupPhase = new double[MAX_CREATURES];
    private final double[] depthVisual = new double[MAX_CREATURES];
    private final double[] massScale = new double[MAX_CREATURES];
    private final double[] localSample = new double[2], tintSample = new double[2];
    private final double[] eventOut = new double[4];
    private final int[] bodyColor = new int[MAX_CREATURES];
    private final double[] bodyHue = new double[MAX_CREATURES];
    private final double[] bodyRadius = new double[MAX_CREATURES];
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sparkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bridgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint auraPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint[] waterGlow = new Paint[2];
    private final Shader[] auraShaders = new Shader[COLOR_BUCKETS];
    private final Path flowPath = new Path(), bridgePath = new Path();
    // Packed buffers keep palette resolution independent of memory/point budget.
    private final float[] points = new float[MAX_POINTS * 2];
    private final int[] counts = new int[COLOR_BUCKETS], offsets = new int[COLOR_BUCKETS];
    private final int[] cursors = new int[COLOR_BUCKETS];
    private final float[] sparks = new float[SPARK_CAPACITY];
    private final int[] sparkCounts = new int[COLOR_BUCKETS], sparkOffsets = new int[COLOR_BUCKETS];
    private final double[] rawX = new double[MAX_POINTS], rawY = new double[MAX_POINTS];
    private final float[] screenX = new float[MAX_POINTS], screenY = new float[MAX_POINTS];
    private final int[] rawCreature = new int[MAX_POINTS], rawSource = new int[MAX_POINTS];
    private final int[] rawBucket = new int[MAX_POINTS];
    private final int[] sourceLookup = new int[MAX_CREATURES * MAX_POINTS];
    private final double[] sumX = new double[MAX_CREATURES], sumY = new double[MAX_CREATURES];
    private final double[] measuredX = new double[MAX_CREATURES], measuredY = new double[MAX_CREATURES];
    private final int[] measuredCount = new int[MAX_CREATURES], radialCount = new int[MAX_CREATURES];
    private final int[][] radialHistogram = new int[MAX_CREATURES][96];
    private final double[] formCenterX = new double[MAX_CREATURES], formCenterY = new double[MAX_CREATURES];
    private final double[] formRadius = new double[MAX_CREATURES];
    private final boolean[] formNormalized = new boolean[MAX_CREATURES];
    private final float[] lines = new float[MAX_LINKS * 4];
    private final int[] lineFrom = new int[MAX_LINKS], lineTo = new int[MAX_LINKS];
    private final int[] lineBucket = new int[MAX_LINKS];
    private final int[] lineCounts = new int[COLOR_BUCKETS], lineOffsets = new int[COLOR_BUCKETS];
    private final int[] lineCursors = new int[COLOR_BUCKETS];
    private final int[][] palettePopulation = new int[MAX_CREATURES][COLOR_BUCKETS];
    // One shared spectral field: every body samples the same water.
    private final double[] fieldX = new double[GRID_SIZE], fieldY = new double[GRID_SIZE];
    private final double[] projection = new double[GRID_SIZE];
    private final double[] mixedFieldX = new double[GRID_SIZE], mixedFieldY = new double[GRID_SIZE];
    private final double[] mixedProjection = new double[GRID_SIZE];
    // Ripples never enter the retained Life low-pass state: expiry restores the
    // untouched field exactly, without a persistent deformation or extra lag.
    private final double[] rippleMixedX = new double[GRID_SIZE], rippleMixedY = new double[GRID_SIZE];
    private final double[] rippleMixedProjection = new double[GRID_SIZE];
    private double[] sampledFieldX = mixedFieldX, sampledFieldY = mixedFieldY;
    private double[] sampledProjection = mixedProjection;
    private final double[] rippleSample = new double[3];
    private boolean mixedFieldsInitialized;
    private final double[] sample = new double[3], shapeA = new double[2], shapeB = new double[2];
    private final double[] phase = new double[MAX_CREATURES];
    private final double[] activity = new double[MAX_CREATURES], energy = new double[MAX_CREATURES];
    private final double[] density = new double[MAX_CREATURES], colorBalance = new double[MAX_CREATURES];
    private final double[] hueTarget = new double[MAX_CREATURES], huePhase = new double[MAX_CREATURES];
    private final double[] formTarget = new double[MAX_CREATURES], formProgress = new double[MAX_CREATURES];
    private final int[] currentForm = new int[MAX_CREATURES], nextForm = new int[MAX_CREATURES];
    private final int[] formStride = new int[MAX_CREATURES], tourSteps = new int[MAX_CREATURES];
    private final int[] visitedForms = new int[MAX_CREATURES];
    private final double[] frequency = new double[MAX_CREATURES], amplitude = new double[MAX_CREATURES];
    private final double[] centerX = new double[MAX_CREATURES], centerY = new double[MAX_CREATURES];
    private final double[] scales = new double[MAX_CREATURES];
    private final int[] renderForm = new int[MAX_CREATURES], renderNext = new int[MAX_CREATURES];
    private final double[] renderBlend = new double[MAX_CREATURES];
    private final double[] renderTime = new double[MAX_CREATURES], renderNextTime = new double[MAX_CREATURES];
    private final int[] colors = new int[COLOR_BUCKETS];
    private final float[] hsv = new float[3];
    private final float[][] history = new float[HISTORY][HISTORY_CAPACITY];
    private final int[][] historyCounts = new int[HISTORY][COLOR_BUCKETS];
    private final int[][] historyOffsets = new int[HISTORY][COLOR_BUCKETS];
    private final int[][] historyColors = new int[HISTORY][COLOR_BUCKETS];
    private final double[] historyTimes = new double[HISTORY];
    private final boolean[] historyValid = new boolean[HISTORY];
    // The companion: a person's touch, return, day, and rhythm.
    private double engagement, attention, attentionU = .5, attentionV = .5;
    private double gathering, gatherU = .5, gatherV = .78;
    private boolean touching;
    private double affection, liveliness = 1, moodGlow = .8;
    private final double[] pendingU = new double[MAX_PENDING_FLASHES], pendingV = new double[MAX_PENDING_FLASHES];
    private final double[] pendingHue = new double[MAX_PENDING_FLASHES], pendingSize = new double[MAX_PENDING_FLASHES];
    private int pendingCount;
    // Fuse and divide flashes.
    private final double[] flashX = new double[MAX_FLASHES], flashY = new double[MAX_FLASHES];
    private final double[] flashAge = new double[MAX_FLASHES], flashSize = new double[MAX_FLASHES];
    private final int[] flashColor = new int[MAX_FLASHES], flashBucket = new int[MAX_FLASHES];
    private final boolean[] flashMerge = new boolean[MAX_FLASHES];
    private long lastEventSerial;
    private int historyCursor, lastWidth, lastHeight, rate = 4;
    private double unitsU = 1.0, unitsV = 0.45;
    private double elapsedTime, nextHistory, colorPhase, meanActivity, meanEnergy;
    private double coupling = .65;
    private double motionSpeed = 1, formSpeed = 1, colorSpeed = 1;
    private double visualCoreScale = 1, visualHueArc = 110, visualGlow = .8;
    private int pointCount = MAX_POINTS;
    private boolean showField;
    private double simulationCostMs, geometryCostMs, canvasCostMs;
    private int lastLinkCount, lastPointCount;
    /** Centered home-page offset. Zero keeps the pond still on a single page. */
    private volatile double pageCentered;

    public KoiRenderer(long seed) {
        chaos = new CoupledChaos(seed);
        pond = new PondDynamics(seed);
        pond.configure(3, true, true, true);
        world = new LifeField(WORLD_COLUMNS, WORLD_ROWS, seed);
        world.setRate(rate);
        PorterDuffXfermode light = new PorterDuffXfermode(PorterDuff.Mode.ADD);
        for (int c = 0; c < MAX_CREATURES; c++) {
            density[c] = world.getPopulation() / (double) WORLD_CELLS;
            currentForm[c] = (c * 3) % KoiForms.COUNT;
            formStride[c] = chooseStride(c);
            nextForm[c] = (currentForm[c] + formStride[c]) % KoiForms.COUNT;
            visitedForms[c] = 1 << currentForm[c];
            hueIdentity[c] = c * 32;
            groupPhase[c] = c;
            depthVisual[c] = pond.getDepth(c);
            massScale[c] = 1;
        }
        for (int b = 0; b < COLOR_BUCKETS; b++) {
            hsv[0] = b * (360f / COLOR_BUCKETS); hsv[1] = .88f; hsv[2] = 1;
            int rgb = Color.HSVToColor(hsv);
            int r = Color.red(rgb), g = Color.green(rgb), bl = Color.blue(rgb);
            auraShaders[b] = new RadialGradient(0, 0, 1,
                new int[] {Color.argb(34, r, g, bl), Color.argb(10, r, g, bl), Color.argb(0, r, g, bl)},
                new float[] {0, .42f, 1}, Shader.TileMode.CLAMP);
        }
        pointPaint.setStyle(Paint.Style.STROKE); pointPaint.setStrokeCap(Paint.Cap.ROUND);
        pointPaint.setXfermode(light);
        sparkPaint.setStyle(Paint.Style.STROKE); sparkPaint.setStrokeCap(Paint.Cap.ROUND);
        sparkPaint.setXfermode(light);
        trailPaint.setStyle(Paint.Style.STROKE); trailPaint.setStrokeCap(Paint.Cap.ROUND);
        trailPaint.setXfermode(light);
        // Independent tiny segments do not need round-cap stroke tessellation.
        linePaint.setStyle(Paint.Style.STROKE); linePaint.setStrokeCap(Paint.Cap.BUTT);
        linePaint.setXfermode(light);
        bridgePaint.setStyle(Paint.Style.STROKE); bridgePaint.setStrokeCap(Paint.Cap.ROUND);
        bridgePaint.setXfermode(light);
        flashPaint.setStyle(Paint.Style.STROKE); flashPaint.setXfermode(light);
        auraPaint.setXfermode(light);
        flowPaint.setStyle(Paint.Style.STROKE); flowPaint.setStrokeCap(Paint.Cap.ROUND);
        flowPaint.setStrokeJoin(Paint.Join.ROUND);
        ripplePaint.setStyle(Paint.Style.STROKE);
        ripplePaint.setStrokeCap(Paint.Cap.BUTT);
        ripplePaint.setColor(Color.rgb(110, 210, 255));
        // Fixed deep-water colors. Rebuilding a palette gradient every frame
        // would allocate, and creature hues already carry the neon.
        int[][] glows = {{24, 18, 52, 82}, {18, 26, 66, 60}};
        for (int i = 0; i < waterGlow.length; i++) {
            waterGlow[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
            int red = glows[i][1], green = glows[i][2], blue = glows[i][3];
            waterGlow[i].setShader(new RadialGradient(0, 0, 1,
                new int[] {Color.argb(glows[i][0], red, green, blue), Color.argb(0, red, green, blue)},
                new float[] {0f, 1f}, Shader.TileMode.CLAMP));
        }
        updatePalette();
    }

    public void configure(int newRate, double strength, int requestedPoints,
                          boolean visibleField, boolean autoReseed) {
        rate = newRate == 1 ? 1 : 4;
        coupling = bounded(strength, 0, 2);
        pointCount = Math.max(2000, Math.min(MAX_POINTS, requestedPoints));
        showField = visibleField;
        world.setRate(rate);
        world.setAutoReseed(autoReseed);
    }
    /** Independent appearance clocks; Life always keeps its selected 1/4 Hz rate. */
    public void setAnimationSpeeds(double motion, double form, double color) {
        motionSpeed = bounded(motion, .02, 2.0);
        formSpeed = bounded(form, .02, 2.0);
        colorSpeed = bounded(color, .02, 2.0);
    }
    public void configurePopulation(int count, boolean varying, boolean collisions) {
        configurePopulation(count, varying, collisions, pond.isMerging());
    }
    public void configurePopulation(int count, boolean varying, boolean collisions, boolean merging) {
        pond.configure(Math.max(1, Math.min(MAX_CREATURES, count)), varying, collisions, merging);
    }
    /**
     * A touch in normalized screen coordinates. Holding draws nearby bodies
     * to circle the finger; a tap brings them to look and adds affection.
     */
    public void companionTouch(double u, double v, int kind) {
        if (!finite(u) || !finite(v)) return;
        attentionU = bounded(u, 0, 1); attentionV = bounded(v, 0, 1);
        if (kind == TOUCH_DOWN) { touching = true; attention = Math.max(attention, .45); engagement = 1; }
        else if (kind == TOUCH_MOVE) { if (touching) attention = Math.max(attention, .45); }
        else if (kind == TOUCH_TAP) {
            touching = false; affection = Companion.pet(affection); attention = 1; engagement = 1;
        } else touching = false;
    }
    /** A welcome back: bodies gather toward the lower middle with a soft glow. */
    public void greet(double strength) {
        double s = bounded(strength, 0, 1);
        if (s <= 0) return;
        gathering = Math.max(gathering, s); engagement = Math.max(engagement, s);
        queueFlash(gatherU, gatherV, Double.NaN, .6 + .6 * s);
    }
    /** 0.35 sleepy to 1.2 lively; see {@link Companion#liveliness}. */
    public void setMood(double value) { liveliness = bounded(value, .35, 1.2); }
    public double getLiveliness() { return liveliness; }
    public double getAffection() { return affection; }
    public void setAffection(double value) { affection = bounded(value, 0, 1); }
    public double getEngagement() { return engagement; }
    /**
     * The colours of apps a person spent time in arrive at the edges of the
     * water as glowing life that the forms can swim through and take on.
     * Hues are on-screen degrees; weights in [0, 1].
     */
    public void tellDay(double[] hues, double[] weights, int count) {
        if (hues == null || weights == null) return;
        int n = Math.min(count, Math.min(hues.length, Math.min(weights.length, 4)));
        for (int k = 0; k < n; k++) {
            double hue = hues[k];
            if (!finite(hue)) continue;
            double weight = bounded(weights[k], .2, 1);
            double u = (k & 1) == 0 ? .05 : .95;
            double v = .16 + .66 * (wrapDegrees(hue * 2.3 + k * 97) / 360);
            world.seedDisc(u, v, 3.5 + 3 * weight, .55);
            tint.paint(u, v, 7 + 6 * weight, wrapDegrees(hue - 214 - colorPhase), .95 * weight);
            queueFlash(u, v, hue, .5 + .5 * weight);
        }
        // The forms take on the day directly: each visible form leans part of
        // the way toward one of the day's colours, then relaxes back toward its
        // own over minutes. Colour in the unseen water alone would fade first.
        int visible = 0;
        for (int c = 0; c < MAX_CREATURES && n > 0; c++) {
            if (pond.getOpacity(c) < .05) continue;
            int k = visible++ % n;
            if (!finite(hues[k])) continue;
            double target = wrapDegrees(hues[k] - 214 - colorPhase);
            hueIdentity[c] = wrapDegrees(hueIdentity[c]
                + arc(target - hueIdentity[c]) * .55 * bounded(weights[k], .3, 1));
        }
        if (n > 0) engagement = Math.max(engagement, .4);
    }
    private void queueFlash(double u, double v, double hue, double size) {
        if (pendingCount >= MAX_PENDING_FLASHES) return;
        pendingU[pendingCount] = u; pendingV[pendingCount] = v;
        pendingHue[pendingCount] = hue; pendingSize[pendingCount] = size;
        pendingCount++;
    }
    /** Bounded comparison controls; normal app defaults need no additional UI. */
    public void setVisualStyle(double coreScale, double hueArcDegrees, double glow) {
        visualCoreScale = bounded(coreScale, .80, 1.25);
        visualHueArc = bounded(hueArcDegrees, 80, 140);
        visualGlow = bounded(glow, 0, 1.5);
    }
    /**
     * Launcher page position. A single page, or an unknown offset, leaves the
     * pond where it is. The value is read on the next draw.
     */
    public void setPageOffset(double xOffset, double xOffsetStep,
                              double xPixelOffset, double surfaceWidth) {
        pageCentered = PageDrift.centeredOffset(xOffset, xOffsetStep, xPixelOffset, surfaceWidth);
    }
    public double getPageOffset() { return pageCentered; }
    /** A normalized screen tap; the service filters drags and multi-touch. */
    public void touch(double u, double v) {
        if (touchRipplesEnabled) touchRipples.touch(u, v);
    }
    public void setTouchRipplesEnabled(boolean enabled) {
        touchRipplesEnabled = enabled;
        if (!enabled) {
            touchRipples.clear();
            rippleDisplacementMagnitude = 0;
            sampledFieldX = mixedFieldX; sampledFieldY = mixedFieldY;
            sampledProjection = mixedProjection;
        }
    }
    public int getActiveRippleCount() { return touchRipples.getActiveCount(); }
    /** Maximum normalized grid force on the last rendered frame, at most .18. */
    public double getRippleDisplacementMagnitude() { return rippleDisplacementMagnitude; }
    public double getVisualCoreScale() { return visualCoreScale; }
    public double getVisualHueArc() { return visualHueArc; }
    public double getVisualGlow() { return visualGlow; }
    public double getMotionSpeed() { return motionSpeed; }
    public double getFormSpeed() { return formSpeed; }
    public double getColorSpeed() { return colorSpeed; }
    public int getPaletteSize() { return COLOR_BUCKETS; }
    public int getCreatureLimit() { return MAX_CREATURES; }
    public int getActiveCreatureCount() { return pond.getVisibleCount(); }
    public int getTargetCreatureCount() { return pond.getTargetCount(); }
    public double getCreatureOpacity(int creature) { return pond.getOpacity(creature); }
    /** Completed fusions and divisions since this renderer started. */
    public int getMergeCount() { return pond.getMergeCount(); }
    public int getDivideCount() { return pond.getDivideCount(); }
    /** Number of visible merged bodies holding two or more members. */
    public int getMergedBodyCount() {
        int merged = 0;
        for (int c = 0; c < MAX_CREATURES; c++) {
            if (pond.getClusterRoot(c) == c && pond.getClusterSize(c) >= 2 && pond.getOpacity(c) > .05) merged++;
        }
        return merged;
    }
    public double getBond(int a, int b) { return pond.getBond(a, b); }
    public double getHueIdentity(int creature) { return hueIdentity[creature]; }
    /** Visual rhythm and rotation group; equal for fully merged members. */
    public double getPhase(int creature) { return phase[creature]; }
    public double getGroupPhase(int creature) { return groupPhase[creature]; }
    /** Changes visual sampling density without advancing or resetting any clock. */
    public void setPointBudget(int requested) {
        pointCount = Math.max(2000, Math.min(MAX_POINTS, requested));
    }
    public int getPointCount() { return pointCount; }
    public double getSimulationCostMs() { return simulationCostMs; }
    public double getGeometryCostMs() { return geometryCostMs; }
    public double getCanvasCostMs() { return canvasCostMs; }
    public int getLastLinkCount() { return lastLinkCount; }
    public int getLastPointCount() { return lastPointCount; }
    public void reseed(long seed) {
        // Existing hues, forms and trails continue smoothly into the new measured run.
        world.reseed(seed);
        chaos.reseed(seed);
    }
    /** The one shared Life world. */
    public LifeField getLifeField() { return world; }
    /** Every body lives in the same world. */
    public LifeField getLifeField(int creature) { return world; }
    public double getColorPhaseDegrees() { return colorPhase; }
    public double getChaosTimeSeconds() { return chaos.getTimeSeconds(); }
    public long getChaosStepCount() { return chaos.getStepCount(); }
    public double getChaosValue(int system, int axis) { return chaosState[system][axis]; }
    public int getPaletteColor(int bucket) { return colors[bucket]; }
    public int getPalettePopulation(int creature, int bucket) { return palettePopulation[creature][bucket]; }
    public int getFormIndex(int creature) { return coupling == 0 ? 0 : currentForm[creature]; }
    public int getNextFormIndex(int creature) { return coupling == 0 ? 0 : nextForm[creature]; }
    public int getVisitedFormMask(int creature) { return coupling == 0 ? 1 : visitedForms[creature]; }
    public double getFormBlend(int creature) {
        if (coupling == 0) return 0;
        double x = bounded((formProgress[creature] - .15) / .75, 0, 1);
        return x * x * (3 - 2 * x);
    }

    public void render(Canvas canvas, double dtSeconds) {
        long simulationStarted = System.nanoTime();
        simulationCostMs = geometryCostMs = canvasCostMs = 0;
        lastLinkCount = lastPointCount = 0;
        double elapsed = bounded(dtSeconds, 0, 1), dt = Math.min(elapsed, .12);
        elapsedTime += elapsed;
        advanceLifeAndAppearance(elapsed, dt);
        long geometryStarted = System.nanoTime();
        simulationCostMs = (geometryStarted - simulationStarted) / 1000000.0;
        int width = canvas.getWidth(), height = canvas.getHeight();
        if (width <= 0 || height <= 0) return;
        if (width != lastWidth || height != lastHeight) {
            Arrays.fill(historyValid, false);
            lastWidth = width; lastHeight = height; nextHistory = elapsedTime;
        }
        double extent = Math.min(width, height);
        unitsU = extent / width; unitsV = extent / height;
        updateFields(elapsed);
        updateRippleField(elapsed, width, height);
        updatePalette();
        Arrays.fill(counts, 0); Arrays.fill(lineCounts, 0); Arrays.fill(sparkCounts, 0);
        Arrays.fill(sourceLookup, -1);
        Arrays.fill(sumX, 0); Arrays.fill(sumY, 0);
        Arrays.fill(measuredCount, 0); Arrays.fill(radialCount, 0);
        for (int c = 0; c < MAX_CREATURES; c++) {
            Arrays.fill(palettePopulation[c], 0);
            Arrays.fill(radialHistogram[c], 0);
        }
        // The companion: attention follows a touch, a welcome gathers bodies
        // toward the thumb, and engagement briefly quickens a slow pond.
        moodGlow = visualGlow * (.7 + .3 * liveliness);
        // Responses last rather than pull hard: calm, but visible.
        if (!touching) attention *= Math.exp(-elapsed / 4.0);
        gathering *= Math.exp(-elapsed / 8.0);
        if (!touching) engagement *= Math.exp(-elapsed / 6.0);
        pond.setAttention((attentionU - .5) * width / extent, (attentionV - .5) * height / extent,
            attention < .02 ? 0 : attention * (.55 + .45 * affection));
        pond.setGathering((gatherU - .5) * width / extent, (gatherV - .5) * height / extent,
            gathering < .02 ? 0 : gathering);
        // A touch or a welcome lifts the pond's own pace a little, never past
        // 1.8x the chosen Drift, so the pond stays calm.
        double pondElapsed = elapsed * motionSpeed * (1 + .8 * engagement);
        pond.advance(0, width / extent, height / extent, drive);
        while (pondElapsed > 0) {
            double step = Math.min(.5, pondElapsed);
            pond.advance(step, width / extent, height / extent, drive);
            pondElapsed -= step;
        }
        activeCreatureCount = 0;
        double totalOpacity = 0;
        for (int c = 0; c < MAX_CREATURES; c++) {
            opacity[c] = pond.getOpacity(c);
            if (opacity[c] <= .001) continue;
            activeCreatures[activeCreatureCount++] = c;
            totalOpacity += opacity[c];
            encounterHue[c] = pond.getEncounterHueOffset(c);
            bodyFlowDistance[c] = extent * .018 * coupling * pond.getRadius(c) / .20;
            centerX[c] = width * .5 + pond.getX(c) * extent
                + PageDrift.pixelShift(pageCentered, depthVisual[c], extent);
            centerY[c] = height * .5 + pond.getY(c) * extent;
            sampleField(centerX[c] / width, centerY[c] / height, sample);
            frequency[c] = bounded(1 + coupling * (.18 * sample[2] + .035 * sample[0] + .07 * chaosState[c][0]), .72, 1.35);
            amplitude[c] = bounded(1 + coupling * (.16 * sample[2] + .045 * sample[1] + .06 * chaosState[c][1]), .75, 1.30);
            renderForm[c] = getFormIndex(c); renderNext[c] = getNextFormIndex(c);
            renderBlend[c] = getFormBlend(c);
            renderTime[c] = phase[c] * KoiForms.speedRatio(renderForm[c]);
            renderNextTime[c] = phase[c] * KoiForms.speedRatio(renderNext[c]);
            bodyHue[c] = wrapDegrees(214 + hueIdentity[c] + encounterHue[c] + 5 * colorBalance[c] + colorPhase);
            hsv[0] = (float) bodyHue[c]; hsv[1] = .86f; hsv[2] = 1;
            bodyColor[c] = Color.HSVToColor(hsv);
            bodyRadius[c] = extent * pond.getRadius(c) * visualCoreScale
                * PageDrift.visualScale(depthVisual[c]) * massScale[c];
        }
        double opacityPrefix = 0;
        for (int active = 0; active < activeCreatureCount; active++) {
            int c = activeCreatures[active];
            pointStarts[c] = (int) (pointCount * opacityPrefix / totalOpacity);
            opacityPrefix += opacity[c];
        }
        int rawCount = 0, pointCreature = activeCreatureCount - 1;
        // Evaluate each requested sample once. Measuring the resulting geometry
        // avoids static form-size guesses and retains the exact original formulas.
        for (int j = pointCount - 1; j >= 0 && pointCreature >= 0; j--) {
            while (pointCreature > 0 && j < pointStarts[activeCreatures[pointCreature]]) pointCreature--;
            int c = activeCreatures[pointCreature], localPoint = j - pointStarts[c];
            int sourcePoint = (localPoint * 8653 + c * 2053) % MAX_POINTS;
            int a = renderForm[c], b = renderNext[c];
            double blend = renderBlend[c], group = groupPhase[c];
            if (blend == 1 && a != b) {
                boolean valid = KoiForms.evaluate(b, sourcePoint, MAX_POINTS, group,
                    renderNextTime[c], frequency[c], amplitude[c], shapeA);
                if (!valid) valid = KoiForms.evaluate(a, sourcePoint, MAX_POINTS, group,
                    renderTime[c], frequency[c], amplitude[c], shapeA);
                if (!valid) continue;
            } else {
                boolean validA = KoiForms.evaluate(a, sourcePoint, MAX_POINTS, group,
                    renderTime[c], frequency[c], amplitude[c], shapeA);
                if (blend > 0 && a != b) {
                    boolean validB = KoiForms.evaluate(b, sourcePoint, MAX_POINTS, group,
                        renderNextTime[c], frequency[c], amplitude[c], shapeB);
                    if (!validA && !validB) continue;
                    if (!validA) { shapeA[0] = shapeB[0]; shapeA[1] = shapeB[1]; }
                    else if (validB) { shapeA[0] += (shapeB[0] - shapeA[0]) * blend; shapeA[1] += (shapeB[1] - shapeA[1]) * blend; }
                } else if (!validA) continue;
            }
            double x = shapeA[0], y = shapeA[1];
            if (!finite(x) || !finite(y)) continue;
            rawX[rawCount] = x; rawY[rawCount] = y;
            rawCreature[rawCount] = c; rawSource[rawCount] = sourcePoint;
            if (Math.abs(x) < 600 && Math.abs(y) < 600) {
                sumX[c] += x; sumY[c] += y; measuredCount[c]++;
            }
            rawCount++;
        }
        for (int active = 0; active < activeCreatureCount; active++) {
            int c = activeCreatures[active];
            measuredX[c] = measuredCount[c] > 0 ? sumX[c] / measuredCount[c] : 0;
            measuredY[c] = measuredCount[c] > 0 ? sumY[c] / measuredCount[c] : 0;
        }
        for (int i = 0; i < rawCount; i++) {
            int c = rawCreature[i];
            double radius = Math.hypot(rawX[i] - measuredX[c], rawY[i] - measuredY[c]);
            if (radius < 600) {
                radialHistogram[c][Math.min(95, (int) (radius * .16))]++;
                radialCount[c]++;
            }
        }
        for (int active = 0; active < activeCreatureCount; active++) {
            int c = activeCreatures[active], sum = 0, bin = 0;
            int threshold = Math.max(1, (int) Math.ceil(radialCount[c] * .90));
            for (; bin < 95; bin++) { sum += radialHistogram[c][bin]; if (sum >= threshold) break; }
            double measuredRadius = bounded((bin + .5) / .16, 28, 320);
            double follow = formNormalized[c] ? -Math.expm1(-elapsed / .38) : 1;
            formCenterX[c] += (measuredX[c] - formCenterX[c]) * follow;
            formCenterY[c] += (measuredY[c] - formCenterY[c]) * follow;
            formRadius[c] += (measuredRadius - formRadius[c]) * follow;
            formNormalized[c] = true;
            scales[c] = bodyRadius[c] / Math.max(28, formRadius[c]);
        }
        // Hue follows the same spatial direction over the whole luminous body,
        // rather than alternating rainbow colors by its sample index.
        for (int i = 0; i < rawCount; i++) {
            int c = rawCreature[i];
            double dx = rawX[i] - formCenterX[c], dy = rawY[i] - formCenterY[c];
            double radius = Math.hypot(dx, dy), normalizedRadius = radius / Math.max(28, formRadius[c]);
            if (normalizedRadius > 1.12) {
                double softRadius = 1.12 + .33 * -Math.expm1(-(normalizedRadius - 1.12) / .33);
                double soften = softRadius / normalizedRadius;
                dx *= soften; dy *= soften;
            }
            double x = centerX[c] + dx * scales[c], y = centerY[c] + dy * scales[c];
            sampleField(x / width, y / height, sample);
            if (coupling > 0) {
                double ripple = sample[2] * coupling * scales[c] * 6;
                x += bodyFlowDistance[c] * sample[0] + ripple * dx / Math.max(20, radius);
                y += bodyFlowDistance[c] * sample[1] + ripple * dy / Math.max(20, radius);
            }
            screenX[i] = (float) x; screenY[i] = (float) y;
            double spatial = bounded((.82 * dx + .42 * dy) / Math.max(28, formRadius[c]), -1, 1);
            double hue = 214 + hueIdentity[c] + encounterHue[c]
                + visualHueArc * .5 * spatial + 7 * sample[2] + 5 * colorBalance[c];
            hue -= Math.floor(hue / 360) * 360;
            int bucket = Math.min(COLOR_BUCKETS - 1, (int) (hue * COLOR_BUCKETS / 360));
            rawBucket[i] = bucket; counts[bucket] += 2; palettePopulation[c][bucket]++;
            sourceLookup[c * MAX_POINTS + rawSource[i]] = i;
        }
        int pointOffset = 0, sparkOffset = 0;
        for (int b = 0; b < COLOR_BUCKETS; b++) {
            offsets[b] = pointOffset; cursors[b] = pointOffset; pointOffset += counts[b];
        }
        for (int i = 0; i < rawCount; i++) {
            int b = rawBucket[i], position = cursors[b];
            points[position] = screenX[i]; points[position + 1] = screenY[i]; cursors[b] += 2;
        }
        // Every fifth luminous point also carries a wide, faint glow.
        for (int b = 0; b < COLOR_BUCKETS; b++) {
            sparkOffsets[b] = sparkOffset;
            for (int i = offsets[b]; i < offsets[b] + counts[b] && sparkOffset + 1 < SPARK_CAPACITY;
                    i += 2 * SPARK_STRIDE) {
                sparks[sparkOffset++] = points[i]; sparks[sparkOffset++] = points[i + 1];
            }
            sparkCounts[b] = sparkOffset - sparkOffsets[b];
        }
        int lineCount = 0;
        for (int active = 0; active < activeCreatureCount; active++) {
            int c = activeCreatures[active], previous = -1, previousSource = -1;
            int end = active + 1 < activeCreatureCount ? pointStarts[activeCreatures[active + 1]] : pointCount;
            int quota = Math.max(1, end - pointStarts[c]);
            int linkBudget = Math.max(1, MAX_LINKS * quota / pointCount);
            int linkStride = Math.max(1, (quota + linkBudget - 1) / linkBudget);
            int candidate = 0, bodyLinks = 0;
            int maxSourceGap = Math.max(3, MAX_POINTS * 3 / quota);
            double limit = extent * pond.getRadius(c) * .16 * massScale[c], limitSquared = limit * limit;
            for (int source = 0; source < MAX_POINTS; source++) {
                int i = sourceLookup[c * MAX_POINTS + source];
                if (i < 0) continue;
                if (previous >= 0 && source - previousSource <= maxSourceGap) {
                    double dx = screenX[i] - screenX[previous], dy = screenY[i] - screenY[previous];
                    double distance = dx * dx + dy * dy;
                    // Evenly spaced domain samples retain readable filaments
                    // while the entire pond stays below the fixed stroke budget.
                    boolean representative = candidate++ % linkStride == 0;
                    if (representative && distance > .20 && distance <= limitSquared
                            && bodyLinks < linkBudget && lineCount < MAX_LINKS) {
                        int b = rawBucket[i];
                        lineFrom[lineCount] = previous; lineTo[lineCount] = i; lineBucket[lineCount] = b;
                        lineCounts[b] += 4; lineCount++; bodyLinks++;
                    }
                }
                previous = i; previousSource = source;
            }
        }
        int lineOffset = 0;
        for (int b = 0; b < COLOR_BUCKETS; b++) {
            lineOffsets[b] = lineOffset; lineCursors[b] = lineOffset; lineOffset += lineCounts[b];
        }
        for (int i = 0; i < lineCount; i++) {
            int b = lineBucket[i], position = lineCursors[b], from = lineFrom[i], to = lineTo[i];
            lines[position] = screenX[from]; lines[position + 1] = screenY[from];
            lines[position + 2] = screenX[to]; lines[position + 3] = screenY[to];
            lineCursors[b] += 4;
        }
        lastLinkCount = lineCount; lastPointCount = rawCount;
        pollEvents(width, height, extent);
        releasePendingFlashes(width, height, extent);
        long canvasStarted = System.nanoTime();
        geometryCostMs = (canvasStarted - geometryStarted) / 1000000.0;
        canvas.drawColor(BACKGROUND);
        drawWater(canvas, width, height, extent);
        drawAtmosphere(canvas, extent);
        drawTouchRipples(canvas, width, height);
        if (showField) drawSpectralFlow(canvas, width, height);
        float stroke = (float) Math.max(1.0, extent / 480 * 1.25);
        drawTrails(canvas, stroke);
        drawBridges(canvas, extent, stroke);
        int sparkAlpha = (int) bounded((20 + 14 * meanEnergy) * moodGlow / .8, 6, 44);
        sparkPaint.setStrokeWidth(stroke * 3.6f);
        int alpha = (int) bounded(152 + 28 * meanActivity + 22 * meanEnergy, 148, 204);
        for (int b = 0; b < COLOR_BUCKETS; b++) {
            if (sparkCounts[b] > 0) {
                sparkPaint.setColor(colors[b]); sparkPaint.setAlpha(sparkAlpha);
                canvas.drawPoints(sparks, sparkOffsets[b], sparkCounts[b], sparkPaint);
            }
        }
        for (int b = 0; b < COLOR_BUCKETS; b++) {
            pointPaint.setColor(colors[b]);
            if (lineCounts[b] > 0) {
                linePaint.setColor(colors[b]);
                linePaint.setStrokeWidth(stroke * .72f); linePaint.setAlpha(alpha / 2);
                canvas.drawLines(lines, lineOffsets[b], lineCounts[b], linePaint);
            }
            if (counts[b] > 0) {
                pointPaint.setStrokeWidth(stroke); pointPaint.setAlpha(alpha);
                canvas.drawPoints(points, offsets[b], counts[b], pointPaint);
            }
        }
        drawFlashes(canvas, elapsed, stroke);
        if (elapsed > 0 && elapsedTime >= nextHistory) captureHistory();
        canvasCostMs = (System.nanoTime() - canvasStarted) / 1000000.0;
    }

    private double bodyU(int c) { return 0.5 + pond.getX(c) * unitsU; }
    private double bodyV(int c) { return 0.5 + pond.getY(c) * unitsV; }

    private void advanceLifeAndAppearance(double elapsed, double visualDt) {
        double smooth = 1 - Math.exp(-elapsed / .48), hueSmooth = 1 - Math.exp(-elapsed / .65);
        double chaosSmooth = 1 - Math.exp(-elapsed * motionSpeed / .28);
        // The ring remains dynamically active when the world settles. Its
        // forcing is the shared world's measured state.
        double chaosElapsed = elapsed * motionSpeed;
        double worldDensity = world.getPopulation() / (double) WORLD_CELLS;
        while (chaosElapsed > 0) {
            double step = Math.min(1, chaosElapsed);
            chaos.advance(step, world.getActivity(), worldDensity);
            chaosElapsed -= step;
        }
        long before = world.getGeneration(); int reseeds = world.getReseedCount();
        world.advance(elapsed);
        long ticks = world.getReseedCount() != reseeds ? Math.max(1, world.getGeneration())
            : Math.max(0, world.getGeneration() - before);
        double generationSeconds = ticks / (double) rate;
        double bandSum = 0;
        for (int band = 0; band < 8; band++) bandSum += world.getBandEnergy(band);
        double globalBalance = (world.getBandEnergy(0) - world.getBandEnergy(1)) / Math.max(.03, bandSum);
        double visibleWeight = 0;
        for (int c = 0; c < MAX_CREATURES; c++) visibleWeight += pond.getOpacity(c);
        visibleWeight = Math.max(.000001, visibleWeight);
        meanActivity = 0; meanEnergy = 0;
        for (int system = 0; system < CoupledChaos.SYSTEM_COUNT; system++) chaos.sample(system, systemSample[system]);
        for (int c = 0; c < MAX_CREATURES; c++) {
            // A bonded body is driven by the blend of its partners' systems, so
            // merged members share one chaotic rhythm exactly.
            for (int axis = 0; axis < 4; axis++) {
                double target = systemSample[c % 3][axis], weight = 1;
                for (int j = 0; j < MAX_CREATURES; j++) {
                    if (j == c) continue;
                    double b = pond.getBond(c, j);
                    if (b <= .001) continue;
                    double w = b * b * (3 - 2 * b);
                    target += w * systemSample[j % 3][axis]; weight += w;
                }
                chaosState[c][axis] += (target / weight - chaosState[c][axis]) * chaosSmooth;
            }
            // Each body senses the one world where it swims.
            double u = bodyU(c), v = bodyV(c);
            world.sampleLocal(u, v, LOCAL_RADIUS, localSample);
            sampleField(u, v, sample);
            double liveActivity = bounded(localSample[1] * 4.5, 0, 1);
            double liveEnergy = bounded(.6 * bandSum * 4.6 + .4 * Math.abs(sample[2]), 0, 1);
            double liveDensity = localSample[0];
            double balance = .5 * globalBalance + .5 * sample[2];
            activity[c] += (liveActivity - activity[c]) * smooth;
            energy[c] += (liveEnergy - energy[c]) * smooth;
            density[c] += (liveDensity - density[c]) * smooth;
            colorBalance[c] += (balance - colorBalance[c]) * smooth;
            for (int axis = 0; axis < 4; axis++) drive[c][axis] = chaosState[c][axis];
            drive[c][4] = sample[0]; drive[c][5] = sample[1]; drive[c][6] = liveActivity;
            if (ticks > 0) {
                // Life generation events add hue travel according to the occupancy,
                // changed cells and spectral amplitude of the water around the body.
                hueTarget[c] += generationSeconds * colorSpeed * (14 + 35 * liveActivity + 18 * liveEnergy + 35 * liveDensity);
                if (coupling > 0) formTarget[c] += generationSeconds * formSpeed * (.10 + .10 * liveActivity + .04 * liveEnergy);
            }
            // Additional travel comes from the integrated Lorenz ring's actual
            // speed and state separation, not an independent cosmetic oscillator.
            double chaoticDrive = 18 * chaosState[c][3]
                + 5 * Math.abs(chaosState[c][0] - chaosState[c][1]);
            hueTarget[c] += elapsed * colorSpeed * chaoticDrive;
            if (coupling > 0) formTarget[c] += elapsed * formSpeed *
                (.035 * chaosState[c][3] + .015 * Math.abs(chaosState[c][0] - chaosState[c][1]));
            double previousHue = huePhase[c];
            huePhase[c] += (hueTarget[c] - huePhase[c]) * hueSmooth;
            if (coupling > 0) advanceForm(c, smooth);
            phase[c] += Math.PI * visualDt * motionSpeed * (.75 + .25 * liveliness)
                * (1 + coupling * (.65 * activity[c] + .24 * energy[c] + .12 * chaosState[c][3]));
            double weight = pond.getOpacity(c) / visibleWeight;
            meanActivity += activity[c] * weight; meanEnergy += energy[c] * weight;
            // Accumulate current travel, not absolute lifetime phases: fading
            // old or new bodies cannot jump the shared palette around the wheel.
            colorPhase += (huePhase[c] - previousHue) * weight;
        }
        shareBetweenBonded(elapsed, elapsed * motionSpeed);
        if (ticks > 0) tendTheWorld(generationSeconds);
    }

    private void advanceForm(int c, double smooth) {
        formProgress[c] += (formTarget[c] - formProgress[c]) * smooth;
        while (formProgress[c] >= 1) {
            formProgress[c] -= 1; formTarget[c] -= 1;
            currentForm[c] = nextForm[c]; visitedForms[c] |= 1 << currentForm[c];
            if (++tourSteps[c] == KoiForms.COUNT) { tourSteps[c] = 0; formStride[c] = chooseStride(c); }
            nextForm[c] = (currentForm[c] + formStride[c]) % KoiForms.COUNT;
        }
    }

    /**
     * Bonded bodies converge: rhythm (at a bounded rate), rotation, depth,
     * colour, chaotic drive, and form. When fully merged they draw as one
     * larger body. Unbonded bodies slowly return toward their own depth and
     * hue, and drift toward the colour of the water around them.
     */
    private void shareBetweenBonded(double elapsed, double pondElapsed) {
        if (!(elapsed > 0)) return;
        for (int i = 0; i < MAX_CREATURES; i++) {
            for (int j = i + 1; j < MAX_CREATURES; j++) {
                double b = pond.getBond(i, j);
                if (b <= .001) continue;
                double w = b * b * (3 - 2 * b);
                // Fusion resolves in visible seconds even at the slowest Drift,
                // but gently: rhythm catches up at most a little faster than it
                // would run anyway, so nothing visibly fast-forwards.
                double k = -Math.expm1(-elapsed * .35 * w);
                double limit = elapsed * (.08 + .35 * w);
                double move = bounded((phase[j] - phase[i]) * k * .5, -limit, limit);
                phase[i] += move; phase[j] -= move;
                double turn = (groupPhase[j] - groupPhase[i]) * k * .5;
                groupPhase[i] += turn; groupPhase[j] -= turn;
                double depth = (depthVisual[j] - depthVisual[i]) * k * .5;
                depthVisual[i] += depth; depthVisual[j] -= depth;
                double hue = arc(hueIdentity[j] - hueIdentity[i]) * k * .5;
                hueIdentity[i] += hue; hueIdentity[j] -= hue;
                for (int axis = 0; axis < 4; axis++) {
                    double shared = (chaosState[j][axis] - chaosState[i][axis]) * k * .5;
                    chaosState[i][axis] += shared; chaosState[j][axis] -= shared;
                }
            }
        }
        // Every member of a merged body follows the same form: its root, the
        // lowest-numbered member. Pairwise hand-offs would pull a third member
        // toward two different forms at once.
        if (coupling > 0) {
            for (int c = 0; c < MAX_CREATURES; c++) {
                int root = pond.getClusterRoot(c);
                if (root == c) continue;
                double w = 0;
                for (int j = 0; j < MAX_CREATURES; j++) {
                    if (j == c || pond.getClusterRoot(j) != root) continue;
                    double b = pond.getBond(c, j);
                    w = Math.max(w, b * b * (3 - 2 * b));
                }
                if (w <= .35) continue;
                adoptForm(root, c, -Math.expm1(-elapsed * .35 * w), elapsed * w * (.3 + formSpeed));
            }
        }
        for (int c = 0; c < MAX_CREATURES; c++) {
            double mass = pond.getBondMass(c);
            double free = 1 - Math.min(1, mass);
            // Merged bodies grow, but more gently than by area, so a body of
            // four members stays crisp: 1.34x for two, 1.79x for four.
            double target = Math.pow(1 + mass, .42);
            massScale[c] += (target - massScale[c]) * -Math.expm1(-pondElapsed / .8);
            depthVisual[c] += (pond.getDepth(c) - depthVisual[c]) * free * -Math.expm1(-pondElapsed / DEPTH_RELAX_SECONDS);
            hueIdentity[c] += arc(c * 32 - hueIdentity[c]) * free * -Math.expm1(-pondElapsed / HUE_RELAX_SECONDS);
            tint.sample(bodyU(c), bodyV(c), tintSample);
            if (tintSample[1] > .05) {
                hueIdentity[c] += arc(tintSample[0] - hueIdentity[c]) * free * tintSample[1]
                    * -Math.expm1(-pondElapsed / TINT_ABSORB_SECONDS);
            }
            hueIdentity[c] = wrapDegrees(hueIdentity[c]);
        }
    }

    /**
     * The higher-index body takes on the lower-index body's form. Changes
     * are made only while no form blend is visible, then completed quickly.
     */
    private void adoptForm(int lower, int higher, double k, double pondDrive) {
        if (currentForm[higher] != currentForm[lower]) {
            // Redirect only while nothing of the old next form is visible
            // (blend is zero below 0.15 progress); otherwise finish that blend.
            if (nextForm[higher] != currentForm[lower] && formProgress[higher] < .15) {
                nextForm[higher] = currentForm[lower];
            }
            formTarget[higher] += pondDrive;
        } else if (nextForm[higher] != nextForm[lower]) {
            if (formProgress[higher] < .15) {
                nextForm[higher] = nextForm[lower];
                formStride[higher] = formStride[lower];
                tourSteps[higher] = tourSteps[lower];
            } else {
                formTarget[higher] += pondDrive;
            }
        } else {
            formStride[higher] = formStride[lower];
            tourSteps[higher] = tourSteps[lower];
            formTarget[higher] += (formTarget[lower] - formTarget[higher]) * k;
            formProgress[higher] += (formProgress[lower] - formProgress[higher]) * k;
        }
    }

    /** Once per generation: colour spreads, then every body seeds life and paints its hue. */
    private void tendTheWorld(double generationSeconds) {
        tint.spread(world, generationSeconds, TINT_FADE_SECONDS);
        double chance = rate == 1 ? .30 : .12;
        for (int c = 0; c < MAX_CREATURES; c++) {
            double visible = pond.getOpacity(c);
            if (visible < .05) continue;
            double u = bodyU(c), v = bodyV(c);
            double radiusCells = pond.getRadius(c) * unitsU * WORLD_COLUMNS * massScale[c];
            world.seedDisc(u, v, 1.3 + .9 * massScale[c], visible * chance * (.6 + .4 * energy[c]));
            tint.paint(u, v, Math.max(2, radiusCells * .75), wrapDegrees(hueIdentity[c]),
                .22 * visible * Math.min(1, generationSeconds * 2));
        }
    }

    private int chooseStride(int creature) {
        int signature = world.getPopulation() + (int) (world.getBandEnergy(0) * 10000)
            + (int) (world.getBandEnergy(3) * 7000) + creature * 131;
        signature += (int) ((chaosState[creature][2] + 1) * 31);
        return TOUR_STRIDES[signature & 3];
    }
    private void updatePalette() {
        for (int b = 0; b < COLOR_BUCKETS; b++) {
            hsv[0] = (float) ((b * (360.0 / COLOR_BUCKETS) + colorPhase) % 360);
            hsv[1] = (float) bounded(.86 + .10 * meanEnergy, .84, .98);
            hsv[2] = 1;
            colors[b] = Color.HSVToColor(hsv);
        }
    }
    private void updateFields(double elapsed) {
        // Life continues at its actual selected generation rate. Its visible
        // deformation follows gradually, with a 2.22-second time constant at
        // 0.18x form speed. The initial frame starts with the measured field.
        if (mixedFieldsInitialized && !(elapsed > 0)) return;
        double follow = mixedFieldsInitialized ? -Math.expm1(-elapsed * formSpeed / .4) : 1;
        world.sampleGrid(GRID_X, GRID_Y, fieldX, fieldY, projection);
        for (int i = 0; i < GRID_SIZE; i++) {
            mixedFieldX[i] += (bounded(fieldX[i], -1, 1) - mixedFieldX[i]) * follow;
            mixedFieldY[i] += (bounded(fieldY[i], -1, 1) - mixedFieldY[i]) * follow;
            mixedProjection[i] += (bounded(projection[i], -1, 1) - mixedProjection[i]) * follow;
        }
        mixedFieldsInitialized = true;
    }
    private void updateRippleField(double elapsed, int width, int height) {
        touchRipples.advance(elapsed);
        rippleDisplacementMagnitude = 0;
        if (!touchRipplesEnabled || touchRipples.getActiveCount() == 0) {
            sampledFieldX = mixedFieldX; sampledFieldY = mixedFieldY;
            sampledProjection = mixedProjection;
            return;
        }
        double extent = Math.min(width, height);
        double widthUnits = width / extent, heightUnits = height / extent;
        // At most 247 * 3 wave samples, independent of the 2k-14k point budget.
        for (int y = 0; y < GRID_Y; y++) for (int x = 0; x < GRID_X; x++) {
            int i = y * GRID_X + x;
            touchRipples.sample(x / (double) (GRID_X - 1), y / (double) (GRID_Y - 1),
                widthUnits, heightUnits, rippleSample);
            double forceX = rippleSample[0], forceY = rippleSample[1], wave = rippleSample[2];
            rippleDisplacementMagnitude = Math.max(rippleDisplacementMagnitude, Math.hypot(forceX, forceY));
            rippleMixedX[i] = bounded(mixedFieldX[i] + forceX, -1, 1);
            rippleMixedY[i] = bounded(mixedFieldY[i] + forceY, -1, 1);
            rippleMixedProjection[i] = bounded(mixedProjection[i] + wave, -1, 1);
        }
        sampledFieldX = rippleMixedX; sampledFieldY = rippleMixedY;
        sampledProjection = rippleMixedProjection;
    }
    private void drawTouchRipples(Canvas canvas, int width, int height) {
        if (!touchRipplesEnabled) return;
        double extent = Math.min(width, height);
        ripplePaint.setStrokeWidth((float) Math.max(.8, extent / 1100));
        for (int i = 0; i < TouchRipples.MAX_RIPPLES; i++) {
            int alpha = (int) (22 * touchRipples.getOpacity(i));
            if (alpha <= 0) continue;
            ripplePaint.setAlpha(alpha);
            canvas.drawCircle((float) (touchRipples.getU(i) * width),
                (float) (touchRipples.getV(i) * height),
                (float) (touchRipples.getRadius(i) * extent), ripplePaint);
        }
    }
    private void sampleField(double u, double v, double[] out) {
        u -= Math.floor(u); v -= Math.floor(v);
        double gx = u * (GRID_X - 1), gy = v * (GRID_Y - 1);
        int ix = Math.min(GRID_X - 2, (int) gx), iy = Math.min(GRID_Y - 2, (int) gy);
        double x = gx - ix, y = gy - iy;
        int i = iy * GRID_X + ix;
        out[0] = interpolate(sampledFieldX, i, x, y);
        out[1] = interpolate(sampledFieldY, i, x, y);
        out[2] = interpolate(sampledProjection, i, x, y);
    }
    private static double interpolate(double[] data, int i, double x, double y) {
        double top = data[i] + (data[i + 1] - data[i]) * x;
        double bottom = data[i + GRID_X] + (data[i + GRID_X + 1] - data[i + GRID_X]) * x;
        return top + (bottom - top) * y;
    }
    private void drawWater(Canvas canvas, int width, int height, double extent) {
        double phase = elapsedTime * PageDrift.waterRadiansPerSecond(motionSpeed);
        double washShift = PageDrift.pixelShift(pageCentered, 0.15, extent);
        for (int band = 0; band < waterGlow.length; band++) {
            double along = 0.5 + 0.16 * Math.sin(phase * (0.70 + 0.12 * band) + band * 2.2);
            double down = 0.34 + band * 0.32 + 0.04 * Math.sin(phase * 0.45 + band);
            float radius = (float) (extent * 0.42);
            int save = canvas.save();
            canvas.translate((float) (along * width + washShift), (float) (down * height));
            canvas.scale(radius, radius);
            canvas.drawCircle(0, 0, 1, waterGlow[band]);
            canvas.restoreToCount(save);
        }
    }
    private void drawAtmosphere(Canvas canvas, double extent) {
        for (int active = 0; active < activeCreatureCount; active++) {
            int c = activeCreatures[active];
            double depth = depthVisual[c];
            float radius = (float) (extent * pond.getRadius(c) * (2.0 + .6 * energy[c])
                * (0.70 + 0.45 * depth) * massScale[c]);
            int save = canvas.save(); canvas.translate((float) centerX[c], (float) centerY[c]);
            canvas.scale(radius, radius * 1.28f);
            auraPaint.setShader(auraShaders[hueBucket(bodyHue[c])]);
            // Members of a merged body share one glow rather than stacking.
            double share = 1 / (1 + .6 * pond.getBondMass(c));
            auraPaint.setAlpha((int) bounded(110 * opacity[c] * moodGlow * (0.45 + 0.55 * depth) * share, 0, 230));
            canvas.drawCircle(0, 0, 1, auraPaint); canvas.restoreToCount(save);
        }
    }
    /**
     * Threads of light between bodies that are fusing or dividing; faint ones
     * between free neighbors that could fuse. A few short paths per pair.
     */
    private void drawBridges(Canvas canvas, double extent, float stroke) {
        if (!pond.isMerging() && !pond.hasBonds()) return;
        for (int ai = 0; ai < activeCreatureCount; ai++) {
            int i = activeCreatures[ai];
            for (int aj = ai + 1; aj < activeCreatureCount; aj++) {
                int j = activeCreatures[aj];
                double dx = centerX[j] - centerX[i], dy = centerY[j] - centerY[i];
                double distance = Math.hypot(dx, dy);
                double reach = bodyRadius[i] + bodyRadius[j];
                if (distance < reach * .18) continue;
                double b = pond.getBond(i, j), intensity;
                if (b > 0) intensity = 4 * b * (1 - b) + .15 * b;
                else if (pond.isMerging() && distance < reach * 1.5) intensity = .22 * (1 - distance / (reach * 1.5));
                else continue;
                intensity *= Math.min(opacity[i], opacity[j]);
                int alpha = (int) bounded(150 * intensity * moodGlow, 0, 160);
                if (alpha < 3) continue;
                double nx = dx / distance, ny = dy / distance;
                for (int t = 0; t < THREADS; t++) {
                    double spread = (t - (THREADS - 1) * .5) * .32;
                    double sway = Math.sin(elapsedTime * .22 + t * 1.3 + i + 2 * j);
                    double ax = centerX[i] + (nx * Math.cos(spread) - ny * Math.sin(spread)) * bodyRadius[i] * .5;
                    double ay = centerY[i] + (ny * Math.cos(spread) + nx * Math.sin(spread)) * bodyRadius[i] * .5;
                    double bx = centerX[j] - (nx * Math.cos(-spread) - ny * Math.sin(-spread)) * bodyRadius[j] * .5;
                    double by = centerY[j] - (ny * Math.cos(-spread) + nx * Math.sin(-spread)) * bodyRadius[j] * .5;
                    double mx = (ax + bx) * .5 - ny * sway * distance * .16;
                    double my = (ay + by) * .5 + nx * sway * distance * .16;
                    bridgePath.reset();
                    bridgePath.moveTo((float) ax, (float) ay);
                    bridgePath.quadTo((float) mx, (float) my, (float) bx, (float) by);
                    bridgePaint.setColor((t & 1) == 0 ? bodyColor[i] : bodyColor[j]);
                    bridgePaint.setStrokeWidth(Math.max(2f, stroke * 3.2f));
                    bridgePaint.setAlpha(alpha / 4);
                    canvas.drawPath(bridgePath, bridgePaint);
                    bridgePaint.setStrokeWidth(Math.max(1f, stroke * 1.1f));
                    bridgePaint.setAlpha(alpha);
                    canvas.drawPath(bridgePath, bridgePaint);
                }
            }
        }
    }
    private void releasePendingFlashes(int width, int height, double extent) {
        for (int k = 0; k < pendingCount; k++) {
            double hue = pendingHue[k];
            if (!finite(hue)) hue = activeCreatureCount > 0 ? bodyHue[activeCreatures[0]] : 200;
            hsv[0] = (float) wrapDegrees(hue); hsv[1] = .86f; hsv[2] = 1;
            addFlash(pendingU[k] * width, pendingV[k] * height,
                extent * .22 * pendingSize[k], Color.HSVToColor(hsv), hueBucket(hue), true);
        }
        pendingCount = 0;
    }
    private void addFlash(double x, double y, double size, int color, int bucket, boolean merge) {
        int slot = -1;
        for (int f = 0; f < MAX_FLASHES && slot < 0; f++) {
            if (flashSize[f] <= 0 || flashAge[f] >= FLASH_SECONDS) slot = f;
        }
        if (slot < 0) {
            slot = 0;
            for (int f = 1; f < MAX_FLASHES; f++) if (flashAge[f] > flashAge[slot]) slot = f;
        }
        flashX[slot] = x; flashY[slot] = y; flashAge[slot] = 0; flashSize[slot] = size;
        flashColor[slot] = color; flashBucket[slot] = bucket; flashMerge[slot] = merge;
    }
    private void pollEvents(int width, int height, double extent) {
        long serial = pond.getEventSerial();
        for (long s = Math.max(lastEventSerial + 1, serial - 15); s <= serial; s++) {
            int type = pond.readEvent(s, eventOut);
            if (type != PondDynamics.EVENT_MERGED && type != PondDynamics.EVENT_DIVIDED) continue;
            int a = (int) eventOut[2], b = (int) eventOut[3];
            // A finished or unused slot first; otherwise the oldest flash.
            int slot = -1;
            for (int f = 0; f < MAX_FLASHES && slot < 0; f++) {
                if (flashSize[f] <= 0 || flashAge[f] >= FLASH_SECONDS) slot = f;
            }
            if (slot < 0) {
                slot = 0;
                for (int f = 1; f < MAX_FLASHES; f++) if (flashAge[f] > flashAge[slot]) slot = f;
            }
            flashX[slot] = width * .5 + eventOut[0] * extent + PageDrift.pixelShift(pageCentered, depthVisual[a], extent);
            flashY[slot] = height * .5 + eventOut[1] * extent;
            flashAge[slot] = 0;
            flashSize[slot] = Math.max(bodyRadius[a], bodyRadius[b]);
            flashColor[slot] = bodyColor[a];
            flashBucket[slot] = hueBucket(bodyHue[a]);
            flashMerge[slot] = type == PondDynamics.EVENT_MERGED;
        }
        lastEventSerial = serial;
    }
    private void drawFlashes(Canvas canvas, double elapsed, float stroke) {
        for (int f = 0; f < MAX_FLASHES; f++) {
            if (flashSize[f] <= 0 || flashAge[f] >= FLASH_SECONDS) continue;
            double t = flashAge[f] / FLASH_SECONDS, fade = (1 - t) * (1 - t);
            double size = flashSize[f] * (flashMerge[f] ? 1.0 : .7);
            int save = canvas.save();
            canvas.translate((float) flashX[f], (float) flashY[f]);
            float glow = (float) (size * (1.1 + .8 * t));
            canvas.scale(glow, glow);
            auraPaint.setShader(auraShaders[flashBucket[f]]);
            auraPaint.setAlpha((int) bounded(150 * fade * moodGlow, 0, 255));
            canvas.drawCircle(0, 0, 1, auraPaint);
            canvas.restoreToCount(save);
            flashPaint.setColor(flashColor[f]);
            flashPaint.setAlpha((int) bounded(80 * fade * moodGlow, 0, 255));
            flashPaint.setStrokeWidth((float) (stroke * (2.4 - 1.6 * t)));
            canvas.drawCircle((float) flashX[f], (float) flashY[f], (float) (size * (.5 + .9 * t)), flashPaint);
            flashAge[f] += elapsed;
        }
    }
    private void drawSpectralFlow(Canvas canvas, int width, int height) {
        float stroke = Math.max(1f, Math.min(width, height) / 800f);
        for (int active = 0; active < activeCreatureCount; active++) {
            int c = activeCreatures[active];
            flowPath.reset();
            for (int stream = 0; stream < 5; stream++) {
                double u = (stream + .5) / 5, v = (active + .5) / activeCreatureCount;
                flowPath.moveTo((float) (u * width), (float) (v * height));
                for (int step = 0; step < 24; step++) {
                    sampleField(u, v, sample); u += sample[0] * .024; v += sample[1] * .024 * width / height;
                    if (u < -.08 || u > 1.08 || v < -.08 || v > 1.08) break;
                    flowPath.lineTo((float) (u * width), (float) (v * height));
                }
            }
            flowPaint.setColor(bodyColor[c]); flowPaint.setStrokeWidth(stroke * 3.8f); flowPaint.setAlpha((int) ((showField ? 8 : 4) * opacity[c]));
            canvas.drawPath(flowPath, flowPaint);
            flowPaint.setStrokeWidth(stroke); flowPaint.setAlpha((int) ((showField ? 24 : 9) * opacity[c])); canvas.drawPath(flowPath, flowPaint);
        }
    }
    private void drawTrails(Canvas canvas, float stroke) {
        for (int h = 0; h < HISTORY; h++) {
            int slot = (historyCursor + h) % HISTORY;
            if (!historyValid[slot]) continue;
            double age = elapsedTime - historyTimes[slot];
            if (age < 0 || age > HISTORY_LIFETIME) continue;
            double fade = 1 - age / HISTORY_LIFETIME;
            for (int b = 0; b < COLOR_BUCKETS; b++) {
                if (historyCounts[slot][b] == 0) continue;
                trailPaint.setColor(historyColors[slot][b]); trailPaint.setStrokeWidth(stroke * (float) (1.15 + age * .7));
                trailPaint.setAlpha((int) bounded((7 + 8 * meanEnergy) * fade * fade * moodGlow, 0, 16));
                canvas.drawPoints(history[slot], historyOffsets[slot][b], historyCounts[slot][b], trailPaint);
            }
        }
    }
    private void captureHistory() {
        int cursor = 0;
        for (int b = 0; b < COLOR_BUCKETS; b++) {
            int begin = cursor;
            historyOffsets[historyCursor][b] = cursor;
            for (int i = offsets[b]; i < offsets[b] + counts[b] && cursor + 1 < HISTORY_CAPACITY; i += 20) {
                history[historyCursor][cursor++] = points[i];
                history[historyCursor][cursor++] = points[i + 1];
            }
            historyCounts[historyCursor][b] = cursor - begin;
            historyColors[historyCursor][b] = colors[b];
        }
        historyTimes[historyCursor] = elapsedTime; historyValid[historyCursor] = true;
        historyCursor = (historyCursor + 1) % HISTORY; nextHistory = elapsedTime + HISTORY_INTERVAL;
    }
    private static int hueBucket(double displayedHue) {
        return Math.min(COLOR_BUCKETS - 1, (int) (wrapDegrees(displayedHue) * COLOR_BUCKETS / 360));
    }
    private static double wrapDegrees(double degrees) {
        if (!finite(degrees)) return 0;
        double wrapped = degrees % 360;
        return wrapped < 0 ? wrapped + 360 : wrapped;
    }
    /** Signed shortest arc in degrees, (-180, 180]. */
    private static double arc(double degrees) {
        double wrapped = wrapDegrees(degrees);
        return wrapped > 180 ? wrapped - 360 : wrapped;
    }
    private static double bounded(double value, double low, double high) {
        return finite(value) ? Math.max(low, Math.min(high, value)) : low;
    }
    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
}
