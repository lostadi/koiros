package org.ostadix.koilife;

/** Plain-Java checks for the pooled ripple model; no Android dependencies. */
public final class TouchRipplesTest {
    private static int checks;
    private static void check(boolean okay, String message) {
        checks++;
        if (!okay) throw new AssertionError(message);
    }
    private static void near(double actual, double expected, double tolerance, String message) {
        check(Math.abs(actual - expected) <= tolerance, message + ": " + actual + " versus " + expected);
    }

    public static void main(String[] args) {
        TouchRipples waves = new TouchRipples();
        double[] out = new double[3], mirrored = new double[3];
        waves.sample(.5, .5, 1, 2, out);
        check(waves.getActiveCount() == 0, "Empty pool");
        near(Math.hypot(out[0], out[1]) + out[2], 0, 0, "No wave is exactly neutral");
        waves.touch(Double.NaN, .5); waves.touch(.5, Double.POSITIVE_INFINITY);
        check(waves.getActiveCount() == 0, "Invalid taps ignored");
        waves.touch(.5, .5);
        check(waves.getActiveCount() == 1, "Tap creates a wave");
        near(waves.getOpacity(0), 0, 0, "Tap fades in without a flash");
        waves.advance(1);
        double radius = waves.getRadius(0);
        check(radius > .025 && radius < .2, "Slow outward propagation");
        waves.sample(.5 + radius, .5, 1, 2, out);
        waves.sample(.5 - radius, .5, 1, 2, mirrored);
        check(out[0] > .05, "Wave deforms the local field");
        near(out[0], -mirrored[0], 1e-12, "Radial symmetry X");
        near(out[1], 0, 0, "Radial axis Y");
        near(out[2], mirrored[2], 1e-12, "Symmetric projection");
        waves.sample(.5, .5 + radius / 2, 1, 2, mirrored);
        near(out[0], mirrored[1], 1e-12, "Portrait wave is circular in screen units");
        waves.sample(.5 + radius / 2, .5, 2, 1, mirrored);
        near(out[0], mirrored[0], 1e-12, "Landscape wave is circular in screen units");
        waves.sample(.95, .95, 1, 2, mirrored);
        near(Math.hypot(mirrored[0], mirrored[1]) + mirrored[2], 0, 0, "Distant field unchanged");

        long xBits = Double.doubleToLongBits(out[0]), yBits = Double.doubleToLongBits(out[1]);
        long zBits = Double.doubleToLongBits(out[2]);
        double frozenAge = waves.getAge(0), frozenRadius = waves.getRadius(0);
        for (int i = 0; i < 100; i++) {
            waves.advance(0); waves.advance(-1); waves.advance(Double.NaN);
            waves.sample(.5 + radius, .5, 1, 2, mirrored);
            check(Double.doubleToLongBits(mirrored[0]) == xBits
                    && Double.doubleToLongBits(mirrored[1]) == yBits
                    && Double.doubleToLongBits(mirrored[2]) == zBits, "Paused field is bit-exact");
        }
        near(waves.getAge(0), frozenAge, 0, "Paused age");
        near(waves.getRadius(0), frozenRadius, 0, "Paused radius");

        waves.touch(.2, .2); waves.advance(.2); waves.touch(.8, .8);
        check(waves.getActiveCount() == 3, "Three slots filled");
        waves.touch(-2, 2);
        check(waves.getActiveCount() == 3, "Fourth tap keeps bounded pool");
        near(waves.getU(0), 0, 0, "Oldest wave replaced; X clamped");
        near(waves.getV(0), 1, 0, "Oldest wave replaced; Y clamped");
        near(waves.getU(1), .2, 0, "Younger wave retained");
        near(waves.getU(2), .8, 0, "Newest prior wave retained");

        waves.clear();
        check(waves.getActiveCount() == 0, "Disable clears all waves");
        for (int i = 0; i < 3; i++) waves.touch(.5, .5);
        waves.advance(.8);
        radius = waves.getRadius(0);
        waves.sample(.5 + radius, .5, 1, 2, out);
        near(Math.hypot(out[0], out[1]), TouchRipples.MAX_FORCE, 1e-12, "Overlapping waves are capped");
        near(out[2], .10, 1e-12, "Projection is capped");
        double previousRadius = radius;
        for (int frame = 0; frame < 360; frame++) {
            waves.advance(1.0 / 60);
            if (waves.isActive(0)) {
                check(waves.getRadius(0) >= previousRadius, "Radius only travels outward");
                previousRadius = waves.getRadius(0);
            }
            for (int y = 0; y < 19; y++) for (int x = 0; x < 13; x++) {
                waves.sample(x / 12.0, y / 18.0, 1, 2, out);
                double force = Math.hypot(out[0], out[1]);
                check(!Double.isNaN(force) && !Double.isInfinite(force)
                    && force <= TouchRipples.MAX_FORCE + 1e-12, "Finite bounded grid force");
                check(out[2] >= 0 && out[2] <= .10, "Bounded grid projection");
            }
        }
        waves.advance(.3);
        check(waves.getActiveCount() == 0, "Waves expire in seven visible seconds");
        waves.sample(.5, .5, 1, 2, out);
        near(Math.hypot(out[0], out[1]) + out[2], 0, 0, "Expired waves leave no persistent field");
        waves.touch(.5, .5); waves.advance(1000);
        check(waves.getActiveCount() == 0, "Long visible advance expires safely");

        TouchRipples single = new TouchRipples(), partitioned = new TouchRipples();
        single.touch(.3, .7); partitioned.touch(.3, .7);
        single.advance(2);
        for (int i = 0; i < 120; i++) partitioned.advance(1.0 / 60);
        single.sample(.45, .7, 1, 2, out);
        partitioned.sample(.45, .7, 1, 2, mirrored);
        for (int i = 0; i < 3; i++) near(out[i], mirrored[i], 1e-12, "Frame-partition independence");
        waves.sample(.5, .5, 0, 2, out);
        near(Math.hypot(out[0], out[1]) + out[2], 0, 0, "Invalid aspect gives neutral output");
        System.out.println("TouchRipplesTest passed " + checks + " checks");
    }
}

