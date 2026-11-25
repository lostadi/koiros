package org.ostadix.koilife;

/** Page parallax, depth scale, and the slow water clock. */
public final class PageDriftTest {
    private static int checks;

    public static void main(String[] args) {
        stillWhenTheLauncherHasOnePage();
        pageStepCentersAndBounds();
        pixelOffsetIsTheFallback();
        nearerBodiesTravelFarther();
        depthChangesSizeWithoutLeavingTheBodyUnread();
        slowMotionStillMovesTheWater();
        nonFiniteInputsStayPut();
        System.out.println("PageDriftTest passed: " + checks + " checks");
    }

    private static void stillWhenTheLauncherHasOnePage() {
        near(PageDrift.centeredOffset(0, 0, 0, 1080), 0, 0, "One page drifted");
        near(PageDrift.centeredOffset(0.4, 0, 0, 1080), 0, 0, "A step of zero still used the normalized offset");
        near(PageDrift.pixelShift(0, 1, 1000), 0, 0, "Centered page moved a body");
    }

    private static void pageStepCentersAndBounds() {
        near(PageDrift.centeredOffset(0, 1, 400, 1080), -0.5, 1e-15, "First page was not the left extreme");
        near(PageDrift.centeredOffset(1, 0.5, 0, 1080), 0.5, 1e-15, "Last page was not the right extreme");
        near(PageDrift.centeredOffset(0.5, 0.25, 900, 1080), 0, 1e-15, "Middle page was not centered");
        near(PageDrift.centeredOffset(-3, 1, 0, 1080), -0.5, 0, "Offset below zero escaped");
        near(PageDrift.centeredOffset(4, 1, 0, 1080), 0.5, 0, "Offset above one escaped");
        // The page step wins over a contradictory pixel offset.
        near(PageDrift.centeredOffset(0.25, 0.5, -1080, 1080), -0.25, 1e-15, "Pixel offset overrode a real page step");
    }

    private static void pixelOffsetIsTheFallback() {
        near(PageDrift.centeredOffset(0, 0, 1080, 1080), 0.5, 1e-15, "A full screen of pixels was not the extreme");
        near(PageDrift.centeredOffset(0, 0, -540, 1080), -0.5, 1e-15, "Half a screen left was not clamped");
        near(PageDrift.centeredOffset(0, 0, 5000, 1000), 0.5, 0, "A multi-page pixel offset was not clamped");
        near(PageDrift.centeredOffset(0, 0, 100, 0), 0, 0, "A zero-width surface produced a shift");
    }

    private static void nearerBodiesTravelFarther() {
        double near = PageDrift.pixelShift(0.5, 1.0, 1000);
        double far = PageDrift.pixelShift(0.5, 0.40, 1000);
        near(near, -70.0, 1e-12, "Nearest extreme shift is not 7 percent");
        require(far < 0 && far > near, "A far body did not move less, and the same way, than a near body");
        near(PageDrift.pixelShift(-0.5, 1.0, 1000), 70.0, 1e-12, "The opposite page did not reverse the scene");
        near(PageDrift.pixelShift(2, 1, 1000), -70.0, 1e-12, "An uncentered value escaped the 7 percent bound");
        near(PageDrift.pixelShift(0.5, 1, 0), 0, 0, "A zero extent produced a shift");
    }

    private static void depthChangesSizeWithoutLeavingTheBodyUnread() {
        double nearest = PageDrift.visualScale(1);
        double farthest = PageDrift.visualScale(0.40);
        near(nearest, 1.25, 1e-15, "Nearest scale");
        near(farthest, 0.83, 1e-15, "Farthest scale");
        require(nearest / farthest >= 1.45, "Depth did not separate sizes enough to read as a school");
        require(farthest >= 0.75, "The far body became too small to read");
        near(PageDrift.visualScale(4), nearest, 0, "Depth above one grew without a bound");
        near(PageDrift.visualScale(-2), 0.55, 0, "Negative depth collapsed the body");
    }

    private static void slowMotionStillMovesTheWater() {
        double saved = PageDrift.waterRadiansPerSecond(0.11);
        require(saved > 0.11 && saved < 0.14, "The saved slow motion froze or rushed the water: " + saved);
        near(PageDrift.waterRadiansPerSecond(0), PageDrift.waterRadiansPerSecond(0.02), 0,
                "Motion below the floor lost the slow water clock");
        require(PageDrift.waterRadiansPerSecond(0.11) > PageDrift.waterRadiansPerSecond(0.02),
                "The slowest setting did not keep a water clock");
        require(PageDrift.waterRadiansPerSecond(2) > PageDrift.waterRadiansPerSecond(1),
                "Faster motion did not speed the water");
        near(PageDrift.waterRadiansPerSecond(9), PageDrift.waterRadiansPerSecond(2), 0, "Water clock escaped the speed cap");
        double turns = saved / (2 * Math.PI);
        require(turns > 1.0 / 60.0 && turns < 1.0 / 40.0, "Slow water is outside one cycle per 40 to 60 seconds");
    }

    private static void nonFiniteInputsStayPut() {
        near(PageDrift.centeredOffset(Double.NaN, 1, 0, 100), 0, 0, "NaN offset");
        near(PageDrift.centeredOffset(Double.NaN, Double.NaN, Double.NaN, Double.NaN), 0, 0, "All-NaN offset");
        near(PageDrift.centeredOffset(0.2, Double.POSITIVE_INFINITY, 0, 100), 0, 0, "Infinite step fell through unsafely");
        // A broken page step may still use a real pixel offset.
        near(PageDrift.centeredOffset(Double.NaN, 1, 10, 100), 0.1, 1e-15, "Pixel fallback after a NaN page offset");
        near(PageDrift.pixelShift(Double.NaN, 1, 100), 0, 0, "NaN shift");
        near(PageDrift.visualScale(Double.NaN), 0.55, 0, "NaN depth");
        near(PageDrift.waterRadiansPerSecond(Double.NaN), PageDrift.waterRadiansPerSecond(0.02), 0, "NaN motion");
        near(PageDrift.positiveMod(-12, 360), 348, 1e-12, "Negative hue wrap");
        near(PageDrift.positiveMod(Double.NaN, 360), 0, 0, "NaN hue wrap");
    }

    private static void near(double actual, double expected, double tolerance, String message) {
        require(Double.isFinite(actual) && Math.abs(actual - expected) <= tolerance,
                message + ": " + actual + " != " + expected);
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
