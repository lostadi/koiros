/*
 * Koi Life: an integrated Artificial Koi and Conway Life wallpaper.
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
import android.graphics.RadialGradient;
import android.graphics.Shader;
import java.util.Arrays;

/** Measured Life spectra drive morphing creatures and a shared neon spectrum. */
public final class KoiRenderer {
    private static final int MAX_CREATURES = 9, COLOR_BUCKETS = 48, MAX_POINTS = 14000;
    private static final int MAX_LINKS = 900;
    private static final int GRID_X = 13, GRID_Y = 19, GRID_SIZE = GRID_X * GRID_Y;
    private static final int HISTORY = 2, HISTORY_CAPACITY = (MAX_POINTS / 10 + COLOR_BUCKETS) * 2;
    private static final double HISTORY_INTERVAL = .12, HISTORY_LIFETIME = .26;
    private static final int BACKGROUND = Color.rgb(3, 7, 14);
    private static final int[] TOUR_STRIDES = {1, 3, 7, 9};
    private final LifeField[] fields = new LifeField[MAX_CREATURES];
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
    private final double[] chaosSample = new double[4];
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint[] auraPaint = new Paint[MAX_CREATURES];
    private final Path flowPath = new Path();
    // Packed buffers keep palette resolution independent of memory/point budget.
    private final float[] points = new float[MAX_POINTS * 2];
    private final int[] counts = new int[COLOR_BUCKETS], offsets = new int[COLOR_BUCKETS];
    private final int[] cursors = new int[COLOR_BUCKETS];
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
    private final double[][] fieldX = new double[MAX_CREATURES][GRID_SIZE];
    private final double[][] fieldY = new double[MAX_CREATURES][GRID_SIZE];
    private final double[][] projection = new double[MAX_CREATURES][GRID_SIZE];
    // Mixing is linear, so do it at grid nodes once rather than for every point.
    private final double[][] mixedFieldX = new double[MAX_CREATURES][GRID_SIZE];
    private final double[][] mixedFieldY = new double[MAX_CREATURES][GRID_SIZE];
    private final double[][] mixedProjection = new double[MAX_CREATURES][GRID_SIZE];
    // Ripples never enter the retained Life low-pass state: expiry restores the
    // untouched field exactly, without a persistent deformation or extra lag.
    private final double[][] rippleMixedX = new double[MAX_CREATURES][GRID_SIZE];
    private final double[][] rippleMixedY = new double[MAX_CREATURES][GRID_SIZE];
    private final double[][] rippleMixedProjection = new double[MAX_CREATURES][GRID_SIZE];
    private double[][] sampledFieldX = mixedFieldX, sampledFieldY = mixedFieldY;
    private double[][] sampledProjection = mixedProjection;
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
    private int historyCursor, lastWidth, lastHeight, rate = 4;
    private double elapsedTime, nextHistory, colorPhase, meanActivity, meanEnergy;
    private double coupling = .65;
    private double motionSpeed = 1, formSpeed = 1, colorSpeed = 1;
    private double visualCoreScale = 1, visualHueArc = 110, visualGlow = .8;
    private int pointCount = MAX_POINTS;
    private boolean showField;
    private double simulationCostMs, geometryCostMs, canvasCostMs;
    private int lastLinkCount, lastPointCount;

    public KoiRenderer(long seed) {
        chaos = new CoupledChaos(seed);
        pond = new PondDynamics(seed);
        for (int c = 0; c < MAX_CREATURES; c++) {
            fields[c] = new LifeField(48, 64, derivedSeed(seed, c));
            fields[c].setRate(rate);
            density[c] = fields[c].getPopulation() / 3072.0;
            currentForm[c] = (c * 3) % KoiForms.COUNT;
            formStride[c] = chooseStride(c);
            nextForm[c] = (currentForm[c] + formStride[c]) % KoiForms.COUNT;
            visitedForms[c] = 1 << currentForm[c];
            auraPaint[c] = new Paint(Paint.ANTI_ALIAS_FLAG);
            hsv[0] = 180 + c * 60; hsv[1] = .90f; hsv[2] = 1;
            int rgb = Color.HSVToColor(hsv);
            auraPaint[c].setShader(new RadialGradient(0, 0, 1,
                new int[] {Color.argb(27, Color.red(rgb), Color.green(rgb), Color.blue(rgb)),
                    Color.argb(8, Color.red(rgb), Color.green(rgb), Color.blue(rgb)),
                    Color.argb(0, Color.red(rgb), Color.green(rgb), Color.blue(rgb))},
                new float[] {0, .4f, 1}, Shader.TileMode.CLAMP));
        }
        pointPaint.setStyle(Paint.Style.STROKE); pointPaint.setStrokeCap(Paint.Cap.ROUND);
        // Independent tiny segments do not need round-cap stroke tessellation.
        linePaint.setStyle(Paint.Style.STROKE); linePaint.setStrokeCap(Paint.Cap.BUTT);
        flowPaint.setStyle(Paint.Style.STROKE); flowPaint.setStrokeCap(Paint.Cap.ROUND);
        flowPaint.setStrokeJoin(Paint.Join.ROUND);
        ripplePaint.setStyle(Paint.Style.STROKE);
        ripplePaint.setStrokeCap(Paint.Cap.BUTT);
        ripplePaint.setColor(Color.rgb(110, 210, 255));
        updatePalette();
    }

    public void configure(int newRate, double strength, int requestedPoints,
                          boolean visibleField, boolean autoReseed) {
        rate = newRate == 1 ? 1 : 4;
        coupling = bounded(strength, 0, 2);
        pointCount = Math.max(2000, Math.min(MAX_POINTS, requestedPoints));
        showField = visibleField;
        for (LifeField field : fields) { field.setRate(rate); field.setAutoReseed(autoReseed); }
    }
    /** Independent appearance clocks; Life always keeps its selected 1/4 Hz rate. */
    public void setAnimationSpeeds(double motion, double form, double color) {
        motionSpeed = bounded(motion, .02, 2.0);
        formSpeed = bounded(form, .02, 2.0);
        colorSpeed = bounded(color, .02, 2.0);
    }
    public void configurePopulation(int count, boolean varying, boolean collisions) {
        pond.configure(Math.max(1, Math.min(MAX_CREATURES, count)), varying, collisions);
    }
    /** Bounded comparison controls; normal app defaults need no additional UI. */
    public void setVisualStyle(double coreScale, double hueArcDegrees, double glow) {
        visualCoreScale = bounded(coreScale, .80, 1.25);
        visualHueArc = bounded(hueArcDegrees, 80, 140);
        visualGlow = bounded(glow, 0, 1.5);
    }
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
        for (int c = 0; c < MAX_CREATURES; c++) fields[c].reseed(derivedSeed(seed, c));
        chaos.reseed(seed);
    }
    public LifeField getLifeField() { return fields[0]; }
    public LifeField getLifeField(int creature) { return fields[creature]; }
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
        updateFields(elapsed);
        updateRippleField(elapsed, width, height);
        updatePalette();
        Arrays.fill(counts, 0); Arrays.fill(lineCounts, 0);
        Arrays.fill(sourceLookup, -1);
        Arrays.fill(sumX, 0); Arrays.fill(sumY, 0);
        Arrays.fill(measuredCount, 0); Arrays.fill(radialCount, 0);
        for (int c = 0; c < MAX_CREATURES; c++) {
            Arrays.fill(palettePopulation[c], 0);
            Arrays.fill(radialHistogram[c], 0);
        }
        double extent = Math.min(width, height);
        double pondElapsed = elapsed * motionSpeed;
        pond.advance(0, width / extent, height / extent, chaosState);
        while (pondElapsed > 0) {
            double step = Math.min(.5, pondElapsed);
            pond.advance(step, width / extent, height / extent, chaosState);
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
            centerX[c] = width * .5 + pond.getX(c) * extent;
            centerY[c] = height * .5 + pond.getY(c) * extent;
            sampleField(c, centerX[c] / width, centerY[c] / height, sample);
            frequency[c] = bounded(1 + coupling * (.18 * sample[2] + .035 * sample[0] + .07 * chaosState[c][0]), .72, 1.35);
            amplitude[c] = bounded(1 + coupling * (.16 * sample[2] + .045 * sample[1] + .06 * chaosState[c][1]), .75, 1.30);
            renderForm[c] = getFormIndex(c); renderNext[c] = getNextFormIndex(c);
            renderBlend[c] = getFormBlend(c);
            renderTime[c] = phase[c] * KoiForms.speedRatio(renderForm[c]);
            renderNextTime[c] = phase[c] * KoiForms.speedRatio(renderNext[c]);
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
            double blend = renderBlend[c];
            if (blend == 1 && a != b) {
                boolean valid = KoiForms.evaluate(b, sourcePoint, MAX_POINTS, c,
                    renderNextTime[c], frequency[c], amplitude[c], shapeA);
                if (!valid) valid = KoiForms.evaluate(a, sourcePoint, MAX_POINTS, c,
                    renderTime[c], frequency[c], amplitude[c], shapeA);
                if (!valid) continue;
            } else {
                boolean validA = KoiForms.evaluate(a, sourcePoint, MAX_POINTS, c,
                    renderTime[c], frequency[c], amplitude[c], shapeA);
                if (blend > 0 && a != b) {
                    boolean validB = KoiForms.evaluate(b, sourcePoint, MAX_POINTS, c,
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
            scales[c] = extent * pond.getRadius(c) * visualCoreScale / Math.max(28, formRadius[c]);
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
            sampleField(c, x / width, y / height, sample);
            if (coupling > 0) {
                double ripple = sample[2] * coupling * scales[c] * 6;
                x += bodyFlowDistance[c] * sample[0] + ripple * dx / Math.max(20, radius);
                y += bodyFlowDistance[c] * sample[1] + ripple * dy / Math.max(20, radius);
            }
            screenX[i] = (float) x; screenY[i] = (float) y;
            double spatial = bounded((.82 * dx + .42 * dy) / Math.max(28, formRadius[c]), -1, 1);
            double hue = 214 + c * 32 + encounterHue[c]
                + visualHueArc * .5 * spatial + 7 * sample[2] + 5 * colorBalance[c];
            hue -= Math.floor(hue / 360) * 360;
            int bucket = Math.min(COLOR_BUCKETS - 1, (int) (hue * COLOR_BUCKETS / 360));
            rawBucket[i] = bucket; counts[bucket] += 2; palettePopulation[c][bucket]++;
            sourceLookup[c * MAX_POINTS + rawSource[i]] = i;
        }
        int pointOffset = 0;
        for (int b = 0; b < COLOR_BUCKETS; b++) {
            offsets[b] = pointOffset; cursors[b] = pointOffset; pointOffset += counts[b];
        }
        for (int i = 0; i < rawCount; i++) {
            int b = rawBucket[i], position = cursors[b];
            points[position] = screenX[i]; points[position + 1] = screenY[i]; cursors[b] += 2;
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
            double limit = extent * pond.getRadius(c) * .16, limitSquared = limit * limit;
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
        long canvasStarted = System.nanoTime();
        geometryCostMs = (canvasStarted - geometryStarted) / 1000000.0;
        canvas.drawColor(BACKGROUND);
        drawAtmosphere(canvas, extent);
        drawTouchRipples(canvas, width, height);
        if (showField) drawSpectralFlow(canvas, width, height);
        float stroke = (float) Math.max(1.0, extent / 480 * 1.25);
        drawTrails(canvas, stroke);
        int alpha = (int) bounded(170 + 24 * meanActivity + 20 * meanEnergy, 166, 216);
        for (int b = 0; b < COLOR_BUCKETS; b++) {
            pointPaint.setColor(colors[b]);
            if (lineCounts[b] > 0) {
                linePaint.setColor(colors[b]);
                linePaint.setStrokeWidth(stroke * .72f); linePaint.setAlpha(alpha * 3 / 5);
                canvas.drawLines(lines, lineOffsets[b], lineCounts[b], linePaint);
            }
            if (counts[b] > 0) {
                pointPaint.setStrokeWidth(stroke); pointPaint.setAlpha(alpha);
                canvas.drawPoints(points, offsets[b], counts[b], pointPaint);
            }
        }
        if (elapsed > 0 && elapsedTime >= nextHistory) captureHistory();
        canvasCostMs = (System.nanoTime() - canvasStarted) / 1000000.0;
    }

    private void advanceLifeAndAppearance(double elapsed, double visualDt) {
        double smooth = 1 - Math.exp(-elapsed / .48), hueSmooth = 1 - Math.exp(-elapsed / .65);
        double chaosSmooth = 1 - Math.exp(-elapsed * motionSpeed / .28);
        double forcingActivity = 0, forcingDensity = 0, visibleWeight = 0;
        for (int c = 0; c < MAX_CREATURES; c++) {
            double weight = pond.getOpacity(c);
            forcingActivity += fields[c].getActivity() * weight;
            forcingDensity += fields[c].getPopulation() / 3072.0 * weight;
            visibleWeight += weight;
        }
        visibleWeight = Math.max(.000001, visibleWeight);
        forcingActivity /= visibleWeight; forcingDensity /= visibleWeight;
        // The ring remains dynamically active when a Life run settles. Its
        // forcing is the previous visible frame's measured Life state.
        double chaosElapsed = elapsed * motionSpeed;
        while (chaosElapsed > 0) {
            double step = Math.min(1, chaosElapsed);
            chaos.advance(step, forcingActivity, forcingDensity);
            chaosElapsed -= step;
        }
        meanActivity = 0; meanEnergy = 0;
        for (int c = 0; c < MAX_CREATURES; c++) {
            LifeField field = fields[c];
            chaos.sample(c % 3, chaosSample);
            for (int axis = 0; axis < 4; axis++)
                chaosState[c][axis] += (chaosSample[axis] - chaosState[c][axis]) * chaosSmooth;
            long before = field.getGeneration(); int reseeds = field.getReseedCount();
            field.advance(elapsed);
            long ticks = field.getReseedCount() != reseeds ? Math.max(1, field.getGeneration()) : Math.max(0, field.getGeneration() - before);
            double sum = 0;
            for (int band = 0; band < 8; band++) sum += field.getBandEnergy(band);
            double liveActivity = bounded(field.getActivity() * 4.5, 0, 1), liveEnergy = bounded(sum * 3.2, 0, 1);
            double liveDensity = field.getPopulation() / 3072.0;
            double balance = (field.getBandEnergy(0) - field.getBandEnergy(1)) / Math.max(.03, sum);
            activity[c] += (liveActivity - activity[c]) * smooth;
            energy[c] += (liveEnergy - energy[c]) * smooth;
            density[c] += (liveDensity - density[c]) * smooth;
            colorBalance[c] += (balance - colorBalance[c]) * smooth;
            if (ticks > 0) {
                double generationSeconds = ticks / (double) rate;
                // Life generation events add hue travel according to actual
                // occupancy, changed cells and spatial-spectrum amplitude.
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
            if (coupling > 0) {
                formProgress[c] += (formTarget[c] - formProgress[c]) * smooth;
                while (formProgress[c] >= 1) {
                    formProgress[c] -= 1; formTarget[c] -= 1;
                    currentForm[c] = nextForm[c]; visitedForms[c] |= 1 << currentForm[c];
                    if (++tourSteps[c] == KoiForms.COUNT) { tourSteps[c] = 0; formStride[c] = chooseStride(c); }
                    nextForm[c] = (currentForm[c] + formStride[c]) % KoiForms.COUNT;
                }
            }
            phase[c] += Math.PI * visualDt * motionSpeed * (1 + coupling * (.65 * activity[c] + .24 * energy[c] + .12 * chaosState[c][3]));
            double weight = pond.getOpacity(c) / visibleWeight;
            meanActivity += activity[c] * weight; meanEnergy += energy[c] * weight;
            // Accumulate current travel, not absolute lifetime phases: fading
            // old or new bodies cannot jump the shared palette around the wheel.
            colorPhase += (huePhase[c] - previousHue) * weight;
        }
    }
    private int chooseStride(int creature) {
        LifeField f = fields[creature];
        int signature = f.getPopulation() + (int) (f.getBandEnergy(0) * 10000) + (int) (f.getBandEnergy(3) * 7000);
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
        for (int c = 0; c < MAX_CREATURES; c++) {
            fields[c].sampleGrid(GRID_X, GRID_Y, fieldX[c], fieldY[c], projection[c]);
            for (int i = 0; i < GRID_SIZE; i++) {
                fieldX[c][i] = bounded(fieldX[c][i], -1, 1);
                fieldY[c][i] = bounded(fieldY[c][i], -1, 1);
                projection[c][i] = bounded(projection[c][i], -1, 1);
            }
        }
        for (int c = 0; c < MAX_CREATURES; c++) {
            int other = (c + 1) % MAX_CREATURES;
            for (int i = 0; i < GRID_SIZE; i++) {
                double targetX = .76 * fieldX[c][i] + .24 * fieldX[other][i];
                double targetY = .76 * fieldY[c][i] + .24 * fieldY[other][i];
                double targetProjection = .76 * projection[c][i] + .24 * projection[other][i];
                mixedFieldX[c][i] += (targetX - mixedFieldX[c][i]) * follow;
                mixedFieldY[c][i] += (targetY - mixedFieldY[c][i]) * follow;
                mixedProjection[c][i] += (targetProjection - mixedProjection[c][i]) * follow;
            }
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
        // Reuse the normal bilinear point sampler with precombined grid arrays.
        for (int y = 0; y < GRID_Y; y++) for (int x = 0; x < GRID_X; x++) {
            int i = y * GRID_X + x;
            touchRipples.sample(x / (double) (GRID_X - 1), y / (double) (GRID_Y - 1),
                widthUnits, heightUnits, rippleSample);
            double forceX = rippleSample[0], forceY = rippleSample[1], wave = rippleSample[2];
            rippleDisplacementMagnitude = Math.max(rippleDisplacementMagnitude, Math.hypot(forceX, forceY));
            for (int c = 0; c < MAX_CREATURES; c++) {
                rippleMixedX[c][i] = bounded(mixedFieldX[c][i] + forceX, -1, 1);
                rippleMixedY[c][i] = bounded(mixedFieldY[c][i] + forceY, -1, 1);
                rippleMixedProjection[c][i] = bounded(mixedProjection[c][i] + wave, -1, 1);
            }
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
    private void sampleField(int c, double u, double v, double[] out) {
        u -= Math.floor(u); v -= Math.floor(v);
        double gx = u * (GRID_X - 1), gy = v * (GRID_Y - 1);
        int ix = Math.min(GRID_X - 2, (int) gx), iy = Math.min(GRID_Y - 2, (int) gy);
        double x = gx - ix, y = gy - iy;
        int i = iy * GRID_X + ix;
        out[0] = interpolate(sampledFieldX[c], i, x, y);
        out[1] = interpolate(sampledFieldY[c], i, x, y);
        out[2] = interpolate(sampledProjection[c], i, x, y);
    }
    private static double interpolate(double[] data, int i, double x, double y) {
        double top = data[i] + (data[i + 1] - data[i]) * x;
        double bottom = data[i + GRID_X] + (data[i + GRID_X + 1] - data[i + GRID_X]) * x;
        return top + (bottom - top) * y;
    }
    private void drawAtmosphere(Canvas canvas, double extent) {
        for (int active = 0; active < activeCreatureCount; active++) {
            int c = activeCreatures[active];
            float radius = (float) (extent * pond.getRadius(c) * (2.0 + .6 * energy[c]));
            int save = canvas.save(); canvas.translate((float) centerX[c], (float) centerY[c]);
            canvas.scale(radius, radius * 1.28f); auraPaint[c].setAlpha((int) bounded(85 * opacity[c] * visualGlow, 0, 220));
            canvas.drawCircle(0, 0, 1, auraPaint[c]); canvas.restoreToCount(save);
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
                    sampleField(c, u, v, sample); u += sample[0] * .024; v += sample[1] * .024 * width / height;
                    if (u < -.08 || u > 1.08 || v < -.08 || v > 1.08) break;
                    flowPath.lineTo((float) (u * width), (float) (v * height));
                }
            }
            flowPaint.setColor(colors[(c * 7) % COLOR_BUCKETS]); flowPaint.setStrokeWidth(stroke * 3.8f); flowPaint.setAlpha((int) ((showField ? 8 : 4) * opacity[c]));
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
                pointPaint.setColor(historyColors[slot][b]); pointPaint.setStrokeWidth(stroke * (float) (1.15 + age * .7));
                pointPaint.setAlpha((int) bounded((7 + 8 * meanEnergy) * fade * fade * visualGlow, 0, 16));
                canvas.drawPoints(history[slot], historyOffsets[slot][b], historyCounts[slot][b], pointPaint);
            }
        }
    }
    private void captureHistory() {
        int cursor = 0;
        for (int b = 0; b < COLOR_BUCKETS; b++) {
            int begin = cursor;
            historyOffsets[historyCursor][b] = cursor;
            for (int i = offsets[b]; i < offsets[b] + counts[b]; i += 20) {
                history[historyCursor][cursor++] = points[i];
                history[historyCursor][cursor++] = points[i + 1];
            }
            historyCounts[historyCursor][b] = cursor - begin;
            historyColors[historyCursor][b] = colors[b];
        }
        historyTimes[historyCursor] = elapsedTime; historyValid[historyCursor] = true;
        historyCursor = (historyCursor + 1) % HISTORY; nextHistory = elapsedTime + HISTORY_INTERVAL;
    }
    private static long derivedSeed(long seed, int field) {
        if (field == 0) return seed;
        long z = seed + 0x9e3779b97f4a7c15L * field;
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
    private static double bounded(double value, double low, double high) {
        return finite(value) ? Math.max(low, Math.min(high, value)) : low;
    }
    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
}
