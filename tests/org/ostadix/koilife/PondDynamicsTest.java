package org.ostadix.koilife;

/** Behavioral checks for counts, smooth appearance, bounded motion and contact. */
public final class PondDynamicsTest {
    private static int checks;

    public static void main(String[] args) {
        defaultLayoutAndPause();
        determinismAndFixedSteps();
        countChangesAndFades();
        resizeAndStressBounds();
        collisionResponse();
        broadSwimmingAndWallTurns();
        gradualCollectiveStructure();
        encounterHueExchange();
        encounterKinematicsBaseline();
        visualDepthStaysOutOfTheMotion();
        System.out.println("PondDynamicsTest passed: " + checks + " checks");
    }

    private static void defaultLayoutAndPause() {
        PondDynamics pond = new PondDynamics(61L);
        require(pond.getTargetCount() == 3 && pond.getVisibleCount() == 3, "Default three must render without a tick");
        for (int i = 0; i < 9; i++) near(pond.getOpacity(i), i < 3 ? 1.0 : 0.0, 0.0, "Initial opacity");
        double[][] before = snapshot(pond);
        for (double time : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) pond.advance(time, 1, 2, null);
        equal(before, snapshot(pond), 0.0, "Paused state moved");
        pond.configure(3, false, false);
        equal(before, snapshot(pond), 0.0, "Repeated configuration changed state");
    }

    private static void determinismAndFixedSteps() {
        PondDynamics a = new PondDynamics(19L), b = new PondDynamics(19L), other = new PondDynamics(20L);
        a.configure(8, true, true); b.configure(8, true, true);
        double[][] drive = new double[9][4];
        for (int i = 0; i < 9; i++) { drive[i][0] = 0.3; drive[i][1] = -0.6; drive[i][2] = 0.2; }
        for (int second = 0; second < 40; second++) {
            for (int i = 0; i < 2; i++) a.advance(0.5, 1, 2, drive);
            for (int i = 0; i < 120; i++) {
                b.configure(8, true, true); // A UI read every frame must not postpone count changes.
                b.advance(1.0 / 120.0, 1, 2, drive);
            }
            equal(snapshot(a), snapshot(b), 1e-12, "Frame partition or settings reads changed simulation");
            require(a.getTargetCount() == b.getTargetCount(), "Population schedule depends on render rate");
        }
        require(Math.abs(a.getX(0) - other.getX(0)) + Math.abs(a.getY(0) - other.getY(0)) > 0.01,
                "Distinct run did not move away from another seed's initial layout");
    }

    private static void countChangesAndFades() {
        PondDynamics pond = new PondDynamics(9L);
        pond.configure(9, false, true);
        require(pond.getTargetCount() == 9 && pond.getVisibleCount() == 3, "Adding creatures must initially preserve opacity");
        pond.advance(0.5, 1, 2, null);
        require(pond.getOpacity(8) > 0.1 && pond.getOpacity(8) < 0.5, "New creature did not fade in gradually");
        for (int i = 0; i < 3; i++) pond.advance(0.5, 1, 2, null);
        near(pond.getOpacity(8), 1.0, 1e-12, "Fade-in did not complete");
        pond.configure(1, false, true);
        require(pond.getTargetCount() == 1 && pond.getVisibleCount() == 9, "Removing creatures popped them instantly");
        pond.advance(0.5, 1, 2, null);
        require(pond.getOpacity(8) > 0.5 && pond.getOpacity(8) < 0.9, "Removed creature did not fade out gradually");
        for (int i = 0; i < 3; i++) pond.advance(0.5, 1, 2, null);
        require(pond.getVisibleCount() == 1, "Fade-out retained invisible bodies");
        pond.configure(9, true, true);
        int target = pond.getTargetCount();
        int changes = 0;
        boolean sawPartial = false;
        for (int tick = 0; tick < 90 * 30; tick++) {
            pond.advance(1.0 / 30.0, 1, 2, null);
            int current = pond.getTargetCount();
            require(current >= 1 && current <= 9, "Variable count outside selected range");
            if (current != target) { changes++; target = current; }
            for (int i = 0; i < 9; i++) if (pond.getOpacity(i) > 0.05 && pond.getOpacity(i) < 0.95) sawPartial = true;
        }
        require(changes >= 6 && changes <= 12, "Automatic count did not change at a visible, gradual cadence");
        require(sawPartial, "Automatic count transition had no partial opacity");
        pond.configure(0, true, true);
        require(pond.getMaxCount() == 1 && pond.getTargetCount() == 1, "Minimum count clamp");
        pond.configure(200, false, true);
        require(pond.getMaxCount() == 9 && pond.getTargetCount() == 9, "Maximum count clamp");
    }

    private static void resizeAndStressBounds() {
        PondDynamics pond = new PondDynamics(101L);
        double oldX = pond.getX(0) / (0.5 - pond.getRadius(0));
        double oldY = pond.getY(0) / (1.0 - pond.getRadius(0));
        pond.advance(0, 2, 1, null);
        near(pond.getX(0) / (1.0 - pond.getRadius(0)), oldX, 1e-12, "Resize lost normalized horizontal position");
        near(pond.getY(0) / (0.5 - pond.getRadius(0)), oldY, 1e-12, "Resize lost normalized vertical position");
        pond.configure(9, true, true);
        double[][] drive = new double[9][4];
        for (int i = 0; i < 9; i++) {
            drive[i][0] = i % 2 == 0 ? 100.0 : -100.0;
            drive[i][1] = i % 3 == 0 ? Double.NaN : Double.POSITIVE_INFINITY;
        }
        for (int frame = 0; frame < 120 * 30; frame++) {
            double width = frame % 600 < 300 ? 1.0 : 2.5;
            double height = frame % 600 < 300 ? 2.5 : 1.0;
            pond.advance(1.0 / 30.0, width, height, drive);
            for (int i = 0; i < 9; i++) {
                double radius = pond.getRadius(i);
                require(Double.isFinite(pond.getX(i)) && Math.abs(pond.getX(i)) + radius <= width / 2 + 1e-10, "Horizontal bounds");
                require(Double.isFinite(pond.getY(i)) && Math.abs(pond.getY(i)) + radius <= height / 2 + 1e-10, "Vertical bounds");
                require(pond.getOpacity(i) >= 0 && pond.getOpacity(i) <= 1, "Opacity bounds");
                double vx = pond.velocityXForTest(i), vy = pond.velocityYForTest(i);
                require(vx * vx + vy * vy <= 0.23 * 0.23 + 1e-12, "Unbounded contact speed");
            }
        }
        double[][] state = snapshot(pond);
        pond.advance(0, Double.NaN, -100, null);
        equal(state, snapshot(pond), 0, "Invalid viewport changed valid state");
        PondDynamics bounded = new PondDynamics(8L), halfSecond = new PondDynamics(8L);
        bounded.advance(1000, 1, 2, null);
        halfSecond.advance(0.5, 1, 2, null);
        equal(snapshot(bounded), snapshot(halfSecond), 0, "Long gap caused unbounded catch-up");
    }

    private static void collisionResponse() {
        PondDynamics contact = headOn(true), ghost = headOn(false);
        contact.advance(1.0 / 120.0, 2, 2, null);
        ghost.advance(1.0 / 120.0, 2, 2, null);
        require(contact.velocityXForTest(0) < 0 && contact.velocityXForTest(1) > 0, "Head-on bodies did not bounce apart");
        require(ghost.velocityXForTest(0) > 0 && ghost.velocityXForTest(1) < 0, "Disabled collisions still bounced bodies");
        require(contact.getX(1) - contact.getX(0) > ghost.getX(1) - ghost.getX(0), "Overlap did not resolve");
        contact.placeBodyForTest(0, 0, 0, 0, 0);
        contact.placeBodyForTest(1, 0, 0, 0, 0);
        contact.advance(0.5, 2, 2, null);
        require(Math.hypot(contact.getX(1) - contact.getX(0), contact.getY(1) - contact.getY(0)) > 0.1,
                "Coincident bodies failed to separate");
    }

    private static void broadSwimmingAndWallTurns() {
        PondDynamics pond = new PondDynamics(27L);
        near(pond.getRadius(0), 0.24, 0, "Three-creature core radius");
        pond.configure(1, false, true);
        for (int i = 0; i < 40; i++) pond.advance(0.5, 1, 2, null);
        near(pond.getRadius(0), 0.34, 1e-10, "Single-creature core radius");
        double lowX = Double.POSITIVE_INFINITY, highX = Double.NEGATIVE_INFINITY;
        double lowY = Double.POSITIVE_INFINITY, highY = Double.NEGATIVE_INFINITY;
        int edgeFrames = 0;
        for (int frame = 0; frame < 180 * 30; frame++) {
            pond.advance(1.0 / 30.0, 1, 2, null);
            lowX = Math.min(lowX, pond.getX(0)); highX = Math.max(highX, pond.getX(0));
            lowY = Math.min(lowY, pond.getY(0)); highY = Math.max(highY, pond.getY(0));
            if (Math.abs(pond.getX(0)) > 0.5 - pond.getRadius(0) - 0.01
                    || Math.abs(pond.getY(0)) > 1.0 - pond.getRadius(0) - 0.01) edgeFrames++;
        }
        require(highX - lowX > 0.08 && highY - lowY > 0.35, "Autonomous swimming did not cover a broad curve");
        require(edgeFrames < 30, "Swimming kept returning to the hard wall");
        pond.placeBodyForTest(0, 0.5 - pond.getRadius(0) - 0.005, 0, 0.15, 0);
        for (int frame = 0; frame < 120; frame++) pond.advance(1.0 / 30.0, 1, 2, null);
        require(pond.getX(0) < 0.5 - pond.getRadius(0) - 0.04, "Outward-moving body did not turn away from the wall");
        pond.configure(9, false, true);
        for (int i = 0; i < 40; i++) pond.advance(0.5, 1, 2, null);
        near(pond.getRadius(0), 0.24 * Math.sqrt(3.0 / 9.0), 1e-10, "Nine-creature core radius");
    }

    private static void gradualCollectiveStructure() {
        PondDynamics coupled = new PondDynamics(17L);
        PondDynamics independent = new PondDynamics(17L, 0.0);
        coupled.configure(3, false, true); independent.configure(3, false, true);
        double lowStream = 1, highStream = 0, lowPair = 1, highPair = 0;
        double lastStream = 0, lastPair = 0;
        for (int frame = 0; frame < 180 * 60; frame++) {
            coupled.advance(0.18 / 60.0, 1, 2, null);
            independent.advance(0.18 / 60.0, 1, 2, null);
            double stream = coupled.streamMixForTest(), pair = coupled.pairMixForTest();
            if (frame > 5) {
                require(Math.abs(stream - lastStream) < 0.001, "Stream influence switched abruptly");
                require(Math.abs(pair - lastPair) < 0.001, "Neighbor influence switched abruptly");
                lowStream = Math.min(lowStream, stream); highStream = Math.max(highStream, stream);
                lowPair = Math.min(lowPair, pair); highPair = Math.max(highPair, pair);
            }
            lastStream = stream; lastPair = pair;
        }
        require(highStream - lowStream > 0.12, "Slow stream did not change its influence");
        require(highPair - lowPair > 0.25, "Slow neighbor tendency did not change");
        double difference = 0;
        for (int i = 0; i < 3; i++)
            difference += Math.hypot(coupled.getX(i) - independent.getX(i), coupled.getY(i) - independent.getY(i));
        require(difference > 0.03, "Collective drive did not rearrange the bodies");
        double phase = coupled.structurePhaseForTest(), stream = coupled.streamMixForTest(), pair = coupled.pairMixForTest();
        double[][] frozen = snapshot(coupled);
        coupled.advance(0, 1, 2, null);
        equal(frozen, snapshot(coupled), 0, "Paused collective positions changed");
        near(phase, coupled.structurePhaseForTest(), 0, "Paused collective phase advanced");
        near(stream, coupled.streamMixForTest(), 0, "Paused stream weight changed");
        near(pair, coupled.pairMixForTest(), 0, "Paused neighbor weight changed");

        // Moving a neighbor must influence the body even when hard collisions
        // are off. The zero-strength comparison has no such interaction.
        PondDynamics near = neighborFixture(0.5, 0.1);
        PondDynamics far = neighborFixture(0.5, 0.7);
        PondDynamics freeNear = neighborFixture(0.0, 0.1);
        PondDynamics freeFar = neighborFixture(0.0, 0.7);
        for (int i = 0; i < 120; i++) {
            near.advance(1.0 / 120.0, 1, 2, null); far.advance(1.0 / 120.0, 1, 2, null);
            freeNear.advance(1.0 / 120.0, 1, 2, null); freeFar.advance(1.0 / 120.0, 1, 2, null);
        }
        require(Math.hypot(near.getX(0) - far.getX(0), near.getY(0) - far.getY(0)) > 1e-4,
                "Neighbor locations did not affect collective swimming");
        near(freeNear.getX(0), freeFar.getX(0), 0, "Disabled shared current coupled horizontal motion");
        near(freeNear.getY(0), freeFar.getY(0), 0, "Disabled shared current coupled vertical motion");
    }

    private static PondDynamics neighborFixture(double strength, double neighborY) {
        PondDynamics pond = new PondDynamics(68L, strength);
        pond.configure(2, false, false);
        for (int i = 0; i < 4; i++) pond.advance(0.5, 1, 2, null);
        pond.placeBodyForTest(0, -0.15, -0.1, 0, 0);
        pond.placeBodyForTest(1, 0.15, neighborY, 0, 0);
        return pond;
    }

    private static void encounterHueExchange() {
        PondDynamics fresh = new PondDynamics(173L);
        for (int i = 0; i < 9; i++) near(fresh.getEncounterHueOffset(i), 0, 0, "Initial encounter hue");
        PondDynamics close = encounterFixture(2), distant = encounterFixture(2);
        double previous = 0;
        for (int tick = 0; tick < 1200; tick++) {
            poseEncounterGrid(close); poseEncounterGrid(distant);
            close.placeBodyForTest(0, -0.15, 0, 0, 0);
            close.placeBodyForTest(1, 0.15, 0, 0, 0);
            close.advance(1.0 / 120.0, 4, 4, null);
            distant.advance(1.0 / 120.0, 4, 4, null);
            double hue = close.getEncounterHueOffset(0);
            require(Math.abs(hue - previous) <= 8.0 / 120.0 + 1e-12, "Encounter hue changed too quickly");
            require(hue >= -24 && hue <= 24, "Encounter hue outside bounds");
            previous = hue;
        }
        require(close.getEncounterHueOffset(0) > 10 && close.getEncounterHueOffset(0) < 16,
                "Nearby lower-index body did not shift toward its neighbor");
        require(close.getEncounterHueOffset(1) < -10 && close.getEncounterHueOffset(1) > -16,
                "Nearby higher-index body did not shift toward its neighbor");
        near(distant.getEncounterHueOffset(0), 0, 0, "Distant neighbors shifted hue");
        double[] frozen = new double[9];
        for (int i = 0; i < 9; i++) frozen[i] = close.getEncounterHueOffset(i);
        for (double dt : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY})
            close.advance(dt, 4, 4, null);
        for (int i = 0; i < 9; i++)
            near(close.getEncounterHueOffset(i), frozen[i], 0, "Paused encounter hue changed");
        for (int tick = 0; tick < 2400; tick++) {
            poseEncounterGrid(close);
            double before = Math.abs(close.getEncounterHueOffset(0));
            close.advance(1.0 / 120.0, 4, 4, null);
            require(Math.abs(close.getEncounterHueOffset(0)) <= before + 1e-12, "Separated hue failed to relax");
        }
        require(Math.abs(close.getEncounterHueOffset(0)) < 0.2, "Separated hue retained its encounter tint");

        PondDynamics hidden = encounterFixture(1);
        require(hidden.getOpacity(1) == 0, "Hidden-neighbor fixture did not finish fading");
        for (int tick = 0; tick < 600; tick++) {
            poseEncounterGrid(hidden);
            hidden.placeBodyForTest(0, 0, 0, 0, 0);
            hidden.placeBodyForTest(1, 0.1, 0, 0, 0);
            hidden.advance(1.0 / 120.0, 4, 4, null);
        }
        near(hidden.getEncounterHueOffset(0), 0, 0, "Invisible neighbor changed hue");

        PondDynamics circular = encounterFixture(9), repeat = encounterFixture(9);
        for (int tick = 0; tick < 1200; tick++) {
            poseCircularEncounter(circular); poseCircularEncounter(repeat);
            circular.advance(1.0 / 120.0, 4, 4, null);
            repeat.advance(1.0 / 120.0, 4, 4, null);
            for (int i = 0; i < 9; i++) {
                double hue = circular.getEncounterHueOffset(i);
                require(Double.isFinite(hue) && hue >= -24 && hue <= 24, "Circular encounter hue bound");
                near(hue, repeat.getEncounterHueOffset(i), 0, "Encounter hue was not deterministic");
            }
        }
        require(circular.getEncounterHueOffset(0) < -20 && circular.getEncounterHueOffset(8) > 20,
                "Encounter hue ignored the shortest arc across zero degrees");
    }

    private static PondDynamics encounterFixture(int count) {
        PondDynamics pond = new PondDynamics(173L, 0.0);
        pond.configure(count, false, false);
        for (int tick = 0; tick < 480; tick++) {
            poseEncounterGrid(pond);
            pond.advance(1.0 / 120.0, 4, 4, null);
        }
        return pond;
    }
    private static void poseEncounterGrid(PondDynamics pond) {
        for (int i = 0; i < 9; i++)
            pond.placeBodyForTest(i, (i % 3 - 1) * 1.2, (i / 3 - 1) * 1.2, 0, 0);
    }
    private static void poseCircularEncounter(PondDynamics pond) {
        poseEncounterGrid(pond);
        pond.placeBodyForTest(0, 0, 0, 0, 0);
        pond.placeBodyForTest(4, 1.2, 1.2, 0, 0);
        pond.placeBodyForTest(8, 0.15, 0, 0, 0);
    }

    private static void encounterKinematicsBaseline() {
        // Recorded from the exact pre-encounter Java through admitted Ostadix:
        // job-687422-1794755365565352164-140-2. The candidate also matched
        // all 32,400 intermediate kinematic comparisons bit for bit there.
        double[][][] expected = {
            {{0.04599932490067141, -0.48822967276580703, 0.005931197562839067, 0.0046767143502549425},
             {0.05800755315165751, 0.4998939072204125, 0.013962558025294128, 0.004506140825933525}},
            {{0.17639592129400272, -0.46882964176513564, -0.01618048943460695, -0.027481111167159122},
             {-0.1135428925829592, 0.4670412331199182, 0.026129712969476384, 0.02529720036134842}}
        };
        int[] counts = {3, 9};
        for (int scenario = 0; scenario < counts.length; scenario++) {
            PondDynamics pond = new PondDynamics(173L);
            pond.configure(counts[scenario], false, true);
            double[][] drive = new double[9][4];
            for (int i = 0; i < 9; i++) {
                drive[i][0] = 0.3; drive[i][1] = -0.2; drive[i][2] = 0.4; drive[i][3] = 0.1;
            }
            for (int frame = 0; frame < 300; frame++) pond.advance(1.0 / 30.0, 1, 2, drive);
            for (int i = 0; i < 2; i++) {
                near(pond.getX(i), expected[scenario][i][0], 1e-9, "Encounter hue changed baseline X");
                near(pond.getY(i), expected[scenario][i][1], 1e-9, "Encounter hue changed baseline Y");
                near(pond.velocityXForTest(i), expected[scenario][i][2], 1e-9, "Encounter hue changed baseline X velocity");
                near(pond.velocityYForTest(i), expected[scenario][i][3], 1e-9, "Encounter hue changed baseline Y velocity");
            }
        }
    }

    private static void visualDepthStaysOutOfTheMotion() {
        PondDynamics pond = new PondDynamics(61L);
        double[] depth = new double[9];
        double low = 1, high = 0;
        for (int i = 0; i < 9; i++) {
            depth[i] = pond.getDepth(i);
            require(depth[i] >= 0.40 && depth[i] <= 1.0, "Depth outside the school range");
            low = Math.min(low, depth[i]);
            high = Math.max(high, depth[i]);
        }
        require(high - low >= 0.50, "Depths are too alike to read as a school");
        double[][] before = snapshot(pond);
        pond.configure(7, true, true);
        for (int i = 0; i < 40; i++) pond.advance(0.5, 1, 2, null);
        for (int i = 0; i < 9; i++) near(pond.getDepth(i), depth[i], 0, "Motion or settings changed depth");
        require(Math.abs(pond.getX(0) - before[0][0]) + Math.abs(pond.getY(0) - before[0][1]) > 0.01,
                "The depth check did not actually advance the pond");
        PondDynamics otherSeed = new PondDynamics(999L);
        for (int i = 0; i < 9; i++) near(otherSeed.getDepth(i), depth[i], 0, "Depth depended on the pond seed");
        // The three bodies shown before any fade already sit at different depths.
        require(pond.getDepth(0) != pond.getDepth(1) && pond.getDepth(1) != pond.getDepth(2)
                && pond.getDepth(0) != pond.getDepth(2), "The opening three share a depth");
    }

    private static PondDynamics headOn(boolean collisions) {
        PondDynamics pond = new PondDynamics(17L);
        pond.configure(2, false, collisions);
        for (int i = 0; i < 4; i++) pond.advance(0.5, 2, 2, null);
        pond.placeBodyForTest(0, -0.19, 0, 0.10, 0);
        pond.placeBodyForTest(1, 0.19, 0, -0.10, 0);
        return pond;
    }

    private static double[][] snapshot(PondDynamics pond) {
        double[][] state = new double[9][5];
        for (int i = 0; i < 9; i++) {
            state[i][0] = pond.getX(i); state[i][1] = pond.getY(i);
            state[i][2] = pond.getOpacity(i); state[i][3] = pond.getRadius(i);
            state[i][4] = pond.velocityXForTest(i) + pond.velocityYForTest(i);
        }
        return state;
    }
    private static void equal(double[][] a, double[][] b, double tolerance, String message) {
        for (int i = 0; i < a.length; i++) for (int j = 0; j < a[i].length; j++) near(a[i][j], b[i][j], tolerance, message);
    }
    private static void near(double a, double b, double tolerance, String message) {
        require(Double.isFinite(a) && Double.isFinite(b) && Math.abs(a - b) <= tolerance, message + ": " + a + " != " + b);
    }
    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
