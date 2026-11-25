package org.ostadix.koilife;

/** Bonding, merging, dividing and resting in the shared pond. */
public final class FusionTest {
    private static int checks;

    public static void main(String[] args) {
        mergingOffNeverBonds();
        touchingBodiesFuseIntoOne();
        mergedBodiesDivideAndRest();
        mergedBodiesNeverExceedTheLimit();
        deterministicAndBounded();
        eventsAreReadable();
        turningMergingOffDivides();
        sharedCurrentMovesEveryone();
        System.out.println("FusionTest passed: " + checks + " checks");
    }

    private static void mergingOffNeverBonds() {
        PondDynamics pond = new PondDynamics(5L);
        pond.configure(3, false, true);
        require(!pond.isMerging(), "Merging must be off unless requested");
        pond.placeBodyForTest(0, -0.02, 0, 0, 0);
        pond.placeBodyForTest(1, 0.02, 0, 0, 0);
        for (int i = 0; i < 1200; i++) {
            pond.advance(1.0 / 40, 1, 2, null);
            for (int a = 0; a < 9; a++) for (int b = 0; b < 9; b++)
                near(pond.getBond(a, b), 0.0, 0.0, "Bond without merging");
        }
        require(pond.getMergeCount() == 0 && pond.getEventSerial() == 0, "Events without merging");
    }

    private static PondDynamics touchingPair(long seed) {
        PondDynamics pond = new PondDynamics(seed);
        pond.configure(2, false, true, true);
        // Let the third pooled body fade out first.
        for (int i = 0; i < 120; i++) pond.advance(1.0 / 60, 1, 2, null);
        pond.placeBodyForTest(0, -0.06, 0.1, 0, 0);
        pond.placeBodyForTest(1, 0.06, 0.1, 0, 0);
        return pond;
    }

    private static void touchingBodiesFuseIntoOne() {
        PondDynamics pond = touchingPair(11L);
        double seconds = 0;
        while (pond.getPairState(0, 1) != PondDynamics.MERGED && seconds < 12) {
            pond.advance(1.0 / 60, 1, 2, null);
            seconds += 1.0 / 60;
            double bond = pond.getBond(0, 1);
            require(bond >= 0 && bond <= 1, "Bond out of range");
        }
        require(pond.getPairState(0, 1) == PondDynamics.MERGED, "Touching pair never merged");
        require(seconds > 2.5, "Fusion should be gradual, took " + seconds);
        near(pond.getBond(0, 1), 1.0, 1e-12, "Merged bond");
        require(pond.getClusterRoot(1) == 0 && pond.getClusterSize(0) == 2, "Merged group membership");
        near(pond.getBondMass(0), 1.0, 1e-9, "Bond mass of a merged pair");
        for (int i = 0; i < 180; i++) pond.advance(1.0 / 60, 1, 2, null);
        if (pond.getPairState(0, 1) == PondDynamics.MERGED) {
            double distance = Math.hypot(pond.getX(1) - pond.getX(0), pond.getY(1) - pond.getY(0));
            require(distance < 0.02, "Merged bodies should share one center, distance " + distance);
        }
        require(pond.getMergeCount() == 1, "One merge expected");
    }

    private static void mergedBodiesDivideAndRest() {
        PondDynamics pond = touchingPair(23L);
        boolean merged = false, divided = false;
        double dividedAt = -1, time = 0, maxDistanceAfter = 0;
        while (time < 240) {
            pond.advance(1.0 / 60, 1, 2, null);
            time += 1.0 / 60;
            int state = pond.getPairState(0, 1);
            if (state == PondDynamics.MERGED) merged = true;
            if (merged && !divided && pond.getDivideCount() > 0) { divided = true; dividedAt = time; }
            if (divided && time - dividedAt < 8.5) {
                require(state == PondDynamics.FREE, "Divided pair fused again before resting");
                maxDistanceAfter = Math.max(maxDistanceAfter,
                    Math.hypot(pond.getX(1) - pond.getX(0), pond.getY(1) - pond.getY(0)));
            }
            if (divided && time - dividedAt >= 8.5) break;
        }
        require(merged && divided, "Merged pair never divided within four minutes");
        require(maxDistanceAfter > 0.2, "Divided bodies stayed together: " + maxDistanceAfter);
    }

    private static void mergedBodiesNeverExceedTheLimit() {
        PondDynamics pond = new PondDynamics(31L);
        pond.configure(9, false, true, true);
        for (int i = 0; i < 120; i++) pond.advance(1.0 / 60, 1, 2, null);
        for (int i = 0; i < 9; i++) pond.placeBodyForTest(i, 0.01 * (i % 3), 0.01 * (i / 3), 0, 0);
        int largest = 0;
        for (int step = 0; step < 60 * 90; step++) {
            pond.advance(1.0 / 60, 1, 2, null);
            for (int i = 0; i < 9; i++) {
                int size = pond.getClusterSize(i);
                largest = Math.max(largest, size);
                require(size <= PondDynamics.MAX_CLUSTER, "Merged group too large: " + size);
            }
        }
        require(largest >= 2, "Crowded bodies never fused");
    }

    private static void deterministicAndBounded() {
        PondDynamics a = new PondDynamics(41L), b = new PondDynamics(41L);
        a.configure(6, true, true, true); b.configure(6, true, true, true);
        double[][] drive = new double[9][7];
        for (int second = 0; second < 300; second++) {
            for (int i = 0; i < 9; i++) {
                drive[i][0] = Math.sin(second * 0.3 + i); drive[i][1] = Math.cos(second * 0.2 + i);
                drive[i][4] = 0.4 * Math.sin(second * 0.05); drive[i][5] = 0.4 * Math.cos(second * 0.07);
                drive[i][6] = 0.5 + 0.5 * Math.sin(second * 0.11 + i);
            }
            for (int k = 0; k < 4; k++) { a.advance(0.25, 1, 2.2, drive); b.advance(0.25, 1, 2.2, drive); }
            for (int i = 0; i < 9; i++) {
                near(a.getX(i), b.getX(i), 0, "Nondeterministic x");
                near(a.getY(i), b.getY(i), 0, "Nondeterministic y");
                require(Math.abs(a.getX(i)) <= 0.5 && Math.abs(a.getY(i)) <= 1.1, "Body escaped the pond");
                for (int j = 0; j < 9; j++) {
                    double bond = a.getBond(i, j);
                    require(bond >= 0 && bond <= 1 && bond == b.getBond(i, j), "Bond invalid or nondeterministic");
                    require(bond == a.getBond(j, i), "Bond not symmetric");
                }
            }
        }
        require(a.getMergeCount() == b.getMergeCount() && a.getDivideCount() == b.getDivideCount(),
            "Event counts differ");
        require(a.getMergeCount() > 0, "A busy pond never merged in five minutes");
    }

    private static void eventsAreReadable() {
        PondDynamics pond = touchingPair(11L);
        double[] out = new double[4];
        require(pond.readEvent(1, out) == 0, "Event before any was recorded");
        for (int i = 0; i < 60 * 12 && pond.getMergeCount() == 0; i++) pond.advance(1.0 / 60, 1, 2, null);
        long serial = pond.getEventSerial();
        require(serial >= 1, "Merge was not recorded");
        require(pond.readEvent(serial, out) == PondDynamics.EVENT_MERGED, "Wrong event type");
        require(Double.isFinite(out[0]) && Double.isFinite(out[1]), "Event position not finite");
        require((int) out[2] == 0 && (int) out[3] == 1, "Event bodies");
        require(pond.readEvent(serial + 1, out) == 0, "Future event readable");
        require(pond.readEvent(0, out) == 0 && pond.readEvent(serial, null) == 0, "Invalid read accepted");
    }

    private static void turningMergingOffDivides() {
        PondDynamics pond = touchingPair(11L);
        for (int i = 0; i < 60 * 12 && pond.getPairState(0, 1) != PondDynamics.MERGED; i++)
            pond.advance(1.0 / 60, 1, 2, null);
        require(pond.getPairState(0, 1) == PondDynamics.MERGED, "Setup merge failed");
        pond.configure(2, false, true, false);
        require(pond.getPairState(0, 1) == PondDynamics.DIVIDING, "Disabling merging should divide");
        for (int i = 0; i < 60 * 4; i++) pond.advance(1.0 / 60, 1, 2, null);
        near(pond.getBond(0, 1), 0.0, 0.0, "Bond after disabling merging");
        require(pond.getPairState(0, 1) == PondDynamics.FREE, "Pair should be free");
        for (int i = 0; i < 60 * 30; i++) {
            pond.advance(1.0 / 60, 1, 2, null);
            require(pond.getBond(0, 1) == 0.0, "Fused while merging was off");
        }
    }

    private static void sharedCurrentMovesEveryone() {
        PondDynamics still = new PondDynamics(53L), carried = new PondDynamics(53L);
        still.configure(3, false, false); carried.configure(3, false, false);
        double[][] current = new double[9][7];
        for (int i = 0; i < 9; i++) { current[i][4] = 1.0; current[i][5] = 0.0; }
        double drift = 0;
        for (int i = 0; i < 60 * 6; i++) {
            still.advance(1.0 / 60, 1, 2, null);
            carried.advance(1.0 / 60, 1, 2, current);
        }
        for (int i = 0; i < 3; i++) drift += carried.getX(i) - still.getX(i);
        require(drift > 0.05, "A shared rightward current should carry bodies right, got " + drift);
    }

    private static void near(double actual, double expected, double tolerance, String message) {
        checks++;
        if (!(Math.abs(actual - expected) <= tolerance)) {
            throw new AssertionError(message + ": expected " + expected + " got " + actual);
        }
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
