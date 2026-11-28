/*
 * Koiros home-screen layout as solid shapes. GPL-3.0-or-later; see LICENSE.
 */
package org.ostadix.koilife;

/**
 * The apps, folders and widgets on the home screen, as solid shapes the dots
 * collide with.
 *
 * <p>Every shape is a rounded rectangle in screen pixels: centre, half width,
 * half height and corner radius. An app icon is a circle (a rounded rectangle
 * whose corner radius is half its side) with a pill below it for its label;
 * widgets and the search bar keep their own bounds with rounded corners.
 * Shapes on a scrolling home page are marked paged, so they can follow the
 * page while the launcher scrolls.
 *
 * <p>Only geometry is kept. No app names, text or contents are stored.
 * Snapshots are immutable and published whole, so the wallpaper reads them
 * from its own thread without locks. No Android classes are used here.
 */
public final class HomeLayout {
    /** The most shapes one snapshot holds. */
    public static final int MAX_SHAPES = 128;
    /** Floats per shape: centre x, centre y, half width, half height, corner radius. */
    public static final int STRIDE = 5;

    /** The latest snapshot, or null when none is known. Written by the layout reader. */
    public static volatile HomeLayout current;
    /** The launcher's page offset as the wallpaper last heard it. */
    public static volatile double liveOffset, liveStep;

    public final float[] shapes;
    public final boolean[] paged;
    public final int count, screenWidth, screenHeight;
    /** The page offset when the layout was read; paged shapes move by its change. */
    public final double offset, step;

    private HomeLayout(float[] shapes, boolean[] paged, int count, int screenWidth, int screenHeight,
                       double offset, double step) {
        this.shapes = shapes; this.paged = paged; this.count = count;
        this.screenWidth = screenWidth; this.screenHeight = screenHeight;
        this.offset = offset; this.step = step;
    }

    /**
     * Builds shapes from launcher items given by their screen bounds.
     *
     * @param labelled  the item is an app or folder icon (it has a name and is drawn as text with an icon)
     * @param nameChars length of its name, used only to size the label pill
     * @param paged     the item sits on a scrolling home page
     * @param density   display density (pixels per dp), used when no icon size can be read
     */
    public static HomeLayout fromItems(int screenWidth, int screenHeight,
                                       int[] left, int[] top, int[] right, int[] bottom,
                                       boolean[] labelled, int[] nameChars, boolean[] paged, int items,
                                       double density, double offset, double step) {
        items = Math.max(0, Math.min(items, Math.min(MAX_SHAPES / 2, left.length)));
        double icon = iconSize(left, top, right, bottom, labelled, items, density);
        float[] shapes = new float[MAX_SHAPES * STRIDE];
        boolean[] onPage = new boolean[MAX_SHAPES];
        int n = 0;
        for (int k = 0; k < items; k++) {
            double w = right[k] - left[k], h = bottom[k] - top[k];
            if (!(w > 1) || !(h > 1)) continue;
            double cx = left[k] + w * .5;
            if (labelled[k] && w <= 2.2 * icon && h <= 2.6 * icon) {
                double s = Math.min(icon, Math.min(w, h)), radius = s * .5 * .96;
                if (h - s > .4 * s) {
                    // Icon above its name, the pair centred in the cell as launchers draw it.
                    double block = s * (1 + LABEL_GAP + LABEL_HEIGHT);
                    double y0 = top[k] + Math.max(0, (h - block) * .5);
                    n = put(shapes, onPage, n, cx, y0 + s * .5, radius, radius, radius, paged[k]);
                    double halfH = s * LABEL_HEIGHT * .5;
                    double halfW = Math.min(w * .47, Math.max(halfH, Math.max(1, nameChars[k]) * s * CHAR_WIDTH * .5 + s * .1));
                    n = put(shapes, onPage, n, cx, y0 + s * (1 + LABEL_GAP) + halfH, halfW, halfH, halfH, paged[k]);
                } else {
                    // A dock icon: no name drawn, the icon centred.
                    n = put(shapes, onPage, n, cx, top[k] + h * .5, radius, radius, radius, paged[k]);
                }
            } else {
                // A widget or search bar: a short one is a pill, a tall one has rounded corners.
                double corner = h < 1.1 * icon ? h * .5 : Math.min(.36 * icon, Math.min(w, h) * .5);
                n = put(shapes, onPage, n, cx, top[k] + h * .5, w * .5, h * .5, corner, paged[k]);
            }
        }
        return new HomeLayout(shapes, onPage, n, screenWidth, screenHeight, offset, step);
    }

    /** Space between an icon and its name, and the name's line height, as fractions of the icon. */
    static final double LABEL_GAP = .11, LABEL_HEIGHT = .27, CHAR_WIDTH = .115;

    /**
     * The icon's side in pixels: the smallest side of any icon cell, since dock
     * cells are sized to the icon itself. Without icons, 64 dp.
     */
    static double iconSize(int[] left, int[] top, int[] right, int[] bottom, boolean[] labelled, int items, double density) {
        double fallback = 64 * (density > 0 && density < 10 ? density : 2.5), best = Double.MAX_VALUE;
        for (int k = 0; k < items; k++) {
            if (!labelled[k]) continue;
            double side = Math.min(right[k] - left[k], bottom[k] - top[k]);
            if (side > 1) best = Math.min(best, side);
        }
        if (best == Double.MAX_VALUE) return fallback;
        return Math.max(.5 * fallback, Math.min(best, 1.6 * fallback));
    }

    private static int put(float[] shapes, boolean[] onPage, int n, double cx, double cy,
                           double hx, double hy, double radius, boolean paged) {
        if (n >= MAX_SHAPES) return n;
        int o = n * STRIDE;
        shapes[o] = (float) cx; shapes[o + 1] = (float) cy;
        shapes[o + 2] = (float) hx; shapes[o + 3] = (float) hy;
        shapes[o + 4] = (float) Math.min(radius, Math.min(hx, hy));
        onPage[n] = paged;
        return n + 1;
    }

    /**
     * Pixels the paged shapes have moved since this layout was read: moving to a
     * later page slides the page left by one screen width per page step.
     */
    public double pageShift(double nowOffset, double nowStep, int width) {
        double s = step > 0 ? step : nowStep;
        if (!(s > 0) || !finite(nowOffset) || !finite(offset)) return 0;
        double pages = (nowOffset - offset) / s;
        return -Math.max(-4, Math.min(4, pages)) * width;
    }

    /**
     * Signed distance from (x, y) to a rounded rectangle, negative inside, with
     * the outward normal written to normal[0..1].
     */
    public static double distance(double x, double y, double cx, double cy, double hx, double hy, double r, double[] normal) {
        double dx = x - cx, dy = y - cy;
        double sx = dx < 0 ? -1 : 1, sy = dy < 0 ? -1 : 1;
        double qx = Math.abs(dx) - (hx - r), qy = Math.abs(dy) - (hy - r);
        double ox = Math.max(qx, 0), oy = Math.max(qy, 0);
        double outside = Math.sqrt(ox * ox + oy * oy);
        if (outside > 1e-9) { normal[0] = sx * ox / outside; normal[1] = sy * oy / outside; }
        else if (qx > qy) { normal[0] = sx; normal[1] = 0; }
        else { normal[0] = 0; normal[1] = sy; }
        return outside + Math.min(Math.max(qx, qy), 0) - r;
    }

    private static boolean finite(double v) { return !Double.isNaN(v) && !Double.isInfinite(v); }
}
