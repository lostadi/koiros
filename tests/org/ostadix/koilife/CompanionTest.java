package org.ostadix.koilife;

/** The pond's relationship with its person: welcome, rhythm, affection, attention. */
public final class CompanionTest {
    private static int checks;

    public static void main(String[] args) {
        welcomeGrowsWithAbsence();
        nightAndLiveliness();
        affectionFadesAndGrows();
        attentionDrawsBodiesIn();
        negativeAttentionSendsThemAway();
        gatheringPullsEveryone();
        zeroStrengthChangesNothing();
        System.out.println("CompanionTest passed: " + checks + " checks");
    }

    private static void welcomeGrowsWithAbsence() {
        near(Companion.welcome(0), 0, 0, "No welcome without absence");
        near(Companion.welcome(19_999), 0, 0, "No welcome under twenty seconds");
        near(Companion.welcome(20_000), 0.25 + 0.75 * Math.log1p(20 / 60.0) / Math.log1p(240), 1e-12, "Welcome at twenty seconds");
        double previous = 0;
        for (long ms = 20_000; ms <= 12L * 3600 * 1000; ms += 60_000) {
            double w = Companion.welcome(ms);
            require(w >= previous - 1e-12 && w >= .25 && w <= 1, "Welcome must grow and stay bounded at " + ms);
            previous = w;
        }
        near(Companion.welcome(4L * 3600 * 1000), 1, 1e-12, "Full welcome after four hours");
        near(Companion.welcome(Long.MAX_VALUE), 1, 0, "Welcome stays bounded");
    }

    private static void nightAndLiveliness() {
        near(Companion.night(12), 0, 0, "Noon"); near(Companion.night(2), 1, 0, "2 am");
        near(Companion.night(22), .5, 1e-12, "22:00 ramps"); near(Companion.night(6), .5, 1e-12, "06:00 ramps");
        near(Companion.night(-1), 1, 0, "Hour wraps"); near(Companion.night(25), 1, 0, "Hour wraps forward");
        near(Companion.liveliness(0, 80, false), 1, 1e-12, "Daytime, healthy battery");
        require(Companion.liveliness(1, 80, false) < Companion.liveliness(0, 80, false), "Night is calmer");
        require(Companion.liveliness(0, 10, false) < Companion.liveliness(0, 30, false), "Low battery is sleepy");
        require(Companion.liveliness(0, 10, true) > Companion.liveliness(0, 10, false), "Charging is not sleepy");
        for (int b = -1; b <= 100; b++) for (double n = 0; n <= 1; n += .25) {
            double l = Companion.liveliness(n, b, b % 2 == 0);
            require(l >= .35 && l <= 1.2, "Liveliness bounds");
        }
        require(Companion.sleepy(15, false) && !Companion.sleepy(15, true) && !Companion.sleepy(50, false)
            && !Companion.sleepy(-1, false), "Sleepy only on a low battery that is not charging");
    }

    private static void affectionFadesAndGrows() {
        double a = 0;
        for (int i = 0; i < 200; i++) {
            double next = Companion.pet(a);
            require(next > a && next <= 1, "Petting grows affection within bounds");
            a = next;
        }
        require(a > .99, "Much play approaches full affection");
        near(Companion.fade(.8, 0), .8, 0, "No fade without time");
        near(Companion.fade(.8, (long) Companion.AFFECTION_FADE_MS), .8 / Math.E, 1e-12, "One time constant");
        require(Companion.fade(1, 30L * 24 * 3600 * 1000) < .01, "A month apart almost forgets");
        near(Companion.fade(Double.NaN, 5), 0, 0, "Invalid affection");
    }

    private static PondDynamics pond(long seed) {
        PondDynamics pond = new PondDynamics(seed);
        pond.configure(3, false, true, false);
        return pond;
    }

    private static double meanDistance(PondDynamics pond, double px, double py) {
        double sum = 0;
        for (int i = 0; i < 3; i++) sum += Math.hypot(pond.getX(i) - px, pond.getY(i) - py);
        return sum / 3;
    }

    private static void attentionDrawsBodiesIn() {
        PondDynamics control = pond(7L), drawn = pond(7L);
        double px = .15, py = .5;
        for (int i = 0; i < 60 * 8; i++) {
            control.advance(1.0 / 60, 1, 2, null);
            drawn.setAttention(px, py, 1);
            drawn.advance(1.0 / 60, 1, 2, null);
        }
        double before = meanDistance(control, px, py), after = meanDistance(drawn, px, py);
        require(after < before * .75, "Attention should draw bodies in: " + after + " vs " + before);
        double nearest = 9;
        for (int i = 0; i < 3; i++) nearest = Math.min(nearest, Math.hypot(drawn.getX(i) - px, drawn.getY(i) - py));
        require(nearest < .35, "Someone should come close to look: " + nearest);
    }

    private static void negativeAttentionSendsThemAway() {
        PondDynamics control = pond(9L), startled = pond(9L);
        double px = 0, py = 0;
        for (int i = 0; i < 60 * 4; i++) {
            control.advance(1.0 / 60, 1, 2, null);
            startled.setAttention(px, py, -1);
            startled.advance(1.0 / 60, 1, 2, null);
        }
        require(meanDistance(startled, px, py) > meanDistance(control, px, py), "A startle should push bodies away");
    }

    private static void gatheringPullsEveryone() {
        PondDynamics control = pond(13L), welcomed = pond(13L);
        double gx = 0, gy = .55;
        for (int i = 0; i < 60 * 6; i++) {
            control.advance(1.0 / 60, 1, 2, null);
            welcomed.setGathering(gx, gy, 1);
            welcomed.advance(1.0 / 60, 1, 2, null);
        }
        // Collisions keep gathered bodies in a ring around the point, so the
        // group gathers as a whole rather than each body landing on it.
        double c = meanDistance(control, gx, gy), w = meanDistance(welcomed, gx, gy);
        require(w < c * .92, "The group should gather (gently): " + w + " vs " + c);
        // A gentle welcome need not bring everyone in quickly, but it never
        // leaves the farthest body farther away than it would otherwise be.
        double farthestControl = 0, farthestWelcomed = 0;
        for (int i = 0; i < 3; i++) {
            farthestControl = Math.max(farthestControl, Math.hypot(control.getX(i) - gx, control.getY(i) - gy));
            farthestWelcomed = Math.max(farthestWelcomed, Math.hypot(welcomed.getX(i) - gx, welcomed.getY(i) - gy));
        }
        require(farthestWelcomed <= farthestControl + 1e-9, "The welcome pushed someone away: "
            + farthestWelcomed + " vs " + farthestControl);
    }

    private static void zeroStrengthChangesNothing() {
        PondDynamics control = pond(21L), quiet = pond(21L);
        for (int i = 0; i < 60 * 20; i++) {
            control.advance(1.0 / 60, 1, 2, null);
            quiet.setAttention(.3, -.4, 0);
            quiet.setGathering(.1, .2, 0);
            quiet.advance(1.0 / 60, 1, 2, null);
        }
        for (int i = 0; i < 9; i++) {
            near(quiet.getX(i), control.getX(i), 0, "Zero attention moved x");
            near(quiet.getY(i), control.getY(i), 0, "Zero attention moved y");
        }
        quiet.setAttention(Double.NaN, 0, 1);
        quiet.advance(1.0 / 60, 1, 2, null);
        control.advance(1.0 / 60, 1, 2, null);
        near(quiet.getX(0), control.getX(0), 0, "Invalid attention must be ignored");
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
