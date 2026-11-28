/*
 * Koiros: dots that obey their own equations in one hidden Game of Life world.
 * GPL-3.0-or-later. See LICENSE and CREDITS.
 */
package org.ostadix.koilife;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
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
 * and always ends at two drawn dots. Each dot is drawn in its own exact
 * colour, with heat mixed in as red, and each bond is a gradient between its
 * dots. Size, brightness and glow follow each dot's rhythm smoothly, so groups
 * that keep the same rhythm visibly pulse together. Only the frame is drawn;
 * the pond goes on beyond it. The Life world is never drawn; it supplies the
 * current the dots swim in, the restlessness that frays bonds, and carries
 * colour between bodies. No allocations occur per frame.
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
     * Dots, their glows and their bonds are drawn as textured quads, every
     * vertex in its dot's exact colour: one draw call each, with no palette
     * steps. Dots are sorted into depth layers so nearer ones sit on top.
     */
    private static final int LAYERS = 8, KIND_STEPS = 1440, DISC = 16, HALO = 32, LINE = 4;
    /** The disc's radius as a fraction of its quad's half-width. */
    private static final float DISC_FILL = .9f;
    /** Heat is drawn as this red, mixed in as strongly as a vertex is hot; the kinds' own reds are softer. */
    private static final int HEAT_COLOR = Color.rgb(255, 38, 22);
    private static final short[] QUAD_INDICES = quadIndices(DotLattice.MAX_EDGES);
    /** Each body is drawn as a domed disc tilted in 3D: dome height and largest tilt, in radians. */
    private static final double DOME = .55, TILT = .62;

    private final LifeField world;
    private final WorldTint tint = new WorldTint(WORLD_COLUMNS, WORLD_ROWS);
    private final CoupledChaos chaos;
    private final DotLattice dots;
    private final TouchRipples ripples = new TouchRipples();
    private boolean ripplesEnabled = true;

    private final double[] fieldX = new double[GRID_SIZE], fieldY = new double[GRID_SIZE];
    private final double[] projection = new double[GRID_SIZE];
    private final double[] waterX = new double[GRID_SIZE], waterY = new double[GRID_SIZE];
    private final double[] pattern = new double[GRID_SIZE];
    private final double[] flowX = new double[GRID_SIZE], flowY = new double[GRID_SIZE];
    private final double[] turbulence = new double[GRID_SIZE];
    private final double[] local = new double[2], rippleSample = new double[3];
    private final double[] tintSample = new double[2], chaosSample = new double[4], eventOut = new double[3];
    private boolean waterReady;

    private final float[] screenX = new float[DotLattice.MAX_DOTS], screenY = new float[DotLattice.MAX_DOTS];
    private final double[] previousX = new double[DotLattice.MAX_DOTS], previousY = new double[DotLattice.MAX_DOTS];
    private final double[] previousPhase = new double[DotLattice.MAX_DOTS];
    private final double[] drawX = new double[DotLattice.MAX_DOTS], drawY = new double[DotLattice.MAX_DOTS];
    private final double[] drawPhase = new double[DotLattice.MAX_DOTS];
    private final double[] auraX = new double[DotLattice.MAX_BODIES], auraY = new double[DotLattice.MAX_BODIES];
    private final int[] auraCount = new int[DotLattice.MAX_BODIES], slotOfId = new int[256];
    private double pondAccumulator;
    private double stirU, stirV, stirVelocityU, stirVelocityV, stirFade, stirAt;
    private double pendingPulseU, pendingPulseV;
    private boolean pulsePending;
    // Depth: each dot is drawn about a smoothed centre of its body, so fusing
    // bodies glide into one projection instead of jumping.
    private final double[] anchorX = new double[DotLattice.MAX_DOTS], anchorY = new double[DotLattice.MAX_DOTS];
    private final double[] anchorR = new double[DotLattice.MAX_DOTS];
    private final double[] anchorDepth = new double[DotLattice.MAX_DOTS], anchorSpin = new double[DotLattice.MAX_DOTS];
    private final double[] auraDepth = new double[DotLattice.MAX_BODIES], auraSpin = new double[DotLattice.MAX_BODIES];
    private double longestBondPx, looseFraction;
    private static final int FIELD_COLUMNS = 24, FIELD_ROWS = 52;
    private final double[][] field = new double[6][FIELD_COLUMNS * FIELD_ROWS];
    private final double[] projected = new double[6];
    private boolean fieldReady;
    private int anchorVersion = -1;
    private double tiltClock, axisClock;
    private int savedVersion = -1;
    private int stepsThisFrame;
    private final boolean[] visibleDot = new boolean[DotLattice.MAX_DOTS], anchorFresh = new boolean[DotLattice.MAX_DOTS];
    private final float[] dotVerts = new float[DotLattice.MAX_DOTS * 8], glowVerts = new float[DotLattice.MAX_DOTS * 8];
    private final float[] lineVerts = new float[DotLattice.MAX_EDGES * 8];
    private final float[] dotTexs = quadTexs(DotLattice.MAX_DOTS, DISC), glowTexs = quadTexs(DotLattice.MAX_DOTS, HALO);
    private final float[] lineTexs = lineTexs(DotLattice.MAX_EDGES);
    private final int[] dotColors = new int[DotLattice.MAX_DOTS * 4], glowColors = new int[DotLattice.MAX_DOTS * 4];
    private final int[] lineColors = new int[DotLattice.MAX_EDGES * 4];
    private final int[] dotColor = new int[DotLattice.MAX_DOTS], dotLayer = new int[DotLattice.MAX_DOTS];
    private final int[] order = new int[DotLattice.MAX_DOTS], layerCounts = new int[LAYERS], layerStart = new int[LAYERS];
    private final float[] dotSize = new float[DotLattice.MAX_DOTS], dotLight = new float[DotLattice.MAX_DOTS];
    private final float[] dotGlow = new float[DotLattice.MAX_DOTS];
    private final int[] kindPalette = new int[KIND_STEPS];
    private final int[] colors = new int[COLOR_BUCKETS];
    private boolean paletteReady;
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
    private double elapsedTime, climate = 1, unitsW = 1, unitsH = 2;
    private double hueDriftTarget, hueDriftNow;
    private double simulationCostMs, geometryCostMs, canvasCostMs;
    private int lastLinkCount, lastPointCount;
    private volatile double pageCentered;
    // The home screen's apps as solid shapes, and how hard dots rebound from them.
    private static final double HOME_RESTITUTION = .55;
    private final HomeCollider home = new HomeCollider();
    private final double[] contact = new double[3];
    private volatile HomeLayout homeLayout;
    private volatile double homeShift;
    private int homeContacts;

    public LatticeRenderer(long seed) {
        world = new LifeField(WORLD_COLUMNS, WORLD_ROWS, seed);
        world.setRate(rate);
        chaos = new CoupledChaos(seed);
        // Each pond starts from a fresh random arrangement; the Life world keeps the saved seed.
        dots = new DotLattice(seed ^ System.nanoTime());
        this.seed = seed;
        PorterDuffXfermode light = new PorterDuffXfermode(PorterDuff.Mode.ADD);
        // Quads textured with a white disc, a soft halo, or a line's cross-section;
        // the vertex colours tint them.
        dotPaint.setShader(new BitmapShader(sprite(DISC, false), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        glowPaint.setShader(new BitmapShader(sprite(HALO, true), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        linePaint.setShader(new BitmapShader(lineProfile(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        for (Paint paint : new Paint[] {dotPaint, glowPaint, linePaint}) {
            paint.setStyle(Paint.Style.FILL); paint.setFilterBitmap(true); paint.setXfermode(light);
        }
        for (int k = 0; k < KIND_STEPS; k++) {
            double hue = k * 360.0 / KIND_STEPS;
            hsv[0] = (float) hue; hsv[1] = (float) kindSaturation(hue); hsv[2] = 1;
            kindPalette[k] = Color.HSVToColor(hsv);
        }
        auraPaint.setXfermode(light);
        for (int b = 0; b < COLOR_BUCKETS; b++) {
            hsv[0] = b * (360f / COLOR_BUCKETS); hsv[1] = .88f; hsv[2] = 1;
            int rgb = Color.HSVToColor(hsv);
            int r = Color.red(rgb), g = Color.green(rgb), bl = Color.blue(rgb);
            auraShaders[b] = new RadialGradient(0, 0, 1,
                new int[] {Color.argb(46, r, g, bl), Color.argb(14, r, g, bl), Color.argb(0, r, g, bl)},
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
    /** Each drawn dot in its own colour, as bright as it is drawn; heat is passing, so left out. */
    @Override public void showColors(ThemeFollower follower) {
        for (int i = 0, n = dots.getCount(); i < n; i++) {
            if (visibleDot[i] && dots.redness[i] < .3) follower.add(214 + dots.hue[i], dotLight[i]);
        }
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
    /** The home screen's shapes to collide with, or null; paged shapes moved by shiftPx. */
    @Override public void setHome(HomeLayout layout, double shiftPx) { homeLayout = layout; homeShift = shiftPx; }
    /** Dots pushed out of an app this frame. */
    public int getHomeContacts() { return homeContacts; }
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
        else if (kind == TOUCH_TAP) {
            touching = false; affection = Companion.pet(affection); attention = 1; engagement = 1;
            pendingPulseU = attentionU; pendingPulseV = attentionV; pulsePending = true;
        }
        else touching = false;
    }

    /**
     * A finger moving over the wallpaper, in screen fractions and screen
     * fractions per second. Nearby dots move with it at once and keep some of
     * its velocity, so they can be pushed, pulled apart, gathered or flung.
     */
    @Override public void stir(double u, double v, double velocityU, double velocityV) {
        if (!finite(u) || !finite(v)) return;
        stirU = bounded(u, 0, 1); stirV = bounded(v, 0, 1);
        stirVelocityU = finite(velocityU) ? bounded(velocityU, -6, 6) : 0;
        stirVelocityV = finite(velocityV) ? bounded(velocityV, -6, 6) : 0;
        stirFade = 1; stirAt = elapsedTime;
        engagement = Math.max(engagement, .6);
    }

    private void applyTouch(double elapsed) {
        int n = dots.getCount();
        double pondPerReal = Math.max(1e-6, motionSpeed * PACE * (1 + .8 * engagement));
        if (stirFade > 0 && elapsed > 0) {
            if (elapsedTime - stirAt > .06) {
                // The finger has stopped or lifted: its pull fades over a fraction of a second.
                stirVelocityU *= Math.exp(-elapsed / .1); stirVelocityV *= Math.exp(-elapsed / .1);
                stirFade = Math.max(0, stirFade - elapsed / .3);
            }
            double fx = (stirU - .5) * unitsW, fy = (stirV - .5) * unitsH, radius = .17;
            double vx = stirVelocityU * unitsW, vy = stirVelocityV * unitsH;
            for (int i = 0; i < n; i++) {
                double dx = dots.px[i] - fx, dy = dots.py[i] - fy, d2 = dx * dx + dy * dy;
                if (d2 > 6.25 * radius * radius) continue;
                double fall = Math.exp(-d2 / (radius * radius)) * stirFade;
                double mx = vx * elapsed * fall * .85, my = vy * elapsed * fall * .85;
                dots.nudge(i, mx, my);
                previousX[i] += mx; previousY[i] += my;
                dots.steer(i, vx / pondPerReal, vy / pondPerReal, .6 * fall);
            }
        }
        if (pulsePending) {
            pulsePending = false;
            // A tap startles nearby dots outward and pulls their rhythms into one
            // bright beat, so they flash together; then curiosity brings them back.
            double fx = (pendingPulseU - .5) * unitsW, fy = (pendingPulseV - .5) * unitsH, radius = .22;
            for (int i = 0; i < n; i++) {
                double dx = dots.px[i] - fx, dy = dots.py[i] - fy, d2 = dx * dx + dy * dy;
                if (d2 > 6.25 * radius * radius) continue;
                double d = Math.sqrt(d2) + 1e-6, fall = Math.exp(-d2 / (radius * radius));
                dots.steer(i, .16 * dx / d, .16 * dy / d, .8 * fall);
                double toBright = -Math.atan2(Math.sin(dots.phase[i]), Math.cos(dots.phase[i]));
                dots.phase[i] += toBright * .75 * fall;
            }
        }
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
            tint.paint(u, v, 7 + 6 * weight, wrapDegrees(hues[k] - 214), .95 * weight);
            queueFlash(u, v, hues[k], .5 + .5 * weight);
        }
        if (n == 0) return;
        for (int slot = 0; slot < dots.bodyCount; slot++) {
            int k = slot % n;
            if (!finite(hues[k])) continue;
            double target = wrapDegrees(hues[k] - 214), lean = .55 * bounded(weights[k], .3, 1);
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
        followSlots();
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
        // A touch or a welcome lifts the pond's own pace by at most 1.8x; with
        // the lattice's own 1.5x, that is at most 2.7x the chosen Drift.
        double pondDelta = elapsed * motionSpeed * PACE * (1 + .8 * engagement);
        pondAccumulator = Math.min(pondAccumulator + pondDelta, 40 * STEP);
        tiltClock += pondDelta * .35; axisClock += pondDelta * .18;
        stepsThisFrame = 0;
        while (pondAccumulator >= STEP) {
            // At high Drift a frame's backlog is taken in at most about four
            // steps of up to 0.1 pond seconds, which the equations tolerate.
            double step = Math.min(.1, Math.max(STEP, pondAccumulator / 4));
            saveState();
            updateWater(step / Math.max(1e-6, motionSpeed * PACE));
            dots.step(step);
            followSlots();
            pondAccumulator -= step;
            stepsThisFrame++;
        }
        if (dots.structureVersion != savedVersion) saveState();
        applyTouch(elapsed);
        double blend = pondAccumulator / STEP;
        for (int i = 0, n = dots.getCount(); i < n; i++) {
            drawX[i] = previousX[i] + (dots.px[i] - previousX[i]) * blend;
            drawY[i] = previousY[i] + (dots.py[i] - previousY[i]) * blend;
            drawPhase[i] = previousPhase[i] + (dots.phase[i] - previousPhase[i]) * blend;
        }
        long geometryStarted = System.nanoTime();
        simulationCostMs = (geometryStarted - started) / 1e6;
        updatePalette();
        measureBodies();
        // Bodies barely move between physics steps; the field follows the steps.
        if (stepsThisFrame > 0 || !fieldReady) { projectionField(); fieldReady = true; }
        boolean glow = pointBudget >= GLOW_BUDGET;
        int n = dots.getCount();
        if (dots.structureVersion != anchorVersion) {
            // A new pond: every anchor starts where its dot is.
            Arrays.fill(anchorFresh, true);
            anchorVersion = dots.structureVersion;
        }
        double follow = -Math.expm1(-elapsed / .45);
        int drawnDots = 0, looseDots = 0;
        float stroke = (float) Math.max(1.0, extent / 480 * 1.25);
        double maxBond = 2.6 * DotLattice.SPACING_HINT * extent;
        // Only what is in the frame is drawn: the pond goes on beyond it. Dots up to
        // a bond's length outside still count, so bonds run off the edge to real dots.
        double nearW = unitsW * .5 + .25, nearH = unitsH * .5 + .25, pad = maxBond + 4 * stroke;
        // Contact begins a little before a dot's centre reaches an app's edge.
        home.prepare(homeLayout, homeShift, width, height, 2.5 * stroke);
        boolean collideHome = home.active();
        homeContacts = 0;
        Arrays.fill(layerCounts, 0);
        for (int i = 0; i < n; i++) {
            visibleDot[i] = false;
            int id = dots.bodyOf[i], slot = id >= 0 && id < slotOfId.length ? slotOfId[id] : -1;
            boolean inBody = slot >= 0 && auraCount[slot] > 0;
            // The projection is a smooth field over the screen (see
            // projectionField), so two bonded neighbours always get nearly
            // the same transform, whichever body each belongs to.
            sampleProjection(drawX[i], drawY[i]);
            double presence = projected[5];
            double f = anchorFresh[i] ? 1 : follow;
            anchorFresh[i] = false;
            double targetX = drawX[i] + (projected[0] - drawX[i]) * presence;
            double targetY = drawY[i] + (projected[1] - drawY[i]) * presence;
            double targetR = .03 + (projected[2] - .03) * presence;
            double targetDepth = dots.depth[i] + (projected[3] - dots.depth[i]) * presence;
            double targetSpin = dots.spin[i] + (projected[4] - dots.spin[i]) * presence;
            anchorX[i] += (targetX - anchorX[i]) * f;
            anchorY[i] += (targetY - anchorY[i]) * f;
            anchorR[i] += (targetR - anchorR[i]) * f;
            anchorDepth[i] += (targetDepth - anchorDepth[i]) * f;
            anchorSpin[i] += (targetSpin - anchorSpin[i]) * f;
            if (!inBody) looseDots++;
            if (dots.alpha[i] <= .35 || Math.abs(drawX[i]) > nearW || Math.abs(drawY[i]) > nearH) continue;
            // Three-dimensional illusion. A body is a domed disc tilted toward
            // or away from the viewer about an axis that slowly turns; nearer
            // bodies (depth, as in the Koi forms) are larger, and the near side
            // of each dome is larger and brighter.
            // Tilt and depth scale come from the body's smoothed values, so a
            // whole body, and a seam while two bodies fuse, projects as one piece.
            double depth = anchorDepth[i], radius = Math.max(.03, anchorR[i]);
            double dx = drawX[i] - anchorX[i], dy = drawY[i] - anchorY[i];
            double z = radius * DOME * Math.sqrt(Math.max(0, 1 - (dx * dx + dy * dy) / (radius * radius)));
            double tilt = TILT * (.55 + .45 * Math.sin(tiltClock + 6.283 * depth));
            double axis = axisClock + 1.7 * anchorSpin[i];
            double ca = Math.cos(axis), sa = Math.sin(axis), ct = Math.cos(tilt), st = Math.sin(tilt);
            double u = dx * ca + dy * sa, v = -dx * sa + dy * ca;
            double v2 = v * ct - z * st, z2 = v * st + z * ct;
            double scale = PageDrift.visualScale(depth) / (1 - .22 * z2 / radius);
            double px = (u * ca - v2 * sa) * scale, py = (u * sa + v2 * ca) * scale;
            screenX[i] = (float) (width * .5 + (anchorX[i] + px) * extent + PageDrift.pixelShift(pageCentered, depth, extent));
            screenY[i] = (float) (height * .5 + (anchorY[i] + py) * extent);
            // Apps on the home screen are solid. A dot drawn inside one is moved
            // out to its surface, the move is carried back into the pond (scaled
            // by how large this dot is drawn) and its motion rebounds.
            if (collideHome && home.collide(screenX[i], screenY[i], contact)) {
                double push = contact[2], mx = push * contact[0], my = push * contact[1];
                screenX[i] += (float) mx; screenY[i] += (float) my;
                double back = 1 / (extent * Math.max(.3, scale));
                dots.nudge(i, mx * back, my * back);
                previousX[i] += mx * back; previousY[i] += my * back;
                dots.bounce(i, contact[0], contact[1], HOME_RESTITUTION);
                homeContacts++;
            }
            if (screenX[i] < -pad || screenX[i] > width + pad || screenY[i] < -pad || screenY[i] > height + pad) continue;
            visibleDot[i] = true;
            // Debris far from any body recedes: smaller and dimmer.
            double nearness = (depth + .35 * z2 / radius + .12 * (dots.mass[i] - 1)) * (.35 + .65 * presence)
                + .08 * (((i * 0.7548776662466927) % 1) - .5);
            // Each dot is drawn in its own colour, exactly: there are no palette
            // steps, so colour moves through a body as a gradient. Heat is mixed
            // in as red, as strongly as the vertex is hot.
            double heat = Math.min(1, dots.redness[i]);
            int kind = kindColor(214 + dots.hue[i]);
            dotColor[i] = heat > .004 ? mix(kind, HEAT_COLOR, heat) : kind;
            // Size and brightness follow nearness and the dot's rhythm smoothly: a
            // body in step swells and brightens as one on every beat.
            double beat = .5 + .5 * Math.cos(drawPhase[i]);
            double near = bounded((nearness - .4) / .7, 0, 1);
            dotSize[i] = (float) (stroke * (.8 + .62 * near) * (1 + .18 * beat) * (1 + .3 * heat));
            dotLight[i] = (float) ((.86 + .14 * near) * (.86 + .14 * beat));
            dotGlow[i] = glow ? (float) Math.max(beat * beat * beat * beat, heat * heat) : 0;
            int layer = (int) (near * (LAYERS - 1) + .5);
            dotLayer[i] = layer;
            layerCounts[layer]++;
            drawnDots++;
        }
        looseFraction = n > 0 ? looseDots / (double) n : 0;
        // Bonds between drawn dots. A snapped bond is not drawn while it fades (it
        // would stretch as the dots part), nor is one far longer than a lattice bond.
        double maxBondSquared = maxBond * maxBond, longest = 0;
        int links = 0, lineQuads = 0;
        float lineHalf = Math.max(1f, stroke * .3f) * .5f + 1f;
        for (int e = 0; e < dots.edgeCount; e++) {
            int a = dots.edgeA[e], b = dots.edgeB[e];
            if (!visibleDot[a] || !visibleDot[b] || dots.edgeDying[e]) continue;
            double s = dots.edgeStrength[e];
            if (s < .08) continue;
            float ax = screenX[a], ay = screenY[a], bx = screenX[b], by = screenY[b];
            double lx = bx - ax, ly = by - ay, length2 = lx * lx + ly * ly;
            if (length2 > maxBondSquared || length2 < 1e-4) continue;
            longest = Math.max(longest, length2);
            links++;
            // A thin quad from dot to dot, each end in its dot's colour: every bond is a gradient.
            double inverse = lineHalf / Math.sqrt(length2);
            float nx = (float) (-ly * inverse), ny = (float) (lx * inverse);
            int v = lineQuads * 8;
            lineVerts[v] = ax + nx; lineVerts[v + 1] = ay + ny; lineVerts[v + 2] = ax - nx; lineVerts[v + 3] = ay - ny;
            lineVerts[v + 4] = bx - nx; lineVerts[v + 5] = by - ny; lineVerts[v + 6] = bx + nx; lineVerts[v + 7] = by + ny;
            int alpha = (int) (84 + 100 * bounded((s - .3) / .4, 0, 1));
            int ca = withAlpha(dotColor[a], alpha), cb = withAlpha(dotColor[b], alpha), k = lineQuads * 4;
            lineColors[k] = ca; lineColors[k + 1] = ca; lineColors[k + 2] = cb; lineColors[k + 3] = cb;
            lineQuads++;
        }
        longestBondPx = Math.sqrt(longest);
        // Dots far to near, so nearer dots sit on top, and their glows.
        for (int l = 0, start = 0; l < LAYERS; l++) { layerStart[l] = start; start += layerCounts[l]; }
        for (int i = 0; i < n; i++) if (visibleDot[i]) order[layerStart[dotLayer[i]]++] = i;
        int glowQuads = 0;
        double glowBase = bounded(72 * moodGlow / .8, 24, 100);
        for (int q = 0; q < drawnDots; q++) {
            int i = order[q], v = q * 8, k = q * 4;
            float cx = screenX[i], cy = screenY[i], h = dotSize[i] * .5f / DISC_FILL;
            dotVerts[v] = cx - h; dotVerts[v + 1] = cy - h; dotVerts[v + 2] = cx + h; dotVerts[v + 3] = cy - h;
            dotVerts[v + 4] = cx + h; dotVerts[v + 5] = cy + h; dotVerts[v + 6] = cx - h; dotVerts[v + 7] = cy + h;
            int color = withAlpha(dotColor[i], (int) (255 * dotLight[i]));
            dotColors[k] = color; dotColors[k + 1] = color; dotColors[k + 2] = color; dotColors[k + 3] = color;
            if (dotGlow[i] > .04f) {
                float g = stroke * 4.2f * (1 + .3f * (float) dots.redness[i]);
                int w = glowQuads * 8, m = glowQuads * 4;
                glowVerts[w] = cx - g; glowVerts[w + 1] = cy - g; glowVerts[w + 2] = cx + g; glowVerts[w + 3] = cy - g;
                glowVerts[w + 4] = cx + g; glowVerts[w + 5] = cy + g; glowVerts[w + 6] = cx - g; glowVerts[w + 7] = cy + g;
                int glowColor = withAlpha(dotColor[i], (int) (glowBase * dotGlow[i]));
                glowColors[m] = glowColor; glowColors[m + 1] = glowColor; glowColors[m + 2] = glowColor; glowColors[m + 3] = glowColor;
                glowQuads++;
            }
        }
        lastPointCount = drawnDots; lastLinkCount = links;
        pollEvents(width, height, extent);
        releasePendingFlashes(width, height, extent);
        long canvasStarted = System.nanoTime();
        geometryCostMs = (canvasStarted - geometryStarted) / 1e6;

        canvas.drawColor(BACKGROUND);
        if (pointBudget >= AURA_BUDGET) drawAuras(canvas, width, height, extent);
        // Three draws in all: glows, bonds, dots, every vertex in its own dot's colour.
        if (glowQuads > 0) {
            canvas.drawVertices(Canvas.VertexMode.TRIANGLES, glowQuads * 8, glowVerts, 0, glowTexs, 0,
                glowColors, 0, QUAD_INDICES, 0, glowQuads * 6, glowPaint);
        }
        if (lineQuads > 0) {
            canvas.drawVertices(Canvas.VertexMode.TRIANGLES, lineQuads * 8, lineVerts, 0, lineTexs, 0,
                lineColors, 0, QUAD_INDICES, 0, lineQuads * 6, linePaint);
        }
        if (drawnDots > 0) {
            canvas.drawVertices(Canvas.VertexMode.TRIANGLES, drawnDots * 8, dotVerts, 0, dotTexs, 0,
                dotColors, 0, QUAD_INDICES, 0, drawnDots * 6, dotPaint);
        }
        drawFlashes(canvas, elapsed);
        canvasCostMs = (System.nanoTime() - canvasStarted) / 1e6;
    }

    /**
     * Keeps each dot's drawing state with it when the pond moves a dot to another
     * slot or a new dot arrives, so no other dot jumps or twitches.
     */
    private void followSlots() {
        int changes = dots.slotChanges;
        if (changes == 0) return;
        dots.slotChanges = 0;
        if (changes > dots.slotFrom.length) { Arrays.fill(anchorFresh, true); saveState(); return; }
        for (int k = 0; k < changes; k++) {
            int from = dots.slotFrom[k], to = dots.slotTo[k];
            if (from < 0) {
                previousX[to] = dots.px[to]; previousY[to] = dots.py[to]; previousPhase[to] = dots.phase[to];
                anchorFresh[to] = true;
            } else {
                previousX[to] = previousX[from]; previousY[to] = previousY[from]; previousPhase[to] = previousPhase[from];
                anchorX[to] = anchorX[from]; anchorY[to] = anchorY[from]; anchorR[to] = anchorR[from];
                anchorDepth[to] = anchorDepth[from]; anchorSpin[to] = anchorSpin[from];
                anchorFresh[to] = anchorFresh[from];
            }
        }
    }

    /** Remembers the state before a physics step, for drawing in between. */
    private void saveState() {
        int n = dots.getCount();
        System.arraycopy(dots.px, 0, previousX, 0, n);
        System.arraycopy(dots.py, 0, previousY, 0, n);
        System.arraycopy(dots.phase, 0, previousPhase, 0, n);
        savedVersion = dots.structureVersion;
    }

    /** Adds lineages up to the chosen count in the emptiest water, or lets the newest fade away. */
    private void reconcilePopulation() {
        dots.setTargetCount(requestedCount * DOTS_PER_BODY);
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
            // Colours, and with them kinds, drift faster when the world is busy.
            hueDriftTarget = colorSpeed * (2 + 5 * activity + 3 * energy);
            tendTheWorld(generationSeconds);
        }
        // The drift's rate eases toward the world's pace, so colours shift steadily.
        hueDriftNow += (hueDriftTarget - hueDriftNow) * -Math.expm1(-elapsed / 20);
        dots.setHueDrift(hueDriftNow);
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
            tint.sample(.5 + dots.px[i] / unitsW, .5 + dots.py[i] / unitsH, tintSample);
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
                pattern[i] += (bounded(projection[i], -1, 1) - pattern[i]) * follow;
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
        dots.setPattern(pattern);
    }

    /**
     * Blends the bodies' centres, sizes, depths and spins into smooth fields
     * on a coarse grid, weighted by closeness and size. Far from every body
     * the presence field falls to zero and dots are drawn untransformed.
     */
    private void projectionField() {
        for (int gy = 0; gy < FIELD_ROWS; gy++) {
            for (int gx = 0; gx < FIELD_COLUMNS; gx++) {
                double px = (gx / (double) (FIELD_COLUMNS - 1) - .5) * unitsW, py = (gy / (double) (FIELD_ROWS - 1) - .5) * unitsH;
                double weight = 0, bx = 0, by = 0, br = 0, bd = 0, bs = 0;
                for (int k = 0; k < dots.bodyCount; k++) {
                    if (auraCount[k] == 0) continue;
                    double reach = dots.bodyRadius[k] * 1.7, ox = px - auraX[k], oy = py - auraY[k];
                    double d2 = (ox * ox + oy * oy) / (reach * reach);
                    if (d2 > 4) continue;
                    double w = Math.exp(-2.2 * d2) * auraCount[k];
                    weight += w; bx += w * auraX[k]; by += w * auraY[k]; br += w * reach;
                    bd += w * auraDepth[k]; bs += w * auraSpin[k];
                }
                int c = gy * FIELD_COLUMNS + gx;
                double inverse = weight > 1e-9 ? 1 / weight : 0;
                field[0][c] = weight > 1e-9 ? bx * inverse : px;
                field[1][c] = weight > 1e-9 ? by * inverse : py;
                field[2][c] = weight > 1e-9 ? br * inverse : .03;
                field[3][c] = bd * inverse;
                field[4][c] = bs * inverse;
                field[5][c] = Math.min(1, weight / 6);
            }
        }
    }
    private void sampleProjection(double px, double py) {
        double u = bounded(px / unitsW + .5, 0, 1) * (FIELD_COLUMNS - 1), v = bounded(py / unitsH + .5, 0, 1) * (FIELD_ROWS - 1);
        int ix = Math.min(FIELD_COLUMNS - 2, (int) u), iy = Math.min(FIELD_ROWS - 2, (int) v);
        double tx = u - ix, ty = v - iy;
        int c = iy * FIELD_COLUMNS + ix;
        for (int q = 0; q < 6; q++) {
            double[] f = field[q];
            double top = f[c] + (f[c + 1] - f[c]) * tx, bottom = f[c + FIELD_COLUMNS] + (f[c + FIELD_COLUMNS + 1] - f[c + FIELD_COLUMNS]) * tx;
            projected[q] = top + (bottom - top) * ty;
        }
        // Where the presence is low the blended depth and spin are undefined; keep them neutral.
        if (projected[5] < 1e-6) { projected[3] = .7; projected[4] = 0; }
    }

    /** Each body's centre, from its drawn (interpolated) dots. */
    private void measureBodies() {
        java.util.Arrays.fill(slotOfId, -1);
        for (int slot = 0; slot < dots.bodyCount; slot++) {
            if (dots.bodyId[slot] >= 0 && dots.bodyId[slot] < slotOfId.length) slotOfId[dots.bodyId[slot]] = slot;
            auraX[slot] = auraY[slot] = 0; auraCount[slot] = 0;
            auraDepth[slot] = auraSpin[slot] = 0;
        }
        for (int i = 0, n = dots.getCount(); i < n; i++) {
            int id = dots.bodyOf[i];
            if (id < 0 || id >= slotOfId.length || slotOfId[id] < 0) continue;
            int slot = slotOfId[id];
            auraX[slot] += drawX[i]; auraY[slot] += drawY[i]; auraCount[slot]++;
            auraDepth[slot] += dots.depth[i]; auraSpin[slot] += dots.spin[i];
        }
        for (int slot = 0; slot < dots.bodyCount; slot++) {
            if (auraCount[slot] == 0) continue;
            auraX[slot] /= auraCount[slot]; auraY[slot] /= auraCount[slot];
            auraDepth[slot] /= auraCount[slot]; auraSpin[slot] /= auraCount[slot];
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
            auraPaint.setShader(auraShaders[hueBucket(214 + dots.bodyHue[slot])]);
            auraPaint.setAlpha((int) bounded(110 * moodGlow * Math.min(1, size) * (.45 + .55 * depth), 0, 150));
            canvas.drawCircle(0, 0, 1, auraPaint);
            canvas.restoreToCount(save);
        }
    }

    private void pollEvents(int width, int height, double extent) {
        long serial = dots.getEventSerial();
        for (long s = Math.max(lastEventSerial + 1, serial - 15); s <= serial; s++) {
            if (dots.readEvent(s, eventOut) == 0) continue;
            addFlash(width * .5 + eventOut[0] * extent, height * .5 + eventOut[1] * extent,
                extent * .16, hueBucket(214 + eventOut[2]));
        }
        lastEventSerial = serial;
    }
    private void releasePendingFlashes(int width, int height, double extent) {
        for (int k = 0; k < pendingCount; k++) {
            double hue = finite(pendingHue[k]) ? pendingHue[k]
                : dots.bodyCount > 0 ? 214 + dots.bodyHue[0] : 200;
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
        if (paletteReady) return;
        for (int b = 0; b < COLOR_BUCKETS; b++) {
            double hue = b * 360.0 / COLOR_BUCKETS;
            hsv[0] = (float) hue; hsv[1] = (float) kindSaturation(hue); hsv[2] = 1;
            colors[b] = Color.HSVToColor(hsv);
        }
        paletteReady = true;
    }

    /** Full saturation, so colours pop, except near pure red, which is kept for heat. */
    private static double kindSaturation(double hue) {
        double nearRed = Math.max(0, Math.cos(Math.toRadians(hue)));
        return 1 - .3 * Math.pow(nearRed, 8);
    }

    private int kindColor(double hue) {
        int k = (int) (wrapDegrees(hue) * (KIND_STEPS / 360.0));
        return kindPalette[k >= KIND_STEPS ? k - KIND_STEPS : k];
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
    }

    private static int mix(int from, int to, double amount) {
        int r = (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * amount);
        int g = (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * amount);
        int b = (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * amount);
        return Color.rgb(r, g, b);
    }

    /** Two triangles per quad: corners 0-1-2 and 0-2-3. */
    private static short[] quadIndices(int quads) {
        short[] indices = new short[quads * 6];
        for (int q = 0; q < quads; q++) {
            int v = q * 4, k = q * 6;
            indices[k] = (short) v; indices[k + 1] = (short) (v + 1); indices[k + 2] = (short) (v + 2);
            indices[k + 3] = (short) v; indices[k + 4] = (short) (v + 2); indices[k + 5] = (short) (v + 3);
        }
        return indices;
    }

    private static float[] quadTexs(int quads, int size) {
        float[] texs = new float[quads * 8];
        for (int q = 0; q < quads; q++) {
            int v = q * 8;
            texs[v + 2] = size; texs[v + 4] = size; texs[v + 5] = size; texs[v + 7] = size;
        }
        return texs;
    }

    /** Across a bond, the line's cross-section; along it, constant. */
    private static float[] lineTexs(int quads) {
        float[] texs = new float[quads * 8];
        for (int q = 0; q < quads; q++) {
            int v = q * 8;
            texs[v + 1] = .5f; texs[v + 2] = LINE; texs[v + 3] = .5f;
            texs[v + 4] = LINE; texs[v + 5] = .5f; texs[v + 7] = .5f;
        }
        return texs;
    }

    /** A white disc with a smooth edge, or a soft round halo. */
    private static Bitmap sprite(int size, boolean halo) {
        int[] pixels = new int[size * size];
        double centre = size / 2.0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double r = Math.hypot(x + .5 - centre, y + .5 - centre) / centre;
                double a = halo ? Math.exp(-4.5 * r * r) * bounded((1 - r) / .2, 0, 1)
                    : bounded((DISC_FILL - r) * centre + .5, 0, 1);
                pixels[y * size + x] = Color.argb((int) Math.round(255 * a), 255, 255, 255);
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size);
        bitmap.setHasMipMap(true);
        return bitmap;
    }

    private static Bitmap lineProfile() {
        Bitmap bitmap = Bitmap.createBitmap(LINE, 1, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(new int[] {0x00FFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0x00FFFFFF}, 0, LINE, 0, 0, LINE, 1);
        return bitmap;
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
    /** Longest bond drawn in the last frame, in pixels; bonds are about 32 px at rest on a phone. */
    double getLongestBondPx() { return longestBondPx; }
    /** Fraction of dots outside any body of fourteen or more in the last frame. */
    double getLooseFraction() { return looseFraction; }
    public LifeField getLifeField() { return world; }
    DotLattice getLattice() { return dots; }

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
