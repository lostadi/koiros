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
        stepAllocatesNothing();
        System.out.println("DotLatticeTest passed: " + checks + " checks");
    }

    /** A lattice in a real Life world's current, as the wallpaper runs it. */
    private static final class Pond {
        final LifeField world = new LifeField(54, 120, 821166217176535313L);
        final DotLattice dots = new DotLattice(821166217176535313L);
        final double[] fx = new double[13 * 19], fy = new double[13 * 19], proj = new double[13 * 19];
        final double[] turbulence = new double[13 * 19], local = new double[2];
        Pond(int bodies, int perBody) {
            world.setRate(1);
            dots.seed(bodies, perBody, W, H);
            dots.setWater(fx, fy, turbulence, 13, 19, 1.0);
        }
        void run(double pondSeconds) {
            for (double t = 0; t < pondSeconds - 1e-9; t += DT) {
                // The person's 0.11x Drift: one pond second is about nine real seconds of Life.
                world.advance(DT / .11);
                world.sampleGrid(13, 19, fx, fy, proj);
                for (int gy = 0; gy < 19; gy++) for (int gx = 0; gx < 13; gx++) {
                    world.sampleLocal(gx / 12.0, gy / 18.0, 5, local);
                    turbulence[gy * 13 + gx] = Math.min(1, local[1] * 4.5);
                }
                dots.step(DT);
            }
        }
    }

    private static void bookkeepingStaysExact() {
        Pond pond = new Pond(7, PER_BODY);
        for (int round = 0; round < 24; round++) {
            pond.run(2.5);
            DotLattice d = pond.dots;
            int[] seen = new int[d.count];
            HashSet<Long> pairs = new HashSet<>();
            for (int e = 0; e < d.edgeCount; e++) {
                int a = d.edgeA[e], b = d.edgeB[e];
                require(a != b && a >= 0 && b >= 0 && a < d.count && b < d.count, "Edge ends must be distinct live dots");
                require(pairs.add(Math.min(a, b) * 100000L + Math.max(a, b)), "No pair is bonded twice");
                require(d.edgeStrength[e] >= 0 && d.edgeStrength[e] <= 1, "Bond strength stays in [0, 1]");
                seen[a]++; seen[b]++;
            }
            for (int i = 0; i < d.count; i++) {
                require(seen[i] == d.degree[i], "Each dot's bond list matches the edges");
                require(d.degree[i] <= d.valence[i], "No dot holds more bonds than its valence");
                require(finite(d.x[i]) && finite(d.y[i]) && finite(d.vx[i]) && finite(d.vy[i]), "Motion stays finite");
                require(Math.abs(d.x[i]) <= W / 2 + 1e-9 && Math.abs(d.y[i]) <= H / 2 + 1e-9, "Dots stay on the screen");
                require(Math.hypot(d.vx[i], d.vy[i]) <= d.maxSpeed + 1e-9, "Speed limit holds");
                require(d.hue[i] >= 0 && d.hue[i] < 360, "Hue stays wrapped");
            }
        }
    }

    private static void deterministicForASeed() {
        Pond a = new Pond(5, 200), b = new Pond(5, 200);
        a.run(15); b.run(15);
        require(a.dots.count == b.dots.count && a.dots.edgeCount == b.dots.edgeCount, "Same seed, same lattice");
        for (int i = 0; i < a.dots.count; i++) {
            require(a.dots.x[i] == b.dots.x[i] && a.dots.y[i] == b.dots.y[i] && a.dots.phase[i] == b.dots.phase[i],
                "Same seed, same motion");
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

    /** Each dot's rules (bond length, breathing, swimming) change as the system evolves, within bounds. */
    private static void rulesEvolveWithTheSystem() {
        Pond pond = new Pond(7, PER_BODY);
        pond.run(5);
        DotLattice d = pond.dots;
        double[] start = java.util.Arrays.copyOf(d.geneSpacing, d.count);
        pond.run(240);
        double moved = 0;
        for (int i = 0; i < d.count && i < start.length; i++) moved += Math.abs(d.geneSpacing[i] - start[i]);
        require(moved / d.count > .015, "Bond-length genes move as the system evolves: " + moved / d.count);
        double lineageShift = 0;
        for (int l = 0; l < 7; l++) lineageShift += Math.abs(d.lineageGene[1][l] - d.lineageBirth[1][l]);
        require(lineageShift / 7 > .01, "Lineage genomes wander: " + lineageShift / 7);
        double sum = 0, square = 0; int bodies = 0;
        for (int s = 0; s < d.bodyCount; s++) {
            if (d.bodySize[s] < 60) continue;
            double mean = 0; int n = 0;
            for (int i = 0; i < d.count; i++) if (d.bodyOf[i] == d.bodyId[s]) { mean += d.geneSpacing[i]; n++; }
            mean /= Math.max(1, n); sum += mean; square += mean * mean; bodies++;
        }
        double spread = bodies > 1 ? Math.sqrt(Math.max(0, square / bodies - (sum / bodies) * (sum / bodies))) : 0;
        require(spread > .01, "Bodies keep different rules, not one kind: " + spread);
        for (int i = 0; i < d.count; i++) {
            require(d.geneSpacing[i] >= .8 && d.geneSpacing[i] <= 1.35 && d.geneBreath[i] >= .04 && d.geneBreath[i] <= .3
                && d.geneSwim[i] >= .5 && d.geneSwim[i] <= 1.8, "Genes stay within their bounds");
        }
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
        for (int i = 0; i < d.count; i++) if (Math.hypot(d.x[i] - px, d.y[i] - py) < radius) n++;
        return n;
    }
    private static boolean finite(double v) { return !Double.isNaN(v) && !Double.isInfinite(v); }
    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
