package org.ostadix.koilife;

/** Home-screen apps as solid shapes, and dots that rebound from them. */
public final class HomeLayoutTest {
    private static int checks;

    // A real Pixel Launcher home screen, 1280 x 2856: a widget, a workspace icon
    // with its name, a dock icon (no name drawn) and the search bar.
    private static final int W = 1280, H = 2856;
    private static final int[] L = { 59, 59, 103, 109 }, T = { 239, 923, 2374, 2597 };
    private static final int[] R = { 1221, 315, 271, 1170 }, B = { 888, 1230, 2563, 2740 };
    private static final boolean[] NAMED = { false, true, true, false }, PAGED = { true, true, false, false };
    private static final int[] CHARS = { 0, 6, 5, 0 };

    public static void main(String[] args) {
        iconsAreSizedFromTheDock();
        shapesFollowTheirItems();
        distanceIsSignedWithAnOutwardNormal();
        colliderPushesOutAndIgnoresOpenWater();
        pagedShapesFollowThePage();
        anotherOrientationIsIgnored();
        bounceReflectsOnlyMotionIntoTheSurface();
        dotsStayOutOfApps();
        System.out.println("HomeLayoutTest passed: " + checks + " checks");
    }

    private static HomeLayout layout(double offset, double step) {
        return HomeLayout.fromItems(W, H, L, T, R, B, NAMED, CHARS, PAGED, 4, 2.55, offset, step);
    }

    private static void iconsAreSizedFromTheDock() {
        near(HomeLayout.iconSize(L, T, R, B, NAMED, 4, 2.55), 168, 0, "The dock cell did not set the icon size");
        boolean[] none = new boolean[4];
        near(HomeLayout.iconSize(L, T, R, B, none, 4, 2.0), 128, 0, "Without icons the size was not 64 dp");
    }

    private static void shapesFollowTheirItems() {
        HomeLayout home = layout(0, 0);
        check(home.count == 5, "Expected widget, icon, name, dock icon and search bar: " + home.count);
        // Widget: its own bounds, rounded corners.
        shape(home, 0, 640, 563.5, 581, 324.5);
        check(home.shapes[4] > 0 && home.shapes[4] < 324.5, "Widget corners were not rounded");
        // Workspace icon: a circle at the top of the cell, centred, and its name below.
        float iconY = home.shapes[HomeLayout.STRIDE + 1], radius = home.shapes[HomeLayout.STRIDE + 2];
        near(home.shapes[HomeLayout.STRIDE], 187, 1e-3, "Icon not centred in its cell");
        near(radius, 168 * .5 * .96, 1e-3, "Icon circle not icon-sized");
        near(home.shapes[HomeLayout.STRIDE + 4], radius, 1e-3, "Icon is not round");
        check(iconY - radius > 923 && iconY + radius < 1230, "Icon left its cell");
        float nameY = home.shapes[2 * HomeLayout.STRIDE + 1];
        check(nameY > iconY + radius && nameY < 1230, "Name not below the icon");
        check(home.shapes[2 * HomeLayout.STRIDE + 2] < 128, "Name wider than its cell");
        // Dock icon: centred in its cell, no name.
        shape(home, 3, 187, 2468.5, 80.64, 80.64);
        // Search bar: a pill.
        near(home.shapes[4 * HomeLayout.STRIDE + 4], 71.5, 1e-3, "Search bar is not a pill");
        check(home.paged[0] && home.paged[1] && home.paged[2] && !home.paged[3] && !home.paged[4], "Paging was not kept");
    }

    private static void distanceIsSignedWithAnOutwardNormal() {
        double[] n = new double[2];
        near(HomeLayout.distance(0, 0, 0, 0, 10, 10, 10, n), -10, 1e-12, "Centre of a circle");
        near(HomeLayout.distance(13, 0, 0, 0, 10, 10, 10, n), 3, 1e-12, "Outside a circle");
        near(n[0], 1, 1e-12, "Normal not outward");
        near(HomeLayout.distance(0, -15, 0, 0, 50, 20, 5, n), -5, 1e-12, "Inside a box near its top");
        near(n[1], -1, 1e-12, "Normal inside a box did not point to the nearest side");
        double d = HomeLayout.distance(60, 30, 0, 0, 50, 20, 5, n);
        near(d, Math.hypot(15, 15) - 5, 1e-12, "Rounded corner distance");
        near(n[0], Math.sqrt(.5), 1e-12, "Corner normal");
    }

    private static void colliderPushesOutAndIgnoresOpenWater() {
        HomeCollider collider = new HomeCollider();
        collider.prepare(layout(0, 0), 0, W, H, 6);
        double[] out = new double[3];
        check(collider.collide(187, 1010, out), "A dot on an icon was not in contact");
        check(out[2] > 6, "Push did not reach the surface and margin");
        check(collider.collide(187, 2468.5 - 70, out) && out[1] < 0, "A dot near a dock icon's top was not pushed up");
        check(!collider.collide(640, 1950, out), "Open water collided");
        check(!collider.collide(-5, 100, out), "Off-screen point collided");
    }

    private static void pagedShapesFollowThePage() {
        HomeLayout home = layout(0, .5);
        near(home.pageShift(.5, .5, W), -W, 1e-9, "One page right did not slide one screen left");
        near(home.pageShift(0, .5, W), 0, 0, "Same page moved");
        near(layout(0, 0).pageShift(.3, 0, W), 0, 0, "Unknown step moved shapes");
        HomeCollider collider = new HomeCollider();
        double[] out = new double[3];
        collider.prepare(home, -W, W, H, 6);
        check(!collider.collide(187, 1010, out), "A paged icon stayed after its page left");
        check(collider.collide(187, 2468.5, out), "The dock moved with the page");
    }

    private static void anotherOrientationIsIgnored() {
        HomeCollider collider = new HomeCollider();
        collider.prepare(layout(0, 0), 0, H, W, 6);
        check(!collider.active(), "A portrait layout was used in landscape");
    }

    private static void bounceReflectsOnlyMotionIntoTheSurface() {
        DotLattice dots = new DotLattice(7);
        dots.seed(1, 40, 1, 2.2);
        for (int k = 0; k < 30; k++) dots.step(1 / 30.0);
        int i = 0;
        dots.steer(i, .2, -.1, 1);
        double before = screenVx(dots, i);
        dots.bounce(i, -1, 0, .5);
        near(screenVx(dots, i), -.5 * before, 1e-9, "Motion into the surface was not reflected at the restitution");
        double after = screenVx(dots, i);
        dots.bounce(i, -1, 0, .5);
        near(screenVx(dots, i), after, 1e-12, "Motion away from the surface changed");
        check(Math.cos(dots.heading[i] - Math.PI) >= -1e-12, "Heading still pointed into the surface");
    }

    private static double screenVx(DotLattice dots, int i) {
        // The on-screen velocity is what steer() sets; read it back the same way.
        double[] v = new double[2];
        dots.screenVelocity(i, v);
        return v[0];
    }

    /**
     * The whole loop without the renderer's dome: dots drawn at their shadows on
     * a 1280 x 2856 screen. With collisions on, almost no dot is ever found inside
     * an app; without them, many are.
     */
    private static void dotsStayOutOfApps() {
        double insideWithout = run(false), insideWith = run(true);
        check(insideWithout > 200, "Control run never crossed an app: " + insideWithout);
        check(insideWith < insideWithout * .02, "Dots still crossed apps: " + insideWith + " vs " + insideWithout);
    }

    private static double run(boolean collide) {
        DotLattice dots = new DotLattice(821166217176535313L);
        double extent = W, uw = 1, uh = H / (double) W;
        dots.seed(7, 80, uw, uh);
        HomeCollider collider = new HomeCollider();
        collider.prepare(layout(0, 0), 0, W, H, 4);
        double[] out = new double[3];
        int inside = 0;
        for (int frame = 0; frame < 900; frame++) {
            dots.step(1 / 30.0);
            for (int i = 0, n = dots.getCount(); i < n; i++) {
                double x = W * .5 + dots.px[i] * extent, y = H * .5 + dots.py[i] * extent;
                if (!collider.collide(x, y, out)) continue;
                if (out[2] > 4.5) inside++; // past the margin: inside the shape itself
                if (collide) {
                    dots.nudge(i, out[2] * out[0] / extent, out[2] * out[1] / extent);
                    dots.bounce(i, out[0], out[1], .55);
                }
            }
        }
        return inside;
    }

    private static void shape(HomeLayout home, int k, double cx, double cy, double hx, double hy) {
        int o = k * HomeLayout.STRIDE;
        near(home.shapes[o], cx, 1e-2, "Shape " + k + " x");
        near(home.shapes[o + 1], cy, 1e-2, "Shape " + k + " y");
        near(home.shapes[o + 2], hx, 1e-2, "Shape " + k + " half width");
        near(home.shapes[o + 3], hy, 1e-2, "Shape " + k + " half height");
    }

    private static void near(double actual, double expected, double tolerance, String message) {
        checks++;
        if (!(Math.abs(actual - expected) <= tolerance)) throw new AssertionError(message + ": " + actual + " vs " + expected);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
