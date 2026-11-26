package org.ostadix.koilife;

import java.util.HashSet;

/**
 * The dot lattice: its bookkeeping stays exact, its motion stays finite,
 * bounded and calm, and the behaviours it is meant to produce on its own
 * (a lattice that builds itself, fusion, division, a size limit) appear
 * without any rule that names them.
 */
public final class DotLatticeTest {
    private static int checks;
    private static final double W = 1.0, H = 2856.0 / 1280.0, DT = .025;
    private static final int PER_BODY = 270;

    public static void main(String[] args) {
        bookkeepingStaysExact();
        deterministicForASeed();
        latticeBuildsItself();
        fusesAndDividesOnItsOwn();
        crowdedGiantComesApart();
        mergingOffKeepsBodiesApart();
        releasedLineageFadesAndLeaves();
        paceStaysCalm();
        attentionDrawsDotsIn();
        rulesEvolveWithTheSystem();
        theFrameIsOpen();
        heatMarksCrowding();
        stepAllocatesNothing();
        System.out.println("DotLatticeTest passed: " + checks + " checks");
    }

    /**
     * A lattice in a real Life world, as the wallpaper runs it at the person's
     * settings: Drift 0.11 with the lattice's 1.5x pace, the current smoothed
     * as the renderer smooths it, the Life pattern, colour drift, and crowding
     * that varies.
     */
    private static final class Pond {
        static final double PACE = .11 * 1.5;
        final LifeField world = new LifeField(54, 120, 821166217176535313L);
        final DotLattice dots = new DotLattice(821166217176535313L);
        final double[] fx = new double[13 * 19], fy = new double[13 * 19], proj = new double[13 * 19];
        final double[] mx = new double[13 * 19], my = new double[13 * 19], mp = new double[13 * 19];
        final double[] turbulence = new double[13 * 19], local = new double[2];
        long lastGeneration = -1;
        Pond(int bodies, int perBody) {
            world.setRate(1);
            dots.seed(bodies, perBody, W, H);
            dots.setWater(mx, my, turbulence, 13, 19, 1.0);
            dots.setPattern(mp);
            dots.setHueDrift(.41 * 4);
        }
        void run(double pondSeconds) {
            for (double t = 0; t < pondSeconds - 1e-9; t += DT) {
                world.advance(DT / PACE);
                if (world.getGeneration() != lastGeneration) {
                    lastGeneration = world.getGeneration();
                    world.sampleGrid(13, 19, fx, fy, proj);
                    for (int gy = 0; gy < 19; gy++) for (int gx = 0; gx < 13; gx++) {
                        world.sampleLocal(gx / 12.0, gy / 18.0, 5, local);
                        turbulence[gy * 13 + gx] = Math.min(1, local[1] * 4.5);
                    }
                }
                double follow = 1 - Math.exp(-(DT / PACE) * .10 / .4);
                for (int i = 0; i < fx.length; i++) {
                    mx[i] += (fx[i] - mx[i]) * follow; my[i] += (fy[i] - my[i]) * follow; mp[i] += (proj[i] - mp[i]) * follow;
                }
                dots.setClimate(.75 + .35 * Math.sin(dots.getTime() * 2 * Math.PI / 150));
                dots.step(DT);
            }
        }
    }

    private static void bookkeepingStaysExact() {
        Pond pond = new Pond(7, PER_BODY);
        for (int round = 0; round < 24; round++) {
            pond.run(2.5);
            DotLattice d = pond.dots;
            int[] seen = new int[d.count], seenAlive = new int[d.count];
            HashSet<Long> pairs = new HashSet<>();
            for (int e = 0; e < d.edgeCount; e++) {
                int a = d.edgeA[e], b = d.edgeB[e];
                require(a != b && a >= 0 && b >= 0 && a < d.count && b < d.count, "Edge ends must be distinct live dots");
                require(pairs.add(Math.min(a, b) * 100000L + Math.max(a, b)), "No pair is bonded twice");
                require(d.edgeStrength[e] >= 0 && d.edgeStrength[e] <= 1, "Bond strength stays in [0, 1]");
                seen[a]++; seen[b]++;
                if (!d.edgeDying[e]) { seenAlive[a]++; seenAlive[b]++; }
            }
            for (int i = 0; i < d.count; i++) {
                require(seen[i] == d.degree[i], "Each dot's bond list matches the edges");
                // Bonds released when a colour lowers a dot's valence fade out first; active ones never exceed it.
                require(seenAlive[i] <= d.valence[i], "No dot holds more active bonds than its valence");
                require(finite(d.x[i]) && finite(d.y[i]) && finite(d.z1[i]) && finite(d.z2[i])
                    && finite(d.vx[i]) && finite(d.vy[i]) && finite(d.vz1[i]) && finite(d.vz2[i]), "Motion stays finite");
                require(Math.abs(d.px[i]) <= W / 2 + d.frameMargin + 1e-9 && Math.abs(d.py[i]) <= H / 2 + d.frameMargin + 1e-9,
                    "Shadows stay within the frame and its margin");
                require(Math.abs(d.z1[i]) <= 2 * d.hiddenExtent + d.hiddenFloor + 1e-4 && Math.abs(d.z2[i]) <= 2 * d.hiddenExtent + d.hiddenFloor + 1e-4,
                    "Hidden coordinates stay in their room");
                double speed4 = Math.sqrt(d.vx[i] * d.vx[i] + d.vy[i] * d.vy[i] + d.vz1[i] * d.vz1[i] + d.vz2[i] * d.vz2[i]);
                require(speed4 <= d.maxSpeed + 1e-9, "Speed limit holds in four dimensions");
                require(d.mass[i] >= .35 - 1e-9 && d.mass[i] <= 3 + 1e-9 && d.redness[i] >= 0 && d.redness[i] <= 1,
                    "Mass and redness stay in range");
                require(d.hue[i] >= 0 && d.hue[i] < 360, "Hue stays wrapped");
            }
        }
    }

    private static void deterministicForASeed() {
        Pond a = new Pond(5, 200), b = new Pond(5, 200);
        a.run(15); b.run(15);
        require(a.dots.count == b.dots.count && a.dots.edgeCount == b.dots.edgeCount, "Same seed, same lattice");
        for (int i = 0; i < a.dots.count; i++) {
            require(a.dots.x[i] == b.dots.x[i] && a.dots.y[i] == b.dots.y[i] && a.dots.z1[i] == b.dots.z1[i]
                && a.dots.phase[i] == b.dots.phase[i] && a.dots.hue[i] == b.dots.hue[i], "Same seed, same motion");
        }
    }

    private static void latticeBuildsItself() {
        Pond pond = new Pond(7, PER_BODY);
        require(pond.dots.edgeCount == 0, "Groups start without bonds");
        pond.run(20);
        require(pond.dots.bondedFraction() > .93, "Nearly every dot bonds into a lattice: " + pond.dots.bondedFraction());
        int large = 0, biggest = 0;
        for (int s = 0; s < pond.dots.bodyCount; s++) {
            if (pond.dots.bodySize[s] >= 100) large++;
            biggest = Math.max(biggest, pond.dots.bodySize[s]);
        }
        require(large >= 3, "The random groups become several bodies, not one net: " + large);
        require(biggest < pond.dots.count / 2, "No single net holds half the dots: " + biggest);
    }

    private static void fusesAndDividesOnItsOwn() {
        Pond pond = new Pond(7, PER_BODY);
        pond.run(40);
        int merges = pond.dots.getMergeCount(), divides = pond.dots.getDivideCount();
        int largest = 0, oversized = 0;
        double bonded = 0; int samples = 0;
        for (int k = 0; k < 52; k++) {
            pond.run(5);
            int now = 0;
            for (int s = 0; s < pond.dots.bodyCount; s++) now = Math.max(now, pond.dots.bodySize[s]);
            largest = Math.max(largest, now);
            if (now > 3 * PER_BODY) oversized++;
            bonded += pond.dots.bondedFraction(); samples++;
        }
        int laterMerges = pond.dots.getMergeCount() - merges, laterDivides = pond.dots.getDivideCount() - divides;
        require(laterMerges >= 4, "Bodies keep fusing after they settle: " + laterMerges);
        require(laterDivides >= 4, "Bodies keep dividing after they settle: " + laterDivides);
        // A large galaxy may pass through, but quorum breaks it up again.
        require(largest <= 4.4 * PER_BODY, "No body runs away with the pond: " + largest);
        require(oversized <= samples / 5, "Oversized bodies are passing, not lasting: " + oversized + " of " + samples);
        require(bonded / samples > .95, "Loose dust stays rare: " + bonded / samples);
    }

    private static void crowdedGiantComesApart() {
        Pond pond = new Pond(1, 1200);
        pond.run(160);
        int pieces = 0;
        for (int s = 0; s < pond.dots.bodyCount; s++) if (pond.dots.bodySize[s] >= DotLattice.EVENT_MIN) pieces++;
        require(pieces >= 2, "A body four times too large divides by quorum: " + pieces + " bodies");
    }

    private static void mergingOffKeepsBodiesApart() {
        Pond pond = new Pond(7, PER_BODY);
        pond.dots.setMerging(false);
        for (int k = 0; k < 12; k++) {
            pond.run(10);
            // A divided body may heal with its own kind; two kinds never share a body.
            int[] lineageOfBody = new int[256];
            java.util.Arrays.fill(lineageOfBody, -1);
            for (int i = 0; i < pond.dots.count; i++) {
                int id = pond.dots.bodyOf[i];
                if (id < 0) continue;
                if (lineageOfBody[id] < 0) lineageOfBody[id] = pond.dots.lineage[i];
                require(lineageOfBody[id] == pond.dots.lineage[i], "Without merging, no body holds two lineages");
            }
        }
    }

    private static void releasedLineageFadesAndLeaves() {
        Pond pond = new Pond(4, 150);
        pond.dots.arrivalRate = 0; pond.dots.arrivalBase = 0;
        pond.run(5);
        int before = pond.dots.count, released = 0;
        for (int i = 0; i < before; i++) if (pond.dots.lineage[i] == 3) released++;
        require(released >= DotLattice.MIN_BODY, "The lineage to release exists: " + released);
        pond.dots.releaseLineage(3);
        pond.run(4);
        require(pond.dots.count == before - released, "A released lineage fades and is removed: " + before + " -> " + pond.dots.count);
        for (int i = 0; i < pond.dots.count; i++) require(pond.dots.lineage[i] != 3, "No dot of the released lineage remains");
        for (int e = 0; e < pond.dots.edgeCount; e++) {
            require(pond.dots.edgeA[e] < pond.dots.count && pond.dots.edgeB[e] < pond.dots.count, "Bonds survive compaction");
        }
    }

    private static void paceStaysCalm() {
        Pond pond = new Pond(7, PER_BODY);
        pond.run(40);
        double sum = 0;
        for (int i = 0; i < pond.dots.count; i++) sum += Math.hypot(pond.dots.vx[i], pond.dots.vy[i]);
        double mean = sum / pond.dots.count;
        // At the person's 0.11x Drift, 0.08 units per pond second is about 11 px/s on their screen.
        require(mean < .08, "Dots drift calmly: " + mean + " units per pond second");
    }

    private static void attentionDrawsDotsIn() {
        Pond a = new Pond(7, PER_BODY), b = new Pond(7, PER_BODY);
        a.run(10); b.run(10);
        b.dots.setAttention(0, .4, 1);
        a.run(15); b.run(15);
        require(near(b.dots, 0, .4, .3) > near(a.dots, 0, .4, .3) + 20, "A touch draws dots toward it");
    }

    /**
     * Colour is kind, and kinds change as the system evolves: properties follow
     * hue exactly, colours (and so properties) move over time and differ
     * between bodies, crowded vertices holding few bonds redden, and the
     * hidden room opens gradually while every shadow stays on the screen.
     */
    private static void rulesEvolveWithTheSystem() {
        Pond pond = new Pond(7, PER_BODY);
        DotLattice d = pond.dots;
        pond.run(.5);
        require(d.extentNowForTest() < .1 * d.hiddenExtent + 1e-9, "The hidden room starts nearly closed");
        double[] start = java.util.Arrays.copyOf(d.geneSpacing, d.count);
        double[] startHue = java.util.Arrays.copyOf(d.nativeHue, d.count);
        int reddened = 0;
        for (int k = 0; k < 48; k++) {
            pond.run(5);
            for (int i = 0; i < d.count; i++) if (d.redness[i] > .5) { reddened++; break; }
        }
        require(Math.abs(d.extentNowForTest() - d.hiddenExtent) < 1e-9, "The hidden room opens fully");
        // Same hue, same kind.
        for (int i = 1; i < d.count; i++) {
            if (Math.abs(DotLattice.arc(d.hue[i] - d.hue[0])) < 1e-9) {
                require(Math.abs(d.geneSpacing[i] - d.geneSpacing[0]) < 1e-9 && Math.abs(d.geneSwim[i] - d.geneSwim[0]) < 1e-9,
                    "Dots of one colour share their properties");
            }
        }
        double moved = 0, hueMoved = 0;
        for (int i = 0; i < d.count && i < start.length; i++) {
            moved += Math.abs(d.geneSpacing[i] - start[i]);
            hueMoved += Math.abs(DotLattice.arc(d.nativeHue[i] - startHue[i]));
        }
        require(moved / d.count > .005, "Properties move as colours flow: " + moved / d.count);
        require(hueMoved / d.count > 2, "Remembered colours drift as the system evolves: " + hueMoved / d.count);
        require(reddened > 0, "Crowded, loosely bound vertices turn red at times");
        double sum = 0, square = 0; int bodies = 0;
        for (int s = 0; s < d.bodyCount; s++) {
            if (d.bodySize[s] < 60) continue;
            double mean = 0; int n = 0;
            for (int i = 0; i < d.count; i++) if (d.bodyOf[i] == d.bodyId[s]) { mean += d.geneSpacing[i]; n++; }
            mean /= Math.max(1, n); sum += mean; square += mean * mean; bodies++;
        }
        double spread = bodies > 1 ? Math.sqrt(Math.max(0, square / bodies - (sum / bodies) * (sum / bodies))) : 0;
        require(spread > .005, "Bodies keep different kinds, not one: " + spread);
        // A finger's nudge moves a shadow exactly as far as the finger moved.
        double before = d.px[0];
        d.nudge(0, .01, 0);
        require(Math.abs(d.px[0] - before - .01) < 1e-9 || Math.abs(d.px[0]) >= W / 2 + d.frameMargin - 1e-9,
            "A nudge moves the shadow by its own amount");
    }

    /**
     * Groups drift in from beyond the edges and dots drift out, and the pond
     * keeps about its size: at most a few percent over, never far short.
     */
    private static void theFrameIsOpen() {
        Pond pond = new Pond(7, PER_BODY);
        DotLattice d = pond.dots;
        int low = Integer.MAX_VALUE, high = 0;
        for (int k = 0; k < 80; k++) {
            pond.run(5);
            if (k >= 6) { low = Math.min(low, d.count); high = Math.max(high, d.count); }
        }
        require(d.arrivals > 0, "Groups arrive from beyond the frame: " + d.arrivals);
        require(d.departures > 0, "Dots leave through the frame: " + d.departures);
        require(d.edgeEntries > 100 && d.edgeExits > 100, "Dots cross the screen's edge both ways: " + d.edgeEntries + " in, " + d.edgeExits + " out");
        require(low >= .9 * d.targetCount && high <= 1.1 * d.targetCount,
            "The pond keeps about its size: " + low + ".." + high + " of " + d.targetCount);
    }

    /**
     * Heat marks crowding: vertices crowded per bond run hotter than calm ones,
     * and hot vertices stay a small minority.
     */
    private static void heatMarksCrowding() {
        Pond pond = new Pond(7, PER_BODY);
        DotLattice d = pond.dots;
        pond.run(40);
        double hot = 0, crowdedHeat = 0, calmHeat = 0, reach = 1.35 * DotLattice.SPACING_HINT;
        int samples = 0, crowded = 0, calm = 0;
        for (int k = 0; k < 20; k++) {
            pond.run(5);
            int h = 0;
            for (int i = 0; i < d.count; i++) {
                if (d.redness[i] > .3) h++;
                int n = 0;
                for (int j = 0; j < d.count; j++) {
                    double dx = d.x[j] - d.x[i], dy = d.y[j] - d.y[i], du = d.z1[j] - d.z1[i], dw = d.z2[j] - d.z2[i];
                    if (j != i && dx * dx + dy * dy + du * du + dw * dw < reach * reach) n++;
                }
                double load = n / (1 + .5 * d.degree[i]);
                if (load >= 4) { crowdedHeat += d.redness[i]; crowded++; } else if (load <= 2) { calmHeat += d.redness[i]; calm++; }
            }
            hot += h / (double) d.count; samples++;
        }
        require(hot / samples < .15, "Hot vertices stay a minority: " + hot / samples);
        require(crowded > 0 && calm > 0, "Both crowded and calm vertices occur: " + crowded + ", " + calm);
        require(crowdedHeat / crowded > 2 * calmHeat / calm + .02,
            "Crowded vertices run hotter: " + crowdedHeat / crowded + " against " + calmHeat / calm);
    }

    private static void stepAllocatesNothing() {
        java.lang.management.ThreadMXBean bean = java.lang.management.ManagementFactory.getThreadMXBean();
        if (!(bean instanceof com.sun.management.ThreadMXBean)) return;
        com.sun.management.ThreadMXBean counter = (com.sun.management.ThreadMXBean) bean;
        long thread = Thread.currentThread().getId();
        if (counter.getThreadAllocatedBytes(thread) < 0) return;
        Pond pond = new Pond(7, PER_BODY);
        pond.run(5);
        for (int k = 0; k < 200; k++) pond.dots.step(DT);
        long before = counter.getThreadAllocatedBytes(thread);
        for (int k = 0; k < 400; k++) pond.dots.step(DT);
        long allocated = counter.getThreadAllocatedBytes(thread) - before;
        require(allocated < 16 * 1024, "A step allocates nothing: " + allocated + " bytes over 400 steps");
    }

    private static int near(DotLattice d, double px, double py, double radius) {
        int n = 0;
        for (int i = 0; i < d.count; i++) if (Math.hypot(d.px[i] - px, d.py[i] - py) < radius) n++;
        return n;
    }
    private static boolean finite(double v) { return !Double.isNaN(v) && !Double.isInfinite(v); }
    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
