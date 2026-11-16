package org.ostadix.koilife;

/** Behavioral checks for drawing density, independent of Android and Life clocks. */
public final class FrameBudgetTest {
    private static int checks;
    private static final double DT = 1.0 / 60.0;

    public static void main(String[] args) {
        invalidSamplesDoNothing();
        warmupAndStableLoad();
        sustainedOverloadReducesDensity();
        recoveryIsSlowAndBounded();
        pausePreservesLearnedDensity();
        mixedLoadNeverThrashes();
        System.out.println("FrameBudgetTest passed " + checks + " checks");
    }

    private static void invalidSamplesDoNothing() {
        FrameBudget budget = new FrameBudget();
        require(budget.getPointCount() == 9000, "Unexpected initial drawing density");
        budget.observeFrame(10, DT);
        double mean = budget.getSmoothedCostMs();
        double[] invalid = {0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (double value : invalid) {
            require(!budget.observeFrame(value, DT), "Invalid duration changed density");
            require(!budget.observeFrame(80, value), "Invalid elapsed time changed density");
        }
        require(budget.getPointCount() == 9000 && budget.getSmoothedCostMs() == mean,
                "Invalid input changed accumulated state");
    }

    private static void warmupAndStableLoad() {
        FrameBudget budget = new FrameBudget();
        for (int i = 0; i < 29; i++) budget.observeFrame(80, DT);
        require(budget.getPointCount() == 9000, "Density changed before half-second warmup");
        budget = new FrameBudget();
        for (int i = 0; i < 120; i++) budget.observeFrame(10, DT);
        budget.observeFrame(28, DT); // An isolated slow frame should not cause a downgrade.
        for (int i = 0; i < 120; i++) budget.observeFrame(10, DT);
        require(budget.getPointCount() == 9000, "One isolated slow frame reduced density");
        for (int i = 0; i < 720; i++) budget.observeFrame(12.1, DT);
        require(budget.getPointCount() == 9000, "Load near the target caused quality oscillation");
    }

    private static void sustainedOverloadReducesDensity() {
        FrameBudget budget = new FrameBudget();
        int changes = 0;
        double lastChange = -10.0;
        for (int frame = 0; frame < 300; frame++) {
            int old = budget.getPointCount();
            boolean changed = budget.observeFrame(80, DT);
            bounds(budget);
            require(changed == (old != budget.getPointCount()), "Change result disagrees with density");
            if (changed) {
                double now = (frame + 1) * DT;
                require(budget.getPointCount() < old, "Overload increased density");
                require(budget.getPointCount() >= old * 0.75, "A downgrade removed over a quarter");
                require(now - lastChange >= 0.60 - 1e-9, "Downgrades occurred too close together");
                if (changes == 0) require(now <= 1.2, "Sustained overload response was too slow");
                lastChange = now;
                changes++;
            }
        }
        require(changes > 0 && budget.getPointCount() == FrameBudget.MIN_POINTS,
                "Persistent overload did not reach the minimum density");
    }

    private static void recoveryIsSlowAndBounded() {
        FrameBudget budget = overloadedBudget();
        int minimum = budget.getPointCount();
        for (int i = 0; i < 90; i++) budget.observeFrame(2, DT);
        require(budget.getPointCount() == minimum, "Brief headroom increased density too soon");
        double lastIncrease = -10.0;
        int changes = 0;
        for (int frame = 0; frame < 3000; frame++) {
            int old = budget.getPointCount();
            if (budget.observeFrame(2, DT)) {
                double now = frame * DT;
                require(budget.getPointCount() > old, "Ample headroom reduced density");
                require(budget.getPointCount() <= old * 1.10 + 1e-9, "Recovery added over ten percent");
                require(now - lastIncrease >= 2.0 - 1e-9, "Recovery changed quality too frequently");
                lastIncrease = now;
                changes++;
            }
            bounds(budget);
        }
        require(changes > 0 && budget.getPointCount() == FrameBudget.MAX_POINTS,
                "Sustained headroom did not recover toward maximum density");
    }

    private static void pausePreservesLearnedDensity() {
        FrameBudget budget = overloadedBudget();
        int learned = budget.getPointCount();
        budget.resetTiming();
        require(budget.getPointCount() == learned, "Pause discarded learned drawing density");
        require(budget.getSmoothedCostMs() == 0, "Pause retained stale timing");
        for (int i = 0; i < 29; i++) budget.observeFrame(1, DT);
        require(budget.getPointCount() == learned, "Resume skipped warmup");
        budget.resetTiming();
        require(!budget.observeFrame(1, 3600), "One long gap counted as sustained recovery");
        require(budget.getPointCount() == learned, "One long gap changed drawing density");
    }

    private static void mixedLoadNeverThrashes() {
        FrameBudget budget = new FrameBudget();
        double lastChange = -10;
        for (int frame = 0; frame < 2400; frame++) {
            double cost = ((frame / 17) % 3 == 0) ? 30 : 6;
            if (budget.observeFrame(cost, DT)) {
                double now = frame * DT;
                require(now - lastChange >= 0.60 - 1e-9, "Bursty load caused per-frame quality changes");
                lastChange = now;
            }
            bounds(budget);
        }
    }

    private static FrameBudget overloadedBudget() {
        FrameBudget budget = new FrameBudget();
        for (int i = 0; i < 300; i++) budget.observeFrame(80, DT);
        require(budget.getPointCount() == FrameBudget.MIN_POINTS, "Overload fixture did not settle at minimum");
        return budget;
    }

    private static void bounds(FrameBudget budget) {
        int count = budget.getPointCount();
        require(count >= 3000 && count <= 14000, "Drawing density outside bounds");
        require(count % 3 == 0, "Creature groups have unequal point counts");
        require(!Double.isNaN(budget.getSmoothedCostMs())
                && !Double.isInfinite(budget.getSmoothedCostMs()), "Timing estimate is not finite");
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
