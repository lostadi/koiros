package org.ostadix.koilife;

/** Deterministic numerical checks for the autonomous, coupled three-system drive. */
public final class CoupledChaosTest {
    private static int checks;
    public static void main(String[] args) {
        repeatabilityAndClock();
        partitionInvariance();
        continuousAutonomousMotion();
        couplingAndDistinctFields();
        inputAndReseedBoundaries();
        System.out.println("CoupledChaosTest passed: " + checks + " checks");
    }
    private static void repeatabilityAndClock() {
        CoupledChaos a = new CoupledChaos(8217L);
        CoupledChaos b = new CoupledChaos(8217L);
        for (int i = 0; i < 300; i++) {
            a.advance(1.0 / 30.0, 0.2, 0.4);
            b.advance(1.0 / 30.0, 0.2, 0.4);
            equalStates(a, b, 0.0, "Same seed and input must reproduce exactly");
        }
        require(a.getStepCount() == 1200L, "Ten seconds must perform 1200 fixed steps");
        near(a.getTimeSeconds(), 10.0, 1e-10, "Simulated time");
        double[][] frozen = states(a);
        long steps = a.getStepCount();
        double time = a.getTimeSeconds();
        for (double dt : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY})
            a.advance(dt, 0.3, 0.8);
        same(frozen, states(a), 0.0, "Paused or invalid time must not change state");
        require(steps == a.getStepCount() && time == a.getTimeSeconds(), "Paused clock advanced");
    }
    private static void partitionInvariance() {
        CoupledChaos whole = new CoupledChaos(995L);
        CoupledChaos quarters = new CoupledChaos(995L);
        CoupledChaos ticks = new CoupledChaos(995L);
        whole.advance(1.0, 0.31, 0.29);
        for (int i = 0; i < 4; i++) quarters.advance(0.25, 0.31, 0.29);
        for (int i = 0; i < 120; i++) ticks.advance(1.0 / 120.0, 0.31, 0.29);
        require(whole.getStepCount() == 120 && quarters.getStepCount() == 120
                && ticks.getStepCount() == 120, "Partition changed step count");
        equalStates(whole, quarters, 1e-13, "Quarter-second partition changed integration");
        equalStates(whole, ticks, 1e-13, "Fixed-tick partition changed integration");
    }
    private static void continuousAutonomousMotion() {
        CoupledChaos drive = new CoupledChaos(1234567L);
        double[][] low = new double[3][4], high = new double[3][4];
        for (int s = 0; s < 3; s++) for (int a = 0; a < 4; a++) {
            low[s][a] = Double.POSITIVE_INFINITY;
            high[s][a] = Double.NEGATIVE_INFINITY;
        }
        for (int frame = 0; frame < 3600; frame++) {
            drive.advance(1.0 / 30.0, 0.0, 0.0);
            double[][] state = states(drive);
            for (int s = 0; s < 3; s++) for (int a = 0; a < 4; a++) {
                double value = state[s][a];
                require(Double.isFinite(value), "Non-finite chaos output");
                require(value >= (a == 3 ? 0.0 : -1.0) && value <= 1.0,
                        "Chaos sample outside normalized range");
                if (frame >= 300) {
                    low[s][a] = Math.min(low[s][a], value);
                    high[s][a] = Math.max(high[s][a], value);
                }
            }
        }
        for (int s = 0; s < 3; s++) for (int a = 0; a < 4; a++)
            require(high[s][a] - low[s][a] > (a == 3 ? 0.005 : 0.08),
                    "Constant Life inputs stalled system " + s + " axis " + a);
        require(drive.getStepCount() == 14400, "120-second clock drift");
        require(drive.getReseedCount() == 0, "Healthy integration unexpectedly reseeded");
    }
    private static void couplingAndDistinctFields() {
        CoupledChaos coupled = new CoupledChaos(96L, 0.12);
        CoupledChaos independent = new CoupledChaos(96L, 0.0);
        CoupledChaos otherSeed = new CoupledChaos(97L, 0.12);
        double[][] initialCoupled = states(coupled), initialIndependent = states(independent);
        for (int system = 0; system < 3; system++) for (int axis = 0; axis < 3; axis++)
            near(initialCoupled[system][axis], initialIndependent[system][axis], 0.0,
                    "Coupling must not change seeded initial coordinates");
        for (int i = 0; i < 120; i++) {
            coupled.advance(1.0 / 30.0, 0.2, 0.5);
            independent.advance(1.0 / 30.0, 0.2, 0.5);
            otherSeed.advance(1.0 / 30.0, 0.2, 0.5);
        }
        double[][] a = states(coupled), b = states(independent), c = states(otherSeed);
        for (int system = 0; system < 3; system++) {
            require(distance(a[system], b[system]) > 1e-5,
                    "Neighbor coupling did not affect system " + system);
            require(distance(a[system], c[system]) > 1e-5,
                    "Seed did not affect system " + system);
            require(distance(a[system], a[(system + 1) % 3]) > 1e-5,
                    "Separate fields collapsed to identical trajectories");
        }
    }
    private static void inputAndReseedBoundaries() {
        CoupledChaos bounded = new CoupledChaos(18L);
        bounded.advance(5.0, 0.0, 0.0);
        require(bounded.getStepCount() == 120, "Catch-up exceeded one second");
        CoupledChaos clamped = new CoupledChaos(29L), normal = new CoupledChaos(29L);
        clamped.advance(0.5, -10.0, 10.0);
        normal.advance(0.5, 0.0, 1.0);
        equalStates(clamped, normal, 0.0, "Life input clamping changed expected boundaries");
        bounded.reseed(18L);
        equalStates(bounded, new CoupledChaos(18L), 0.0, "Reseed failed to restore seed state");
        require(bounded.getStepCount() == 0 && bounded.getTimeSeconds() == 0.0,
                "Reseed failed to reset clock");
        require(bounded.getReseedCount() == 1, "Reseed accounting mismatch");
    }
    private static double[][] states(CoupledChaos drive) {
        double[][] result = new double[3][4];
        for (int i = 0; i < 3; i++) drive.sample(i, result[i]);
        return result;
    }
    private static void equalStates(CoupledChaos a, CoupledChaos b, double tolerance, String message) {
        same(states(a), states(b), tolerance, message);
    }
    private static void same(double[][] a, double[][] b, double tolerance, String message) {
        for (int i = 0; i < a.length; i++) for (int j = 0; j < a[i].length; j++)
            near(a[i][j], b[i][j], tolerance, message);
    }
    private static double distance(double[] a, double[] b) {
        double total = 0;
        for (int i = 0; i < a.length; i++) total += Math.abs(a[i] - b[i]);
        return total;
    }
    private static void near(double a, double b, double tolerance, String message) {
        require(Double.isFinite(a) && Double.isFinite(b) && Math.abs(a - b) <= tolerance,
                message + ": " + a + " != " + b);
    }
    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
