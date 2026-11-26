package org.ostadix.koilife;

import java.util.Arrays;

/**
 * The pond reduced to the equations each dot obeys.
 *
 * <p>Every dot carries a position and velocity, a rhythm (phase), a colour
 * (hue), a spin, a swimming heading, a depth and a valence: how many bonds it
 * can hold. Dots within reach of each other bond, more readily when they are
 * in step and alike in colour. A bond is a spring whose rest length breathes
 * with the rhythm of its two dots; it snaps when stretched too far, and more
 * easily when its dots fall out of step or the water around it is restless.
 * Along every bond, rhythm, colour, spin, heading and depth diffuse. Each dot
 * also swims, circles the density it senses in the direction of its spin, is
 * carried by the shared Life current, and feels a weak long-range push from
 * every other dot.
 *
 * <p>Nothing above the dot is scripted. Bodies are the connected groups of
 * bonds; fusing, dividing, synchrony and the sharing of colour are what these
 * equations do. {@link #observe} only watches them happen, to report events.
 *
 * <p>A dot's colour is its kind: bond length, breathing, swimming, mass and
 * part of its valence are read from its hue, so properties change as colours
 * flow.
 *
 * <p>Crowding has limits. Gathering obeys a continuous exclusion principle at
 * each dot's own scale, fading as packing nears critical and pushing apart
 * beyond it. A vertex crowded per bond it holds heats (drawn as red), repels
 * other hot vertices and bonds outward to cool ones, so clusters unfold.
 *
 * <p>The screen is a window onto a larger pond: groups arrive from beyond its
 * edges, dots can leave over them, and the pond keeps about its size.
 *
 * <p>The dots live in four dimensions. Bonds, repulsion and exclusion act on
 * the full position; everything sensed at the scale of crowds (gathering,
 * swirl, spreading, the current, the frame, touch) acts on its shadow on the
 * screen, a projection that slowly turns, so forces between dots come and go
 * as the higher-dimensional arrangement rotates past the screen.
 *
 * <p>Coordinates are centered and measured in units of the screen's shorter
 * side; time is pond seconds. step() performs no allocation once the screen
 * size is known.
 */
public final class DotLattice {
    public static final int MAX_DOTS = 2700, MAX_BONDS = 6, MAX_EDGES = MAX_DOTS * MAX_BONDS / 2;
    /** Smallest connected group reported as a body. */
    public static final int MIN_BODY = 14;
    /** Both sides of a reported fusion or division hold at least this many dots. */
    public static final int EVENT_MIN = 36;
    public static final int MAX_BODIES = 64;
    public static final int EVENT_MERGED = 1, EVENT_DIVIDED = 2;
    private static final int EVENT_CAPACITY = 16, IDS = 128, LINEAGES = 9;
    private static final double BOND_SECONDS = .45, UNBOND_SECONDS = .35, FADE_SECONDS = 2.5;
    private static final double FIELD_CELL = .0625, WIDE_SIGMA = .30, NEAR_SIGMA = .06;
    private static final double COS_45 = Math.cos(Math.toRadians(45)), SIN_45 = Math.sin(Math.toRadians(45));
    private static final double COS_120 = Math.cos(Math.toRadians(120)), SIN_120 = Math.sin(Math.toRadians(120));
    private static final double COS_200 = Math.cos(Math.toRadians(200)), SIN_200 = Math.sin(Math.toRadians(200));
    private static final double COS_220 = Math.cos(Math.toRadians(220)), SIN_220 = Math.sin(Math.toRadians(220));
    private static final double COS_290 = Math.cos(Math.toRadians(290)), SIN_290 = Math.sin(Math.toRadians(290));
    /**
     * Valence recipes, centre to rim, one per lineage: crystal cores with chain
     * rims, honeycomb nets, square nets, lace. Each tenth of a group's dots, in
     * order from its centre, takes the next entry.
     */
    private static final int[][] RECIPES = {
        {6, 6, 6, 6, 3, 3, 2, 2, 2, 2},
        {3, 3, 3, 3, 3, 3, 3, 3, 2, 2},
        {6, 6, 6, 6, 6, 6, 4, 4, 4, 3},
        {4, 3, 2, 2, 2, 2, 2, 2, 2, 2},
        {6, 6, 4, 4, 4, 4, 4, 4, 3, 3},
        {5, 5, 3, 3, 3, 3, 2, 2, 2, 2},
        {6, 6, 6, 4, 4, 4, 2, 2, 2, 2},
        {6, 6, 6, 6, 6, 3, 3, 3, 3, 3},
        {4, 4, 3, 3, 3, 2, 2, 2, 2, 2},
    };
    /** Fixed visual depth per lineage, as in the classic forms; 1 is nearest. */
    private static final double[] DEPTHS = {1.00, 0.46, 0.78, 0.58, 0.40, 0.88, 0.66, 0.94, 0.52};
    /** Exclusion between unbonded dots, in spacings, by valence: open lattices for few bonds. */
    private static final double[] EXCLUSION = {1.6, 1.6, 1.52, 1.38, 1.24, 1.12, 1.0};

    // ---- Governing constants. Lengths in screen-short-side units, times in pond seconds. ----
    /** The lattice spacing in screen-short-side units, for drawing bounds. */
    static final double SPACING_HINT = .025;
    double spacing = SPACING_HINT;
    double coreFraction = .72, breakFraction = 1.85;
    double bondStiffness = 36, bondDamping = 2.5, coreStiffness = 90, exclusionStiffness = 18;
    /** Bonds at one dot repel in angle: chains straighten, three bonds open to 120 degrees, six to 60. */
    double bendStiffness = .12;
    double drag = 6;
    double bondRate = 5, fragility = .35, restlessness = .6;
    double frequency = 3.7, frequencySpread = .12, syncCoupling = 2.53;
    double hueDiffusion = .1, hueMemory = 1 / 40.0;
    double spinDiffusion = .5, spinMemory = 1 / 30.0, depthDiffusion = .4, depthMemory = 1 / 25.0;
    double swirl = .0939, swirlGradient = 2.5, swim = .026, alignment = 2.0, wander = .7;
    /**
     * Quorum sensing: above this long-range density (a normal body's centre
     * reads 0.55-0.75) a dot gathers less and its bonds fray, so a body that
     * grows far past its kind's size loosens and comes apart.
     */
    double quorumLevel = .90, quorumRange = .5, quorumLoosen = .9, quorumFray = 2.5;
    private final double[] crowding = new double[MAX_DOTS];
    /**
     * A continuous exclusion principle. A dot's state is its rhythm; its
     * same-state occupancy n is the density of dots in step with it, sensed at
     * body scale. Each dot's energy -J n + J n^4 / (4 n*^3) rewards company in
     * its own state only up to the limit n*: the pull into step, and toward
     * dots in step, is scaled by 1 - (n / n*)^3, which fades as a same-state
     * crowd nears critical mass and turns into a push out of step and apart
     * beyond it. Many dots may share a place only by occupying different
     * states, so a cluster can grow only by differentiating.
     */
    double occupancyLimit = 1.4, degeneracyPressure = .0204;
    private final double[] exclusion = new double[MAX_DOTS], occupancy = new double[MAX_DOTS];
    /**
     * Rhythm coupling obeys the same law at body scale (the wide field, limit
     * rhythmOccupancyLimit): a body in step stays in step, while an over-full
     * same-state crowd falls out of step. At the dot's own scale it would
     * loosen the tightest cores' rhythm and weaken unison.
     */
    private final double[] rhythmExclusion = new double[MAX_DOTS];
    double rhythmOccupancyLimit = 1.15;
    /** Bodies count as fused only when at least this many grown bonds join them. */
    int fuseLinks = 4;
    /**
     * Swarmalator terms: drift up the near density (gather), more strongly up
     * the density of dots in step (gatherInStep, the J of O'Keeffe, Hong and
     * Strogatz 2017), down the wide density (spread); rhythm is also pulled
     * toward the local mean rhythm (fieldSync).
     */
    double gather = .0025, gatherInStep = .00506, spread = .0072, fieldSync = .783, waterSpeed = .02, jitter = .004;
    /**
     * Rhythm-driven motion. pacemaker: how much faster deeper dots keep time.
     * strokePulse, strokeTwist, strokeOrbit: speeds (units per pond second) of
     * a dot's in-and-out, around, and circling strokes, each following its phase.
     */
    double pacemaker = .0949, strokePulse = .0278, strokeTwist = .00333, strokeOrbit = .00873;
    /** How fast a dot in sparse surroundings swims toward the nearest crowd. */
    double seek = .0465;
    double wallMargin = .045, maxSpeed = .25;
    /**
     * The frame is a window onto a larger pond. Near an edge swimmers turn
     * back and are eased inward (wallZone, below); beyond it a shore pulls
     * them back, strongest just outside and fading to nothing at the far side
     * of the frameMargin, where a dot has left. All of it weakens once the
     * pond is over-full (spill), so a crowded pond spills over its edges.
     * Groups arrive from beyond the edges, arrivalBase groups per pond second
     * until the pond is 4% over its target, plus arrivalRate for each tenth it
     * is short; an arrival that tips the pond over-full makes it spill, so
     * groups keep coming and going. Each slides in a row at a time, at its own
     * speed, width, density, size and colour, with an entry drive that fades
     * over entryFade pond seconds.
     */
    double frameMargin = .18, tether = 2.5, spill = 20, arrivalBase = .06, arrivalRate = .1, entryFade = 12;
    int targetCount;
    final double[] driveX = new double[MAX_DOTS], driveY = new double[MAX_DOTS];
    private double originX = .5, originY = 1, frameHold = 1, entryKeep = 1, departLine = .97 * .18;
    /** Groups that arrived, dots that left, and dots that crossed the screen's edge each way. */
    int arrivals, departures, edgeEntries, edgeExits;
    // The group now sliding in through an edge, a row at a time.
    private boolean emitting;
    private double emitX, emitY, emitDirX, emitDirY, emitSpeed, emitGap, emitClock;
    private double emitHue, emitPhase, emitDepth, emitHeading, emitSpin;
    private int emitRows, emitPerRow, emitRow, emitTotal, emitLineage, emitRecipe;
    /**
     * Slot changes since the renderer last looked: a dot moved from one slot to
     * another (from >= 0), or a new dot took a slot (from < 0). A count above
     * the capacity means some went unrecorded.
     */
    final int[] slotFrom = new int[2 * MAX_DOTS], slotTo = new int[2 * MAX_DOTS];
    int slotChanges;
    /** Within this distance of an edge a dot turns its heading inward and is eased inward. */
    double wallZone = .14, wallTurn = 1.6, wallPush = .02;

    // ---- Dots ----
    final double[] x = new double[MAX_DOTS], y = new double[MAX_DOTS];
    final double[] vx = new double[MAX_DOTS], vy = new double[MAX_DOTS];
    final double[] phase = new double[MAX_DOTS], omegaScale = new double[MAX_DOTS];
    /**
     * Each lineage's natural rhythm wanders (an Ornstein-Uhlenbeck walk around
     * the shared frequency), so which groups can lock together keeps changing.
     */
    final double[] lineageOmega = new double[LINEAGES];
    /** Each lineage's spin at birth: remembered spin keeps leaning back to it, so mixing cannot erase rotation. */
    final double[] lineageSpin = new double[LINEAGES];
    /**
     * Each lineage's own colour, which its dots' memories lean back to at
     * colorHeredity per pond second, so mixing cannot erase colour. Each
     * lineage's colour drifts at its own rate, so which colours meet keeps changing.
     */
    final double[] lineageHue = new double[LINEAGES], lineageHueRate = new double[LINEAGES];
    double colorHeredity = 1 / 90.0;
    double rhythmWander = 15;
    final double[] hue = new double[MAX_DOTS], nativeHue = new double[MAX_DOTS];
    final double[] spin = new double[MAX_DOTS], nativeSpin = new double[MAX_DOTS];
    final double[] depth = new double[MAX_DOTS], nativeDepth = new double[MAX_DOTS];
    final double[] heading = new double[MAX_DOTS], alpha = new double[MAX_DOTS];
    final int[] valence = new int[MAX_DOTS], lineage = new int[MAX_DOTS];
    /**
     * Colour is kind, read on the colour wheel as drawn (display hue = 214 +
     * hue). Each property is a smooth function of the hue, peaking at its own
     * colour: orange-yellow bonds long (open lattices), green breathes hard,
     * blue holds an extra bond and is heavy (crystals), violet swims fast.
     * Heat is separate: pressure heats a vertex, which glows red and is in its
     * most repulsive state. Hot vertices keep other hot vertices away, gather
     * less (drifting outward when fully hot), let hot-hot bonds fray, bond
     * readily with cool vertices, and are light; as they spread out they cool
     * and show their own colour again. Dots of one colour behave alike,
     * a gradient across a body is a gradient of properties, and properties
     * drift as colours do.
     */
    double colorSpacing = .0848, colorBreath = .0901, colorSwim = .37, colorValence = .9, colorMass = .615;
    /**
     * Heat (shown as red): how fast pressure heats a vertex and how fast it
     * cools (per pond second), and how strongly heat repels.
     */
    double reddening = .444, cooling = .25, redRepulsion = .514, redDrift = 1.08, redFray = 2, redLinking = .195;
    /**
     * Pressure, which heats a vertex: how crowded it is, counted as the dots
     * within 1.35 spacings of it (bonded or not), divided by how bound it is,
     * n / (1 + bondEase * k) for k bonds. A well-knit lattice stays calm (each
     * neighbour is a bond); a crushed core, or a loose dot in a crowd, heats.
     */
    double bondEase = .5, pressureStart = 3.6, pressureRange = 1.5;
    /** Mass: heavier vertices accelerate less under every force; bonds add mass, redness removes it. */
    final double[] mass = new double[MAX_DOTS], redness = new double[MAX_DOTS];
    /**
     * Colour dynamics: a dot's hue relaxes toward its remembered hue, shifted
     * by where it is (the hidden Life pattern under it, and how deep inside a
     * body it sits), spreads along bonds, and its memory slowly learns what the
     * dot became, so colour, and with it kind, keeps flowing.
     */
    double colorPattern = 70, colorDepth = 35, hueLearning = 1 / 120.0;
    private double hueDrift;
    final double[] geneSpacing = new double[MAX_DOTS], geneBreath = new double[MAX_DOTS], geneSwim = new double[MAX_DOTS];
    final int[] baseValence = new int[MAX_DOTS];
    private final int[] neighbours = new int[MAX_DOTS];
    /**
     * Hidden dimensions. Each dot also has coordinates z1 and z2, confined to
     * plus or minus hiddenExtent. The screen shows the projection
     * (x cos a + z1 sin a, y cos b + z2 sin b); a and b swing slowly by up to
     * projectionSwing radians. The hidden room opens over openSeconds.
     */
    double hiddenExtent = .0197, hiddenFloor = .01, projectionSwing = .433, openSeconds = 60, hiddenStiffness = 20, hiddenRestore = 2;
    final double[] z1 = new double[MAX_DOTS], z2 = new double[MAX_DOTS];
    final double[] vz1 = new double[MAX_DOTS], vz2 = new double[MAX_DOTS];
    /** Each dot's shadow on the screen, updated after every step. */
    final double[] px = new double[MAX_DOTS], py = new double[MAX_DOTS];
    private final double[] fz1 = new double[MAX_DOTS], fz2 = new double[MAX_DOTS];
    private double angleA, angleB, cosA = 1, sinA, cosB = 1, sinB, extentNow, swingPhaseA, swingPhaseB;
    final boolean[] leaving = new boolean[MAX_DOTS];
    private final double[] fx = new double[MAX_DOTS], fy = new double[MAX_DOTS];
    private final double[] cosP = new double[MAX_DOTS], sinP = new double[MAX_DOTS];
    private final double[] cosH = new double[MAX_DOTS], sinH = new double[MAX_DOTS];
    private final double[] hueX = new double[MAX_DOTS], hueY = new double[MAX_DOTS];
    private final double[] weightSum = new double[MAX_DOTS], syncSum = new double[MAX_DOTS];
    private final double[] hueSum = new double[MAX_DOTS], spinSum = new double[MAX_DOTS];
    private final double[] headSum = new double[MAX_DOTS], depthSum = new double[MAX_DOTS];
    private final double[] restless = new double[MAX_DOTS];
    int count;
    /** Changes whenever dots are added, removed or renumbered; a drawer's saved positions are then stale. */
    int structureVersion;

    // ---- Bonds: a dense edge list plus each dot's list of edge indices ----
    final int[] edgeA = new int[MAX_EDGES], edgeB = new int[MAX_EDGES];
    final double[] edgeStrength = new double[MAX_EDGES];
    final boolean[] edgeDying = new boolean[MAX_EDGES];
    final int[] degree = new int[MAX_DOTS];
    private final int[] adjacent = new int[MAX_DOTS * MAX_BONDS];
    int edgeCount;

    // ---- Neighbour hash ----
    private double cell;
    private int cols, rows;
    private int[] cellStart = new int[1], cellCursor = new int[1];
    private final int[] cellItems = new int[MAX_DOTS], dotCell = new int[MAX_DOTS];

    // ---- Coarse density field for the long-range push and for sensing ----
    private int fieldCols, fieldRows;
    private double[] density = new double[1], scratch = new double[1];
    private double[] wide = new double[1], near = new double[1];
    private double[] cosDeposit = new double[1], sinDeposit = new double[1];
    private double[] nearCos = new double[1], nearSin = new double[1];
    private double[] wideCos = new double[1], wideSin = new double[1];
    private final double[] wideKernel = kernel(WIDE_SIGMA / FIELD_CELL), nearKernel = kernel(NEAR_SIGMA / FIELD_CELL);
    private double referenceDensity = 1;

    // ---- Water: the shared Life current and its restlessness, on a grid over the screen ----
    private double[] waterX, waterY, waterTurbulence, waterPattern;
    private int waterCols, waterRows;
    private double waterStrength = 1;

    // ---- A person: attention, gathering, mood ----
    private double attentionX, attentionY, attentionStrength, gatherX, gatherY, gatherStrength;
    private double liveliness = 1;
    private boolean merging = true, collisions = true;
    private double rewire = 1, climate = 1;

    /** Nanoseconds spent per phase: hash, pairs, bonds, field, integrate, observe. */
    final long[] cost = new long[6];
    long costSteps;
    private double width = 1, height = 2;
    private long random;
    private double time;

    // ---- Observation of bodies (does not feed back into the dots) ----
    private final int[] parent = new int[MAX_DOTS], componentSize = new int[MAX_DOTS];
    private final int[] componentSlot = new int[MAX_DOTS];
    final int[] bodyOf = new int[MAX_DOTS];
    private final int[] bigRoot = new int[MAX_BODIES], bigId = new int[MAX_BODIES];
    private final int[][] votes = new int[MAX_BODIES][IDS];
    private final int[] idOwner = new int[IDS], idBest = new int[IDS];
    private final boolean[] idUsed = new boolean[IDS];
    private final int[] links = new int[IDS * IDS], touchedKeys = new int[512];
    private int linkTouched;
    int bodyCount;
    final int[] bodyId = new int[MAX_BODIES], bodySize = new int[MAX_BODIES];
    final double[] bodyX = new double[MAX_BODIES], bodyY = new double[MAX_BODIES];
    final double[] bodyRadius = new double[MAX_BODIES], bodyHue = new double[MAX_BODIES];
    final double[] bodyDepth = new double[MAX_BODIES], bodyEnergy = new double[MAX_BODIES];
    private final double[] sumX = new double[MAX_BODIES], sumY = new double[MAX_BODIES];
    private final double[] sumR = new double[MAX_BODIES], sumHX = new double[MAX_BODIES], sumHY = new double[MAX_BODIES];
    private final double[] sumD = new double[MAX_BODIES], sumE = new double[MAX_BODIES];
    private final int[] eventType = new int[EVENT_CAPACITY];
    private final double[] eventX = new double[EVENT_CAPACITY], eventY = new double[EVENT_CAPACITY];
    private final double[] eventHue = new double[EVENT_CAPACITY];
    private long eventSerial;
    private int mergeCount, divideCount;
    private double nextObservation;

    public DotLattice(long seed) {
        random = seed ^ 0x6c617474696365L;
        if (random == 0) random = 0x9e3779b97f4a7c15L;
        Arrays.fill(bodyOf, -1);
        swingPhaseA = rand() * 2 * Math.PI; swingPhaseB = rand() * 2 * Math.PI;
    }

    /**
     * Lays out {@code bodies} groups of {@code perBody} dots spread over the
     * screen, each with its own colour, valence, spin, rhythm and depth. The
     * groups start unbonded: their lattices build themselves.
     */
    public void seed(int bodies, int perBody, double widthUnits, double heightUnits) {
        width = Math.max(1, widthUnits); height = Math.max(1, heightUnits);
        bodies = Math.max(1, Math.min(9, bodies));
        perBody = Math.max(MIN_BODY, Math.min(MAX_DOTS / bodies, perBody));
        targetCount = Math.min(MAX_DOTS - 200, bodies * perBody);
        originX = width * .5 + frameMargin; originY = height * .5 + frameMargin;
        count = 0; edgeCount = 0;
        emitting = false;
        structureVersion++;
        Arrays.fill(degree, 0);
        Arrays.fill(bodyOf, -1);
        Arrays.fill(idUsed, false);
        // Groups of random size land at random places, spread out by keeping
        // the best of several random candidates (farthest from those already placed).
        double[] share = new double[bodies], placedX = new double[bodies], placedY = new double[bodies];
        double[] placedR = new double[bodies];
        double shareSum = 0;
        for (int b = 0; b < bodies; b++) { share[b] = .65 + .7 * rand(); shareSum += share[b]; }
        int total = bodies * perBody, given = 0;
        double marginX = Math.min(.16, width * .2), marginY = Math.min(.16, height * .2);
        for (int b = 0; b < bodies; b++) {
            int dots = b == bodies - 1 ? total - given : (int) Math.round(total * share[b] / shareSum);
            dots = Math.max(MIN_BODY, Math.min(MAX_DOTS - count, dots));
            given += dots;
            // Keep the gap between cloud edges as wide as possible, sizes included.
            double radius = spacing * .55 * Math.sqrt(dots) * 1.25;
            double bestX = 0, bestY = 0, best = -1e9;
            for (int k = 0; k < 32; k++) {
                double cx = (rand() - .5) * (width - 2 * marginX), cy = (rand() - .5) * (height - 2 * marginY);
                double gap = 1e9;
                for (int q = 0; q < b; q++) gap = Math.min(gap, Math.hypot(cx - placedX[q], cy - placedY[q]) - placedR[q] - radius);
                if (gap > best) { best = gap; bestX = cx; bestY = cy; }
            }
            placedX[b] = bestX; placedY[b] = bestY; placedR[b] = radius;
            addBody(b, bestX, bestY, dots, 1);
        }
    }

    /** Adds one group of dots; alpha starts at {@code initialAlpha} and fades to one. */
    public void addBody(int identity, double cx, double cy, int dots, double initialAlpha) {
        int[] recipe = RECIPES[Math.floorMod(identity, RECIPES.length)];
        double baseHue = Math.floorMod(identity * 32, 360);
        double baseSpin = (identity % 2 == 0 ? 1 : -1) * (.65 + .35 * rand());
        int lineageIndex = Math.floorMod(identity, LINEAGES);
        lineageOmega[lineageIndex] = frequency * (1 + frequencySpread * (2 * rand() - 1));
        double baseDepth = DEPTHS[Math.floorMod(identity, DEPTHS.length)];
        double basePhase = rand() * Math.PI * 2;
        double heading0 = rand() * Math.PI * 2;
        lineageSpin[lineageIndex] = baseSpin;
        lineageHue[lineageIndex] = baseHue;
        lineageHueRate[lineageIndex] = .7 + .6 * rand();
        // An irregular start: a lumpy, stretched, tilted cloud, with part of
        // the group still loose in a trailing stream, so bodies assemble.
        double aspect = 1 + .8 * rand(), tilt = rand() * Math.PI, ct = Math.cos(tilt), st = Math.sin(tilt);
        double lumpA = .28 * rand(), lumpB = .18 * rand(), lumpPhaseA = rand() * 6.283, lumpPhaseB = rand() * 6.283;
        int loose = (int) (dots * (.08 + .10 * rand()));
        double streamAngle = rand() * 6.283, streamCurl = (rand() - .5) * 3, reach = spacing * .55 * Math.sqrt(dots);
        for (int k = 0; k < dots && count < MAX_DOTS; k++) {
            double ox, oy;
            if (k >= dots - loose) {
                double along = (k - (dots - loose) + 1) / (double) loose;
                double distance = reach * (1.05 + 1.1 * along) + (rand() - .5) * spacing * 2;
                double angle = streamAngle + streamCurl * along + (rand() - .5) * .5;
                ox = distance * Math.cos(angle); oy = distance * Math.sin(angle);
            } else {
                double angle = k * 2.399963229728653 + (rand() - .5) * .6;
                double radius = spacing * .55 * Math.sqrt(k + .5)
                    * (1 + lumpA * Math.sin(3 * angle + lumpPhaseA) + lumpB * Math.sin(5 * angle + lumpPhaseB))
                    + (rand() - .5) * spacing * .4;
                double ex = radius * Math.cos(angle) * Math.sqrt(aspect), ey = radius * Math.sin(angle) / Math.sqrt(aspect);
                ox = ex * ct - ey * st; oy = ex * st + ey * ct;
            }
            int i = count++;
            noteSlot(-1, i);
            // Anything that would start off the screen is folded back inside, not piled on the wall.
            x[i] = fold(cx + ox, width * .5 - wallMargin);
            y[i] = fold(cy + oy, height * .5 - wallMargin);
            vx[i] = vy[i] = 0; driveX[i] = driveY[i] = 0;
            z1[i] = (rand() - .5) * .002; z2[i] = (rand() - .5) * .002; vz1[i] = vz2[i] = 0;
            px[i] = x[i]; py[i] = y[i];
            // Inner dots take the recipe's first (higher) valences, outer dots the last.
            baseValence[i] = valence[i] = recipe[Math.min(recipe.length - 1, k * recipe.length / Math.max(1, dots))];
            geneSpacing[i] = 1; geneBreath[i] = .14; geneSwim[i] = 1; mass[i] = 1; redness[i] = 0;
            nativeHue[i] = hue[i] = baseHue;
            nativeSpin[i] = spin[i] = baseSpin;
            nativeDepth[i] = depth[i] = baseDepth;
            omegaScale[i] = 1 + .02 * (2 * rand() - 1);
            phase[i] = basePhase + .3 * (rand() - .5);
            heading[i] = heading0 + .4 * (rand() - .5);
            alpha[i] = initialAlpha;
            leaving[i] = false;
            lineage[i] = lineageIndex;
            degree[i] = 0;
            bodyOf[i] = -1;
        }
    }

    /** The population the pond keeps through arrivals; departures are free. */
    public void setTargetCount(int dots) { targetCount = Math.max(0, Math.min(MAX_DOTS - 200, dots)); }

    /**
     * A group arrives from beyond a random edge: a few to dozens of vertices of
     * one random colour (and so one kind), as a thin stream or a broad band,
     * tight or sparse, sliding in a row at a time at its own speed.
     */
    private void arrive() {
        double halfW = width * .5, halfH = height * .5;
        emitTotal = 6 + (int) (70 * rand() * rand());
        emitPerRow = Math.min(emitTotal, rand() < .4 ? 1 + (int) (2 * rand()) : 3 + (int) (6 * rand()));
        emitRows = (emitTotal + emitPerRow - 1) / emitPerRow;
        emitGap = spacing * (.75 + 1.2 * rand());
        emitSpeed = .03 + .09 * Math.pow(rand(), 1.5);
        // A random point along the edges, clear of the corners for the band's
        // width, in the middle of the margin beyond the screen.
        double band = .5 * (emitPerRow - 1) * emitGap + spacing, out = .6 * frameMargin;
        double along = rand() * 2 * (width + height), nx, ny;
        if (along < width) { emitX = clear(along - halfW, halfW, band); emitY = -halfH - out; nx = 0; ny = 1; }
        else if ((along -= width) < height) { emitX = halfW + out; emitY = clear(along - halfH, halfH, band); nx = -1; ny = 0; }
        else if ((along -= height) < width) { emitX = clear(halfW - along, halfW, band); emitY = halfH + out; nx = 0; ny = -1; }
        else { along -= width; emitX = -halfW - out; emitY = clear(halfH - along, halfH, band); nx = 1; ny = 0; }
        double turn = (rand() - .5) * 1.2;
        emitDirX = nx * Math.cos(turn) - ny * Math.sin(turn);
        emitDirY = nx * Math.sin(turn) + ny * Math.cos(turn);
        emitLineage = (int) (rand() * LINEAGES) % LINEAGES;
        emitRecipe = (int) (rand() * RECIPES.length) % RECIPES.length;
        emitPhase = rand() * 2 * Math.PI; emitDepth = .4 + .6 * rand();
        emitHeading = Math.atan2(emitDirY, emitDirX);
        // A lineage not yet in the pond gets its own spin, rhythm and colour;
        // a group of a known lineage comes in near that lineage's colour.
        if (lineageSpin[emitLineage] == 0) {
            lineageSpin[emitLineage] = (rand() < .5 ? 1 : -1) * (.65 + .35 * rand());
            lineageHue[emitLineage] = rand() * 360;
            lineageHueRate[emitLineage] = .7 + .6 * rand();
        }
        if (lineageOmega[emitLineage] < .5 * frequency) lineageOmega[emitLineage] = frequency * (1 + frequencySpread * (2 * rand() - 1));
        emitHue = wrapDegrees(lineageHue[emitLineage] + 80 * (rand() - .5));
        emitSpin = lineageSpin[emitLineage];
        emitRow = 0; emitClock = 0; emitting = true;
        arrivals++;
    }

    /** The shore's pull at a signed distance beyond the edge (positive pulls inward). */
    private double shore(double beyond) {
        double b = Math.abs(beyond), fade = Math.max(0, 1 - b / frameMargin);
        return beyond * fade * fade;
    }

    private static double clear(double v, double half, double band) {
        double limit = Math.max(0, half - band - .02);
        return clamp(v, -limit, limit);
    }

    /** The arriving group's next rows, each as the one before has moved in. */
    private void emitGroup(double dt) {
        emitClock += dt;
        double interval = .87 * emitGap / emitSpeed;
        int[] recipe = RECIPES[emitRecipe];
        while (emitting && emitClock >= 0) {
            emitClock -= interval;
            for (int k = 0; k < emitPerRow && count < MAX_DOTS; k++) {
                int n = emitRow * emitPerRow + k;
                if (n >= emitTotal) break;
                // Rows offset by half a gap: the band enters as a triangular lattice.
                double across = (k - .5 * (emitPerRow - 1) + (emitRow % 2 == 0 ? 0 : .5)) * emitGap
                    + (rand() - .5) * .25 * emitGap;
                double ahead = (rand() - .5) * .25 * emitGap;
                double sx = emitX - emitDirY * across + emitDirX * ahead, sy = emitY + emitDirX * across + emitDirY * ahead;
                int i = count++;
                noteSlot(-1, i);
                z1[i] = (rand() - .5) * .002; z2[i] = (rand() - .5) * .002;
                // Placed so that its shadow, not its four-dimensional position, enters there.
                x[i] = (sx - z1[i] * sinA) / cosA; y[i] = (sy - z2[i] * sinB) / cosB;
                px[i] = sx; py[i] = sy;
                vx[i] = vy[i] = vz1[i] = vz2[i] = 0;
                driveX[i] = emitSpeed * emitDirX; driveY[i] = emitSpeed * emitDirY;
                // The leading rows take the recipe's first (higher) valences.
                baseValence[i] = valence[i] = recipe[Math.min(recipe.length - 1, n * recipe.length / Math.max(1, emitTotal))];
                geneSpacing[i] = 1; geneBreath[i] = .14; geneSwim[i] = 1; mass[i] = 1; redness[i] = 0;
                nativeHue[i] = hue[i] = emitHue;
                nativeSpin[i] = spin[i] = emitSpin;
                nativeDepth[i] = depth[i] = emitDepth;
                omegaScale[i] = 1 + .02 * (2 * rand() - 1);
                phase[i] = emitPhase + .3 * (rand() - .5);
                heading[i] = emitHeading + .3 * (rand() - .5);
                alpha[i] = 1; leaving[i] = false; lineage[i] = emitLineage; degree[i] = 0; bodyOf[i] = -1;
            }
            emitRow++;
            if (emitRow >= emitRows || count >= MAX_DOTS) emitting = false;
        }
    }

    private void noteSlot(int from, int to) {
        if (slotChanges < slotFrom.length) { slotFrom[slotChanges] = from; slotTo[slotChanges] = to; }
        if (slotChanges <= slotFrom.length) slotChanges++;
    }

    private static double fold(double v, double limit) {
        if (v > limit) v = 2 * limit - v;
        if (v < -limit) v = -2 * limit - v;
        return clamp(v, -limit, limit);
    }

    /** Dots of this lineage fade out and are removed. */
    public void releaseLineage(int identity) {
        for (int i = 0; i < count; i++) if (lineage[i] == identity) leaving[i] = true;
    }

    public void resize(double widthUnits, double heightUnits) {
        double nw = finite(widthUnits) && widthUnits > 0 ? clamp(widthUnits, 1, 20) : width;
        double nh = finite(heightUnits) && heightUnits > 0 ? clamp(heightUnits, 1, 20) : height;
        if (nw == width && nh == height) return;
        double sx = nw / width, sy = nh / height;
        for (int i = 0; i < count; i++) { x[i] *= sx; y[i] *= sy; px[i] *= sx; py[i] *= sy; }
        width = nw; height = nh;
    }

    /**
     * The water: row-major grids sampled at (column/(cols-1), row/(rows-1)) of
     * the screen. Flow components and restlessness are in [-1, 1] and [0, 1].
     * The arrays are read, not copied, on each step.
     */
    public void setWater(double[] flowX, double[] flowY, double[] turbulence, int columns, int rows, double strength) {
        waterX = flowX; waterY = flowY; waterTurbulence = turbulence;
        waterCols = columns; waterRows = rows; waterStrength = clamp(strength, 0, 2);
    }
    /** The Life world's local pattern (its spectral projection, in [-1, 1]) on the same grid. */
    public void setPattern(double[] projection) { waterPattern = projection; }
    public void setAttention(double px, double py, double strength) {
        if (!finite(px) || !finite(py) || !finite(strength)) { attentionStrength = 0; return; }
        attentionX = px; attentionY = py; attentionStrength = clamp(strength, -1, 1);
    }
    public void setGathering(double px, double py, double strength) {
        if (!finite(px) || !finite(py) || !finite(strength)) { gatherStrength = 0; return; }
        gatherX = px; gatherY = py; gatherStrength = clamp(strength, 0, 1);
    }
    /** 0.35 sleepy to 1.2 lively: scales swimming, swirling and rhythm. */
    public void setLiveliness(double value) { liveliness = clamp(value, .35, 1.2); }
    public void setMerging(boolean value) { merging = value; }
    public boolean isMerging() { return merging; }
    public void setCollisions(boolean value) { collisions = value; }
    /** How quickly lattices rebuild: multiplies bond formation and spontaneous breaking. */
    public void setRewire(double value) { rewire = clamp(value, .1, 4); }
    /** Multiplies the long-range push; below one, bodies crowd and fuse more. */
    public void setClimate(double value) { climate = clamp(value, 0, 3); }
    /** Every dot's colour, and so its kind, drifts by this many degrees per pond second. */
    public void setHueDrift(double degreesPerPondSecond) { hueDrift = finite(degreesPerPondSecond) ? degreesPerPondSecond : 0; }

    // ------------------------------------------------------------------ step

    public void step(double dt) {
        if (!(dt > 0) || !finite(dt) || count == 0) return;
        dt = Math.min(dt, .12);
        time += dt;
        double tau = rhythmWander / rewire, sigma = frequency * frequencySpread;
        for (int l = 0; l < LINEAGES; l++) {
            lineageHue[l] = wrapDegrees(lineageHue[l] + dt * hueDrift * lineageHueRate[l]);
            lineageOmega[l] += -(lineageOmega[l] - frequency) * dt / tau
                + sigma * Math.sqrt(2 * dt / tau) * (rand() - .5) * 3.4641016151377544;
        }
        // The hidden room opens gradually; the projection swings slowly through it.
        double opening = clamp(time / Math.max(1e-6, openSeconds), 0, 1);
        extentNow = hiddenExtent * opening;
        angleA = projectionSwing * opening * Math.sin(2 * Math.PI * time / 61 + swingPhaseA);
        angleB = projectionSwing * opening * Math.sin(2 * Math.PI * time / 89 + swingPhaseB);
        cosA = Math.cos(angleA); sinA = Math.sin(angleA); cosB = Math.cos(angleB); sinB = Math.sin(angleB);
        originX = width * .5 + frameMargin; originY = height * .5 + frameMargin;
        // The pond keeps its level. Groups come in from beyond the frame, more often
        // the shorter it is; over-full, its pull back weakens and it spills over the edges.
        double fill = targetCount > 0 ? count / (double) targetCount : 1;
        // The edges hold fully up to the target, less as it fills past it, and past
        // 5% over they drain: an over-full pond overflows at its rim.
        frameHold = 1 - clamp((fill - 1) * spill, 0, 2);
        departLine = frameMargin * (.97 - .4 * Math.min(1, 1 - frameHold));
        entryKeep = Math.exp(-dt / entryFade);
        if (targetCount > 0 && !emitting && fill < 1.04 && count < MAX_DOTS - 100) {
            double rate = arrivalBase + arrivalRate * clamp((1 - fill) / .1, 0, 3);
            if (rand() < rate * dt) arrive();
        }
        if (emitting) emitGroup(dt);
        for (int i = 0; i < count; i++) {
            cosP[i] = Math.cos(phase[i]); sinP[i] = Math.sin(phase[i]);
            cosH[i] = Math.cos(heading[i]); sinH[i] = Math.sin(heading[i]);
            double h = Math.toRadians(hue[i]);
            hueX[i] = Math.cos(h); hueY[i] = Math.sin(h);
            fx[i] = fy[i] = fz1[i] = fz2[i] = 0;
            weightSum[i] = syncSum[i] = hueSum[i] = spinSum[i] = headSum[i] = depthSum[i] = 0;
            neighbours[i] = 0;
            px[i] = x[i] * cosA + z1[i] * sinA; py[i] = y[i] * cosB + z2[i] * sinB;
            // Colour is kind: properties read from the hue as drawn.
            double shown = Math.toRadians(hue[i] + 214), cx = Math.cos(shown), cy = Math.sin(shown);
            geneSpacing[i] = 1 + colorSpacing * (cx * COS_45 + cy * SIN_45);
            geneBreath[i] = Math.max(.02, .14 + colorBreath * (cx * COS_120 + cy * SIN_120));
            geneSwim[i] = Math.max(.2, 1 + colorSwim * (cx * COS_290 + cy * SIN_290));
            mass[i] = clamp((1 + colorMass * (cx * COS_220 + cy * SIN_220)) * (1 + .12 * (degree[i] - 2)) * (1 - .45 * redness[i]), .35, 3);
            int kind = (int) Math.round(colorValence * (cx * COS_200 + cy * SIN_200));
            valence[i] = Math.max(2, Math.min(6, baseValence[i] + kind));
            if (degree[i] > valence[i]) {
                int alive = 0;
                for (int k = 0; k < degree[i]; k++) if (!edgeDying[adjacent[i * MAX_BONDS + k]]) alive++;
                for (; alive > valence[i]; alive--) releaseLongestBond(i);
            }
            restless[i] = turbulenceAt(px[i], py[i]);
            crowding[i] = fieldCols > 0 ? clamp((crowdingAt(px[i], py[i]) - quorumLevel) / quorumRange, 0, 1) : 0;
            if (fieldCols > 0) {
                double gx = (px[i] + originX) / FIELD_CELL + .5, gy = (py[i] + originY) / FIELD_CELL + .5;
                // Same-state occupancy at the dot's own scale: the density of dots in step
                // with it within a few spacings, against a relaxed lattice's.
                occupancy[i] = Math.max(0, (cosP[i] * value(nearCos, gx, gy) + sinP[i] * value(nearSin, gx, gy)) / referenceDensity);
                double wide = Math.max(0, (cosP[i] * value(wideCos, gx, gy) + sinP[i] * value(wideSin, gx, gy)) / referenceDensity) / rhythmOccupancyLimit;
                rhythmExclusion[i] = clamp(1 - wide * wide * wide, -1.5, 1);
            } else {
                occupancy[i] = 0;
                rhythmExclusion[i] = 1;
            }
            double load = occupancy[i] / occupancyLimit;
            exclusion[i] = clamp(1 - load * load * load, -1.5, 1);
        }
        long t0 = System.nanoTime();
        hash();
        long t1 = System.nanoTime();
        pairs(dt);
        long t2 = System.nanoTime();
        bonds(dt);
        bend();
        long t3 = System.nanoTime();
        field();
        long t4 = System.nanoTime();
        integrate(dt);
        removeDeparted();
        long t5 = System.nanoTime();
        if (time >= nextObservation) { observe(); nextObservation = time + .2; }
        long t6 = System.nanoTime();
        cost[0] += t1 - t0; cost[1] += t2 - t1; cost[2] += t3 - t2; cost[3] += t4 - t3; cost[4] += t5 - t4; cost[5] += t6 - t5; costSteps++;
    }

    private void hash() {
        cell = (EXCLUSION[0] + .2) * spacing * 1.001;
        int c = (int) Math.ceil((width + 2 * frameMargin) / cell) + 1, r = (int) Math.ceil((height + 2 * frameMargin) / cell) + 1;
        if (c * r + 1 > cellStart.length) { cellStart = new int[c * r + 1]; cellCursor = new int[c * r + 1]; }
        cols = c; rows = r;
        Arrays.fill(cellStart, 0, c * r + 1, 0);
        for (int i = 0; i < count; i++) {
            int cx = clampInt((int) ((px[i] + originX) / cell), 0, c - 1);
            int cy = clampInt((int) ((py[i] + originY) / cell), 0, r - 1);
            int k = cy * c + cx;
            dotCell[i] = k;
            cellStart[k + 1]++;
        }
        for (int k = 0; k < c * r; k++) cellStart[k + 1] += cellStart[k];
        System.arraycopy(cellStart, 0, cellCursor, 0, c * r);
        for (int i = 0; i < count; i++) cellItems[cellCursor[dotCell[i]]++] = i;
    }

    /**
     * Short-range forces between every nearby pair, and the birth of new
     * bonds. Each pair of neighbouring cells is visited once.
     */
    private void pairs(double dt) {
        for (int cy = 0; cy < rows; cy++) {
            for (int cx = 0; cx < cols; cx++) {
                int k = cy * cols + cx;
                for (int s = cellStart[k], e = cellStart[k + 1]; s < e; s++) {
                    int i = cellItems[s];
                    for (int t = s + 1; t < e; t++) interact(i, cellItems[t], dt);
                    if (cx + 1 < cols) neighbours(i, k + 1, dt);
                    if (cy + 1 < rows) {
                        if (cx > 0) neighbours(i, k + cols - 1, dt);
                        neighbours(i, k + cols, dt);
                        if (cx + 1 < cols) neighbours(i, k + cols + 1, dt);
                    }
                }
            }
        }
    }

    private void neighbours(int i, int k, double dt) {
        for (int s = cellStart[k], e = cellStart[k + 1]; s < e; s++) interact(i, cellItems[s], dt);
    }

    private void interact(int i, int j, double dt) {
        double keepOut = .5 * (EXCLUSION[valence[i]] + EXCLUSION[valence[j]]) * spacing
            * (1 + redRepulsion * Math.sqrt(redness[i] * redness[j]));
        double reach = keepOut + .2 * spacing;
        double dx = x[j] - x[i], dy = y[j] - y[i], du = z1[j] - z1[i], dw = z2[j] - z2[i];
        double d2 = dx * dx + dy * dy + du * du + dw * dw;
        if (d2 >= reach * reach) return;
        double d = Math.sqrt(d2), nx, ny, nu, nw;
        if (d > 1e-9) { nx = dx / d; ny = dy / d; nu = du / d; nw = dw / d; } else { nx = 1; ny = 0; nu = 0; nw = 0; d = 1e-9; }
        boolean bonded = bondedTo(i, j);
        // Without collisions, dots of different lineages pass through each other.
        boolean apart = !collisions && lineage[i] != lineage[j];
        double f = 0, core = coreFraction * spacing;
        if (d < 1.35 * spacing) { neighbours[i]++; neighbours[j]++; }
        if (d < core && !apart) f -= coreStiffness * (core - d);
        if (!bonded) {
            if (d < keepOut && !apart) f -= exclusionStiffness * (keepOut - d);
            if (degree[i] < valence[i] && degree[j] < valence[j]
                    && !leaving[i] && !leaving[j] && edgeCount < MAX_EDGES
                    && (merging || lineage[i] == lineage[j])) {
                // Without merging, dots bond only to their own kind, so bodies never fuse.
                // The propensity to bond: in step, and alike in colour.
                double inStep = .5 + .5 * (cosP[i] * cosP[j] + sinP[i] * sinP[j]);
                double alike = .5 + .5 * (hueX[i] * hueX[j] + hueY[i] * hueY[j]);
                double affinity = inStep * inStep * (.3 + .7 * alike);
                // Red vertices do not bond to each other, and link readily to those that are not red.
                affinity *= (1 - redness[i] * redness[j]) * (1 + redLinking * Math.abs(redness[i] - redness[j]));
                // Quorum also stops a crowded body taking in other kinds, so
                // small bodies fuse freely but none keeps absorbing.
                if (lineage[i] != lineage[j]) affinity *= (1 - crowding[i]) * (1 - crowding[j]);
                if (rand() < bondRate * rewire * affinity * dt) addEdge(i, j);
            }
        }
        if (f != 0) {
            fx[i] += f * nx; fy[i] += f * ny; fz1[i] += f * nu; fz2[i] += f * nw;
            fx[j] -= f * nx; fy[j] -= f * ny; fz1[j] -= f * nu; fz2[j] -= f * nw;
        }
    }

    /** Bond springs and everything that travels along bonds; bonds also snap here. */
    private void bonds(double dt) {
        for (int e = edgeCount - 1; e >= 0; e--) {
            int a = edgeA[e], b = edgeB[e];
            double dx = x[b] - x[a], dy = y[b] - y[a], du = z1[b] - z1[a], dw = z2[b] - z2[a];
            double d = Math.sqrt(dx * dx + dy * dy + du * du + dw * dw);
            if (edgeDying[e]) {
                edgeStrength[e] -= dt / UNBOND_SECONDS;
                if (edgeStrength[e] <= 0) { removeEdge(e); continue; }
            } else {
                edgeStrength[e] = Math.min(1, edgeStrength[e] + dt / BOND_SECONDS);
                double outOfStep = .5 - .5 * (cosP[a] * cosP[b] + sinP[a] * sinP[b]);
                double unrest = .5 * (restless[a] + restless[b]);
                double crowd = .5 * (crowding[a] + crowding[b]);
                double hazard = fragility * rewire * (.05 + outOfStep * outOfStep + restlessness * unrest * unrest
                    + quorumFray * crowd + redFray * redness[a] * redness[b]);
                if (d > breakFraction * spacing || leaving[a] || leaving[b] || rand() < hazard * dt) {
                    edgeDying[e] = true;
                }
            }
            double s = edgeStrength[e];
            if (d > 1e-9) {
                double nx = dx / d, ny = dy / d, nu = du / d, nw = dw / d;
                double breath = .5 * (geneBreath[a] + geneBreath[b]);
                double rest = spacing * .5 * (geneSpacing[a] + geneSpacing[b]) * (1 + breath * .5 * (cosP[a] + cosP[b]));
                double closing = (vx[b] - vx[a]) * nx + (vy[b] - vy[a]) * ny + (vz1[b] - vz1[a]) * nu + (vz2[b] - vz2[a]) * nw;
                double f = s * (bondStiffness * (d - rest) + bondDamping * closing);
                fx[a] += f * nx; fy[a] += f * ny; fz1[a] += f * nu; fz2[a] += f * nw;
                fx[b] -= f * nx; fy[b] -= f * ny; fz1[b] -= f * nu; fz2[b] -= f * nw;
            }
            weightSum[a] += s; weightSum[b] += s;
            double sync = sinP[b] * cosP[a] - cosP[b] * sinP[a];
            syncSum[a] += s * sync; syncSum[b] -= s * sync;
            double hueArc = arc(hue[b] - hue[a]);
            hueSum[a] += s * hueArc; hueSum[b] -= s * hueArc;
            double spinDiff = spin[b] - spin[a];
            spinSum[a] += s * spinDiff; spinSum[b] -= s * spinDiff;
            double turn = sinH[b] * cosH[a] - cosH[b] * sinH[a];
            headSum[a] += s * turn; headSum[b] -= s * turn;
            double depthDiff = depth[b] - depth[a];
            depthSum[a] += s * depthDiff; depthSum[b] -= s * depthDiff;
        }
    }

    /**
     * Bonds meeting at a dot repel in angle (energy k cos of each angle), so
     * they spread evenly around it: a lattice holds its shape instead of
     * folding, and chains stay smooth.
     */
    private void bend() {
        double k = bendStiffness;
        for (int i = 0; i < count; i++) {
            int n = degree[i], base = i * MAX_BONDS;
            if (n < 2) continue;
            for (int p = 0; p < n; p++) {
                int ep = adjacent[base + p];
                int j = edgeA[ep] == i ? edgeB[ep] : edgeA[ep];
                double ax = x[j] - x[i], ay = y[j] - y[i], au = z1[j] - z1[i], aw = z2[j] - z2[i];
                double la = Math.sqrt(ax * ax + ay * ay + au * au + aw * aw);
                if (la < 1e-9) continue;
                ax /= la; ay /= la; au /= la; aw /= la;
                for (int q = p + 1; q < n; q++) {
                    int eq = adjacent[base + q];
                    int m = edgeA[eq] == i ? edgeB[eq] : edgeA[eq];
                    double bx = x[m] - x[i], by = y[m] - y[i], bu = z1[m] - z1[i], bw = z2[m] - z2[i];
                    double lb = Math.sqrt(bx * bx + by * by + bu * bu + bw * bw);
                    if (lb < 1e-9) continue;
                    bx /= lb; by /= lb; bu /= lb; bw /= lb;
                    double c = k * edgeStrength[ep] * edgeStrength[eq] * spacing;
                    double cos = ax * bx + ay * by + au * bu + aw * bw;
                    double fjx = -c * (bx - cos * ax) / la, fjy = -c * (by - cos * ay) / la;
                    double fju = -c * (bu - cos * au) / la, fjw = -c * (bw - cos * aw) / la;
                    double fmx = -c * (ax - cos * bx) / lb, fmy = -c * (ay - cos * by) / lb;
                    double fmu = -c * (au - cos * bu) / lb, fmw = -c * (aw - cos * bw) / lb;
                    fx[j] += fjx; fy[j] += fjy; fz1[j] += fju; fz2[j] += fjw;
                    fx[m] += fmx; fy[m] += fmy; fz1[m] += fmu; fz2[m] += fmw;
                    fx[i] -= fjx + fmx; fy[i] -= fjy + fmy; fz1[i] -= fju + fmu; fz2[i] -= fjw + fmw;
                }
            }
        }
    }

    /** Deposits dots on a coarse grid; wide and near blurs give the long-range push and sensing. */
    private void field() {
        int c = (int) Math.ceil((width + 2 * frameMargin) / FIELD_CELL) + 2, r = (int) Math.ceil((height + 2 * frameMargin) / FIELD_CELL) + 2;
        if (c * r > density.length) {
            density = new double[c * r]; scratch = new double[c * r];
            wide = new double[c * r]; near = new double[c * r];
            cosDeposit = new double[c * r]; sinDeposit = new double[c * r];
            nearCos = new double[c * r]; nearSin = new double[c * r];
            wideCos = new double[c * r]; wideSin = new double[c * r];
        }
        fieldCols = c; fieldRows = r;
        Arrays.fill(density, 0, c * r, 0);
        Arrays.fill(cosDeposit, 0, c * r, 0);
        Arrays.fill(sinDeposit, 0, c * r, 0);
        for (int i = 0; i < count; i++) {
            double gx = (px[i] + originX) / FIELD_CELL + .5, gy = (py[i] + originY) / FIELD_CELL + .5;
            int ix = clampInt((int) gx, 0, c - 2), iy = clampInt((int) gy, 0, r - 2);
            double tx = clamp(gx - ix, 0, 1), ty = clamp(gy - iy, 0, 1), m = alpha[i];
            int k = iy * c + ix;
            double w00 = m * (1 - tx) * (1 - ty), w10 = m * tx * (1 - ty), w01 = m * (1 - tx) * ty, w11 = m * tx * ty;
            density[k] += w00; density[k + 1] += w10; density[k + c] += w01; density[k + c + 1] += w11;
            double co = cosP[i], si = sinP[i];
            cosDeposit[k] += w00 * co; cosDeposit[k + 1] += w10 * co; cosDeposit[k + c] += w01 * co; cosDeposit[k + c + 1] += w11 * co;
            sinDeposit[k] += w00 * si; sinDeposit[k + 1] += w10 * si; sinDeposit[k + c] += w01 * si; sinDeposit[k + c + 1] += w11 * si;
        }
        blur(density, wide, wideKernel, c, r);
        blur(density, near, nearKernel, c, r);
        blur(cosDeposit, nearCos, nearKernel, c, r);
        blur(cosDeposit, wideCos, wideKernel, c, r);
        blur(sinDeposit, wideSin, wideKernel, c, r);
        blur(sinDeposit, nearSin, nearKernel, c, r);
        // Normalise so that the interior of a close-packed lattice reads about one.
        double cellArea = FIELD_CELL * FIELD_CELL;
        referenceDensity = cellArea / (.866 * spacing * spacing * 1.3);
    }

    private void integrate(double dt) {
        double halfW = width * .5, halfH = height * .5;
        double mood = .55 + .45 * liveliness;
        double inverseReference = 1 / referenceDensity;
        for (int i = 0; i < count; i++) {
            double w = Math.max(1e-9, weightSum[i]), norm = 1 / Math.max(1, w);
            // Rhythm: each dot's own frequency, pulled into step along its bonds.
            // Deeper in a body the rhythm runs slightly faster, so bodies keep
            // phase waves travelling from core to rim (a pacemaker).
            double deep = fieldCols > 0 ? clamp(value(near, (px[i] + originX) / FIELD_CELL + .5, (py[i] + originY) / FIELD_CELL + .5) / referenceDensity, 0, 1.5) : .8;
            phase[i] += dt * (lineageOmega[lineage[i]] * omegaScale[i] * mood * (1 + pacemaker * (deep - .8))
                + rhythmExclusion[i] * syncCoupling * syncSum[i] * norm);
            if (phase[i] > 1e4) phase[i] -= 2 * Math.PI * Math.floor(phase[i] / (2 * Math.PI));
            spin[i] += dt * (spinDiffusion * spinSum[i] * norm + spinMemory * (nativeSpin[i] - spin[i]));
            // The spin a dot remembers follows what its body does, and its lineage.
            nativeSpin[i] += dt * (hueLearning * (spin[i] - nativeSpin[i]) + (lineageSpin[lineage[i]] - nativeSpin[i]) / 40);
            depth[i] += dt * (depthDiffusion * depthSum[i] * norm + depthMemory * (nativeDepth[i] - depth[i]));
            heading[i] += dt * alignment * headSum[i] * norm + wander * Math.sqrt(dt) * (rand() - .5) * 2;
            // Near or beyond an edge: the inward direction, weighted by closeness
            // (one at wallZone inside the edge, and more beyond it).
            double inX = 0, inY = 0;
            double sx = px[i], sy = py[i];
            double nearLeft = 1 - (sx + halfW) / wallZone, nearRight = 1 - (halfW - sx) / wallZone;
            double nearTop = 1 - (sy + halfH) / wallZone, nearBottom = 1 - (halfH - sy) / wallZone;
            if (nearLeft > 0) inX += nearLeft; if (nearRight > 0) inX -= nearRight;
            if (nearTop > 0) inY += nearTop; if (nearBottom > 0) inY -= nearBottom;
            if (inX != 0 || inY != 0) {
                // Swimmers turn back from an edge, unless something carries them away.
                double toward = Math.atan2(inY, inX), closeness = Math.min(1, Math.sqrt(inX * inX + inY * inY));
                // An over-full pond stops turning swimmers back: it lets go.
                if (Math.cos(heading[i] - toward) < .3) {
                    heading[i] += dt * wallTurn * Math.max(0, frameHold) * closeness * Math.sin(toward - heading[i]);
                }
            }

            // Sensing the coarse density: gradients of the near and wide fields.
            double gx = (sx + originX) / FIELD_CELL + .5, gy = (sy + originY) / FIELD_CELL + .5;
            double nearX = gradient(near, gx, gy, true) * inverseReference;
            double nearY = gradient(near, gx, gy, false) * inverseReference;
            double wideX = gradient(wide, gx, gy, true) * inverseReference;
            double wideY = gradient(wide, gx, gy, false) * inverseReference;
            double nearMagnitude = Math.sqrt(nearX * nearX + nearY * nearY);
            // The density of dots in step with this one: cos(theta_j - theta_i) weighted.
            double inStepX = (cosP[i] * gradient(nearCos, gx, gy, true) + sinP[i] * gradient(nearSin, gx, gy, true)) * inverseReference;
            double inStepY = (cosP[i] * gradient(nearCos, gx, gy, false) + sinP[i] * gradient(nearSin, gx, gy, false)) * inverseReference;
            // The local mean rhythm pulls this dot's rhythm (Kuramoto through the field).
            double localCos = value(nearCos, gx, gy), localSin = value(nearSin, gx, gy);
            double localDensity = Math.max(.2 * referenceDensity, value(near, gx, gy));
            phase[i] += dt * rhythmExclusion[i] * fieldSync * (cosP[i] * localSin - sinP[i] * localCos) / localDensity;
            // Colour flows: toward the remembered hue, shifted by the hidden pattern
            // below and by how deep in a body the dot sits; along bonds; with the drift;
            // and the memory slowly learns what the dot became.
            double depthInBody = clamp(value(near, gx, gy) / referenceDensity, 0, 1.5);
            double favouredHue = nativeHue[i] + colorPattern * patternAt(sx, sy) + colorDepth * (depthInBody - .8);
            // Pressure: crowding per bond. It heats the vertex, which glows red and
            // turns repulsive; heat cools as the pressure eases.
            double bonds = 0;
            for (int k = 0; k < degree[i]; k++) { int e = adjacent[i * MAX_BONDS + k]; if (!edgeDying[e]) bonds += edgeStrength[e]; }
            double load = neighbours[i] / (1 + bondEase * bonds);
            double pressure = clamp((load - pressureStart) / pressureRange, 0, 1);
            redness[i] += dt * (pressure > redness[i] ? reddening * (pressure - redness[i]) : cooling * (pressure - redness[i]));
            redness[i] = clamp(redness[i], 0, 1);
            hue[i] = wrapDegrees(hue[i] + dt * (hueDiffusion * hueSum[i] * norm + hueMemory * 2.5 * arc(favouredHue - hue[i]) + hueDrift));
            nativeHue[i] = wrapDegrees(nativeHue[i] + dt * ((1 - pressure) * hueLearning * arc(hue[i] - nativeHue[i])
                + colorHeredity * arc(lineageHue[lineage[i]] - nativeHue[i]) + hueDrift));

            // The velocity this dot would settle to in still conditions.
            double targetX = 0, targetY = 0;
            // Swirl: circle the density it senses, in the direction of its spin.
            if (nearMagnitude > 1e-9) {
                double sense = Math.min(1, nearMagnitude / swirlGradient) / nearMagnitude;
                targetX += -nearY * sense * swirl * spin[i] * mood;
                targetY += nearX * sense * swirl * spin[i] * mood;
            }
            // Gather toward the density it senses, and harder toward dots in step.
            // Dots that hold more bonds gather harder, so dense cores sort inward
            // and lacy, low-valence dots make the rim (differential adhesion).
            // Dots with unused valence seek company harder, so loose dots rejoin.
            double hunger = 1 + 1.2 * (1 - Math.min(1, degree[i] / (double) valence[i]));
            double cling = (.4 + .6 * valence[i] / 6.0) * (1 - quorumLoosen * crowding[i]) * hunger * (1 - redDrift * redness[i]);
            // Plain gathering obeys the same exclusion for all dots, in step or not:
            // it fades as packing nears critical and pushes apart beyond it.
            double packing = value(near, gx, gy) / referenceDensity;
            double brake = clamp(1 - packing * packing * packing / (occupancyLimit * occupancyLimit * occupancyLimit), -1.5, 1);
            targetX += cling * (gather * brake * nearX + exclusion[i] * gatherInStep * inStepX);
            targetY += cling * (gather * brake * nearY + exclusion[i] * gatherInStep * inStepY);
            // Degeneracy pressure: past critical mass, a same-state crowd pushes its dots apart.
            if (exclusion[i] < 0) {
                double ox = (cosP[i] * gradient(wideCos, gx, gy, true) + sinP[i] * gradient(wideSin, gx, gy, true)) * inverseReference;
                double oy = (cosP[i] * gradient(wideCos, gx, gy, false) + sinP[i] * gradient(wideSin, gx, gy, false)) * inverseReference;
                targetX += degeneracyPressure * exclusion[i] * ox;
                targetY += degeneracyPressure * exclusion[i] * oy;
            }
            // Eased in from the edges (less when the pond is over-full), and an
            // arriving group's fading drive.
            targetX += wallPush * inX * frameHold + driveX[i];
            targetY += wallPush * inY * frameHold + driveY[i];
            driveX[i] *= entryKeep; driveY[i] *= entryKeep;
            // Strokes: each dot moves to its own rhythm, relative to the density it
            // feels: in and out (pulse), around (twist), and in a small circle along
            // its heading (orbit). In step, a whole body moves as one; where phases
            // lag, the strokes travel through it as waves.
            if (nearMagnitude > 1e-9) {
                double inward = Math.min(1, nearMagnitude / swirlGradient) / nearMagnitude;
                double rx = nearX * inward, ry = nearY * inward;
                double stroke = cosP[i] * strokePulse, turn = sinP[i] * strokeTwist * spin[i];
                targetX += stroke * rx - turn * ry;
                targetY += stroke * ry + turn * rx;
            }
            double orbitAngle = phase[i] + heading[i];
            targetX += strokeOrbit * Math.cos(orbitAngle);
            targetY += strokeOrbit * Math.sin(orbitAngle);
            // Swim along its heading.
            targetX += swim * geneSwim[i] * mood * cosH[i];
            targetY += swim * geneSwim[i] * mood * sinH[i];
            // The shared Life current.
            if (waterX != null) {
                sampleWater(sx, sy);
                targetX += waterSpeed * waterStrength * waterSampleX;
                targetY += waterSpeed * waterStrength * waterSampleY;
            }
            // A person.
            if (attentionStrength != 0) {
                double dx = attentionX - sx, dy = attentionY - sy;
                double d = Math.sqrt(dx * dx + dy * dy) + 1e-9;
                double reach = d / .85, fall = 1 / (1 + reach * reach);
                if (attentionStrength > 0) {
                    double approach = clamp((d - .12) / .12, -.6, 1);
                    targetX += attentionStrength * fall * (.10 * approach * dx - .03 * dy) / d;
                    targetY += attentionStrength * fall * (.10 * approach * dy + .03 * dx) / d;
                } else {
                    targetX += attentionStrength * fall * .08 * dx / d;
                    targetY += attentionStrength * fall * .08 * dy / d;
                }
            }
            if (gatherStrength > 0) {
                targetX += gatherStrength * .05 * (gatherX - sx);
                targetY += gatherStrength * .05 * (gatherY - sy);
            }
            // A dot whose own neighbourhood is sparse (a shed fragment) climbs
            // toward the crowd it senses at long range instead of being pushed
            // from it; a dot inside a body keeps the bodies apart.
            double sparse = clamp(1 - value(near, gx, gy) / (.5 * referenceDensity), 0, 1);
            double wideMagnitude = Math.sqrt(wideX * wideX + wideY * wideY);
            if (sparse > 0 && wideMagnitude > 1e-9) {
                double toward = Math.min(1, wideMagnitude / .3) / wideMagnitude;
                targetX += sparse * seek * wideX * toward;
                targetY += sparse * seek * wideY * toward;
            }
            // Screen-space accelerations: the long-range push and the walls act on the shadow.
            double ax = -(1 - sparse) * spread * climate * wideX * drag;
            double ay = -(1 - sparse) * spread * climate * wideY * drag;
            // Beyond the frame, a shore: a pull back that is strongest just outside
            // and fades to nothing at the margin's far side, so a dot carried far
            // enough out drifts away.
            ax += tether * frameHold * shore(sx < -halfW ? -halfW - sx : sx > halfW ? halfW - sx : 0);
            ay += tether * frameHold * shore(sy < -halfH ? -halfH - sy : sy > halfH ? halfH - sy : 0);
            // A little thermal motion, more where the Life water is restless.
            double kick = jitter * (1 + 2 * restless[i]) * Math.sqrt(dt) * drag;
            ax += kick * (rand() - .5) * 2; ay += kick * (rand() - .5) * 2;
            // Lift everything sensed on the screen back into four dimensions, along
            // the directions the projection currently mixes; keep the hidden room.
            double hx = fx[i] + cosA * ax, hu = fz1[i] + sinA * ax - hiddenStiffness * outside(z1[i], extentNow) - hiddenRestore * z1[i];
            double hy = fy[i] + cosB * ay, hw = fz2[i] + sinB * ay - hiddenStiffness * outside(z2[i], extentNow) - hiddenRestore * z2[i];
            double tx = cosA * targetX, tu = sinA * targetX, ty = cosB * targetY, tw = sinB * targetY;
            // Implicit drag toward the target velocity: stable at any step. Mass
            // divides every force, including the dot's own drive.
            double inverseMass = 1 / mass[i], damp = 1 / (1 + dt * drag * inverseMass);
            vx[i] = (vx[i] + dt * inverseMass * (hx + drag * tx)) * damp;
            vy[i] = (vy[i] + dt * inverseMass * (hy + drag * ty)) * damp;
            vz1[i] = (vz1[i] + dt * inverseMass * (hu + drag * tu)) * damp;
            vz2[i] = (vz2[i] + dt * inverseMass * (hw + drag * tw)) * damp;
            double speed = Math.sqrt(vx[i] * vx[i] + vy[i] * vy[i] + vz1[i] * vz1[i] + vz2[i] * vz2[i]);
            if (speed > maxSpeed) { double c = maxSpeed / speed; vx[i] *= c; vy[i] *= c; vz1[i] *= c; vz2[i] *= c; }
            // Room enough for every shadow to reach the margin's far side, at any projection.
            double roomW = (halfW + frameMargin) / Math.max(.5, cosA) + extentNow;
            double roomH = (halfH + frameMargin) / Math.max(.5, cosB) + extentNow, roomHidden = 2 * extentNow + hiddenFloor;
            x[i] = clamp(x[i] + vx[i] * dt, -roomW, roomW);
            y[i] = clamp(y[i] + vy[i] * dt, -roomH, roomH);
            z1[i] = clamp(z1[i] + vz1[i] * dt, -roomHidden, roomHidden);
            z2[i] = clamp(z2[i] + vz2[i] * dt, -roomHidden, roomHidden);
            alpha[i] = clamp(alpha[i] + (leaving[i] ? -dt : dt) / FADE_SECONDS, 0, 1);
            if (!finite(x[i]) || !finite(y[i]) || !finite(z1[i]) || !finite(z2[i])
                    || !finite(vx[i]) || !finite(vy[i]) || !finite(vz1[i]) || !finite(vz2[i])) {
                x[i] = 0; y[i] = 0; z1[i] = 0; z2[i] = 0; vx[i] = 0; vy[i] = 0; vz1[i] = 0; vz2[i] = 0;
            }
            boolean wasIn = Math.abs(px[i]) <= halfW && Math.abs(py[i]) <= halfH;
            px[i] = clamp(x[i] * cosA + z1[i] * sinA, -halfW - frameMargin, halfW + frameMargin);
            py[i] = clamp(y[i] * cosB + z2[i] * sinB, -halfH - frameMargin, halfH + frameMargin);
            if (wasIn != (Math.abs(px[i]) <= halfW && Math.abs(py[i]) <= halfH)) { if (wasIn) edgeExits++; else edgeEntries++; }
            // A dot that reaches the far side of the margin has left the pond (an
            // over-full pond's far side comes nearer).
            if (!leaving[i] && (Math.abs(px[i]) >= halfW + departLine || Math.abs(py[i]) >= halfH + departLine)) {
                leaving[i] = true; alpha[i] = 0; departures++;
            }
        }
    }

    /**
     * Moves dot i so that its shadow on the screen moves by (dx, dy), along the
     * directions the projection currently shows. For a finger dragging dots.
     */
    void nudge(int i, double dx, double dy) {
        x[i] += dx * cosA; z1[i] += dx * sinA; y[i] += dy * cosB; z2[i] += dy * sinB;
        px[i] = clamp(px[i] + dx, -width * .5 - frameMargin, width * .5 + frameMargin);
        py[i] = clamp(py[i] + dy, -height * .5 - frameMargin, height * .5 + frameMargin);
    }
    /** Turns dot i's on-screen velocity part of the way toward (vxScreen, vyScreen), in units per pond second. */
    void steer(int i, double vxScreen, double vyScreen, double amount) {
        double k = clamp(amount, 0, 1);
        double currentX = vx[i] * cosA + vz1[i] * sinA, currentY = vy[i] * cosB + vz2[i] * sinB;
        double ex = (clamp(vxScreen, -maxSpeed, maxSpeed) - currentX) * k, ey = (clamp(vyScreen, -maxSpeed, maxSpeed) - currentY) * k;
        vx[i] += ex * cosA; vz1[i] += ex * sinA; vy[i] += ey * cosB; vz2[i] += ey * sinB;
    }

    /** How far a hidden coordinate sits outside the open room. */
    private static double outside(double v, double extent) {
        return v > extent ? v - extent : v < -extent ? v + extent : 0;
    }

    private void releaseLongestBond(int i) {
        int longest = -1; double best = -1;
        for (int k = 0; k < degree[i]; k++) {
            int e = adjacent[i * MAX_BONDS + k];
            if (edgeDying[e]) continue;
            int j = edgeA[e] == i ? edgeB[e] : edgeA[e];
            double d = (x[j] - x[i]) * (x[j] - x[i]) + (y[j] - y[i]) * (y[j] - y[i])
                + (z1[j] - z1[i]) * (z1[j] - z1[i]) + (z2[j] - z2[i]) * (z2[j] - z2[i]);
            if (d > best) { best = d; longest = e; }
        }
        if (longest >= 0) edgeDying[longest] = true;
    }

    private void removeDeparted() {
        for (int i = count - 1; i >= 0; i--) {
            if (!leaving[i] || alpha[i] > 0) continue;
            for (int k = degree[i] - 1; k >= 0; k--) removeEdge(adjacent[i * MAX_BONDS + k]);
            moveDot(count - 1, i);
            count--;
        }
    }

    /** Moves dot {@code from} into slot {@code to}, rewriting its bonds. */
    private void moveDot(int from, int to) {
        if (from == to) return;
        noteSlot(from, to);
        x[to] = x[from]; y[to] = y[from]; vx[to] = vx[from]; vy[to] = vy[from];
        driveX[to] = driveX[from]; driveY[to] = driveY[from];
        phase[to] = phase[from]; omegaScale[to] = omegaScale[from];
        hue[to] = hue[from]; nativeHue[to] = nativeHue[from];
        spin[to] = spin[from]; nativeSpin[to] = nativeSpin[from];
        depth[to] = depth[from]; nativeDepth[to] = nativeDepth[from];
        heading[to] = heading[from]; alpha[to] = alpha[from];
        valence[to] = valence[from]; lineage[to] = lineage[from]; leaving[to] = leaving[from];
        baseValence[to] = baseValence[from];
        geneSpacing[to] = geneSpacing[from]; geneBreath[to] = geneBreath[from]; geneSwim[to] = geneSwim[from];
        mass[to] = mass[from]; redness[to] = redness[from];
        z1[to] = z1[from]; z2[to] = z2[from]; vz1[to] = vz1[from]; vz2[to] = vz2[from];
        px[to] = px[from]; py[to] = py[from];
        bodyOf[to] = bodyOf[from];
        degree[to] = degree[from];
        for (int k = 0; k < degree[from]; k++) {
            int e = adjacent[from * MAX_BONDS + k];
            adjacent[to * MAX_BONDS + k] = e;
            if (edgeA[e] == from) edgeA[e] = to; else edgeB[e] = to;
        }
        degree[from] = 0;
    }

    // ------------------------------------------------------------------ bonds

    private boolean bondedTo(int i, int j) {
        int a = degree[i] <= degree[j] ? i : j, other = a == i ? j : i;
        for (int k = 0; k < degree[a]; k++) {
            int e = adjacent[a * MAX_BONDS + k];
            if (edgeA[e] == other || edgeB[e] == other) return true;
        }
        return false;
    }

    private void addEdge(int i, int j) {
        int e = edgeCount++;
        edgeA[e] = i; edgeB[e] = j; edgeStrength[e] = 0; edgeDying[e] = false;
        adjacent[i * MAX_BONDS + degree[i]++] = e;
        adjacent[j * MAX_BONDS + degree[j]++] = e;
    }

    private void removeEdge(int e) {
        detach(edgeA[e], e);
        detach(edgeB[e], e);
        int last = --edgeCount;
        if (e != last) {
            edgeA[e] = edgeA[last]; edgeB[e] = edgeB[last];
            edgeStrength[e] = edgeStrength[last]; edgeDying[e] = edgeDying[last];
            retarget(edgeA[e], last, e);
            retarget(edgeB[e], last, e);
        }
    }

    private void detach(int dot, int e) {
        int base = dot * MAX_BONDS;
        for (int k = 0; k < degree[dot]; k++) {
            if (adjacent[base + k] == e) {
                adjacent[base + k] = adjacent[base + --degree[dot]];
                return;
            }
        }
    }

    private void retarget(int dot, int from, int to) {
        int base = dot * MAX_BONDS;
        for (int k = 0; k < degree[dot]; k++) if (adjacent[base + k] == from) { adjacent[base + k] = to; return; }
    }

    // ------------------------------------------------------------------ observation

    /**
     * Finds the connected groups of grown bonds and follows them from one
     * observation to the next. A group that holds most of two earlier bodies
     * is a fusion; an earlier body now spread over two groups has divided.
     * Watching changes nothing about the dots.
     */
    void observe() {
        for (int i = 0; i < count; i++) { parent[i] = i; componentSize[i] = 0; }
        // Count grown bonds between different earlier bodies; a few threads do not fuse them.
        linkTouched = 0;
        for (int e = 0; e < edgeCount; e++) {
            if (edgeDying[e] || edgeStrength[e] < .5) continue;
            int ba = bodyOf[edgeA[e]], bb = bodyOf[edgeB[e]];
            if (ba < 0 || bb < 0 || ba == bb) continue;
            int key = Math.min(ba, bb) * IDS + Math.max(ba, bb);
            if (links[key]++ == 0 && linkTouched < touchedKeys.length) touchedKeys[linkTouched++] = key;
        }
        for (int e = 0; e < edgeCount; e++) {
            if (edgeDying[e] || edgeStrength[e] < .5) continue;
            int ba = bodyOf[edgeA[e]], bb = bodyOf[edgeB[e]];
            if (ba >= 0 && bb >= 0 && ba != bb && links[Math.min(ba, bb) * IDS + Math.max(ba, bb)] < fuseLinks) continue;
            int ra = find(edgeA[e]), rb = find(edgeB[e]);
            if (ra != rb) { if (ra < rb) parent[rb] = ra; else parent[ra] = rb; }
        }
        for (int t = 0; t < linkTouched; t++) links[touchedKeys[t]] = 0;
        for (int i = 0; i < count; i++) { parent[i] = find(i); componentSize[parent[i]]++; }
        int big = 0;
        for (int i = 0; i < count; i++) {
            componentSlot[i] = -1;
            if (parent[i] == i && componentSize[i] >= MIN_BODY && big < MAX_BODIES) {
                componentSlot[i] = big;
                bigRoot[big] = i;
                Arrays.fill(votes[big], 0);
                big++;
            }
        }
        for (int i = 0; i < count; i++) {
            int slot = componentSlot[parent[i]];
            if (slot >= 0 && bodyOf[i] >= 0) votes[slot][bodyOf[i]]++;
        }
        // Each earlier body belongs to the group holding most of it.
        Arrays.fill(idOwner, -1); Arrays.fill(idBest, 0);
        for (int slot = 0; slot < big; slot++) {
            for (int id = 0; id < IDS; id++) {
                if (votes[slot][id] > idBest[id]) { idBest[id] = votes[slot][id]; idOwner[id] = slot; }
            }
        }
        boolean[] claimed = idUsed;
        Arrays.fill(claimed, false);
        for (int slot = 0; slot < big; slot++) {
            int top = -1, topVotes = 0;
            for (int id = 0; id < IDS; id++) {
                if (idOwner[id] == slot && votes[slot][id] > topVotes) { top = id; topVotes = votes[slot][id]; }
            }
            bigId[slot] = top;
            if (top >= 0) claimed[top] = true;
        }
        for (int slot = 0; slot < big; slot++) {
            int root = bigRoot[slot];
            if (bigId[slot] < 0) {
                // A group that grew from dots of a body owned elsewhere has budded off it.
                int from = -1, fromVotes = 0;
                for (int id = 0; id < IDS; id++) if (votes[slot][id] > fromVotes) { from = id; fromVotes = votes[slot][id]; }
                int id = freeId(claimed);
                bigId[slot] = id;
                if (id >= 0) claimed[id] = true;
                if (from >= 0 && fromVotes >= EVENT_MIN && bodySizeOf(slot) >= EVENT_MIN) {
                    divideCount++;
                    record(EVENT_DIVIDED, root);
                }
            } else {
                // Any other earlier body mostly inside this group has fused into it.
                for (int id = 0; id < IDS; id++) {
                    if (id == bigId[slot] || idOwner[id] != slot || votes[slot][id] < EVENT_MIN
                            || votes[slot][bigId[slot]] < EVENT_MIN) continue;
                    mergeCount++;
                    record(EVENT_MERGED, root);
                }
            }
        }
        for (int i = 0; i < count; i++) {
            int slot = componentSlot[parent[i]];
            bodyOf[i] = slot >= 0 ? bigId[slot] : -1;
        }
        // Body summaries for drawing.
        bodyCount = big;
        for (int slot = 0; slot < big; slot++) {
            sumX[slot] = sumY[slot] = sumR[slot] = sumHX[slot] = sumHY[slot] = sumD[slot] = sumE[slot] = 0;
            bodySize[slot] = 0;
            bodyId[slot] = bigId[slot];
        }
        for (int i = 0; i < count; i++) {
            int slot = componentSlot[parent[i]];
            if (slot < 0) continue;
            sumX[slot] += px[i]; sumY[slot] += py[i]; bodySize[slot]++;
            double h = Math.toRadians(hue[i]);
            sumHX[slot] += Math.cos(h); sumHY[slot] += Math.sin(h);
            sumD[slot] += depth[i];
            sumE[slot] += vx[i] * vx[i] + vy[i] * vy[i];
        }
        for (int slot = 0; slot < big; slot++) {
            double n = Math.max(1, bodySize[slot]);
            bodyX[slot] = sumX[slot] / n; bodyY[slot] = sumY[slot] / n;
            bodyHue[slot] = wrapDegrees(Math.toDegrees(Math.atan2(sumHY[slot], sumHX[slot])));
            bodyDepth[slot] = sumD[slot] / n;
            bodyEnergy[slot] = Math.sqrt(sumE[slot] / n);
        }
        for (int i = 0; i < count; i++) {
            int slot = componentSlot[parent[i]];
            if (slot < 0) continue;
            double dx = px[i] - bodyX[slot], dy = py[i] - bodyY[slot];
            sumR[slot] += dx * dx + dy * dy;
        }
        for (int slot = 0; slot < big; slot++) bodyRadius[slot] = Math.sqrt(sumR[slot] / Math.max(1, bodySize[slot]));
    }

    private int bodySizeOf(int slot) { return componentSize[bigRoot[slot]]; }

    private static int freeId(boolean[] claimed) {
        for (int id = 0; id < IDS; id++) if (!claimed[id]) return id;
        return -1;
    }

    private int find(int i) {
        while (parent[i] != i) { parent[i] = parent[parent[i]]; i = parent[i]; }
        return i;
    }

    private void record(int type, int root) {
        double sx = 0, sy = 0, hx = 0, hy = 0; int n = 0;
        for (int i = 0; i < count; i++) {
            if (parent[i] != root) continue;
            sx += px[i]; sy += py[i]; n++;
            double h = Math.toRadians(hue[i]); hx += Math.cos(h); hy += Math.sin(h);
        }
        eventSerial++;
        int slot = (int) (eventSerial % EVENT_CAPACITY);
        eventType[slot] = type;
        eventX[slot] = n > 0 ? sx / n : 0; eventY[slot] = n > 0 ? sy / n : 0;
        eventHue[slot] = wrapDegrees(Math.toDegrees(Math.atan2(hy, hx)));
    }

    public long getEventSerial() { return eventSerial; }
    /** out[0..1] centred position, out[2] hue. Returns the type, or zero if overwritten. */
    public int readEvent(long serial, double[] out) {
        if (serial <= 0 || serial > eventSerial || eventSerial - serial >= EVENT_CAPACITY || out == null || out.length < 3) return 0;
        int slot = (int) (serial % EVENT_CAPACITY);
        out[0] = eventX[slot]; out[1] = eventY[slot]; out[2] = eventHue[slot];
        return eventType[slot];
    }
    /** The long-range (wide) density a dot at (px, py) senses, in lattice-interior units. */
    double crowdingAt(double px, double py) {
        double gx = (px + originX) / FIELD_CELL + .5, gy = (py + originY) / FIELD_CELL + .5;
        return value(wide, gx, gy) / referenceDensity;
    }
    double extentNowForTest() { return extentNow; }
    public int getMergeCount() { return mergeCount; }
    public int getDivideCount() { return divideCount; }
    public int getCount() { return count; }
    public int getEdgeCount() { return edgeCount; }
    public int getBodyCount() { return bodyCount; }
    public double getTime() { return time; }
    public double getWidth() { return width; }
    public double getHeight() { return height; }

    /** Fraction of visible dots holding at least one grown bond. */
    public double bondedFraction() {
        int bonded = 0, visible = 0;
        for (int i = 0; i < count; i++) {
            if (alpha[i] < .5) continue;
            visible++;
            for (int k = 0; k < degree[i]; k++) {
                int e = adjacent[i * MAX_BONDS + k];
                if (!edgeDying[e] && edgeStrength[e] >= .5) { bonded++; break; }
            }
        }
        return visible == 0 ? 1 : bonded / (double) visible;
    }

    // ------------------------------------------------------------------ helpers

    private double waterSampleX, waterSampleY;
    private void sampleWater(double px, double py) {
        double u = clamp(px / width + .5, 0, 1) * (waterCols - 1);
        double v = clamp(py / height + .5, 0, 1) * (waterRows - 1);
        int ix = Math.min(waterCols - 2, (int) u), iy = Math.min(waterRows - 2, (int) v);
        double tx = u - ix, ty = v - iy;
        int k = iy * waterCols + ix;
        waterSampleX = bilinear(waterX, k, waterCols, tx, ty);
        waterSampleY = bilinear(waterY, k, waterCols, tx, ty);
    }
    private double patternAt(double px, double py) {
        if (waterPattern == null) return 0;
        double u = clamp(px / width + .5, 0, 1) * (waterCols - 1);
        double v = clamp(py / height + .5, 0, 1) * (waterRows - 1);
        int ix = Math.min(waterCols - 2, (int) u), iy = Math.min(waterRows - 2, (int) v);
        return clamp(bilinear(waterPattern, iy * waterCols + ix, waterCols, u - ix, v - iy), -1, 1);
    }
    private double turbulenceAt(double px, double py) {
        if (waterTurbulence == null) return 0;
        double u = clamp(px / width + .5, 0, 1) * (waterCols - 1);
        double v = clamp(py / height + .5, 0, 1) * (waterRows - 1);
        int ix = Math.min(waterCols - 2, (int) u), iy = Math.min(waterRows - 2, (int) v);
        return clamp(bilinear(waterTurbulence, iy * waterCols + ix, waterCols, u - ix, v - iy), 0, 1);
    }
    private static double bilinear(double[] g, int k, int stride, double tx, double ty) {
        double top = g[k] + (g[k + 1] - g[k]) * tx;
        double bottom = g[k + stride] + (g[k + stride + 1] - g[k + stride]) * tx;
        return top + (bottom - top) * ty;
    }
    /** Central-difference gradient of a field at fractional cell (gx, gy), per unit length. */
    private double gradient(double[] f, double gx, double gy, boolean alongX) {
        int c = fieldCols, r = fieldRows;
        int ix = clampInt((int) gx, 1, c - 3), iy = clampInt((int) gy, 1, r - 3);
        double tx = clamp(gx - ix, 0, 1), ty = clamp(gy - iy, 0, 1);
        double g00, g10, g01, g11;
        if (alongX) {
            g00 = f[iy * c + ix + 1] - f[iy * c + ix - 1];
            g10 = f[iy * c + ix + 2] - f[iy * c + ix];
            g01 = f[(iy + 1) * c + ix + 1] - f[(iy + 1) * c + ix - 1];
            g11 = f[(iy + 1) * c + ix + 2] - f[(iy + 1) * c + ix];
        } else {
            g00 = f[(iy + 1) * c + ix] - f[(iy - 1) * c + ix];
            g10 = f[(iy + 1) * c + ix + 1] - f[(iy - 1) * c + ix + 1];
            g01 = f[(iy + 2) * c + ix] - f[iy * c + ix];
            g11 = f[(iy + 2) * c + ix + 1] - f[iy * c + ix + 1];
        }
        double top = g00 + (g10 - g00) * tx, bottom = g01 + (g11 - g01) * tx;
        return (top + (bottom - top) * ty) / (2 * FIELD_CELL);
    }
    /**
     * Separable Gaussian blur. The screen edges mirror the field, so a wall
     * pushes back like an image of the dots near it instead of collecting them.
     */
    private void blur(double[] in, double[] out, double[] k, int c, int r) {
        int radius = k.length - 1;
        for (int row = 0; row < r; row++) {
            int base = row * c;
            for (int col = 0; col < c; col++) {
                double sum = in[base + col] * k[0];
                for (int t = 1; t <= radius; t++) {
                    sum += (in[base + mirror(col - t, c)] + in[base + mirror(col + t, c)]) * k[t];
                }
                scratch[base + col] = sum;
            }
        }
        for (int row = 0; row < r; row++) {
            for (int col = 0; col < c; col++) {
                double sum = scratch[row * c + col] * k[0];
                for (int t = 1; t <= radius; t++) {
                    sum += (scratch[mirror(row - t, r) * c + col] + scratch[mirror(row + t, r) * c + col]) * k[t];
                }
                out[row * c + col] = sum;
            }
        }
    }
    /** Reflects a cell index about the walls, which lie half a cell inside the first and last cells. */
    private static int mirror(int k, int n) {
        if (k < 0) k = 1 - k;
        if (k > n - 1) k = 2 * n - 3 - k;
        return k < 0 ? 0 : k > n - 1 ? n - 1 : k;
    }
    private double value(double[] f, double gx, double gy) {
        int c = fieldCols;
        int ix = clampInt((int) gx, 0, c - 2), iy = clampInt((int) gy, 0, fieldRows - 2);
        double tx = clamp(gx - ix, 0, 1), ty = clamp(gy - iy, 0, 1);
        return bilinear(f, iy * c + ix, c, tx, ty);
    }
    private static double[] kernel(double sigmaCells) {
        int radius = Math.max(1, (int) Math.ceil(sigmaCells * 2.5));
        double[] k = new double[radius + 1];
        double sum = 0;
        for (int t = 0; t <= radius; t++) { k[t] = Math.exp(-t * t / (2 * sigmaCells * sigmaCells)); sum += t == 0 ? k[t] : 2 * k[t]; }
        for (int t = 0; t <= radius; t++) k[t] /= sum;
        return k;
    }
    private double rand() {
        random ^= random >>> 12; random ^= random << 25; random ^= random >>> 27;
        return ((random * 0x2545F4914F6CDD1DL) >>> 11) * 0x1.0p-53;
    }
    static double wrapDegrees(double degrees) {
        if (!finite(degrees)) return 0;
        double w = degrees % 360;
        if (w < 0) w += 360;
        // A tiny negative remainder rounds to exactly 360; keep the range half-open.
        return w >= 360 ? 0 : w;
    }
    static double arc(double degrees) {
        double w = wrapDegrees(degrees);
        return w > 180 ? w - 360 : w;
    }
    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
    private static int clampInt(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    private static boolean finite(double v) { return !Double.isNaN(v) && !Double.isInfinite(v); }
}
